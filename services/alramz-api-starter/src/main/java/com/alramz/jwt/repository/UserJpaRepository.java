package com.alramz.jwt.repository;

import com.alramz.jwt.model.UserRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserRecord, Long> {

    Optional<UserRecord> findByUsername(String username);

    Optional<UserRecord> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
