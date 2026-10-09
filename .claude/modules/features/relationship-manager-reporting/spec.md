# Spec: Relationship Manager Reporting

Source: `.claude/modules/features/relationship-manager-reporting/intake-extraction.md` (webMethods
`AlRamzPortal` migration intake). All facts marked "resolved" in that document are treated as
settled here without re-derivation. This spec additionally resolves the 5 items the extraction
deliberately left open (Section 11), 4 of which were genuine blocking product/design decisions
answered by the requester (see Section 7 — Open Questions & Decisions) and 1 of which (cross-package
SQL verification) the extraction itself already determined is not blocking for spec authoring.

## 1. Feature Objective & Scope

**Problem.** AlRamzPortal's legacy webMethods `getRelationshipManagers` and
`getCommissionsByRelationshipManager` operations need a Spring Boot equivalent so the portal
frontend can list relationship managers and their commission/trading-volume reporting without the
legacy Integration Server package.

**Target service.** `services/reference-data-service` — an existing service, chosen by the
requester over `data-validation-service` (which already has a matching `brok` Oracle datasource)
and over a brand-new service. `alramz-service-scaffold` is **not** needed; this spec stays inside
`reference-data-service`'s existing Maven module.

**Two endpoints, one feature:**
1. `GET /relationship-managers` — list relationship managers.
2. `POST /relationship-managers/commissions` — commissions/trading-volume report, optionally
   filtered by date range and/or relationship-manager codes.

**Done looks like:** both endpoints exist behind JWT auth in `reference-data-service`, backed by a
newly-configured `brok` Oracle datasource, return the shapes defined in Section 3, and the four
previously-open business-rule questions (RM_DISABLES filtering, the AND/OR Islamic-office defect,
blank-date-string handling, legacy field-name compatibility) are implemented exactly as decided in
Section 7 — not left as TODOs in the code.

**Explicit out of scope:**
- Any change to `data-validation-service` (it is not the target; it's cited only as datasource/SQL
  precedent).
- Building `alramz-service-scaffold` output or a new service.
- Writing/porting the legacy webMethods `AlgoIntegrations.adapters:getCommissionsByRelationshipManager`
  package itself — this spec re-implements its logic in Spring Boot from the intake doc's
  reconstructed SQL (Section 4 below), not from the live legacy source (unavailable/unverified —
  see Section 4's "unverified query" note and Section 9's residual risk).
- Any UI/frontend change to AlRamzPortal — out of scope for this backend spec, but Section 7 (Q4)
  records the assumption that the frontend will be updated in lockstep with this migration's wire
  format.
- Revisiting `services/reference-data-service/service.yaml` sizing (cpu `0.5`, memory `1Gi`,
  `minReplicas: 1`/`maxReplicas: 5`) — flagged as a question worth asking the platform team once
  real commissions-query load is known (an aggregation join over `INVOICE_HEADER` could be
  non-trivial), not something this spec changes.

## 2. Repository Grounding

- **Contract file**: `services/reference-data-service/specs/apiSpecs.yaml` — exists today with
  `/info`, `/health/deep`, and four `/cache/entries/...` operations, all under a spec-root
  `security: - BearerAuth: []`. This spec **amends** that file; it is not a new file.
- **Real `GenericResponse` envelope** (`apiSpecs.yaml` `components.schemas.GenericResponse`, lines
  193-215): `responseCode` (string), `responseMessage` (string), `response` (untyped `object`),
  optional `correlationId` (uuid). No `errorCode`/`errorMsg` pair exists — the intake doc's assumed
  5-field envelope does not apply; this spec uses the real 3(+1) field shape throughout.
- **Codegen plugin**: `services/reference-data-service/pom.xml:179-212` — same
  `openapi-generator-maven-plugin` config as `data-validation-service`
  (`interfaceOnly=true`, `apiPackage=com.alramz.api`, `modelPackage=com.alramz.model`). A new
  `RelationshipManagers` tag generates `com.alramz.api.RelationshipManagersApi` at build time.
- **Controller precedent**: `services/reference-data-service/src/main/java/com/alramz/controllers/RedisCacheController.java`
  — `implements <Tag>Api`, one `@JwtSecured(roles = "APP_REFERENCE_DATA")` + `@Loggable` per
  method, class-level `@RequestMapping("/api/v1")`. The new controller follows this exact shape.
