package com.alramz.repository;

import com.alramz.model.EmailLogEntry;
import com.alramz.model.EmailTemplate;
import com.alramz.utils.SqlQueriesManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real sql-queries.xml SQL against the 002 changeset on H2 in Postgres mode (no Spring context). */
class EmailTemplateRepositoryTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static NamedParameterJdbcTemplate jdbc;
    private static EmailTemplateRepository repository;

    @BeforeAll
    static void setUp() throws Exception {
        final DriverManagerDataSource dataSource =
                new DriverManagerDataSource("jdbc:h2:mem:emailrepo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("db/changelog/test/sql/002-notification-email-tables.sql"));
        }
        jdbc = new NamedParameterJdbcTemplate(dataSource);
        repository = new EmailTemplateRepository(jdbc,
                new SqlQueriesManager(new MockEnvironment().withProperty("sql.file", "classpath:sql/sql-queries.xml")));
        jdbc.update("INSERT INTO email_configurations (id, template_name, number_of_placeholders, max_attempts, from_email,"
                + " en_subject, ar_subject, en_body, ar_body, attachments_max_size, number_of_attachments,"
                + " type_of_attachments, is_bulk) VALUES ('T1', 'STATEMENT', 2, 3, 'notification@alramz.ae', 'S', 'ع',"
                + " 'Dear %1%', '<p>%1%</p>', '1000', 2, 'PDF,PNG', 1)", Map.of());
        jdbc.update("INSERT INTO email_configurations (id, template_name, max_attempts, from_email)"
                + " VALUES ('T2', 'MINIMAL', 1, 'notification@alramz.ae')", Map.of());
    }

    private static EmailLogEntry entry(final String to, final String invoker, final LocalDateTime at) {
        return new EmailLogEntry("T1", to, null, null, "a".repeat(64), "EN", at, invoker, "::1", "{}");
    }

    @Test
    void findByName_mapsEveryColumn() {
        final EmailTemplate t = repository.findByName("STATEMENT").orElseThrow();

        assertThat(t).isEqualTo(new EmailTemplate("T1", "STATEMENT", 2, 3, "notification@alramz.ae", "S", "ع",
                "Dear %1%", "<p>%1%</p>", "1000", 2, "PDF,PNG", true));
    }

    @Test
    void findByName_keepsNullsAndDefaults() {
        final EmailTemplate t = repository.findByName("MINIMAL").orElseThrow();

        assertThat(t.numberOfPlaceholders()).isZero();
        assertThat(t.numberOfAttachments()).isNull();
        assertThat(t.bulk()).isFalse();
        assertThat(t.enBody()).isNull();
    }

    @Test
    void findByName_isExactAndCaseSensitive() {
        assertThat(repository.findByName("statement")).isEmpty();
        assertThat(repository.findByName("NOPE")).isEmpty();
    }

    @Test
    void countSentToday_bulkAndPerRecipientWithinDayBounds() {
        repository.insertLog(entry("a@example.com", "count-app", DAY));                    // start: included
        repository.insertLog(entry("a@example.com", "count-app", DAY.plusHours(23).plusMinutes(59)));
        repository.insertLog(entry("b@example.com", "count-app", DAY.plusHours(1)));
        repository.insertLog(entry("a@example.com", "count-app", DAY.minusSeconds(1)));     // yesterday (E15)
        repository.insertLog(entry("a@example.com", "count-app", DAY.plusDays(1)));         // tomorrow: excluded
        repository.insertLog(entry("a@example.com", "other-app", DAY.plusHours(2)));        // other invoker

        assertThat(repository.countSentToday("T1", "count-app", null, DAY, DAY.plusDays(1))).isEqualTo(3);
        assertThat(repository.countSentToday("T1", "count-app", "a@example.com", DAY, DAY.plusDays(1))).isEqualTo(2);
        assertThat(repository.countSentToday("T2", "count-app", null, DAY, DAY.plusDays(1))).isZero();
    }

    @Test
    void insertLog_writesEveryColumn() {
        repository.insertLog(new EmailLogEntry("T9", "x@example.com", "c@example.com", "b@example.com", "f".repeat(64),
                "AR", DAY, "insert-app", "10.0.0.1", "{\"attachmentMetaData\":[]}"));

        final Map<String, Object> row = jdbc.queryForMap("SELECT * FROM email_log WHERE template_id = 'T9'", Map.of());
        assertThat(row).containsEntry("TO_EMAIL", "x@example.com").containsEntry("CC_EMAIL", "c@example.com")
                .containsEntry("BCC_EMAIL", "b@example.com").containsEntry("LANGUAGE", "AR")
                .containsEntry("INVOKER", "insert-app").containsEntry("IP_ADDRESS", "10.0.0.1")
                .containsEntry("ATTACHMENTS_META_DATA", "{\"attachmentMetaData\":[]}");
        assertThat(row.get("ID")).isNotNull();
    }
}
