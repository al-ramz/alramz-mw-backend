package com.alramz.controllers;

import com.alramz.jwt.service.TokenProvider;
import com.alramz.openbee.OpenBeeWireMockSupport;
import com.alramz.service.FilingCategoryCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real embedded server, the starter's security chain and filters, and a WireMock Open Bee. Tokens are
 * minted with the starter's TokenProvider; no profile is activated (the test classpath config supplies fake
 * keys).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        // the base application.yml is shadowed by the test copy: mirror its multipart settings here
        "spring.servlet.multipart.max-file-size=10MB",
        "spring.servlet.multipart.max-request-size=11MB",
        "spring.servlet.multipart.resolve-lazily=true",
        // mirror the deployed logging exclusions (save + content paths)
        "company.logging.excluded-paths=/actuator/**,/swagger-ui/**,/v3/api-docs/**,/api/v1/dms/documents,/api/v1/dms/documents/*/content"
})
class DmsApiEndToEndTest {

    static final String ROLE = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER";
    static final ObjectMapper JSON = new ObjectMapper();

    static final WireMockServer OPEN_BEE = startOpenBee();
    static final Path SPOOL_DIR = createSpoolDir();

    @DynamicPropertySource
    static void openBee(DynamicPropertyRegistry registry) {
        registry.add("dms.openbee.base-url", OPEN_BEE::baseUrl);
        registry.add("dms.openbee.retry.initial-backoff", () -> "10ms");
        registry.add("dms.upload.temp-dir", SPOOL_DIR::toString);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TokenProvider tokenProvider;

    @Autowired
    @Qualifier("dmsCategoryCache")
    private Cache<String, FilingCategoryCatalog.CatalogEntry> categoryCache;

    @Autowired
    @Qualifier("dmsListCache")
    private Cache<Long, FilingCategoryCatalog.ListEntry> listCache;

    private RestClient http;

    @BeforeEach
    void setUp() {
        OPEN_BEE.resetAll();
        categoryCache.invalidateAll();
        listCache.invalidateAll();
        http = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    @AfterAll
    static void stopOpenBee() {
        OPEN_BEE.stop();
    }

    // ------------------------------------------------------------------ security

    /**
     * The starter's chain has no AuthenticationEntryPoint, so Spring Security's default answers 403 with an empty
     * body (not 401 as the spec assumed). Changing it is a starter change, out of this feature's scope; this test
     * pins the real behaviour: rejected before MVC, no DMS envelope, no Open Bee call.
     */
    @Test
    void noToken_isRejectedByTheStarterBeforeReachingDms() {
        ResponseEntity<String> response = http.get().uri("/api/v1/dms/filing-categories").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
        assertThat(response.getBody()).as("no DMS envelope").isNull();
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
    }

    @Test
    void invalidToken_isRejectedByTheStarterBeforeReachingDms() {
        ResponseEntity<String> response = http.get().uri("/api/v1/dms/filing-categories")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isIn(401, 403);
        assertThat(response.getBody()).as("no DMS envelope").isNull();
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
    }

    @Test
    void tokenWithoutRole_is403WithNullErrorCode() throws Exception {
        ResponseEntity<String> response = http.get().uri("/api/v1/dms/filing-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer("onboarding-portal", "APP_SOMETHING_ELSE"))
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("errorCode").isNull()).isTrue();
        assertThat(body.path("errorMsg").asText()).isEqualTo("Access denied");
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
    }

    @Test
    void unconfiguredSubject_is403Dms109WithoutCallingOpenBee() throws Exception {
        ResponseEntity<String> response = http.get().uri("/api/v1/dms/filing-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer("unknown-consumer", ROLE))
                .retrieve().toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS109");
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
    }

    // ------------------------------------------------------------------ categories

    @Test
    void categories_endToEnd_perConsumerKeyAndCache() throws Exception {
        stubCategories();

        ResponseEntity<String> first = getCategories("onboarding-portal");
        ResponseEntity<String> again = getCategories("onboarding-portal");
        ResponseEntity<String> crm = getCategories("crm");

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        JsonNode body = JSON.readTree(first.getBody());
        assertThat(body.path("responseCode").asText()).isEqualTo("200");
        JsonNode category = body.path("response").path("filingCategories").get(0);
        assertThat(category.path("id").asLong()).isEqualTo(42);
        assertThat(category.path("path").asText()).isEqualTo("/Clients/[CLIENT_ID]");
        assertThat(category.path("fileNamePattern").asText()).isEqualTo("PASSPORT_[CLIENT_ID]");
        assertThat(category.path("metadata").get(1).path("listValues").get(0).path("name").asText()).isEqualTo("Passport");
        assertThat(category.path("metadata").get(3).path("readOnly").asBoolean()).isTrue();
        assertThat(category.path("metadata").get(1).path("allowNewValue").asBoolean()).isFalse();
        // an addNewValue list (category 43, list 8) is flagged, has no values and is never fetched
        JsonNode requestId = body.path("response").path("filingCategories").get(1).path("metadata").get(3);
        assertThat(requestId.path("id").asLong()).isEqualTo(11403);
        assertThat(requestId.path("allowNewValue").asBoolean()).isTrue();
        assertThat(requestId.path("listValues").isNull()).isTrue();
        OPEN_BEE.verify(0, getRequestedFor(urlPathEqualTo("/filing/ws/list/8")));
        // "do not expose" fields never appear (addNewValue is exposed as allowNewValue only)
        assertThat(first.getBody()).doesNotContain("idCreator", "creationDate", "reapply", "\"type\":\"Core\"", "image", "addNewValue");
        assertThat(again.getStatusCode().value()).isEqualTo(200);
        assertThat(crm.getStatusCode().value()).isEqualTo(200);

        // onboarding: 1 category page + 1 list; the second onboarding call is served from cache; crm: own page
        // (the list is shared, so crm makes no list call)
        List<String> auth = OPEN_BEE.getAllServeEvents().stream()
                .peek(e -> assertThat(e.getRequest().getHeaders().getHeader("Authorization").values()).hasSize(1))
                .map(e -> e.getRequest().getHeader("Authorization"))
                .toList();
        assertThat(auth).containsExactlyInAnyOrder("Key test-key-onboarding", "Key test-key-onboarding", "Key test-key-crm");
        assertThat(auth).noneMatch(a -> a.contains("Bearer"));
    }

    // ------------------------------------------------------------------ save

    @Test
    void save_endToEnd_returns201AndDeletesTheSpoolFile() throws Exception {
        stubCategories();
        OPEN_BEE.stubFor(post(urlPathEqualTo("/browse/ws/document")).willReturn(aResponse().withStatus(201)
                .withHeader("Id-Document", "88213").withBody("{\"Result\":\"Document added successfully\"}")));

        ResponseEntity<String> response = save("onboarding-portal", "%PDF-1.7 synthetic".getBytes(StandardCharsets.UTF_8),
                SAVE_METADATA);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getHeaders().getLocation()).isNull();
        JsonNode body = JSON.readTree(response.getBody());
        assertThat(body.path("responseCode").asText()).isEqualTo("201");
        assertThat(body.path("response").path("documentId").asLong()).isEqualTo(88213);
        assertThat(body.path("response").path("filingCategoryId").asLong()).isEqualTo(42);
        OPEN_BEE.verify(1, postRequestedFor(urlPathEqualTo("/browse/ws/document"))
                .withHeader("Authorization", equalTo("Key test-key-onboarding")));
        assertThat(SPOOL_DIR).isEmptyDirectory();
    }

    @Test
    void save_openBeeServerError_is503Dms203AfterExactlyOneAttempt() throws Exception {
        stubCategories();
        OPEN_BEE.stubFor(post(urlPathEqualTo("/browse/ws/document")).willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = save("onboarding-portal", new byte[]{1, 2, 3}, SAVE_METADATA);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS203");
        OPEN_BEE.verify(1, postRequestedFor(urlPathEqualTo("/browse/ws/document")));
        assertThat(SPOOL_DIR).isEmptyDirectory();
    }

    /**
     * Form fields sent the way Postman and browsers do (no part Content-Type, raw UTF-8 bytes) reach Open Bee
     * unchanged: an Arabic file name and metadata value, plus a free-entry list value (addNewValue) that is in
     * no list and whose list is never fetched.
     */
    @Test
    void save_formFieldsWithoutContentType_areReadAsUtf8() throws Exception {
        stubCategories();
        OPEN_BEE.stubFor(post(urlPathEqualTo("/browse/ws/document")).willReturn(aResponse().withStatus(201)
                .withHeader("Id-Document", "88214")));

        HttpResponse<String> response = rawMultipart("onboarding-portal", "%PDF-1.7".getBytes(StandardCharsets.UTF_8),
                "43", "جواز.pdf", "{\"11400\":\"عميل-1\",\"11403\":[\"9001\",\"NEW-VALUE\"]}");

        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        OPEN_BEE.verify(1, postRequestedFor(urlPathEqualTo("/browse/ws/document"))
                .withRequestBodyPart(aMultipart().withName("name").withBody(equalTo("جواز.pdf")).build())
                .withRequestBodyPart(aMultipart().withName("metadatas[11400]").withBody(equalTo("عميل-1")).build())
                .withRequestBodyPart(aMultipart().withName("metadatas[11403][]").withBody(equalTo("NEW-VALUE")).build()));
        OPEN_BEE.verify(0, getRequestedFor(urlPathEqualTo("/filing/ws/list/8")));
    }

    @Test
    void save_missingFilingCategoryId_is400Dms101() throws Exception {
        HttpResponse<String> response = rawMultipart("onboarding-portal", new byte[]{1}, null, null, null);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("errorCode").asText()).isEqualTo("DMS101");
        assertThat(body.path("errorMsg").asText()).isEqualTo("filingCategoryId is required");
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
    }

    /** MockMvc does not apply container multipart limits: this goes through the real Tomcat (E20). */
    @Test
    void save_fileOverTheContainerLimit_is413Dms107() throws Exception {
        byte[] tooLarge = new byte[10 * 1024 * 1024 + 100 * 1024];

        // A fixed Content-Length body (not chunked), as a typical client sends it; the excess over 10 MB stays
        // under Tomcat's 2 MB max-swallow-size, so the 413 response is readable (technical_gaps G3).
        HttpResponse<String> response = rawMultipart("onboarding-portal", tooLarge, "42", "passport.pdf", SAVE_METADATA);

        assertThat(response.statusCode()).isEqualTo(413);
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.path("errorCode").asText()).isEqualTo("DMS107");
        assertThat(body.path("errorMsg").asText()).isEqualTo("File exceeds the maximum size of 10 MB");
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
        assertThat(SPOOL_DIR).isEmptyDirectory();
    }

    static final String SAVE_METADATA = "{\"11329\":\"FIT-100234\",\"11330\":\"Passport\"}";

    ResponseEntity<String> save(String subject, byte[] bytes, String metadata) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(bytes)).filename("passport.pdf").contentType(MediaType.APPLICATION_PDF);
        builder.part("filingCategoryId", "42");
        builder.part("fileName", "passport.pdf");
        builder.part("metadata", metadata);
        return http.post().uri("/api/v1/dms/documents")
                .header(HttpHeaders.AUTHORIZATION, bearer(subject, ROLE))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(builder.build())
                .retrieve().toEntity(String.class);
    }

    // ------------------------------------------------------------------ search

    @Test
    void search_endToEnd_exactOpenBeeBodyAndMappedResponse() throws Exception {
        stubCategories();
        OPEN_BEE.stubFor(post(urlPathEqualTo("/advancedSearch/ws/advanced-search"))
                .withRequestBody(equalToJson(OpenBeeWireMockSupport.SEARCH_REQUEST_C4))
                .willReturn(okJson(OpenBeeWireMockSupport.SEARCH_RESPONSE)));

        ResponseEntity<String> response = search("crm",
                "{\"filingCategoryId\":42,\"metadata\":{\"11329\":\"FIT-100234\",\"11331\":\"2027-12-31\"},\"page\":1,\"pageSize\":25}");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode result = JSON.readTree(response.getBody()).path("response");
        assertThat(result.path("documents")).hasSize(1);
        JsonNode document = result.path("documents").get(0);
        assertThat(document.path("documentId").asLong()).isEqualTo(88213);
        assertThat(document.path("creationDate").asText()).isEqualTo("2026-10-28T10:15:00+04:00");
        assertThat(document.path("metadata").get(0).path("id").asLong()).isEqualTo(11329);
        assertThat(document.path("metadata").get(0).path("name").asText()).isEqualTo("CLIENT_ID");
        assertThat(result.path("totalCount").asLong()).isEqualTo(1);
        assertThat(result.path("totalPages").asInt()).isEqualTo(1);
        assertThat(response.getBody()).doesNotContain("creator", "modifiedBy", "idFolder", "idParentFolder",
                "idDocumentVersion", "folderType", "isHidden", "svc onboarding");
    }

    @Test
    void search_openBeeServerError_isRetriedThreeTimesThen503() throws Exception {
        stubCategories();
        OPEN_BEE.stubFor(post(urlPathEqualTo("/advancedSearch/ws/advanced-search")).willReturn(aResponse().withStatus(500)));

        ResponseEntity<String> response = search("crm", "{\"filingCategoryId\":42}");

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS203");
        OPEN_BEE.verify(3, postRequestedFor(urlPathEqualTo("/advancedSearch/ws/advanced-search")));
    }

    @Test
    void search_openBeeBadRequest_isNotRetried() throws Exception {
        stubCategories();
        OPEN_BEE.stubFor(post(urlPathEqualTo("/advancedSearch/ws/advanced-search")).willReturn(aResponse().withStatus(400)));

        ResponseEntity<String> response = search("crm", "{\"filingCategoryId\":42}");

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS201");
        OPEN_BEE.verify(1, postRequestedFor(urlPathEqualTo("/advancedSearch/ws/advanced-search")));
    }

    @Test
    void search_pageSizeOverMaxAndDateArray_areRejected() throws Exception {
        stubCategories();

        ResponseEntity<String> tooBig = search("crm", "{\"filingCategoryId\":42,\"pageSize\":101}");
        assertThat(tooBig.getStatusCode().value()).isEqualTo(400);
        assertThat(JSON.readTree(tooBig.getBody()).path("errorCode").asText()).isEqualTo("DMS101");
        assertThat(JSON.readTree(tooBig.getBody()).path("errorMsg").asText()).isEqualTo("pageSize must be between 1 and 100");

        ResponseEntity<String> dateArray = search("crm", "{\"filingCategoryId\":42,\"metadata\":{\"11331\":[\"2027-12-31\"]}}");
        assertThat(dateArray.getStatusCode().value()).isEqualTo(400);
        assertThat(JSON.readTree(dateArray.getBody()).path("errorCode").asText()).isEqualTo("DMS105");
        assertThat(dateArray.getBody()).doesNotContain("2027-12-31");
    }

    // ------------------------------------------------------------------ download

    @Test
    void download_nameFromContentDisposition() {
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/file/88213")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/pdf")
                .withHeader("Content-Disposition", "attachment; filename=\"PASSPORT_FIT-100234.pdf\"")
                .withBody("%PDF-1.7".getBytes(StandardCharsets.UTF_8))));
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/document/88213"))
                .willReturn(okJson("{\"document\":{\"name\":\"Info\",\"extension\":\"pdf\"}}")));

        ResponseEntity<byte[]> response = download("onboarding-portal", "88213");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"PASSPORT_FIT-100234.pdf\"; filename*=UTF-8''PASSPORT_FIT-100234.pdf");
        assertThat(response.getBody()).isEqualTo("%PDF-1.7".getBytes(StandardCharsets.UTF_8));
        // document info is always looked up first (Open Bee serves one request per key at a time); a
        // Content-Disposition from Open Bee, if it ever sends one, still wins
        OPEN_BEE.verify(1, getRequestedFor(urlPathEqualTo("/ws/v2/document/88213")));
    }

    @Test
    void download_unknownDocument_isDms108_withoutRequestingTheFile() throws Exception {
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/document/555")).willReturn(aResponse().withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\":{\"code\":\"DOCUMENT_NOTFOUND\",\"message\":\"m\"}}")));

        ResponseEntity<byte[]> response = download("onboarding-portal", "555");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS108");
        OPEN_BEE.verify(0, getRequestedFor(urlPathEqualTo("/ws/v2/file/555")));
    }

    @Test
    void download_nameFromDocumentInfo_thenDefault() {
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/file/88213")).willReturn(aResponse().withStatus(200).withBody(new byte[]{1})));
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/document/88213"))
                .willReturn(okJson("{\"document\":{\"name\":\"جواز.pdf\"}}")));

        ResponseEntity<byte[]> fromInfo = download("onboarding-portal", "88213");
        assertThat(fromInfo.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(fromInfo.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"____.pdf\"; filename*=UTF-8''%D8%AC%D9%88%D8%A7%D8%B2.pdf");

        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/document/88213")).willReturn(aResponse().withStatus(500)));
        ResponseEntity<byte[]> fallback = download("onboarding-portal", "88213");
        assertThat(fallback.getStatusCode().value()).isEqualTo(200);
        assertThat(fallback.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("filename=\"document-88213\"");
    }

    @Test
    void download_notFound_isJson404Dms108() throws Exception {
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/file/404")).willReturn(aResponse().withStatus(404)));

        ResponseEntity<byte[]> response = download("onboarding-portal", "404");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
        assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS108");
    }

    @Test
    void download_invalidDocumentId_isDms101() throws Exception {
        for (String id : List.of("0", "abc")) {
            ResponseEntity<byte[]> response = download("onboarding-portal", id);
            assertThat(response.getStatusCode().value()).isEqualTo(400);
            assertThat(JSON.readTree(response.getBody()).path("errorCode").asText()).isEqualTo("DMS101");
        }
        assertThat(OPEN_BEE.getAllServeEvents()).isEmpty();
    }

    /**
     * 64 MB streamed through the real server: read incrementally by the client, the response is chunked (no
     * Content-Length was known), which proves no ContentCachingResponseWrapper buffered it (the content path is
     * excluded from both caching filters).
     */
    @Test
    void download_64MegabytesStreamsUnbufferedAndUnchanged() throws Exception {
        byte[] large = new byte[64 * 1024 * 1024];
        new java.util.Random(42).nextBytes(large);
        byte[] expectedDigest = java.security.MessageDigest.getInstance("SHA-256").digest(large);
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/file/77")).willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/octet-stream")
                .withHeader("Content-Disposition", "attachment; filename=\"big.bin\"")
                .withBody(large).withChunkedDribbleDelay(64, 500)));
        OPEN_BEE.stubFor(get(urlPathEqualTo("/ws/v2/document/77"))
                .willReturn(okJson("{\"document\":{\"name\":\"big\",\"extension\":\"bin\"}}")));
        large = null; // NOPMD - let the test's copy go; WireMock holds the stub body

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/dms/documents/77/content"))
                .header(HttpHeaders.AUTHORIZATION, bearer("onboarding-portal", ROLE))
                .header(HttpHeaders.ACCEPT, "application/octet-stream, application/json")
                .GET().build();
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        long total = 0;
        HttpResponse<java.io.InputStream> response;
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (java.io.InputStream in = response.body()) {
                byte[] buffer = new byte[64 * 1024];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    digest.update(buffer, 0, n);
                    total += n;
                }
            }
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Length")).isEmpty();
        assertThat(total).isEqualTo(64L * 1024 * 1024);
        assertThat(digest.digest()).isEqualTo(expectedDigest);
    }

    ResponseEntity<String> search(String subject, String json) {
        return http.post().uri("/api/v1/dms/documents/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(subject, ROLE))
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .retrieve().toEntity(String.class);
    }

    ResponseEntity<byte[]> download(String subject, String documentId) {
        return http.get().uri("/api/v1/dms/documents/" + documentId + "/content")
                .header(HttpHeaders.AUTHORIZATION, bearer(subject, ROLE))
                .header(HttpHeaders.ACCEPT, "application/octet-stream, application/json")
                .retrieve().toEntity(byte[].class);
    }

    /** A multipart body as Postman/browsers build it: text fields carry no Content-Type; null fields are left out. */
    HttpResponse<String> rawMultipart(String subject, byte[] fileBytes, String filingCategoryId, String fileName,
                                      String metadata) throws Exception {
        String boundary = "DmsTestBoundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"passport.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(fileBytes);
        String[][] fields = {{"filingCategoryId", filingCategoryId}, {"fileName", fileName}, {"metadata", metadata}};
        for (String[] field : fields) {
            if (field[1] != null) {
                body.writeBytes(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field[0] + "\"\r\n\r\n"
                        + field[1]).getBytes(StandardCharsets.UTF_8));
            }
        }
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/dms/documents"))
                .header(HttpHeaders.AUTHORIZATION, bearer(subject, ROLE))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    // ------------------------------------------------------------------ helpers

    ResponseEntity<String> getCategories(String subject) {
        return http.get().uri("/api/v1/dms/filing-categories")
                .header(HttpHeaders.AUTHORIZATION, bearer(subject, ROLE))
                .retrieve().toEntity(String.class);
    }

    static void stubCategories() {
        OPEN_BEE.stubFor(get(urlPathEqualTo("/filingCategories/ws/filingCategories"))
                .willReturn(okJson(OpenBeeWireMockSupport.CATEGORIES_PAGE)));
        OPEN_BEE.stubFor(get(urlPathMatching("/filing/ws/list/7")).willReturn(okJson(OpenBeeWireMockSupport.LIST_7)));
    }

    String bearer(String subject, String role) {
        return "Bearer " + tokenProvider.generateTokenPair(subject, List.of(role)).accessToken();
    }

    private static Path createSpoolDir() {
        try {
            return Files.createTempDirectory("dms-e2e-spool");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static WireMockServer startOpenBee() {
        WireMockServer server = new WireMockServer(WireMockConfiguration.options().dynamicPort()
                .notifier(new OpenBeeWireMockSupport.SilentNotifier()));
        server.start();
        return server;
    }
}
