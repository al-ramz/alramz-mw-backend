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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    private final UserRepositoryOps users = mock(UserRepositoryOps.class);
    private final PasswordEncoderService encoderMock = mock(PasswordEncoderService.class);
    private final TokenProvider tokens = mock(TokenProvider.class);
    private final RefreshTokenRepositoryOps refreshTokens = mock(RefreshTokenRepositoryOps.class);
    private final AuthService mocked = new AuthService(users, encoderMock, tokens, refreshTokens);
    private final TokenPair pair = new TokenPair("a2", "r2", "Bearer", 900);

    private static User user(String name) {
        return new User("1", name, name + "@x.com", "enc", List.of("USER"), true, "APP", "dev");
    }

    @Test
    void login_unknownUser_throwsException() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mocked.login("ghost", "pw"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consumer or Consumer Password is wrong");
    }

    @Test
    void register_success_savesEncodedUserAndReturnsTokens() {
        when(encoderMock.encode("pw")).thenReturn("hashed");
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokens.generateTokenPair("bob", List.of("ADMIN"), "APP", "qa")).thenReturn(pair);

        TokenPair result = mocked.register("bob", "bob@x.com", "pw", List.of("ADMIN"), "APP", "qa");

        assertThat(result).isSameAs(pair);
        org.mockito.ArgumentCaptor<User> saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().password()).isEqualTo("hashed");
        assertThat(saved.getValue().enabled()).isTrue();
    }

    @Test
    void register_duplicateUsernameOrEmail_isRejected() {
        when(users.existsByUsername("bob")).thenReturn(true);
        assertThatThrownBy(() -> mocked.register("bob", "e", "pw", List.of(), "A", "E"))
                .hasMessage("Username already exists");

        when(users.existsByUsername("amy")).thenReturn(false);
        when(users.existsByEmail("e")).thenReturn(true);
        assertThatThrownBy(() -> mocked.register("amy", "e", "pw", List.of(), "A", "E"))
                .hasMessage("Email already exists");
        verify(users, never()).save(any());
    }

    @Test
    void logout_revokesToken() {
        mocked.logout("r1");
        verify(refreshTokens).revoke("r1");
    }

    @Test
    void refresh_validToken_rotatesAndIssuesNewPair() {
        when(refreshTokens.findByToken("r1")).thenReturn(Optional.of(
                new RefreshToken("id", "r1", "bob", Instant.now().plusSeconds(60), Instant.now(), false)));
        when(users.findByUsername("bob")).thenReturn(Optional.of(user("bob")));
        when(tokens.generateTokenPair("bob", List.of("USER"), "APP", "dev")).thenReturn(pair);

        TokenPair result = mocked.refresh("r1");

        assertThat(result).isSameAs(pair);
        verify(refreshTokens).revoke("r1");
        org.mockito.ArgumentCaptor<RefreshToken> saved = org.mockito.ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().token()).isEqualTo("r2");
        assertThat(saved.getValue().userId()).isEqualTo("bob");
        assertThat(saved.getValue().revoked()).isFalse();
    }

    @Test
    void refresh_unknownRevokedOrExpiredToken_isRejected() {
        when(refreshTokens.findByToken("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> mocked.refresh("missing")).hasMessage("Invalid refresh token");

        when(refreshTokens.findByToken("revoked")).thenReturn(Optional.of(
                new RefreshToken("id", "revoked", "bob", Instant.now().plusSeconds(60), Instant.now(), true)));
        assertThatThrownBy(() -> mocked.refresh("revoked")).hasMessage("Refresh token expired or revoked");

        when(refreshTokens.findByToken("old")).thenReturn(Optional.of(
                new RefreshToken("id", "old", "bob", Instant.now().minusSeconds(60), Instant.now(), false)));
        assertThatThrownBy(() -> mocked.refresh("old")).hasMessage("Refresh token expired or revoked");
        verify(refreshTokens, never()).revoke(any());
    }

    @Test
    void refresh_userDeleted_isRejectedAfterRevocation() {
        when(refreshTokens.findByToken("r1")).thenReturn(Optional.of(
                new RefreshToken("id", "r1", "gone", Instant.now().plusSeconds(60), Instant.now(), false)));
        when(users.findByUsername("gone")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mocked.refresh("r1")).hasMessage("User not found");
        verify(refreshTokens).revoke("r1");
    }
}
