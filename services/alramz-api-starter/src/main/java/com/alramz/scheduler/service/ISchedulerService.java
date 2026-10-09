package com.alramz.scheduler.service;

import java.util.Map;
import java.util.Set;

import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.model.SchedStatus;

public interface ISchedulerService {

    String stopScheduler() throws Exception;

    String hardStopScheduler() throws Exception;

    Map<String, Set<ScheduleInfoBean>> reintializeScheduler(final String jobGroupName) throws Exception;

    Map<String, Set<ScheduleInfoBean>> reintializeSchedulerForJobs(String jobGroupName, String... jobIds) throws Exception;

    Map<String, Set<ScheduleInfoBean>> schedule(String jobGroupName, String... jobIds);

    Map<String, Boolean> unregisterJobs(String... jobIds) throws Exception;

    SchedStatus getSchedulerStat();

}