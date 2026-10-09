package com.alramz.scheduler.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "schedule_job")
public class ScheduleJobEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_group_name", nullable = false, length = 100)
    private String jobGroupName;

    @Column(name = "schedule_id", nullable = false, length = 100)
    private String scheduleId;

    @Column(name = "worker_bean_name", nullable = false, length = 100)
    private String workerBeanName;

    @Column(name = "job_bean_names", length = 200)
    private String jobBeanNames;

    @Column(name = "job_parameter", length = 500)
    private String jobParameter;

    @Column(name = "schedule_mode", nullable = false, length = 20)
    private String scheduleMode;

    @Column(name = "cron_expr", length = 100)
    private String cronExpr;

    @Column(name = "delay")
    private Long delay;

    @Column(name = "interval_seconds")
    private Long intervalSeconds;

    @Column(name = "enable", nullable = false, length = 1)
    private String enable;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobGroupName() {
        return jobGroupName;
    }

    public void setJobGroupName(String jobGroupName) {
        this.jobGroupName = jobGroupName;
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

    public Long getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(Long intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
    }

    public String getEnable() {
        return enable;
    }

    public void setEnable(String enable) {
        this.enable = enable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}