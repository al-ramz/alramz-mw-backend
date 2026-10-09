# Intake Extraction: Relationship Manager Reporting

## 1. Source Document Reference

- **Document**: "AlRamzPortal Service — API Documentation & Integration Reference" (Al Ramz Capital, Middleware Migration Programme), v2.1 — "No-Data Responses Reclassified from 404 to 200 OK".
- **Source system**: Software AG webMethods Integration Server, package `AlRamzPortal` (built 2023-07-20), migrating to Spring Boot 3.x / Java 21 on Azure.
- **Services covered**: `getRelationshipManagers` (GET, no params) and `getCommissionsByRelationshipManager` (POST, optional date range + RM codes) — two read-only reporting endpoints over the Broker Insight Oracle schema (connection alias `RMZ:RMZ`, schema `INSIGHT`).
- **Known source-side gaps** (not this repo's problem to fix, but relevant to implementation risk): the live commissions adapter (`AlgoIntegrations.adapters:getCommissionsByRelationshipManager`) is in a package not included in this export — the SQL in the doc's Section 6.2 is a high-confidence reconstruction from an unused, identically-shaped local copy, not verified live source.

## 2. Target Placement

- **Service**: `services/reference-data-service` — **existing service**, chosen explicitly by the requester over `data-validation-service` (which already has a matching `brok` Oracle datasource configured) and over building a brand-new dedicated service. Not a new-service scaffold; `alramz-service-scaffold` is not needed.
- **Consequence of this choice**: `reference-data-service` currently has **no Oracle `brok` datasource configured at all** (only `middleware`, Postgres — confirmed by reading its `application-dev.yml`). Adding `company.datasource.brok.*` to every one of its profile files (`dev`/`test`/`preprod`/`prod`/`docker`) is now a required setup step for this feature, not optional. The starter's `brok` datasource type already exists (`BrokDataSourceAutoConfiguration` in `alramz-api-starter`) — this is *configuring* an existing capability, not adding a new one.
- **Connection values**: `data-validation-service`'s existing `brok` config (`jdbc:oracle:thin:@//172.25.1.218:1521/BROKDEV`, username `insight`) is strong repo-precedent evidence that this is the same Broker Insight/`INSIGHT` schema this intake doc describes — reuse those same per-environment values for `reference-data-service`'s new `brok` block. This is an assumption based on username/host match, not independently re-verified against the Oracle side — flagged in Data Mapping (Section 9) as worth a quick DBA confirmation, not treated as blocking.
- **Operation count**: two operations, one feature. Both share one clear business capability (relationship-manager reporting) per this skill's own merge criterion — extracted together below, kept separable per-endpoint.

## 3. Endpoint Identity

Controllers in this repo add `/api/v1` at the class level (`@RequestMapping("/api/v1")`, confirmed from `RedisCacheController`); OpenAPI spec paths themselves are bare.

| | Endpoint 1 | Endpoint 2 |
|---|---|---|
| **HTTP verb** | GET | POST |
| **Bare path** (in `specs/apiSpecs.yaml`) | `/relationship-managers` | `/relationship-managers/commissions` |
| **Full path** (served) | `/api/v1/relationship-managers` | `/api/v1/relationship-managers/commissions` |
| **Tag** | `RelationshipManagers` | `RelationshipManagers` |
| **operationId** | `getRelationshipManagers` | `getCommissionsByRelationshipManager` |

Both `operationId`s are kept as-is from the source doc — they're already meaningful REST operation names, not internal webMethods service-package codenames, so no rename is needed to fit this repo's naming style.

## 4. Security

**Resolved default: both endpoints are JWT-secured**, role `APP_REFERENCE_DATA` — the single role already used by every other `reference-data-service` endpoint (`RedisCacheController`, all five operations: `@JwtSecured(roles = "APP_REFERENCE_DATA")`). Neither endpoint is added to `company.jwt.permit-all-urls` (which today contains only `/api/v1/info`).

This explicitly does **not** carry over the source system's posture. The intake doc's Section 3.4 states neither legacy service enforces access control at the Integration Server layer (`check_internal_acls=no`, package `listACL` unset) — per this skill's own rule, that describes the *legacy* perimeter (an unknown/undocumented network boundary), not evidence that these endpoints should be public in the target. This repo secures everything by default; nothing in the source doc's migration notes recommends public access for the target design (it only flags the gap as something to confirm with the security/platform team) — so the default (secured) applies, not an exception.

## 5. Request Schema

### `getRelationshipManagers` (GET)
No request parameters — matches source exactly (Section 4.2).

### `getCommissionsByRelationshipManager` (POST)

| Field | Type | Required | Notes |
|---|---|---|---|
| `startDate` | `string` (`format: date`, ISO-8601 `YYYY-MM-DD`) | No | Legacy: `YYYYMMDD` string. Doc's own legacy→target call, adopted as-is (Section 5.2). Default when absent: `1900-01-01` (was `19000101`). |
| `endDate` | `string` (`format: date`) | No | Same as above. Default when absent: `2999-01-01` (was `29990101`). |
| `relationshipManagerCodes` | `array` of `string` | No | Legacy: comma-delimited single string (`"3030,3032"`). Doc's own legacy→target call, adopted as-is (Section 5.2, Appendix D Finding 10). Omitted/empty ⇒ no RM filter (all relationship managers). |

**Field-naming assumption worth confirming (not treated as blocking — see Section 11):** the source doc already recommends breaking wire-format changes for this endpoint (CSV→array, `YYYYMMDD`→ISO date), which implies the consuming AlRamzPortal frontend is expected to be updated in lockstep with this migration rather than preserved byte-for-byte. On that basis, this extraction also normalizes *response* field names to this repo's camelCase convention (Section 6) rather than the legacy spelling (`relationshipManagerID`, `NameEN`/`NameAR`). If the existing portal frontend must keep working unmodified against the new endpoint, every field name below needs to match the legacy spelling exactly instead.

## 6. Response Envelope

**This repo's real envelope does not match the source doc's assumed one — the real schema wins.**

Read directly from `services/reference-data-service/specs/apiSpecs.yaml`, `components.schemas.GenericResponse`:

```yaml
GenericResponse:
  type: object
  required: [responseCode, responseMessage, response]
  properties:
    responseCode: { type: string }      # e.g. "200"
    responseMessage: { type: string }   # e.g. "OK"
    response: { type: object }          # domain payload; null-shaped on error
    correlationId: { type: string, format: uuid }  # optional, present in schema
```

**Discrepancy from the source doc**: the doc's Section 2.3 assumes a *different* envelope — `responseCode`/`responseMessage` plus a separate nullable `errorCode`/`errorMsg` pair. This repo's actual `GenericResponse` has **no `errorCode`/`errorMsg` fields at all**. Neither of AlRamzPortal's own endpoints ever populates that pair in practice anyway (confirmed by the doc's own Sections 4.6.1/5.6.1 — "no remaining client-input-error condition"), so nothing of substance is lost — but `spec.md` must specify the real 3-field (+ optional `correlationId`) envelope, not the doc's assumed 5-field one.

