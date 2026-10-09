package com.alramz.tracing.config;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.transport.RequestReplyReceiverContext;
import io.micrometer.observation.transport.RequestReplySenderContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;

import static org.assertj.core.api.Assertions.assertThat;

class TracingAutoConfigurationTest {

    private final MockEnvironment environment = new MockEnvironment();

    private ObservationFilter filter() {
        return new TracingAutoConfiguration().alramzSpanEnrichmentObservationFilter(environment);
    }

    private static String tag(Observation.Context context, String key) {
        KeyValue kv = context.getHighCardinalityKeyValue(key);
        return kv == null ? null : kv.getValue();
    }

    @Test
    void clientCallIsOutboundWithActiveProfile() {
        environment.setActiveProfiles("dev");
        Observation.Context context = filter().map(new RequestReplySenderContext<Object, Object>((carrier, key, value) -> { }));
        assertThat(tag(context, TracingAutoConfiguration.DIRECTION_KEY)).isEqualTo("outbound");
        assertThat(tag(context, TracingAutoConfiguration.PROFILE_KEY)).isEqualTo("dev");
    }

    @Test
    void serverRequestIsInbound() {
        Observation.Context context = filter().map(new RequestReplyReceiverContext<Object, Object>((carrier, key) -> null));
        assertThat(tag(context, TracingAutoConfiguration.DIRECTION_KEY)).isEqualTo("inbound");
        assertThat(tag(context, TracingAutoConfiguration.PROFILE_KEY)).isEqualTo("default");
    }

    @Test
    void nonHttpObservationGetsProfileButNoDirection() {
        environment.setActiveProfiles("dev", "local");
        Observation.Context context = filter().map(new Observation.Context());
        assertThat(tag(context, TracingAutoConfiguration.DIRECTION_KEY)).isNull();
        assertThat(tag(context, TracingAutoConfiguration.PROFILE_KEY)).isEqualTo("dev,local");
    }

    @Test
    void actuatorRequestsAreNotObservedButApiRequestsAre() {
        io.micrometer.observation.ObservationPredicate predicate =
                new TracingAutoConfiguration.ServletErrorRecordingConfiguration().alramzActuatorObservationPredicate();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest scrape = new MockHttpServletRequest("GET", "/actuator/prometheus");
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/v1/info");

        assertThat(predicate.test("http.server.requests", new ServerRequestObservationContext(scrape, response))).isFalse();
        assertThat(predicate.test("http.server.requests", new ServerRequestObservationContext(api, response))).isTrue();

        // Spring Security filter spans: matched through the request bound to the thread
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                new org.springframework.web.context.request.ServletRequestAttributes(scrape));
        try {
            assertThat(predicate.test("spring.security.filterchains", new Observation.Context())).isFalse();
        } finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
        assertThat(predicate.test("spring.security.filterchains", new Observation.Context())).isTrue();
        assertThat(TracingAutoConfiguration.ServletErrorRecordingConfiguration.isActuator("/actuatorx")).isFalse();
    }

    @Test
    void handledControllerExceptionIsAttachedToServerObservation() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/info");
        MockHttpServletResponse response = new MockHttpServletResponse();
        ServerRequestObservationContext context = new ServerRequestObservationContext(request, response);
        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, context);
        io.micrometer.tracing.Span serverSpan = org.mockito.Mockito.mock(io.micrometer.tracing.Span.class);
        io.micrometer.tracing.handler.TracingObservationHandler.TracingContext tracing =
                new io.micrometer.tracing.handler.TracingObservationHandler.TracingContext();
        tracing.setSpan(serverSpan);
        context.put(io.micrometer.tracing.handler.TracingObservationHandler.TracingContext.class, tracing);
        IllegalArgumentException ex = new IllegalArgumentException("Invalid UUID string: x");

        Object result = new TracingAutoConfiguration.ServerSpanErrorRecorder()
                .resolveException(request, response, null, ex);

        assertThat(result).isNull(); // controller advice still handles it
        assertThat(context.getError()).isSameAs(ex);
        org.mockito.Mockito.verify(serverSpan).error(ex); // ERROR status + stack trace on the server span
    }
}
