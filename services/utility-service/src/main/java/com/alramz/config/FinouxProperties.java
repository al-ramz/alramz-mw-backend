package com.alramz.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = FinouxProperties.PREFIX)
public class FinouxProperties {

    public static final String PREFIX = "adapter.finoux";

    @NotBlank
    private String baseUrl;

    private String apiKey;

    @Positive
    private int requestTimeoutSeconds = 5;

    private Post post = new Post();

    @Getter
    @Setter
    public static class Post {
        private String postedUserFlag = "ADMIN";
        private String publishFlag = "A";
        private String postTypeId = "2";
    }
}
