package com.alramz.service.impl;

import com.alramz.dfm.DfmMemberApiClient;
import com.alramz.dfm.DfmOnboardingOutcome;
import com.alramz.dfm.DfmOnboardingResponse;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.DfmInvestorErrorCode;
import com.alramz.exception.DfmRejectedException;
import com.alramz.exception.DfmUnavailableException;
import com.alramz.model.DfmAddress;
import com.alramz.model.DfmCrsCountry;
import com.alramz.model.DfmDividendPayment;
import com.alramz.model.DfmEmiratesId;
import com.alramz.model.DfmGuardian;
import com.alramz.model.DfmInvestorRegistration;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.DfmPassport;
import com.alramz.model.DfmPortfolioOption;
import com.alramz.model.DfmSignature;
import com.alramz.service.DfmInvestorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Cross-field rules R1-R10 (Bean Validation has already run), then the DFM call and outcome mapping. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DfmInvestorServiceImpl implements DfmInvestorService {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final int ADULT_AGE = 18;
    private static final String UAE = "AE";
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    /** Arab League member states (rule R3). Business to confirm. */
    private static final Set<String> ARAB_LEAGUE = Set.of("AE", "BH", "DJ", "DZ", "EG", "IQ", "JO", "KM", "KW", "LB",
            "LY", "MA", "MR", "OM", "PS", "QA", "SA", "SD", "SO", "SY", "TN", "YE");

    private static final String DUPLICATE_DEFAULT =
            "A registration with this requestId is still in progress at DFM; retry later with the same requestId";

    private enum FileType { JPEG, PNG, PDF }

    private final DfmMemberApiClient client;

    @Override
    public DfmInvestorRegistration register(DfmInvestorRegistrationRequest request) {
        validate(request);
        return switch (client.onboard(request)) {
            case DfmOnboardingOutcome.Success success -> toRegistration(success.body(), request.getRequestId());
            case DfmOnboardingOutcome.Rejected rejected -> throw rejection(rejected);
            case DfmOnboardingOutcome.Unavailable unavailable -> throw new DfmUnavailableException(
                    unavailableCode(unavailable.reason()), "DFM unavailable: " + unavailable.reason(), unavailable.cause());
        };
    }

    // ---------------------------------------------------------------- rules

    private static void validate(DfmInvestorRegistrationRequest r) {
        DfmAddress address = r.getAddress();
        DfmDividendPayment pay = r.getDividendPayment();

        check(ISO_COUNTRIES.contains(address.getCountry()), "address.country",
                "address.country must be an ISO 3166-1 alpha-2 code", DfmInvestorErrorCode.DFN001);
        check(!isBlank(address.getCity()), "address.city",
                "address.city is required and must be at most 25 characters", DfmInvestorErrorCode.DFN002);
        rule(ISO_COUNTRIES.contains(r.getNationality()), "nationality", "nationality must be an ISO 3166-1 alpha-2 code");

        rule(!UAE.equals(r.getNationality()) || r.getEmiratesId() != null, "emiratesId",
                "emiratesId is required when nationality is AE");
        rule(r.getEmiratesId() != null || r.getPassport() != null, "passport",
                "passport is required when emiratesId is absent");
        rule(!ARAB_LEAGUE.contains(r.getNationality()) || !isBlank(r.getFullNameAr()), "fullNameAr",
                "fullNameAr is required for an Arab League nationality");
        rule(!UAE.equals(address.getCountry()) || !isBlank(address.getPoBox()), "address.poBox",
                "address.poBox is required when address.country is AE");
        rule(!isBlank(address.getPoBox()) || !isBlank(address.getLine()), "address.line",
                "address.line is required when address.poBox is absent");
        rule(pay.getMethod() != DfmDividendPayment.MethodEnum.BANK || !isBlank(pay.getAedIban()), "dividendPayment.aedIban",
                "dividendPayment.aedIban is required when method is BANK");
        rule(pay.getMethod() != DfmDividendPayment.MethodEnum.IVESTOR || !isBlank(pay.getIvestorPrintName()),
                "dividendPayment.ivestorPrintName", "dividendPayment.ivestorPrintName is required when method is IVESTOR");
        rule(!r.getPortfolioOptions().contains(DfmPortfolioOption.MARGIN) || r.getPortfolioOptions().contains(DfmPortfolioOption.CASH),
                "portfolioOptions", "portfolioOptions must include CASH when MARGIN is requested");
        rule(isAdult(r.getDateOfBirth()) || r.getGuardian() != null, "guardian",
                "guardian is required when the investor is under 18");

        if (r.getCrs() != null && r.getCrs().getCountries() != null) {
            List<DfmCrsCountry> countries = r.getCrs().getCountries();
            for (int i = 0; i < countries.size(); i++) {
                String field = "crs.countries[" + i + "].country";
                rule(ISO_COUNTRIES.contains(countries.get(i).getCountry()), field, field + " must be an ISO 3166-1 alpha-2 code");
            }
        }

        validateFiles(r);
    }

    /** R10: every Base64 field decodes to a JPEG, PNG or PDF, matching its imageType where there is one. */
    private static void validateFiles(DfmInvestorRegistrationRequest r) {
        DfmEmiratesId eid = r.getEmiratesId();
        if (eid != null) {
            fileType(eid.getFrontImage(), "emiratesId.frontImage");
            fileType(eid.getBackImage(), "emiratesId.backImage");
        }
        DfmPassport passport = r.getPassport();
        if (passport != null) {
            matchType(fileType(passport.getImage(), "passport.image"), passport.getImageType().getValue(), "passport.image");
        }
        DfmSignature signature = r.getSignature();
        matchType(fileType(signature.getImage(), "signature.image"), signature.getImageType().getValue(), "signature.image");
        DfmGuardian guardian = r.getGuardian();
        if (guardian != null) {
            fileType(guardian.getProofDocument(), "guardian.proofDocument");
        }
    }

    private static FileType fileType(String base64, String field) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw invalid(field, field + " must be valid Base64");
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return FileType.JPEG;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47)) {
            return FileType.PNG;
        }
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) {
            return FileType.PDF;
        }
        throw invalid(field, field + " must be a JPEG, PNG or PDF");
    }

    private static void matchType(FileType detected, String imageType, String field) {
        FileType declared = switch (imageType) {
            case "jpg", "jpeg" -> FileType.JPEG;
            case "png" -> FileType.PNG;
            default -> FileType.PDF;
        };
        rule(detected == declared, field, field + " content does not match its imageType " + imageType);
    }

    private static boolean startsWith(byte[] bytes, int... magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if ((bytes[i] & 0xFF) != magic[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAdult(LocalDate dateOfBirth) {
        return Period.between(dateOfBirth, LocalDate.now(DUBAI)).getYears() >= ADULT_AGE;
    }

    private static void rule(boolean ok, String field, String message) {
        check(ok, field, message, DfmInvestorErrorCode.DFN003);
    }

    private static void check(boolean ok, String field, String message, DfmInvestorErrorCode code) {
        if (!ok) {
            throw new ApplicationException(field, message, code.code());
        }
    }

    private static ApplicationException invalid(String field, String message) {
        return new ApplicationException(field, message, DfmInvestorErrorCode.DFN003.code());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ---------------------------------------------------------------- outcomes

    private static DfmInvestorRegistration toRegistration(DfmOnboardingResponse body, UUID requestId) {
        warnIfNotUuid("memberReference", body.memberReference(), requestId);
        warnIfNotUuid("dfmReference", body.dfmReference(), requestId);
        return new DfmInvestorRegistration()
                .nin(body.custNin())
                .cashTradingNumber(body.cashAcc())
                .marginTradingNumber(body.marginAcc())
                .memberReference(body.memberReference())
                .dfmReference(body.dfmReference())
                .trackingNumber(body.trackingNo())
                .dfmMessage(body.responseMsg());
    }

    private static void warnIfNotUuid(String field, String value, UUID requestId) {
        if (value == null) {
            return;
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("DFM returned a non-UUID {} for request_id={}; passing it through", field, requestId);
        }
    }

    private static DfmRejectedException rejection(DfmOnboardingOutcome.Rejected rejected) {
        boolean hasMessage = !isBlank(rejected.providerMessage());
        if (rejected.duplicateInProgress()) {
            return new DfmRejectedException(DfmInvestorErrorCode.DFN007, hasMessage ? rejected.providerMessage() : DUPLICATE_DEFAULT);
        }
        return new DfmRejectedException(DfmInvestorErrorCode.DFN006, hasMessage ? rejected.providerMessage()
                : "DFM rejected the request (HTTP " + rejected.providerCode() + ")");
    }

    private static DfmInvestorErrorCode unavailableCode(DfmOnboardingOutcome.Reason reason) {
        return switch (reason) {
            case TIMEOUT -> DfmInvestorErrorCode.DFN011;
            case CIRCUIT_OPEN -> DfmInvestorErrorCode.DFN014;
            case BAD_RESPONSE -> DfmInvestorErrorCode.DFN010;
        };
    }
}
