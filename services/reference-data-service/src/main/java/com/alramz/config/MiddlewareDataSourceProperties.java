package com.alramz.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConfigurationProperties(prefix = "company.datasource.middleware")
public class MiddlewareDataSourceProperties {

    private boolean enabled = true;
    private String url;
    private String username;
    private String plainPassword;
    private String driverClassName = "org.postgresql.Driver";
    private StartupValidation startupValidation = new StartupValidation();
    private Pool pool = new Pool();
    private SqlLogging sqlLogging = new SqlLogging();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPlainPassword() {
        return plainPassword;
    }

    public void setPlainPassword(String plainPassword) {
        this.plainPassword = plainPassword;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public StartupValidation getStartupValidation() {
        return startupValidation;
    }

    public void setStartupValidation(StartupValidation startupValidation) {
        this.startupValidation = startupValidation;
    }

    public Pool getPool() {
        return pool;
    }

    public void setPool(Pool pool) {
        this.pool = pool;
    }

    public SqlLogging getSqlLogging() {
        return sqlLogging;
    }

    public void setSqlLogging(SqlLogging sqlLogging) {
        this.sqlLogging = sqlLogging;
    }

    public DataSource createDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(plainPassword);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setMaximumPoolSize(pool.getMaximumPoolSize());
        dataSource.setMinimumIdle(pool.getMinimumIdle());
        return dataSource;
    }

    public static class StartupValidation {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Pool {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }
    }

    public static class SqlLogging {
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}