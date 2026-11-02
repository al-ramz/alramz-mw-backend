package com.alramz.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulerLockServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private SchedulerLockService service;

    @Test
    void tryAcquireLock_shouldReturnTrueAndBindLeaseParamsWhenRowUpserted() {
        when(jdbc.update(startsWith("INSERT INTO application_workflow_locks"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        LocalDateTime before = LocalDateTime.now();

        assertThat(service.tryAcquireLock("job-1", Duration.ofMinutes(5))).isTrue();

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(anyString(), captor.capture());
        MapSqlParameterSource params = captor.getValue();
        assertThat(params.getValue("jobId")).isEqualTo("job-1");
        assertThat(params.getValue("status")).isEqualTo("IN_PROGRESS");
        assertThat(params.getValue("lockedBy")).isNotNull();
        assertThat((LocalDateTime) params.getValue("expiresAt")).isAfterOrEqualTo(before.plusMinutes(5));
    }

    @Test
    void tryAcquireLock_shouldReturnFalseWhenLockHeldElsewhere() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThat(service.tryAcquireLock("job-1", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void tryAcquireLock_shouldReturnFalseOnDatabaseError() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThat(service.tryAcquireLock("job-1", Duration.ofMinutes(5))).isFalse();
    }

    @Test
    void releaseLock_shouldDeleteOwnLock() {
        service.releaseLock("job-1");

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).update(startsWith("DELETE FROM application_workflow_locks WHERE mapping_name = :jobId AND locked_by"),
                captor.capture());
        assertThat(captor.getValue().getValue("jobId")).isEqualTo("job-1");
    }

    @Test
    void releaseLock_shouldSwallowDatabaseError() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatNoException().isThrownBy(() -> service.releaseLock("job-1"));
    }

    @Test
    void cleanStaleLocks_shouldDeleteExpiredLocksForJob() {
        service.cleanStaleLocks("job-1");

        verify(jdbc).update(startsWith("DELETE FROM application_workflow_locks WHERE mapping_name = :jobId AND expires_at"),
                any(MapSqlParameterSource.class));
    }

    @Test
    void cleanStaleLocks_shouldSwallowDatabaseError() {
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertThatNoException().isThrownBy(() -> service.cleanStaleLocks("job-1"));
    }
}
