package com.alramz.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every shipped profile must take the JWT key from JWT_SECRET (no public fallback) and keep every send/report endpoint secured.
 * Exception: dev may fall back to the starter's public default key, a deliberate local-convenience decision (security E-1).
 */
class ProfileSecurityConfigTest {

    private static final String DEV_SECRET_WITH_FALLBACK = "${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}";

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-dev.yml", "application-docker.yml",
            "application-test.yml", "application-preprod.yml", "application-prod.yml"})
    @SuppressWarnings("unchecked")
    void jwtSecretIsRequiredAndSendIsNotPublic(final String file) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            final Map<String, Object> yaml = new Yaml().load(in);
            final Map<String, Object> jwt = (Map<String, Object>) ((Map<String, Object>) yaml.get("company")).get("jwt");
            if ("application-dev.yml".equals(file)) {
                assertThat(jwt.get("secret")).isIn("${JWT_SECRET}", DEV_SECRET_WITH_FALLBACK);
            } else {
                assertThat(jwt.get("secret")).isEqualTo("${JWT_SECRET}");
            }
            assertThat((List<String>) jwt.get("permit-all-urls")).doesNotContain("/api/v1/email/send",
                    "/api/v1/email/template/send", "/api/v1/sms/send", "/api/v1/sms/report/**",
                    "/api/v1/sms/report/{messageId}", "/api/v1/sms/**", "/api/v1/otp/send", "/api/v1/otp/validate",
                    "/api/v1/otp/configurations", "/api/v1/otp/**", "/api/v1/**");
        }
    }
}
