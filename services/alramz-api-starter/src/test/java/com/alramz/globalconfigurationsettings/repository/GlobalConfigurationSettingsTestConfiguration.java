package com.alramz.globalconfigurationsettings.repository;

import com.alramz.utils.SqlQueriesManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@TestConfiguration
@Import({
    com.alramz.globalconfigurationsettings.config.GlobalConfigurationSettingsAutoConfiguration.class,
    SqlQueriesManager.class
})
public class GlobalConfigurationSettingsTestConfiguration {

    @Primary
    @Bean(name = "dataSource")
    public DataSource dataSource(Environment environment) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        String url = environment.getProperty("spring.datasource.url", "jdbc:h2:mem:test");
        String username = environment.getProperty("spring.datasource.username", "sa");
        String password = environment.getProperty("spring.datasource.password", "");
        String driverClassName = environment.getProperty("spring.datasource.driverClassName", "org.h2.Driver");

        System.out.println("DataSource URL: " + url);
        System.out.println("DataSource Username: " + username);
        System.out.println("DataSource Driver: " + driverClassName);

        dataSource.setUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setDriverClassName(driverClassName);
        return dataSource;
    }

    @Bean(name = "jdbcTemplate")
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "middlewareNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate(DataSource dataSource) {
        return new NamedParameterJdbcTemplate(new JdbcTemplate(dataSource));
    }
}
