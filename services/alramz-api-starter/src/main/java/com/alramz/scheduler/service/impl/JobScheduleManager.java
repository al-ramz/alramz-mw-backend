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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import com.alramz.logging.util.LoggingHelper;
import com.alramz.scheduler.constants.SchedulerConstants;
import com.alramz.scheduler.config.SchedulerProperties;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.model.SchedStatus;
import com.alramz.scheduler.service.ISchedulerService;
import com.alramz.scheduler.service.Schedulable;

public class JobScheduleManager implements ISchedulerService {
    private static final Logger log = LoggerFactory.getLogger(JobScheduleManager.class);
    private static final String CRON_EXP = "CRON_EXP";
    private static final String CANCELLING_TASK = "Cancelling task: [{}]...";

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

    private final Map<ScheduleInfoBean, ScheduledFuture<Schedulable>> scheduledTasks = new ConcurrentHashMap<>();

    @Override
    public String stopScheduler() {

        if (log.isInfoEnabled()) {
            log.info("Shutdown requested...");
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        scheduledTasks.forEach((ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> futureTasks) -> {
            if (log.isInfoEnabled()) {
                log.info(CANCELLING_TASK, scheduleInfoBean);
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
    public String hardStopScheduler() {

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
    public Map<String, Set<ScheduleInfoBean>> reintializeScheduler(final String jobGroupName) {

        if (log.isInfoEnabled()) {
            log.info("Reinitialization requested...");
        }

        if (log.isInfoEnabled()) {
            log.info("Before initialization::: {}", getSchedulerStat());
        }

        if (threadPoolTaskScheduler.getActiveCount() > 0 || threadPoolTaskScheduler.getPoolSize() > 0 || !scheduledTasks.isEmpty()) {
            if (log.isErrorEnabled()) {
                log.error("The scheduler has not been shutdown yet. 1st Shutdown it by calling \"/api/secured/scheduler/stop\" before reinitializing...");
            }

            throw new IllegalStateException(SchedulerConstants.SCHEDULER_REINITIALIZATION_ERROR);
        }

        threadPoolTaskScheduler.initialize();
        if (log.isInfoEnabled()) {
            log.info("Rescheduling all jobs...");
        }

        Map<String, Set<ScheduleInfoBean>> schedulingResult = schedule(jobGroupName);

        if (log.isInfoEnabled()) {
            log.info("After initialization::: {}", getSchedulerStat());
        }

        return schedulingResult;
    }

    @Override
    public Map<String, Set<ScheduleInfoBean>> reintializeSchedulerForJobs(String jobGroupName, String... jobIds) {

        if (log.isInfoEnabled()) {
            log.info("Schedule Reinitialization requested for Jobs...{}", Arrays.toString(jobIds));
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        scheduledTasks.forEach((ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> futureTasks) -> {

            if (Arrays.stream(jobIds).anyMatch(scheduleInfoBean.getScheduleId()::equals)) {
                if (log.isInfoEnabled()) {
                    log.info(CANCELLING_TASK, scheduleInfoBean);
                }
                if (futureTasks.cancel(false)) {
                    scheduledTasks.remove(scheduleInfoBean);
                }
            }
        });
        if (log.isInfoEnabled()) {
            log.info("Post cancelling the Jobs:\n{}", getSchedulerStat());
        }
        if (log.isInfoEnabled()) {
            log.info("Registering those jobs to the scheduler...");
        }

        return schedule(jobGroupName, jobIds);
    }

    @Override
    public Map<String, Boolean> unregisterJobs(String... jobIds) {

        if (log.isInfoEnabled()) {
            log.info("Schedule unregister requested for Jobs...{}", Arrays.toString(jobIds));
        }
        if (log.isInfoEnabled()) {
            log.info(getSchedulerStat().toString());
        }

        Map<String, Boolean> cancellationStatus = new HashMap<>();

        scheduledTasks.forEach((ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> futureTasks) -> {

            if (Arrays.stream(jobIds).anyMatch(scheduleInfoBean.getScheduleId()::equals)) {
                if (log.isInfoEnabled()) {
                    log.info(CANCELLING_TASK, scheduleInfoBean);
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
            log.info("Post cancelling the Jobs\n{}", getSchedulerStat());
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

    public synchronized Map<String, Set<ScheduleInfoBean>> schedule(String jobGroupName, String... jobIds) {

        String safeGroupName = LoggingHelper.stripCrlf(jobGroupName);
        Set<ScheduleInfoBean> invalidSchedulingJobs = new HashSet<>();
        Set<ScheduleInfoBean> rejectedSchedulingJobs = new HashSet<>();
        Set<ScheduleInfoBean> succeedSchedulingJobs = new HashSet<>();
        try {
            List<ScheduleInfoBean> scheduledJobs = loadJobs(jobGroupName, safeGroupName, jobIds);

            if (log.isInfoEnabled()) {
                log.info("Total jobs to schedule for jobGroupName={}: {}", safeGroupName, scheduledJobs.size());
            }

            for (final ScheduleInfoBean scheduleInfoBean : scheduledJobs) {
                if (scheduledTasks.containsKey(scheduleInfoBean)) {
                    if (log.isErrorEnabled()) {
                        log.error("Scheduler: ScheduleId= {} already scheduled and active, hence rejecting",
                                LoggingHelper.stripCrlf(scheduleInfoBean.getScheduleId()));
                    }
                    rejectedSchedulingJobs.add(scheduleInfoBean);
                } else {
                    // Each job uses its own future: an invalid/rejected job must not inherit the previous job's
                    // future and be reported as SUCCEED (or be cancellable via the wrong task).
                    ScheduledFuture<Schedulable> future =
                            scheduleJob(scheduleInfoBean, invalidSchedulingJobs, rejectedSchedulingJobs);
                    if (future != null) {
                        succeedSchedulingJobs.add(scheduleInfoBean);
                        registerScheduledTask(scheduleInfoBean, future);
                    }
                }
            }
        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while scheduling..", e);
            }
        }
        logFailedJobs(invalidSchedulingJobs, rejectedSchedulingJobs);
        if (log.isInfoEnabled()) {
            log.info("Scheduling result for jobGroupName={} => SUCCEED={}, INVALID={}, REJECTED={}",
                    safeGroupName, succeedSchedulingJobs.size(), invalidSchedulingJobs.size(), rejectedSchedulingJobs.size());
        }
        Map<String, Set<ScheduleInfoBean>> schedulingResult = new HashMap<>();
        schedulingResult.put("SUCCEED", succeedSchedulingJobs);
        schedulingResult.put("INVALID", invalidSchedulingJobs);
        schedulingResult.put("REJECTED", rejectedSchedulingJobs);

        return schedulingResult;
    }

    private List<ScheduleInfoBean> loadJobs(String jobGroupName, String safeGroupName, String... jobIds) {
        boolean dbMode = middlewareNamedParameterJdbcTemplate != null;
        if (log.isInfoEnabled()) {
            log.info("Scheduler startup loading: jobGroupName={}, jobIds={}, dbMode={}", safeGroupName,
                    hasJobIds(jobIds) ? LoggingHelper.stripCrlf(Arrays.toString(jobIds)) : "ALL", dbMode);
        }

        List<ScheduleInfoBean> dbJobs = new ArrayList<>();
        if (dbMode) {
            dbJobs = loadDbJobs(jobGroupName, safeGroupName);
        } else if (log.isInfoEnabled()) {
            log.info("middlewareNamedParameterJdbcTemplate is not available. Falling back to YAML properties for scheduler jobs");
        }

        if (dbJobs.isEmpty()) {
            if (log.isInfoEnabled()) {
                log.info("No DB jobs found for jobGroupName={}. Loading from YAML properties...", safeGroupName);
            }
            return loadYamlJobs(jobIds);
        }
        return filterDbJobs(dbJobs, jobIds);
    }

    private List<ScheduleInfoBean> loadDbJobs(String jobGroupName, String safeGroupName) {
        String sql = "SELECT job_group_name, schedule_id, worker_bean_name, job_bean_names, job_parameter, " +
                "schedule_mode, cron_expr, delay, interval_seconds, enable FROM schedule_job " +
                "WHERE job_group_name = :jobGroupName AND enable = 'Y'";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("jobGroupName", jobGroupName);
        List<ScheduleInfoBean> dbJobs = middlewareNamedParameterJdbcTemplate.query(sql, params, (rs, rowNum) -> {
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
            log.info("Fetched {} scheduled jobs from schedule_job table for jobGroupName={}", dbJobs.size(), safeGroupName);
        }
        return dbJobs;
    }

    private List<ScheduleInfoBean> loadYamlJobs(String... jobIds) {
        List<ScheduleInfoBean> scheduledJobs = new ArrayList<>();
        for (SchedulerProperties.ScheduleJobProperties jobProps : properties.getJobs()) {
            if ("Y".equalsIgnoreCase(jobProps.getEnable()) && matchesJobIds(jobProps.getScheduleId(), jobIds)) {
                ScheduleInfoBean b = new ScheduleInfoBean(
                        jobProps.getWorkerBeanName(),
                        jobProps.getJobBeanNames(),
                        jobProps.getJobParameter(),
                        jobProps.getScheduleMode(),
                        null,
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
        }
        return scheduledJobs;
    }

    private List<ScheduleInfoBean> filterDbJobs(List<ScheduleInfoBean> dbJobs, String... jobIds) {
        List<ScheduleInfoBean> scheduledJobs = new ArrayList<>();
        for (ScheduleInfoBean scheduleInfoBean : dbJobs) {
            if (matchesJobIds(scheduleInfoBean.getScheduleId(), jobIds)) {
                scheduledJobs.add(scheduleInfoBean);
                if (log.isInfoEnabled()) {
                    log.info("DB Sceduled job loaded: scheduleId={}, workerBeanName={}, scheduleMode={}, cronExpr={}, delay={}, intervalSeconds={}",
                            scheduleInfoBean.getScheduleId(), scheduleInfoBean.getWorkerBeanName(), scheduleInfoBean.getScheduleMode(),
                            scheduleInfoBean.getCronExpr(), scheduleInfoBean.getDelay(), scheduleInfoBean.getInterval());
                }
            }
        }
        return scheduledJobs;
    }

    private static boolean hasJobIds(String... jobIds) {
        return jobIds != null && jobIds.length > 0;
    }

    private static boolean matchesJobIds(String scheduleId, String... jobIds) {
        return !hasJobIds(jobIds) || Arrays.stream(jobIds).anyMatch(id -> id.equalsIgnoreCase(scheduleId));
    }

    /**
     * Schedules a single job according to its mode. Returns the future, or {@code null} when the job was
     * classified as invalid or rejected (in which case it has been added to the matching set).
     */
    private ScheduledFuture<Schedulable> scheduleJob(ScheduleInfoBean scheduleInfoBean,
                                                     Set<ScheduleInfoBean> invalidSchedulingJobs,
                                                     Set<ScheduleInfoBean> rejectedSchedulingJobs) {
        Runnable runnableObj = () -> {
            Schedulable worker = (Schedulable) beanFactory.getBean(scheduleInfoBean.getWorkerBeanName());
            worker.run(scheduleInfoBean);
        };
        try {
            ScheduledFuture<Schedulable> future = scheduleByMode(scheduleInfoBean, runnableObj);
            if (future == null) {
                if (log.isErrorEnabled()) {
                    log.error("Job: {} has been ignored. Can't be schedule due to invalid scheduling configuration", scheduleInfoBean);
                }
                invalidSchedulingJobs.add(scheduleInfoBean);
            }
            return future;
        } catch (TaskRejectedException | IllegalArgumentException tie) {
            if (tie instanceof IllegalArgumentException && CRON_EXP.equalsIgnoreCase(scheduleInfoBean.getScheduleMode())) {
                if (log.isErrorEnabled()) {
                    log.error("Job: {} has invalid CRON expression. Failed to schedule.", scheduleInfoBean, tie);
                }
                invalidSchedulingJobs.add(scheduleInfoBean);
            } else {
                rejectedSchedulingJobs.add(scheduleInfoBean);
                if (log.isErrorEnabled()) {
                    log.error("Job: {} got failed to schedule.", scheduleInfoBean, tie);
                }
            }
            return null;
        }
    }

    /** Returns the scheduled future, or {@code null} when the job's scheduling configuration is invalid. */
    @SuppressWarnings("unchecked") // ThreadPoolTaskScheduler returns ScheduledFuture<?>
    private ScheduledFuture<Schedulable> scheduleByMode(ScheduleInfoBean job, Runnable runnableObj) {
        String mode = job.getScheduleMode();
        if ("FIXED_RATE".equalsIgnoreCase(mode) && job.getInterval() != null && job.getInterval() > -1) {
            return (ScheduledFuture<Schedulable>) (job.getStartTime() != null
                    ? threadPoolTaskScheduler.scheduleAtFixedRate(runnableObj, job.getStartTime(), job.getInterval())
                    : threadPoolTaskScheduler.scheduleAtFixedRate(runnableObj, job.getInterval()));
        }
        if ("FIXED_DELAY".equalsIgnoreCase(mode) && job.getDelay() != null && job.getDelay() > -1) {
            return (ScheduledFuture<Schedulable>) (job.getStartTime() != null
                    ? threadPoolTaskScheduler.scheduleWithFixedDelay(runnableObj, job.getStartTime(), job.getDelay())
                    : threadPoolTaskScheduler.scheduleWithFixedDelay(runnableObj, job.getDelay()));
        }
        if (CRON_EXP.equalsIgnoreCase(mode) && job.getCronExpr() != null) {
            return (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.schedule(runnableObj, new CronTrigger(job.getCronExpr()));
        }
        if ("ONE_TIME".equalsIgnoreCase(mode) && job.getStartTime() != null) {
            return (ScheduledFuture<Schedulable>) threadPoolTaskScheduler.schedule(runnableObj, job.getStartTime());
        }
        return null;
    }

    private void registerScheduledTask(ScheduleInfoBean scheduleInfoBean, ScheduledFuture<Schedulable> future) {
        if (scheduledTasks.containsKey(scheduleInfoBean)) {
            scheduledTasks.put(scheduleInfoBean, scheduledTasks.remove(scheduleInfoBean));
        } else {
            scheduledTasks.put(scheduleInfoBean, future);
        }
    }

    private static void logFailedJobs(Set<ScheduleInfoBean> invalidSchedulingJobs, Set<ScheduleInfoBean> rejectedSchedulingJobs) {
        if (!invalidSchedulingJobs.isEmpty() || !rejectedSchedulingJobs.isEmpty()) {
            if (log.isInfoEnabled()) {
                log.info("Below list of jobs failed to schedule...");
            }
            if (log.isErrorEnabled()) {
                log.error("InvalidSchedulingJobs:{}\nRejectedSchedulingJobs: {}", invalidSchedulingJobs, rejectedSchedulingJobs);
            }
        }
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
