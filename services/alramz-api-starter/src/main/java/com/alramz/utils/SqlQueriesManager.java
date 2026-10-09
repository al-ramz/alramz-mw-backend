package com.alramz.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqlQueriesManager {

    private static Properties props;

    private final Environment environment;

    public String getSQLQueryFromConfig(String key) throws IOException {
        if (props == null) {
            if (log.isInfoEnabled()) {
                log.info("Loading config from " + environment.getRequiredProperty("sql.file"));
            }
            loadSQLQueriesFromFile();
            if (props == null) {
                if (log.isErrorEnabled()) {
                    log.error("Error: Loading allConfigs from " + environment.getRequiredProperty("sql.file")
                    + " return null.");
                }
                return null;
            }
        }
        return props.getProperty(key);
    }

    private synchronized void loadSQLQueriesFromFile() throws IOException {
        try {
            if (log.isInfoEnabled()) {
                log.info("Initializing SQL Query Manager");
            }

            String sqlFiles = environment.getRequiredProperty("sql.file");
            String[] filePaths = sqlFiles.split(",");

            if (props == null) {
                props = new Properties();
            }

            for (String sqlFile : filePaths) {
                sqlFile = sqlFile.trim();
                if (sqlFile.startsWith("classpath:")) {
                    sqlFile = sqlFile.substring("classpath:".length());
                }

                InputStream in = getClass().getClassLoader().getResourceAsStream(sqlFile);

                if (in == null) {
                    log.warn("Warning: SQL file not found: {}", sqlFile);
                    continue;
                }

                props.loadFromXML(in);
                if (log.isInfoEnabled()) {
                    log.info("Loaded SQL config from: {}", sqlFile);
                }
            }

            if (log.isInfoEnabled()) {
                log.info("SQL Query Manager initialized with {} queries", props.size());
            }
        } finally {
        }
    }
}
