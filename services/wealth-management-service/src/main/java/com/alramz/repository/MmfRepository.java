package com.alramz.repository;

import com.alramz.model.MmfAccount;
import com.alramz.model.MmfBook;
import com.alramz.model.MmfCustodianCash;
import com.alramz.model.MmfCustodianPosition;
import com.alramz.model.MmfProfitDistributionItem;
import com.alramz.model.MmfSweep;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Oracle INSIGHT access for both books. DataAccessExceptions propagate (the service/handler classify them). */
public interface MmfRepository {

    Optional<MmfAccount> findAccount(MmfBook book, String accountNumber);

    /** Both dates null means today. */
    List<MmfSweep> findSweeps(MmfBook book, String sweepType, LocalDate fromDate, LocalDate toDateExclusive);

    /** SWEEP_NO to SWEEP_TYPE, locked FOR UPDATE; must run inside that book's transaction. */
    Map<Long, String> lockSweepTypes(MmfBook book, Collection<Long> sweepNos);

    int confirmSweepIns(MmfBook book, Collection<Long> sweepNos);

    long nextSweepNo(MmfBook book);

    void insertProfitDistribution(MmfBook book, long sweepNo, MmfProfitDistributionItem item);

    /** CAPITAL only. */
    List<MmfCustodianCash> findCustodianCash(Collection<String> clientIds);

    /** CAPITAL only; a null asOfDate means calendar yesterday (SQL NVL). */
    List<MmfCustodianPosition> findCustodianPositions(Collection<String> clientIds, LocalDate asOfDate);
}
