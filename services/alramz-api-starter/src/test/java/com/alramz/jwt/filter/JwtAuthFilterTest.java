package com.alramz.jwt.filter;

import com.alramz.jwt.config.JwtProperties;
import com.alramz.jwt.service.TokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthFilterTest {

    private static final String SECRET = "testSecretKey1234567890123456789012";
    private TokenProvider tokenProvider;
    private JwtAuthFilter filter;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        tokenProvider = new TokenProvider(properties);
        filter = new JwtAuthFilter(properties, tokenProvider);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain run(String authorization) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/secure");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    void validBearerTokenAuthenticatesWithRoleAuthorities() throws Exception {
        String token = tokenProvider.generateTokenPair("alice", List.of("ADMIN", "USER")).accessToken();

        MockFilterChain chain = run("Bearer " + token);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(chain.getRequest()).isNotNull();
        assertThat(auth.getName()).isEqualTo("alice");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void tokenWithoutRolesClaimGetsNoAuthorities() throws Exception {
        String token = tokenProvider.generateRefreshToken("bob");

        run("Bearer " + token);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth.getName()).isEqualTo("bob");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void invalidTokenLeavesContextUnauthenticatedButContinuesChain() throws Exception {
        MockFilterChain chain = run("Bearer not.a.jwt");

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingOrNonBearerHeaderIsPassedThrough() throws Exception {
        assertThat(run(null).getRequest()).isNotNull();
        assertThat(run("Basic abc").getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
