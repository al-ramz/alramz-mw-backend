package com.alramz.controllers;

import com.alramz.api.EmailApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailRequest;
import com.alramz.model.EmailSendResult;
import com.alramz.model.GenericResponse;
import com.alramz.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("PMD.MethodArgumentCouldBeFinal")
public class EmailController implements EmailApi {

    private final EmailService emailService;

    // No @Loggable: it would log EmailRequest.toString(), which includes the body and recipient addresses.
    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> sendEmail(final EmailRequest emailRequest) {
        emailService.sendEmail(emailRequest);
        final String correlationId = MDCUtil.getCorrelationId();
        return ResponseEntity.ok(new GenericResponse()
                .responseCode("200")
                .responseMessage("OK")
                .response(new EmailSendResult().correlationId(
                        correlationId != null ? correlationId : UUID.randomUUID().toString())));
    }
}
