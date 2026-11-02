package com.alramz.openbee;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Open Bee error body {@code {"error":[{"code":"...","message":"..."}]}}; the live Open Bee also sends a single
 * object ({@code {"error":{"code":..}}}). Only the codes are ever used.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBeeErrorBody(@JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<Item> error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String code, String message) {
    }
}
