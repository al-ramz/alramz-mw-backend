package com.alramz.jwt.repository;

import com.alramz.jwt.model.User;
import com.alramz.jwt.model.UserRecord;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Repository
public class UserRepository implements com.alramz.jwt.repository.UserRepositoryOps {

    private final UserJpaRepository jpaRepository;

    public UserRepository(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserRecord entity = toEntity(user);
        UserRecord saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    private User toDomain(UserRecord entity) {
        java.util.List<String> roles = entity.getRoles() == null || entity.getRoles().isBlank()
                ? java.util.List.of()
                : java.util.Arrays.stream(entity.getRoles().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
        return new User(
                String.valueOf(entity.getId()),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPassword(),
                roles,
                "Y".equals(entity.getEnable()),
                entity.getApplication(),
                entity.getEnvironment()
        );
    }

    private UserRecord toEntity(User user) {
        UserRecord entity = new UserRecord();
        entity.setUsername(user.username());
        entity.setEmail(user.email());
        entity.setPassword(user.password());
        entity.setRoles(user.roles() == null ? "" : String.join(",", user.roles()));
        entity.setEnable(user.enabled() ? "Y" : "N");
        entity.setApplication(user.application());
        entity.setEnvironment(user.environment());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }
}
