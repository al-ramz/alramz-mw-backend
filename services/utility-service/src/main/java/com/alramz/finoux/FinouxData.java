package com.alramz.finoux;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FinouxData(List<Map<String, Object>> table) {
}
