package com.alramz.repository;

import com.alramz.model.CommissionResult;
import com.alramz.model.RelationshipManager;

import java.time.LocalDate;
import java.util.List;

public interface RelationshipManagerRepository {

    List<RelationshipManager> findActiveRelationshipManagers();

    List<CommissionResult> findCommissionsByRelationshipManager(
            LocalDate startDate, LocalDate endDate, List<String> relationshipManagerCodes);
}
