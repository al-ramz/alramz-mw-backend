package com.alramz.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class BeanCreationLoggingPostProcessor implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BeanCreationLoggingPostProcessor.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        String logMessage = "[Bean: " + beanName + "] - Successfully Created";
        logger.info(logMessage);
        return bean;
    }
}
