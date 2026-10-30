# Spec: notifications-sms-email (templated email, Part 1, plus SMS gateway, Part 3)

Sources:
- `.claude/modules/features/notifications-sms-email/source-design-doc.md`: user request and the live Oracle DDL.
- `.claude/modules/features/notification-graph-gateway/source-design-doc.md`: Parts 1, 3 and 4 of the consolidated spec.
- `.claude/modules/features/notification-graph-gateway/spec.md` and `implementation-report.md`: feature 1, already implemented, uncommitted.

This is a re-run. The previous 01_elicit run came back BLOCKED with 7 questions. The user answered them all, plus two more (section 7). No new blocking gap was found.

---

## 1. Feature Objective & Scope

**Problem.** `alramz-notification-service` can send a raw email through Microsoft Graph (feature 1). It still cannot do three things:
- send a business **templated** email (Part 1: template lookup, placeholder rendering, attachment rules, daily send limit, audit);
- send an **SMS** at all (Part 3: Etisalat/Waselney router, the `INSIGHT.SMS_TABLE` dispatch job, Waselney delivery report).

This feature adds all three. It also adds the persistence they need. As a result, the notification service stops being DB-less.

**Target service:** `services/alramz-notification-service` only. It is an existing service, so no scaffold is needed.

**Triggers:**

| # | Trigger | What it does |
|---|---|---|
| T1 | `POST /api/v1/email/template/send` (HTTP, JWT) | Sends a templated email |
| T2 | `POST /api/v1/sms/send` (HTTP, JWT) | Sends an SMS through the provider router |
| T3 | `GET /api/v1/sms/report/{messageId}` (HTTP, JWT) | Returns the Waselney delivery report |
| T4 | Starter-scheduler job `smsDispatchJobRunner` (FIXED_DELAY) | Claims and sends pending `INSIGHT.SMS_TABLE` rows |
| T5 | In-process `com.alramz.service.SmsService#send(SmsSendRequest)` | Same router as T2. The next feature (`notification-otp`) calls it |

**Done means:**
- The three endpoints return the `GenericResponse` envelope with real HTTP statuses.
- A templated send writes one `email_log` row and enforces `MAX_ATTEMPTS` per day.
- The dispatch job claims rows safely across pods and writes the final STATUS back.
- With no provider credentials configured, everything runs in **simulated** mode. Live SMS sending stays off.

**In scope:**
- Contract additions to `specs/openapi.yaml`.
- A codegen config fix in `pom.xml`: the tags fix, plus the models to generate.
- New dependencies: Liquibase, Postgres, Oracle JDBC.
- Liquibase changesets for `email_configurations`, `email_log` and the scheduler row.
- The middleware datasource (`DatasourceConfiguration`) and the brok datasource.
- `@EnableScheduler`.
- Templated-email service, SMS router, Etisalat/Waselney/VeriPhone clients, `SMS_TABLE` repository, dispatch job.
- New exceptions and handler arms.
- Config in all 6 `application*.yml`.
- `apiCollection.http` blocks.
- Tests.

**Out of scope (explicit):**
- **All of Part 4 OTP.** That means `OTP_CONFIGURATION`, `OTP_MANAGEMENT`, `OTP_ATTEMPTS`, sendOTP/validateOTP/getMessagesLookup, and the OTP email/SMS templates. All of it is the next feature, `notification-otp` (decision Q5).
- **Legacy adapter endpoints** (`/sendEmail`, `/sendSMS` and similar) and the legacy numeric response codes (decision Q4).
- **Creating or altering `INSIGHT.SMS_TABLE`.** It is an external Oracle table owned by the source systems (decision Q7).
- Retrying failed SMS rows (decision Q9).
- Real provider credentials. Live SMS stays off (decision Q3).
- Seed data for email templates. Template rows are data, loaded by ops or a later changeset (section 9).
- The Seq "asynchronousIngestion" event. It is replaced by structured logs.
- Any change to `alramz-api-starter`, `infra/`, `k8s/` or `.github/`. New env vars and secrets must be wired by ops (section 5, "Operational follow-ups").
- Any change to the Graph gateway behaviour (`EmailServiceImpl`) beyond what section 3.1 states.

---

## 2. Repository Grounding

| Concern | Existing artifact (verified) | How this spec uses it |
|---|---|---|
| Contract | `services/alramz-notification-service/specs/openapi.yaml`: one path `/api/v1/email/send` (`sendEmail`, tag `Email`); schemas `EmailRequest`, `EmailAttachment`, `GenericResponse`, `EmailSendResult`; root `security: - BearerAuth: []` | **Amend.** Add 3 paths and their schemas. The file is `openapi.yaml`, not `apiSpecs.yaml`, and its paths carry the full `/api/v1` prefix (feature 1 kept this local precedent). New paths follow it. |
| Codegen | `pom.xml` openapi-generator 7.23.0, `useBeanValidation=false`, `modelsToGenerate=GenericResponse,EmailSendResult,EmailRequest,EmailAttachment`. `<useTags>` is nested inside a bogus `<tags>` element, so it is ignored and every path generates into one interface, `com.alramz.api.ApiApi` | Move `<useTags>true</useTags>` to be a direct child of `<configOptions>`. This is needed: with `skipDefaultInterface=true`, one `ApiApi` would force a single controller to implement every operation. The interfaces become `EmailApi`, `TemplatedEmailApi` and `SmsApi`. Extend `modelsToGenerate` (section 3.5). |
| Controllers | `controllers/EmailController implements ApiApi`, `@JwtSecured(roles = "APP_NOTIFICATION")`, no `@Loggable` (it would log the body) | Change to `implements EmailApi`. Nothing else changes. New `TemplatedEmailController` and `SmsController` follow the same style. |
| Email gateway (in-process) | `service/EmailService#sendEmail(EmailRequest)`. Throws `EmailValidationException(code MGE-V-0xx)` and `EmailServiceException(code MGE-B-00x, retryAfter)`. `EmailServiceImpl` validates with the regex `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`, enforces `app.email.allowed-senders`, `max-body-length` 10000 and `max-total-attachment-size-bytes` 3000000 | The templated service builds an `EmailRequest` and calls it. Gateway exceptions are mapped as in section 3.1 step 9. |
| Exceptions + advice | `exception/GlobalExceptionHandler` (`@RestControllerAdvice(basePackages="com.alramz.controllers")`, `@Order(0)`). The status is picked from the exception's **code** with a switch, and every body is `GenericResponse` + `EmailSendResult{correlationId}`. Explicit `AccessDeniedException` → 403. `HttpMessageNotReadableException` → MGE-V-005 (Base64 in `attachments[n].content`) or MGE-V-006. | Add two arms (`TemplatedEmailException`, `SmsException`), using the same code-to-status pattern. The binding-level MGE-V-005/006 arms stay service-wide (section 6). |
| Request-size guard | `config/RequestSizeLimitConfig`: a `HandlerInterceptor` on `/api/v1/email/send` that rejects `Content-Length > cap*4/3 + 256 KiB` | Add the path `/api/v1/email/template/send`. On that path, throw `TemplatedEmailException("EML007")`. |
| Config | `config/EmailProperties` (record, `app.email`), `config/GraphProperties` (`app.graph`), bound with `@EnableConfigurationProperties(EmailProperties.class)` on `AlramzNotificationServiceApplication` | New record `config/SmsProperties` (`app.sms`), added to that annotation. |
| Profiles | 6 files: `application.yml`, `-dev`, `-docker`, `-test`, `-preprod`, `-prod`. Each of the 5 profile files has an H2 `spring.datasource` block and `company.scheduler.management-endpoints.enabled: false`. `permit-all-urls` = `/api/v1/info` (+ `/actuator/prometheus` in dev) | Replace the H2 `spring.datasource` blocks with `company.datasource.middleware` (+ `brok`). Add `spring.liquibase.change-log`, `company.scheduler.*` and `app.sms.*`. No permit-all change. |
| Datasources | Starter `MiddlewareDataSourceAutoConfiguration` (Postgres, `middleware*` beans), `BrokDataSourceAutoConfiguration` (Oracle, `brok*` beans). Both have `enabled=false` by default | Enable `middleware` in every profile. Enable `brok` only by env (`COMPANY_DATASOURCE_BROK_ENABLED`, default `false`). |
| Datasource precedent | `wealth-management-service`: `config/DatasourceConfiguration.java` (`@Primary` re-export of the middleware DataSource/TxManager); `application-dev.yml` lines 113-150 (middleware + brok blocks, env-var-driven); `repository/MmfRepositoryImpl` (`@ConditionalOnProperty(prefix="company.datasource", name="brok.enabled", havingValue="true")` + `@Qualifier("brokNamedParameterJdbcTemplate")`); `service/impl/MmfServiceImpl` (`TransactionTemplate` over `@Qualifier("brokTransactionManager")`); pom `spring-boot-starter-liquibase`, `postgresql` (runtime), `ojdbc11` + `orai18n` (runtime), `h2` (test) | Copy `DatasourceConfiguration` verbatim. Copy the yml shape **without copying its dev credential defaults**. Use the brok gating and `TransactionTemplate` patterns for the `SMS_TABLE` repository. `orai18n` is needed because `MESSAGE_BODY` is `NVARCHAR2` (Arabic). |
| Shared Postgres DB | Every service points `middleware` at the **same** DB (`alramz-mw-dev-db` in dev, `alramzmwdb` elsewhere), so `DATABASECHANGELOG` is shared. `onboarding-service` and `reference-data-service` both ship `db/changelog/<profile>/sql/001-schedule-job.sql`, with changeset `gaurav:001-create-schedule-job-table` and identical content | **Every new changelog filename carries a `notification` token**, so its `(author, id, filename)` cannot collide with another service's changeset of the same name. A collision with different content would raise a checksum error and stop startup. |
| Liquibase conventions | `.claude/howtos/liquibase-migrations.md`: per-profile masters with `includeAll` and their own `sql/`; `--liquibase formatted sql`; changeset id = filename number + description; `IF NOT EXISTS`; `--rollback`. `wealth-management-service/.../db/changelog/dev/db.changelog-master.yaml` (`errorIfMissingOrEmpty: false`). `reference-data-service` keeps a deployed `test` tree under `src/main/resources/db/changelog/test/` | Notification has no changelog today, so this is the first changeset set: `001`–`003` (section 4). The test tree goes in `src/main` because `application-test.yml` is a deployed profile (Seq on, `${JWT_SECRET}` required). |
| Scheduler | `.claude/howtos/scheduler-jobs.md` says to use the starter's DB-driven scheduler, not plain `@Scheduled`. `SchedulerConfiguration#schedulerService` needs `@Qualifier("middlewareNamedParameterJdbcTemplate")`. `JobScheduleManager` reads `schedule_job WHERE job_group_name=:g AND enable='Y'`, and `FIXED_DELAY` calls `scheduleWithFixedDelay(runnable, delay)` with `delay` in **milliseconds**. Existing groups: `dataValidation`, `referenceData` | **Decision: starter scheduler, not `@Scheduled`.** The howto forbids `@Scheduled`, and the middleware datasource it needs is being enabled anyway. Add `@EnableScheduler(jobGroupName = "notification")`. Seed a `FIXED_DELAY` row, which never overlaps itself inside a pod. Keep `management-endpoints.enabled: false` (the existing explicit choice). The kill switch is `app.sms.dispatch.enabled` (section 5). |
| SQL text | Starter `utils/SqlQueriesManager` (`sql.file` property, XML properties). `wealth-management-service/src/main/resources/application.yml` sets `sql.file: classpath:sql/sql-queries.xml` | New `src/main/resources/sql/sql-queries.xml`. `sql.file` goes in `application.yml`. |
| Caller identity / IP / correlation | Starter `jwt/context/JwtContext#getCurrentApplication()` (null outside a request or with no claim); `logging/util/LoggingHelper.extractClientIp(HttpServletRequest)` (static, X-Forwarded-For first); `logging/util/MDCUtil#getCorrelationId()` | Invoker = `getCurrentApplication()`, or `"unknown"` when null. IP = `extractClientIp`. `correlationId` = MDC value, or a new UUID. |
| Outbound HTTP logging | Starter `LoggingAutoConfiguration` registers `alramzRestTemplateLoggingInterceptor` and `alramzWebClientLoggingFilterFunction` as **opt-in** beans | Do **not** wire either one into the SMS/VeriPhone clients. The Waselney and VeriPhone URLs carry credentials in the query string. |
| VeriPhone precedent | `onboarding-service`: `PhoneValidationServiceImpl` calls `POST /v2/verify` with query params `key`, `phone`; `PhoneServiceProperties` (`adapter.external-veriphone-service`, `api-key: ${VERIPHONE_API_KEY:}`) | Use the same endpoint and env var name. The client is Spring `RestClient` (spring-web is already present, so no WebClient or resilience4j dependency is added). |
| Masking | Starter `SensitiveDataMasker` / `LoggingProperties`: request/response `include-payload=false` by default, `database-logging.enabled=false` by default. Notification enables neither | No masker change. Payload and DB audit logging stay off for this service (section 5). |
| Context test | `src/test/java/com/alramz/AlramzNotificationServiceApplicationTests` (`@SpringBootTest(properties={JWT_SECRET, AZURE_*})`, default profile, no `src/test/resources`) | Add middleware-on-H2 + test changelog properties to its `properties` array. Do **not** add `src/test/resources/application.yml`: it would shadow the main `application.yml`, which `ProfileSecurityConfigTest` reads from the classpath. |
| Profile security test | `src/test/java/com/alramz/config/ProfileSecurityConfigTest` asserts `company.jwt.secret == "${JWT_SECRET}"` and that `/api/v1/email/send` is not permit-all, for all 6 files | Extend it to the 3 new paths. Note: the current `application-dev.yml` has `secret: ${JWT_SECRET:defaultSecret…}`, so this test already fails on dev before this feature (section 9). |
| Sizing | `service.yaml`: 0.5 CPU, 1Gi, `-Xmx512m`, 1-5 replicas | No change. This is a question only (section 9). |
| Manual requests | `apiCollection.http` (`@authToken`, secured send blocks) | Add a positive and a negative block for each new endpoint. |

