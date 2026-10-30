# DFMIntegrations — NINOnboarding — API Design & Migration Specification

_webMethods Integration Server 10.7 → Java 21 / Spring Boot 3 · Al Ramz Middleware Migration · DFM Integration_

| Field | Value |
|---|---|
| Document | DFM-NINOnboarding-API-Design-Document |
| Version | 0.1 — first draft for review |
| Date | 19 October 2026 |
| Source | IS package export `DFMIntegrations.zip` — only `DFMIntegrations.v2.services:NINOnboarding` (other services covered in an earlier document); Confluence space INTEGRATIO |
| Consumers | Internal middleware callers (service catalog); gateway route DFM 1.0 /NINCreation (Confluence) |
| Status | Draft — proposals in this document are not yet agreed (see Appendix E) |

## 1. Overview

### 1.1 Purpose

This document reverse-engineers the webMethods Integration Server 10.7 Flow service **`DFMIntegrations.v2.services:NINOnboarding`** into a migration-ready API specification for Java 21 / Spring Boot 3. The service registers an investor with the Dubai Financial Market (DFM): it forwards the client’s identity, address, dividend-payment and tax data to DFM’s Member API, which creates (or finds) the investor’s National Investor Number (NIN) and opens cash and, optionally, margin trading accounts on DFM’s Equator system. Everything below is sourced from the service’s `flow.xml`, its `node.ndf` signature, the package manifest and the Confluence INTEGRATIO pages.

### 1.2 Scope

**In scope**

- Flow service `DFMIntegrations.v2.services:NINOnboarding` (and its `.bak` copy for change history).
- Its outbound call to the DFM Member API `POST {NIN_DFM_BASE_URL}/memberapis/onboarding` (Chapter 5).
- The configuration it reads (static data, global variables) and the logging it performs.

**Out of scope** (covered by an earlier document, as requested)

- `v2.services:onboarding` and `v2.services:accountUpdate` (the DFM-initiated onboarding journey, REST resource `DFMIntegrations.restAPIs:DFM`).
- `v2.services:processRequestsPendingAccountCreation`, trigger `v2.trigger:processRequestsPendingBackofficeCreation`, the six `v2.adapters:*` JDBC services and doc type `docs:NINOnboardingRequest`.
- The callers of NINOnboarding in other packages, and `SEQDatalust` / `commonUtility` internals.

### 1.3 Executive Summary

NINOnboarding is a thin, synchronous pass-through. It normalises the mobile number, checks only that the address country and city are present, truncates the address, adds the broker member code and a security key, POSTs the request to DFM, and copies DFM’s `response_code`, `response_msg`, references, NIN and trading-account numbers back to the caller. It has no database access of its own. The service catalog lists its consumer as “Middleware — internal call”; Confluence also documents a gateway URL `/gateway/DFM/1.0/NINCreation`. The IS REST resource in this export does not map the service, so it is reached as an internal IS service (and possibly via the gateway’s invoke route — open item).

> [!CAUTION]
> **Key migration notes**
>
> - **Secrets and PII can be returned to the caller:** the service preserves `lastError` in its output. On any exception after the DFM request is built (e.g. DFM unreachable, non-JSON reply), `lastError.pipeline` contains the full request body and headers with the real DFM security key, the Emirates ID / passport / signature images and all personal data (F-01, verify).
> - **Validation errors come back as 500:** the two address checks set `1069` and EXIT, but the CATCH unconditionally overwrites the code with `500 / Internal Server Error`; the `1069` branch in the CATCH is unreachable (F-02).
> - **Silent data changes:** mobile numbers that do not start with `00` get `00` prepended (`+971…` → `00+971…`, `050…` → `00050…`); addresses are cut to 39 characters, although the comment and DFM allow 40 (F-03, F-04).
> - **DFM outcome passed through verbatim:** the HTTP status from DFM is never checked; DFM’s own `response_code` becomes the middleware `responseCode` (F-05).
> - **No timeout** on the DFM call; retries are safe only because DFM de-duplicates on `request_id` (F-06).
> - **Credential on Confluence:** the NINOnboarding page’s sample request contains a `securitykey` value; it is not reproduced here and should be rotated if it is a real key (F-07).

#### 1.3.1 Source Artefacts and References

