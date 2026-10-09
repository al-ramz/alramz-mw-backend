package com.alramz.datasource.config;

import com.alramz.utils.PWProtector;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "cipher", name = "password")
@EnableConfigurationProperties(EncryptionProperties.class)
public class EncryptionAutoConfiguration {

    @Bean(name = "pwProtector")
    public PWProtector pwProtector(EncryptionProperties properties) {
        return new PWProtector(properties.getPassword());
    }
}
