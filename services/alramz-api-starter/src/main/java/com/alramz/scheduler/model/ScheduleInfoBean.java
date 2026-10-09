package com.alramz.scheduler.model;

import java.io.Serializable;
import java.sql.Timestamp;

public final class ScheduleInfoBean implements Serializable {

    private static final long serialVersionUID = 6013779929527787611L;

    private String scheduleId;
    private String workerBeanName;
    private String jobBeanNames;
    private String jobParameter;
    private String scheduleMode;
    private Timestamp startTime;
    private String cronExpr;
    private Long delay = -1L;
    private Long interval = -1L;
    private String enable;

    public ScheduleInfoBean() {
    }

    public ScheduleInfoBean(String workerBeanName, String jobBeanNames, String jobParameter, String scheduleMode,
                            Timestamp startTime, String cronExpr, Long delay, Long interval, String enable) {
        this.workerBeanName = workerBeanName;
        this.jobBeanNames = jobBeanNames;
        this.jobParameter = jobParameter;
        this.scheduleMode = scheduleMode;
        this.startTime = startTime;
        this.cronExpr = cronExpr;
        this.delay = delay;
        this.interval = interval;
        this.enable = enable;
    }

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

    public Timestamp getStartTime() {
        return startTime;
    }

    public void setStartTime(Timestamp startTime) {
        this.startTime = startTime;
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

    @Override
    public String toString() {
        return "\nScheduleInfoBean [scheduleId=" + scheduleId + ", workerBeanName=" + workerBeanName + ", jobBeanNames="
                + jobBeanNames + ", jobParameter=" + jobParameter + ", scheduleMode=" + scheduleMode + ", startTime="
                + startTime + ", cronExpr=" + cronExpr + ", delay=" + delay + ", interval=" + interval + ", enable="
                + enable + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((scheduleId == null) ? 0 : scheduleId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        ScheduleInfoBean other = (ScheduleInfoBean) obj;
        if (scheduleId == null) {
            if (other.scheduleId != null) {
                return false;
            }
        } else if (!scheduleId.equals(other.scheduleId)) {
            return false;
        }
        return true;
    }

}