package com.alramz.service.impl;

import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsRecipientResult.ServiceProviderEnum;
import com.alramz.model.SmsRecipientResult.StatusEnum;
import com.alramz.model.SmsSendRequest;
import com.alramz.model.SmsSendResult;
import com.alramz.service.SmsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SMS router (spec 3.2): validates, routes each number to Waselney or Etisalat, sends Waselney numbers first,
 * and returns one result per number. Simulated mode (live-send-enabled=false) makes no provider call at all.
 * Logs carry masked numbers (last 4 digits) and never the message body.
 */
@Slf4j
@Service
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity"})
public class SmsServiceImpl implements SmsService {

    private static final Pattern SEPARATORS = Pattern.compile("[;,/\\s]+");
    private static final Pattern NUMBER = Pattern.compile("^[1-9][0-9]{7,14}$");
    private static final Pattern MSG_CATEGORY = Pattern.compile("^[0-9]{1,3}(\\.[0-9]{1,3})?$");
    private static final int MAX_EXPIRY_SECS = 86_400;
    private static final Pattern PROVIDER_CODE = Pattern.compile("^[A-Za-z0-9._-]{1,16}$");

    private final SmsProperties properties;
    private final EtisalatSmsClient etisalat;
    private final WaselneySmsClient waselney;
    private final VeriPhoneClient veriPhone;

    public SmsServiceImpl(final SmsProperties properties, final EtisalatSmsClient etisalat,
                          final WaselneySmsClient waselney, final VeriPhoneClient veriPhone) {
        this.properties = properties;
        this.etisalat = etisalat;
        this.waselney = waselney;
        this.veriPhone = veriPhone;
    }

    @Override
    public Outcome sendWithOutcome(final SmsSendRequest request) {
        final Map<String, Integer> numbers = numbersWithEntryIndex(request.getMobileNumbers());
        final List<String> missing = new ArrayList<>();
        if (request.getMessageBody() == null || request.getMessageBody().isBlank()) {
            missing.add("messageBody");
        }
        if (numbers.isEmpty()) {
            missing.add("mobileNumbers");
        }
        if (!missing.isEmpty()) {
            throw new SmsException("SMS001", "Request failed due to missing: " + String.join(", ", missing));
        }
        if (request.getMessageBody().length() > properties.maxMessageLength()) {
            throw new SmsException("SMS003", "Field length exceeded: messageBody");
        }
        if (numbers.size() > properties.maxRecipients()) {
            throw new SmsException("SMS004", "Number of recipients exceeds " + properties.maxRecipients());
        }
        numbers.forEach((number, entry) -> {
            if (!NUMBER.matcher(number).matches()) {
                throw new SmsException("SMS002", "Invalid mobile number in: mobileNumbers[" + entry + "]");
            }
        });
        final String msgCategory = request.getMsgCategory() == null
                ? properties.defaults().msgCategory() : request.getMsgCategory();
        if (!MSG_CATEGORY.matcher(msgCategory).matches()) {
            throw new SmsException("SMS005", "Invalid field: msgCategory");
        }
        final int expiry = request.getExpiryInSecs() == null
                ? properties.defaults().expiryInSecs() : request.getExpiryInSecs();
        if (expiry < 1 || expiry > MAX_EXPIRY_SECS) {
            throw new SmsException("SMS005", "Invalid field: expiryInSecs");
        }

        final String correlationId = correlationId();
        final boolean live = properties.liveSendEnabled();
        final Map<String, SmsRecipientResult> results = new LinkedHashMap<>();
        numbers.keySet().forEach(n -> results.put(n, new SmsRecipientResult().mobileNumber(n).serviceProvider(route(n, live))));
        boolean anyRejected = false;
        for (ServiceProviderEnum provider : List.of(ServiceProviderEnum.WASELNEY, ServiceProviderEnum.ETISALAT)) {
            for (SmsRecipientResult result : results.values()) {
                if (result.getServiceProvider() == provider) {
                    anyRejected |= send(result, request.getMessageBody(), msgCategory, expiry, correlationId, live);
                }
            }
        }
        return new Outcome(new SmsSendResult().correlationId(correlationId).simulated(!live)
                .results(new ArrayList<>(results.values())), anyRejected);
    }

