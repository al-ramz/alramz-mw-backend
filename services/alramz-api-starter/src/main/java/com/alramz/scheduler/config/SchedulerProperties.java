package com.alramz.scheduler.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = SchedulerProperties.PREFIX)
public class SchedulerProperties {

    public static final String PREFIX = "company.scheduler";

    private int poolSize = 50;
    private String threadGroupName = "Scheduled-Tasks";
    private String threadNamePrefix = "TaskScheduler-Worker-";
    private boolean managementEndpointsEnabled = false;
    private List<ScheduleJobProperties> jobs = new ArrayList<>();

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public String getThreadGroupName() {
        return threadGroupName;
    }

    public void setThreadGroupName(String threadGroupName) {
        this.threadGroupName = threadGroupName;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
        this.threadNamePrefix = threadNamePrefix;
    }

    public boolean isManagementEndpointsEnabled() {
        return managementEndpointsEnabled;
    }

    public void setManagementEndpointsEnabled(boolean managementEndpointsEnabled) {
        this.managementEndpointsEnabled = managementEndpointsEnabled;
    }

    public List<ScheduleJobProperties> getJobs() {
        return jobs;
    }

    public void setJobs(List<ScheduleJobProperties> jobs) {
        this.jobs = jobs;
    }

    public static class ScheduleJobProperties {
        private String scheduleId;
        private String workerBeanName;
        private String jobBeanNames = "";
        private String jobParameter = "";
        private String scheduleMode = "CRON_EXP";
        private String cronExpr;
        private Long delay = -1L;
        private Long interval = -1L;
        private String enable = "Y";

        public String getScheduleId() {
            return scheduleId;
        }

        public void setScheduleId(String scheduleId) {
            this.scheduleId = scheduleId;
        }

        public String getWorkerBeanName() {
            return workerBeanName;
        }

        public void setWorkerBeanName(String workerBeanName) {
            this.workerBeanName = workerBeanName;
        }

        public String getJobBeanNames() {
            return jobBeanNames;
        }

        public void setJobBeanNames(String jobBeanNames) {
            this.jobBeanNames = jobBeanNames;
        }

        public String getJobParameter() {
            return jobParameter;
        }

        public void setJobParameter(String jobParameter) {
            this.jobParameter = jobParameter;
        }

        public String getScheduleMode() {
            return scheduleMode;
        }

        public void setScheduleMode(String scheduleMode) {
            this.scheduleMode = scheduleMode;
        }

        public String getCronExpr() {
            return cronExpr;
        }

        public void setCronExpr(String cronExpr) {
            this.cronExpr = cronExpr;
        }

        public Long getDelay() {
            return delay;
        }

        public void setDelay(Long delay) {
            this.delay = delay;
        }

        public Long getInterval() {
            return interval;
        }

        public void setInterval(Long interval) {
            this.interval = interval;
        }

        public String getEnable() {
            return enable;
        }

        public void setEnable(String enable) {
            this.enable = enable;
        }
    }
}