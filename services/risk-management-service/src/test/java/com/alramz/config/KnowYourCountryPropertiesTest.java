package com.alramz.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** E16: the context must refuse to start without a vendor key or URL. */
class KnowYourCountryPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(KnowYourCountryProperties.class)
    static class BindingConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfig.class);

    @Test
    void shouldBindValidValues() {
        runner.withPropertyValues(
                        "knowyourcountry.base-url=https://knowyourcountry-test.example.invalid",
                        "knowyourcountry.api-key=test-api-key",
                        "knowyourcountry.request-timeout=2s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    KnowYourCountryProperties properties = context.getBean(KnowYourCountryProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://knowyourcountry-test.example.invalid");
                    assertThat(properties.getApiKey()).isEqualTo("test-api-key");
                    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(2));
                });
    }

    @Test
    void requestTimeout_shouldDefaultTo30Seconds() {
        runner.withPropertyValues(
                        "knowyourcountry.base-url=https://knowyourcountry-test.example.invalid",
                        "knowyourcountry.api-key=test-api-key")
                .run(context -> assertThat(context.getBean(KnowYourCountryProperties.class).getRequestTimeout())
                        .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void shouldFailToStartWhenApiKeyIsBlank() {
        runner.withPropertyValues(
                        "knowyourcountry.base-url=https://knowyourcountry-test.example.invalid",
                        "knowyourcountry.api-key=  ")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailToStartWhenApiKeyIsMissing() {
        runner.withPropertyValues("knowyourcountry.base-url=https://knowyourcountry-test.example.invalid")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldFailToStartWhenBaseUrlIsBlank() {
        runner.withPropertyValues("knowyourcountry.base-url=", "knowyourcountry.api-key=test-api-key")
                .run(context -> assertThat(context).hasFailed());
    }
}
