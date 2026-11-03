package com.alramz.logging.aspect;

import com.alramz.logging.util.MethodExecutionLoggingHelper;
import com.alramz.logging.util.MDCUtil;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;

@Aspect
@Slf4j
public class MethodExecutionLoggingAspect {

    private final ObjectProvider<MethodExecutionLoggingHelper> helperProvider;
    private final ObjectProvider<Tracer> tracerProvider;

    public MethodExecutionLoggingAspect(ObjectProvider<MethodExecutionLoggingHelper> helperProvider,
                                        ObjectProvider<Tracer> tracerProvider) {
        this.helperProvider = helperProvider;
        this.tracerProvider = tracerProvider;
        log.debug("MethodExecutionLoggingAspect initialized with helperProvider");
    }

    @Around("@annotation(com.alramz.logging.aspect.Loggable)")
    public Object logMethodExecution(ProceedingJoinPoint pjp) throws Throwable {
        MethodExecutionLoggingHelper helper = helperProvider.getIfAvailable();
        if (helper == null) {
            log.debug("MethodExecutionLoggingHelper not available, skipping logging");
            return pjp.proceed();
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = signature.getDeclaringType();

        boolean annotationPresent = method.isAnnotationPresent(Loggable.class);

        // Only log if annotation is present OR if the class is not excluded
        if (!annotationPresent && helper.isExcluded(targetClass)) {
            return pjp.proceed();
        }

        log.info("Aspect intercepted: {}.{}", targetClass.getSimpleName(), method.getName());

        String fullMethod = targetClass.getSimpleName() + "#" + method.getName();
        LogOptions options = LogOptions.resolve(annotationPresent ? method.getAnnotation(Loggable.class) : null, helper);
        Invocation call = new Invocation(fullMethod, MDCUtil.getCorrelationId(), helper.getSeparator(),
                helper.getMaxPayloadLength());

        // Child span per @Loggable method: masked args/return as tags, exceptions (with stack trace) recorded.
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.nextSpan().name(fullMethod).tag("code.function", fullMethod).start();

        logEntry(pjp, helper, options, span, call);

        long start = System.nanoTime();
        Object result = null;
        Throwable thrown = null;

        try (Tracer.SpanInScope scope = span == null ? null : tracer.withSpan(span)) {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            onException(t, options, span, call);
            throw t;
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String returnValue = options.logReturn() && thrown == null && (span != null || isExitLogged(options))
                    ? helper.serialize(result, call.maxLength()) : null;
            endSpan(span, returnValue);
            if (isExitLogged(options)) {
                log.info("{}\n<<< EXITING: {} [correlationId={}]\n<<< RETURN: {}\n<<< EXECUTION TIME: {}ms\n{}",
                        call.separator(), call.fullMethod(), call.correlationId(),
                        returnValue == null ? "[disabled]" : returnValue, elapsedMs, call.separator());
            }
        }
    }

    private static void logEntry(ProceedingJoinPoint pjp, MethodExecutionLoggingHelper helper, LogOptions options,
                                 Span span, Invocation call) {
        boolean entryLogged = options.logEntry() && log.isInfoEnabled();
        String args = options.logArgs() && (span != null || entryLogged)
                ? helper.formatArgs(pjp, call.maxLength()) : null;
        if (span != null && args != null) {
            span.tag("method.args", args);
        }
        if (entryLogged) {
            log.info("{}\n>>> ENTERING: {} [correlationId={}]\n>>> ARGS: {}\n{}",
                    call.separator(), call.fullMethod(), call.correlationId(), args == null ? "[]" : args, call.separator());
        }
    }

    private static void onException(Throwable t, LogOptions options, Span span, Invocation call) {
        if (span != null) {
            span.error(t);
        }
        if (options.logExceptions() && log.isErrorEnabled()) {
            log.error("{}\n<<< EXCEPTION in {} [correlationId={}]: {}\n{}",
                    call.separator(), call.fullMethod(), call.correlationId(), t.getMessage(), call.separator(), t);
        }
    }

    private static boolean isExitLogged(LogOptions options) {
        return options.logExit() && log.isInfoEnabled();
    }

    private static void endSpan(Span span, String returnValue) {
        if (span != null) {
            if (returnValue != null) {
                span.tag("method.return", returnValue);
            }
            span.end();
        }
    }

    private record Invocation(String fullMethod, String correlationId, String separator, int maxLength) {
    }

    /** Effective logging switches: the method's {@link Loggable} wins, otherwise the configured defaults. */
    private record LogOptions(boolean logEntry, boolean logExit, boolean logArgs, boolean logReturn, boolean logExceptions) {

        static LogOptions resolve(Loggable loggable, MethodExecutionLoggingHelper helper) {
            if (loggable != null) {
                return new LogOptions(loggable.logEntry(), loggable.logExit(), loggable.logArgs(),
                        loggable.logReturn(), loggable.logExceptions());
            }
            return new LogOptions(helper.isLogEntry(), helper.isLogExit(), helper.isLogArgs(),
                    helper.isLogReturn(), helper.isLogExceptions());
        }
    }
}
