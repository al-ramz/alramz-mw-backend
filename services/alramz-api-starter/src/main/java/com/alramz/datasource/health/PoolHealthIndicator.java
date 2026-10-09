package com.alramz.datasource.health;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PoolHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(PoolHealthIndicator.class);
    private final HikariDataSource dataSource;
    private final String name;

    public PoolHealthIndicator(HikariDataSource dataSource, String name) {
        this.dataSource = dataSource;
        this.name = name;
    }

    @Override
    public Health health() {
        try {
            HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean(); // NOPMD LawOfDemeter
            if (poolMXBean == null) {
                return Health.up()
                        .withDetail(name + ".status", "mxBean-unavailable")
                        .build();
            }

            Health.Builder builder = Health.up();
            builder.withDetail(name + ".totalConnections", poolMXBean.getTotalConnections());
            builder.withDetail(name + ".activeConnections", poolMXBean.getActiveConnections());
            builder.withDetail(name + ".idleConnections", poolMXBean.getIdleConnections());
            builder.withDetail(name + ".pendingThreads", poolMXBean.getThreadsAwaitingConnection());
            return builder.build();
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            logger.error("[{}] Health check failed", name, e);
            return Health.down(e).build();
        }
    }
}
