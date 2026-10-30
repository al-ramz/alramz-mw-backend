package com.alramz.service.impl;

import com.alramz.config.EmailProperties;
import com.alramz.config.GraphConfig;
import com.alramz.exception.EmailConfigurationException;
import com.alramz.exception.EmailServiceException;
import com.alramz.exception.EmailValidationException;
import com.alramz.jwt.context.JwtContext;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailAttachment;
import com.alramz.model.EmailRequest;
import com.alramz.service.EmailService;
import com.microsoft.graph.http.GraphError;
import com.microsoft.graph.http.GraphErrorResponse;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.models.UserSendMailParameterSet;
import com.microsoft.graph.requests.AttachmentCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Email gateway over Microsoft Graph. All validation runs here so HTTP and in-process callers get the same codes.
 * Logs never carry the body, subject, attachment content, recipient addresses or tokens.
 */
@Slf4j
@Service
@SuppressWarnings({"PMD.TooManyMethods", "PMD.MethodArgumentCouldBeFinal"})
public class EmailServiceImpl implements EmailService {

    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String RETRY_AFTER = "retry-after";
    private static final String STATUS_TAG = "messaging.status";

    private final EmailProperties emailProperties;
    private final ObjectProvider<GraphServiceClient<?>> graphClientProvider;
    private final Tracer tracer;
    private final ObjectProvider<JwtContext> jwtContextProvider;

    public EmailServiceImpl(
            final EmailProperties emailProperties,
            @Qualifier("graphServiceClient") final ObjectProvider<GraphServiceClient<?>> graphClientProvider,
            final Tracer tracer,
            final ObjectProvider<JwtContext> jwtContextProvider
    ) {
        this.emailProperties = emailProperties;
        this.graphClientProvider = graphClientProvider;
        this.tracer = tracer;
        this.jwtContextProvider = jwtContextProvider;
    }

    /** Facts about one send that are safe to log. */
    private record SendContext(String correlationId, String invoker, String from, int to, int cc, int bcc,
                               int attachments, long attachmentBytes, BodyType bodyType) {
    }

    @Override
    public void sendEmail(final EmailRequest request) {
        final String correlationId = correlationId();
        final String invoker = invoker();
        final Message message;
        final SendContext ctx;
        try {
            final String from = strip(request.getFrom());
            final List<String> to = split(request.getTo());
            final List<String> cc = split(request.getCc());
            final List<String> bcc = split(request.getBcc());
            final List<EmailAttachment> attachments = request.getAttachments() == null
                    ? List.of() : request.getAttachments();

            final long totalBytes = validate(request, from, to, cc, bcc, attachments);

            message = buildMessage(request, to, cc, bcc, attachments);
            ctx = new SendContext(correlationId, invoker, from, to.size(), cc.size(), bcc.size(),
                    attachments.size(), totalBytes, message.body.contentType); // NOPMD LawOfDemeter
        } catch (EmailValidationException ex) {
            log.warn("Email rejected code={} correlationId={} invoker={}", ex.getCode(), correlationId, invoker);
            throw ex;
        }

        final GraphServiceClient<?> graphClient = graphClientProvider.getIfAvailable(); // NOPMD LawOfDemeter
        if (graphClient == null) {
            throw new EmailConfigurationException("Preferred email service provider not configured");
        }
        send(graphClient, message, ctx);
    }

    private void send(final GraphServiceClient<?> graphClient, final Message message, final SendContext ctx) {
        final UserSendMailParameterSet params = UserSendMailParameterSet.newBuilder()
                .withMessage(message)
                .withSaveToSentItems(true)
                .build();
        final Span span = tracer.nextSpan()
                .name("graph.send_mail")
                .tag("messaging.system", "microsoft-graph")
                .start();
        try {
            postWithAuthRetry(graphClient, ctx.from(), params);
            span.tag(STATUS_TAG, "success");
            logOutcome(ctx, "SUCCESS", null, 202, null);
        } catch (GraphServiceException gse) {
            // Never pass gse to the logger, the span or as a cause: its message embeds the request URL and body.
            final EmailServiceException mapped = mapGraphFailure(gse, ctx);
            span.tag(STATUS_TAG, "failure");
            span.tag("mge.code", mapped.getCode());
            throw mapped;
        } catch (RuntimeException ex) { // NOPMD AvoidCatchingGenericException
            span.tag(STATUS_TAG, "failure");
            span.tag("mge.code", "MGE-B-004");
            log.error("MGE-B-004 Unhandled email gateway error correlationId={} invoker={} from={}",
                    ctx.correlationId(), ctx.invoker(), ctx.from(), ex);
            throw new EmailServiceException("MGE-B-004", "Unhandled email gateway error", ex, null);
        } finally {
            span.end();
        }
    }