---

## 3. Interface Contracts

Common facts for all three endpoints. They were resolved from the user's answer to Q4 and the feature 1 precedent:
- **Security:** JWT-secured, `@JwtSecured(roles = "APP_NOTIFICATION")` on each controller method. Not in `permit-all-urls`.
- **Envelope:** `GenericResponse {responseCode, responseMessage, errorCode?, errorMsg?, response}`. `responseCode` is the HTTP status as a string. `errorCode`/`errorMsg` are filled only for caller-fixable errors. On errors, `response` = `EmailSendResult{correlationId}` (the existing handler shape, reused service-wide). `correlationId` equals the `X-Correlation-ID` response header.
- **Correlation:** taken from the `X-Correlation-ID` header (starter `CorrelationIdFilter`). There is **no** body `correlationId` field. A stray body `correlationId`/`correlationID` is ignored, because Jackson's unknown-property handling is lenient (assumption, section 9).
- **401:** produced by the starter's Spring Security chain before the controller, not in this envelope. Declare it with a description only (feature 1 precedent).
- **Binding errors (service-wide codes):**
  - unparseable JSON → 400 `MGE-V-006` "Malformed JSON request";
  - invalid Base64 in `attachments[n].content` → 400 `MGE-V-005` "Invalid attachment at position n: content is not valid Base64".
  These come from the existing handler arm, which cannot know the feature (section 6).

### 3.1 T1: `POST /api/v1/email/template/send` (`operationId: sendTemplatedEmail`, tag `TemplatedEmail`)

**Request `TemplatedEmailRequest`** (Part 1 §2.2; generated without bean validation, so all checks are manual in the service):

| Field | Type | Required | Rules |
|---|---|---|---|
| `to` | string | yes | Split on `[;,/\s\\]+`, drop empty tokens, at least one token. Each token must match the gateway regex. Re-joined with `,` (Part 1 §3.3 target) |
| `cc` | string | no | Same rules. Null or blank means none |
| `bcc` | string | no | Same rules. Null or blank means none |
| `templateName` | string | yes | Exact, case-sensitive match on `email_configurations.template_name` |
| `language` | string, enum `EN`/`AR` | yes | Exact upper case |
| `placeholders` | string[] | conditional | Positional. Element i replaces `%i%` (1-based) in the body |
| `attachments` | `TemplateAttachment[]` | no | — |

`TemplateAttachment`:

| Field | Type | Required | Notes |
|---|---|---|---|
| `content` | string, `format: byte` | yes | Base64 |
| `contentType` | string | yes | Type **code**: `JPEG`, `JPG`, `PNG`, `PDF`, `MSWORD`, `MSEXCEL`, `CSV`, `TRN` or `TEXT`. Alias `contenttype` via `x-field-extra-annotation` `@JsonAlias` (feature 1 mechanism) |
| `fileName` | string | yes | Alias `filename` |

**Processing.** Part 1 §3, with the recommended target order:
1. **Field checks (collect).** Collect the fields that are missing or invalid, in the order `to`, `cc`, `bcc`, `templateName`, `language`, `attachments`.
   - `attachments` is collected when any item has null/empty `content` or null/blank `contentType`.
   - If any field was collected, throw `EML001` "Request failed due to missing: <fields>", joined with `, `.
2. **Load the template** by name (`email_configurations`). If there is none, throw `EML002`.
3. **Placeholders.** Let `n = number_of_placeholders`. Throw `EML003` in either case:
   - `placeholders` was sent and its size ≠ n;
   - `placeholders` was omitted or empty while n > 0. This is Part 1's recommended fix.
4. **Attachments.**
   - (a) Throw `EML004` if count > `number_of_attachments` (null counts as 0).
   - (b) For each attachment, in order:
     - throw `EML005` if the code is not in `type_of_attachments`, split on `,`, trimmed, exact match;
     - then throw `EML006` if `fileName` is blank;
     - then throw `EML005` if the code is `MSWORD` and the extension is not `.doc`/`.docx`, or the code is `MSEXCEL` and the extension is not `.xls`/`.xlsx` (case-insensitive).
   - (c) Throw `EML007` if the sum of decoded byte lengths > `attachments_max_size` parsed as a long. If the attachments are non-empty and the value is null, blank or unparseable, that is a template misconfiguration: throw `EML010` and log it.
5. **Daily limit.** The day is the current `Asia/Dubai` calendar day, `[00:00, next 00:00)`. Count the `email_log` rows for that day:
   - `is_bulk = 1`: template + invoker;
   - otherwise: template + normalised `to` + invoker.
   If `used >= max_attempts`, throw `EML008`.
