package com.alramz.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class MiddlewareDataSourcePropertiesTest {

    @Test
    void defaults_shouldMatchDocumentedValues() {
        MiddlewareDataSourceProperties props = new MiddlewareDataSourceProperties();

        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getDriverClassName()).isEqualTo("org.postgresql.Driver");
        assertThat(props.getStartupValidation().isEnabled()).isFalse();
        assertThat(props.getSqlLogging().isEnabled()).isFalse();
        assertThat(props.getPool().getMaximumPoolSize()).isEqualTo(10);
        assertThat(props.getPool().getMinimumIdle()).isEqualTo(2);
    }

    @Test
    void createDataSource_shouldBuildHikariPoolFromProperties() {
        MiddlewareDataSourceProperties props = new MiddlewareDataSourceProperties();
        props.setEnabled(false);
        props.setUrl("jdbc:h2:mem:mw");
        props.setUsername("sa");
        props.setPlainPassword("secret");
        props.setDriverClassName("org.h2.Driver");
        MiddlewareDataSourceProperties.Pool pool = new MiddlewareDataSourceProperties.Pool();
        pool.setMaximumPoolSize(5);
        pool.setMinimumIdle(1);
        props.setPool(pool);
        MiddlewareDataSourceProperties.StartupValidation validation = new MiddlewareDataSourceProperties.StartupValidation();
        validation.setEnabled(true);
        props.setStartupValidation(validation);
        MiddlewareDataSourceProperties.SqlLogging sqlLogging = new MiddlewareDataSourceProperties.SqlLogging();
        sqlLogging.setEnabled(true);
        props.setSqlLogging(sqlLogging);

        DataSource ds = props.createDataSource();

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getStartupValidation().isEnabled()).isTrue();
        assertThat(props.getSqlLogging().isEnabled()).isTrue();
        assertThat(ds).isInstanceOfSatisfying(HikariDataSource.class, h -> {
            assertThat(h.getJdbcUrl()).isEqualTo("jdbc:h2:mem:mw");
            assertThat(h.getUsername()).isEqualTo("sa");
            assertThat(h.getPassword()).isEqualTo("secret");
            assertThat(h.getDriverClassName()).isEqualTo("org.h2.Driver");
            assertThat(h.getMaximumPoolSize()).isEqualTo(5);
            assertThat(h.getMinimumIdle()).isEqualTo(1);
            h.close();
        });
    }
}