| Artefact | Detail |
|---|---|
| Package export | `DFMIntegrations.zip` — manifest created 2023-07-20, last patch 2026-07-14 14:57 GST (description `StaticData_`) |
| Flow service | `v2.services:NINOnboarding` — `flow.xml` (2.2 MB, 320 summarised steps) and `flow.xml.bak` |
| `.bak` diff | The current version removes `cust_signatureimage`, `eid_attachment_front`, `eid_attachment_back`, `pp_attachment` and `proof_of_guardianship` from the logged `requestBody` — consistent with Release v72.0 (2026-07-15), which started persisting logs to `middleware.SEQ_LOG` (`logToDatabase = Y`). |
| Confluence (INTEGRATIO) | Page “NINOnboarding” (2794913836): request/response parameters, DFM codes; “AL RAMZ FULL Service Catalog” (2783510538): row “ninCreation”; “Release v72.0 – Deployment Plan” (4013752349) |
| Error-code registry | Confluence “REST API response codes” (2795896833): `1069` = “Validation List Failed”, `1091` = “Middleware system validation error”, `500` = Internal Server Error. |
| Catalog inconsistency | The catalog row “ninCreation” points to `v2.services:onboarding`, while the “onboarding/accountUpdate” row points to `v2.services:NINOnboarding` — the two flow paths appear swapped (F-10). |

## 2. Solution Architecture

### 2.1 As-Built Legacy Architecture

![NINOnboarding as-built view](images/01_asbuilt.png)
_Figure 2-1 — NINOnboarding as-built view (image not included in this repo copy)_

| Colour | Meaning |
|---|---|
| Light blue | Callers |
| Green | Flow service |
| Amber | External system (DFM) |
| Grey | Shared IS services and configuration |

### 2.2 End-to-End Journey

The service has a single downstream system (DFM’s Member API); a separate journey diagram would repeat Figure 2-1, so none is drawn. In the wider onboarding journey this call sits between the client-facing onboarding steps (handled by the calling package) and the DFM onboarding bot / Fetcher processing that later records the trading numbers in the back office (see the AlgoIntegrations document, Fetcher job).

### 2.3 Target Architecture (Spring Boot)

In the target design the service becomes the DFM module of the OnBoarding Spring Boot service (in API-led terms, a **DFM System API**): a controller with Bean Validation, a mapping service and a `RestClient`-based DFM client with timeouts and a circuit breaker. The DFM security key moves from an IS global variable to a secrets store; member code and base URL become configuration properties.

![Target component view](images/03_target.png)
_Figure 2-2 — Target component view (image not included in this repo copy)_

### 2.4 Target Response & Result Model

#### 2.4.1 Response Envelope

| Field | Type | Description |
|---|---|---|
| `responseCode` / `responseMessage` | String | HTTP status and reason phrase (always present).<br>_Legacy vs. target field format:_ legacy `responseCode` / `responseMessage` carry DFM’s own `response_code` / `response_msg` on every DFM reply, or `500` / `Internal Server Error`. |
| `errorCode` / `errorMsg` | String, nullable | Service code `DFNnnn` and caller-actionable message — client-input errors only. |
| `correlationId` | String (UUID) | From `X-Correlation-Id`, else generated.<br>_Legacy vs. target field format:_ legacy `correlationID` body field, generated with `commonUtility.java:GenerateGUID` when blank. |
| `response` | Object, nullable | Registration result (4.5). |

#### 2.4.2 Internal Call — `OperationResult<T>`

The DFM client returns a sealed result instead of exceptions and raw maps:

```java
public sealed interface OperationResult<T> {
  record Success<T>(T value) implements OperationResult<T> {}
  record BusinessFailure<T>(String providerCode, String providerMessage) implements OperationResult<T> {}  // DFM 4xx
  record TechnicalFailure<T>(String code, Throwable cause, boolean retryable) implements OperationResult<T> {} // timeout, 5xx, bad body
}
```

#### 2.4.3 Service Error Codes (prefix DFN)

Prefix **DFN** (confirmed) — distinct from codes used in the earlier DFM onboarding document. `DFN001–009` client input / DFM business rejections, `DFN010–019` backend and provider errors.

## 3. Prerequisites & Static Configuration

### 3.1 Integration Server Global Variables

| Variable | Use | Target |
|---|---|---|
| `DFMsecuritykey` | DFM shared secret, sent **twice**: header `securitykey` and body field `securitykey`. Masked as `**********` in the logged copies. | Secrets store (Vault / Azure Key Vault), injected into the DFM client; sent only where DFM requires it (confirm header vs body). |
| `environment` | Read in the FINALLY block; branches `PROD`, `UAT`, default all do nothing. A disabled `UAT` branch would return a hard-coded fake success. | Not needed; test stubs belong in a test profile / WireMock. |

### 3.2 Static / Feature-Flag Configuration

| Key (application MIDDLEWARE) | Meaning | Target property |
|---|---|---|
| `NIN_DFM_BASE_URL` | Base URL of the DFM Member API; the path `/memberapis/onboarding` is appended in the Flow. | `dfm.member-api.base-url` |
| `NIN_DFM_REQUEST_MBRCODE` | Broker member code sent as `request_mbrcode`. | `dfm.member-api.member-code` |

