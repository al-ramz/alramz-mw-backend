package com.alramz.service;

import com.alramz.model.EmailRequest;
import com.alramz.model.EmailSendResponse;

public interface EmailService {

    EmailSendResponse sendEmail(EmailRequest request);
}
