package com.alramz.logging;

import com.alramz.logging.util.LogMaskingUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingUtilTest {

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********",
                List.of("password", "token", "authorization", "accessToken",
                        "refreshToken", "jwt", "secret", "apiKey"), List.of());
    }

    @Test
    void masksSensitiveJsonValues() {
        String in = "{\"username\":\"bob\",\"password\":\"s3cr3t\",\"token\":\"abc\"}";
        String out = LogMaskingUtil.mask(in);
        assertThat(out).contains("\"password\": \"********\"");
        assertThat(out).contains("\"token\": \"********\"");
        assertThat(out).contains("\"username\": \"bob\"");
        assertThat(out).doesNotContain("s3cr3t");
    }

    @Test
    void maskValueMasksSensitiveKeys() {
        assertThat(LogMaskingUtil.maskValue("password", "secret")).isEqualTo("********");
        assertThat(LogMaskingUtil.maskValue("name", "bob")).isEqualTo("bob");
        assertThat(LogMaskingUtil.maskValue("password", null)).isNull();
        assertThat(LogMaskingUtil.maskValue(null, "x")).isEqualTo("x");
    }

    @Test
    void masksBearerAndJwt() {
        assertThat(LogMaskingUtil.mask("Authorization: Bearer abc.def.ghi")).doesNotContain("abc.def");
        assertThat(LogMaskingUtil.mask("eyJhbA.eyJabc.def")).contains("********");
    }

    @Test
    void masksStructuredIdentifiers() {
        assertThat(LogMaskingUtil.mask("ssn=123-45-6789")).contains("********");
        assertThat(LogMaskingUtil.mask("card 4111111111111111 done")).contains("********");
    }

    @Test
    void maskHeaderMasksSensitiveHeader() {
        assertThat(LogMaskingUtil.maskHeader("Authorization", List.of("Bearer xyz"))).isEqualTo("********");
        assertThat(LogMaskingUtil.maskHeader("X-Request-Id", List.of("r1"))).isEqualTo("r1");
        assertThat(LogMaskingUtil.maskHeader("Authorization", List.of())).isEmpty();
    }

    @Test
    void isSensitiveKeyChecksSubstrings() {
        assertThat(LogMaskingUtil.isSensitiveKey("password")).isTrue();
        assertThat(LogMaskingUtil.isSensitiveKey("userPassword")).isTrue();
        assertThat(LogMaskingUtil.isSensitiveKey("name")).isFalse();
        assertThat(LogMaskingUtil.isSensitiveKey(null)).isFalse();
    }

    @Test
    void disabledMaskingReturnsOriginal() {
        LogMaskingUtil.configure(false, "********", List.of("password"), List.of());
        assertThat(LogMaskingUtil.isEnabled()).isFalse();
        String in = "{\"password\":\"secret\"}";
        assertThat(LogMaskingUtil.mask(in)).isEqualTo(in);
    }

    @Test
    void customPatternsAreApplied() {
        LogMaskingUtil.configure(true, "[REDACTED]",
                List.of("password"), List.of("\\b\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b"));
        String out = LogMaskingUtil.mask("card 1234-5678-9012-3456 ok");
        assertThat(out).contains("[REDACTED]");
        assertThat(out).doesNotContain("1234-5678-9012-3456");
    }
}
