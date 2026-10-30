package com.alramz.service.impl;

import com.alramz.dfm.DfmMemberApiClient;
import com.alramz.dfm.DfmOnboardingOutcome;
import com.alramz.dfm.DfmOnboardingResponse;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.DfmInvestorErrorCode;
import com.alramz.exception.DfmRejectedException;
import com.alramz.exception.DfmUnavailableException;
import com.alramz.model.DfmAddress;
import com.alramz.model.DfmCrs;
import com.alramz.model.DfmCrsCountry;
import com.alramz.model.DfmDividendPayment;
import com.alramz.model.DfmEmiratesId;
import com.alramz.model.DfmGuardian;
import com.alramz.model.DfmInvestorRegistration;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.DfmPassport;
import com.alramz.model.DfmPortfolioOption;
import com.alramz.model.DfmSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DfmInvestorServiceImplTest {

    /** 1x1 PNG. */
    public static final String PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
    /** Starts with FF D8 FF. */
    public static final String JPEG = "/9j/4AAQ";
    /** "%PDF-1.4". */
    public static final String PDF = "JVBERi0xLjQ=";

    @Mock
    private DfmMemberApiClient client;

    private DfmInvestorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DfmInvestorServiceImpl(client);
    }

    /** A request that passes every rule: AE national, EID, AE address with PO box, BANK dividends, CASH only. */
    public static DfmInvestorRegistrationRequest validRequest() {
        return new DfmInvestorRegistrationRequest()
                .requestId(UUID.fromString("3f2b8c1e-7d4a-4f6b-9c2e-1a0b9d8e7f60"))
                .firstName("Test").lastName("User").fullNameEn("Test User").fullNameAr("مستخدم تجريبي")
                .dateOfBirth(LocalDate.of(1990, 1, 15))
                .gender(DfmInvestorRegistrationRequest.GenderEnum.M)
                .nationality("AE")
                .mobile("+971500000000")
                .email("test.user@example.com")
                .address(new DfmAddress().country("AE").city("Dubai").line("Example Street 1").poBox("00000"))
                .dividendPayment(new DfmDividendPayment().method(DfmDividendPayment.MethodEnum.BANK)
                        .aedIban("AE000000000000000000000"))
                .portfolioOptions(new LinkedHashSet<>(Set.of(DfmPortfolioOption.CASH)))
                .emiratesId(new DfmEmiratesId().eidNumber("784-0000-0000000-0").expiryDate(LocalDate.of(2030, 1, 1))
                        .frontImage(PNG).backImage(PNG))
                .signature(new DfmSignature().imageType(DfmSignature.ImageTypeEnum.PNG).image(PNG));
    }

    private static DfmInvestorRegistrationRequest valid(Consumer<DfmInvestorRegistrationRequest> change) {
        DfmInvestorRegistrationRequest request = validRequest();
        change.accept(request);
        return request;
    }

    private void assertRejected(DfmInvestorRegistrationRequest request, String field, DfmInvestorErrorCode code) {
        assertThatThrownBy(() -> service.register(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", field)
                .hasFieldOrPropertyWithValue("serviceErrorResponseCode", code.name());
        verifyNoInteractions(client);
    }

    private static DfmOnboardingResponse dfmOk(String memberReference, String dfmReference) {
        return new DfmOnboardingResponse("200", "OK", memberReference, dfmReference, "2313258142", "9400000", null, "EGA0000000");
    }

    // ---------- success ----------

    @Test
    void register_shouldMapAllSevenFieldsOnSuccess() {
        String member = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d";
        String dfm = "1b2c3d4e-5f60-4718-89ab-cdef01234567";
        DfmInvestorRegistrationRequest request = validRequest();
        when(client.onboard(request)).thenReturn(new DfmOnboardingOutcome.Success(
                new DfmOnboardingResponse("200", "OK", member, dfm, "2313258142", "9400000", "9400001", "EGA0000000")));

        DfmInvestorRegistration result = service.register(request);

        assertThat(result.getNin()).isEqualTo("EGA0000000");
        assertThat(result.getCashTradingNumber()).isEqualTo("9400000");
        assertThat(result.getMarginTradingNumber()).isEqualTo("9400001");
        assertThat(result.getMemberReference()).isEqualTo(member);
        assertThat(result.getDfmReference()).isEqualTo(dfm);
        assertThat(result.getTrackingNumber()).isEqualTo("2313258142");
        assertThat(result.getDfmMessage()).isEqualTo("OK");
    }

    @Test
    void register_shouldPassNonUuidReferencesThrough() {
        when(client.onboard(any())).thenReturn(new DfmOnboardingOutcome.Success(dfmOk("MBR-1", "not-a-uuid")));

        DfmInvestorRegistration result = service.register(validRequest());

        assertThat(result.getMemberReference()).isEqualTo("MBR-1");
        assertThat(result.getDfmReference()).isEqualTo("not-a-uuid");
    }

    @Test
    void register_shouldAcceptPassportOnlyNonArabAdultWithIvestorAndCashMargin() {
        DfmInvestorRegistrationRequest request = valid(r -> r
                .nationality("GB").fullNameAr(null).emiratesId(null)
                .passport(new DfmPassport().passportNumber("X0000000").expiryDate(LocalDate.of(2030, 1, 1))
                        .imageType(DfmPassport.ImageTypeEnum.JPG).image(JPEG))
                .address(new DfmAddress().country("GB").city("London").poBox("PO1"))
                .dividendPayment(new DfmDividendPayment().method(DfmDividendPayment.MethodEnum.IVESTOR).ivestorPrintName("TEST USER"))
                .portfolioOptions(new LinkedHashSet<>(List.of(DfmPortfolioOption.MARGIN, DfmPortfolioOption.CASH)))
                .crs(new DfmCrs().countries(List.of(new DfmCrsCountry().country("GB").hasTin(true).tin("T1"))))
                .signature(new DfmSignature().imageType(DfmSignature.ImageTypeEnum.PDF).image(PDF)));
        when(client.onboard(request)).thenReturn(new DfmOnboardingOutcome.Success(dfmOk(null, null)));

        assertThat(service.register(request).getNin()).isEqualTo("EGA0000000");
    }

    @Test
    void register_shouldAcceptMinorWithGuardian() {
        LocalDate tenYearsAgo = LocalDate.now(ZoneId.of("Asia/Dubai")).minusYears(10);
        DfmInvestorRegistrationRequest request = valid(r -> r.dateOfBirth(tenYearsAgo)
                .guardian(new DfmGuardian().name("Guardian Name").type("PARENT").proofDocument(PDF)));
        when(client.onboard(request)).thenReturn(new DfmOnboardingOutcome.Success(dfmOk(null, null)));

        assertThat(service.register(request)).isNotNull();
    }

    // ---------- cross-field rules ----------

    @Test
    void r1_aeNationalWithoutEmiratesId_shouldBeDfn003() {
        assertRejected(valid(r -> r.emiratesId(null).passport(new DfmPassport().passportNumber("X1")
                .expiryDate(LocalDate.of(2030, 1, 1)).imageType(DfmPassport.ImageTypeEnum.PNG).image(PNG))),
                "emiratesId", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r2_noEmiratesIdAndNoPassport_shouldBeDfn003() {
        assertRejected(valid(r -> r.nationality("GB").emiratesId(null)), "passport", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r3_arabNationalityWithoutArabicName_shouldBeDfn003() {
        assertRejected(valid(r -> r.nationality("EG").fullNameAr(" ")), "fullNameAr", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r4_aeAddressWithoutPoBox_shouldBeDfn003() {
        assertRejected(valid(r -> r.getAddress().poBox(null)), "address.poBox", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r5_noPoBoxAndNoLine_shouldBeDfn003() {
        assertRejected(valid(r -> r.address(new DfmAddress().country("GB").city("London"))),
                "address.line", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r6_bankWithoutAedIban_shouldBeDfn003() {
        assertRejected(valid(r -> r.getDividendPayment().aedIban(null)), "dividendPayment.aedIban", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r6_ivestorWithoutPrintName_shouldBeDfn003() {
        assertRejected(valid(r -> r.dividendPayment(new DfmDividendPayment().method(DfmDividendPayment.MethodEnum.IVESTOR))),
                "dividendPayment.ivestorPrintName", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r7_marginWithoutCash_shouldBeDfn003() {
        assertRejected(valid(r -> r.portfolioOptions(new LinkedHashSet<>(Set.of(DfmPortfolioOption.MARGIN)))),
                "portfolioOptions", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r8_minorWithoutGuardian_shouldBeDfn003() {
        LocalDate tenYearsAgo = LocalDate.now(ZoneId.of("Asia/Dubai")).minusYears(10);
        assertRejected(valid(r -> r.dateOfBirth(tenYearsAgo)), "guardian", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r9_unknownAddressCountry_shouldBeDfn001() {
        assertRejected(valid(r -> r.getAddress().country("ZZ")), "address.country", DfmInvestorErrorCode.DFN001);
    }

    @Test
    void r9b_unknownNationality_shouldBeDfn003() {
        assertRejected(valid(r -> r.nationality("ZZ")), "nationality", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r9b_unknownCrsCountry_shouldBeDfn003NotDfn001() {
        assertRejected(valid(r -> r.crs(new DfmCrs().resident(true).countries(List.of(
                        new DfmCrsCountry().country("AE").hasTin(false),
                        new DfmCrsCountry().country("ZZ").hasTin(false))))),
                "crs.countries[1].country", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void blankCity_shouldBeDfn002() {
        assertRejected(valid(r -> r.getAddress().city("   ")), "address.city", DfmInvestorErrorCode.DFN002);
    }

    @Test
    void r10_undecodableBase64_shouldBeDfn003() {
        assertRejected(valid(r -> r.getEmiratesId().frontImage("not base64!")), "emiratesId.frontImage", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r10_unknownMagicBytes_shouldBeDfn003() {
        assertRejected(valid(r -> r.getEmiratesId().backImage("aGVsbG8=")), "emiratesId.backImage", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r10_emptyImage_shouldBeDfn003() {
        assertRejected(valid(r -> r.getEmiratesId().backImage("")), "emiratesId.backImage", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r10_imageTypeMismatch_shouldBeDfn003() {
        assertRejected(valid(r -> r.getSignature().imageType(DfmSignature.ImageTypeEnum.JPEG)), "signature.image", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r10_passportImageTypeMismatch_shouldBeDfn003() {
        assertRejected(valid(r -> r.passport(new DfmPassport().passportNumber("X1").expiryDate(LocalDate.of(2030, 1, 1))
                .imageType(DfmPassport.ImageTypeEnum.PDF).image(PNG))), "passport.image", DfmInvestorErrorCode.DFN003);
    }

    @Test
    void r10_badGuardianProof_shouldBeDfn003() {
        assertRejected(valid(r -> r.guardian(new DfmGuardian().name("G").type("PARENT").proofDocument("aGVsbG8="))),
                "guardian.proofDocument", DfmInvestorErrorCode.DFN003);
    }

    // ---------- DFM outcomes ----------

    @Test
    void rejected_shouldBeDfn006WithDfmMessage() {
        when(client.onboard(any())).thenReturn(new DfmOnboardingOutcome.Rejected("400", "Request id should be a valid GUID.", false));

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOf(DfmRejectedException.class)
                .hasMessage("Request id should be a valid GUID.")
                .hasFieldOrPropertyWithValue("code", DfmInvestorErrorCode.DFN006);
    }

    @Test
    void rejectedWithoutMessage_shouldUseDefaultText() {
        when(client.onboard(any())).thenReturn(new DfmOnboardingOutcome.Rejected("400", null, false));

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOf(DfmRejectedException.class)
                .hasMessage("DFM rejected the request (HTTP 400)");
    }

    @Test
    void duplicateInProgress_shouldBeDfn007() {
        when(client.onboard(any())).thenReturn(new DfmOnboardingOutcome.Rejected("409", " ", true));

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOf(DfmRejectedException.class)
                .hasMessage("A registration with this requestId is still in progress at DFM; retry later with the same requestId")
                .hasFieldOrPropertyWithValue("code", DfmInvestorErrorCode.DFN007);
    }

    @Test
    void duplicateInProgressWithMessage_shouldEchoIt() {
        when(client.onboard(any())).thenReturn(new DfmOnboardingOutcome.Rejected("409", "In progress", true));

        assertThatThrownBy(() -> service.register(validRequest())).hasMessage("In progress");
    }

    @ParameterizedTest
    @EnumSource(DfmOnboardingOutcome.Reason.class)
    void unavailable_shouldMapReasonToCode(DfmOnboardingOutcome.Reason reason) {
        TimeoutException cause = new TimeoutException("t");
        when(client.onboard(any())).thenReturn(new DfmOnboardingOutcome.Unavailable(reason, cause));
        DfmInvestorErrorCode expected = switch (reason) {
            case TIMEOUT -> DfmInvestorErrorCode.DFN011;
            case CIRCUIT_OPEN -> DfmInvestorErrorCode.DFN014;
            case BAD_RESPONSE -> DfmInvestorErrorCode.DFN010;
        };

        assertThatThrownBy(() -> service.register(validRequest()))
                .isInstanceOf(DfmUnavailableException.class)
                .hasFieldOrPropertyWithValue("code", expected)
                .hasCause(cause);
    }
}
