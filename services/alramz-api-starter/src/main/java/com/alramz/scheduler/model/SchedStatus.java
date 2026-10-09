package com.alramz.scheduler.model;

import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import com.alramz.scheduler.service.Schedulable;

public class SchedStatus {

    private final Map<ScheduleInfoBean, ScheduledFuture<Schedulable>> scheduledTasks;
    private final int activeTaskCount;
    private final int currentPoolSize;
    private final long maxPoolSize;
    private final long completedTaskCount;
    private final int currentQSize;

    public SchedStatus(Map<ScheduleInfoBean, ScheduledFuture<Schedulable>> scheduledTasks,
                       int activeTaskCount, int currentPoolSize, long maxPoolSize,
                       long completedTaskCount, int currentQSize) {
        this.scheduledTasks = scheduledTasks;
        this.activeTaskCount = activeTaskCount;
        this.currentPoolSize = currentPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.completedTaskCount = completedTaskCount;
        this.currentQSize = currentQSize;
    }

    public Map<ScheduleInfoBean, ScheduledFuture<Schedulable>> getScheduledTasks() {
        return scheduledTasks;
    }

    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public int getCurrentPoolSize() {
        return currentPoolSize;
    }

    public long getMaxPoolSize() {
        return maxPoolSize;
    }

    public long getCompletedTaskCount() {
        return completedTaskCount;
    }

    public int getCurrentQSize() {
        return currentQSize;
    }

}