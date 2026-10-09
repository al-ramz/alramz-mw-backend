package com.alramz.jwt.service;

import com.alramz.jwt.model.RefreshToken;
import com.alramz.jwt.model.TokenPair;
import com.alramz.jwt.model.User;
import com.alramz.jwt.repository.RefreshTokenRepositoryOps;
import com.alramz.jwt.repository.UserRepositoryOps;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    @Test
    void login_success_returnsTokenPair() {
        UserRepositoryOps userRepo = new UserRepositoryOps() {
            @Override public Optional<User> findByUsername(String u) { return Optional.of(new User("1", u, u + "@test.com", "encoded", List.of("USER"), true, "APP_TEST", "dev")); }
            @Override public Optional<User> findByEmail(String e) { return Optional.empty(); }
            @Override public User save(User u) { return u; }
            @Override public boolean existsByUsername(String u) { return false; }
            @Override public boolean existsByEmail(String e) { return false; }
        };
        PasswordEncoderService encoder = new PasswordEncoderService() {
            @Override public boolean matches(String raw, String enc) { return true; }
        };
        TokenProvider tokenProvider = new TokenProvider(new com.alramz.jwt.config.JwtProperties()) {
            @Override public TokenPair generateTokenPair(String username, List<String> roles, String application, String environment) { return new TokenPair("access", "refresh", "Bearer", 900); }
        };
        RefreshTokenRepositoryOps refreshRepo = new RefreshTokenRepositoryOps() {
            @Override public RefreshToken save(RefreshToken t) { return t; }
            @Override public java.util.Optional<RefreshToken> findByToken(String t) { return java.util.Optional.empty(); }
            @Override public void revoke(String t) {}
            @Override public void revokeAllByUserId(String u) {}
        };

        AuthService service = new AuthService(userRepo, encoder, tokenProvider, refreshRepo);
        TokenPair result = service.login("testuser", "password");

        assertNotNull(result);
        assertEquals("access", result.accessToken());
        assertEquals("refresh", result.refreshToken());
    }

    @Test
    void login_invalidPassword_throwsException() {
        UserRepositoryOps userRepo = new UserRepositoryOps() {
            @Override public Optional<User> findByUsername(String u) { return Optional.of(new User("1", u, u + "@test.com", "encoded", List.of("USER"), true, "APP_TEST", "dev")); }
            @Override public Optional<User> findByEmail(String e) { return Optional.empty(); }
            @Override public User save(User u) { return u; }
            @Override public boolean existsByUsername(String u) { return false; }
            @Override public boolean existsByEmail(String e) { return false; }
        };
        PasswordEncoderService encoder = new PasswordEncoderService() {
            @Override public boolean matches(String raw, String enc) { return false; }
        };
        TokenProvider tokenProvider = new TokenProvider(new com.alramz.jwt.config.JwtProperties()) {
            @Override public TokenPair generateTokenPair(String username, List<String> roles, String application, String environment) { return new TokenPair("access", "refresh", "Bearer", 900); }
        };
        RefreshTokenRepositoryOps refreshRepo = new RefreshTokenRepositoryOps() {
            @Override public RefreshToken save(RefreshToken t) { return t; }
            @Override public java.util.Optional<RefreshToken> findByToken(String t) { return java.util.Optional.empty(); }
            @Override public void revoke(String t) {}
            @Override public void revokeAllByUserId(String u) {}
        };

        AuthService service = new AuthService(userRepo, encoder, tokenProvider, refreshRepo);
        assertThrows(IllegalArgumentException.class, () -> service.login("testuser", "wrong"));
    }

    @Test
    void login_disabledAccount_throwsException() {
        UserRepositoryOps userRepo = new UserRepositoryOps() {
            @Override public Optional<User> findByUsername(String u) { return Optional.of(new User("1", u, u + "@test.com", "encoded", List.of("USER"), false, "APP_TEST", "dev")); }
            @Override public Optional<User> findByEmail(String e) { return Optional.empty(); }
            @Override public User save(User u) { return u; }
            @Override public boolean existsByUsername(String u) { return false; }
            @Override public boolean existsByEmail(String e) { return false; }
        };
        PasswordEncoderService encoder = new PasswordEncoderService() {
            @Override public boolean matches(String raw, String enc) { return true; }
        };
        TokenProvider tokenProvider = new TokenProvider(new com.alramz.jwt.config.JwtProperties()) {
            @Override public TokenPair generateTokenPair(String username, List<String> roles, String application, String environment) { return new TokenPair("access", "refresh", "Bearer", 900); }
        };
        RefreshTokenRepositoryOps refreshRepo = new RefreshTokenRepositoryOps() {
            @Override public RefreshToken save(RefreshToken t) { return t; }
            @Override public java.util.Optional<RefreshToken> findByToken(String t) { return java.util.Optional.empty(); }
            @Override public void revoke(String t) {}
            @Override public void revokeAllByUserId(String u) {}
        };

        AuthService service = new AuthService(userRepo, encoder, tokenProvider, refreshRepo);
        assertThrows(IllegalArgumentException.class, () -> service.login("testuser", "password"));
    }
}
