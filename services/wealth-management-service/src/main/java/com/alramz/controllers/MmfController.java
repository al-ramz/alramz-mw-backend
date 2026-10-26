package com.alramz.controllers;

import com.alramz.api.MmfApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.model.MmfAccountResponse;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCashListResponse;
import com.alramz.model.MmfCustodianPositionListResponse;
import com.alramz.model.MmfProfitDistributionItem;
import com.alramz.model.MmfProfitDistributionResponse;
import com.alramz.model.MmfProfitDistributionResult;
import com.alramz.model.MmfSweepConfirmationItem;
import com.alramz.model.MmfSweepInListResponse;
import com.alramz.model.MmfSweepOutListResponse;
import com.alramz.service.MmfService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "company.datasource", name = "brok.enabled", havingValue = "true")
public class MmfController implements MmfApi {

    private static final String ROLE = "APP_WEALTH_MANAGEMENT_SERVICE_USER";

    private final MmfService mmfService;

    // Explicit rather than Lombok @RequiredArgsConstructor: the IDE's ECJ build of the Lombok-generated
    // constructor fails ("Flexible Constructor Bodies") and overwrites javac's class in target/classes.
    public MmfController(MmfService mmfService) {
        this.mmfService = mmfService;
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> getMmfAccount(String accountNumber, MmfBook book) {
        return ok(new MmfAccountResponse().account(mmfService.getAccount(accountNumber, book)));
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> listMmfSweepIns(LocalDate fromDate, LocalDate toDate) {
        return ok(new MmfSweepInListResponse().sweepIns(mmfService.listSweeps(MmfService.SWEEP_IN, fromDate, toDate)));
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> listMmfSweepOuts(LocalDate fromDate, LocalDate toDate) {
        return ok(new MmfSweepOutListResponse().sweepOuts(mmfService.listSweeps(MmfService.SWEEP_OUT, fromDate, toDate)));
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> confirmMmfSweepIns(List<MmfSweepConfirmationItem> mmfSweepConfirmationItem) {
        return ok(mmfService.confirmSweepIns(mmfSweepConfirmationItem));
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> createMmfProfitDistributions(List<MmfProfitDistributionItem> mmfProfitDistributionItem) {
        List<MmfProfitDistributionResult> results = mmfService.createProfitDistributions(mmfProfitDistributionItem);
        boolean anyFailed = results.stream()
                .anyMatch(r -> r.getStatus() == MmfProfitDistributionResult.StatusEnum.FAILED);
        return respond(anyFailed ? HttpStatus.MULTI_STATUS : HttpStatus.CREATED,
                new MmfProfitDistributionResponse().results(results));
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> listMmfCustodianCash(List<String> clientId) {
        return ok(new MmfCustodianCashListResponse().custodianCash(mmfService.listCustodianCash(clientId)));
    }

    @Override
    @JwtSecured(roles = ROLE)
    @Loggable
    public ResponseEntity<GenericResponse> listMmfCustodianPositions(LocalDate asOfDate) {
        return ok(new MmfCustodianPositionListResponse().custodianPositions(mmfService.listCustodianPositions(asOfDate)));
    }

    private static ResponseEntity<GenericResponse> ok(Object payload) {
        return respond(HttpStatus.OK, payload);
    }

    private static ResponseEntity<GenericResponse> respond(HttpStatus status, Object payload) {
        GenericResponse body = new GenericResponse();
        body.setResponseCode(String.valueOf(status.value()));
        body.setResponseMessage(status.getReasonPhrase());
        body.setResponse(payload);
        body.setCorrelationId(MDCUtil.getCorrelationIdAsUuid());
        return ResponseEntity.status(status).body(body);
    }
}
