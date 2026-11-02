package com.alramz.controllers;

import com.alramz.exception.ApplicationException;
import com.alramz.exception.ExternalSystemException;
import com.alramz.model.GenericResponse;
import com.alramz.model.IBANRequest;
import com.alramz.model.PhoneRequest;
import com.alramz.model.ValidationRequest;
import com.alramz.service.IBANValidationService;
import com.alramz.service.PhoneValidationService;
import com.alramz.service.ValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DataValidationControllerTest {

    @Mock
    private IBANValidationService ibanValidationService;

    @Mock
    private PhoneValidationService phoneValidationService;

    @Mock
    private ValidationService validationService;

    @Test
    void validateIBAN_shouldReturnOkResponse() {
        DataValidationController controller = new DataValidationController(ibanValidationService, phoneValidationService, validationService);

        IBANRequest ibanRequest = new IBANRequest();
        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setResponseCode("200");
        genericResponse.setResponseMessage("OK");

        when(ibanValidationService.validate(any(IBANRequest.class))).thenReturn(genericResponse);

        ResponseEntity<GenericResponse> response = controller.validateIBAN(ibanRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(genericResponse);
        verify(ibanValidationService).validate(ibanRequest);
    }

    @Test
    void verifyPhone_shouldReturnOkResponse() {
        DataValidationController controller = new DataValidationController(ibanValidationService, phoneValidationService, validationService);

        PhoneRequest phoneRequest = new PhoneRequest();
        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setResponseCode("200");
        genericResponse.setResponseMessage("OK");

        when(phoneValidationService.validate(any(PhoneRequest.class))).thenReturn(genericResponse);

        ResponseEntity<GenericResponse> response = controller.verifyPhone(phoneRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(genericResponse);
        verify(phoneValidationService).validate(phoneRequest);
    }

    @Test
    void validateExistingData_shouldReturnOkResponse() {
        DataValidationController controller = new DataValidationController(ibanValidationService, phoneValidationService, validationService);

        ValidationRequest validationRequest = new ValidationRequest();
        GenericResponse genericResponse = new GenericResponse();
        genericResponse.setResponseCode("200");
        genericResponse.setResponseMessage("OK");

        when(validationService.validate(any(ValidationRequest.class))).thenReturn(genericResponse);

        ResponseEntity<GenericResponse> response = controller.validateExistingData(validationRequest);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(genericResponse);
        verify(validationService).validate(validationRequest);
    }

    private static final String CORRELATION_ID = "3f1c2b4a-5d6e-4f70-8a9b-0c1d2e3f4a5b";

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void validateExistingData_shouldReturnBadRequestWithFieldAndHeaderCorrelationIdOnApplicationException() {
        DataValidationController controller = new DataValidationController(ibanValidationService, phoneValidationService, validationService);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Correlation-Id", CORRELATION_ID);
        bindRequest(httpRequest);
        when(validationService.validate(any(ValidationRequest.class))).thenThrow(new ApplicationException("email", "Email already exists"));

        ResponseEntity<GenericResponse> response = controller.validateExistingData(new ValidationRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getResponseCode()).isEqualTo("400");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("email: Email already exists");
        assertThat(response.getBody().getResponse()).isNull();
        assertThat(response.getBody().getCorrelationId()).isEqualTo(UUID.fromString(CORRELATION_ID));
    }

    @Test
    void validateExistingData_shouldUseMessageOnlyAndQueryParamCorrelationIdWhenNoField() {
        DataValidationController controller = new DataValidationController(ibanValidationService, phoneValidationService, validationService);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.addHeader("X-Correlation-Id", " ");
        httpRequest.setParameter("correlationId", CORRELATION_ID);
        bindRequest(httpRequest);
        when(validationService.validate(any(ValidationRequest.class))).thenThrow(new ApplicationException("Invalid request"));

        ResponseEntity<GenericResponse> response = controller.validateExistingData(new ValidationRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getResponseMessage()).isEqualTo("Invalid request");
        assertThat(response.getBody().getCorrelationId()).isEqualTo(UUID.fromString(CORRELATION_ID));
    }

    @Test
    void validateExistingData_shouldReturnServiceUnavailableWithGeneratedCorrelationIdOnExternalFailure() {
        DataValidationController controller = new DataValidationController(ibanValidationService, phoneValidationService, validationService);
        bindRequest(new MockHttpServletRequest());
        when(validationService.validate(any(ValidationRequest.class))).thenThrow(new ExternalSystemException("eTrade down"));

        ResponseEntity<GenericResponse> response = controller.validateExistingData(new ValidationRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody().getResponseCode()).isEqualTo("503");
        assertThat(response.getBody().getResponseMessage()).isEqualTo("eTrade down");
        assertThat(response.getBody().getResponse()).isNull();
        assertThat(response.getBody().getCorrelationId()).isNotNull();
    }
}
