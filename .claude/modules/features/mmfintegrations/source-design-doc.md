# Feature request (verbatim from user)

> i want you to create a feature called as mmfintegrations. all the endpoints mentioned in this mmfintegrations should be going in wealth-management-service.

---

# MMFIntegrations Package — API Design & Migration Specification

_webMethods Integration Server 10.7 → Java 21 / Spring Boot 3 · Al Ramz Middleware Migration · Money Market Fund (Athena)_

| Field | Value |
|---|---|
| Document | MMFIntegrations-API-Design-Document |
| Version | 0.1 — first draft for review |
| Date | 1 October 2026 |
| Source | IS package export `MMFIntegrations.zip` (7 REST services, 1 utility Flow, 1 Java service, 11 JDBC adapters); Confluence space INTEGRATIO |
| Consumer | Athena (fund administration) via API Gateway API MMFIntegration 1.0 |
| Status | Draft — proposals in this document are not yet agreed (see Appendix E) |

## 1. Overview

### 1.1 Purpose

This document reverse-engineers the webMethods Integration Server 10.7 package **MMFIntegrations** (Money Market Fund integration with the Athena fund-administration platform) into a migration-ready API specification for Java 21 / Spring Boot 3. It records what each legacy Flow service actually does — sourced from `flow.xml`, `node.ndf`, the JDBC adapter metadata and the IS Java service in the export — proposes a RESTful target contract for every exposed operation, and lists the source defects the migration must not carry forward.

### 1.2 Scope

**In scope**

- REST resource `MMFIntegrations.athena.restAPIs:athena` (and its Swagger descriptor `athenaDsc`) with seven operations: `getMMFClientData`, `getSweepIn`, `getSweepOut`, `sweepInConfirmation`, `pushProfitDistribution`, `getCustodiansCash`, `getCustodianPositions`.
- Utility services `MMFIntegrations.athena.utils:insertSweepData` (Flow) and `utils:convertStringToDate` (Java).
- The 11 JDBC adapter services in `MMFIntegrations.athena.adapters`, the 13 document types in `athena.docs`, and the two Oracle connections they use (`RMZ:RMZ`, `MMF:MMF`).

**Out of scope**

- The Athena-side APIs Al Ramz calls (`MMF/1.0/Request/sweepIn`, `sweepOut`, `cashRecon`, `positionRecon`, `mmfClients` — Confluence folder *Athena*). They are not implemented in this package.
- Shared services from other IS packages (`commonUtility`, `SEQDatalust`) — documented only as dependencies.
- The back-office processes that create sweep rows in `SWEEP_FACILITY` (type `W` / `O`).

### 1.3 Executive Summary

MMFIntegrations is a thin data-access layer that lets **Athena** read and update the cash sweeps between Al Ramz client accounts and the money market fund. Sweeps live in `INSIGHT.SWEEP_FACILITY`, which exists in **two Oracle databases**: the Capital back-office (`RMZ`, source `C`) and the MMF book (`MMF`, source `M`). Athena pulls pending sweep-ins (type `W`) and sweep-outs (type `O`), confirms sweep-ins (`W → I`), and pushes profit distributions (new type `P` rows). Two further operations return custodian cash balances and security positions for reconciliation; they are hidden from consumers at the API Gateway but remain callable on the Integration Server.

All seven operations are verb-named `POST` endpoints that always return HTTP 200; the outcome is carried in `responseCode` (`200`, `204`, `1069`, `500` or `503`). Every service builds SQL by string substitution into a Dynamic-SQL adapter (`${query}`); for three of them the substituted values come from the caller.

> [!CAUTION]
> **Key migration notes**
>
> - **SQL injection in two write/read paths:** `sweepInConfirmation` splices caller-supplied `sweepNo` values into `UPDATE … WHERE SWEEP_NO IN (…)` (the only validation is “not blank”), and `getCustodiansCash` splices unvalidated `clientID` values into a balance query (F-01, F-02).
> - **Request bodies written to the server disk:** `pushProfitDistribution` calls `pub.flow:savePipelineToFile` on every request (F-03).
> - **Non-atomic sweep numbering and cross-database updates:** new sweep numbers are `MAX(SWEEP_NO)+1`; sweep confirmations update two databases with transactions disabled (F-04, F-05).
> - **No idempotency on profit distribution:** re-posting the same batch inserts duplicate payouts; items with an unknown `source` are silently dropped while the call reports success (F-06).
> - **Positional column mapping drift:** `getCustodianPositions` selects 7 columns into a 6-field adapter output, so values are likely shifted (F-09 — verify).
> - **Credentials:** the production-style gateway API key is published in plain text on the Confluence API pages (value not reproduced here) (F-15).

#### 1.3.1 Source Artefacts and References

| Artefact | Detail |
|---|---|
| Package export | `MMFIntegrations.zip` — manifest build 2025-10-24 16:35 GST, description `MMFINTEGRATION_MMFINTEGRATION` |
| Flow services | 7 REST-bound services + `utils:insertSweepData`; Java service `utils:convertStringToDate` |
| Adapters | 11 JDBC adapter services: Custom SQL (`getClientDetails`, `getClientDetailsFromMMF`, `getMaxSweepNo`), Dynamic SQL `${query}` (`getSweepIn`, `getSweepOut`, `getSweepInFromMMF`, `getSweepOutFromMMF`, `updateSweepIn`, `getCustodiansCash`, `getCustodianPositions`), Insert (`pushProfitDistribution`) |
| Swagger descriptor | `athenaDsc` — Swagger 2.0, title *Athena*, host `ARC-MWD-IS.alramz.local:5555`, responses 200 / 401 |
| Confluence (INTEGRATIO) | Folder *MMFIntegrations*: pages getMMFClientData, getSweepIn, getSweepOut, sweepInConfirmation, pushProfitDistribution, getCustodiansCash, getCustodianPositions; *26/10/2025 – Deployment Plan* (v27.0) |
| Error-code registry | Confluence *REST API response codes* (page 2795896833) — the shared legacy-code catalogue. It defines `1069` as “Validation List Failed” and `1091` as “Middleware system validation error” (see F-11). |

## 2. Solution Architecture

### 2.1 As-Built Legacy Architecture

Athena reaches the package through the API Gateway API *MMFIntegration 1.0* (application *MMF*, API-key header). The deployment plan disables “Expose to consumers” for `/getCustodianPositions` and `/getCustodiansCash`, so those two are internal at the gateway — but the IS REST resource itself has no ACL (`check_internal_acls = no`) and still serves them.

| Legacy REST template (POST) | Flow service | Database(s) | Gateway exposure |
|---|---|---|---|
| `/getMMFClientData` | `athena.services:getMMFClientData` | RMZ or MMF (by `source`) | Exposed |
| `/getSweepIn` | `athena.services:getSweepIn` | RMZ + MMF | Exposed |
| `/getSweepOut` | `athena.services:getSweepOut` | RMZ + MMF | Exposed |
| `/sweepInConfirmation` | `athena.services:sweepInConfirmation` | RMZ + MMF (UPDATE) | Exposed |
| `/pushProfitDistribution` | `athena.services:pushProfitDistribution` | RMZ + MMF (INSERT) | Exposed |
| `/getCustodiansCash` | `athena.services:getCustodiansCash` | RMZ | Not exposed to consumers |
| `/getCustodianPositions` | `athena.services:getCustodianPositions` | RMZ | Not exposed to consumers |

