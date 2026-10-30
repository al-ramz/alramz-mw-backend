package com.alramz.service;

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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Templated email (spec 3.1): validates against the stored template, renders it, sends it through
 * {@link EmailService} and writes one email_log row. Logs carry counts only, never addresses, placeholders,
 * subject, body or attachment content.
 */
@Slf4j
@Service
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity", "PMD.CognitiveComplexity"})
public class TemplatedEmailService {

    // Same rule as EmailServiceImpl.EMAIL (kept there untouched): fail fast with EML001 before the gateway sees it.
    static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern SEPARATORS = Pattern.compile("[;,/\\s\\\\]+");
    private static final Set<String> KNOWN_TYPES =
            Set.of("JPEG", "JPG", "PNG", "PDF", "MSWORD", "MSEXCEL", "CSV", "TRN", "TEXT");
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_IP_LENGTH = 45;
    private static final String UNKNOWN = "unknown";
    private static final Pattern LOGGABLE = Pattern.compile("^[A-Za-z0-9 ._-]{1,100}$");

    private final EmailService emailService;
    private final EmailTemplateRepository repository;
    private final ObjectProvider<JwtContext> jwtContextProvider;
    private final Clock clock;

    @Autowired
    public TemplatedEmailService(final EmailService emailService, final EmailTemplateRepository repository,
                                 final ObjectProvider<JwtContext> jwtContextProvider) {
        this(emailService, repository, jwtContextProvider, Clock.system(DUBAI));
    }

    TemplatedEmailService(final EmailService emailService, final EmailTemplateRepository repository,
                          final ObjectProvider<JwtContext> jwtContextProvider, final Clock clock) {
        this.emailService = emailService;
        this.repository = repository;
        this.jwtContextProvider = jwtContextProvider;
        this.clock = clock;
    }

    /** Sends the templated email and returns the correlation id. Throws TemplatedEmailException (EML0xx). */
    public String send(final TemplatedEmailRequest request, final String clientIp) {
        final String correlationId = correlationId();
        final String invoker = invoker();
        final List<String> to = split(request.getTo());
        final List<String> cc = split(request.getCc());
        final List<String> bcc = split(request.getBcc());
        final List<TemplateAttachment> attachments = request.getAttachments() == null ? List.of() : request.getAttachments();
        long attachmentBytes = 0;
        String outcome = "EML010";
        try {
            checkFields(request, to, cc, bcc, attachments);
            final EmailTemplate template = repository.findByName(request.getTemplateName())
                    .orElseThrow(() -> new TemplatedEmailException("EML002", "Invalid template name"));
            final List<String> placeholders = request.getPlaceholders() == null ? List.of() : request.getPlaceholders();
            if (placeholders.size() != template.numberOfPlaceholders()) {
                throw new TemplatedEmailException("EML003", "Invalid number of placeholders");
            }
            attachmentBytes = checkAttachments(template, attachments);
            final String toJoined = String.join(",", to);
            checkDailyLimit(template, invoker, toJoined);

            final boolean english = "EN".equals(request.getLanguage());
            final String subject = english ? template.enSubject() : template.arSubject();
            final String storedBody = english ? template.enBody() : template.arBody();
            if (subject == null || storedBody == null) {
                throw misconfigured("no " + request.getLanguage() + " subject or body", template);
            }
            final String body = render(storedBody, placeholders);

            send(new EmailRequest()
                    .to(toJoined)
                    .cc(joinOrNull(cc))
                    .bcc(joinOrNull(bcc))
                    .from(template.fromEmail())
                    .subject(subject)
                    .body(body)
                    .attachments(attachments.isEmpty() ? null : attachments.stream()
                            .map(a -> new EmailAttachment().fileName(a.getFileName())
                                    .contentType(mime(a.getContentType(), a.getFileName())).content(a.getContent()))
                            .toList()));

            audit(new EmailLogEntry(template.id(), toJoined, joinOrNull(cc), joinOrNull(bcc), sha256Hex(body),
                    request.getLanguage(), LocalDateTime.now(clock), invoker, truncate(clientIp), metadata(attachments)),
                    correlationId);
            outcome = "OK";
            return correlationId;
        } catch (TemplatedEmailException e) {
            outcome = e.getCode();
            throw e;
        } finally {
            log.info("Templated email outcome={} correlationId={} templateName={} language={} invoker={} to={} cc={}"
                            + " bcc={} attachments={} attachmentBytes={}", outcome, correlationId,
                    loggable(request.getTemplateName()), loggable(request.getLanguage()), invoker, to.size(), cc.size(), bcc.size(),
                    attachments.size(), attachmentBytes);
        }
    }

