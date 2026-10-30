package com.alramz.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.httpcore.AuthenticationHandler;
import com.microsoft.graph.httpcore.HttpClients;
import com.microsoft.graph.httpcore.RedirectHandler;
import com.microsoft.graph.httpcore.RetryHandler;
import com.microsoft.graph.httpcore.middlewareoption.IShouldRetry;
import com.microsoft.graph.httpcore.middlewareoption.RetryOptions;
import com.alramz.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableConfigurationProperties(GraphProperties.class)
@SuppressWarnings("PMD.AtLeastOneConstructor")
public class GraphConfig {

    private static final Logger LOG = LoggerFactory.getLogger(GraphConfig.class);

    /** Longest Graph Retry-After (seconds) we are willing to sleep for on the request thread. */
    static final long MAX_RETRY_AFTER_SECONDS = 5;

    /**
     * Retry only HTTP 429. sendMail is a non-idempotent POST, so a 503/504 retry could send a duplicate email.
     * A Retry-After that is longer than the cap, or not numeric (RetryHandler would fail on Long.parseLong for an
     * HTTP-date), stops retrying so the call fails fast with 429. Without Retry-After the handler's own backoff
     * (delay 1s, at most 2 retries) applies, so the worst case is a few seconds on the calling thread.
     */
    static final IShouldRetry RETRY_ONLY_429 = (delay, executionCount, request, response) ->
            response.code() == 429 && isRetryAfterWithinCap(response.header("Retry-After"));

    static final RetryOptions RETRY_OPTIONS = new RetryOptions(RETRY_ONLY_429, 2, 1);

    @Bean
    @ConditionalOnProperty(name = "app.email.service-provider", havingValue = "MICROSOFT_GRAPH")
    public GraphServiceClient<?> graphServiceClient(final GraphProperties properties) {
        final String tenantId = StringUtils.requireNonBlank(properties.tenantId(), "tenant-id");
        final String clientId = StringUtils.requireNonBlank(properties.clientId(), "client-id");
        final String clientSecret = StringUtils.requireNonBlank(properties.clientSecret(), "client-secret");

        final String scopes = properties.graphScopes() != null && !properties.graphScopes().isEmpty()
                ? properties.graphScopes()
                : "https://graph.microsoft.com/.default";

        if (LOG.isInfoEnabled()) {
            LOG.info("Configuring GraphServiceClient");
        }

        final ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .tenantId(tenantId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();

        final TokenCredentialAuthProvider authProvider =
                new TokenCredentialAuthProvider(
                        List.of(scopes),
                        credential);

        final GraphServiceClient<?> graphClient = GraphServiceClient
                .builder()
                .authenticationProvider(authProvider)
                .httpClient(HttpClients.custom()
                        .addInterceptor(new AuthenticationHandler(authProvider))
                        .addInterceptor(new RetryHandler(RETRY_OPTIONS))
                        .addInterceptor(new RedirectHandler())
                        .build())
                .buildClient();

        if (LOG.isInfoEnabled()) {
            LOG.info("=========== GraphServiceClient configured successfully =============");
        }
        return graphClient;
    }

    private static boolean isRetryAfterWithinCap(final String retryAfter) {
        if (retryAfter == null) {
            return true;
        }
        final Long seconds = parseRetryAfterSeconds(retryAfter);
        return seconds != null && seconds <= MAX_RETRY_AFTER_SECONDS;
    }

    /**
     * Parses a Retry-After value given in seconds. Tolerates the SDK's list formatting ("[5]").
     * Returns null for a missing, blank, negative or non-numeric (HTTP-date) value.
     */
    public static Long parseRetryAfterSeconds(final String retryAfter) {
        if (retryAfter == null) {
            return null;
        }
        String value = retryAfter.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (value.isEmpty() || value.length() > 9 || !value.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return Long.parseLong(value);
    }
}
