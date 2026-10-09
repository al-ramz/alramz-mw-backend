package com.alramz.tracing.listener;

import io.micrometer.tracing.Tracer;
import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TracingQueryExecutionListener implements QueryExecutionListener {

    private static final String SPAN_NAME = "db.query";
    private static final int MAX_QUERY_LENGTH = 4096;

    private final Tracer tracer;
    private final Map<String, String> dbSystemByDataSource = new ConcurrentHashMap<>();

    public TracingQueryExecutionListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        if (queryInfoList == null || queryInfoList.isEmpty()) {
            return;
        }

        io.micrometer.tracing.Span span = tracer.nextSpan().name(SPAN_NAME).start();
        String dbName = execInfo.getDataSourceName();
        if (dbName != null && !dbName.isBlank()) {
            span.tag("db.name", dbName);
        }

        String dbSystem = resolveDbSystem(execInfo);
        if (dbSystem != null) {
            span.tag("db.system", dbSystem);
        }

        QueryInfo firstQuery = queryInfoList.get(0);
        String query = firstQuery.getQuery();
        if (query != null && !query.isBlank()) {
            String operation = extractOperation(query);
            if (operation != null) {
                span.tag("db.operation", operation);
            }
            String truncated = query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
            span.tag("db.query.text", truncated);
        }

        execInfo.addCustomValue("tracing.span", span);
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        Object customValue = execInfo.getCustomValue("tracing.span", Object.class);
        if (customValue instanceof io.micrometer.tracing.Span span) {
            if (execInfo.getThrowable() != null) {
                span.error(execInfo.getThrowable());
            }
            span.end();
        }
    }

    /** db.system from the JDBC URL scheme (jdbc:postgresql:..., jdbc:oracle:...), resolved once per datasource. */
    private String resolveDbSystem(ExecutionInfo execInfo) {
        String key = execInfo.getDataSourceName() == null ? "" : execInfo.getDataSourceName();
        return dbSystemByDataSource.computeIfAbsent(key, k -> dbSystemFromUrl(execInfo));
    }

    private static String dbSystemFromUrl(ExecutionInfo execInfo) {
        try {
            String[] parts = execInfo.getStatement().getConnection().getMetaData().getURL().split(":", 3);
            return parts.length > 1 ? parts[1].toLowerCase(Locale.ROOT) : "unknown";
        } catch (SQLException | RuntimeException e) {
            return "unknown";
        }
    }

    static String extractOperation(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
    }
}
