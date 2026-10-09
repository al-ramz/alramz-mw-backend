package com.alramz.controllers;

import com.alramz.model.CommissionsByRelationshipManagerRequest;
import com.alramz.model.GenericResponse;
import com.alramz.model.RelationshipManagerCommissionsSummary;
import com.alramz.model.RelationshipManagerListResult;
import com.alramz.service.RelationshipManagerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipManagerControllerTest {

    @Mock
    private RelationshipManagerService relationshipManagerService;

    @Test
    void getRelationshipManagers_shouldReturnOkResponseWrappingServiceResult() {
        RelationshipManagerController controller = new RelationshipManagerController(relationshipManagerService);

        RelationshipManagerListResult expected = new RelationshipManagerListResult();
        when(relationshipManagerService.getAllRelationshipManagers()).thenReturn(expected);

        ResponseEntity<GenericResponse> response = controller.getRelationshipManagers();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("OK");
        assertThat(response.getBody().getResponse()).isSameAs(expected);
        verify(relationshipManagerService).getAllRelationshipManagers();
    }

    @Test
    void getCommissionsByRelationshipManager_shouldReturnOkResponseWrappingServiceResult() {
        RelationshipManagerController controller = new RelationshipManagerController(relationshipManagerService);

        CommissionsByRelationshipManagerRequest request = new CommissionsByRelationshipManagerRequest();
        RelationshipManagerCommissionsSummary expected = new RelationshipManagerCommissionsSummary();
        when(relationshipManagerService.getCommissionsByRelationshipManager(any(CommissionsByRelationshipManagerRequest.class)))
                .thenReturn(expected);

        ResponseEntity<GenericResponse> response = controller.getCommissionsByRelationshipManager(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getResponseCode()).isEqualTo("200");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("OK");
        assertThat(response.getBody().getResponse()).isSameAs(expected);
        verify(relationshipManagerService).getCommissionsByRelationshipManager(request);
    }
}
