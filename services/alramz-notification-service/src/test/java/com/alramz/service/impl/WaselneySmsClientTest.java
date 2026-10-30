package com.alramz.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WaselneySmsClientTest {

    private static final String SEND = "https://waselney.example.com/send";
    private static final String REPORT = "https://waselney.example.com/xmlreport.aspx";
    private static final String PASSWORD = "p&ss=W0rd";

    private MockRestServiceServer server;
    private WaselneySmsClient client;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        final SmsProperties props = new SmsProperties(true, "WASELNEY", 50, 1500, Duration.ofSeconds(5), Duration.ofSeconds(10),
                null, null, new SmsProperties.Waselney(SEND, REPORT, "wuser", PASSWORD), null, null);
        final RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WaselneySmsClient(props, builder);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(WaselneySmsClient.class)).addAppender(logs);
        ((Logger) LoggerFactory.getLogger(SmsServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(WaselneySmsClient.class)).detachAppender(logs);
        ((Logger) LoggerFactory.getLogger(SmsServiceImpl.class)).detachAppender(logs);
    }

    private void assertNoSecretsLogged() {
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain("W0rd").doesNotContain("password").doesNotContain("pwd=").doesNotContain("?")
                .doesNotContain("971501234567"));
    }

    @Test
    void encodesMessageAsUcs2Hex() {
        assertThat(WaselneySmsClient.encode("Hi ع")).isEqualTo("0048006900200639");
    }

    @Test
    void sendBuildsEncodedQueryAndReturnsMessageId() {
        server.expect(requestTo(SEND + "?username=wuser&password=p%26ss%3DW0rd&type=2&dlr=0&source=AL%20RAMZ"
                        + "&destination=971501234567&message=0048006900200639"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("1701|123|abc", MediaType.TEXT_PLAIN));

        assertThat(client.send("971501234567", "Hi ع")).isEqualTo("123");
        server.verify();
    }

    @Test
    void nonSuccessCodeIsRejected() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEND))).andRespond(withSuccess("1702|x", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.send("971501234567", "m"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS009")
                .hasMessage("SMS provider rejected WASELNEY 1702");
    }

    @Test
    void unexpectedResponseBodyIsNeverEchoedIntoTheMessage() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEND))).andRespond(
                withSuccess("Bad request /send?username=u&password=secret\r\nINFO forged", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.send("971501234567", "m"))
                .isInstanceOf(SmsException.class)
                .hasMessage("SMS provider rejected WASELNEY unexpected");
    }

    @Test
    void emptyResponseIsRejected() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEND))).andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.send("971501234567", "m"))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS009");
    }

    @Test
    void successWithoutMessageIdIsRejected() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEND))).andRespond(withSuccess("1701", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.send("971501234567", "m"))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS009");
    }

    @Test
    void transportFailureIsSms010AndUrlIsNeverLogged() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(SEND))).andRespond(request -> {
            throw new ResourceAccessException("I/O error on GET request for \"" + request.getURI() + "\"",
                    new SocketTimeoutException("connect timed out"));
        });

        assertThatThrownBy(() -> client.send("971501234567", "m"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS010")
                .hasMessage("SMS provider unavailable WASELNEY SocketTimeoutException")
                .hasNoCause();
        assertThat(logs.list).isNotEmpty();
        assertNoSecretsLogged();
    }

    @Test
    void reportReturnsOneMapPerReport() {
        server.expect(requestTo(REPORT + "?uname=wuser&pwd=p%26ss%3DW0rd&usertype=0&id=88123456"))
                .andRespond(withSuccess("<DeliveryReport><DeliveryReport>"
                        + "<Report><Status>DELIVERED</Status><DeliveryDate>2026-10-26 10:15:02</DeliveryDate></Report>"
                        + "<Report Mobile=\"x\"><Status>PENDING</Status></Report>"
                        + "</DeliveryReport></DeliveryReport>", MediaType.TEXT_XML));

        final List<Map<String, String>> reports = client.report("88123456");

        assertThat(reports).containsExactly(
                Map.of("Status", "DELIVERED", "DeliveryDate", "2026-10-26 10:15:02"),
                Map.of("Mobile", "x", "Status", "PENDING"));
    }

    @Test
    void e34_noRecordTextOrNoReportsIsNotFound() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT)))
                .andRespond(withSuccess("There is no record(s) found", MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> client.report("1"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS008")
                .hasMessage("No SMS record found");

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT)))
                .andRespond(withSuccess("<DeliveryReport><DeliveryReport/></DeliveryReport>", MediaType.TEXT_XML));
        assertThatThrownBy(() -> client.report("1")).hasFieldOrPropertyWithValue("code", "SMS008");

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT))).andRespond(withSuccess());
        assertThatThrownBy(() -> client.report("1")).hasFieldOrPropertyWithValue("code", "SMS008");
    }

    @Test
    void e35_doctypeIsRejected() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT))).andRespond(withSuccess(
                "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                        + "<DeliveryReport><DeliveryReport><Report><S>&x;</S></Report></DeliveryReport></DeliveryReport>",
                MediaType.TEXT_XML));

        assertThatThrownBy(() -> client.report("1"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS009");
    }

    @Test
    void e35_unparseableOrNon2xxIsBadGateway() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT))).andRespond(withSuccess("<oops", MediaType.TEXT_XML));
        assertThatThrownBy(() -> client.report("1")).hasFieldOrPropertyWithValue("code", "SMS009");

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT))).andRespond(withServerError());
        assertThatThrownBy(() -> client.report("1"))
                .hasFieldOrPropertyWithValue("code", "SMS009").hasMessage("SMS provider rejected WASELNEY 500");

        server.reset();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> client.report("1")).hasFieldOrPropertyWithValue("code", "SMS009");
        assertNoSecretsLogged();
    }

    @Test
    void e36_reportTimeoutIsUnavailable() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(REPORT))).andRespond(request -> {
            throw new ResourceAccessException("I/O error on GET request for \"" + request.getURI() + "\"",
                    new SocketTimeoutException("Read timed out"));
        });

        assertThatThrownBy(() -> client.report("1"))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS010");
        assertNoSecretsLogged();
    }
}
