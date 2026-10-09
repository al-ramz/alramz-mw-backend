package com.alramz.datasource.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = EncryptionProperties.PREFIX)
public class EncryptionProperties {

    public static final String PREFIX = "cipher";

    private String password;
}
