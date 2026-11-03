package com.alramz.openbee;

import com.alramz.service.CatalogMetadata;
import com.alramz.service.ConsumerContext;
import com.alramz.service.MetadataTypeGroup;
import com.alramz.service.ValidatedMetadata;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Brief 11.2 save / Appendix C.3 against a WireMock Open Bee. */
class OpenBeeClientUploadTest {

    private static final ConsumerContext ONBOARDING =
            new ConsumerContext("onboarding-portal", OpenBeeWireMockSupport.KEY_ONBOARDING);
    private static final String PATH = "/browse/ws/document";
    private static final byte[] BYTES = "%PDF-1.7 synthetic passport bytes".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private OpenBeeWireMockSupport openBee;
    private Path file;
    private String sha256;

    @BeforeEach
    void setUp() throws Exception {
        openBee = new OpenBeeWireMockSupport();
        file = Files.write(tempDir.resolve("3f1c.upload"), BYTES);
        sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(BYTES));
    }

    @AfterEach
    void tearDown() {
        openBee.close();
    }

    @Test
    void addDocument_sendsExactlyTheAppendixC3Fields() {
        stubCreated("Id-Document", "88213");

        long id = openBee.client().addDocument(ONBOARDING, upload(List.of(
                single(11329, MetadataTypeGroup.TEXT, "FIT-100234"),
                single(11330, MetadataTypeGroup.LIST, new CatalogMetadata.ListValue(101, "Passport", null))), true));

        assertThat(id).isEqualTo(88213L);
        LoggedRequest request = onlyRequest();
        assertThat(request.getHeaders().getHeader("Authorization").values()).containsExactly("Key test-key-onboarding");
        assertThat(request.getHeader("OB-CHECKSUM")).isEqualTo(sha256);
        assertThat(request.getHeader("OB-CHECKSUM-ALGO")).isEqualTo("sha256");
        assertThat(request.getHeader("Content-Type")).startsWith("multipart/form-data");

        Map<String, List<String>> parts = parts(request);
        assertThat(parts.keySet()).containsExactly("file", "name", "description", "type",
                "metadatas[11329]", "metadatas[11330]", "overwrite");
        assertThat(parts.get("name")).containsExactly("passport.pdf");
        assertThat(parts.get("description")).containsExactly("passport.pdf");
        assertThat(parts.get("type")).containsExactly("42");
        assertThat(parts.get("metadatas[11329]")).containsExactly("FIT-100234");
        assertThat(parts.get("metadatas[11330]")).containsExactly("Passport");
        assertThat(parts.get("overwrite")).containsExactly("true");
        assertThat(parts).doesNotContainKeys("path", "idParentFolder", "signature", "textLayer");

        Request.Part filePart = request.getParts().stream().filter(p -> "file".equals(p.getName())).findFirst().orElseThrow();
        assertThat(filePart.getHeader("Content-Disposition").firstValue()).contains("filename=\"passport.pdf\"");
        assertThat(filePart.getBody().asBytes()).isEqualTo(BYTES);
    }

    @Test
    void multipleList_isRepeatedArrayField_andMixedCaseTextUnchanged() {
        stubCreated("Id-Document", "88214");
        CatalogMetadata products = new CatalogMetadata(11401, "PRODUCTS", "LIST", MetadataTypeGroup.LIST, false, true,
                false, null, 7L, null);

        openBee.client().addDocument(ONBOARDING, upload(List.of(
                single(11400, MetadataTypeGroup.TEXT, "FiT-100234 MiXeD"),
                new ValidatedMetadata(products, List.of(new CatalogMetadata.ListValue(101, "Passport", null),
                        new CatalogMetadata.ListValue(102, "Emirates ID", null)))), false));

        Map<String, List<String>> parts = parts(onlyRequest());
        assertThat(parts.get("metadatas[11401][]")).containsExactly("Passport", "Emirates ID");
        assertThat(parts.get("metadatas[11400]")).containsExactly("FiT-100234 MiXeD");
        assertThat(parts.get("overwrite")).containsExactly("false");
    }

    @Test
    void idDocument_isReadCaseInsensitively() {
        stubCreated("id-document", "88215");

        assertThat(openBee.client().addDocument(ONBOARDING, upload(List.of(), true))).isEqualTo(88215L);
    }

    @Test
    void missingOrInvalidIdDocument_isBadResponse() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(201)
                .withBody("{\"Result\":\"Document added successfully\"}")));

        assertThatThrownBy(() -> openBee.client().addDocument(ONBOARDING, upload(List.of(), true)))
                .isInstanceOfSatisfying(OpenBeeException.class,
                        e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.BAD_RESPONSE));
        assertThatThrownBy(() -> OpenBeeClient.documentId("abc")).isInstanceOf(OpenBeeException.class);
        assertThatThrownBy(() -> OpenBeeClient.documentId("0")).isInstanceOf(OpenBeeException.class);
        assertThat(OpenBeeClient.documentId(" 7 ")).isEqualTo(7L);
    }

    @Test
    void unauthorizedAndForbidden_areHttpErrors() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(401)
                .withBody(OpenBeeWireMockSupport.BAD_CREDENTIALS)));
        assertThatThrownBy(() -> openBee.client().addDocument(ONBOARDING, upload(List.of(), true)))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(401));

        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(403)
                .withBody(OpenBeeWireMockSupport.PERMISSION_DENIED)));
        assertThatThrownBy(() -> openBee.client().addDocument(ONBOARDING, upload(List.of(), true)))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.getObCodes()).containsExactly("PERMISSION_DENIED"));
    }

    @Test
    void serverError_isNotRetried() {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> openBee.client().addDocument(ONBOARDING, upload(List.of(), true)))
                .isInstanceOfSatisfying(OpenBeeException.class, e -> assertThat(e.getHttpStatus()).isEqualTo(500));
        openBee.server().verify(1, postRequestedFor(urlPathEqualTo(PATH)));
    }

    @Test
    void timeout_isNotRetried() {
        try (OpenBeeWireMockSupport slow = new OpenBeeWireMockSupport(Duration.ofSeconds(5), Duration.ofMillis(800))) {
            slow.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(201)
                    .withHeader("Id-Document", "1").withFixedDelay(3000)));

            assertThatThrownBy(() -> slow.client().addDocument(ONBOARDING, upload(List.of(), true)))
                    .isInstanceOfSatisfying(OpenBeeException.class,
                            e -> assertThat(e.getKind()).isEqualTo(OpenBeeException.Kind.TIMEOUT));
            slow.server().verify(1, postRequestedFor(urlPathEqualTo(PATH)));
        }
    }

    @Test
    void upload_toStringHidesNameAndValues() {
        OpenBeeUpload upload = upload(List.of(single(11329, MetadataTypeGroup.TEXT, "FIT-100234")), true);

        assertThat(upload.toString()).doesNotContain("passport").doesNotContain("FIT-100234");
    }

    // ------------------------------------------------------------------ helpers

    private void stubCreated(String header, String id) {
        openBee.server().stubFor(post(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(201)
                .withHeader(header, id).withHeader("Content-Type", "application/json")
                .withBody("{\"Result\":\"Document added successfully\"}")));
    }

    private OpenBeeUpload upload(List<ValidatedMetadata> metadata, boolean overwrite) {
        return new OpenBeeUpload(file, "passport.pdf", "passport.pdf", 42, metadata, overwrite, sha256);
    }

    private static ValidatedMetadata single(long id, MetadataTypeGroup group, Object value) {
        return new ValidatedMetadata(new CatalogMetadata(id, "M" + id, group.name(), group, false, false, false,
                null, null, null), List.of(value));
    }

    private LoggedRequest onlyRequest() {
        assertThat(openBee.server().getAllServeEvents()).hasSize(1);
        return openBee.server().getAllServeEvents().get(0).getRequest();
    }

    private static Map<String, List<String>> parts(LoggedRequest request) {
        Map<String, List<String>> parts = new LinkedHashMap<>();
        for (Request.Part part : request.getParts()) {
            parts.computeIfAbsent(part.getName(), k -> new ArrayList<>()).add(part.getBody().asString());
        }
        return parts;
    }
}
