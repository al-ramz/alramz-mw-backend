package com.alramz.finoux;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FinouxParam(@JsonProperty("Key") String key, @JsonProperty("Value") String value) {
}
