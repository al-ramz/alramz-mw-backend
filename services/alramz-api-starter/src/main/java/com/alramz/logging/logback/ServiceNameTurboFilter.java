package com.alramz.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.Setter;
import org.slf4j.Marker;
import org.slf4j.MDC;

/**
 * Logback {@link TurboFilter} that injects the resolved service name into the
 * MDC of every log record. This guarantees the {@code serviceName} field is
 * present on all logs (including framework startup logs emitted before any
 * request reaches the {@link com.alramz.logging.filter.CorrelationIdFilter}).
 */
public class ServiceNameTurboFilter extends TurboFilter {

    @Setter
    private String serviceName = "unknown";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable t) {
        if (serviceName != null && !serviceName.isBlank()) {
            try {
                MDC.put("ServiceName", serviceName);
            } catch (Exception ignored) { // NOPMD AvoidCatchingGenericException
                // MDC may be unavailable on some runtimes.
            }
        }
        return FilterReply.NEUTRAL;
    }
}
