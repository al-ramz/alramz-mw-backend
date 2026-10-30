package com.alramz.dfm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** DFM's onboarding response. A numeric response_code is coerced to String by Jackson. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DfmOnboardingResponse(
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_msg") String responseMsg,
        @JsonProperty("member_reference") String memberReference,
        @JsonProperty("dfm_reference") String dfmReference,
        @JsonProperty("tracking_no") String trackingNo,
        @JsonProperty("cash_acc") String cashAcc,
        @JsonProperty("margin_acc") String marginAcc,
        @JsonProperty("cust_nin") String custNin) {
}
