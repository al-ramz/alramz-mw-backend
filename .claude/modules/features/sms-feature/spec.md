# Spec: sms-feature (Part 4 OTP: sendOTP / validateOTP / getMessagesLookup)

Sources:
- `.claude/modules/features/sms-feature/source-design-doc.md`: the user's request, the gap analysis, 8 authoritative user decisions, and the live `OTP_CONFIGURATION` DDL.
- `.claude/modules/features/notification-graph-gateway/source-design-doc.md`, Part 4 (OTP) §1–§8.
- Features this one builds on. Both are implemented and uncommitted in `services/alramz-notification-service`:
  - `notification-graph-gateway`: `EmailService#sendEmail`, `GenericResponse`, the sender allow-list, `APP_NOTIFICATION`.
  - `notifications-sms-email`: `SmsService`, Liquibase 001–003, the middleware datasource, `sql-queries.xml`, the starter scheduler.

No new blocking gap was found, so `AskUserQuestion` was not used (section 7).

---

## 1. Feature Objective & Scope

**Problem.** `alramz-notification-service` can send email (Graph) and SMS (Etisalat/Waselney), but it has no OTP capability. The "SMS configurations table" the user could not find is `OTP_CONFIGURATION` (decision 2). That table, `OTP_MANAGEMENT`, `OTP_ATTEMPTS`, and the three legacy OTP operations are all missing.

**Target service:** `services/alramz-notification-service` only. It is an existing service, so no scaffold is needed.

**Triggers:**

| # | Trigger | What it does |
|---|---|---|
| T1 | `POST /api/v1/otp/send` (HTTP, JWT) | Generates an OTP, stores its hash, and sends it by SMS and/or email per `sent_via` |
| T2 | `POST /api/v1/otp/validate` (HTTP, JWT) | Compares the caller's hashed OTP with the active, unexpired OTP. Runs in one transaction |
| T3 | `GET /api/v1/otp/configurations` (HTTP, JWT) | Returns every `otp_configuration` row (legacy `getMessagesLookup`) |
| T4 | Starter-scheduler job `otpCleanupJobRunner` (FIXED_DELAY, hourly) | Deactivates expired active OTP rows. This replaces the legacy global UPDATE that ran on every call |

**Done means:**
- The three endpoints return `GenericResponse` with real HTTP statuses.
- A send writes one `otp_management` row per recipient and enforces `max_attempts` per Asia/Dubai day, per recipient.
- A validate is atomic and limits attempts per OTP.
- Fixed test OTPs work in dev/docker/test, and the service cannot start in prod with them enabled.

**In scope:**
- `specs/openapi.yaml` additions, and `pom.xml` `modelsToGenerate`.
- Changesets `004`/`005` in all 5 profile folders.
- `OtpProperties`, `OtpException`, `OtpRepository`, `OtpService`, `OtpController`, `OtpCleanupJob`.
- One handler arm, config, `sql-queries.xml` keys.
- `apiCollection.http` blocks, and tests.

**Out of scope (explicit):**
- Legacy-path adapters (`/sendOTP`, `/validateOTP`, `/getMessagesLookup`) and legacy numeric response codes (decision 5).
- Seed rows for `otp_configuration` (decision 4). It is business-owned data, loaded by ops.
- A separate `sms_configuration` table (decision 2). SMS provider routing stays in `app.sms.*`.
- HMAC or salting of the OTP hash. It would break the client contract (Part 4 §8), so it is a future item.
- Retention/purge of old `otp_management` rows. No retention rule was given (section 9).
- Any change to `SmsServiceImpl` routing, `EmailServiceImpl`, `alramz-api-starter` (including `SensitiveDataMasker`), `infra/`, `k8s/`, or `.github/`.

---

## 2. Repository Grounding

All paths are under `services/alramz-notification-service/` unless stated otherwise.