6. **Render.**
   - The subject is `EN_SUBJECT`/`AR_SUBJECT` exactly as stored.
   - The body is `EN_BODY`/`AR_BODY`, with each `%i%` replaced literally (`String.replace`, every occurrence).
   - When the stored body is HTML (its stripped value starts with `<`, the gateway's rule), each placeholder value is first HTML-escaped with Spring `HtmlUtils.htmlEscape` (assumption, section 9).
   - A null subject or body for the chosen language is a misconfiguration: throw `EML010`.
7. **MIME mapping** (Part 1 §3.4 table):

   | Code | Condition | MIME type |
   |---|---|---|
   | `JPEG`, `JPG` | — | `image/jpeg` |
   | `PNG` | — | `image/png` |
   | `PDF` | — | `application/pdf` |
   | `MSWORD` | `.doc` | `application/msword` |
   | `MSWORD` | `.docx` | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
   | `MSEXCEL` | `.xls` | `application/vnd.ms-excel` |
   | `MSEXCEL` | `.xlsx` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
   | `CSV` | — | `text/csv; charset=UTF-8` |
   | `TRN`, `TEXT` | — | `text/plain; charset=UTF-8` |
8. **Send.** Build `com.alramz.model.EmailRequest`:
   - `to`, `cc`, `bcc`: the normalised lists, comma-joined;
   - `from` = `from_email`;
   - `subject`, `body`;
   - `EmailAttachment{fileName, contentType=MIME, content}`.
   Then call `emailService.sendEmail(request)`.
9. **Gateway exception mapping:**
   - `EmailServiceException` (any MGE-B code) → `EML009` (502). Log it with the MGE code.
   - `EmailValidationException` `MGE-V-008` → `EML007` (413).
   - `EmailValidationException` `MGE-V-004` → `EML004` (400).
   - Any other `EmailValidationException` → `EML010` (500), logged as "template misconfiguration <MGE code>". Examples: `from_email` not in `allowed-senders` (V-007), or a rendered body over `max-body-length` (V-003).
10. **Audit, after the send succeeds.** Insert into `email_log`:
    - `template_id`; normalised `to`/`cc`/`bcc`;
    - `email_body` = lowercase hex SHA-256 of the rendered body (UTF-8);
    - `language`;
    - `date_time` = now as a `LocalDateTime` in Asia/Dubai, from an injected `Clock`;
    - `invoker`; `ip_address`;
    - `attachments_meta_data` = `{"attachmentMetaData":[{"ct":<code>,"fn":<fileName>,"s":<decodedBytes>}]}`, or `{}` when there are none.
    **If the insert throws, log an error (correlationId + template id, no addresses) and still return 200** (decision Q8).
11. Return 200.
12. Log one structured line per request. It holds: correlationId, templateName, language, invoker, recipient **counts**, attachment count and total bytes, and the outcome code. It never holds addresses, placeholders, the body, the subject or attachment content.

**Statuses produced:** 200, 400, 401, 403, 413, 429, 500, 502. There is no 503: a Graph outage surfaces as `EML009` 502, per Part 1.

**Examples** (each goes into `openapi.yaml` as a real `example`):
```jsonc
// 200
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "7f1c2b9e-3d4a-4c1e-9b7a-2f6d8e0a1c55" } }
// 400, one example each for EML001..EML006 (+ MGE-V-005/006 binding)
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "EML001",
  "errorMsg": "Request failed due to missing: to, language", "response": { "correlationId": "7f1c..." } }
{ ... "errorCode": "EML002", "errorMsg": "Invalid template name" ... }
{ ... "errorCode": "EML003", "errorMsg": "Invalid number of placeholders" ... }
{ ... "errorCode": "EML004", "errorMsg": "Number of attachments does not match template configurations" ... }
{ ... "errorCode": "EML005", "errorMsg": "Type of attachments does not match template configurations" ... }
{ ... "errorCode": "EML006", "errorMsg": "Please provide the fileName for all attachments" ... }
// 403 role check failed
{ "responseCode": "403", "responseMessage": "Forbidden", "errorCode": null, "errorMsg": null, "response": { "correlationId": "7f1c..." } }
// 413
{ "responseCode": "413", "responseMessage": "Payload Too Large", "errorCode": "EML007",
  "errorMsg": "Attachments size limit exceeded", "response": { "correlationId": "7f1c..." } }
// 429
{ "responseCode": "429", "responseMessage": "Too Many Requests", "errorCode": "EML008",
  "errorMsg": "Maximum number of attempts exceeded", "response": { "correlationId": "7f1c..." } }
// 500 (EML010, logged only)
{ "responseCode": "500", "responseMessage": "Internal Server Error", "errorCode": null, "errorMsg": null, "response": { "correlationId": "7f1c..." } }
// 502 (EML009, logged only)
{ "responseCode": "502", "responseMessage": "Bad Gateway", "errorCode": null, "errorMsg": null, "response": { "correlationId": "7f1c..." } }
```

**Error codes** (Part 1 §5; the legacy codes are kept for traceability only):

| Code | HTTP | In body | errorMsg | Legacy |
|---|---|---|---|---|
| EML001 | 400 | yes | `Request failed due to missing: <fields>` | 1043 |
| EML002 | 400 | yes | `Invalid template name` | 1048 |
| EML003 | 400 | yes | `Invalid number of placeholders` | 1049 |
| EML004 | 400 | yes | `Number of attachments does not match template configurations` | 1052 |
| EML005 | 400 | yes | `Type of attachments does not match template configurations` | 1053 |
| EML006 | 400 | yes | `Please provide the fileName for all attachments` | 1054 |
| EML007 | 413 | yes | `Attachments size limit exceeded` | 1055 |
| EML008 | 429 | yes | `Maximum number of attempts exceeded` | 1056 |
| EML009 | 502 | no (logged) | `Email service failed <MGE code>` | 1058 |
| EML010 | 500 | no (logged with stack) | `Unhandled exception` / `Template misconfiguration <detail>` | 500 |

### 3.2 T2: `POST /api/v1/sms/send` (`operationId: sendSms`, tag `Sms`)

**Request `SmsSendRequest`** (Part 3 §4):

| Field | Type | Required | Rules |
|---|---|---|---|
| `messageBody` | string | yes | Not blank, at most `app.sms.max-message-length` (1500, the size of `SMS_TABLE.MESSAGE_BODY` NVARCHAR2) |
| `mobileNumbers` | string[] | yes | Alias `mobileNumber`. Each entry may hold several numbers separated by `[;,/\s]+`. After splitting, drop empty tokens, strip one leading `+`, and de-duplicate keeping order. Each number must match `^[1-9][0-9]{7,14}$` (international, no `+`). There must be at least 1 and at most `app.sms.max-recipients` (50) numbers |
| `msgCategory` | string | no | Etisalat only. Default `app.sms.defaults.msg-category` (`4.2`). Pattern `^[0-9]{1,3}(\.[0-9]{1,3})?$` |
| `expiryInSecs` | integer | no | Etisalat only. Default `app.sms.defaults.expiry-in-secs` (300). Range 1..86400 |

**Validation order:** missing (`SMS001`), message length (`SMS003`), recipient count after splitting (`SMS004`), each number (`SMS002`), `msgCategory`/`expiryInSecs` (`SMS005`).

**Router (`SmsServiceImpl#send`)**, per Part 3 §4:
1. If `app.sms.preferred-provider` ≠ `ETISALAT`, every number goes to Waselney.
2. If it is `ETISALAT`, route each number like this:
   - `excluded-prefixes` empty → Etisalat;
   - the number starts with a listed prefix → VeriPhone lookup of `country_code`. If that code is in `excluded-country-codes` → Waselney, otherwise Etisalat. **A VeriPhone failure also routes to Etisalat** (assumption, section 9);
   - no prefix match → Etisalat.
3. Send to the Waselney numbers first, then the Etisalat numbers, one provider call per number.
4. Collect **one result per number**. This fixes the legacy bug where a mixed-provider call lost the first provider's result.

**Live vs simulated.** `app.sms.live-send-enabled` (default `false` everywhere, decision Q3):
- When false, no provider HTTP call is made.
  - Waselney simulates the response `1701|<number>|<correlationId>`, so `messageId` = `<number>`.
  - Etisalat simulates statusCode 201, so `messageId` = `<number>`.
  - VeriPhone is not called; the number routes to Etisalat.
- `simulated: true` is set in the result.

**Etisalat client** (Part 3 §5):
- **Login:** `POST {etisalat.base-url}/login/user` with `{"username","password"}`. The token is cached in memory per pod. It is refreshed when it is within 60 s of `decodedValue.expiryDt`, or **once** on a 401 from send.
- **Send:** `POST {base}/campaigns/submissions/sms/nb`, header `Authorization: bearer <token>`. Body:
  - `recipient`, `msg`, `msgCategory`;
  - `contentType` `3.1`, `senderAddr` `AL RAMZ`, `priority` 0 (all three from `app.sms.defaults.*`);
  - `clientTxnId` = correlationId;
  - `expiryDt` = UTC now + expiryInSecs, formatted `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` (real UTC).
- **Result:** response `statusCode == 201` → SENT with `messageId` = `jobId`, falling back to the recipient. Any other value → FAILED (provider rejected).
- **No transport retry.** The legacy client retried 3 times with 5 s waits. Dropped: a read-timeout retry can send a duplicate SMS (section 9).

**Waselney client** (Part 3 §6):
- **Encoding:** encode the message once as UCS-2 hex (`msg.chars().mapToObj(c -> String.format("%04x", c)).collect(joining())`).
- **Request:** one call per number, `GET {waselney.send-url}?username=&password=&type=2&dlr=0&source=AL RAMZ&destination=<n>&message=<enc>`, with query values URL-encoded through `UriComponentsBuilder`.
- **Result:** the text response's first `|`-token `1701` → SENT with `messageId` = token[1]. Anything else → FAILED (provider rejected).

**Transport failures** (connect or read timeout, I/O error) → that number is FAILED (unavailable). Timeouts are `app.sms.connect-timeout` (5 s) and `app.sms.read-timeout` (10 s), applied through the `RestClient` request factory.

**Response `SmsSendResult`** (inside `GenericResponse.response`):
```yaml
SmsSendResult:
  required: [correlationId, simulated, results]
  correlationId: string
  simulated: boolean
  results: SmsRecipientResult[]   # one per de-duplicated number, request order
SmsRecipientResult:
  required: [mobileNumber, serviceProvider, status]
  mobileNumber: string            # normalised, no '+'
  serviceProvider: string, enum [ETISALAT, WASELNEY]
  status: string, enum [SENT, FAILED]
  messageId: string, nullable     # provider id; null when FAILED
```

**HTTP outcome:**
- 200 when at least one number is SENT. A partial failure still returns 200, and the per-number `status` shows it.
- When **all** numbers failed:
  - 502 `SMS009` (logged) if any failure was a provider rejection;
  - 503 `SMS010` (logged) if every failure was a transport failure.

**Statuses produced:** 200, 400, 401, 403, 500, 502, 503.

**Examples:**
```jsonc
// 200
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "5b1d0c3e-…", "simulated": false,
    "results": [ { "mobileNumber": "971501234567", "serviceProvider": "WASELNEY", "status": "SENT", "messageId": "88123456" },
                 { "mobileNumber": "14155550100", "serviceProvider": "ETISALAT", "status": "FAILED", "messageId": null } ] } }
// 400 (SMS001..SMS005)
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "SMS001",
  "errorMsg": "Request failed due to missing: messageBody, mobileNumbers", "response": { "correlationId": "5b1d…" } }
{ ... "errorCode": "SMS002", "errorMsg": "Invalid mobile number in: mobileNumbers[2]" ... }   // 1-based entry index
{ ... "errorCode": "SMS003", "errorMsg": "Field length exceeded: messageBody" ... }
{ ... "errorCode": "SMS004", "errorMsg": "Number of recipients exceeds 50" ... }
{ ... "errorCode": "SMS005", "errorMsg": "Invalid field: expiryInSecs" ... }
// 403 (role)            -> errorCode null
// 500 (SMS011, logged)  -> errorCode null
// 502 (SMS009, logged)  -> errorCode null
// 503 (SMS010, logged)  -> errorCode null
{ "responseCode": "503", "responseMessage": "Service Unavailable", "errorCode": null, "errorMsg": null, "response": { "correlationId": "5b1d…" } }
```

**In-process contract** (for `notification-otp`):
```java
// com.alramz.service.SmsService (interface, the only one added: it is a cross-feature contract like EmailService)
SmsSendResult send(SmsSendRequest request);   // generated com.alramz.model types
// Returns per-number results; never throws for provider failures (all-failed is visible in results).
// Throws SmsException(SMS001..SMS005) for caller-fixable input, same validation as the HTTP path.
```
`SmsController` throws `SmsException("SMS009"|"SMS010")` when no result is SENT, so the HTTP status reflects an all-failed send.

### 3.3 T3: `GET /api/v1/sms/report/{messageId}` (`operationId: getSmsReport`, tag `Sms`)

- **Path parameter** `messageId`: string, pattern `^[A-Za-z0-9-]{1,65}$`, checked manually in the service. A mismatch throws `SMS007`. This also stops query-string injection into the provider URL.
- **Provider:** Waselney only (Part 3 §6). Call `GET {waselney.report-url}?uname=&pwd=&usertype=0&id=<messageId>`.
- **Parsing:** parse the XML with a hardened `DocumentBuilderFactory`: `disallow-doctype-decl=true`, external entities off, `FEATURE_SECURE_PROCESSING`.
- **Mapping:** read every `DeliveryReport/DeliveryReport/Report` element. Each becomes a map of child-element name to its text. The `Report` field names are not documented in Part 3, so they are passed through as-is.
- **Results:**
  - The body contains `There is no record(s) found`, or there are zero `Report` elements → 404 `SMS008`.
  - The XML is unparseable or the HTTP status is not 2xx → 502 `SMS009`.
  - A transport failure → 503 `SMS010`.
- **When `live-send-enabled=false`:** no call is made, and the response is 404 `SMS008`. No real record can exist.

**Response `SmsReportResult`:**
```yaml
SmsReportResult:
  required: [correlationId, messageId, reports]
  correlationId: string
  messageId: string
  reports: array of { type: object, additionalProperties: { type: string } }
```

**Statuses produced:** 200, 400, 401, 403, 404, 500, 502, 503.
```jsonc
// 200
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "…", "messageId": "88123456", "reports": [ { "Status": "DELIVERED", "DeliveryDate": "2026-10-26 10:15:02" } ] } }
// 400
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "SMS007", "errorMsg": "Invalid message id", "response": { "correlationId": "…" } }
// 404
{ "responseCode": "404", "responseMessage": "Not Found", "errorCode": "SMS008", "errorMsg": "No SMS record found", "response": { "correlationId": "…" } }
// 403/500/502/503: errorCode null (as in 3.2)
```

**SMS error codes:**

| Code | HTTP | In body | errorMsg / log text |
|---|---|---|---|
| SMS001 | 400 | yes | `Request failed due to missing: <fields>` (order `messageBody, mobileNumbers`) |
| SMS002 | 400 | yes | `Invalid mobile number in: mobileNumbers[<i>]` |
| SMS003 | 400 | yes | `Field length exceeded: messageBody` |
| SMS004 | 400 | yes | `Number of recipients exceeds <max>` |
| SMS005 | 400 | yes | `Invalid field: <msgCategory\|expiryInSecs>` |
| SMS007 | 400 | yes | `Invalid message id` |
| SMS008 | 404 | yes | `No SMS record found` |
| SMS009 | 502 | no (logged) | `SMS provider rejected <provider> <code>` |
| SMS010 | 503 | no (logged) | `SMS provider unavailable <provider> <exception class>` |
| SMS011 | 500 | no (logged with stack) | `Unhandled SMS error` |

`SMS006` is deliberately unused. Malformed JSON is the service-wide `MGE-V-006`.

### 3.4 T4: SMS dispatch job (replaces `listenToBulkSMS` → JMS → `sendFitSmsMsg`)

**Bean and schedule:**
- Bean: `@Component("smsDispatchJobRunner") class SmsDispatchJob implements Schedulable`, in package `com.alramz.scheduler.job` (howto location).
- Schedule: `schedule_job` row with `job_group_name='notification'`, `schedule_id='smsDispatch'`, `schedule_mode='FIXED_DELAY'`, `delay=15000` (ms) and `enable='Y'`. To change the cadence, add a new changeset with an `UPDATE`.

**`run(ScheduleInfoBean)`**: the whole body is in try/catch and logs. It never throws.
1. If `app.sms.dispatch.enabled=false`, return right away. Startup already guarantees that `dispatch.enabled=true` implies brok is enabled and the statuses are configured (section 5).
2. **Claim.** One `TransactionTemplate` over `@Qualifier("brokTransactionManager")`:
   ```sql
   SELECT SERIAL_NO, MESSAGE_BODY, MOBILE_NO, SEND_ATTEMPTS FROM INSIGHT.SMS_TABLE
    WHERE STATUS = :pendingStatus
    ORDER BY PRIORITY, CREATION_DATE, SERIAL_NO
    FOR UPDATE SKIP LOCKED
   ```
   Limit it with JDBC `setMaxRows(batchSize)` **and** `setFetchSize(batchSize)`, not `FETCH FIRST`:
   - Oracle rejects `FETCH FIRST … FOR UPDATE` (ORA-02014).
   - The brok pool's `defaultRowPrefetch` (100) would otherwise fetch, and so lock, more rows than the batch.

   Then, in the same transaction, run `UPDATE INSIGHT.SMS_TABLE SET STATUS = :inProgressStatus WHERE SERIAL_NO IN (:ids)` and commit. This keeps the row lock to milliseconds and makes the batch exclusive to this pod.
3. **Per claimed row**, outside any transaction. Set a fresh UUID correlationId in MDC first. Every final update is guarded by `WHERE SERIAL_NO = :id AND STATUS = :inProgressStatus`, and `SEND_DATE` = Asia/Dubai local now.

   | Outcome | STATUS | MESSAGE_ID (truncated to 65 chars) | SEND_DATE | SEND_ATTEMPTS |
   |---|---|---|---|---|
   | `MOBILE_NO` (read as `BigDecimal.toPlainString()`) fails the number rule | `invalidMobileStatus` | unchanged | now | unchanged |
   | router result SENT | `sentStatus` (88) | `200\|<messageId>\|<correlationId>\|<provider>` | now | NVL+1 |
   | router result FAILED | `failedStatus` (1) | `500\|<correlationId>\|<provider>` (`503\|…` for a transport failure) | now | NVL+1 |
   | unexpected exception for the row | `failedStatus` (1) | `503\|<correlationId>\|NONE` | now | NVL+1 |

   The router is called with `SmsSendRequest{messageBody, mobileNumbers=[mobileNo], msgCategory=defaults (4.2), expiryInSecs=defaults (300)}`, through the **same** `SmsService`, so simulated mode applies here too.
4. **No retry.** Statuses 1 and invalid are final, and `pendingStatus` must differ from every final status (startup check).
5. **Log** one summary line per run: claimed, sent, failed and invalid counts, and duration. Per-row lines hold the serial number, provider, outcome and a masked number (last 4 digits). They never hold the message body.

**Multi-pod safety:** `FOR UPDATE SKIP LOCKED` together with the immediate in-progress status gives each pod a disjoint batch. `FIXED_DELAY` stops a pod from overlapping itself. **ShedLock or `SchedulerLockService` is not needed.** Neither prevents anything the claim does not already prevent, and both would serialise pods for no gain.

**Crash window:** rows claimed by a pod that dies before the final update stay in `inProgressStatus`. They are never retried automatically (decision Q9). Ops can find them by that status (section 9).

```java
// com.alramz.repository.SmsTableRepository (concrete @Repository, brok-gated; no interface: one implementation, no cross-feature caller)
List<SmsTableRow> claimPending(int batchSize);                       // select + mark in-progress, one brok tx
int markResult(long serialNo, int status, String messageId, LocalDateTime sendDate, boolean incrementAttempts);
record SmsTableRow(long serialNo, String messageBody, String mobileNo, Integer sendAttempts) {}
```

### 3.5 Codegen and contract file changes (`specs/openapi.yaml`, `pom.xml`)

- **Add paths** 3.1-3.3. Each one declares every status listed in its section, with the example above. 401 gets a description only.
- **Add schemas:** `TemplatedEmailRequest`, `TemplateAttachment`, `SmsSendRequest`, `SmsSendResult`, `SmsRecipientResult`, `SmsReportResult`.
- **Generic descriptions:** generalise the `GenericResponse.errorCode` description from "MGE-V-0xx" to "MGE-V-0xx / EML00x / SMS0xx". Do the same for `response` ("operation result: EmailSendResult, SmsSendResult, SmsReportResult, or EmailSendResult{correlationId} on errors").
- **`pom.xml`:**
  - set `modelsToGenerate` = `GenericResponse,EmailSendResult,EmailRequest,EmailAttachment,TemplatedEmailRequest,TemplateAttachment,SmsSendRequest,SmsSendResult,SmsRecipientResult,SmsReportResult`;
  - move `<useTags>true</useTags>` to be a direct child of `<configOptions>` and delete the `<tags>` wrapper.

---

## 4. Data Model & Persistence Changes

**This is the first Liquibase setup in `alramz-notification-service`** (decision Q6). The tables go on the Postgres `middleware` DB (decision Q7). The Oracle DDL is translated:
- `VARCHAR2` → `VARCHAR`;
- `NUMBER` → `INTEGER`/`SMALLINT`/`BIGINT`;
- `CLOB` → `TEXT`.

Unquoted, lower-case identifiers are used.

**Wiring:**
- **Dependencies:** `spring-boot-starter-liquibase` (BOM-managed); `org.postgresql:postgresql`, `com.oracle.database.jdbc:ojdbc11` and `com.oracle.database.nls:orai18n` (runtime); `h2` changes from `runtime` to `test` scope.
- **Masters:** `src/main/resources/db/changelog/{dev,docker,test,preprod,prod}/db.changelog-master.yaml`, each `includeAll: path: db/changelog/<profile>/sql` with `errorIfMissingOrEmpty: false` (wealth template).
- **Profile config:** each `application-<profile>.yml` sets `spring.liquibase.change-log: classpath:db/changelog/<profile>/db.changelog-master.yaml`.
- **Context test:** `AlramzNotificationServiceApplicationTests` points at the `test` master on H2 with `MODE=PostgreSQL`.

**Changesets.** Each identical file goes into all 5 `sql/` folders (dev, docker, test, preprod, prod). The author is `gaurav`, and each changeset id equals its filename stem. The `notification` token is in every filename because of the shared-DB collision risk (section 2).

| File | Contents | Rollback |
|---|---|---|
| `001-notification-schedule-job.sql` | Changeset `001-notification-schedule-job`: `CREATE TABLE IF NOT EXISTS schedule_job (…)` and `CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_job_group_schedule`, byte-identical to `onboarding-service/.../001-schedule-job.sql`'s first changeset. On the shared DB it is a no-op. It keeps a fresh or isolated DB bootable | none (a shared table must not be dropped) |
| `002-notification-email-tables.sql` | Changeset `002-notification-email-tables`. Creates `email_configurations` and `email_log` plus one index (below) | `DROP TABLE IF EXISTS email_log; DROP TABLE IF EXISTS email_configurations;` |
| `003-notification-sms-dispatch-job.sql` | Changeset `003-notification-sms-dispatch-job`: `INSERT INTO schedule_job (…) VALUES ('notification','smsDispatch','smsDispatchJobRunner','smsDispatchJobRunner','', 'FIXED_DELAY', NULL, 15000, NULL, 'Y', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (job_group_name, schedule_id) DO NOTHING` | `DELETE FROM schedule_job WHERE job_group_name='notification' AND schedule_id='smsDispatch';` |

**`email_configurations`** (from the user's DDL; changes from it are marked):

| Column | Type | Note |
|---|---|---|
| `id` | `VARCHAR(20)` PK | as DDL (`email_configurations_pk`) |
| `template_name` | `VARCHAR(100) NOT NULL UNIQUE` | **NOT NULL UNIQUE added**: the lookup by name must be deterministic |
| `number_of_placeholders` | `INTEGER NOT NULL DEFAULT 0` | **NOT NULL DEFAULT 0 added** |
| `max_attempts` | `INTEGER NOT NULL` | **NOT NULL added**: a null daily limit has no defined meaning |
| `from_email` | `VARCHAR(254) NOT NULL` | **widened from 50**, NOT NULL. Must be in `app.email.allowed-senders` |
| `has_attachments` | `SMALLINT` | unused (Part 1 §6) |
| `ar_subject`, `en_subject` | `VARCHAR(200)` | as DDL |
| `attachments_max_size` | `VARCHAR(20)` | as DDL (bytes as text, parsed safely) |
| `number_of_attachments` | `INTEGER` | as DDL |
| `type_of_attachments` | `VARCHAR(50)` | as DDL (all 9 codes fit in 44 characters) |
| `is_bulk` | `SMALLINT NOT NULL DEFAULT 0 CHECK (is_bulk IN (0,1))` | **per Q6** |
| `ar_body`, `en_body` | `TEXT` | from CLOB |
| `default_recipient` | `VARCHAR(200)` | unused |

**`email_log`** (Part 1 §4.2, with the undersized columns widened):

| Column | Type | Note |
|---|---|---|
| `id` | `BIGSERIAL` PK | replaces the Oracle sequence plus VARCHAR2(20) |
| `template_id` | `VARCHAR(20) NOT NULL` | no FK (an audit row must survive a template change) |
| `to_email` | `VARCHAR(4000) NOT NULL` | **widened from 50** |
| `cc_email`, `bcc_email` | `VARCHAR(4000)` | **widened from 50** |
| `email_body` | `VARCHAR(64) NOT NULL` | SHA-256 hex |
| `language` | `VARCHAR(2) NOT NULL` | |
| `date_time` | `TIMESTAMP NOT NULL` | Asia/Dubai local time |
| `invoker` | `VARCHAR(100) NOT NULL` | **widened from 20** (JWT application claim) |
| `ip_address` | `VARCHAR(45)` | **widened from 20 for IPv6** |
| `attachments_meta_data` | `TEXT` | **widened from VARCHAR2(20)** |

Index: `CREATE INDEX IF NOT EXISTS idx_email_log_template_invoker_time ON email_log(template_id, invoker, date_time)`. It serves both count queries. `to_email` is filtered after the index range, because it is too wide to index.

**Not created:**
- `INSIGHT.SMS_TABLE` (external, read and updated through brok only).
- The OTP tables (feature `notification-otp`).

**Data access** (repo pattern: `NamedParameterJdbcTemplate`, with SQL in `src/main/resources/sql/sql-queries.xml` through `SqlQueriesManager`; no JPA entities):
```java
// com.alramz.repository.EmailTemplateRepository  (@Repository, @Qualifier("middlewareNamedParameterJdbcTemplate"))
Optional<EmailTemplate> findByName(String templateName);
int countSentToday(String templateId, String invoker, String toOrNull, LocalDateTime dayStart, LocalDateTime dayEnd); // toOrNull == null -> bulk count
void insertLog(EmailLogEntry entry);
// com.alramz.repository.SmsTableRepository  (see 3.4; brok-gated, @Qualifier("brokNamedParameterJdbcTemplate"))
```
`EmailTemplate` and `EmailLogEntry` are Java records in `com.alramz.model`. They are hand-written, not generated, because they are not API models. One repository class covers both email tables, since they are one feature with one caller.

---

## 5. Security & Config Impact

**JWT / permit-all:**
- No change. All three new endpoints stay authenticated and carry `@JwtSecured(roles = "APP_NOTIFICATION")`. They are **not** added to `company.jwt.permit-all-urls` in any profile.
- `ProfileSecurityConfigTest` is extended to assert this.

**Datasources** (all 5 profile files, in the shape of the wealth `application-dev.yml` blocks; the H2 `spring.datasource` blocks are removed):
- **`middleware`:** `enabled: true`. `url`/`username` come from `COMPANY_DATASOURCE_MIDDLEWARE_*` env vars.
  - dev/docker/test: `plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD}`, **with no committed default**.
  - preprod/prod: `password` + `passwordVector` from env, and `cipher.password: ${CIPHER_MASTER_KEY}`.
  - Pool: `maximum-pool-size: 5`, `minimum-idle: 1`.
- **`brok`:** `enabled: ${COMPANY_DATASOURCE_BROK_ENABLED:false}`. URL and credentials come from `COMPANY_DATASOURCE_BROK_*` (same secret style as middleware). Pool max 3. `oracle.*` timeouts as in wealth.
- **`DatasourceConfiguration`:** new class, copied verbatim from wealth.

**Scheduler:**
- Add `@EnableScheduler(jobGroupName = "notification")` on `AlramzNotificationServiceApplication`.
- In all 5 profile files, set `company.scheduler.pool-size: 2`, `thread-group-name: notification-scheduled-tasks-` and `thread-name-prefix: Notification-TaskScheduler-Worker-`.
- `management-endpoints.enabled` stays `false`.

**New config `app.sms.*`** (`SmsProperties`, a record, `@ConfigurationProperties("app.sms")`). It goes in all 6 files, mirroring how `app.email` is laid out. Every secret is an env or Key Vault placeholder with an empty default:

| Key | Default | Notes |
|---|---|---|
| `live-send-enabled` | `${SMS_LIVE_SEND_ENABLED:false}` | **false in every profile** until credentials are supplied (Q3) |
| `preferred-provider` | `${SMS_PREFERRED_PROVIDER:WASELNEY}` | `ETISALAT` or anything else (anything else means Waselney) |
| `max-recipients` | `50` | |
| `max-message-length` | `1500` | |
| `connect-timeout` / `read-timeout` | `5s` / `10s` | `Duration` |
| `defaults.msg-category` / `expiry-in-secs` / `content-type` / `sender` / `priority` | `4.2` / `300` / `3.1` / `AL RAMZ` / `0` | Part 3 §7 |
| `etisalat.base-url` / `username` / `password` | `${SMS_ETISALAT_BASE_URL:}` / `${SMS_ETISALAT_USERNAME:}` / `${SMS_ETISALAT_PASSWORD:}` | password is a secret |
| `etisalat.excluded-prefixes` / `excluded-country-codes` | `${SMS_ETISALAT_EXCLUDED_PREFIXES:}` / `${SMS_ETISALAT_EXCLUDED_COUNTRY_CODES:}` | comma lists |
| `waselney.send-url` / `report-url` / `username` / `password` | `${SMS_WASELNEY_SEND_URL:}` / `${SMS_WASELNEY_REPORT_URL:}` / `${SMS_WASELNEY_USERNAME:}` / `${SMS_WASELNEY_PASSWORD:}` | two URLs because Part 3 states send and report as different paths |
| `veriphone.base-url` / `api-key` | `${SMS_VERIPHONE_BASE_URL:https://api.veriphone.io}` / `${VERIPHONE_API_KEY:}` | same env var name as onboarding |
| `dispatch.enabled` | `${SMS_DISPATCH_ENABLED:false}` | **off by default** (the legacy webMethods consumer may still own `SMS_TABLE`) |
| `dispatch.batch-size` | `50` | |
| `dispatch.status.pending` / `in-progress` / `invalid-mobile` | `${SMS_STATUS_PENDING:}` / `${SMS_STATUS_IN_PROGRESS:}` / `${SMS_STATUS_INVALID_MOBILE:}` | **no defaults**: the DB owner has not confirmed them (Q9) |
| `dispatch.status.sent` / `failed` | `88` / `1` | legacy |

**Fail-fast startup checks** (in `SmsProperties`' compact constructor, or a small `@PostConstruct` validator):
- When `live-send-enabled=true`, startup fails unless these are present:
  - the Waselney `send-url`, `username` and `password`;
  - the Etisalat `base-url`, `username` and `password` when `preferred-provider=ETISALAT`;
  - the VeriPhone `api-key` when `excluded-prefixes` is non-empty.
- When `dispatch.enabled=true`, startup fails unless all of these hold:
  - `company.datasource.brok.enabled=true`;
  - pending, in-progress and invalid-mobile are all set;
  - all 5 status values are distinct;
  - pending is neither 88 nor 1. This prevents a resend loop.
- Nothing is checked while both flags are false. That is the current state, so startup needs no new secrets beyond the DB ones.

**`sql.file: classpath:sql/sql-queries.xml`** goes in `application.yml`.

**Masking and logging:**
- No `SensitiveDataMasker` change.
- `company.logging.request/response.include-payload` and `database-logging.enabled` must stay unset or false for this service. The masker does not cover `body`, `messageBody`, `placeholders` or `content`.
- No `@Loggable` on the new controllers.
- **Never logged anywhere:** message bodies, email bodies or subjects, placeholder values, attachment content, full email addresses, full mobile numbers (mask to the last 4 digits), provider passwords, the VeriPhone key, the Etisalat token.
- **Credential-bearing URLs.** The Waselney send and report URLs and the VeriPhone URL carry credentials in the query string. Spring's `ResourceAccessException` message embeds the full request URI, so for these calls the code must:
  - never log the exception message, stack trace or URI;
  - never attach the exception as a cause;
  - log only the provider, the exception class name and the HTTP status.
  The starter's `alramzRestTemplateLoggingInterceptor` must not be wired into these clients.

**XML (getReport):** XXE-hardened parser (section 3.3).

**Placeholder HTML-escaping:** when the template body is HTML, placeholder values are escaped (3.1 step 6). This blocks caller-supplied markup or links from being injected into Al Ramz-branded mail.

**Operational follow-ups** (not code in this feature; `k8s/` and `infra/` are out of scope):
- Provide `COMPANY_DATASOURCE_MIDDLEWARE_*`, `CIPHER_MASTER_KEY` and the optional `COMPANY_DATASOURCE_BROK_*` to the notification pods.
- Provide the `SMS_*` and `VERIPHONE_API_KEY` secrets once they are available.
- Confirm the `SMS_STATUS_*` values with the INSIGHT DB owner.
- Grant `APP_NOTIFICATION` to calling applications.
- Insert the `email_configurations` rows, with every `from_email` listed in `APP_EMAIL_ALLOWED_SENDERS`.

---

## 6. Edge Cases & Error Behaviors

**Exceptions.** There is one new exception per feature area. Each carries a code, and `GlobalExceptionHandler` maps the code to a status, matching the existing `EmailValidationException`/`EmailServiceException` style:
- **`TemplatedEmailException(String code, String message[, Throwable cause])`.** EML001-006 → 400, EML007 → 413, EML008 → 429: these carry `errorCode`/`errorMsg`. EML009 → 502 and EML010 → 500: these have null codes and are logged.
- **`SmsException(String code, String message[, Throwable cause])`.** SMS001-005 and SMS007 → 400, SMS008 → 404: these carry codes. SMS009 → 502, SMS010 → 503, SMS011 → 500: these have null codes and are logged without the cause message (section 5).

The existing arms stay as they are: `AccessDeniedException` → 403, `HttpMessageNotReadableException` → MGE-V-005/006, and `Exception` → 500. The catch-all now logs the generic text "Unhandled error" instead of "MGE-B-004 … email gateway" when the request is not `/api/v1/email/send`. Optionally, it can keep one generic message.

| # | Scenario | Exception | HTTP | errorCode |
|---|---|---|---|---|
| E1 | No or invalid JWT on any new endpoint | Spring Security (starter) | 401 (starter body) | n/a |
| E2 | JWT without `APP_NOTIFICATION` | `AccessDeniedException` | 403 | null |
| E3 | Malformed JSON on T1/T2 | `HttpMessageNotReadableException` | 400 | MGE-V-006 |
| E4 | T1 `attachments[1].content` not Base64 | `HttpMessageNotReadableException` | 400 | MGE-V-005 |
| E5 | T1 `to` missing and `language`=`en` | `TemplatedEmailException` | 400 | EML001 `…missing: to, language` |
| E6 | T1 `cc: "a@x.com;bad"` | `TemplatedEmailException` | 400 | EML001 `…missing: cc` |
| E7 | T1 unknown `templateName` | `TemplatedEmailException` | 400 | EML002 |
| E8 | T1 template needs 2 placeholders, 1 sent; or 0 sent | `TemplatedEmailException` | 400 | EML003 |
| E9 | T1 3 attachments, template allows 2 (or null) | `TemplatedEmailException` | 400 | EML004 |
| E10 | T1 `contentType: "EXE"`, or `MSWORD` with `.pdf` | `TemplatedEmailException` | 400 | EML005 |
| E11 | T1 attachment `fileName` blank | `TemplatedEmailException` | 400 | EML006 |
| E12 | T1 decoded total > `attachments_max_size`; gateway V-008; Content-Length over the cap | `TemplatedEmailException` | 413 | EML007 |
| E13 | T1 `attachments_max_size` unparseable with attachments present | `TemplatedEmailException` | 500 | null (EML010 logged) |
| E14 | T1 non-bulk: today's rows for template+to+invoker ≥ `max_attempts`; bulk: template+invoker | `TemplatedEmailException` | 429 | EML008 |
| E15 | T1 yesterday's (Dubai) rows only | none | 200 | — |
| E16 | T1 `from_email` not in `allowed-senders` (gateway V-007) or body > `max-body-length` (V-003) | `TemplatedEmailException` | 500 | null (EML010 logged with the MGE code) |
| E17 | T1 Graph failure (any MGE-B) | `TemplatedEmailException` | 502 | null (EML009 logged) |
| E18 | T1 send OK, `email_log` insert throws | none (error logged) | 200 | — |
| E19 | T1 HTML template, placeholder `<b>x</b>` | none | 200; body contains `&lt;b&gt;x&lt;/b&gt;` | — |
| E20 | T2 `messageBody` blank / `mobileNumbers` missing | `SmsException` | 400 | SMS001 |
| E21 | T2 `mobileNumbers: ["97150123456x"]` | `SmsException` | 400 | SMS002 |
| E22 | T2 body of 1501 characters | `SmsException` | 400 | SMS003 |
| E23 | T2 51 distinct numbers after splitting | `SmsException` | 400 | SMS004 |
| E24 | T2 `expiryInSecs: 0` / `msgCategory: "abc"` | `SmsException` | 400 | SMS005 |
| E25 | T2 `["971501234567; 971501234567"]` | none | 200, one result (de-duplicated) | — |
| E26 | T2 `live-send-enabled=false` | none | 200, `simulated: true`, every number SENT | — |
| E27 | T2 one number SENT, one FAILED | none | 200, per-number status | — |
| E28 | T2 all FAILED, at least one provider rejection (Waselney ≠1701, Etisalat ≠201) | `SmsException` | 502 | null (SMS009) |
| E29 | T2 all FAILED by timeout or I/O | `SmsException` | 503 | null (SMS010) |
| E30 | T2 Etisalat 401, then success after one refresh | none | 200 | — |
| E31 | T2 Etisalat 401 twice | FAILED for that number | 200/502 per E27/E28 | — |
| E32 | T2 ETISALAT preferred, prefix matches, VeriPhone down | none, routed to Etisalat | per result | — |
| E33 | T3 `messageId` = `abc&pwd=x` | `SmsException` | 400 | SMS007 |
| E34 | T3 provider "There is no record(s) found", or simulated mode | `SmsException` | 404 | SMS008 |
| E35 | T3 XML has a DOCTYPE / unparseable / non-2xx | `SmsException` | 502 | null (SMS009) |
| E36 | T3 timeout | `SmsException` | 503 | null (SMS010) |
| E37 | Any unexpected runtime error on T1/T2/T3 | `Exception` catch-all | 500 | null |
| E38 | T4 `dispatch.enabled=false` | none | no SQL issued | — |
| E39 | T4 two pods claim at once | none | disjoint row sets (SKIP LOCKED) | — |
| E40 | T4 row `MOBILE_NO` invalid | none | STATUS=invalid, attempts unchanged | — |
| E41 | T4 router SENT / FAILED / row exception | none | STATUS 88 / 1 / 1, MESSAGE_ID per 3.4, attempts +1 | — |
| E42 | T4 MESSAGE_ID over 65 characters | none | truncated to 65 | — |
| E43 | T4 brok down during claim | logged, run ends | rows untouched | — |
| E44 | Startup with `dispatch.enabled=true` and brok disabled, a status missing, or duplicate statuses | `IllegalStateException` | app fails to start | — |
| E45 | Startup with `live-send-enabled=true` and required credentials missing | `IllegalStateException` | app fails to start | — |

---

## 7. Open Questions & Decisions (decision log)

The previous run raised Q1-Q7, and the user answered them. Q8-Q9 were answered alongside. All of these are authoritative. The exact option text from the previous run was not kept on disk, so the options below are summarised from that run's report.

| Q | Question | Options offered (summary) | User's choice |
|---|---|---|---|
| Q1 | How much of OTP (Part 4) is in scope? | Full Part 4 / lookup only / defer | **Full Part 4 flow** (sendOTP, validateOTP, getMessagesLookup, OTP_MANAGEMENT, OTP_ATTEMPTS), delivered in a **separate** feature (Q5) |
| Q2 | How much of the SMS gateway (Part 3) is in scope? | Send endpoint only / send + dispatch job / full Part 3 | **Full Part 3**: POST send through the Etisalat/Waselney router, the `INSIGHT.SMS_TABLE` polling dispatch job, and getReport |
| Q3 | SMS provider credentials? | Supply now / placeholders, live off / block | **Not available yet.** Read URLs and credentials from env or Key Vault placeholders and keep live sending **off** (simulated) until they are supplied → `app.sms.live-send-enabled=false` |
| Q4 | Which contract? | New `/api/v1` only / new + legacy adapters (programme decision 2) / legacy only | **New `/api/v1` endpoints only**: GenericResponse, `@JwtSecured(roles = "APP_NOTIFICATION")`, **no legacy adapters**. This overrides programme decision 2 for this feature |
| Q5 | How to split the work? | One feature / 2 features / keep the programme's 4 | **2 features, split along dependency lines.** This feature = Part 1 templated email + Part 3 SMS gateway. **The next feature, `notification-otp` (not in this spec), delivers the full Part 4 OTP flow on top of both channels.** It reuses `EmailService#sendEmail` and `SmsService#send` |
| Q6 | Who owns the schema? | Service via Liquibase / DBA-owned existing Oracle tables | **The service fully owns its schema via Liquibase.** Create `email_configurations` and `email_log` now; the OTP tables come with the OTP feature. Widen the known undersized columns (recipients, IPv6 `ip_address`, `attachments_meta_data`). `is_bulk NOT NULL DEFAULT 0`. Follow the repo's Liquibase conventions. This reverses feature 1's "DB-less" statement |
| Q7 | Which datasource? | Postgres `middleware` / Oracle `MIDDLEWARE` schema | **Postgres `middleware`**, with the Oracle DDL translated to Postgres types, using the starter middleware datasource. `INSIGHT.SMS_TABLE` stays an external Oracle table reached through the starter's `brok` datasource with an explicit `@Qualifier`. It is never created or altered by Liquibase |
| Q8 | `EMAIL_LOG` semantics? | (answered with Q6/Q7) | Insert an audit row after each **successful** send, and count today's rows against `MAX_ATTEMPTS` (`IS_BULK` scopes, per Part 1 §3.6). A failed audit insert must not change the response once the mail is sent |
| Q9 | `SMS_TABLE` statuses and retry? | (answered with Q2) | **Configurable, no retry.** Pick up rows with a configurable pending STATUS. Claim them with `FOR UPDATE SKIP LOCKED` plus a configurable in-progress status. Sent = 88 and failed = 1 (final). Invalid mobile gets a configurable status. Values come from config until the DB owner confirms them. Multi-pod safety comes from the claim (ShedLock only if justified: it is not, see 3.4) |

Non-blocking defaults from the previous run that were applied:
- reuse `EmailService#sendEmail`, `GenericResponse` and the sender allow-list;
- EML001-008 in the body, EML009/010 logged only, under the existing `@Order(0)` handler;
- never log secrets or payloads;
- the scheduler choice was resolved in section 2 (starter scheduler).

**Re-check for new blocking gaps in this run: none.** These items were considered and judged non-blocking, because each is reversible, cheap, or settled by repo precedent:
- the Oracle `FETCH FIRST`/`FOR UPDATE` limitation (technical, resolved in 3.4);
- coexistence with the legacy `SMS_TABLE` consumer (the dispatch flag is off by default);
- the undocumented Waselney `Report` fields (passed through);
- the absence of template seed rows (data);
- HTML-escaping of placeholders.

All are listed in section 9.

---

## 8. Explicit Acceptance Criteria

Contract and codegen:
- [ ] `specs/openapi.yaml` declares `POST /api/v1/email/template/send` (`sendTemplatedEmail`, tag `TemplatedEmail`), `POST /api/v1/sms/send` (`sendSms`, tag `Sms`) and `GET /api/v1/sms/report/{messageId}` (`getSmsReport`, tag `Sms`).
- [ ] Each new path lists exactly the statuses in 3.1/3.2/3.3, with the examples given. 401 has a description only.
- [ ] Schemas `TemplatedEmailRequest`, `TemplateAttachment` (`@JsonAlias` for `contenttype`/`filename`), `SmsSendRequest` (alias `mobileNumber`), `SmsSendResult`, `SmsRecipientResult` and `SmsReportResult` exist.
- [ ] `pom.xml` has `<useTags>true</useTags>` directly under `<configOptions>` and no `<tags>` element. The generated interfaces are `EmailApi`, `TemplatedEmailApi` and `SmsApi`, and `ApiApi` is no longer generated. `EmailController implements EmailApi`.
- [ ] `modelsToGenerate` lists the 10 models in 3.5.
- [ ] `mvn -pl services/alramz-notification-service -am test-compile` passes.

Persistence:
- [ ] `pom.xml` adds `spring-boot-starter-liquibase`, `postgresql` (runtime), `ojdbc11` (runtime) and `orai18n` (runtime). `h2` is `test` scope.
- [ ] `src/main/resources/db/changelog/{dev,docker,test,preprod,prod}/db.changelog-master.yaml` exist. Each `sql/` folder holds byte-identical `001-notification-schedule-job.sql`, `002-notification-email-tables.sql` and `003-notification-sms-dispatch-job.sql`, with changeset ids equal to the filename stems (`find … -name '00?-notification-*.sql'` returns 15 files).
- [ ] `002` creates the tables with the exact column types and constraints in section 4 (widened columns, `is_bulk SMALLINT NOT NULL DEFAULT 0` with CHECK, `template_name NOT NULL UNIQUE`), plus `idx_email_log_template_invoker_time`. It has a `--rollback`.
- [ ] `003` inserts the `notification/smsDispatch/smsDispatchJobRunner` FIXED_DELAY 15000 row with `ON CONFLICT … DO NOTHING`, and has a `--rollback`.
- [ ] No file anywhere creates or alters `SMS_TABLE` (grep `SMS_TABLE` in `db/changelog` finds nothing).
- [ ] `config/DatasourceConfiguration` re-exports `middlewareDataSource`/`middlewareTransactionManager` as `@Primary`.
- [ ] All 5 profile yml files enable `company.datasource.middleware`, set `spring.liquibase.change-log` to their own master, and contain no `spring.datasource` H2 block.
- [ ] preprod and prod use `password`/`passwordVector` + `cipher.password: ${CIPHER_MASTER_KEY}` and no `plainPassword`.
- [ ] No committed datasource password default in any file.
- [ ] `brok.enabled` defaults to `false`.
- [ ] Every `brok` injection carries `@Qualifier("brok…")`, and `SmsTableRepository` is `@ConditionalOnProperty(prefix="company.datasource", name="brok.enabled", havingValue="true")`.
- [ ] `AlramzNotificationServiceApplicationTests#contextLoads` passes with middleware on H2 (`MODE=PostgreSQL`) and the `test` changelog applied.

Security and config:
- [ ] None of the 3 new paths appears in any `permit-all-urls`. `ProfileSecurityConfigTest` asserts this for all 6 files.
- [ ] Each new controller method carries `@JwtSecured(roles = "APP_NOTIFICATION")`. A test proves that a role failure gives 403 with `errorCode` null.
- [ ] All 6 yml files define `app.sms.*` per section 5, with `live-send-enabled` defaulting to `false` and `dispatch.enabled` defaulting to `false`. The pending, in-progress and invalid-mobile statuses have no default value.
- [ ] `AlramzNotificationServiceApplication` carries `@EnableScheduler(jobGroupName = "notification")` and binds `SmsProperties`.
- [ ] Startup-validation tests cover E44 and E45 (enabled flags with missing or duplicate values throw). With both flags false, nothing is checked.
- [ ] `RequestSizeLimitConfig` covers `/api/v1/email/template/send` and gives EML007/413 there.

Templated email (unit tests, Mockito-first per `testing-patterns.md`):
- [ ] Every scenario E5-E19 produces the status and code in section 6, with `errorMsg` matching the 3.1 table text.
- [ ] A successful send calls `EmailService#sendEmail` exactly once, with:
  - `from` = `from_email`;
  - comma-normalised recipients;
  - the stored subject;
  - a body with `%1%`/`%2%` replaced at every occurrence;
  - attachments carrying the MIME types from the 3.1 table (one assertion per row of that table).
- [ ] After a successful send, exactly one `email_log` row is inserted, with:
  - `email_body` = SHA-256 hex of the rendered body;
  - `attachments_meta_data` in the `{"attachmentMetaData":[{"ct","fn","s"}]}` shape, or `{}` when there are no attachments;
  - `invoker` from `JwtContext` (`"unknown"` when null);
  - `date_time` from the injected Asia/Dubai clock.
  No row is inserted on any failure path.
- [ ] The daily count uses the bulk query (no `to`) when `is_bulk=1` and the per-recipient query otherwise, both bounded by the Dubai day.
- [ ] A test against H2 proves `countSentToday` + `insertLog` + `findByName` SQL runs on the `002` schema.

SMS (unit tests):
- [ ] E20-E29 give the listed status and code.
- [ ] Simulated mode makes **zero** HTTP calls: the clients are mocked or a `MockRestServiceServer` asserts no request.
- [ ] Routing:
  - `WASELNEY` (or any non-`ETISALAT` value) sends everything to Waselney;
  - `ETISALAT` with empty prefixes sends everything to Etisalat;
  - a prefix match whose VeriPhone `country_code` is in the excluded codes goes to Waselney, and one that is not goes to Etisalat;
  - a VeriPhone failure goes to Etisalat;
  - Waselney numbers are sent before Etisalat numbers.
- [ ] Waselney encodes `"Hi ع"` as `0048006900200639`, URL-encodes the query, maps `1701|123|…` to SENT/`123` and anything else to FAILED.
- [ ] Etisalat:
  - `expiryDt` is in UTC with the `…SSS'Z'` format;
  - 201 maps to SENT with `messageId` = `jobId`;
  - a 401 triggers exactly one token refresh and one re-send;
  - the token is reused across calls until it is within 60 s of expiry.
- [ ] getReport (E33-E36): a DOCTYPE-bearing response is rejected (502), and a two-`Report` XML gives two maps.
- [ ] Log capture tests prove that no log line contains:
  - the message body;
  - a full mobile number;
  - a provider password or the VeriPhone key;
  - a Waselney/VeriPhone URL query string. This must also hold when a `ResourceAccessException` whose message contains the URL is thrown.

Dispatch job (unit tests with a mocked `SmsTableRepository` and `SmsService`, plus a repository SQL test):
- [ ] E38: the repository is never touched when the flag is off.
- [ ] E40-E42: the exact STATUS, MESSAGE_ID format and truncation, SEND_ATTEMPTS increment and guard (`AND STATUS = :inProgressStatus`) are passed to `markResult`.
- [ ] E43: an exception in claim or in one row does not escape `run()`, and the other rows in the batch are still processed.
- [ ] The claim SQL in `sql-queries.xml` contains `FOR UPDATE SKIP LOCKED` and does **not** contain `FETCH FIRST`. The claim statement sets `maxRows` and `fetchSize` to `batchSize`, and the select and the in-progress update run in one brok `TransactionTemplate`.

Manual and final:
- [ ] `apiCollection.http` has a positive and a negative block for each new endpoint, with `Authorization: Bearer {{authToken}}` and only synthetic `example.com` addresses or `9715000000xx` numbers.
- [ ] `mvn -pl services/alramz-notification-service -am test` passes.

---

## 9. Self-Critique

**Confidence: medium.**

Every section cites verified files. All 9 user decisions are applied, and no blocking gap remains. Confidence is not higher for three reasons:
- the Oracle claim mechanics cannot be verified offline;
- three provider protocols (Etisalat, Waselney, VeriPhone) are specified only from Part 3's prose;
- this is the service's first datasource and Liquibase setup, on a DB shared with other services.

**Residual non-blocking assumptions:**
1. **OTP templates belong to `notification-otp`.** The user's earlier wording ("email and OTP templates" in feature A) could be read as putting OTP templates here. But OTP templates live in `OTP_CONFIGURATION` (`EMAIL_MESSAGE_*` / `SMS_MESSAGE_*`), which Q5/Q6 assign to the OTP feature. This feature's templated email covers `EMAIL_CONFIGURATIONS` only.
2. **`INSIGHT.SMS_TABLE` lives on the `brok` datasource** (QA URL `…/BROKQA`, user `insight` in the wealth dev yml). This follows Q7 and wealth's "INSIGHT schema on brok". If SMS_TABLE is actually on `integration`, only the qualifier and the gating property change.
3. **Claim semantics.** Two behaviours are assumed:
   - Oracle `FOR UPDATE SKIP LOCKED` locks rows as they are fetched;
   - `setMaxRows` + `setFetchSize(batchSize)` therefore claims at most `batchSize` rows despite the pool's `defaultRowPrefetch=100`.
   02_plan or 03_implement should confirm this against BROKQA with two concurrent sessions. The fallback is `WHERE ROWNUM <= :batchSize` without `ORDER BY` (this loses priority ordering).
4. **Dispatch defaults to off** (`SMS_DISPATCH_ENABLED=false`), because the legacy webMethods listener may still consume `SMS_TABLE`. Running both would double-send. Cutover = set the statuses and flip the flag.
5. **Stuck rows.** Rows left in `inProgressStatus` by a crashed pod are not recovered automatically (Q9, no retry). There is no reaper; ops resets them by status.
6. **No transport retry for SMS sends**, and one token refresh for Etisalat (legacy: 3 retries with 5 s waits). Reason: a read-timeout retry can deliver the same SMS twice, and a 15 s stall blocks request threads.
7. **VeriPhone failure routes to Etisalat** (the legacy "non-matching → Etisalat" default) instead of failing the whole call with 503.
8. **Mobile number rule** `^[1-9][0-9]{7,14}$` after stripping one leading `+`. It approximates E.164. The legacy `commonValidator.validateMobileNumber` rules were not available.
9. **Partial SMS success returns 200** with per-number status. All-failed returns 502 or 503. This fixes Part 3's mixed-provider result loss.
10. **Etisalat `messageId` in the API = `jobId`, but `SMS_TABLE.MESSAGE_ID` keeps the legacy layout.** The layout is code|recipient|correlationId|provider; recipient is the legacy content, which also fits 65 characters for numbers of up to 15 digits. The `jobId` is logged.
11. **getReport `Report` fields are passed through** as name→text maps, because Part 3 does not document them. `waselney.report-url` is a separate key from `send-url`, because Part 3 writes the two endpoints in an inconsistent form.
12. **Placeholder values are HTML-escaped when the template body is HTML.** This is a security-motivated deviation from the legacy literal replace. If any template relies on raw HTML placeholders, it renders escaped markup, and the fix is a one-line revert. Flag for 04_security.
13. **Template rows the gateway rejects.** A template whose rendered body exceeds `app.email.max-body-length` (10000), or whose `from_email` is not allow-listed, returns 500 (EML010). Ops must size templates or raise that key.
14. **Daily-limit race.** The limit is check-then-send-then-insert, so concurrent requests can exceed `max_attempts` by a few. Legacy has the same race; it is accepted. Ceiling: a `SELECT … FOR UPDATE` or a counter row would be needed if exact limits matter.
15. **Schema deviations from the user's DDL.** Beyond the requested widenings, these are added:
    - `template_name NOT NULL UNIQUE`;
    - `max_attempts NOT NULL`;
    - `from_email NOT NULL` (widened to 254);
    - `number_of_placeholders NOT NULL DEFAULT 0`;
    - `invoker` widened to 100.
    Each removes an undefined-behaviour case.
16. **Body `correlationId` is not part of the request.** Correlation comes from the `X-Correlation-ID` header only (feature 1 precedent). A body value is ignored.
17. **Binding-level errors keep the service-wide codes** MGE-V-005/006 on T1/T2, rather than EML/SMS codes. The handler has no endpoint context at that point.
18. **Pre-existing test failure.** `ProfileSecurityConfigTest` expects `secret: ${JWT_SECRET}`, but `application-dev.yml` currently has a default fallback. That is an uncommitted change outside this feature. 03_implement should record it as a baseline failure, not as something this feature caused.
19. **Sizing (question only, no change).**
    - The dispatch job runs one row at a time, 50 rows per 15 s per pod. Worst case is 50 × 10 s timeouts per run.
    - The new pools add up to 5 middleware + 3 brok connections per pod (×5 replicas) on shared databases.
    - `service.yaml` at `-Xmx512m` is probably fine. Revisit if the SMS volume is high.

**Weakest part:** the dispatch claim (3.4) together with the shared-DB Liquibase rollout (section 4).
- The claim's correctness rests on Oracle locking behaviour under JDBC row limits (assumption 3), which no test in this repo can prove against H2.
- Liquibase is being introduced into a service whose `DATABASECHANGELOG` is shared with five other services. The `notification` filename token prevents id collisions, but the first deploy should still be watched for lock or checksum issues.
