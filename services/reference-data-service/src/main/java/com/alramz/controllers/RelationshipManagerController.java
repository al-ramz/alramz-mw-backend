package com.alramz.controllers;

import com.alramz.api.RelationshipManagersApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.CommissionsByRelationshipManagerRequest;
import com.alramz.model.GenericResponse;
import com.alramz.model.RelationshipManagerCommissionsSummary;
import com.alramz.model.RelationshipManagerListResult;
import com.alramz.service.RelationshipManagerService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(prefix = "company.datasource.brok", name = "enabled", havingValue = "true")
public class RelationshipManagerController implements RelationshipManagersApi {

    private final RelationshipManagerService relationshipManagerService;

    public RelationshipManagerController(RelationshipManagerService relationshipManagerService) {
        this.relationshipManagerService = relationshipManagerService;
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> getRelationshipManagers() {
        RelationshipManagerListResult result = relationshipManagerService.getAllRelationshipManagers();

        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage("OK");
        response.setResponse(result);
        return ResponseEntity.ok(response);
    }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> getCommissionsByRelationshipManager(
            CommissionsByRelationshipManagerRequest commissionsByRelationshipManagerRequest) {
        RelationshipManagerCommissionsSummary result = relationshipManagerService
                .getCommissionsByRelationshipManager(commissionsByRelationshipManagerRequest);

        GenericResponse response = new GenericResponse();
        response.setResponseCode("200");
        response.setResponseMessage("OK");
        response.setResponse(result);
        return ResponseEntity.ok(response);
    }
}