| Concern | Existing artifact (verified) | How this spec uses it |
|---|---|---|
| Contract | `specs/openapi.yaml` (OpenAPI 3.0.3). Paths carry the full `/api/v1` prefix (`/api/v1/email/send`, `/api/v1/email/template/send`, `/api/v1/sms/send`, `/api/v1/sms/report/{messageId}`). Root `security: - BearerAuth: []`. Schemas include `GenericResponse` and `EmailSendResult` | **Amend.** Add 3 paths under tag `Otp`, which generates the interface `com.alramz.api.OtpApi`. The full `/api/v1` prefix follows the file's local precedent, recorded by both prior specs, rather than the checklist's bare-path rule. |
| Codegen | `pom.xml`: `<useTags>true</useTags>`; `modelsToGenerate=GenericResponse,…,SmsReportResult` (10 models); `useBeanValidation=false`. Aliases use `x-field-extra-annotation: '@com.fasterxml.jackson.annotation.JsonAlias({...})'` (`openapi.yaml` lines 737/832) | Append 7 OTP models (section 3.5). Legacy field names become `@JsonAlias`. All checks are manual in the service. |
| Controllers | `controllers/SmsController implements SmsApi`. `@JwtSecured(roles = "APP_NOTIFICATION")` on each method. No `@Loggable` ("it would log the message body") | New `controllers/OtpController implements OtpApi`, in the same style. No `@Loggable`: the request carries the hash and recipients. |
| Exceptions + advice | `exception/GlobalExceptionHandler`: `@RestControllerAdvice(basePackages="com.alramz.controllers")`, `@Order(0)`. A per-exception code→status switch plus `codedOrLogged` (4xx carry code/msg; 5xx are logged and carry none). `SmsException`/`TemplatedEmailException` are `(code, message)` | New `exception/OtpException(code, message)` and one `handleOtp` arm using `codedOrLogged`. |
| Email gateway (in-process) | `service/EmailService#sendEmail(EmailRequest)`. It throws `EmailValidationException` (MGE-V-0xx; V-007 = sender not in `app.email.allowed-senders`) or `EmailServiceException` (MGE-B-00x). An `EmailConfigurationException` is thrown when there is no Graph client | OTP email goes through it, **one call per email recipient**, so recipients never see each other and status is per recipient. |
| SMS gateway (in-process) | `service/SmsService#sendWithOutcome(SmsSendRequest)`. Returns per-number `SmsRecipientResult{status SENT/FAILED}`. Throws `SmsException(SMS001..005)` for bad input only. Simulated when `app.sms.live-send-enabled=false`. `SmsProperties.Defaults` javadoc: "the dispatch job and the OTP feature may pass different values" | OTP SMS is one `sendWithOutcome` call with `msgCategory` = `app.otp.sms-msg-category` (`4.5`, Part 4 §3) and `expiryInSecs` = the config's `expiry_in_seconds`. |
| Validators (reuse) | `service/TemplatedEmailService`: `private static final Pattern EMAIL` (identical to `EmailServiceImpl.EMAIL`), package-private `static String sha256Hex(String)` (lowercase hex, UTF-8), package-private `static String loggable(String)`. `service/impl/SmsServiceImpl`: `private static final Pattern NUMBER = ^[1-9][0-9]{7,14}$`, `public static String mask(String)` | **Reuse, do not copy.** Widen `TemplatedEmailService.EMAIL` to package-private (`OtpService` lives in the same package, `com.alramz.service`). Add `public static boolean isValidMobile(String)` to `SmsServiceImpl`, wrapping `NUMBER`, in the same style as the already-public `mask`. `sha256Hex`, `loggable` and `mask` are used as they are. |
| Datasource / tx | `config/DatasourceConfiguration`: `@Primary` `DataSource` and `PlatformTransactionManager` re-export the middleware beans. `repository/EmailTemplateRepository`: `@Qualifier("middlewareNamedParameterJdbcTemplate")` + `SqlQueriesManager` | New `repository/OtpRepository`, using the same injection. Transactions use a `TransactionTemplate` over the `@Primary` (middleware) `PlatformTransactionManager`, following the `SmsTableRepository`/`MmfServiceImpl` precedent. |
| SQL text | `src/main/resources/sql/sql-queries.xml` (`email.*`, `sms.*` keys, `:named` binds only). `sql.file` is set in `application.yml` | Add the `otp.*` keys (section 4). |
| Liquibase | `db/changelog/{dev,docker,test,preprod,prod}/sql/00{1,2,3}-notification-*.sql`, byte-identical per name. Author `gaurav`, id = filename stem. `003` uses target-less `ON CONFLICT DO NOTHING` (D-2: H2 PostgreSQL mode rejects `ON CONFLICT (cols)`). No other service has `otp_*` tables (grep of `services/`) | The next files are `004-notification-otp-tables.sql` and `005-notification-otp-cleanup-job.sql`, in all 5 folders. Every SQL statement must also run on H2 `MODE=PostgreSQL`, so there are no partial indexes and no `ON CONFLICT (cols)`. |
| Scheduler | `@EnableScheduler(jobGroupName = "notification")` on `AlramzNotificationServiceApplication`. `scheduler/job/SmsDispatchJob` (`@Component("smsDispatchJobRunner") implements Schedulable`, never throws). Starter `JobScheduleManager` supports `FIXED_DELAY` (ms), `FIXED_RATE` and `CRON_EXP` (`new CronTrigger(expr)`, JVM default zone) | New `scheduler/job/OtpCleanupJob` (`otpCleanupJobRunner`), FIXED_DELAY. CRON was **not** chosen because its zone is the pod's JVM zone, not Asia/Dubai. |
| Time | `TemplatedEmailService`/`SmsDispatchJob`: `Clock.system(ZoneId.of("Asia/Dubai"))`, injectable for tests, timestamps stored as Dubai `LocalDateTime` | Same for every OTP timestamp and for the daily window. |
| Config | `application.yml`: `app.email.allowed-senders: ${APP_EMAIL_ALLOWED_SENDERS:notification@alramz.ae}`. `application-prod.yml`: the same key with default `cm@alramz.ae`. dev/docker/test/preprod: `notification@alramz.ae`. `app.sms.*` lives only in `application.yml` (amendment A-1) | New `config/OtpProperties` (record, `app.otp`), added to `@EnableConfigurationProperties`. Most keys go in `application.yml`; the per-profile values are in section 5. |
| Caller / correlation | `JwtContext#getCurrentApplication()` (invoker, `"unknown"` when null), `MDCUtil#getCorrelationId()` (or a new UUID) | Used for log lines and `correlationId` only. The OTP tables have no invoker column (legacy). |
| JWT | `company.jwt.permit-all-urls` = `/api/v1/info` (+ `/actuator/prometheus` in dev). `src/test/java/com/alramz/config/ProfileSecurityConfigTest` asserts non-public paths for all 6 yml files | No permit-all change. Extend the test to the 3 OTP paths. |
| Masking | Starter `audit/SensitiveDataMasker`: the key set has no `otp`/`hashedotp`. Request/response payload logging is off in this service, and no OTP controller method uses `@Loggable` | No starter change (out of scope). The service guarantees in code that the OTP and hash are never logged (section 5). |
| Sizing | `service.yaml`: 0.5 CPU, 1Gi, 1–5 replicas | No change. The OTP calls are light; this is a question only (section 9). |

---

## 3. Interface Contracts

**Common facts** (precedent from the two prior features, plus decision 5):
- **Security:** JWT, `@JwtSecured(roles = "APP_NOTIFICATION")` on every method. This is the secured-by-default standard, applied.
- **Envelope:** `GenericResponse {responseCode, responseMessage, errorCode?, errorMsg?, response}`.
  - `responseCode` is the HTTP status as a string.
  - `errorCode`/`errorMsg` are filled only for 4xx.
  - On errors, `response` = `EmailSendResult{correlationId}` (the existing handler shape).
- **Correlation:** taken from the `X-Correlation-ID` header. A body `correlationID` is ignored (lenient unknown properties, as in prior features).
- **401:** produced by the starter security chain before the controller. It gets a description only in `openapi.yaml`.
- **403:** role failure → `AccessDeniedException` arm, `errorCode` null.
- **Malformed JSON:** → existing service-wide 400 `MGE-V-006` "Malformed JSON request".
- **No `/api/v1` legacy aliases.**

### 3.1 T1: `POST /api/v1/otp/send` (`operationId: sendOtp`, tag `Otp`)

**Request `OtpSendRequest`:**

| Field | Alias (`@JsonAlias`) | Type | Required | Rules |
|---|---|---|---|---|
| `serviceName` | `key` | string | yes | Not blank. Exact, case-sensitive match on `otp_configuration.service_name` |
| `recipients` | `value` | string | yes | Not blank. Parsed per "Recipient parsing" below. Example `user@example.com\|971500000001` |
| `language` | — | string, doc `pattern: '^(EN\|AR)$'` | yes | Exactly `EN` or `AR`. Anything else is OTP001 (legacy gotcha: restrict to EN/AR). It is not a generated enum (D-8 precedent), so a bad value is OTP001, not MGE-V-006 |
| `ipAddress` | — | string, `maxLength: 45` | yes | The end user's IP, supplied by the caller (legacy). Must match `^[0-9A-Fa-f.:]{2,45}$`, which covers IPv4, IPv6 and IPv4-mapped IPv6 |

