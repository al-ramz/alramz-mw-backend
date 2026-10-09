package com.alramz.jwt.controller;

import com.alramz.client.UserRequestContext;
import com.alramz.models.UserRequestDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ValidateController {

    @GetMapping("/api/auth/validate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> validate() {
        UserRequestDetails user = UserRequestContext.get();
        return ResponseEntity.ok(new UserResponse(user.getUserId()));
    }

    public record UserResponse(String userId) {
    }
}
