package com.alramz.controllers;

import com.alramz.api.OtpApi;
import com.alramz.jwt.annotation.JwtSecured;
import com.alramz.model.GenericResponse;
import com.alramz.model.OtpSendRequest;
import com.alramz.model.OtpValidateRequest;
import com.alramz.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@SuppressWarnings("PMD.MethodArgumentCouldBeFinal")
public class OtpController implements OtpApi {

    private final OtpService otpService;

    // No @Loggable on any method: the bodies carry recipients and the OTP hash.
    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> sendOtp(final OtpSendRequest otpSendRequest) {
        return ok(otpService.send(otpSendRequest));
    }

    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> validateOtp(final OtpValidateRequest otpValidateRequest) {
        return ok(otpService.validate(otpValidateRequest));
    }

    @Override
    @JwtSecured(roles = "APP_NOTIFICATION")
    public ResponseEntity<GenericResponse> getOtpConfigurations() {
        return ok(otpService.configurations());
    }

    private static ResponseEntity<GenericResponse> ok(final Object result) {
        return ResponseEntity.ok(new GenericResponse().responseCode("200").responseMessage("OK").response(result));
    }
}
