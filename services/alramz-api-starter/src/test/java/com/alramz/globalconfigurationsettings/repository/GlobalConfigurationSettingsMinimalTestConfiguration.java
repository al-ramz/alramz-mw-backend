package com.alramz.globalconfigurationsettings.repository;

import com.alramz.utils.SqlQueriesManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@TestConfiguration
@Import({
    com.alramz.globalconfigurationsettings.config.GlobalConfigurationSettingsAutoConfiguration.class,
    SqlQueriesManager.class
})
public class GlobalConfigurationSettingsMinimalTestConfiguration {

    @Bean(name = "middlewareNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(new JdbcTemplate(dataSource));
    }
}
