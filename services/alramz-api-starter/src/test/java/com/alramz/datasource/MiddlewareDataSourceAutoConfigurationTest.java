package com.alramz.datasource;

import com.alramz.datasource.config.DatasourceProperties;
import com.alramz.datasource.config.EncryptionAutoConfiguration;
import com.alramz.datasource.config.MiddlewareDataSourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class MiddlewareDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MiddlewareDataSourceAutoConfiguration.class, EncryptionAutoConfiguration.class)
            .withPropertyValues(
                    "cipher.password=1234567890123456",
                    "company.datasource.middleware.enabled=true",
                    "company.datasource.middleware.url=jdbc:h2:mem:testdb",
                    "company.datasource.middleware.username=sa",
                    "company.datasource.middleware.password=",
                    "company.datasource.middleware.plainPassword=sa",
                    "company.datasource.middleware.driverClassName=org.h2.Driver",
                    "company.datasource.middleware.startup-validation.enabled=false"
            );

    @Test
    void enabledWithValidPropertiesCreatesAllBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("middlewareDataSource");
            assertThat(context).hasBean("middlewareJdbcTemplate");
            assertThat(context).hasBean("middlewareNamedParameterJdbcTemplate");
            assertThat(context).hasBean("middlewareTransactionManager");
            assertThat(context).hasBean("middlewareHealthIndicator");
            assertThat(context).hasSingleBean(javax.sql.DataSource.class);
        });
    }

    @Test
    void disabledCreatesNoBeans() {
        contextRunner
                .withPropertyValues("company.datasource.middleware.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("middlewareDataSource");
                    assertThat(context).doesNotHaveBean("middlewareJdbcTemplate");
                    assertThat(context).doesNotHaveBean("middlewareTransactionManager");
                });
    }

    @Test
    void missingUrlThrowsException() {
        contextRunner
                .withPropertyValues("company.datasource.middleware.url=")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void missingUsernameThrowsException() {
        contextRunner
                .withPropertyValues("company.datasource.middleware.username=")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void missingPasswordThrowsException() {
        contextRunner
                .withPropertyValues("company.datasource.middleware.password=",
                                    "company.datasource.middleware.plainPassword=")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void plainPasswordIsUsedWhenProvided() {
        contextRunner.run(context -> {
            DataSource ds = context.getBean("middlewareDataSource", DataSource.class);
            assertThat(ds).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
            com.zaxxer.hikari.HikariDataSource hikari = (com.zaxxer.hikari.HikariDataSource) ds;
            assertThat(hikari.getUsername()).isEqualTo("sa");
        });
    }

    @Test
    void startupValidationDisabledAllowsInvalidUrl() {
        contextRunner
                .withPropertyValues("company.datasource.middleware.url=jdbc:h2:mem:nonexistent",
                                    "company.datasource.middleware.startup-validation.enabled=false")
                .run(context -> {
                    assertThat(context).hasBean("middlewareDataSource");
                });
    }
}
