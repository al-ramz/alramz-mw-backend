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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

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
    private static final String DYNAMIC_WHERE_CLAUSE_PLACEHOLDER = "{dynamicWhereClause}";

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
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("startDate", Date.valueOf(startDate));
            params.addValue("endDate", Date.valueOf(endDate));

            String dynamicWhereClause = buildRelationshipManagerClause(relationshipManagerCodes, params);
            String sql = sqlQueriesManager.getSQLQueryFromConfig("relationship.manager.commissions.find")
                    .replace(DYNAMIC_WHERE_CLAUSE_PLACEHOLDER, dynamicWhereClause);

            log.info("Executing relationship.manager.commissions.find: {}", inlineParams(sql, params));

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
     * Renders {@code sql} with each {@code :name} replaced by its bound value as a SQL
     * literal, for logging only — the query itself still executes with bind parameters.
     */
    private static String inlineParams(String sql, MapSqlParameterSource params) {
        String rendered = sql;
        for (String name : params.getParameterNames()) {
            rendered = rendered.replaceAll(":" + name + "\\b",
                    Matcher.quoteReplacement(toSqlLiteral(params.getValue(name))));
        }
        return rendered;
    }

    private static String toSqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Collection<?> values) {
            return values.stream().map(RelationshipManagerRepositoryImpl::toSqlLiteral)
                    .collect(Collectors.joining(", "));
        }
        if (value instanceof Date date) {
            return "DATE '" + date + "'";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /**
     * Builds the RM predicate substituted for {@value #DYNAMIC_WHERE_CLAUSE_PLACEHOLDER}.
     * RM {@value #ISLAMIC_OFFICE_CODE} is special: when requested it only matches Islamic
     * securities ({@code SC_ISLAMIC = 'Y'}); all other requested codes match plainly. No
     * codes means no RM filter. Codes stay bind parameters — only fixed SQL is concatenated.
     */
    private static String buildRelationshipManagerClause(List<String> codes, MapSqlParameterSource params) {
        if (codes == null || codes.isEmpty()) {
            return "1 = 1";
        }
        List<String> predicates = new ArrayList<>();
        if (codes.contains(ISLAMIC_OFFICE_CODE)) {
            params.addValue("islamicOfficeCode", ISLAMIC_OFFICE_CODE);
            predicates.add("(CL_RELATION_MANG = :islamicOfficeCode AND csc.SC_ISLAMIC = 'Y')");
        }
        List<String> otherCodes = codes.stream().filter(code -> !ISLAMIC_OFFICE_CODE.equals(code)).toList();
        if (!otherCodes.isEmpty()) {
            params.addValue("relationshipManagerCodes", otherCodes);
            predicates.add("CL_RELATION_MANG IN (:relationshipManagerCodes)");
        }
        return "(" + String.join(" OR ", predicates) + ")";
    }
}


