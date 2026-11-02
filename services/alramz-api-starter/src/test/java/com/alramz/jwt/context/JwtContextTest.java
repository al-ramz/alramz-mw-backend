package com.alramz.jwt.context;

import com.alramz.jwt.config.JwtProperties;
import com.alramz.jwt.service.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtContextTest {

    private TokenProvider tokenProvider;
    private JwtContext context;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("testSecretKey1234567890123456789012");
        tokenProvider = new TokenProvider(properties);
        context = new JwtContext(properties, tokenProvider);
    }

    @AfterEach
    void reset() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bindRequest(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void readsApplicationAndEnvironmentClaimsFromBearerToken() {
        String token = tokenProvider.generateTokenPair("u", List.of("USER"), "APP_X", "qa").accessToken();
        bindRequest("Bearer " + token);

        assertThat(context.getCurrentApplication()).isEqualTo("APP_X");
        assertThat(context.getCurrentEnvironment()).isEqualTo("qa");
    }

    @Test
    void returnsNullWithoutRequestOrBearerOrValidToken() {
        assertThat(context.getCurrentEnvironment()).isNull();

        bindRequest(null);
        assertThat(context.getCurrentEnvironment()).isNull();

        bindRequest("Basic xyz");
        assertThat(context.getCurrentEnvironment()).isNull();

        bindRequest("Bearer garbage");
        assertThat(context.getClaim("environment")).isNull();
    }
}
