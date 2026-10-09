package com.alramz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.alramz.config.EmailProperties;

@SpringBootApplication
@EnableConfigurationProperties(EmailProperties.class)
@SuppressWarnings("PMD.UseUtilityClass")
public class AlramzNotificationServiceApplication {

    private AlramzNotificationServiceApplication() {
        // Spring Boot application class
    }

    public static void main(final String[] args) {
        SpringApplication.run(AlramzNotificationServiceApplication.class, args);
    }

}
