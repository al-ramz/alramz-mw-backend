package com.alramz.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link MmfProperties} in every profile (unconditional, so binding never depends on the datasources). */
@Configuration
@EnableConfigurationProperties(MmfProperties.class)
public class MmfConfiguration {
}
