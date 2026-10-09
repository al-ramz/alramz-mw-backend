package com.alramz.jwt.repository;

import com.alramz.jwt.model.RefreshToken;
import com.alramz.jwt.model.User;
import com.alramz.jwt.service.PasswordEncoderService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {

    @Test
    void userRepository_crudOperations() {
        InMemoryUserRepo repo = new InMemoryUserRepo();
        User user = new User(null, "john", "john@test.com", "pass", List.of("ADMIN"), true, "APP_TEST", "dev");
        repo.save(user);

        assertEquals(Optional.of(new User("1", "john", "john@test.com", "pass", List.of("ADMIN"), true, "APP_TEST", "dev")),
                repo.findByUsername("john"));
        assertTrue(repo.existsByUsername("john"));
    }

    @Test
    void refreshTokenRepository_revokeWorks() {
        InMemoryRefreshTokenRepo repo = new InMemoryRefreshTokenRepo();
        RefreshToken token = new RefreshToken(null, "rt1", "user1", Instant.now().plusSeconds(100), Instant.now(), false);
        repo.save(token);

        assertEquals(Optional.of(token), repo.findByToken("rt1"));
        repo.revoke("rt1");
        assertEquals(Optional.of(new RefreshToken(null, "rt1", "user1", token.expiresAt(), token.createdAt(), true)),
                repo.findByToken("rt1"));
    }

    @Test
    void passwordEncoder_matchesCorrectly() {
        PasswordEncoderService encoder = new PasswordEncoderService();
        String encoded = encoder.encode("password123");
        assertTrue(encoder.matches("password123", encoded));
        assertFalse(encoder.matches("wrong", encoded));
    }

    private static class InMemoryUserRepo implements UserRepositoryOps {
        private final List<User> users = new ArrayList<>();
        private long idCounter = 1;

        @Override public Optional<User> findByUsername(String u) { return users.stream().filter(user -> user.username().equals(u)).findFirst(); }
        @Override public Optional<User> findByEmail(String e) { return users.stream().filter(user -> user.email().equals(e)).findFirst(); }
        @Override public synchronized User save(User user) {
            User existing = findByUsername(user.username()).orElse(null);
            if (existing != null) {
                users.remove(existing);
            }
            User saved = new User(String.valueOf(idCounter++), user.username(), user.email(), user.password(), user.roles(), user.enabled(), user.application(), user.environment());
            users.add(saved);
            return saved;
        }
        @Override public boolean existsByUsername(String u) { return findByUsername(u).isPresent(); }
        @Override public boolean existsByEmail(String e) { return findByEmail(e).isPresent(); }
    }

    private static class InMemoryRefreshTokenRepo implements RefreshTokenRepositoryOps {
        private final List<RefreshToken> tokens = new ArrayList<>();

        @Override public RefreshToken save(RefreshToken token) {
            tokens.removeIf(t -> t.token().equals(token.token()));
            tokens.add(token);
            return token;
        }
        @Override public Optional<RefreshToken> findByToken(String t) { return tokens.stream().filter(token -> token.token().equals(t)).findFirst(); }
        @Override public void revoke(String t) { findByToken(t).ifPresent(token -> { tokens.remove(token); tokens.add(new RefreshToken(token.id(), token.token(), token.userId(), token.expiresAt(), token.createdAt(), true)); }); }
        @Override public void revokeAllByUserId(String u) { tokens.stream().filter(t -> t.userId().equals(u)).forEach(t -> revoke(t.token())); }
    }
}
