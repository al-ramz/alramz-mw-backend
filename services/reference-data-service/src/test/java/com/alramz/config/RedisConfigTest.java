package com.alramz.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RedisConfigTest {

    @Test
    void redisConnectionFactory_shouldBuildLocalFactoryFromConnectionProperties() {
        CompanyRedisProperties props = new CompanyRedisProperties();
        props.getConnection().setHost("redis.local");
        props.getConnection().setPort(6390);
        props.getConnection().setPassword("pw");
        props.getConnection().setTimeout("PT3S");
        props.getConnection().getSsl().setEnabled(true);

        RedisConnectionFactory factory = new RedisConfig(props).redisConnectionFactory();

        assertThat(factory).isInstanceOfSatisfying(LettuceConnectionFactory.class, f -> {
            assertThat(f.getHostName()).isEqualTo("redis.local");
            assertThat(f.getPort()).isEqualTo(6390);
            assertThat(f.getPassword()).isEqualTo("pw");
            assertThat(f.isUseSsl()).isTrue();
            assertThat(f.getTimeout()).isEqualTo(3000L);
        });
    }

    @Test
    void redisConnectionFactory_shouldFallBackToTwoSecondTimeoutWhenUnparseable() {
        CompanyRedisProperties props = new CompanyRedisProperties();
        props.getConnection().setPassword("  ");

        RedisConnectionFactory factory = new RedisConfig(props).redisConnectionFactory();

        assertThat(factory).isInstanceOfSatisfying(LettuceConnectionFactory.class, f -> {
            assertThat(f.getHostName()).isEqualTo("localhost");
            assertThat(f.getPassword()).isNull();
            assertThat(f.isUseSsl()).isFalse();
            assertThat(f.getTimeout()).isEqualTo(2000L);
        });
    }

    /**
     * Documents a production bug: the azure branch builds the factory with an immutable
     * {@code LettuceClientConfiguration} and then calls {@code setUseSsl(true)}, which Spring Data
     * Redis rejects, so {@code deployment.mode=azure} cannot start. Flip this test once fixed
     * (e.g. by using {@code LettuceClientConfiguration.builder().useSsl()}).
     */
    @Test
    void redisConnectionFactory_azureModeCurrentlyFailsBecauseSslIsSetOnImmutableClientConfig() {
        CompanyRedisProperties props = new CompanyRedisProperties();
        props.getDeployment().setMode("AZURE");
        props.getConnection().setHost("cache.redis.azure.net");
        props.getConnection().setPort(6380);
        RedisConfig config = new RedisConfig(props);

        assertThatThrownBy(config::redisConnectionFactory)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MutableLettuceClientConfiguration");
    }

    @Test
    void redisTemplate_shouldUseStringKeySerializers() {
        RedisConnectionFactory cf = mock(RedisConnectionFactory.class);

        RedisTemplate<String, String> template = new RedisConfig(new CompanyRedisProperties()).redisTemplate(cf);

        assertThat(template.getConnectionFactory()).isSameAs(cf);
        assertThat(template.getKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getHashKeySerializer()).isInstanceOf(StringRedisSerializer.class);
        assertThat(template.getValueSerializer()).isNotNull();
        assertThat(template.getHashValueSerializer()).isNotNull();
    }
}