    /** One retry on Graph 401: the auth provider asks the credential for a token again. */
    private void postWithAuthRetry(final GraphServiceClient<?> graphClient, final String from,
                                   final UserSendMailParameterSet params) {
        try {
            post(graphClient, from, params);
        } catch (GraphServiceException first) {
            if (first.getResponseCode() != 401) {
                throw first;
            }
            log.warn("Graph returned 401, retrying once correlationId={}", correlationId());
            post(graphClient, from, params);
        }
    }

    private void post(final GraphServiceClient<?> graphClient, final String from, final UserSendMailParameterSet params) {
        graphClient.users(from).sendMail(params).buildRequest().post(); // NOPMD LawOfDemeter
    }

    private EmailServiceException mapGraphFailure(final GraphServiceException gse, final SendContext ctx) {
        final int status = gse.getResponseCode();
        // getServiceError() throws NPE when the SDK has no parsed error body, so read getError() first.
        final GraphErrorResponse errorResponse = gse.getError();
        final GraphError serviceError = errorResponse != null ? errorResponse.error : null;
        final String graphCode = serviceError != null ? serviceError.code : null;
        if (status == 401) {
            logOutcome(ctx, "FAILURE", "MGE-B-001", status, graphCode);
            return new EmailServiceException("MGE-B-001", "Graph rejected token as unauthorized");
        }
        if (status == 429) {
            final String retryAfter = retryAfter(gse.getResponseHeaders());
            logOutcome(ctx, "THROTTLED", "MGE-B-002", status, graphCode);
            log.warn("MGE-B-002 Graph throttled; Retry-After={} correlationId={}", retryAfter, ctx.correlationId());
            return new EmailServiceException("MGE-B-002", "Graph throttled; Retry-After=" + retryAfter, null, retryAfter);
        }
        logOutcome(ctx, "FAILURE", "MGE-B-003", status, graphCode);
        return new EmailServiceException("MGE-B-003", "Graph send failed " + status + " " + graphCode);
    }

    /** Numeric seconds from the "Name : value" response header list, or null if absent or not numeric. */
    static String retryAfter(final List<String> responseHeaders) {
        if (responseHeaders == null) {
            return null;
        }
        for (final String header : responseHeaders) {
            final int sep = header == null ? -1 : header.indexOf(':');
            if (sep > 0 && RETRY_AFTER.equalsIgnoreCase(header.substring(0, sep).trim())) {
                final Long seconds = GraphConfig.parseRetryAfterSeconds(header.substring(sep + 1));
                return seconds == null ? null : String.valueOf(seconds);
            }
        }
        return null;
    }

    private void logOutcome(final SendContext ctx, final String outcome, final String code,
                            final int graphStatus, final String graphCode) {
        final String format = "Email send outcome={} code={} correlationId={} invoker={} from={} to={} cc={} bcc={}"
                + " attachments={} attachmentBytes={} bodyType={} graphStatus={} graphError={}";
        final Object[] args = {outcome, code, ctx.correlationId(), ctx.invoker(), ctx.from(), ctx.to(), ctx.cc(),
                ctx.bcc(), ctx.attachments(), ctx.attachmentBytes(), ctx.bodyType(), graphStatus, graphCode};
        if (code == null) {
            log.info(format, args);
        } else {
            log.error(format, args);
        }
    }

    // ---------------------------------------------------------------- validation (spec 3.5 order)

