package com.alramz.knowyourcountry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One vendor row; unused vendor fields (score, risk_colour, ...) are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowYourCountryRecord(
        @JsonProperty("name") String name,
        @JsonProperty("iso_alpha2") String isoAlpha2,
        @JsonProperty("iso_alpha3") String isoAlpha3,
        @JsonProperty("risk_level") String riskLevel) {
}
