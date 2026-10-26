package com.alramz.repository;

import com.alramz.exception.MmfBackendException;
import com.alramz.model.MmfAccount;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCash;
import com.alramz.model.MmfCustodianPosition;
import com.alramz.model.MmfMarket;
import com.alramz.model.MmfProfitDistributionItem;
import com.alramz.model.MmfSweep;
import com.alramz.utils.SqlQueriesManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Direct SQL against the INSIGHT schema on {@code brok} for both books (CAPITAL and MMF).
 * Unlike reporting's repository, DataAccessExceptions are NOT wrapped here: the service needs them per item
 * (profit distributions) and the handler classifies the rest into the operation's 500/503 code.
 */
@Slf4j
@Repository
@ConditionalOnProperty(prefix = "company.datasource", name = "brok.enabled", havingValue = "true")
public class MmfRepositoryImpl implements MmfRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueriesManager sqlQueriesManager;

    public MmfRepositoryImpl(
            @Qualifier("brokNamedParameterJdbcTemplate") NamedParameterJdbcTemplate brokNamedParameterJdbcTemplate,
            SqlQueriesManager sqlQueriesManager) {
        this.jdbc = brokNamedParameterJdbcTemplate;
        this.sqlQueriesManager = sqlQueriesManager;
    }

    @Override
    public Optional<MmfAccount> findAccount(MmfBook book, String accountNumber) {
        // Bound as a String: index-safe whatever CL_CLIENT_ID's column type turns out to be.
        List<MmfAccount> rows = jdbc.query(sql("mmf.account.find"),
                new MapSqlParameterSource("accountNumber", accountNumber),
                (rs, rowNum) -> new MmfAccount()
                        .clientId(rs.getString("CLIENT_ID"))
                        .clientNameEn(rs.getString("CLIENT_NAME_EN"))
                        .clientNameAr(rs.getString("CLIENT_NAME_AR"))
                        .accountNumber(rs.getString("ACCOUNT_NUMBER"))
                        .accountName(rs.getString("ACCOUNT_NAME"))
                        .baseCurrency(rs.getString("BASE_CURRENCY")));
        if (rows.size() > 1) {
            log.warn("Account {} matched {} rows in book {}; using the first", accountNumber, rows.size(), book);
        }
        return rows.stream().findFirst();
    }

    @Override
    public List<MmfSweep> findSweeps(MmfBook book, String sweepType, LocalDate fromDate, LocalDate toDateExclusive) {
        MapSqlParameterSource params = new MapSqlParameterSource("type", sweepType);
        String key = "mmf.sweep.find.today";
        if (fromDate != null) {
            key = "mmf.sweep.find.range";
            params.addValue("fromDate", Date.valueOf(fromDate));
            params.addValue("toDateExclusive", Date.valueOf(toDateExclusive));
        }
        return jdbc.query(sql(key), params, (rs, rowNum) -> new MmfSweep()
                .sweepNo(rs.getLong("SWEEP_NO"))
                .date(rs.getDate("SWEEP_DATE").toLocalDate())
                .accountNumber(rs.getString("CLIENT_ID"))
                .amount(rs.getBigDecimal("AMT"))
                .source(book));
    }

    @Override
    public Map<Long, String> lockSweepTypes(MmfBook book, Collection<Long> sweepNos) {
        Map<Long, String> types = new LinkedHashMap<>();
        jdbc.query(sql("mmf.sweep.lock"), new MapSqlParameterSource("sweepNos", new ArrayList<>(sweepNos)),
                rs -> {
                    types.put(rs.getLong("SWEEP_NO"), rs.getString("SWEEP_TYPE"));
                });
        return types;
    }

    @Override
    public int confirmSweepIns(MmfBook book, Collection<Long> sweepNos) {
        return jdbc.update(sql("mmf.sweep.confirm"),
                new MapSqlParameterSource("sweepNos", new ArrayList<>(sweepNos)));
    }

    @Override
    public long nextSweepNo(MmfBook book) {
        Long next = jdbc.queryForObject(sql("mmf.sweep.next"), new MapSqlParameterSource(), Long.class);
        if (next == null) {
            throw new MmfBackendException(null, "MAX(SWEEP_NO) returned no value", null);
        }
        return next;
    }

    @Override
    public void insertProfitDistribution(MmfBook book, long sweepNo, MmfProfitDistributionItem item) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sweepNo", sweepNo)
                .addValue("sweepDate", Date.valueOf(item.getSweepDate()))
                .addValue("accountNumber", Long.valueOf(item.getAccountNumber()))
                .addValue("amount", item.getAmount())
                .addValue("referenceId", Long.valueOf(item.getReferenceId()))
                .addValue("srcType", book == MmfBook.CAPITAL ? "C" : "M");
        jdbc.update(sql("mmf.profit.insert"), params);
    }

    @Override
    public List<MmfCustodianCash> findCustodianCash(Collection<String> clientIds) {
        return jdbc.query(sql("mmf.custodian.cash"),
                new MapSqlParameterSource("clientIds", new ArrayList<>(clientIds)),
                (rs, rowNum) -> new MmfCustodianCash()
                        .clientMainId(rs.getString("CL_MAIN_CLIENT_ID"))
                        .clientId(rs.getString("CL_CLIENT_ID"))
                        .clientName(rs.getString("CLIENT_NAME"))
                        .cashBalance(rs.getBigDecimal("CASH_BALANCE")));
    }

    @Override
    public List<MmfCustodianPosition> findCustodianPositions(Collection<String> clientIds, LocalDate asOfDate) {
        MapSqlParameterSource params = new MapSqlParameterSource("clientIds", new ArrayList<>(clientIds))
                // Typed so a null bind reaches Oracle's NVL as a DATE.
                .addValue("asOfDate", asOfDate != null ? Date.valueOf(asOfDate) : null, Types.DATE);
        return jdbc.query(sql("mmf.custodian.positions"), params, (rs, rowNum) -> {
            String market = rs.getString("MARKET");
            Date priceDate = rs.getDate("PRICE_DATE");
            return new MmfCustodianPosition()
                    .clientId(rs.getString("CLIENT_ID"))
                    .isin(rs.getString("ISIN"))
                    .symbol(rs.getString("SYMBOL"))
                    .quantity(rs.getBigDecimal("QUANTITY"))
                    .closePrice(rs.getBigDecimal("CLOSE_PRICE"))
                    .currency(rs.getString("CURRENCY"))
                    .market(market != null ? MmfMarket.fromValue(market) : null)
                    .priceDate(priceDate != null ? priceDate.toLocalDate() : null);
        });
    }

    private String sql(String key) {
        try {
            String sql = sqlQueriesManager.getSQLQueryFromConfig(key);
            if (sql == null) {
                throw new MmfBackendException(null, "SQL key not configured: " + key, null);
            }
            return sql;
        } catch (IOException e) {
            throw new MmfBackendException(null, "Unable to load SQL " + key, e);
        }
    }
}
