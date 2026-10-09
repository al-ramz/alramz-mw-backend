package com.alramz.controllers;

import java.time.Instant;
import java.util.Map;

import com.alramz.jwt.annotation.PermitAll;
import com.alramz.logging.aspect.Loggable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationController {

    private final String applicationName;
    private final String host;

    public ApplicationController(@Value("${spring.application.name}") String applicationName,
                                 @Value("${server.host:localhost}") String host) {
        this.applicationName = applicationName;
        this.host = host;
    }

    @GetMapping("/api/v1/info")
    @PermitAll
    @Loggable
    public Map<String, Object> info() {
        return Map.of(
                "applicationName", applicationName,
                "host", host,
                "timestamp", Instant.now());
    }
}
