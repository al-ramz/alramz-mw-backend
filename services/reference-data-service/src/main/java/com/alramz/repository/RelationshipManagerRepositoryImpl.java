package com.alramz.repository;

import com.alramz.exception.RelationshipManagerLookupException;
import com.alramz.model.CommissionResult;
import com.alramz.model.RelationshipManager;
import com.alramz.utils.SqlQueriesManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

/**
 * Direct-SQL access to the {@code brok} Oracle {@code INSIGHT} schema for relationship
 * manager reference data and commission reporting. Queries are externalized in
 * {@code sql-queries.xml} and loaded via {@link SqlQueriesManager}, per this repo's
 * {@code NamedParameterJdbcTemplate} + {@code SqlQueriesManager} convention
 * ({@code DfmOnboardingRepositoryImpl} in onboarding-service).
 */
@Repository
@Slf4j
@ConditionalOnProperty(prefix = "company.datasource.brok", name = "enabled", havingValue = "true")
public class RelationshipManagerRepositoryImpl implements RelationshipManagerRepository {

    private static final String ISLAMIC_OFFICE_CODE = "3031";
    private static final String ISLAMIC_MODE_NONE = "NONE";
    private static final String ISLAMIC_MODE_ONLY = "ONLY";
    private static final String ISLAMIC_MODE_MIXED = "MIXED";
    private static final String NO_CODE_FILTER_PLACEHOLDER = "__NO_FILTER__";

    private final NamedParameterJdbcTemplate brokNamedParameterJdbcTemplate;
    private final SqlQueriesManager sqlQueriesManager;

    public RelationshipManagerRepositoryImpl(
            @Qualifier("brokNamedParameterJdbcTemplate") NamedParameterJdbcTemplate brokNamedParameterJdbcTemplate,
            SqlQueriesManager sqlQueriesManager) {
        this.brokNamedParameterJdbcTemplate = brokNamedParameterJdbcTemplate;
        this.sqlQueriesManager = sqlQueriesManager;
    }

    @Override
    public List<RelationshipManager> findActiveRelationshipManagers() {
        try {
            String sql = sqlQueriesManager.getSQLQueryFromConfig("relationship.manager.find.active");
            return brokNamedParameterJdbcTemplate.query(sql, (rs, rowNum) -> {
                RelationshipManager relationshipManager = new RelationshipManager();
                relationshipManager.setId(rs.getInt("RM_NO"));
                relationshipManager.setNameEn(rs.getString("RM_NAME_EN"));
                relationshipManager.setNameAr(rs.getString("RM_NAME_AR"));
                return relationshipManager;
            });
        } catch (IOException e) {
            log.error("Failed to load relationship.manager.find.active query", e);
            throw new RelationshipManagerLookupException("Unable to retrieve relationship managers");
        } catch (DataAccessException e) {
            log.error("Failed to query active relationship managers", e);
            throw new RelationshipManagerLookupException("Unable to retrieve relationship managers");
        }
    }

    @Override
    public List<CommissionResult> findCommissionsByRelationshipManager(
            LocalDate startDate, LocalDate endDate, List<String> relationshipManagerCodes) {
        try {
            String sql = sqlQueriesManager.getSQLQueryFromConfig("relationship.manager.commissions.find");

            boolean hasCodes = relationshipManagerCodes != null && !relationshipManagerCodes.isEmpty();

            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("startDate", Date.valueOf(startDate));
            params.addValue("endDate", Date.valueOf(endDate));
            params.addValue("filterByCode", hasCodes ? "Y" : "N");
            params.addValue("relationshipManagerCodes",
                    hasCodes ? relationshipManagerCodes : List.of(NO_CODE_FILTER_PLACEHOLDER));
            params.addValue("islamicMode", islamicMode(hasCodes ? relationshipManagerCodes : null));

            return brokNamedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
                CommissionResult result = new CommissionResult();
                result.setClientNumber(rs.getString("CLIENT_NUMBER"));
                result.setClientName(rs.getString("CLIENT_NAME"));
                result.setTradingVolume(rs.getBigDecimal("TRADING_VOLUME"));
                result.setReceivedCommission(rs.getBigDecimal("RECEIVED_COMMISSION"));
                return result;
            });
        } catch (IOException e) {
            log.error("Failed to load relationship.manager.commissions.find query", e);
            throw new RelationshipManagerLookupException("Unable to retrieve commission report");
        } catch (DataAccessException e) {
            log.error("Failed to query relationship manager commissions", e);
            throw new RelationshipManagerLookupException("Unable to retrieve commission report");
        }
    }

    /**
     * Derives the Islamic-office predicate mode from the requested RM codes, per Q2's
     * deliberately-preserved AND defect (spec.md Section 3b/7): {@code NONE} when RM
     * {@value #ISLAMIC_OFFICE_CODE} isn't requested (no Islamic filter applied),
     * {@code ONLY} when it's the sole requested code (the Islamic predicate resolves
     * correctly), {@code MIXED} when it's requested alongside any other code (the SQL's
     * AND join then yields zero rows, not a union).
     */
    private String islamicMode(List<String> codes) {
        if (codes == null || !codes.contains(ISLAMIC_OFFICE_CODE)) {
            return ISLAMIC_MODE_NONE;
        }
        return codes.size() == 1 ? ISLAMIC_MODE_ONLY : ISLAMIC_MODE_MIXED;
    }
}
