package com.alramz.finoux;

import com.alramz.config.FinouxProperties;
import com.alramz.model.CommunityPost;
import com.alramz.model.CommunityPostRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the real WebClient + resilience chain against a stubbed exchange instead of a live Finoux. */
class FinouxClientTest {

    private static final String POSTED_ROW = """
            {"isSuccess": true, "message": "OK", "data": {"table": [{
              "post_ID": 4747, "posted_User_Id": 2, "posted_By": "admin", "status": "A",
              "created_Dtm": "2025-07-24T16:40:42+00:00", "publish_dtm": "2025-07-24T16:40:42+00:00",
              "posttype": "2"}]}}
            """;

    private final AtomicReference<ClientRequest> sentRequest = new AtomicReference<>();

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private FinouxClient clientAnswering(Mono<ClientResponse> response) {
        ExchangeFunction exchange = request -> {
            sentRequest.set(request);
            return response;
        };
        FinouxProperties properties = new FinouxProperties();
        properties.setBaseUrl("https://finoux.example.invalid");
        properties.setApiKey("test-api-key");
        return new FinouxClient(
                WebClient.builder().baseUrl(properties.getBaseUrl()).exchangeFunction(exchange).build(),
                CircuitBreaker.ofDefaults("finoux-test"),
                Retry.of("finoux-test", RetryConfig.custom().maxAttempts(1).build()),
                TimeLimiter.of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build()),
                properties,
                new MockEnvironment().withProperty("spring.application.name", "utility-service"),
                new ObjectMapper());
    }

    private static Mono<ClientResponse> json(HttpStatus status, String body) {
        return Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    private static CommunityPostRequest request() {
        CommunityPostRequest request = new CommunityPostRequest(2L, "admin", "admin", "desc");
        request.setTagSymbol("1234");
        return request;
    }

    @Test
    void insertPost_shouldMapASuccessfulResponseToAPost() {
        FinouxResult result = clientAnswering(json(HttpStatus.OK, POSTED_ROW)).insertPost(request(), "<p>desc</p>");

        assertThat(result).isInstanceOf(FinouxResult.Posted.class);
        CommunityPost post = ((FinouxResult.Posted) result).post();
        assertThat(post.getPostId()).isEqualTo(4747L);
        assertThat(post.getPostedUserId()).isEqualTo(2L);
        assertThat(post.getPostedBy()).isEqualTo("admin");
        assertThat(post.getStatus()).isEqualTo(CommunityPost.StatusEnum.ACTIVE);
        assertThat(post.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2025-07-24T16:40:42+00:00"));
        assertThat(post.getPostType()).isEqualTo("2");
    }

    @Test
    void insertPost_shouldSendTheApiKeyToTheMarketEndpoint() {
        clientAnswering(json(HttpStatus.OK, POSTED_ROW)).insertPost(request(), "desc");

        assertThat(sentRequest.get().url().getPath()).isEqualTo("/api/market");
        assertThat(sentRequest.get().headers().getFirst("X-API-Key")).isEqualTo("test-api-key");
    }

    @Test
    void insertPost_shouldFallBackToTheRequestedUserWhenFinouxOmitsIt() {
        String body = """
                {"isSuccess": true, "data": {"table": [{"post_ID": "4747"}]}}
                """;

        FinouxResult result = clientAnswering(json(HttpStatus.OK, body)).insertPost(request(), "desc");

        CommunityPost post = ((FinouxResult.Posted) result).post();
        assertThat(post.getPostId()).isEqualTo(4747L);
        assertThat(post.getPostedUserId()).isEqualTo(2L);
    }

    @Test
    void insertPost_shouldRejectWhenIsSuccessIsFalse() {
        String body = """
                {"isSuccess": false, "message": "Business rule failed"}
                """;

        FinouxResult result = clientAnswering(json(HttpStatus.OK, body)).insertPost(request(), "desc");

        assertThat(result).isEqualTo(new FinouxResult.Rejected("Business rule failed"));
    }

    @Test
    void insertPost_shouldRejectWhenThePostIdIsMissing() {
        String body = """
                {"isSuccess": true, "data": {"table": []}}
                """;

        FinouxResult result = clientAnswering(json(HttpStatus.OK, body)).insertPost(request(), "desc");

        assertThat(result).isInstanceOf(FinouxResult.Rejected.class);
    }

    @Test
    void insertPost_shouldRejectUnreadableDataInsteadOfFailingWithA500() {
        String body = """
                {"isSuccess": true, "data": {"table": [{"post_ID": 1, "created_Dtm": "yesterday"}]}}
                """;

        FinouxResult result = clientAnswering(json(HttpStatus.OK, body)).insertPost(request(), "desc");

        assertThat(result).isInstanceOf(FinouxResult.Rejected.class);
        assertThat(((FinouxResult.Rejected) result).message()).startsWith("Finoux returned unreadable post data");
    }

    @Test
    void insertPost_shouldRejectAnUnknownStatus() {
        String body = """
                {"isSuccess": true, "data": {"table": [{"post_ID": 1, "status": "Z"}]}}
                """;

        FinouxResult result = clientAnswering(json(HttpStatus.OK, body)).insertPost(request(), "desc");

        assertThat(result).isInstanceOf(FinouxResult.Rejected.class);
    }

    @Test
    void insertPost_shouldReportFailedOnANon2xxStatus() {
        String body = """
                {"message": "Finoux internal error"}
                """;

        FinouxResult result = clientAnswering(json(HttpStatus.INTERNAL_SERVER_ERROR, body)).insertPost(request(), "desc");

        assertThat(result).isEqualTo(new FinouxResult.Failed(500, "Finoux internal error"));
    }

    @Test
    void insertPost_shouldReportUnavailableOnTimeout() {
        FinouxResult result = clientAnswering(Mono.never()).insertPost(request(), "desc");

        assertThat(result).isInstanceOf(FinouxResult.Unavailable.class);
    }
}
