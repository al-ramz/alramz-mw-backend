package com.alramz.repository;

import com.alramz.utils.SqlQueriesManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * otp_configuration, otp_management and otp_attempts on the middleware (Postgres) datasource. SQL lives in
 * sql-queries.xml. Single statements only: OtpService owns the transactions.
 */
@Repository
public class OtpRepository {

    public record OtpConfig(int messageId, String serviceName, int expiryInSeconds, int tokenLength,
                            String smsMessageEnglish, String smsMessageArabic, int maxAttempts, String sentVia,
                            String emailMessageEnglish, String emailMessageArabic, Integer maxValidationAttempts) {
    }

    /** A new otp_management row; it is always inserted active with status PENDING. */
    public record OtpRow(String serviceName, String mobileEmail, String otpHash, LocalDateTime created,
                         LocalDateTime expiry, String ipAddress) {
    }

    public record OtpCandidate(long id, String mobileEmail, String otpHash, int validationAttempts) {
    }

    private static final RowMapper<OtpConfig> CONFIG = (rs, rowNum) -> new OtpConfig(
            rs.getInt("message_id"),
            rs.getString("service_name"),
            rs.getInt("expiry_in_seconds"),
            rs.getInt("token_length"),
            rs.getString("sms_message_english"),
            rs.getString("sms_message_arabic"),
            rs.getInt("max_attempts"),
            rs.getString("sent_via"),
            rs.getString("email_message_english"),
            rs.getString("email_message_arabic"),
            rs.getObject("max_validation_attempts", Integer.class));

    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueriesManager sqlQueriesManager;

    public OtpRepository(
            @Qualifier("middlewareNamedParameterJdbcTemplate") final NamedParameterJdbcTemplate jdbc,
            final SqlQueriesManager sqlQueriesManager) {
        this.jdbc = jdbc;
        this.sqlQueriesManager = sqlQueriesManager;
    }

    public Optional<OtpConfig> findConfig(final String serviceName) {
        return jdbc.query(sql("otp.config.findByName"), new MapSqlParameterSource("serviceName", serviceName), CONFIG)
                .stream().findFirst();
    }

    public List<OtpConfig> findAllConfigs() {
        return jdbc.query(sql("otp.config.findAll"), CONFIG);
    }

    public int maxAttemptsSince(final String serviceName, final Collection<String> recipients,
                                final LocalDateTime dayStart) {
        final Integer max = jdbc.queryForObject(sql("otp.attempts.maxSince"),
                recipients(serviceName, recipients).addValue("dayStart", dayStart), Integer.class);
        return max == null ? 0 : max;
    }

    public void deactivateActive(final String serviceName, final Collection<String> recipients) {
        jdbc.update(sql("otp.management.deactivateActive"), recipients(serviceName, recipients));
    }

    public long insertOtp(final OtpRow row) {
        final GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(sql("otp.management.insert"), new MapSqlParameterSource("serviceName", row.serviceName())
                .addValue("mobileEmail", row.mobileEmail())
                .addValue("otp", row.otpHash())
                .addValue("created", row.created())
                .addValue("expiry", row.expiry())
                .addValue("ipAddress", row.ipAddress()), keyHolder, new String[]{"id"});
        // getKey(), not getKeys().get("id"): H2 reports the column upper-cased.
        return keyHolder.getKey().longValue();
    }

    public void markSent(final Collection<Long> ids) {
        jdbc.update(sql("otp.management.markSent"), new MapSqlParameterSource("ids", ids));
    }

    public void markFailed(final Collection<Long> ids) {
        jdbc.update(sql("otp.management.markFailed"), new MapSqlParameterSource("ids", ids));
    }

    /** Daily send counter +1, restarting at 1 when the row was last touched before dayStart. */
    public void upsertAttempt(final String serviceName, final String recipient, final LocalDateTime now,
                              final LocalDateTime dayStart) {
        final MapSqlParameterSource params = new MapSqlParameterSource("serviceName", serviceName)
                .addValue("recipient", recipient)
                .addValue("now", now)
                .addValue("dayStart", dayStart);
        jdbc.update(sql("otp.attempts.insertIfAbsent"), params);
        jdbc.update(sql("otp.attempts.update"), params);
    }

    /** Active, unexpired rows, newest first, locked FOR UPDATE: call inside a transaction. */
    public List<OtpCandidate> lockActiveCandidates(final String serviceName, final Collection<String> recipients,
                                                   final LocalDateTime now) {
        return jdbc.query(sql("otp.management.lockCandidates"), recipients(serviceName, recipients).addValue("now", now),
                (rs, rowNum) -> new OtpCandidate(rs.getLong("id"), rs.getString("mobile_email"), rs.getString("otp"),
                        rs.getInt("validation_attempts")));
    }

    public void markValidated(final Collection<Long> ids) {
        jdbc.update(sql("otp.management.markValidated"), new MapSqlParameterSource("ids", ids));
    }

    public void incrementValidationAttempts(final Collection<Long> ids) {
        jdbc.update(sql("otp.management.incrementValidation"), new MapSqlParameterSource("ids", ids));
    }

    public void resetAttempts(final String serviceName, final Collection<String> recipients) {
        jdbc.update(sql("otp.attempts.reset"), recipients(serviceName, recipients));
    }

    private static MapSqlParameterSource recipients(final String serviceName, final Collection<String> recipients) {
        return new MapSqlParameterSource("serviceName", serviceName).addValue("recipients", recipients);
    }

    private String sql(final String key) {
        try {
            return sqlQueriesManager.getSQLQueryFromConfig(key);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
