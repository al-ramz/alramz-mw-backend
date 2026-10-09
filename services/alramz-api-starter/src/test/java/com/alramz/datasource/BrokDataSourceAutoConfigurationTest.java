package com.alramz.datasource;

import com.alramz.datasource.config.BrokDataSourceAutoConfiguration;
import com.alramz.datasource.config.EncryptionAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrokDataSourceAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BrokDataSourceAutoConfiguration.class, EncryptionAutoConfiguration.class)
            .withPropertyValues(
                    "cipher.password=1234567890123456",
                    "company.datasource.brok.enabled=true",
                    "company.datasource.brok.url=jdbc:h2:mem:broktest",
                    "company.datasource.brok.username=sa",
                    "company.datasource.brok.password=",
                    "company.datasource.brok.plainPassword=brok_pass",
                    "company.datasource.brok.driverClassName=org.h2.Driver",
                    "company.datasource.brok.startup-validation.enabled=false"
            );

    @Test
    void enabledWithValidPropertiesCreatesAllBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("brokDataSource");
            assertThat(context).hasBean("brokJdbcTemplate");
            assertThat(context).hasBean("brokNamedParameterJdbcTemplate");
            assertThat(context).hasBean("brokTransactionManager");
            assertThat(context).hasBean("brokHealthIndicator");
        });
    }

    @Test
    void disabledCreatesNoBeans() {
        contextRunner
                .withPropertyValues("company.datasource.brok.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("brokDataSource");
                    assertThat(context).doesNotHaveBean("brokJdbcTemplate");
                    assertThat(context).doesNotHaveBean("brokTransactionManager");
                });
    }

    @Test
    void missingUrlThrowsException() {
        contextRunner
                .withPropertyValues("company.datasource.brok.url=")
                .run(context -> {
                    assertThat(context).hasFailed();
                });
    }

    @Test
    void plainPasswordIsUsedWhenProvided() {
        contextRunner.run(context -> {
            DataSource ds = context.getBean("brokDataSource", DataSource.class);
            assertThat(ds).isInstanceOf(HikariDataSource.class);
            HikariDataSource hikari = (HikariDataSource) ds;
            assertThat(hikari.getUsername()).isEqualTo("sa");
        });
    }
}
