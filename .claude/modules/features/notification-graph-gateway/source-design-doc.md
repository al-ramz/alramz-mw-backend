# Feature request (verbatim from user)

> notfication-service-endpoints features as below. i want you to implement this make sure that we are using Microsoft Graph Client. also, make sure you use best practices to design these endpoints. if you are not sure anywhere please do ask questions before you assume anything.
>
> i want to implement all these things in alramz-notification-service

## Programme-level decisions (answered by user, apply to ALL four notification features)

1. **Split** — 4 features, run in dependency order, each through both SDLC gates:
   1. `notification-graph-gateway` — Part 2 (harden/extend the existing MS Graph `EmailService`).
   2. `notification-templated-email` — Part 1 (templated send, EMAIL_CONFIGURATIONS / EMAIL_LOG).
   3. `notification-sms-gateway` — Part 3 (dispatch job, Etisalat/Waselney router, getReport).
   4. `notification-otp` — Part 4 (sendOTP / validateOTP / getMessagesLookup; depends on 2 + 3).
   This file is the single shared source doc; features 2–4 reference it from here.
2. **Response contract — BOTH**: new HTTP-status-semantics endpoints under `/api/v1/...` using the
   Part 1 envelope (`responseCode` mirrors HTTP status, `errorCode`/`errorMsg` only for caller-fixable
   errors, `response` wrapper, `X-Correlation-ID` header, legacy field-name aliases accepted) **plus**
   thin legacy-path endpoints (e.g. `/sendEmail`, `/sendOTP`) that return the legacy contract
   (always HTTP 200, legacy numeric `responseCode` values / field names) for phased consumer cutover.
   Both must share the same service layer — legacy endpoints are adapters only.
3. **Database — service owns schema via Liquibase**: alramz-notification-service adds Liquibase
   changesets for the tables it needs (fix the spec's known undersized columns in the DDL rather than
   reproducing them). Note CLAUDE.md currently says data-validation-service is the only Liquibase
   module — this feature set changes that.
4. **Caller identity — repo JWT**: use the alramz-api-starter JWT; invoker/application name comes
   from a token claim; every caller must be authenticated (remove `/api/v1/email/send` from
   permit-all). The `x-Gateway-APIKey` mechanism in the spec is NOT used.