    private static void checkFields(final TemplatedEmailRequest request, final List<String> to, final List<String> cc,
                                    final List<String> bcc, final List<TemplateAttachment> attachments) {
        final List<String> missing = new ArrayList<>();
        if (to.isEmpty() || !allValid(to)) {
            missing.add("to");
        }
        if (!allValid(cc)) {
            missing.add("cc");
        }
        if (!allValid(bcc)) {
            missing.add("bcc");
        }
        if (isBlank(request.getTemplateName())) {
            missing.add("templateName");
        }
        if (!"EN".equals(request.getLanguage()) && !"AR".equals(request.getLanguage())) {
            missing.add("language");
        }
        if (attachments.stream().anyMatch(a -> a == null || a.getContent() == null || a.getContent().length == 0
                || isBlank(a.getContentType()))) {
            missing.add("attachments");
        }
        if (!missing.isEmpty()) {
            throw new TemplatedEmailException("EML001", "Request failed due to missing: " + String.join(", ", missing));
        }
    }

    private static long checkAttachments(final EmailTemplate template, final List<TemplateAttachment> attachments) {
        final int allowedCount = template.numberOfAttachments() == null ? 0 : template.numberOfAttachments();
        if (attachments.size() > allowedCount) {
            throw new TemplatedEmailException("EML004", "Number of attachments does not match template configurations");
        }
        final List<String> allowedTypes = template.typeOfAttachments() == null ? List.of()
                : Arrays.stream(template.typeOfAttachments().split(",")).map(String::trim).toList();
        long total = 0;
        for (TemplateAttachment attachment : attachments) {
            final String code = attachment.getContentType();
            if (!KNOWN_TYPES.contains(code) || !allowedTypes.contains(code)) {
                throw typeMismatch();
            }
            if (isBlank(attachment.getFileName())) {
                throw new TemplatedEmailException("EML006", "Please provide the fileName for all attachments");
            }
            final String extension = extension(attachment.getFileName());
            if ("MSWORD".equals(code) && !"doc".equals(extension) && !"docx".equals(extension)
                    || "MSEXCEL".equals(code) && !"xls".equals(extension) && !"xlsx".equals(extension)) {
                throw typeMismatch();
            }
            total += attachment.getContent().length;
        }
        if (attachments.isEmpty()) {
            return 0;
        }
        final long max;
        try {
            max = Long.parseLong(template.attachmentsMaxSize() == null ? "" : template.attachmentsMaxSize().trim());
        } catch (NumberFormatException e) {
            throw misconfigured("attachments_max_size is not a number", template);
        }
        if (total > max) {
            throw new TemplatedEmailException("EML007", "Attachments size limit exceeded");
        }
        return total;
    }

    private void checkDailyLimit(final EmailTemplate template, final String invoker, final String toJoined) {
        final LocalDateTime dayStart = LocalDate.now(clock).atStartOfDay();
        // ponytail: check-then-send-then-insert, so concurrent requests can exceed max_attempts slightly (legacy has the same race).
        final int used = repository.countSentToday(template.id(), invoker, template.bulk() ? null : toJoined,
                dayStart, dayStart.plusDays(1));
        if (used >= template.maxAttempts()) {
            throw new TemplatedEmailException("EML008", "Maximum number of attempts exceeded");
        }
    }

    /**
     * Escapes placeholders whenever the gateway will send the result as HTML (rendered body starts with '<'),
     * not only when the stored body does: a text template that opens with a placeholder would otherwise let a
     * caller-supplied leading '<' turn the whole mail into unescaped HTML.
     */
    private static String render(final String storedBody, final List<String> placeholders) {
        final String raw = replace(storedBody, placeholders, false);
        return raw.strip().startsWith("<") ? replace(storedBody, placeholders, true) : raw;
    }

