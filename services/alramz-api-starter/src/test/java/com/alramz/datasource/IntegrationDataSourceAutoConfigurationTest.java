package com.alramz.datasource;

import com.alramz.datasource.config.EncryptionAutoConfiguration;
import com.alramz.datasource.config.IntegrationDataSourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IntegrationDataSourceAutoConfiguration.class, EncryptionAutoConfiguration.class)
            .withPropertyValues(
                    "cipher.password=1234567890123456",
                    "company.datasource.integration.enabled=true",
                    "company.datasource.integration.url=jdbc:h2:mem:inttest",
                    "company.datasource.integration.username=sa",
                    "company.datasource.integration.password=",
                    "company.datasource.integration.plainPassword=int_pass",
                    "company.datasource.integration.driverClassName=org.h2.Driver",
                    "company.datasource.integration.startup-validation.enabled=false"
            );

    @Test
    void enabledWithValidPropertiesCreatesAllBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("integrationDataSource");
            assertThat(context).hasBean("integrationJdbcTemplate");
            assertThat(context).hasBean("integrationNamedParameterJdbcTemplate");
            assertThat(context).hasBean("integrationTransactionManager");
            assertThat(context).hasBean("integrationHealthIndicator");
        });
    }

    @Test
    void disabledCreatesNoBeans() {
        contextRunner
                .withPropertyValues("company.datasource.integration.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("integrationDataSource");
                    assertThat(context).doesNotHaveBean("integrationJdbcTemplate");
                    assertThat(context).doesNotHaveBean("integrationTransactionManager");
                });
    }

    @Test
    void missingUrlThrowsException() {
        contextRunner
                .withPropertyValues("company.datasource.integration.url=")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void plainPasswordIsUsedWhenProvided() {
        contextRunner.run(context -> {
            DataSource ds = context.getBean("integrationDataSource", DataSource.class);
            assertThat(ds).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
            com.zaxxer.hikari.HikariDataSource hikari = (com.zaxxer.hikari.HikariDataSource) ds;
            assertThat(hikari.getUsername()).isEqualTo("sa");
        });
    }
}
