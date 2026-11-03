package com.alramz.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 5 DEPLOYED profiles (dev, docker, test, preprod, prod) as committed: no automated test activates them
 * (they need real env vars), so their values are asserted here directly. Placeholders are read unresolved.
 */
class DmsProfileConfigTest {

    private static final List<String> STARTER_DEFAULT_EXCLUSIONS = List.of("/actuator/**", "/swagger-ui/**", "/v3/api-docs/**");

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "test", "preprod", "prod"})
    void dmsBlock_envPlaceholdersOnly(String profile) throws IOException {
        Binder binder = binder(new ClassPathResource("application-" + profile + ".yml"));

        assertThat(string(binder, "dms.openbee.base-url")).isEqualTo("${DMS_OPENBEE_BASE_URL:}");
        assertThat(string(binder, "dms.openbee.time-zone")).isEqualTo("Asia/Dubai");
        Map<String, DmsProperties.Consumer> consumers = binder
                .bind("dms.consumers", Bindable.mapOf(String.class, DmsProperties.Consumer.class)).get();
        assertThat(consumers).containsOnlyKeys("onboarding-portal", "crm");
        assertThat(consumers.get("onboarding-portal").getApiKey()).isEqualTo("${DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL:}");
        assertThat(consumers.get("crm").getApiKey()).isEqualTo("${DMS_OPENBEE_API_KEY_CRM:}");
        assertThat(binder.bind("dms.consumer-id-header", String.class).isBound()).isFalse();
        assertThat(binder.bind("dms.upload.chunked-threshold", String.class).isBound()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "docker", "test", "preprod", "prod"})
    void securityActuatorAndLoggingHardening(String profile) throws IOException {
        Binder binder = binder(new ClassPathResource("application-" + profile + ".yml"));

        assertThat(list(binder, "company.jwt.permit-all-urls")).containsExactly("/api/v1/info");
        assertThat(string(binder, "management.endpoints.web.exposure.include")).isEqualTo("health,info,prometheus");

        assertThat(bool(binder, "company.logging.request.include-payload")).isFalse();
        assertThat(bool(binder, "company.logging.request.include-headers")).isFalse();
        assertThat(bool(binder, "company.logging.response.include-payload")).isFalse();
        assertThat(bool(binder, "company.logging.database-logging.enabled")).isTrue();
        assertThat(bool(binder, "company.logging.database-logging.include-request-payload")).isFalse();
        assertThat(bool(binder, "company.logging.database-logging.include-response-payload")).isFalse();
        assertThat(binder.bind("company.logging.database-logging.outbound-enabled", Boolean.class).isBound()).isFalse();

        assertThat(list(binder, "company.logging.excluded-paths"))
                .containsAll(STARTER_DEFAULT_EXCLUSIONS)
                .contains("/api/v1/dms/documents", "/api/v1/dms/documents/*/content");
        assertThat(list(binder, "company.logging.database-logging.excluded-paths")).containsExactly(
                "/api/v1/info", "/actuator/**", "/swagger-ui/**", "/v3/api-docs/**", "/api/v1/dms/documents/*/content");
    }

    @Test
    void baseApplicationYml_multipartLimits() throws IOException {
        // read from the module directory: the test classpath has its own application.yml that shadows it
        Binder binder = binder(new FileSystemResource("src/main/resources/application.yml"));

        assertThat(string(binder, "spring.servlet.multipart.max-file-size")).isEqualTo("${dms.upload.max-file-size:10MB}");
        assertThat(string(binder, "spring.servlet.multipart.max-request-size")).isEqualTo("11MB");
        assertThat(bool(binder, "spring.servlet.multipart.resolve-lazily")).isTrue();
        assertThat(string(binder, "spring.profiles.active")).isEqualTo("dev");
    }

    @Test
    void exclusionPatterns_matchSaveAndContentButNotSearch() {
        AntPathMatcher matcher = new AntPathMatcher();

        assertThat(matcher.match("/api/v1/dms/documents", "/api/v1/dms/documents")).isTrue();
        assertThat(matcher.match("/api/v1/dms/documents", "/api/v1/dms/documents/search")).isFalse();
        assertThat(matcher.match("/api/v1/dms/documents/*/content", "/api/v1/dms/documents/88213/content")).isTrue();
        assertThat(matcher.match("/api/v1/dms/documents/*/content", "/api/v1/dms/filing-categories")).isFalse();
    }

    private static Binder binder(Resource resource) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(resource.getFilename(), resource);
        assertThat(sources).hasSize(1);
        return new Binder(ConfigurationPropertySources.from(sources.get(0)));
    }

    private static String string(Binder binder, String name) {
        return binder.bind(name, String.class).get();
    }

    private static boolean bool(Binder binder, String name) {
        return binder.bind(name, Boolean.class).get();
    }

    private static List<String> list(Binder binder, String name) {
        return binder.bind(name, Bindable.listOf(String.class)).get();
    }
}
