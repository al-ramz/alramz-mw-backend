package com.alramz.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.SmsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VeriPhoneClientTest {

    private static final String URL = "https://veriphone.example.com/v2/verify?key=Vk%2Fey&phone=%2B14155550100";

    private MockRestServiceServer server;
    private VeriPhoneClient client;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        final SmsProperties props = new SmsProperties(false, "ETISALAT", 50, 1500, Duration.ofSeconds(5), Duration.ofSeconds(10),
                null, null, null, new SmsProperties.Veriphone("https://veriphone.example.com", "Vk/ey"), null);
        final RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new VeriPhoneClient(props, builder);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(VeriPhoneClient.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(VeriPhoneClient.class)).detachAppender(logs);
    }

    @Test
    void returnsCountryCode() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"status\":\"success\",\"country_code\":\"US\"}", MediaType.APPLICATION_JSON));

        assertThat(client.countryCode("14155550100")).contains("US");
        server.verify();
    }

    @Test
    void missingOrBlankCountryCodeIsEmpty() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));
        assertThat(client.countryCode("14155550100")).isEmpty();

        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"country_code\":\" \"}", MediaType.APPLICATION_JSON));
        assertThat(client.countryCode("14155550100")).isEmpty();
    }

    @Test
    void failuresAreEmptyAndNeverLogTheKeyOrUrl() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(client.countryCode("14155550100")).isEmpty();

        server.reset();
        server.expect(requestTo(URL)).andRespond(request -> {
            throw new ResourceAccessException("I/O error on POST request for \"" + request.getURI() + "\"",
                    new ConnectException("refused"));
        });
        assertThat(client.countryCode("14155550100")).isEmpty();

        assertThat(logs.list).hasSize(2);
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain("Vk").doesNotContain("key=").doesNotContain("14155550100"));
    }
}
