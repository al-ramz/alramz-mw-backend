package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OTP settings that vary per environment. fixedTestOtpEnabled stores 1234/123456 and skips delivery (never in
 * prod: OtpService refuses to start). emailFrom must be one of app.email.allowed-senders.
 */
@ConfigurationProperties(prefix = "app.otp")
public record OtpProperties(boolean fixedTestOtpEnabled, String emailFrom) {
}
