package com.alramz.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = KnowYourCountryProperties.PREFIX)
public class KnowYourCountryProperties {

    public static final String PREFIX = "knowyourcountry";

    @NotBlank
    private String baseUrl;

    /** Sent as {@code Authorization: ApiKey <key>}. No default: a missing key must fail startup. */
    @NotBlank
    private String apiKey;

    /** TimeLimiter bound for the whole vendor call, including the single retry. */
    private Duration requestTimeout = Duration.ofSeconds(30);
}
