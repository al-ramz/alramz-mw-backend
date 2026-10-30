package com.alramz.service.impl;

import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Etisalat SMS API. The login token is cached per pod and refreshed within 60 s of its expiry, or once on a
 * 401 from send. No transport retry: a retried read timeout can deliver the same SMS twice.
 */
@Slf4j
@Component
public class EtisalatSmsClient {

    private static final String PROVIDER = "ETISALAT";
    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final long REFRESH_MARGIN_SECONDS = 60;
    private static final long EPOCH_MILLIS_THRESHOLD = 100_000_000_000L;

    private record Token(String value, Instant expiry) {
    }

    private final SmsProperties.Etisalat config;
    private final RestClient restClient;
    private final Clock clock;
    private Token token;

    @Autowired
    public EtisalatSmsClient(final SmsProperties properties) {
        this(properties, SmsServiceImpl.restClientBuilder(properties), Clock.systemUTC());
    }

    EtisalatSmsClient(final SmsProperties properties, final RestClient.Builder builder, final Clock clock) {
        this.config = properties.etisalat();
        this.restClient = builder.build();
        this.clock = clock;
    }

    /** Returns the jobId (or the recipient when absent), or throws SmsException SMS009 / SMS010. */
    public String send(final String number, final String message, final String msgCategory, final int expiryInSecs,
                       final String correlationId) {
        final Map<String, Object> body = new LinkedHashMap<>();
        body.put("recipient", number);
        body.put("msg", message);
        body.put("msgCategory", msgCategory);
        body.put("contentType", SmsProperties.CONTENT_TYPE);
        body.put("senderAddr", SmsProperties.SENDER);
        body.put("priority", SmsProperties.PRIORITY);
        body.put("clientTxnId", correlationId);
        body.put("expiryDt", EXPIRY.format(clock.instant().plusSeconds(expiryInSecs)));
        try {
            return submit(token(false), body, number);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Etisalat token rejected; refreshing once");
            try {
                return submit(token(true), body, number);
            } catch (RestClientException retry) {
                throw SmsServiceImpl.providerFailure(PROVIDER, retry);
            }
        } catch (RestClientException e) {
            throw SmsServiceImpl.providerFailure(PROVIDER, e);
        }
    }

    private String submit(final String bearer, final Map<String, Object> body, final String number) {
        final Map<?, ?> response = restClient.post()
                .uri(config.baseUrl() + "/campaigns/submissions/sms/nb")
                .header(HttpHeaders.AUTHORIZATION, "bearer " + bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
        final Object status = response == null ? null : response.get("statusCode");
        if (!"201".equals(String.valueOf(status))) {
            final String code = SmsServiceImpl.providerCode(status);
            log.warn("SMS provider rejected provider={} statusCode={}", PROVIDER, code);
            throw new SmsException("SMS009", "SMS provider rejected " + PROVIDER + " " + code);
        }
        final Object jobId = response.get("jobId");
        return jobId == null ? number : jobId.toString();
    }

    // ponytail: one lock per pod around login; per-username caching only if several Etisalat accounts appear.
    private synchronized String token(final boolean forceRefresh) {
        if (!forceRefresh && token != null
                && clock.instant().isBefore(token.expiry().minusSeconds(REFRESH_MARGIN_SECONDS))) {
            return token.value();
        }
        final Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(config.baseUrl() + "/login/user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("username", nullToEmpty(config.username()), "password", nullToEmpty(config.password())))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw SmsServiceImpl.providerFailure(PROVIDER, e);
        }
        final Object value = response == null ? null : response.get("token");
        if (value == null) {
            log.warn("SMS provider rejected provider={} login returned no token", PROVIDER);
            throw new SmsException("SMS009", "SMS provider rejected " + PROVIDER + " login");
        }
        final Object decoded = response.get("decodedValue");
        token = new Token(value.toString(), parseExpiry(decoded instanceof Map<?, ?> map ? map.get("expiryDt") : null));
        return token.value();
    }

    /**
     * expiryDt as epoch seconds/millis or an ISO instant / UTC local date-time. Unknown formats never expire
     * proactively and rely on the one-shot 401 refresh.
     */
    static Instant parseExpiry(final Object expiry) {
        if (expiry instanceof Number number) {
            final long value = number.longValue();
            return value > EPOCH_MILLIS_THRESHOLD ? Instant.ofEpochMilli(value) : Instant.ofEpochSecond(value);
        }
        if (expiry != null) {
            final String text = expiry.toString().strip();
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException notInstant) {
                try {
                    return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException ignored) {
                    log.warn("Etisalat token expiry not understood; relying on 401 refresh");
                }
            }
        }
        return Instant.MAX;
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }
}
