package com.alramz.service;

import com.alramz.config.EmailProperties;
import com.alramz.config.OtpProperties;
import com.alramz.exception.EmailServiceException;
import com.alramz.exception.EmailValidationException;
import com.alramz.exception.OtpException;
import com.alramz.exception.SmsException;
import com.alramz.jwt.context.JwtContext;
import com.alramz.logging.util.MDCUtil;
import com.alramz.model.EmailRequest;
import com.alramz.model.OtpChannelResult;
import com.alramz.model.OtpConfiguration;
import com.alramz.model.OtpConfigurationList;
import com.alramz.model.OtpSendRequest;
import com.alramz.model.OtpSendResult;
import com.alramz.model.OtpValidateRequest;
import com.alramz.model.OtpValidateResult;
import com.alramz.model.SmsRecipientResult;
import com.alramz.model.SmsSendRequest;
import com.alramz.repository.OtpRepository;
import com.alramz.repository.OtpRepository.OtpCandidate;
import com.alramz.repository.OtpRepository.OtpConfig;
import com.alramz.repository.OtpRepository.OtpRow;
import com.alramz.service.impl.SmsServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * OTP send / validate / configuration lookup (spec 3.1-3.3). Deliberately no @Transactional: every transaction is
 * a short TransactionTemplate, and no transaction or connection is held while SMS/email is sent.
 * Logs carry counts and codes only, never the OTP, its hash, the rendered text, an address or a number.
 */
@Slf4j
@Service
@SuppressWarnings({"PMD.TooManyMethods", "PMD.CyclomaticComplexity", "PMD.CognitiveComplexity",
        "PMD.ExcessiveImports", "PMD.AvoidCatchingGenericException"})
public class OtpService {

    static final int MAX_RECIPIENTS = 5;
    static final String SMS_MSG_CATEGORY = "4.5";
    static final int DEFAULT_MAX_VALIDATION_ATTEMPTS = 3;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern SEPARATORS = Pattern.compile("[|;,/\\s]+");
    private static final Pattern IP_ADDRESS = Pattern.compile("^[0-9A-Fa-f.:]{2,45}$");
    private static final Pattern HASH = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final String SUBJECT_BODY_DELIMITER = "^+^";
    private static final String PLACEHOLDER = "%OTP%";
    private static final String VIA_SMS = "SMS";
    private static final String VIA_EMAIL = "EMAIL";
    private static final String VIA_BOTH = "EMAIL|SMS";
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");

    private final OtpRepository repository;
    private final SmsService smsService;
    private final EmailService emailService;
    private final boolean fixedTestOtp;
    private final String emailFrom;
    private final TransactionTemplate transaction;
    private final ObjectProvider<JwtContext> jwtContextProvider;
    private final Clock clock;

    /** Recipients kept for the configured channels; mobiles have no leading '+', emails are upper case. */
    private record Recipients(List<String> mobiles, List<String> emails) {
        List<String> all() {
            return Stream.concat(mobiles.stream(), emails.stream()).toList();
        }
    }

    @Autowired
    @SuppressWarnings("PMD.ExcessiveParameterList")
    public OtpService(final OtpRepository repository, final SmsService smsService, final EmailService emailService,
                      final OtpProperties properties, final EmailProperties emailProperties,
                      @Qualifier("middlewareTransactionManager") final PlatformTransactionManager transactionManager,
                      final Environment environment, final ObjectProvider<JwtContext> jwtContextProvider) {
        this(repository, smsService, emailService, properties, emailProperties, transactionManager, environment,
                jwtContextProvider, Clock.system(DUBAI));
    }