5. **Microsoft Graph**: email sending must use the Microsoft Graph Java SDK client
   (`com.microsoft.graph:microsoft-graph`, already wired in `config/GraphConfig.java`) — not raw HTTP,
   not JavaMailSender (overrides Part 1 §3.7's JavaMailSender suggestion).

---

# Source specification (verbatim from user)

**Al Ramz Capital Middleware Migration Programme** — Notification Services — Consolidated API Specification

Email Notification · Email Gateway (Microsoft Graph) · SMS Gateway · OTP Services
Software AG webMethods → Spring Boot 3.x / Java 21 Migration

| Part | Document | Legacy package | Services covered |
|---|---|---|---|
| 1 | Email Notification Service — API Specification | Notifications | Notifications.EMail.services:sendEmail / POST /sendEmail |
| 2 | Microsoft Graph Send Email Service — API Specification | EmailGateway | EmailGateway.services.Microsoft.Graph:sendEmail |
| 3 | SMS Gateway — SMS Dispatch Service & API Specification | SMSGateway | SMS dispatch job, sendSMS router (Etisalat, Waselney), getReport |
| 4 | OTP Services — API Specification | OTP | POST /sendOTP, POST /validateOTP, GET /getMessagesLookup |

The Email Notification Service (Part 1) and OTP Services (Part 4) are business-facing services. They send through the shared gateways: e-mail through the EmailGateway package (Part 2) and SMS through the SMSGateway package (Part 3).

---

# PART 1 — Email Notification Service — API Specification (`POST /sendEmail`)

| Attribute | Detail |
|---|---|
| Source package | `Notifications` (manifest v1.0, last patch `Email_Changes_29012026` — 2026-01-29) |
| Source service | `Notifications.EMail.services:sendEmail` (Flow, stateless) |
| REST resource | `Notifications.RestAPIs:Notifications` → `POST /sendEmail` |
| Consumers | Common service — invoked by other IS packages and via the API Gateway |
| Database | Oracle schema `MIDDLEWARE` via JDBC connection `MiddlewareConnection:Middleware` |

## 1. Overview

### 1.1 Purpose

`POST /sendEmail` sends a templated email on behalf of a calling application. The caller supplies recipients, a template name, a language (EN/AR), positional placeholder values and optional Base64 attachments. The service loads the template from the `EMAIL_CONFIGURATIONS` table, validates the request against the template rules (placeholder count, attachment count / type / size, daily send limit), renders the subject and body, dispatches the email through the shared SMTP gateway and writes an audit row to `EMAIL_LOG`.

It is a **common service**: other webMethods packages call it directly, and external channels call it through the API Gateway. The Spring Boot implementation must therefore keep the request contract stable for existing consumers (see §6).

Target conventions:
- All fields use camelCase. Legacy field names that differ (`contenttype`, `filename`, `correlationID`) must be accepted as aliases (e.g. Jackson `@JsonAlias`) during the transition.
- `responseCode` / `responseMessage` mirror the HTTP status. `errorCode` / `errorMsg` are populated only for caller-fixable errors (EML001–EML008) and left `null` for backend failures (EML009–EML010).
- Each error code carries the **legacy `responseCode`** (1043, 1048 … 1058, 500) in §5 so consumer mappings can be traced.

### 1.2 Source Components in Scope

| webMethods component | Type | Role |
|---|---|---|
| `Notifications.EMail.services:sendEmail` | Flow service | Main orchestration |
| `Notifications.EMail.adapters:emailTemplateExists` | JDBC Custom SQL | Checks that the template name exists |
| `Notifications.EMail.adapters:getEmailConfigurations` | JDBC Select | Loads template configuration |
| `Notifications.EMail.adapters:validateAttachmentType` | JDBC Custom SQL | Checks an attachment type against the template |
| `Notifications.EMail.adapters:getUsedAttempts` | JDBC Custom SQL | Today's send count per template + recipient + invoker |
| `Notifications.EMail.adapters:getUsedAttemptsForBulk` | JDBC Custom SQL | Today's send count per template + invoker (bulk templates) |
| `Notifications.EMail.adapters:insertEmailLogs` | JDBC Insert | Writes the audit row to `EMAIL_LOG` |
| `Notifications.RestAPIs:Notifications` | REST resource | Exposes `POST /sendEmail` (and `POST /sendSMS`) |

External dependencies: `EmailGateway.services:sendEmail`, `commonUtility.APIgateway:getApplicationName`, `commonUtility.java:GenerateGUID`, `commonUtility.java:getAttachmentSize`, `commonUtility.string.stringHashing:stringHashing`, `SEQDatalust.services:asynchronousIngestion`. The `Notifications.SMS` folder (`sendSMS`) and the empty `Notifications.MessageQueue` folder are out of scope.

## 2. API Contract

### 2.1 Endpoint

| Attribute | Detail |
|---|---|
| HTTP Method | `POST` |
| Path | `/sendEmail` |
| Content Type | `application/json` (UTF-8 — Arabic content must round-trip) |
| Authentication | Not enforced by the Flow. Caller identity from `x-Gateway-APIKey` header (SUPERSEDED: use JWT per decision 4). |
| Legacy HTTP status | Always `200`; outcome carried only in the body `responseCode` |

### 2.2 Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `to` | string | Yes | One or more recipient addresses. Separators `,` `;` `/` `\` or whitespace are accepted and normalised to `,` (see §3.3). |
| `cc` | string | No | Optional CC list, same format and normalisation as `to`. |
| `bcc` | string | No | Optional BCC list, same format and normalisation as `to`. |
| `templateName` | string | Yes | Must match `EMAIL_CONFIGURATIONS.TEMPLATE_NAME` exactly (case-sensitive). |
| `language` | string | Yes | `EN` or `AR` (upper case, exact). Selects `EN_*` or `AR_*` subject/body. |
| `placeholders` | string[] | Conditional | Positional values. Element 1 replaces `%1%`, element 2 replaces `%2%`, … in the body. When sent, the count must equal `NUMBER_OF_PLACEHOLDERS`. |
| `attachments` | Document[] | No | Optional list of attachments. |
| ↳ `content` | string | Yes | File content, Base64 encoded. |
| ↳ `contentType` | string | Yes | Attachment type **code**, not a MIME type: `JPEG`, `JPG`, `PNG`, `PDF`, `MSWORD`, `MSEXCEL`, `CSV`, `TRN`, `TEXT`. Legacy name: `contenttype`. |
| ↳ `fileName` | string | Yes | File name including extension. Legacy name: `filename`. |
| `correlationId` | string | No | Caller trace id. A new GUID is generated when blank. Legacy name: `correlationID`. |

Sample request:
```json
{
  "to": "customer@example.com",
  "cc": "relationship.manager@example.com",
  "bcc": null,
  "templateName": "ACCOUNT_OPENING_CONFIRMATION",
  "language": "EN",
  "placeholders": ["Ahmed Ali", "AR-100245"],
  "attachments": [
    { "content": "JVBERi0xLjQKJcfsj6IKNSAwIG9iago8PC9MZW5ndGggNiAwIFI...", "contentType": "PDF", "fileName": "Welcome_Pack.pdf" }
  ],
  "correlationId": "7f1c2b9e-3d4a-4c1e-9b7a-2f6d8e0a1c55"
}
```

### 2.3 Response Body

| Field | Type | Notes |
|---|---|---|
| `responseCode` | string | Mirrors the HTTP status as a string (`"200"`, `"400"`, `"413"`, `"429"`, `"502"`, `"500"`). |
| `responseMessage` | string | Mirrors the HTTP reason phrase (`"OK"`, `"Bad Request"`, …). |
| `errorCode` | string \| null | EML001–EML008 for caller-fixable errors; `null` on success and backend errors. |
| `errorMsg` | string \| null | Business description (legacy message text, see §5); `null` on success and backend errors. |
| `response` | Document | Wrapper for the domain payload; always present. |
| ↳ `correlationId` | string | Echo of the request `correlationId`, or the generated GUID. Also returned in the `X-Correlation-ID` header. |

Legacy output was `responseCode`, `responseMessage`, `correlationID` and `lastError` (a `pub.event:exceptionInfo` document returned on internal errors). `lastError` must **not** be returned by the target; log it instead.

Samples:
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "response": { "correlationId": "7f1c2b9e-..." } }
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "EML001", "errorMsg": "Request failed due to missing: to, language", "response": { "correlationId": "7f1c2b9e-..." } }
{ "responseCode": "502", "responseMessage": "Bad Gateway", "errorCode": null, "errorMsg": null, "response": { "correlationId": "7f1c2b9e-..." } }
```

### 2.4 HTTP Status Codes

| HTTP Status | Reason Phrase | Meaning |
|---|---|---|
| 200 | OK | Email accepted by the gateway and audit row written. |
| 400 | Bad Request | Request failed a field or template rule (EML001–EML006). |
| 413 | Payload Too Large | Total decoded attachment size exceeds the template limit (EML007). |
| 429 | Too Many Requests | Daily send limit for the template reached (EML008). |
| 502 | Bad Gateway | Gateway did not confirm delivery (EML009). |
| 500 | Internal Server Error | Unhandled exception, including database failures (EML010). |

## 3. Business & Validation Logic

### 3.1 Processing Flow

The legacy Flow runs as Try / Catch / Finally. A validation failure sets the legacy code and exits the main sequence successfully, so the Finally block always runs.

1. Initialise. Capture `requestTimestamp` (`yyyy-MM-dd HH:mm:ss.SSS`, timezone `GMT+4`). If `correlationId` is blank, generate a GUID.
2. Resolve caller (application name = "invoker") and remote IP.
3. Build log copy of request: serialise `to`, `cc`, `bcc`, `templateName`, `language`, `placeholders` and each attachment's `contentType` + `fileName` to JSON (`requestBody`). Attachment `content` excluded. On serialisation failure use `{}`.
4. Validate the request in the order given in §3.2. Field errors are collected; template-rule errors stop processing immediately.
5. Check the daily send limit (§3.6).
6. Render the email. Pick `EN_SUBJECT`/`EN_BODY` or `AR_SUBJECT`/`AR_BODY` by `language`, then replace placeholders in the body (§3.5).
7. Map attachment type codes to MIME types (§3.4).
8. Send through the gateway with `from = FROM_EMAIL` (§3.7). Anything other than success → EML009.
9. Audit. Hash the rendered body with SHA-256, serialise attachment metadata to JSON and insert a row into `EMAIL_LOG` (§3.8). Capture `responseTimestamp`.
10. Respond `200 OK`.
11. Catch: any exception → EML010 (legacy `500`), capture the exception.
12. Finally: ship the request/response event to the central log platform (Seq) asynchronously, then return only the response fields.

### 3.2 Validation Rules (legacy execution order)

"Collect" = field name added to a missing-fields list reported together as EML001 at step 8; "Stop" = respond immediately.

| # | Check | Rule | On failure |
|---|---|---|---|
| 1 | `to` | Present and non-blank; after normalisation every address matches `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` | Collect `to` |
| 2 | `cc` (if sent) | Same format rule | Collect `cc` |
| 3 | `bcc` (if sent) | Same format rule | Collect `bcc` |
| 4 | `templateName` | Present → must exist in `EMAIL_CONFIGURATIONS`; then load config. Absent → collect. | Not found: Stop, EML002; Absent: Collect `templateName` |
| 5 | `language` | Exactly `EN` or `AR` | Collect `language` |
| 6 | `placeholders` (if sent) | Count = `NUMBER_OF_PLACEHOLDERS` | Stop, EML003 |
| 7a | `attachments` count (if sent) | Count ≤ `NUMBER_OF_ATTACHMENTS` | Stop, EML004 |
| 7b | Each attachment type | `contentType` code allowed by `TYPE_OF_ATTACHMENTS` | Stop, EML005 |
| 7c | Each attachment file name | `fileName` non-blank | Stop, EML006 |
| 7d | Total attachment size | Sum of Base64-decoded byte lengths ≤ `ATTACHMENTS_MAX_SIZE` | Stop, EML007 |
| 8 | Collected fields | Missing-fields list empty | Stop, EML001 "Request failed due to missing: <fields>" (comma + space separated) |
| 9 | Daily limit | Used attempts today < `MAX_ATTEMPTS` | Stop, EML008 |

Recommended target order: run field checks (1–5) first and return EML001, then the template checks. (Legacy: missing `templateName` + placeholders returns EML003 against a null config.)

### 3.3 Recipient Normalisation

Legacy: trim; replace spaces with `,`; regex-replace `[;,/\s\\]` with `,` (can leave empty entries). Target: split on `[;,/\s\\]+`, drop empty tokens, validate each address individually, join with `,`.

### 3.4 Attachment Type Handling

Validation: legacy is case-sensitive `TYPE_OF_ATTACHMENTS LIKE '%<contentType>%'`. Target: split `TYPE_OF_ATTACHMENTS` on commas, exact code match.

MIME mapping:

| Type code | Condition | MIME type |
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
| Other | — | Passed through unchanged (legacy) |

Legacy extension matching is case-sensitive. Recommend case-insensitive matching and rejecting `MSWORD`/`MSEXCEL` with other extensions.

Audit metadata per attachment `{ "ct": <code>, "fn": <fileName>, "s": <decoded bytes> }` as `{"attachmentMetaData":[…]}`; `{}` when none.

### 3.5 Placeholder Substitution

Tokens `%1%`…`%n%` (1-based), every occurrence replaced literally; body only, subject as stored. If `placeholders` omitted, legacy skips the count check and leaves tokens. Recommend: reject when `NUMBER_OF_PLACEHOLDERS > 0` and none sent.

### 3.6 Daily Send Limit

Counts `EMAIL_LOG` rows for the current DB day vs `MAX_ATTEMPTS`. `IS_BULK=1`: template + invoker. `IS_BULK=0`: template + normalised `to` + invoker. Allowed when `usedAttempts < MAX_ATTEMPTS`, else EML008. Only successful sends are logged.

### 3.7 Email Dispatch

Gateway inputs: `from`=FROM_EMAIL, normalised to/cc/bcc, subject, rendered body, attachments (Base64 content, MIME type, filename). (Per decision 5: send via the MS Graph SDK gateway from feature 1.)

### 3.8 Audit Logging

Insert into `EMAIL_LOG`: TEMPLATE_ID, TO/CC/BCC_EMAIL (normalised), EMAIL_BODY (SHA-256 hex of rendered body), LANGUAGE, DATE_TIME, INVOKER, IP_ADDRESS, ATTACHMENTS_META_DATA; ID by DB sequence. Plus one structured log event per request (requestBody, timestamps, responseCode/Message, correlationId). Never log attachment content.

## 4. External Dependency & Configuration

### 4.1 Configuration

`spring.datasource.*` (MIDDLEWARE), email gateway settings, `notifications.email.timezone` (`Asia/Dubai`), caller-identity source, logging endpoint.

### 4.2 Tables (MIDDLEWARE) — legacy DDL

EMAIL_CONFIGURATIONS: `ID` VARCHAR2(20) NOT NULL, `TEMPLATE_NAME` VARCHAR2(100), `NUMBER_OF_PLACEHOLDERS` NUMBER, `MAX_ATTEMPTS` NUMBER, `FROM_EMAIL` VARCHAR2(50), `HAS_ATTACHMENTS` NUMBER (unused), `AR_SUBJECT`/`EN_SUBJECT` VARCHAR2(200), `AR_BODY`/`EN_BODY` CLOB, `ATTACHMENTS_MAX_SIZE` VARCHAR2(20) (bytes as text), `NUMBER_OF_ATTACHMENTS` NUMBER, `TYPE_OF_ATTACHMENTS` VARCHAR2(50), `IS_BULK` NUMBER, `DEFAULT_RECIPIENT` VARCHAR2(200) (unused).

EMAIL_LOG: `ID` VARCHAR2(20), `TEMPLATE_ID` VARCHAR2(20), `TO_EMAIL`/`CC_EMAIL`/`BCC_EMAIL` VARCHAR2(50) (overflow risk), `EMAIL_BODY` VARCHAR2(1000), `LANGUAGE` VARCHAR2(2), `DATE_TIME` TIMESTAMP(6), `INVOKER` VARCHAR2(20), `IP_ADDRESS` VARCHAR2(20) (too short for IPv6), `ATTACHMENTS_META_DATA` VARCHAR2(20) (too short).

### 4.3 SQL

```sql
-- getEmailConfigurations
SELECT ID, NUMBER_OF_PLACEHOLDERS, MAX_ATTEMPTS, FROM_EMAIL, HAS_ATTACHMENTS, AR_SUBJECT, EN_SUBJECT, AR_BODY, EN_BODY,
       ATTACHMENTS_MAX_SIZE, NUMBER_OF_ATTACHMENTS, TYPE_OF_ATTACHMENTS, IS_BULK, DEFAULT_RECIPIENT
