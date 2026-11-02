package com.alramz.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyRedisPropertiesTest {

    @Test
    void defaults_shouldTargetLocalPlaintextRedis() {
        CompanyRedisProperties props = new CompanyRedisProperties();

        assertThat(props.getDeployment().getMode()).isEqualTo("local");
        assertThat(props.getConnection().getHost()).isEqualTo("localhost");
        assertThat(props.getConnection().getPort()).isEqualTo(6379);
        assertThat(props.getConnection().getPassword()).isEmpty();
        assertThat(props.getConnection().getTimeout()).isEqualTo("2s");
        assertThat(props.getConnection().getSsl().isEnabled()).isFalse();
        assertThat(props.getCache().getMappings()).isNull();
    }

    @Test
    void setters_shouldBindNestedConnectionAndMappingValues() {
        CompanyRedisProperties.Deployment deployment = new CompanyRedisProperties.Deployment();
        deployment.setMode("azure");
        CompanyRedisProperties.Connection.Ssl ssl = new CompanyRedisProperties.Connection.Ssl();
        ssl.setEnabled(true);
        CompanyRedisProperties.Connection connection = new CompanyRedisProperties.Connection();
        connection.setHost("redis.internal");
        connection.setPort(6380);
        connection.setPassword("pw");
        connection.setTimeout("PT5S");
        connection.setSsl(ssl);
        CompanyRedisProperties.Cache.CacheMapping mapping = new CompanyRedisProperties.Cache.CacheMapping();
        mapping.setTable("banks");
        mapping.setCacheKey("ref:banks");
        mapping.setReloadStrategy("startup");
        mapping.setTtl("1H");
        mapping.setEnabled(false);
        CompanyRedisProperties.Cache cache = new CompanyRedisProperties.Cache();
        cache.setMappings(Map.of("banks", mapping));

        CompanyRedisProperties props = new CompanyRedisProperties();
        props.setDeployment(deployment);
        props.setConnection(connection);
        props.setCache(cache);

        assertThat(props.getDeployment().getMode()).isEqualTo("azure");
        assertThat(props.getConnection().getHost()).isEqualTo("redis.internal");
        assertThat(props.getConnection().getPort()).isEqualTo(6380);
        assertThat(props.getConnection().getPassword()).isEqualTo("pw");
        assertThat(props.getConnection().getTimeout()).isEqualTo("PT5S");
        assertThat(props.getConnection().getSsl().isEnabled()).isTrue();
        CompanyRedisProperties.Cache.CacheMapping bound = props.getCache().getMappings().get("banks");
        assertThat(bound.getTable()).isEqualTo("banks");
        assertThat(bound.getCacheKey()).isEqualTo("ref:banks");
        assertThat(bound.getReloadStrategy()).isEqualTo("startup");
        assertThat(bound.getTtl()).isEqualTo("1H");
        assertThat(bound.isEnabled()).isFalse();
    }
}