**Recipient parsing** (shared by T1 and T2, in `OtpService`):
1. `strip()`, then `toUpperCase(Locale.ROOT)`. Legacy stores and compares emails in upper case.
2. Split on `[|;,/\s]+`, drop empty tokens, de-duplicate in order.
3. Classify each token:
   - It matches `TemplatedEmailService.EMAIL` → email.
   - Otherwise, after stripping one leading `+`, it satisfies `SmsServiceImpl.isValidMobile` → mobile.
   - Otherwise → **reject**. The first such token gives `OTP004` if it contains `@`, else `OTP003`. This is a deliberate change from legacy, which silently ignored such tokens (section 9).
4. Keep only the channels in `sent_via`:
   - `SMS`: mobiles only. If there are none → `OTP003`.
   - `EMAIL`: emails only. If there are none → `OTP004`.
   - `EMAIL|SMS`: both kinds. At least one is guaranteed by step 3.
   Tokens for a channel not in `sent_via` are ignored, as in legacy.
5. If the kept count > `app.otp.max-recipients` (5) → `OTP005`.

The kept list is "the recipients" for every DB read and write below.

**Processing:**
1. **Field checks (collect).** Check in the order `serviceName, recipients, language, ipAddress`. If any field fails → `OTP001 "Request failed due to missing: <fields>"`.
2. **Load the config** by `service_name`. If there is none → `OTP002`.
3. **Parse recipients** as above (OTP003/004/005).
4. **Daily send limit.** The day is the current Asia/Dubai day, starting at `dayStart`.
   - `used = COALESCE(MAX(attempts), 0)` over `otp_attempts` rows for (service, recipients) with `last_attempt_date >= dayStart`.
   - If `used >= max_attempts` → `OTP006` (429).
   - This replaces the legacy *global* `UPDATE OTP_ATTEMPTS SET ATTEMPTS=0 …` that ran on every call: the reset is now per user, at read time, so no write and no job is needed.
5. **Build the messages** (before any send; failures are `OTP010`, logged as template misconfiguration):
   - **SMS** (when there are mobiles): `sms_message_english`/`sms_message_arabic` by `language`. If it is null or blank → OTP010.
   - **Email** (when there are emails): `email_message_english`/`email_message_arabic`, split on the **first** literal `^+^` into subject and body, both trimmed. If the template is null, has no delimiter, or the subject or body is blank → OTP010.
   - `%OTP%` is replaced (every occurrence, `String.replace`) in the SMS text and in the email subject and body.
6. **Generate the OTP:**
   - If `app.otp.fixed-test-otp-enabled` is true: `123456` when `service_name` ends with `_`, else `1234` (decision 8).
   - Otherwise: `token_length` digits from a static `SecureRandom`. Each digit is uniform 0–9, so leading zeros are allowed.
   - `hash = TemplatedEmailService.sha256Hex(otp)`: SHA-256 over UTF-8, lowercase hex (decision 6).
   - Timestamps: `creation = now`, `expiry = now + expiry_in_seconds`.
7. **Deliver:**
   - **Fixed-test mode:** no SMS or email call; every recipient counts as delivered (decision 8).
   - **SMS:** one `smsService.sendWithOutcome(new SmsSendRequest().messageBody(sms).mobileNumbers(mobiles).msgCategory(app.otp.sms-msg-category).expiryInSecs(expiry_in_seconds))` call.
     - Each mobile is SENT or FAILED from its `SmsRecipientResult`.
     - If `app.sms.live-send-enabled=false`, `SmsService` already simulates SENT (existing behaviour).
   - **Email:** for each email address, call `emailService.sendEmail(new EmailRequest().to(addr).from(app.otp.email-from).subject(subject).body(body))`. Returning normally means SENT.
   - **Failure rule, both channels:** any `RuntimeException` from a channel call marks that call's recipients FAILED. This covers `SmsException`, `EmailServiceException`, `EmailValidationException` and `EmailConfigurationException`.
     - The failure is logged with its code or class name only, never the message or cause (prior-feature rule: messages can carry URLs).
     - An `EmailValidationException` is logged at ERROR as `OTP email misconfiguration <MGE code>`. Example: the sender is not allowed (V-007) or the body is too long (V-003).
   - There is no retry.
8. **Record** (one middleware transaction, `TransactionTemplate`):
   - (a) `UPDATE otp_management SET active='0' WHERE service_name=:key AND mobile_email IN (:recipients) AND active='1'`. This deactivates previous OTPs, scoped by recipient. Errors are **not** swallowed.
   - (b) Insert one `otp_management` row per recipient:
     - `active='1'`, `status='SENT'` if delivered;
     - otherwise `active='0'`, `status='FAILED'`;
     - plus the hash, `creation_date_time`, `expiry_date_time`, `ip_address`, and `validation_attempts=0`.
   - (c) **Only if at least one recipient was delivered:** for each recipient, upsert `otp_attempts`:
     - First `UPDATE … SET attempts = CASE WHEN last_attempt_date < :dayStart THEN 1 ELSE attempts + 1 END, last_attempt_date = :now WHERE service_name=:key AND mobile_email=:r`.
     - If 0 rows were updated → `INSERT (…, attempts=1, last_attempt_date=:now)`.
     - On `DuplicateKeyException` (a concurrent insert), repeat the UPDATE once.
   - If the record transaction throws, the OTP may already have been delivered but cannot be validated. The result is `OTP010` 500, logged (section 9).
9. **Outcome** (decision 7):
   - If at least one recipient was delivered → **200**, with per-channel status.
     - A channel is `SENT` if at least one of its recipients was delivered, else `FAILED`.
     - Only channels that had recipients are listed.
   - If no recipient was delivered → `OTP009` **502**, logged, thrown after step 8 commits. The FAILED rows are kept, and `otp_attempts` is unchanged.
10. **Log** one line per request:
    - It holds: `outcome`, `correlationId`, `serviceName` (via `loggable`), `language`, `invoker`, the mobile/email counts, the delivered counts, and `fixedTestOtp`.
    - It **never** holds the OTP, the hash, the rendered text, email addresses, or unmasked numbers.

**Response `OtpSendResult`** (inside `GenericResponse.response`):
```yaml
OtpSendResult:
  required: [correlationId, channels]
  correlationId: string
  channels: OtpChannelResult[]       # SMS first, then EMAIL; only channels that had recipients
OtpChannelResult:
  required: [channel, status]
  channel: string, enum [SMS, EMAIL]
  status: string, enum [SENT, FAILED]
```

**Statuses produced:** 200, 400, 401, 403, 429, 500, 502.
- There is **no 503**: a provider or Graph outage is a channel FAILED, so an all-failed send is 502 (decision 7).