FROM MIDDLEWARE.EMAIL_CONFIGURATIONS WHERE TEMPLATE_NAME = :templateName
-- getUsedAttempts (IS_BULK = 0)
SELECT COUNT(*) FROM EMAIL_LOG WHERE TEMPLATE_ID = :templateId AND TO_EMAIL = :to AND INVOKER = :invoker AND TRUNC(DATE_TIME) = TRUNC(SYSDATE)
-- getUsedAttemptsForBulk (IS_BULK = 1)
SELECT COUNT(*) FROM EMAIL_LOG WHERE TEMPLATE_ID = :templateId AND INVOKER = :invoker AND TRUNC(DATE_TIME) = TRUNC(SYSDATE)
-- insertEmailLogs
INSERT INTO MIDDLEWARE.EMAIL_LOG (TEMPLATE_ID, TO_EMAIL, CC_EMAIL, BCC_EMAIL, EMAIL_BODY, LANGUAGE, DATE_TIME, INVOKER, IP_ADDRESS, ATTACHMENTS_META_DATA) VALUES (?,?,?,?,?,?,?,?,?,?)
```
`emailTemplateExists` / `validateAttachmentType` fold into the config lookup (empty → EML002; type check in Java).

## 5. Error Codes

| errorCode | Legacy responseCode | HTTP | errorMsg |
|---|---|---|---|
| EML001 | 1043 | 400 | Request failed due to missing: <fields> |
| EML002 | 1048 | 400 | Invalid template name |
| EML003 | 1049 | 400 | Invalid number of placeholders |
| EML004 | 1052 | 400 | Number of attachments does not match template configurations |
| EML005 | 1053 | 400 | Type of attachments does not match template configurations |
| EML006 | 1054 | 400 | Please provide the fileName for all attachments |
| EML007 | 1055 | 413 | Attachments size limit exceeded |
| EML008 | 1056 | 429 | Maximum number of attempts exceeded |
| EML009 (internal) | 1058 | 502 | (logged) Email service failed |
| EML010 (internal) | 500 | 500 | (logged) Unhandled exception |

`<fields>` order: `to`, `cc`, `bcc`, `templateName`, `language`. Legacy EML003 text leading space dropped. EML009/010 never appear in `errorCode`.

## 6. Implementation Notes

- Consumer compatibility: legacy callers read numeric codes with HTTP 200 → served by the legacy adapter endpoint (decision 2).
- Internal callers bypassed the daily limit (null invoker) → every caller must be authenticated (decision 4).
- Audit insert can fail after the email is sent (undersized columns) → widen columns in our DDL; audit write must not change the response once mail is sent.
- Validation-order quirk, placeholders not enforced when omitted, loose cc/bcc regexes, loose attachment type check — fix per recommendations above.
- `IS_BULK` null/other → treat as 0; add NOT NULL.
- Day boundary: one explicit zone (Asia/Dubai) or DB day.
- `HAS_ATTACHMENTS`, `DEFAULT_RECIPIENT` unused — no behaviour.
- `lastError` never returned; logged with correlationId via `@RestControllerAdvice`.
- Always set responseTimestamp. PII masking in logs. `ATTACHMENTS_MAX_SIZE` parse safely. Cap HTTP request size (Base64 +33%).

---

# PART 2 — Microsoft Graph Send Email Service (`EmailGateway.services.Microsoft.Graph:sendEmail`)

## 1. Overview

Sends an email (optional cc/bcc and file attachments) on behalf of a mailbox via Microsoft Graph sendMail. Resolves an OAuth2 client-credentials token (cached or fresh), assembles a Graph message from flat request fields, posts to `/users/{from}/sendMail`.

Legacy endpoint: `POST /invoke/EmailGateway.services.Microsoft.Graph/sendEmail` (assumed — no REST descriptor found).

Legacy response envelope (no nested `response`, no errorCode split):
```json
{ "status": "string", "correlationID": "string", "responseCode": "string", "responseMessage": "string" }
```

## 2. API Contract

Request:

| Field | Type | Notes |
|---|---|---|
| `to` | string | Comma-separated recipients. |
| `cc` | string | Comma-separated, optional. |
| `bcc` | string | Comma-separated, optional. |
| `subject` | string | Subject. |
| `from` | string | Sending mailbox (UPN/email); substituted into `/users/{from}/sendMail`. |
| `body` | string | If trimmed value starts with `<` → Graph contentType `HTML`, else `TEXT`. |
| `attachments` | record[] | Optional. |
| ↳ `contenttype` | string | MIME type → Graph `contentType`. |
| ↳ `content` | string | Base64 bytes → Graph `contentBytes` (must already be base64). |
| ↳ `filename` | string | → Graph `name`. |

```json
{
  "to": "jane.doe@example.com,john.smith@example.com", "cc": "manager@example.com", "bcc": "",
  "subject": "Q3 Statement Ready", "from": "notifications@alramz.ae",
  "body": "<p>Dear Client,</p><p>Your Q3 account statement is attached.</p>",
  "attachments": [ { "filename": "Q3_Statement.pdf", "contenttype": "application/pdf", "content": "JVBERi0xLjQKJ..." } ]
}
```

Response:

| Field | Notes |
|---|---|
| `status` | "Mail was sent successfully." or "Mail send failed. {code} - {message}" |
| `correlationID` | Caller-supplied or generated; always present. |
| `responseCode` | Success: Graph status passed through (e.g. "202"). Any failure: legacy hard-codes "500". |
| `responseMessage` | Success: Graph reason phrase. Failure: "Internal Server Error. Please Try Again Later." |

Status mapping (target design): 200/202 success; 400 local validation failure (target, not legacy); 401/502 Graph auth failure (target); 429 Graph rate-limit with Retry-After pass-through (target); 500 legacy for everything / target for true internal errors.

## 3. Processing (legacy)

1. requestTimestamp (GMT+4); correlationID generated if absent.
2. Look up `MICROSOFT_GRAPH_SEND_MAIL_BASE_URL`.
3. Cached access token or fetch via getAuthenticationToken.
4. Headers: JSON + Bearer.
5. Build Graph message: subject; `saveToSentItems=true`; body contentType HTML if trimmed body starts with `<` else TEXT.
6. Tokenize `to`/`cc`/`bcc` on commas into toRecipients/ccRecipients/bccRecipients (skip if blank).
7. Attachments → `#microsoft.graph.fileAttachment` (filename→name, contenttype→contentType, content→contentBytes).
8–10. Serialise, substitute `{from}`, POST.
11. On "Authorization Required"/"Unauthorized": evict cached token, fetch new one — but then fails anyway (no retry of the send).
12–13. 2xx → success. 429 → sleep Retry-After seconds (or 5s) then still fail (no retry). Other → fail.
14–16. Audit log (Seq); catch-all overwrites responseCode='500'.

