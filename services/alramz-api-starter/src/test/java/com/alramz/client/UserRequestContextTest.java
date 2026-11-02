package com.alramz.client;

import com.alramz.models.UserRequestDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRequestContextTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue("tok-" + subject).header("alg", "none").subject(subject).build();
    }

    private static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(principal, null));
    }

    @Test
    void getReadsSubjectAndTokenFromJwtPrincipal() {
        authenticate(jwt("user-1"));

        UserRequestDetails details = UserRequestContext.get();

        assertThat(details.getUserId()).isEqualTo("user-1");
        assertThat(details.getBearerToken()).isEqualTo("tok-user-1");
    }

    @Test
    void getHandlesStringAndOtherPrincipals() {
        authenticate("alice");
        assertThat(UserRequestContext.get().getUserId()).isEqualTo("alice");
        assertThat(UserRequestContext.get().getBearerToken()).isNull();

        authenticate(42);
        assertThat(UserRequestContext.get().getUserId()).isEqualTo("42");
    }

    @Test
    void getWithoutAuthenticationIsDenied() {
        assertThatThrownBy(UserRequestContext::get).isInstanceOf(AccessDeniedException.class)
                .hasMessage("No authentication found");
    }

    @Test
    void validateUserChecksJwtSubjectAndSkipsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("k", "anon",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        assertThatCode(() -> UserRequestContext.validateUser("anyone")).doesNotThrowAnyException();

        authenticate(jwt("user-1"));
        assertThatCode(() -> UserRequestContext.validateUser("user-1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> UserRequestContext.validateUser("user-2"))
                .isInstanceOf(AccessDeniedException.class).hasMessage("No user found.");
    }
}
