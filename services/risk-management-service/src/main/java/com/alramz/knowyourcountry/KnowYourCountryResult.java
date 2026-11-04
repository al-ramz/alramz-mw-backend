package com.alramz.knowyourcountry;

import java.util.List;

/** Outcome of a KnowYourCountry call. */
public sealed interface KnowYourCountryResult
        permits KnowYourCountryResult.Success, KnowYourCountryResult.Rejected,
        KnowYourCountryResult.Malformed, KnowYourCountryResult.Unavailable {

    /** Vendor 2xx with a JSON array (possibly empty), null elements already dropped. */
    record Success(List<KnowYourCountryRecord> rows) implements KnowYourCountryResult {
        public Success {
            rows = List.copyOf(rows);
        }
    }

    /** The vendor answered with a non-2xx status. */
    record Rejected(int httpStatus) implements KnowYourCountryResult {
    }

    /** 2xx, but the body is empty, unparsable or not a JSON array. */
    record Malformed(String detail) implements KnowYourCountryResult {
    }

    /** Timeout, connection failure, or circuit breaker open. */
    record Unavailable(Throwable cause) implements KnowYourCountryResult {
    }
}
