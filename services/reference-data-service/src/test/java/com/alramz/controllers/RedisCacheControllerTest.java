package com.alramz.controllers;

import com.alramz.model.GenericResponse;
import com.alramz.service.RedisCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheControllerTest {

    private static final List<Map<String, Object>> ENTRIES = List.of(Map.of("code", "AE"));

    @Mock
    private RedisCacheService service;

    @InjectMocks
    private RedisCacheController controller;

    private static void assertOk(ResponseEntity<GenericResponse> response, String message, Object body) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo(message);
        assertThat(response.getBody().getResponse()).isEqualTo(body);
    }

    @Test
    void flushCache_shouldReturnServiceMessageWithNoBody() {
        when(service.flushCache("countries")).thenReturn("Cache flushed successfully");

        assertOk(controller.flushCache("countries"), "Cache flushed successfully", null);
    }

    @Test
    void getCacheEntriesByKey_shouldReturnEntries() {
        when(service.getAllEntries("countries")).thenReturn(ENTRIES);

        assertOk(controller.getCacheEntriesByKey("countries"), "OK", ENTRIES);
    }

    @Test
    void getCacheStatus_shouldReturnStatusMap() {
        Map<String, Object> status = Map.of("exists", true);
        when(service.getCacheStatus("countries")).thenReturn(status);

        assertOk(controller.getCacheStatus("countries"), "OK", status);
    }

    @Test
    void reloadCache_shouldReturnReloadMessageAndFreshEntries() {
        when(service.reloadGlobalConfig("countries")).thenReturn("Cache reloaded successfully with 1 entries");
        when(service.getAllEntries("countries")).thenReturn(ENTRIES);

        assertOk(controller.reloadCache("countries"), "Cache reloaded successfully with 1 entries", ENTRIES);
    }

    @Test
    void checkDeepHealth_shouldReturnHealthMap() {
        Map<String, Object> health = Map.of("overallStatus", "UP");
        when(service.checkDeepHealth()).thenReturn(health);

        assertOk(controller.checkDeepHealth(), "OK", health);
    }
}