### 2.2 End-to-End Journey — Sweep Lifecycle

Each operation on its own is a single database hop. Sweep-type codes are taken from the SQL literals in the Flows; their business meaning (`W` = sweep-in awaiting fund confirmation, `I` = confirmed, `O` = sweep-out, `P` = profit distribution) is inferred from usage and listed as an open item.

### 2.3 Target Architecture (Spring Boot)

One `mmf-integration-service` with two `DataSource` beans (Capital book, MMF book). The legacy duplication of every adapter per database (e.g. `getClientDetails` / `getClientDetailsFromMMF`) collapses into one repository per aggregate, parameterised by book — a shared bean, not a per-caller copy. All SQL uses bind parameters.

```
ae.alramz.mmf
 ├─ api            MmfAccountController, SweepController, ProfitDistributionController, CustodianController, dto/*
 ├─ application    SweepService, ProfitDistributionService, CustodianReportService
 ├─ domain         Book {CAPITAL, MMF}, SweepType {W, I, O, P}, Sweep, ProfitDistribution, OperationResult
 ├─ persistence    SweepRepository, AccountRepository, CustodianRepository (JdbcClient per Book)
 └─ config         DataSourceConfig (capitalDs, mmfDs), SecurityConfig, GlobalExceptionHandler, CorrelationIdFilter
```

(NOTE from user request: these endpoints go into the existing `services/wealth-management-service`, not a new `mmf-integration-service`.)

### 2.4 Target Response & Result Model

#### 2.4.1 Standard response envelope

| Field | Type | Always present | Meaning |
|---|---|---|---|
| responseCode | String | Yes | Duplicates the HTTP status, e.g. `"200"`, `"400"`. |
| responseMessage | String | Yes | HTTP reason phrase, e.g. `"OK"`, `"Bad Request"`. Legacy messages are free text (`success`, `No results found`, `Middleware system validation error : …`). |
| errorCode | String (nullable) | Yes | Service error code `MMF###` — client-input errors only; `null` on success and backend errors. |
| errorMsg | String (nullable) | Yes | Caller-actionable message; `null` otherwise. |
| correlationId | String (UUID) | Yes | Echo of `X-Correlation-Id` (generated if absent). Legacy key `correlationID`. |
| response | Object (nullable) | On success | Operation payload (legacy key kept). |

#### 2.4.2 Legacy error-handling pattern (shared by all seven services)

1. Validation failures call `commonUtility.services:checkAndThrowError` with a message beginning `1069 …` (or `pub.schema:validate` errors re-thrown as `1069 <path><ISC message>`).
2. CATCH: if `lastError/error` starts with `1069` → `responseCode = 1069`, `responseMessage = "Middleware system validation error : <rest of message>"`.
3. Any other exception → `responseCode = 503`, `responseMessage = "Internal Server Error"` (the two custodian services use `500` instead).
4. Empty result → `responseCode = 204` with message `success` or `No results found`, body `response: null`.
5. FINALLY: `SEQDatalust.services:asynchronousIngestion` audit log, then `clearPipeline` preserving the envelope.

Because there is no `pub.flow:setResponseCode`, the HTTP transport status is always **200** for every handled outcome.

#### 2.4.3 Service error-code scheme

Target codes use the prefix **MMF** + three digits in per-API blocks.

| Block | Endpoint |
|---|---|
| MMF001 – MMF009 | Get MMF account (Chapter 4) |
| MMF010 – MMF019 | List sweep-ins (Chapter 5) |
| MMF020 – MMF029 | List sweep-outs (Chapter 6) |
| MMF030 – MMF039 | Confirm sweep-ins (Chapter 7) |
| MMF040 – MMF049 | Push profit distributions (Chapter 8) |
| MMF050 – MMF059 | Custodian cash (Chapter 9) |
| MMF060 – MMF069 | Custodian positions (Chapter 10) |
| MMF090 – MMF099 | Internal utilities (Chapter 11) |

#### 2.4.4 Internal result type

```java
public sealed interface OperationResult<T> permits Success, BusinessFailure, TechnicalFailure {
  record Success<T>(T value) implements OperationResult<T> {}
  record BusinessFailure<T>(String serviceErrorCode, String message) implements OperationResult<T> {}
  record TechnicalFailure<T>(String serviceErrorCode, Book book, Throwable cause) implements OperationResult<T> {}
}
// replaces the legacy {status: "true"/"false", error: "<raw adapter text>"} returned by utils:insertSweepData
```

### 2.5 Legacy-to-Target Endpoint Map

| Legacy (deployed) | Target (proposed) | Success status |
|---|---|---|
| POST /getMMFClientData | GET /api/v1/mmf/accounts/{accountNumber}?book=CAPITAL\|MMF | 200 OK |
| POST /getSweepIn | GET /api/v1/mmf/sweep-ins?fromDate=&toDate= | 200 OK |
| POST /getSweepOut | GET /api/v1/mmf/sweep-outs?fromDate=&toDate= | 200 OK |
| POST /sweepInConfirmation | POST /api/v1/mmf/sweep-ins/confirmations | 200 OK |
| POST /pushProfitDistribution | POST /api/v1/mmf/profit-distributions | 201 Created / 207 Multi-Status |
| POST /getCustodiansCash | GET /api/v1/mmf/custodian-cash?clientId=… | 200 OK |
| POST /getCustodianPositions | GET /api/v1/mmf/custodian-positions[?asOfDate=] | 200 OK |

## 3. Prerequisites & Static Configuration

### 3.1 Integration Server Global Variables

None.

### 3.2 Static / Feature-Flag Configuration

| Item | Where | Value / behaviour | Target |
|---|---|---|---|
| commonUtility static data `MMFATHENA_CUSTODIAN_POSITIONS` (application MIDDLEWARE) | getCustodianPositions | Comma-separated client IDs pasted into `CL_CLIENT_ID IN (…)` | `mmf.custodian.client-ids` list property, bound as parameters |
| JDBC connection `RMZ:RMZ` | all adapters (default) | Capital back-office Oracle, schema INSIGHT | `capitalDataSource` |
| JDBC connection `MMF:MMF` | `*FromMMF` adapters; `$connectionName` override in sweepInConfirmation / insertSweepData | MMF book Oracle, schema INSIGHT. Deployment plan v27.0 notes it existed on only one PROD node (IS1) at the time. | `mmfDataSource` |
| Hard-coded SQL constants | several | `SWEEP_TYPE` W / I / O / P; `USR_CODE = 3`; price date `trunc(sysdate-1)`; market codes 5 = DFM, 8 = DIFX, 6 = ADX; `SEGMENT_ID = 0` | Enums / config |
| File name `tdfs` | pushProfitDistribution | `savePipelineToFile` target on the IS host (F-03) | Remove |

### 3.3 Upstream / Downstream Dependencies

