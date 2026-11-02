package com.alramz.scheduler.service.impl;

import com.alramz.scheduler.config.SchedulerProperties;
import com.alramz.scheduler.constants.SchedulerConstants;
import com.alramz.scheduler.model.SchedStatus;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.service.Schedulable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobScheduleManagerTest {

    private ThreadPoolTaskScheduler taskScheduler;
    private BeanFactory beanFactory;
    private SchedulerProperties properties;
    private final CountDownLatch ran = new CountDownLatch(1);

    @BeforeEach
    void setUp() {
        taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(2);
        taskScheduler.initialize();
        beanFactory = mock(BeanFactory.class);
        Schedulable worker = bean -> ran.countDown();
        when(beanFactory.getBean("worker")).thenReturn(worker);
        properties = new SchedulerProperties();
    }

    @AfterEach
    void tearDown() {
        taskScheduler.shutdown();
    }

    private static SchedulerProperties.ScheduleJobProperties job(String id, String mode) {
        SchedulerProperties.ScheduleJobProperties p = new SchedulerProperties.ScheduleJobProperties();
        p.setScheduleId(id);
        p.setWorkerBeanName("worker");
        p.setScheduleMode(mode);
        return p;
    }

    private JobScheduleManager yamlManager(SchedulerProperties.ScheduleJobProperties... jobs) {
        properties.setJobs(List.of(jobs));
        JobScheduleManager m = new JobScheduleManager(taskScheduler, beanFactory, properties, null);
        m.configExecutor();
        return m;
    }

    private static Set<String> ids(Set<ScheduleInfoBean> beans) {
        return beans.stream().map(ScheduleInfoBean::getScheduleId).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void schedulesYamlJobsByModeAndClassifiesInvalidOnes() throws Exception {
        SchedulerProperties.ScheduleJobProperties rate = job("RATE", "FIXED_RATE");
        rate.setInterval(50L);
        SchedulerProperties.ScheduleJobProperties delay = job("DELAY", "FIXED_DELAY");
        delay.setDelay(1000L);
        SchedulerProperties.ScheduleJobProperties cron = job("CRON", "CRON_EXP");
        cron.setCronExpr("0 0 2 * * *");
        SchedulerProperties.ScheduleJobProperties badCron = job("BADCRON", "CRON_EXP");
        badCron.setCronExpr("not a cron");
        SchedulerProperties.ScheduleJobProperties oneTimeNoStart = job("ONCE", "ONE_TIME");
        SchedulerProperties.ScheduleJobProperties disabled = job("OFF", "FIXED_RATE");
        disabled.setEnable("N");

        JobScheduleManager manager = yamlManager(badCron, oneTimeNoStart, disabled, rate, delay, cron);
        Map<String, Set<ScheduleInfoBean>> result = manager.schedule("group");

        assertThat(ids(result.get("SUCCEED"))).containsExactlyInAnyOrder("RATE", "DELAY", "CRON");
        assertThat(ids(result.get("INVALID"))).containsExactlyInAnyOrder("BADCRON", "ONCE");
        assertThat(result.get("REJECTED")).isEmpty();
        assertThat(ran.await(5, TimeUnit.SECONDS)).as("worker bean invoked").isTrue();

        SchedStatus stat = manager.getSchedulerStat();
        assertThat(ids(stat.getScheduledTasks().keySet())).containsExactlyInAnyOrder("RATE", "DELAY", "CRON");
        assertThat(stat.getMaxPoolSize()).isPositive();
    }

    @Test
    void rejectsJobAlreadyScheduledAndFiltersByJobIds() {
        SchedulerProperties.ScheduleJobProperties a = job("A", "FIXED_DELAY");
        a.setDelay(60_000L);
        SchedulerProperties.ScheduleJobProperties b = job("B", "FIXED_DELAY");
        b.setDelay(60_000L);
        JobScheduleManager manager = yamlManager(a, b);

        Map<String, Set<ScheduleInfoBean>> first = manager.schedule("group", "a");
        Map<String, Set<ScheduleInfoBean>> second = manager.schedule("group", "A");

        assertThat(ids(first.get("SUCCEED"))).containsExactly("A");
        assertThat(ids(second.get("REJECTED"))).containsExactly("A");
        assertThat(second.get("SUCCEED")).isEmpty();
    }

    @Test
    void rejectedWhenExecutorIsShutDown() {
        SchedulerProperties.ScheduleJobProperties a = job("A", "FIXED_RATE");
        a.setInterval(60_000L);
        JobScheduleManager manager = yamlManager(a);
        taskScheduler.getScheduledThreadPoolExecutor().shutdown();

        Map<String, Set<ScheduleInfoBean>> result = manager.schedule("group");

        assertThat(ids(result.get("REJECTED"))).containsExactly("A");
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadsJobsFromDatabaseWhenTemplateAvailable() throws Exception {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("worker_bean_name")).thenReturn("worker");
        when(rs.getString("schedule_mode")).thenReturn("FIXED_DELAY");
        when(rs.getLong("delay")).thenReturn(60_000L);
        when(rs.getLong("interval_seconds")).thenReturn(-1L);
        when(rs.getString("enable")).thenReturn("Y");
        when(rs.getString("schedule_id")).thenReturn("DB1", "DB2");
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class))).thenAnswer(inv -> {
            RowMapper<ScheduleInfoBean> mapper = inv.getArgument(2);
            return List.of(mapper.mapRow(rs, 0), mapper.mapRow(rs, 1));
        });
        JobScheduleManager manager = new JobScheduleManager(taskScheduler, beanFactory, properties, jdbc);

        Map<String, Set<ScheduleInfoBean>> result = manager.schedule("group", "db2");

        assertThat(ids(result.get("SUCCEED"))).containsExactly("DB2");
    }

    @Test
    void databaseErrorIsSwallowedAndReturnsEmptyResult() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new IllegalStateException("db down"));
        JobScheduleManager manager = new JobScheduleManager(taskScheduler, beanFactory, properties, jdbc);

        Map<String, Set<ScheduleInfoBean>> result = manager.schedule("group");

        assertThat(result).containsOnlyKeys("SUCCEED", "INVALID", "REJECTED");
        assertThat(result.values()).allMatch(Set::isEmpty);
    }

    @Test
    void unregisterJobsCancelsMatchingTasks() throws Exception {
        SchedulerProperties.ScheduleJobProperties a = job("A", "FIXED_DELAY");
        a.setDelay(60_000L);
        SchedulerProperties.ScheduleJobProperties b = job("B", "FIXED_DELAY");
        b.setDelay(60_000L);
        JobScheduleManager manager = yamlManager(a, b);
        manager.schedule("group");

        Map<String, Boolean> status = manager.unregisterJobs("A");

        assertThat(status).containsExactly(Map.entry("A", true));
        assertThat(ids(manager.getSchedulerStat().getScheduledTasks().keySet())).containsExactly("B");
    }

    @Test
    void reinitializeForJobsCancelsAndReschedules() throws Exception {
        SchedulerProperties.ScheduleJobProperties a = job("A", "FIXED_DELAY");
        a.setDelay(60_000L);
        JobScheduleManager manager = yamlManager(a);
        manager.schedule("group");

        Map<String, Set<ScheduleInfoBean>> result = manager.reintializeSchedulerForJobs("group", "A");

        assertThat(ids(result.get("SUCCEED"))).containsExactly("A");
        assertThat(result.get("REJECTED")).isEmpty();
    }

    @Test
    void stopCancelsAllTasksAndReportsCompletion() throws Exception {
        SchedulerProperties.ScheduleJobProperties a = job("A", "FIXED_DELAY");
        a.setDelay(60_000L);
        JobScheduleManager manager = yamlManager(a);
        manager.schedule("group");

        String result = manager.stopScheduler();

        assertThat(result).containsAnyOf("All tasks completed execution", "tasks still running");
        assertThat(manager.getSchedulerStat().getScheduledTasks()).isEmpty();
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Test
    void hardStopShutsDownExecutorImmediately() throws Exception {
        JobScheduleManager manager = yamlManager();

        String result = manager.hardStopScheduler();

        assertThat(result).startsWith("Force Shutdown completed.");
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue();
    }

    @Test
    void reinitializeFailsWhileSchedulerStillHasTasks() {
        SchedulerProperties.ScheduleJobProperties a = job("A", "FIXED_DELAY");
        a.setDelay(60_000L);
        JobScheduleManager manager = yamlManager(a);
        manager.schedule("group");

        assertThatThrownBy(() -> manager.reintializeScheduler("group"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage(SchedulerConstants.SCHEDULER_REINITIALIZATION_ERROR);
    }

    @Test
    void reinitializeSchedulesJobsOnIdleScheduler() throws Exception {
        ThreadPoolTaskScheduler idle = mock(ThreadPoolTaskScheduler.class);
        java.util.concurrent.ScheduledThreadPoolExecutor executor = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
        when(idle.getActiveCount()).thenReturn(0);
        when(idle.getPoolSize()).thenReturn(0);
        when(idle.getScheduledThreadPoolExecutor()).thenReturn(executor);
        when(idle.getScheduledExecutor()).thenReturn(executor);
        properties.setJobs(List.of(job("X", "UNKNOWN")));
        JobScheduleManager manager = new JobScheduleManager(idle, beanFactory, properties, null);

        Map<String, Set<ScheduleInfoBean>> result = manager.reintializeScheduler("group");
        manager.stop();

        assertThat(ids(result.get("INVALID"))).containsExactly("X");
        org.mockito.Mockito.verify(idle).initialize();
        assertThat(executor.isShutdown()).isTrue();
    }
}