- **Package layout precedent**: `reference-data-service` already uses a flat layer-based layout
  (`com.alramz.controllers`, `com.alramz.config`, `com.alramz.service`, `com.alramz.scheduler.job`),
  not per-feature subpackages — this spec follows that sibling convention rather than introducing a
  new `com.alramz.relationshipmanager.*` package family, adding `com.alramz.repository` (new
  package, named to match `data-validation-service`'s own `com.alramz.repository`) and
  `com.alramz.exception` (new package — see below).
- **Datasource precedent**: `services/data-validation-service/src/main/resources/application-{dev,test,preprod,docker}.yml`
  each already declare (identically, `application-dev.yml:140-156` shown, `-test`/`-preprod` are
  byte-identical, `-docker` uses `${COMPANY_DATASOURCE_BROK_*}` env-var interpolation with the same
  defaults, `application-docker.yml:62-72`):
  ```yaml
  company:
    datasource:
      brok:
        enabled: true
        url: jdbc:oracle:thin:@//172.25.1.218:1521/BROKDEV
        username: insight
        plainPassword: RAMZ
        driverClassName: oracle.jdbc.OracleDriver
        startup-validation: { enabled: false }
        pool: { maximum-pool-size: 10, minimum-idle: 2 }
        sql-logging: { enabled: false }
        oracle: { connectTimeout: 10000, readTimeout: 60000, defaultRowPrefetch: 100 }
  ```
  `services/reference-data-service/src/main/java/com/alramz/config/DatasourceConfiguration.java`
  confirms `reference-data-service` today only wires `middleware` (Postgres) as `@Primary` — no
  `brok` block exists yet in any of its `application-{dev,test,preprod,prod,docker}.yml`. Adding
  this block (mirroring the values above verbatim, per the extraction's own confirmed assumption
  that this is the same `RMZ:RMZ`/`INSIGHT` schema) is a required setup step for this feature.
  **Residual note**: `data-validation-service` uses `plainPassword` (dev-only per CLAUDE.md §4)
  even in its `preprod` profile — this spec mirrors that existing (imperfect) precedent rather than
  unilaterally introducing `cipher.password` + encrypted `password`/`passwordVector` for
  `reference-data-service`'s `brok` block, since CLAUDE.md instructs copying real repo precedent,
  not inventing a stricter pattern the sibling service doesn't itself follow. Flagged for the
  platform/security team, not fixed here.
- **SQL-externalization precedent**: `services/data-validation-service/src/main/java/com/alramz/service/impl/NinTradingNumberValidationService.java`
  (`@Qualifier("brokJdbcTemplate") JdbcTemplate` + `SqlQueriesManager.getSQLQueryFromConfig(...)`)
  and `services/data-validation-service/src/main/java/com/alramz/repository/DfmOnboardingRepositoryImpl.java`
  (`@Qualifier("middlewareNamedParameterJdbcTemplate") NamedParameterJdbcTemplate` +
  `SqlQueriesManager`, `@Repository` implementing a plain interface) are the two patterns this
  feature's repository layer follows — `NamedParameterJdbcTemplate` specifically, because the
  commissions query needs a dynamic `IN (:codes)` expansion for `relationshipManagerCodes`, which
  named-parameter binding handles far more cleanly than positional `?` placeholders.
  `services/reference-data-service/src/main/resources/sql/sql-queries.xml` already exists (an
  empty `<properties></properties>` scaffold) and is already registered via
  `services/reference-data-service/src/main/resources/application.yml:13-14`
  (`sql.file: classpath:sql/global-configuration-settings-queries.xml,classpath:sql/sql-queries.xml`)
  — new query entries go directly into this existing file; no new `sql.file` registration needed.
- **Exception-handling precedent**: per `.claude/howtos/exception-handling.md` §2/§5,
  `reference-data-service` has **no** `exception` package and **no** `GlobalExceptionHandler`
  today — only the starter's generic `LoggingExceptionHandler` safety net
  (`services/alramz-api-starter/src/main/java/com/alramz/logging/exception/LoggingExceptionHandler.java`,
  `@Order(LOWEST_PRECEDENCE)`). This feature is the first in this service to need the service-owned
  layer; it must be added following `data-validation-service`'s `GlobalExceptionHandler.java` shape
  (`@RestControllerAdvice(basePackages = "com.alramz.controllers")`, `@Order(0)`, one
  `@ExceptionHandler` per typed exception plus `MethodArgumentNotValidException` and
  `HttpMessageNotReadableException` handlers plus a catch-all `Exception` handler) — explicitly
  **not** `OnboardingExceptionHandler`'s pattern (documented dead code due to an `@Order` bug).
- **JWT / `permit-all-urls`**: `services/reference-data-service/src/main/resources/application-dev.yml:75-84`
  — `company.jwt.permit-all-urls` today contains only `/api/v1/info`. Both new endpoints stay
  authenticated; neither is added to this list (see Section 5).
- **Role precedent**: every existing `reference-data-service` operation in `RedisCacheController`
  uses `@JwtSecured(roles = "APP_REFERENCE_DATA")` — reused as-is (Section 5 of the extraction),
  no new role.
- **Masking precedent**: `services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java:23-28`
  — the hardcoded sensitive-key set covers tokens/passwords/EID/passport fields
  (`cust_nin`, `eid_no`, `passportnumber`, `pp_no`, etc.). None of this feature's fields
  (`clientNumber`, `clientName`, `nameEn`/`nameAr`, trading volumes/commissions) match an existing
  key. See Section 5 for the residual (non-blocking) compliance note on `clientNumber`.

## 3. Interface Contracts

Both operations amend `services/reference-data-service/specs/apiSpecs.yaml`. Tag:
`RelationshipManagers` (new tag in this file, matching the file's existing per-feature tag style —
`Info`, `Health`, `RedisCache`).

### 3a. `GET /relationship-managers`

| Fact | Value | Resolution |
|---|---|---|
| Bare path | `/relationship-managers` | From intake doc, no rename needed |
| Full served path | `/api/v1/relationship-managers` | Controller-level `@RequestMapping("/api/v1")` |
| `operationId` | `getRelationshipManagers` | Kept as-is from source (already meaningful) |
| Security | JWT-secured, `@JwtSecured(roles = "APP_REFERENCE_DATA")` | Repo precedent (`RedisCacheController`), standard default applied |
| Request | none | Matches legacy exactly |
| Response envelope | `GenericResponse` | Standard default, real envelope from `apiSpecs.yaml` |

**Response payload shape** (`response` field): `RelationshipManagerListResult` —
```json
{ "relationshipManagers": [ { "id": 101, "nameEn": "John Smith", "nameAr": "جون سميث" } ] }
```
`id` is `integer` (source `RM_NO NUMBER(4)`), `nameEn`/`nameAr` are `string`.

**Business rule applied (Q1, Section 7)**: only relationship managers **not** flagged disabled are
returned — the query adds a predicate excluding `RM_DISABLES`-flagged rows (exact column value
semantics, e.g. `'Y'`/`'N'` vs. `1`/`0`, to be confirmed against the live `CB_RELATION_MANAGER`
schema during implementation — a single-predicate SQL detail, not a contract-shape risk). This is a
**deliberate behavior change from the legacy system**, which never filtered on this flag.

**Status codes and examples** (every one below is real, not boilerplate — no `503` since this
endpoint calls only the internal Oracle `brok` datasource, not an external HTTP system):

| Status | Condition | Example |
|---|---|---|
| 200 | Success, including zero active RMs (empty array — Section 6, preserved target-design decision) | `{"responseCode":"200","responseMessage":"OK","response":{"relationshipManagers":[{"id":101,"nameEn":"John Smith","nameAr":"جون سميث"}]}}` |
| 401 | Missing/invalid JWT (starter's `JwtAuthenticationFilter`, unchanged repo behavior) | `{"responseCode":"401","responseMessage":"Unauthorized","response":null}` |
| 500 | Any unhandled `brok` datasource/query failure, mapped from `RelationshipManagerLookupException` (Section 6) | `{"responseCode":"500","responseMessage":"Unable to retrieve relationship managers","response":null,"correlationId":"550e8400-e29b-41d4-a716-446655440000"}` |

### 3b. `POST /relationship-managers/commissions`

| Fact | Value | Resolution |
|---|---|---|
| Bare path | `/relationship-managers/commissions` | From intake doc |
| Full served path | `/api/v1/relationship-managers/commissions` | Controller-level prefix |
| `operationId` | `getCommissionsByRelationshipManager` | Kept as-is |
| Security | JWT-secured, `@JwtSecured(roles = "APP_REFERENCE_DATA")` | Same role, same rationale as 3a |
| Response envelope | `GenericResponse` | Standard default |

**Request schema** (`CommissionsByRelationshipManagerRequest`, all fields optional):

| Field | Type | Format decision (Q4) | Default when absent | Default when blank string (Q3) |
|---|---|---|---|---|
| `startDate` | `string` | ISO-8601 `YYYY-MM-DD` (modern convention, not legacy `YYYYMMDD`) | `1900-01-01` | Treated as absent → `1900-01-01` |
| `endDate` | `string` | Same | `2999-01-01` | Treated as absent → `2999-01-01` |
| `relationshipManagerCodes` | `array` of `string` | JSON array (not legacy CSV) | no RM filter (all RMs) | n/a (array, not a date) |

**Design note — why `startDate`/`endDate` are OpenAPI `type: string` (not `format: date`)**: to
implement Q3's resolution (blank string ⇒ treated exactly like an absent field) without depending
on undocumented Jackson `jsr310`/`LocalDate`-deserializer empty-string behavior (this repo's
`services/reference-data-service/src/main/java/com/alramz/config/JacksonConfig.java` sets no
`ACCEPT_EMPTY_STRING_AS_NULL_OBJECT` override), the generated request DTO field stays a plain
`String`. `performBeanValidation=true` (already the plugin's config, `pom.xml:179-212`) lets the
schema declare `pattern: "^$|\\d{4}-\\d{2}-\\d{2}"` (empty string OR ISO date), which the generator
turns into a Jakarta `@Pattern` annotation — a non-blank, non-ISO string (e.g. `"20240101"` or
`"not-a-date"`) fails this pattern and is caught by the same `MethodArgumentNotValidException`
handler `data-validation-service`'s `GlobalExceptionHandler` already demonstrates
(`GlobalExceptionHandler.java:55-68`), producing a 400. The service layer then does the
blank-vs-absent-vs-value logic explicitly: `isBlank() → apply default`, else `LocalDate.parse(value)`
(a calendar-invalid-but-pattern-matching value like `2024-13-45` throws `DateTimeParseException`
here, caught and rethrown as the typed `InvalidDateRangeException`, Section 6 → 400).

**Business rules applied**:
- **Q2 (Section 7)**: the Islamic-office predicate (`SC_ISLAMIC = 'Y'` for RM code `3031`) and the
  general `CL_RELATION_MANG IN (:codes)` predicate are joined with **AND**, preserving the legacy
  defect byte-for-byte. Requesting `3031` together with any other RM code continues to silently
  return an empty `results` array (200, not an error) — see Section 6 for the explicit edge case
  and Section 7 for the decision record and the recommended follow-up.
- RM code `3031` alone still resolves via the dedicated `SC_ISLAMIC = 'Y'` predicate (Business Rule
  6, preserved).

**Response payload shape** (`response` field): `RelationshipManagerCommissionsSummary` —
```json
{
  "totalTradingVolume": 187500.75,
  "totalReceivedCommission": 937.50,
  "results": [
    { "clientNumber": "C-10293", "clientName": "Acme Trading LLC", "tradingVolume": 125000.50, "receivedCommission": 625.25 }
  ]
}
```
Totals are summed in the service layer using `BigDecimal` over the per-row `CommissionResult`s
returned by the repository (per the anti-pattern table, no string round-trip) — not a second SQL
round trip.

**Status codes and examples** (no `503`; same rationale as 3a — this endpoint calls only the
internal `brok` datasource):

| Status | Condition | Example |
|---|---|---|
| 200 | Success, including the empty-result case (`3031` + another code, or a genuinely empty match — both 200 per the preserved target-design decision) | `{"responseCode":"200","responseMessage":"OK","response":{"totalTradingVolume":0,"totalReceivedCommission":0,"results":[]}}` |
| 400 | Malformed JSON body; `relationshipManagerCodes` element of the wrong JSON type (e.g. a number instead of a string); a non-blank `startDate`/`endDate` that fails the ISO-date pattern or fails `LocalDate.parse` (e.g. `2024-13-45`) | `{"responseCode":"400","responseMessage":"startDate: must be blank or a valid ISO-8601 date (YYYY-MM-DD)","response":null}` |
| 401 | Missing/invalid JWT | `{"responseCode":"401","responseMessage":"Unauthorized","response":null}` |
| 500 | Any unhandled `brok` datasource/query failure | `{"responseCode":"500","responseMessage":"Unable to retrieve commission report","response":null,"correlationId":"550e8400-e29b-41d4-a716-446655440000"}` |

No internal/business error code field exists on either response — the real `GenericResponse` has
no such field (Section 2), matching this repo's existing `GenericResponse` operations, not
`OnboardingResponse`'s `internal_error_code` convention.

### 3c. Java method signatures (new, hand-written — alongside the generated `RelationshipManagersApi`)

```java
// com.alramz.controllers.RelationshipManagerController
@RestController
@RequestMapping("/api/v1")
public class RelationshipManagerController implements RelationshipManagersApi {
    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> getRelationshipManagers() { ... }

    @Override
    @JwtSecured(roles = "APP_REFERENCE_DATA")
    @Loggable
    public ResponseEntity<GenericResponse> getCommissionsByRelationshipManager(
            CommissionsByRelationshipManagerRequest request) { ... }
}

// com.alramz.service.RelationshipManagerService
public interface RelationshipManagerService {
    RelationshipManagerListResult getAllRelationshipManagers();
    RelationshipManagerCommissionsSummary getCommissionsByRelationshipManager(
            CommissionsByRelationshipManagerRequest request);
}

// com.alramz.repository.RelationshipManagerRepository
public interface RelationshipManagerRepository {
    List<RelationshipManager> findActiveRelationshipManagers();
    List<CommissionResult> findCommissionsByRelationshipManager(
            LocalDate startDate, LocalDate endDate, List<String> relationshipManagerCodes);
}
```
(`RelationshipManager`, `CommissionResult`, `RelationshipManagerListResult`,
`RelationshipManagerCommissionsSummary`, `CommissionsByRelationshipManagerRequest` are the OpenAPI
schema-generated model classes named in Section 3a/3b — reused directly as the repository/service
return types, following `data-validation-service`'s convention of using generated models as real
domain types rather than a parallel hand-written DTO family.)

## 4. Data Model & Persistence Changes

**No Liquibase changeset is needed.** `reference-data-service`'s own Liquibase-managed schema
(`src/main/resources/db/changelog/{dev,test,preprod,prod,docker}/`, currently at
`003-rename-to-application-workflow-locks.sql` as the latest numbered file across profiles) is its
`middleware` Postgres database, used for cache/audit/lock tables — this feature touches none of
that. All data for this feature is read-only against the pre-existing Oracle `INSIGHT` schema via
the new `brok` datasource (Section 2); those tables (`CB_RELATION_MANAGER`, `CB_SEC_COMP`,
`INVOICE_HEADER`, `CB_MAIN_CLIENT`, `CB_CLIENT`) are not owned or migrated by this service.

**Required setup (not a changeset, a config addition — Section 5)**: add `company.datasource.brok`
to `application-{dev,test,preprod,prod,docker}.yml`, values mirrored from
`data-validation-service`'s existing block (Section 2).

**New SQL, externalized into the existing `services/reference-data-service/src/main/resources/sql/sql-queries.xml`**
(currently an empty `<properties></properties>` scaffold, already registered via `application.yml`'s
`sql.file` — no new registration needed):

- `relationship.manager.find.active` — `SELECT RM_NO, RM_NAME_EN, RM_NAME_AR FROM CB_RELATION_MANAGER WHERE <RM_DISABLES exclusion predicate — Q1>`.
- `relationship.manager.commissions.find` — joins `INVOICE_HEADER` → `CB_CLIENT` → `CB_MAIN_CLIENT`
  for client identity, `CB_SEC_COMP` for the `SC_ISLAMIC` Islamic-office predicate, filtered by
  `INV_DATE BETWEEN :startDate AND :endDate`, and by RM codes via the AND-preserved predicate pair
  described in Section 3b (Q2) — bound entirely through named parameters
  (`NamedParameterJdbcTemplate`), never string concatenation (anti-pattern table, Section 2).

**Residual risk carried forward from the intake extraction, not resolved by this spec** (extraction
Section 9/11, item 5 — explicitly **not** blocking for spec authoring per the extraction's own
conclusion): the commissions query above is a faithful transcription of the intake doc's Section 6.2
reconstruction, not the verified live `AlgoIntegrations.adapters:getCommissionsByRelationshipManager`
SQL (that package wasn't in the migration export). `03_implement`/QA must confirm the real query
(joins, any `total_comm <> 0.0`-style filters, exact column casing) against the live system or a DBA
**before production sign-off** — implementing against the reconstruction now is acceptable per the
extraction's own determination.

## 5. Security & Config Impact

- **`permit-all-urls`**: **no change** — both endpoints stay authenticated by default;
  `company.jwt.permit-all-urls` in every `reference-data-service` profile continues to list only
  `/api/v1/info` (Section 2).
- **Role**: `APP_REFERENCE_DATA`, reused as-is — no new role introduced.
- **New config**: `company.datasource.brok.*` block added to
  `application-{dev,test,preprod,prod,docker}.yml`, values mirrored from `data-validation-service`
  (Section 2) — `enabled`, `url`, `username`, `plainPassword`, `driverClassName`,
  `startup-validation.enabled`, `pool.{maximum-pool-size,minimum-idle}`, `sql-logging.enabled`,
  `oracle.{connectTimeout,readTimeout,defaultRowPrefetch}`, all under the `company.datasource.brok`
  prefix that `BrokDataSourceAutoConfiguration` (in `alramz-api-starter`) already understands — this
  is configuring an existing starter capability, not building a new one.
- **Datasource injection**: repository/service code must inject
  `@Qualifier("brokNamedParameterJdbcTemplate") NamedParameterJdbcTemplate` (or
  `@Qualifier("brokJdbcTemplate") JdbcTemplate` if named-parameter binding isn't needed for a given
  query) — never an unqualified `JdbcTemplate`/`DataSource`, since `reference-data-service` will now
  have two datasources (`middleware` `@Primary`, `brok` new).
- **Masking**: no `SensitiveDataMasker` key-set addition required — none of this feature's fields
  (`clientNumber`, `clientName`, `nameEn`/`nameAr`, `tradingVolume`, `receivedCommission`) match the
  existing sensitive-key set (`SensitiveDataMasker.java:23-28`). **Residual, non-blocking note**:
  `clientNumber` (source `CL_MAIN_CLIENT_ID`) is a client identifier that a compliance/risk review
  may independently want masked in logs even though it isn't in today's hardcoded key set — flagged
  for that team's awareness, not treated as a spec-blocking gap (extending the key set is a cheap,
  reversible follow-up if asked for).
- **Config prefix for the two new SQL query keys**: not a Spring `@ConfigurationProperties` prefix —
  they're `sql-queries.xml` entries (Section 4), loaded via the existing `sql.file` list in
  `application.yml`, no new prefix needed.

## 6. Edge Cases & Error Behaviors

| # | Edge case | Endpoint(s) | Typed exception | HTTP status / body |
|---|---|---|---|---|
| 1 | Zero active relationship managers match (all disabled, or table empty) | `GET /relationship-managers` | none (empty result is success) | 200, `{"relationshipManagers":[]}` |
| 2 | `startDate`/`endDate` entirely absent | POST commissions | none | 200, defaults `1900-01-01`/`2999-01-01` applied |
| 3 | `startDate`/`endDate` present as `""` (blank) | POST commissions | none (Q3: treated as absent) | 200, same defaults as #2 applied silently |
| 4 | `startDate`/`endDate` present, non-blank, fails the ISO-date pattern (e.g. `"20240101"`) | POST commissions | `MethodArgumentNotValidException` (Jakarta `@Pattern` failure — no new typed exception needed, reuses `data-validation-service`'s existing handled type) | 400, `GenericResponse` with `responseMessage` describing the field |
| 5 | `startDate`/`endDate` matches the ISO pattern but is not a real calendar date (e.g. `"2024-13-45"`) | POST commissions | `InvalidDateRangeException` (new, `com.alramz.exception`) | 400 |
| 6 | `relationshipManagerCodes` element has the wrong JSON type (e.g. a number) | POST commissions | `HttpMessageNotReadableException` (Jackson binding failure) | 400 |
| 7 | `relationshipManagerCodes` omitted or `[]` | POST commissions | none | 200, no RM filter applied (all RMs) |
| 8 | `relationshipManagerCodes` contains `"3031"` alone | POST commissions | none | 200, Islamic-office predicate only |
| 9 | `relationshipManagerCodes` contains `"3031"` **and** another code | POST commissions | none (Q2: preserved AND defect, not an error) | 200, `{"totalTradingVolume":0,"totalReceivedCommission":0,"results":[]}` — **not** a union of both groups, by deliberate decision (Section 7) |
| 10 | Zero commission rows match the given filters (any other reason) | POST commissions | none | 200, same empty-totals shape as #9 |
| 11 | `brok` Oracle datasource unreachable, or any SQL/JDBC exception | Both | `RelationshipManagerLookupException` (new, `com.alramz.exception`) | 500, `response: null`, no raw exception detail in body (anti-pattern table, Section 2) |
| 12 | Missing/invalid/expired JWT | Both | (starter's JWT filter, unchanged) | 401 |
| 13 | Malformed JSON request body (POST only) | POST commissions | `HttpMessageNotReadableException` | 400 |

Every status code named in Section 3 (200/401/500 for GET; 200/400/401/500 for POST) has a
corresponding row above, and every row above maps to a status code named in Section 3 — no
mismatch between the two sections.

## 7. Open Questions & Decisions

All 4 items below were genuine blocking gaps per the "Resolving Blocking Gaps" test (no repo
precedent either way, materially affect the Interface Contract or Data Model, expensive to reverse
once implemented) and were put to the requester directly; answers below are now treated as settled
precedent for this feature, not open items.

**Q1 — RM_DISABLES filtering (`getRelationshipManagers`, Business Rule 3).**
Options offered: (A, recommended) preserve legacy behavior, no filter, return all rows; (B) exclude
disabled RMs via a `RM_DISABLES` predicate; (C) return all rows plus an `active` field, let the
frontend filter.
**Decision: Option B.** Disabled relationship managers are excluded from `GET /relationship-managers`.
This is a **deliberate behavior change from the legacy system**, which never filtered on this flag —
called out explicitly in Section 3a and Section 6 (edge case #1), not silently introduced.

**Q2 — AND/OR defect for RM code `3031` (Business Rule 7).**
Options offered: (A, recommended) fix to OR, matching the commented-out original SQL and the
intake doc's own recommendation; (B) preserve the legacy AND behavior for byte-for-byte parity;
(C) reject a mixed `3031` + other-code request with 400 instead of either silent behavior.
**Decision: Option B.** The AND join is preserved exactly as today's production behavior —
requesting `3031` together with any other RM code continues to silently return an empty result
(Section 3b, Section 6 edge case #9). **This is a known, deliberately-preserved defect, not a
silent replication** — documented here with a paper trail, and a follow-up ticket to revisit the
AND→OR fix is recommended (out of scope for this feature's implementation).

**Q3 — Blank-date-string handling (Business Rule 5).**
Options offered: (A, recommended) reject as 400; (B) silently treat as absent, apply the default.
**Decision: Option B.** An empty string for `startDate`/`endDate` is treated identically to an
absent field — the `1900-01-01`/`2999-01-01` default is applied silently, no error. Implemented via
the `type: string` + `pattern: "^$|\\d{4}-\\d{2}-\\d{2}"` design in Section 3b, which still rejects
genuinely malformed (non-blank) date strings as 400 (edge case #4) — only the blank case is
special-cased to defaulting.

**Q4 — Legacy field-name / wire-format compatibility.**
Options offered: (A, recommended) modern camelCase + JSON array + ISO dates, assuming the
AlRamzPortal frontend is updated in lockstep; (B) preserve legacy field names/formats exactly
(`relationshipManagerID`, `NameEN`/`NameAR`, CSV string, `YYYYMMDD`); (C) hybrid — modern canonical
contract, but lenient acceptance of legacy request formats too.
**Decision: Option A.** All field names and formats in Section 3 (`id`/`nameEn`/`nameAr`,
`relationshipManagerCodes` as a JSON array, ISO-8601 dates) are final as drafted — no legacy-spelling
or CSV/`YYYYMMDD` compatibility shim is implemented. This assumes AlRamzPortal's frontend is updated
alongside this migration.

**Not re-opened (extraction's own non-blocking determination, carried forward, not asked):**
cross-package adapter SQL verification (intake doc Section 11, item 5) remains an unresolved
verification task for production sign-off, not a spec-blocking decision — see Section 4's residual
risk note and Section 9 below.

## 8. Explicit Acceptance Criteria

- [ ] `services/reference-data-service/specs/apiSpecs.yaml` contains a new `RelationshipManagers`
      tag with `GET /relationship-managers` (`operationId: getRelationshipManagers`) and
      `POST /relationship-managers/commissions` (`operationId: getCommissionsByRelationshipManager`),
      both under the file's existing `security: - BearerAuth: []`.
  - [ ] `components.schemas` adds `RelationshipManager`, `RelationshipManagerListResult`,
      `CommissionResult`, `RelationshipManagerCommissionsSummary`, and
      `CommissionsByRelationshipManagerRequest`, each with a filled-in `example`.
  - [ ] Every status code in Section 3's tables (200/401/500 for GET; 200/400/401/500 for POST) is
      present in the spec with a real example body, not a bare `description`.
- [ ] `company.datasource.brok` is present, enabled, and configured identically to
      `data-validation-service`'s block in every one of
      `application-{dev,test,preprod,prod,docker}.yml` for `reference-data-service`.
- [ ] `GET /api/v1/relationship-managers` returns only relationship managers not flagged disabled
      (Q1) — verified by a test asserting a disabled-flagged RM is excluded from the response.
- [ ] `POST /api/v1/relationship-managers/commissions` with `relationshipManagerCodes: ["3031", "<other>"]`
      returns `200` with `results: []` and both totals `0` (Q2 preserved-defect behavior) — verified
      by a test asserting this exact empty-result behavior, not a union.
- [ ] `POST /api/v1/relationship-managers/commissions` with `startDate: ""` (or `endDate: ""`)
      behaves identically to omitting the field entirely (Q3) — verified by a test comparing both
      request variants produce the same result.
- [ ] `POST /api/v1/relationship-managers/commissions` with a non-blank, non-ISO `startDate` (e.g.
      `"20240101"`) or a syntactically-ISO-but-invalid date (e.g. `"2024-13-45"`) returns `400`.
- [ ] Response field names are exactly `id`/`nameEn`/`nameAr` (GET) and
      `clientNumber`/`clientName`/`tradingVolume`/`receivedCommission`/`totalTradingVolume`/`totalReceivedCommission`/`results`
      (POST) — no legacy spelling (Q4).
- [ ] `com.alramz.exception.GlobalExceptionHandler` (new,
      `@RestControllerAdvice(basePackages = "com.alramz.controllers")`, `@Order(0)`) exists in
      `reference-data-service`, handling `RelationshipManagerLookupException` (→500),
      `InvalidDateRangeException` (→400), `MethodArgumentNotValidException` (→400),
      `HttpMessageNotReadableException` (→400), and a catch-all `Exception` (→500) — none of them
      leaking raw exception detail into the response body.
  - [ ] `com.alramz.exception.RelationshipManagerLookupException` and
      `com.alramz.exception.InvalidDateRangeException` exist as typed `RuntimeException` subclasses.
- [ ] `com.alramz.repository.RelationshipManagerRepository`/`RelationshipManagerRepositoryImpl`
      inject `@Qualifier("brokNamedParameterJdbcTemplate") NamedParameterJdbcTemplate`, never an
      unqualified `JdbcTemplate`/`DataSource`.
- [ ] Both SQL queries are externalized in `services/reference-data-service/src/main/resources/sql/sql-queries.xml`
      (keys `relationship.manager.find.active`, `relationship.manager.commissions.find`), loaded via
      `SqlQueriesManager.getSQLQueryFromConfig(...)`, bound with named parameters — no string
      concatenation of user input into SQL text.
  - [ ] `totalTradingVolume`/`totalReceivedCommission` are computed via `BigDecimal` summation in
      the service layer (or SQL `SUM`), never via a string round-trip.
- [ ] Neither endpoint's path is added to `company.jwt.permit-all-urls` in any profile.
- [ ] `mvn -pl services/reference-data-service -am clean test` passes, including new controller
      tests exercising each row of Section 6's edge-case table.

## 9. Self-Critique

**Confidence: medium.**

**Residual, non-blocking assumptions made:**
- The exact `RM_DISABLES` column value semantics (`'Y'`/`'N'` vs. `1`/`0`/`NULL`-means-active) are
  assumed, not verified against the live Oracle schema — a single-predicate SQL detail to confirm
  during `03_implement`, not a contract-shape risk.
- `data-validation-service`'s `brok` connection values (host/port/SID, username `insight`) are
  reused for `reference-data-service` on the strength of the extraction's own username/host-match
  reasoning, not an independently re-verified DBA confirmation (extraction Section 2) — carried
  forward as-is per the extraction's own non-blocking classification.
- The cross-package commissions-adapter SQL reconstruction (Section 4/9) is implemented as
  documented, with production sign-off explicitly gated on separate live-source verification — not
  re-litigated here since the extraction already concluded this doesn't block spec authoring.
- The `type: string` + regex-pattern design for `startDate`/`endDate` (Section 3b) is this spec's
  own technical choice to implement Q3 cleanly, not a fact taken directly from the intake doc or an
  existing repo precedent for exactly this scenario — a reasonable, low-risk design decision
  (doesn't change the wire format) but worth a second look in `02_plan`/`03_implement` if a
  reviewer prefers `format: date` + a custom Jackson deserializer instead.
- `clientNumber` masking (Section 5) is flagged for compliance awareness but not acted on, since
  it doesn't match `SensitiveDataMasker`'s existing key set and no request/precedent says to extend
  it.

**Weakest part of this spec:** the commissions query's exact SQL (joins, filters, column casing) is
still unverified against a live source per the extraction's own admission — this spec is
implementation-ready against the *reconstructed* query, but a DBA/source-system confirmation step
remains a real, externally-gated risk before production sign-off, independent of anything `01_elicit`
through `04_security` can resolve internally.
