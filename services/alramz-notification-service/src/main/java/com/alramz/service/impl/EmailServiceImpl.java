package com.alramz.service.impl;

import com.alramz.config.EmailProperties;
import com.alramz.exception.EmailConfigurationException;
import com.alramz.exception.EmailServiceException;
import com.alramz.exception.EmailValidationException;
import com.alramz.model.EmailAttachment;
import com.alramz.model.EmailRequest;
import com.alramz.model.EmailSendResponse;
import com.alramz.service.EmailService;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.UserSendMailParameterSet;
import com.microsoft.graph.requests.AttachmentCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@SuppressWarnings({"PMD.TooManyMethods", "PMD.MethodArgumentCouldBeFinal"})
public class EmailServiceImpl implements EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailServiceImpl.class);
    private static final String PROVIDER = "MICROSOFT_GRAPH";

    private final EmailProperties emailProperties;
    private final org.springframework.beans.factory.ObjectProvider<GraphServiceClient<?>> graphClientProvider;
    private final Tracer tracer;

    public EmailServiceImpl(
            final EmailProperties emailProperties,
            @Qualifier("graphServiceClient") final org.springframework.beans.factory.ObjectProvider<GraphServiceClient<?>> graphClientProvider,
            final Tracer tracer
    ) {
        this.emailProperties = emailProperties;
        this.graphClientProvider = graphClientProvider;
        this.tracer = tracer;
    }

    @Override
    public EmailSendResponse sendEmail(final EmailRequest request) {
        validateRequest(request);

        final UUID correlationId = UUID.randomUUID();
        final UUID traceId = UUID.randomUUID();

        final GraphServiceClient<?> graphClient = graphClientProvider.getIfAvailable(); // NOPMD LawOfDemeter
        if (graphClient == null) {
            throw new EmailConfigurationException("Preferred email service provider not configured");
        }

        return sendEmailWithGraphClient(request, correlationId, traceId, graphClient);
    }

    private EmailSendResponse sendEmailWithGraphClient(final EmailRequest request, final UUID correlationId,
                                                        final UUID traceId, final GraphServiceClient<?> graphClient) {
        io.micrometer.tracing.Span span = tracer.nextSpan()
                .name("graph.send_mail")
                .tag("messaging.system", "microsoft-graph")
                .start();
        try {
            final Message message = buildGraphMessage(request);
            graphClient.users(request.getFrom()) // NOPMD LawOfDemeter
                    .sendMail(UserSendMailParameterSet.newBuilder()
                            .withMessage(message)
                            .withSaveToSentItems(false)
                            .build())
                    .buildRequest()
                    .post();

            final EmailSendResponse response = new EmailSendResponse();
            response.setCorrelationID(correlationId);
            response.setStatus("SUCCESS");
            response.setResponseCode("200");
            response.setResponseMessage("OK");
            response.setProvider(PROVIDER);
            response.setSentTo(request.getTo());
            response.setSentAt(java.time.OffsetDateTime.now());
            response.setTraceId(traceId);
            span.tag("messaging.status", "success");
            return response;

        } catch (Exception ex) { // NOPMD AvoidCatchingGenericException
            span.tag("messaging.status", "failure");
            span.error(ex);
            logEmailError(ex);
            throw new EmailServiceException(503, "Service Unavailable", ex);
        } finally {
            span.end();
        }
    }

    private void validateRequest(EmailRequest request) {
        validateRequiredFields(request);
        validateEmailFormats(request);
        validateFieldLengths(request);
        validateAttachments(request);
    }

    private void validateRequiredFields(final EmailRequest request) {
        if (ObjectUtils.isEmpty(request.getTo())) {
            throw new EmailValidationException("400", "Missing or invalid required field: to");
        }
        if (ObjectUtils.isEmpty(request.getSubject())) {
            throw new EmailValidationException("400", "Missing or invalid required field: subject");
        }
        if (ObjectUtils.isEmpty(request.getFrom())) {
            throw new EmailValidationException("400", "Missing or invalid required field: from");
        }
        if (ObjectUtils.isEmpty(request.getBody())) {
            throw new EmailValidationException("400", "Missing or invalid required field: body");
        }
    }

    private void validateEmailFormats(final EmailRequest request) {
        if (!isValidEmail(request.getTo())) {
            throw new EmailValidationException("400", "Invalid Email Format");
        }
        if (!isValidEmail(request.getFrom())) {
            throw new EmailValidationException("400", "Invalid Email Format");
        }
    }

    private void validateFieldLengths(final EmailRequest request) {
        if (request.getSubject() != null && request.getSubject().length() > emailProperties.maxSubjectLength()) {
            throw new EmailValidationException("400", "Invalid Subject Length");
        }
        if (request.getBody() != null && request.getBody().length() > emailProperties.maxBodyLength()) {
            throw new EmailValidationException("400", "Invalid Body Length");
        }
    }

    private void validateAttachments(final EmailRequest request) {
        final List<EmailAttachment> attachments = request.getAttachments();
        if (!attachments.isEmpty()) {
            if (attachments.size() > emailProperties.maxAttachments()) {
                throw new EmailValidationException("400", "Invalid Attachment Count");
            }
            for (final EmailAttachment attachment : attachments) {
                final long sizeBytes = attachment.getContent().length;
                if (sizeBytes > emailProperties.maxAttachmentSizeBytes()) {
                    throw new EmailValidationException("400", "Invalid Attachment Size");
                }
            }
        }
    }

    private Message buildGraphMessage(final EmailRequest request) {
        final Message message = new Message();
        message.subject = request.getSubject();

        final ItemBody body = new ItemBody();
        body.contentType = BodyType.HTML; // NOPMD LawOfDemeter
        body.content = request.getBody();
        message.body = body; // NOPMD LawOfDemeter

        final Recipient toRecipient = new Recipient();
        final EmailAddress toAddress = new EmailAddress();
        toAddress.address = request.getTo();
        toRecipient.emailAddress = toAddress; // NOPMD LawOfDemeter
        message.toRecipients = List.of(toRecipient); // NOPMD LawOfDemeter

        final List<EmailAttachment> attachments = request.getAttachments();
        if (!attachments.isEmpty()) {
            final List<com.microsoft.graph.models.Attachment> graphAttachments = new ArrayList<>();
            for (final EmailAttachment attachment : attachments) {
                final FileAttachment fileAttachment = new FileAttachment();
                fileAttachment.name = attachment.getName();
                fileAttachment.contentBytes = attachment.getContent(); // NOPMD LawOfDemeter
                fileAttachment.size = attachment.getContent().length;
                graphAttachments.add(fileAttachment);
            }
            message.attachments = new AttachmentCollectionPage(graphAttachments, null); // NOPMD LawOfDemeter
        }

        return message;
    }

    private boolean isValidEmail(final String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        final String regex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(regex);
    }

    private void logEmailError(final Exception ex) {
        if (ex instanceof com.microsoft.graph.http.GraphServiceException gse) {
            final com.microsoft.graph.http.GraphError serviceError = gse.getServiceError();
            if (LOG.isErrorEnabled()) {
                LOG.error("Graph Error {} - Code: {}, Message: {}", gse.getResponseCode(),
                    serviceError != null ? serviceError.code : "N/A",
                    serviceError != null ? serviceError.message : "N/A", gse);
            }
        } else {
            if (LOG.isErrorEnabled()) {
                LOG.error("Email send failed", ex);
            }
        }
    }
}
