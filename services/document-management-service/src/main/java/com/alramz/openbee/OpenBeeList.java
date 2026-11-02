package com.alramz.openbee;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** {@code GET /filing/ws/list/{idList}?recursive=false}. The value {@code image} is not declared. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenBeeList(Long id, String name, List<Value> values) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(Long id, String name, Long idSublist) {
    }
}
