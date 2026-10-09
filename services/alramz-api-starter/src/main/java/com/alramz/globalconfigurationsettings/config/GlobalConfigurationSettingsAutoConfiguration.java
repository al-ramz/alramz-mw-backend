package com.alramz.globalconfigurationsettings.config;

import com.alramz.globalconfigurationsettings.mapper.GlobalConfigurationSettingsRowMapper;
import com.alramz.globalconfigurationsettings.repository.JdbcGlobalConfigurationSettingsRepository;
import com.alramz.globalconfigurationsettings.repository.GlobalConfigurationSettingsRepository;
import com.alramz.globalconfigurationsettings.service.GlobalConfigurationSettingsService;
import com.alramz.utils.SqlQueriesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Auto-configuration for the global configuration settings framework.
 * Enabled only when the middleware datasource is enabled.
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "company.datasource.middleware",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = false
)
@ConditionalOnBean(name = "middlewareNamedParameterJdbcTemplate")
@Import({
    SqlQueriesManager.class,
    GlobalConfigurationSettingsRowMapper.class
})
public class GlobalConfigurationSettingsAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GlobalConfigurationSettingsAutoConfiguration.class);

    @Bean
    public GlobalConfigurationSettingsRepository referenceDataRepository(
            @Qualifier("middlewareNamedParameterJdbcTemplate") NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate,
            GlobalConfigurationSettingsRowMapper rowMapper,
            SqlQueriesManager sqlQueriesManager
    ) {
        logger.info("[Bean: referenceDataRepository] - Successfully Created");
        return new JdbcGlobalConfigurationSettingsRepository(middlewareNamedParameterJdbcTemplate, rowMapper, sqlQueriesManager);
    }

    @Bean
    public GlobalConfigurationSettingsService referenceDataService(GlobalConfigurationSettingsRepository repository) {
        logger.info("[Bean: referenceDataService] - Successfully Created");
        return new GlobalConfigurationSettingsService(repository);
    }
}
