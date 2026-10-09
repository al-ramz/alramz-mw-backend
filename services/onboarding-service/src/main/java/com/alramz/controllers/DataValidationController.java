package com.alramz.controllers;

import com.alramz.api.DfmOnboardingApi;
import com.alramz.api.ExistingDataApi;
import com.alramz.api.IbanApi;
import com.alramz.api.VeriPhoneApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.aspect.Loggable;
import com.alramz.model.OnboardingRequest;
import com.alramz.model.OnboardingResponse;
import com.alramz.service.OnboardingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alramz.model.GenericResponse;
import com.alramz.model.IBANRequest;
import com.alramz.model.PhoneRequest;
import com.alramz.model.ValidationRequest;
import com.alramz.service.IBANValidationService;
import com.alramz.service.PhoneValidationService;
import com.alramz.service.ValidationService;
import com.alramz.exception.ApplicationException;
import com.alramz.exception.ExternalSystemException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DataValidationController implements IbanApi, VeriPhoneApi, ExistingDataApi {

    private final IBANValidationService ibanValidationService;
    private final PhoneValidationService phoneValidationService;
    private final ValidationService validationService;

    @Override
    @JwtSecured(roles = "APP_DATA_VALIDATION")
    @Loggable
    public ResponseEntity<GenericResponse> validateIBAN(IBANRequest ibANRequest) {
        return ResponseEntity.ok(ibanValidationService.validate(ibANRequest));
    }

    @Override
    @JwtSecured(roles = "APP_DATA_VALIDATION")
    @Loggable
    public ResponseEntity<GenericResponse> verifyPhone(PhoneRequest phoneRequest) {
        return ResponseEntity.ok(phoneValidationService.validate(phoneRequest));
    }

    @Override
    @JwtSecured(roles = "APP_DATA_VALIDATION")
    @Loggable
    public ResponseEntity<GenericResponse> validateExistingData(ValidationRequest validationRequest) {
        try {
            return ResponseEntity.ok(validationService.validate(validationRequest));
        } catch (ApplicationException ex) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String message = ex.getField() != null ? ex.getField() + ": " + ex.getMessage() : ex.getMessage();
            GenericResponse response = new GenericResponse();
            response.setResponseCode("400");
            response.setResponseMessage(message);
            response.setResponse(null);
            response.setCorrelationId(correlationId(request));
            return ResponseEntity.badRequest().body(response);
        } catch (ExternalSystemException ex) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            GenericResponse response = new GenericResponse();
            response.setResponseCode("503");
            response.setResponseMessage(ex.getMessage());
            response.setResponse(null);
            response.setCorrelationId(correlationId(request));
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }

    private UUID correlationId(HttpServletRequest request) {
        String correlationId = request.getHeader("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getParameter("correlationId");
        }
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        return UUID.fromString(correlationId);
    }

}
