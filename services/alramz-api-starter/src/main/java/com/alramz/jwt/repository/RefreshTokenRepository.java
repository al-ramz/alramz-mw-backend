package com.alramz.jwt.repository;

import com.alramz.jwt.model.RefreshToken;
import com.alramz.jwt.model.RefreshTokenRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Repository
public class RefreshTokenRepository implements RefreshTokenRepositoryOps {

    private final RefreshTokenJpaRepository jpaRepository;

    public RefreshTokenRepository(RefreshTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenRecord entity = toEntity(token);
        RefreshTokenRecord saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return jpaRepository.findByToken(token).map(this::toDomain);
    }

    public List<RefreshToken> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId).stream().map(this::toDomain).toList();
    }

    @Override
    public void revoke(String token) {
        jpaRepository.findByToken(token).ifPresent(entity -> {
            entity.setRevoked("Y");
            jpaRepository.save(entity);
        });
    }

    @Override
    public void revokeAllByUserId(String userId) {
        findByUserId(userId).forEach(t -> revoke(t.token()));
    }

    private RefreshToken toDomain(RefreshTokenRecord entity) {
        return new RefreshToken(
                String.valueOf(entity.getId()),
                entity.getToken(),
                entity.getUserId(),
                entity.getExpiresAt().atZone(ZoneId.systemDefault()).toInstant(), // NOPMD LawOfDemeter
                entity.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant(), // NOPMD LawOfDemeter
                "Y".equals(entity.getRevoked())
        );
    }

    private RefreshTokenRecord toEntity(RefreshToken token) {
        RefreshTokenRecord entity = new RefreshTokenRecord();
        entity.setToken(token.token());
        entity.setUserId(token.userId());
        entity.setExpiresAt(LocalDateTime.ofInstant(token.expiresAt(), ZoneId.systemDefault()));
        entity.setCreatedAt(LocalDateTime.ofInstant(token.createdAt(), ZoneId.systemDefault()));
        entity.setRevoked(token.revoked() ? "Y" : "N");
        return entity;
    }
}
