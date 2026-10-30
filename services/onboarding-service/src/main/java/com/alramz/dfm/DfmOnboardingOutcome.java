package com.alramz.dfm;

/** Every way a DFM onboarding call can end. Only {@link Unavailable} counts toward the circuit breaker. */
public sealed interface DfmOnboardingOutcome {

    record Success(DfmOnboardingResponse body) implements DfmOnboardingOutcome {
    }

    record Rejected(String providerCode, String providerMessage, boolean duplicateInProgress) implements DfmOnboardingOutcome {
    }

    record Unavailable(Reason reason, Throwable cause) implements DfmOnboardingOutcome {
    }

    enum Reason { TIMEOUT, CIRCUIT_OPEN, BAD_RESPONSE }
}
