package com.alramz.openbee;

import com.alramz.config.DmsProperties;
import com.alramz.config.OpenBeeClientConfig;
import com.alramz.service.ConsumerContext;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;
import java.util.concurrent.TimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 11.2 download / Appendix C.5 against a WireMock Open Bee. */
class OpenBeeClientDownloadTest {

    private static final ConsumerContext ONBOARDING =
            new ConsumerContext("onboarding-portal", OpenBeeWireMockSupport.KEY_ONBOARDING);
    private static final String FILE = "/ws/v2/file/88213";

    private OpenBeeWireMockSupport openBee;

    @BeforeEach
    void setUp() {
        openBee = new OpenBeeWireMockSupport();
    }

    @AfterEach
    void tearDown() {
        openBee.close();
    }

    @Test
    void downloadFile_streamsBytesUnchangedWithHeaders() throws Exception {
        byte[] bytes = new byte[300_000];
        new Random(7).nextBytes(bytes);
        openBee.server().stubFor(get(urlPathEqualTo(FILE)).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/pdf")
                .withHeader("Content-Disposition", "attachment; filename=\"PASSPORT_FIT-100234.pdf\"")
                .withBody(bytes)));

        OpenBeeFile file = openBee.client().downloadFile(ONBOARDING, 88213);

        assertThat(file.contentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(file.fileName()).contains("PASSPORT_FIT-100234.pdf");
        assertThat(file.toString()).doesNotContain("PASSPORT");
        try (InputStream in = DataBufferUtils.subscriberInputStream(file.body(), 4)) {
            assertThat(in.readAllBytes()).isEqualTo(bytes);
        }
        // no thumb parameter at all: the live Open Bee serves a PDF rendition for any value but 0, even "false"
        openBee.server().verify(getRequestedFor(urlPathEqualTo(FILE))
                .withQueryParam("thumb", absent())
                .withHeader("Authorization", equalTo("Key test-key-onboarding")));
    }

    @Test
    void downloadFile_withoutContentTypeOrDisposition() throws Exception {
        openBee.server().stubFor(get(urlPathEqualTo(FILE)).willReturn(aResponse().withStatus(200)
                .withBody("plain".getBytes(StandardCharsets.UTF_8))));

        OpenBeeFile file = openBee.client().downloadFile(ONBOARDING, 88213);

        assertThat(file.fileName()).isEmpty();
        try (InputStream in = DataBufferUtils.subscriberInputStream(file.body(), 4)) {
            assertThat(in.readAllBytes()).isEqualTo("plain".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void notFound_isMappedBeforeAnyBody() {
        openBee.server().stubFor(get(urlPathEqualTo(FILE)).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> openBee.client().downloadFile(ONBOARDING, 88213))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> {
                    assertThat(e.getHttpStatus()).isEqualTo(404);
                    assertThat(OpenBeeErrorMapper.toDms(e, OpenBeeOperation.DOWNLOAD, true).getErrorCode().code())
                            .isEqualTo("DMS108");
                });
        openBee.server().verify(1, getRequestedFor(urlPathEqualTo(FILE)));
    }

    @Test
    void serverError_isNotRetried() {
        openBee.server().stubFor(get(urlPathEqualTo(FILE)).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> openBee.client().downloadFile(ONBOARDING, 88213)).isInstanceOf(OpenBeeException.class);
        openBee.server().verify(1, getRequestedFor(urlPathEqualTo(FILE)));
    }

    /** Headers and a first buffer arrive, then the body stalls: the per-buffer timeout fails the stream (E47). */
    @Test
    void stalledBody_timesOutBetweenBuffers() {
        DmsProperties properties = OpenBeeWireMockSupport.properties("http://openbee.invalid");
        properties.getOpenbee().setTransferReadTimeout(Duration.ofMillis(300));
        OpenBeeClientConfig config = new OpenBeeClientConfig(properties);
        WebClient stalling = WebClient.builder().exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/pdf")
                .body(Flux.concat(Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(new byte[]{1, 2, 3})), Flux.never()))
                .build())).build();
        OpenBeeClient client = new OpenBeeClient(stalling, config.openBeeCircuitBreaker(CircuitBreakerRegistry.ofDefaults()),
                config.openBeeReadRetry(RetryRegistry.ofDefaults()), config.openBeeWriteRetry(RetryRegistry.ofDefaults()),
                config.openBeeTimeLimiter(TimeLimiterRegistry.ofDefaults()), properties, new MetadataEncoder(properties));

        OpenBeeFile file = client.downloadFile(ONBOARDING, 88213);

        assertThatThrownBy(() -> {
            try (InputStream in = DataBufferUtils.subscriberInputStream(file.body(), 4)) {
                in.readAllBytes();
            }
        }).hasRootCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void stalledHeaders_timeOut() {
        try (OpenBeeWireMockSupport slow = new OpenBeeWireMockSupport(Duration.ofSeconds(2), Duration.ofMillis(300))) {
            slow.server().stubFor(get(urlPathEqualTo(FILE)).willReturn(aResponse().withStatus(200)
                    .withBody(new byte[16]).withFixedDelay(2000)));

            assertThatThrownBy(() -> slow.client().downloadFile(ONBOARDING, 88213))
                    .isInstanceOfSatisfying(OpenBeeException.class,
                            e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.TIMEOUT));
        }
    }

    @Test
    void contentDispositionParsing() {
        assertThat(OpenBeeClient.fileName("attachment; filename*=UTF-8''%D8%AC%D9%88%D8%A7%D8%B2.pdf")).contains("جواز.pdf");
        assertThat(OpenBeeClient.fileName("attachment")).isEmpty();
        assertThat(OpenBeeClient.fileName("   ")).isEmpty();
        assertThat(OpenBeeClient.fileName(null)).isEmpty();
        assertThat(OpenBeeClient.fileName("attachment; filename*=bogus''x")).isEmpty();
    }

    @Test
    void getDocumentName_readsTheDocumentObject() {
        openBee.server().stubFor(get(urlPathEqualTo("/ws/v2/document/88213"))
                .willReturn(okJson("{\"document\":{\"id\":88213,\"name\":\"PASSPORT_FIT-100234.pdf\"},\"comments\":{}}")));

        assertThat(openBee.client().getDocumentName(ONBOARDING, 88213)).contains("PASSPORT_FIT-100234.pdf");
    }

    @Test
    void getDocumentName_toleratesOtherShapes() {
        assertThat(OpenBeeClient.documentName("{\"document\":{\"title\":\"T.pdf\"}}")).contains("T.pdf");
        assertThat(OpenBeeClient.documentName("{\"document\":{\"name\":\" \"}}")).isEmpty();
        assertThat(OpenBeeClient.documentName("{}")).isEmpty();
        // live Open Bee: name without the extension, extension in its own field
        assertThat(OpenBeeClient.documentName("{\"document\":{\"name\":\"testfile\",\"extension\":\"txt\"}}"))
                .contains("testfile.txt");
        assertThat(OpenBeeClient.documentName("{\"document\":{\"name\":\"A.PDF\",\"extension\":\"pdf\"}}")).contains("A.PDF");
        assertThat(OpenBeeClient.documentName("{\"document\":{\"name\":\"noext\",\"extension\":\"\"}}")).contains("noext");
        assertThatThrownBy(() -> OpenBeeClient.documentName("{not json"))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.BAD_RESPONSE));
    }

    @Test
    void getDocumentName_notFound_isHttpError() {
        openBee.server().stubFor(get(urlPathEqualTo("/ws/v2/document/88213")).willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> openBee.client().getDocumentName(ONBOARDING, 88213))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(404));
    }
}