**Examples** (each goes into `openapi.yaml` as a real `example`):
```jsonc
// 200 (partial delivery: decision 7)
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "5b1d0c3e-8a2f-4c6e-9d1b-7e3f2a4c5d6e",
    "channels": [ { "channel": "SMS", "status": "SENT" }, { "channel": "EMAIL", "status": "FAILED" } ] } }
// 400
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "OTP001",
  "errorMsg": "Request failed due to missing: language, ipAddress", "response": { "correlationId": "5b1d…" } }
{ … "errorCode": "OTP002", "errorMsg": "Invalid OTP service" … }
{ … "errorCode": "OTP003", "errorMsg": "Invalid mobile number" … }
{ … "errorCode": "OTP004", "errorMsg": "Invalid email" … }
{ … "errorCode": "OTP005", "errorMsg": "Number of recipients exceeds 5" … }
// 403 (role): errorCode null
// 429
{ "responseCode": "429", "responseMessage": "Too Many Requests", "errorCode": "OTP006",
  "errorMsg": "User exceeded maximum number of attempts", "response": { "correlationId": "5b1d…" } }
// 500 (OTP010, logged): errorCode null
{ "responseCode": "500", "responseMessage": "Internal Server Error", "errorCode": null, "errorMsg": null, "response": { "correlationId": "5b1d…" } }
// 502 (OTP009, logged): errorCode null
{ "responseCode": "502", "responseMessage": "Bad Gateway", "errorCode": null, "errorMsg": null, "response": { "correlationId": "5b1d…" } }
```

### 3.2 T2: `POST /api/v1/otp/validate` (`operationId: validateOtp`, tag `Otp`)

**Request `OtpValidateRequest`:**

| Field | Alias | Type | Required | Rules |
|---|---|---|---|---|
| `serviceName` | `key` | string | yes | As in 3.1 |
| `recipients` | `value` | string | yes | Same parsing as in 3.1, including the `sent_via` filter, OTP003/004/005 |
| `hashedOtp` | `hashedOTP` | string, doc `pattern: '^[0-9a-fA-F]{64}$'` | yes | 64 hex characters, **compared case-insensitively** (decision 6). A malformed value is OTP001 and does not count as an attempt |

**Processing.** One `TransactionTemplate` over the middleware tx manager covers steps 1–6 (legacy gotcha: single transaction):
1. Field checks, in the order `serviceName, recipients, hashedOtp` → OTP001.
2. Load the config. If there is none → **`OTP002` (400)**. This fixes the legacy behaviour, which returned 500 here.
3. Parse recipients (OTP003/004/005).
4. Lock the candidates:
   ```sql
   SELECT id, otp, validation_attempts FROM otp_management
    WHERE service_name = :key AND mobile_email IN (:recipients)
      AND active = '1' AND expiry_date_time > :now
    ORDER BY id DESC FOR UPDATE
   ```
   - The expiry filter replaces the legacy global "deactivate all expired" UPDATE on every call. Correctness never depends on the cleanup job.
   - `FOR UPDATE` serialises concurrent guesses against the same OTP, so the attempt count cannot be raced.
5. Let `limit = max_validation_attempts`, or `app.otp.default-max-validation-attempts` (3) when it is null (legacy gotcha). If any candidate has `validation_attempts >= limit` → `OTP008` (429). There are no writes before this point, so the rollback is harmless.
6. Compare. Let `matched` = the candidates whose `otp` equals `hashedOtp.toLowerCase(Locale.ROOT)`, compared with `MessageDigest.isEqual` on ASCII bytes (constant time).
   - **If `matched` is non-empty:**
     - `UPDATE otp_management SET active='0', status='VALIDATED' WHERE id IN (:matchedIds)`. Deactivation is by **row id**, not hash + service (legacy gotcha).
     - Then `UPDATE otp_attempts SET attempts=0 WHERE service_name=:key AND mobile_email IN (:recipients)`. This is the legacy "reset counters" step, now per user.
     - Commit and return VALID.
   - **If there are no candidates, or no match:**
     - `UPDATE otp_management SET validation_attempts = validation_attempts + 1 WHERE id IN (:candidateIds)`. This is skipped when there are no candidates.
     - Commit and return INVALID.
7. **After the commit:** VALID → 200. INVALID → throw `OTP007` (400). The exception is thrown outside the transaction, so the attempt increment is never rolled back.
8. **Log:** `outcome`, `correlationId`, `serviceName` (`loggable`), `invoker`, the recipient count. Never the hash.

**Response `OtpValidateResult`:** `{ correlationId: string, valid: boolean }`. `valid` is always `true` on 200; it is kept so that legacy consumers can map `response.valid` directly.

**Statuses produced:** 200, 400, 401, 403, 429, 500. There is no downstream call, so there is no 502 or 503.

```jsonc
// 200
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "5b1d…", "valid": true } }
// 400 (OTP001..OTP005, OTP007)
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "OTP007", "errorMsg": "Invalid OTP", "response": { "correlationId": "5b1d…" } }
{ … "errorCode": "OTP001", "errorMsg": "Request failed due to missing: hashedOtp" … }
{ … "errorCode": "OTP002", "errorMsg": "Invalid OTP service" … }
// 429
{ "responseCode": "429", "responseMessage": "Too Many Requests", "errorCode": "OTP008",
  "errorMsg": "User exceeded maximum number of validation attempts", "response": { "correlationId": "5b1d…" } }
// 403 / 500: errorCode null
```

The invalid OTP case is **400, not 401**. Part 4 §7 allows either, but 401 in this service means "the caller app's JWT was rejected". A 401 here would trip consumers' token-refresh logic.

### 3.3 T3: `GET /api/v1/otp/configurations` (`operationId: getOtpConfigurations`, tag `Otp`)

- **Input:** none.
- **Query:** `SELECT service_name, expiry_in_seconds, token_length, max_attempts, sms_message_english, sms_message_arabic, email_message_english, email_message_arabic, sent_via FROM otp_configuration ORDER BY service_name`.
- **Result:** an empty table gives 200 with `messages: []`. `max_validation_attempts` is not returned (legacy).
- **No audit row.** No log line beyond the starter's request log.

**Response `OtpConfigurationList`:** `{ correlationId, messages: OtpConfiguration[] }`.

`OtpConfiguration`:
- `serviceName` (string), `expiryInSeconds` (int), `tokenLength` (int), `maxAttempts` (int);
- `smsMessageEnglish`, `smsMessageArabic`, `emailMessageEnglish`, `emailMessageArabic` (string, nullable);
- `sentVia` (string, enum `SMS`/`EMAIL`/`EMAIL|SMS`).

**Statuses produced:** 200, 401, 403, 500.
- There is no 400: there is no input to validate.
- There is no 503: a middleware DB failure is the generic 500, as in `EmailTemplateRepository` today.

