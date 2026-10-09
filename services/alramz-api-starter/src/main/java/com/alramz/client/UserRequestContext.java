package com.alramz.client;

import com.alramz.models.UserRequestDetails;
import com.alramz.models.UserRequestDetails.UserRequestDetailsBuilder;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class UserRequestContext {
    private UserRequestContext() {
    }

    public static UserRequestDetails get() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("No authentication found");
        }

        Object principal = authentication.getPrincipal(); // NOPMD LawOfDemeter
        UserRequestDetailsBuilder userRequestDetails = UserRequestDetails.builder();

        switch (principal) {
            case Jwt jwt -> {
                userRequestDetails.userId(jwt.getSubject());
                userRequestDetails.bearerToken(jwt.getTokenValue());
            }
            case String username -> {
                userRequestDetails.userId(username);
                userRequestDetails.bearerToken(null);
            }
            default -> {
                userRequestDetails.userId(principal.toString());
                userRequestDetails.bearerToken(null);
            }
        }

        return userRequestDetails.build();
    }

    public static void validateUser(String userId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof AnonymousAuthenticationToken) {
            return; // profile local
        }

        Jwt jwt = (Jwt) authentication.getPrincipal(); // NOPMD LawOfDemeter
        if (!jwt.getSubject().equals(userId)) {
            throw new AccessDeniedException("No user found.");
        }
    }
}