Validation: legacy performs none; Graph errors are all flattened to 500.

## 4. Auth Token Service (getAuthenticationToken)

Client-credentials grant to `https://login.microsoftonline.com/{tenantID}/oauth2/v2.0/token`, scope `https://graph.microsoft.com/.default`, client id/secret; token cached with no TTL (reactive 401 eviction only). Java notes: prefer MSAL4J / Azure Identity which caches and refreshes by expiry; if hand-rolled, track `expires_in`.

## 5. Configuration

`MICROSOFT_GRAPH_SEND_MAIL_BASE_URL` (contains `{from}`), `MICROSOFT_GRAPH_TOKEN_BASE_URL` (contains `{tenantID}`), `MICROSOFT_GRAPH_TOKEN_TENANT_ID`, `MICROSOFT_GRAPH_TOKEN_CLIENT_ID`; secret `MICROSOFT_GRAPH_TOKEN_CLIENT_SECRET` (Vault/Key Vault, never logged).

## 6. Error Codes

| Internal code | Legacy | Target | Cause |
|---|---|---|---|
| MGE-B-001 | 500 | 401 or 502 | Graph rejected (refreshed) token as unauthorized |
| MGE-B-002 | 500 | 429 | Graph 429; legacy waits Retry-After then fails without retry |
| MGE-B-003 | 500 | 502 | Any other Graph non-2xx (400 invalid recipient, 403, 5xx) |
| MGE-B-004 | 500 | 500 | Unhandled exception (token endpoint unreachable, parse failure…) |

