package com.alramz.datasource.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasourcePropertiesTest {

    @Test
    void defaultValuesAreApplied() {
        DatasourceProperties properties = new DatasourceProperties();

        DatasourceProperties.PoolProperties pool = properties.getMiddleware().getPool();
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getMinimumIdle()).isEqualTo(2);
        assertThat(pool.getConnectionTimeout()).isEqualTo(30000L);
        assertThat(pool.getIdleTimeout()).isEqualTo(600000L);
        assertThat(pool.getKeepaliveTime()).isEqualTo(30000L);
        assertThat(pool.getMaxLifetime()).isEqualTo(1800000L);
        assertThat(pool.getLeakDetectionThreshold()).isEqualTo(0L);
        assertThat(pool.getQueryTimeout()).isEqualTo(0L);

        assertThat(properties.getMiddleware().getStartupValidation().isEnabled()).isTrue();
        assertThat(properties.getMiddleware().getSqlLogging().isEnabled()).isFalse();
        assertThat(properties.getMiddleware().getSqlLogging().isLogParameters()).isFalse();
        assertThat(properties.getMiddleware().getSqlLogging().getSlowQueryThresholdMs()).isEqualTo(0L);
        assertThat(properties.getBrok().getSqlLogging().isEnabled()).isFalse();
        assertThat(properties.getIntegration().getSqlLogging().isEnabled()).isFalse();
    }

    @Test
    void nestedPropertiesCanBeSet() {
        DatasourceProperties properties = new DatasourceProperties();
        properties.getMiddleware().setEnabled(true);
        properties.getMiddleware().setUrl("jdbc:postgresql://localhost:5432/mw");
        properties.getMiddleware().setUsername("user");
        properties.getMiddleware().setPassword("pass");
        properties.getMiddleware().setDriverClassName("org.postgresql.Driver");
        properties.getMiddleware().getPool().setMaximumPoolSize(20);

        assertThat(properties.getMiddleware().isEnabled()).isTrue();
        assertThat(properties.getMiddleware().getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/mw");
        assertThat(properties.getMiddleware().getPool().getMaximumPoolSize()).isEqualTo(20);
    }

    @Test
    void oracleDefaultsAreApplied() {
        DatasourceProperties properties = new DatasourceProperties();
        assertThat(properties.getBrok().getOracle().getConnectTimeout()).isEqualTo(10000);
        assertThat(properties.getBrok().getOracle().getReadTimeout()).isEqualTo(60000);
        assertThat(properties.getBrok().getOracle().getDefaultRowPrefetch()).isEqualTo(100);
        assertThat(properties.getIntegration().getOracle().getConnectTimeout()).isEqualTo(10000);
    }

    @Test
    void postgresDefaultsAreApplied() {
        DatasourceProperties properties = new DatasourceProperties();
        assertThat(properties.getMiddleware().getPostgres().isSsl()).isTrue();
        assertThat(properties.getMiddleware().getPostgres().getPrepareThreshold()).isEqualTo(5);
    }
}
