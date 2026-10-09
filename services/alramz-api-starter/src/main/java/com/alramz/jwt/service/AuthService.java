package com.alramz.jwt.service;

import com.alramz.jwt.model.RefreshToken;
import com.alramz.jwt.model.TokenPair;
import com.alramz.jwt.model.User;
import com.alramz.jwt.repository.RefreshTokenRepositoryOps;
import com.alramz.jwt.repository.UserRepositoryOps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final UserRepositoryOps userRepository;
    private final PasswordEncoderService passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenRepositoryOps refreshTokenRepository;

    public AuthService(UserRepositoryOps userRepository,
                       PasswordEncoderService passwordEncoder,
                       TokenProvider tokenProvider,
                       RefreshTokenRepositoryOps refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    public TokenPair login(String consumer, String consumerPassword) {
        User user = userRepository.findByUsername(consumer)
                .orElseThrow(() -> {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Login failed: user '{}' not found", consumer);
                    }
                    return new IllegalArgumentException("Consumer or Consumer Password is wrong");
                });

        if (!passwordEncoder.matches(consumerPassword, user.password())) {
            if (logger.isWarnEnabled()) {
                logger.warn("Login failed: invalid password for user '{}'", consumer);
            }
            throw new IllegalArgumentException("Consumer or Consumer Password is wrong");
        }

        if (!user.enabled()) {
            if (logger.isWarnEnabled()) {
                logger.warn("Login failed: account disabled for user '{}'", consumer);
            }
            throw new IllegalArgumentException("Consumer or Consumer Password is wrong");
        }

        if (logger.isInfoEnabled()) {
            logger.info("User '{}' logged in successfully with application={}, environment={}", consumer, user.application(), user.environment());
        }

        TokenPair tokenPair = tokenProvider.generateTokenPair(
                consumer,
                user.roles(),
                user.application(),
                user.environment()
        );

        RefreshToken refreshToken = new RefreshToken(
                null,
                tokenPair.refreshToken(),
                consumer,
                Instant.now().plusSeconds(7 * 24 * 60 * 60),
                Instant.now(),
                false
        );
        refreshTokenRepository.save(refreshToken);

        return tokenPair;
    }

    public TokenPair register(String consumer, String email, String consumerPassword, List<String> roles, String application, String environment) {
        if (userRepository.existsByUsername(consumer)) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists");
        }

        String encodedPassword = passwordEncoder.encode(consumerPassword);
        User user = new User(null, consumer, email, encodedPassword, roles, true, application, environment);
        User savedUser = userRepository.save(user);

        if (logger.isInfoEnabled()) {
            logger.info("User '{}' registered successfully with application={}, environment={}, roles={}", consumer, application, environment, roles);
        }

        return tokenProvider.generateTokenPair(savedUser.username(), savedUser.roles(), savedUser.application(), savedUser.environment());
    }

    public void logout(String refreshToken) {
        refreshTokenRepository.revoke(refreshToken);
        if (logger.isInfoEnabled()) {
            logger.info("Refresh token revoked successfully");
        }
    }

    public TokenPair refresh(String refreshToken) {
        RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Token refresh failed: invalid refresh token");
                    }
                    return new IllegalArgumentException("Invalid refresh token");
                });

        if (storedToken.revoked() || storedToken.expiresAt().isBefore(Instant.now())) {
            if (logger.isWarnEnabled()) {
                logger.warn("Token refresh failed: refresh token expired or revoked for user '{}'", storedToken.userId());
            }
            throw new IllegalArgumentException("Refresh token expired or revoked");
        }

        refreshTokenRepository.revoke(refreshToken);

        User user = userRepository.findByUsername(storedToken.userId())
                .orElseThrow(() -> {
                    if (logger.isErrorEnabled()) {
                        logger.error("Token refresh failed: user '{}' not found", storedToken.userId());
                    }
                    return new IllegalArgumentException("User not found");
                });

        if (logger.isInfoEnabled()) {
            logger.info("Token refreshed successfully for user '{}'", storedToken.userId());
        }

        TokenPair tokenPair = tokenProvider.generateTokenPair(
                user.username(),
                user.roles(),
                user.application(),
                user.environment()
        );

        RefreshToken newRefreshToken = new RefreshToken(
                null,
                tokenPair.refreshToken(),
                storedToken.userId(),
                Instant.now().plusSeconds(7 * 24 * 60 * 60),
                Instant.now(),
                false
        );
        refreshTokenRepository.save(newRefreshToken);

        return tokenPair;
    }
}