| Dependency | Used by | Criticality (health check) |
|---|---|---|
| Oracle RMZ (INSIGHT: SWEEP_FACILITY, CB_CLIENT, CB_MAIN_CLIENT, F_PORT, DERV_F_PORT, CB_SEC_COMP, functions F_Get_Balance_As_Of, GET_CL_NAME_MAIN_CLIENT) | all | Critical |
| Oracle MMF (INSIGHT: SWEEP_FACILITY, CB_CLIENT, CB_MAIN_CLIENT) | account (book M), sweeps, confirmations, profit distribution | Critical |
| `commonUtility.java:GenerateGUID`, `commonUtility.services:checkAndThrowError`, `commonUtility.services:getStaticData` | all / positions | Replaced by in-service code / config |
| `SEQDatalust.services:asynchronousIngestion` | all seven | Non-critical (audit log) |

### 3.4 Security Notes

| Aspect | Legacy (as-built) | Target |
|---|---|---|
| Caller authentication | Gateway API key (`x-Gateway-APIKey`). The key value is printed on every MMFIntegration Confluence page (not reproduced here). | OAuth2 client-credentials (or mTLS) for Athena; rotate the published key. |
| Service ACL | `check_internal_acls = no` on all services; manifest has no `listACL`. The custodian endpoints are hidden only at the gateway. | Spring Security scopes per endpoint (`mmf.sweeps.read`, `mmf.sweeps.write`, `mmf.custodian.read`). |
| Injection | Dynamic SQL from caller input in sweepInConfirmation and getCustodiansCash (F-01, F-02); date filters are pattern-validated before substitution, so getSweepIn / getSweepOut are not injectable. | Bind parameters only. |
| Data at rest | pushProfitDistribution writes the request pipeline to file `tdfs` on the IS host (F-03). | No pipeline dumps. |
| Error leakage | Raw adapter / ORA exception text returned in `failSweep[].error` and schema ISC messages in `responseMessage`. | Log detail server-side; return codes only. |

## 4. API — Get MMF Account (getMMFClientData)

### 4.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | POST `/getMMFClientData` → `MMFIntegrations.athena.services:getMMFClientData` |
| Target operation | GET `/api/v1/mmf/accounts/{accountNumber}?book=CAPITAL\|MMF` |
| Success status | 200 OK |
| Consumer | Athena (client onboarding / static data) |
| Data source | `CB_CLIENT ⋈ CB_MAIN_CLIENT` via `getClientDetails` (RMZ) or `getClientDetailsFromMMF` (MMF) — Custom SQL with bind parameter |

Legacy `POST /getMMFClientData` → target `GET /api/v1/mmf/accounts/{accountNumber}?book=…`. Single-resource lookup keyed on `CL_CLIENT_ID = ?`, so a miss is 404. `book` stays a query parameter because nothing guarantees an account number is unique across the two books (open item).

### 4.2 Request Schema

| Field | Target Type | Required | Description |
|---|---|---|---|
| `accountNumber` (path) | String (digits, ≤ 9) | Yes | Client sub-account (`CL_CLIENT_ID`). Legacy only required “not blank”. Target pattern `^\d{1,9}$` inferred — confirm (open item). |
| `book` (query) | enum {CAPITAL, MMF} | Yes | Which database to read. Legacy `source` = `C` / `M`; other → `1069 Invalid Source Type`. |

### 4.3 Business Logic Summary

1. Generate correlation id; copy `accountNumber` / `source` into `mmfClientRequest`; serialise for the audit log.
2. Null check → `1069 accountNumber and source are required` (never fires).
3. `pub.schema:validate`; invalid → `1069 <path><ISC message>`.
4. `source = C` → `getClientDetails` (RMZ); `source = M` → `getClientDetailsFromMMF` (MMF); other → `1069 Invalid Source Type`.
5. SQL: `SELECT c.CL_MAIN_CLIENT_ID clientID, m.CLE_CLIENT_NAME clientNameEn, m.CLA_CLIENT_NAME clientNameAr, c.CL_CLIENT_ID accountNumber, c.CLE_CLIENT_NAME accountName, c.CUR_CODE baseCurrency FROM INSIGHT.CB_CLIENT c JOIN INSIGHT.CB_MAIN_CLIENT m ON … WHERE c.CL_CLIENT_ID = ?`.
6. Rows ≥ 1 → `200 / success` with `response.MMFClients[]`; 0 rows → `204 / success`.
7. CATCH → `1069 / …` or `503 / Internal Server Error`; FINALLY audit log.

Observations: legacy response is an array though key is unique — target returns single `account` object. Swagger doc declares `clientNameAR`, `AccountName`, `BaseCurrency` but adapter emits `clientNameAr`, `accountName`, `baseCurrency` (F-10). Confluence sample shows `"responseCode": "ok"` (F-19).

### 4.4 Sample Request

```http
GET /api/v1/mmf/accounts/2939418?book=CAPITAL
Authorization: Bearer <token>
```

Legacy: `POST /getMMFClientData` `{"accountNumber":"2939418","source":"C"}`.

### 4.5 Response Schema

| Field | Target Type | Description |
|---|---|---|
| `response.account` | Object | The account. Legacy `response.MMFClients[]`. |
| `clientId` | String | Main client id (`CL_MAIN_CLIENT_ID`), e.g. `1100206936164`. Legacy `clientID`. |
| `clientNameEn` / `clientNameAr` | String | Main client name, English / Arabic. |
| `accountNumber` | String | Sub-account (`CL_CLIENT_ID`). |
| `accountName` | String | Sub-account name (`CLE_CLIENT_NAME`). |
| `baseCurrency` | String (ISO 4217) | Account currency (`CUR_CODE`). |

### 4.6 HTTP Status Code Reference

| HTTP Status | Service Error Code | Description | Legacy |
|---|---|---|---|
| 400 | MMF001 | `accountNumber` missing, blank or not numeric. | 1069 schema message |
| 400 | MMF002 | `book` missing. | 1069 schema message |
| 400 | MMF003 | `book` not CAPITAL / MMF. | 1069 Invalid Source Type |
| 404 | MMF004 | No account with this number in the selected book. | 204 success |
| 500 | MMF005 | Unexpected database / SQL error. | 503 |
| 503 | MMF006 | Selected database unreachable. | 503 |

### 4.7 Example Target Response Envelopes

```json
{
  "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33",
  "response": { "account": {
      "clientId": "1100206936164", "clientNameEn": "1100206936164-INDIVIDUAL", "clientNameAr": "1100206936164-INDIVIDUAL",
      "accountNumber": "2939418", "accountName": "1100206936164-NORMAL-AED", "baseCurrency": "AED" } }
}
```

```json
{ "responseCode": "404", "responseMessage": "Not Found", "errorCode": "MMF004",
  "errorMsg": "Account 2939418 not found in book CAPITAL", "correlationId": "6a1f0c2e-..." }
```

```json
{ "responseCode": "503", "responseMessage": "Service Unavailable", "errorCode": null, "errorMsg": null, "correlationId": "6a1f0c2e-..." }
```

## 5. API — List Sweep-Ins (getSweepIn)

