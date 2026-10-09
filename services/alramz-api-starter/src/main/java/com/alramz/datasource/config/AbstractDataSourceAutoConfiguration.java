package com.alramz.datasource.config;

import com.alramz.utils.PWProtector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.alramz.tracing.listener.TracingQueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.listener.logging.DefaultQueryLogEntryCreator;
import org.springframework.beans.factory.ObjectProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public abstract class AbstractDataSourceAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AbstractDataSourceAutoConfiguration.class);

    protected HikariDataSource createHikariDataSource(
            DatasourceProperties.DatasourceConfig config,
            PWProtector pwProtector,
            String dbName) {

        validateConfig(config, dbName);

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(dbName + "Pool");
        hikariConfig.setJdbcUrl(config.getUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(resolvePassword(config, pwProtector));

        String driverClassName = config.getDriverClassName();
        if (driverClassName == null || driverClassName.isBlank()) {
            driverClassName = getDefaultDriverClassName(dbName);
        }
        if (driverClassName != null && !driverClassName.isBlank()) {
            hikariConfig.setDriverClassName(driverClassName);
        }

        applyPoolProperties(hikariConfig, config.getPool());
        applyDatabaseSpecificProperties(hikariConfig, config, dbName);
        hikariConfig.setRegisterMbeans(false);

        HikariDataSource dataSource = new HikariDataSource(hikariConfig);

        if (config.getStartupValidation().isEnabled()) { // NOPMD LawOfDemeter
            validateStartupConnection(dataSource, dbName);
        }

        if (logger.isInfoEnabled()) {
            logger.info("[{}] DataSource created: url={}, poolSize={}, minIdle={}", dbName, maskUrl(config.getUrl()), config.getPool().getMaximumPoolSize(), config.getPool().getMinimumIdle()); // NOPMD LawOfDemeter
        }
        return dataSource;
    }

    protected String resolvePassword(DatasourceProperties.DatasourceConfig config, PWProtector pwProtector) {
        if (config.getPlainPassword() != null && !config.getPlainPassword().isBlank()) {
            if (logger.isWarnEnabled()) {
                logger.warn("[{}] Using plaintext password (plainPassword). This should only be used in local/dev profiles.", getDbName());
            }
            return config.getPlainPassword();
        }
        if (config.getPassword() != null && !config.getPassword().isBlank()
                && config.getPasswordVector() != null && !config.getPasswordVector().isBlank()) {
            return pwProtector.decrypt(config.getPassword(), config.getPasswordVector());
        }
        return null;
    }

    protected void applyPoolProperties(HikariConfig hikariConfig, DatasourceProperties.PoolProperties pool) {
        hikariConfig.setMaximumPoolSize(pool.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(pool.getMinimumIdle());
        hikariConfig.setConnectionTimeout(pool.getConnectionTimeout());
        hikariConfig.setIdleTimeout(pool.getIdleTimeout());
        hikariConfig.setKeepaliveTime(pool.getKeepaliveTime());
        hikariConfig.setMaxLifetime(pool.getMaxLifetime());
        hikariConfig.setLeakDetectionThreshold(pool.getLeakDetectionThreshold());
    }

    protected void applyDatabaseSpecificProperties(HikariConfig hikariConfig, DatasourceProperties.DatasourceConfig config, String dbName) {
        if ("brok".equals(dbName) || "integration".equals(dbName)) {
            if (config.getOracle() != null) {
                Properties props = hikariConfig.getDataSourceProperties();
                if (props == null) {
                    props = new Properties();
                    hikariConfig.setDataSourceProperties(props);
                }
                if (config.getOracle().getConnectTimeout() > 0) { // NOPMD LawOfDemeter
                    props.setProperty("oracle.net.CONNECT_TIMEOUT", String.valueOf(config.getOracle().getConnectTimeout())); // NOPMD LawOfDemeter
                }
                if (config.getOracle().getReadTimeout() > 0) { // NOPMD LawOfDemeter
                    props.setProperty("oracle.jdbc.ReadTimeout", String.valueOf(config.getOracle().getReadTimeout())); // NOPMD LawOfDemeter
                }
                if (config.getOracle().getDefaultRowPrefetch() > 0) { // NOPMD LawOfDemeter
                    props.setProperty("defaultRowPrefetch", String.valueOf(config.getOracle().getDefaultRowPrefetch())); // NOPMD LawOfDemeter
                }
            }
        } else if ("middleware".equals(dbName)) {
            if (config.getPostgres() != null) {
                Properties props = hikariConfig.getDataSourceProperties();
                if (props == null) {
                    props = new Properties();
                    hikariConfig.setDataSourceProperties(props);
                }
                String url = config.getUrl();
                boolean isLocal = url != null && (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("mem:"));
                boolean ssl = config.getPostgres().isSsl() && !isLocal;
                props.setProperty("ssl", String.valueOf(ssl));
                if (config.getPostgres().getPrepareThreshold() > 0) { // NOPMD LawOfDemeter
                    props.setProperty("prepareThreshold", String.valueOf(config.getPostgres().getPrepareThreshold())); // NOPMD LawOfDemeter
                }
            }
        }
    }

    protected void validateStartupConnection(HikariDataSource dataSource, String dbName) {
        try (Connection connection = dataSource.getConnection()) {
            if (logger.isInfoEnabled()) {
                logger.info("[{}] Startup connection validation successful", dbName);
            }
        } catch (SQLException e) {
            if (logger.isErrorEnabled()) {
                logger.error("[{}] Startup connection validation failed: {}", dbName, e.getMessage());
            }
            throw new IllegalStateException("Failed to establish startup connection for [" + dbName + "]: " + e.getMessage(), e);
        }
    }

    protected DataSource wrapWithProxy(DataSource dataSource, DatasourceProperties.SqlLoggingProperties sqlLogging, String dbName, ObjectProvider<TracingQueryExecutionListener> tracingListenerProvider) {
        // Tracing is independent of SQL logging: proxy whenever either one needs to see the queries.
        TracingQueryExecutionListener tracingListener = tracingListenerProvider.getIfAvailable();
        if (!sqlLogging.isEnabled() && tracingListener == null) {
            return dataSource;
        }

        ProxyDataSource proxyDataSource = new ProxyDataSource();
        proxyDataSource.setDataSource(dataSource);
        proxyDataSource.setDataSourceName(dbName);

        if (sqlLogging.isEnabled()) {
            DefaultQueryLogEntryCreator logEntryCreator = new DefaultQueryLogEntryCreator();
            logEntryCreator.setMultiline(true);

            net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener queryLoggingListener = new net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener();
            queryLoggingListener.setLogLevel(net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel.DEBUG);
            queryLoggingListener.setQueryLogEntryCreator(logEntryCreator);
            proxyDataSource.addListener(queryLoggingListener);

            if (sqlLogging.getSlowQueryThresholdMs() > 0) {
                net.ttddyy.dsproxy.listener.logging.SLF4JSlowQueryListener slowQueryListener = new net.ttddyy.dsproxy.listener.logging.SLF4JSlowQueryListener(sqlLogging.getSlowQueryThresholdMs(), TimeUnit.MILLISECONDS);
                slowQueryListener.setQueryLogEntryCreator(logEntryCreator);
                proxyDataSource.addListener(slowQueryListener);
            }

            if (logger.isInfoEnabled()) {
                logger.info("[{}] SQL logging enabled via datasource-proxy (logParameters={}, slowQueryThresholdMs={})", dbName, sqlLogging.isLogParameters(), sqlLogging.getSlowQueryThresholdMs());
            }
        }

        if (tracingListener != null) {
            proxyDataSource.addListener(tracingListener);
            if (logger.isInfoEnabled()) {
                logger.info("[{}] JDBC tracing listener registered via datasource-proxy", dbName);
            }
        }

        return proxyDataSource;
    }

    protected void validateConfig(DatasourceProperties.DatasourceConfig config, String dbName) {
        if (!config.isEnabled()) {
            return;
        }
        if (config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("[" + dbName + "] url is required when enabled=true");
        }
        if (config.getUsername() == null || config.getUsername().isBlank()) {
            throw new IllegalArgumentException("[" + dbName + "] username is required when enabled=true");
        }
        if ((config.getPassword() == null || config.getPassword().isBlank())
                && (config.getPlainPassword() == null || config.getPlainPassword().isBlank())) {
            throw new IllegalArgumentException("[" + dbName + "] password or plainPassword is required when enabled=true");
        }
    }

    protected abstract String getDbName();

    protected abstract DatasourceProperties.DatasourceConfig getConfig(DatasourceProperties properties);

    protected abstract String getDefaultDriverClassName(String dbName);

    private String maskUrl(String url) {
        if (url == null || url.length() < 20) {
            return url;
        }
        int atIndex = url.indexOf('@');
        if (atIndex > 0) {
            return url.substring(0, 8) + "***" + url.substring(atIndex);
        }
        return url;
    }
}
