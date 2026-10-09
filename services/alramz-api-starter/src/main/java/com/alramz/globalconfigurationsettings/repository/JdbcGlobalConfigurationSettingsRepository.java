package com.alramz.globalconfigurationsettings.repository;

import com.alramz.globalconfigurationsettings.mapper.GlobalConfigurationSettingsRowMapper;
import com.alramz.globalconfigurationsettings.model.GlobalConfigurationSettings;
import com.alramz.utils.SqlQueriesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JdbcGlobalConfigurationSettingsRepository implements GlobalConfigurationSettingsRepository {

    private static final Logger logger = LoggerFactory.getLogger(JdbcGlobalConfigurationSettingsRepository.class);

    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;
    private final GlobalConfigurationSettingsRowMapper rowMapper;
    private final SqlQueriesManager sqlQueriesManager;

    public JdbcGlobalConfigurationSettingsRepository(
            @Qualifier("middlewareNamedParameterJdbcTemplate") NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate,
            GlobalConfigurationSettingsRowMapper rowMapper,
            SqlQueriesManager sqlQueriesManager
    ) {
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
        this.rowMapper = rowMapper;
        this.sqlQueriesManager = sqlQueriesManager;
        logger.info("[Bean: JdbcGlobalConfigurationSettingsRepository] - Successfully Created");
    }

    @Override
    public List<GlobalConfigurationSettings> find(String identifier, String identifierType, String status) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be null or blank");
        }

        try {
            String queryKey = selectQueryKey(identifierType, status);
            String sql = sqlQueriesManager.getSQLQueryFromConfig(queryKey);
            MapSqlParameterSource params = buildParameters(identifier, identifierType, status);

            List<GlobalConfigurationSettings> result = middlewareNamedParameterJdbcTemplate.query(sql, params, rowMapper);
            return List.copyOf(result);
        } catch (DataAccessException e) {
            logger.error("Failed to find global configuration settings for identifier: {}", identifier, e);
            throw e;
        } catch (Exception e) {
            logger.error("Error loading SQL query for find operation with identifier: {}", identifier, e);
            throw new RuntimeException("Failed to find global configuration settings", e);
        }
    }

    @Override
    public List<String> findIdentifierTexts(String identifier, String identifierType, String status) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be null or blank");
        }

        try {
            String queryKey = selectIdentifierTextsQueryKey(identifierType, status);
            String sql = sqlQueriesManager.getSQLQueryFromConfig(queryKey);
            MapSqlParameterSource params = buildParameters(identifier, identifierType, status);

            List<String> result = middlewareNamedParameterJdbcTemplate.queryForList(sql, params, String.class);
            return List.copyOf(result);
        } catch (DataAccessException e) {
            logger.error("Failed to find identifier texts for identifier: {}", identifier, e);
            throw e;
        } catch (Exception e) {
            logger.error("Error loading SQL query for findIdentifierTexts operation with identifier: {}", identifier, e);
            throw new RuntimeException("Failed to find identifier texts", e);
        }
    }

    @Override
    public Map<String, List<String>> findAsMap(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be null or blank");
        }

        List<GlobalConfigurationSettings> dataList = find(identifier);
        if (dataList.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> texts = dataList.stream()
            .map(GlobalConfigurationSettings::getIdentifierText)
            .collect(Collectors.toUnmodifiableList());

        return Collections.unmodifiableMap(
            Map.of(identifier, texts)
        );
    }

    @Override
    public int updateIdentifierText(String identifier, String identifierType, String identifierText, String status) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier cannot be null or blank");
        }

        try {
            String queryKey = status != null && !status.isBlank()
                ? "global.configuration.settings.update"
                : "global.configuration.settings.update.without.status";

            String sql = sqlQueriesManager.getSQLQueryFromConfig(queryKey);
            MapSqlParameterSource params = new MapSqlParameterSource();
            params.addValue("identifier", identifier);
            params.addValue("identifierType", identifierType);
            params.addValue("identifierText", identifierText);
            if (status != null && !status.isBlank()) {
                params.addValue("status", status);
            }

            return middlewareNamedParameterJdbcTemplate.update(sql, params);
        } catch (DataAccessException e) {
            logger.error("Failed to update identifier text for identifier: {}", identifier, e);
            throw e;
        } catch (Exception e) {
            logger.error("Error loading SQL query for updateIdentifierText operation with identifier: {}", identifier, e);
            throw new RuntimeException("Failed to update identifier text", e);
        }
    }

    private String selectQueryKey(String identifierType, String status) {
        boolean hasType = identifierType != null && !identifierType.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        if (hasType && hasStatus) {
            return "global.configuration.settings.find.with.type.and.status";
        } else if (hasType) {
            return "global.configuration.settings.find.with.type";
        } else if (hasStatus) {
            return "global.configuration.settings.find.with.status";
        } else {
            return "global.configuration.settings.find";
        }
    }

    private String selectIdentifierTextsQueryKey(String identifierType, String status) {
        boolean hasType = identifierType != null && !identifierType.isBlank();
        boolean hasStatus = status != null && !status.isBlank();

        if (hasType && hasStatus) {
            return "global.configuration.settings.find.identifier.texts.with.type.and.status";
        } else if (hasType) {
            return "global.configuration.settings.find.identifier.texts.with.type";
        } else if (hasStatus) {
            return "global.configuration.settings.find.identifier.texts.with.status";
        } else {
            return "global.configuration.settings.find.identifier.texts";
        }
    }

    private MapSqlParameterSource buildParameters(String identifier, String identifierType, String status) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("identifier", identifier);

        if (identifierType != null && !identifierType.isBlank()) {
            params.addValue("identifierType", identifierType);
        }
        if (status != null && !status.isBlank()) {
            params.addValue("status", status);
        }

        return params;
    }
}
