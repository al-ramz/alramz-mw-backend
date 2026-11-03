package com.alramz.audit;

import com.alramz.logging.util.LogMaskingUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    private static final String BASE64 = "A".repeat(60);
    private final SensitiveDataMasker masker = new SensitiveDataMasker(new ObjectMapper(), true);
    private final SensitiveDataMasker disabled = new SensitiveDataMasker(new ObjectMapper(), false);

    @BeforeEach
    void configureMasking() {
        LogMaskingUtil.configure(true, "********", List.of("password", "token"), List.of());
    }

    @Test
    @SuppressWarnings("unchecked")
    void masksSensitiveKeysRecursivelyInMapsAndCollections() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("accessToken", "abc");
        nested.put("city", "Dubai");
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("Password", "p@ss");
        input.put("api_key", 12345);
        input.put("secret", null);
        input.put("eid_attachment_front", BASE64);
        input.put("profile_image", BASE64);
        input.put("name", "Ali");
        input.put("age", 30);
        input.put("photo", "data:image/png;base64," + "x".repeat(120));
        input.put("nested", nested);
        input.put("items", List.of(Map.of("token", "t"), List.of("plain"), "  ", 5));
        input.put(null, "nullKey");

        Map<String, Object> masked = (Map<String, Object>) masker.mask(input);

        assertThat(masked)
                .containsEntry("Password", "***")
                .containsEntry("api_key", "***")
                .containsEntry("secret", null);
        assertThat((String) masked.get("eid_attachment_front")).isEqualTo("A".repeat(20) + "... [truncated]");
        assertThat((String) masked.get("profile_image")).endsWith("... [truncated]");
        assertThat(masked).containsEntry("name", "Ali").containsEntry("age", 30);
        assertThat((String) masked.get("photo")).isEqualTo("data:image/png;base6... [truncated]");
        assertThat((Map<String, Object>) masked.get("nested"))
                .containsEntry("accessToken", "***").containsEntry("city", "Dubai");
        List<Object> items = new ArrayList<>((java.util.Collection<Object>) masked.get("items"));
        assertThat((Map<String, Object>) items.get(0)).containsEntry("token", "***");
        assertThat(items.get(1)).isEqualTo(List.of("plain"));
        assertThat(items.get(2)).isEqualTo("  ");
        assertThat(items.get(3)).isEqualTo(5);
        assertThat(masked).containsEntry("", "nullKey");
    }

    @Test
    void masksTopLevelStringsAndPassesThroughOtherTypes() {
        assertThat(masker.mask("/9j/" + "x".repeat(120))).isEqualTo("/9j/" + "x".repeat(16) + "... [truncated]");
        assertThat(masker.mask("short")).isEqualTo("short");
        assertThat(masker.mask(List.of("iVBOR" + "y".repeat(120)))).isEqualTo(List.of("iVBOR" + "y".repeat(15) + "... [truncated]"));
        assertThat(masker.mask(42)).isEqualTo(42);
        assertThat(masker.mask(null)).isNull();
    }

    @Test
    void disabledMaskerReturnsInputUntouched() {
        Map<String, String> input = Map.of("password", "p");
        assertThat(disabled.mask(input)).isSameAs(input);
        assertThat(disabled.maskBody("{\"password\":\"p\"}")).isNull();
        assertThat(disabled.maskHeaders(new MockHttpServletRequest())).isNull();
        assertThat(disabled.maskRequestBody(new MockHttpServletRequest())).isNull();
        assertThat(disabled.maskResponseBody(new ContentCachingResponseWrapper(new MockHttpServletResponse()))).isNull();
    }

    @Test
    void maskBodyMasksJsonAndTruncatesLongBodies() {
        assertThat(masker.maskBody("{\"password\":\"p\",\"user\":\"u\"}"))
                .contains("\"password\": \"********\"").contains("\"user\": \"u\"");
        String longBody = "z".repeat(6000);
        assertThat(masker.maskBody(longBody)).hasSize(5000 + "...[truncated]".length()).endsWith("...[truncated]");
        assertThat(masker.maskBody("")).isNull();
        assertThat(masker.maskBody(null)).isNull();
    }

    @Test
    void maskRequestBodyReadsCachedContent() throws Exception {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/x");
        raw.setContent("{\"token\":\"abc\"}".getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(raw, 1024);
        wrapper.getInputStream().readAllBytes();

        assertThat(masker.maskRequestBody(wrapper)).isEqualTo("{\"token\": \"********\"}");
        assertThat(masker.maskRequestBody(raw)).isNull();
        assertThat(masker.maskRequestBody(null)).isNull();
    }

    @Test
    void maskResponseBodyReadsCachedContent() throws Exception {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(new MockHttpServletResponse());
        assertThat(masker.maskResponseBody(wrapper)).isNull();
        wrapper.getOutputStream().write("{\"password\":\"x\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(masker.maskResponseBody(wrapper)).isEqualTo("{\"password\": \"********\"}");
        assertThat(masker.maskResponseBody(null)).isNull();
    }

    @Test
    void maskHeadersSerializesMaskedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Token", "secret-value");
        request.addHeader("Accept", "a");
        request.addHeader("Accept", "b");

        String json = masker.maskHeaders(request);

        assertThat(json).contains("\"X-Token\":\"********\"").contains("\"Accept\":\"a,b\"");
        assertThat(masker.maskHeaders(null)).isNull();
    }

    @Test
    void maskHeadersReturnsNullWhenContainerHasNoHeaderNames() {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public java.util.Enumeration<String> getHeaderNames() {
                return null;
            }
        };
        assertThat(masker.maskHeaders(request)).isNull();
    }
}
