package com.alramz.jwt.repository;

import com.alramz.jwt.model.RefreshTokenRecord;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenRecord, Long> {

    Optional<RefreshTokenRecord> findByToken(String token);

    List<RefreshTokenRecord> findByUserId(String userId);

    void deleteByToken(String token);
}
