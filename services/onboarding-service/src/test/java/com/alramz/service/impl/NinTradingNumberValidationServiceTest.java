package com.alramz.service.impl;

import com.alramz.utils.SqlQueriesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NinTradingNumberValidationServiceTest {

    @Mock
    private JdbcTemplate brokJdbcTemplate;

    @Mock
    private SqlQueriesManager sqlQueriesManager;

    private NinTradingNumberValidationService service;

    @BeforeEach
    void setUp() {
        service = new NinTradingNumberValidationService(brokJdbcTemplate, sqlQueriesManager);
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldThrowWhenExchangeMissing() {
        assertThatThrownBy(() -> service.checkIfNinOrTradingNumberExists(null, "123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exchange is required");

        assertThatThrownBy(() -> service.checkIfNinOrTradingNumberExists("", "123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exchange is required");
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldThrowWhenBothMissing() {
        assertThatThrownBy(() -> service.checkIfNinOrTradingNumberExists("DFM", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one of nationalInvestorNumber or tradingNumber is required");
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldThrowOnSqlQueryLoadFailure() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenThrow(new IOException("Failed to load"));

        assertThatThrownBy(() -> service.checkIfNinOrTradingNumberExists("DFM", "123", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to load SQL query for NIN/trading number check");
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldReturnTrueWhenNinExists() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq("123"), eq("DFM"), eq("")))
                .thenReturn(Map.of("NIN_EXISTS", 1, "TRADING_NUMBER_EXISTS", 0));

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", "123", null);

        assertThat(result).isTrue();
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldReturnFalseWhenNinDoesNotExist() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq("123"), eq("DFM"), eq("")))
                .thenReturn(Map.of("NIN_EXISTS", 0, "TRADING_NUMBER_EXISTS", 0));

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", "123", null);

        assertThat(result).isFalse();
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldReturnTrueWhenTradingNumberExists() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq(""), eq("DFM"), eq("456")))
                .thenReturn(Map.of("NIN_EXISTS", 0, "TRADING_NUMBER_EXISTS", 1));

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", null, "456");

        assertThat(result).isTrue();
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldReturnFalseWhenTradingNumberDoesNotExist() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq(""), eq("DFM"), eq("456")))
                .thenReturn(Map.of("NIN_EXISTS", 0, "TRADING_NUMBER_EXISTS", 0));

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", null, "456");

        assertThat(result).isFalse();
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldReturnTrueWhenEitherExists() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq("123"), eq("DFM"), eq("456")))
                .thenReturn(Map.of("NIN_EXISTS", 1, "TRADING_NUMBER_EXISTS", 0));

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", "123", "456");

        assertThat(result).isTrue();
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldReturnFalseWhenNeitherExists() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq("123"), eq("DFM"), eq("456")))
                .thenReturn(Map.of("NIN_EXISTS", 0, "TRADING_NUMBER_EXISTS", 0));

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", "123", "456");

        assertThat(result).isFalse();
    }

    @Test
    void checkIfNinOrTradingNumberExists_shouldHandleMissingColumnsGracefully() throws IOException {
        when(sqlQueriesManager.getSQLQueryFromConfig("nin.trading.number.check.exists"))
                .thenReturn("SELECT ... FROM DUAL");
        when(brokJdbcTemplate.queryForMap(anyString(), eq("DFM"), eq("123"), eq("DFM"), eq("")))
                .thenReturn(Map.of());

        boolean result = service.checkIfNinOrTradingNumberExists("DFM", "123", null);

        assertThat(result).isFalse();
    }
}