### 5.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | POST `/getSweepIn` |
| Target operation | GET `/api/v1/mmf/sweep-ins?fromDate=&toDate=` |
| Success status | 200 OK |
| Data source | `INSIGHT.SWEEP_FACILITY` (SWEEP_TYPE = 'W') in RMZ via `getSweepIn` and in MMF via `getSweepOutFromMMF` — Dynamic SQL |

Collection endpoint: empty range → 200 with empty list.

### 5.2 Request Schema

| Field (query) | Target Type | Required | Description |
|---|---|---|---|
| `fromDate` | LocalDate (ISO 8601) | With `toDate` | Range start (inclusive). Legacy `DD-MM-YYYY` pattern (does not reject 31-02-2025). |
| `toDate` | LocalDate (ISO 8601) | With `fromDate` | Range end (inclusive, whole day). Legacy compares `SWEEP_DATE BETWEEN` **without** `TRUNC`, so rows later than 00:00 on `toDate` are excluded (F-07). |
| (neither) | — | — | Both omitted → today’s sweeps (`TRUNC(SWEEP_DATE) = TRUNC(SYSDATE)`). |

### 5.3 Business Logic Summary

1. Generate correlation id. (Mandatory-dates check and schema validation present but **disabled**.)
2. Both dates present → validate pattern and build: `SELECT SWEEP_NO, TO_CHAR(SWEEP_DATE,'DD-MM-YYYY') SWEEP_DATE, CLIENT_ID INVESTOR_ID, AMT AMOUNT FROM INSIGHT.SWEEP_FACILITY WHERE SWEEP_DATE BETWEEN TO_DATE('%fromDate%','DD-MM-YYYY') AND TO_DATE('%toDate%','DD-MM-YYYY') AND SWEEP_TYPE = 'W'`.
3. Both absent → `… WHERE TRUNC(SWEEP_DATE) = TRUNC(SYSDATE) AND SWEEP_TYPE = 'W' ORDER BY SWEEP_NO DESC`. Exactly one present → `1069 either pass both fromDate and toDate else dont pass both fromDate and toDate`.
4. Run same SQL against RMZ (tag `source = C`) and MMF (`getSweepOutFromMMF` adapter, F-14, tag `source = M`). Columns mapped by position to `sweepNo, date, accountNumber, amount`.
5. Append MMF list to Capital list; size ≥ 1 → `200` with `response.sweepsIn`; else `204`.
6. CATCH → `1069` or `503`.

Observations: no global ordering — target sorts by (date, sweepNo). No `fromDate ≤ toDate` validation, no upper bound — proposed MMF012 and max range (e.g. 93 days) — open item. Target uses bind parameters.

### 5.4 Sample Request

`GET /api/v1/mmf/sweep-ins?fromDate=2019-12-26&toDate=2020-05-27`

### 5.5 Response Schema

| Field | Target Type | Description |
|---|---|---|
| `response.sweepIns[]` | Array | Sweeps from both books, merged (empty array when none). |
| `sweepNo` | Long | `SWEEP_NO` NUMBER(15) NOT NULL. |
| `date` | LocalDate (ISO 8601) | `SWEEP_DATE` DATE NOT NULL. Legacy two formats (F-08). |
| `accountNumber` | String (digits, ≤ 9) | `CLIENT_ID` NUMBER(9) NOT NULL. |
| `amount` | BigDecimal (scale 3) | `AMT` NUMBER(25,3). |
| `source` | enum Book {CAPITAL, MMF} | Book the row came from. |

### 5.6 HTTP Status Code Reference

Empty result is 200 with `sweepIns: []`, not 204.

| HTTP | Code | Description |
|---|---|---|
| 400 | MMF010 | Only one of `fromDate` / `toDate` supplied. |
| 400 | MMF011 | Date not a valid ISO 8601 calendar date. |
| 400 | MMF012 | `fromDate` after `toDate`, or range above the maximum — proposed. |
| 200 | — | No sweeps → empty list. |
| 500 | MMF015 | Unexpected database error. |
| 503 | MMF016 | RMZ or MMF database unreachable. |

### 5.7 Example

```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "correlationId": "...",
  "response": { "sweepIns": [ { "sweepNo": 52, "date": "2019-12-30", "accountNumber": "2944237", "amount": 3838.02, "source": "CAPITAL" } ] } }
```

```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MMF010",
  "errorMsg": "Provide both fromDate and toDate, or neither", "correlationId": "..." }
```

## 6. API — List Sweep-Outs (getSweepOut)

Identical in shape to Chapter 5 with these differences:

- Target operation: GET `/api/v1/mmf/sweep-outs?fromDate=&toDate=`; SWEEP_TYPE = 'O'; RMZ adapter `getSweepOut`, MMF adapter `getSweepOutFromMMF`.
- Legacy dated query uses `TRUNC(SWEEP_DATE) BETWEEN … ORDER BY SWEEP_NO DESC` (whole day included).
- Legacy “today” query returns `SWEEP_DATE` unformatted (F-08).
- Response array key `response.sweepOuts[]` (legacy `sweepsOut`).
- Error codes: MMF020 (only one date), MMF021 (invalid date), MMF022 (from > to / range too large — proposed), MMF025 (500 DB error), MMF026 (503 DB unreachable). Empty → 200 `sweepOuts: []`.

Example: `{"sweepNo": 24294, "date": "2020-05-27", "accountNumber": "2944758", "amount": 50000, "source": "CAPITAL"}`.

## 7. API — Confirm Sweep-Ins (sweepInConfirmation)

### 7.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | POST `/sweepInConfirmation` |
| Target operation | POST `/api/v1/mmf/sweep-ins/confirmations` |
| Success status | 200 OK |
| Consumer | Athena (after booking the subscriptions) |
| Data source | `UPDATE INSIGHT.SWEEP_FACILITY SET SWEEP_TYPE = 'I'` via `updateSweepIn` (Dynamic SQL) on RMZ then MMF |

Batch state transition (W → I). Alternative `PATCH /api/v1/mmf/sweep-ins` — open item.

### 7.2 Request Schema

| Field | Target Type | Required | Description |
|---|---|---|---|
| (body) | Array of items (1–1000) | Yes | Bare JSON array. Empty → error. |
| `sweepNo` | Long | Yes | Positive integer, bound as array parameter. Legacy any non-blank text pasted into `IN (…)` — SQL injection (F-01). |
| `source` | enum Book {CAPITAL, MMF} | Yes | Book holding the sweep. Legacy `^(C\|M)$`. |

### 7.3 Business Logic Summary

1. Generate correlation id.
2. Empty → `1069 no inputs are passed`. Validate schema.
3. Split by `source` into Capital and MMF lists.
4. Capital non-empty → `UPDATE INSIGHT.SWEEP_FACILITY SET SWEEP_TYPE = 'I' WHERE SWEEP_NO IN (<joined>)` on RMZ; `recordsUpdatedInCapital`.
5. Same for MMF → `recordsUpdatedInMMF`.
6. Transactions disabled → each update auto-commits independently.
7. `200 / success`. CATCH → `1069` or `503`.

