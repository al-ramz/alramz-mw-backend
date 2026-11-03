package com.alramz.finoux;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record FinouxInsertPostDataRequest(
        @JsonProperty("METHOD") String method,
        @JsonProperty("Version") String version,
        @JsonProperty("PARAM") List<FinouxParam> param) {

    public FinouxInsertPostDataRequest(String method, String version, List<FinouxParam> param) {
        this.method = method;
        this.version = version;
        this.param = param == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(param));
    }
}
