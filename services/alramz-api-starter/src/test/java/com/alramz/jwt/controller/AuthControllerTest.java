package com.alramz.jwt.controller;

import com.alramz.jwt.model.TokenPair;
import com.alramz.jwt.model.User;
import com.alramz.jwt.model.RefreshToken;
import com.alramz.jwt.repository.UserRepositoryOps;
import com.alramz.jwt.repository.RefreshTokenRepositoryOps;
import com.alramz.jwt.service.AuthService;
import com.alramz.jwt.service.PasswordEncoderService;
import com.alramz.jwt.service.TokenProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {

    @Test
    void login_success() {
        AuthService authService = new AuthService(
                new TestUserRepo(),
                new PasswordEncoderService() {
                    @Override public boolean matches(String raw, String enc) { return true; }
                },
                new TokenProvider(new com.alramz.jwt.config.JwtProperties()) {
                    @Override public TokenPair generateTokenPair(String username, List<String> roles, String application, String environment) { return new TokenPair("access", "refresh", "Bearer", 900); }
                },
                new TestRefreshRepo()
        );

        AuthController controller = new AuthController(authService);
        var response = controller.login(new AuthController.LoginRequest("test", "pass"));

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("access", response.getBody().accessToken());
    }

    @Test
    void login_invalidCredentials_throwsException() {
        AuthService authService = new AuthService(
                new TestUserRepo(),
                new PasswordEncoderService() {
                    @Override public boolean matches(String raw, String enc) { return false; }
                },
                new TokenProvider(new com.alramz.jwt.config.JwtProperties()) {
                    @Override public TokenPair generateTokenPair(String username, List<String> roles, String application, String environment) { return new TokenPair("access", "refresh", "Bearer", 900); }
                },
                new TestRefreshRepo()
        );

        AuthController controller = new AuthController(authService);
        assertThrows(IllegalArgumentException.class, () -> controller.login(new AuthController.LoginRequest("test", "pass")));
    }

    private static class TestUserRepo implements UserRepositoryOps {
        @Override public Optional<User> findByUsername(String u) { return Optional.of(new User("1", u, u + "@test.com", "enc", List.of("USER"), true, "APP_TEST", "dev")); }
        @Override public Optional<User> findByEmail(String e) { return Optional.empty(); }
        @Override public User save(User u) { return u; }
        @Override public boolean existsByUsername(String u) { return false; }
        @Override public boolean existsByEmail(String e) { return false; }
    }

    private static class TestRefreshRepo implements RefreshTokenRepositoryOps {
        @Override public RefreshToken save(RefreshToken t) { return t; }
        @Override public java.util.Optional<RefreshToken> findByToken(String t) { return java.util.Optional.empty(); }
        @Override public void revoke(String t) {}
        @Override public void revokeAllByUserId(String u) {}
    }
}
