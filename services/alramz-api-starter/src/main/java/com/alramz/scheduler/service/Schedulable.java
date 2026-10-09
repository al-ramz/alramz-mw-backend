package com.alramz.scheduler.service;

import com.alramz.scheduler.model.ScheduleInfoBean;

public interface Schedulable {

    void run(ScheduleInfoBean scheduleInfoBean);

}