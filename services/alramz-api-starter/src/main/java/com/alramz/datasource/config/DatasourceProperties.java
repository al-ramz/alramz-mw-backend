package com.alramz.datasource.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = DatasourceProperties.PREFIX)
public class DatasourceProperties {

    public static final String PREFIX = "company.datasource";

    private DatasourceConfig middleware = new DatasourceConfig();
    private DatasourceConfig brok = new DatasourceConfig();
    private DatasourceConfig integration = new DatasourceConfig();

    @Getter
    @Setter
    public static class DatasourceConfig {
        private boolean enabled = false;
        private String url;
        private String username;
        private String password;
        private String passwordVector;
        private String plainPassword;
        private String driverClassName;
        private StartupValidation startupValidation = new StartupValidation();
        private PoolProperties pool = new PoolProperties();
        private SqlLoggingProperties sqlLogging = new SqlLoggingProperties();
        private OracleProperties oracle = new OracleProperties();
        private PostgresProperties postgres = new PostgresProperties();
    }

    @Getter
    @Setter
    public static class StartupValidation {
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class PoolProperties {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long keepaliveTime = 30000;
        private long maxLifetime = 1800000;
        private long leakDetectionThreshold = 0;
        private long queryTimeout = 0;
    }

    @Getter
    @Setter
    public static class SqlLoggingProperties {
        private boolean enabled = false;
        private boolean logParameters = false;
        private long slowQueryThresholdMs = 0;
    }

    @Getter
    @Setter
    public static class OracleProperties {
        private int connectTimeout = 10000;
        private int readTimeout = 60000;
        private int defaultRowPrefetch = 100;
    }

    @Getter
    @Setter
    public static class PostgresProperties {
        private boolean ssl = true;
        private int prepareThreshold = 5;
    }
}
