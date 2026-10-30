package com.alramz.service;

import com.alramz.model.EmailRequest;

/**
 * The email gateway. Used by EmailController and, in-process, by other features.
 */
public interface EmailService {

    /**
     * Validates the request and sends it through Microsoft Graph.
     * Returns normally when Graph accepted the message.
     *
     * @throws com.alramz.exception.EmailValidationException caller-fixable problem, getCode() is MGE-V-0xx
     * @throws com.alramz.exception.EmailServiceException backend failure, getCode() is MGE-B-00x and
     *         getRetryAfter() is the Graph Retry-After seconds for a throttled call (nullable)
     * @throws com.alramz.exception.EmailConfigurationException no Graph client is configured
     */
    void sendEmail(EmailRequest request);
}
