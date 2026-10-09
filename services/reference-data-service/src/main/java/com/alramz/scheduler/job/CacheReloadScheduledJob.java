package com.alramz.scheduler.job;

import com.alramz.config.CompanyRedisProperties;
import com.alramz.service.RedisCacheService;
import com.alramz.service.SchedulerLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.service.Schedulable;

import java.time.Duration;

@Component("cacheReloadJobRunner")
public class CacheReloadScheduledJob implements Schedulable {

    private static final Logger log = LoggerFactory.getLogger(CacheReloadScheduledJob.class);
    private final RedisCacheService redisCacheService;
    private final CompanyRedisProperties companyRedisProperties;
    private final SchedulerLockService schedulerLockService;

    public CacheReloadScheduledJob(RedisCacheService redisCacheService,
                                   CompanyRedisProperties companyRedisProperties,
                                   SchedulerLockService schedulerLockService) {
        this.redisCacheService = redisCacheService;
        this.companyRedisProperties = companyRedisProperties;
        this.schedulerLockService = schedulerLockService;
    }

    @Override
    public void run(ScheduleInfoBean scheduleInfoBean) {
        if (log.isInfoEnabled()) {
            log.info("Executing scheduled cache reload job: scheduleId={}, cronExpr={}, jobParameter={}",
                    scheduleInfoBean.getScheduleId(),
                    scheduleInfoBean.getCronExpr(),
                    scheduleInfoBean.getJobParameter());
        }

        String mappingName = scheduleInfoBean.getJobParameter();
        if (mappingName == null || mappingName.isBlank()) {
            log.warn("Cache reload job skipped: jobParameter/mappingName is blank for scheduleId={}", scheduleInfoBean.getScheduleId());
            return;
        }

        String lockKey = "cacheReloadJob-" + mappingName;
        schedulerLockService.cleanStaleLocks(lockKey);

        if (!schedulerLockService.tryAcquireLock(lockKey, Duration.ofMinutes(5))) {
            log.info("Skipping cache reload for mapping={} because another instance holds the lock", mappingName);
            return;
        }

        try {
            String result = redisCacheService.reloadGlobalConfig(mappingName);
            log.info("Cache reload result for {}: {}", mappingName, result);
        } catch (Exception e) {
            log.error("Cache reload failed for mapping {}: {}", mappingName, e.getMessage(), e);
        } finally {
            schedulerLockService.releaseLock(lockKey);
        }
    }
}
