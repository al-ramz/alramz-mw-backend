package com.alramz.service;

import com.alramz.model.SmsSendRequest;
import com.alramz.model.SmsSendResult;

/**
 * The SMS gateway router. Used by SmsController, the SMS_TABLE dispatch job and, in-process, by other features.
 */
public interface SmsService {

    /**
     * Validates the request and sends one SMS per de-duplicated number. Never throws for provider failures:
     * they show as FAILED results.
     *
     * @throws com.alramz.exception.SmsException SMS001..SMS005 for caller-fixable input
     */
    Outcome sendWithOutcome(SmsSendRequest request);

    default SmsSendResult send(final SmsSendRequest request) {
        return sendWithOutcome(request).result();
    }

    /** anyProviderRejected: at least one number failed because a provider rejected it (vs. was unreachable). */
    record Outcome(SmsSendResult result, boolean anyProviderRejected) {
    }
}