    @SuppressWarnings("PMD.ExcessiveParameterList")
    OtpService(final OtpRepository repository, final SmsService smsService, final EmailService emailService,
               final OtpProperties properties, final EmailProperties emailProperties,
               final PlatformTransactionManager transactionManager, final Environment environment,
               final ObjectProvider<JwtContext> jwtContextProvider, final Clock clock) {
        // A yml literal does not stop APP_OTP_FIXEDTESTOTPENABLED (relaxed binding) from turning this on, so every
        // deployed profile needs the hard guard.
        if (properties.fixedTestOtpEnabled() && environment.matchesProfiles("prod | preprod")) {
            throw new IllegalStateException("app.otp.fixed-test-otp-enabled must be false in prod and preprod");
        }
        final String from = properties.emailFrom() == null ? "" : properties.emailFrom().strip();
        if (emailProperties.allowedSenders().stream().noneMatch(sender -> sender.strip().equalsIgnoreCase(from))) {
            // Otherwise every OTP email would fail at runtime with MGE-V-007.
            throw new IllegalStateException("app.otp.email-from is not an allowed sender");
        }
        this.repository = repository;
        this.smsService = smsService;
        this.emailService = emailService;
        this.fixedTestOtp = properties.fixedTestOtpEnabled();
        this.emailFrom = from;
        this.transaction = new TransactionTemplate(transactionManager);
        this.jwtContextProvider = jwtContextProvider;
        this.clock = clock;
    }

    /**
     * Order: checks and reads -> tx1 (deactivate prior OTPs, insert PENDING active rows) -> delivery with no
     * transaction open -> tx2 (SENT/FAILED + daily counters). A tx2 failure is logged and does not change the
     * response: the delivered OTP is already active and validatable.
     */
    public OtpSendResult send(final OtpSendRequest request) {
        final String correlationId = correlationId();
        final Set<String> delivered = new HashSet<>();
        Recipients recipients = new Recipients(List.of(), List.of());
        String outcome = "ERROR";
        try {
            checkSendFields(request);
            final OtpConfig config = config(request.getServiceName());
            recipients = parse(request.getRecipients(), config.sentVia());
            final List<String> all = recipients.all();
            final LocalDateTime now = LocalDateTime.now(clock);
            final LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
            // ponytail: check-then-send, so concurrent sends can pass the limit together (legacy has the same race).
            if (repository.maxAttemptsSince(config.serviceName(), all, dayStart) >= config.maxAttempts()) {
                throw new OtpException("OTP006", "User exceeded maximum number of attempts");
            }
            final boolean english = "EN".equals(request.getLanguage());
            final String smsTemplate = recipients.mobiles().isEmpty() ? null : smsTemplate(config, english);
            final String[] emailTemplate = recipients.emails().isEmpty() ? null : emailTemplate(config, english);

            final String otp = otp(config);
            final Map<String, Long> ids = insertPending(config, all, TemplatedEmailService.sha256Hex(otp), now,
                    request.getIpAddress());

            if (fixedTestOtp) {
                delivered.addAll(all);
            } else {
                if (smsTemplate != null) {
                    delivered.addAll(sendSms(recipients.mobiles(), smsTemplate.replace(PLACEHOLDER, otp),
                            config.expiryInSeconds(), correlationId));
                }
                if (emailTemplate != null) {
                    delivered.addAll(sendEmails(recipients.emails(), emailTemplate[0].replace(PLACEHOLDER, otp),
                            emailTemplate[1].replace(PLACEHOLDER, otp), correlationId));
                }
            }

            finish(config.serviceName(), all, ids, delivered, now, dayStart, correlationId);
            if (delivered.isEmpty()) {
                throw new OtpException("OTP009", "OTP delivery failed for every recipient");
            }
            outcome = "OK";
            return new OtpSendResult().correlationId(correlationId).channels(channels(recipients, delivered));
        } catch (OtpException e) {
            outcome = e.getCode();
            throw e;
        } finally {
            log.info("OTP send outcome={} correlationId={} serviceName={} language={} invoker={} mobiles={} emails={}"
                            + " delivered={} fixedTestOtp={}", outcome, correlationId,
                    TemplatedEmailService.loggable(request.getServiceName()),
                    TemplatedEmailService.loggable(request.getLanguage()), invoker(), recipients.mobiles().size(),
                    recipients.emails().size(), delivered.size(), fixedTestOtp);
        }
    }

