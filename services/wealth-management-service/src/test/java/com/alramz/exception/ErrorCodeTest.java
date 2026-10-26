package com.alramz.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alramz.exception.ErrorCode.Kind.ANY;
import static com.alramz.exception.ErrorCode.Kind.MISSING;
import static com.alramz.exception.ErrorCode.Kind.SIZE;
import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource({
            "getMmfAccount, accountNumber, ANY, MMF001",
            "getMmfAccount, book, MISSING, MMF002",
            "getMmfAccount, book, ANY, MMF003",
            "listMmfSweepIns, fromDate, ANY, MMF011",
            "listMmfSweepIns, toDate, ANY, MMF011",
            "listMmfSweepOuts, fromDate, ANY, MMF021",
            "listMmfSweepOuts, toDate, ANY, MMF021",
            "confirmMmfSweepIns, body, ANY, MMF030",
            "confirmMmfSweepIns, body, MISSING, MMF030",
            "confirmMmfSweepIns, body, SIZE, MMF030",
            "confirmMmfSweepIns, sweepNo, MISSING, MMF031",
            "confirmMmfSweepIns, source, ANY, MMF031",
            "confirmMmfSweepIns, source, MISSING, MMF031",
            "confirmMmfSweepIns, sweepNo, ANY, MMF032",
            "createMmfProfitDistributions, body, ANY, MMF040",
            "createMmfProfitDistributions, body, MISSING, MMF040",
            "createMmfProfitDistributions, sweepDate, ANY, MMF041",
            "createMmfProfitDistributions, amount, ANY, MMF041",
            "createMmfProfitDistributions, referenceId, MISSING, MMF041",
            "createMmfProfitDistributions, accountNumber, ANY, MMF042",
            "createMmfProfitDistributions, source, MISSING, MMF043",
            "createMmfProfitDistributions, source, ANY, MMF043",
            "listMmfCustodianCash, clientId, MISSING, MMF050",
            "listMmfCustodianCash, clientId, SIZE, MMF052",
            "listMmfCustodianCash, clientId, ANY, MMF051",
            "listMmfCustodianPositions, asOfDate, ANY, MMF060",
            // unknown op / field / body on a GET -> fallback
            "unknownOperation, accountNumber, ANY, MMF090",
            "getMmfAccount, nonsense, ANY, MMF090",
            "listMmfSweepIns, body, MISSING, MMF090"
    })
    void forInput_shouldResolveOperationSpecificCode(String op, String input, ErrorCode.Kind kind, String expected) {
        assertThat(ErrorCode.forInput(op, input, kind).code()).isEqualTo(expected);
    }

    @Test
    void forInput_sameFieldResolvesDifferentlyPerOperation() {
        assertThat(ErrorCode.forInput("getMmfAccount", "accountNumber", ANY).code()).isEqualTo("MMF001");
        assertThat(ErrorCode.forInput("createMmfProfitDistributions", "accountNumber", ANY).code()).isEqualTo("MMF042");
        assertThat(ErrorCode.forInput("confirmMmfSweepIns", "source", ANY).code()).isEqualTo("MMF031");
        assertThat(ErrorCode.forInput("createMmfProfitDistributions", "source", ANY).code()).isEqualTo("MMF043");
    }

    @Test
    void forInput_shouldHandleNulls() {
        assertThat(ErrorCode.forInput(null, "book", MISSING)).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(ErrorCode.forInput("getMmfAccount", null, ANY)).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @ParameterizedTest(name = "{0} -> {1} / {2}")
    @CsvSource({
            "getMmfAccount, MMF005, MMF006",
            "listMmfSweepIns, MMF015, MMF016",
            "listMmfSweepOuts, MMF025, MMF026",
            "confirmMmfSweepIns, MMF035, MMF036",
            "createMmfProfitDistributions, MMF045, MMF046",
            "listMmfCustodianCash, MMF055, MMF056",
            "listMmfCustodianPositions, MMF066, MMF067",
            "unknownOperation, MMF090, MMF090"
    })
    void internalAndUnavailable_shouldResolvePerOperation(String op, String internal, String unavailable) {
        assertThat(ErrorCode.internalFor(op).code()).isEqualTo(internal);
        assertThat(ErrorCode.unavailableFor(op).code()).isEqualTo(unavailable);
    }

    @Test
    void internalAndUnavailable_shouldFallBackForNullOperation() {
        assertThat(ErrorCode.internalFor(null)).isEqualTo(ErrorCode.INVALID_REQUEST);
        assertThat(ErrorCode.unavailableFor(null)).isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void inputMessage_shouldMatchSpecExampleTexts() {
        assertThat(ErrorCode.inputMessage("getMmfAccount", "accountNumber", ANY)).isEqualTo("accountNumber must be 1-9 digits");
        assertThat(ErrorCode.inputMessage("getMmfAccount", "book", MISSING)).isEqualTo("book is required");
        assertThat(ErrorCode.inputMessage("getMmfAccount", "book", ANY)).isEqualTo("book must be CAPITAL or MMF");
        assertThat(ErrorCode.inputMessage("listMmfSweepIns", "fromDate", ANY)).isEqualTo("fromDate must be an ISO-8601 date (yyyy-MM-dd)");
        assertThat(ErrorCode.inputMessage("listMmfSweepOuts", "toDate", ANY)).isEqualTo("toDate must be an ISO-8601 date (yyyy-MM-dd)");
        assertThat(ErrorCode.inputMessage("confirmMmfSweepIns", "body", SIZE)).isEqualTo("Request must contain 1-1000 items");
        assertThat(ErrorCode.inputMessage("confirmMmfSweepIns", "source", ANY)).isEqualTo("source must be CAPITAL or MMF");
        assertThat(ErrorCode.inputMessage("confirmMmfSweepIns", "sweepNo", ANY)).isEqualTo("sweepNo must be a positive integer");
        assertThat(ErrorCode.inputMessage("createMmfProfitDistributions", "amount", ANY)).isEqualTo("amount must be > 0 with at most 3 decimals");
        assertThat(ErrorCode.inputMessage("createMmfProfitDistributions", "accountNumber", ANY)).isEqualTo("accountNumber must be 1-9 digits");
        assertThat(ErrorCode.inputMessage("listMmfCustodianCash", "clientId", MISSING)).isEqualTo("At least one clientId is required");
        assertThat(ErrorCode.inputMessage("listMmfCustodianCash", "clientId", SIZE)).isEqualTo("At most 500 clientId values are allowed");
        assertThat(ErrorCode.inputMessage("listMmfCustodianPositions", "asOfDate", ANY)).isEqualTo("asOfDate must be an ISO-8601 date not in the future");
        assertThat(ErrorCode.inputMessage("nope", "nope", ANY)).isEqualTo("Invalid request");
    }

    @Test
    void codes_shouldBeUniqueAndPrefixed() {
        Set<String> codes = Arrays.stream(ErrorCode.values()).map(ErrorCode::code).collect(Collectors.toSet());
        assertThat(codes).hasSize(ErrorCode.values().length);
        assertThat(codes).allMatch(c -> c.matches("MMF\\d{3}"));
        assertThat(ErrorCode.values()).allSatisfy(c -> assertThat(c.defaultMessage()).isNotBlank());
    }
}
