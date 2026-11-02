package com.alramz.scheduler.controller;

import com.alramz.scheduler.model.RefreshJobsRequest;
import com.alramz.scheduler.model.RestartSchedulerRequest;
import com.alramz.scheduler.model.SchedStatus;
import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.service.ISchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchedulerManagerControllerTest {

    private ISchedulerService scheduler;
    private SchedulerManagerController controller;

    @BeforeEach
    void setUp() {
        scheduler = mock(ISchedulerService.class);
        controller = new SchedulerManagerController();
        controller.scheduler = scheduler;
    }

    @Test
    void stopReturnsSchedulerResult() throws Exception {
        when(scheduler.stopScheduler()).thenReturn("stopped");

        ResponseEntity<String> response = controller.stopScheduler();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("stopped");
    }

    @Test
    void stopFailureIsReportedInBody() throws Exception {
        when(scheduler.stopScheduler()).thenThrow(new IllegalStateException("x"));

        assertThat(controller.stopScheduler().getBody()).isEqualTo("Failed to Stop the scheduler");
    }

    @Test
    void restartDelegatesWithJobGroup() throws Exception {
        Map<String, Set<ScheduleInfoBean>> result = Map.of("SUCCEED", Set.of());
        when(scheduler.reintializeScheduler("grp")).thenReturn(result);

        ResponseEntity<Map<String, Set<ScheduleInfoBean>>> response =
                controller.restartScheduler(new RestartSchedulerRequest("grp"));

        assertThat(response.getBody()).isSameAs(result);
    }

    @Test
    void restartFailureReturnsErrorKey() throws Exception {
        when(scheduler.reintializeScheduler("grp")).thenThrow(new RuntimeException("busy"));

        Map<String, Set<ScheduleInfoBean>> body = controller.restartScheduler(new RestartSchedulerRequest("grp")).getBody();

        assertThat(body).containsOnlyKeys("ERROR: Failed to restart the scheduler");
    }

    @Test
    void refreshJobsPassesJobIdsAsArray() throws Exception {
        Map<String, Set<ScheduleInfoBean>> result = Map.of("SUCCEED", Set.of());
        when(scheduler.reintializeSchedulerForJobs("grp", "J1", "J2")).thenReturn(result);

        ResponseEntity<Map<String, Set<ScheduleInfoBean>>> response =
                controller.restartScheduledForJobs(new RefreshJobsRequest("grp", List.of("J1", "J2")));

        assertThat(response.getBody()).isSameAs(result);
    }

    @Test
    void refreshJobsFailureReturnsErrorKey() throws Exception {
        when(scheduler.reintializeSchedulerForJobs("grp", "J1")).thenThrow(new RuntimeException("x"));

        assertThat(controller.restartScheduledForJobs(new RefreshJobsRequest("grp", List.of("J1"))).getBody())
                .containsOnlyKeys("ERROR: Failed to restart the scheduler");
    }

    @Test
    void unregisterJobsReturnsCancellationStatus() throws Exception {
        when(scheduler.unregisterJobs("J1")).thenReturn(Map.of("J1", true));

        assertThat(controller.unregisterJobs(List.of("J1")).getBody()).containsEntry("J1", true);
    }

    @Test
    void unregisterJobsFailureReturnsFalseFlag() throws Exception {
        when(scheduler.unregisterJobs("J1")).thenThrow(new RuntimeException("x"));

        assertThat(controller.unregisterJobs(List.of("J1")).getBody())
                .containsEntry("ERROR: Failed to restart the scheduler", false);
    }

    @Test
    void statReturnsSchedulerStatus() throws Exception {
        SchedStatus stat = new SchedStatus(Map.of(), 1, 2, 3L, 4L, 5);
        when(scheduler.getSchedulerStat()).thenReturn(stat);

        SchedStatus body = controller.getSchedulerStat().getBody();

        assertThat(body).isSameAs(stat);
        assertThat(body.getActiveTaskCount()).isEqualTo(1);
        assertThat(body.getCurrentPoolSize()).isEqualTo(2);
        assertThat(body.getMaxPoolSize()).isEqualTo(3L);
        assertThat(body.getCompletedTaskCount()).isEqualTo(4L);
        assertThat(body.getCurrentQSize()).isEqualTo(5);
    }

    @Test
    void statFailureReturnsNullBody() throws Exception {
        when(scheduler.getSchedulerStat()).thenThrow(new RuntimeException("x"));

        assertThat(controller.getSchedulerStat().getBody()).isNull();
    }

    @Test
    void hardStopReturnsResultOrFailureMessage() throws Exception {
        when(scheduler.hardStopScheduler()).thenReturn("forced");
        assertThat(controller.hardStopScheduler().getBody()).isEqualTo("forced");

        when(scheduler.hardStopScheduler()).thenThrow(new RuntimeException("x"));
        assertThat(controller.hardStopScheduler().getBody()).isEqualTo("Failed to stop the scheduler");
    }
}
