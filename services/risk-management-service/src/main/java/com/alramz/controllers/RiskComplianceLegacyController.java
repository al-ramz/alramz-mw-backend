package com.alramz.controllers;

import com.alramz.api.RiskComplianceLegacyApi;
import com.alramz.exception.RiskComplianceException;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.Country;
import com.alramz.model.CountryListResponse;
import com.alramz.model.CountryRating;
import com.alramz.model.LegacyCountryListEnvelope;
import com.alramz.model.LegacyCountryRating;
import com.alramz.model.LegacyCountryRatingData;
import com.alramz.model.LegacyCountryRatingEnvelope;
import com.alramz.model.LegacyRiskComplianceRequest;
import com.alramz.service.RiskComplianceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Legacy webMethods-compatible POST routes: always HTTP 200, outcome in the inner responseCode
 * (200 / 1018 / 500), {@code correlationID} spelling, string rating, response omitted when empty or failed.
 */
@Slf4j
@RestController
@RequestMapping(RiskComplianceLegacyController.BASE_PATH)
@RequiredArgsConstructor
public class RiskComplianceLegacyController implements RiskComplianceLegacyApi {

    static final String BASE_PATH = "/api/v1";
    /** Built from the generated route constants, so the matcher can't drift from the real mappings. */
    private static final String COUNTRIES_PATH = BASE_PATH + PATH_GET_COUNTRIES_LEGACY;
    private static final String RATINGS_PATH = BASE_PATH + PATH_GET_COUNTRY_RATINGS_LEGACY;
    private static final String SUCCESS_CODE = "200";
    private static final String SUCCESS_MESSAGE = "success";
    private static final String VENDOR_FAILURE_CODE = "1018";
    private static final String INTERNAL_ERROR_CODE = "500";
    private static final String INTERNAL_ERROR_MESSAGE = "Internal Server Error";
    /** The legacy pipeline's "has a value" test. */
    private static final Pattern NON_SPACE = Pattern.compile("[^ ]");
    /**
     * The body value goes into MDC (every plain-text log line) and the outbound X-Correlation-ID header, and
     * unlike the header path it has no Tomcat CR/LF or size check: printable ASCII, at most 128 chars, only.
     */
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("\\p{Print}{1,128}");

    private final RiskComplianceService riskComplianceService;

    @Override
    @JwtSecured(roles = "APP_RISK_MANAGEMENT")
    @Loggable
    public ResponseEntity<LegacyCountryListEnvelope> getCountriesLegacy(LegacyRiskComplianceRequest request) {
        LegacyCountryListEnvelope envelope = new LegacyCountryListEnvelope().correlationID(resolveCorrelationId(request));
        try {
            List<Country> countries = riskComplianceService.getCountries();
            envelope.responseCode(SUCCESS_CODE).responseMessage(SUCCESS_MESSAGE);
            if (!countries.isEmpty()) {
                envelope.response(new CountryListResponse().countryList(countries));
            }
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - legacy contract: every failure is an HTTP 200 envelope
            Outcome outcome = failure(e, "Failed to get countrylist");
            envelope.responseCode(outcome.code()).responseMessage(outcome.message());
        }
        return ResponseEntity.ok(envelope);
    }

    @Override
    @JwtSecured(roles = "APP_RISK_MANAGEMENT")
    @Loggable
    public ResponseEntity<LegacyCountryRatingEnvelope> getCountryRatingsLegacy(LegacyRiskComplianceRequest request) {
        LegacyCountryRatingEnvelope envelope =
                new LegacyCountryRatingEnvelope().correlationID(resolveCorrelationId(request));
        try {
            List<LegacyCountryRating> ratings = riskComplianceService.getCountryRatings().stream()
                    .map(RiskComplianceLegacyController::toLegacy)
                    .toList();
            envelope.responseCode(SUCCESS_CODE).responseMessage(SUCCESS_MESSAGE);
            if (!ratings.isEmpty()) {
                envelope.response(new LegacyCountryRatingData().countryRating(ratings));
            }
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - legacy contract: every failure is an HTTP 200 envelope
            Outcome outcome = failure(e, "Failed to get countryRating");
            envelope.responseCode(outcome.code()).responseMessage(outcome.message());
        }
        return ResponseEntity.ok(envelope);
    }

    /** E14: malformed JSON / unsupported body type still answers HTTP 200 + legacy "500". */
    @ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<Object> handleUnreadableBody(Exception ex, HttpServletRequest request) {
        log.warn("Legacy request body rejected: {}", ex.getClass().getSimpleName());
        return legacyInternalError(request);
    }

    /** True for a POST to one of the two legacy routes. */
    public static boolean isLegacyRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return "POST".equals(request.getMethod()) && uri != null
                && (uri.endsWith(COUNTRIES_PATH) || uri.endsWith(RATINGS_PATH));
    }

    /** HTTP 200 + the legacy "500" envelope of the route's own type. */
    public static ResponseEntity<Object> legacyInternalError(HttpServletRequest request) {
        String correlationId = fallbackCorrelationId();
        String uri = request.getRequestURI();
        Object body = uri != null && uri.endsWith(RATINGS_PATH)
                ? new LegacyCountryRatingEnvelope().correlationID(correlationId)
                        .responseCode(INTERNAL_ERROR_CODE).responseMessage(INTERNAL_ERROR_MESSAGE)
                : new LegacyCountryListEnvelope().correlationID(correlationId)
                        .responseCode(INTERNAL_ERROR_CODE).responseMessage(INTERNAL_ERROR_MESSAGE);
        return ResponseEntity.ok(body);
    }

    private static LegacyCountryRating toLegacy(CountryRating rating) {
        return new LegacyCountryRating()
                .name(rating.getName())
                .iso2(rating.getIso2())
                .iso3(rating.getIso3())
                .rating(String.valueOf(rating.getRating()))
                .riskLevel(LegacyCountryRating.RiskLevelEnum.fromValue(
                        Objects.requireNonNull(rating.getRiskLevel(), "riskLevel").getValue()));
    }

    private static Outcome failure(RuntimeException e, String vendorFailureMessage) {
        if (e instanceof RiskComplianceException typed) {
            return typed.getErrorCode().isVendorRejection()
                    ? new Outcome(VENDOR_FAILURE_CODE, vendorFailureMessage)
                    : new Outcome(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
        }
        log.error("Unexpected failure on a legacy RiskCompliance route", e);
        return new Outcome(INTERNAL_ERROR_CODE, INTERNAL_ERROR_MESSAGE);
    }

    private static String resolveCorrelationId(LegacyRiskComplianceRequest request) {
        String fromBody = request != null ? request.getCorrelationID() : null;
        if (fromBody != null && NON_SPACE.matcher(fromBody).find()) {
            if (SAFE_CORRELATION_ID.matcher(fromBody).matches()) {
                MDCUtil.putCorrelationId(fromBody);
                return fromBody;
            }
            // Never log the value itself: it is what was rejected (log injection).
            log.warn("Ignoring legacy body correlationID: not printable ASCII or longer than 128 chars");
        }
        return fallbackCorrelationId();
    }

    private static String fallbackCorrelationId() {
        String fromMdc = MDCUtil.getCorrelationId();
        return fromMdc != null && !fromMdc.isBlank() ? fromMdc : UUID.randomUUID().toString();
    }

    private record Outcome(String code, String message) {
    }
}
