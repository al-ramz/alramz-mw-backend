package com.alramz.config;

import javax.sql.DataSource;

import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import liquibase.integration.spring.SpringLiquibase;

@Configuration
@EnableConfigurationProperties(LiquibaseProperties.class)
public class LiquibaseConfiguration {

    @Bean
    public SpringLiquibase liquibase(DataSource middlewareDataSource, LiquibaseProperties liquibaseProperties,
            ResourceLoader resourceLoader) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(middlewareDataSource);
        liquibase.setChangeLog(liquibaseProperties.getChangeLog());
        if (liquibaseProperties.getContexts() != null && !liquibaseProperties.getContexts().isEmpty()) {
            liquibase.setContexts(String.join(",", liquibaseProperties.getContexts()));
        }
        liquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());
        liquibase.setLiquibaseSchema(liquibaseProperties.getLiquibaseSchema());
        liquibase.setLiquibaseTablespace(liquibaseProperties.getLiquibaseTablespace());
        liquibase.setDatabaseChangeLogTable(liquibaseProperties.getDatabaseChangeLogTable());
        liquibase.setDatabaseChangeLogLockTable(liquibaseProperties.getDatabaseChangeLogLockTable());
        liquibase.setDropFirst(liquibaseProperties.isDropFirst());
        liquibase.setClearCheckSums(liquibaseProperties.isClearChecksums());
        liquibase.setShouldRun(liquibaseProperties.isEnabled());
        if (liquibaseProperties.getLabelFilter() != null && !liquibaseProperties.getLabelFilter().isEmpty()) {
            liquibase.setLabelFilter(String.join(",", liquibaseProperties.getLabelFilter()));
        }
        if (liquibaseProperties.getParameters() != null && !liquibaseProperties.getParameters().isEmpty()) {
            liquibase.setChangeLogParameters(liquibaseProperties.getParameters());
        }
        liquibase.setRollbackFile(liquibaseProperties.getRollbackFile());
        liquibase.setTestRollbackOnUpdate(liquibaseProperties.isTestRollbackOnUpdate());
        liquibase.setTag(liquibaseProperties.getTag());
        liquibase.setResourceLoader(resourceLoader);
        return liquibase;
    }
}