```jsonc
// 200
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "5b1d…", "messages": [ { "serviceName": "LOGIN_OTP", "expiryInSeconds": 300, "tokenLength": 6,
    "maxAttempts": 5, "smsMessageEnglish": "Your OTP is %OTP%", "smsMessageArabic": "رمز التحقق %OTP%",
    "emailMessageEnglish": "Your OTP ^+^ <p>Your OTP is %OTP%</p>", "emailMessageArabic": null, "sentVia": "EMAIL|SMS" } ] } }
// 500: errorCode null
```

### 3.4 Error codes (prefix `OTP`, following the `EML`/`SMS` precedent; Part 4 §7 gives the HTTP statuses)

| Code | HTTP | In body | errorMsg / log text | Legacy |
|---|---|---|---|---|
| OTP001 | 400 | yes | `Request failed due to missing: <fields>` (missing or invalid) | 1025 |
| OTP002 | 400 | yes | `Invalid OTP service` | 1135 (validate: legacy 500) |
| OTP003 | 400 | yes | `Invalid mobile number` | 1060 |
| OTP004 | 400 | yes | `Invalid email` | 1068 |
| OTP005 | 400 | yes | `Number of recipients exceeds <max>` | new |
| OTP006 | 429 | yes | `User exceeded maximum number of attempts` | 1017 |
| OTP007 | 400 | yes | `Invalid OTP` | 1005 |
| OTP008 | 429 | yes | `User exceeded maximum number of validation attempts` | 1133 |
| OTP009 | 502 | no (logged) | `OTP delivery failed for every recipient` | 402 |
| OTP010 | 500 | no (logged with stack) | `OTP template misconfiguration <detail>` / `OTP record failed` | 500 |

`GlobalExceptionHandler.handleOtp`:
- OTP001–005 and OTP007 → `BAD_REQUEST`;
- OTP006 and OTP008 → `TOO_MANY_REQUESTS`;
- OTP009 → `BAD_GATEWAY`;
- default → `INTERNAL_SERVER_ERROR`;
- all through `codedOrLogged`.

Unknown runtime exceptions fall through to the existing `Exception` arm (500).

### 3.5 Java signatures and codegen changes

```java
// com.alramz.service.OtpService — concrete @Service, no interface (one implementation, no cross-feature caller)
OtpSendResult send(OtpSendRequest request);                 // throws OtpException
OtpValidateResult validate(OtpValidateRequest request);     // throws OtpException; OTP007 thrown after commit
OtpConfigurationList configurations();

// com.alramz.repository.OtpRepository — @Repository, @Qualifier("middlewareNamedParameterJdbcTemplate"), SqlQueriesManager
Optional<OtpConfig> findConfig(String serviceName);
List<OtpConfig> findAllConfigs();
int maxAttemptsSince(String serviceName, Collection<String> recipients, LocalDateTime dayStart);
void deactivateActive(String serviceName, Collection<String> recipients);
void insertOtp(OtpRow row);
void upsertAttempt(String serviceName, String recipient, LocalDateTime now, LocalDateTime dayStart);
List<OtpCandidate> lockActiveCandidates(String serviceName, Collection<String> recipients, LocalDateTime now);
void markValidated(Collection<Long> ids);
void incrementValidationAttempts(Collection<Long> ids);
void resetAttempts(String serviceName, Collection<String> recipients);
int deactivateExpired(LocalDateTime now);                    // cleanup job

// com.alramz.model (hand-written records, not API models — EmailTemplate precedent)
record OtpConfig(int messageId, String serviceName, int expiryInSeconds, int tokenLength, String smsMessageEnglish,
                 String smsMessageArabic, int maxAttempts, String sentVia, String emailMessageEnglish,
                 String emailMessageArabic, Integer maxValidationAttempts) {}
record OtpRow(String serviceName, String mobileEmail, String otpHash, LocalDateTime created, LocalDateTime expiry,
              boolean active, String status, String ipAddress) {}
record OtpCandidate(long id, String otpHash, int validationAttempts) {}
```

**Changes to existing files:**
- **`specs/openapi.yaml`:**
  - Add the 3 paths, each declaring every status listed above with its example (401 as a description only).
  - Add the schemas `OtpSendRequest`, `OtpSendResult`, `OtpChannelResult`, `OtpValidateRequest`, `OtpValidateResult`, `OtpConfigurationList`, `OtpConfiguration`.
  - Generalise `GenericResponse.errorCode` to read "MGE-V-0xx / EML00x / SMS0xx / OTP0xx", and `response` to list the OTP results too.
- **`pom.xml`:** append those 7 names to `modelsToGenerate`.

### 3.6 T4: OTP cleanup job

**Bean:** `@Component("otpCleanupJobRunner") class OtpCleanupJob implements Schedulable`, in `com.alramz.scheduler.job`.

**`run(ScheduleInfoBean)`:**
- Calls `repository.deactivateExpired(LocalDateTime.now(dubaiClock))`, i.e. `UPDATE otp_management SET active='0' WHERE active='1' AND expiry_date_time < :now`.
- Logs `OTP cleanup deactivated={} durationMs={}`.
- The whole body is in try/catch; it never throws (the `SmsDispatchJob` pattern).

**Schedule:** a `schedule_job` row with `('notification','otpCleanup','otpCleanupJobRunner','otpCleanupJobRunner','', 'FIXED_DELAY', NULL, 3600000, NULL, 'Y', …)` (changeset 005).

**Multi-pod:** the UPDATE is idempotent, so concurrent pods are harmless. No lock is needed.

**Daily attempt reset:** no job is needed. It is per user at read time (3.1 step 4, 8c).

**Kill switch:** set `schedule_job.enable='N'`. There is no extra property: YAGNI, and the job is harmless.

---

## 4. Data Model & Persistence Changes

All tables are in the Postgres `middleware` DB (decision 3). The Oracle DDL is translated:
- `VARCHAR2` → `VARCHAR`;
- `NUMBER` → `INTEGER`/`BIGINT`;
- `CLOB` → `TEXT`.

Identifiers are unquoted and lower-case. Every statement must also run on H2 `MODE=PostgreSQL` (context test).

**Changesets.** Each identical file goes into **all 5** folders: `src/main/resources/db/changelog/{dev,docker,test,preprod,prod}/sql/`. The author is `gaurav`, and each id equals the filename stem. The existing per-profile `includeAll` masters pick them up, so no master edit is needed.