Observations: **no state guard** (no `AND SWEEP_TYPE = 'W'`). Silent partial confirmation — target returns 409 (MMF033) listing unknown / non-pending numbers, or per-item results (open item). Cross-database consistency — target: one transaction per book plus idempotent retry (confirming an already-`I` sweep is a no-op success).

### 7.4 Sample Request

```json
[ {"sweepNo": 2170411, "source": "CAPITAL"}, {"sweepNo": 2170412, "source": "CAPITAL"}, {"sweepNo": 2345, "source": "MMF"} ]
```

### 7.5 Response Schema

| Field | Type | Description |
|---|---|---|
| `response.confirmedCapital` | Integer | Rows changed W → I in Capital. |
| `response.confirmedMmf` | Integer | Rows changed W → I in MMF. |
| `response.alreadyConfirmed[]` | Array of {sweepNo, book} | Items already `I` (idempotent retry) — new. |

### 7.6 HTTP Status Code Reference

| HTTP | Code | Description |
|---|---|---|
| 400 | MMF030 | Empty request. |
| 400 | MMF031 | Item missing `sweepNo` / `source`, or `source` invalid. |
| 400 | MMF032 | `sweepNo` not a positive integer. |
| 409 | MMF033 | One or more sweep numbers unknown or not in pending (W) state. |
| 500 | MMF035 | Unexpected database error (nothing committed). |
| 503 | MMF036 | RMZ or MMF database unreachable. |
| 500 | MMF037 | One book committed, the other failed — compensated / queued for retry; alert raised. |

### 7.7 Examples

```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "correlationId": "...",
  "response": { "confirmedCapital": 2, "confirmedMmf": 1, "alreadyConfirmed": [] } }
```

```json
{ "responseCode": "409", "responseMessage": "Conflict", "errorCode": "MMF033", "errorMsg": "Sweeps not pending: CAPITAL 2170412", "correlationId": "..." }
```

## 8. API — Push Profit Distributions (pushProfitDistribution)

### 8.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | POST `/pushProfitDistribution` → `utils:insertSweepData` per item |
| Target operation | POST `/api/v1/mmf/profit-distributions` |
| Success status | 201 Created (all items) / 207 Multi-Status (mixed outcome) — 207 is an open item; alternative 200 with per-item results |
| Data source | `INSERT INTO INSIGHT.SWEEP_FACILITY` (SWEEP_TYPE = 'P') on RMZ or MMF; `getMaxSweepNo` for the key |

### 8.2 Request Schema

| Field | Target Type | Required | Description |
|---|---|---|---|
| (body) | Array of items (1–5000) | Yes | Bare JSON array. |
| `sweepDate` | LocalDate (ISO 8601) | Yes | → `SWEEP_DATE`. Legacy lenient `SimpleDateFormat` (31-02 → 3 March). |
| `accountNumber` | String (digits, ≤ 9) | Yes | → `CLIENT_ID` NUMBER(9). |
| `amount` | BigDecimal (≤ 22 integer digits, scale ≤ 3) | Yes | → `AMT` NUMBER(25,3). Negative allowed? — open item. |
| `referenceId` | String (digits, ≤ 15) | Yes (target) | → `REF_ID` NUMBER(15); idempotency key, unique per book. |
| `source` | enum Book {CAPITAL, MMF} | Yes | Book to post into. Legacy silently drops other values (F-06). |

### 8.3 Business Logic Summary

1. **`pub.flow:savePipelineToFile` to `tdfs` (active)** (F-03) — remove.
2. Empty → `1069 no inputs are passed`; validate.
3. Split by source; other sources silently dropped.
4. Per item: `SELECT MAX(SWEEP_NO)` → `+1`; parse `sweepDate`; `UPD_TIME` = today as string; INSERT `SWEEP_NO, SWEEP_DATE, SWEEP_TYPE = P, CLIENT_ID, AMT, REF_ID, SRC_TYPE = source, SEGMENT_ID = 0, UPD_TIME`. Exception → `status=false` with raw text.
5. Success → `successSweep.accountNumber[]`; failure → `failSweep[]{accountNumber, error}`.
6. Always 200 once validation passed.

Observations: `MAX+1` race — target uses DB sequence per book (F-04). Per-item auto-commit; no idempotency (F-06). `UPD_TIME` string into DATE column — possibly every insert fails today (F-12). Result keyed by non-unique `accountNumber` — target echoes `referenceId`. `JV_NO` / `USR_CODE` never set — open item.

### 8.4 Sample Request

```json
[ {"sweepDate": "2025-04-20", "accountNumber": "1005", "amount": 2500.75, "referenceId": "987654321", "source": "CAPITAL"},
  {"sweepDate": "2025-04-20", "accountNumber": "1006", "amount": 3700.5, "referenceId": "123456789", "source": "CAPITAL"} ]
```

### 8.5 Response Schema

| Field | Type | Description |
|---|---|---|
| `response.results[]` | Array | One entry per request item, in request order. |
| `referenceId`, `accountNumber`, `book` | String / enum | Echo. |
| `status` | enum {CREATED, FAILED} | Item outcome. |
| `sweepNo` | Long | Allocated `SWEEP_NO` (CREATED only). |
| `errorCode` | String (nullable) | e.g. `MMF047`; raw DB message logged, not returned. |

### 8.6 HTTP Status Code Reference

Validation all-or-nothing: any invalid item → 400 before anything is written.

| HTTP | Code | Description |
|---|---|---|
| 400 | MMF040 | Empty request. |
| 400 | MMF041 | Item fails field rules (date, amount format, missing field). |
| 400 | MMF042 | `accountNumber` not numeric / > 9 digits. |
| 400 | MMF043 | `source` not CAPITAL / MMF. |
| 409 | MMF044 | `referenceId` already booked in that book (identical-payload replay returns original result instead). |
| 500 | MMF045 | Unexpected error before any item processed. |
| 503 | MMF046 | Target database unreachable. |
| 207 | MMF047 | Item insert failed after validation; others succeeded. |

### 8.7 Examples

```json
{ "responseCode": "201", "responseMessage": "Created", "errorCode": null, "errorMsg": null, "correlationId": "...",
  "response": { "results": [
    {"referenceId": "987654321", "accountNumber": "1005", "book": "CAPITAL", "status": "CREATED", "sweepNo": 2170513, "errorCode": null},
    {"referenceId": "123456789", "accountNumber": "1006", "book": "CAPITAL", "status": "CREATED", "sweepNo": 2170514, "errorCode": null} ] } }
```

```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MMF043", "errorMsg": "Item 3: source must be CAPITAL or MMF", "correlationId": "..." }
```

```json
{ "responseCode": "207", "responseMessage": "Multi-Status", "errorCode": null, "errorMsg": null, "correlationId": "...",
  "response": { "results": [
    {"referenceId": "987654321", "accountNumber": "1005", "book": "CAPITAL", "status": "FAILED", "sweepNo": null, "errorCode": "MMF047"},
    {"referenceId": "123456789", "accountNumber": "1006", "book": "CAPITAL", "status": "CREATED", "sweepNo": 2170514, "errorCode": null} ] } }
```

## 9. API — Custodian Cash (getCustodiansCash)