    private long validate(final EmailRequest request, final String from, final List<String> to, final List<String> cc,
                          final List<String> bcc, final List<EmailAttachment> attachments) {
        final List<String> missing = new ArrayList<>();
        if (to.isEmpty()) {
            missing.add("to");
        }
        if (from.isEmpty()) {
            missing.add("from");
        }
        if (isBlank(request.getSubject())) {
            missing.add("subject");
        }
        if (isBlank(request.getBody())) {
            missing.add("body");
        }
        if (!missing.isEmpty()) {
            throw new EmailValidationException("MGE-V-001", "Request failed due to missing: " + String.join(", ", missing));
        }

        final List<String> invalid = new ArrayList<>();
        addIfInvalid(invalid, "to", to);
        addIfInvalid(invalid, "cc", cc);
        addIfInvalid(invalid, "bcc", bcc);
        addIfInvalid(invalid, "from", List.of(from));
        if (!invalid.isEmpty()) {
            throw new EmailValidationException("MGE-V-002", "Invalid email address in: " + String.join(", ", invalid));
        }

        if (request.getSubject().length() > emailProperties.maxSubjectLength()) {
            throw new EmailValidationException("MGE-V-003", "Field length exceeded: subject");
        }
        if (request.getBody().length() > emailProperties.maxBodyLength()) {
            throw new EmailValidationException("MGE-V-003", "Field length exceeded: body");
        }

        if (attachments.size() > emailProperties.maxAttachments()) {
            throw new EmailValidationException("MGE-V-004", "Number of attachments exceeds " + emailProperties.maxAttachments());
        }
        long total = 0;
        for (int i = 0; i < attachments.size(); i++) {
            final EmailAttachment attachment = attachments.get(i);
            final String prefix = "Invalid attachment at position " + (i + 1) + ": ";
            if (attachment == null || isBlank(attachment.getFileName())) {
                throw new EmailValidationException("MGE-V-005", prefix + "fileName is required");
            }
            if (attachment.getContent() == null || attachment.getContent().length == 0) {
                throw new EmailValidationException("MGE-V-005", prefix + "content is required");
            }
            total += attachment.getContent().length;
        }
        if (total > emailProperties.maxTotalAttachmentSizeBytes()) {
            throw new EmailValidationException("MGE-V-008", "Attachments size limit exceeded");
        }

        // An empty allow-list rejects every sender (fail closed).
        if (emailProperties.allowedSenders().stream().noneMatch(s -> s.strip().equalsIgnoreCase(from))) {
            throw new EmailValidationException("MGE-V-007", "Sender mailbox is not permitted");
        }
        return total;
    }

    private static void addIfInvalid(final List<String> invalid, final String field, final List<String> addresses) {
        if (addresses.stream().anyMatch(a -> !EMAIL.matcher(a).matches())) {
            invalid.add(field);
        }
    }

    // ---------------------------------------------------------------- Graph message

    private Message buildMessage(final EmailRequest request, final List<String> to, final List<String> cc,
                                 final List<String> bcc, final List<EmailAttachment> attachments) {
        final Message message = new Message();
        message.subject = request.getSubject();

        final ItemBody body = new ItemBody();
        body.contentType = request.getBody().strip().startsWith("<") ? BodyType.HTML : BodyType.TEXT; // NOPMD LawOfDemeter
        body.content = request.getBody();
        message.body = body; // NOPMD LawOfDemeter

        message.toRecipients = recipients(to); // NOPMD LawOfDemeter
        if (!cc.isEmpty()) {
            message.ccRecipients = recipients(cc); // NOPMD LawOfDemeter
        }
        if (!bcc.isEmpty()) {
            message.bccRecipients = recipients(bcc); // NOPMD LawOfDemeter
        }

        if (!attachments.isEmpty()) {
            final List<Attachment> graphAttachments = new ArrayList<>();
            for (final EmailAttachment attachment : attachments) {
                final FileAttachment fileAttachment = new FileAttachment();
                fileAttachment.oDataType = "#microsoft.graph.fileAttachment";
                fileAttachment.name = attachment.getFileName();
                fileAttachment.contentType = attachment.getContentType();
                fileAttachment.contentBytes = attachment.getContent(); // NOPMD LawOfDemeter
                graphAttachments.add(fileAttachment);
            }
            message.attachments = new AttachmentCollectionPage(graphAttachments, null); // NOPMD LawOfDemeter
        }
        return message;
    }

    private static List<Recipient> recipients(final List<String> addresses) {
        final List<Recipient> recipients = new ArrayList<>();
        for (final String address : addresses) {
            final EmailAddress emailAddress = new EmailAddress();
            emailAddress.address = address;
            final Recipient recipient = new Recipient();
            recipient.emailAddress = emailAddress; // NOPMD LawOfDemeter
            recipients.add(recipient);
        }
        return recipients;
    }

    // ---------------------------------------------------------------- helpers

    private static List<String> split(final String list) {
        if (list == null) {
            return List.of();
        }
        return Arrays.stream(list.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private static String strip(final String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static String correlationId() {
        final String id = MDCUtil.getCorrelationId();
        return id != null ? id : UUID.randomUUID().toString();
    }

    private String invoker() {
        final JwtContext jwtContext = jwtContextProvider.getIfAvailable(); // NOPMD LawOfDemeter
        final String application = jwtContext != null ? jwtContext.getCurrentApplication() : null;
        return application != null ? application : "unknown";
    }
}
