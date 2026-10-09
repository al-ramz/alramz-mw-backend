package com.alramz.datasource;

import com.alramz.datasource.config.EncryptionAutoConfiguration;
import com.alramz.datasource.config.MiddlewareDataSourceAutoConfiguration;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourceProxyIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MiddlewareDataSourceAutoConfiguration.class, EncryptionAutoConfiguration.class)
            .withPropertyValues(
                    "cipher.password=1234567890123456",
                    "company.datasource.middleware.enabled=true",
                    "company.datasource.middleware.url=jdbc:h2:mem:testdb",
                    "company.datasource.middleware.username=sa",
                    "company.datasource.middleware.plainPassword=sa",
                    "company.datasource.middleware.driverClassName=org.h2.Driver",
                    "company.datasource.middleware.startup-validation.enabled=false",
                    "company.datasource.middleware.sql-logging.enabled=true",
                    "company.datasource.middleware.sql-logging.log-parameters=true",
                    "company.datasource.middleware.sql-logging.slow-query-threshold-ms=100"
            );

    @Test
    void sqlLoggingEnabledWrapsDataSourceWithProxy() {
        contextRunner.run(context -> {
            DataSource dataSource = context.getBean("middlewareDataSource", DataSource.class);
            assertThat(dataSource).isInstanceOf(ProxyDataSource.class);

            ProxyDataSource proxy = (ProxyDataSource) dataSource;
            assertThat(proxy.getDataSource()).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
        });
    }

    @Test
    void sqlLoggingDisabledUsesRawDataSource() {
        contextRunner
                .withPropertyValues("company.datasource.middleware.sql-logging.enabled=false")
                .run(context -> {
                    DataSource dataSource = context.getBean("middlewareDataSource", DataSource.class);
                    assertThat(dataSource).isInstanceOf(com.zaxxer.hikari.HikariDataSource.class);
                    assertThat(dataSource).isNotInstanceOf(ProxyDataSource.class);
                });
    }

    @Test
    void sqlLoggingDisabledStillProxiesForTracing() {
        com.alramz.tracing.listener.TracingQueryExecutionListener tracingListener =
                new com.alramz.tracing.listener.TracingQueryExecutionListener(io.micrometer.tracing.Tracer.NOOP);
        contextRunner
                .withPropertyValues("company.datasource.middleware.sql-logging.enabled=false")
                .withBean(com.alramz.tracing.listener.TracingQueryExecutionListener.class, () -> tracingListener)
                .run(context -> {
                    DataSource dataSource = context.getBean("middlewareDataSource", DataSource.class);
                    assertThat(dataSource).isInstanceOf(ProxyDataSource.class);
                    assertThat(((ProxyDataSource) dataSource).getProxyConfig().getQueryListener().getListeners())
                            .containsExactly(tracingListener);
                });
    }
}
