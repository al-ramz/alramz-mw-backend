package com.alramz.jwt.repository;

import com.alramz.jwt.model.User;

import java.util.Optional;

public interface UserRepositoryOps {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    User save(User user);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
