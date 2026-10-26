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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class MmfRepositoryImplTest {

    @Mock
    private NamedParameterJdbcTemplate brokTemplate;

    @Mock
    private SqlQueriesManager sqlQueriesManager;

    private MmfRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new MmfRepositoryImpl(brokTemplate, sqlQueriesManager);
    }

    // ---------- account ----------

    @Test
    void findAccount_capital_shouldBindStringAndMapByName() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.account.find")).thenReturn("SQL");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(brokTemplate.query(eq("SQL"), params.capture(), mapper.capture())).thenReturn(List.of());

        assertThat(repository.findAccount(MmfBook.CAPITAL, "2939418")).isEmpty();

        assertThat(params.getValue().getValue("accountNumber")).isEqualTo("2939418");

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CLIENT_ID")).thenReturn("1100206936164");
        when(rs.getString("CLIENT_NAME_EN")).thenReturn("EN");
        when(rs.getString("CLIENT_NAME_AR")).thenReturn("AR");
        when(rs.getString("ACCOUNT_NUMBER")).thenReturn("2939418");
        when(rs.getString("ACCOUNT_NAME")).thenReturn("NAME");
        when(rs.getString("BASE_CURRENCY")).thenReturn("AED");
        MmfAccount account = (MmfAccount) mapper.getValue().mapRow(rs, 0);
        assertThat(account).isEqualTo(new MmfAccount().clientId("1100206936164").clientNameEn("EN").clientNameAr("AR")
                .accountNumber("2939418").accountName("NAME").baseCurrency("AED"));
    }

    @Test
    void findAccount_mmf_shouldUseBrokAndTakeFirstOfSeveralRows() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.account.find")).thenReturn("SQL");
        MmfAccount first = new MmfAccount().accountNumber("1");
        when(brokTemplate.query(eq("SQL"), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(first, new MmfAccount().accountNumber("2")));

        assertThat(repository.findAccount(MmfBook.MMF, "1")).isEqualTo(Optional.of(first));
    }

    @Test
    void sqlLoadFailure_shouldThrowBackendExceptionWithNullCode() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig(anyString())).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> repository.findAccount(MmfBook.CAPITAL, "1"))
                .isInstanceOf(MmfBackendException.class)
                .hasFieldOrPropertyWithValue("errorCode", null);
    }

    @Test
    void missingSqlKey_shouldThrowBackendException() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig(anyString())).thenReturn(null);

        assertThatThrownBy(() -> repository.nextSweepNo(MmfBook.CAPITAL)).isInstanceOf(MmfBackendException.class);
    }

    // ---------- sweeps ----------

    @Test
    void findSweeps_range_shouldBindTypeAndDatesAndMapRows() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.sweep.find.range")).thenReturn("RANGE");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(brokTemplate.query(eq("RANGE"), params.capture(), mapper.capture())).thenReturn(List.of());

        repository.findSweeps(MmfBook.MMF, "O", LocalDate.of(2025, 4, 1), LocalDate.of(2025, 5, 1));

        assertThat(params.getValue().getValue("type")).isEqualTo("O");
        assertThat(params.getValue().getValue("fromDate")).isEqualTo(Date.valueOf("2025-04-01"));
        assertThat(params.getValue().getValue("toDateExclusive")).isEqualTo(Date.valueOf("2025-05-01"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("SWEEP_NO")).thenReturn(52L);
        when(rs.getDate("SWEEP_DATE")).thenReturn(Date.valueOf("2019-12-30"));
        when(rs.getString("CLIENT_ID")).thenReturn("2944237");
        when(rs.getBigDecimal("AMT")).thenReturn(new BigDecimal("3838.02"));
        MmfSweep sweep = (MmfSweep) mapper.getValue().mapRow(rs, 0);
        assertThat(sweep).isEqualTo(new MmfSweep().sweepNo(52L).date(LocalDate.of(2019, 12, 30))
                .accountNumber("2944237").amount(new BigDecimal("3838.02")).source(MmfBook.MMF));
    }

    @Test
    void findSweeps_today_shouldUseTodayKeyWithTypeOnly() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.sweep.find.today")).thenReturn("TODAY");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(brokTemplate.query(eq("TODAY"), params.capture(), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repository.findSweeps(MmfBook.CAPITAL, "W", null, null)).isEmpty();
        assertThat(params.getValue().getParameterNames()).containsExactly("type");
    }

    @Test
    void lockSweepTypes_shouldBindLongListAndCollectTypes() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.sweep.lock")).thenReturn("LOCK");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("SWEEP_NO")).thenReturn(7L);
        when(rs.getString("SWEEP_TYPE")).thenReturn("W");
        doAnswer(inv -> {
            ((RowCallbackHandler) inv.getArgument(2)).processRow(rs);
            return null;
        }).when(brokTemplate).query(eq("LOCK"), params.capture(), any(RowCallbackHandler.class));

        Map<Long, String> types = repository.lockSweepTypes(MmfBook.CAPITAL, List.of(7L, 8L));

        assertThat(types).containsExactly(Map.entry(7L, "W"));
        assertThat(params.getValue().getValue("sweepNos")).isEqualTo(List.of(7L, 8L));
    }

    @Test
    void confirmSweepIns_shouldUpdateOnBrok() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.sweep.confirm")).thenReturn("UPD");
        when(brokTemplate.update(eq("UPD"), any(MapSqlParameterSource.class))).thenReturn(2);

        assertThat(repository.confirmSweepIns(MmfBook.MMF, List.of(1L, 2L))).isEqualTo(2);
    }

    // ---------- profit ----------

    @Test
    void nextSweepNo_shouldReadMaxPlusOne() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.sweep.next")).thenReturn("SEQ");
        when(brokTemplate.queryForObject(eq("SEQ"), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(2170513L);

        assertThat(repository.nextSweepNo(MmfBook.CAPITAL)).isEqualTo(2170513L);
    }

    @Test
    void insertProfitDistribution_shouldBindTypedValuesAndSourceType() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.profit.insert")).thenReturn("INS");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(brokTemplate.update(eq("INS"), params.capture())).thenReturn(1);
        MmfProfitDistributionItem item = new MmfProfitDistributionItem().sweepDate(LocalDate.of(2025, 4, 20))
                .accountNumber("1005").amount(new BigDecimal("2500.75")).referenceId("987654321").source(MmfBook.MMF);

        repository.insertProfitDistribution(MmfBook.MMF, 99L, item);
        repository.insertProfitDistribution(MmfBook.CAPITAL, 100L, item.source(MmfBook.CAPITAL));

        MapSqlParameterSource mmf = params.getAllValues().get(0);
        assertThat(mmf.getValue("sweepNo")).isEqualTo(99L);
        assertThat(mmf.getValue("sweepDate")).isEqualTo(Date.valueOf("2025-04-20"));
        assertThat(mmf.getValue("accountNumber")).isEqualTo(1005L);
        assertThat(mmf.getValue("amount")).isEqualTo(new BigDecimal("2500.75"));
        assertThat(mmf.getValue("referenceId")).isEqualTo(987654321L);
        assertThat(mmf.getValue("srcType")).isEqualTo("M");
        assertThat(params.getAllValues().get(1).getValue("srcType")).isEqualTo("C");
    }

    // ---------- custodian ----------

    @Test
    void findCustodianCash_shouldMapBalanceAsBigDecimal() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.custodian.cash")).thenReturn("CASH");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(brokTemplate.query(eq("CASH"), params.capture(), mapper.capture())).thenReturn(List.of());

        repository.findCustodianCash(List.of("2001214", "2001215"));

        assertThat(params.getValue().getValue("clientIds")).isEqualTo(List.of("2001214", "2001215"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CL_MAIN_CLIENT_ID")).thenReturn("1100106001214");
        when(rs.getString("CL_CLIENT_ID")).thenReturn("2001214");
        when(rs.getString("CLIENT_NAME")).thenReturn("NAME");
        when(rs.getBigDecimal("CASH_BALANCE")).thenReturn(BigDecimal.ZERO);
        assertThat((MmfCustodianCash) mapper.getValue().mapRow(rs, 0)).isEqualTo(new MmfCustodianCash()
                .clientMainId("1100106001214").clientId("2001214").clientName("NAME").cashBalance(BigDecimal.ZERO));
    }

    @Test
    void findCustodianPositions_shouldTypeNullAsOfDateAndMapByName() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.custodian.positions")).thenReturn("POS");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(brokTemplate.query(eq("POS"), params.capture(), mapper.capture())).thenReturn(List.of());

        repository.findCustodianPositions(List.of("2001214"), null);

        assertThat(params.getValue().getValue("asOfDate")).isNull();
        assertThat(params.getValue().getSqlType("asOfDate")).isEqualTo(Types.DATE);
        assertThat(params.getValue().getValue("clientIds")).isEqualTo(List.of("2001214"));

        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CLIENT_ID")).thenReturn("2001214");
        when(rs.getString("ISIN")).thenReturn("AEE000301011");
        when(rs.getString("SYMBOL")).thenReturn("EMAAR");
        when(rs.getBigDecimal("QUANTITY")).thenReturn(new BigDecimal("15000"));
        when(rs.getBigDecimal("CLOSE_PRICE")).thenReturn(new BigDecimal("9.85"));
        when(rs.getString("CURRENCY")).thenReturn("AED");
        when(rs.getString("MARKET")).thenReturn("DFM", (String) null);
        when(rs.getDate("PRICE_DATE")).thenReturn(Date.valueOf("2026-10-18"), (Date) null);

        MmfCustodianPosition first = (MmfCustodianPosition) mapper.getValue().mapRow(rs, 0);
        assertThat(first).isEqualTo(new MmfCustodianPosition().clientId("2001214").isin("AEE000301011").symbol("EMAAR")
                .quantity(new BigDecimal("15000")).closePrice(new BigDecimal("9.85")).currency("AED")
                .market(MmfMarket.DFM).priceDate(LocalDate.of(2026, 10, 18)));

        MmfCustodianPosition second = (MmfCustodianPosition) mapper.getValue().mapRow(rs, 1);
        assertThat(second.getMarket()).isNull();
        assertThat(second.getPriceDate()).isNull();
    }

    @Test
    void findCustodianPositions_withDate_shouldBindSqlDate() throws Exception {
        when(sqlQueriesManager.getSQLQueryFromConfig("mmf.custodian.positions")).thenReturn("POS");
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(brokTemplate.query(eq("POS"), params.capture(), any(RowMapper.class))).thenReturn(List.of());

        repository.findCustodianPositions(List.of("1"), LocalDate.of(2025, 4, 20));

        assertThat(params.getValue().getValue("asOfDate")).isEqualTo(Date.valueOf("2025-04-20"));
    }

    // ---------- the real SQL file ----------

    @Test
    void sqlFile_shouldDefineAllKeysWithBindsOnly() throws Exception {
        Properties sql = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("sql/sql-queries.xml")) {
            sql.loadFromXML(in);
        }

        assertThat(sql.stringPropertyNames()).containsExactlyInAnyOrder(
                "mmf.account.find", "mmf.sweep.find.range", "mmf.sweep.find.today", "mmf.sweep.lock",
                "mmf.sweep.confirm", "mmf.sweep.next", "mmf.profit.insert", "mmf.custodian.cash",
                "mmf.custodian.positions");
        assertThat(sql.getProperty("mmf.sweep.confirm")).contains("SWEEP_TYPE = 'W'").contains("IN (:sweepNos)");
        assertThat(sql.getProperty("mmf.sweep.lock")).contains("FOR UPDATE").contains("IN (:sweepNos)");
        assertThat(sql.getProperty("mmf.sweep.next")).contains("NVL(MAX(SWEEP_NO), 0) + 1");
        assertThat(sql.getProperty("mmf.profit.insert"))
                .contains("'P'").contains(":srcType").contains("SEGMENT_ID").contains("SYSDATE").doesNotContain("MAX(");
        assertThat(sql.getProperty("mmf.sweep.find.range")).contains("SWEEP_DATE < :toDateExclusive");
        assertThat(sql.getProperty("mmf.custodian.cash")).contains("IN (:clientIds)");
        assertThat(sql.getProperty("mmf.custodian.positions"))
                .contains("NVL(:asOfDate, TRUNC(SYSDATE) - 1)").contains("IN (:clientIds)").contains("UNION ALL");
        sql.stringPropertyNames().forEach(key ->
                assertThat(sql.getProperty(key)).as(key).doesNotContain("||").doesNotContain("${"));
    }
}
