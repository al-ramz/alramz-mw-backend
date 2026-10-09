package com.alramz.logging.interceptor;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserter;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Reads WebClient request and response bodies without taking them away from the real caller. */
public final class WebClientBodies {

    private WebClientBodies() {
    }

    /** Wraps the body inserter so the serialized request is handed to {@code sink} as it is written. */
    public static ClientRequest captureRequestBody(ClientRequest request, Consumer<String> sink) {
        BodyInserter<?, ? super ClientHttpRequest> original = request.body();
        BodyInserter<Object, ClientHttpRequest> capturing = (outputMessage, context) ->
                original.insert(new ClientHttpRequestDecorator(outputMessage) {
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        return DataBufferUtils.join(body)
                                .defaultIfEmpty(bufferFactory().wrap(new byte[0]))
                                .flatMap(buffer -> {
                                    sink.accept(buffer.toString(StandardCharsets.UTF_8));
                                    return super.writeWith(Mono.just(buffer));
                                });
                    }
                }, context);
        return ClientRequest.from(request).body(capturing).build();
    }

    /**
     * Reads the whole response body, hands it to {@code sink} as text (empty when there is none), and
     * returns a response that replays the same bytes so the caller can still decode it.
     */
    // ponytail: buffers the whole textual response in memory; fine for JSON APIs, not for large downloads
    public static Mono<ClientResponse> readBody(ClientResponse response, Consumer<String> sink) {
        return DataBufferUtils.join(response.body(BodyExtractors.toDataBuffers()))
                .map(buffer -> {
                    byte[] bytes = new byte[buffer.readableByteCount()];
                    buffer.read(bytes);
                    DataBufferUtils.release(buffer);
                    return bytes;
                })
                .defaultIfEmpty(new byte[0])
                .map(bytes -> {
                    sink.accept(new String(bytes, charsetOf(response)));
                    // Replace the already-consumed body without subscribing to it again: mutate().body(String)
                    // releases the original body first, which fails because it can be read only once.
                    return response.mutate()
                            .body(consumed -> Flux.defer(() -> Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes))))
                            .build();
                });
    }

    /** Whether a body is worth reading as text; an unknown content type counts as text. */
    public static boolean isTextual(MediaType type) {
        return type == null
                || "text".equals(type.getType())
                || MediaType.APPLICATION_JSON.isCompatibleWith(type)
                || type.getSubtype().endsWith("+json")
                || type.getSubtype().endsWith("xml");
    }

    private static Charset charsetOf(ClientResponse response) {
        return response.headers().contentType()
                .map(MediaType::getCharset)
                .orElse(StandardCharsets.UTF_8);
    }
}
