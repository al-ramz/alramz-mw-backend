package com.alramz.logging.aspect;

import java.lang.annotation.*;

/**
 * Opt-in marker annotation. When {@code company.logging.aspect.enabled=true},
 * every method annotated with {@code @Loggable} is traced by
 * {@link MethodExecutionLoggingAspect}.
 * <p>
 * Attributes allow per-method overrides of the global aspect settings.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Loggable {
    boolean logEntry() default true;
    boolean logExit() default true;
    boolean logArgs() default true;
    boolean logReturn() default true;
    boolean logExceptions() default true;
}
