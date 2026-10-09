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
@ConditionalOnProperty(prefix = "company.datasource.brok", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(DatasourceProperties.class)
public class BrokDataSourceAutoConfiguration extends AbstractDataSourceAutoConfiguration {

    @Bean(name = "brokDataSource", destroyMethod = "close")
    public DataSource brokDataSource(DatasourceProperties properties, PWProtector pwProtector, ObjectProvider<TracingQueryExecutionListener> tracingListenerProvider) {
        HikariDataSource hikariDataSource = createHikariDataSource(properties.getBrok(), pwProtector, "brok");
        return wrapWithProxy(hikariDataSource, properties.getBrok().getSqlLogging(), "brok", tracingListenerProvider); // NOPMD LawOfDemeter
    }

    @Bean(name = "brokJdbcTemplate")
    public JdbcTemplate brokJdbcTemplate(@Qualifier("brokDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "brokNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate brokNamedParameterJdbcTemplate(@Qualifier("brokDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "brokTransactionManager")
    public DataSourceTransactionManager brokTransactionManager(@Qualifier("brokDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "brokHealthIndicator")
    public PoolHealthIndicator brokHealthIndicator(@Qualifier("brokDataSource") DataSource dataSource) {
        HikariDataSource raw = unwrapHikariDataSource(dataSource);
        return new PoolHealthIndicator(raw, "brok");
    }

    @Override
    protected String getDbName() {
        return "brok";
    }

    @Override
    protected DatasourceProperties.DatasourceConfig getConfig(DatasourceProperties properties) {
        return properties.getBrok();
    }

    @Override
    protected String getDefaultDriverClassName(String dbName) {
        if ("brok".equals(dbName)) {
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