Read through `commonUtility.v2.services:getStaticData` and turned into a map with `pub.document:documentListToDocument`. A missing key produces an empty URL or member code — the call then fails with an exception and the generic `500`.

### 3.3 Upstream / Downstream Dependencies

| Dependency | Kind | Health check (target) |
|---|---|---|
| DFM Member API (`/memberapis/onboarding`) | HTTPS JSON, shared-secret header | Circuit-breaker state only; no synthetic onboarding call |
| Static data (`commonUtility.v2`) | IS service | Replaced by configuration properties |
| Seq / `middleware.SEQ_LOG` | Log sink | Not a health dependency |
| Callers (other middleware packages) | Internal IS invoke | n/a — they call this service |

### 3.4 Security Notes

| Aspect | Legacy (as exported) | Target |
|---|---|---|
| Service ACL | `check_internal_acls = no`; manifest `listACL` null. Exposure is limited only by the service not being mapped in the package’s REST resource; the gateway URL on Confluence suggests an invoke route — confirm. | Internal-only endpoint behind the gateway; OAuth2 client credentials with a scope such as `dfm.onboarding.write`. |
| DFM credential | Global variable `DFMsecuritykey`, header and body; a value appears in the Confluence sample request. | Secret store; rotate the key exposed on Confluence; never logged. |
| Error leakage | `lastError` (with `pipeline`) is preserved in the output (F-01). | GlobalExceptionHandler returns the envelope only. |
| Personal data in logs | Full request (minus images since the latest change, key masked) and DFM response are written to Seq and `middleware.SEQ_LOG` CLOBs: NIN, Emirates ID number, passport number, date of birth, IBANs, mobile, e-mail. `requestBodyToLog`, which still contains the images, is also left in the pipeline. | Field-level masking (EID, passport, IBAN, NIN partially masked), no images, retention policy for the log table. |
| Transport | DFM base URL from static data; scheme not visible; no timeout. | HTTPS enforced, trust store pinned to DFM, connect/read timeouts. |

## 4. API — DFM Investor Onboarding (NINOnboarding)

### 4.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | IS service `DFMIntegrations.v2.services:NINOnboarding` (internal invoke; not in REST resource `DFMIntegrations.restAPIs:DFM`) |
| Gateway URL (Confluence) | `https://api-uat.alramz.ae/gateway/DFM/1.0/NINCreation` (POST) |
| Target operation | POST `/api/v1/dfm/investors` |
| Success status | 201 Created |
| Consumer (catalog) | Middleware — internal call |
| Downstream | DFM Member API `POST {NIN_DFM_BASE_URL}/memberapis/onboarding` |

> [!IMPORTANT]
> **Legacy vs. target endpoint**
>
> Legacy gateway path `/NINCreation` (verb-like) → target `POST /api/v1/dfm/investors`: the call registers an investor at DFM (NIN plus trading accounts). **201 Created** is proposed for a successful registration. Judgment call: when DFM reports an exact NIN match for an existing investor nothing new may be created; returning 200 in that case would need a reliable signal from DFM (today only the free-text message says “Exact NIN match”) — open item. `requestId` keeps DFM’s idempotency semantics, so a retried POST with the same id is safe.

### 4.2 Request Schema

Legacy fields are flat snake_case strings; the target groups them. Lengths are DFM’s limits from Confluence. Every legacy field is declared `string` in `node.ndf` and nothing except address country and city is validated by the Flow.

#### 4.2.1 Request Identity

| Field (legacy) | Target field · type | Required | Description |
|---|---|---|---|
| `request_id` | `requestId` · UUID | Yes | Unique per registration; DFM returns the earlier result for a repeated, completed id and rejects a repeated, unfinished one.<br>_Legacy vs. target field format:_ legacy free string (≤ 100); DFM answers `400 — Request id should be a valid GUID` otherwise. Target validates UUID up front (DFN003). |
| `correlationID` | header `X-Correlation-Id` · UUID | No | Tracing id. |

#### 4.2.2 Personal Details and Contact

