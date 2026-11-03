package com.alramz.controllers;

import com.alramz.jwt.service.TokenProvider;
import com.alramz.openbee.OpenBeeWireMockSupport;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * No metadata value, file name or API key reaches the application log (spec Done criterion), with logging
 * configured as the deployed profiles configure it. WireMock uses a silent notifier so its own request dumps
 * are not captured. One DMS summary line per request carries the IDs, code, duration and extension.
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.servlet.multipart.max-file-size=10MB",
        "spring.servlet.multipart.max-request-size=11MB",
        "spring.servlet.multipart.resolve-lazily=true",
        "company.logging.aspect.enabled=true",
        "company.logging.request.enabled=true",
        "company.logging.request.include-headers=false",
        "company.logging.request.include-payload=false",
        "company.logging.response.enabled=true",
        "company.logging.response.include-payload=false",
        "company.logging.database-logging.include-request-payload=false",
        "company.logging.database-logging.include-response-payload=false",
        "company.logging.excluded-paths=/actuator/**,/swagger-ui/**,/v3/api-docs/**,/api/v1/dms/documents,/api/v1/dms/documents/*/content",
        "company.logging.database-logging.excluded-paths=/api/v1/info,/actuator/**,/swagger-ui/**,/v3/api-docs/**,/api/v1/dms/documents/*/content"
})
class DmsLoggingHygieneTest {

    private static final String METADATA_VALUE = "FIT-100234";
    private static final String FILE_NAME = "passport.pdf";

    static final WireMockServer OPEN_BEE = start();

    @DynamicPropertySource
    static void openBee(DynamicPropertyRegistry registry) {
        registry.add("dms.openbee.base-url", OPEN_BEE::baseUrl);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TokenProvider tokenProvider;

    @AfterAll
    static void stop() {
        OPEN_BEE.stop();
    }

    @Test
    void saveAndSearch_neverLogValuesNamesOrKeys(CapturedOutput output) {
        OPEN_BEE.stubFor(get(urlPathEqualTo("/filingCategories/ws/filingCategories"))
                .willReturn(okJson(OpenBeeWireMockSupport.CATEGORIES_PAGE)));
        OPEN_BEE.stubFor(get(urlPathEqualTo("/filing/ws/list/7")).willReturn(okJson(OpenBeeWireMockSupport.LIST_7)));
        OPEN_BEE.stubFor(post(urlPathEqualTo("/browse/ws/document")).willReturn(aResponse().withStatus(201)
                .withHeader("Id-Document", "88213")));
        OPEN_BEE.stubFor(post(urlPathEqualTo("/advancedSearch/ws/advanced-search"))
                .willReturn(okJson(OpenBeeWireMockSupport.SEARCH_RESPONSE)));
        RestClient http = RestClient.builder().baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { }).build();
        String bearer = "Bearer " + tokenProvider.generateTokenPair("onboarding-portal",
                List.of("APP_DOCUMENT_MANAGEMENT_SERVICE_USER")).accessToken();

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("%PDF-1.7 synthetic".getBytes(StandardCharsets.UTF_8)))
                .filename(FILE_NAME).contentType(MediaType.APPLICATION_PDF);
        builder.part("filingCategoryId", "42");
        builder.part("fileName", FILE_NAME);
        builder.part("metadata", "{\"11329\":\"" + METADATA_VALUE + "\",\"11330\":\"Passport\"}");
        ResponseEntity<String> saved = http.post().uri("/api/v1/dms/documents")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.MULTIPART_FORM_DATA).body(builder.build())
                .retrieve().toEntity(String.class);
        ResponseEntity<String> searched = http.post().uri("/api/v1/dms/documents/search")
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .body("{\"filingCategoryId\":42,\"metadata\":{\"11329\":\"" + METADATA_VALUE + "\"}}")
                .retrieve().toEntity(String.class);
        ResponseEntity<String> rejected = http.post().uri("/api/v1/dms/documents/search")
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
                .body("{\"filingCategoryId\":42,\"metadata\":{\"11331\":\"" + METADATA_VALUE + "\"}}")
                .retrieve().toEntity(String.class);

        assertThat(saved.getStatusCode().value()).isEqualTo(201);
        assertThat(searched.getStatusCode().value()).isEqualTo(200);
        assertThat(rejected.getStatusCode().value()).isEqualTo(400);
        assertThat(rejected.getBody()).doesNotContain(METADATA_VALUE);

        // the starter logs through an AsyncAppender: wait for all three summary lines, otherwise the
        // doesNotContain checks below could pass on output that has not been written yet
        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                Arrays.stream(output.getAll().split("\\R")).filter(l -> l.contains("DMS request")).count() >= 3);
        String logs = output.getAll();
        assertThat(logs).doesNotContain(METADATA_VALUE).doesNotContain(FILE_NAME)
                .doesNotContain(OpenBeeWireMockSupport.KEY_ONBOARDING).doesNotContain(OpenBeeWireMockSupport.KEY_CRM);

        List<String> summaryLines = Arrays.stream(logs.split("\\R")).filter(l -> l.contains("DMS request")).toList();
        assertThat(summaryLines).hasSize(3);
        assertThat(summaryLines.get(0)).contains("consumerId=onboarding-portal", "operation=saveDmsDocument",
                "filingCategoryId=42", "documentId=88213", "openBeeStatus=201", "httpStatus=201", "dmsCode=null",
                "durationMs=", "extension=pdf");
        assertThat(summaryLines.get(2)).contains("operation=searchDmsDocuments", "httpStatus=400", "dmsCode=DMS105");
    }

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort()
                .notifier(new OpenBeeWireMockSupport.SilentNotifier()));
        server.start();
        return server;
    }
}