### 9.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | POST `/getCustodiansCash` (not exposed to consumers at gateway) |
| Target operation | GET `/api/v1/mmf/custodian-cash?clientId=…&clientId=…` (or `POST …/custodian-cash/search` if URL limits) |
| Success status | 200 OK |
| Consumer | Internal reconciliation |
| Data source | RMZ `CB_CLIENT`, functions `GET_CL_NAME_MAIN_CLIENT`, `INSIGHT.F_Get_Balance_As_Of` |

### 9.2 Request Schema

| Field | Type | Required | Description |
|---|---|---|---|
| `clientId` (repeated) | List<String> (1–500, each digits ≤ 9) | Yes | Legacy no validation, pasted into `IN (…)` — SQL injection (F-02). |

### 9.3 Business Logic Summary

1. List size ≥ 1 else `1069 clientID cannot be empty`.
2. `SELECT CL_MAIN_CLIENT_ID CLIENT_MAIN_ID, CL_CLIENT_ID CLIENT_ID, GET_CL_NAME_MAIN_CLIENT(CL_CLIENT_ID) CLIENT_NAME, INSIGHT.F_Get_Balance_As_Of(CL_MAIN_CLIENT_ID, CL_CLIENT_ID, SYSDATE, 'N', 'Y') CASH_BALANCE FROM CB_CLIENT WHERE CL_CLIENT_ID IN (<list>)`.
3. Rows ≥ 1 → `200`; else `204 / No results found`.
4. CATCH → `1069` or `500`.

Observations: ids not found silently omitted (collection semantics). `'N','Y'` flags unknown — open item; `asOf` param proposed. `clinetMainID` typo → `clientMainId`.

### 9.5 Response Schema

`response.custodianCash[]`: `clientMainId` (String), `clientId` (String), `clientName` (String), `cashBalance` (String declared — BigDecimal proposed, open item).

### 9.6 Codes

| HTTP | Code | Description |
|---|---|---|
| 400 | MMF050 | No `clientId`. |
| 400 | MMF051 | `clientId` not numeric / > 9 digits. |
| 400 | MMF052 | More than 500 ids. |
| 200 | — | No matches → empty list. |
| 500 | MMF055 | Unexpected DB / function error. |
| 503 | MMF056 | RMZ unreachable. |

Example row: `{"clientMainId": "1100106001214", "clientId": "2001214", "clientName": "1100106001214-NORMAL-AED", "cashBalance": "0"}`.

## 10. API — Custodian Positions (getCustodianPositions)

### 10.1 Endpoint Summary

| Attribute | Value |
|---|---|
| Legacy operation | POST `/getCustodianPositions` (not exposed to consumers), takes no input |
| Target operation | GET `/api/v1/mmf/custodian-positions[?asOfDate=]` |
| Success status | 200 OK |
| Data source | RMZ `F_PORT` ∪ `DERV_F_PORT` ⋈ `CB_CLIENT`, `CB_MAIN_CLIENT`, `CB_SEC_COMP`; client list from static data |

### 10.2 Request Schema

`asOfDate` (query, optional, LocalDate) — price date; default previous business day — **new**. Legacy always `trunc(sysdate-1)`.

### 10.3 Business Logic Summary

1. Read static data `MMFATHENA_CUSTODIAN_POSITIONS` → comma-separated client ids. Empty → `1069 clientID cannot be empty` (config fault).
2. Query (two branches, `UNION ALL`): `F.Client_ID ClientID, sc.SC_ISIN_CODE ISIN, sc.TICKER_ID Symbol, F.QTY quantity, f.PR_C_PRICE price, C.CUR_CODE Currency, CASE sc.SC_MRK_CODE WHEN 5 THEN DFM WHEN 8 THEN DIFX WHEN 6 THEN ADX ELSE '' END Market` from `F_PORT` and `DERV_F_PORT`, `WHERE CL_CLIENT_ID IN (<static list>) AND PR_PRICE_DATE = trunc(sysdate-1) AND USR_CODE = 3 AND QTY <> 0`.
3. Rows mapped by position into 6 fields for 7 columns (F-09).
4. Rows ≥ 1 → 200; else 204. CATCH → `1069` or `500`.

Observations: likely shifted columns (F-09 — verify). Target `closePrice` + `symbol`. `USR_CODE = 3` and market mapping → configuration.

### 10.5 Response Schema

`response.custodianPositions[]`: `clientId`, `isin`, `symbol` (new), `quantity` (String declared — BigDecimal proposed), `closePrice` (String declared — BigDecimal proposed), `currency`, `market` (enum {DFM, DIFX, ADX}), `priceDate` (LocalDate, new).

### 10.6 Codes

| HTTP | Code | Description |
|---|---|---|
| 400 | MMF060 | `asOfDate` invalid or in the future (proposed). |
| 200 | — | No positions → empty list. |
| 500 | MMF065 | Custodian client list not configured. |
| 500 | MMF066 | Unexpected database error. |
| 503 | MMF067 | RMZ unreachable. |

## 11. Downstream & Back-office Integration APIs (internal only)

### 11.1 utils:insertSweepData

Target: `SweepRepository.insertProfitDistribution(Book, ProfitDistribution) : OperationResult<Long>`.

1. `SELECT MAX(SWEEP_NO) AS MAX_SWEEP_NO FROM INSIGHT.SWEEP_FACILITY` (no lock) → `+1` (empty table errors).
2. `convertStringToDate(sweepDate, "dd-MM-yyyy")`.
3. `UPD_TIME` = today as string (into DATE column).
4. INSERT `SWEEP_NO, SWEEP_DATE, SWEEP_TYPE='P', CLIENT_ID, AMT, REF_ID, SRC_TYPE, SEGMENT_ID='0', UPD_TIME`.
5. Result ≥ 1 → `status = true`; CATCH → `status = false`, raw error.

Codes: MMF090 (date unparseable → 400 MMF041), MMF091 (insert failed → 207 item MMF047), MMF092 (duplicate SWEEP_NO → 207 item MMF047).

### 11.2 utils:convertStringToDate (Java)

`new SimpleDateFormat(datePattern).parse(dateString)` — lenient. Target: `LocalDate.parse(value, ISO_LOCAL_DATE)` at the API boundary (strict).

### 11.3 JDBC Adapter Inventory

| Adapter | Template | Connection | SQL / table | Callers |
|---|---|---|---|---|
| getClientDetails | Custom SQL | RMZ | CB_CLIENT ⋈ CB_MAIN_CLIENT WHERE CL_CLIENT_ID = ? | getMMFClientData |
| getClientDetailsFromMMF | Custom SQL | MMF | same | getMMFClientData |
| getSweepIn | Dynamic SQL | RMZ | `${query}` | getSweepIn |
| getSweepOut | Dynamic SQL | RMZ | `${query}` | getSweepOut |
| getSweepInFromMMF | Dynamic SQL | MMF | `${query}` | none |
| getSweepOutFromMMF | Dynamic SQL | MMF | `${query}` | getSweepIn, getSweepOut |
| updateSweepIn | Dynamic SQL | RMZ (overridden) | UPDATE | sweepInConfirmation |
| getMaxSweepNo | Custom SQL | RMZ (overridden) | SELECT MAX(SWEEP_NO) | insertSweepData |
| pushProfitDistribution | Insert | RMZ (overridden) | INSIGHT.SWEEP_FACILITY | insertSweepData |
| getCustodiansCash | Dynamic SQL | RMZ | `${query}` | getCustodiansCash |
| getCustodianPositions | Dynamic SQL | RMZ | `${query}` | getCustodianPositions |

