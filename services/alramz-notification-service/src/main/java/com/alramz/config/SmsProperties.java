package com.alramz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * app.sms.* (SMS gateway and SMS_TABLE dispatch job). Fail-fast checks run only when live sending or dispatch is
 * switched on (E44/E45). Provider constants that never change (content type, sender, priority, sent/failed
 * statuses) are code constants below, not configuration.
 */
@ConfigurationProperties(prefix = "app.sms")
public record SmsProperties(
        boolean liveSendEnabled,
        String preferredProvider,
        int maxRecipients,
        int maxMessageLength,
        Duration connectTimeout,
        Duration readTimeout,
        Defaults defaults,
        Etisalat etisalat,
        Waselney waselney,
        Veriphone veriphone,
        Dispatch dispatch
) {

    public static final String CONTENT_TYPE = "3.1";
    public static final String SENDER = "AL RAMZ";
    public static final int PRIORITY = 0;
    public static final int STATUS_SENT = 88;
    public static final int STATUS_FAILED = 1;
    private static final String REDACTED = "***";

    public SmsProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(10) : readTimeout;
        defaults = defaults == null ? new Defaults(null, null) : defaults;
        etisalat = etisalat == null ? new Etisalat(null, null, null, null, null) : etisalat;
        waselney = waselney == null ? new Waselney(null, null, null, null) : waselney;
        veriphone = veriphone == null ? new Veriphone(null, null) : veriphone;
        dispatch = dispatch == null ? new Dispatch(false, 50, null) : dispatch;

        final List<String> problems = new ArrayList<>();
        if (liveSendEnabled) {
            require(problems, waselney.sendUrl(), "app.sms.waselney.send-url");
            require(problems, waselney.username(), "app.sms.waselney.username");
            require(problems, waselney.password(), "app.sms.waselney.password");
            if (isEtisalat(preferredProvider)) {
                require(problems, etisalat.baseUrl(), "app.sms.etisalat.base-url");
                require(problems, etisalat.username(), "app.sms.etisalat.username");
                require(problems, etisalat.password(), "app.sms.etisalat.password");
                if (!etisalat.excludedPrefixes().isEmpty()) {
                    require(problems, veriphone.apiKey(), "app.sms.veriphone.api-key");
                }
            }
        }
        if (dispatch.enabled()) {
            final Status status = dispatch.status();
            if (dispatch.batchSize() < 1) {
                problems.add("app.sms.dispatch.batch-size must be positive");
            }
            requireStatus(problems, status.pending(), "app.sms.dispatch.status.pending");
            requireStatus(problems, status.inProgress(), "app.sms.dispatch.status.in-progress");
            requireStatus(problems, status.invalidMobile(), "app.sms.dispatch.status.invalid-mobile");
            if (status.pending() != null && status.inProgress() != null && status.invalidMobile() != null
                    && new HashSet<>(List.of(status.pending(), status.inProgress(), status.invalidMobile(),
                    STATUS_SENT, STATUS_FAILED)).size() != 5) {
                problems.add("app.sms.dispatch.status pending/in-progress/invalid-mobile must be distinct from each"
                        + " other and from sent (88) and failed (1)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid SMS configuration: " + String.join("; ", problems));
        }
    }

    public boolean etisalatPreferred() {
        return isEtisalat(preferredProvider);
    }

    private static boolean isEtisalat(final String provider) {
        return provider != null && "ETISALAT".equals(provider.strip().toUpperCase(Locale.ROOT));
    }

    private static void require(final List<String> problems, final String value, final String key) {
        if (value == null || value.isBlank()) {
            problems.add(key + " is required when app.sms.live-send-enabled=true");
        }
    }

    private static void requireStatus(final List<String> problems, final Integer value, final String key) {
        if (value == null) {
            problems.add(key + " is required when app.sms.dispatch.enabled=true");
        }
    }

    private static List<String> clean(final List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::strip).toList();
    }

    private static String redact(final String secret) {
        return secret == null || secret.isEmpty() ? "" : REDACTED;
    }

    /** Request defaults; the dispatch job and the OTP feature may pass different values. */
    public record Defaults(String msgCategory, Integer expiryInSecs) {
        public Defaults {
            msgCategory = msgCategory == null ? "4.2" : msgCategory;
            expiryInSecs = expiryInSecs == null ? 300 : expiryInSecs;
        }
    }

    public record Etisalat(String baseUrl, String username, String password, List<String> excludedPrefixes,
                           List<String> excludedCountryCodes) {
        public Etisalat {
            excludedPrefixes = clean(excludedPrefixes);
            excludedCountryCodes = clean(excludedCountryCodes);
        }

        @Override
        public String toString() {
            return "Etisalat[baseUrl=" + baseUrl + ", username=" + username + ", password=" + redact(password)
                    + ", excludedPrefixes=" + excludedPrefixes + ", excludedCountryCodes=" + excludedCountryCodes + "]";
        }
    }

    public record Waselney(String sendUrl, String reportUrl, String username, String password) {
        @Override
        public String toString() {
            return "Waselney[sendUrl=" + sendUrl + ", reportUrl=" + reportUrl + ", username=" + username
                    + ", password=" + redact(password) + "]";
        }
    }

    public record Veriphone(String baseUrl, String apiKey) {
        @Override
        public String toString() {
            return "Veriphone[baseUrl=" + baseUrl + ", apiKey=" + redact(apiKey) + "]";
        }
    }

    public record Dispatch(boolean enabled, int batchSize, Status status) {
        public Dispatch {
            status = status == null ? new Status(null, null, null) : status;
        }
    }

    /** No defaults: the INSIGHT.SMS_TABLE owner has not confirmed these values. */
    public record Status(Integer pending, Integer inProgress, Integer invalidMobile) {
    }
}
