package com.alramz.scheduler.model;

public class RestartSchedulerRequest {
    private String jobGroupName;

    public RestartSchedulerRequest() {
    }

    public RestartSchedulerRequest(String jobGroupName) {
        this.jobGroupName = jobGroupName;
    }

    public String getJobGroupName() {
        return jobGroupName;
    }

    public void setJobGroupName(String jobGroupName) {
        this.jobGroupName = jobGroupName;
    }
}
