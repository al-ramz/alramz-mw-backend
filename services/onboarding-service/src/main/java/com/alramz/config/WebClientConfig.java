package com.alramz.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(WebClient.Builder builder, ObjectMapper objectMapper, ExchangeFilterFunction alramzWebClientLoggingFilterFunction) {

        var decoder = new Jackson2JsonDecoder(objectMapper);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().jackson2JsonDecoder(decoder))
                .build();

        return builder
                .exchangeStrategies(strategies)
                .filter(alramzWebClientLoggingFilterFunction)
                .build();
    }
}