| File | Contents | Rollback |
|---|---|---|
| `004-notification-otp-tables.sql` | Changeset `004-notification-otp-tables`: `CREATE TABLE IF NOT EXISTS` for `otp_configuration`, `otp_management` and `otp_attempts`, plus the indexes below | `DROP TABLE IF EXISTS otp_attempts; DROP TABLE IF EXISTS otp_management; DROP TABLE IF EXISTS otp_configuration;` |
| `005-notification-otp-cleanup-job.sql` | Changeset `005-notification-otp-cleanup-job`: `INSERT INTO schedule_job (…)` the 3.6 row, `ON CONFLICT DO NOTHING` (target-less, per the D-2 precedent) | `DELETE FROM schedule_job WHERE job_group_name='notification' AND schedule_id='otpCleanup';` |

No existing changeset (001–003) is edited.

**`otp_configuration`** (from the user's live DDL; changes are marked):

| Column | Type | Note |
|---|---|---|
| `message_id` | `INTEGER NOT NULL` PK (`otp_configuration_pk`) | **PK added** |
| `service_name` | `VARCHAR(200) NOT NULL` UNIQUE (`otp_configuration_service_name_uk`) | **UNIQUE added**: the lookup by name must be deterministic (precedent: `email_configurations.template_name`) |
| `expiry_in_seconds` | `INTEGER NOT NULL` CHECK `BETWEEN 1 AND 86400` | **CHECK added**: also a valid `SmsSendRequest.expiryInSecs` (SMS005 range) |
| `token_length` | `INTEGER NOT NULL` CHECK `BETWEEN 4 AND 10` | **type tightened** from `VARCHAR2(200)` (section 9) |
| `sms_message_arabic`, `sms_message_english` | `VARCHAR(4000)` | as DDL (`%OTP%`) |
| `max_attempts` | `INTEGER NOT NULL` CHECK `>= 1` | CHECK added |
| `sent_via` | `VARCHAR(20) NOT NULL` CHECK `IN ('SMS','EMAIL','EMAIL\|SMS')` | **NOT NULL + CHECK added**; narrowed from 200 |
| `email_message_arabic`, `email_message_english` | `TEXT` | from CLOB (`<subject>^+^<body>`) |
| `max_validation_attempts` | `INTEGER` CHECK `IS NULL OR >= 1` | stays nullable; null → `app.otp.default-max-validation-attempts` |

**`otp_management`** (Part 4 §2.2, widened):

| Column | Type | Note |
|---|---|---|
| `id` | `BIGSERIAL` PK | replaces the generated NUMBER |
| `service_name` | `VARCHAR(200) NOT NULL` | no FK (an audit row must survive a config change) |
| `mobile_email` | `VARCHAR(254) NOT NULL` | **one normalised recipient per row**; widened from 200 to the email maximum |
| `otp` | `VARCHAR(64) NOT NULL` | SHA-256 lowercase hex |
| `creation_date_time`, `expiry_date_time` | `TIMESTAMP NOT NULL` | Asia/Dubai local |
| `active` | `VARCHAR(1) NOT NULL` CHECK `IN ('0','1')` | |
| `status` | `VARCHAR(20) NOT NULL` CHECK `IN ('SENT','FAILED','VALIDATED')` | |
| `ip_address` | `VARCHAR(45)` | **widened from 20 for IPv6** |
| `validation_attempts` | `INTEGER NOT NULL DEFAULT 0` | |

Indexes:
- `idx_otp_management_lookup (service_name, mobile_email, active)`: serves the send deactivate and the validate lock.
- `idx_otp_management_expiry (active, expiry_date_time)`: serves the cleanup job.

**`otp_attempts`** (Part 4 §2.3, keyed per recipient):

| Column | Type | Note |
|---|---|---|
| `id` | `BIGSERIAL` PK | |
| `service_name` | `VARCHAR(200) NOT NULL` | |
| `mobile_email` | `VARCHAR(254) NOT NULL` | one recipient; legacy keyed on the whole normalised value |
| `attempts` | `INTEGER NOT NULL DEFAULT 0` | sends on `last_attempt_date`'s Dubai day |
| `last_attempt_date` | `TIMESTAMP NOT NULL` | Asia/Dubai local |

Constraint: `otp_attempts_service_recipient_uk UNIQUE (service_name, mobile_email)`.

**`sql-queries.xml` keys:** `otp.config.findByName`, `otp.config.findAll`, `otp.attempts.maxSince`, `otp.management.deactivateActive`, `otp.management.insert`, `otp.attempts.update`, `otp.attempts.insert`, `otp.management.lockCandidates`, `otp.management.markValidated`, `otp.management.incrementValidation`, `otp.attempts.reset`, `otp.management.deactivateExpired`. All use `:named` binds, with `&lt;`/`&gt;` escaped as in the existing entries.

---

## 5. Security & Config Impact

**JWT / permit-all:**
- **None: all 3 paths stay authenticated** (secured-by-default, applied) with `@JwtSecured(roles = "APP_NOTIFICATION")`.
- They are not added to `permit-all-urls` in any of the 6 yml files.
- `ProfileSecurityConfigTest` is extended to assert this. The known baseline dev-profile `JWT_SECRET` failure (D-0) is unchanged.

**New config `app.otp.*`** (`config/OtpProperties`, a record, `@ConfigurationProperties("app.otp")`, added to `@EnableConfigurationProperties` on `AlramzNotificationServiceApplication`):

| Key | `application.yml` | Profile overrides | Notes |
|---|---|---|---|
| `fixed-test-otp-enabled` | `${OTP_FIXED_TEST_OTP_ENABLED:false}` | dev/docker/test: `${OTP_FIXED_TEST_OTP_ENABLED:true}`. preprod/prod: literal `false` | decision 8 |
| `email-from` | `${OTP_EMAIL_FROM:notification@alramz.ae}` | prod: `${OTP_EMAIL_FROM:cm@alramz.ae}` | matches each profile's `app.email.allowed-senders` default |
| `sms-msg-category` | `"4.5"` | — | Part 4 §3 |
| `default-max-validation-attempts` | `3` | — | used when `max_validation_attempts` is null |
| `max-recipients` | `5` | — | new abuse cap (section 9) |

**Startup guards.** These live in the `OtpService` constructor, which takes `Environment`, `OtpProperties` and `EmailProperties`. Each throws `IllegalStateException`, so the context fails to start (the E44/E45 fail-fast precedent):
1. `fixed-test-otp-enabled=true` while `environment.matchesProfiles("prod")` → "app.otp.fixed-test-otp-enabled must be false in prod".
2. `email-from` (stripped) is not in `app.email.allowed-senders` (case-insensitive) → "app.otp.email-from is not an allowed sender". Without this check every OTP email would fail with MGE-V-007 at runtime.

**Secrets / logging:**
- No new secrets.
- The plain OTP is **never logged**, and neither is the hash, the rendered SMS or email text, or an email address. Mobiles are logged only via `SmsServiceImpl.mask`. Caller strings go through `TemplatedEmailService.loggable`.
- `SensitiveDataMasker` gets no change: payload logging is off in this service and `OtpController` has no `@Loggable`. **Follow-up** (starter change, out of scope): add `hashedotp` and `otp` to its key set before anyone enables payload logging here.
- `RequestSizeLimitConfig` gets no change: the OTP bodies are tiny.

**Operational follow-ups (ops, not this feature):**
- Load `otp_configuration` rows.
- Set `OTP_EMAIL_FROM` if the defaults are wrong.
- Confirm the legacy hash encoding with the known value `123456` before go-live (decision 6 residual).

---

## 6. Edge Cases & Error Behaviors

| # | Case | Exception | HTTP / body |
|---|---|---|---|
| E1 | send: `serviceName`/`recipients` blank, `language` not exactly `EN`/`AR` (e.g. `en`, `FR`), `ipAddress` missing or not matching the pattern | `OtpException("OTP001")`, fields collected in order | 400 `OTP001` |
| E2 | validate: `hashedOtp` missing or not 64 hex characters | `OTP001` | 400 `OTP001`; no attempt counted |
| E3 | Unknown `serviceName` (send **and validate**) | `OTP002` | 400 `OTP002` (validate no longer 500) |
| E4 | A token is neither a valid email nor a valid mobile (`@` → email) | `OTP003`/`OTP004` | 400 |
| E5 | `sent_via=SMS`, only emails given / `sent_via=EMAIL`, only mobiles given | `OTP003` / `OTP004` | 400 |
| E6 | More than 5 kept recipients | `OTP005` | 400 |
| E7 | Today's (Dubai) `MAX(attempts)` ≥ `max_attempts`. Yesterday's counters do not count (per-user reset) | `OTP006` | 429 |
| E8 | Template for the language/channel is null or blank; email template has no `^+^`; subject or body is blank | `OTP010`, logged as misconfiguration; **no send, no rows** | 500, code null |
| E9 | Fixed-test mode | none | 200; no `SmsService`/`EmailService` call; OTP `1234` (or `123456` for names ending `_`) is stored hashed |
| E10 | `EMAIL\|SMS`: one channel fails, one delivers | none | 200 with per-channel status. The delivered rows are ACTIVE/SENT, the failed rows FAILED/inactive, and `otp_attempts` is +1 |
| E11 | Every recipient fails (provider rejection, transport, Graph error, sender not allowed, SMS body too long) | `OTP009`, thrown after the FAILED rows commit | 502, code null; `otp_attempts` unchanged |
| E12 | DB failure before any send (config read, limit read) | Spring `DataAccessException` → existing `Exception` arm | 500; nothing sent |
| E13 | DB failure in the record transaction after delivery | `OTP010` ("OTP record failed"), logged | 500. The OTP may have been delivered but cannot be validated (section 9) |
| E14 | validate: no active, unexpired row (never sent, expired, already validated, or FAILED only) | `OTP007` after commit | 400 |
| E15 | validate: a candidate has `validation_attempts ≥ limit` (null limit → 3) | `OTP008` | 429 |
| E16 | validate: wrong hash | `OTP007`, thrown **after** the `validation_attempts+1` commit | 400; the counter persists |
| E17 | validate: hash in upper case | none | 200 (case-insensitive) |
| E18 | validate: match | none | 200 `valid:true`. Only the matched **row ids** are VALIDATED/inactive, and `otp_attempts` is reset for those recipients |
| E19 | Two concurrent validates of the same OTP | serialised by `FOR UPDATE` | the first gets 200; the second finds no active row → 400 `OTP007` |
| E20 | Malformed JSON | existing arm | 400 `MGE-V-006` |
| E21 | Missing/invalid JWT / wrong role | starter / `AccessDeniedException` arm | 401 / 403 code null |
| E22 | Cleanup job: DB down | caught and logged | no effect on requests |
| E23 | Startup: fixed-test flag on in prod / `email-from` not allowed | `IllegalStateException` | context fails to start |
| E24 | configurations: empty table | none | 200 `messages: []` |
| E25 | configurations: DB failure | existing `Exception` arm | 500 |

---

## 7. Open Questions & Decisions

No new blocking gap was raised in this run (`AskUserQuestion` not used). The decisions below were raised with the user by the orchestrating session before this run (`source-design-doc.md`, "User decisions") and are recorded here as final:

| # | Question | Options considered | User's choice |
|---|---|---|---|
| D1 | Scope | gap list only / OTP tables only / full Part 4 | **Full Part 4 OTP flow**, including cleanup of expired OTPs and the daily attempt reset |
| D2 | What is the "SMS configurations table"? | new `sms_configuration` / `OTP_CONFIGURATION` | **`OTP_CONFIGURATION`**. SMS routing stays in `app.sms.*` |
| D3 | Schema ownership | external DB / service-owned Liquibase | **Liquibase, Postgres `middleware`**, `004-notification-*` onward, all 5 profile folders |
| D4 | Seed template rows | seed / none | **None** |
| D5 | Contract | legacy + new / new only | **New `/api/v1` only**, `GenericResponse`, HTTP semantics, `APP_NOTIFICATION` |
| D6 | Hash format | as legacy / lowercase hex | **Server stores lowercase hex SHA-256; validate compares case-insensitively**. The legacy encoding is to be confirmed before go-live |
| D7 | Partial delivery | legacy silent 200 / 200 with per-channel status / fail | **200 if at least one channel delivered**, with per-channel status; the failed channel's rows are FAILED; all failed → 502 |
| D8 | Test OTPs | legacy env flag / profile flag | **`app.otp.fixed-test-otp-enabled`**: true in dev/docker/test, false in preprod/prod; startup fails if true in prod; no real send when on |

Decisions made in this stage from repo precedent, and so not asked:
- The `OTP` code prefix and HTTP mapping (EML/SMS precedent plus the §7 table; invalid OTP → 400 rather than 401).
- Recipient validation reuses the `TemplatedEmailService.EMAIL` and `SmsServiceImpl` number rules.
- The OTP sender: `notification@alramz.ae` outside prod and `cm@alramz.ae` in prod.
- The SMS category is `4.5`.
- The cleanup job is a starter-scheduler `schedule_job` row (changeset 005), and the daily reset happens per user at read time.

---

## 8. Explicit Acceptance Criteria

Contract and codegen:
- [ ] `openapi.yaml` declares `POST /api/v1/otp/send` (`sendOtp`), `POST /api/v1/otp/validate` (`validateOtp`) and `GET /api/v1/otp/configurations` (`getOtpConfigurations`), with tag `Otp`. Each lists exactly the statuses in 3.1/3.2/3.3 with a real example (401 as a description only).
- [ ] The 7 OTP schemas exist. The `key`/`value`/`hashedOTP` aliases bind, proven by a binding test.
- [ ] `pom.xml` `modelsToGenerate` includes the 7 models. `OtpController implements com.alramz.api.OtpApi`. `mvn -pl services/alramz-notification-service -am test-compile` passes.

Persistence:
- [ ] `004-notification-otp-tables.sql` and `005-notification-otp-cleanup-job.sql` exist in all 5 `sql/` folders, byte-identical per name. Changeset ids equal the filename stems, each file has `--rollback`, and 001–003 are unchanged (checked with git diff).
- [ ] The columns, types, CHECKs, PK/UNIQUE constraints and two indexes match section 4. Proven by an H2 repository test that runs the 004 script.
- [ ] `AlramzNotificationServiceApplicationTests#contextLoads` applies 004/005 on H2, and the scheduler logs `scheduleId=otpCleanup … FIXED_DELAY … delay=3600000`.

Send (`OtpServiceTest`, mocked repository/`SmsService`/`EmailService`, fixed `Clock`):
- [ ] E1, E3–E8: each produces its code, and no `SmsService`/`EmailService` call or repository write happens.
- [ ] The generated OTP has `token_length` digits, and the stored hash equals `sha256Hex(otp)`, lowercase. In fixed-test mode the OTP is `1234`, or `123456` for a name ending `_`, and there are zero channel calls (E9).
- [ ] SMS is called once with `msgCategory "4.5"` and `expiryInSecs = expiry_in_seconds`. Email is called once per address with `from = app.otp.email-from`, with the subject and body split on `^+^` and `%OTP%` replaced.
- [ ] E10: partial delivery → 200, with channels `[SMS SENT, EMAIL FAILED]`, the SMS rows active/SENT, the email rows inactive/FAILED, and `upsertAttempt` called for every recipient.
- [ ] E11: all failed → `OTP009` after `insertOtp` (FAILED rows), with no `upsertAttempt`.
- [ ] Previous active rows are deactivated by `(service_name, recipients)` in the same transaction as the insert.
- [ ] The daily limit uses the Dubai `dayStart`. A counter whose `last_attempt_date` is yesterday does not block, and the upsert resets it to 1. Proven on H2 for the SQL.

Validate:
- [ ] E2, E3, E14–E19 behave as in section 6. On H2: a wrong hash leaves `validation_attempts` incremented **after** the 400 (not rolled back). A match sets only the matched ids to `VALIDATED`/`active='0'`. An upper-case hash matches.
- [ ] Config missing → 400 `OTP002`, not 500.
- [ ] A null `max_validation_attempts` uses 3.

Lookup:
- [ ] It returns every row in `service_name` order, without `maxValidationAttempts`. An empty table gives `messages: []`.

Handler, security and config:
- [ ] `GlobalExceptionHandlerTest`: OTP001–005/007 → 400 with the code; OTP006/008 → 429 with the code; OTP009 → 502 and OTP010 → 500, both with a null code.
- [ ] `@JwtSecured(roles="APP_NOTIFICATION")` is on all 3 methods. `ProfileSecurityConfigTest` asserts the 3 paths are not permit-all in all 6 yml files (the dev baseline failure excepted).
- [ ] `app.otp.*` matches the section 5 table per profile. The startup guards (E23) are covered by tests: prod + flag on → `IllegalStateException`; `email-from` outside the allow-list → `IllegalStateException`; dev + flag on → OK.
- [ ] Log-capture tests: no log line from send, validate or cleanup contains the OTP, the hash, an email address, the rendered text, or an unmasked mobile.

Cleanup job:
- [ ] `OtpCleanupJob` deactivates only rows with `active='1' AND expiry_date_time < now` (H2). It never throws when the repository throws.

Manual / final:
- [ ] `apiCollection.http` has a positive and a negative block per endpoint, using `{{authToken}}` and synthetic `example.com` / `97150000001x` values.
- [ ] `mvn -pl services/alramz-notification-service -am test`: everything passes except the recorded baseline `ProfileSecurityConfigTest[application-dev.yml]`.

---

## 9. Self-Critique

**Confidence: medium-high.**
- Every contract, table and flow is grounded in existing classes (`SmsService`, `EmailService`, `TemplatedEmailService` helpers, `GlobalExceptionHandler.codedOrLogged`, `SmsDispatchJob`, changesets 001–003).
- The 8 user decisions cover the expensive choices.
- The rest is legacy-faithful or follows precedent.

**Residual non-blocking assumptions:**
1. **Unrecognised recipient tokens are rejected** (OTP003/OTP004). Legacy silently dropped them. This is stricter and caller-visible, but cheap to relax.
2. **`max-recipients` = 5** is a new anti-bombing cap that legacy did not have. It is configurable.
3. **`token_length` becomes `INTEGER` with CHECK 4–10, `sent_via` becomes NOT NULL with a CHECK on the 3 values, and `expiry_in_seconds` is CHECKed to 1–86400.** Ops data loads with legacy values outside these will fail at insert time, which is visible and intended.
4. **Emails are stored, matched and sent in upper case** (legacy normalisation). Graph and mail servers treat addresses case-insensitively in practice.
5. **Only channel-relevant recipients count.** An email in the request of an SMS-only service is ignored, as in legacy.
6. **`otp_attempts` is incremented for every recipient whenever at least one was delivered.** This is stricter than legacy's single row per whole value; it stops a caller bypassing the limit by reordering or combining recipients.
7. **No retention purge** of `otp_management`. The table grows by about one row per recipient per send. A retention changeset or job can come later.
8. **Send race window.** Two concurrent sends to the same recipient can both pass the daily check and leave two active OTPs. Both are valid until expiry. Legacy has the same race, and the email feature accepted the same one.
9. **The fixed-test guard checks only the `prod` profile** (decision 8). preprod sets the literal `false` but is not hard-guarded.
10. `service.yaml` sizing is unchanged. OTP traffic is per-login, which is light, and SMS and email calls are synchronous with the existing 5 s/10 s timeouts.

**Weakest part:** E13, delivery succeeded but the record transaction failed. The user receives an OTP that cannot be validated, and the caller sees 500 and will typically retry, which issues a new OTP. Writing the rows *before* sending, then updating their status afterwards, would close this gap. The cost is a second write and the deactivate-then-fail-all case. The simpler order was chosen; `02_plan` can flip it if the reviewer prefers. The other unproven part is decision 6's legacy hash encoding, which can only be confirmed against a real legacy client.