| Field (legacy) | Target field · type | Required | Description |
|---|---|---|---|
| `cust_firstname`, `cust_lastname` | `firstName`, `lastName` · String ≤ 40 | Yes | Names as on ID. |
| `cust_engfullname`, `cust_arfullname` | `fullNameEn`, `fullNameAr` · String ≤ 40 | En yes; Ar for Arab nationals | Full names. |
| `cust_dob` | `dateOfBirth` · LocalDate (ISO 8601) | Yes | As on Emirates ID / passport.<br>_Legacy vs. target field format:_ legacy string; DFM already expects `YYYY-MM-DD`, so the wire format does not change — only the type. |
| `cust_gender` | `gender` · enum {M, F} | Yes | As on ID. |
| `cust_nationality` | `nationality` · ISO 3166-1 alpha-2 | Yes | Two-letter country code. |
| `cust_familyid` | `familyId` · String ≤ 15 | UAE nationals (optional) | Family book id. |
| `cust_nin` | `nin` · String ≤ 15 | No | Known NIN (existing investor); empty for new investors. |
| `cust_mobile` | `mobile` · String (E.164, e.g. `+971501234567`) | Yes | Investor mobile; converted to DFM’s `00971…` form by the client.<br>_Legacy vs. target field format:_ legacy: if the first two characters are not `00`, `00` is prepended — `+971…` becomes `00+971…`, `0501234567` becomes `000501234567` (F-03). Target accepts E.164 only and converts `+` → `00`. |
| `cust_email` | `email` · String (e-mail) ≤ 80 | Yes | Investor e-mail. |

#### 4.2.3 Identity Documents and Signature

| Field (legacy) | Target field · type | Required | Description |
|---|---|---|---|
| `eid_no`, `eid_expirydate` | `emiratesId.number` · `^784-\d{4}-\d{7}-\d$`; `emiratesId.expiryDate` · LocalDate | UAE nationals / residents | Emirates ID.<br>_Legacy vs. target field format:_ legacy strings; Confluence examples show the number with and without dashes — normalise to one form before sending (open item: which form DFM expects). |
| `eid_attachment_front`, `eid_attachment_back` | `emiratesId.frontImage`, `backImage` · Base64 (≤ 2 MB each) | UAE nationals / residents | ID images. |
| `pp_no`, `pp_expirydate` | `passport.number` ≤ 15; `passport.expiryDate` · LocalDate | International investors | Passport. |
| `pp_attachment`, `pp_attachment_type` | `passport.image` · Base64 ≤ 2 MB; `passport.imageType` · enum {jpg, jpeg, png, pdf} | International investors | Passport image and type. |
| `cust_signatureimage`, `cust_signatureimage_type` | `signature.image` · Base64 ≤ 1 MB; `signature.imageType` · enum | Yes | Signature specimen.<br>_Legacy vs. target field format:_ legacy free-text file types (`jpg`, `png`); target restricts to an allow-list and checks the Base64 decodes to that type. |

#### 4.2.4 Address

| Field (legacy) | Target field · type | Required | Description |
|---|---|---|---|
| `adr_country` | `address.country` · ISO 3166-1 alpha-2 | Yes | Address country.<br>_Legacy vs. target field format:_ legacy: only non-blank is checked; intended error `1069 — Address country is incorrect or missing.` is overwritten by the CATCH (F-02). |
| `adr_city` | `address.city` · String ≤ 25 | Yes | City.<br>_Legacy vs. target field format:_ same check and defect as country (`Address city is incorrect or missing.`). |
| `adr_address` | `address.line` · String ≤ 40 | Non-PO-box addresses | Street address.<br>_Legacy vs. target field format:_ legacy `substring(0, 39)` keeps **39** characters (comment says 40) and silently drops the rest (F-04). Target rejects > 40 (DFN005) instead of truncating. |
| `adr_pobox`, `adr_zipcode` | `address.poBox` ≤ 10, `address.zipCode` ≤ 10 | PO box for UAE addresses | PO box / postcode. |
| `adr_phone` | `address.phone` · E.164 | No | Landline. |

#### 4.2.5 Dividend Payment

| Field (legacy) | Target field · type | Required | Description |
|---|---|---|---|
| `pay_method` | `dividendPayment.method` · enum {IVESTOR, BANK} | Yes | Dividend method.<br>_Legacy vs. target field format:_ legacy free text (`bank` in the Confluence sample, “iVESTOR / Bank” in the table); target enum, mapped to DFM’s spelling. |
| `pay_aed_iban`, `pay_usd_iban` | `aedIban`, `usdIban` · IBAN (≤ 23) | For BANK | UAE IBANs. |
| `pay_frn_iban`, `pay_frn_swift` | `foreignAccount` ≤ 34, `foreignSwift` · BIC (8/11) | Optional | Foreign account. |
| `pay_cor_iban`, `pay_cor_swift` | `correspondentIban`, `correspondentSwift` | Optional | Correspondent bank. |
| `pay_routecode` | `routeCode` · String ≤ 20 | US, GB, AU, NZ, IN banks | Routing code. |
| `pay_printname` | `ivestorPrintName` · String ≤ 26 | For IVESTOR | Name printed on the iVESTOR card. |

#### 4.2.6 Portfolio, Tax and Guardianship

