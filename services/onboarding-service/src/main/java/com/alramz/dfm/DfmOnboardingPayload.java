package com.alramz.dfm;

import com.alramz.model.DfmAddress;
import com.alramz.model.DfmCrs;
import com.alramz.model.DfmCrsCountry;
import com.alramz.model.DfmDividendPayment;
import com.alramz.model.DfmEmiratesId;
import com.alramz.model.DfmFatca;
import com.alramz.model.DfmGuardian;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.DfmPassport;
import com.alramz.model.DfmPortfolioOption;
import com.alramz.model.DfmSignature;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * DFM's {@code memberapis/onboarding} body. Components are ordered small scalars first (the security key at the
 * very top) and Base64 attachments last, so the key stays inside the masked prefix when the outbound span logger
 * truncates before masking. Never logged and has no custom toString: it carries the security key and ID images.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DfmOnboardingPayload(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("securitykey") String securityKey,
        @JsonProperty("request_mbrcode") String memberCode,
        @JsonProperty("cust_firstname") String firstName,
        @JsonProperty("cust_lastname") String lastName,
        @JsonProperty("cust_engfullname") String fullNameEn,
        @JsonProperty("cust_arfullname") String fullNameAr,
        @JsonProperty("cust_dob") String dateOfBirth,
        @JsonProperty("cust_gender") String gender,
        @JsonProperty("cust_nationality") String nationality,
        @JsonProperty("cust_familyid") String familyId,
        @JsonProperty("cust_nin") String nin,
        @JsonProperty("cust_mobile") String mobile,
        @JsonProperty("cust_email") String email,
        @JsonProperty("adr_country") String addressCountry,
        @JsonProperty("adr_city") String addressCity,
        @JsonProperty("adr_address") String addressLine,
        @JsonProperty("adr_pobox") String poBox,
        @JsonProperty("adr_zipcode") String zipCode,
        @JsonProperty("adr_phone") String addressPhone,
        @JsonProperty("pay_method") String payMethod,
        @JsonProperty("pay_aed_iban") String aedIban,
        @JsonProperty("pay_usd_iban") String usdIban,
        @JsonProperty("pay_frn_iban") String foreignAccount,
        @JsonProperty("pay_frn_swift") String foreignSwift,
        @JsonProperty("pay_cor_iban") String correspondentIban,
        @JsonProperty("pay_cor_swift") String correspondentSwift,
        @JsonProperty("pay_routecode") String routeCode,
        @JsonProperty("pay_printname") String printName,
        @JsonProperty("portfolio_options") String portfolioOptions,
        @JsonProperty("csr_resident") String crsResident,
        @JsonProperty("csr") Csr crs,
        @JsonProperty("fatca_uscitizen") String fatcaUsCitizen,
        @JsonProperty("fatca_tin") String fatcaTin,
        @JsonProperty("eid_no") String eidNumber,
        @JsonProperty("eid_expirydate") String eidExpiryDate,
        @JsonProperty("pp_no") String passportNumber,
        @JsonProperty("pp_expirydate") String passportExpiryDate,
        @JsonProperty("pp_attachment_type") String passportImageType,
        @JsonProperty("cust_signatureimage_type") String signatureImageType,
        @JsonProperty("guardian_name") String guardianName,
        @JsonProperty("guardianship_type") String guardianshipType,
        @JsonProperty("eid_attachment_front") String eidFrontImage,
        @JsonProperty("eid_attachment_back") String eidBackImage,
        @JsonProperty("pp_attachment") String passportImage,
        @JsonProperty("cust_signatureimage") String signatureImage,
        @JsonProperty("proof_of_guardianship") String guardianProof) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Csr(@JsonProperty("csr_countries") List<CsrCountry> countries) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CsrCountry(
            @JsonProperty("csr_country") String country,
            @JsonProperty("csr_havetin") String hasTin,
            @JsonProperty("csr_tin") String tin,
            @JsonProperty("csr_notinreason") String noTinReason,
            @JsonProperty("csr_whynotinissued") String whyNoTinIssued) {
    }

    public static DfmOnboardingPayload from(DfmInvestorRegistrationRequest r, String memberCode, String securityKey) {
        DfmAddress address = r.getAddress();
        DfmDividendPayment pay = r.getDividendPayment();
        DfmCrs crs = r.getCrs();
        DfmFatca fatca = r.getFatca();
        DfmEmiratesId eid = r.getEmiratesId();
        DfmPassport passport = r.getPassport();
        DfmSignature signature = r.getSignature();
        DfmGuardian guardian = r.getGuardian();
        return new DfmOnboardingPayload(
                String.valueOf(r.getRequestId()),
                securityKey,
                memberCode,
                r.getFirstName(),
                r.getLastName(),
                r.getFullNameEn(),
                r.getFullNameAr(),
                date(r.getDateOfBirth()),
                r.getGender() == null ? null : r.getGender().getValue(),
                r.getNationality(),
                r.getFamilyId(),
                r.getNin(),
                dialPrefix(r.getMobile()),
                r.getEmail(),
                address.getCountry(),
                address.getCity(),
                address.getLine(),
                address.getPoBox(),
                address.getZipCode(),
                dialPrefix(address.getPhone()),
                pay.getMethod() == DfmDividendPayment.MethodEnum.IVESTOR ? "iVESTOR" : "bank",
                pay.getAedIban(),
                pay.getUsdIban(),
                pay.getForeignAccount(),
                pay.getForeignSwift(),
                pay.getCorrespondentIban(),
                pay.getCorrespondentSwift(),
                pay.getRouteCode(),
                pay.getIvestorPrintName(),
                portfolioOptions(r.getPortfolioOptions()),
                crs == null ? null : yesNo(crs.getResident()),
                crs == null || crs.getCountries() == null || crs.getCountries().isEmpty() ? null
                        : new Csr(crs.getCountries().stream().map(DfmOnboardingPayload::csrCountry).toList()),
                fatca == null ? null : yesNo(fatca.getUsPerson()),
                fatca == null ? null : fatca.getTin(),
                eid == null ? null : eid.getEidNumber(),
                eid == null ? null : date(eid.getExpiryDate()),
                passport == null ? null : passport.getPassportNumber(),
                passport == null ? null : date(passport.getExpiryDate()),
                passport == null ? null : passport.getImageType().getValue(),
                signature.getImageType().getValue(),
                guardian == null ? null : guardian.getName(),
                guardian == null ? null : guardian.getType(),
                eid == null ? null : eid.getFrontImage(),
                eid == null ? null : eid.getBackImage(),
                passport == null ? null : passport.getImage(),
                signature.getImage(),
                guardian == null ? null : guardian.getProofDocument());
    }

    private static CsrCountry csrCountry(DfmCrsCountry c) {
        return new CsrCountry(c.getCountry(), yesNo(c.getHasTin()), c.getTin(), c.getNoTinReason(), c.getWhyNoTinIssued());
    }

    /** E.164 to DFM's international format: +971501234567 becomes 00971501234567. */
    private static String dialPrefix(String number) {
        return number != null && number.startsWith("+") ? "00" + number.substring(1) : number;
    }

    /** Always CASH before MARGIN, lower-case, comma-joined: "cash" or "cash,margin". */
    private static String portfolioOptions(Set<DfmPortfolioOption> options) {
        return Stream.of(DfmPortfolioOption.CASH, DfmPortfolioOption.MARGIN)
                .filter(options::contains)
                .map(option -> option.getValue().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(","));
    }

    private static String yesNo(Boolean value) {
        if (value == null) {
            return null;
        }
        return value ? "Y" : "N";
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }
}
