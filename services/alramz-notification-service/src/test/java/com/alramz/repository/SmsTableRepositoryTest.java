package com.alramz.repository;

import com.alramz.utils.SqlQueriesManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** SQL text and transaction boundary only: Oracle SKIP LOCKED semantics need a two-session BROKQA check. */
@ExtendWith(MockitoExtension.class)
class SmsTableRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SqlQueriesManager queries;
    private SmsTableRepository repository;

    @BeforeEach
    void setUp() {
        queries = new SqlQueriesManager(new MockEnvironment().withProperty("sql.file", "classpath:sql/sql-queries.xml"));
        repository = new SmsTableRepository(jdbc, transactionManager, queries);
    }

    private static String normalized(final String sql) {
        return sql.replaceAll("\\s+", " ").strip();
    }

    @Test
    void claimSqlLocksABoundedOrderedCandidateSetWithoutFetchFirst() throws Exception {
        final String sql = normalized(queries.getSQLQueryFromConfig("sms.claim.select"));

        assertThat(sql).contains("FROM INSIGHT.SMS_TABLE")
                .contains("SERIAL_NO IN (SELECT SERIAL_NO FROM (SELECT SERIAL_NO FROM INSIGHT.SMS_TABLE"
                        + " WHERE STATUS = :pendingStatus ORDER BY PRIORITY, CREATION_DATE, SERIAL_NO)"
                        + " WHERE ROWNUM <= :batchSize)")
                .endsWith("FOR UPDATE SKIP LOCKED")
                .doesNotContain("FETCH FIRST");
    }

    @Test
    void updatesAreGuardedByStatus() throws Exception {
        assertThat(normalized(queries.getSQLQueryFromConfig("sms.claim.markInProgress")))
                .contains("SET STATUS = :inProgressStatus").contains("SERIAL_NO IN (:ids)")
                .contains("AND STATUS = :pendingStatus");
        assertThat(normalized(queries.getSQLQueryFromConfig("sms.markResult.incrementAttempts")))
                .contains("SEND_ATTEMPTS = NVL(SEND_ATTEMPTS, 0) + 1").contains("MESSAGE_ID = :messageId")
                .contains("WHERE SERIAL_NO = :serialNo AND STATUS = :inProgressStatus");
        assertThat(normalized(queries.getSQLQueryFromConfig("sms.markResult.keepAttempts")))
                .doesNotContain("SEND_ATTEMPTS").doesNotContain("MESSAGE_ID")
                .contains("WHERE SERIAL_NO = :serialNo AND STATUS = :inProgressStatus");
    }

    @Test
    @SuppressWarnings("unchecked")
    void claimSelectsAndMarksInProgressInOneTransaction() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        final SmsTableRepository.SmsTableRow row = new SmsTableRepository.SmsTableRow(7L, "body", "971500000010", null);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of(row));

        assertThat(repository.claimPending(50, 10, 20)).containsExactly(row);

        final ArgumentCaptor<MapSqlParameterSource> select = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        final ArgumentCaptor<MapSqlParameterSource> update = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        final InOrder order = inOrder(transactionManager, jdbc);
        order.verify(transactionManager).getTransaction(any());
        order.verify(jdbc).query(anyString(), select.capture(), any(RowMapper.class));
        order.verify(jdbc).update(anyString(), update.capture());
        order.verify(transactionManager).commit(any());
        verify(transactionManager, times(1)).getTransaction(any());
        assertThat(select.getValue().getValue("pendingStatus")).isEqualTo(10);
        assertThat(select.getValue().getValue("batchSize")).isEqualTo(50);
        assertThat(update.getValue().getValue("ids")).isEqualTo(List.of(7L));
        assertThat(update.getValue().getValue("inProgressStatus")).isEqualTo(20);
        assertThat(update.getValue().getValue("pendingStatus")).isEqualTo(10);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyClaimIssuesNoUpdate() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenReturn(List.of());

        assertThat(repository.claimPending(50, 10, 20)).isEmpty();
        verify(jdbc, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void rowMapperReadsMobileNumberAsPlainDigits() throws Exception {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        final ArgumentCaptor<RowMapper> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), mapper.capture())).thenReturn(List.of());
        repository.claimPending(1, 10, 20);

        final ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("SERIAL_NO")).thenReturn(9L);
        when(rs.getString("MESSAGE_BODY")).thenReturn("hi");
        when(rs.getBigDecimal("MOBILE_NO")).thenReturn(new BigDecimal("9.71500000010E11"));
        when(rs.getObject("SEND_ATTEMPTS", Integer.class)).thenReturn(2);
        assertThat(mapper.getValue().mapRow(rs, 0))
                .isEqualTo(new SmsTableRepository.SmsTableRow(9L, "hi", "971500000010", 2));

        when(rs.getBigDecimal("MOBILE_NO")).thenReturn(null);
        assertThat(((SmsTableRepository.SmsTableRow) mapper.getValue().mapRow(rs, 0)).mobileNo()).isNull();
    }

    @Test
    void markResultChoosesStatementAndBindsGuard() {
        final LocalDateTime now = LocalDateTime.of(2026, 10, 26, 14, 0);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        assertThat(repository.markResult(7L, 88, "200|id|cid|WASELNEY", now, true, 20)).isEqualTo(1);
        assertThat(repository.markResult(8L, 30, null, now, false, 20)).isEqualTo(1);

        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, times(2)).update(sql.capture(), params.capture());
        assertThat(sql.getAllValues().get(0)).contains("NVL(SEND_ATTEMPTS, 0) + 1");
        assertThat(sql.getAllValues().get(1)).doesNotContain("SEND_ATTEMPTS");
        final MapSqlParameterSource first = params.getAllValues().get(0);
        assertThat(first.getValue("serialNo")).isEqualTo(7L);
        assertThat(first.getValue("status")).isEqualTo(88);
        assertThat(first.getValue("messageId")).isEqualTo("200|id|cid|WASELNEY");
        assertThat(first.getValue("sendDate")).isEqualTo(Timestamp.valueOf(now));
        assertThat(first.getValue("inProgressStatus")).isEqualTo(20);
    }
}
