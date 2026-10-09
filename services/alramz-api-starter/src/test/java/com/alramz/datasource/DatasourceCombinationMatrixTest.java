package com.alramz.datasource;

import com.alramz.datasource.config.BrokDataSourceAutoConfiguration;
import com.alramz.datasource.config.EncryptionAutoConfiguration;
import com.alramz.datasource.config.IntegrationDataSourceAutoConfiguration;
import com.alramz.datasource.config.MiddlewareDataSourceAutoConfiguration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceCombinationMatrixTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    EncryptionAutoConfiguration.class,
                    MiddlewareDataSourceAutoConfiguration.class,
                    BrokDataSourceAutoConfiguration.class,
                    IntegrationDataSourceAutoConfiguration.class)
            .withPropertyValues(
                    "cipher.password=1234567890123456",
                    "company.datasource.middleware.url=jdbc:h2:mem:testdb",
                    "company.datasource.middleware.username=sa",
                    "company.datasource.middleware.plainPassword=sa",
                    "company.datasource.middleware.driverClassName=org.h2.Driver",
                    "company.datasource.middleware.startup-validation.enabled=false",
                    "company.datasource.brok.url=jdbc:h2:mem:broktest",
                    "company.datasource.brok.username=sa",
                    "company.datasource.brok.plainPassword=sa",
                    "company.datasource.brok.driverClassName=org.h2.Driver",
                    "company.datasource.brok.startup-validation.enabled=false",
                    "company.datasource.integration.url=jdbc:h2:mem:inttest",
                    "company.datasource.integration.username=sa",
                    "company.datasource.integration.plainPassword=sa",
                    "company.datasource.integration.driverClassName=org.h2.Driver",
                    "company.datasource.integration.startup-validation.enabled=false"
            );

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void middlewareBeanPresence(boolean enabled) {
        contextRunner
                .withPropertyValues("company.datasource.middleware.enabled=" + enabled)
                .run(context -> {
                    if (enabled) {
                        assertThat(context).hasBean("middlewareDataSource");
                        assertThat(context).hasBean("middlewareJdbcTemplate");
                        assertThat(context).hasBean("middlewareNamedParameterJdbcTemplate");
                        assertThat(context).hasBean("middlewareTransactionManager");
                        assertThat(context).hasBean("middlewareHealthIndicator");
                    } else {
                        assertThat(context).doesNotHaveBean("middlewareDataSource");
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void brokBeanPresence(boolean enabled) {
        contextRunner
                .withPropertyValues("company.datasource.brok.enabled=" + enabled)
                .run(context -> {
                    if (enabled) {
                        assertThat(context).hasBean("brokDataSource");
                        assertThat(context).hasBean("brokJdbcTemplate");
                        assertThat(context).hasBean("brokNamedParameterJdbcTemplate");
                        assertThat(context).hasBean("brokTransactionManager");
                        assertThat(context).hasBean("brokHealthIndicator");
                    } else {
                        assertThat(context).doesNotHaveBean("brokDataSource");
                    }
                });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void integrationBeanPresence(boolean enabled) {
        contextRunner
                .withPropertyValues("company.datasource.integration.enabled=" + enabled)
                .run(context -> {
                    if (enabled) {
                        assertThat(context).hasBean("integrationDataSource");
                        assertThat(context).hasBean("integrationJdbcTemplate");
                        assertThat(context).hasBean("integrationNamedParameterJdbcTemplate");
                        assertThat(context).hasBean("integrationTransactionManager");
                        assertThat(context).hasBean("integrationHealthIndicator");
                    } else {
                        assertThat(context).doesNotHaveBean("integrationDataSource");
                    }
                });
    }
}
