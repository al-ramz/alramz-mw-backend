package com.alramz.service.impl;

import com.alramz.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

/** VeriPhone number lookup (POST /v2/verify?key=&phone=, the onboarding precedent). The key is in the query: never log the URI. */
@Slf4j
@Component
public class VeriPhoneClient {

    private final SmsProperties.Veriphone config;
    private final RestClient restClient;

    @Autowired
    public VeriPhoneClient(final SmsProperties properties) {
        this(properties, SmsServiceImpl.restClientBuilder(properties));
    }

    VeriPhoneClient(final SmsProperties properties, final RestClient.Builder builder) {
        this.config = properties.veriphone();
        this.restClient = builder.build();
    }

    /** ISO country code of an international number (no '+'), or empty on any failure. */
    public Optional<String> countryCode(final String number) {
        try {
            final Map<?, ?> body = restClient.post()
                    .uri(UriComponentsBuilder.fromUriString(config.baseUrl() + "/v2/verify")
                            .queryParam("key", "{key}")
                            .queryParam("phone", "{phone}")
                            .encode()
                            .buildAndExpand(config.apiKey(), "+" + number)
                            .toUri())
                    .retrieve()
                    .body(Map.class);
            final Object code = body == null ? null : body.get("country_code");
            return code == null || code.toString().isBlank() ? Optional.empty() : Optional.of(code.toString().strip());
        } catch (RestClientException e) {
            log.warn("VeriPhone lookup failed error={}", e.getMostSpecificCause().getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