## 7. Implementation Notes

- MAJOR — legacy collapses every failure into 500; target differentiates (confirm with consumers — legacy adapter keeps 500 per decision 2).
- MAJOR — token refresh on 401 does not retry the send; recommend retry once with refreshed token.
- MAJOR — 429 branch waits then fails; recommend genuine retry-after-backoff.
- No REST descriptor — confirm external path.
- `{from}` per-user send pattern must be preserved.
- `attachments[].content` must already be base64 — document/validate it.
- Outer dispatcher `EmailGateway.services:sendEmail` routes by `PREFERRED_EMAIL_SERVICE_PROVIDER` (MICROSOFT_GRAPH) and normalises success to '200'/'OK'. Replicate as a thin routing layer only if multiple providers are needed.
- Token cache: track expiry / proactive refresh.
- Add Bean Validation on `to`/`subject`/`from` → fast 400.

Open items: external path/method; dispatcher needed?; preserve always-500 vs differentiated; fix no-retry gaps?; Graph max request/attachment size for the tenant.

---

# PART 3 — SMS Gateway — SMS Dispatch Service & API Specification

Legacy package `SMSGateway` v1.0. Legacy entry: JDBC Adapter Insert Notification `listenToBulkSMS` on `INSIGHT.SMS_TABLE` → JMS queue `BulkSMS` → trigger `listenToFitSmsMsg` → `sendFitSmsMsg`. Services: `sendFitSmsMsg`, `sendSMS` (router), `Etisalat:sendSMS`, `Waselney:sendSMS`, `utils:getAuthenticationToken`, `getReport`, `java:EncodeMessage`, adapter `updateSMSTable`. Target: `@Scheduled` polling job instead of adapter notification + JMS. External: Oracle `INSIGHT.SMS_TABLE`, Etisalat SMS API, Waselney SMS API, VeriPhone (number lookup), Seq.

## 1. Overview

Other systems queue SMS by inserting rows into `INSIGHT.SMS_TABLE`. For each new row: validate the mobile number, route to Etisalat or Waselney, write outcome back (`STATUS`, `MESSAGE_ID`, `SEND_DATE`, `SEND_ATTEMPTS`). Target replaces event pickup with a scheduled polling job.

```text
TARGET: @Scheduled SmsDispatchJob ──► SELECT pending rows FROM INSIGHT.SMS_TABLE (claim, FOR UPDATE SKIP LOCKED)
   └─ for each row: validate ─► SmsRouter.send() ─► Etisalat / Waselney client ─► UPDATE SMS_TABLE
```

Internal outputs use legacy names `responseCode`, `responseMessage`, `messageID`, `correlationID`, `serviceProvider`. Timestamps GMT+4. `sendSMS` and `getReport` are not REST in legacy; decide whether to expose.

## 2. INSIGHT.SMS_TABLE

| Column | Type | Used | Notes |
|---|---|---|---|
| SERIAL_NO | NUMBER(18) NOT NULL | Key | |
| MESSAGE_ID | VARCHAR2(65) | Write | pipe-delimited result |
| SENDER_NAME | VARCHAR2(50) | No | sender hard-coded `AL RAMZ` |
| SEND_DATE | DATE | Write | processing time GMT+4 |
| CREATION_DATE | DATE | No | recommended for ordering |
| MESSAGE_BODY | NVARCHAR2(1500) | Read | |
| MOBILE_NO | NUMBER(21) | Read | international, no `+` |
| STATUS | NUMBER(10) | R/W | notification only when `STATUS <> 88`; set `88` sent / `1` failed |
| CLIENT_ID | NUMBER(13) | No | |
| SHOW_IN_LOG | VARCHAR2(1) | No | |
| MESSAGE_LANG | VARCHAR2(10) | No | |
| VENDOR_STATUS | VARCHAR2(5) | No | |
| MARKET_ORDER_NO | VARCHAR2(40) | No | |
| ORDER_ID | NUMBER(10) | No | |
| IS_TRIM | VARCHAR2(1) | No | |
| TRIM_TEXT | VARCHAR2(500) | No | |
| SOURCETYPE | NUMBER(1) | No | |
| PRIORITY | NUMBER(1) | No | recommended for ordering |
| SEND_ATTEMPTS | NUMBER | R/W | +1 per attempt |

Legacy: each inserted row delivered exactly once; failed (STATUS 1) or skipped (invalid mobile) rows never picked up again — no retry.

Target pickup: `@Scheduled(fixedDelayString="${sms.dispatch.poll-interval}")`, ShedLock if multi-pod; claim batch:
`SELECT … FROM INSIGHT.SMS_TABLE WHERE STATUS = :pendingStatus AND NVL(SEND_ATTEMPTS,0) < :maxAttempts ORDER BY PRIORITY, CREATION_DATE, SERIAL_NO FETCH FIRST :batchSize ROWS ONLY FOR UPDATE SKIP LOCKED`; mark in-progress before calling providers.

