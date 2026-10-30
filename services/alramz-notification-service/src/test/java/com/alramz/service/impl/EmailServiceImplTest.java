package com.alramz.service.impl;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.config.EmailProperties;
import com.alramz.exception.EmailConfigurationException;
import com.alramz.exception.EmailServiceException;
import com.alramz.exception.EmailValidationException;
import com.alramz.jwt.context.JwtContext;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailAttachment;
import com.alramz.model.EmailRequest;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.GraphError;
import com.microsoft.graph.http.GraphErrorResponse;
import com.microsoft.graph.http.GraphServiceException;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.UserSendMailParameterSet;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.UserRequestBuilder;
import com.microsoft.graph.requests.UserSendMailRequest;
import com.microsoft.graph.requests.UserSendMailRequestBuilder;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailServiceImplTest {

    private static final String SENDER = "notification@alramz.ae";

    @Mock
    private ObjectProvider<GraphServiceClient<?>> graphClientProvider;
    @Mock
    private ObjectProvider<JwtContext> jwtContextProvider;
    @Mock
    private JwtContext jwtContext;
    @Mock
    private GraphServiceClient<?> graphClient;
    @Mock
    private UserRequestBuilder userRequestBuilder;
    @Mock
    private UserSendMailRequestBuilder sendMailRequestBuilder;
    @Mock
    private UserSendMailRequest sendMailRequest;

    private final ArgumentCaptor<UserSendMailParameterSet> params = ArgumentCaptor.forClass(UserSendMailParameterSet.class);
    private EmailServiceImpl service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        service = newService(List.of(SENDER));
        when(((ObjectProvider) graphClientProvider).getIfAvailable()).thenReturn(graphClient);
        when(jwtContextProvider.getIfAvailable()).thenReturn(jwtContext);
        when(jwtContext.getCurrentApplication()).thenReturn("feature-two");
        when(graphClient.users(anyString())).thenReturn(userRequestBuilder);
        when(userRequestBuilder.sendMail(params.capture())).thenReturn(sendMailRequestBuilder);
        when(sendMailRequestBuilder.buildRequest()).thenReturn(sendMailRequest);
        MDCUtil.putCorrelationId("cid-123");

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(EmailServiceImpl.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(EmailServiceImpl.class)).detachAppender(logs);
        MDC.clear();
    }

    private EmailServiceImpl newService(final List<String> allowedSenders) {
        return new EmailServiceImpl(new EmailProperties(255, 10_000, 10, 3_000_000, allowedSenders),
                graphClientProvider, Tracer.NOOP, jwtContextProvider);
    }

    private static EmailRequest request() {
        return new EmailRequest().to("jane.doe@example.com").from(SENDER).subject("Q3 Statement Ready").body("Hello");
    }

    private static EmailAttachment attachment(final String name, final byte[] content) {
        return new EmailAttachment().fileName(name).contentType("application/pdf").content(content);
    }

    private static GraphServiceException graphError(final int status, final Map<String, String> headers) {
        final GraphErrorResponse error = new GraphErrorResponse();
        error.error = new GraphError();
        error.error.code = "ErrorCode" + status;
        return GraphServiceException.createFromResponse("https://graph/users/x/sendMail", "POST", new ArrayList<>(),
                "{\"subject\":\"Q3 Statement Ready\"}", headers, "status " + status, status, error, false);
    }

    private Message sentMessage() {
        return params.getValue().message;
    }

    private void assertValidation(final EmailRequest request, final String code, final String message) {
        assertThatThrownBy(() -> service.sendEmail(request))
                .isInstanceOf(EmailValidationException.class)
                .hasFieldOrPropertyWithValue("code", code)
                .hasMessage(message);
        verify(graphClient, never()).users(anyString());
    }

    // ------------------------------------------------------------------ success paths

    @Test
    void sendEmail_buildsMultiRecipientMessageAndSavesToSentItems() {
        service.sendEmail(request().to("a@x.com, b@y.com").cc("manager@example.com").bcc("audit@example.com"));

        verify(graphClient).users(SENDER);
        verify(sendMailRequest).post();
        assertThat(params.getValue().saveToSentItems).isTrue();
        final Message message = sentMessage();
        assertThat(message.toRecipients).extracting(r -> r.emailAddress.address).containsExactly("a@x.com", "b@y.com");
        assertThat(message.ccRecipients).hasSize(1);
        assertThat(message.bccRecipients).hasSize(1);
        assertThat(message.subject).isEqualTo("Q3 Statement Ready");
        assertThat(message.attachments).isNull();
    }

    @Test
    void sendEmail_blankCcAndBccProduceNoRecipients() {
        service.sendEmail(request().cc("  ").bcc(null));

        assertThat(sentMessage().ccRecipients).isNull();
        assertThat(sentMessage().bccRecipients).isNull();
    }

    @Test
    void sendEmail_plainBodyIsText() {
        service.sendEmail(request().body("Hello"));

        assertThat(sentMessage().body.contentType).isEqualTo(BodyType.TEXT);
        assertThat(sentMessage().body.content).isEqualTo("Hello");
    }

    @Test
    void sendEmail_bodyStartingWithTagIsHtml() {
        service.sendEmail(request().body("  <p>Hi</p>"));

        assertThat(sentMessage().body.contentType).isEqualTo(BodyType.HTML);
    }

    @Test
    void sendEmail_mapsAttachmentsToFileAttachments() {
        final byte[] pdf = "%PDF-1.4".getBytes(StandardCharsets.US_ASCII);
        service.sendEmail(request().attachments(List.of(attachment("Q3_Statement.pdf", pdf))));

        final FileAttachment file = (FileAttachment) sentMessage().attachments.getCurrentPage().get(0);
        assertThat(file.name).isEqualTo("Q3_Statement.pdf");
        assertThat(file.contentType).isEqualTo("application/pdf");
        assertThat(file.contentBytes).isEqualTo(pdf);
        assertThat(file.oDataType).isEqualTo("#microsoft.graph.fileAttachment");
    }

    @Test
    void sendEmail_senderMatchIsCaseInsensitive() {
        service.sendEmail(request().from("Notification@ALRAMZ.ae"));

        verify(sendMailRequest).post();
    }

    @Test
    void sendEmail_unknownInvokerAndNoMdcStillSend() {
        MDC.clear();
        when(jwtContextProvider.getIfAvailable()).thenReturn(null);

        service.sendEmail(request());

        verify(sendMailRequest).post();
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("invoker=unknown"));
    }

    @Test
    void sendEmail_nullAttachmentListIsTreatedAsNone() {
        service.sendEmail(request().attachments(null));

        assertThat(sentMessage().attachments).isNull();
    }

    // ------------------------------------------------------------------ validation (E4-E8, E10, E12)

    @Test
    void sendEmail_missingFieldsAreListedInOrder() {
        assertValidation(new EmailRequest().to(" , ").from(SENDER).body("x"), "MGE-V-001",
                "Request failed due to missing: to, subject");
        assertValidation(new EmailRequest(), "MGE-V-001", "Request failed due to missing: to, from, subject, body");
    }

    @Test
    void sendEmail_invalidAddressesAreListedInOrder() {
        assertValidation(request().to("a@x.com,bad").cc("also-bad").bcc("ok@example.com").from("nope"), "MGE-V-002",
                "Invalid email address in: to, cc, from");
        assertValidation(request().bcc("x@y"), "MGE-V-002", "Invalid email address in: bcc");
    }

    @Test
    void sendEmail_subjectTooLongInProcessIsRejected() {
        assertValidation(request().subject("s".repeat(256)), "MGE-V-003", "Field length exceeded: subject");
    }

    @Test
    void sendEmail_bodyTooLongIsRejected() {
        assertValidation(request().body("b".repeat(10_001)), "MGE-V-003", "Field length exceeded: body");
    }

    @Test
    void sendEmail_tooManyAttachmentsIsRejected() {
        final List<EmailAttachment> eleven = new ArrayList<>(Collections.nCopies(11, attachment("a.txt", new byte[]{1})));
        assertValidation(request().attachments(eleven), "MGE-V-004", "Number of attachments exceeds 10");
    }

    @Test
    void sendEmail_badAttachmentItemsReportOneBasedPosition() {
        assertValidation(request().attachments(List.of(attachment("ok.txt", new byte[]{1}), attachment(" ", new byte[]{1}))),
                "MGE-V-005", "Invalid attachment at position 2: fileName is required");
        assertValidation(request().attachments(List.of(attachment("a.txt", null))),
                "MGE-V-005", "Invalid attachment at position 1: content is required");
        assertValidation(request().attachments(List.of(attachment("a.txt", new byte[0]))),
                "MGE-V-005", "Invalid attachment at position 1: content is required");
        final List<EmailAttachment> withNull = new ArrayList<>();
        withNull.add(null);
        assertValidation(request().attachments(withNull), "MGE-V-005", "Invalid attachment at position 1: fileName is required");
    }

    @Test
    void sendEmail_totalAttachmentSizeOverCapIsRejected() {
        assertValidation(request().attachments(List.of(attachment("a.bin", new byte[2_000_000]),
                attachment("b.bin", new byte[1_000_001]))), "MGE-V-008", "Attachments size limit exceeded");
    }

    @Test
    void sendEmail_totalAttachmentSizeAtCapIsAccepted() {
        service.sendEmail(request().attachments(List.of(attachment("a.bin", new byte[3_000_000]))));

        verify(sendMailRequest).post();
    }

    @Test
    void sendEmail_senderNotAllowedIsRejected() {
        assertValidation(request().from("ceo@example.com"), "MGE-V-007", "Sender mailbox is not permitted");
    }

    @Test
    void sendEmail_emptyAllowListRejectsEverySender() {
        service = newService(null);

        assertValidation(request(), "MGE-V-007", "Sender mailbox is not permitted");
    }

    // ------------------------------------------------------------------ Graph failures (E13-E19)

    @Test
    void sendEmail_graph401ThenSuccessPostsTwice() {
        doThrow(graphError(401, Map.of())).doNothing().when(sendMailRequest).post();

        service.sendEmail(request());

        verify(sendMailRequest, times(2)).post();
    }

    @Test
    void sendEmail_graph401TwiceIsBackendB001() {
        doThrow(graphError(401, Map.of())).when(sendMailRequest).post();

        assertThatThrownBy(() -> service.sendEmail(request()))
                .isInstanceOf(EmailServiceException.class)
                .hasFieldOrPropertyWithValue("code", "MGE-B-001")
                .hasNoCause();
        verify(sendMailRequest, times(2)).post();
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("code=MGE-B-001"));
    }

    @Test
    void sendEmail_graph429WithRetryAfterIsB002WithHeaderValue() {
        doThrow(graphError(429, Map.of("Retry-After", "30"))).when(sendMailRequest).post();

        assertThatThrownBy(() -> service.sendEmail(request()))
                .isInstanceOf(EmailServiceException.class)
                .hasFieldOrPropertyWithValue("code", "MGE-B-002")
                .hasFieldOrPropertyWithValue("retryAfter", "30")
                .hasNoCause();
        verify(sendMailRequest, times(1)).post();
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("MGE-B-002 Graph throttled; Retry-After=30"));
    }

    @Test
    void sendEmail_graph429WithoutRetryAfterIsB002WithoutValue() {
        doThrow(graphError(429, Map.of())).when(sendMailRequest).post();

        assertThatThrownBy(() -> service.sendEmail(request()))
                .hasFieldOrPropertyWithValue("code", "MGE-B-002")
                .hasFieldOrPropertyWithValue("retryAfter", null);
    }

    @Test
    void sendEmail_graph429WithHttpDateRetryAfterStillB002WithoutValue() {
        doThrow(graphError(429, Map.of("Retry-After", "Wed, 21 Oct 2026 07:28:00 GMT"))).when(sendMailRequest).post();

        assertThatThrownBy(() -> service.sendEmail(request()))
                .hasFieldOrPropertyWithValue("code", "MGE-B-002")
                .hasFieldOrPropertyWithValue("retryAfter", null);
    }

    @Test
    void sendEmail_otherGraphErrorsAreB003() {
        doThrow(graphError(400, Map.of())).when(sendMailRequest).post();
        assertThatThrownBy(() -> service.sendEmail(request()))
                .hasFieldOrPropertyWithValue("code", "MGE-B-003")
                .hasMessage("Graph send failed 400 ErrorCode400")
                .hasNoCause();

        doThrow(graphError(503, Map.of())).when(sendMailRequest).post();
        assertThatThrownBy(() -> service.sendEmail(request()))
                .hasFieldOrPropertyWithValue("code", "MGE-B-003");
    }

    @Test
    void sendEmail_graphErrorWithoutServiceErrorIsB003() {
        doThrow(GraphServiceException.createFromResponse("u", "POST", new ArrayList<>(), null, Map.of(), "Not Found",
                404, null, false)).when(sendMailRequest).post();

        assertThatThrownBy(() -> service.sendEmail(request()))
                .hasFieldOrPropertyWithValue("code", "MGE-B-003")
                .hasMessage("Graph send failed 404 null");
    }

    @Test
    void sendEmail_clientOrUnexpectedExceptionIsB004() {
        final ClientException clientException = new ClientException("Error during http request", null);
        doThrow(clientException).when(sendMailRequest).post();
        assertThatThrownBy(() -> service.sendEmail(request()))
                .isInstanceOf(EmailServiceException.class)
                .hasFieldOrPropertyWithValue("code", "MGE-B-004")
                .hasCause(clientException);

        doThrow(new IllegalStateException("boom")).when(sendMailRequest).post();
        assertThatThrownBy(() -> service.sendEmail(request()))
                .hasFieldOrPropertyWithValue("code", "MGE-B-004");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void sendEmail_noGraphClientIsConfigurationError() {
        when(((ObjectProvider) graphClientProvider).getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.sendEmail(request())).isInstanceOf(EmailConfigurationException.class);
    }

    // ------------------------------------------------------------------ Retry-After parsing

    @Test
    void retryAfter_readsSdkHeaderFormats() {
        assertThat(EmailServiceImpl.retryAfter(List.of("Content-Type : application/json", "Retry-After : 5"))).isEqualTo("5");
        assertThat(EmailServiceImpl.retryAfter(List.of("retry-after : [7]"))).isEqualTo("7");
        assertThat(EmailServiceImpl.retryAfter(List.of("Retry-After : soon"))).isNull();
        assertThat(EmailServiceImpl.retryAfter(Collections.singletonList(null))).isNull();
        assertThat(EmailServiceImpl.retryAfter(List.of(": 5", "X-Other : 1"))).isNull();
        assertThat(EmailServiceImpl.retryAfter(null)).isNull();
    }

    // ------------------------------------------------------------------ log hygiene

    @Test
    void logs_neverContainBodySubjectRecipientsOrContent() {
        final String secretBody = "Your OTP is 918273";
        final byte[] content = "ATTACHMENT-SECRET".getBytes(StandardCharsets.US_ASCII);
        doThrow(graphError(429, Map.of("Retry-After", "30"))).doNothing().when(sendMailRequest).post();
        final EmailRequest request = request().to("jane.doe@example.com").cc("manager@example.com")
                .subject("Secret Subject").body(secretBody).attachments(List.of(attachment("a.txt", content)));

        assertThatThrownBy(() -> service.sendEmail(request)).isInstanceOf(EmailServiceException.class);
        service.sendEmail(request);
        assertThatThrownBy(() -> service.sendEmail(request().to("leak@example.com,bad"))).isInstanceOf(EmailValidationException.class);

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain(secretBody, "Secret Subject", "jane.doe@example.com", "manager@example.com",
                        "leak@example.com", "ATTACHMENT-SECRET"));
    }

    @Test
    void sendEmail_validationFailureDoesNotTouchGraphProvider() {
        assertThatThrownBy(() -> service.sendEmail(request().from("ceo@example.com")))
                .isInstanceOf(EmailValidationException.class);
        verify(graphClientProvider, never()).getIfAvailable();
        verify(userRequestBuilder, never()).sendMail(any());
    }
}
