package com.alramz.datasource;

import com.alramz.datasource.config.EncryptionAutoConfiguration;
import com.alramz.datasource.config.MiddlewareDataSourceAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceLifecycleTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MiddlewareDataSourceAutoConfiguration.class, EncryptionAutoConfiguration.class)
            .withPropertyValues(
                    "cipher.password=1234567890123456",
                    "company.datasource.middleware.enabled=true",
                    "company.datasource.middleware.url=jdbc:h2:mem:testdb",
                    "company.datasource.middleware.username=sa",
                    "company.datasource.middleware.plainPassword=sa",
                    "company.datasource.middleware.driverClassName=org.h2.Driver",
                    "company.datasource.middleware.startup-validation.enabled=false"
            );

    @Test
    void hikariDataSourceClosesOnContextClose() {
        contextRunner.run(context -> {
            HikariDataSource dataSource = context.getBean("middlewareDataSource", HikariDataSource.class);
            assertThat(dataSource).isNotNull();
            assertThat(dataSource.isClosed()).isFalse();
        });
    }
}