| Field (legacy) | Target field · type | Required | Description |
|---|---|---|---|
| `portfolio_options` | `portfolioOptions` · List<enum {CASH, MARGIN}> | Yes | Accounts to open; MARGIN implies CASH.<br>_Legacy vs. target field format:_ legacy comma-separated string (`"cash"`, `"cash,margin"`) — a multi-value field packed into one string; target JSON array, serialised back to DFM’s comma format by the client. |
| `csr_resident` | `crs.resident` · Boolean | Confirm | CRS residency declaration.<br>_Legacy vs. target field format:_ not on the Confluence page (added later); legacy value set unknown (open item). |
| `csr.csr_countries[]` (`csr_country`, `csr_havetin`, `csr_tin`, `csr_notinreason`, `csr_whynotinissued`) | `crs.countries[]` {country ISO 3166-1, hasTin Boolean, tin, noTinReason, whyNoTinIssued} | When applicable | CRS tax residencies. |
| `fatca_uscitizen`, `fatca_tin` | `fatca.usPerson` · Boolean; `fatca.tin` | Confirm | FATCA declaration. |
| `guardian_name`, `guardianship_type`, `proof_of_guardianship` | `guardian.name`, `guardian.type` (enum, confirm), `guardian.proofDocument` · Base64 | Minors | Guardian details. |

### 4.3 Business Logic Summary

1. Record `requestTimestamp` (GMT+4); serialise the incoming fields into `requestBodyToLog` (images included); generate `correlationID` if blank.
2. Mobile: if `cust_mobile` is not blank and its first two characters are not `00`, prepend `00`.
3. `adr_country` blank → set `1069 / Address country is incorrect or missing.` and EXIT FAILURE; same for `adr_city`.
4. `adr_address` not blank → keep characters 0–38.
5. Read `NIN_DFM_BASE_URL` and `NIN_DFM_REQUEST_MBRCODE` from static data.
6. Build `DFMRequestBody` = all request fields + `securitykey` (global variable) + `request_mbrcode`; build a masked `requestBody` for logging (key masked, images and proof of guardianship excluded); set headers `Content-Type: application/json` and `securitykey`.
7. `pub.client:http` POST `<base>/memberapis/onboarding` (no timeout); parse the body as JSON.
8. Map `response_code` → `responseCode`, `response_msg` → `responseMessage`, `member_reference`, `dfm_reference`, `tracking_no`, `cash_acc`, `margin_acc`, `cust_nin` → `response.*`. The HTTP status is not checked.
9. CATCH: restore logging fields from `lastError.pipeline`; if the error text starts with `1069` → `1069 / Middleware system validation error : …`, otherwise `500 / Internal Server Error` (always the case in practice, F-02).
10. FINALLY: `SEQDatalust.services:asynchronousIngestion` with `logToDatabase = Y`; read `environment` (all branches empty; a disabled UAT mock remains); clear the pipeline keeping `responseCode`, `responseMessage`, `response`, `correlationID`, `lastError`.

> [!NOTE]
> **Business-logic observations for the target design**
>
> - Residency rules documented by DFM (EID mandatory for UAE nationals/residents, passport for international investors, Arabic name for Arab nationals, PO box for UAE addresses) are not checked in the middleware; the target validates them up front so the caller gets a specific error rather than a DFM rejection.
> - DFM business rejections (its `400`, duplicate `request_id`) are client-input problems for the caller and map to 400 / 409 with DFM’s message in `errorMsg`; DFM `5xx`, unreadable bodies and timeouts are provider errors (502 / 504).
> - “Potential NIN match” in DFM’s `200` message (Confluence) may require manual follow-up; the target exposes DFM’s message as `response.dfmMessage` so the caller can act on it (open item: exact semantics).
> - Requests can reach ~7 MB of Base64 images. Alternative: accept DMS document ids and fetch the images inside the service — decision for the onboarding design.

### 4.4 Sample Request

```json
{
  "requestId": "3f6c2a1e-8b7d-4c5e-9a0f-1d2e3c4b5a69",
  "firstName": "Sara",
  "lastName": "Ahmed",
  "fullNameEn": "Sara Ahmed",
  "fullNameAr": "سارة أحمد",
  "dateOfBirth": "1990-06-12",
  "gender": "F",
  "nationality": "EG",
  "mobile": "+971500000000",
  "email": "investor@example.com",
  "passport": { "number": "A1234567", "expiryDate": "2030-06-12", "image": "<base64>", "imageType": "png" },
  "emiratesId": { "number": "784-1990-1234567-1", "expiryDate": "2028-06-12", "frontImage": "<base64>", "backImage": "<base64>" },
  "signature": { "image": "<base64>", "imageType": "png" },
  "address": { "country": "AE", "city": "Abu Dhabi", "line": "Reem Island", "poBox": "12345" },
  "dividendPayment": { "method": "BANK", "aedIban": "AE070331234567890123456" },
  "portfolioOptions": ["CASH"],
  "crs": { "resident": true, "countries": [ { "country": "AE", "hasTin": false, "noTinReason": "B" } ] },
  "fatca": { "usPerson": false }
}
```

