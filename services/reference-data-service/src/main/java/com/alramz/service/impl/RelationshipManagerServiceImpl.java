package com.alramz.service.impl;

import com.alramz.exception.InvalidDateRangeException;
import com.alramz.model.CommissionResult;
import com.alramz.model.CommissionsByRelationshipManagerRequest;
import com.alramz.model.RelationshipManager;
import com.alramz.model.RelationshipManagerCommissionsSummary;
import com.alramz.model.RelationshipManagerListResult;
import com.alramz.repository.RelationshipManagerRepository;
import com.alramz.service.RelationshipManagerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "company.datasource.brok", name = "enabled", havingValue = "true")
public class RelationshipManagerServiceImpl implements RelationshipManagerService {

    static final LocalDate DEFAULT_START_DATE = LocalDate.of(1900, 1, 1);
    static final LocalDate DEFAULT_END_DATE = LocalDate.of(2999, 1, 1);

    private final RelationshipManagerRepository relationshipManagerRepository;

    public RelationshipManagerServiceImpl(RelationshipManagerRepository relationshipManagerRepository) {
        this.relationshipManagerRepository = relationshipManagerRepository;
    }

    @Override
    public RelationshipManagerListResult getAllRelationshipManagers() {
        List<RelationshipManager> relationshipManagers = relationshipManagerRepository.findActiveRelationshipManagers();

        RelationshipManagerListResult result = new RelationshipManagerListResult();
        result.setRelationshipManagers(relationshipManagers);
        return result;
    }

    @Override
    public RelationshipManagerCommissionsSummary getCommissionsByRelationshipManager(
            CommissionsByRelationshipManagerRequest request) {
        LocalDate startDate = resolveDate("startDate", request == null ? null : request.getStartDate(), DEFAULT_START_DATE);
        LocalDate endDate = resolveDate("endDate", request == null ? null : request.getEndDate(), DEFAULT_END_DATE);
        List<String> relationshipManagerCodes = request == null ? null : request.getRelationshipManagerCodes();

        List<CommissionResult> results = relationshipManagerRepository
                .findCommissionsByRelationshipManager(startDate, endDate, relationshipManagerCodes);

        BigDecimal totalTradingVolume = BigDecimal.ZERO;
        BigDecimal totalReceivedCommission = BigDecimal.ZERO;
        for (CommissionResult result : results) {
            if (result.getTradingVolume() != null) {
                totalTradingVolume = totalTradingVolume.add(result.getTradingVolume());
            }
            if (result.getReceivedCommission() != null) {
                totalReceivedCommission = totalReceivedCommission.add(result.getReceivedCommission());
            }
        }

        RelationshipManagerCommissionsSummary summary = new RelationshipManagerCommissionsSummary();
        summary.setTotalTradingVolume(totalTradingVolume);
        summary.setTotalReceivedCommission(totalReceivedCommission);
        summary.setResults(results);
        return summary;
    }

    /**
     * Q3 (spec.md Section 3b/7): a blank {@code value} is treated identically to an
     * absent field, silently applying {@code defaultValue} — only a non-blank value that
     * fails {@link LocalDate#parse(CharSequence)} (syntactically ISO but not a real
     * calendar date, e.g. {@code "2024-13-45"}) is rejected, as {@link InvalidDateRangeException}.
     */
    private LocalDate resolveDate(String fieldName, String value, LocalDate defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvalidDateRangeException(
                    fieldName + ": must be blank or a valid ISO-8601 date (YYYY-MM-DD)");
        }
    }
}
