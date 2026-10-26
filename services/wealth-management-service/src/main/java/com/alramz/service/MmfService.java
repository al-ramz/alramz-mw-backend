package com.alramz.service;

import com.alramz.model.MmfAccount;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCash;
import com.alramz.model.MmfCustodianPosition;
import com.alramz.model.MmfProfitDistributionItem;
import com.alramz.model.MmfProfitDistributionResult;
import com.alramz.model.MmfSweep;
import com.alramz.model.MmfSweepConfirmationItem;
import com.alramz.model.MmfSweepConfirmationResult;

import java.time.LocalDate;
import java.util.List;

/** MMF / Athena sweep and custodian operations across the CAPITAL and MMF books (both on brok). */
public interface MmfService {

    String SWEEP_IN = "W";
    String SWEEP_OUT = "O";

    MmfAccount getAccount(String accountNumber, MmfBook book);

    /** {@code sweepType} is "W" or "O"; both dates null means today. */
    List<MmfSweep> listSweeps(String sweepType, LocalDate fromDate, LocalDate toDate);

    MmfSweepConfirmationResult confirmSweepIns(List<MmfSweepConfirmationItem> items);

    /** Throws for the 409/500/503 aggregate outcomes; otherwise returns per-item results in request order. */
    List<MmfProfitDistributionResult> createProfitDistributions(List<MmfProfitDistributionItem> items);

    List<MmfCustodianCash> listCustodianCash(List<String> clientIds);

    /** A null {@code asOfDate} means the legacy default, calendar yesterday. */
    List<MmfCustodianPosition> listCustodianPositions(LocalDate asOfDate);
}
