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
        

        String className = targetClass.getSimpleName();
        String methodName = method.getName();
        String fullMethod = className + "#" + methodName;

        Loggable loggable = annotationPresent ? method.getAnnotation(Loggable.class) : null;
        boolean logEntry = loggable != null ? loggable.logEntry() : helper.isLogEntry();
        boolean logExit = loggable != null ? loggable.logExit() : helper.isLogExit();
        boolean logArgs = loggable != null ? loggable.logArgs() : helper.isLogArgs();
        boolean logReturn = loggable != null ? loggable.logReturn() : helper.isLogReturn();
        boolean logExceptions = loggable != null ? loggable.logExceptions() : helper.isLogExceptions();
        int maxLength = helper.getMaxPayloadLength();
        String separator = helper.getSeparator();

        String correlationId = MDCUtil.getCorrelationId();

        // Child span per @Loggable method: masked args/return as tags, exceptions (with stack trace) recorded.
        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.nextSpan().name(fullMethod).tag("code.function", fullMethod).start();

        String args = logArgs && (span != null || logEntry && log.isInfoEnabled()) ? helper.formatArgs(pjp, maxLength) : null;
        if (span != null && args != null) {
            span.tag("method.args", args);
        }
        if (logEntry && log.isInfoEnabled()) {
            log.info("{}\n>>> ENTERING: {} [correlationId={}]\n>>> ARGS: {}\n{}",
                    separator, fullMethod, correlationId, args == null ? "[]" : args, separator);
        }

        long start = System.nanoTime();
        Object result = null;
        Throwable thrown = null;

        try (Tracer.SpanInScope scope = span == null ? null : tracer.withSpan(span)) {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            if (span != null) {
                span.error(t);
            }
            if (logExceptions && log.isErrorEnabled()) {
                log.error("{}\n<<< EXCEPTION in {} [correlationId={}]: {}\n{}",
                        separator, fullMethod, correlationId, t.getMessage(), separator, t);
            }
            throw t;
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
            String returnValue = logReturn && thrown == null && (span != null || logExit && log.isInfoEnabled())
                    ? helper.serialize(result, maxLength) : null;
            if (span != null) {
                if (returnValue != null) {
                    span.tag("method.return", returnValue);
                }
                span.end();
            }
            if (logExit && log.isInfoEnabled()) {
                log.info("{}\n<<< EXITING: {} [correlationId={}]\n<<< RETURN: {}\n<<< EXECUTION TIME: {}ms\n{}",
                        separator, fullMethod, correlationId, returnValue == null ? "[disabled]" : returnValue, elapsedMs, separator);
            }
        }
    }
}
