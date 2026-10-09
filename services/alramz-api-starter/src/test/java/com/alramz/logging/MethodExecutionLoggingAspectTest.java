package com.alramz.logging;

import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.aspect.MethodExecutionLoggingAspect;
import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MethodExecutionLoggingHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MethodExecutionLoggingAspectTest {

    private MethodExecutionLoggingAspect aspect;
    private ListAppender<ILoggingEvent> appender;
    private LoggingProperties properties;

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********", List.of(), List.of());
        properties = new LoggingProperties();
        properties.getAspect().setMaxPayloadLength(500);
        properties.getPerformance().setThreshold(Duration.ofMillis(0));

        MethodExecutionLoggingHelper helper = new MethodExecutionLoggingHelper(properties, new ObjectMapper());
        ObjectProvider<MethodExecutionLoggingHelper> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(helper);
        aspect = new MethodExecutionLoggingAspect(provider, Mockito.mock(ObjectProvider.class));

        Logger logger = (Logger) LoggerFactory.getLogger(MethodExecutionLoggingAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
    }

    @AfterEach
    void tearDown() {
        appender.stop();
    }

    @Test
    void logsEntryAndExitWithArgsAndReturn() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = TestService.class.getMethod("doWork", String.class, int.class);

        when(pjp.proceed()).thenReturn("result");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{"arg1", 123});
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(TestService.class);

        aspect.logMethodExecution(pjp);

        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        String allMessages = events.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(allMessages).contains(">>> ENTERING:");
        assertThat(allMessages).contains(">>> ARGS:");
        assertThat(allMessages).contains("<<< EXITING:");
        assertThat(allMessages).contains("<<< RETURN: result");
        assertThat(allMessages).contains("<<< EXECUTION TIME:");
    }

    @Test
    void logsExceptionWithStackTrace() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = TestService.class.getMethod("doWork", String.class, int.class);
        RuntimeException ex = new RuntimeException("boom");

        when(pjp.proceed()).thenThrow(ex);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(TestService.class);

        try {
            aspect.logMethodExecution(pjp);
        } catch (RuntimeException ignored) {
        }

        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        String allMessages = events.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(allMessages).contains("<<< EXCEPTION in");
        assertThat(allMessages).contains("boom");
    }

    @Test
    void recordsMethodSpanWithArgsReturnAndException() throws Throwable {
        io.micrometer.tracing.Tracer tracer = mock(io.micrometer.tracing.Tracer.class);
        io.micrometer.tracing.Span span = mock(io.micrometer.tracing.Span.class, Mockito.RETURNS_SELF);
        when(tracer.nextSpan()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(mock(io.micrometer.tracing.Tracer.SpanInScope.class));
        ObjectProvider<io.micrometer.tracing.Tracer> tracerProvider = Mockito.mock(ObjectProvider.class);
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        ObjectProvider<MethodExecutionLoggingHelper> helperProvider = Mockito.mock(ObjectProvider.class);
        when(helperProvider.getIfAvailable()).thenReturn(new MethodExecutionLoggingHelper(properties, new ObjectMapper()));
        MethodExecutionLoggingAspect tracingAspect = new MethodExecutionLoggingAspect(helperProvider, tracerProvider);

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{"arg1", 123});
        when(signature.getMethod()).thenReturn(TestService.class.getMethod("doWork", String.class, int.class));
        when(signature.getDeclaringType()).thenReturn(TestService.class);

        when(pjp.proceed()).thenReturn("result");
        tracingAspect.logMethodExecution(pjp);
        Mockito.verify(span).name("TestService#doWork");
        Mockito.verify(span).tag(Mockito.eq("method.args"), Mockito.contains("arg1"));
        Mockito.verify(span).tag("method.return", "result");
        Mockito.verify(span).end();

        RuntimeException ex = new RuntimeException("boom");
        when(pjp.proceed()).thenThrow(ex);
        try {
            tracingAspect.logMethodExecution(pjp);
        } catch (RuntimeException ignored) {
        }
        Mockito.verify(span).error(ex);
        Mockito.verify(span, Mockito.times(2)).end();
    }

    @Test
    void skipsExcludedPackage() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = Object.class.getMethod("toString");

        when(pjp.proceed()).thenReturn("ok");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(org.springframework.web.client.RestTemplate.class);

        aspect.logMethodExecution(pjp);

        assertThat(appender.list).isEmpty();
    }

    @Test
    void respectsLoggableOverrideWhenClassExcluded() throws Throwable {
        class CustomRestTemplate extends org.springframework.web.client.RestTemplate {
            @Loggable
            public String custom() {
                return "ok";
            }
        }

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method customMethod = CustomRestTemplate.class.getMethod("custom");

        when(pjp.proceed()).thenReturn("ok");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        when(signature.getMethod()).thenReturn(customMethod);
        when(signature.getDeclaringType()).thenReturn(CustomRestTemplate.class);

        aspect.logMethodExecution(pjp);

        assertThat(appender.list).isNotEmpty();
        String allMessages = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(allMessages).contains(">>> ENTERING:")
                .contains("<<< EXITING:");
    }

    @Test
    void separatorReplacesEscapedNewlines() throws Throwable {
        properties.getAspect().setSeparator("===\\n===");

        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method method = TestService.class.getMethod("doWork", String.class, int.class);

        when(pjp.proceed()).thenReturn("result");
        when(pjp.getSignature()).thenReturn(signature);
        when(pjp.getArgs()).thenReturn(new Object[]{"arg1", 123});
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn(TestService.class);

        aspect.logMethodExecution(pjp);

        List<ILoggingEvent> events = appender.list;
        assertThat(events).isNotEmpty();
        String allMessages = events.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(allMessages).contains("===\n===");
    }

    static class TestService {
        public String doWork(String arg1, int arg2) {
            return "ok";
        }
    }
}
