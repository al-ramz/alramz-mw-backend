package com.alramz.service;

import com.alramz.model.CommissionsByRelationshipManagerRequest;
import com.alramz.model.RelationshipManagerCommissionsSummary;
import com.alramz.model.RelationshipManagerListResult;

public interface RelationshipManagerService {

    RelationshipManagerListResult getAllRelationshipManagers();

    RelationshipManagerCommissionsSummary getCommissionsByRelationshipManager(
            CommissionsByRelationshipManagerRequest request);
}
