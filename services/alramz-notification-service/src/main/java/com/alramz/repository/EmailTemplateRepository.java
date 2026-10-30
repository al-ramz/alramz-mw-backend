package com.alramz.repository;

import com.alramz.model.EmailLogEntry;
import com.alramz.model.EmailTemplate;
import com.alramz.utils.SqlQueriesManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Optional;

/** email_configurations and email_log on the middleware (Postgres) datasource. SQL lives in sql-queries.xml. */
@Repository
public class EmailTemplateRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final SqlQueriesManager sqlQueriesManager;

    public EmailTemplateRepository(
            @Qualifier("middlewareNamedParameterJdbcTemplate") final NamedParameterJdbcTemplate jdbc,
            final SqlQueriesManager sqlQueriesManager) {
        this.jdbc = jdbc;
        this.sqlQueriesManager = sqlQueriesManager;
    }

    public Optional<EmailTemplate> findByName(final String templateName) {
        return jdbc.query(sql("email.template.findByName"), new MapSqlParameterSource("templateName", templateName),
                (rs, rowNum) -> new EmailTemplate(
                        rs.getString("id"),
                        rs.getString("template_name"),
                        rs.getInt("number_of_placeholders"),
                        rs.getInt("max_attempts"),
                        rs.getString("from_email"),
                        rs.getString("en_subject"),
                        rs.getString("ar_subject"),
                        rs.getString("en_body"),
                        rs.getString("ar_body"),
                        rs.getString("attachments_max_size"),
                        rs.getObject("number_of_attachments", Integer.class),
                        rs.getString("type_of_attachments"),
                        rs.getInt("is_bulk") == 1)).stream().findFirst();
    }

    /** Rows for [dayStart, dayEnd). toOrNull == null counts template + invoker (bulk), else adds the recipient. */
    public int countSentToday(final String templateId, final String invoker, final String toOrNull,
                              final LocalDateTime dayStart, final LocalDateTime dayEnd) {
        final MapSqlParameterSource params = new MapSqlParameterSource("templateId", templateId)
                .addValue("invoker", invoker)
                .addValue("dayStart", dayStart)
                .addValue("dayEnd", dayEnd);
        String key = "email.log.countBulk";
        if (toOrNull != null) {
            key = "email.log.countPerRecipient";
            params.addValue("to", toOrNull);
        }
        final Integer count = jdbc.queryForObject(sql(key), params, Integer.class);
        return count == null ? 0 : count;
    }

    public void insertLog(final EmailLogEntry entry) {
        jdbc.update(sql("email.log.insert"), new MapSqlParameterSource("templateId", entry.templateId())
                .addValue("to", entry.to())
                .addValue("cc", entry.cc())
                .addValue("bcc", entry.bcc())
                .addValue("emailBody", entry.bodySha256())
                .addValue("language", entry.language())
                .addValue("dateTime", entry.dateTime())
                .addValue("invoker", entry.invoker())
                .addValue("ipAddress", entry.ipAddress())
                .addValue("attachmentsMetaData", entry.attachmentsMetaData()));
    }

    private String sql(final String key) {
        try {
            return sqlQueriesManager.getSQLQueryFromConfig(key);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
