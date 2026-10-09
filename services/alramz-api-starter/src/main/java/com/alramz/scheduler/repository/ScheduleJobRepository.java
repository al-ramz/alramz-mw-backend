package com.alramz.scheduler.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alramz.scheduler.entity.ScheduleJobEntity;

public interface ScheduleJobRepository extends JpaRepository<ScheduleJobEntity, Long> {

    List<ScheduleJobEntity> findByJobGroupNameAndEnable(String jobGroupName, String enable);
}