package com.alramz.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record ETradeResponse(
    @JsonProperty("Error_code") @JsonAlias("Error_Code") String errorCode,
    @JsonProperty("Reference_No") @JsonAlias("Reference_no") String referenceNo,
    JsonNode resData,
    @JsonProperty("Exists") Boolean exists
) {}