Legacy: flat snake_case JSON (`request_id`, `cust_mobile` `"00971500000000"`, `portfolio_options` `"cash"`, …) — see Chapter 6 for the full mapping.

### 4.5 Response Schema

| Field | Target Type | Description |
|---|---|---|
| envelope | — | See 2.4.1. |
| `response.nin` | String ≤ 15 | Supplied or newly created NIN. |
| `response.cashTradingNumber` | String ≤ 20 | Equator cash account (when requested). |
| `response.marginTradingNumber` | String ≤ 20, nullable | Equator margin account (when requested). |
| `response.memberReference` / `dfmReference` | UUID | Member-side and exchange-side references.<br>_Legacy vs. target field format:_ legacy strings (36 chars); target validates UUID format on read and logs if DFM deviates. |
| `response.trackingNumber` | String ≤ 12 | DFM eServices tracking number. |
| `response.dfmMessage` | String | New: DFM’s `response_msg` (e.g. NIN match information).<br>_Legacy vs. target field format:_ legacy puts DFM’s message into the envelope’s `responseMessage`. |

### 4.6 HTTP Status Code Reference

Legacy transport status: **HTTP 200** for every outcome (no `pub.flow:setResponseCode`). “Legacy Error Code” quotes the inner `responseCode — responseMessage` the service returns; values marked “DFM pass-through” are DFM’s own codes copied verbatim. The HTTP Status column is the **proposed target** status.

#### 4.6.1 Client Input Errors

| HTTP Status | Service Error Code | Description | Legacy Error Code |
|---|---|---|---|
| 400 Bad Request | DFN001 | `address.country` missing or not ISO 3166-1 alpha-2. | HTTP 200 · 500 — Internal Server Error (intended `1069 — Address country is incorrect or missing.`, overwritten by the CATCH, F-02) |
| 400 Bad Request | DFN002 | `address.city` missing or > 25 characters. | HTTP 200 · 500 — Internal Server Error (intended `1069 — Address city is incorrect or missing.`, overwritten) |
| 400 Bad Request | DFN003 | Mandatory field missing or malformed (UUID, dates, enums, IBAN, image size/type, residency rules). | — (not validated in the middleware; DFM decides) |
| 400 Bad Request | DFN004 | `mobile` not E.164. | — (silently prefixed with `00`, F-03) |
| 400 Bad Request | DFN005 | `address.line` longer than 40 characters. | — (silently truncated to 39, F-04) |
| 400 Bad Request | DFN006 | DFM rejected the request data. | HTTP 200 · 400 — `<DFM response_msg>` (DFM pass-through, e.g. “Request id should be a valid GUID.”) |
| 409 Conflict | DFN007 | `requestId` reused while DFM is still processing the first request. | HTTP 200 · `<DFM response_code>` — `<DFM response_msg>` (DFM pass-through; exact code not documented) |

#### 4.6.2 Backend / Provider Errors

| HTTP Status | Service Error Code | Description | Legacy Error Code |
|---|---|---|---|
| 201 Created | — | DFM accepted the registration (new or matched NIN). | HTTP 200 · 200 — `<DFM response_msg>` (DFM pass-through, e.g. “OK”, “Exact NIN match: …”) |
| 502 Bad Gateway | DFN010 | DFM answered 5xx or a body that is not valid JSON. | HTTP 200 · `<DFM response_code>` passed through; non-JSON body → 500 — Internal Server Error |
| 504 Gateway Timeout | DFN011 | DFM unreachable or no answer within the timeout. | HTTP 200 · 500 — Internal Server Error (plus `lastError` with pipeline, F-01) |
| 500 Internal Server Error | DFN012 | Configuration missing (base URL, member code, security key). | HTTP 200 · 500 — Internal Server Error |
| 500 Internal Server Error | DFN013 | Unexpected error. | HTTP 200 · 500 — Internal Server Error |

> [!WARNING]
> **Design decision — 409 / 502 / 504 outside the default status list**
>
> 409 separates a still-running duplicate from bad data; 502 / 504 let callers distinguish DFM faults from middleware faults (and decide whether to retry with the same `requestId`). Confluence also lists `1005 — Incorrect OTP` among this API’s codes; nothing in the Flow or the DFM call relates to an OTP, so it is treated as a documentation copy error (F-10) and not mapped.

### 4.7 Example Target Response Envelopes

Success:

```json
{
  "responseCode": "201",
  "responseMessage": "Created",
  "errorCode": null,
  "errorMsg": null,
  "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33",
  "response": {
    "nin": "EGA0000000",
    "cashTradingNumber": "9400000",
    "marginTradingNumber": null,
    "memberReference": "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d",
    "dfmReference": "1b2c3d4e-5f60-4718-89ab-cdef01234567",
    "trackingNumber": "2313258142",
    "dfmMessage": "OK"
  }
}
```

