package com.alramz.repository;

import com.alramz.repository.OtpRepository.OtpCandidate;
import com.alramz.repository.OtpRepository.OtpConfig;
import com.alramz.repository.OtpRepository.OtpRow;
import com.alramz.utils.SqlQueriesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs the real otp.* SQL against the 004 changeset on H2 in Postgres mode (no Spring context). */
class OtpRepositoryTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static final LocalDateTime NOW = DAY.plusHours(10);
    private static final String HASH = "a".repeat(64);
    private static NamedParameterJdbcTemplate jdbc;
    private static OtpRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        final DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:otprepo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/changelog/test/sql/004-notification-otp-tables.sql"));
        }
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        repository = new OtpRepository(jdbc,
                new SqlQueriesManager(new MockEnvironment().withProperty("sql.file", "classpath:sql/sql-queries.xml")));
        insertConfig(1, "LOGIN_OTP", 6, "EMAIL|SMS", 3);
        insertConfig(2, "ALPHA_", 4, "SMS", null);
    }

    private static void insertConfig(final int id, final String name, final int tokenLength, final String sentVia,
                                     final Integer maxValidation) {
        jdbc.update("INSERT INTO otp_configuration (message_id, service_name, expiry_in_seconds, token_length,"
                + " sms_message_english, sms_message_arabic, max_attempts, sent_via, email_message_english,"
                + " email_message_arabic, max_validation_attempts) VALUES (:id, :name, 300, :len, 'OTP %OTP%', 'ع %OTP%',"
                + " 5, :via, 'S ^+^ B %OTP%', NULL, :mv)", new MapSqlParameterSource("id", id).addValue("name", name)
                .addValue("len", tokenLength).addValue("via", sentVia).addValue("mv", maxValidation));
    }

    private static long insert(final String service, final String recipient, final LocalDateTime expiry) {
        return repository.insertOtp(new OtpRow(service, recipient, HASH, NOW, expiry, "10.0.0.1"));
    }

    private static Map<String, Object> row(final long id) {
        return jdbc.queryForMap("SELECT * FROM otp_management WHERE id = :id", Map.of("id", id));
    }

    private static int attempts(final String service, final String recipient) {
        return jdbc.queryForObject("SELECT attempts FROM otp_attempts WHERE service_name = :s AND mobile_email = :r",
                Map.of("s", service, "r", recipient), Integer.class);
    }

    @Test
    void findConfig_mapsEveryColumnAndIsCaseSensitive() {
        assertThat(repository.findConfig("LOGIN_OTP")).contains(new OtpConfig(1, "LOGIN_OTP", 300, 6, "OTP %OTP%",
                "ع %OTP%", 5, "EMAIL|SMS", "S ^+^ B %OTP%", null, 3));
        assertThat(repository.findConfig("ALPHA_").orElseThrow().maxValidationAttempts()).isNull();
        assertThat(repository.findConfig("login_otp")).isEmpty();
    }

    @Test
    void findAllConfigs_isOrderedByServiceName() {
        assertThat(repository.findAllConfigs()).extracting(OtpConfig::serviceName).containsExactly("ALPHA_", "LOGIN_OTP");
    }

    @Test
    void schemaRejectsValuesOutsideTheConstraints() {
        assertThatThrownBy(() -> insertConfig(10, "BAD_LEN", 3, "SMS", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertConfig(11, "BAD_LEN2", 11, "SMS", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertConfig(12, "BAD_VIA", 6, "WHATSAPP", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertConfig(13, "BAD_MV", 6, "SMS", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertConfig(14, "LOGIN_OTP", 6, "SMS", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO otp_management (service_name, mobile_email, otp,"
                + " creation_date_time, expiry_date_time, active, status) VALUES ('S', 'R', 'h', NOW(), NOW(), '1', 'X')",
                Map.of())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO otp_management (service_name, mobile_email, otp,"
                + " creation_date_time, expiry_date_time, active, status) VALUES ('S', 'R', 'h', NOW(), NOW(), '2', 'SENT')",
                Map.of())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void insertOtp_returnsDistinctIdsAndStoresActivePendingRows() {
        final long first = insert("INS", "971500000010", NOW.plusMinutes(5));
        final long second = insert("INS", "971500000010", NOW.plusMinutes(5));

        assertThat(second).isGreaterThan(first);
        assertThat(row(first)).containsEntry("ACTIVE", "1").containsEntry("STATUS", "PENDING")
                .containsEntry("OTP", HASH).containsEntry("IP_ADDRESS", "10.0.0.1").containsEntry("VALIDATION_ATTEMPTS", 0);
    }

    @Test
    void deactivateActive_isScopedToServiceAndRecipients() {
        final long target = insert("DEACT", "A@EXAMPLE.COM", NOW.plusMinutes(5));
        final long otherRecipient = insert("DEACT", "971500000011", NOW.plusMinutes(5));
        final long otherService = insert("DEACT2", "A@EXAMPLE.COM", NOW.plusMinutes(5));

        repository.deactivateActive("DEACT", List.of("A@EXAMPLE.COM"));

        assertThat(row(target)).containsEntry("ACTIVE", "0");
        assertThat(row(otherRecipient)).containsEntry("ACTIVE", "1");
        assertThat(row(otherService)).containsEntry("ACTIVE", "1");
    }

    @Test
    void markSentKeepsActiveAndMarkFailedDeactivates() {
        final long sent = insert("MARK", "971500000012", NOW.plusMinutes(5));
        final long failed = insert("MARK", "B@EXAMPLE.COM", NOW.plusMinutes(5));

        repository.markSent(List.of(sent));
        repository.markFailed(List.of(failed));

        assertThat(row(sent)).containsEntry("STATUS", "SENT").containsEntry("ACTIVE", "1");
        assertThat(row(failed)).containsEntry("STATUS", "FAILED").containsEntry("ACTIVE", "0");
    }

    @Test
    void upsertAttempt_firstInsertThenIncrementThenResetsOnANewDay() {
        repository.upsertAttempt("UPS", "971500000013", NOW, DAY);
        assertThat(attempts("UPS", "971500000013")).isEqualTo(1);

        repository.upsertAttempt("UPS", "971500000013", NOW.plusMinutes(1), DAY);
        assertThat(attempts("UPS", "971500000013")).isEqualTo(2);

        // Next day: last_attempt_date is before the new dayStart, so the counter restarts at 1.
        repository.upsertAttempt("UPS", "971500000013", NOW.plusDays(1), DAY.plusDays(1));
        assertThat(attempts("UPS", "971500000013")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM otp_attempts WHERE service_name = 'UPS'", Map.of(),
                Integer.class)).isEqualTo(1);
    }

    @Test
    void maxAttemptsSince_ignoresYesterdayAndOtherRecipients() {
        repository.upsertAttempt("MAX", "971500000014", NOW, DAY);
        repository.upsertAttempt("MAX", "971500000014", NOW, DAY);
        repository.upsertAttempt("MAX", "C@EXAMPLE.COM", NOW, DAY);
        repository.upsertAttempt("MAX", "971500000015", DAY.minusSeconds(1), DAY.minusDays(1)); // yesterday

        assertThat(repository.maxAttemptsSince("MAX", List.of("971500000014", "C@EXAMPLE.COM"), DAY)).isEqualTo(2);
        assertThat(repository.maxAttemptsSince("MAX", List.of("971500000015"), DAY)).isZero();
        assertThat(repository.maxAttemptsSince("MAX", List.of("NOBODY"), DAY)).isZero();
    }

    @Test
    void lockActiveCandidates_excludesExpiredInactiveAndOtherRecipientsNewestFirst() {
        final long older = insert("LOCK", "971500000016", NOW.plusMinutes(5));
        final long newer = insert("LOCK", "D@EXAMPLE.COM", NOW.plusMinutes(5));
        insert("LOCK", "971500000016", NOW.minusSeconds(1));                       // expired
        final long failed = insert("LOCK", "971500000016", NOW.plusMinutes(5));
        repository.markFailed(List.of(failed));                                   // inactive
        insert("LOCK", "971500000099", NOW.plusMinutes(5));                       // other recipient

        final List<OtpCandidate> candidates =
                repository.lockActiveCandidates("LOCK", List.of("971500000016", "D@EXAMPLE.COM"), NOW);

        assertThat(candidates).containsExactly(new OtpCandidate(newer, "D@EXAMPLE.COM", HASH, 0),
                new OtpCandidate(older, "971500000016", HASH, 0));
    }

    @Test
    void markValidatedTouchesOnlyGivenIdsAndIncrementPersists() {
        final long matched = insert("VAL", "971500000017", NOW.plusMinutes(5));
        final long other = insert("VAL", "971500000018", NOW.plusMinutes(5));

        repository.markValidated(List.of(matched));
        repository.incrementValidationAttempts(List.of(other));
        repository.incrementValidationAttempts(List.of(other));

        assertThat(row(matched)).containsEntry("ACTIVE", "0").containsEntry("STATUS", "VALIDATED");
        assertThat(row(other)).containsEntry("ACTIVE", "1").containsEntry("STATUS", "PENDING")
                .containsEntry("VALIDATION_ATTEMPTS", 2);
    }

    @Test
    void resetAttempts_isScopedToServiceAndRecipients() {
        repository.upsertAttempt("RST", "971500000019", NOW, DAY);
        repository.upsertAttempt("RST", "E@EXAMPLE.COM", NOW, DAY);
        repository.upsertAttempt("RST2", "971500000019", NOW, DAY);

        repository.resetAttempts("RST", List.of("971500000019"));

        assertThat(attempts("RST", "971500000019")).isZero();
        assertThat(attempts("RST", "E@EXAMPLE.COM")).isEqualTo(1);
        assertThat(attempts("RST2", "971500000019")).isEqualTo(1);
    }
}
