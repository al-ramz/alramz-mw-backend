package com.alramz.scheduler.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alramz.logging.util.LoggingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(SchedulerManagerController.class);
    private static final String RESTART_FAILED = "ERROR: Failed to restart the scheduler";
    private static final String RESTART_EXCEPTION = "Exception occurred while trying to restart the scheduler.";

    private final ISchedulerService scheduler;

    public SchedulerManagerController(ISchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping(value = "/api/secured/scheduler/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> stopScheduler() {

        if (log.isInfoEnabled()) {
            log.info("Request received to stop the scheduler. Trying to stop the scheduler...");
        }
        String result;
        try {
            result = scheduler.stopScheduler();

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to stop the scheduler.", e);
            }

            return new ResponseEntity<>("Failed to Stop the scheduler", HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler stopped successfully");
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/restart", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Set<ScheduleInfoBean>>> restartScheduler(@RequestBody RestartSchedulerRequest request) {

        String jobGroupName = LoggingHelper.stripCrlf(request.getJobGroupName());

        if (log.isInfoEnabled()) {
            log.info("Request received to restart the scheduler with jobGroupName={}. Trying to restart the scheduler...",
                    jobGroupName);
        }
        Map<String, Set<ScheduleInfoBean>> result = new HashMap<>();
        try {
            result = scheduler.reintializeScheduler(jobGroupName);

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error(RESTART_EXCEPTION, e);
            }
            result.put(RESTART_FAILED, null);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler restarted successfully");
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/refresh/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Set<ScheduleInfoBean>>> restartScheduledForJobs(@RequestBody RefreshJobsRequest request) {

        String jobGroupName = LoggingHelper.stripCrlf(request.getJobGroupName());
        List<String> jobIds = request.getJobIds().stream().map(LoggingHelper::stripCrlf).toList();

        if (log.isInfoEnabled()) {
            log.info("Request received to restart the scheduler with jobGroupName={} and JobIds=[{}]. Trying to restart the scheduler...",
                    jobGroupName, String.join(",", jobIds));
        }
        Map<String, Set<ScheduleInfoBean>> result = new HashMap<>();
        try {
            result = scheduler.reintializeSchedulerForJobs(jobGroupName, jobIds.toArray(new String[jobIds.size()]));

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error(RESTART_EXCEPTION, e);
            }
            result.put(RESTART_FAILED, null);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler restarted successfully");
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/unregister/jobs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Boolean>> unregisterJobs(@RequestBody List<String> requestedJobIds) {

        List<String> jobIds = requestedJobIds.stream().map(LoggingHelper::stripCrlf).toList();
        if (log.isInfoEnabled()) {
            log.info("Request received to unregisterJobs - JobIds=[{}]", String.join(",", jobIds));
        }
        Map<String, Boolean> result = new HashMap<>();
        try {
            result = scheduler.unregisterJobs(jobIds.toArray(new String[jobIds.size()]));

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error(RESTART_EXCEPTION, e);
            }
            result.put(RESTART_FAILED, false);
            return new ResponseEntity<>(result, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Scheduler restarted successfully");
        }

        return new ResponseEntity<>(result, HttpStatus.OK);
    }

    @GetMapping(value = "/api/secured/scheduler/stat", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SchedStatus> getSchedulerStat() {

        if (log.isInfoEnabled()) {
            log.info("Request received to get the scheduler current status..");
        }
        SchedStatus stat = null;
        try {
            stat = scheduler.getSchedulerStat(); // NOPMD LawOfDemeter

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while getting the scheduler status", e);
            }
            return new ResponseEntity<>(stat, HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Returning the scheduler stat");
        }

        return new ResponseEntity<>(stat, HttpStatus.OK);
    }

    @PostMapping(value = "/api/secured/scheduler/hardstop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> hardStopScheduler() {

        if (log.isInfoEnabled()) {
            log.info("Request received to force stop the scheduler. Trying to stop ...");
        }
        String stat;
        try {
            stat = scheduler.hardStopScheduler();

        } catch (Exception e) { // NOPMD AvoidCatchingGenericException
            if (log.isErrorEnabled()) {
                log.error("Exception occurred while trying to stop the scheduler", e);
            }
            return new ResponseEntity<>("Failed to stop the scheduler", HttpStatus.OK);
        }
        if (log.isInfoEnabled()) {
            log.info("Returning the scheduler stat");
        }

        return new ResponseEntity<>(stat, HttpStatus.OK);
    }

}
