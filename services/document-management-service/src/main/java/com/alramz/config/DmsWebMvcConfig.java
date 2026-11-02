package com.alramz.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the single DMS summary log line for every DMS endpoint. */
@Configuration
public class DmsWebMvcConfig implements WebMvcConfigurer {

    static final String DMS_PATHS = "/api/v1/dms/**";

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DmsRequestSummaryInterceptor()).addPathPatterns(DMS_PATHS);
    }
}
