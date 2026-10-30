package com.alramz.config;

import com.alramz.exception.EmailValidationException;
import com.alramz.exception.TemplatedEmailException;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RequestSizeLimitConfigTest {

    private final RequestSizeLimitConfig config =
            new RequestSizeLimitConfig(new EmailProperties(255, 10_000, 10, 3_000_000, List.of()));
    private final HandlerInterceptor interceptor = config.sizeLimitInterceptor();

    private static MockHttpServletRequest requestWithLength(final long length) {
        final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/email/send");
        if (length >= 0) {
            request.setContent(new byte[(int) length]);
        }
        return request;
    }

    @Test
    void rejectsContentLengthOverDerivedLimit() {
        assertThatThrownBy(() -> interceptor.preHandle(requestWithLength(4_262_145), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(EmailValidationException.class)
                .hasFieldOrPropertyWithValue("code", "MGE-V-008")
                .hasMessage("Attachments size limit exceeded");
    }

    @Test
    void allowsContentLengthAtLimit() throws Exception {
        assertThat(interceptor.preHandle(requestWithLength(4_262_144), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void allowsRequestWithoutContentLength() throws Exception {
        assertThat(interceptor.preHandle(requestWithLength(-1), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void templatePathRejectsWithEml007() {
        final HandlerInterceptor template = config.templateSizeLimitInterceptor();

        assertThatThrownBy(() -> template.preHandle(requestWithLength(4_262_145), new MockHttpServletResponse(), new Object()))
                .isInstanceOf(TemplatedEmailException.class)
                .hasFieldOrPropertyWithValue("code", "EML007")
                .hasMessage("Attachments size limit exceeded");
    }

    @Test
    void templatePathAllowsContentLengthAtLimit() throws Exception {
        assertThat(config.templateSizeLimitInterceptor()
                .preHandle(requestWithLength(4_262_144), new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void registersInterceptorOnSendAndTemplatePaths() {
        final InterceptorRegistry registry = mock(InterceptorRegistry.class, RETURNS_SELF);
        final org.springframework.web.servlet.config.annotation.InterceptorRegistration registration =
                mock(org.springframework.web.servlet.config.annotation.InterceptorRegistration.class, RETURNS_SELF);
        org.mockito.Mockito.when(registry.addInterceptor(any())).thenReturn(registration);

        config.addInterceptors(registry);

        verify(registration).addPathPatterns("/api/v1/email/send");
        verify(registration).addPathPatterns("/api/v1/email/template/send");
    }

    /** GraphConfig's Graph retry policy (Gate 1 amendment: retry only 429, capped wait). Same package, so kept here. */
    @Nested
    class GraphRetryPolicy {


        private static final Request REQUEST = new Request.Builder().url("https://graph.microsoft.com/v1.0/users/x/sendMail").build();

        private static Response response(final int code, final String retryAfter) {
            final Response.Builder builder = new Response.Builder().request(REQUEST).protocol(Protocol.HTTP_1_1)
                    .code(code).message("status");
            if (retryAfter != null) {
                builder.header("Retry-After", retryAfter);
            }
            return builder.build();
        }

        private static boolean shouldRetry(final int code, final String retryAfter) {
            return GraphConfig.RETRY_ONLY_429.shouldRetry(1, 1, REQUEST, response(code, retryAfter));
        }

        @Test
        void retriesOnly429() {
            assertThat(shouldRetry(429, null)).isTrue();
            assertThat(shouldRetry(503, null)).isFalse();
            assertThat(shouldRetry(504, null)).isFalse();
            assertThat(shouldRetry(500, null)).isFalse();
        }

        @Test
        void retries429OnlyWhenRetryAfterIsWithinCap() {
            assertThat(shouldRetry(429, "5")).isTrue();
            assertThat(shouldRetry(429, "0")).isTrue();
            assertThat(shouldRetry(429, "6")).isFalse();
            assertThat(shouldRetry(429, "180")).isFalse();
        }

        @Test
        void doesNotRetryOnMalformedRetryAfter() {
            assertThat(shouldRetry(429, "Wed, 21 Oct 2026 07:28:00 GMT")).isFalse();
            assertThat(shouldRetry(429, "-1")).isFalse();
            assertThat(shouldRetry(429, " ")).isFalse();
        }

        @Test
        void retryOptionsKeepRetriesAndDelaySmall() {
            assertThat(GraphConfig.RETRY_OPTIONS.maxRetries()).isEqualTo(2);
            assertThat(GraphConfig.RETRY_OPTIONS.delay()).isEqualTo(1);
            assertThat(GraphConfig.RETRY_OPTIONS.shouldRetry()).isSameAs(GraphConfig.RETRY_ONLY_429);
        }

        @Test
        void parseRetryAfterSecondsHandlesSdkAndBadFormats() {
            assertThat(GraphConfig.parseRetryAfterSeconds("30")).isEqualTo(30L);
            assertThat(GraphConfig.parseRetryAfterSeconds(" [5] ")).isEqualTo(5L);
            assertThat(GraphConfig.parseRetryAfterSeconds("[]")).isNull();
            assertThat(GraphConfig.parseRetryAfterSeconds("1234567890")).isNull();
            assertThat(GraphConfig.parseRetryAfterSeconds("Wed, 21 Oct 2026 07:28:00 GMT")).isNull();
            assertThat(GraphConfig.parseRetryAfterSeconds(null)).isNull();
        }

        @Test
        void buildsClientWithoutFetchingAToken() {
            final GraphProperties properties = new GraphProperties("tenant", "client", "secret", null);

            assertThat(new GraphConfig().graphServiceClient(properties)).isNotNull();
        }

        @Test
        void rejectsMissingCredentials() {
            assertThatThrownBy(() -> new GraphConfig().graphServiceClient(new GraphProperties(" ", "c", "s", "scope")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
