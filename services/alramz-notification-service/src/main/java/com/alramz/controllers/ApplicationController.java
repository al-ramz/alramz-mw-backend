package com.alramz.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@SuppressWarnings({"PMD.AtLeastOneConstructor", "PMD.SignatureDeclareThrowsException"})
public class ApplicationController {

    @GetMapping("/api/v1/info")
    public Map<String, Object> info() throws Exception {
        final Map<String, Object> response = new HashMap<>();
        response.put("applicationName", "alramz-notification-service");
        response.put("host", InetAddress.getLocalHost().getHostName());
        response.put("timestamp", Instant.now());
        return response;
    }
}