Client input error:

```json
{
  "responseCode": "400",
  "responseMessage": "Bad Request",
  "errorCode": "DFN001",
  "errorMsg": "address.country must be an ISO 3166-1 alpha-2 code",
  "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33"
}
```

Backend / provider error:

```json
{
  "responseCode": "504",
  "responseMessage": "Gateway Timeout",
  "errorCode": null,
  "errorMsg": null,
  "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33"
}
```

### 4.8 Target Process Flow (Spring Boot)

![DFM investor onboarding target flow](images/10_flow_nin.png)
_Figure 4-1 — DFM investor onboarding target flow (image not included in this repo copy)_

## 5. Downstream Integration — DFM Member Onboarding API

> [!IMPORTANT]
> **External provider API (called, not exposed)**
>
> Identity: `POST {NIN_DFM_BASE_URL}/memberapis/onboarding`, JSON, header `securitykey`, body adds `securitykey` and `request_mbrcode`.
> Request: the snake_case fields of 4.2 unchanged (after mobile / address adjustments).
> Response fields used: `response_code`, `response_msg`, `member_reference`, `dfm_reference`, `tracking_no`, `cash_acc`, `margin_acc`, `cust_nin`.

### 5.1 Target Client Contract

```
public interface DfmMemberApiClient {
  OperationResult<DfmOnboardingResult> onboard(DfmOnboardingPayload payload);   // payload carries request_id
}
// timeouts: connect 5 s, read 60 s (confirm DFM SLA); retry once on connect failure with the SAME request_id;
// circuit breaker opens after 5 consecutive technical failures; securitykey from the secret store.
```

### 5.2 Outcome Reference

| Outcome | Service Error Code | Condition | Legacy Behaviour |
|---|---|---|---|
| Success | — | HTTP 2xx and `response_code` = 200 | Codes and fields copied to the caller |
| BusinessFailure | DFN006 / DFN007 | HTTP 4xx or `response_code` 4xx | DFM code and message copied to the caller as is |
| TechnicalFailure (retryable) | DFN010 / DFN011 | HTTP 5xx, invalid JSON, timeout, connection error | Exception → 500 with `lastError`; no retry |

## 6. Data Mapping Reference

| Target API field | Legacy / DFM field | Transformation |
|---|---|---|
| requestId | request_id | UUID string |
| firstName / lastName | cust_firstname / cust_lastname | — |
| fullNameEn / fullNameAr | cust_engfullname / cust_arfullname | — |
| dateOfBirth | cust_dob | LocalDate → `yyyy-MM-dd` |
| gender / nationality | cust_gender / cust_nationality | enum / ISO alpha-2 |
| familyId / nin | cust_familyid / cust_nin | — |
| mobile | cust_mobile | E.164 `+971…` → `00971…` |
| email | cust_email | — |
| emiratesId.number / expiryDate / frontImage / backImage | eid_no / eid_expirydate / eid_attachment_front / eid_attachment_back | date → `yyyy-MM-dd` |
| passport.number / expiryDate / image / imageType | pp_no / pp_expirydate / pp_attachment / pp_attachment_type | date → `yyyy-MM-dd` |
| signature.image / imageType | cust_signatureimage / cust_signatureimage_type | — |
| address.country / city / line / poBox / zipCode / phone | adr_country / adr_city / adr_address / adr_pobox / adr_zipcode / adr_phone | phone E.164 → `00…`; no truncation |
| dividendPayment.method | pay_method | IVESTOR → `iVESTOR`, BANK → `bank` (confirm DFM spelling) |
| dividendPayment.aedIban / usdIban / foreignAccount / foreignSwift / correspondentIban / correspondentSwift / routeCode / ivestorPrintName | pay_aed_iban / pay_usd_iban / pay_frn_iban / pay_frn_swift / pay_cor_iban / pay_cor_swift / pay_routecode / pay_printname | — |
| portfolioOptions[] | portfolio_options | join lower-case with “,” |
| crs.resident / crs.countries[] | csr_resident / csr.csr_countries[] | Boolean → DFM value set (confirm) |
| fatca.usPerson / fatca.tin | fatca_uscitizen / fatca_tin | Boolean → DFM value set (confirm) |
| guardian.name / type / proofDocument | guardian_name / guardianship_type / proof_of_guardianship | — |
| (config) | request_mbrcode, securitykey | from `dfm.member-api.member-code` and the secret store |
| response.memberReference / dfmReference / trackingNumber | member_reference / dfm_reference / tracking_no | — |
| response.cashTradingNumber / marginTradingNumber / nin | cash_acc / margin_acc / cust_nin | — |
| response.dfmMessage | response_msg | — |