    /**
     * Checks and parsing run outside the transaction; the transaction covers only lock -> compare -> update.
     * OTP007 is thrown after commit so the attempt increment is never rolled back.
     */
    public OtpValidateResult validate(final OtpValidateRequest request) {
        final String correlationId = correlationId();
        int recipientCount = 0;
        String outcome = "ERROR";
        try {
            checkValidateFields(request);
            final OtpConfig config = config(request.getServiceName());
            final List<String> all = parse(request.getRecipients(), config.sentVia()).all();
            recipientCount = all.size();
            final int limit = config.maxValidationAttempts() == null
                    ? DEFAULT_MAX_VALIDATION_ATTEMPTS : config.maxValidationAttempts();
            final byte[] given = request.getHashedOtp().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            final LocalDateTime now = LocalDateTime.now(clock);

            final Boolean valid = transaction.execute(status -> check(config.serviceName(), all, given, limit, now));
            if (!Boolean.TRUE.equals(valid)) {
                throw new OtpException("OTP007", "Invalid OTP");
            }
            outcome = "OK";
            return new OtpValidateResult().correlationId(correlationId).valid(true);
        } catch (OtpException e) {
            outcome = e.getCode();
            throw e;
        } finally {
            log.info("OTP validate outcome={} correlationId={} serviceName={} invoker={} recipients={}", outcome,
                    correlationId, TemplatedEmailService.loggable(request.getServiceName()), invoker(), recipientCount);
        }
    }

    public OtpConfigurationList configurations() {
        return new OtpConfigurationList().correlationId(correlationId()).messages(repository.findAllConfigs().stream()
                .map(c -> new OtpConfiguration()
                        .serviceName(c.serviceName())
                        .expiryInSeconds(c.expiryInSeconds())
                        .tokenLength(c.tokenLength())
                        .maxAttempts(c.maxAttempts())
                        .smsMessageEnglish(c.smsMessageEnglish())
                        .smsMessageArabic(c.smsMessageArabic())
                        .emailMessageEnglish(c.emailMessageEnglish())
                        .emailMessageArabic(c.emailMessageArabic())
                        .sentVia(OtpConfiguration.SentViaEnum.fromValue(c.sentVia())))
                .toList());
    }

    private boolean check(final String serviceName, final List<String> recipients, final byte[] given,
                          final int limit, final LocalDateTime now) {
        final List<OtpCandidate> candidates = repository.lockActiveCandidates(serviceName, recipients, now);
        if (candidates.stream().anyMatch(c -> c.validationAttempts() >= limit)) {
            // No write has happened yet, so the rollback this causes is harmless.
            throw new OtpException("OTP008", "User exceeded maximum number of validation attempts");
        }
        final List<OtpCandidate> matched = candidates.stream()
                .filter(c -> MessageDigest.isEqual(c.otpHash().getBytes(StandardCharsets.US_ASCII), given))
                .toList();
        if (!matched.isEmpty()) {
            repository.markValidated(matched.stream().map(OtpCandidate::id).toList());
            // Only the recipients that matched: resetting every listed recipient would let a caller clear a
            // victim's daily send cap by validating its own OTP with the victim listed alongside.
            repository.resetAttempts(serviceName, matched.stream().map(OtpCandidate::mobileEmail).distinct().toList());
            return true;
        }
        if (!candidates.isEmpty()) {
            repository.incrementValidationAttempts(candidates.stream().map(OtpCandidate::id).toList());
        }
        return false;
    }

    private Map<String, Long> insertPending(final OtpConfig config, final List<String> recipients, final String hash,
                                            final LocalDateTime now, final String ipAddress) {
        final Map<String, Long> ids = new LinkedHashMap<>();
        transaction.executeWithoutResult(status -> {
            repository.deactivateActive(config.serviceName(), recipients);
            for (String recipient : recipients) {
                ids.put(recipient, repository.insertOtp(new OtpRow(config.serviceName(), recipient, hash, now,
                        now.plusSeconds(config.expiryInSeconds()), ipAddress)));
            }
        });
        return ids;
    }

