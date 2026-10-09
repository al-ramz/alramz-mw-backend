package com.alramz.scheduler.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.alramz.scheduler.constants.SchedulerConstants;
import com.alramz.scheduler.config.SchedulerProperties;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.model.SchedStatus;
import com.alramz.scheduler.service.ISchedulerService;
import com.alramz.scheduler.service.Schedulable;

public class JobScheduleManager implements ISchedulerService {
    private static final Logger log = LoggerFactory.getLogger(JobScheduleManager.class);

    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;
    private final BeanFactory beanFactory;
    private final SchedulerProperties properties;
    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;

    public JobScheduleManager(ThreadPoolTaskScheduler threadPoolTaskScheduler, BeanFactory beanFactory, SchedulerProperties properties,
                              NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate) {
        this.threadPoolTaskScheduler = threadPoolTaskScheduler;
        this.beanFactory = beanFactory;
        this.properties = properties;
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
    }

    private Map<ScheduleInfoBean, ScheduledFuture<Schedulable>> scheduledTasks = new ConcurrentHashMap<>();

    @Override
    public String stopScheduler() throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Shutdown requested...");
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        scheduledTasks.forEach((ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> futureTasks) -> {
            if (log.isInfoEnabled()) {
                log.info("Cancelling task: [" + scheduleInfoBean.toString() + "]...");
            }
            if (futureTasks.cancel(false)) {
                scheduledTasks.remove(scheduleInfoBean);
            }
        });

        threadPoolTaskScheduler.getScheduledThreadPoolExecutor().shutdown();

        if (log.isInfoEnabled()) {
            log.info("Shutdown completed...");
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        return threadPoolTaskScheduler.getActiveCount() > 0 ? threadPoolTaskScheduler.getActiveCount() + " tasks still running. Please wait...\n" + getSchedulerStat().toString() :
                " All tasks completed execution\n" + getSchedulerStat().toString();
    }

    @Override
    public String hardStopScheduler() throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Force Shutdown requested...");
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        threadPoolTaskScheduler.getScheduledThreadPoolExecutor().shutdownNow();

        if (log.isInfoEnabled()) {
            log.info("Force Shutdown completed...");
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        return "Force Shutdown completed.\n" + getSchedulerStat().toString();
    }

