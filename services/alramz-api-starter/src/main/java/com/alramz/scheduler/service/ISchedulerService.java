package com.alramz.scheduler.service;

import java.util.Map;
import java.util.Set;

import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.model.SchedStatus;

public interface ISchedulerService {

    String stopScheduler() ;

    String hardStopScheduler() ;

    Map<String, Set<ScheduleInfoBean>> reintializeScheduler(String jobGroupName) ;

    Map<String, Set<ScheduleInfoBean>> reintializeSchedulerForJobs(String jobGroupName, String... jobIds) ;

    Map<String, Set<ScheduleInfoBean>> schedule(String jobGroupName, String... jobIds);

    Map<String, Boolean> unregisterJobs(String... jobIds) ;

    SchedStatus getSchedulerStat();

}