- **`getRelationshipManagers` `response` payload**: `{ relationshipManagers: RelationshipManager[] }`, where `RelationshipManager = { id: integer, nameEn: string, nameAr: string }`. Source `RM_NO` is `NUMBER(4)` — typed `integer` in the target per the doc's own legacy-vs-target note (Section 4.5), not the legacy all-string type.
- **`getCommissionsByRelationshipManager` `response` payload**: `{ totalTradingVolume: number, totalReceivedCommission: number, results: CommissionResult[] }`, where `CommissionResult = { clientNumber: string, clientName: string, tradingVolume: number, receivedCommission: number }`.
- **Empty result**: both endpoints return **200 OK** with an empty array (`relationshipManagers: []`, or `results: []` + totals `0`) — not 404. This is a deliberate, already-reasoned target-design decision in the source doc (Section 2.3): both are collection/report endpoints, and a zero-row match is a normal outcome, not an error. Preserved as-is; no reason to relitigate a sound design decision.
- **On error** (500): `response` is `null`; no internal exception detail is ever included in it (see Section 10, Anti-Pattern 4).

## 7. Status Codes

| Status | Condition | Applies to |
|---|---|---|
| 200 | Success, including the empty-result case | Both |
| **400** | Malformed request body — invalid JSON, wrong type for `relationshipManagerCodes` elements, unparsable `startDate`/`endDate` | `getCommissionsByRelationshipManager` only |
| 500 | Any unhandled adapter/database exception (backend/provider failure) | Both |

**This diverges from the source doc's own conclusion** ("no remaining client-input-error condition," Sections 4.6.1/5.6.1) — that conclusion was about the *legacy Flow's* validation, which had none. It doesn't hold for the Spring Boot target: a malformed JSON body or an unparsable date string will trip this repo's own `@Valid`/Jackson binding failures regardless of what the legacy Flow did, and per this skill's own rule that 400 belongs in the extraction even though the source doc says otherwise. This also gives a **clean home for the source doc's own blank-string finding** (Section 5.2 — an empty `""` date bypasses the legacy default and previously surfaced as an untargeted 500): in the target, that case can be validated and rejected as 400 instead — see Open Question 3 below for the exact behavior to implement.

