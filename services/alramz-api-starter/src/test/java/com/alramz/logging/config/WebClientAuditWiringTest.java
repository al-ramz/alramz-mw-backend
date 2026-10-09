package com.alramz.logging.config;

import com.alramz.audit.ApiAuditLogService;
import com.alramz.audit.SensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Outbound auditing is opt-in: services that don't set outbound-enabled must behave exactly as before. */
class WebClientAuditWiringTest {

    private final ApiAuditLogService auditLogService = mock(ApiAuditLogService.class);
    private final LoggingProperties properties = new LoggingProperties();

    private ExchangeFilterFunction filterFunction(boolean auditBeanPresent) {
        when(auditLogService.getMasker()).thenReturn(new SensitiveDataMasker(new ObjectMapper(), true));
        ObjectProvider<ApiAuditLogService> provider = auditBeanPresent
                ? new StaticListableBeanFactory(Map.of("apiAuditLogService", auditLogService)).getBeanProvider(ApiAuditLogService.class)
                : new StaticListableBeanFactory().getBeanProvider(ApiAuditLogService.class);
        return new LoggingAutoConfiguration(new StaticListableBeanFactory().getBeanProvider(LoggingProperties.class))
                .alramzWebClientLoggingFilterFunction(properties, provider,
                        new MockEnvironment().withProperty("spring.application.name", "test-service"));
    }

    private static void call(ExchangeFilterFunction filter) {
        WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK).build()))
                .filter(filter)
                .build()
                .get().uri("https://upstream.example.invalid/ping")
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Test
    void auditsOutboundCallsWhenTheServiceOptsIn() {
        properties.getDatabaseLogging().setOutboundEnabled(true);

        call(filterFunction(true));

        verify(auditLogService, timeout(2000)).log(any());
    }

    @Test
    void doesNotAuditOutboundCallsByDefault() {
        call(filterFunction(true));

        verify(auditLogService, after(300).never()).log(any());
    }

    @Test
    void doesNotAuditWhenDatabaseLoggingIsUnavailable() {
        properties.getDatabaseLogging().setOutboundEnabled(true);

        call(filterFunction(false));

        verify(auditLogService, after(300).never()).log(any());
    }
}
