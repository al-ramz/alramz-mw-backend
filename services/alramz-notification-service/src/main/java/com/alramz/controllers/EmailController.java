package com.alramz.controllers;

import com.alramz.api.ApiApi;
import com.alramz.model.EmailRequest;
import com.alramz.model.EmailSendResponse;
import com.alramz.service.EmailService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("PMD.MethodArgumentCouldBeFinal")
public class EmailController implements ApiApi {

    private final EmailService emailService;

    @Override
    public ResponseEntity<EmailSendResponse> sendEmail(final EmailRequest emailRequest) {
        return ResponseEntity.ok(emailService.sendEmail(emailRequest));
    }
}
