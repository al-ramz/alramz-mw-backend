package com.alramz.controllers;

import org.springframework.web.bind.annotation.RestController;

/** Test fixture in the controller package that ApiAuditAspect used to intercept. */
@RestController
public class AuditProbeController {

    public String create(String body) {
        return "created";
    }
}
