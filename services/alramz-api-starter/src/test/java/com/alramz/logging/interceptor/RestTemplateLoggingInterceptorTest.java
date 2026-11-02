package com.alramz.logging.interceptor;

import com.alramz.logging.config.LoggingProperties;
import com.alramz.logging.util.LogMaskingUtil;
import com.alramz.logging.util.MDCUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestTemplateLoggingInterceptorTest {

    private LoggingProperties properties;
    private RestTemplateLoggingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        LogMaskingUtil.configure(true, "********", List.of("password"), List.of());
        properties = new LoggingProperties();
        interceptor = new RestTemplateLoggingInterceptor(properties);
    }

    @AfterEach
    void tearDown() {
        MDCUtil.clear();
    }

    private static MockClientHttpRequest request() {
        return new MockClientHttpRequest(HttpMethod.POST, URI.create("http://downstream/api"));
    }

    @Test
    void propagatesCorrelationIdAndReturnsResponse() throws IOException {
        MDCUtil.putCorrelationId("corr-1");
        properties.getRequest().setIncludePayload(true);
        properties.getRequest().setMaxPayloadLength(5);
        MockClientHttpRequest request = request();
        MockClientHttpResponse response = new MockClientHttpResponse(new byte[0], HttpStatus.OK);

        ClientHttpResponse result = interceptor.intercept(request,
                "{\"password\":\"x\"}".getBytes(StandardCharsets.UTF_8), (r, b) -> response);

        assertThat(result).isSameAs(response);
        assertThat(request.getHeaders().getFirst("X-Correlation-ID")).isEqualTo("corr-1");
    }

    @Test
    void keepsExistingCorrelationHeaderAndHandlesEmptyBody() throws IOException {
        MDCUtil.putCorrelationId("corr-1");
        properties.getRequest().setIncludePayload(true);
        properties.getPerformance().setThreshold(Duration.ofMillis(-1));
        MockClientHttpRequest request = request();
        request.getHeaders().set("X-Correlation-ID", "upstream");

        interceptor.intercept(request, new byte[0], (r, b) -> new MockClientHttpResponse(new byte[0], HttpStatus.CREATED));

        assertThat(request.getHeaders().get("X-Correlation-ID")).containsExactly("upstream");
    }

    @Test
    void noCorrelationIdMeansNoHeader() throws IOException {
        MockClientHttpRequest request = request();
        properties.getPerformance().setThreshold(null);

        interceptor.intercept(request, null, (r, b) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThat(request.getHeaders().containsHeader("X-Correlation-ID")).isFalse();
    }

    @Test
    void rethrowsExecutionFailure() {
        ClientHttpRequestExecution failing = (r, b) -> {
            throw new IOException("connection reset");
        };

        assertThatThrownBy(() -> interceptor.intercept(request(), new byte[0], failing))
                .isInstanceOf(IOException.class)
                .hasMessage("connection reset");
    }
}
