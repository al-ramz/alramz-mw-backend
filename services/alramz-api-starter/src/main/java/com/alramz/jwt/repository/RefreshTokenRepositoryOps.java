package com.alramz.jwt.repository;

import com.alramz.jwt.model.RefreshToken;


public interface RefreshTokenRepositoryOps {

    RefreshToken save(RefreshToken token);

    java.util.Optional<RefreshToken> findByToken(String token);

    void revoke(String token);

    void revokeAllByUserId(String userId);
}
