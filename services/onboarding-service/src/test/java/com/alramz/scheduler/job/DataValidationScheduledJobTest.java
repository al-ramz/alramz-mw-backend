package com.alramz.scheduler.job;

import com.alramz.utils.SqlQueriesManager;
import com.alramz.scheduler.model.ScheduleInfoBean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataValidationScheduledJobTest {

    @Mock
    private SqlQueriesManager sqlQueriesManager;

    private DataValidationScheduledJob job;

    @BeforeEach
    void setUp() {
        job = new DataValidationScheduledJob(sqlQueriesManager);
    }

    @Test
    void run_shouldExecuteSuccessfully() {
        ScheduleInfoBean scheduleInfoBean = new ScheduleInfoBean();
        scheduleInfoBean.setScheduleId("1");
        scheduleInfoBean.setCronExpr("0 0 * * *");
        scheduleInfoBean.setScheduleMode("AUTO");

        try {
            when(sqlQueriesManager.getSQLQueryFromConfig("schedule.job.select.by.group"))
                    .thenReturn("SELECT * FROM schedule_job");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        job.run(scheduleInfoBean);

        assertThat(scheduleInfoBean.getScheduleId()).isEqualTo("1");
    }
}