DECISION NEEDED (DB owner): (1) STATUS value inserted for new SMS; (2) may new "in-progress"/"invalid mobile" statuses be introduced; (3) should failed rows be retried up to maxAttempts. Without (1) polling on `STATUS <> 88` resends failed/invalid rows forever.

## 3. Dispatch Job (replaces sendFitSmsMsg)

Per row: init timestamp + GUID; validate mobile (`validateMobileNumber`; ≠200 → no send, no update); call Send SMS with `messageBody`, `mobileNumbers=[mobileNo]`, `msgCategory="4.2"`, `expiryInSecs="300"`; `sendAttempts+1`; `UPDATE INSIGHT.SMS_TABLE SET MESSAGE_ID=?, STATUS=?, SEND_DATE=?, SEND_ATTEMPTS=? WHERE SERIAL_NO=?`; exception → 503 logged only, row untouched; audit log.

| Result | STATUS | MESSAGE_ID | SEND_DATE | SEND_ATTEMPTS |
|---|---|---|---|---|
| 200 | 88 | `<code>\|<messageID[0]>\|<correlationID>\|<provider>` | now | +1 |
| other | 1 | `<code>\|<correlationID>\|<provider>` | now | +1 |
| invalid mobile / exception | unchanged | | | |

MESSAGE_ID VARCHAR2(65) can overflow — truncate or widen.

## 4. Send SMS (Provider Router)

Legacy `SMSGateway.services:sendSMS`; target `SmsRouter.send()`, optionally `POST /sms/send`.

Request: `messageBody` (req), `mobileNumbers` String[] (req; legacy `mobileNumber(s)`; entries may contain several numbers separated by `; , /` whitespace), `msgCategory` (Etisalat only), `expiryInSecs` (Etisalat only), `correlationID`.
Response: `responseCode` (200 / 500 provider failure / 503 exception), `responseMessage` (`Success` / `Internal Server Error. Please Try Again Later.` / `Service Unavailable`), `messageID` String[] (Etisalat → the mobile number; Waselney → provider id), `correlationID`, `serviceProvider` (`ETISALAT`/`WASELNEY`), `lastError` (replace with logging).

```jsonc
{ "messageBody": "Your OTP is 123456", "mobileNumbers": ["971501234567"], "msgCategory": "4.2", "expiryInSecs": "300", "correlationID": "5b1d..." }
{ "responseCode": "200", "responseMessage": "Success", "messageID": ["971501234567"], "correlationID": "5b1d...", "serviceProvider": "ETISALAT" }
```

Routing: `PREFERRED_SMS_SERVICE_PROVIDER` = `WASELNEY` or anything other than `ETISALAT` → Waselney. `ETISALAT` → per number: if `ETISALAT_EXCLUDED_COUNTRIES` (prefix list) empty → Etisalat; if number starts with a listed prefix → VeriPhone lookup `country_code`; if in `ETISALAT_EXCLUDED_COUNTRY_CODES` → Waselney else Etisalat; non-matching → Etisalat. Send Waselney list first, then Etisalat. Exception → 503. Mixed-provider calls: second overwrites responseCode/serviceProvider (first result lost).

Optional REST `POST /sms/send` if other systems call sendSMS directly today.

## 5. Etisalat

Auth: `POST {ETISALAT_SMS_BASE_URL}/login/user` `{"username","password"}` → `token` (+ `decodedValue.expiryDt`, `sessionId`); retry 3× 5s; cache keyed by username; target refresh on 401 or before expiry.
Send: per recipient `POST {base}/campaigns/submissions/sms/nb`, `Authorization: bearer <token>`; retry 3× 5s on exception; "Authorization Required" → new token, repeat (target: limit to one refresh). Success: response `statusCode`=201 → 200/Success else 500. messageID = recipient number.
Body: `recipient`, `msg`, `msgCategory`, `contentType` "3.1", `senderAddr` "AL RAMZ", `priority` 0, `clientTxnId` = correlationID, `expiryDt` = now+expiryInSecs `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` (target: real UTC). Response `statusCode`, `jobId` (recommend storing jobId), others unused.

## 6. Waselney

Send: split recipients; encode message once (UCS-2 hex: each char → 4-digit lowercase hex of UTF-16 unit; `msg.chars().mapToObj(c -> String.format("%04x", c)).collect(joining())`); per recipient `GET {SMS_BASE_URL}?username=…&password=…&type=2&dlr=0&source=AL RAMZ&destination=<n>&message=<enc>`. Response text `<resultCode>|<messageId>|…`; `1701` → 200 else 500; messageID = token[1]. Non-PROD (`environment ≠ PROD`): not sent, simulated `1701|971562388556|<correlationID>`.
Get Report: `GET {SMS_BASE_URL}/xmlreport.aspx?uname=…&pwd=…&usertype=0&id=<id>` → XML `DeliveryReport/DeliveryReport/Report[]`; 200 / 404 "No SMS record found" when "There is no record(s) found" / 503 exception. Target optional `GET /sms/report/{id}`.

## 7. Configuration

`sms.provider.preferred`, `sms.etisalat.excluded-prefixes`, `sms.etisalat.excluded-country-codes`, `sms.etisalat.base-url`, `sms.etisalat.username`, `sms.etisalat.password` (secret), `sms.waselney.base-url`, `sms.waselney.username`, `sms.waselney.password` (secret), `sms.live-send-enabled`/profile, `sms.default.*` (4.2, 300, 3.1, AL RAMZ), `sms.dispatch.poll-interval`/`batch-size`/`max-attempts`/status values, `spring.datasource.*` (INSIGHT).
Dependencies: `validateMobileNumber` (port rules or libphonenumber), VeriPhone REST (or libphonenumber region), GUID, audit publisher with masking, token cache.

## 8. Codes

200 Success; 500 provider failure; 503 exception; 404 getReport no record; STATUS 88 sent / 1 failed; 1701 Waselney success; 201 Etisalat success.

## 9. Implementation Notes

No retry of failed SMS; invalid mobiles left untouched (add explicit status); exceptions leave row untouched (catch per row, set failure status); double-send risk (claim rows); Etisalat messageID is the phone (store jobId); Etisalat expiryDt local time labelled Z (use UTC); Etisalat token refresh can loop (limit one); Etisalat sends real SMS in every env, Waselney only PROD; Waselney password in URL and logged (mask; prefer POST if supported); MESSAGE_ID length; mixed-provider result loss; unused columns; GMT+4 timestamps; legacy is single-threaded serial.

