package com.alramz.controllers;

import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.model.MmfAccount;
import com.alramz.model.MmfAccountResponse;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCash;
import com.alramz.model.MmfCustodianCashListResponse;
import com.alramz.model.MmfCustodianPositionListResponse;
import com.alramz.model.MmfProfitDistributionItem;
import com.alramz.model.MmfProfitDistributionResponse;
import com.alramz.model.MmfProfitDistributionResult;
import com.alramz.model.MmfProfitDistributionResult.StatusEnum;
import com.alramz.model.MmfSweep;
import com.alramz.model.MmfSweepConfirmationItem;
import com.alramz.model.MmfSweepConfirmationResult;
import com.alramz.model.MmfSweepInListResponse;
import com.alramz.model.MmfSweepOutListResponse;
import com.alramz.service.MmfService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MmfControllerTest {

    @Mock
    private MmfService mmfService;

    private MmfController controller;

    @BeforeEach
    void setUp() {
        controller = new MmfController(mmfService);
    }

    @AfterEach
    void clearMdc() {
        MDCUtil.clear();
    }

    @Test
    void getMmfAccount_shouldWrapAccount() {
        MmfAccount account = new MmfAccount().accountNumber("2939418");
        when(mmfService.getAccount("2939418", MmfBook.CAPITAL)).thenReturn(account);

        ResponseEntity<GenericResponse> response = controller.getMmfAccount("2939418", MmfBook.CAPITAL);

        assertOk(response);
        assertThat(response.getBody().getResponse()).isEqualTo(new MmfAccountResponse().account(account));
    }

    @Test
    void listMmfSweepIns_shouldRequestTypeW() {
        LocalDate from = LocalDate.of(2025, 4, 1);
        LocalDate to = LocalDate.of(2025, 4, 30);
        List<MmfSweep> sweeps = List.of(new MmfSweep().sweepNo(1L));
        when(mmfService.listSweeps("W", from, to)).thenReturn(sweeps);

        ResponseEntity<GenericResponse> response = controller.listMmfSweepIns(from, to);

        assertOk(response);
        assertThat(response.getBody().getResponse()).isEqualTo(new MmfSweepInListResponse().sweepIns(sweeps));
    }

    @Test
    void listMmfSweepOuts_shouldRequestTypeOAndReturnEmptyList() {
        when(mmfService.listSweeps("O", null, null)).thenReturn(List.of());

        ResponseEntity<GenericResponse> response = controller.listMmfSweepOuts(null, null);

        assertOk(response);
        assertThat(((MmfSweepOutListResponse) response.getBody().getResponse()).getSweepOuts()).isEmpty();
    }

    @Test
    void confirmMmfSweepIns_shouldReturnCounts() {
        List<MmfSweepConfirmationItem> items = List.of(new MmfSweepConfirmationItem().sweepNo(1L).source(MmfBook.CAPITAL));
        MmfSweepConfirmationResult result = new MmfSweepConfirmationResult().confirmedCapital(1).confirmedMmf(0);
        when(mmfService.confirmSweepIns(items)).thenReturn(result);

        ResponseEntity<GenericResponse> response = controller.confirmMmfSweepIns(items);

        assertOk(response);
        assertThat(response.getBody().getResponse()).isSameAs(result);
    }

    @Test
    void createMmfProfitDistributions_allCreated_shouldReturn201() {
        List<MmfProfitDistributionItem> items = List.of(new MmfProfitDistributionItem());
        List<MmfProfitDistributionResult> results = List.of(new MmfProfitDistributionResult().status(StatusEnum.CREATED).sweepNo(1L));
        when(mmfService.createProfitDistributions(items)).thenReturn(results);

        ResponseEntity<GenericResponse> response = controller.createMmfProfitDistributions(items);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getResponseCode()).isEqualTo("201");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Created");
        assertThat(response.getBody().getResponse()).isEqualTo(new MmfProfitDistributionResponse().results(results));
    }

    @Test
    void createMmfProfitDistributions_anyFailed_shouldReturn207() {
        List<MmfProfitDistributionItem> items = List.of(new MmfProfitDistributionItem(), new MmfProfitDistributionItem());
        when(mmfService.createProfitDistributions(items)).thenReturn(List.of(
                new MmfProfitDistributionResult().status(StatusEnum.FAILED).errorCode("MMF047"),
                new MmfProfitDistributionResult().status(StatusEnum.CREATED).sweepNo(2L)));

        ResponseEntity<GenericResponse> response = controller.createMmfProfitDistributions(items);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MULTI_STATUS);
        assertThat(response.getBody().getResponseCode()).isEqualTo("207");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Multi-Status");
        assertThat(response.getBody().getErrorCode()).isNull();
    }

    @Test
    void listMmfCustodianCash_shouldWrapRows() {
        List<MmfCustodianCash> rows = List.of(new MmfCustodianCash().clientId("2001214"));
        when(mmfService.listCustodianCash(List.of("2001214"))).thenReturn(rows);

        ResponseEntity<GenericResponse> response = controller.listMmfCustodianCash(List.of("2001214"));

        assertOk(response);
        assertThat(response.getBody().getResponse()).isEqualTo(new MmfCustodianCashListResponse().custodianCash(rows));
    }

    @Test
    void listMmfCustodianPositions_emptyShouldStill200() {
        when(mmfService.listCustodianPositions(null)).thenReturn(List.of());

        ResponseEntity<GenericResponse> response = controller.listMmfCustodianPositions(null);

        assertOk(response);
        assertThat(((MmfCustodianPositionListResponse) response.getBody().getResponse()).getCustodianPositions()).isEmpty();
    }

    @Test
    void response_shouldCarryMdcCorrelationId() {
        MDCUtil.putCorrelationId("6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33");
        when(mmfService.listCustodianPositions(null)).thenReturn(List.of());

        assertThat(controller.listMmfCustodianPositions(null).getBody().getCorrelationId())
                .hasToString("6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33");
    }

    private static void assertOk(ResponseEntity<GenericResponse> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("OK");
        assertThat(response.getBody().getErrorCode()).isNull();
        assertThat(response.getBody().getErrorMsg()).isNull();
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }
}
