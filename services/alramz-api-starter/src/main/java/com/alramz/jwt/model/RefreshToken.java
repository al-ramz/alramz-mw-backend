package com.alramz.jwt.model;

import java.time.Instant;

public record RefreshToken(
        String id,
        String token,
        String userId,
        Instant expiresAt,
        Instant createdAt,
        boolean revoked
) {
}
