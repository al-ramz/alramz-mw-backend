package com.alramz.config;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Configuration
public class RedisConfig {

    private static final String AZURE_MODE = "azure";

    private final CompanyRedisProperties properties;

    public RedisConfig(CompanyRedisProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        if (AZURE_MODE.equalsIgnoreCase(properties.getDeployment().getMode())) {
            return createAzureRedisConnectionFactory();
        }
        return createLocalRedisConnectionFactory();
    }

    // reason: false positive - Connection is the CompanyRedisProperties.Connection config bean, not a closeable resource
    @SuppressWarnings("PMD.CloseResource")
    private RedisConnectionFactory createLocalRedisConnectionFactory() {
        CompanyRedisProperties.Connection connection = properties.getConnection();
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(connection.getHost());
        config.setPort(connection.getPort());
        if (connection.getPassword() != null && !connection.getPassword().isBlank()) {
            config.setPassword(RedisPassword.of(connection.getPassword()));
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.setUseSsl(connection.getSsl().isEnabled());
        applyTimeout(factory, connection.getTimeout());
        return factory;
    }

    // reason: false positive - Connection is the CompanyRedisProperties.Connection config bean, not a closeable resource
    @SuppressWarnings("PMD.CloseResource")
    private RedisConnectionFactory createAzureRedisConnectionFactory() {
        CompanyRedisProperties.Connection connection = properties.getConnection();
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(parseDuration(connection.getTimeout(), Duration.ofSeconds(2)))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(connection.getHost(), connection.getPort()) {
                    @Override
                    public RedisPassword getPassword() {
                        return RedisPassword.of(resolveAzureAccessToken());
                    }
                },
                clientConfig
        );
        factory.setUseSsl(true);
        return factory;
    }

    // reason: boundary - any Azure identity/reactor failure is rethrown as one IllegalStateException with context
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String resolveAzureAccessToken() {
        try {
            DefaultAzureCredential credential = new DefaultAzureCredentialBuilder().build();
            return Objects.requireNonNull(credential.getToken(
                    new com.azure.core.credential.TokenRequestContext()
                            .addScopes("https://redis.azure.com/.default")
            ).block(), "Azure token request returned no token").getToken();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to obtain Azure managed identity token for Redis", e);
        }
    }

    private void applyTimeout(LettuceConnectionFactory factory, String timeout) {
        Duration d = parseDuration(timeout, Duration.ofSeconds(2));
        factory.setTimeout(d.toMillis());
        factory.setShutdownTimeout(d.toMillis());
    }

    private Duration parseDuration(String timeout, Duration defaultDuration) {
        if (timeout == null) {
            return defaultDuration;
        }
        try {
            return Duration.parse(timeout);
        } catch (DateTimeParseException e) {
            return defaultDuration;
        }
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericToStringSerializer<>(String.class));
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericToStringSerializer<>(String.class));
        return template;
    }
}
