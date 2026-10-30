# Spec: notification-graph-gateway (Programme feature 1 of 4, Part 2: Microsoft Graph Send Email gateway)

Source: `.claude/modules/features/notification-graph-gateway/source-design-doc.md` (user request, programme decisions, Part 2).
This is a re-run. The previous 01_elicit run was BLOCKED on 5 questions, and the user has now answered all of them (see section 7).

---

## 1. Feature Objective & Scope

**Problem.** `alramz-notification-service` already sends mail through the Microsoft Graph Java SDK, but the gateway is not production-safe:
- it logs a live Graph access token;
- every failure becomes 503;
- every body is sent as HTML;
- `to` accepts only one address;
- there is no sender control;
- the endpoint is public;
- the request models are hand-copied and have drifted from the contract.

This feature hardens it into the shared **email gateway**. Programme features 2 (templated email, Part 1) and 4 (OTP, Part 4) will call it **in-process**.

**Target service:** `services/alramz-notification-service` only. It is an existing service, so no scaffold is needed.

**Triggers:**
1. HTTP: `POST /api/v1/email/send`. This changes the existing operation in place.
2. In-process: the `com.alramz.service.EmailService#sendEmail(EmailRequest)` Java call, used by later features.

**Done means:**
- One authenticated endpoint and one in-process service method share the same validation, sender allow-list, Graph dispatch and typed errors.
- Responses use the programme envelope with real HTTP statuses (200/400/401/403/413/429/500/502).
- No token is fetched or logged at startup.

**In scope:**
- Contract change to `specs/openapi.yaml` (`sendEmail`).
- Model generation (the hand-copied models are deleted).
- `EmailServiceImpl` rewrite: multi-recipient, cc/bcc, HTML/TEXT detection, `saveToSentItems=true`, sender allow-list, total-attachment cap, one retry on Graph 401, mapping of Graph 429/other errors.
- `GlobalExceptionHandler` rewrite.
- `GraphConfig` defect removal.
- A request-size guard.
- JWT on the endpoint.
- Config updates in all 6 `application*.yml`.
- `apiCollection.http` update.
- Tests.

**Out of scope (explicit):**
- **No legacy adapter endpoint** (for example `/invoke/EmailGateway.services.Microsoft.Graph/sendEmail` or `/sendEmail`) **for this feature.** This deliberately overrides programme decision 2 ("BOTH contracts") for feature 1 only, because Part 2 was only ever called in-process (user answer Q1). Features 2 (templated email) and 4 (OTP) **still** ship their legacy adapters.
- No provider dispatcher or routing layer (Part 2 §7 "outer dispatcher"). `app.email.service-provider` plus `@ConditionalOnProperty` stays as-is.
- No persistence and no Liquibase. Programme decision 3 starts applying with feature 2.
- No templating, no `EMAIL_LOG`, no daily limits (Part 1). No SMS (Part 3). No OTP (Part 4).
- No Graph large-attachment upload sessions (attachments over 3 MB). The cap prevents them.
- No change to `alramz-api-starter` (masking, JWT, correlation filter), `infra/`, `k8s/` or `.github/`.
- No fix to the generator's `<tags>` nesting in `pom.xml`, which makes the generated interface `ApiApi` instead of `EmailApi`. Keep `ApiApi`.

---

## 2. Repository Grounding

