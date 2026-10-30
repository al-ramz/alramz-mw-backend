package com.alramz.controllers;

import com.alramz.api.SmsApi;
import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.GenericResponse;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsReportResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.service.SmsService;
import com.alramz.service.impl.WaselneySmsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("PMD.MethodArgumentCouldBeFinal")
public class SmsController implements SmsApi {

    // Also stops query-string injection into the provider report URL.
    private static final Pattern MESSAGE_ID = Pattern.compile("^[A-Za-z0-9-]{1,65}$");

    private final SmsService smsService;
    private final WaselneySmsClient waselneySmsClient;
    private final SmsProperties smsProperties;

    // No @Loggable on either method: it would log the message body and full numbers.
    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> sendSms(final SmsSendRequest smsSendRequest) {
        final SmsService.Outcome outcome = smsService.sendWithOutcome(smsSendRequest);
        if (outcome.result().getResults().stream().noneMatch(r -> r.getStatus() == SmsRecipientResult.StatusEnum.SENT)) {
            throw outcome.anyProviderRejected()
                    ? new SmsException("SMS009", "SMS provider rejected every recipient")
                    : new SmsException("SMS010", "SMS provider unavailable for every recipient");
        }
        return ok(outcome.result());
    }

    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> getSmsReport(final String messageId) {
        if (messageId == null || !MESSAGE_ID.matcher(messageId).matches()) {
            throw new SmsException("SMS007", "Invalid message id");
        }
        if (!smsProperties.liveSendEnabled()) {
            throw new SmsException("SMS008", "No SMS record found"); // simulated sends have no provider record
        }
        final String correlationId = MDCUtil.getCorrelationId();
        return ok(new SmsReportResult()
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .messageId(messageId)
                .reports(waselneySmsClient.report(messageId)));
    }

    private static ResponseEntity<GenericResponse> ok(final Object result) {
        return ResponseEntity.ok(new GenericResponse().responseCode("200").responseMessage("OK").response(result));
    }
}
