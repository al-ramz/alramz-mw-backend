package com.alramz.service.impl;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.alramz.utils.SqlQueriesManager;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "company.datasource.brok", name = "enabled", havingValue = "true", matchIfMissing = false)
public class NinTradingNumberValidationService {

    private final JdbcTemplate brokJdbcTemplate;
    private final SqlQueriesManager sqlQueriesManager;

    public NinTradingNumberValidationService(@Qualifier("brokJdbcTemplate") JdbcTemplate brokJdbcTemplate,
                                             SqlQueriesManager sqlQueriesManager) {
        this.brokJdbcTemplate = brokJdbcTemplate;
        this.sqlQueriesManager = sqlQueriesManager;
    }

    public boolean checkIfNinOrTradingNumberExists(String exchange, String nationalInvestorNumber, String tradingNumber) {
        if (exchange == null || exchange.trim().isEmpty()) {
            throw new IllegalArgumentException("exchange is required");
        }
        if (nationalInvestorNumber == null && tradingNumber == null) {
            throw new IllegalArgumentException("At least one of nationalInvestorNumber or tradingNumber is required");
        }

        try {
            String selectQuery = sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists");
            log.debug("Loaded NIN/trading number check query: {}", selectQuery);

            Map<String, Object> result = brokJdbcTemplate.queryForMap(
                selectQuery,
                exchange,
                nationalInvestorNumber != null ? nationalInvestorNumber : "",
                exchange,
                tradingNumber != null ? tradingNumber : ""
            );

            boolean ninExists = ((Number) result.getOrDefault("NIN_EXISTS", 0)).intValue() == 1;
            boolean tradingExists = ((Number) result.getOrDefault("TRADING_NUMBER_EXISTS", 0)).intValue() == 1;

            if (nationalInvestorNumber != null && tradingNumber != null) {
                return ninExists || tradingExists;
            } else if (nationalInvestorNumber != null) {
                return ninExists;
            } else {
                return tradingExists;
            }
        } catch (IOException e) {
            log.error("Failed to load SQL query for NIN/trading number check", e);
            throw new IllegalStateException("Failed to load SQL query for NIN/trading number check", e);
        }
    }
}
