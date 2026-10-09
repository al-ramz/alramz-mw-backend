package com.alramz.tracing.config;

import com.alramz.tracing.listener.TracingQueryExecutionListener;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.ServerHttpObservationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

/**
 * JDBC span listener for the datasource-proxy wrapper, plus span enrichment (active profile, call
 * direction). Everything else (OTel SDK, OTLP export, W3C propagation, HTTP server/WebClient spans,
 * MDC traceId/spanId) comes from Spring Boot's spring-boot-starter-opentelemetry, configured via
 * management.tracing.* and management.opentelemetry.tracing.export.otlp.*.
 */
@AutoConfiguration
@ConditionalOnClass(Tracer.class)
public class TracingAutoConfiguration {

    static final String PROFILE_KEY = "spring.profiles.active";
    static final String DIRECTION_KEY = "direction";
    static final String ACTUATOR_PATH = "/actuator";

    @Bean
    TracingQueryExecutionListener tracingQueryExecutionListener(Tracer tracer) {
        return new TracingQueryExecutionListener(tracer);
    }

    /** Connects the logback OTEL appender to Boot's OpenTelemetry SDK so log records are exported over OTLP. */
    @Bean
    InitializingBean alramzOpenTelemetryLogbackInstaller(OpenTelemetry openTelemetry) {
        return () -> OpenTelemetryAppender.install(openTelemetry);
    }

    /**
     * Tags every observation-backed span with the active Spring profile, and HTTP spans with their
     * direction: inbound for server requests, outbound for client calls (WebClient/RestClient).
     * High-cardinality so the tags go to spans only and do not add dimensions to HTTP metrics.
     */
    @Bean
    ObservationFilter alramzSpanEnrichmentObservationFilter(Environment environment) {
        String[] active = environment.getActiveProfiles();
        KeyValue profile = KeyValue.of(PROFILE_KEY, active.length == 0 ? "default" : String.join(",", active));
        KeyValue inbound = KeyValue.of(DIRECTION_KEY, "inbound");
        KeyValue outbound = KeyValue.of(DIRECTION_KEY, "outbound");
        return context -> {
            context.addHighCardinalityKeyValue(profile);
            if (context instanceof RequestReplySenderContext<?, ?>) {
                context.addHighCardinalityKeyValue(outbound);
            } else if (context instanceof RequestReplyReceiverContext<?, ?>) {
                context.addHighCardinalityKeyValue(inbound);
            }
            return context;
        };
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(HandlerExceptionResolver.class)
    static class ServletErrorRecordingConfiguration {

        @Bean
        ServerSpanErrorRecorder alramzServerSpanErrorRecorder() {
            return new ServerSpanErrorRecorder();
        }

        /**
         * Drops every observation (server span, Spring Security spans, metrics) made while serving
         * {@code /actuator/**}, so Prometheus scrapes and health probes do not flood the trace backend.
         * The server request is matched on its own context, because it starts before
         * RequestContextHolder is populated; the security filter spans are matched on the bound request.
         */
        @Bean
        ObservationPredicate alramzActuatorObservationPredicate() {
            return (name, context) -> {
                HttpServletRequest request = context instanceof ServerRequestObservationContext server
                        ? server.getCarrier()
                        : RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                                ? attributes.getRequest()
                                : null;
                return request == null || !isActuator(request.getRequestURI());
            };
        }

        static boolean isActuator(String uri) {
            return uri != null && (uri.equals(ACTUATOR_PATH) || uri.startsWith(ACTUATOR_PATH + "/"));
        }
    }

    /**
     * Runs before every {@code @ControllerAdvice}: exceptions they turn into a response never reach the
     * observation filter, so the server span would otherwise show no error. Attaching it to the server
     * observation sets the {@code exception} tag; erroring the server span marks it ERROR and records the
     * exception with its stack trace. Returns {@code null} so the normal exception handlers still build
     * the response.
     */
    static final class ServerSpanErrorRecorder implements HandlerExceptionResolver, Ordered {

        @Override
        public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response,
                                             Object handler, Exception ex) {
            ServerHttpObservationFilter.findObservationContext(request).ifPresent(context -> {
                context.setError(ex);
                TracingObservationHandler.TracingContext tracing = context.get(TracingObservationHandler.TracingContext.class);
                if (tracing != null && tracing.getSpan() != null) {
                    tracing.getSpan().error(ex);
                }
            });
            return null;
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