### 11.4 Other IS Package Dependencies

| Service | Use | Target |
|---|---|---|
| `commonUtility.java:GenerateGUID` | Correlation id | `UUID.randomUUID()` in a servlet filter |
| `commonUtility.services:checkAndThrowError` | Throws to CATCH | Typed exceptions + `@ControllerAdvice` |
| `commonUtility.services:getStaticData` | `MMFATHENA_CUSTODIAN_POSITIONS` | Spring configuration property |
| `SEQDatalust.services:asynchronousIngestion` | Audit log | Structured logging / audit topic. Audit-log failures must never fail a request. |

## 12. Data Mapping Reference

### 12.1 INSIGHT.SWEEP_FACILITY ↔ API fields

| Column | Type | Read by | Written by | Target |
|---|---|---|---|---|
| SWEEP_NO | NUMBER(15) NOT NULL | sweepNo | MAX+1 | Long (sequence) |
| SWEEP_DATE | DATE NOT NULL | date | profit: sweepDate | LocalDate |
| SWEEP_TYPE | VARCHAR2(1) NOT NULL | filter W / O | P; I | enum SweepType |
| CLIENT_ID | NUMBER(9) NOT NULL | accountNumber | profit: accountNumber | String ^\d{1,9}$ |
| AMT | NUMBER(25,3) | amount | profit: amount | BigDecimal |
| JV_NO | NUMBER(8) | — | never | open item |
| USR_CODE | VARCHAR2(4) | — | never | open item |
| REF_ID | NUMBER(15) | — | profit: referenceID | String (unique per book) |
| UPD_TIME | DATE | — | today string | server timestamp |
| SRC_TYPE | VARCHAR2(1) | — | profit: source (C/M) | book code |
| SEGMENT_ID | NUMBER(3) | — | 0 | constant / config |

### 12.2 Account / custodian fields

| Source | Legacy key | Target key |
|---|---|---|
| CB_CLIENT.CL_MAIN_CLIENT_ID | clientID / clinetMainID | clientId / clientMainId |
| CB_MAIN_CLIENT.CLE_CLIENT_NAME / CLA_CLIENT_NAME | clientNameEn / clientNameAr | clientNameEn / clientNameAr |
| CB_CLIENT.CL_CLIENT_ID | accountNumber / clientID | accountNumber / clientId |
| CB_CLIENT.CLE_CLIENT_NAME | accountName | accountName |
| CB_CLIENT.CUR_CODE | baseCurrency / currency | baseCurrency / currency |
| GET_CL_NAME_MAIN_CLIENT(CL_CLIENT_ID) | clientName | clientName |
| F_Get_Balance_As_Of(main, client, SYSDATE, N, Y) | cashBalance | cashBalance |
| CB_SEC_COMP.SC_ISIN_CODE / TICKER_ID | ISIN / (lost) | isin / symbol |
| F_PORT.QTY / PR_C_PRICE | quantity / qlosePrice | quantity / closePrice |
| CB_SEC_COMP.SC_MRK_CODE (5, 8, 6) | market (DFM, DIFX, ADX) | market |

### 12.3 Book (source) codes

| Legacy | Connection | Target enum | Meaning |
|---|---|---|---|
| C | RMZ:RMZ | CAPITAL | Al Ramz Capital back-office (INSIGHT schema) |
| M | MMF:MMF | MMF | Money-market-fund book (INSIGHT schema in a separate database) |

## Appendix A — Pseudocode

### A.1 sweepInConfirmation (legacy)

```
if empty(request): fail 1069 "no inputs are passed"
validate(request)            // sweepNo: non-blank; source: ^(C|M)$
cap = [i.sweepNo for i in request if i.source == "C"]
mmf = [i.sweepNo for i in request if i.source == "M"]
if cap: n1 = execute(RMZ, "UPDATE INSIGHT.SWEEP_FACILITY SET SWEEP_TYPE='I' WHERE SWEEP_NO IN (" + join(cap, ",") + ")")   // injectable, auto-commit
if mmf: n2 = execute(MMF, same with mmf)
return 200 {recordsUpdatedInCapital: n1 ?: 0, recordsUpdatedInMMF: n2 ?: 0}
```

### A.2 pushProfitDistribution + insertSweepData (legacy)

```
savePipelineToFile("tdfs")                            // active
validate(items)
for item in items where source == "C": result(item, insertSweepData(item, RMZ))
for item in items where source == "M": result(item, insertSweepData(item, MMF))
// other sources: ignored
return 200 {successSweep.accountNumber[], failSweep[{accountNumber, error}]}

insertSweepData(item, db):
  try:
    no = db.query("SELECT MAX(SWEEP_NO) FROM INSIGHT.SWEEP_FACILITY") + 1   // race
    d  = SimpleDateFormat("dd-MM-yyyy").parse(item.sweepDate)              // lenient
    db.insert(SWEEP_NO=no, SWEEP_DATE=d, SWEEP_TYPE="P", CLIENT_ID=item.accountNumber, AMT=item.amount,
              REF_ID=item.referenceID, SRC_TYPE=item.source, SEGMENT_ID=0, UPD_TIME=today("yyyy-MM-dd"))
    return {status:"true"}
  catch e: return {status:"false", error:e.message}
```

### A.3 Target confirmation (sketch)

```
@Transactional(transactionManager = "#{book.txManager}")
ConfirmResult confirm(Book book, List<Long> sweepNos) {
  var rows = repo.lockByNumbers(book, sweepNos);                 // SELECT … FOR UPDATE
  var invalid = rows.stream().filter(r -> r.type() != W && r.type() != I).toList();
  var missing = difference(sweepNos, rows);
  if (!invalid.isEmpty() || !missing.isEmpty()) throw new MmfConflict("MMF033", missing, invalid);
  int changed = repo.updateType(book, sweepNos, W, I);          // WHERE SWEEP_NO IN (:ids) AND SWEEP_TYPE = 'W'
  return new ConfirmResult(changed, alreadyConfirmed(rows));
}
```

## Appendix B — Glossary

| Term | Meaning |
|---|---|
| MMF | Money Market Fund. |
| Athena | Fund-administration platform that consumes this API. |
| Book | One of the two databases holding sweeps: Capital (RMZ, `C`) or MMF (`M`). |
| Sweep-in / sweep-out | Automatic transfer of idle client cash into / out of the fund. |
| SWEEP_TYPE | `W` pending sweep-in, `I` confirmed sweep-in, `O` sweep-out, `P` profit distribution (inferred). |
| Profit distribution | Allocation of fund profit to a client sub-account, posted as a `P` sweep row. |

## Appendix C — Database DDL

### C.1 Source DDL (reconstructed — no DDL in the export)