    /** Sends one number and fills its status; returns true when the provider rejected it. */
    private boolean send(final SmsRecipientResult result, final String message, final String msgCategory,
                         final int expiry, final String correlationId, final boolean live) {
        final String number = result.getMobileNumber();
        if (!live) {
            // Simulated provider responses: Waselney 1701|<number>|..., Etisalat 201 -> messageId is the number.
            result.status(StatusEnum.SENT).messageId(number);
            log.info("SMS simulated provider={} number={} correlationId={}", result.getServiceProvider(), mask(number),
                    correlationId);
            return false;
        }
        try {
            final String messageId = result.getServiceProvider() == ServiceProviderEnum.WASELNEY
                    ? waselney.send(number, message)
                    : etisalat.send(number, message, msgCategory, expiry, correlationId);
            result.status(StatusEnum.SENT).messageId(messageId);
            log.info("SMS sent provider={} number={} correlationId={}", result.getServiceProvider(), mask(number),
                    correlationId);
            return false;
        } catch (SmsException e) {
            result.status(StatusEnum.FAILED);
            log.warn("SMS failed provider={} number={} code={} correlationId={}", result.getServiceProvider(),
                    mask(number), e.getCode(), correlationId);
            return "SMS009".equals(e.getCode());
        }
    }

    private ServiceProviderEnum route(final String number, final boolean live) {
        if (!properties.etisalatPreferred()) {
            return ServiceProviderEnum.WASELNEY;
        }
        final SmsProperties.Etisalat config = properties.etisalat();
        if (!live || config.excludedPrefixes().stream().noneMatch(number::startsWith)) {
            return ServiceProviderEnum.ETISALAT;
        }
        // A failed lookup (empty) routes to Etisalat, the legacy default for non-matching numbers.
        return veriPhone.countryCode(number)
                .filter(code -> config.excludedCountryCodes().stream().anyMatch(code::equalsIgnoreCase))
                .map(code -> ServiceProviderEnum.WASELNEY)
                .orElse(ServiceProviderEnum.ETISALAT);
    }

    /** De-duplicated numbers in request order, each with the 1-based index of the entry it came from. */
    private static Map<String, Integer> numbersWithEntryIndex(final List<String> entries) {
        final Map<String, Integer> numbers = new LinkedHashMap<>();
        if (entries == null) {
            return numbers;
        }
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i) == null) {
                continue;
            }
            for (String token : SEPARATORS.split(entries.get(i).strip())) {
                if (!token.isEmpty()) {
                    numbers.putIfAbsent(token.startsWith("+") ? token.substring(1) : token, i + 1);
                }
            }
        }
        return numbers;
    }

    /** The same number rule sendWithOutcome enforces (SMS002), for callers that pre-check (OtpService). */
    public static boolean isValidMobile(final String number) {
        return number != null && NUMBER.matcher(number).matches();
    }

    public static String mask(final String number) {
        return number == null || number.length() < 4 ? "***" : "***" + number.substring(number.length() - 4);
    }

    private static String correlationId() {
        final String correlationId = MDCUtil.getCorrelationId();
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }

    /**
     * Provider RestClient: the static builder (no Boot customizers, no observation, so credential-bearing URLs
     * never reach traces; no logging interceptor) with the app.sms timeouts.
     */
    static RestClient.Builder restClientBuilder(final SmsProperties properties) {
        final SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * A provider-supplied result code, safe for logs and exception messages: kept only if it is a short token,
     * because an unexpected response body (an error page, an echoed request URL with credentials, CR/LF) must
     * never be logged.
     */
    static String providerCode(final Object value) {
        final String text = value == null ? "" : value.toString();
        if (text.isEmpty()) {
            return "empty";
        }
        return PROVIDER_CODE.matcher(text).matches() ? text : "unexpected";
    }

    /**
     * Maps a provider call failure without its message, URI or cause (they can carry credentials): an HTTP error
     * status is a rejection (SMS009), an I/O failure is unavailability (SMS010), anything else a rejection.
     */
    static SmsException providerFailure(final String provider, final RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            final int status = response.getStatusCode().value();
            log.warn("SMS provider rejected provider={} httpStatus={}", provider, status);
            return new SmsException("SMS009", "SMS provider rejected " + provider + " " + status);
        }
        final String type = e.getMostSpecificCause().getClass().getSimpleName();
        if (e instanceof ResourceAccessException) {
            log.warn("SMS provider unavailable provider={} error={}", provider, type);
            return new SmsException("SMS010", "SMS provider unavailable " + provider + " " + type);
        }
        log.warn("SMS provider response unreadable provider={} error={}", provider, type);
        return new SmsException("SMS009", "SMS provider rejected " + provider + " " + type);
    }
}