    private void finish(final String serviceName, final List<String> recipients, final Map<String, Long> ids,
                        final Set<String> delivered, final LocalDateTime now, final LocalDateTime dayStart,
                        final String correlationId) {
        final Map<Boolean, List<Long>> byDelivery = ids.entrySet().stream().collect(Collectors.partitioningBy(
                e -> delivered.contains(e.getKey()), Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
        final List<Long> sent = byDelivery.get(true);
        final List<Long> failed = byDelivery.get(false);
        try {
            transaction.executeWithoutResult(status -> {
                if (!sent.isEmpty()) {
                    repository.markSent(sent);
                }
                if (!failed.isEmpty()) {
                    repository.markFailed(failed);
                }
                if (!sent.isEmpty()) {
                    recipients.forEach(r -> repository.upsertAttempt(serviceName, r, now, dayStart));
                }
            });
        } catch (RuntimeException e) {
            // The rows stay PENDING/active: a delivered OTP still validates. Only the daily counter is lost.
            log.error("OTP record failed correlationId={} error={}", correlationId, e.getClass().getSimpleName());
        }
    }

    /** Delivered numbers; any failure (bad input, provider) leaves the numbers undelivered. */
    private Set<String> sendSms(final List<String> mobiles, final String text, final int expiryInSeconds,
                                final String correlationId) {
        try {
            return smsService.sendWithOutcome(new SmsSendRequest().messageBody(text).mobileNumbers(mobiles)
                            .msgCategory(SMS_MSG_CATEGORY).expiryInSecs(expiryInSeconds))
                    .result().getResults().stream()
                    .filter(r -> r.getStatus() == SmsRecipientResult.StatusEnum.SENT)
                    .map(SmsRecipientResult::getMobileNumber)
                    .collect(Collectors.toSet());
        } catch (RuntimeException e) {
            log.warn("OTP SMS failed code={} correlationId={}",
                    e instanceof SmsException sms ? sms.getCode() : e.getClass().getSimpleName(), correlationId);
            return Set.of();
        }
    }

    /** One call per address, so recipients never see each other and each gets its own status. */
    private Set<String> sendEmails(final List<String> emails, final String subject, final String body,
                                   final String correlationId) {
        final Set<String> sent = new HashSet<>();
        for (String address : emails) {
            try {
                emailService.sendEmail(new EmailRequest().to(address).from(emailFrom).subject(subject).body(body));
                sent.add(address);
            } catch (EmailValidationException e) {
                log.error("OTP email misconfiguration {} correlationId={}", e.getCode(), correlationId);
            } catch (RuntimeException e) {
                log.warn("OTP email failed code={} correlationId={}",
                        e instanceof EmailServiceException gateway ? gateway.getCode() : e.getClass().getSimpleName(),
                        correlationId);
            }
        }
        return sent;
    }

    private static List<OtpChannelResult> channels(final Recipients recipients, final Set<String> delivered) {
        final List<OtpChannelResult> channels = new ArrayList<>();
        if (!recipients.mobiles().isEmpty()) {
            channels.add(channel(OtpChannelResult.ChannelEnum.SMS, recipients.mobiles(), delivered));
        }
        if (!recipients.emails().isEmpty()) {
            channels.add(channel(OtpChannelResult.ChannelEnum.EMAIL, recipients.emails(), delivered));
        }
        return channels;
    }

    private static OtpChannelResult channel(final OtpChannelResult.ChannelEnum channel, final List<String> recipients,
                                            final Set<String> delivered) {
        return new OtpChannelResult().channel(channel).status(recipients.stream().anyMatch(delivered::contains)
                ? OtpChannelResult.StatusEnum.SENT : OtpChannelResult.StatusEnum.FAILED);
    }

    private OtpConfig config(final String serviceName) {
        return repository.findConfig(serviceName).orElseThrow(() -> new OtpException("OTP002", "Invalid OTP service"));
    }

    private static void checkSendFields(final OtpSendRequest request) {
        final List<String> missing = new ArrayList<>();
        if (isBlank(request.getServiceName())) {
            missing.add("serviceName");
        }
        if (tokens(request.getRecipients()).isEmpty()) {
            missing.add("recipients");
        }
        if (!"EN".equals(request.getLanguage()) && !"AR".equals(request.getLanguage())) {
            missing.add("language");
        }
        if (request.getIpAddress() == null || !IP_ADDRESS.matcher(request.getIpAddress()).matches()) {
            missing.add("ipAddress");
        }
        failIfMissing(missing);
    }

    private static void checkValidateFields(final OtpValidateRequest request) {
        final List<String> missing = new ArrayList<>();
        if (isBlank(request.getServiceName())) {
            missing.add("serviceName");
        }
        if (tokens(request.getRecipients()).isEmpty()) {
            missing.add("recipients");
        }
        if (request.getHashedOtp() == null || !HASH.matcher(request.getHashedOtp()).matches()) {
            missing.add("hashedOtp");
        }
        failIfMissing(missing);
    }

    private static void failIfMissing(final List<String> missing) {
        if (!missing.isEmpty()) {
            throw new OtpException("OTP001", "Request failed due to missing: " + String.join(", ", missing));
        }
    }

    /** Every token must be an email or a mobile; only the channels in sentVia are kept (spec 3.1 parsing). */
    private static Recipients parse(final String value, final String sentVia) {
        final Set<String> mobiles = new LinkedHashSet<>();
        final Set<String> emails = new LinkedHashSet<>();
        for (String token : tokens(value)) {
            if (TemplatedEmailService.EMAIL.matcher(token).matches()) {
                emails.add(token);
                continue;
            }
            final String number = token.startsWith("+") ? token.substring(1) : token;
            if (!SmsServiceImpl.isValidMobile(number)) {
                throw token.indexOf('@') >= 0 ? invalidEmail() : invalidMobile();
            }
            mobiles.add(number);
        }
        if (VIA_SMS.equals(sentVia) && mobiles.isEmpty()) {
            throw invalidMobile();
        }
        if (VIA_EMAIL.equals(sentVia) && emails.isEmpty()) {
            throw invalidEmail();
        }
        final boolean sms = VIA_SMS.equals(sentVia) || VIA_BOTH.equals(sentVia);
        final boolean email = VIA_EMAIL.equals(sentVia) || VIA_BOTH.equals(sentVia);
        if (!sms && !email) {
            throw new OtpException("OTP010", "OTP template misconfiguration: unknown sent_via");
        }
        final Recipients kept = new Recipients(sms ? List.copyOf(mobiles) : List.of(), email ? List.copyOf(emails) : List.of());
        if (kept.all().size() > MAX_RECIPIENTS) {
            throw new OtpException("OTP005", "Number of recipients exceeds " + MAX_RECIPIENTS);
        }
        return kept;
    }

    private static List<String> tokens(final String value) {
        return value == null ? List.of() : Arrays.stream(SEPARATORS.split(value.strip().toUpperCase(Locale.ROOT)))
                .filter(t -> !t.isEmpty()).distinct().toList();
    }

    private static String smsTemplate(final OtpConfig config, final boolean english) {
        final String template = english ? config.smsMessageEnglish() : config.smsMessageArabic();
        if (isBlank(template)) {
            throw misconfigured("no " + language(english) + " SMS template");
        }
        return template;
    }

    /** {subject, body}, split on the first ^+^ and trimmed. */
    private static String[] emailTemplate(final OtpConfig config, final boolean english) {
        final String template = english ? config.emailMessageEnglish() : config.emailMessageArabic();
        final int split = template == null ? -1 : template.indexOf(SUBJECT_BODY_DELIMITER);
        if (split < 0) {
            throw misconfigured("no " + language(english) + " email template with subject ^+^ body");
        }
        final String subject = template.substring(0, split).strip();
        final String body = template.substring(split + SUBJECT_BODY_DELIMITER.length()).strip();
        if (subject.isEmpty() || body.isEmpty()) {
            throw misconfigured("blank " + language(english) + " email subject or body");
        }
        return new String[]{subject, body};
    }

    private String otp(final OtpConfig config) {
        if (fixedTestOtp) {
            return config.serviceName().endsWith("_") ? "123456" : "1234";
        }
        final StringBuilder otp = new StringBuilder(config.tokenLength());
        for (int i = 0; i < config.tokenLength(); i++) {
            otp.append(RANDOM.nextInt(10));
        }
        return otp.toString();
    }

    private static OtpException misconfigured(final String detail) {
        return new OtpException("OTP010", "OTP template misconfiguration: " + detail);
    }

    private static OtpException invalidMobile() {
        return new OtpException("OTP003", "Invalid mobile number");
    }

    private static OtpException invalidEmail() {
        return new OtpException("OTP004", "Invalid email");
    }

    private static String language(final boolean english) {
        return english ? "EN" : "AR";
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private String invoker() {
        final JwtContext context = jwtContextProvider.getIfAvailable();
        final String application = context == null ? null : context.getCurrentApplication();
        return application == null ? "unknown" : TemplatedEmailService.loggable(application);
    }

    private static String correlationId() {
        final String correlationId = MDCUtil.getCorrelationId();
        return correlationId != null ? correlationId : UUID.randomUUID().toString();
    }
}
