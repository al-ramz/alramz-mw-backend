package com.alramz.scheduler.job;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.service.Schedulable;
import com.alramz.utils.SqlQueriesManager;

@Component("dataValidationJobRunner")
public class DataValidationScheduledJob implements Schedulable {

    private static final Logger log = LoggerFactory.getLogger(DataValidationScheduledJob.class);
    private final SqlQueriesManager sqlQueriesManager;

    public DataValidationScheduledJob(SqlQueriesManager sqlQueriesManager) {
        this.sqlQueriesManager = sqlQueriesManager;
    }

    @Override
    public void run(ScheduleInfoBean scheduleInfoBean) {
        if (log.isInfoEnabled()) {
            log.info("Executing scheduled data validation job: scheduleId={}, cronExpr={}, mode={}",
            scheduleInfoBean.getScheduleId(),
            scheduleInfoBean.getCronExpr(),
            scheduleInfoBean.getScheduleMode());
        }

        try {
            String selectQuery = sqlQueriesManager.getSQLQueryFromConfig("schedule.job.select.by.group");
            if (log.isDebugEnabled()) {
                log.debug("Loaded SQL query: {}", selectQuery);
            }
        } catch (IOException e) {
            if (log.isErrorEnabled()) {
                log.error("Failed to load SQL query from config", e);
            }
        }
    }
}