package com.alramz.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alramz.exception.EmailServiceException;
import com.alramz.exception.EmailValidationException;
import com.alramz.exception.TemplatedEmailException;
import com.alramz.jwt.context.JwtContext;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailAttachment;
import com.alramz.model.EmailLogEntry;
import com.alramz.model.EmailRequest;
import com.alramz.model.EmailTemplate;
import com.alramz.model.TemplateAttachment;
import com.alramz.model.TemplatedEmailRequest;
import com.alramz.repository.EmailTemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TemplatedEmailServiceTest {

    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    // 2026-10-26T20:30:00Z is 2026-10-27 00:30 in Dubai, so the Dubai day differs from the UTC day.
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-26T20:30:00Z"), DUBAI);
    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 10, 27, 0, 0);
    private static final String IP = "10.0.0.7";

    @Mock
    private EmailService emailService;
    @Mock
    private EmailTemplateRepository repository;
    @Mock
    private ObjectProvider<JwtContext> jwtContextProvider;
    @Mock
    private JwtContext jwtContext;

    private TemplatedEmailService service;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void setUp() {
        service = new TemplatedEmailService(emailService, repository, jwtContextProvider, CLOCK);
        when(jwtContextProvider.getIfAvailable()).thenReturn(jwtContext);
        when(jwtContext.getCurrentApplication()).thenReturn("caller-app");
        MDCUtil.putCorrelationId("cid-1");
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(TemplatedEmailService.class)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(TemplatedEmailService.class)).detachAppender(logs);
        MDC.clear();
    }

    private static EmailTemplate template(final int placeholders, final Integer attachments, final String types,
                                          final String maxSize, final boolean bulk, final String enBody) {
        return new EmailTemplate("T1", "STATEMENT", placeholders, 3, "notification@alramz.ae",
                "Statement ready", "كشف الحساب", enBody, "<p>مرحبا %1%</p>", maxSize, attachments, types, bulk);
    }

    private static EmailTemplate simpleTemplate() {
        return template(2, 3, "PDF,MSWORD, MSEXCEL,JPEG,JPG,PNG,CSV,TRN,TEXT", "1000", false,
                "Dear %1%, your %2% statement. Bye %1%.");
    }

    private static TemplatedEmailRequest request() {
        return new TemplatedEmailRequest().to("jane.doe@example.com; john.smith@example.com")
                .templateName("STATEMENT").language("EN").placeholders(new ArrayList<>(List.of("Jane", "Q3")));
    }

    private static TemplateAttachment attachment(final String type, final String name, final String content) {
        return new TemplateAttachment().contentType(type).fileName(name)
                .content(content == null ? null : content.getBytes(StandardCharsets.UTF_8));
    }

    private void givenTemplate(final EmailTemplate template) {
        when(repository.findByName("STATEMENT")).thenReturn(java.util.Optional.of(template));
    }

    private EmailRequest sentRequest() {
        final ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendEmail(captor.capture());
        return captor.getValue();
    }

    private EmailLogEntry loggedEntry() {
        final ArgumentCaptor<EmailLogEntry> captor = ArgumentCaptor.forClass(EmailLogEntry.class);
        verify(repository).insertLog(captor.capture());
        return captor.getValue();
    }

    private void assertFails(final TemplatedEmailRequest request, final String code, final String message) {
        assertThatThrownBy(() -> service.send(request, IP))
                .isInstanceOf(TemplatedEmailException.class)
                .hasFieldOrPropertyWithValue("code", code)
                .hasMessage(message);
        verify(emailService, never()).sendEmail(any());
        verify(repository, never()).insertLog(any());
    }

    // ---- main scenario ----

    @Test
    void send_rendersAndSendsTemplateAndWritesOneAuditRow() {
        givenTemplate(simpleTemplate());

        final String correlationId = service.send(request(), IP);

        assertThat(correlationId).isEqualTo("cid-1");
        final EmailRequest sent = sentRequest();
        assertThat(sent.getFrom()).isEqualTo("notification@alramz.ae");
        assertThat(sent.getTo()).isEqualTo("jane.doe@example.com,john.smith@example.com");
        assertThat(sent.getCc()).isNull();
        assertThat(sent.getBcc()).isNull();
        assertThat(sent.getSubject()).isEqualTo("Statement ready");
        assertThat(sent.getBody()).isEqualTo("Dear Jane, your Q3 statement. Bye Jane.");
        assertThat(sent.getAttachments()).isNullOrEmpty();

        final EmailLogEntry entry = loggedEntry();
        assertThat(entry.templateId()).isEqualTo("T1");
        assertThat(entry.to()).isEqualTo("jane.doe@example.com,john.smith@example.com");
        // sha256("Dear Jane, your Q3 statement. Bye Jane.")
        assertThat(entry.bodySha256()).hasSize(64).matches("[0-9a-f]{64}")
                .isEqualTo(TemplatedEmailService.sha256Hex("Dear Jane, your Q3 statement. Bye Jane."));
        assertThat(entry.language()).isEqualTo("EN");
        assertThat(entry.dateTime()).isEqualTo(LocalDateTime.of(2026, 10, 27, 0, 30));
        assertThat(entry.invoker()).isEqualTo("caller-app");
        assertThat(entry.ipAddress()).isEqualTo(IP);
        assertThat(entry.attachmentsMetaData()).isEqualTo("{}");
    }

    @Test
    void sha256HexIsLowercaseHexOfUtf8() {
        assertThat(TemplatedEmailService.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void send_normalisesCcAndBccAndUsesArabicContent() {
        givenTemplate(template(1, 0, "", null, false, "unused"));
        final TemplatedEmailRequest request = new TemplatedEmailRequest().to("a@example.com/b@example.com")
                .cc("c@example.com\\d@example.com").bcc("e@example.com, f@example.com").templateName("STATEMENT")
                .language("AR").placeholders(List.of("<b>x</b>"));

        service.send(request, IP);

        final EmailRequest sent = sentRequest();
        assertThat(sent.getTo()).isEqualTo("a@example.com,b@example.com");
        assertThat(sent.getCc()).isEqualTo("c@example.com,d@example.com");
        assertThat(sent.getBcc()).isEqualTo("e@example.com,f@example.com");
        assertThat(sent.getSubject()).isEqualTo("كشف الحساب");
        // E19: the AR body is HTML, so the placeholder is escaped.
        assertThat(sent.getBody()).isEqualTo("<p>مرحبا &lt;b&gt;x&lt;/b&gt;</p>");
        final EmailLogEntry entry = loggedEntry();
        assertThat(entry.cc()).isEqualTo("c@example.com,d@example.com");
        assertThat(entry.bcc()).isEqualTo("e@example.com,f@example.com");
        assertThat(entry.language()).isEqualTo("AR");
    }

    @Test
    void send_doesNotEscapePlaceholdersInPlainTextBody() {
        givenTemplate(template(1, 0, "", null, false, "Hi %1%"));

        service.send(request().placeholders(List.of("<b>x</b>")), IP);

        assertThat(sentRequest().getBody()).isEqualTo("Hi <b>x</b>");
    }

    @Test
    void send_escapesPlaceholdersWhenALeadingPlaceholderWouldMakeATextBodyHtml() {
        // The gateway sends any body starting with '<' as HTML, so a leading placeholder must not open raw markup.
        givenTemplate(template(1, 0, "", null, false, "%1%, your statement is ready"));

        service.send(request().placeholders(List.of("<a href=\"https://evil.example\">Click</a>")), IP);

        assertThat(sentRequest().getBody())
                .isEqualTo("&lt;a href=&quot;https://evil.example&quot;&gt;Click&lt;/a&gt;, your statement is ready")
                .doesNotStartWith("<");
    }

    @Test
    void loggableDropsControlCharactersAndOverlongValues() {
        assertThat(TemplatedEmailService.loggable("STATEMENT_EN")).isEqualTo("STATEMENT_EN");
        assertThat(TemplatedEmailService.loggable("x\r\nINFO forged line")).isEqualTo("<invalid>");
        assertThat(TemplatedEmailService.loggable("a".repeat(101))).isEqualTo("<invalid>");
    }

    @Test
    void send_withoutPlaceholdersWhenTemplateNeedsNone() {
        givenTemplate(template(0, null, null, null, false, "Static body"));

        service.send(request().placeholders(null), IP);

        assertThat(sentRequest().getBody()).isEqualTo("Static body");
    }

    @Test
    void send_usesUnknownInvokerWhenNoJwtContext() {
        when(jwtContextProvider.getIfAvailable()).thenReturn(null);
        givenTemplate(simpleTemplate());

        service.send(request(), IP);

        assertThat(loggedEntry().invoker()).isEqualTo("unknown");
        verify(repository).countSentToday(eq("T1"), eq("unknown"), anyString(), any(), any());
    }

    @Test
    void send_usesUnknownInvokerWhenClaimIsMissingAndGeneratesCorrelationId() {
        when(jwtContext.getCurrentApplication()).thenReturn(null);
        MDC.clear();
        givenTemplate(simpleTemplate());

        assertThat(service.send(request(), null)).hasSize(36);
        final EmailLogEntry entry = loggedEntry();
        assertThat(entry.invoker()).isEqualTo("unknown");
        assertThat(entry.ipAddress()).isNull();
    }

    @Test
    void send_truncatesOverlongClientIp() {
        givenTemplate(simpleTemplate());

        service.send(request(), "1".repeat(60));

        assertThat(loggedEntry().ipAddress()).hasSize(45);
    }

    // ---- MIME mapping (one assertion per row of the 3.1 table) ----

    @ParameterizedTest
    @CsvSource({
            "JPEG, a.jpeg, image/jpeg",
            "JPG, a.jpg, image/jpeg",
            "PNG, a.png, image/png",
            "PDF, a.pdf, application/pdf",
            "MSWORD, a.doc, application/msword",
            "MSWORD, a.DOCX, application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "MSEXCEL, a.xls, application/vnd.ms-excel",
            "MSEXCEL, a.xlsx, application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "CSV, a.csv, text/csv; charset=UTF-8",
            "TRN, a.trn, text/plain; charset=UTF-8",
            "TEXT, a.txt, text/plain; charset=UTF-8"
    })
    void send_mapsAttachmentTypeCodeToMime(final String code, final String fileName, final String mime) {
        givenTemplate(simpleTemplate());

        service.send(request().attachments(List.of(attachment(code, fileName, "hello"))), IP);

        final EmailAttachment sent = sentRequest().getAttachments().get(0);
        assertThat(sent.getContentType()).isEqualTo(mime);
        assertThat(sent.getFileName()).isEqualTo(fileName);
        assertThat(sent.getContent()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void send_recordsAttachmentMetadata() {
        givenTemplate(simpleTemplate());

        service.send(request().attachments(List.of(attachment("PDF", "a.pdf", "hello"),
                attachment("PNG", "b \"q\".png", "abc"))), IP);

        assertThat(loggedEntry().attachmentsMetaData()).isEqualTo(
                "{\"attachmentMetaData\":[{\"ct\":\"PDF\",\"fn\":\"a.pdf\",\"s\":5},"
                        + "{\"ct\":\"PNG\",\"fn\":\"b \\\"q\\\".png\",\"s\":3}]}");
    }

    // ---- E5/E6 field checks ----

    @Test
    void e5_missingToAndBadLanguageAreCollected() {
        assertFails(request().to(null).language("en"), "EML001", "Request failed due to missing: to, language");
        verifyNoInteractions(repository);
    }

    @Test
    void e6_invalidCcAddress() {
        assertFails(request().cc("a@x.com;bad"), "EML001", "Request failed due to missing: cc");
    }

    @Test
    void fieldChecks_collectAllInOrder() {
        final TemplatedEmailRequest request = new TemplatedEmailRequest().to(" ; ").cc("x").bcc("y@")
                .templateName(" ").language(null)
                .attachments(List.of(attachment(" ", "a.pdf", "x")));

        assertFails(request, "EML001", "Request failed due to missing: to, cc, bcc, templateName, language, attachments");
    }

    @Test
    void fieldChecks_attachmentWithoutContentIsMissing() {
        assertFails(request().attachments(List.of(attachment("PDF", "a.pdf", null))), "EML001",
                "Request failed due to missing: attachments");
        assertFails(request().attachments(List.of(attachment("PDF", "a.pdf", ""))), "EML001",
                "Request failed due to missing: attachments");
        final List<TemplateAttachment> withNull = new ArrayList<>();
        withNull.add(null);
        assertFails(request().attachments(withNull), "EML001", "Request failed due to missing: attachments");
    }

    @Test
    void fieldChecks_blankCcAndBccMeanNone() {
        givenTemplate(simpleTemplate());

        service.send(request().cc(" ").bcc(""), IP);

        assertThat(sentRequest().getCc()).isNull();
    }

    // ---- E7..E13 template rules ----

    @Test
    void e7_unknownTemplate() {
        when(repository.findByName("STATEMENT")).thenReturn(java.util.Optional.empty());

        assertFails(request(), "EML002", "Invalid template name");
    }

    @Test
    void e8_wrongPlaceholderCount() {
        givenTemplate(simpleTemplate());

        assertFails(request().placeholders(List.of("Jane")), "EML003", "Invalid number of placeholders");
        assertFails(request().placeholders(null), "EML003", "Invalid number of placeholders");
        assertFails(request().placeholders(List.of()), "EML003", "Invalid number of placeholders");
    }

    @Test
    void e9_tooManyAttachments() {
        givenTemplate(template(2, 2, "PDF", "1000", false, "b"));
        final TemplateAttachment a = attachment("PDF", "a.pdf", "x");

        assertFails(request().attachments(List.of(a, a, a)), "EML004",
                "Number of attachments does not match template configurations");
    }

    @Test
    void e9_nullAttachmentCountMeansNone() {
        givenTemplate(template(2, null, "PDF", "1000", false, "b"));

        assertFails(request().attachments(List.of(attachment("PDF", "a.pdf", "x"))), "EML004",
                "Number of attachments does not match template configurations");
    }

    @Test
    void e10_typeNotAllowedOrWrongExtension() {
        givenTemplate(simpleTemplate());
        final String msg = "Type of attachments does not match template configurations";

        assertFails(request().attachments(List.of(attachment("EXE", "a.exe", "x"))), "EML005", msg);
        assertFails(request().attachments(List.of(attachment("pdf", "a.pdf", "x"))), "EML005", msg);
        assertFails(request().attachments(List.of(attachment("MSWORD", "a.pdf", "x"))), "EML005", msg);
        assertFails(request().attachments(List.of(attachment("MSEXCEL", "a.doc", "x"))), "EML005", msg);
        assertFails(request().attachments(List.of(attachment("MSWORD", "noextension", "x"))), "EML005", msg);
    }

    @Test
    void e10_unknownCodeIsRejectedEvenIfTemplateListsIt() {
        givenTemplate(template(2, 1, "EXE", "1000", false, "b"));

        assertFails(request().attachments(List.of(attachment("EXE", "a.exe", "x"))), "EML005",
                "Type of attachments does not match template configurations");
    }

    @Test
    void e10_templateWithoutTypesAllowsNothing() {
        givenTemplate(template(2, 1, null, "1000", false, "b"));

        assertFails(request().attachments(List.of(attachment("PDF", "a.pdf", "x"))), "EML005",
                "Type of attachments does not match template configurations");
    }

    @Test
    void e11_blankFileName() {
        givenTemplate(simpleTemplate());

        assertFails(request().attachments(List.of(attachment("PDF", " ", "x"))), "EML006",
                "Please provide the fileName for all attachments");
        assertFails(request().attachments(List.of(attachment("PDF", null, "x"))), "EML006",
                "Please provide the fileName for all attachments");
    }

    @Test
    void e12_decodedTotalOverTemplateLimit() {
        givenTemplate(template(2, 2, "PDF", "5", false, "b"));

        assertFails(request().attachments(List.of(attachment("PDF", "a.pdf", "abc"), attachment("PDF", "b.pdf", "abc"))),
                "EML007", "Attachments size limit exceeded");
    }

    @Test
    void e12_totalAtLimitIsAccepted() {
        givenTemplate(template(2, 2, "PDF", " 6 ", false, "b"));

        service.send(request().attachments(List.of(attachment("PDF", "a.pdf", "abc"), attachment("PDF", "b.pdf", "abc"))), IP);

        verify(emailService).sendEmail(any());
    }

    @Test
    void e13_unparseableMaxSizeWithAttachmentsIsMisconfiguration() {
        for (String bad : new String[]{null, " ", "3MB"}) {
            givenTemplate(template(2, 1, "PDF", bad, false, "b"));

            assertThatThrownBy(() -> service.send(request().attachments(List.of(attachment("PDF", "a.pdf", "x"))), IP))
                    .isInstanceOf(TemplatedEmailException.class)
                    .hasFieldOrPropertyWithValue("code", "EML010");
        }
        verify(emailService, never()).sendEmail(any());
    }

    @Test
    void e13_unparseableMaxSizeWithoutAttachmentsIsIgnored() {
        givenTemplate(template(2, 0, "PDF", "3MB", false, "b %1% %2%"));

        service.send(request(), IP);

        verify(emailService).sendEmail(any());
    }

    // ---- E14/E15 daily limit ----

    @Test
    void e14_perRecipientLimitReached() {
        givenTemplate(simpleTemplate());
        when(repository.countSentToday("T1", "caller-app", "jane.doe@example.com,john.smith@example.com",
                DAY_START, DAY_START.plusDays(1))).thenReturn(3);

        assertFails(request(), "EML008", "Maximum number of attempts exceeded");
    }

    @Test
    void e14_bulkLimitCountsWithoutRecipient() {
        givenTemplate(template(2, 3, "PDF", "1000", true, "b"));
        when(repository.countSentToday(eq("T1"), eq("caller-app"), isNull(), eq(DAY_START), eq(DAY_START.plusDays(1))))
                .thenReturn(5);

        assertFails(request(), "EML008", "Maximum number of attempts exceeded");
    }

    @Test
    void e15_belowLimitInTheDubaiDaySends() {
        givenTemplate(simpleTemplate());
        when(repository.countSentToday(anyString(), anyString(), anyString(), any(), any())).thenReturn(2);

        service.send(request(), IP);

        verify(repository).countSentToday("T1", "caller-app", "jane.doe@example.com,john.smith@example.com",
                DAY_START, DAY_START.plusDays(1));
        verify(emailService).sendEmail(any());
    }

    // ---- render misconfiguration ----

    @Test
    void nullBodyForLanguageIsMisconfiguration() {
        givenTemplate(template(2, 0, "", null, false, null));

        assertFails(request(), "EML010", "Template misconfiguration: no EN subject or body");
    }

    @Test
    void nullSubjectForLanguageIsMisconfiguration() {
        givenTemplate(new EmailTemplate("T1", "STATEMENT", 0, 3, "notification@alramz.ae", null, null,
                "b", "b", null, null, null, false));

        assertFails(request().placeholders(null), "EML010", "Template misconfiguration: no EN subject or body");
    }

    @Test
    void nullPlaceholderValueRendersEmpty() {
        givenTemplate(template(1, 0, "", null, false, "Hi %1%!"));
        final List<String> values = new ArrayList<>();
        values.add(null);

        service.send(request().placeholders(values), IP);

        assertThat(sentRequest().getBody()).isEqualTo("Hi !");
    }

    // ---- E16/E17/E12 gateway mapping ----

    @Test
    void e17_gatewayBackendFailureIsEml009WithoutCause() {
        givenTemplate(simpleTemplate());
        doThrow(new EmailServiceException("MGE-B-003", "Graph said no to jane.doe@example.com"))
                .when(emailService).sendEmail(any());

        assertThatThrownBy(() -> service.send(request(), IP))
                .isInstanceOf(TemplatedEmailException.class)
                .hasFieldOrPropertyWithValue("code", "EML009")
                .hasMessage("Email service failed MGE-B-003")
                .hasNoCause();
        verify(repository, never()).insertLog(any());
    }

    @ParameterizedTest
    @CsvSource({
            "MGE-V-008, EML007, Attachments size limit exceeded",
            "MGE-V-004, EML004, Number of attachments does not match template configurations",
            "MGE-V-007, EML010, Template misconfiguration MGE-V-007",
            "MGE-V-003, EML010, Template misconfiguration MGE-V-003"
    })
    void e16_gatewayValidationCodesAreMapped(final String gatewayCode, final String code, final String message) {
        givenTemplate(simpleTemplate());
        doThrow(new EmailValidationException(gatewayCode, "detail")).when(emailService).sendEmail(any());

        assertThatThrownBy(() -> service.send(request(), IP))
                .isInstanceOf(TemplatedEmailException.class)
                .hasFieldOrPropertyWithValue("code", code)
                .hasMessage(message);
        verify(repository, never()).insertLog(any());
    }

    // ---- E18 audit failure ----

    @Test
    void e18_auditInsertFailureStillSucceeds() {
        givenTemplate(simpleTemplate());
        doThrow(new IllegalStateException("db down")).when(repository).insertLog(any());

        assertThat(service.send(request(), IP)).isEqualTo("cid-1");
        assertThat(logs.list).anyMatch(e -> e.getFormattedMessage().contains("email_log insert failed"));
    }

    // ---- logging hygiene ----

    @Test
    void logsNeverContainAddressesPlaceholdersSubjectOrBody() {
        givenTemplate(simpleTemplate());
        service.send(request().cc("boss@example.com").attachments(List.of(attachment("PDF", "a.pdf", "SECRETDATA"))), IP);
        when(repository.findByName("STATEMENT")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.send(request(), IP)).isInstanceOf(TemplatedEmailException.class);

        assertThat(logs.list).isNotEmpty();
        assertThat(logs.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain("@example.com").doesNotContain("Jane").doesNotContain("Q3")
                .doesNotContain("Statement ready").doesNotContain("Dear").doesNotContain("SECRETDATA"));
        assertThat(logs.list.get(0).getFormattedMessage()).contains("outcome=OK", "to=2", "cc=1", "attachments=1",
                "attachmentBytes=10");
        assertThat(logs.list.get(logs.list.size() - 1).getFormattedMessage()).contains("outcome=EML002");
    }
}