Suggested: `SmsDispatchJob`, `SmsRepository`, `MobileNumberValidator`, `SmsRouter`, `EtisalatClient` + `EtisalatTokenCache`, `WaselneyClient` + `Ucs2Encoder`, `VeriPhoneClient`, `SmsProperties`, `AuditLogPublisher`, optional `SmsController` (`POST /sms/send`, `GET /sms/report/{id}`).

---

# PART 4 — OTP Services (`POST /sendOTP`, `POST /validateOTP`, `GET /getMessagesLookup`)

Legacy `OTP` v1.0; REST resource `OTP.restAPIs:OTP`; DB `MIDDLEWARE`: `OTP_CONFIGURATION`, `OTP_MANAGEMENT`, `OTP_ATTEMPTS`. Downstream: SMSGateway sendSMS, EmailGateway sendEmail, commonValidator (email/mobile), commonUtility (random digits, hashing, timestamps, static data), Seq.

## 1. Overview

Each use-case is an OTP service row in `OTP_CONFIGURATION` (`SERVICE_NAME` = `key`): token length, expiry, channel (SMS/EMAIL/both), EN/AR templates, attempt limits.
- Send OTP — generate numeric OTP, store SHA-256 hash, send by SMS and/or e-mail.
- Validate OTP — compare caller-supplied **hashed** OTP to the active unexpired OTP.
- Lookup — return all configurations/templates.

Legacy conventions: legacy field names on the wire; body-level business codes, HTTP always 200; `correlationID` echoed/generated; GMT+4; e-mails stored/compared upper case.

```text
POST /sendOTP {key, value, language, ipAddress}
  1 schema validate ─► 1025; 2 normalise value (trim, UPPER, separators → ','); 3 load config ─none─► 1135
  4 split value into e-mails/mobiles, validate per SENT_VIA ─► 1068 / 1060 / 1025
  5 deactivate previous OTPs; 6 reset yesterday's counters; today's sends ≥ MAX_ATTEMPTS ─► 1017
  7 generate OTP (TOKEN_LENGTH digits), expiry = now + EXPIRY_IN_SECONDS, SHA-256
  8 fill template (%OTP%), send SMS and/or e-mail; 9 record OTP_MANAGEMENT (SENT/FAILED) + OTP_ATTEMPTS
POST /validateOTP {key, value, hashedOTP}
  1 deactivate all expired; load config ─none─► 1135 (legacy returns 500); 2 split/validate recipients
  3 validation attempts ≥ MAX_VALIDATION_ATTEMPTS ─► 1133
  4 compare ─match─► 200 valid=true (deactivate, reset counters) ─else─► 1005 valid=false (+1 attempt)
GET /getMessagesLookup ─► all rows of OTP_CONFIGURATION
```

## 2. Data Model

OTP_CONFIGURATION: `MESSAGE_ID` NUMBER NOT NULL, `SERVICE_NAME` VARCHAR2(200) NOT NULL (names ending `_` get 6-digit test OTP in non-PROD), `EXPIRY_IN_SECONDS` NUMBER NOT NULL, `TOKEN_LENGTH` VARCHAR2(200) NOT NULL, `SMS_MESSAGE_ENGLISH`/`SMS_MESSAGE_ARABIC` VARCHAR2(4000) (`%OTP%`), `EMAIL_MESSAGE_ENGLISH`/`EMAIL_MESSAGE_ARABIC` CLOB (`<subject>^+^<body>`), `SENT_VIA` VARCHAR2(200) (`SMS`/`EMAIL`/`EMAIL|SMS`), `MAX_ATTEMPTS` NUMBER NOT NULL, `MAX_VALIDATION_ATTEMPTS` NUMBER.

OTP_MANAGEMENT: `ID` NUMBER NOT NULL (generated), `SERVICE_NAME`, `MOBILE_EMAIL` VARCHAR2(200) NOT NULL, `OTP` VARCHAR2(200) NOT NULL (SHA-256 hash), `CREATION_DATE_TIME`/`EXPIRY_DATE_TIME` TIMESTAMP(6), `ACTIVE` VARCHAR2(1) NOT NULL (`1`/`0`), `STATUS` VARCHAR2(20) (`SENT`/`FAILED`/`VALIDATED`), `IP_ADDRESS` VARCHAR2(20) (too short IPv6), `VALIDATION_ATTEMPTS` NUMBER.

OTP_ATTEMPTS: `ID`, `MOBILE_EMAIL` VARCHAR2(200), `SERVICE_NAME`, `ATTEMPTS` NUMBER, `LAST_ATTEMPT_DATE` TIMESTAMP(6).

## 3. Send OTP

Request: `key` (req, min 1; unknown → 1135), `value` (req, min 2; recipients separated by `| , ; /` whitespace, e.g. `user@x.com|971501234567`), `language` (req, 2 chars `EN`/`AR`), `ipAddress` (req, min 1), `correlationID` (optional).
Response: `correlationID`, `responseCode` (200, 402, 1017, 1025, 1060, 1068, 1135, 500), `responseMessage`, `response.status` (Boolean; present when send reached).

```json
{ "correlationID": "5b1d...", "responseCode": "200", "responseMessage": "OK", "response": { "status": true } }
{ "correlationID": "…", "responseCode": "402", "responseMessage": "REQUEST FAILED", "response": { "status": false } }
{ "correlationID": "…", "responseCode": "1017", "responseMessage": "User exceeded maximum number of attempts" }
```

Logic: 1 init + read `OTP_EMAIL_SMS_DELIVERY_ENABLED` and `environment`. 2 schema validate → `1025 Middleware Validation Exception : <detail>`. 3 normalise value (trim → UPPER → spaces→`,` → `; , / ws`→`,`). 4 load config, none → `1135 Invalid OTP Service`. 5 split on `|` then `,`; validateEmail → e-mail list, validateMobileNumber → mobile list. 6 SENT_VIA: `EMAIL|SMS` needs ≥1 of either else `1025 Invalid Request Parameters`; `EMAIL` needs e-mail else `1068 Invalid Email`; `SMS` needs mobile else `1060 Invalid Mobile Number`. 7 deactivate previous: `UPDATE OTP_MANAGEMENT SET ACTIVE='0' WHERE SERVICE_NAME=:key AND MOBILE_EMAIL IN (:phone,:emailId,:value)` (errors ignored). 8 `UPDATE OTP_ATTEMPTS SET ATTEMPTS=0 WHERE TRUNC(LAST_ATTEMPT_DATE) <> TRUNC(today)` (all rows), then `SELECT MAX(ATTEMPTS) … WHERE MOBILE_EMAIL IN (…) AND SERVICE_NAME=:key`; ≥ MAX_ATTEMPTS → `1017`. 9 generate TOKEN_LENGTH digits; non-PROD and delivery flag ≠ true: fixed `123456` if SERVICE_NAME ends `_`, else `1234`; SHA-256. 10 build EN/AR messages, replace `%OTP%`; e-mail template split on `^+^` into subject/body (trimmed). 11 send. 12 decide result, write tables. 13 exceptions: 1017/1025/1060/1068/1135 as thrown; schema → 1025; else 500. 14 audit, return.

