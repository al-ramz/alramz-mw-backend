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
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class EtisalatSmsClientTest {

    private static final String BASE = "https://etisalat.example.com";
    private static final String LOGIN = BASE + "/login/user";
    private static final String SEND = BASE + "/campaigns/submissions/sms/nb";
    private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
    private static final String NUMBER = "971501234567";

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(final java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    private MockRestServiceServer server;
    private EtisalatSmsClient client;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        final SmsProperties props = new SmsProperties(true, "ETISALAT", 50, 1500, Duration.ofSeconds(5), Duration.ofSeconds(10),
                null, new SmsProperties.Etisalat(BASE, "euser", "S3cretPass", List.of(), List.of()),
                new SmsProperties.Waselney("https://w", "https://w/r", "u", "p"), null, null);
        final RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EtisalatSmsClient(props, builder, clock);
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(EtisalatSmsClient.class)).addAppender(logs);
        ((Logger) LoggerFactory.getLogger(SmsServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(EtisalatSmsClient.class)).detachAppender(logs);
        ((Logger) LoggerFactory.getLogger(SmsServiceImpl.class)).detachAppender(logs);
    }

    private void expectLogin(final String token, final String expiry) {
        server.expect(requestTo(LOGIN)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("euser"))
                .andExpect(jsonPath("$.password").value("S3cretPass"))
                .andRespond(withSuccess("{\"token\":\"" + token + "\",\"decodedValue\":{\"expiryDt\":" + expiry + "}}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectSend(final String token, final String response) {
        server.expect(requestTo(SEND)).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "bearer " + token))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(response));
    }

    @Test
    void loginsThenSendsAndReturnsJobId() {
        expectLogin("tok-1", "\"2026-10-26T11:00:00Z\"");
        server.expect(requestTo(SEND))
                .andExpect(header("Authorization", "bearer tok-1"))
                .andExpect(jsonPath("$.recipient").value(NUMBER))
                .andExpect(jsonPath("$.msg").value("hello"))
                .andExpect(jsonPath("$.msgCategory").value("4.2"))
                .andExpect(jsonPath("$.contentType").value("3.1"))
                .andExpect(jsonPath("$.senderAddr").value("AL RAMZ"))
                .andExpect(jsonPath("$.priority").value(0))
                .andExpect(jsonPath("$.clientTxnId").value("cid-1"))
                .andExpect(jsonPath("$.expiryDt").value("2026-10-26T10:05:00.000Z"))
                .andRespond(withSuccess("{\"statusCode\":201,\"jobId\":\"J-77\"}", MediaType.APPLICATION_JSON));

        assertThat(client.send(NUMBER, "hello", "4.2", 300, "cid-1")).isEqualTo("J-77");
        server.verify();
    }

    @Test
    void missingJobIdFallsBackToRecipient() {
        expectLogin("tok-1", "\"2026-10-26T11:00:00Z\"");
        expectSend("tok-1", "{\"statusCode\":\"201\"}");

        assertThat(client.send(NUMBER, "hello", "4.2", 300, "cid-1")).isEqualTo(NUMBER);
    }

    @Test
    void tokenIsReusedUntilWithinSixtySecondsOfExpiry() {
        expectLogin("tok-1", "\"2026-10-26T10:10:00Z\"");
        expectSend("tok-1", "{\"statusCode\":201,\"jobId\":\"a\"}");
        expectSend("tok-1", "{\"statusCode\":201,\"jobId\":\"b\"}");
        expectLogin("tok-2", "\"2026-10-26T12:00:00Z\"");
        expectSend("tok-2", "{\"statusCode\":201,\"jobId\":\"c\"}");

        client.send(NUMBER, "m", "4.2", 300, "c1");
        now.set(NOW.plusSeconds(530));   // 70 s before expiry: still reused
        client.send(NUMBER, "m", "4.2", 300, "c2");
        now.set(NOW.plusSeconds(545));   // 55 s before expiry: refreshed
        assertThat(client.send(NUMBER, "m", "4.2", 300, "c3")).isEqualTo("c");
        server.verify();
    }

    @Test
    void epochSecondsAndMillisExpiryAreUnderstood() {
        expectLogin("tok-1", String.valueOf(NOW.plusSeconds(3600).getEpochSecond()));
        expectSend("tok-1", "{\"statusCode\":201,\"jobId\":\"a\"}");
        expectSend("tok-1", "{\"statusCode\":201,\"jobId\":\"b\"}");

        client.send(NUMBER, "m", "4.2", 300, "c1");
        client.send(NUMBER, "m", "4.2", 300, "c2");
        server.verify();

        assertThat(EtisalatSmsClient.parseExpiry(NOW.toEpochMilli())).isEqualTo(NOW);
        assertThat(EtisalatSmsClient.parseExpiry("2026-10-26T10:00:00")).isEqualTo(NOW);
        assertThat(EtisalatSmsClient.parseExpiry("not a date")).isEqualTo(Instant.MAX);
        assertThat(EtisalatSmsClient.parseExpiry(null)).isEqualTo(Instant.MAX);
    }

    @Test
    void e30_oneUnauthorizedTriggersExactlyOneRefreshAndResend() {
        expectLogin("tok-1", "\"2026-10-26T11:00:00Z\"");
        server.expect(requestTo(SEND)).andExpect(header("Authorization", "bearer tok-1"))
                .andRespond(withUnauthorizedRequest());
        expectLogin("tok-2", "\"2026-10-26T11:00:00Z\"");
        expectSend("tok-2", "{\"statusCode\":201,\"jobId\":\"J\"}");

        assertThat(client.send(NUMBER, "m", "4.2", 300, "c")).isEqualTo("J");
        server.verify();
    }

    @Test
    void e31_twoUnauthorizedIsRejected() {
        expectLogin("tok-1", "\"2026-10-26T11:00:00Z\"");
        server.expect(requestTo(SEND)).andRespond(withUnauthorizedRequest());
        expectLogin("tok-2", "\"2026-10-26T11:00:00Z\"");
        server.expect(requestTo(SEND)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.send(NUMBER, "m", "4.2", 300, "c"))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS009");
        server.verify();
    }

    @Test
    void statusOtherThan201IsRejected() {
        expectLogin("tok-1", "\"2026-10-26T11:00:00Z\"");
        expectSend("tok-1", "{\"statusCode\":400}");

        assertThatThrownBy(() -> client.send(NUMBER, "m", "4.2", 300, "c"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS009")
                .hasMessage("SMS provider rejected ETISALAT 400");
    }

    @Test
    void loginRejectedIsSms009AndLoginWithoutTokenIsRejected() {
        server.expect(requestTo(LOGIN)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.send(NUMBER, "m", "4.2", 300, "c"))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS009");

        server.reset();
        server.expect(requestTo(LOGIN)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.send(NUMBER, "m", "4.2", 300, "c"))
                .isInstanceOf(SmsException.class).hasFieldOrPropertyWithValue("code", "SMS009");
    }

    @Test
    void transportFailureIsSms010AndNeverLogsSecrets() {
        expectLogin("tok-1", "\"2026-10-26T11:00:00Z\"");
        server.expect(ExpectedCount.once(), requestTo(SEND)).andRespond(request -> {
            throw new ResourceAccessException("I/O error on POST " + SEND + "?password=S3cretPass",
                    new SocketTimeoutException("Read timed out"));
        });

        assertThatThrownBy(() -> client.send(NUMBER, "hello body", "4.2", 300, "c"))
                .isInstanceOf(SmsException.class)
                .hasFieldOrPropertyWithValue("code", "SMS010")
                .hasMessage("SMS provider unavailable ETISALAT SocketTimeoutException")
                .hasNoCause();
        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain("S3cretPass").doesNotContain("tok-1").doesNotContain(NUMBER).doesNotContain("hello body"));
    }
}
