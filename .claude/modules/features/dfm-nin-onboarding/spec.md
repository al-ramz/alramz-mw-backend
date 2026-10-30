# Spec: dfm-nin-onboarding (DFM investor registration / NIN creation)

Source request: `.claude/modules/features/dfm-nin-onboarding/source-design-doc.md`. This is the
DFM-NINOnboarding API Design & Migration Specification v0.1, which migrates webMethods IS 10.7
`DFMIntegrations.v2.services:NINOnboarding` to Spring Boot.

Target: the **existing** `services/onboarding-service`. The design doc calls it "the DFM module of the
OnBoarding Spring Boot service", and that service already hosts the inbound DFM journey
(`POST /api/v1/dfm/onboarding`).

---

## 1. Feature Objective & Scope

**Problem.** Al Ramz middleware must register investors with the Dubai Financial Market (DFM). It sends
identity, address, dividend-payment and tax data to DFM's Member API `POST {base}/memberapis/onboarding`.
DFM then creates or finds the investor's NIN and opens cash and, optionally, margin trading accounts.

Today the IS flow `NINOnboarding` does this as a thin pass-through. It has these defects:
- it leaks `lastError.pipeline` to callers, including the DFM security key and ID images (F-01);
- it turns validation errors into `500` (F-02);
- it silently mangles mobile numbers and addresses (F-03, F-04);
- it never checks DFM's HTTP status (F-05);
- it has no timeout on the DFM call (F-06).

**What this feature does.** It adds one synchronous, JWT-secured endpoint, `POST /api/v1/dfm/investors`,
to `onboarding-service`. The endpoint:
1. validates a typed, grouped JSON request up front;
2. maps it to DFM's snake_case payload (adding the member code and security key from configuration);
3. calls DFM with a connect/read timeout, a single connect-failure retry and a circuit breaker;
4. returns the NIN, trading numbers and references in the repo's `GenericResponse` envelope, with real
   HTTP statuses and `DFN###` error codes.

**Trigger.** Synchronous HTTP call from internal middleware consumers. The legacy consumer is
"Middleware, internal call"; the gateway route `/gateway/DFM/1.0/NINCreation` is re-pointed
operationally.

