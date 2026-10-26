package com.alramz.service.impl;

import com.alramz.exception.InvalidDateRangeException;
import com.alramz.model.CommissionResult;
import com.alramz.model.CommissionsByRelationshipManagerRequest;
import com.alramz.model.RelationshipManager;
import com.alramz.model.RelationshipManagerCommissionsSummary;
import com.alramz.model.RelationshipManagerListResult;
import com.alramz.repository.RelationshipManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipManagerServiceImplTest {

    private static final LocalDate DEFAULT_START_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate DEFAULT_END_DATE = LocalDate.of(2999, 1, 1);

    @Mock
    private RelationshipManagerRepository relationshipManagerRepository;

    private RelationshipManagerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RelationshipManagerServiceImpl(relationshipManagerRepository);
    }

    @Test
    void getAllRelationshipManagers_shouldReturnEmptyListResult_whenNoneActive() {
        when(relationshipManagerRepository.findActiveRelationshipManagers()).thenReturn(List.of());

        RelationshipManagerListResult result = service.getAllRelationshipManagers();

        assertThat(result.getRelationshipManagers()).isEmpty();
    }

    @Test
    void getAllRelationshipManagers_shouldPassThroughRepositoryResults() {
        RelationshipManager rm = new RelationshipManager();
        rm.setId(101);
        rm.setNameEn("John Smith");
        rm.setNameAr("جون سميث");
        when(relationshipManagerRepository.findActiveRelationshipManagers()).thenReturn(List.of(rm));

        RelationshipManagerListResult result = service.getAllRelationshipManagers();

        assertThat(result.getRelationshipManagers()).containsExactly(rm);
    }

    @Test
    void getCommissionsByRelationshipManager_shouldApplyDefaultDates_whenAbsent() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of());

        service.getCommissionsByRelationshipManager(request);

        verify(relationshipManagerRepository)
                .findCommissionsByRelationshipManager(eq(DEFAULT_START_DATE), eq(DEFAULT_END_DATE), eq(List.of()));
    }

    @Test
    void getCommissionsByRelationshipManager_shouldApplyDefaultDates_whenBlank() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        request.setStartDate("");
        request.setEndDate("");
        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of());

        service.getCommissionsByRelationshipManager(request);

        verify(relationshipManagerRepository)
                .findCommissionsByRelationshipManager(eq(DEFAULT_START_DATE), eq(DEFAULT_END_DATE), eq(List.of()));
    }

    @Test
    void getCommissionsByRelationshipManager_blankAndAbsentDates_shouldProduceIdenticalResult() {
        CommissionsByRelationshipManagerRequest absentRequest = new CommissionsByRelationshipManagerRequest();
        CommissionsByRelationshipManagerRequest blankRequest = new CommissionsByRelationshipManagerRequest();
        blankRequest.setStartDate("");
        blankRequest.setEndDate("");

        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of());

        RelationshipManagerCommissionsSummary absentResult = service.getCommissionsByRelationshipManager(absentRequest);
        RelationshipManagerCommissionsSummary blankResult = service.getCommissionsByRelationshipManager(blankRequest);

        assertThat(blankResult.getTotalTradingVolume()).isEqualByComparingTo(absentResult.getTotalTradingVolume());
        assertThat(blankResult.getTotalReceivedCommission()).isEqualByComparingTo(absentResult.getTotalReceivedCommission());
        assertThat(blankResult.getResults()).isEqualTo(absentResult.getResults());
    }

    @Test
    void getCommissionsByRelationshipManager_shouldThrowInvalidDateRangeException_forNonIsoButPatternMatchingStartDate() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        request.setStartDate("2024-13-45");

        assertThatThrownBy(() -> service.getCommissionsByRelationshipManager(request))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("startDate");
    }

    @Test
    void getCommissionsByRelationshipManager_shouldThrowInvalidDateRangeException_forInvalidEndDate() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        request.setEndDate("2024-13-45");

        assertThatThrownBy(() -> service.getCommissionsByRelationshipManager(request))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("endDate");
    }

    @Test
    void getCommissionsByRelationshipManager_shouldPassThroughEmptyCodes_whenOmitted() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of());

        service.getCommissionsByRelationshipManager(request);

        verify(relationshipManagerRepository).findCommissionsByRelationshipManager(any(), any(), eq(List.of()));
    }

    @Test
    void getCommissionsByRelationshipManager_shouldPassThroughSoleIslamicCodeUnchanged() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        request.setRelationshipManagerCodes(List.of("3031"));
        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of());

        service.getCommissionsByRelationshipManager(request);

        verify(relationshipManagerRepository).findCommissionsByRelationshipManager(any(), any(), eq(List.of("3031")));
    }

    @Test
    void getCommissionsByRelationshipManager_shouldReturnZeroTotalsAndEmptyResults_forMixedIslamicCodeRequest() {
        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        request.setRelationshipManagerCodes(List.of("3031", "5000"));
        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), eq(List.of("3031", "5000"))))
                .thenReturn(List.of());

        RelationshipManagerCommissionsSummary summary = service.getCommissionsByRelationshipManager(request);

        assertThat(summary.getTotalTradingVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalReceivedCommission()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getResults()).isEmpty();
    }

    @Test
    void getCommissionsByRelationshipManager_shouldSumTradingVolumeAndCommissionUsingBigDecimal() {
        CommissionResult r1 = new CommissionResult();
        r1.setClientNumber("C-1");
        r1.setClientName("Client One");
        r1.setTradingVolume(new BigDecimal("100.10"));
        r1.setReceivedCommission(new BigDecimal("5.05"));

        CommissionResult r2 = new CommissionResult();
        r2.setClientNumber("C-2");
        r2.setClientName("Client Two");
        r2.setTradingVolume(new BigDecimal("200.20"));
        r2.setReceivedCommission(new BigDecimal("10.10"));

        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of(r1, r2));

        RelationshipManagerCommissionsSummary summary = service.getCommissionsByRelationshipManager(
                new CommissionsByRelationshipManagerRequest());

        assertThat(summary.getTotalTradingVolume()).isEqualByComparingTo("300.30");
        assertThat(summary.getTotalReceivedCommission()).isEqualByComparingTo("15.15");
        assertThat(summary.getResults()).containsExactly(r1, r2);
    }

    @Test
    void getCommissionsByRelationshipManager_shouldHandleNullRequest() {
        when(relationshipManagerRepository.findCommissionsByRelationshipManager(any(), any(), any()))
                .thenReturn(List.of());

        RelationshipManagerCommissionsSummary summary = service.getCommissionsByRelationshipManager(null);

        assertThat(summary.getTotalTradingVolume()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getTotalReceivedCommission()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.getResults()).isEmpty();
        verify(relationshipManagerRepository)
                .findCommissionsByRelationshipManager(eq(DEFAULT_START_DATE), eq(DEFAULT_END_DATE), isNull());
    }
}