    @Override
    public Map<String, Set<ScheduleInfoBean>> reintializeScheduler(final String jobGroupName) throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Reinitialization requested...");
        }

        if (log.isInfoEnabled()) {
            log.info("Before initialization::: " + getSchedulerStat().toString());
        }

        if (threadPoolTaskScheduler.getActiveCount() > 0 || threadPoolTaskScheduler.getPoolSize() > 0 || scheduledTasks.size() > 0) {
            if (log.isErrorEnabled()) {
                log.error("The scheduler has not been shutdown yet. 1st Shutdown it by calling \"/api/secured/scheduler/stop\" before reinitializing...");
            }

            throw new RuntimeException(SchedulerConstants.SCHEDULER_REINITIALIZATION_ERROR);
        }

        threadPoolTaskScheduler.initialize();
        if (log.isInfoEnabled()) {
            log.info("Rescheduling all jobs...");
        }

        Map<String, Set<ScheduleInfoBean>> schedulingResult = schedule(jobGroupName);

        if (log.isInfoEnabled()) {
            log.info("After initialization::: " + getSchedulerStat().toString());
        }

        return schedulingResult;
    }

    @Override
    public Map<String, Set<ScheduleInfoBean>> reintializeSchedulerForJobs(String jobGroupName, String... jobIds) throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Schedule Reinitialization requested for Jobs..." + Arrays.toString(jobIds));
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        scheduledTasks.forEach((ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> futureTasks) -> {

            if (Arrays.stream(jobIds).anyMatch(scheduleInfoBean.getScheduleId()::equals)) {
                if (log.isInfoEnabled()) {
                    log.info("Cancelling task: [" + scheduleInfoBean.toString() + "]...");
                }
                if (futureTasks.cancel(false)) {
                    scheduledTasks.remove(scheduleInfoBean);
                }
            }
        });
        if (log.isInfoEnabled()) {
            log.info("Post cancelling the Jobs:\n" + getSchedulerStat().toString());
        }
        if (log.isInfoEnabled()) {
            log.info("Registering those jobs to the scheduler...");
        }

        return schedule(jobGroupName, jobIds);
    }

    @Override
    public Map<String, Boolean> unregisterJobs(String... jobIds) throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Schedule unregister requested for Jobs..." + jobIds);
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        Map<String, Boolean> cancellationStatus = new HashMap<>();

        scheduledTasks.forEach((ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> futureTasks) -> {

            if (Arrays.stream(jobIds).anyMatch(scheduleInfoBean.getScheduleId()::equals)) {
                if (log.isInfoEnabled()) {
                    log.info("Cancelling task: [" + scheduleInfoBean.toString() + "]...");
                }
                if (futureTasks.cancel(false)) {
                    scheduledTasks.remove(scheduleInfoBean);
                    cancellationStatus.put(scheduleInfoBean.getScheduleId(), true);
                } else {
                    cancellationStatus.put(scheduleInfoBean.getScheduleId(), false);
                }
            }
        });
        if (log.isInfoEnabled()) {
            log.info("Post cancelling the Jobs\n" + getSchedulerStat().toString());
        }

        return cancellationStatus;
    }

    @Override
    public SchedStatus getSchedulerStat() {

        return new SchedStatus(scheduledTasks, threadPoolTaskScheduler.getActiveCount(),
                threadPoolTaskScheduler.getPoolSize(),
                threadPoolTaskScheduler.getScheduledThreadPoolExecutor().getMaximumPoolSize(),
                threadPoolTaskScheduler.getScheduledThreadPoolExecutor().getCompletedTaskCount(),
                threadPoolTaskScheduler.getScheduledThreadPoolExecutor().getQueue().size()); // NOPMD LawOfDemeter

    }

    @SuppressWarnings("unchecked")
    public synchronized Map<String, Set<ScheduleInfoBean>> schedule(String jobGroupName, String... jobIds) {

        Set<ScheduleInfoBean> invalidSchedulingJobs = new HashSet<>();
        Set<ScheduleInfoBean> rejectedSchedulingJobs = new HashSet<>();
        Set<ScheduleInfoBean> succeedSchedulingJobs = new HashSet<>();
        ScheduledFuture<Schedulable> future = null;
        List<ScheduleInfoBean> scheduledJobs = new ArrayList<>();
        try {
            List<ScheduleInfoBean> dbJobs = new ArrayList<>();
            boolean dbMode = middlewareNamedParameterJdbcTemplate != null;
            if (log.isInfoEnabled()) {
                log.info("Scheduler startup loading: jobGroupName={}, jobIds={}, dbMode={}", jobGroupName,
                        jobIds != null && jobIds.length > 0 ? Arrays.toString(jobIds) : "ALL", dbMode);
            }

            if (dbMode) {
                String sql = "SELECT job_group_name, schedule_id, worker_bean_name, job_bean_names, job_parameter, " +
                        "schedule_mode, cron_expr, delay, interval_seconds, enable FROM schedule_job " +
                        "WHERE job_group_name = :jobGroupName AND enable = 'Y'";
                MapSqlParameterSource params = new MapSqlParameterSource();
                params.addValue("jobGroupName", jobGroupName);
                dbJobs = middlewareNamedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
                    ScheduleInfoBean b = new ScheduleInfoBean();
                    b.setWorkerBeanName(rs.getString("worker_bean_name"));
                    b.setJobBeanNames(rs.getString("job_bean_names"));
                    b.setJobParameter(rs.getString("job_parameter"));
                    b.setScheduleMode(rs.getString("schedule_mode"));
                    b.setCronExpr(rs.getString("cron_expr"));
                    b.setDelay(rs.getLong("delay"));
                    b.setInterval(rs.getLong("interval_seconds"));
                    b.setEnable(rs.getString("enable"));
                    b.setScheduleId(rs.getString("schedule_id"));
                    return b;
                });
                if (log.isInfoEnabled()) {
                    log.info("Fetched {} scheduled jobs from schedule_job table for jobGroupName={}", dbJobs.size(), jobGroupName);
                }
            } else {
                if (log.isInfoEnabled()) {
                    log.info("middlewareNamedParameterJdbcTemplate is not available. Falling back to YAML properties for scheduler jobs");
                }
            }
            if (dbJobs.isEmpty()) {
                if (log.isInfoEnabled()) {
                    log.info("No DB jobs found for jobGroupName={}. Loading from YAML properties...", jobGroupName);
                }
                for (SchedulerProperties.ScheduleJobProperties jobProps : properties.getJobs()) {
                    if (!"Y".equalsIgnoreCase(jobProps.getEnable())) {
                        continue;
                    }
                    if (jobIds != null && jobIds.length > 0) {
                        boolean matched = Arrays.stream(jobIds).anyMatch(id -> id.equalsIgnoreCase(jobProps.getScheduleId()));
                        if (!matched) {
                            continue;
                        }
                    }
                    java.sql.Timestamp startTime = null;
                    ScheduleInfoBean b = new ScheduleInfoBean(
                            jobProps.getWorkerBeanName(),
                            jobProps.getJobBeanNames(),
                            jobProps.getJobParameter(),
                            jobProps.getScheduleMode(),
                            startTime,
                            jobProps.getCronExpr(),
                            jobProps.getDelay(),
                            jobProps.getInterval(),
                            jobProps.getEnable()
                    );
                    b.setScheduleId(jobProps.getScheduleId());
                    scheduledJobs.add(b);
                    if (log.isInfoEnabled()) {
                        log.info("YAML job loaded: scheduleId={}, workerBeanName={}, scheduleMode={}, cronExpr={}, interval={}",
                                jobProps.getScheduleId(), jobProps.getWorkerBeanName(), jobProps.getScheduleMode(),
                                jobProps.getCronExpr(), jobProps.getInterval());
                    }
                }
            } else {
                for (ScheduleInfoBean scheduleInfoBean : dbJobs) {
                    if (jobIds != null && jobIds.length > 0) {
                        boolean matched = Arrays.stream(jobIds).anyMatch(id -> id.equalsIgnoreCase(scheduleInfoBean.getScheduleId()));
                        if (!matched) {
                            continue;
                        }
                    }
                    scheduledJobs.add(scheduleInfoBean);
                    if (log.isInfoEnabled()) {
                        log.info("DB Sceduled job loaded: scheduleId={}, workerBeanName={}, scheduleMode={}, cronExpr={}, delay={}, intervalSeconds={}",
                                scheduleInfoBean.getScheduleId(), scheduleInfoBean.getWorkerBeanName(), scheduleInfoBean.getScheduleMode(),
                                scheduleInfoBean.getCronExpr(), scheduleInfoBean.getDelay(), scheduleInfoBean.getInterval());
                    }
                }
            }

            if (log.isInfoEnabled()) {
                log.info("Total jobs to schedule for jobGroupName={}: {}", jobGroupName, scheduledJobs.size());
            }

            for (final ScheduleInfoBean scheduleInfoBean : scheduledJobs) {

                if (scheduledTasks.containsKey(scheduleInfoBean)) {
                    if (log.isErrorEnabled()) {
                        log.error("Scheduler: ScheduleId= " + scheduleInfoBean.getScheduleId() + " already scheduled and active, hence rejecting");
                    }
                    rejectedSchedulingJobs.add(scheduleInfoBean);

                } else {
                    Runnable runnableObj = () -> {
                        Schedulable worker = (Schedulable) beanFactory.getBean(scheduleInfoBean.getWorkerBeanName());
                        worker.run(scheduleInfoBean);
                    };


                    try {
                        if ("FIXED_RATE".equalsIgnoreCase(scheduleInfoBean.getScheduleMode()) && scheduleInfoBean.getInterval() != null && scheduleInfoBean.getInterval() > -1) {
                            if (scheduleInfoBean.getStartTime() != null) {
                                future = (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.scheduleAtFixedRate(
                                        runnableObj, scheduleInfoBean.getStartTime(), scheduleInfoBean.getInterval());
                            } else {
                                future = (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.scheduleAtFixedRate(runnableObj, scheduleInfoBean.getInterval());
                            }

                        } else if ("FIXED_DELAY".equalsIgnoreCase(scheduleInfoBean.getScheduleMode()) && scheduleInfoBean.getDelay() != null && scheduleInfoBean.getDelay() > -1) {
                            if (scheduleInfoBean.getStartTime() != null) {
                                future = (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.scheduleWithFixedDelay(
                                        runnableObj, scheduleInfoBean.getStartTime(), scheduleInfoBean.getDelay());
                            } else {
                                future = (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.scheduleWithFixedDelay(runnableObj, scheduleInfoBean.getDelay());
                            }

                        } else if ("CRON_EXP".equalsIgnoreCase(scheduleInfoBean.getScheduleMode()) && scheduleInfoBean.getCronExpr() != null) {
                            CronTrigger trigger = new CronTrigger(scheduleInfoBean.getCronExpr());
                            future = (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.schedule(runnableObj, trigger);

                        } else if ("ONE_TIME".equalsIgnoreCase(scheduleInfoBean.getScheduleMode()) && scheduleInfoBean.getStartTime() != null) {
                            future = (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.schedule(runnableObj, scheduleInfoBean.getStartTime());

                        } else {
                            if (log.isErrorEnabled()) {
                                log.error("Job: " + scheduleInfoBean.toString() + " has been ignored. Can't be schedule due to invalid scheduling configuration");
                            }
                            invalidSchedulingJobs.add(scheduleInfoBean);
                        }
                    } catch (TaskRejectedException | IllegalArgumentException tie) {
                        if (tie instanceof IllegalArgumentException && "CRON_EXP".equalsIgnoreCase(scheduleInfoBean.getScheduleMode())) {
                            if (log.isErrorEnabled()) {
                                log.error("Job: " + scheduleInfoBean.toString() + " has invalid CRON expression. Failed to schedule.\n " + ExceptionUtils.getStackTrace(tie));
                            }
                            invalidSchedulingJobs.add(scheduleInfoBean);
                        } else {
                            rejectedSchedulingJobs.add(scheduleInfoBean);
                            if (log.isErrorEnabled()) {
                                log.error("Job: " + scheduleInfoBean.toString() + " got failed to schedule.\n " + ExceptionUtils.getStackTrace(tie));
                            }
                        }
                    }
                    if (future != null) {
                        succeedSchedulingJobs.add(scheduleInfoBean);
                        if (scheduledTasks.containsKey(scheduleInfoBean)) {
                            scheduledTasks.put(scheduleInfoBean, scheduledTasks.remove(scheduleInfoBean));
                        } else {
                            scheduledTasks.put(scheduleInfoBean, future);
                        }
                    }
                }
            }
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while scheduling.." + ExceptionUtils.getStackTrace(e));
            }
        }
        if (invalidSchedulingJobs.size() > 0 || rejectedSchedulingJobs.size() > 0) {
            if (log.isInfoEnabled()) {
                log.info("Below list of jobs failed to schedule...");
            }
            if (log.isErrorEnabled()) {
                log.error("InvalidSchedulingJobs:" + invalidSchedulingJobs + "\nRejectedSchedulingJobs: " + rejectedSchedulingJobs.toString());
            }

            //TODO sendNotification(invalidSchedulingJobs, rejectedSchedulingJobs);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduling result for jobGroupName={} => SUCCEED={}, INVALID={}, REJECTED={}",
                    jobGroupName, succeedSchedulingJobs.size(), invalidSchedulingJobs.size(), rejectedSchedulingJobs.size());
        }
        @SuppressWarnings("serial")
        Map<String, Set<ScheduleInfoBean>> schedulingResult = new HashMap<String, Set<ScheduleInfoBean>>() {
            {
                put("SUCCEED", succeedSchedulingJobs);
                put("INVALID", invalidSchedulingJobs);
                put("REJECTED", rejectedSchedulingJobs);
            }
        };

        return schedulingResult;
    }


    @PostConstruct
    public void configExecutor() {
        threadPoolTaskScheduler.getScheduledThreadPoolExecutor().setRemoveOnCancelPolicy(true);
    }

    @PreDestroy
    public void stop() {
        ScheduledExecutorService scheduledExecutorService = threadPoolTaskScheduler.getScheduledExecutor();
        scheduledExecutorService.shutdown();
    }
}