package com.alramz.scheduler.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.alramz.scheduler.service.ISchedulerService;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.model.SchedStatus;
import com.alramz.scheduler.model.RestartSchedulerRequest;
import com.alramz.scheduler.model.RefreshJobsRequest;

@ConditionalOnProperty(name = "company.scheduler.management-endpoints.enabled", havingValue = "true")
@ConditionalOnWebApplication
@RestController
public class SchedulerManagerController {

    private static Logger log = LoggerFactory.getLogger(SchedulerManagerController.class);

    @Autowired
    ISchedulerService scheduler;

    @PostMapping(value = "/api/secured/scheduler/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> stopScheduler() throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Request received to stop the scheduler. Trying to stop the scheduler...");
        }
        String result;
        try {
            result = scheduler.stopScheduler();

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to stop the scheduler. " + ExceptionUtils.getStackTrace(e));
            }

            return new ResponseEntity<>("Failed to Stop the scheduler", HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler stopped successfully");
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/restart", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Set<ScheduleInfoBean>>> restartScheduler(@RequestBody RestartSchedulerRequest request)
            throws Exception {

        String jobGroupName = request.getJobGroupName();

        if (log.isInfoEnabled()) {
            log.info("Request received to restart the scheduler with jobGroupName=" + jobGroupName
            + ". Trying to restart the scheduler...");
        }
        Map<String, Set<ScheduleInfoBean>> result = new HashMap<>();
        try {
            result = scheduler.reintializeScheduler(jobGroupName);

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to restart the scheduler. " + ExceptionUtils.getStackTrace(e));
            }
            result.put("ERROR: Failed to restart the scheduler", null);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler restarted successfully");
        }

        return new ResponseEntity<Map<String, Set<ScheduleInfoBean>>>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/refresh/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Set<ScheduleInfoBean>>> restartScheduledForJobs(@RequestBody RefreshJobsRequest request)
            throws Exception {

        String jobGroupName = request.getJobGroupName();
        List<String> jobIds = request.getJobIds();

        if (log.isInfoEnabled()) {
            log.info("Request received to restart the scheduler with jobGroupName=" + jobGroupName + " and JobIds=["
            + String.join(",", jobIds) + "]. Trying to restart the scheduler...");
        }
        Map<String, Set<ScheduleInfoBean>> result = new HashMap<>();
        try {
            result = scheduler.reintializeSchedulerForJobs(jobGroupName, jobIds.toArray(new String[jobIds.size()]));

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to restart the scheduler. " + ExceptionUtils.getStackTrace(e));
            }
            result.put("ERROR: Failed to restart the scheduler", null);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler restarted successfully");
        }

        return new ResponseEntity<Map<String, Set<ScheduleInfoBean>>>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/unregister/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Boolean>> unregisterJobs(@RequestBody List<String> jobIds) throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Request received to unregisterJobs - JobIds=[" + String.join(",", jobIds) + "]");
        }
        Map<String, Boolean> result = new HashMap<>();
        try {
            result = scheduler.unregisterJobs(jobIds.toArray(new String[jobIds.size()]));

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to restart the scheduler. " + ExceptionUtils.getStackTrace(e));
            }
            result.put("ERROR: Failed to restart the scheduler", false);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler restarted successfully");
        }

        return new ResponseEntity<Map<String, Boolean>>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/api/secured/scheduler/stat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedStatus> getSchedulerStat() throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Request received to get the scheduler current status..");
        }
        SchedStatus stat = null;
        try {
            stat = scheduler.getSchedulerStat(); // NOPMD LawOfDemeter

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while getting the scheduler status" + ExceptionUtils.getStackTrace(e));
            }
            return new ResponseEntity<>(stat, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Returning the scheduler stat");
        }

        return new ResponseEntity<>(stat, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/hardstop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hardStopScheduler() throws Exception {

        if (log.isInfoEnabled()) {
            log.info("Request received to force stop the scheduler. Trying to stop ...");
        }
        String stat;
        try {
            stat = scheduler.hardStopScheduler();

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to stop the scheduler" + ExceptionUtils.getStackTrace(e));
            }
            return new ResponseEntity<>("Failed to stop the scheduler", HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Returning the scheduler stat");
        }

        return new ResponseEntity<>(stat, HttpStatus.OK);
    }

}