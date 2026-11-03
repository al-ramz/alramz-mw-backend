package com.alramz.scheduler.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(SchedulerProperties.class)
@Import(SchedulerConfiguration.class)
public class SchedulerAutoConfiguration {
}