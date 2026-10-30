package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * DFM Member API settings. Blank values never fail startup; {@code DfmMemberApiClient} checks them at call time
 * and answers 500 DFN012, so the rest of the service keeps working while DFM is unconfigured.
 */
@ConfigurationProperties(prefix = DfmMemberApiProperties.PREFIX)
public record DfmMemberApiProperties(
        String baseUrl,
        String onboardingPath,
        String memberCode,
        String headerSecurityKey,
        String bodySecurityKey,
        @DefaultValue("5") int connectTimeoutSeconds,
        @DefaultValue("60") int readTimeoutSeconds,
        @DefaultValue List<String> duplicateInProgressCodes) {

    public static final String PREFIX = "adapter.dfm-member-api";
}
