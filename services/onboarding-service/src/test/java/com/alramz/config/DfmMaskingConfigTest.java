package com.alramz.config;

import com.alramz.logging.util.LogMaskingUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies every onboarding-service profile carries the DFM adapter block and the extended masking list, and that the list masks what it must. */
class DfmMaskingConfigTest {

    private static final List<String> STARTER_DEFAULTS = List.of("password", "token", "authorization", "accessToken",
            "refreshToken", "jwt", "aadhaar", "pan", "ssn", "creditCard", "cvv", "apiKey", "secret", "session", "cookie");
    private static final List<String> DFM_ADDITIONS = List.of("securitykey", "nin", "eidNumber", "eid_no", "passportNumber",
            "pp_no", "iban", "foreignAccount", "dateOfBirth", "cust_dob", "image", "attachment", "proofDocument", "guardianship", "tin");

    private boolean savedEnabled;
    private List<String> savedKeys;

    @BeforeEach
    void saveMaskingConfig() {
        savedEnabled = LogMaskingUtil.isEnabled();
        savedKeys = new ArrayList<>(LogMaskingUtil.getSensitiveKeys());
    }

    @AfterEach
    void restoreMaskingConfig() {
        // LogMaskingUtil is JVM-global static state shared with every other test in this surefire fork.
        LogMaskingUtil.configure(savedEnabled, null, savedKeys, List.of());
    }

    private static Properties load(String profile) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-" + profile + ".yml"));
        return yaml.getObject();
    }

    private static List<String> list(Properties properties, String key) {
        List<String> values = new ArrayList<>();
        for (int i = 0; properties.containsKey(key + "[" + i + "]"); i++) {
            values.add(properties.getProperty(key + "[" + i + "]"));
        }
        return values;
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "test", "preprod", "prod"})
    void everyProfile_shouldDeclareTheMaskingListAndTheDfmAdapter(String profile) {
        Properties properties = load(profile);

        List<String> keys = list(properties, "company.logging.masking.sensitive-keys");
        assertThat(keys).containsAll(STARTER_DEFAULTS).containsAll(DFM_ADDITIONS);
        assertThat(list(properties, "company.jwt.permit-all-urls")).noneMatch(url -> url.contains("/dfm/investors"));
        assertThat(properties.getProperty("adapter.dfm-member-api.connect-timeout-seconds")).isEqualTo("5");
        assertThat(properties.getProperty("adapter.dfm-member-api.onboarding-path")).isNotBlank();
        assertThat(properties.getProperty("adapter.dfm-member-api.read-timeout-seconds")).isEqualTo("60");

        if ("test".equals(profile)) {
            assertThat(properties.getProperty("adapter.dfm-member-api.base-url")).isEqualTo("https://dfm.test");
            assertThat(properties.getProperty("adapter.dfm-member-api.member-code")).isEqualTo("TEST01");
            assertThat(properties.getProperty("adapter.dfm-member-api.header-security-key")).isEqualTo("header-test-key");
            assertThat(properties.getProperty("adapter.dfm-member-api.body-security-key")).isEqualTo("body-test-key");
        } else {
            // Keys are env-var placeholders with empty defaults only; dev may default the non-secret URL and member code.
            assertThat(properties.getProperty("adapter.dfm-member-api.base-url")).startsWith("${DFM_MEMBER_API_BASE_URL:");
            assertThat(properties.getProperty("adapter.dfm-member-api.member-code")).startsWith("${DFM_MEMBER_API_MEMBER_CODE:");
            // dev may carry local fallback keys; every deployed profile leaves them empty.
            for (String key : List.of("HEADER", "BODY")) {
                String value = properties.getProperty("adapter.dfm-member-api." + key.toLowerCase() + "-security-key");
                String placeholder = "${DFM_MEMBER_API_" + key + "_SECURITY_KEY:";
                if ("dev".equals(profile)) {
                    assertThat(value).startsWith(placeholder);
                } else {
                    assertThat(value).isEqualTo(placeholder + "}");
                }
            }
            assertThat(properties.getProperty("adapter.dfm-member-api.duplicate-in-progress-codes"))
                    .isEqualTo("${DFM_MEMBER_API_DUPLICATE_IN_PROGRESS_CODES:}");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "test", "preprod", "prod"})
    void theConfiguredList_shouldMaskDfmIdentifiersButNotTradingNumbers(String profile) {
        LogMaskingUtil.configure(true, null, list(load(profile), "company.logging.masking.sensitive-keys"), null);

        String masked = LogMaskingUtil.mask("""
                {"securitykey": "K-SECRET", "eidNumber": "784-0000-0000000-0", "passportNumber": "P-SECRET",
                 "nin": "N-SECRET", "aedIban": "AE000000000000000000000", "dateOfBirth": "1990-01-15",
                 "frontImage": "IMG-SECRET", "proofDocument": "DOC-SECRET",
                 "eid_no": "784-0000-0000000-1", "pp_no": "P2-SECRET", "cust_dob": "1991-02-02", "cust_nin": "N2-SECRET",
                 "pay_aed_iban": "AE000000000000000000001", "eid_attachment_front": "ATT-SECRET",
                 "tin": "TIN-SECRET", "fatca_tin": "123456789-SECRET", "csr_tin": "CTIN-SECRET",
                 "trackingNumber": "2313258142", "cashTradingNumber": "9400000"}
                """);

        assertThat(masked).doesNotContain("SECRET", "784-0000", "AE0000", "1990-01-15", "1991-02-02");
        assertThat(masked).contains("\"trackingNumber\": \"2313258142\"", "\"cashTradingNumber\": \"9400000\"");
    }
}
