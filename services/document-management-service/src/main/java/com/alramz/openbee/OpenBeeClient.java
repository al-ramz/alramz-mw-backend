package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.logging.constants.LoggingConstants;
import com.alramz.logging.util.MDCUtil;
import com.alramz.service.ConsumerContext;
import com.alramz.service.DmsRequestSummary;
import com.alramz.service.FileNameResolver;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.codec.CodecException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The ONLY class that knows Open Bee paths, query parameters, multipart field names and header names.
 *
 * <p>Deliberately in {@code com.alramz.openbee}, outside {@code com.alramz.client}, so the starter's
 * {@code ApiAuditAspect} never serialises its arguments (API key, metadata values, file) into
 * {@code api_audit_log}; and deliberately NOT an {@code AbstractRestClient}, which would add the caller's JWT as
 * a second {@code Authorization} header. Every call takes the consumer and sets (never adds)
 * {@code Authorization: Key <apiKey>}; there is no default or shared key. Bodies, file names, metadata values
 * and keys are never logged.
 *
 * <p>Operator order per call: request, timeout, error translation, circuit breaker (an open circuit becomes
 * {@link OpenBeeException.Kind#CIRCUIT_OPEN}, never retried), retry; then {@code block()} on the servlet thread.
 */
@Slf4j
@Component
public class OpenBeeClient {

    static final String EP_CATEGORIES = "/filingCategories/ws/filingCategories";
    static final String EP_LIST = "/filing/ws/list/{idList}";
    static final String EP_DOCUMENT = "/browse/ws/document";
    static final String EP_SEARCH = "/advancedSearch/ws/advanced-search";
    static final String EP_FILE = "/ws/v2/file/{idDocument}";
    static final String EP_DOCUMENT_INFO = "/ws/v2/document/{idDocument}";

    static final String HEADER_CHECKSUM = "OB-CHECKSUM";
    static final String HEADER_CHECKSUM_ALGO = "OB-CHECKSUM-ALGO";
    static final String CHECKSUM_ALGO = "sha256";
    static final String HEADER_ID_DOCUMENT = "Id-Document";

    static final String KEY_SCHEME = "Key ";
    static final String CORRELATION_HEADER = LoggingConstants.DEFAULT_CORRELATION_ID_HEADER;

    private static final ObjectMapper LENIENT_JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final byte[] EMPTY = new byte[0];

    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry readRetry;
    private final Retry writeRetry;
    private final TimeLimiter timeLimiter;
    private final DmsProperties properties;
    private final MetadataEncoder metadataEncoder;

    public OpenBeeClient(@Qualifier("openBeeWebClient") WebClient webClient,
                         @Qualifier("openBeeCircuitBreaker") CircuitBreaker circuitBreaker,
                         @Qualifier("openBeeReadRetry") Retry readRetry,
                         @Qualifier("openBeeWriteRetry") Retry writeRetry,
                         @Qualifier("openBeeTimeLimiter") TimeLimiter timeLimiter,
                         DmsProperties properties,
                         MetadataEncoder metadataEncoder) {
        this.webClient = webClient;
        this.circuitBreaker = circuitBreaker;
        this.readRetry = readRetry;
        this.writeRetry = writeRetry;
        this.timeLimiter = timeLimiter;
        this.properties = properties;
        this.metadataEncoder = metadataEncoder;
    }

    // ------------------------------------------------------------------ upload

    /**
     * Uploads a spooled file; returns Open Bee's {@code Id-Document}. NOT retried (a retry could store a duplicate
     * version), bounded by {@code transfer-read-timeout}. Never sends path, idParentFolder, signature or textLayer.
     */
    public long addDocument(ConsumerContext consumer, OpenBeeUpload upload) {
        Consumer<HttpHeaders> headers = headers(consumer).andThen(h -> {
            h.set(HEADER_CHECKSUM, upload.sha256Hex());
            h.set(HEADER_CHECKSUM_ALGO, CHECKSUM_ALGO);
        });
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new FileSystemResource(upload.file()))
                .filename(upload.fileName())
                .contentType(MediaTypeFactory.getMediaType(upload.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM));
        builder.part("name", upload.fileName());
        builder.part("description", upload.description());
        builder.part("type", String.valueOf(upload.filingCategoryId()));
        for (Map.Entry<String, String> field : metadataEncoder.encode(upload.metadata())) {
            builder.part(field.getKey(), field.getValue());
        }
        builder.part("overwrite", String.valueOf(upload.overwrite()));
        MultiValueMap<String, HttpEntity<?>> body = builder.build();

        Mono<ResponseEntity<Void>> call = Mono.defer(() -> webClient.post()
                        .uri(EP_DOCUMENT)
                        .headers(headers)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromMultipartData(body))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> toError(response, EP_DOCUMENT))
                        .toBodilessEntity())
                .timeout(properties.getOpenbee().getTransferReadTimeout())
                .onErrorMap(e -> translate(e, EP_DOCUMENT));
        ResponseEntity<Void> entity = execute(call, writeRetry, EP_DOCUMENT);
        if (entity == null) {
            return documentId(null);
        }
        recordStatus(entity.getStatusCode().value());
        return documentId(entity.getHeaders().getFirst(HEADER_ID_DOCUMENT));
    }

    /** {@code Id-Document} (HttpHeaders lookups are case-insensitive); missing or not a positive integer is BAD_RESPONSE. */
    static long documentId(String header) {
        if (header != null) {
            try {
                long id = Long.parseLong(header.trim());
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException e) {
                // fall through
            }
        }
        throw recordStatus(new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 201, List.of(), EP_DOCUMENT));
    }

    // ------------------------------------------------------------------ filing categories / lists

    /** One page of the categories the consumer may classify into ({@code use}), name ascending. Retried. */
    public OpenBeeCategoryPage listFilingCategories(ConsumerContext consumer, int page, int itemsPerPage) {
        Consumer<HttpHeaders> headers = headers(consumer);
        return readJson(EP_CATEGORIES, () -> webClient.get()
                .uri(builder -> builder.path(EP_CATEGORIES)
                        .queryParam("taxonomies", "Core")
                        .queryParam("permissions", "use")
                        .queryParam("itemsPerPage", itemsPerPage)
                        .queryParam("page", page)
                        .queryParam("order", "name")
                        .queryParam("direction", "ASC")
                        .build())
                .headers(headers)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> toError(response, EP_CATEGORIES))
                .bodyToMono(OpenBeeCategoryPage.class));
    }

    /** A list's values, without sublists ({@code recursive=false}). Retried. */
    public OpenBeeList getList(ConsumerContext consumer, long idList) {
        Consumer<HttpHeaders> headers = headers(consumer);
        return readJson(EP_LIST, () -> webClient.get()
                .uri(builder -> builder.path(EP_LIST).queryParam("recursive", false).build(idList))
                .headers(headers)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> toError(response, EP_LIST))
                .bodyToMono(OpenBeeList.class));
    }

    // ------------------------------------------------------------------ search

    /** Advanced Search. Retried (read). */
    public OpenBeeSearchResponse advancedSearch(ConsumerContext consumer, OpenBeeSearchRequest body) {
        Consumer<HttpHeaders> headers = headers(consumer);
        return readJson(EP_SEARCH, () -> webClient.post()
                .uri(EP_SEARCH)
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> toError(response, EP_SEARCH))
                .bodyToMono(OpenBeeSearchResponse.class));
    }

    // ------------------------------------------------------------------ download

    /**
     * Opens the document's current file. The status is mapped before any byte is read; the circuit breaker
     * and {@code transfer-read-timeout} guard the headers, then the body flux times out between buffers.
     * Never retried. The caller must consume or cancel the body.
     */
    public OpenBeeFile downloadFile(ConsumerContext consumer, long idDocument) {
        Consumer<HttpHeaders> headers = headers(consumer);
        Duration transferTimeout = properties.getOpenbee().getTransferReadTimeout();
        String correlationId = MDCUtil.getCorrelationId();
        Mono<ResponseEntity<Flux<DataBuffer>>> call = Mono.defer(() -> webClient.get()
                        // no thumb parameter: the live Open Bee treats any value but 0 (even "false") as true
                        // and then serves a PDF rendition instead of the original file
                        .uri(builder -> builder.path(EP_FILE).build(idDocument))
                        .headers(headers)
                        .accept(MediaType.ALL)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, response -> toError(response, EP_FILE))
                        .toEntityFlux(DataBuffer.class))
                .timeout(transferTimeout)
                .onErrorMap(e -> translate(e, EP_FILE));
        ResponseEntity<Flux<DataBuffer>> entity = execute(call, writeRetry, EP_FILE);
        if (entity == null || entity.getBody() == null) {
            throw recordStatus(new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 200, List.of(), EP_FILE));
        }
        recordStatus(entity.getStatusCode().value());
        HttpHeaders responseHeaders = entity.getHeaders();
        long length = responseHeaders.getContentLength();
        Flux<DataBuffer> body = entity.getBody()
                .timeout(transferTimeout)
                .doOnError(e -> log.error("Open Bee download of document {} failed after streaming started "
                        + "(correlationId={}, error={})", idDocument, correlationId, e.getClass().getSimpleName()));
        return new OpenBeeFile(responseHeaders.getContentType(), length >= 0 ? length : null,
                fileName(responseHeaders.getFirst(HttpHeaders.CONTENT_DISPOSITION)), body);
    }

    /** The file name from a Content-Disposition header (filename* preferred), if any. */
    static Optional<String> fileName(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isBlank()) {
            return Optional.empty();
        }
        try {
            String name = ContentDisposition.parse(contentDisposition).getFilename();
            return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * The document's name from REST v2 document info, with its extension (V4: the live Open Bee sends no
     * Content-Disposition on downloads, and {@code name} comes without the extension, e.g. {@code testfile} +
     * {@code extension: txt}). Retried (read).
     */
    public Optional<String> getDocumentName(ConsumerContext consumer, long idDocument) {
        Consumer<HttpHeaders> headers = headers(consumer);
        String json = readJson(EP_DOCUMENT_INFO, () -> webClient.get()
                .uri(builder -> builder.path(EP_DOCUMENT_INFO).build(idDocument))
                .headers(headers)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> toError(response, EP_DOCUMENT_INFO))
                .bodyToMono(String.class));
        return documentName(json);
    }

    static Optional<String> documentName(String json) {
        try {
            JsonNode document = LENIENT_JSON.readTree(json).path("document");
            for (String field : List.of("name", "fileName", "filename", "title")) {
                JsonNode value = document.path(field);
                if (value.isTextual() && !value.asText().isBlank()) {
                    return Optional.of(FileNameResolver.withExtension(value.asText().trim(),
                            document.path("extension").asText("")));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 200, List.of(), EP_DOCUMENT_INFO, e);
        }
    }

    // ------------------------------------------------------------------ plumbing

    /**
     * Headers are computed on the calling (servlet) thread: the correlation ID is captured from the MDC here
     * so retries on another thread still carry it.
     */
    private Consumer<HttpHeaders> headers(ConsumerContext consumer) {
        String apiKey = consumer.apiKey();
        String correlationId = MDCUtil.getCorrelationId();
        return h -> {
            h.set(HttpHeaders.AUTHORIZATION, KEY_SCHEME + apiKey);
            if (correlationId != null) {
                h.set(CORRELATION_HEADER, correlationId);
            }
        };
    }

    private <T> T readJson(String endpoint, Supplier<Mono<T>> call) {
        Mono<T> mono = Mono.defer(call)
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .onErrorMap(e -> translate(e, endpoint));
        T result = execute(mono, readRetry, endpoint);
        if (result == null) {
            throw recordStatus(new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 200, List.of(), endpoint));
        }
        return result;
    }

    /** Circuit breaker, then retry, then block; records the last Open Bee status for the summary line. */
    private <T> T execute(Mono<T> translated, Retry retry, String endpoint) {
        Mono<T> guarded = translated
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(CallNotPermittedException.class,
                        e -> new OpenBeeException(OpenBeeException.Kind.CIRCUIT_OPEN, 0, List.of(), endpoint, e))
                .transformDeferred(RetryOperator.of(retry));
        try {
            T result = guarded.block();
            recordStatus(200);
            return result;
        } catch (OpenBeeException e) {
            throw recordStatus(e);
        }
    }

    Mono<OpenBeeException> toError(ClientResponse response, String endpoint) {
        int status = response.statusCode().value();
        return response.bodyToMono(byte[].class)
                .onErrorReturn(EMPTY)
                .defaultIfEmpty(EMPTY)
                .map(body -> new OpenBeeException(OpenBeeException.Kind.HTTP_ERROR, status, errorCodes(body), endpoint));
    }

    /** Open Bee codes from {@code {"error":[{"code":..}]}}; tolerant of empty or non-JSON bodies. */
    static List<String> errorCodes(byte[] body) {
        if (body == null || body.length == 0) {
            return List.of();
        }
        try {
            OpenBeeErrorBody parsed = LENIENT_JSON.readValue(body, OpenBeeErrorBody.class);
            List<String> codes = new ArrayList<>();
            if (parsed != null && parsed.error() != null) {
                for (OpenBeeErrorBody.Item item : parsed.error()) {
                    if (item != null && item.code() != null) {
                        codes.add(item.code());
                    }
                }
            }
            return codes;
        } catch (IOException | RuntimeException e) { // NOPMD AvoidCatchingGenericException - any parse failure
            return List.of();
        }
    }

    static Throwable translate(Throwable e, String endpoint) {
        if (e instanceof OpenBeeException || e instanceof CallNotPermittedException) {
            return e;
        }
        if (e instanceof TimeoutException) {
            return new OpenBeeException(OpenBeeException.Kind.TIMEOUT, 0, List.of(), endpoint, e);
        }
        if (e instanceof WebClientRequestException) {
            OpenBeeException.Kind kind = hasCause(e, HttpTimeoutException.class)
                    ? OpenBeeException.Kind.TIMEOUT : OpenBeeException.Kind.CONNECTION;
            return new OpenBeeException(kind, 0, List.of(), endpoint, e);
        }
        if (e instanceof CodecException || e instanceof UnsupportedMediaTypeException) {
            return new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 200, List.of(), endpoint, e);
        }
        if (hasCause(e, IOException.class)) {
            return new OpenBeeException(OpenBeeException.Kind.CONNECTION, 0, List.of(), endpoint, e);
        }
        return new OpenBeeException(OpenBeeException.Kind.BAD_RESPONSE, 0, List.of(), endpoint, e);
    }

    private static boolean hasCause(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }

    private static OpenBeeException recordStatus(OpenBeeException e) {
        recordStatus(e.getHttpStatus());
        return e;
    }

    private static void recordStatus(int status) {
        DmsRequestSummary.current().ifPresent(summary -> summary.setOpenBeeStatus(status));
    }

}