Delivery: SMS via sendSMS (`msgCategory` 4.5); e-mail via sendEmail (`from` = `cm@alramz.ae` PROD / `notification@alramz.ae` non-PROD with delivery flag). Non-PROD flag ≠ true: not sent, treated as 200. Missing recipient for channel: `EMAIL`/`SMS` only → FAILED row + 1025; `EMAIL|SMS` → 1026 / 1027 (unreachable in legacy).

| SENT_VIA | Channel results | Response | OTP_MANAGEMENT | OTP_ATTEMPTS |
|---|---|---|---|---|
| SMS | SMS 200 | 200, status true | mobiles ACTIVE 1 SENT | +1 (MERGE) |
| EMAIL | Email 200 | 200, true | e-mails ACTIVE 1 SENT | +1 |
| EMAIL\|SMS | both 200 | 200, true | both rows ACTIVE 1 SENT | +1 |
| EMAIL\|SMS | one 200 | 200, true (partial not reported) | both ACTIVE 1 SENT | +1 |
| any | required failed | 402 REQUEST FAILED, false | ACTIVE 0 FAILED | unchanged |

Rows: hash, creation/expiry, IP, SERVICE_NAME, VALIDATION_ATTEMPTS 0. OTP_ATTEMPTS MERGE on (normalised value, SERVICE_NAME, latest ID).

## 4. Validate OTP (single transaction)

Request: `key`, `value`, `hashedOTP` (SHA-256 of user-entered OTP, same format as legacy stringHashing; case-sensitive), `correlationID`. No schema validation in legacy.
Response: `correlationID`, `responseCode` (200, 1005, 1133, 1025, 1060, 1068, 500), `responseMessage`, `response.valid`.

```json
{ "correlationID": "…", "responseCode": "200", "responseMessage": "OK", "response": { "valid": true } }
{ "correlationID": "…", "responseCode": "1005", "responseMessage": "Invalid OTP", "response": { "valid": false } }
{ "correlationID": "…", "responseCode": "1133", "responseMessage": "User exceeded maximum number of validation attempts" }
```

Logic: normalise; `UPDATE OTP_MANAGEMENT SET ACTIVE='0' WHERE EXPIRY_DATE_TIME < :now` (all users); load config (none → 1135, legacy returns 500); classify/channel check; `SELECT MAX(VALIDATION_ATTEMPTS) … ACTIVE=1` ≥ max → 1133; find active unexpired hash (first) and compare exactly; match → `SET ACTIVE='0', STATUS='VALIDATED' WHERE SERVICE_NAME=:key AND OTP=:hashedOTP` + reset OTP_ATTEMPTS → 200 valid=true; no match → VALIDATION_ATTEMPTS+1 → 1005 valid=false; exception → rollback, codes as thrown, else 500.

## 5. Lookup

`GET /getMessagesLookup`: `SELECT SERVICE_NAME, EXPIRY_IN_SECONDS, TOKEN_LENGTH, MAX_ATTEMPTS, SMS_MESSAGE_ARABIC, SMS_MESSAGE_ENGLISH, EMAIL_MESSAGE_ARABIC, EMAIL_MESSAGE_ENGLISH, SENT_VIA FROM OTP_CONFIGURATION` → 200; error → 500. No audit, no correlation ID.
Response: `responseCode`, `responseMessage`, `response.messages[]` { `serviceName`, `expiryInSeconds`, `tokenLength`, `maxAttempts`, `smsMessageEnglish`, `smsMessageArabic`, `emailMessageEnglish`, `emailMessageArabic`, `sentVia` }. MAX_VALIDATION_ATTEMPTS not returned.

## 6. Configuration

`otp.environment`/profile, `otp.delivery-enabled`, `otp.test-otp.default`/`.underscore-services` (1234/123456), `otp.email.from.prod`/`.nonprod`, `otp.sms.category` (4.5), `otp.hash.algorithm` (SHA256), `spring.datasource.*`, `app.timezone` (GMT+4). Deps: SecureRandom, MessageDigest SHA-256 (same encoding as legacy), Bean Validation, email/mobile validators.

## 7. Codes

| responseCode | responseMessage | API | Target HTTP |
|---|---|---|---|
| 200 | OK | All | 200 |
| 402 | REQUEST FAILED | Send | 502 |
| 1005 | Invalid OTP | Validate | 400/401 |
| 1017 | User exceeded maximum number of attempts | Send | 429 |
| 1025 | Invalid Request Parameters / Middleware Validation Exception : … | Send, Validate | 400 |
| 1060 | Invalid Mobile Number | Send, Validate | 400 |
| 1068 | Invalid Email | Send, Validate | 400 |
| 1133 | User exceeded maximum number of validation attempts | Validate | 429 |
| 1135 | Invalid OTP Service | Send (Validate legacy 500) | 400 |
| 500 | Internal Server Error | All | 500 |

## 8. Implementation Notes

Hash format must match exactly (confirm encoding with known value `123456`); client-side unsalted SHA-256 of 4–6 digits is reversible (future HMAC — breaking); validate should return 1135 not 500; partial delivery reported as success (decide); global resets on every call → scheduled clean-up or per-user; validated deactivation by hash+service only → include recipient/row id; language other than EN/AR → restrict (400); default MAX_VALIDATION_ATTEMPTS; test OTPs tied to profile, never prod; log swallowed deactivation errors; map subject/correlationID explicitly; IP_ADDRESS 20 too short; single `@Transactional` validate; no inbound auth → JWT (decision 4).

Suggested: `OtpController`, `SendOtpRequest`/`ValidateOtpRequest`/`OtpResponse<T>`, `RecipientParser`, `OtpService` (`@Transactional`), `OtpGenerator`/`OtpHasher`, repositories, `TemplateRenderer`, `SmsClient`/`EmailClient`, `OtpCleanupJob`, `GlobalExceptionHandler`.