`getRelationshipManagers` takes no input, so it has no realistic 400 case.

## 8. Business Rules

Each rule is verbatim-derived from the doc's Business Logic Summary sections (4.3, 5.3), tagged per this skill's three-way scheme.

**`getRelationshipManagers`:**

1. **Preserve.** Query all rows from `CB_RELATION_MANAGER` (`RM_NO`, `RM_NAME_EN`, `RM_NAME_AR`), no filtering, no pagination.
2. **Preserve (target-design decision already made).** Empty result ⇒ 200 with `relationshipManagers: []`, not 404 (Section 2.3).
3. **Open Question (not defaulted).** `RM_DISABLES` (an inactive/retired flag on `CB_RELATION_MANAGER`) is never filtered by the legacy query — the source doc itself says to "confirm with business whether disabled relationship managers should be excluded." This is a genuine product question, not something either the request or repo precedent resolves — carried to Section 11.

**`getCommissionsByRelationshipManager`:**

4. **Preserve.** Default `startDate`/`endDate` to `1900-01-01`/`2999-01-01` (ISO equivalents of `19000101`/`29990101`) only when the field is **entirely absent** from the request.
5. **Defect — flagged, not resolved.** A caller-supplied empty string for `startDate`/`endDate` bypasses the absent-field default and (in the legacy system) reached `TO_DATE` unconverted, surfacing as an untargeted 500. The doc recommends "explicit blank-string handling" but doesn't specify the exact target behavior — carried to Open Question 3 (Section 11), with 400 (input validation) as the natural target-repo fit per Section 7 above.
6. **Preserve.** Split `relationshipManagerCodes` into individual RM codes (target: already an array, no client-side splitting needed); code `3031` is the Islamic trading office and must be filtered via a dedicated `SC_ISLAMIC = 'Y'` predicate on `CB_SEC_COMP`, not a plain `CL_RELATION_MANG = 3031` match; all other codes filter via a plain `CL_RELATION_MANG IN (...)` predicate.
7. **Defect — flagged, not resolved.** In the legacy query, the `3031`/Islamic-office predicate and the general `IN (...)` predicate are joined with **AND**, not **OR** — so requesting `3031` together with any other code produces a self-contradictory WHERE clause and silently returns an empty result instead of the union of both. The adapter's own SQL retains a commented-out original condition using **OR**, which the doc treats as evidence this is a regression, not the intended design, and recommends implementing the two predicates as OR (or issuing one query per group and merging). **This is not applied automatically** — per this skill's rule, a flagged defect is always carried forward as an explicit product decision, never silently fixed or silently replicated. Carried to Open Question 2 (Section 11).
8. **Preserve (target-design decision already made).** Empty result ⇒ 200 with `results: []` and both totals `0`, not 404 (Section 2.3) — same rationale as rule 2.
9. **Preserve.** Any unhandled adapter/database exception (including a validation failure that reaches the database layer) maps to 500, `response: null`.

## 9. Data Mapping

| Source (Oracle, `brok` datasource once configured) | Target |
|---|---|
| `INSIGHT.CB_RELATION_MANAGER.RM_NO` | `response.relationshipManagers[].id` (integer) |
| `INSIGHT.CB_RELATION_MANAGER.RM_NAME_EN` | `response.relationshipManagers[].nameEn` |
| `INSIGHT.CB_RELATION_MANAGER.RM_NAME_AR` | `response.relationshipManagers[].nameAr` |
| `CB_MAIN_CLIENT.CL_MAIN_CLIENT_ID` (joined via `INVOICE_HEADER` → `CB_CLIENT` → `CB_MAIN_CLIENT`) | `response.results[].clientNumber` |
| `CB_MAIN_CLIENT.CLE_CLIENT_NAME` | `response.results[].clientName` |
| `SUM(INVOICE_HEADER.TOTAL)` | `response.results[].tradingVolume` (also summed into `totalTradingVolume`) |
| `SUM(INVOICE_HEADER.LOC_OFFICE_COMM / 2)` | `response.results[].receivedCommission` (also summed into `totalReceivedCommission`) |
| `INVOICE_HEADER.INV_DATE` (via `CB_SEC_COMP.SC_COMP_ID` join) | filtered by `startDate`/`endDate` |
| `CB_SEC_COMP.SC_ISLAMIC` | Islamic-office predicate (rule 6/7 above) |

**Missing-datasource gap (not an open question — a required setup step, resolved by copying repo precedent):** `reference-data-service` needs a new `company.datasource.brok` block added to `application-{dev,test,preprod,prod,docker}.yml`, mirroring `data-validation-service`'s existing block (same connection alias identity: host `172.25.1.218:1521/BROKDEV`, username `insight`).

