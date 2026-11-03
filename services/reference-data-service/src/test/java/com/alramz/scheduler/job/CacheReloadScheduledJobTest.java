package com.alramz.scheduler.job;

import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.service.RedisCacheService;
import com.alramz.service.SchedulerLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CacheReloadScheduledJobTest {

    private static final String LOCK_KEY = "cacheReloadJob-countries";

    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private SchedulerLockService lockService;

    private CacheReloadScheduledJob job;

    @BeforeEach
    void setUp() {
        job = new CacheReloadScheduledJob(redisCacheService, lockService);
    }

    private static ScheduleInfoBean bean(String param) {
        ScheduleInfoBean bean = new ScheduleInfoBean();
        bean.setScheduleId("s1");
        bean.setCronExpr("0 0 * * * *");
        bean.setJobParameter(param);
        return bean;
    }

    @Test
    void run_shouldCleanAcquireReloadAndReleaseLock() {
        when(lockService.tryAcquireLock(LOCK_KEY, Duration.ofMinutes(5))).thenReturn(true);
        when(redisCacheService.reloadGlobalConfig("countries")).thenReturn("ok");

        job.run(bean("countries"));

        InOrder order = inOrder(lockService, redisCacheService);
        order.verify(lockService).cleanStaleLocks(LOCK_KEY);
        order.verify(lockService).tryAcquireLock(LOCK_KEY, Duration.ofMinutes(5));
        order.verify(redisCacheService).reloadGlobalConfig("countries");
        order.verify(lockService).releaseLock(LOCK_KEY);
    }

    @Test
    void run_shouldSkipWhenJobParameterBlank() {
        job.run(bean("  "));
        job.run(bean(null));

        verifyNoInteractions(lockService, redisCacheService);
    }

    @Test
    void run_shouldSkipReloadWhenLockHeldElsewhere() {
        when(lockService.tryAcquireLock(LOCK_KEY, Duration.ofMinutes(5))).thenReturn(false);

        job.run(bean("countries"));

        verify(redisCacheService, never()).reloadGlobalConfig(anyString());
        verify(lockService, never()).releaseLock(anyString());
    }

    @Test
    void run_shouldReleaseLockEvenWhenReloadThrows() {
        when(lockService.tryAcquireLock(LOCK_KEY, Duration.ofMinutes(5))).thenReturn(true);
        when(redisCacheService.reloadGlobalConfig("countries")).thenThrow(new IllegalStateException("boom"));

        assertThatNoException().isThrownBy(() -> job.run(bean("countries")));
        verify(lockService).releaseLock(LOCK_KEY);
    }
}
