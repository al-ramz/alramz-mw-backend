package com.alramz.finoux;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinouxData(List<Map<String, Object>> table) {

    // Explicit canonical constructor (Jackson still binds through it): copy so the record is immutable.
    public FinouxData(List<Map<String, Object>> table) {
        this.table = table == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(table));
    }
}
