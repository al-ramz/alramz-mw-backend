package com.alramz.scheduler.model;

import java.util.List;

public class RefreshJobsRequest {
    private String jobGroupName;
    private List<String> jobIds;

    public RefreshJobsRequest() {
    }

    public RefreshJobsRequest(String jobGroupName, List<String> jobIds) {
        this.jobGroupName = jobGroupName;
        this.jobIds = jobIds;
    }

    public String getJobGroupName() {
        return jobGroupName;
    }

    public void setJobGroupName(String jobGroupName) {
        this.jobGroupName = jobGroupName;
    }

    public List<String> getJobIds() {
        return jobIds;
    }

    public void setJobIds(List<String> jobIds) {
        this.jobIds = jobIds;
    }
}
