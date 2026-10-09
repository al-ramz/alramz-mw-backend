package com.alramz.repository;

import com.alramz.model.OnboardingRequest;
import com.alramz.utils.SqlQueriesManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

@Repository
@Slf4j
public class DfmOnboardingRepositoryImpl implements DfmOnboardingRepository {

    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SqlQueriesManager sqlQueriesManager;

    public DfmOnboardingRepositoryImpl(
            @Qualifier("middlewareNamedParameterJdbcTemplate") NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate,
            ObjectMapper objectMapper,
            SqlQueriesManager sqlQueriesManager) {
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
        this.objectMapper = objectMapper;
        this.sqlQueriesManager = sqlQueriesManager;
    }

    @Override
    public void insert(OnboardingRequest request, String memberReferenceNumber) {
        String sql;
        try {
            sql = sqlQueriesManager.getSQLQueryFromConfig("dfm.onboarding.insert");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load onboarding insert query", e);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("requestId", request.getRequestId());
        params.put("custMobile", request.getCustMobile());
        params.put("custEmail", request.getCustEmail());
        params.put("custNin", request.getCustNin());
        params.put("eidDob", toSqlDate(request.getEidDob()));
        params.put("eidExpirydate", toSqlDate(request.getEidExpirydate()));
        params.put("eidFullname", request.getEidFullname());
        params.put("eidNo", request.getEidNo());
        params.put("eidIssuedate", toSqlDate(request.getEidIssuedate()));
        params.put("eidPrimaryid", request.getEidPrimaryid());
        params.put("eidSecondaryid", request.getEidSecondaryid());
        params.put("eidSex", request.getEidSex());
        params.put("eidResidencyexpirydate", toSqlDate(request.getEidResidencyexpirydate()));
        params.put("eidResidencynumber", request.getEidResidencynumber());
        params.put("eidFamilyid", request.getEidFamilyid());
        params.put("eidNationality", request.getEidNationality());
        params.put("eidArabicfullname", request.getEidArabicfullname());
        params.put("eidAttachmentFront", request.getEidAttachmentFront());
        params.put("eidAttachmentBack", request.getEidAttachmentBack());
        params.put("eidAttachmentType", request.getEidAttachmentType());
        params.put("ppDob", toSqlDate(request.getPpDob()));
        params.put("ppExpirydate", toSqlDate(request.getPpExpirydate()));
        params.put("ppFullname", request.getPpFullname());
        params.put("ppNo", request.getPpNo());
        params.put("ppNationality", request.getPpNationality());
        params.put("ppPrimaryid", request.getPpPrimaryid());
        params.put("ppSecondaryid", request.getPpSecondaryid());
        params.put("ppSex", request.getPpSex());
        params.put("ppAttachment", request.getPpAttachment());
        params.put("ppAttachmentType", request.getPpAttachmentType());
        params.put("pinfAddress", request.getPinfAddress());
        params.put("pinfCity", request.getPinfCity());
        params.put("pinfMothername", request.getPinfMothername());
        params.put("pinfPobox", request.getPinfPobox());
        params.put("pinfPhone", request.getPinfPhone());
        params.put("pinfCountry", request.getPinfCountry());
        params.put("pinfSignatureimage", request.getPinfSignatureimage());
        params.put("pinfSignatureimageType", request.getPinfSignatureimageType());
        params.put("payMethod", request.getPayMethod());
        params.put("payAedIban", request.getPayAedIban());
        params.put("payUsdIban", request.getPayUsdIban());
        params.put("payFrnIban", request.getPayFrnIban());
        params.put("payFrnSwift", request.getPayFrnSwift());
        params.put("payCorIban", request.getPayCorIban());
        params.put("payCorSwift", request.getPayCorSwift());
        params.put("payRoutecode", request.getPayRoutecode());
        params.put("portfolioOptions", request.getPortfolioOptions());
        params.put("empStatus", request.getEmpStatus());
        params.put("empName", request.getEmpName());
        params.put("empPosition", request.getEmpPosition());
        params.put("empMarketorissuer", request.getEmpMarketorissuer());
        params.put("empMarketorissuerCompany", request.getEmpMarketorissuerCompany());
        params.put("empRelatedtomarketemployee", request.getEmpRelatedtomarketemployee());
        params.put("empRelatedsJson", toJson(request.getEmpRelateds()));
        params.put("incSource", request.getIncSource());
        params.put("incRange", request.getIncRange());
        params.put("invKnowledge", request.getInvKnowledge());
        params.put("invStrategy", request.getInvStrategy());
        params.put("invInstrument", request.getInvInstrument());
        params.put("invRisk", request.getInvRisk());
        params.put("invAmount", request.getInvAmount());
        params.put("invKnowledgeTrading", request.getInvKnowledgeTrading());
        params.put("invKnowledgeSource", request.getInvKnowledgeSource());
        params.put("invEducation", request.getInvEducation());
        params.put("invTradePrev", request.getInvTradePrev());
        params.put("invTradeFreq", request.getInvTradeFreq());
        params.put("invRiskHigh", request.getInvRiskHigh());
        params.put("invEquity", request.getInvEquity());
        params.put("invSca", request.getInvSca());
        params.put("invScaAuthority", request.getInvScaAuthority());
        params.put("invScaType", request.getInvScaType());
        params.put("invQualified", request.getInvQualified());
        params.put("csrJson", toJson(request.getCsr()));
        params.put("fatcaUscitizen", request.getFatcaUscitizen());
        params.put("fatcaTin", request.getFatcaTin());
        params.put("kycMatch", request.getKycMatch());
        params.put("kycMatchDetailsJson", toJson(request.getKycMatchDetails()));
        params.put("cashTradingNumber", null);
        params.put("marginTradingNumber", null);
        params.put("memberReferenceNumber", memberReferenceNumber);
        params.put("accountStatus", "NEW");
        params.put("userCode", null);
        params.put("fitNumber", null);
        params.put("dfmRequestTime", new Timestamp(System.currentTimeMillis()));
        params.put("dfmAccountUpdateRequestTime", null);
        params.put("backOfficeOnboardingRequestTime", null);
        params.put("kycUpdateRequestTime", null);

        middlewareNamedParameterJdbcTemplate.update(sql, params);
    }

    private java.sql.Date toSqlDate(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return java.sql.Date.valueOf(localDate);
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON", e);
            return null;
        }
    }
}