| Concern | Existing artifact (verified) | How this spec uses it |
|---|---|---|
| Contract | `services/alramz-notification-service/specs/openapi.yaml`: path `/api/v1/email/send`, `operationId: sendEmail`, schemas `EmailRequest`, `EmailAttachment`, `EmailSendResponse`, `ErrorResponse`; root `security: - BearerAuth: []` | **Amend in place.** Keep the path and operationId. Replace the response schemas with `GenericResponse` + `EmailSendResult`. This service's file is `openapi.yaml`, not `apiSpecs.yaml`. Its paths carry the full `/api/v1` prefix and `EmailController` has no class-level `@RequestMapping`. Keep that local precedent; the checklist's bare-path rule does not apply here. |
| Codegen | `pom.xml` `openapi-generator-maven-plugin` 7.23.0, `interfaceOnly`, `useSpringBoot3`, `performBeanValidation`, `modelsToGenerate = EmailSendResponse,ErrorResponse`, apiPackage `com.alramz.api`, modelPackage `com.alramz.model` | Change `modelsToGenerate` to `GenericResponse,EmailSendResult,EmailRequest,EmailAttachment`. |
| Hand-copied models (defect) | `src/main/java/com/alramz/model/EmailRequest.java` and `EmailAttachment.java`, stamped `@Generated` on 2026-09-17 but hand-maintained (attachment field is `name`; `to` is `@Email`, so one address only) | **Delete both.** They are generated from the contract from now on. |
| Controller | `controllers/EmailController.java` implements generated `com.alramz.api.ApiApi`, returns `ResponseEntity<EmailSendResponse>` | Return `ResponseEntity<GenericResponse>`. Add `@JwtSecured(roles = "APP_NOTIFICATION")`. |
| Service | `service/EmailService.java` (interface) and `service/impl/EmailServiceImpl.java` (Graph send via `graphClient.users(from).sendMail(UserSendMailParameterSet...)`, `BodyType.HTML` hard-coded line 165, single `toRecipients` lines 169-173, `withSaveToSentItems(false)` line 78, every exception becomes `EmailServiceException(503, ...)` line 99, micrometer `Tracer` span `graph.send_mail`) | Rewrite the implementation. Keep the interface (it is the in-process entry point). Keep the tracing span. |
| Graph client | `config/GraphConfig.java`: `ClientSecretCredential` + `TokenCredentialAuthProvider` + `GraphServiceClient.builder()`, `@ConditionalOnProperty(app.email.service-provider=MICROSOFT_GRAPH)`; **lines 47-50 call `credential.getToken(...).block()` at startup and log `"Access Token: {}"` at INFO** | Delete lines 47-50 and the now-unused `AccessToken`/`TokenRequestContext` imports. Nothing else changes. |
| Graph SDK | `com.microsoft.graph:microsoft-graph:3.8.0` (pom property `msgraph.legacy.version`), transitive `microsoft-graph-core:2.0.4` (present in `~/.m2`) | Keep the SDK's default middleware (`RetryHandler`). See the Q4 assumption in section 9. |
| Exceptions | `exception/EmailValidationException(String code, String message)`, `exception/EmailServiceException(int statusCode, String message[, cause])`, `exception/EmailConfigurationException`, `exception/GlobalExceptionHandler` (`@RestControllerAdvice(basePackages="com.alramz.controllers")`, maps `EmailServiceException` and `EmailConfigurationException` to **503** whatever their status, and `Exception` to 500) | Reuse the first two with added fields (section 6). Delete `EmailConfigurationException` and fold it into `EmailServiceException`. Rewrite the handler. |
| Envelope precedent | `services/wealth-management-service/specs/apiSpecs.yaml` `GenericResponse {responseCode, responseMessage, response:object, correlationId, errorCode?, errorMsg?}`; `wealth-management-service/.../exception/GlobalExceptionHandler.java` (`@Order(0)`, explicit `AccessDeniedException` → 403 handler "otherwise the catch-all ... turns @JwtSecured's 403 into a 500", `buildResponse` fills `responseCode`/`responseMessage` from `HttpStatus`) | Follow the same style, with one programme override: `correlationId` lives **inside `response`**, not at top level. |
| Config | `config/EmailProperties` (`@ConfigurationProperties("app.email")`: `maxSubjectLength`, `maxBodyLength`, `maxAttachments`, `maxAttachmentSizeBytes`); `config/GraphProperties` (`app.graph.*`, secrets from `${AZURE_TENANT_ID}`/`${AZURE_CLIENT_ID}`/`${AZURE_CLIENT_SECRET}`) | Reshape `EmailProperties` (section 5). `GraphProperties` is unchanged. |
| Profiles | `src/main/resources/application.yml`, `application-dev.yml`, `-docker.yml`, `-test.yml`, `-preprod.yml`, `-prod.yml`. All 6 contain `app.email.*` and `company.jwt.permit-all-urls` including `/api/v1/email/send` | Edit all 6 (section 5). |
| JWT | starter `com.alramz.jwt.annotation.JwtSecured(roles)`, `jwt/aspect/JwtSecuredAspect` (throws `AccessDeniedException`), `jwt/context/JwtContext#getCurrentApplication()`, `jwt/config/JwtAutoConfiguration` (`permit-all-urls`, then `anyRequest().authenticated()`) | Secure the endpoint. The invoker name comes from `JwtContext`. |
| Correlation | starter `logging/filter/CorrelationIdFilter` (reads or generates `X-Correlation-ID`, puts it in MDC, echoes the response header; `responseHeader` defaults to true), `logging/util/MDCUtil#getCorrelationId()` | `response.correlationId` = `MDCUtil.getCorrelationId()`, with a fresh UUID if null (in-process calls outside a request). |
| Starter last-resort advice | starter `logging/exception/LoggingExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`, handles `Exception` and `IllegalArgumentException`) | Our handler gets `@Order(0)` (wealth precedent) so it always wins. |
| Masking | starter `audit/SensitiveDataMasker` (key set has no `content` or `body`); `logging/config/LoggingProperties` request `includePayload=false` by default; the notification service yml files do not enable payload or DB logging | No masker change. The gateway's own logs must never include body, attachment content or recipient addresses (section 5). |
| Sizing | `service.yaml`: 0.5 CPU, 1Gi, `-Xmx512m`, 1-5 replicas | A 3 MB attachment cap is about 4.3 MB JSON plus about 3 MB decoded per in-flight request. This is fine for 512m. A sizing review is noted as a question, not a change (section 9). |
| Manual test file | `apiCollection.http` (send examples without `Authorization`, attachment uses `name`) | Update the two send examples: add `Authorization: Bearer {{authToken}}` and use `fileName`/`contentType`. |
| Tests | Only `src/test/java/com/alramz/AppTest.java` (placeholder) | New tests (section 8). |

