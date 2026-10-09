package com.alramz.datasource.config;

import com.alramz.datasource.health.PoolHealthIndicator;
import com.alramz.utils.PWProtector;
import com.zaxxer.hikari.HikariDataSource;
import com.alramz.tracing.listener.TracingQueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.springframework.beans.factory.ObjectProvider;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnProperty(prefix = "company.datasource.integration", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(DatasourceProperties.class)
public class IntegrationDataSourceAutoConfiguration extends AbstractDataSourceAutoConfiguration {

    @Bean(name = "integrationDataSource", destroyMethod = "close")
    public DataSource integrationDataSource(DatasourceProperties properties, PWProtector pwProtector, ObjectProvider<TracingQueryExecutionListener> tracingListenerProvider) {
        HikariDataSource hikariDataSource = createHikariDataSource(properties.getIntegration(), pwProtector, "integration");
        return wrapWithProxy(hikariDataSource, properties.getIntegration().getSqlLogging(), "integration", tracingListenerProvider); // NOPMD LawOfDemeter
    }

    @Bean(name = "integrationJdbcTemplate")
    public JdbcTemplate integrationJdbcTemplate(@Qualifier("integrationDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "integrationNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate integrationNamedParameterJdbcTemplate(@Qualifier("integrationDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "integrationTransactionManager")
    public DataSourceTransactionManager integrationTransactionManager(@Qualifier("integrationDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "integrationHealthIndicator")
    public PoolHealthIndicator integrationHealthIndicator(@Qualifier("integrationDataSource") DataSource dataSource) {
        HikariDataSource raw = unwrapHikariDataSource(dataSource);
        return new PoolHealthIndicator(raw, "integration");
    }

    @Override
    protected String getDbName() {
        return "integration";
    }

    @Override
    protected DatasourceProperties.DatasourceConfig getConfig(DatasourceProperties properties) {
        return properties.getIntegration();
    }

    @Override
    protected String getDefaultDriverClassName(String dbName) {
        if ("integration".equals(dbName)) {
            return "oracle.jdbc.OracleDriver";
        }
        return null;
    }

    private static HikariDataSource unwrapHikariDataSource(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            return hikari;
        }
        if (dataSource instanceof ProxyDataSource proxy) {
            DataSource underlying = proxy.getDataSource();
            if (underlying instanceof HikariDataSource hikari) {
                return hikari;
            }
        }
        throw new IllegalStateException("Expected HikariDataSource but got: " + dataSource.getClass().getName());
    }
}
