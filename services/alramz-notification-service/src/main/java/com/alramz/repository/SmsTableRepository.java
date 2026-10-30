package com.alramz.repository;

import com.alramz.utils.SqlQueriesManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * INSIGHT.SMS_TABLE on brok (external table, never created or altered here). Exists only when brok is enabled.
 * The shared brok template is never mutated (no maxRows/fetchSize): the batch bound is in the claim SQL.
 */
@Repository
@ConditionalOnProperty(prefix = "company.datasource", name = "brok.enabled", havingValue = "true")
public class SmsTableRepository {

    public record SmsTableRow(long serialNo, String messageBody, String mobileNo, Integer sendAttempts) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final SqlQueriesManager sqlQueriesManager;

    public SmsTableRepository(
            @Qualifier("brokNamedParameterJdbcTemplate") final NamedParameterJdbcTemplate jdbc,
            @Qualifier("brokTransactionManager") final PlatformTransactionManager brokTransactionManager,
            final SqlQueriesManager sqlQueriesManager) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(brokTransactionManager);
        this.sqlQueriesManager = sqlQueriesManager;
    }

    /** Locks up to batchSize pending rows and marks them in progress, in one short brok transaction. */
    public List<SmsTableRow> claimPending(final int batchSize, final int pendingStatus, final int inProgressStatus) {
        return transaction.execute(status -> {
            final List<SmsTableRow> rows = jdbc.query(sql("sms.claim.select"),
                    new MapSqlParameterSource("pendingStatus", pendingStatus).addValue("batchSize", batchSize),
                    (rs, rowNum) -> {
                        final BigDecimal mobile = rs.getBigDecimal("MOBILE_NO");
                        return new SmsTableRow(rs.getLong("SERIAL_NO"), rs.getString("MESSAGE_BODY"),
                                mobile == null ? null : mobile.toPlainString(),
                                rs.getObject("SEND_ATTEMPTS", Integer.class));
                    });
            if (!rows.isEmpty()) {
                jdbc.update(sql("sms.claim.markInProgress"),
                        new MapSqlParameterSource("ids", rows.stream().map(SmsTableRow::serialNo).toList())
                                .addValue("inProgressStatus", inProgressStatus)
                                .addValue("pendingStatus", pendingStatus));
            }
            return rows;
        });
    }

    /** Final update, guarded by the in-progress status. incrementAttempts=false keeps MESSAGE_ID and SEND_ATTEMPTS. */
    public int markResult(final long serialNo, final int status, final String messageId, final LocalDateTime sendDate,
                          final boolean incrementAttempts, final int inProgressStatus) {
        final MapSqlParameterSource params = new MapSqlParameterSource("serialNo", serialNo)
                .addValue("status", status)
                .addValue("sendDate", Timestamp.valueOf(sendDate))
                .addValue("inProgressStatus", inProgressStatus);
        if (!incrementAttempts) {
            return jdbc.update(sql("sms.markResult.keepAttempts"), params);
        }
        return jdbc.update(sql("sms.markResult.incrementAttempts"), params.addValue("messageId", messageId));
    }

    private String sql(final String key) {
        try {
            return sqlQueriesManager.getSQLQueryFromConfig(key);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
