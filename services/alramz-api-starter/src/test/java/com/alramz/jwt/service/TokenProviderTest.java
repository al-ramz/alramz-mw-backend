package com.alramz.jwt.service;

import com.alramz.jwt.config.JwtProperties;
import com.alramz.jwt.model.TokenPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenProviderTest {

    @Test
    void generateTokenPair_createsValidTokens() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("testSecretKey1234567890123456789012");
        properties.setAccessTokenExpirationMs(60000);
        properties.setRefreshTokenExpirationMs(120000);

        TokenProvider provider = new TokenProvider(properties);
        TokenPair pair = provider.generateTokenPair("testuser", List.of("ADMIN", "USER"));

        assertNotNull(pair.accessToken());
        assertNotNull(pair.refreshToken());
        assertEquals("Bearer", pair.tokenType());
        assertTrue(pair.expiresIn() > 0);
    }

    @Test
    void generateTokenPair_refreshTokensAreUniqueWithinSameSecond() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("testSecretKey1234567890123456789012");

        TokenProvider provider = new TokenProvider(properties);

        assertNotEquals(provider.generateTokenPair("testuser", List.of("USER")).refreshToken(),
                provider.generateTokenPair("testuser", List.of("USER")).refreshToken());
        assertNotEquals(provider.generateRefreshToken("testuser"), provider.generateRefreshToken("testuser"));
    }

    @Test
    void validateToken_returnsUsernameForValidToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("testSecretKey1234567890123456789012");
        properties.setAccessTokenExpirationMs(60000);
        properties.setRefreshTokenExpirationMs(120000);

        TokenProvider provider = new TokenProvider(properties);
        TokenPair pair = provider.generateTokenPair("testuser", List.of("USER"));

        var username = provider.validateToken(pair.accessToken());
        assertTrue(username.isPresent());
        assertEquals("testuser", username.get());
    }

    @Test
    void validateToken_returnsEmptyForInvalidToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("testSecretKey1234567890123456789012");

        TokenProvider provider = new TokenProvider(properties);
        var result = provider.validateToken("invalid.token.here");

        assertTrue(result.isEmpty());
    }

    @Test
    void extractUsername_returnsUsernameFromToken() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("testSecretKey1234567890123456789012");

        TokenProvider provider = new TokenProvider(properties);
        TokenPair pair = provider.generateTokenPair("testuser", List.of("USER"));

        String username = provider.extractUsername(pair.accessToken());
        assertEquals("testuser", username);
    }
}
