package com.alramz.controllers;


import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.alramz.jwt.annotation.PermitAll;
import com.alramz.logging.aspect.Loggable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationController {

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.host:localhost}")
    private String host;

    @GetMapping("/api/v1/info")
    @PermitAll
    @Loggable
    public Map<String, Object> info() throws Exception {

        Map<String, Object> response = new HashMap<>();
        response.put("applicationName", applicationName);
        response.put("host", host);
        response.put("timestamp", Instant.now());

        return response;
    }
}
