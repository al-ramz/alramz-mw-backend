package com.alramz.jwt.model;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public record User(
        String id,
        String username,
        String email,
        String password,
        List<String> roles,
        boolean enabled,
        String application,
        String environment
) {
    public User {
        if (roles == null) roles = List.of();
        if (application == null) application = "";
        if (environment == null) environment = "";
    }

    public org.springframework.security.core.userdetails.UserDetails toUserDetails() {
        return new org.springframework.security.core.userdetails.User(
                username,
                password,
                enabled,
                true,
                true,
                true,
                authorities(roles)
        );
    }

    private static Collection<? extends org.springframework.security.core.GrantedAuthority> authorities(List<String> roles) {
        return roles.stream()
                .map(role -> (org.springframework.security.core.GrantedAuthority) () -> "ROLE_" + role)
                .toList();
    }
}
