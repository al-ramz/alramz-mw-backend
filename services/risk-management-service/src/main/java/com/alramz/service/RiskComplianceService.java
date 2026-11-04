package com.alramz.service;

import com.alramz.exception.ErrorCode;
import com.alramz.exception.RiskComplianceException;
import com.alramz.knowyourcountry.KnowYourCountryClient;
import com.alramz.knowyourcountry.KnowYourCountryRecord;
import com.alramz.knowyourcountry.KnowYourCountryResult;
import com.alramz.knowyourcountry.RiskBand;
import com.alramz.model.Country;
import com.alramz.model.CountryRating;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
@Service
public class RiskComplianceService {

    static final String UNMAPPED_COUNTER = "risk.compliance.country.rating.unmapped";

    private static final Codes COUNTRY_CODES =
            new Codes(ErrorCode.CLS001, ErrorCode.CLS002, ErrorCode.CLS003, ErrorCode.CLS004);
    private static final Codes RATING_CODES =
            new Codes(ErrorCode.CRT001, ErrorCode.CRT002, ErrorCode.CRT003, ErrorCode.CRT004);

    private final KnowYourCountryClient client;
    private final Counter unmappedRatings;

    public RiskComplianceService(KnowYourCountryClient client, MeterRegistry meterRegistry) {
        this.client = client;
        // No label tag: vendor labels are unbounded (cardinality). The label is in the WARN line.
        this.unmappedRatings = Counter.builder(UNMAPPED_COUNTER)
                .description("KnowYourCountry rows omitted because their risk_level has no band")
                .register(meterRegistry);
    }

    public List<Country> getCountries() {
        List<Country> countries = load(client::fetchCountries, COUNTRY_CODES, rows -> rows.stream()
                .map(row -> new Country().name(row.name()).iso2(row.isoAlpha2()).iso3(row.isoAlpha3()))
                .toList());
        log.info("Returning {} countries", countries.size());
        return countries;
    }

    public List<CountryRating> getCountryRatings() {
        List<CountryRating> ratings = load(client::fetchCountryRatings, RATING_CODES, this::toRatings);
        log.info("Returning {} country ratings", ratings.size());
        return ratings;
    }

    private List<CountryRating> toRatings(List<KnowYourCountryRecord> rows) {
        List<CountryRating> ratings = new ArrayList<>(rows.size());
        for (KnowYourCountryRecord row : rows) {
            Optional<RiskBand> band = RiskBand.fromVendorLabel(row.riskLevel());
            if (band.isEmpty()) {
                log.warn("Unmapped KnowYourCountry risk_level '{}' for iso2 '{}'; country omitted",
                        forLog(row.riskLevel()), forLog(row.isoAlpha2()));
                unmappedRatings.increment();
                continue;
            }
            ratings.add(new CountryRating().name(row.name()).iso2(row.isoAlpha2()).iso3(row.isoAlpha3())
                    .rating(band.get().rating())
                    .riskLevel(CountryRating.RiskLevelEnum.fromValue(band.get().code())));
        }
        return ratings;
    }

    /** Vendor text in a plain-text log line: control chars neutralized (no forged lines), length capped. */
    static String forLog(String vendorValue) {
        if (vendorValue == null) {
            return null;
        }
        String safe = vendorValue.replaceAll("\\p{Cntrl}", "_");
        return safe.length() > 64 ? safe.substring(0, 64) + "..." : safe;
    }

    private static <T> List<T> load(Supplier<KnowYourCountryResult> call, Codes codes,
                                    Function<List<KnowYourCountryRecord>, List<T>> mapper) {
        KnowYourCountryResult result;
        try {
            result = call.get();
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - any unexpected client failure is xxx004 by contract
            throw new RiskComplianceException(codes.internal(), e);
        }
        return switch (result) {
            case KnowYourCountryResult.Success(var rows) -> map(rows, mapper, codes);
            case KnowYourCountryResult.Rejected rejected -> throw new RiskComplianceException(codes.rejected(), null);
            case KnowYourCountryResult.Malformed malformed -> throw new RiskComplianceException(codes.malformed(), null);
            case KnowYourCountryResult.Unavailable(var cause) ->
                    throw new RiskComplianceException(codes.unavailable(), cause);
        };
    }

    private static <T> List<T> map(List<KnowYourCountryRecord> rows,
                                   Function<List<KnowYourCountryRecord>, List<T>> mapper, Codes codes) {
        try {
            return mapper.apply(rows);
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException - a mapping bug is xxx004 by contract
            throw new RiskComplianceException(codes.internal(), e);
        }
    }

    private record Codes(ErrorCode rejected, ErrorCode malformed, ErrorCode unavailable, ErrorCode internal) {
    }
}
