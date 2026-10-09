package com.alramz.controllers;

import com.alramz.api.HealthApi;
import com.alramz.api.RedisCacheApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.GenericResponse;
import com.alramz.service.RedisCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Generated;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Generated("org.openapitools.codegen.languages.SpringCodegen")
public class RedisCacheController implements RedisCacheApi, HealthApi {

    private final RedisCacheService redisCacheService;

    public RedisCacheController(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> flushCache(String cacheKey) {
        String result = redisCacheService.flushCache(cacheKey);
        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage(result);
        response.setResponse(null);
        return ResponseEntity.ok(response);
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> getCacheEntriesByKey(String cacheKey) {
        List<Map<String, Object>> entries = redisCacheService.getAllEntries(cacheKey);
        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage("OK");
        response.setResponse(entries);
        return ResponseEntity.ok(response);
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> getCacheStatus(String cacheKey) {
        Map<String, Object> status = redisCacheService.getCacheStatus(cacheKey);

        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage("OK");
        response.setResponse(status);
        return ResponseEntity.ok(response);
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> reloadCache(String cacheKey) {
        String result = redisCacheService.reloadGlobalConfig(cacheKey);
        List<Map<String, Object>> entries = redisCacheService.getAllEntries(cacheKey);
        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage(result);
        response.setResponse(entries);
        return ResponseEntity.ok(response);
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> checkDeepHealth() {
        Map<String, Object> health = redisCacheService.checkDeepHealth();

        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage("OK");
        response.setResponse(health);
        return ResponseEntity.ok(response);
    }
}
