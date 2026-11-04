package com.alramz.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DatasourceConfiguration {

    @Bean
    @Primary
    public DataSource primaryDataSource(@Qualifier("middlewareDataSource") DataSource middlewareDataSource) {
        return middlewareDataSource;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("middlewareTransactionManager") DataSourceTransactionManager middlewareTransactionManager) {
        return middlewareTransactionManager;
    }
}