**Unverified query (flagged, not blocking spec authoring, but blocking for implementation sign-off):** the commissions query in the intake doc's Section 6.2 is explicitly a *reconstruction* from an unused local adapter copy, not the verified live `AlgoIntegrations` query. `03_implement`/QA should confirm the real query (joins, `total_comm <> 0.0` filter, column casing) against the actual live system or a DBA before this goes to production — the SQL as extracted here is a faithful transcription of the doc's Section 6.2, not independently re-verified.

## 10. Anti-Patterns to Exclude

| Source-doc finding | What to do instead in this repo |
|---|---|
| SQL built by `${var}` textual substitution, no bind parameters, no validation (Appendix D #1) | Use `@Qualifier("brokJdbcTemplate") JdbcTemplate` (or `NamedParameterJdbcTemplate`) with `?`/named bind parameters, and externalize the SQL via `SqlQueriesManager.getSQLQueryFromConfig("...")` reading from `src/main/resources/sql/sql-queries.xml` — the exact pattern already used by `NinTradingNumberValidationService` and `DfmOnboardingRepositoryImpl` in `data-validation-service` against this same `brok` datasource. |
| Totals accumulated via a string round-trip (`objectToString` → `addFloats` → `toNumber`) (Appendix D #7) | Aggregate with `BigDecimal` in the service layer (or let the SQL `SUM(...)` do it and map straight to `BigDecimal`/`double` — no string round-trip either way). |
| No access control at the integration-server layer (Appendix D #6) | `@JwtSecured(roles = "APP_REFERENCE_DATA")` on both new controller methods — see Section 4. |
| Possible internal exception (`lastError`) leak into the response body on 500 (Appendix D #4) | `reference-data-service` has **no `GlobalExceptionHandler` today** (confirmed — no `exception`/`handler` classes exist in its source tree). This feature is the first to need one for this service: add a typed exception (e.g. `RelationshipManagerLookupException`) + a `@RestControllerAdvice` that maps it, and any other unhandled exception, to 500 with `response: null` and a generic `responseMessage` — never the raw exception object — following the shape of `data-validation-service`'s `GlobalExceptionHandler` (see `.claude/howtos/exception-handling.md`), not its `OnboardingExceptionHandler` (documented there as dead code due to an `@Order` bug — don't copy that part). |
| Comma-delimited multi-value request field (Appendix D #10) | `relationshipManagerCodes: string[]` JSON array — see Section 5. |
| Legacy `YYYYMMDD` date strings | ISO-8601 `date` fields (`YYYY-MM-DD`) — see Section 5. |
| Hand-writing a `@RestController` route instead of implementing the generated OpenAPI interface (not from this doc — a real, separate pitfall already present in this exact service, `ApplicationController`'s `/api/v1/info`) | The new controller **must** `implements` the generated `RelationshipManagersApi` interface produced from `specs/apiSpecs.yaml`, per `.claude/howtos/openapi-contract-first-controllers.md` — don't repeat `ApplicationController`'s bypass of the codegen pattern. |

## 11. Open Questions for `01_elicit`

Only genuinely blocking items — everything else above is resolved and should be treated as settled going into `spec.md`.

1. **`RM_DISABLES` filtering** — should `getRelationshipManagers` exclude disabled/inactive relationship managers, or return all rows as the legacy query does? (Business Rule 3.)
2. **AND/OR defect for RM code `3031`** — should the target implementation fix the Islamic-office filter to OR (matching the commented-out original SQL and the doc's own recommendation), or deliberately preserve the legacy AND behavior for exact parity during transition? (Business Rule 7.) This changes real output for any caller combining `3031` with another RM code.
3. **Blank-date-string handling** — when `startDate`/`endDate` is present but an empty string, should the target reject it as 400 (recommended, and consistent with Section 7's status-code design), or silently treat it the same as an absent field (apply the default)? (Business Rule 5.)
4. **Legacy field-name compatibility** — does the existing AlRamzPortal frontend need the new endpoint's response field names to match the legacy spelling exactly (`relationshipManagerID`, `NameEN`/`NameAR`, etc.), or is this repo's camelCase convention (assumed throughout Section 6) acceptable because the frontend will be updated alongside this migration? This also affects whether `relationshipManagerCodes` (array) and ISO dates are viable request formats, or whether the legacy CSV/`YYYYMMDD` shapes must be accepted for compatibility.
5. **Cross-package adapter verification** — the commissions query (Section 9) is a documented reconstruction, not verified live source. Before this goes to production, someone needs access to the real `AlgoIntegrations.adapters:getCommissionsByRelationshipManager` package to confirm the SQL matches. Not blocking for writing `spec.md`/implementing against the reconstruction, but blocking for sign-off.