**Done** (from the requester's view):
- A caller can POST the §3 request and get `201` with NIN, trading numbers and references.
- Invalid input gets `400` + `DFN001–005`.
- A DFM rejection gets `400 DFN006` or `409 DFN007`, carrying DFM's message.
- DFM faults get `502 DFN010`, `504 DFN011` or `503 DFN014`.
- No secret, stack trace or request body is ever returned to the caller.
- The DFM security key never appears unmasked in logs or spans.

**Out of scope (explicit)**
- The inbound DFM journey (`v2.services:onboarding` / `accountUpdate`). It already exists as
  `OnboardingController` / `POST /dfm/onboarding`, and this feature does not change it.
- `processRequestsPendingAccountCreation`, its trigger, the `v2.adapters:*` JDBC services and
  `docs:NINOnboardingRequest` (design doc §1.2).
- Persisting anything. The legacy flow has no DB access (design doc Appendix C). The legacy
  `SEQDatalust`/`middleware.SEQ_LOG` logging is replaced by the starter's existing request/response
  logging and inbound `api_audit_log` row; no new table is added.
- A legacy-shaped shim (flat snake_case `/NINCreation`). Callers cut over to the new contract. This
  follows the user's precedent decision Q1 in `.claude/modules/features/mmfintegrations/spec.md`.
- These operational items:
  - rotating the DFM key exposed on Confluence and anonymising that page (F-07, Appendix E #8);
  - gateway re-routing (E #3);
  - Key Vault / k8s secret wiring (`k8s/`, `infra/`, `.github/SECRETS.md`);
  - registering consumers with the new role;
  - `SEQ_LOG` retention.
- Accepting DMS document ids instead of inline Base64 (E #7). The request's contract specifies
  inline Base64.
- A health indicator for DFM. The design doc §3.3 asks only for circuit-breaker state, which
  resilience4j already exposes.
- Fixing these pre-existing defects in the existing `GlobalExceptionHandler`. They are noted in §9 for
  follow-up:
  - no `AccessDeniedException` handler, so a `403` becomes a `503` or `500`;
  - `UUID.fromString` on a client-supplied `X-Correlation-Id`.
- Fixing the pre-existing `ApiAuditAspect` / `AbstractRestClient` exposures (see §5). They are flagged
  for `04_security`.

---

## 2. Repository Grounding

| Concern | Existing artefact this spec builds on |
|---|---|
| Host service | `services/onboarding-service`, which already has `OnboardingController implements DfmOnboardingApi` (`@RequestMapping("/api/v1")`, `@JwtSecured(roles = "APP_DATA_VALIDATION")`). The `.claude/howtos/*` docs still call this code "data-validation-service". |
| Contract file (amend) | `services/onboarding-service/specs/apiSpecs.yaml`. It has `/iban/validate`, `/veriphone/verify`, `/existing-data/validation` and `/dfm/onboarding`. Its root has `security: - BearerAuth: []`. Its `GenericResponse` has `responseCode`, `responseMessage`, `response`, `correlationId`(uuid) and **no** `errorCode`/`errorMsg`. |
| Codegen | `services/onboarding-service/pom.xml` `openapi-generator-maven-plugin`: `interfaceOnly`, `useTags`, `performBeanValidation`, `useResponseEntity`, `openApiNullable=false`. Tag `DFM Onboarding` generates `com.alramz.api.DfmOnboardingApi`, so tag `DFM Investors` will generate `DfmInvestorsApi`. Models go to `com.alramz.model`. |
| Envelope + error-code precedent | `services/utility-service/specs/apiSpecs.yaml` `GenericResponse` (`responseCode`, `responseMessage`, `response`, `correlationId`, `errorCode` nullable, `errorMsg` nullable). The `utility-service` `exception/ErrorCode.java` enum has `FNX###` codes. `mmfintegrations/spec.md` set the precedent of amending a service's `GenericResponse` to match utility. |
| External-client precedent | `services/utility-service/.../finoux/{FinouxClient, FinouxResult}.java` is a feature package `com.alramz.finoux` with a sealed result type (`Posted/Rejected/Failed/Unavailable`), matching the design doc's `OperationResult`. `.../config/ExternalFinouxServiceConfig.java` and `onboarding-service/.../config/ExternalETradeServiceConfig.java` build a qualified `WebClient` with `alramzWebClientLoggingFilterFunction` and resilience4j `CircuitBreaker`/`Retry`/`TimeLimiter` beans from the registries. |
| Starter HTTP client | `alramz-api-starter/.../client/AbstractRestClient.java`. It is **not** used here, for two reasons: (a) `execute()` adds the **inbound caller's bearer JWT** to every outbound request (`getToken()`), which would leak our JWT to DFM; (b) `handleError()` keeps only a `message` field, so DFM's `response_msg` would be lost. The new client uses the same `WebClient` and resilience4j reactor operators directly. |
| Config-properties precedent | `onboarding-service/.../config/ETradeProperties.java`: a `record` with `@ConfigurationProperties(prefix = "adapter.etrade")`. Adapter config lives under `adapter.*` in every `application-{profile}.yml`, with secrets as `${ENV_VAR:}` (e.g. `adapter.external-iban-service.api-key: ${IBAN_API_KEY:}`). |
| Exception handling | `onboarding-service/.../exception/GlobalExceptionHandler.java` uses `@RestControllerAdvice(basePackages="com.alramz.controllers") @Order(0)` and branches on path (`VALIDATION_PATH`, `ONBOARDING_PATH`). Its catch-all returns **503** for unknown paths, and it has no `AccessDeniedException` handler. Existing typed exceptions: `ApplicationException(field, message, serviceErrorResponseCode)`, `ExternalSystemException`, `TechnicalException(message, errorCode)`. The pattern doc is `.claude/howtos/exception-handling.md` §4 Step 4: a narrower `assignableTypes` advice must have the **lower** `@Order`. The `utility-service` `GlobalExceptionHandler` maps `AccessDeniedException` to 403 explicitly. |
| Correlation id | The starter's `CorrelationIdFilter` + `CorrelationIdResponseBodyAdvice` fill `setCorrelationId(UUID)` on any body. `MDCUtil.getCorrelationIdAsUuid()` falls back to a fresh UUID for a non-UUID header. |
| JWT | `@JwtSecured(roles = ...)` (`String[] roles`); `JwtSecuredAspect` throws `AccessDeniedException`. `permit-all-urls` in every `application-*.yml` contains only `/api/v1/info` (+ `/actuator/prometheus` in dev). |
| Logging / masking | `company.logging.request/response.include-payload: true`, `include-headers: true`, `database-logging.enabled: true` (`include-request-payload: true`), `aspect.enabled: true`, in all `onboarding-service` profiles. The `outbound-enabled` property is **not** set. `SensitiveDataMasker.maskBody()` (used for `api_audit_log` bodies) = `LogMaskingUtil.mask(truncate(body, 5000))`. So `company.logging.masking.sensitive-keys` (substring, case-insensitive, scalar JSON values only) governs audit-row bodies, log lines **and** `WebClientLoggingFilter` span tags (outbound headers and bodies). `ApiAuditAspect` audits `execution(* com.alramz.client..*(..))` with `masker.mask(Map)`, which does **not** walk POJOs, so a POJO argument is stored unmasked. |
| Persistence | `onboarding-service/src/main/resources/db/changelog/{dev,docker,preprod,prod}/sql/001…006-*.sql`. This feature adds no changeset. |
| Sizing | `services/onboarding-service/service.yaml`: 0.5 CPU / 1Gi, 1–5 replicas, `-Xmx768m`. |
| Jackson | `onboarding-service/.../config/JacksonConfig.java` sets global `SNAKE_CASE`. Generated models carry explicit `@JsonProperty("<yamlName>")`, so the camelCase YAML names below go on the wire. Hand-written DFM records must use explicit `@JsonProperty`. |
| `.http` collection | `services/onboarding-service/apiCollection.http` registers a consumer with `roles: ["APP_DATA_VALIDATION"]`. Requests are added per `.claude/howtos/api-collection-http-requests.md` §4. |

**Placement** (flat package-by-layer, matching `onboarding-service`; DFM client in its own feature
package, following utility's `com.alramz.finoux`):

| Package | New class(es) |
|---|---|
| `com.alramz.controllers` | `DfmInvestorController implements com.alramz.api.DfmInvestorsApi` |
| `com.alramz.service` | `DfmInvestorService` (interface, matching the service's interface+impl convention) |
| `com.alramz.service.impl` | `DfmInvestorServiceImpl` |
| `com.alramz.dfm` | `DfmMemberApiClient`, `DfmOnboardingPayload` (+ nested records), `DfmOnboardingResponse`, `DfmOnboardingOutcome` (sealed) |
| `com.alramz.config` | `DfmMemberApiProperties`, `ExternalDfmMemberApiConfig` |
| `com.alramz.exception` | `DfmInvestorErrorCode`, `DfmRejectedException`, `DfmUnavailableException`, `DfmConfigurationException`, `DfmInvestorExceptionHandler` |

`com.alramz.dfm` is deliberately **outside** `com.alramz.client..*`, so `ApiAuditAspect` does not
persist the unmasked payload POJO (with images and the security key) as an OUTBOUND audit row.

---

## 3. Interface Contracts

### 3.0 Summary

| Fact | Value | Resolved by |
|---|---|---|
| Verb + bare path | `POST /dfm/investors` (served at `/api/v1/dfm/investors`) | Request (design doc §4.1) |
| Tag / operationId | `DFM Investors` / `registerDfmInvestor` | Defaulted; matches the `DFM Onboarding`/`onboard` style |
| Security | JWT-secured, `@JwtSecured(roles = "APP_DFM_ONBOARDING")`; **not** in `permit-all-urls` | Request asks for a dedicated write permission (design doc §3.4 `dfm.onboarding.write`). Mapped to the repo's `APP_<SERVICE_CONCERN>` role mechanism, since the starter has no OAuth2 scopes. |
| Envelope | `GenericResponse`, amended with nullable `errorCode`, `errorMsg` | Checklist default + utility-service precedent; identical to the design doc §2.4.1 envelope |
| Success | `201 Created` for every accepted registration, including a DFM "Exact NIN match" | Request (§4.1). DFM gives no reliable match signal (E #1), so it is always 201. |
| Contract file | **Amend** `services/onboarding-service/specs/apiSpecs.yaml` (add path + schemas; amend `GenericResponse`) | Checklist §4.1 |
| Headers | `Authorization: Bearer <jwt>` required; `X-Correlation-Id` optional, echoed as `correlationId` | Starter behaviour |

**`GenericResponse` amendment.** Add these two properties:
- `errorCode`: `type: string, nullable: true, example: DFN001`;
- `errorMsg`: `type: string, nullable: true`.

The change is additive. Existing operations keep working and simply emit `"errorCode": null,
"errorMsg": null`. No existing example changes.

**Responses list in YAML.**
- `201`, `400`, `409`, `500`, `502`, `503` and `504` each get a `GenericResponse` example (below).
- `401` and `403` get a description only. This matches the existing file's convention: the `401` body
  comes from the starter's JWT filter, outside MVC.

This endpoint is deliberately more complete than the sparser `validateIBAN`-style precedent
(checklist §5 pitfall 1).

### 3.1 Request schema: `DfmInvestorRegistrationRequest`

The request follows design doc §4.2 with two field renames: `emiratesId.number` becomes
`emiratesId.eidNumber`, and `passport.number` becomes `passport.passportNumber`. The reason is that
masking is substring-on-leaf-key, and a generic `number` key cannot be masked without also masking
`trackingNumber`/`cashTradingNumber` (see §5).

Every string is trimmed by Jackson as received; there is no trimming in code. All dates are
`type: string, format: date` (→ `LocalDate`, ISO `yyyy-MM-dd`).

**Property order matters**, because audit bodies are truncated at 5000 chars *before* masking:
- top level: identity scalars → `address` → `dividendPayment` → `portfolioOptions` → `crs` →
  `fatca` → `emiratesId` → `passport` → `signature` → `guardian` (image-bearing objects last);
- inside each object, image fields come last.

| Field | Type / constraint (YAML) | Required | Notes |
|---|---|---|---|
| `requestId` | string, `format: uuid` | yes | DFM idempotency key; maps to `request_id` |
| `firstName`, `lastName` | string, minLength 1, maxLength 40 | yes | |
| `fullNameEn` | string, minLength 1, maxLength 40 | yes | |
| `fullNameAr` | string, maxLength 40 | conditional | Required when `nationality` is an Arab League state (rule R3) |
| `dateOfBirth` | date | yes | |
| `gender` | enum `[M, F]` | yes | |
| `nationality` | string, pattern `^[A-Z]{2}$` | yes | Also checked against `Locale.getISOCountries()` |
| `familyId` | string, maxLength 15 | no | |
| `nin` | string, maxLength 15 | no | Known NIN of an existing investor |
| `mobile` | string, pattern `^\+[1-9]\d{6,14}$` (E.164) | yes | |
| `email` | string, `format: email`, maxLength 80 | yes | |
| `address` | `DfmAddress` | yes | |
| `address.country` | string, pattern `^[A-Z]{2}$` | yes | ISO-checked |
| `address.city` | string, minLength 1, maxLength 25 | yes | |
| `address.line` | string, maxLength 40 | conditional | Required when `poBox` is absent (R5) |
| `address.poBox`, `address.zipCode` | string, maxLength 10 | conditional | `poBox` required when `address.country == AE` (R4) |
| `address.phone` | string, E.164 pattern | no | |
| `dividendPayment` | `DfmDividendPayment` | yes | |
| `dividendPayment.method` | enum `[IVESTOR, BANK]` | yes | |
| `.aedIban`, `.usdIban` | string, pattern `^AE\d{21}$` | conditional | `aedIban` required for `BANK` (R6) |
| `.foreignAccount` | string, maxLength 34 | no | |
| `.foreignSwift`, `.correspondentSwift` | string, pattern `^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$` | no | |
| `.correspondentIban` | string, maxLength 34 | no | |
| `.routeCode` | string, maxLength 20 | no | DFM enforces the per-country rule |
| `.ivestorPrintName` | string, maxLength 26 | conditional | Required for `IVESTOR` (R6) |
| `portfolioOptions` | array of enum `[CASH, MARGIN]`, minItems 1, uniqueItems | yes | `MARGIN` requires `CASH` (R7) |
| `crs` | `DfmCrs` | no | |
| `crs.resident` | boolean | no | |
| `crs.countries[]` | `{country: ^[A-Z]{2}$ (req), hasTin: boolean (req), tin: string ≤ 50, noTinReason: string ≤ 10, whyNoTinIssued: string ≤ 200}` | no | |
| `fatca` | `{usPerson: boolean, tin: string ≤ 50}` | no | |
| `emiratesId` | `{eidNumber: ^784-\d{4}-\d{7}-\d$ (req), expiryDate: date (req), frontImage: base64 (req), backImage: base64 (req)}` | conditional | R1/R2 |
| `passport` | `{passportNumber: string 1–15 (req), expiryDate: date (req), imageType: enum [jpg, jpeg, png, pdf] (req), image: base64 (req)}` | conditional | R2 |
| `signature` | `{imageType: enum [jpg, jpeg, png, pdf] (req), image: base64 (req)}` | yes | |
| `guardian` | `{name: string 1–100 (req), type: string 1–50 (req), proofDocument: base64 (req)}` | conditional | R8 |

Base64 fields are `type: string`, standard alphabet, no `data:` prefix. Their size limits are:
- `maxLength: 2796204` (2 MiB decoded) for `frontImage`, `backImage`, `passport.image` and
  `proofDocument`;
- `maxLength: 1398104` (1 MiB decoded) for `signature.image`.

**Cross-field rules.** These are checked in `DfmInvestorServiceImpl` after Bean Validation. A
failure throws `ApplicationException(field, msg, code)`.

| Rule | Condition | Code |
|---|---|---|
| R1 | `nationality == AE` → `emiratesId` required | DFN003 |
| R2 | `emiratesId` absent → `passport` required | DFN003 |
| R3 | `nationality` ∈ {AE, BH, DJ, DZ, EG, IQ, JO, KM, KW, LB, LY, MA, MR, OM, PS, QA, SA, SD, SO, SY, TN, YE} → `fullNameAr` required | DFN003 |
| R4 | `address.country == AE` → `address.poBox` required | DFN003 |
| R5 | `address.poBox` absent → `address.line` required | DFN003 |
| R6 | `BANK` → `aedIban` required; `IVESTOR` → `ivestorPrintName` required | DFN003 |
| R7 | `MARGIN` in `portfolioOptions` → `CASH` also present | DFN003 |
| R8 | Age < 18 on today's `Asia/Dubai` date → `guardian` required | DFN003 |
| R9 | `address.country` not in `Locale.getISOCountries()` | DFN001 |
| R9b | `nationality` / `crs.countries[].country` not ISO | DFN003 |
| R10 | Each Base64 field must decode (`Base64.getDecoder()`), and its magic bytes must be JPEG `FFD8FF`, PNG `89504E47` or PDF `25504446`. For fields with an `imageType`, the detected type must match (`jpg`/`jpeg` ↔ JPEG). | DFN003 |

Request example: the sample in design doc §4.4, with `emiratesId.number` → `eidNumber`,
`passport.number` → `passportNumber`, and `fullNameAr` kept.

### 3.2 Response

**201 example**

```json
{ "responseCode": "201", "responseMessage": "Created", "errorCode": null, "errorMsg": null,
  "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33",
  "response": { "nin": "EGA0000000", "cashTradingNumber": "9400000", "marginTradingNumber": null,
    "memberReference": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d", "dfmReference": "1b2c3d4e-5f60-4718-89ab-cdef01234567",
    "trackingNumber": "2313258142", "dfmMessage": "OK" } }
```

**`DfmInvestorRegistration` schema** (all `type: string`, nullable):
`nin`, `cashTradingNumber`, `marginTradingNumber`, `memberReference`, `dfmReference`,
`trackingNumber`, `dfmMessage`.

`memberReference` and `dfmReference` are plain strings (maxLength 36), not `format: uuid`. If DFM
sends a non-UUID, the value is passed through and a WARN is logged. This avoids losing data on a
format deviation, as design doc §4.5 intends.

**Error responses.** Every one has `response: null`, `correlationId` set, and the HTTP status equal
to `responseCode`.

| Status | errorCode | When | Example `errorMsg` |
|---|---|---|---|
| 400 | DFN001 | `address.country` missing, not `^[A-Z]{2}$`, or not ISO (R9) | `address.country must be an ISO 3166-1 alpha-2 code` |
| 400 | DFN002 | `address.city` missing, blank, or > 25 chars | `address.city is required and must be at most 25 characters` |
| 400 | DFN003 | Any other missing or malformed field; malformed JSON; bad enum, date or UUID; rules R1–R8, R9b, R10 | `emiratesId is required when nationality is AE` |
| 400 | DFN004 | `mobile` missing or not E.164 | `mobile must be E.164, e.g. +971501234567` |
| 400 | DFN005 | `address.line` > 40 chars | `address.line must be at most 40 characters` |
| 400 | DFN006 | DFM rejected the data (§3.4) | DFM's `response_msg` verbatim, e.g. `Request id should be a valid GUID.`. If the body is empty or unreadable on a 4xx: `DFM rejected the request (HTTP 400)` |
| 401 | – | JWT missing or invalid (starter filter) | – |
| 403 | null | Role `APP_DFM_ONBOARDING` missing | `Access denied` |
| 409 | DFN007 | `requestId` still being processed by DFM (§3.4) | DFM's `response_msg`, else `A registration with this requestId is still in progress at DFM; retry later with the same requestId` |
| 500 | DFN012 | `adapter.dfm-member-api.base-url` / `member-code` / `security-key` blank, or base-url not `https://` | `DFM integration is not configured` |
| 500 | DFN013 | Any unexpected exception | `Internal Server Error` |
| 502 | DFN010 | DFM HTTP 5xx, 401/403, or other non-2xx/4xx; unparseable body; `response_code` not 200 / 4xx / duplicate | `DFM returned an invalid or failed response` |
| 503 | DFN014 | Circuit breaker open (`CallNotPermittedException`) | `DFM is temporarily unavailable; retry later with the same requestId` |
| 504 | DFN011 | Connect failure after one retry, or no answer within the read timeout | `DFM did not respond in time; retry later with the same requestId` |

**When several Bean Validation errors occur in one request:**
- `errorMsg` joins all `"<fieldPath>: <message>"` entries with `"; "`, sorted by field path;
- `errorCode` is the first match in precedence order DFN001 → DFN002 → DFN004 → DFN005 → DFN003.

Example 400:
```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "DFN001",
  "errorMsg": "address.country must be an ISO 3166-1 alpha-2 code", "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33", "response": null }
```

Example 504:
```json
{ "responseCode": "504", "responseMessage": "Gateway Timeout", "errorCode": "DFN011",
  "errorMsg": "DFM did not respond in time; retry later with the same requestId", "correlationId": "…", "response": null }
```

Provider and 500 errors carry their code. This follows the utility precedent of `FNX012`/`FNX013`
on 503/500, and supersedes design doc §4.7, which shows `errorCode: null` on 504.

### 3.3 Outbound DFM call (`DfmMemberApiClient`)

**Request.**
- `POST {adapter.dfm-member-api.base-url}/memberapis/onboarding`.
- Headers: `Content-Type: application/json`, `Accept: application/json`, and `securitykey: <key>`.
- **No `Authorization` header.** The client does not extend `AbstractRestClient`.
- The security key is sent in both the header **and** the body. This is the legacy behaviour, and it
  stays until DFM confirms otherwise (E #4).

**Body.** `DfmOnboardingPayload` is a record with `@JsonInclude(NON_NULL)` and an explicit
`@JsonProperty` on every component. Mapping per design doc §6:

| DFM field | From |
|---|---|
| `request_id` | `requestId.toString()` |
| `securitykey` | `adapter.dfm-member-api.security-key` |
| `request_mbrcode` | `adapter.dfm-member-api.member-code` |
| `cust_firstname`, `cust_lastname`, `cust_engfullname`, `cust_arfullname` | as given |
| `cust_dob` | `yyyy-MM-dd` |
| `cust_gender` | `M` / `F` |
| `cust_nationality`, `cust_familyid` | as given |
| `cust_nin` | `nin` |
| `cust_mobile` | E.164 with the leading `+` replaced by `00` (`+971501234567` → `00971501234567`) |
| `cust_email` | as given |
| `eid_no` | as given (dashed form) |
| `eid_expirydate` | `yyyy-MM-dd` |
| `eid_attachment_front`, `eid_attachment_back` | Base64 as given |
| `pp_no`, `pp_expirydate`, `pp_attachment`, `pp_attachment_type` | as given (type lower-case) |
| `cust_signatureimage`, `cust_signatureimage_type` | as given |
| `adr_country`, `adr_city` | as given |
| `adr_address` | `address.line` (no truncation) |
| `adr_pobox`, `adr_zipcode` | as given |
| `adr_phone` | `+` → `00` |
| `pay_method` | `IVESTOR` → `iVESTOR`, `BANK` → `bank` |
| `pay_aed_iban`, `pay_usd_iban`, `pay_frn_iban` (= `foreignAccount`), `pay_frn_swift`, `pay_cor_iban`, `pay_cor_swift`, `pay_routecode` | as given |
| `pay_printname` | `ivestorPrintName` |
| `portfolio_options` | lower-case, comma-joined in order CASH, MARGIN (`"cash"` / `"cash,margin"`) |
| `csr_resident` | `Y` / `N` |
| `csr.csr_countries[]` | `csr_country`; `csr_havetin` (`Y`/`N`); `csr_tin`; `csr_notinreason`; `csr_whynotinissued` |
| `fatca_uscitizen` | `Y` / `N` |
| `fatca_tin` | as given |
| `guardian_name`, `guardianship_type`, `proof_of_guardianship` | as given |

`Y`/`N` follows the precedent in `OnboardingServiceImpl.isUsCitizenFlag` (`"Y"`) for DFM's own
`fatca_uscitizen`.

**Response read.** The body is read as a String and parsed with the service `ObjectMapper` into
`DfmOnboardingResponse(@JsonProperty("response_code") String responseCode, response_msg,
member_reference, dfm_reference, tracking_no, cash_acc, margin_acc, cust_nin)`. A numeric
`response_code` is coerced to String.

**Classification** (`exchangeToMono`, so the status and body are both available):

| Condition | Outcome |
|---|---|
| Connect failure (`WebClientRequestException` caused by `ConnectException` / `ConnectTimeoutException`) | Retried **once** with the identical payload (same `request_id`), then `Unavailable(TIMEOUT)` → DFN011 |
| Read timeout (`TimeLimiter` `TimeoutException`, or a netty `ReadTimeoutException`) | `Unavailable(TIMEOUT)` → DFN011. No retry. |
| `CallNotPermittedException` | `Unavailable(CIRCUIT_OPEN)` → DFN014 |
| HTTP 5xx, 401, 403, 1xx/3xx | `Unavailable(BAD_RESPONSE)` → DFN010. 401/403 are logged at ERROR as "DFM rejected credentials". |
| HTTP 409, or HTTP 2xx with `response_code` ∈ `duplicate-in-progress-codes` | `Rejected(duplicateInProgress=true)` → DFN007 |
| Other HTTP 4xx | `Rejected(msg = response_msg if parseable)` → DFN006 |
| HTTP 2xx with unparseable body | `Unavailable(BAD_RESPONSE)` → DFN010 |
| HTTP 2xx, `response_code == "200"` | `Success` → 201 |
| HTTP 2xx, `response_code` 400–499 | `Rejected` → DFN006 |
| HTTP 2xx, any other `response_code` | `Unavailable(BAD_RESPONSE)` → DFN010 |

**Circuit breaker accounting.** Only the `Unavailable` cases are emitted as error signals *before*
the `CircuitBreakerOperator`, so only technical failures count toward opening the breaker.

**Resilience settings.** These are hard-coded in `ExternalDfmMemberApiConfig`, with resilience id
`dfm-member-api`.

| Setting | Value |
|---|---|
| `CircuitBreakerConfig` | COUNT_BASED, `slidingWindowSize(5)`, `minimumNumberOfCalls(5)`, `failureRateThreshold(100)`. It opens after 5 consecutive technical failures; the open-state wait stays at the resilience4j default of 60 s. |
| `RetryConfig` | `maxAttempts(2)`, `retryOnException` = connect failure only |
| `TimeLimiterConfig` | `timeoutDuration = read-timeout-seconds` |
| Connect timeout | Set on the connector to `connect-timeout-seconds` *if* `ReactorClientHttpConnector` is on the classpath. 03_implement must verify; if it is absent, rely on the `TimeLimiter`. |

The `WebClient` bean is `@Bean("dfmMemberApiWebClient")`, built from `WebClient.Builder` with
`alramzWebClientLoggingFilterFunction`, like `ExternalETradeServiceConfig`.

### 3.4 Java signatures (new)

```java
// com.alramz.config.DfmMemberApiProperties
@ConfigurationProperties(prefix = "adapter.dfm-member-api")
public record DfmMemberApiProperties(String baseUrl, String memberCode, String securityKey,
        @DefaultValue("5") int connectTimeoutSeconds, @DefaultValue("60") int readTimeoutSeconds,
        @DefaultValue List<String> duplicateInProgressCodes) {}

// com.alramz.dfm.DfmOnboardingOutcome
public sealed interface DfmOnboardingOutcome {
    record Success(DfmOnboardingResponse body) implements DfmOnboardingOutcome {}
    record Rejected(String providerCode, String providerMessage, boolean duplicateInProgress) implements DfmOnboardingOutcome {}
    record Unavailable(Reason reason, Throwable cause) implements DfmOnboardingOutcome {}
    enum Reason { TIMEOUT, CIRCUIT_OPEN, BAD_RESPONSE }
}

// com.alramz.dfm.DfmMemberApiClient   (@Component; NOT @Loggable; NOT under com.alramz.client)
public DfmOnboardingOutcome onboard(DfmInvestorRegistrationRequest request); // builds payload incl. member code + key

// com.alramz.dfm.DfmOnboardingPayload
static DfmOnboardingPayload from(DfmInvestorRegistrationRequest r, String memberCode, String securityKey);

// com.alramz.service.DfmInvestorService
DfmInvestorRegistration register(DfmInvestorRegistrationRequest request); // throws ApplicationException / Dfm*Exception

// com.alramz.controllers.DfmInvestorController
@Override @JwtSecured(roles = "APP_DFM_ONBOARDING") @Loggable(logArgs = false)
public ResponseEntity<GenericResponse> registerDfmInvestor(DfmInvestorRegistrationRequest request);
// -> ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse{ "201","Created", response=registration })
```

---

## 4. Data Model & Persistence Changes

**None.**
- No entity, no table, no SQL key, no Liquibase changeset.
- The `onboarding-service` changelog folders (`db/changelog/{dev,docker,preprod,prod}/sql/001…006`,
  test `001…005`) are untouched, and no changeset number is used.
- The legacy flow had no DB access (design doc Appendix C).
- The inbound request/response is persisted only by the existing starter `api_audit_log` filter
  (masked; see §5).
- Generated OpenAPI models (`com.alramz.model.DfmInvestorRegistrationRequest`, `DfmAddress`,
  `DfmDividendPayment`, `DfmCrs`, `DfmCrsCountry`, `DfmFatca`, `DfmEmiratesId`, `DfmPassport`,
  `DfmSignature`, `DfmGuardian`, `DfmInvestorRegistration`) are transport DTOs only.

---

## 5. Security & Config Impact

**`permit-all-urls`: no change.** The endpoint stays authenticated. The role is the new
`APP_DFM_ONBOARDING`, deliberately **not** `APP_DATA_VALIDATION`. That role is held by
validation/inbound-DFM consumers and must not grant investor registration at the exchange. Consumers
get the role via `/api/auth/register` (operational).

**New config** in `application-{dev,docker,test,preprod,prod}.yml` under `adapter:`, matching where
`adapter.etrade` lives:

```yaml
adapter:
  dfm-member-api:
    base-url: ${DFM_MEMBER_API_BASE_URL:}
    member-code: ${DFM_MEMBER_API_MEMBER_CODE:}
    security-key: ${DFM_MEMBER_API_SECURITY_KEY:}
    connect-timeout-seconds: 5
    read-timeout-seconds: 60
    duplicate-in-progress-codes: ${DFM_MEMBER_API_DUPLICATE_IN_PROGRESS_CODES:}
```

- No real URL, member code or key is committed. Empty values give `500 DFN012` at call time; startup
  does not fail, so other endpoints are unaffected.
- `application-test.yml` uses obviously-fake values (`https://dfm.test`, `TEST01`, `test-key`), and
  tests stub the `ExchangeFunction` (`FinouxClientTest` precedent).
- Key Vault / k8s env wiring for the three `DFM_MEMBER_API_*` secrets is a deployment prerequisite
  outside this repo's edit scope.

**Transport.** `base-url` must start with `https://`, otherwise `DFN012`. Trust-store pinning (design
doc §3.4) is JVM/infra work and out of scope.

**Masking.** Add `company.logging.masking.sensitive-keys` to all five `onboarding-service` profile
files. Restate the 15 starter defaults (`password, token, authorization, accessToken, refreshToken,
jwt, aadhaar, pan, ssn, creditCard, cvv, apiKey, secret, session, cookie`) and add:

| Key | What it covers |
|---|---|
| `securitykey` | Outbound header and body |
| `nin` | `nin`, `cust_nin` |
| `eidNumber`, `eid_no` | Emirates ID number |
| `passportNumber`, `pp_no` | Passport number |
| `iban` | `aedIban`, `usdIban`, `correspondentIban`, `pay_*_iban`; also the existing `/iban/validate` `IBAN` key |
| `foreignAccount` | Foreign account |
| `dateOfBirth`, `cust_dob` | Date of birth |
| `image`, `attachment`, `proofDocument`, `guardianship` | Base64 images and their DFM equivalents |

This single list governs request/response log lines, `api_audit_log` bodies
(`SensitiveDataMasker.maskBody` → `LogMaskingUtil`) and outbound span tags (`WebClientLoggingFilter`).
No starter change is needed. Matching is by substring, so a few unrelated keys containing these
substrings (e.g. `imageType`, `*warning*`) are also masked. That over-masking is accepted.

**Remaining exposure, accepted by precedent and flagged for `04_security`.** `maskBody` truncates
before masking, so a Base64 value that straddles the 5000-char cut stays partially unmasked. The
existing `/dfm/onboarding` endpoint has the same exposure. The schema property order in §3.1
minimises this.

**What is never logged.**
- `DfmMemberApiClient` and the service carry no `@Loggable`.
- The controller uses `@Loggable(logArgs = false)`; the inbound body is already captured, masked, by
  the request filter.
- The client logs only: HTTP status, DFM `response_code`, `response_msg`, `request_id`, and duration.

**Error leakage.** The handler returns only the envelope, with fixed texts for 500/502/503/504. DFM's
`response_msg` is returned only for DFN006/DFN007, where it is the caller-actionable message (design
doc §4.3). This eliminates F-01.

**Pre-existing issues, not fixed here, for `04_security`:**
- `AbstractRestClient` forwards the inbound JWT to every downstream (ETrade, IBAN, VeriPhone, Finoux).
- `ApiAuditAspect` stores POJO arguments of `com.alramz.client..*` methods unmasked.

---

## 6. Edge Cases & Error Behaviors

**Exception types.** These are new in `com.alramz.exception` unless marked "existing". All extend
`RuntimeException` and carry no HTTP status.

| Type | Carries |
|---|---|
| `ApplicationException(field, message, code)` (existing, reused) | DFN001–005 input failures |
| `DfmRejectedException(DfmInvestorErrorCode code, String message)` | DFN006 / DFN007 |
| `DfmUnavailableException(DfmInvestorErrorCode code, String message, Throwable cause)` | DFN010 / DFN011 / DFN014 |
| `DfmConfigurationException(String message)` | DFN012 |
| `DfmInvestorErrorCode` enum | `DFN001…DFN007, DFN010…DFN014`, each with a code string and field name (utility `ErrorCode.forField` pattern) |

**Handler.** `DfmInvestorExceptionHandler` is
`@RestControllerAdvice(assignableTypes = DfmInvestorController.class) @Order(-1)`. Its lower order
means it wins over `GlobalExceptionHandler`'s `@Order(0)` for this controller only, so the existing
handler's file stays untouched (exception-handling.md §4 Step 4). Every body is built with
`MDCUtil.getCorrelationIdAsUuid()`.

| # | Failure mode | Raised as | HTTP / code |
|---|---|---|---|
| E1 | Malformed JSON, wrong type, bad enum/date/UUID (`HttpMessageNotReadableException`) | framework | 400 DFN003 `Malformed or invalid JSON request` |
| E2 | Bean Validation failure (`MethodArgumentNotValidException`) | framework | 400; code by field-path precedence (§3.2) |
| E3 | Cross-field rule R1–R8, R9b, R10 | `ApplicationException(field, msg, "DFN003")` | 400 DFN003 |
| E4 | `address.country` not ISO (R9) | `ApplicationException("address.country", …, "DFN001")` | 400 DFN001 |
| E5 | Config blank or non-https | `DfmConfigurationException` (checked before any outbound call) | 500 DFN012; logged at ERROR, without the values |
| E6 | DFM business rejection | `DfmRejectedException(DFN006)` | 400 DFN006 + DFM message |
| E7 | DFM duplicate still in progress | `DfmRejectedException(DFN007)` | 409 DFN007 |
| E8 | DFM 5xx / 401 / 403 / unparseable / unexpected code | `DfmUnavailableException(DFN010)` | 502 DFN010 |
| E9 | Connect failure (after 1 retry) / read timeout | `DfmUnavailableException(DFN011)` | 504 DFN011 |
| E10 | Circuit open | `DfmUnavailableException(DFN014)` | 503 DFN014 |
| E11 | Missing role | `AccessDeniedException` (starter) | 403, errorCode null, `Access denied`. An explicit handler is **required**, otherwise the catch-all turns it into a 500. |
| E12 | Anything else | `Exception` catch-all | 500 DFN013 `Internal Server Error`; logged with stack trace, never echoed |
| E13 | Missing or invalid JWT | starter `JwtAuthFilter` | 401 (starter body) |
| E14 | Timeout where DFM actually completed | E9 | The caller retries with the same `requestId`, and DFM returns the earlier result → 201 (DFM idempotency, design doc §4.2.1) |
| E15 | DFM success with non-UUID references | Success | 201, value passed through, WARN logged |
| E16 | `X-Correlation-Id` not a UUID | Starter `MDCUtil` | A fresh UUID is used; never a 500 |

**Status coverage.** Each status in §3.2 maps to at least one edge case:

| Status | Edge cases |
|---|---|
| 201 | E14, E15 |
| 400 | E1–E4, E6 |
| 401 | E13 |
| 403 | E11 |
| 409 | E7 |
| 500 | E5, E12 |
| 502 | E8 |
| 503 | E10 |
| 504 | E9 |

---

## 7. Open Questions & Decisions

No blocking gap was raised; `AskUserQuestion` was not used. Every design doc Appendix E item was
resolved by the request itself, a repo precedent, or a documented default:

| E # | Item | Resolution (non-blocking) |
|---|---|---|
| 1 | Path, 201, exact-NIN-match → 200? | Request: `POST /dfm/investors`, always **201**. There is no reliable DFM signal for a match; `dfmMessage` carries DFM's text. |
| 2 | 409/502/504 + DFM duplicate code | Adopted, plus 503 DFN014 for an open circuit (repo precedent: `ExternalSystemException` → 503). 409 fires on HTTP 409 or a configured `duplicate-in-progress-codes` value (empty until DFM confirms). |
| 3 | Callers / gateway | Operational. No legacy shim, per the mmfintegrations Q1 precedent. |
| 4 | Security key header vs body; EID format; value sets; "Potential NIN match" | Keep the legacy header **and** body. EID is sent dashed, as validated. `Y`/`N` booleans follow `OnboardingServiceImpl` precedent. `pay_method` follows design doc §6. `guardian.type` is free text. "Potential match" is surfaced via `dfmMessage`. |
| 5 | Verify F-01 in UAT | Moot for the target: the envelope never carries errors or pipeline. |
| 6 | Which field SEQ logs | Moot: replaced by starter logging + masking (§5). |
| 7 | Inline Base64 vs DMS | Request contract: inline Base64, with size and type limits. |
| 8 | Key rotation, SEQ_LOG retention | Operational, out of scope. |

---

## 8. Explicit Acceptance Criteria

**Contract**
- [ ] `services/onboarding-service/specs/apiSpecs.yaml` contains `POST /dfm/investors` with tag
  `DFM Investors` and `operationId: registerDfmInvestor`.
- [ ] Its request schema `DfmInvestorRegistrationRequest` and nested schemas match §3.1, in the
  property order stated there.
- [ ] It has responses `201/400/401/403/409/500/502/503/504`, with a `GenericResponse` example for
  every status except 401/403.
- [ ] No other path is modified.
- [ ] `GenericResponse` in that file has nullable `errorCode` and `errorMsg`.
- [ ] `mvn -pl services/onboarding-service -am test-compile` passes. The generated
  `com.alramz.api.DfmInvestorsApi` is implemented by `DfmInvestorController` (`@RequestMapping("/api/v1")`).

**Security**
- [ ] `registerDfmInvestor` carries `@JwtSecured(roles = "APP_DFM_ONBOARDING")`.
- [ ] A `@WebMvcTest` with a token lacking that role gets **403**, with `errorCode` null.
- [ ] `/api/v1/dfm/investors` is absent from every `permit-all-urls`.

**Success path**
- [ ] A valid request with a stubbed DFM response (HTTP 200, `response_code` `"200"`) returns HTTP
  201, `responseCode` `"201"`, the 7 `response` fields mapped per §3.2, and a non-null `correlationId`.

**Outbound payload** (asserted on the captured outbound request):
- [ ] The JSON has snake_case keys per §3.3.
- [ ] `cust_mobile` is `00971…` for input `+971…`.
- [ ] `portfolio_options` is `"cash,margin"` for `[MARGIN, CASH]`.
- [ ] `pay_method` is `bank` for `BANK`.
- [ ] `fatca_uscitizen` is `N` for `false`.
- [ ] `adr_address` is not truncated.
- [ ] `request_mbrcode` and `securitykey` come from config.
- [ ] The `securitykey` header is present.
- [ ] There is **no** `Authorization` header.
- [ ] Null fields are omitted.

**Validation**
- [ ] Each of DFN001, DFN002, DFN004 and DFN005 is produced by a dedicated test input.
- [ ] DFN003 is produced for each of R1–R8, R10 and malformed JSON.
- [ ] Each of these responses is HTTP 400 with the matching `errorCode`.

**DFM outcomes**
- [ ] DFM HTTP 400 (`response_msg` X) → 400 DFN006 with `errorMsg` X.
- [ ] DFM HTTP 409 → 409 DFN007.
- [ ] HTTP 2xx with a `response_code` listed in `duplicate-in-progress-codes` → 409 DFN007.
- [ ] HTTP 5xx → 502 DFN010.
- [ ] HTTP 401 → 502 DFN010.
- [ ] Non-JSON 200 body → 502 DFN010.
- [ ] Read timeout → 504 DFN011.
- [ ] Connect failure → exactly **2** attempts, then 504 DFN011.
- [ ] A DFM 5xx is attempted exactly **once**.

**Resilience and configuration**
- [ ] After 5 consecutive technical failures, the 6th call returns 503 DFN014 without an outbound
  request.
- [ ] DFM business rejections (4xx) do not count toward opening the breaker.
- [ ] Blank `security-key` (or `base-url`/`member-code`, or a non-https base-url) → 500 DFN012, with
  no outbound request.
- [ ] Any 500/502/503/504 body contains no stack trace, exception class, DFM body or request data.

**Masking**
- [ ] `company.logging.masking.sensitive-keys` in all five profile files contains the 15 defaults
  plus the §5 additions.
- [ ] A unit test on `LogMaskingUtil.mask` (configured with that list) shows that `securitykey`,
  `eidNumber`, `passportNumber`, `nin`, `aedIban`, `dateOfBirth` and `frontImage` values are masked.

**Package and logging placement**
- [ ] `DfmMemberApiClient` lives in `com.alramz.dfm` (not `com.alramz.client`) and has no `@Loggable`.
- [ ] The controller method uses `@Loggable(logArgs = false)`.

**Untouched files**
- [ ] `GlobalExceptionHandler.java`, `OnboardingController.java`, and all `db/changelog/**` files are
  unchanged.
- [ ] `/dfm/onboarding`, `/iban/validate`, `/veriphone/verify` and `/existing-data/validation` tests
  still pass (`mvn -pl services/onboarding-service -am test`).

**Config and collection**
- [ ] The `adapter.dfm-member-api` block from §5 is present in
  `application-{dev,docker,test,preprod,prod}.yml`, with no real URL or secret value.
- [ ] `services/onboarding-service/apiCollection.http` has:
  - a `Register DFM investor` block with `Authorization: Bearer {{authToken}}` and an obviously-fake
    body;
  - a `-ve test case` block (missing `address.country`);
  - a register block whose `roles` include `APP_DFM_ONBOARDING`.

---

## 9. Self-Critique

**Confidence: medium.** The repo grounding is solid: host service, envelope, client, resilience,
config and masking all have concrete precedents. Confidence is not high because several DFM-side
facts are unconfirmed and are encoded as defaults that only a live DFM UAT call can validate.

**Residual non-blocking assumptions.** Each is cheap to change in one place: the mapper or the
config.

| # | Assumption | Fallback if wrong |
|---|---|---|
| 1 | DFM boolean value sets are `Y`/`N` | DFM rejects → DFN006 |
| 2 | `pay_method` values are `iVESTOR`/`bank` | DFM rejects → DFN006 |
| 3 | EID is sent dashed | DFM rejects → DFN006 |
| 4 | The security key is accepted in header + body | — |
| 5 | DFM's "duplicate in progress" arrives as HTTP 409 or a code yet to be configured | Until then it surfaces as 400 DFN006, not 409 |
| 6 | `guardian.type` is free text ≤ 50, and the CRS/TIN lengths are invented caps | — |
| 7 | Arab League list for R3 and the age-18 threshold for R8 | — |
| 8 | Field renames `eidNumber`/`passportNumber` (deviation from the design doc, for maskability) | — |
| 9 | 503 DFN014 for an open circuit (addition to the design doc) | — |
| 10 | Connect-timeout wiring depends on which `ClientHttpConnector` is on the classpath | 03_implement must verify |

**Sizing question (not changed here).** `service.yaml` gives a 768 MiB heap. Each in-flight request
can hold about 7 MB of Base64 several times over (cached body, DTO, decoded copies for R10, outbound
JSON), roughly 30–40 MB. Someone should confirm the expected concurrency, or the memory/heap sizing
should be revisited. There is also no explicit request-size cap: Jackson's default 20 M-char string
limit plus the per-field `maxLength` apply only after the full body is read. The gateway/ingress body
limit is the real guard, and `04_security` should confirm it.

**Weakest part.** The PII-in-logs story relies on substring key masking, which is applied **after**
5000-char truncation. A partially unmasked Base64 fragment can still reach `api_audit_log`, and email
and mobile are logged in clear. That matches the existing `/dfm/onboarding` endpoint and the starter's
design, but it does not fully meet design doc §3.4 "no images". A complete fix (mask-then-truncate in
`SensitiveDataMasker.maskBody`, or excluding this path from payload audit) is a starter or
logging-policy decision for `04_security` / the starter owner.
