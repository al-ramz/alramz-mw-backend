package com.alramz.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ETradeTokenProvider {

    private final ETradeProperties etradeProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant cachedAt;
    private volatile long tokenTtlSeconds;

    public ETradeTokenProvider(
            @Qualifier("etradeValidationService") WebClient webClient,
            ETradeProperties etradeProperties,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.etradeProperties = etradeProperties;
        this.objectMapper = objectMapper;
        this.tokenTtlSeconds = etradeProperties.tokenTtlSeconds();
    }

    public synchronized Optional<String> getToken() {
        if (cachedToken != null && cachedAt != null) {
            long elapsed = Instant.now().getEpochSecond() - cachedAt.getEpochSecond();
            if (elapsed < tokenTtlSeconds) {
                if (log.isDebugEnabled()) {
                    log.debug("Returning cached eTrade token (age={}s)", elapsed);
                }
                return Optional.of(cachedToken);
            }
            if (log.isInfoEnabled()) {
                log.info("eTrade token expired (age={}s, ttl={}s), refreshing", elapsed, tokenTtlSeconds);
            }
        }

        return fetchAndCacheToken();
    }

    public synchronized void invalidate() {
        if (log.isInfoEnabled()) {
            log.info("Invalidating cached eTrade token");
        }
        cachedToken = null;
        cachedAt = null;
    }

    private Optional<String> fetchAndCacheToken() {
        try {
            Map<String, Object> requestBody = Map.of(
                    "Client_ID", etradeProperties.clientId(),
                    "Client_Secret", etradeProperties.clientSecret(),
                    "IMEI", "1",
                    "buildversion", "1",
                    "loginDevice", "1",
                    "OS", "1",
                    "Source", "1",
                    "Reference_No", "1"
            );

            JsonNode response = webClient.post()
                    .uri(etradeProperties.tokenPath())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(Mono.justOrEmpty(requestBody), Object.class)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (!"0".equals(response.path("Error_code").asText())) {
                String errorCode = response != null ? response.path("Error_code").asText("null") : "null response";
                if (log.isWarnEnabled()) {
                    log.warn("Failed to fetch eTrade token: Error_code={}", errorCode);
                }
                return Optional.empty();
            }

            JsonNode resData = response.path("resData");
            String accessToken = resData.path("access_token").asText(null);
            int expiresIn = resData.path("expires_in").asInt(3000);

            if (accessToken == null || accessToken.isEmpty()) {
                if (log.isWarnEnabled()) {
                    log.warn("eTrade token response missing access_token");
                }
                return Optional.empty();
            }

            long safetyMargin = 300;
            this.tokenTtlSeconds = Math.max(expiresIn - safetyMargin, 60);
            this.cachedToken = accessToken;
            this.cachedAt = Instant.now();

            if (log.isInfoEnabled()) {
                log.info("Successfully fetched and cached eTrade token (ttl={}s)", tokenTtlSeconds);
            }
            return Optional.of(accessToken);

        } catch (WebClientResponseException e) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to fetch eTrade token: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            }
            return Optional.empty();
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isWarnEnabled()) {
                log.warn("Failed to fetch eTrade token", e);
            }
            return Optional.empty();
        }
    }
}
