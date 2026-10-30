package com.alramz.controllers;

import com.alramz.api.TemplatedEmailApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.util.LoggingHelper;
import com.alramz.model.EmailSendResult;
import com.alramz.model.GenericResponse;
import com.alramz.model.TemplatedEmailRequest;
import com.alramz.service.TemplatedEmailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("PMD.MethodArgumentCouldBeFinal")
public class TemplatedEmailController implements TemplatedEmailApi {

    private final TemplatedEmailService templatedEmailService;
    private final HttpServletRequest httpServletRequest;

    // No @Loggable: it would log recipients and placeholder values.
    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> sendTemplatedEmail(final TemplatedEmailRequest templatedEmailRequest) {
        final String correlationId = templatedEmailService.send(templatedEmailRequest,
                LoggingHelper.extractClientIp(httpServletRequest));
        return ResponseEntity.ok(new GenericResponse()
                .responseCode("200")
                .responseMessage("OK")
                .response(new EmailSendResult().correlationId(correlationId)));
    }
}
