package com.alramz.scheduler.model;

import com.alramz.scheduler.config.SchedulerProperties;
import com.alramz.scheduler.entity.ScheduleJobEntity;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerModelTest {

    @Test
    void scheduleJobEntityRoundTripsAllFields() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 2, 3, 4);
        ScheduleJobEntity e = new ScheduleJobEntity();
        e.setId(7L);
        e.setJobGroupName("grp");
        e.setScheduleId("S1");
        e.setWorkerBeanName("worker");
        e.setJobBeanNames("a,b");
        e.setJobParameter("p");
        e.setScheduleMode("CRON_EXP");
        e.setCronExpr("0 0 * * * *");
        e.setDelay(10L);
        e.setIntervalSeconds(20L);
        e.setEnable("Y");
        e.setCreatedAt(now);
        e.setUpdatedAt(now.plusDays(1));

        assertThat(e.getId()).isEqualTo(7L);
        assertThat(e.getJobGroupName()).isEqualTo("grp");
        assertThat(e.getScheduleId()).isEqualTo("S1");
        assertThat(e.getWorkerBeanName()).isEqualTo("worker");
        assertThat(e.getJobBeanNames()).isEqualTo("a,b");
        assertThat(e.getJobParameter()).isEqualTo("p");
        assertThat(e.getScheduleMode()).isEqualTo("CRON_EXP");
        assertThat(e.getCronExpr()).isEqualTo("0 0 * * * *");
        assertThat(e.getDelay()).isEqualTo(10L);
        assertThat(e.getIntervalSeconds()).isEqualTo(20L);
        assertThat(e.getEnable()).isEqualTo("Y");
        assertThat(e.getCreatedAt()).isEqualTo(now);
        assertThat(e.getUpdatedAt()).isEqualTo(now.plusDays(1));
    }

    @Test
    void schedulerPropertiesExposeDefaultsAndSetters() {
        SchedulerProperties p = new SchedulerProperties();
        assertThat(p.getPoolSize()).isEqualTo(50);
        assertThat(p.getThreadGroupName()).isEqualTo("Scheduled-Tasks");
        assertThat(p.getThreadNamePrefix()).isEqualTo("TaskScheduler-Worker-");
        assertThat(p.isManagementEndpointsEnabled()).isFalse();
        assertThat(p.getJobs()).isEmpty();

        SchedulerProperties.ScheduleJobProperties job = new SchedulerProperties.ScheduleJobProperties();
        assertThat(job.getScheduleMode()).isEqualTo("CRON_EXP");
        assertThat(job.getEnable()).isEqualTo("Y");
        assertThat(job.getDelay()).isEqualTo(-1L);
        assertThat(job.getInterval()).isEqualTo(-1L);
        job.setScheduleId("J");
        job.setWorkerBeanName("w");
        job.setJobBeanNames("b");
        job.setJobParameter("x");
        job.setScheduleMode("FIXED_RATE");
        job.setCronExpr("c");
        job.setDelay(1L);
        job.setInterval(2L);
        job.setEnable("N");

        p.setPoolSize(3);
        p.setThreadGroupName("g");
        p.setThreadNamePrefix("t-");
        p.setManagementEndpointsEnabled(true);
        p.setJobs(List.of(job));

        assertThat(p.getPoolSize()).isEqualTo(3);
        assertThat(p.getThreadGroupName()).isEqualTo("g");
        assertThat(p.getThreadNamePrefix()).isEqualTo("t-");
        assertThat(p.isManagementEndpointsEnabled()).isTrue();
        SchedulerProperties.ScheduleJobProperties j = p.getJobs().get(0);
        assertThat(j.getScheduleId()).isEqualTo("J");
        assertThat(j.getWorkerBeanName()).isEqualTo("w");
        assertThat(j.getJobBeanNames()).isEqualTo("b");
        assertThat(j.getJobParameter()).isEqualTo("x");
        assertThat(j.getScheduleMode()).isEqualTo("FIXED_RATE");
        assertThat(j.getCronExpr()).isEqualTo("c");
        assertThat(j.getDelay()).isEqualTo(1L);
        assertThat(j.getInterval()).isEqualTo(2L);
        assertThat(j.getEnable()).isEqualTo("N");
    }

    @Test
    void scheduleInfoBeanEqualityIsByScheduleIdOnly() {
        Timestamp start = new Timestamp(0);
        ScheduleInfoBean a = new ScheduleInfoBean("w", "b", "p", "ONE_TIME", start, null, 1L, 2L, "Y");
        a.setScheduleId("S1");
        ScheduleInfoBean b = new ScheduleInfoBean();
        b.setScheduleId("S1");
        ScheduleInfoBean c = new ScheduleInfoBean();
        c.setScheduleId("S2");
        ScheduleInfoBean noId = new ScheduleInfoBean();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(c).isNotEqualTo(null).isNotEqualTo("S1");
        assertThat(noId).isEqualTo(new ScheduleInfoBean()).isNotEqualTo(a);
        assertThat(a).isNotEqualTo(noId);
        assertThat(a.getStartTime()).isEqualTo(start);
        assertThat(a.getJobBeanNames()).isEqualTo("b");
        assertThat(a.getJobParameter()).isEqualTo("p");
        assertThat(a.getEnable()).isEqualTo("Y");
        assertThat(a.toString()).contains("scheduleId=S1", "scheduleMode=ONE_TIME");
    }

    @Test
    void requestModelsExposeSetters() {
        RefreshJobsRequest refresh = new RefreshJobsRequest();
        refresh.setJobGroupName("g");
        refresh.setJobIds(List.of("a"));
        RestartSchedulerRequest restart = new RestartSchedulerRequest();
        restart.setJobGroupName("r");

        assertThat(refresh.getJobGroupName()).isEqualTo("g");
        assertThat(refresh.getJobIds()).containsExactly("a");
        assertThat(restart.getJobGroupName()).isEqualTo("r");
    }
}