```sql
CREATE TABLE INSIGHT.SWEEP_FACILITY (      -- RMZ and MMF databases
  SWEEP_NO    NUMBER(15)   NOT NULL,
  SWEEP_DATE  DATE         NOT NULL,
  SWEEP_TYPE  VARCHAR2(1)  NOT NULL,
  CLIENT_ID   NUMBER(9)    NOT NULL,
  AMT         NUMBER(25,3),
  JV_NO       NUMBER(8),
  USR_CODE    VARCHAR2(4),
  REF_ID      NUMBER(15),
  UPD_TIME    DATE,
  SRC_TYPE    VARCHAR2(1),
  SEGMENT_ID  NUMBER(3)
);
-- referenced only (types unknown): CB_CLIENT(CL_CLIENT_ID, CL_MAIN_CLIENT_ID, CLE_CLIENT_NAME, CUR_CODE),
--   CB_MAIN_CLIENT(CL_MAIN_CLIENT_ID, CLE_CLIENT_NAME, CLA_CLIENT_NAME), F_PORT / DERV_F_PORT(CLIENT_ID, COMP_ID,
--   QTY, PR_C_PRICE, PR_PRICE_DATE, USR_CODE), CB_SEC_COMP(SC_COMP_ID, SC_ISIN_CODE, TICKER_ID, SC_MRK_CODE);
--   functions INSIGHT.F_Get_Balance_As_Of(...), GET_CL_NAME_MAIN_CLIENT(...)
```

### C.2 Type-Mapping Decisions

| Column | Source | Java | JSON |
|---|---|---|---|
| SWEEP_NO | NUMBER(15) | long | number |
| SWEEP_DATE | DATE | LocalDate | string (ISO 8601) |
| SWEEP_TYPE | VARCHAR2(1) | enum SweepType | string |
| CLIENT_ID | NUMBER(9) | String | string |
| AMT | NUMBER(25,3) | BigDecimal | number |
| REF_ID | NUMBER(15) | String | string |
| UPD_TIME | DATE | OffsetDateTime → DATE | — |
| SRC_TYPE | VARCHAR2(1) | enum Book | string |

### C.3 Target DDL (proposed, delta — apply in both databases)

```sql
CREATE SEQUENCE INSIGHT.SWEEP_FACILITY_SEQ START WITH <current MAX(SWEEP_NO)+1> NOCACHE;   -- per database
ALTER TABLE INSIGHT.SWEEP_FACILITY ADD CONSTRAINT PK_SWEEP_FACILITY PRIMARY KEY (SWEEP_NO);   -- if absent
ALTER TABLE INSIGHT.SWEEP_FACILITY ADD CONSTRAINT CK_SWEEP_TYPE CHECK (SWEEP_TYPE IN ('W','I','O','P'));
CREATE UNIQUE INDEX UX_SWEEP_PROFIT_REF ON INSIGHT.SWEEP_FACILITY
       (CASE WHEN SWEEP_TYPE = 'P' THEN REF_ID END);                                 -- idempotency
CREATE INDEX IX_SWEEP_TYPE_DATE ON INSIGHT.SWEEP_FACILITY (SWEEP_TYPE, TRUNC(SWEEP_DATE));
```

Coordinate the sequence with any back-office process that inserts sweep rows today (it may also use MAX+1) — open item.

## Appendix D — Source Findings

| ID | Severity | Service | Finding | Target action |
|---|---|---|---|---|
| F-01 | Critical | sweepInConfirmation | `sweepNo` joined into UPDATE … IN (…) — SQL injection. | Bind parameters; numeric validation. |
| F-02 | Critical | getCustodiansCash | `clientID` joined into IN (…) — SQL injection + any client's balance exposed. | Bind parameters; scope; ACL. |
| F-03 | High | pushProfitDistribution | `savePipelineToFile("tdfs")` active. | Remove. |
| F-04 | High | insertSweepData | `SWEEP_NO = MAX+1` without lock. | DB sequence. |
| F-05 | High | sweepInConfirmation | No transactions, no W guard, unknown numbers ignored. | Per-book tx, state guard, 409. |
| F-06 | High | pushProfitDistribution | No idempotency; unknown source silently dropped. | Mandatory unique referenceId; reject unknown source. |
| F-07 | Medium | getSweepIn | BETWEEN without TRUNC; no ORDER BY. | TRUNC / half-open range. |
| F-08 | Medium | getSweepOut | Two date formats. | Single ISO format. |
| F-09 | Medium (verify) | getCustodianPositions | 7 columns into 6 positional fields. | Name-based mapping; verify live. |
| F-10 | Medium | Swagger | Declared names differ from emitted keys. | Generate OpenAPI from DTOs. |
| F-11 | Medium | All | 1069/503/500/204-in-200 code misuse. | New MMF### codes; real HTTP statuses. |
| F-12 | High (verify) | insertSweepData | UPD_TIME string into DATE. | Server timestamp. |
| F-13 | Low | getMMFClientData | Dead null check; raw ISC messages. | Bean Validation messages. |
| F-14 | Low | getSweepIn | MMF leg uses getSweepOutFromMMF. | Single repository. |
| F-15 | High | Security | Gateway API key on Confluence; no IS ACL. | Rotate key; OAuth2. |
| F-16 | Low | getCustodianPositions | Hard-coded USR_CODE, market codes, sysdate-1. | Config + asOfDate. |
| F-17 | Low | pushProfitDistribution | Results keyed by accountNumber; raw errors returned. | Echo referenceId; codes only. |
| F-18 | Low | pushProfitDistribution | Amount allows negatives / unlimited scale; lenient date. | Strict validation. |
| F-19 | Low | Documentation | Confluence drift. | Regenerate docs. |

## Appendix E — Open Items & Assumptions

| # | Item | Type |
|---|---|---|
| 1 | Per-API code blocks under the `MMF` prefix. | Decision |
| 2 | Endpoint renames, notably `POST /sweep-ins/confirmations` vs `PATCH /sweep-ins`, and `book` as query parameter. | Decision |
| 3 | 207 Multi-Status for partial profit-distribution batches vs 200 with per-item results. | Decision |
| 4 | Empty collections return 200 with empty list instead of legacy 204; account lookup returns 404. | Decision |
| 5 | Confirmation semantics: all-or-nothing 409 vs per-item results; idempotent re-confirmation of `I` rows. | Decision |
| 6 | SWEEP_TYPE meanings (W/I/O/P) inferred from usage. | Verify |
| 7 | F-09 positional shift and F-12 `UPD_TIME` failure — confirm against production. | Verify |
| 8 | Types not in export: CB_CLIENT.CL_CLIENT_ID, F_PORT.QTY / PR_C_PRICE, return type of F_Get_Balance_As_Of. | Verify |
| 9 | Negative profit amounts valid? `JV_NO` / `USR_CODE` must be populated? | Business |
| 10 | Other writers to SWEEP_FACILITY and their key generation, before introducing a sequence. | Verify |
| 11 | Max date range and batch sizes (proposed 93 days / 1000 / 5000 / 500). | Decision |
| 12 | Disabled legacy code documented but inactive. | Info |
| 13 | Operational: rotate published gateway API key; delete `tdfs` file from IS hosts. | Action |