## Appendix A — Pseudocode

```
@PostMapping("/api/v1/dfm/investors")
ResponseEntity<Envelope<DfmInvestor>> register(@Valid @RequestBody NinOnboardingRequest req) {
  DfmOnboardingPayload p = mapper.toDfm(req, props.memberCode());   // E.164 -> 00…, enums -> DFM values, lists -> csv
  return switch (dfm.onboard(p)) {
    case Success<DfmOnboardingResult> s ->
        created(s.value());                                           // 201
    case BusinessFailure<?> b when b.isDuplicateInProgress() ->
        error(409, "DFN007", b.providerMessage());
    case BusinessFailure<?> b ->
        error(400, "DFN006", b.providerMessage());
    case TechnicalFailure<?> t when t.isTimeout() ->
        providerError(504, "DFN011", t);                                // logged, not returned
    case TechnicalFailure<?> t ->
        providerError(502, "DFN010", t);
  };
}
```

## Appendix B — Glossary

| Term | Meaning |
|---|---|
| DFM | Dubai Financial Market. |
| NIN | National Investor Number issued by the exchange / depository. |
| Equator | DFM / DCSD system where trading accounts are created. |
| Member code | Broker identifier sent as `request_mbrcode`. |
| iVESTOR | DFM prepaid card used for dividend payments. |
| CRS / FATCA | Common Reporting Standard and US Foreign Account Tax Compliance Act declarations. |
| EID | Emirates ID. |

## Appendix C — DDL & Type Mapping

Not applicable: NINOnboarding reads and writes no tables. Its logs land in `middleware.SEQ_LOG` (created by Release v72.0 and owned by SEQDatalust), whose `REQUEST_JSON` / `RESPONSE_JSON` CLOBs now hold this service’s personal data — a retention and masking concern recorded as F-08, not a schema for this service. Type decisions for the API fields are in 4.2 and Chapter 6.

## Appendix D — Findings

| ID | Severity | Finding |
|---|---|---|
| F-01 | Critical (verify) | `lastError` is preserved in the service output; on any exception after the request is built, `lastError.pipeline` returns the DFM security key, full request (incl. Base64 ID images) and headers to the caller. |
| F-02 | Medium | Address-country / city validation sets `1069` then EXITs; the CATCH overwrites the code with `500 / Internal Server Error`. The CATCH’s `1069` branch is unreachable because the EXIT carries no message. |
| F-03 | Medium | Mobile normalisation prepends `00` to anything not starting with `00` (`+971…` → `00+971…`, `05…` → `0005…`). |
| F-04 | Low | Address truncated to 39 characters (`substring(0,39)`), not 40, without telling the caller. |
| F-05 | Medium | DFM HTTP status never checked; DFM `response_code` copied as the middleware code, mixing provider and middleware code spaces. |
| F-06 | Medium | No connect/read timeout on the DFM call; no retry. |
| F-07 | High | The Confluence NINOnboarding page’s sample request includes a `securitykey` value (and an employee’s personal data); value not reproduced — rotate if real and anonymise the page. |
| F-08 | Medium | Personal data (NIN, EID and passport numbers, DOB, IBANs) logged to Seq and persisted in `middleware.SEQ_LOG`; `requestBodyToLog` with images also remains in the pipeline. |
| F-09 | Low | Disabled `UAT` branch in FINALLY would return a hard-coded fake success; duplicate `UAT` labels in the same BRANCH. |
| F-10 | Low | Documentation drift: service catalog flow paths swapped between “ninCreation” and “onboarding/accountUpdate”; Confluence lists `1005 Incorrect OTP` for this API; CRS/FATCA/guardian fields missing from the Confluence page; unused doc type `docs:NINOnboardingRequest` lacks them too. |

## Appendix E — Open Items

1. Confirm endpoint `POST /api/v1/dfm/investors`, 201 for success, and whether an exact NIN match should return 200.
2. Confirm 409 / 502 / 504 usage and the DFM codes for “duplicate request still processing”.
3. Which callers invoke NINOnboarding (catalog says internal call) and how the gateway route `/DFM/1.0/NINCreation` reaches it.
4. DFM: is `securitykey` required in both header and body? Expected EID number format; value sets for `pay_method`, `csr_resident`, `fatca_uscitizen`, `guardianship_type`; meaning of “Potential NIN match”.
5. Verify F-01 (lastError content returned to callers) in UAT.
6. Which field `SEQDatalust.asynchronousIngestion` actually logs (`requestBody` vs `requestBodyToLog`).
7. Images inline (Base64) vs DMS document references.
8. Operational: rotate the DFM key shown on Confluence; anonymise the sample; agree masking and retention for `SEQ_LOG`.
