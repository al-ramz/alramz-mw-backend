package com.alramz.controllers;

import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.DfmInvestorRegistration;
import com.alramz.model.DfmInvestorRegistrationRequest;
import com.alramz.model.GenericResponse;
import com.alramz.service.DfmInvestorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DfmInvestorControllerTest {

    @Mock
    private DfmInvestorService dfmInvestorService;

    @Test
    void registerDfmInvestor_shouldReturn201WithTheRegistration() {
        DfmInvestorController controller = new DfmInvestorController(dfmInvestorService);
        DfmInvestorRegistrationRequest request = new DfmInvestorRegistrationRequest();
        DfmInvestorRegistration registration = new DfmInvestorRegistration().nin("EGA0000000");
        when(dfmInvestorService.register(request)).thenReturn(registration);

        ResponseEntity<GenericResponse> response = controller.registerDfmInvestor(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getResponseCode()).isEqualTo("201");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Created");
        assertThat(response.getBody().getResponse()).isSameAs(registration);
        assertThat(response.getBody().getErrorCode()).isNull();
    }

    @Test
    void registerDfmInvestor_shouldBeSecuredWithTheDedicatedRoleAndNotLogArgs() throws Exception {
        Method method = DfmInvestorController.class.getMethod("registerDfmInvestor", DfmInvestorRegistrationRequest.class);

        assertThat(method.getAnnotation(JwtSecured.class).roles()).containsExactly("APP_DATA_VALIDATION");
        assertThat(method.getAnnotation(Loggable.class).logArgs()).isFalse();
        assertThat(DfmInvestorController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1");
    }
}