---

## 3. Interface Contracts

### 3.1 HTTP operation (amends `specs/openapi.yaml`)

| Fact | Value | Resolution source |
|---|---|---|
| Verb + path | `POST /api/v1/email/send` (full path kept, matching this file's precedent) | Q2 = A (same path) |
| operationId / tag | `sendEmail` / `Email` (unchanged) | existing contract |
| Security | JWT-secured. **Remove** from `permit-all-urls` in all 6 yml files. `@JwtSecured(roles = "APP_NOTIFICATION")` on `EmailController#sendEmail` | programme decision 4 + default (accepted) |
| Request | `EmailRequest` (below) | Part 2 §2 + defaults |
| Envelope | `GenericResponse` (programme envelope) | Q2 = A |
| Success status | **200** (Graph's 202 is normalised to 200) | default (accepted) |
| Statuses produced | 200, 400, 401, 403, 413, 429, 500, 502 (no 503, see 3.4) | derived from section 6 |

**Breaking change (accepted, Q2 = A):** the old `EmailSendResponse` fields (`status`, `provider`, `sentTo`, `sentAt`, `traceId`, `correlationID`, `error`) and `ErrorResponse` are removed. The attachment field `name` becomes `fileName`, with `name` still accepted as an alias. The `to` field now accepts a list. All existing HTTP callers must send a JWT with role `APP_NOTIFICATION`.

### 3.2 Request schema `EmailRequest`

| Field | Type | Required | Rules |
|---|---|---|---|
| `to` | string | yes | Comma-separated list. Split on `,`, trim each token, drop empty tokens. At least one token. Each token must match `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` (Part 1 §3.2 regex). **No `format: email`** in the schema; it would generate `@Email` and reject lists. |
| `cc` | string | no | Same list rules. Blank or null means none. |
| `bcc` | string | no | Same list rules. Blank or null means none. |
| `from` | string | yes | A single address, same regex. Must be in `app.email.allowed-senders` (case-insensitive). Used as `/users/{from}/sendMail`. |
| `subject` | string | yes | Not blank. `maxLength: 255`. |
| `body` | string | yes | Not blank. `maxLength: 10000`. If `body.strip().startsWith("<")` the Graph body type is `HTML`, otherwise `TEXT`. |
| `attachments` | `EmailAttachment[]` | no | `maxItems: 10`. Sum of decoded `content` bytes must be ≤ `app.email.max-total-attachment-size-bytes` (3,000,000). |

`EmailAttachment`:

| Field | Type | Required | Aliases (Jackson `@JsonAlias`) | Maps to Graph `fileAttachment` |
|---|---|---|---|---|
| `fileName` | string | yes (not blank) | `filename`, `name` | `name` |
| `contentType` | string (MIME type, e.g. `application/pdf`) | no | `contenttype` | `contentType` (null lets Graph default it) |
| `content` | string, `format: byte` (Base64) | yes | — | `contentBytes` |

Aliases are emitted through the generator's `x-field-extra-annotation` vendor extension on each property, for example `x-field-extra-annotation: '@com.fasterxml.jackson.annotation.JsonAlias({"filename","name"})'`. Jackson merges the field annotation with the generated getter/setter `@JsonProperty`. If 7.23.0 does not emit it, the fallback is a Jackson mix-in registered in config. 02_plan decides.

`content` stays `format: byte`, so Jackson Base64-decodes it into `byte[]` during binding. Invalid Base64 fails binding (MGE-V-005), and decoded sizes come straight from `byte[].length`.

Example:
```json
{
  "to": "jane.doe@example.com,john.smith@example.com",
  "cc": "manager@example.com",
  "bcc": "",
  "from": "notification@alramz.ae",
  "subject": "Q3 Statement Ready",
  "body": "<p>Dear Client,</p><p>Your Q3 account statement is attached.</p>",
  "attachments": [
    { "fileName": "Q3_Statement.pdf", "contentType": "application/pdf", "content": "JVBERi0xLjQKJ..." }
  ]
}
```

### 3.3 Response envelope

```yaml
GenericResponse:
  type: object
  required: [responseCode, responseMessage, response]
  properties:
    responseCode:    { type: string }            # HTTP status as string, e.g. "200"
    responseMessage: { type: string }            # HTTP reason phrase, e.g. "OK"
    errorCode:       { type: string, nullable: true }  # MGE-V-0xx only; null on success and backend errors
    errorMsg:        { type: string, nullable: true }  # caller-fixable text only; null otherwise
    response:        { type: object }            # always present; for this op an EmailSendResult
EmailSendResult:
  type: object
  required: [correlationId]
  properties:
    correlationId: { type: string }              # = X-Correlation-ID response header
```

`correlationId` is a `string`, not a `uuid`, because callers may send non-UUID `X-Correlation-ID` values (the legacy `correlationID` was free text) and the body must equal the header.

### 3.4 One example per status (each goes into `openapi.yaml` as a real `example`)

```jsonc
// 200 OK
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "7f1c2b9e-3d4a-4c1e-9b7a-2f6d8e0a1c55" } }

// 400 Bad Request (one example per code MGE-V-001..006)
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MGE-V-001",
  "errorMsg": "Request failed due to missing: to, subject", "response": { "correlationId": "7f1c..." } }
{ ... "errorCode": "MGE-V-002", "errorMsg": "Invalid email address in: cc", ... }
{ ... "errorCode": "MGE-V-003", "errorMsg": "Field length exceeded: subject", ... }
{ ... "errorCode": "MGE-V-004", "errorMsg": "Number of attachments exceeds 10", ... }
{ ... "errorCode": "MGE-V-005", "errorMsg": "Invalid attachment at position 2: content is not valid Base64", ... }
{ ... "errorCode": "MGE-V-006", "errorMsg": "Malformed JSON request", ... }

// 401 Unauthorized: missing or invalid JWT. Rejected by the starter's Spring Security chain before the
// controller; the body is produced by Spring Security, NOT this envelope. Declare with description only.

// 403 Forbidden, (a) role check failed (@JwtSecured -> AccessDeniedException)
{ "responseCode": "403", "responseMessage": "Forbidden", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "7f1c..." } }
// 403 Forbidden, (b) sender not allowed
{ "responseCode": "403", "responseMessage": "Forbidden", "errorCode": "MGE-V-007",
  "errorMsg": "Sender mailbox is not permitted", "response": { "correlationId": "7f1c..." } }

// 413 Payload Too Large
{ "responseCode": "413", "responseMessage": "Payload Too Large", "errorCode": "MGE-V-008",
  "errorMsg": "Attachments size limit exceeded", "response": { "correlationId": "7f1c..." } }

// 429 Too Many Requests (Graph still throttling after SDK retries; header Retry-After: 30 passed through)
{ "responseCode": "429", "responseMessage": "Too Many Requests", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "7f1c..." } }

// 500 Internal Server Error (MGE-B-004, logged only)
{ "responseCode": "500", "responseMessage": "Internal Server Error", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "7f1c..." } }

// 502 Bad Gateway (MGE-B-001 or MGE-B-003, logged only)
{ "responseCode": "502", "responseMessage": "Bad Gateway", "errorCode": null, "errorMsg": null,
  "response": { "correlationId": "7f1c..." } }
```

There is no 503. A missing Graph client (provider not `MICROSOFT_GRAPH`) is a server misconfiguration and returns 500 (MGE-B-004). Graph 503/504 responses that persist after the SDK's retries return 502 (MGE-B-003).

### 3.5 Error codes

| Code | HTTP | In body? | errorMsg / log text | Trigger |
|---|---|---|---|---|
| MGE-V-001 | 400 | yes | `Request failed due to missing: <fields>` (order `to, from, subject, body`, joined with `, `) | null or blank required field, or `to` has no tokens after splitting |
| MGE-V-002 | 400 | yes | `Invalid email address in: <fields>` (order `to, cc, bcc, from`) | any token fails the regex |
| MGE-V-003 | 400 | yes | `Field length exceeded: <field>` | subject over 255 or body over 10000 |
| MGE-V-004 | 400 | yes | `Number of attachments exceeds 10` | more than 10 attachments |
| MGE-V-005 | 400 | yes | `Invalid attachment at position <n>: <fileName is required \| content is required \| content is not valid Base64>` (1-based) | bad attachment item |
| MGE-V-006 | 400 | yes | `Malformed JSON request` | unparseable or empty body (any other binding error) |
| MGE-V-007 | 403 | yes | `Sender mailbox is not permitted` | `from` not in `app.email.allowed-senders` (also when the list is empty: fail closed) |
| MGE-V-008 | 413 | yes | `Attachments size limit exceeded` | decoded total over the cap, or `Content-Length` over the request cap |
| MGE-B-001 | 502 | no (logged) | `Graph rejected token as unauthorized` | Graph 401 on the first attempt **and** on the single retry |
| MGE-B-002 | 429 | no (logged) | `Graph throttled; Retry-After=<v>` | Graph 429 after the SDK RetryHandler gives up |
| MGE-B-003 | 502 | no (logged, with Graph `error.code`/`message`) | `Graph send failed <status> <code>` | any other Graph non-2xx (400 invalid recipient, 403, 404 mailbox, 5xx) |
| MGE-B-004 | 500 | no (logged with stack) | `Unhandled email gateway error` | `ClientException`, IO or token-endpoint failure, missing Graph client, anything unexpected |

Validation order, so results are deterministic: JSON binding (V-006/V-005), then required (V-001), addresses (V-002), lengths (V-003), attachment count (V-004), attachment items (V-005), total size (V-008), sender allow-list (V-007), then the Graph call.

### 3.6 In-process contract (for features 2 and 4)

```java
// com.alramz.service.EmailService: existing interface, signature changes
void sendEmail(EmailRequest request);   // com.alramz.model.EmailRequest (now generated)
// returns normally  => Graph accepted the message
// throws EmailValidationException  => caller-fixable (getErrorCode() MGE-V-0xx, getStatus() 400/403/413)
// throws EmailServiceException     => backend (getInternalCode() MGE-B-00x, getStatus() 429/502/500, getRetryAfter() nullable)
```

- The **same** validation, allow-list and size checks run for in-process calls. `EmailServiceImpl` calls the `jakarta.validation.Validator` bean on the request itself, because generated `@NotNull`/`@Size` only fire automatically on the HTTP path. Both paths map violations to MGE-V codes through **one** mapper, so a given request gets the same code either way.
- Correlation id: `MDCUtil.getCorrelationId()`, or a new UUID when absent (for example, a future scheduled job).
- Invoker, for logging only: `JwtContext#getCurrentApplication()`, or `"unknown"` when null.
- Feature 2 maps `EmailServiceException` to its EML009 and `EmailValidationException` to its own codes. That is feature 2's concern.
- Feature 2's template `FROM_EMAIL` values must be added to `app.email.allowed-senders`.

### 3.7 Graph dispatch rules (inside `EmailServiceImpl`)

- `Message`:
  - `subject`;
  - `body` = `ItemBody{contentType = HTML|TEXT per 3.2, content}`;
  - `toRecipients`/`ccRecipients`/`bccRecipients` from the split lists, with cc/bcc omitted when empty;
  - `attachments` = `FileAttachment{name=fileName, contentType, contentBytes=content}`.
- `UserSendMailParameterSet.withSaveToSentItems(true)` (currently `false`).
- **401 handling:** on `GraphServiceException` with `getResponseCode()==401`, retry the send **once**. The auth provider calls `credential.getToken` again. A second 401 raises MGE-B-001.
- **429 handling:** no app-level retry. The SDK `RetryHandler` already retried. A final 429 raises MGE-B-002 → HTTP 429, with the `Retry-After` value from the Graph response headers copied to our response when present, and omitted otherwise.
- Other Graph non-2xx raises MGE-B-003. Any other exception raises MGE-B-004.
- Keep the existing `graph.send_mail` tracing span (success/failure tags).

---

## 4. Data Model & Persistence Changes

None. No tables, entities or Liquibase changesets. Part 2 is stateless (the legacy Seq audit is replaced by structured logs, section 5). Programme decision 3 (Liquibase in this service) begins with feature 2.

Model-class changes, all generated from `specs/openapi.yaml` into `com.alramz.model`:
- **added** `GenericResponse` and `EmailSendResult`;
- **now generated** `EmailRequest` and `EmailAttachment`;
- **removed** `EmailSendResponse` and `ErrorResponse`;
- **deleted from `src/main/java`** the hand-copied `model/EmailRequest.java` and `model/EmailAttachment.java`.

---

## 5. Security & Config Impact

**Mandatory security defect fix:** `config/GraphConfig.java` lines 47-50 fetch a token at startup and log the raw access token at INFO. Delete both the fetch and the log. No token value may appear in any log.

**JWT / permit-all:**
- Remove `- /api/v1/email/send` from `company.jwt.permit-all-urls` in all 6 files: `application.yml` line 30, `application-dev.yml` line 66, and `application-docker.yml`, `application-test.yml`, `application-preprod.yml`, `application-prod.yml` line 32.
- `/api/v1/info` and `/actuator/prometheus` (dev) stay public.
- The endpoint is then authenticated by `anyRequest().authenticated()`, plus `@JwtSecured(roles = "APP_NOTIFICATION")`.
- The token-issuing side must grant `APP_NOTIFICATION` to calling applications. That is an operational follow-up, not code here.

**Config (`app.email`, `EmailProperties` record reshaped):**

| Key | Change | Value |
|---|---|---|
| `app.email.service-provider` | unchanged | `MICROSOFT_GRAPH` |
| `app.email.allowed-senders` | **new**, `List<String>`, all 6 files | base/dev/docker/test/preprod: `${APP_EMAIL_ALLOWED_SENDERS:notification@alramz.ae}`; prod: `${APP_EMAIL_ALLOWED_SENDERS:cm@alramz.ae}` (comma-separated env override). Empty list rejects all senders. |
| `app.email.max-total-attachment-size-bytes` | **new**, replaces `max-attachment-size-bytes` (10485760, per-attachment) in all 6 files | `3000000` |
| `app.email.max-attachment-size-bytes` | **deleted** | — |
| `app.email.max-subject-length`, `max-body-length`, `max-attachments` | **deleted** from yml and from the record | These limits now come from the contract only (`maxLength 255`, `maxLength 10000`, `maxItems 10`), enforced by the generated Bean Validation on both paths (3.6). That leaves one source of truth instead of two drifting ones. |

Why 3,000,000 bytes: Graph's single-request `sendMail` limit is 4 MB, and attachments of 3 MB or more need an upload session. 3,000,000 decoded bytes is 4,000,000 Base64 characters, which leaves headroom under 4 MiB for the rest of the message.

**Request-size cap (Q5):**
- A small `OncePerRequestFilter` in `com.alramz.config`, limited to `POST /api/v1/email/send`, rejects `Content-Length > maxTotalAttachmentSizeBytes * 4 / 3 + 262_144` (4,262,144 bytes at the default) with **413** and the MGE-V-008 envelope, written directly because it runs before MVC.
- The limit is derived, not a new key, so the two cannot drift.
- Tomcat's `max-http-form-post-size` does not apply to JSON, which is why a filter is needed.
- Ceiling: chunked requests without `Content-Length` pass the filter and are still capped by the decoded-total check after binding. Mark this in code with a `ponytail:` comment, and bound the input stream if chunked abuse is ever seen.

**Masking / logging:**
- No `SensitiveDataMasker` change. Request payload logging is off by default (`LoggingProperties` `includePayload=false`), and this service does not enable it or DB audit logging.
- The masker's key set does **not** cover `content` or `body` (OTP mails from feature 4 will carry OTPs in `body`). So `company.logging.request.include-payload` must stay off for this service. Enabling it would need a starter masker change (out of scope).
- Gateway log lines include: correlationId, invoker, `from`, recipient **counts**, attachment count and total decoded bytes, body type, outcome, Graph status, Graph `error.code`, and the MGE code.
- Gateway log lines never include: body, subject, attachment content, recipient addresses, or tokens.

**Secrets:** unchanged. `app.graph.client-secret` comes from `${AZURE_CLIENT_SECRET}`. Nothing is copied from README or notes.

---

## 6. Edge Cases & Error Behaviors

Exception model (minimal; reuse and extend existing classes):
- `EmailValidationException`: `(HttpStatus status, String errorCode, String message)`. The handler returns `status` with `errorCode`/`errorMsg` filled in.
- `EmailServiceException`: `(HttpStatus status, String internalCode, String message, Throwable cause, String retryAfter)`. The handler logs `internalCode` and the cause, and returns `status` with `errorCode=null` and `errorMsg=null`. It adds a `Retry-After` header when `retryAfter` is non-null.
- `EmailConfigurationException`: **deleted**. The missing Graph client case becomes `EmailServiceException(500, "MGE-B-004", ...)`.
- `GlobalExceptionHandler`:
  - add `@Order(0)` (wealth precedent);
  - every response uses `GenericResponse` + `EmailSendResult(correlationId)`;
  - explicit `AccessDeniedException` → 403 handler;
  - `MethodArgumentNotValidException` and `ConstraintViolationException` → shared MGE-V mapper;
  - `HttpMessageNotReadableException` → MGE-V-005 if the Jackson path points into `attachments[n].content`, otherwise MGE-V-006 (cause-chain walk as in wealth `handleNotReadable`);
  - `Exception` → 500, logged as MGE-B-004.

| # | Scenario | Exception | HTTP | errorCode |
|---|---|---|---|---|
| E1 | No or invalid JWT | Spring Security (starter) | 401 (starter body) | n/a |
| E2 | JWT without `APP_NOTIFICATION` | `AccessDeniedException` | 403 | null |
| E3 | Body not JSON / empty | `HttpMessageNotReadableException` | 400 | MGE-V-006 |
| E4 | `to`/`from`/`subject`/`body` missing or blank; `to` = `" , "` | `EmailValidationException` (or bean-validation exceptions mapped) | 400 | MGE-V-001 |
| E5 | `to: "a@x.com,bad"`, invalid `cc`/`bcc`/`from` token | `EmailValidationException` | 400 | MGE-V-002 |
| E6 | subject 256 chars / body 10001 chars | bean validation → mapper | 400 | MGE-V-003 |
| E7 | 11 attachments | bean validation → mapper | 400 | MGE-V-004 |
| E8 | attachment without `fileName` or `content`; `content` not Base64 | bean validation / `HttpMessageNotReadableException` | 400 | MGE-V-005 |
| E9 | legacy aliases `filename`/`contenttype`/`name` | none (accepted) | 200 | — |
| E10 | decoded attachments total 3,000,001 bytes | `EmailValidationException` | 413 | MGE-V-008 |
| E11 | `Content-Length` over 4,262,144 | request-size filter | 413 | MGE-V-008 |
| E12 | `from` not in allow-list (case-insensitive), or list empty | `EmailValidationException` | 403 | MGE-V-007 |
| E13 | Graph 401, retry succeeds | none | 200 | — |
| E14 | Graph 401 twice | `EmailServiceException` MGE-B-001 | 502 | null |
| E15 | Graph 429 after SDK retries, with `Retry-After: 30` | `EmailServiceException` MGE-B-002 | 429 + `Retry-After: 30` | null |
| E16 | Graph 429 without `Retry-After` | MGE-B-002 | 429, no header | null |
| E17 | Graph 400 (invalid recipient) / 403 / 404 / 5xx after retries | MGE-B-003 | 502 | null |
| E18 | Graph unreachable, token endpoint failure, `ClientException` | MGE-B-004 | 500 | null |
| E19 | `app.email.service-provider` ≠ `MICROSOFT_GRAPH` (no Graph bean) | MGE-B-004 | 500 | null |
| E20 | Plain-text body `"Hello"` | none | 200; Graph body type TEXT | — |
| E21 | Body `"  <p>Hi</p>"` | none | 200; HTML | — |
| E22 | Any in-process call failing E4-E19 | same exceptions, not mapped to HTTP | — | — |

---

## 7. Open Questions & Decisions (decision log)

All five were raised in the previous BLOCKED run and answered by the user. They are authoritative.

| Q | Question | Options offered | User's choice |
|---|---|---|---|
| Q1 | Is an external legacy adapter path needed for Part 2 (`/invoke/EmailGateway.services.Microsoft.Graph/sendEmail`)? | Ship a legacy adapter per programme decision 2 / no legacy path | **No legacy path.** Part 2 was only ever called in-process. Ship the in-process service plus `/api/v1` only. This **explicitly overrides programme decision 2 for this feature only**. Features 2 and 4 keep their legacy adapters. |
| Q2 | What happens to the existing `POST /api/v1/email/send`? | A: change in place to the programme envelope / B: new path, keep old / C: keep old envelope | **A.** Same path and operationId, programme envelope. Breaking current callers is accepted. |
| Q3 | How should the `from` mailbox be controlled? | A: config allow-list, 403 / B: no control / C: per-caller mapping | **A.** `app.email.allowed-senders` per profile. Other senders get 403 with a caller-fixable code (MGE-V-007). |
| Q4 | What to do about Graph throttling? | A: keep SDK RetryHandler, return 429 + Retry-After / B: app-level backoff / C: flatten to 502 | **A.** Log MGE-B-002. |
| Q5 | What attachment size limits apply? | A: about 3 MB total, count 10, 413, servlet cap / B: upload sessions / C: keep 10 MB per file | **A.** `app.email.max-total-attachment-size-bytes`, 413 MGE-V-008, plus the request-size cap. |

No new blocking gap came up in this run.

---

## 8. Explicit Acceptance Criteria

Contract and codegen:
- [ ] `specs/openapi.yaml` `POST /api/v1/email/send` (`operationId: sendEmail`) uses `EmailRequest` per 3.2, including the `x-field-extra-annotation` aliases or the mix-in fallback, and has no `format: email` on `to`/`from`. It declares responses 200/400/401/403/413/429/500/502, each with the example from 3.4 (401 description only), and the 429 response declares a `Retry-After` header.
- [ ] `EmailSendResponse` and `ErrorResponse` schemas are removed. `GenericResponse` and `EmailSendResult` are added.
- [ ] `pom.xml` `modelsToGenerate` = `GenericResponse,EmailSendResult,EmailRequest,EmailAttachment`.
- [ ] `src/main/java/com/alramz/model/EmailRequest.java` and `EmailAttachment.java` no longer exist.
- [ ] `mvn -pl services/alramz-notification-service -am test-compile` passes.

Security and config:
- [ ] `GraphConfig` contains no `getToken(` call and no log statement containing the token. A grep for `Access Token` in `src/main` finds nothing.
- [ ] `/api/v1/email/send` appears in none of the 6 `application*.yml` files.
- [ ] `EmailController#sendEmail` carries `@JwtSecured(roles = "APP_NOTIFICATION")`. A test without a JWT gets 401/403 and never reaches `EmailService`. A test with a JWT lacking the role gets 403 with `errorCode` null.
- [ ] All 6 files define `app.email.allowed-senders` and `app.email.max-total-attachment-size-bytes: 3000000`, and none define `max-attachment-size-bytes`, `max-subject-length`, `max-body-length` or `max-attachments`.
- [ ] `EmailConfigurationException` is deleted.

Behaviour (unit tests on `EmailServiceImpl` with a mocked `GraphServiceClient`, plus MockMvc/WebMvc tests on controller and handler):
- [ ] Success returns HTTP 200 with body `{responseCode:"200", responseMessage:"OK", errorCode:null, errorMsg:null, response:{correlationId}}`, where `correlationId` equals the request's `X-Correlation-ID` when one is sent.
- [ ] `to:"a@x.com, b@y.com"` with `cc` and `bcc` produces a Graph `Message` with 2 `toRecipients`, 1 `ccRecipients` and 1 `bccRecipients`. Blank `cc` produces no `ccRecipients`.
- [ ] Body `"Hello"` is sent as `BodyType.TEXT`. Body `"  <p>Hi</p>"` is sent as `BodyType.HTML`.
- [ ] `saveToSentItems` is `true` in the Graph call.
- [ ] Each of E3-E8, E10-E12 returns the HTTP status and `errorCode` listed in section 6, with `errorMsg` matching the 3.5 text.
- [ ] Attachment JSON using `filename`/`contenttype` and JSON using `name` both bind and reach Graph as `FileAttachment.name`/`contentType`.
- [ ] Graph 401 then success makes exactly 2 Graph calls and returns 200. Graph 401 twice returns 502 with `errorCode` null and an MGE-B-001 log line.
- [ ] A Graph `GraphServiceException` with code 429 and Retry-After `30` returns 429 with response header `Retry-After: 30` and `errorCode` null. The same without the header returns 429 with no `Retry-After` header.
- [ ] Graph 400/500 (`GraphServiceException`) returns 502. `ClientException` or `RuntimeException` returns 500. Both have `errorCode` null.
- [ ] An in-process call `emailService.sendEmail(request)` with a 256-character subject throws `EmailValidationException` with code `MGE-V-003` (proves bean validation runs outside HTTP). A disallowed `from` throws with `MGE-V-007` / 403.
- [ ] No gateway log line contains the body, subject, attachment content or a recipient address (asserted in at least one test with a log capture, or by code review in 04_security).
- [ ] `apiCollection.http` send examples carry `Authorization: Bearer {{authToken}}` and use `fileName`/`contentType`.
- [ ] `mvn -pl services/alramz-notification-service -am test` passes.

---

## 9. Self-Critique

**Confidence: medium-high.**

Every section cites verified files, and all five former blocking gaps are resolved by the user. Confidence is not "high" because three SDK and generator behaviours could not be verified offline: the jar contents could not be read with the tools available in this stage.

**Residual non-blocking assumptions:**
1. **Q4 SDK retry defaults (02_plan must confirm against `microsoft-graph-core-2.0.4-sources.jar` in `~/.m2`).**
   - Assumed: `GraphServiceClient.builder()` installs `RetryHandler` by default with `RetryOptions` defaults of max 3 retries, 3 s base delay, 180 s max delay, retrying 429/503/504 and honouring `Retry-After`.
   - Assumed: after exhaustion the SDK throws `GraphServiceException` with `getResponseCode()==429`, and response headers (including `Retry-After`) are retrievable from it.
   - If headers are not exposed, the 429 is still returned without the header (E16 behaviour).
   - Consequence: a throttled request can hold a Tomcat thread for tens of seconds.
2. **401 "fresh token"**: `ClientSecretCredential` (azure-identity 1.15.0) may return its cached token on the retry. The single retry then helps only with transient 401s. A genuinely bad credential still ends in MGE-B-001, which is acceptable.
3. **MGE-B-001 maps to 502, not 401.** Part 2 §6 allows "401 or 502". 401 would falsely tell the caller its own JWT is bad.
4. **Graph 400 (for example an invalid recipient the regex accepted) is a backend 502** per Part 2 §6, not a caller-fixable 400.
5. `x-field-extra-annotation` is emitted by openapi-generator 7.23.0 for Spring models, with a mix-in fallback otherwise. The `name` alias is kept to soften the break for current callers. This is cheap and not requested.
6. Allowed-sender defaults (`notification@alramz.ae` non-prod, `cm@alramz.ae` prod) are taken from `apiCollection.http` and Part 4 §3. They are env-overridable. Feature 2 must add template `FROM_EMAIL` values.
7. Unauthenticated requests get the starter's Spring Security response (401 or 403, not this envelope). The starter has no custom entry point, and changing that is out of scope.
8. 403 on a role failure carries `errorCode` and `errorMsg` both null. This follows the programme rule; wealth puts `"Access denied"` in `errorMsg`.
9. All previous-run defaults are accepted as stated: no dispatcher, JWT + `APP_NOTIFICATION`, wealth-style envelope, `MGE-V-0xx` prefix, B-codes logged only, 200 on success, comma lists, attachment aliases, `<` means HTML, `saveToSentItems=true`, one 401 retry.
10. Sizing (question only, no change): `service.yaml` at `-Xmx512m` should be fine at about 7 MB peak per request. Revisit if features 2 and 4 drive high concurrent attachment volume.

**Weakest part:** the 429 path (E15/E16). It depends on unverified `microsoft-graph-core` 2.0.4 internals: default retry options, and whether `Retry-After` is reachable from `GraphServiceException` after the RetryHandler gives up. 02_plan should open the sources jar first and adjust E15's test if headers are not exposed. The request-size filter's chunked-transfer gap is the second-weakest point. It is a known ceiling, marked rather than solved.