    private static String replace(final String storedBody, final List<String> placeholders, final boolean escape) {
        String body = storedBody;
        for (int i = 0; i < placeholders.size(); i++) {
            final String value = placeholders.get(i) == null ? "" : placeholders.get(i);
            body = body.replace("%" + (i + 1) + "%", escape ? HtmlUtils.htmlEscape(value) : value);
        }
        return body;
    }

    private void send(final EmailRequest emailRequest) {
        try {
            emailService.sendEmail(emailRequest);
        } catch (EmailServiceException e) {
            // Not chained: the gateway message is never surfaced.
            throw new TemplatedEmailException("EML009", "Email service failed " + e.getCode());
        } catch (EmailValidationException e) {
            switch (e.getCode()) {
                case "MGE-V-008" -> throw new TemplatedEmailException("EML007", "Attachments size limit exceeded");
                case "MGE-V-004" -> throw new TemplatedEmailException("EML004",
                        "Number of attachments does not match template configurations");
                default -> throw new TemplatedEmailException("EML010", "Template misconfiguration " + e.getCode());
            }
        }
    }

    private void audit(final EmailLogEntry entry, final String correlationId) {
        try {
            repository.insertLog(entry);
        } catch (RuntimeException e) { // NOPMD AvoidCatchingGenericException: the mail is sent; the response must not change
            log.error("email_log insert failed correlationId={} templateId={} error={}", correlationId,
                    entry.templateId(), e.getClass().getSimpleName());
        }
    }

    private static TemplatedEmailException misconfigured(final String detail, final EmailTemplate template) {
        log.error("Template misconfiguration templateId={}: {}", template.id(), detail);
        return new TemplatedEmailException("EML010", "Template misconfiguration: " + detail);
    }

    private static TemplatedEmailException typeMismatch() {
        return new TemplatedEmailException("EML005", "Type of attachments does not match template configurations");
    }

    static String mime(final String code, final String fileName) {
        return switch (code) {
            case "JPEG", "JPG" -> "image/jpeg";
            case "PNG" -> "image/png";
            case "PDF" -> "application/pdf";
            case "MSWORD" -> "docx".equals(extension(fileName))
                    ? "application/vnd.openxmlformats-officedocument.wordprocessingml.document" : "application/msword";
            case "MSEXCEL" -> "xlsx".equals(extension(fileName))
                    ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "application/vnd.ms-excel";
            case "CSV" -> "text/csv; charset=UTF-8";
            default -> "text/plain; charset=UTF-8"; // TRN, TEXT (codes were checked against KNOWN_TYPES)
        };
    }

    static String sha256Hex(final String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String metadata(final List<TemplateAttachment> attachments) {
        if (attachments.isEmpty()) {
            return "{}";
        }
        final List<Map<String, Object>> items = attachments.stream().map(a -> {
            final Map<String, Object> item = new LinkedHashMap<>();
            item.put("ct", a.getContentType());
            item.put("fn", a.getFileName());
            item.put("s", a.getContent().length);
            return item;
        }).toList();
        return JSON.writeValueAsString(Map.of("attachmentMetaData", items));
    }

    private static String extension(final String fileName) {
        final int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static List<String> split(final String value) {
        return value == null ? List.of()
                : Arrays.stream(SEPARATORS.split(value.strip())).filter(t -> !t.isEmpty()).toList();
    }

    private static boolean allValid(final List<String> addresses) {
        return addresses.stream().allMatch(a -> EMAIL.matcher(a).matches());
    }

    private static String joinOrNull(final List<String> addresses) {
        return addresses.isEmpty() ? null : String.join(",", addresses);
    }

    /** Caller-supplied value for a log line: kept only if short and free of CR/LF and other control characters. */
    static String loggable(final String value) {
        return value == null || LOGGABLE.matcher(value).matches() ? value : "<invalid>";
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(final String ip) {
        return ip == null || ip.length() <= MAX_IP_LENGTH ? ip : ip.substring(0, MAX_IP_LENGTH);
    }

    private String invoker() {
        final JwtContext context = jwtContextProvider.getIfAvailable();
        final String application = context == null ? null : context.getCurrentApplication();
        return application == null ? UNKNOWN : application;
    }

    private static String correlationId() {
        final String correlationId = MDCUtil.getCorrelationId();
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}
