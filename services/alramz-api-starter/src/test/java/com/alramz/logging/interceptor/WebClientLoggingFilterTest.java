package com.alramz.logging.interceptor;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class WebClientLoggingFilterTest {

    @AfterEach
    void resetMasking() {
        LogMaskingUtil.configure(true, null, List.of(), List.of());
        reactor.core.publisher.Hooks.resetOnErrorDropped();
    }

    @Test
    void tagsClientSpanWithMaskedHeadersAndBodiesAndKeepsResponseBody() {
        LogMaskingUtil.configure(true, null, List.of("password", "authorization"), List.of());
        LoggingProperties properties = new LoggingProperties();
        properties.getRequest().setIncludeHeaders(true);
        properties.getRequest().setIncludePayload(true);
        properties.getResponse().setIncludePayload(true);

        List<Observation.Context> stopped = new CopyOnWriteArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }
        });

        // Stub transport: serialize the request body like a real connector would, then answer with JSON
        // whose body, like a real HTTP response, can be subscribed to only once.
        List<Throwable> dropped = new CopyOnWriteArrayList<>();
        reactor.core.publisher.Hooks.onErrorDropped(dropped::add);
        java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean();
        reactor.core.publisher.Flux<org.springframework.core.io.buffer.DataBuffer> singleUseBody =
                reactor.core.publisher.Flux.defer(() -> consumed.getAndSet(true)
                        ? reactor.core.publisher.Flux.error(new IllegalStateException("body can only be consumed once"))
                        : reactor.core.publisher.Flux.just(org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance
                                .wrap("{\"exists\":true,\"password\":\"resp-secret\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        ExchangeFunction transport = request -> request
                .writeTo(new MockClientHttpRequest(HttpMethod.POST, URI.create("http://etrade/IfEidExists")),
                        ExchangeStrategies.withDefaults())
                .then(reactor.core.publisher.Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(singleUseBody)
                        .build()));

        WebClient client = WebClient.builder()
                .exchangeFunction(transport)
                .observationRegistry(registry)
                .filter(new WebClientLoggingFilter(properties).filterFunction())
                .build();

        String body = client.post().uri("http://etrade/IfEidExists")
                .header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("eid", "784-1234", "password", "req-secret"))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        assertThat(body).isEqualTo("{\"exists\":true,\"password\":\"resp-secret\"}");
        assertThat(dropped).isEmpty(); // re-reading the single-use body would be dropped here
        assertThat(stopped).hasSize(1);
        Map<String, String> tags = new HashMap<>();
        for (KeyValue kv : stopped.get(0).getHighCardinalityKeyValues()) {
            tags.put(kv.getKey(), kv.getValue());
        }
        assertThat(tags.get("http.request.body")).contains("\"eid\": \"784-1234\"").doesNotContain("req-secret");
        assertThat(tags.get("http.response.body")).contains("\"exists\": true").doesNotContain("resp-secret");
        assertThat(tags.get("http.request.header.authorization")).doesNotContain("abc.def.ghi");
        assertThat(tags.get("http.request.header.content-type")).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        assertThat(tags.get("http.response.header.content-type")).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    }
}
