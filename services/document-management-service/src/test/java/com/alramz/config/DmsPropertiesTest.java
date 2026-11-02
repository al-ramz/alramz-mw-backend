package com.alramz.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DmsPropertiesTest {

    private static final String[] VALID = {
            "dms.openbee.base-url=https://openbee.example.test",
            "dms.openbee.time-zone=Asia/Dubai",
            "dms.consumers.[onboarding-portal].api-key=test-key-onboarding",
            "dms.consumers.[crm].api-key=test-key-crm"
    };

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaults_matchTheSpecTable() {
        DmsProperties properties = new DmsProperties();

        assertThat(properties.getOpenbee().getConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getOpenbee().getReadTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(properties.getOpenbee().getTransferReadTimeout()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.getOpenbee().getRetry().getMaxAttempts()).isEqualTo(3);
        assertThat(properties.getOpenbee().getRetry().getInitialBackoff()).isEqualTo(Duration.ofMillis(500));
        assertThat(properties.getOpenbee().getUploadDateFormat()).isEqualTo("yyyy-MM-dd");
        assertThat(properties.getOpenbee().getUploadBooleanFormat()).isEqualTo(DmsProperties.UploadBooleanFormat.ONE_ZERO);
        assertThat(properties.getOpenbee().getUploadListValue()).isEqualTo(DmsProperties.UploadListValue.NAME);
        assertThat(properties.getConsumers()).isEmpty();
        assertThat(properties.getCache().getCategoriesTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getCache().getListsTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.getCache().getForcedRefreshMinInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getUpload().getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(10));
        assertThat(properties.getUpload().getTempDir()).isEqualTo(Path.of(System.getProperty("java.io.tmpdir")));
        assertThat(properties.getSearch().getDefaultPageSize()).isEqualTo(25);
        assertThat(properties.getSearch().getMaxPageSize()).isEqualTo(100);
    }

    @Test
    void noConsumerIdHeaderOrChunkedThreshold() {
        assertThat(DmsProperties.class.getDeclaredFields()).extracting("name").doesNotContain("consumerIdHeader");
        assertThat(DmsProperties.Upload.class.getDeclaredFields()).extracting("name").doesNotContain("chunkedThreshold");
    }

    @Test
    void binding_bracketedKeysVerbatimAndTypedValues() {
        Map<String, String> values = new HashMap<>(toMap(VALID));
        values.put("dms.consumers.[svc_with_underscore].api-key", "k");
        values.put("dms.openbee.connect-timeout", "2s");
        values.put("dms.openbee.upload-boolean-format", "TRUE_FALSE");
        values.put("dms.openbee.upload-list-value", "ID");
        values.put("dms.openbee.upload-date-format", "dd/MM/yyyy");
        values.put("dms.upload.max-file-size", "5MB");
        values.put("dms.upload.temp-dir", "/var/dms-spool");
        values.put("dms.search.max-page-size", "50");

        DmsProperties properties = new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate(DmsProperties.PREFIX, DmsProperties.class);

        assertThat(properties.getConsumers()).containsOnlyKeys("onboarding-portal", "crm", "svc_with_underscore");
        assertThat(properties.getConsumers().get("crm").getApiKey()).isEqualTo("test-key-crm");
        assertThat(properties.getOpenbee().getTimeZone()).isEqualTo(ZoneId.of("Asia/Dubai"));
        assertThat(properties.getOpenbee().getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.getOpenbee().getUploadBooleanFormat()).isEqualTo(DmsProperties.UploadBooleanFormat.TRUE_FALSE);
        assertThat(properties.getOpenbee().getUploadListValue()).isEqualTo(DmsProperties.UploadListValue.ID);
        assertThat(properties.getOpenbee().getUploadDateFormat()).isEqualTo("dd/MM/yyyy");
        assertThat(properties.getUpload().getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(5));
        assertThat(properties.getUpload().getTempDir()).isEqualTo(Path.of("/var/dms-spool"));
        assertThat(properties.getSearch().getMaxPageSize()).isEqualTo(50);
    }

    @Test
    void validConfiguration_starts() {
        runner.withPropertyValues(VALID).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DmsProperties.class).getConsumers()).hasSize(2);
        });
    }

    @Test
    void emptyConsumerMap_isValid() {
        runner.withPropertyValues(VALID[0], VALID[1]).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void blankConsumerKey_failsStartupNamingTheConsumerButNoKey() {
        runner.withPropertyValues(VALID[0], VALID[1], VALID[2], "dms.consumers.[crm].api-key=").run(context -> {
            assertThat(context).hasFailed();
            String message = fullMessage(context.getStartupFailure());
            assertThat(message).contains("crm").contains("consumers[crm].apiKey");
            assertThat(message).doesNotContain("test-key-onboarding");
        });
    }

    @Test
    void blankBaseUrl_failsStartup() {
        runner.withPropertyValues("dms.openbee.base-url=", VALID[1]).run(context -> {
            assertThat(context).hasFailed();
            assertThat(fullMessage(context.getStartupFailure())).contains("baseUrl");
        });
    }

    @Test
    void missingTimeZone_failsStartup() {
        runner.withPropertyValues(VALID[0]).run(context -> {
            assertThat(context).hasFailed();
            assertThat(fullMessage(context.getStartupFailure())).contains("timeZone");
        });
    }

    @Test
    void catalogProtections_haveSafeDefaults() {
        DmsProperties.Cache cache = new DmsProperties().getCache();
        assertThat(cache.getLockWait()).isEqualTo(Duration.ofSeconds(20));
        assertThat(cache.getLoadDeadline()).isEqualTo(Duration.ofMinutes(2));
        assertThat(cache.getRefreshAhead()).isEqualTo(Duration.ofMinutes(5));
        assertThat(cache.getListLoadConcurrency()).isEqualTo(8);
        assertThat(cache.isRefreshAheadShorterThanTtl()).isTrue();
    }

    @Test
    void refreshAheadNotShorterThanTtl_failsStartup() {
        runner.withPropertyValues(VALID[0], VALID[1], "dms.cache.refresh-ahead=15m").run(context -> {
            assertThat(context).hasFailed();
            assertThat(fullMessage(context.getStartupFailure())).contains("refresh-ahead must be shorter");
        });
    }

    @Test
    void listLoadConcurrencyAbove32_failsStartup() {
        runner.withPropertyValues(VALID[0], VALID[1], "dms.cache.list-load-concurrency=33")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidTimeZone_failsStartup() {
        runner.withPropertyValues(VALID[0], "dms.openbee.time-zone=Mars/Olympus").run(context -> assertThat(context).hasFailed());
    }

    private static Map<String, String> toMap(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            map.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return map;
    }

    private static String fullMessage(Throwable failure) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DmsProperties.class)
    static class PropertiesConfig {
    }
}
