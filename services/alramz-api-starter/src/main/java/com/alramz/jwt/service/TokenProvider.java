package com.alramz.jwt.service;

import com.alramz.jwt.config.JwtProperties;
import com.alramz.jwt.model.TokenPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import io.jsonwebtoken.JwtException;

@Service
public class TokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(TokenProvider.class);

    private final JwtProperties properties;
    private final javax.crypto.SecretKey key;

    public TokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.key = io.jsonwebtoken.security.Keys.hmacShaKeyFor(properties.getSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public TokenPair generateTokenPair(String username, List<String> roles, String application, String environment) {
        long now = System.currentTimeMillis();
        String accessToken = io.jsonwebtoken.Jwts.builder()
                .subject(username)
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + properties.getAccessTokenExpirationMs()))
                .claim("roles", roles)
                .claim(properties.getApplicationClaimName(), application)
                .claim(properties.getEnvironmentClaimName(), environment)
                .signWith(key)
                .compact();

        String refreshToken = io.jsonwebtoken.Jwts.builder()
                .subject(username)
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + properties.getRefreshTokenExpirationMs()))
                .signWith(key)
                .compact();

        if (logger.isDebugEnabled()) {
            logger.debug("Generated token pair for user '{}' with roles={}, application={}, environment={}", username, roles, application, environment);
        }

        return TokenPair.of(accessToken, refreshToken, properties.getAccessTokenExpirationMs() / 1000);
    }

    public TokenPair generateTokenPair(String username, List<String> roles) {
        return generateTokenPair(username, roles, "", "");
    }

    public String generateRefreshToken(String username) {
        long now = System.currentTimeMillis();
        return io.jsonwebtoken.Jwts.builder()
                .subject(username)
                .id(java.util.UUID.randomUUID().toString())
                .issuedAt(new java.util.Date(now))
                .expiration(new java.util.Date(now + properties.getRefreshTokenExpirationMs()))
                .signWith(key)
                .compact();
    }

    public java.util.Optional<String> validateToken(String token) {
        try {
            io.jsonwebtoken.Claims claims = io.jsonwebtoken.Jwts.parser() // NOPMD LawOfDemeter
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            java.util.Date expiration = claims.getExpiration();
            if (expiration.before(new java.util.Date())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Token validation failed: token expired for subject '{}'", claims.getSubject());
                }
                return java.util.Optional.empty();
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Token validated successfully for subject '{}'", claims.getSubject());
            }
            return java.util.Optional.of(claims.getSubject());
        } catch (JwtException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Token validation failed: {}", e.getMessage());
            }
            return java.util.Optional.empty();
        }
    }

    public String extractUsername(String token) {
        try {
            return io.jsonwebtoken.Jwts.parser() // NOPMD LawOfDemeter
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    public String extractClaim(String token, String claimName) {
        try {
            return io.jsonwebtoken.Jwts.parser() // NOPMD LawOfDemeter
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .get(claimName, String.class);
        } catch (JwtException e) {
            return null;
        }
    }
}
