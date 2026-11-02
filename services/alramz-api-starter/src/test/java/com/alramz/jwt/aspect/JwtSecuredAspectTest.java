package com.alramz.jwt.aspect;

import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.jwt.context.JwtContext;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtSecuredAspectTest {

    private final JwtContext jwtContext = mock(JwtContext.class);
    private final JwtSecuredAspect aspect = new JwtSecuredAspect(jwtContext);
    private final JoinPoint joinPoint = mock(JoinPoint.class);

    @SuppressWarnings("unused")
    static class Target {
        @JwtSecured
        void open() {
        }

        @JwtSecured(roles = {"ADMIN"}, environment = {"prod", "qa"})
        void guarded() {
        }
    }

    @BeforeEach
    void setUp() {
        when(joinPoint.getSignature()).thenReturn(mock(Signature.class));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static JwtSecured annotation(String method) throws NoSuchMethodException {
        return Target.class.getDeclaredMethod(method).getAnnotation(JwtSecured.class);
    }

    private static void authenticate(boolean authenticated, String... authorities) {
        TestingAuthenticationToken token = new TestingAuthenticationToken("u", null, authorities);
        token.setAuthenticated(authenticated);
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    void grantsAccessWithMatchingRoleAndEnvironment() throws Exception {
        authenticate(true, "ROLE_ADMIN");
        when(jwtContext.getCurrentEnvironment()).thenReturn("qa");

        assertThatCode(() -> aspect.checkJwtSecured(joinPoint, annotation("guarded"))).doesNotThrowAnyException();
    }

    @Test
    void annotationWithoutConstraintsOnlyRequiresAuthentication() throws Exception {
        authenticate(true);

        assertThatCode(() -> aspect.checkJwtSecured(joinPoint, annotation("open"))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnauthenticated() throws Exception {
        authenticate(false, "ROLE_ADMIN");
        JwtSecured secured = annotation("guarded");

        assertThatThrownBy(() -> aspect.checkJwtSecured(joinPoint, secured))
                .isInstanceOf(AccessDeniedException.class).hasMessage("Unauthenticated");
    }

    @Test
    void rejectsMissingRole() throws Exception {
        authenticate(true, "ROLE_USER");
        JwtSecured secured = annotation("guarded");

        assertThatThrownBy(() -> aspect.checkJwtSecured(joinPoint, secured))
                .isInstanceOf(AccessDeniedException.class).hasMessageStartingWith("Insufficient role");
    }

    @Test
    void rejectsMissingOrWrongEnvironment() throws Exception {
        authenticate(true, "ROLE_ADMIN");
        JwtSecured secured = annotation("guarded");

        when(jwtContext.getCurrentEnvironment()).thenReturn(" ");
        assertThatThrownBy(() -> aspect.checkJwtSecured(joinPoint, secured))
                .hasMessage("Environment claim missing in token");

        when(jwtContext.getCurrentEnvironment()).thenReturn(null);
        assertThatThrownBy(() -> aspect.checkJwtSecured(joinPoint, secured))
                .hasMessage("Environment claim missing in token");

        when(jwtContext.getCurrentEnvironment()).thenReturn("dev");
        assertThatThrownBy(() -> aspect.checkJwtSecured(joinPoint, secured))
                .hasMessageStartingWith("Insufficient environment");
    }
}
