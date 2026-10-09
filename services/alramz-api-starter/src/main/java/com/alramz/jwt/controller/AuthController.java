package com.alramz.jwt.controller;

import com.alramz.jwt.model.TokenPair;
import com.alramz.jwt.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        if (request.consumer() == null || request.consumer().isBlank() ||
            request.consumerPassword() == null || request.consumerPassword().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        TokenPair tokens = authService.login(request.consumer(), request.consumerPassword());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn()));
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<TokenResponse> register(@RequestBody RegisterRequest request) {
        if (request.consumer() == null || request.consumer().isBlank() ||
            request.email() == null || request.email().isBlank() ||
            request.consumerPassword() == null || request.consumerPassword().isBlank() ||
            request.application() == null || request.application().isBlank() ||
            request.environment() == null || request.environment().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        TokenPair tokens = authService.register(request.consumer(), request.email(), request.consumerPassword(), request.roles(), request.application(), request.environment());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn()));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshTokenRequest request) {
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/auth/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody RefreshTokenRequest request) {
        if (request.refreshToken() == null || request.refreshToken().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        TokenPair tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.tokenType(), tokens.expiresIn()));
    }

    public record LoginRequest(String consumer, String consumerPassword) {}

    public record RegisterRequest(
            String consumer,
            String email,
            String consumerPassword,
            java.util.List<String> roles,
            String application,
            String environment
    ) {}

    public record RefreshTokenRequest(String refreshToken) {}

    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {}
}
