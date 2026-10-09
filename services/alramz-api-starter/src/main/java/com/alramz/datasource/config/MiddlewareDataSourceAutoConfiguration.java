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
@ConditionalOnProperty(prefix = "company.datasource.middleware", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(DatasourceProperties.class)
public class MiddlewareDataSourceAutoConfiguration extends AbstractDataSourceAutoConfiguration {

    @Bean(name = "middlewareDataSource", destroyMethod = "close")
    public DataSource middlewareDataSource(DatasourceProperties properties, PWProtector pwProtector, ObjectProvider<TracingQueryExecutionListener> tracingListenerProvider) {
        HikariDataSource hikariDataSource = createHikariDataSource(properties.getMiddleware(), pwProtector, "middleware");
        return wrapWithProxy(hikariDataSource, properties.getMiddleware().getSqlLogging(), "middleware", tracingListenerProvider); // NOPMD LawOfDemeter
    }

    @Bean(name = "middlewareJdbcTemplate")
    public JdbcTemplate middlewareJdbcTemplate(@Qualifier("middlewareDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "middlewareNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate(@Qualifier("middlewareDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "middlewareTransactionManager")
    public DataSourceTransactionManager middlewareTransactionManager(@Qualifier("middlewareDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "middlewareHealthIndicator")
    public PoolHealthIndicator middlewareHealthIndicator(@Qualifier("middlewareDataSource") DataSource dataSource) {
        HikariDataSource raw = unwrapHikariDataSource(dataSource);
        return new PoolHealthIndicator(raw, "middleware");
    }

    @Override
    protected String getDbName() {
        return "middleware";
    }

    @Override
    protected DatasourceProperties.DatasourceConfig getConfig(DatasourceProperties properties) {
        return properties.getMiddleware();
    }

    @Override
    protected String getDefaultDriverClassName(String dbName) {
        if ("middleware".equals(dbName)) {
            return "org.postgresql.Driver";
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
