package com.alramz.datasource.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractDataSourceAutoConfigurationTest {

    private final AbstractDataSourceAutoConfiguration configuration = new AbstractDataSourceAutoConfiguration() {
        @Override
        protected String getDbName() {
            return "test";
        }

        @Override
        protected DatasourceProperties.DatasourceConfig getConfig(DatasourceProperties properties) {
            return properties.getMiddleware();
        }

        @Override
        protected String getDefaultDriverClassName(String dbName) {
            return null;
        }
    };

    @Test
    void oraclePropertiesAppliedForIntegrationAndSkipsNonPositiveValues() {
        DatasourceProperties.DatasourceConfig config = new DatasourceProperties.DatasourceConfig();
        config.getOracle().setReadTimeout(0);
        HikariConfig hikariConfig = new HikariConfig();

        configuration.applyDatabaseSpecificProperties(hikariConfig, config, "integration");

        Properties props = hikariConfig.getDataSourceProperties();
        assertThat(props)
                .containsEntry("oracle.net.CONNECT_TIMEOUT", "10000")
                .containsEntry("defaultRowPrefetch", "100")
                .doesNotContainKey("oracle.jdbc.ReadTimeout")
                .doesNotContainKey("ssl");
    }

    @Test
    void oraclePropertiesSkippedWhenOracleConfigMissing() {
        DatasourceProperties.DatasourceConfig config = new DatasourceProperties.DatasourceConfig();
        config.setOracle(null);
        HikariConfig hikariConfig = new HikariConfig();

        configuration.applyDatabaseSpecificProperties(hikariConfig, config, "brok");

        assertThat(hikariConfig.getDataSourceProperties()).isEmpty();
    }

    @Test
    void postgresSslDisabledForLocalUrl() {
        DatasourceProperties.DatasourceConfig config = new DatasourceProperties.DatasourceConfig();
        config.setUrl("jdbc:postgresql://localhost:5432/db");
        HikariConfig hikariConfig = new HikariConfig();

        configuration.applyDatabaseSpecificProperties(hikariConfig, config, "middleware");

        assertThat(hikariConfig.getDataSourceProperties())
                .containsEntry("ssl", "false")
                .containsEntry("prepareThreshold", "5")
                .doesNotContainKey("oracle.net.CONNECT_TIMEOUT");
    }

    @Test
    void postgresSslEnabledForRemoteUrlAndNullPostgresSkipped() {
        DatasourceProperties.DatasourceConfig config = new DatasourceProperties.DatasourceConfig();
        config.setUrl("jdbc:postgresql://db.internal:5432/db");
        HikariConfig hikariConfig = new HikariConfig();
        configuration.applyDatabaseSpecificProperties(hikariConfig, config, "middleware");
        assertThat(hikariConfig.getDataSourceProperties()).containsEntry("ssl", "true");

        config.setPostgres(null);
        HikariConfig untouched = new HikariConfig();
        configuration.applyDatabaseSpecificProperties(untouched, config, "middleware");
        assertThat(untouched.getDataSourceProperties()).isEmpty();
    }
}
