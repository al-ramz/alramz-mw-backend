# Spec — mmfintegrations (MMF / Athena sweep and custodian integration)

Source request: `.claude/modules/features/mmfintegrations/source-design-doc.md` (MMFIntegrations API
Design Document v0.1, webMethods IS 10.7 → Spring Boot). Target: **existing**
`services/wealth-management-service`.

---

## 1. Feature Objective & Scope

**Problem.** Athena (the fund-administration platform) reads and updates cash sweeps between Al Ramz
client accounts and the Money Market Fund, and reads custodian cash and positions for reconciliation.
Today it does this through the legacy webMethods package `MMFIntegrations`. That package has:
- SQL injection (F-01, F-02);
- request bodies written to disk (F-03);
- racy `MAX+1` keys (F-04);
- two-database updates without transactions (F-05);
- no idempotency (F-06);
- positional column drift (F-09);
- always-HTTP-200 home-grown codes (F-11).

This feature re-implements **all seven operations** (design doc chapters 4–10) in
`wealth-management-service`, using the modern contract from design doc §2.5. Athena and the API
Gateway cut over together, with no legacy shim (decision Q1). Once cut over, the IS package can be
decommissioned.

**Target service.** `services/wealth-management-service`. No new service, no new
`@AutoConfiguration`, no new datasource. Both Oracle books map onto the starter's existing named
datasources (see §2).

**Trigger.** Synchronous HTTP calls from Athena, through the API Gateway, with a JWT bearer token.

**In scope: 7 endpoints**

| Legacy (IS) | New (this spec) | Book(s) |
|---|---|---|
| POST `/getMMFClientData` | GET `/api/v1/mmf/accounts/{accountNumber}?book=` | selected |
| POST `/getSweepIn` | GET `/api/v1/mmf/sweep-ins?fromDate=&toDate=` | both |
| POST `/getSweepOut` | GET `/api/v1/mmf/sweep-outs?fromDate=&toDate=` | both |
| POST `/sweepInConfirmation` | POST `/api/v1/mmf/sweep-ins/confirmations` | both (UPDATE) |
| POST `/pushProfitDistribution` | POST `/api/v1/mmf/profit-distributions` | selected per item (INSERT) |
| POST `/getCustodiansCash` | GET `/api/v1/mmf/custodian-cash?clientId=…&clientId=…` | CAPITAL only |
| POST `/getCustodianPositions` | GET `/api/v1/mmf/custodian-positions[?asOfDate=]` | CAPITAL only |

**Done** means:
- Athena can make all seven calls against `wealth-management-service`.
- Responses use real HTTP statuses, ISO-8601 dates, `MMF###` error codes and the utility-service-style
  `GenericResponse` envelope.
- All SQL uses bind parameters only. This includes every `IN` list: there is no string-built SQL.
- Sweep confirmation is state-guarded and locked per book.
- Profit distribution is idempotent on `referenceId`. `SWEEP_NO` is `MAX+1` (legacy numbering), and a PK
  collision is retried once.
- Custodian positions map columns by name, and its client list comes from configuration.

**Out of scope (explicit)**
- The Athena-side APIs that Al Ramz calls (design doc §1.2).
- The back-office processes that create `W`/`O` sweep rows.
- DDL in the Oracle `RMZ`/`MMF` databases. This is a DBA release prerequisite (§4), not a Liquibase
  changeset.
- These operational actions outside this repo:
  - API Gateway re-configuration;
  - rotating the gateway API key published on Confluence (F-15);
  - deleting the `tdfs` file from IS hosts (F-03 cleanup).
- An `asOf` parameter for custodian cash. Design doc §9 proposes it, but it is not requested.
  Custodian cash stays "balance as of `SYSDATE`", as in legacy.
- A replacement for the `SEQDatalust` audit. The starter's existing request/response DB audit
  logging (`company.logging.database-logging`) covers it.

---

## 2. Repository Grounding

| Concern | Existing artefact this spec builds on |
|---|---|
| Contract file (amend) | `services/wealth-management-service/specs/apiSpecs.yaml`. It currently has only `/api/v1/info`, and its `GenericResponse` has only `responseCode`/`responseMessage`/`response`. |
| Codegen | `services/wealth-management-service/pom.xml` `openapi-generator-maven-plugin` 7.23.0: `interfaceOnly`, `useTags`, `performBeanValidation`, `useResponseEntity`, `apiPackage=com.alramz.api`, `modelPackage=com.alramz.model`. |
| Envelope + error-code precedent | `services/utility-service/specs/apiSpecs.yaml` `GenericResponse` (`responseCode`, `responseMessage`, `response`, `correlationId` uuid, `errorCode` nullable, `errorMsg` nullable), with per-status examples on `createCommunityPost`. |
| Controller precedent | `services/utility-service/.../controllers/CommunityPostController.java`: `@RestController @RequestMapping("/api/v1")`, implements the generated `CommunityApi`, uses `@JwtSecured(roles=…)` and `@Loggable`, builds a `GenericResponse` and sets `correlationId` via `MDCUtil.getCorrelationIdAsUuid()`. |
| Exception handling precedent | `services/utility-service/.../exception/{ErrorCode, ApplicationException, GlobalExceptionHandler}.java`: `@RestControllerAdvice(basePackages="com.alramz.controllers") @Order(0)`, a `buildResponse(status, ErrorCode, msg)` helper, an explicit `AccessDeniedException` → 403, and a catch-all → 500 that never leaks the message. wealth-management-service has **no** `exception` package yet, so this feature adds one. |
| Repository precedent | `services/reporting-management-service/.../repository/RelationshipManagerRepositoryImpl.java`: `@Qualifier("brokNamedParameterJdbcTemplate") NamedParameterJdbcTemplate`, SQL from `SqlQueriesManager.getSQLQueryFromConfig(key)`, and a list bound as a named parameter (`relationshipManagerCodes`). `@ConditionalOnProperty(prefix="company.datasource.brok", name="enabled", havingValue="true")` sits on the repository, the service **and** the controller. |
| SQL externalisation | The starter's `com.alramz.utils.SqlQueriesManager` reads the `sql.file` property, a comma list of classpath XML `Properties` files. reporting sets `sql.file: classpath:sql/...` in `application.yml`. wealth has no `sql.file` and no `sql/` folder yet. |
| Datasources | Starter `BrokDataSourceAutoConfiguration` provides `brokNamedParameterJdbcTemplate` and `brokTransactionManager`. `IntegrationDataSourceAutoConfiguration` provides `integrationNamedParameterJdbcTemplate` and `integrationTransactionManager` (prefix `company.datasource.integration`, default driver `oracle.jdbc.OracleDriver`). wealth configures `company.datasource.brok` in dev/docker/preprod/prod (`enabled: false` in test) and has **no** `integration` block. `ojdbc11` and `orai18n` are already in wealth's `pom.xml`. |
| Primary TM | `services/wealth-management-service/.../config/DatasourceConfiguration.java` re-exports `middlewareTransactionManager` as `@Primary`. A bare `@Transactional` would therefore bind to Postgres, so Oracle work must name its TM explicitly (`.claude/howtos/multi-datasource-and-jpa.md` §3b). |
| Config-properties convention | `.claude/howtos/configuration-properties.md`: a `@Getter @Setter` class with a `public static final String PREFIX` and a feature-specific prefix. The utility precedent is `FinouxProperties` (`@ConfigurationProperties(prefix = FinouxProperties.PREFIX)`). wealth has no service-level properties class yet. |
| JWT role | `APP_WEALTH_MANAGEMENT_SERVICE_USER`, already registered in `services/wealth-management-service/apiCollection.http`. In every `application-*.yml`, `permit-all-urls` contains only `/api/v1/info`. |
| Correlation id | The starter's `CorrelationIdFilter` (header `X-Correlation-ID`, `LoggingConstants.DEFAULT_CORRELATION_ID_HEADER`) plus `CorrelationIdResponseBodyAdvice`, which fills `setCorrelationId(UUID)` on any body. No custom filter is needed; this replaces the design doc's proposed `CorrelationIdFilter`. |
| Masking | The starter's `com.alramz.audit.SensitiveDataMasker`. Its key set covers credentials and EID/passport/NIN keys, not account numbers or balances. |
| Liquibase | wealth's `db/changelog/{dev,docker,preprod,prod}` and test `sql/001-api-audit-log.sql`, `002-jwt-tables.sql` cover only the middleware Postgres. This feature leaves them untouched (see §4). |
| Sizing | `services/wealth-management-service/service.yaml`: 0.5 CPU / 1Gi, 1–5 replicas, `-Xmx768m`. |
| Jackson | `services/wealth-management-service/.../config/JacksonConfig.java` sets `SNAKE_CASE` naming. Generated models carry explicit `@JsonProperty("<yamlName>")`, so the camelCase YAML property names below are what goes on the wire. |

**Book → datasource mapping** (non-blocking default, carried from the previous run):

| Book (`MmfBook`) | Legacy `source` / `SRC_TYPE` | Legacy connection | Starter datasource | Template / TM bean |
|---|---|---|---|---|
| `CAPITAL` | `C` | `RMZ:RMZ` (Capital back-office, schema INSIGHT) | `brok` | `brokNamedParameterJdbcTemplate` / `brokTransactionManager` |
| `MMF` | `M` | `MMF:MMF` (MMF book, schema INSIGHT) | `integration` | `integrationNamedParameterJdbcTemplate` / `integrationTransactionManager` |

The custodian endpoints read only the CAPITAL book (`brok`), as the legacy adapters do (design doc §11.3).

**Placement.** wealth, reporting and utility all use a flat package-by-layer layout. New code:
- `com.alramz.controllers.MmfController implements com.alramz.api.MmfApi`, with `@RequestMapping("/api/v1")`.
- `com.alramz.service.MmfService` plus `com.alramz.service.impl.MmfServiceImpl`. This mirrors reporting's
  service/impl split.
- `com.alramz.repository.MmfRepository` plus `com.alramz.repository.MmfRepositoryImpl`.
- `com.alramz.config.MmfProperties`.
- `com.alramz.exception.*` (a new package, see §6).
- `src/main/resources/sql/sql-queries.xml`.

Generated DTOs land in `com.alramz.model`. Every schema name is prefixed `Mmf` so it cannot collide.

---

## 3. Interface Contracts

### 3.0 Common to all seven operations

**Contract file and paths**
- This **amends** `services/wealth-management-service/specs/apiSpecs.yaml`.
- Paths are written **bare** (`/mmf/...`). `MmfController` adds `/api/v1` via
  `@RequestMapping("/api/v1")`. This follows the utility-service precedent and checklist §4.3.
- The existing `/api/v1/info` entry stays as it is. The hand-written `ApplicationController` does not
  implement `InfoApi`, so there is no conflict.
- **Tag:** `Mmf`. `useTags` turns this into the single interface `MmfApi`.

**Security**
- Every method is JWT-secured with `@JwtSecured(roles = "APP_WEALTH_MANAGEMENT_SERVICE_USER")`. This
  is decision Q2 = B, and it is also the secured-by-default standard.
- The root-level `security: - BearerAuth: []` already applies to all operations.
- Every operation lists `'401'` and `'403'` with a description only. This matches utility-service's
  `createCommunityPost` convention: the 401 body comes from the starter's `JwtAuthFilter`, outside
  `GlobalExceptionHandler`.

**Envelope**
- All operations return `GenericResponse`. Amend wealth's schema to match utility-service exactly by
  adding `correlationId` (string, uuid), `errorCode` (string, nullable) and `errorMsg` (string,
  nullable).
- On success, `errorCode` and `errorMsg` are `null` and `response` carries the payload.
- On failure, `response` is absent or null and `errorCode` is always an `MMF###` code. The one
  exception is 403, where `errorCode` is `null`.
- 500 and 503 responses also carry their per-endpoint code. This follows utility's precedent (`FNX013`
  on 500, `FNX012` on 503) and supersedes the design doc's §4.7 sample, which shows `errorCode: null`
  on 503.

**Dates and headers**
- Dates are ISO-8601 `yyyy-MM-dd` (`type: string, format: date` → `LocalDate`), parsed strictly.
- `Authorization: Bearer <jwt>` is required.
- `X-Correlation-ID` is optional and is echoed back as `correlationId`.

**503 vs 500.** Every operation reads or writes an external Oracle book, so a 503 can genuinely
happen.
- A Spring `DataAccessResourceFailureException` (including `CannotGetJdbcConnectionException`) or
  `TransientDataAccessResourceException` → the operation's **503** code.
- Any other `DataAccessException` → the operation's **500** code.

**Limits.** These are non-blocking defaults from design doc Appendix E #11. They are written into the
contract (`maxItems`) and enforced in code; none of them is a config property.

| Limit | Value | Note |
|---|---|---|
| Date range | at most **93 days**, inclusive | |
| Confirmations | at most **1000** items | Keeps the Oracle `IN` list within its 1000-expression limit. |
| Profit distributions | at most **5000** items | |
| Custodian-cash `clientId` values | at most **500** | |

New `components.schemas`:

```yaml
MmfBook:          { type: string, enum: [CAPITAL, MMF] }
MmfAccount:       { type: object, properties: { clientId: string, clientNameEn: string, clientNameAr: string,
                    accountNumber: string, accountName: string, baseCurrency: string } }
MmfAccountResponse: { type: object, properties: { account: $ref MmfAccount } }
MmfSweep:         { type: object, required: [sweepNo, date, accountNumber, amount, source],
                    properties: { sweepNo: integer/int64, date: string/date, accountNumber: string,
                                  amount: number, source: $ref MmfBook } }
MmfSweepInListResponse:  { type: object, properties: { sweepIns:  array of MmfSweep } }
MmfSweepOutListResponse: { type: object, properties: { sweepOuts: array of MmfSweep } }
MmfSweepConfirmationItem: { type: object, required: [sweepNo, source],
                    properties: { sweepNo: integer/int64 minimum 1, source: $ref MmfBook } }
MmfSweepRef:      { type: object, properties: { sweepNo: integer/int64, book: $ref MmfBook } }
MmfSweepConfirmationResult: { type: object, properties: { confirmedCapital: integer, confirmedMmf: integer,
                    alreadyConfirmed: array of MmfSweepRef } }
MmfProfitDistributionItem: { type: object, required: [sweepDate, accountNumber, amount, referenceId, source],
                    properties: { sweepDate: string/date, accountNumber: string pattern '^\d{1,9}$',
                                  amount: number exclusiveMinimum 0, referenceId: string pattern '^\d{1,15}$',
                                  source: $ref MmfBook } }
MmfProfitDistributionResult: { type: object, properties: { referenceId: string, accountNumber: string,
                    book: $ref MmfBook, status: string enum [CREATED, FAILED],
                    sweepNo: integer/int64 nullable, errorCode: string nullable } }
MmfProfitDistributionResponse: { type: object, properties: { results: array of MmfProfitDistributionResult } }
MmfCustodianCash: { type: object, properties: { clientMainId: string, clientId: string, clientName: string,
                    cashBalance: number } }
MmfCustodianCashListResponse: { type: object, properties: { custodianCash: array of MmfCustodianCash } }
MmfMarket:        { type: string, enum: [DFM, DIFX, ADX] }
MmfCustodianPosition: { type: object, properties: { clientId: string, isin: string, symbol: string,
                    quantity: number, closePrice: number, currency: string,
                    market: $ref MmfMarket (nullable), priceDate: string/date } }
MmfCustodianPositionListResponse: { type: object, properties: { custodianPositions: array of MmfCustodianPosition } }
```

### 3.1 Get MMF account: `GET /mmf/accounts/{accountNumber}`, `operationId: getMmfAccount`

**Parameters**

| Name | In | Required | Type / rule |
|---|---|---|---|
| `accountNumber` | path | yes | string, pattern `^\d{1,9}$` (`CB_CLIENT.CL_CLIENT_ID`) |
| `book` | query | yes | `MmfBook` |

**Logic**
- The query runs on the selected book's template, using SQL key `mmf.account.find`:
  ```sql
  SELECT c.CL_MAIN_CLIENT_ID, m.CLE_CLIENT_NAME, m.CLA_CLIENT_NAME, c.CL_CLIENT_ID, c.CLE_CLIENT_NAME, c.CUR_CODE
  FROM INSIGHT.CB_CLIENT c
  JOIN INSIGHT.CB_MAIN_CLIENT m ON m.CL_MAIN_CLIENT_ID = c.CL_MAIN_CLIENT_ID
  WHERE c.CL_CLIENT_ID = :accountNumber
  ```
  The join is inferred; confirm it (see §9).
- `:accountNumber` is bound as a **String**. This stays index-safe whatever the column type turns out
  to be (Appendix E #8).
- Columns are mapped by name, not by position.
- If more than one row comes back, the first row wins and a WARN is logged.

**Responses**

| Status | errorCode | When | Example `errorMsg` |
|---|---|---|---|
| 200 | – | found | – |
| 400 | MMF001 | `accountNumber` not `^\d{1,9}$` | `accountNumber must be 1-9 digits` |
| 400 | MMF002 | `book` missing | `book is required` |
| 400 | MMF003 | `book` not CAPITAL/MMF | `book must be CAPITAL or MMF` |
| 401 / 403 | – | JWT missing or invalid / role missing | – |
| 404 | MMF004 | no row in the selected book | `Account 2939418 not found in book CAPITAL` |
| 500 | MMF005 | other DB error | `Internal Server Error` |
| 503 | MMF006 | selected book unreachable | `MMF data source unavailable` |

200 example:
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33",
  "response": { "account": { "clientId": "1100206936164", "clientNameEn": "1100206936164-INDIVIDUAL",
    "clientNameAr": "1100206936164-INDIVIDUAL", "accountNumber": "2939418",
    "accountName": "1100206936164-NORMAL-AED", "baseCurrency": "AED" } } }
```

404 example:
```json
{ "responseCode": "404", "responseMessage": "Not Found", "errorCode": "MMF004",
  "errorMsg": "Account 2939418 not found in book CAPITAL", "correlationId": "6a1f0c2e-3b7d-4e55-9a10-2f8c4d1e7b33" }
```

The 400, 500 and 503 bodies have the same shape with the codes above. For example, 503:
`{"responseCode":"503","responseMessage":"Service Unavailable","errorCode":"MMF006","errorMsg":"MMF data source unavailable",...}`.

### 3.2 List sweep-ins: `GET /mmf/sweep-ins`, `operationId: listMmfSweepIns`

**Parameters.** `fromDate` and `toDate` are optional query parameters (`string/date`). Supply both or
neither.

**Logic.** The same query runs against **both** books with `SWEEP_TYPE = 'W'`. It selects
`SWEEP_NO, SWEEP_DATE, CLIENT_ID, AMT`.
- **Date range given:** SQL key `mmf.sweep.find.range`:
  ```sql
  WHERE SWEEP_TYPE = :type AND SWEEP_DATE >= :fromDate AND SWEEP_DATE < :toDateExclusive
  ```
  `toDateExclusive` is `toDate + 1 day`, so the range is half-open and includes the whole of `toDate`.
  This fixes F-07.
- **No dates given:** SQL key `mmf.sweep.find.today`:
  ```sql
  WHERE SWEEP_TYPE = :type AND SWEEP_DATE >= TRUNC(SYSDATE) AND SWEEP_DATE < TRUNC(SYSDATE) + 1
  ```
- Each row is tagged `source = CAPITAL | MMF`. Rows from both books are merged and sorted by
  `(date ASC, sweepNo ASC)`.
- `date` is the `LocalDate` of `SWEEP_DATE`, which fixes F-08. `accountNumber` is `CLIENT_ID` as a
  string.
- If either book fails, the whole request fails. There is no partial list.

**Responses**

| Status | errorCode | When |
|---|---|---|
| 200 | – | rows, or empty `sweepIns: []` (never 204) |
| 400 | MMF010 | exactly one of `fromDate`/`toDate` supplied |
| 400 | MMF011 | a date is not a valid ISO calendar date (e.g. `2025-02-31`) |
| 400 | MMF012 | `fromDate > toDate`, or the range is longer than 93 days inclusive |
| 401 / 403 | – | auth |
| 500 | MMF015 | DB error in either book |
| 503 | MMF016 | either book unreachable |

200 example:
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "correlationId": "…",
  "response": { "sweepIns": [ { "sweepNo": 52, "date": "2019-12-30", "accountNumber": "2944237", "amount": 3838.02, "source": "CAPITAL" } ] } }
```

400 example:
```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MMF010",
  "errorMsg": "Provide both fromDate and toDate, or neither", "correlationId": "…" }
```

Other error examples, same shape:

| Code | `errorMsg` |
|---|---|
| MMF011 | `fromDate must be an ISO-8601 date (yyyy-MM-dd)` |
| MMF012 | `Date range must not exceed 93 days and fromDate must not be after toDate` |
| MMF015 | `Internal Server Error` |
| MMF016 | `MMF data source unavailable` |

### 3.3 List sweep-outs: `GET /mmf/sweep-outs`, `operationId: listMmfSweepOuts`

Identical to §3.2, with these differences:
- It filters on `SWEEP_TYPE = 'O'`. The same SQL keys are reused with `:type = 'O'`. This removes
  F-14 (wrong adapter) and the legacy `TRUNC`/format inconsistency.
- The response key is `sweepOuts`, using `MmfSweepOutListResponse`.
- Its codes are MMF020 (only one date), MMF021 (invalid date), MMF022 (range or order), MMF025 (500)
  and MMF026 (503).

200 example item: `{"sweepNo": 24294, "date": "2020-05-27", "accountNumber": "2944758", "amount": 50000, "source": "CAPITAL"}`.

### 3.4 Confirm sweep-ins: `POST /mmf/sweep-ins/confirmations`, `operationId: confirmMmfSweepIns`

**Request body.** A bare JSON array of `MmfSweepConfirmationItem`, `minItems: 1`, `maxItems: 1000`:
```json
[ {"sweepNo": 2170411, "source": "CAPITAL"}, {"sweepNo": 2170412, "source": "CAPITAL"}, {"sweepNo": 2345, "source": "MMF"} ]
```

**Logic** (decision Q5):
1. De-duplicate items by `(source, sweepNo)` and split them by book.
2. For each **non-empty** book, open a transaction on that book's TM (`brokTransactionManager` or
   `integrationTransactionManager`). Use a per-book `TransactionTemplate`, programmatically.
3. In that transaction, lock the rows with SQL key `mmf.sweep.lock`:
   ```sql
   SELECT SWEEP_NO, SWEEP_TYPE FROM INSIGHT.SWEEP_FACILITY WHERE SWEEP_NO IN (:sweepNos) FOR UPDATE
   ```
4. Classify each requested number:
   - **missing:** no row;
   - **not pending:** type is neither `W` nor `I`;
   - **already confirmed:** type `I`;
   - **to confirm:** type `W`.
5. Lock and validate **both** books before updating **either**: nest the second book's transaction
   inside the first. If any number in either book is missing or not pending, throw
   `MmfConflictException(MMF033)`. Both transactions roll back and nothing changes.
6. In each book, update with SQL key `mmf.sweep.confirm`:
   ```sql
   UPDATE INSIGHT.SWEEP_FACILITY SET SWEEP_TYPE = 'I' WHERE SWEEP_NO IN (:sweepNos) AND SWEEP_TYPE = 'W'
   ```
   The `SWEEP_TYPE = 'W'` condition is the state guard. Together with the binds, this fixes F-01 and
   F-05.
7. Commit the inner transaction, then the outer one.
   - If the second commit fails after the first succeeded, throw `MmfBackendException(MMF037)`. Log it
     at ERROR with the committed book and its sweep numbers; this log line is the alert hook.
   - There is no compensation and no retry queue. Athena retries the whole request. That is safe
     because already-`I` rows come back in `alreadyConfirmed` and are no-ops.

**Responses**

| Status | errorCode | When |
|---|---|---|
| 200 | – | all valid; counts plus the `alreadyConfirmed` list (which may be empty) |
| 400 | MMF030 | body empty (`[]`), missing, or more than 1000 items |
| 400 | MMF031 | an item is missing `sweepNo` or `source`, or `source` is not CAPITAL/MMF |
| 400 | MMF032 | `sweepNo` is not a positive integer (≤ 0, fractional, or non-numeric) |
| 401 / 403 | – | auth |
| 409 | MMF033 | one or more numbers are unknown or not pending; **nothing changed** |
| 500 | MMF035 | DB error before any commit; nothing committed |
| 500 | MMF037 | one book committed and the other failed; a retry is safe (idempotent) |
| 503 | MMF036 | a required book is unreachable before any commit |

200 example:
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "correlationId": "…",
  "response": { "confirmedCapital": 2, "confirmedMmf": 0, "alreadyConfirmed": [ {"sweepNo": 2345, "book": "MMF"} ] } }
```

409 example:
```json
{ "responseCode": "409", "responseMessage": "Conflict", "errorCode": "MMF033",
  "errorMsg": "Sweeps unknown or not pending: CAPITAL 2170412", "correlationId": "…" }
```

500 (MMF037) example:
```json
{ "responseCode": "500", "responseMessage": "Internal Server Error", "errorCode": "MMF037",
  "errorMsg": "Confirmation partially applied; retry the request", "correlationId": "…" }
```

Other error examples, same shape:

| Code | `errorMsg` |
|---|---|
| MMF030 | `Request must contain 1-1000 items` |
| MMF031 | `Item 2: source must be CAPITAL or MMF` |
| MMF032 | `Item 1: sweepNo must be a positive integer` |
| MMF035 | `Internal Server Error` |
| MMF036 | `MMF data source unavailable` |

### 3.5 Push profit distributions: `POST /mmf/profit-distributions`, `operationId: createMmfProfitDistributions`

**Request body.** A bare JSON array of `MmfProfitDistributionItem`, `minItems: 1`, `maxItems: 5000`:
```json
[ {"sweepDate": "2025-04-20", "accountNumber": "1005", "amount": 2500.75, "referenceId": "987654321", "source": "CAPITAL"},
  {"sweepDate": "2025-04-20", "accountNumber": "1006", "amount": 3700.5,  "referenceId": "123456789", "source": "CAPITAL"} ]
```

**Validation is all-or-nothing.** Any invalid item → 400 before anything is written. On top of the
YAML constraints, the service checks that `amount` has at most 22 integer digits and a scale of at
most 3 (`NUMBER(25,3)`). The generator does not emit `@Digits`, so this check is done in code and
raised as `ApplicationException(MMF041)`.

**Logic** (decisions Q3 and Q4):
1. **No pipeline dump.** F-03 is removed.
2. Process items in request order. Each item is its own short unit on its book's template:
   1. Allocate `SWEEP_NO` with `SELECT NVL(MAX(SWEEP_NO), 0) + 1 FROM INSIGHT.SWEEP_FACILITY` (SQL key
      `mmf.sweep.next`). There is no DB sequence (Q3 revised).
   2. Insert the row with SQL key `mmf.profit.insert`:

      | Column | Value |
      |---|---|
      | `SWEEP_NO` | `:sweepNo` |
      | `SWEEP_DATE` | `:sweepDate` |
      | `SWEEP_TYPE` | `'P'` |
      | `CLIENT_ID` | `:accountNumber`, bound as Long |
      | `AMT` | `:amount` |
      | `REF_ID` | `:referenceId`, bound as Long |
      | `SRC_TYPE` | `'C'` or `'M'` |
      | `SEGMENT_ID` | `0` |
      | `UPD_TIME` | `SYSDATE`, a server timestamp (fixes F-12) |
      | `JV_NO`, `USR_CODE` | **left NULL** (non-blocking default) |

   3. If the insert succeeds, the item is `CREATED` with its `sweepNo`.
3. If the insert raises `DuplicateKeyException` (ORA-00001) and the message names the REF_ID unique
   index `UX_SWEEP_PROFIT_REF`, the item is `FAILED` with `errorCode MMF044`.
   - The DB index is the idempotency guard; there is no application pre-check.
   - A `referenceId` repeated within the same request is caught the same way.
   - It is never retried.
4. Any other `DuplicateKeyException` is a `SWEEP_NO` PK collision: a concurrent writer read the same
   `MAX`. The item is retried **once**: re-read `MAX+1` and insert again. If the retry succeeds the item
   is `CREATED` with the new `sweepNo`; if it fails, step 3 or 5 applies to that failure.
5. Any other insert failure, including a second PK collision, makes the item `FAILED` with `errorCode
   MMF047`. The raw DB message is logged and never returned.
6. If a book becomes unreachable (a resource-failure exception), the remaining items for that book
   are marked `FAILED` / `MMF047` **without being attempted**. This avoids 5000 connect-timeouts in a
   row.
6. Aggregate status:

   | Outcome | Status |
   |---|---|
   | Every item CREATED | **201** with `results[]` |
   | Every item FAILED with MMF044 (a pure replay) | **409 MMF044**; `errorMsg` lists the referenceIds |
   | Every item FAILED, and the first failure was "book unreachable" | **503 MMF046** |
   | Every item FAILED, any other cause | **500 MMF045** |
   | Anything else (mixed outcomes) | **207 Multi-Status** with per-item `results[]` |

**Responses**

| Status | errorCode | When |
|---|---|---|
| 201 | – | all items created |
| 207 | per item: MMF044 / MMF047 | mixed outcome |
| 400 | MMF040 | body empty, missing, or more than 5000 items |
| 400 | MMF041 | an item field rule fails (see below) |
| 400 | MMF042 | `accountNumber` missing or not `^\d{1,9}$` |
| 400 | MMF043 | `source` missing or not CAPITAL/MMF |
| 401 / 403 | – | auth |
| 409 | MMF044 | every item's `referenceId` is already booked in its book |
| 500 | MMF045 | no item could be created (non-connectivity DB errors) |
| 503 | MMF046 | target book unreachable and no item created |

MMF041 covers these item field failures:
- `sweepDate` missing or not a valid ISO date;
- `amount` missing, ≤ 0, with more than 22 integer digits, or with a scale above 3;
- `referenceId` missing or not `^\d{1,15}$`.

201 example:
```json
{ "responseCode": "201", "responseMessage": "Created", "errorCode": null, "errorMsg": null, "correlationId": "…",
  "response": { "results": [
    {"referenceId": "987654321", "accountNumber": "1005", "book": "CAPITAL", "status": "CREATED", "sweepNo": 2170513, "errorCode": null},
    {"referenceId": "123456789", "accountNumber": "1006", "book": "CAPITAL", "status": "CREATED", "sweepNo": 2170514, "errorCode": null} ] } }
```

207 example:
```json
{ "responseCode": "207", "responseMessage": "Multi-Status", "errorCode": null, "errorMsg": null, "correlationId": "…",
  "response": { "results": [
    {"referenceId": "987654321", "accountNumber": "1005", "book": "CAPITAL", "status": "FAILED", "sweepNo": null, "errorCode": "MMF047"},
    {"referenceId": "123456789", "accountNumber": "1006", "book": "CAPITAL", "status": "CREATED", "sweepNo": 2170514, "errorCode": null} ] } }
```

409 example:
```json
{ "responseCode": "409", "responseMessage": "Conflict", "errorCode": "MMF044",
  "errorMsg": "referenceId already booked: CAPITAL 987654321, CAPITAL 123456789", "correlationId": "…" }
```

400 example:
```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MMF043",
  "errorMsg": "Item 3: source must be CAPITAL or MMF", "correlationId": "…" }
```

Other error examples, same shape:

| Code | `errorMsg` |
|---|---|
| MMF040 | `Request must contain 1-5000 items` |
| MMF041 | `Item 1: amount must be > 0 with at most 3 decimals` |
| MMF042 | `Item 2: accountNumber must be 1-9 digits` |
| MMF045 | `Internal Server Error` |
| MMF046 | `MMF data source unavailable` |

### 3.6 Custodian cash: `GET /mmf/custodian-cash`, `operationId: listMmfCustodianCash`

**Parameters**

| Name | In | Required | Type / rule |
|---|---|---|---|
| `clientId` | query, repeated (`style: form, explode: true`) | yes | array of string, each `^\d{1,9}$`, `minItems: 1`, `maxItems: 500` |

Example: `GET /api/v1/mmf/custodian-cash?clientId=2001214&clientId=2001215`

**Logic.** This endpoint reads the CAPITAL book (`brok`) only.
- De-duplicate the ids, then run SQL key `mmf.custodian.cash`:
  ```sql
  SELECT CL_MAIN_CLIENT_ID, CL_CLIENT_ID,
         GET_CL_NAME_MAIN_CLIENT(CL_CLIENT_ID) CLIENT_NAME,
         INSIGHT.F_Get_Balance_As_Of(CL_MAIN_CLIENT_ID, CL_CLIENT_ID, SYSDATE, 'N', 'Y') CASH_BALANCE
  FROM INSIGHT.CB_CLIENT
  WHERE CL_CLIENT_ID IN (:clientIds)
  ORDER BY CL_CLIENT_ID
  ```
- `:clientIds` is a bound `List<String>`, expanded by `NamedParameterJdbcTemplate`. This fixes F-02.
- The `'N','Y'` flags are kept verbatim from legacy.
- Unknown ids are silently omitted, as in legacy (collection semantics).
- `cashBalance` is read with `rs.getBigDecimal("CASH_BALANCE")` and returned as a JSON number. The
  legacy declared type was String; see the verify item in §9.
- The legacy typo `clinetMainID` becomes `clientMainId`.

**Responses**

| Status | errorCode | When |
|---|---|---|
| 200 | – | rows, or empty `custodianCash: []` (never 204) |
| 400 | MMF050 | no `clientId` supplied |
| 400 | MMF051 | a `clientId` is not 1–9 digits |
| 400 | MMF052 | more than 500 `clientId` values |
| 401 / 403 | – | auth |
| 500 | MMF055 | DB or function error |
| 503 | MMF056 | CAPITAL book unreachable |

200 example:
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "correlationId": "…",
  "response": { "custodianCash": [ { "clientMainId": "1100106001214", "clientId": "2001214",
    "clientName": "1100106001214-NORMAL-AED", "cashBalance": 0 } ] } }
```

400 example:
```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MMF051",
  "errorMsg": "clientId must be 1-9 digits", "correlationId": "…" }
```

Other error examples, same shape:

| Code | `errorMsg` |
|---|---|
| MMF050 | `At least one clientId is required` |
| MMF052 | `At most 500 clientId values are allowed` |
| MMF055 | `Internal Server Error` |
| MMF056 | `MMF data source unavailable` |

### 3.7 Custodian positions: `GET /mmf/custodian-positions`, `operationId: listMmfCustodianPositions`

**Parameters**

| Name | In | Required | Type / rule |
|---|---|---|---|
| `asOfDate` | query | no | `string/date`, the price date |

- If `asOfDate` is absent, it defaults to **calendar yesterday**, which is the legacy
  `TRUNC(SYSDATE-1)`. The service passes `:asOfDate = null` and the SQL uses
  `NVL(:asOfDate, TRUNC(SYSDATE) - 1)`.
- An `asOfDate` later than today (`LocalDate.now(ZoneId.of("Asia/Dubai"))`) → 400 MMF060.

**Logic.** This endpoint reads the CAPITAL book (`brok`) only.
1. Read the client list from `MmfProperties.custodian.clientIds`, the property
   `mmf.custodian.client-ids`. This replaces the IS static data `MMFATHENA_CUSTODIAN_POSITIONS`. If
   the list is empty or missing → 500 MMF065 (a configuration fault).
2. Run SQL key `mmf.custodian.positions`, a `UNION ALL` of an `F_PORT` branch and a `DERV_F_PORT`
   branch. Each branch:
   - joins `CB_CLIENT`, `CB_MAIN_CLIENT` and `CB_SEC_COMP`;
   - aliases its columns **by name**: `CLIENT_ID`, `ISIN` (`SC_ISIN_CODE`), `SYMBOL` (`TICKER_ID`),
     `QUANTITY` (`QTY`), `CLOSE_PRICE` (`PR_C_PRICE`), `CURRENCY` (`CUR_CODE`), and `MARKET`
     (`CASE SC_MRK_CODE WHEN 5 THEN 'DFM' WHEN 8 THEN 'DIFX' WHEN 6 THEN 'ADX' END`);
   - filters with
     `WHERE CL_CLIENT_ID IN (:clientIds) AND PR_PRICE_DATE = NVL(:asOfDate, TRUNC(SYSDATE) - 1) AND USR_CODE = 3 AND QTY <> 0`.

   The result is ordered by `CLIENT_ID, ISIN`.
   - `:clientIds` is the configured list, bound as a parameter. There is no string-built `IN` list.
   - The `USR_CODE = 3` constant and the market-code `CASE` stay in the SQL (see the F-16 note in §9).
   - Join predicates are inferred from design doc Appendix C.1 column lists (`F.CLIENT_ID = C.CL_CLIENT_ID`,
     `F.COMP_ID = SC.SC_COMP_ID`, `C.CL_MAIN_CLIENT_ID = M.CL_MAIN_CLIENT_ID`). They must be confirmed
     against the IS export (§9).
3. Map rows by column name, which fixes F-09.
   - `quantity` and `closePrice` are read as `BigDecimal`.
   - An unknown market code maps to `market: null`. Legacy returned `''` here.
   - `priceDate` is the effective date that was used.

**Responses**

| Status | errorCode | When |
|---|---|---|
| 200 | – | rows, or empty `custodianPositions: []` (never 204) |
| 400 | MMF060 | `asOfDate` is not a valid ISO date, or is in the future |
| 401 / 403 | – | auth |
| 500 | MMF065 | `mmf.custodian.client-ids` not configured or empty |
| 500 | MMF066 | DB error |
| 503 | MMF067 | CAPITAL book unreachable |

200 example:
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null, "correlationId": "…",
  "response": { "custodianPositions": [ { "clientId": "2001214", "isin": "AEE000301011", "symbol": "EMAAR",
    "quantity": 15000, "closePrice": 9.85, "currency": "AED", "market": "DFM", "priceDate": "2026-10-18" } ] } }
```

Error examples:
```json
{ "responseCode": "400", "responseMessage": "Bad Request", "errorCode": "MMF060",
  "errorMsg": "asOfDate must be an ISO-8601 date not in the future", "correlationId": "…" }
```
```json
{ "responseCode": "500", "responseMessage": "Internal Server Error", "errorCode": "MMF065",
  "errorMsg": "Custodian client list is not configured", "correlationId": "…" }
```

MMF066 and MMF067 have the same shape, with `Internal Server Error` and `MMF data source unavailable`.

### 3.8 Java signatures (new)

```java
// com.alramz.config.MmfProperties   (@Getter @Setter, PREFIX = "mmf", per configuration-properties.md)
public static class Custodian { private List<String> clientIds = new ArrayList<>(); }

// com.alramz.service.MmfService
MmfAccount getAccount(String accountNumber, MmfBook book);
List<MmfSweep> listSweeps(String sweepType, LocalDate fromDate, LocalDate toDate);   // "W" | "O"; both null = today
MmfSweepConfirmationResult confirmSweepIns(List<MmfSweepConfirmationItem> items);
List<MmfProfitDistributionResult> createProfitDistributions(List<MmfProfitDistributionItem> items); // throws for 409/500/503 aggregate
List<MmfCustodianCash> listCustodianCash(List<String> clientIds);
List<MmfCustodianPosition> listCustodianPositions(LocalDate asOfDate);               // null = legacy yesterday

// com.alramz.repository.MmfRepository   (Impl holds Map<MmfBook, NamedParameterJdbcTemplate> from the two @Qualifier'd beans)
Optional<MmfAccount> findAccount(MmfBook book, String accountNumber);
List<MmfSweep> findSweeps(MmfBook book, String sweepType, LocalDate fromDate, LocalDate toDateExclusive); // nulls = today
Map<Long, String> lockSweepTypes(MmfBook book, Collection<Long> sweepNos);   // SWEEP_NO -> SWEEP_TYPE, FOR UPDATE
int confirmSweepIns(MmfBook book, Collection<Long> sweepNos);
long nextSweepNo(MmfBook book);
void insertProfitDistribution(MmfBook book, long sweepNo, MmfProfitDistributionItem item);
List<MmfCustodianCash> findCustodianCash(Collection<String> clientIds);                        // CAPITAL only
List<MmfCustodianPosition> findCustodianPositions(Collection<String> clientIds, LocalDate asOfDate); // CAPITAL only
```

For profit distributions, `MmfController` chooses `ResponseEntity.status(HttpStatus.MULTI_STATUS)`
when any item is `FAILED`, and `HttpStatus.CREATED` otherwise.

---

## 4. Data Model & Persistence Changes

**No Liquibase changeset.**
- wealth's Liquibase manages only the middleware Postgres audit and JWT tables.
- The Oracle `INSIGHT` schemas in RMZ and MMF are external systems; this repo does not manage them.
- No `db/changelog/**` file changes, and no changeset number is used up.

**No new JPA entity.**
- All Oracle access goes through `NamedParameterJdbcTemplate`, with SQL in
  `src/main/resources/sql/sql-queries.xml` (a new file).
- The file defines 9 keys: `mmf.account.find`, `mmf.sweep.find.range`, `mmf.sweep.find.today`,
  `mmf.sweep.lock`, `mmf.sweep.confirm`, `mmf.sweep.next`, `mmf.profit.insert`,
  `mmf.custodian.cash`, `mmf.custodian.positions`.
- Both books use schema `INSIGHT`, so the same SQL serves both.

**Release prerequisite (DBA, outside this repo; decision Q3).** Apply once in the `brok` Oracle
database (schema `INSIGHT`) **before** go-live. No sequence is needed (Q3 revised):
1. `CREATE UNIQUE INDEX UX_SWEEP_PROFIT_REF ON INSIGHT.SWEEP_FACILITY (CASE WHEN SWEEP_TYPE = 'P' THEN REF_ID END);`
   Use this **exact index name**. The service relies on it to tell a duplicate `referenceId`
   (MMF044) apart from other duplicate-key errors.
   Check first that existing `P` rows have no duplicate `REF_ID`, or the index creation fails.
2. If the `brok` connection user is not the schema owner, grant it:
   - `SELECT, INSERT, UPDATE` on `INSIGHT.SWEEP_FACILITY`.

   The `brok` user additionally needs:
   - `SELECT` on `CB_CLIENT`, `CB_MAIN_CLIENT`, `F_PORT`, `DERV_F_PORT` and `CB_SEC_COMP`;
   - `EXECUTE` on `INSIGHT.F_Get_Balance_As_Of` and `GET_CL_NAME_MAIN_CLIENT`.

Optional, not required by this spec: `PK_SWEEP_FACILITY`, `CK_SWEEP_TYPE`, and `IX_SWEEP_TYPE_DATE`.
The range query uses plain `SWEEP_DATE` bounds, so a plain `(SWEEP_TYPE, SWEEP_DATE)` index would serve
it better than the `TRUNC` function index.

**Coordination note** (Appendix E #10). Other back-office writers to `SWEEP_FACILITY` (the `W`/`O`
creators) also use `MAX(SWEEP_NO)+1`, so a PK collision is possible. The service retries a collided
item once; a second collision surfaces as item-level MMF047, and the caller retries it.

---

## 5. Security & Config Impact

**`permit-all-urls`: no change.** All seven endpoints stay authenticated, with role
`APP_WEALTH_MANAGEMENT_SERVICE_USER` enforced by `@JwtSecured`. Athena's JWT consumer is registered
with that role through `/api/auth/register`, as shown in `apiCollection.http`.

**Custodian exposure (accepted, decision Q2 = B).**
- On IS, the custodian endpoints were hidden only at the gateway. Here they are reachable with the
  same Athena credential and role as the sweep endpoints.
- `custodian-cash` returns the balance of **any** `CL_CLIENT_ID` the caller names (F-02 exposure).
  This spec only removes the injection, not that exposure.
- The user has explicitly accepted this. No narrower role or allow-list is added.

**New config: `company.datasource.integration` block** (the MMF book), in
`application-{dev,docker,preprod,prod}.yml`. It mirrors the existing `brok` block:
- `enabled: ${COMPANY_DATASOURCE_INTEGRATION_ENABLED:true}`;
- `url` and `username`;
- password: `plainPassword` in dev only; preprod and prod use `password` + `passwordVector`
  (cipher-encrypted), as `.claude/howtos/multi-datasource-and-jpa.md` and the existing comments
  require;
- `driverClassName`, default `oracle.jdbc.OracleDriver`;
- `startup-validation`, `pool.*` and `sql-logging`;
- `oracle.{connectTimeout,readTimeout,defaultRowPrefetch}`.

All values come from `COMPANY_DATASOURCE_INTEGRATION_*` env vars **without** hard-coded hosts or
passwords. The real MMF host is not known to this repo. `application-test.yml` gets
`integration: enabled: false`, mirroring `brok: enabled: false` (there is no Oracle in CI).

**New config: `mmf.custodian.client-ids`.**
- A list of strings bound to `com.alramz.config.MmfProperties` (`PREFIX = "mmf"`, enabled via
  `@EnableConfigurationProperties`).
- Add `mmf.custodian.client-ids: ${MMF_CUSTODIAN_CLIENT_IDS:}` to
  `application-{dev,docker,preprod,prod}.yml`. Spring binds a comma-separated value to the list.
- The values are today's `MMFATHENA_CUSTODIAN_POSITIONS` static data. They must be supplied per
  environment.
- If the value is empty, the custodian-positions endpoint returns 500 MMF065. Nothing else is affected.
- `application-test.yml` may set a sample list.

**New config: `sql.file`.** Add `sql.file: classpath:sql/sql-queries.xml` to wealth's
`application.yml`. This follows reporting's precedent.

**Conditional beans.** `MmfController`, `MmfServiceImpl` and `MmfRepositoryImpl` carry
`@ConditionalOnProperty(prefix = "company.datasource", name = {"brok.enabled", "integration.enabled"}, havingValue = "true")`.
This is the same pattern as reporting's `RelationshipManager*` chain, and it lets the test profile
start without Oracle.

**Secrets / Key Vault.** The new `COMPANY_DATASOURCE_INTEGRATION_*` and `MMF_CUSTODIAN_CLIENT_IDS`
env vars must reach the wealth deployment the same way the `BROK` ones do. That wiring lives in
`k8s/`, `infra/` and `.github/SECRETS.md`, all outside this spec's edit scope. **02_plan** lists it as
a deployment prerequisite and does not edit those files.

**Masking: no `SensitiveDataMasker` additions.**
- The payloads contain client and account numbers, names, balances, positions and amounts. None of
  them are credentials or national-ID data.
- reporting-management-service already logs `clientNumber`/`clientName` unmasked.
- Changing the masker would be a starter-wide change, which is out of scope. Flagged for
  `04_security` (§9).

**Injection.** Every caller-supplied or configured value is a bind parameter, including every
`IN (:list)`, which `NamedParameterJdbcTemplate` expands. Nothing is concatenated into SQL.

**Error leakage.** 500 and 503 `errorMsg` values are fixed texts. ORA and function messages are only
logged.

**Gateway.** The API Gateway must re-point `MMFIntegration` to the new paths, including the two
custodian paths, at the same time as the Athena cut-over (Q1). This is an operational dependency.

---

## 6. Edge Cases & Error Behaviors

### 6.1 Exception types

New package `com.alramz.exception`, modelled on utility-service:

| Type | HTTP | Notes |
|---|---|---|
| `ErrorCode` enum | – | One constant per code: MMF001–006, MMF010–012/015/016, MMF020–022/025/026, MMF030–033/035–037, MMF040–047, MMF050–052/055/056, MMF060/065–067, plus `INVALID_REQUEST` (MMF090) as the fallback for unclassifiable malformed input. |
| `ApplicationException(ErrorCode, msg)` | 400 | |
| `MmfNotFoundException(ErrorCode, msg)` | 404 | |
| `MmfConflictException(ErrorCode, msg)` | 409 | |
| `MmfBackendException(ErrorCode, msg, cause)` | 500 | Also used for MMF065 (config fault). |
| `MmfBackendUnavailableException(ErrorCode, msg, cause)` | 503 | |

### 6.2 `GlobalExceptionHandler`

Declared as `@RestControllerAdvice(basePackages = "com.alramz.controllers") @Order(0)`, using the
utility `buildResponse(status, ErrorCode, msg)` helper. It handles:
- the five exception types above;
- `AccessDeniedException` → 403 with `errorCode null`;
- the framework input exceptions listed next;
- catch-all `Exception` → 500 with the fixed text `Internal Server Error`. The code is the
  operation's 500 code when the operation can be resolved, otherwise MMF090.

**Framework input failures must resolve to the operation-specific code.** The field name alone is
ambiguous: `accountNumber` and `source` appear in more than one operation.

Recommended mechanism: the handler takes a `HandlerMethod` argument and keys
`ErrorCode.forInput(operationName, field)` on the method name, which equals the `operationId`.

It must cover:
- `MethodArgumentNotValidException`;
- `HandlerMethodValidationException` (Spring 6.1+, for `@Size` on a `List` body or query parameter,
  and `@Pattern` on path or query parameters);
- `MethodArgumentTypeMismatchException` (a bad enum or date in a query parameter);
- `MissingServletRequestParameterException`;
- `HttpMessageNotReadableException` (bad JSON, or a bad enum or number in the body). Use the Jackson
  `InvalidFormatException` path to get the item index and field.

### 6.3 Enumerated failure modes

| # | Op | Failure mode | Exception | HTTP / code |
|---|---|---|---|---|
| E1 | account | `accountNumber` not 1–9 digits | HandlerMethodValidation | 400 MMF001 |
| E2 | account | `book` absent | MissingServletRequestParameter | 400 MMF002 |
| E3 | account | `book=FOO` | MethodArgumentTypeMismatch | 400 MMF003 |
| E4 | account | no row | `MmfNotFoundException` | 404 MMF004 |
| E5 | account | SQL / grammar / ORA error | `MmfBackendException` | 500 MMF005 |
| E6 | account | book unreachable or connection timeout | `MmfBackendUnavailableException` | 503 MMF006 |
| E7 | sweep-ins / outs | only one date given | `ApplicationException` | 400 MMF010 / MMF020 |
| E8 | sweep-ins / outs | `2025-02-31` or `30-12-2019` | MethodArgumentTypeMismatch | 400 MMF011 / MMF021 |
| E9 | sweep-ins / outs | `fromDate > toDate`, or range > 93 days | `ApplicationException` | 400 MMF012 / MMF022 |
| E10 | sweep-ins / outs | no rows | – | 200 with empty list |
| E11 | sweep-ins / outs | DB error in either book | `MmfBackendException` | 500 MMF015 / MMF025 |
| E12 | sweep-ins / outs | either book unreachable | `MmfBackendUnavailableException` | 503 MMF016 / MMF026 |
| E13 | confirm | `[]`, no body, or > 1000 items | HandlerMethodValidation / NotReadable | 400 MMF030 |
| E14 | confirm | item missing a field, or bad `source` | MethodArgumentNotValid / NotReadable | 400 MMF031 |
| E15 | confirm | `sweepNo` ≤ 0, `1.5` or `"abc"` | MethodArgumentNotValid / NotReadable | 400 MMF032 |
| E16 | confirm | unknown number, or type `O`/`P` | `MmfConflictException` | 409 MMF033; both books rolled back |
| E17 | confirm | every requested sweep already `I` | – | 200 with counts 0; all listed in `alreadyConfirmed` |
| E18 | confirm | duplicate `(source, sweepNo)` in the request | – | de-duplicated and counted once |
| E19 | confirm | DB error before the first commit | `MmfBackendException` | 500 MMF035 |
| E20 | confirm | book unreachable before the first commit | `MmfBackendUnavailableException` | 503 MMF036 |
| E21 | confirm | first book committed, second commit failed | `MmfBackendException` | 500 MMF037 with an ERROR log; a retry is idempotent |
| E22 | profit | `[]`, no body, or > 5000 items | HandlerMethodValidation / NotReadable | 400 MMF040 |
| E23 | profit | bad `sweepDate`, `amount` or `referenceId` | MethodArgumentNotValid / NotReadable / `ApplicationException` | 400 MMF041 |
| E24 | profit | bad `accountNumber` | MethodArgumentNotValid | 400 MMF042 |
| E25 | profit | bad or missing `source` | MethodArgumentNotValid / NotReadable | 400 MMF043 |
| E26 | profit | every `referenceId` already booked (replay) | `MmfConflictException` | 409 MMF044 |
| E27 | profit | some items duplicate, others new | – | 207; the duplicates are FAILED/MMF044 |
| E28 | profit | some item inserts fail (other DB error, PK collision on retry too) | – | 207; those items are FAILED/MMF047 |
| E29 | profit | every item fails on a non-connectivity DB error | `MmfBackendException` | 500 MMF045 |
| E30 | profit | book unreachable and nothing created | `MmfBackendUnavailableException` | 503 MMF046 |
| E31 | profit | book unreachable mid-batch, after some items were created | – | 207; the rest of that book is FAILED/MMF047 and not attempted |
| E32 | custodian-cash | no `clientId` | MissingServletRequestParameter / HandlerMethodValidation | 400 MMF050 |
| E33 | custodian-cash | `clientId=12ab` or 10 digits | HandlerMethodValidation | 400 MMF051 |
| E34 | custodian-cash | 501 `clientId` values | HandlerMethodValidation | 400 MMF052 |
| E35 | custodian-cash | no matching clients | – | 200 with `custodianCash: []` |
| E36 | custodian-cash | DB error, or `F_Get_Balance_As_Of` raises | `MmfBackendException` | 500 MMF055 |
| E37 | custodian-cash | CAPITAL unreachable | `MmfBackendUnavailableException` | 503 MMF056 |
| E38 | custodian-positions | `asOfDate=2026-13-01`, or a future date | MethodArgumentTypeMismatch / `ApplicationException` | 400 MMF060 |
| E39 | custodian-positions | `mmf.custodian.client-ids` empty | `MmfBackendException` | 500 MMF065 (no DB call) |
| E40 | custodian-positions | no positions | – | 200 with `custodianPositions: []` |
| E41 | custodian-positions | DB error | `MmfBackendException` | 500 MMF066 |
| E42 | custodian-positions | CAPITAL unreachable | `MmfBackendUnavailableException` | 503 MMF067 |
| E43 | all | JWT missing, invalid or expired | starter `JwtAuthFilter` | 401 |
| E44 | all | JWT lacks `APP_WEALTH_MANAGEMENT_SERVICE_USER` | `AccessDeniedException` | 403 with `errorCode null` |
| E45 | all | malformed JSON or unclassifiable input | NotReadable | 400 with the operation's empty/invalid-body code when it can be resolved, otherwise MMF090 |

---

## 7. Open Questions & Decisions

This is the decision log. The user answered every question before this file was written, and **no**
items remain unresolved.

**Q1 — Contract shape.**
- **Options offered:**
  - A: the modern contract from design doc §2.5 (resource paths, real HTTP statuses, ISO dates, MMF###
    codes, utility-service `GenericResponse` with errorCode/errorMsg/correlationId);
  - B: legacy-compatible POST verb-paths that return 200 with legacy codes;
  - C: both, via a legacy shim.
- **Chosen: A.** Athena and the API Gateway cut over together. There is no legacy shim. → §3.

**Q2 — Custodian endpoints and authorization.**
- **Options offered:**
  - A: build the custodian endpoints with a separate, narrower role;
  - B: build all 7 with the existing `APP_WEALTH_MANAGEMENT_SERVICE_USER` role;
  - C: build them with an id allow-list or scope;
  - D: defer the custodian endpoints.
- **Chosen: B.** This was initially answered as D, then changed to B by the user during this run.
  - All 7 endpoints are built, including `GET /mmf/custodian-cash` and `GET /mmf/custodian-positions`
    (design doc chapters 9 and 10).
  - All use `@JwtSecured(roles = "APP_WEALTH_MANAGEMENT_SERVICE_USER")`.
  - **The user accepted that Athena's credentials can reach the custodian endpoints.** On IS they were
    hidden only at the gateway. As F-02 notes, custodian-cash can return any client's balance.
  - The custodian defaults are reinstated: a 500-id limit, the client-id list as config property
    `mmf.custodian.client-ids`, all values bound as parameters, and F-09 / F-16 / Appendix E #8
    flagged as verify items.
- → §1, §3.0, §3.6, §3.7, §5, §9.

**Q3 — SWEEP_NO allocation and profit idempotency.**
- **Options offered:**
  - A: a DBA-created `INSIGHT.SWEEP_FACILITY_SEQ` plus a unique REF_ID index for `P` rows, in both
    DBs;
  - B: app-side locking of `MAX+1`;
  - C: keep `MAX+1` without idempotency.
- **Chosen: A** originally. **Revised 2026-10-26: C plus the REF_ID index and a single retry.**
  - The user decided not to use a sequence. `SWEEP_NO` is `NVL(MAX(SWEEP_NO), 0) + 1`, as in legacy.
  - There is only one database (`brok`, schema `INSIGHT`). The DBA creates only the REF_ID index there.
  - ORA-00001 on the REF_ID index maps to MMF044 (409) and is never retried.
  - ORA-00001 on any other key (the `SWEEP_NO` PK) is retried once with a fresh `MAX+1`, then MMF047.
  - Accepted risk: concurrent writers can still collide twice; that item fails and the caller retries.
- → §3.5, §4.

**Q4 — Partial profit-distribution outcome.**
- **Options offered:**
  - A: 201 when every item is created, 207 Multi-Status with per-item `results[]` otherwise;
  - B: 200 with per-item results;
  - C: all-or-nothing.
- **Chosen: A.** Each failed item carries MMF047. → §3.5.

**Q5 — Confirmation semantics.**
- **Options offered:**
  - A: one transaction per book with `SELECT … FOR UPDATE`, all-or-nothing 409, idempotent `I`, and
    500 MMF037 on a cross-book partial commit with no compensation;
  - B: per-item results;
  - C: compensation or a retry queue.
- **Chosen: A.**
  - Any unknown or not-pending number → 409 MMF033, and nothing changes.
  - Re-confirming an `I` row is a no-op, reported in `alreadyConfirmed`.
  - If one book committed and the other failed → 500 MMF037; retrying is safe.
- → §3.4.

**Non-blocking defaults applied.** None of these needed a question.

| Area | Default |
|---|---|
| Datasources | `brok` for CAPITAL/RMZ (and both custodian endpoints); the starter's `integration` datasource for MMF |
| Data access | `NamedParameterJdbcTemplate` + `sql-queries.xml`/`SqlQueriesManager`, following reporting's `RelationshipManagerRepositoryImpl` |
| Error handling | `ErrorCode` enum + `GlobalExceptionHandler`, following utility-service |
| Paths | bare paths, with `@RequestMapping("/api/v1")` |
| Liquibase | no changeset |
| Limits | 93 days / 1000 / 5000 / 500 |
| `SWEEP_FACILITY` columns | `JV_NO` and `USR_CODE` left NULL |
| Custodian positions | `asOfDate` defaults to the legacy `TRUNC(SYSDATE-1)` |
| Custodian cash | no `asOf` parameter |

---

## 8. Explicit Acceptance Criteria

**Contract**
- [ ] `services/wealth-management-service/specs/apiSpecs.yaml` has these bare paths, all tagged `Mmf`:

  | Path | Method | operationId |
  |---|---|---|
  | `/mmf/accounts/{accountNumber}` | GET | `getMmfAccount` |
  | `/mmf/sweep-ins` | GET | `listMmfSweepIns` |
  | `/mmf/sweep-outs` | GET | `listMmfSweepOuts` |
  | `/mmf/sweep-ins/confirmations` | POST | `confirmMmfSweepIns` |
  | `/mmf/profit-distributions` | POST | `createMmfProfitDistributions` |
  | `/mmf/custodian-cash` | GET | `listMmfCustodianCash` |
  | `/mmf/custodian-positions` | GET | `listMmfCustodianPositions` |
- [ ] Every one of those operations declares each status listed for it in §3.1–§3.7. Every status except 401/403 has a filled-in `example` body; `'401'`/`'403'` have descriptions.
- [ ] The `GenericResponse` schema in that file includes `correlationId` (uuid), `errorCode` (nullable) and `errorMsg` (nullable).
- [ ] The `Mmf*` schemas in §3.0 exist with the stated required fields, patterns, and `minItems`/`maxItems` (1–1000, 1–5000, 1–500 for `clientId`).
- [ ] `mvn -pl services/wealth-management-service -am test-compile` succeeds.
- [ ] `MmfController` implements `com.alramz.api.MmfApi` with `@RequestMapping("/api/v1")`.

**Security and config**
- [ ] All 7 `MmfController` methods carry `@JwtSecured(roles = "APP_WEALTH_MANAGEMENT_SERVICE_USER")`.
- [ ] `permit-all-urls` is unchanged in every wealth `application-*.yml`.
- [ ] A test shows that a valid JWT without the role gets 403, not 500.
- [ ] `company.datasource.integration` is present in `application-{dev,docker,preprod,prod}.yml`, with no literal password in preprod or prod.
- [ ] `application-test.yml` has `integration.enabled: false`.
- [ ] `MmfProperties` binds `mmf.custodian.client-ids`.
- [ ] `mmf.custodian.client-ids: ${MMF_CUSTODIAN_CLIENT_IDS:}` is present in `application-{dev,docker,preprod,prod}.yml`.
- [ ] `application.yml` sets `sql.file: classpath:sql/sql-queries.xml`.
- [ ] `sql/sql-queries.xml` defines all 9 keys listed in §4.
- [ ] No SQL in `sql-queries.xml` or the repository concatenates a request or config value. Every value, including every `IN` list, is a `:named` bind.
- [ ] The repository injects only `@Qualifier("brokNamedParameterJdbcTemplate")` and `@Qualifier("integrationNamedParameterJdbcTemplate")`.
- [ ] Every transaction names `brokTransactionManager` or `integrationTransactionManager` explicitly.

**Behaviour** (unit or MockMvc tests with a mocked repository or service)

*Account*
- [ ] Found → 200 with `response.account`.
- [ ] Not found → 404 MMF004.
- [ ] `accountNumber=12ab` → 400 MMF001.
- [ ] `book` missing → 400 MMF002.
- [ ] `book=FOO` → 400 MMF003.
- [ ] Connection failure → 503 MMF006.
- [ ] Any other `DataAccessException` → 500 MMF005.

*Sweep-ins*
- [ ] Only `fromDate` given → 400 MMF010.
- [ ] `fromDate=2025-02-31` → 400 MMF011.
- [ ] `from > to`, or a 94-day range → 400 MMF012. A 93-day range is accepted.
- [ ] No rows → 200 with `sweepIns: []`.
- [ ] Rows from both books are merged, tagged with `source`, and sorted by date then sweepNo.
- [ ] No dates → the repository's "today" path is called. A range → it is called with `toDateExclusive = toDate + 1`.

*Sweep-outs*
- [ ] The same behaviour as sweep-ins, with codes MMF020/021/022/025/026, key `sweepOuts`, and type `O`.

*Confirm sweep-ins*
- [ ] `[]` → 400 MMF030. 1001 items → 400 MMF030.
- [ ] Missing `source` → 400 MMF031.
- [ ] `sweepNo: 0` → 400 MMF032.
- [ ] Any missing or `O`/`P` sweep in either book → 409 MMF033. The UPDATE (`confirmSweepIns`) is never called for **either** book.
- [ ] `W` rows are counted in `confirmedCapital`/`confirmedMmf`, `I` rows are listed in `alreadyConfirmed`, and the response is 200.
- [ ] The UPDATE SQL contains `AND SWEEP_TYPE = 'W'`.
- [ ] If the second book's commit fails after the first committed → 500 MMF037, plus an ERROR log line.

*Profit distributions*
- [ ] Each of these → 400 with the code shown, and no repository write:

  | Input | Code |
  |---|---|
  | `[]` | MMF040 |
  | bad date | MMF041 |
  | `amount` ≤ 0 | MMF041 |
  | `amount` with 4 decimals | MMF041 |
  | 10-digit `accountNumber` | MMF042 |
  | `source=X` | MMF043 |
- [ ] All inserts succeed → HTTP 201 with `responseCode "201"`. Results come back in request order, each with a `sweepNo`.
- [ ] One `DuplicateKeyException` naming `UX_SWEEP_PROFIT_REF` plus one success → HTTP 207, with that item FAILED/MMF044.
- [ ] One other `DataAccessException` plus one success → 207, with that item FAILED/MMF047.
- [ ] No raw DB text appears in the response body.
- [ ] Every item is a duplicate → 409 MMF044.
- [ ] Every item fails on a non-connectivity error → 500 MMF045.
- [ ] Book unreachable on the first item → 503 MMF046, and no further insert is attempted for that book.
- [ ] `SWEEP_NO` comes from `NVL(MAX(SWEEP_NO), 0) + 1`; the insert SQL uses `SWEEP_TYPE 'P'`, `SRC_TYPE 'C'/'M'`, `SEGMENT_ID 0` and `UPD_TIME = SYSDATE`.
- [ ] A `SWEEP_NO` PK collision is retried once with a fresh `MAX+1`; a REF_ID duplicate is not retried.

*Custodian cash*
- [ ] No `clientId` → 400 MMF050.
- [ ] `clientId=12ab` → 400 MMF051.
- [ ] 501 ids → 400 MMF052. 500 ids are accepted.
- [ ] Duplicate ids are de-duplicated before the repository call.
- [ ] No matches → 200 with `custodianCash: []`.
- [ ] `cashBalance` is a JSON number.
- [ ] DB error → 500 MMF055. CAPITAL unreachable → 503 MMF056.
- [ ] Only the `brok` template is used.

*Custodian positions*
- [ ] A future `asOfDate` → 400 MMF060. `asOfDate=2026-13-01` → 400 MMF060.
- [ ] No `asOfDate` → the repository is called with `null`, and the SQL uses `NVL(:asOfDate, TRUNC(SYSDATE) - 1)`.
- [ ] Empty `mmf.custodian.client-ids` → 500 MMF065, with no repository call.
- [ ] The configured ids are passed as a bound list.
- [ ] Rows are mapped by column name. `quantity` and `closePrice` are numbers; `market` is DFM, DIFX, ADX or null; `priceDate` is set.
- [ ] DB error → 500 MMF066. Unreachable → 503 MMF067.

*Envelope and build*
- [ ] Every error body has:
  - `responseCode` equal to the HTTP status;
  - the matching reason phrase;
  - the listed `errorCode`;
  - a non-null `correlationId` that equals the `X-Correlation-ID` request header when one is supplied.
- [ ] `mvn -pl services/wealth-management-service -am test` passes under the test profile (`brok` and `integration` disabled).

**Housekeeping**
- [ ] `services/wealth-management-service/apiCollection.http` has one sample request per new endpoint (7 in total).
- [ ] No files changed under `infra/`, `.github/workflows/` or `db/changelog/`.

---

## 9. Self-Critique

**Confidence: medium.** The contract, codes, statuses and transaction semantics are pinned by the
design doc and decisions Q1–Q5. The datasource, repository, error-handling, config and codegen
conventions are all cited from real files.

Confidence is lower than for a sweep-only scope because adding the custodian endpoints brings in
legacy SQL that the design doc gives only in part. The `MMFIntegrations.zip` export is not in this
repo (checked), so the custodian positions join predicates and the account join are **inferred** from
the Appendix C.1 column lists.

**Residual non-blocking assumptions**
1. **Datasource mapping.** CAPITAL is `brok` and MMF is `integration`. This assumes `brok` really
   points at the RMZ Capital INSIGHT schema. The MMF connection details are supplied by
   `COMPANY_DATASOURCE_INTEGRATION_*` env vars.
2. **Q3 + Q4 combined for profit distributions.** Duplicate detection is per item (MMF044 inside a
   207). The 409 is returned only when *every* item is a duplicate. There is no "identical replay
   returns the original result" behaviour. MMF045 is reinterpreted as "no item could be created".
3. **Index name dependency.** Telling duplicates apart relies on the DBA using the exact index name
   `UX_SWEEP_PROFIT_REF`. Any other name turns duplicates into MMF047.
4. **Amount and unused columns.** `amount` must be > 0; Appendix E #9 is unresolved. `JV_NO` and
   `USR_CODE` stay NULL.
5. **Error codes on 500/503.** These responses carry an errorCode, following the utility precedent.
6. **Silent input handling.** Duplicate confirmation items and duplicate `clientId` values are
   de-duplicated silently. An account lookup that returns several rows takes the first.
7. **Custodian-positions dates.** `asOfDate` defaults to calendar yesterday (legacy behaviour), not
   "previous business day": there is no business-day calendar in this repo. "Future" is judged
   against `Asia/Dubai` today.
8. **F-16 is only partly addressed.** The client list moves to config and `asOfDate` becomes a
   parameter. `USR_CODE = 3` and the market-code mapping stay as SQL constants, because they never
   change today. Move them to `MmfProperties` if they ever need to vary.
9. **Masking and exposure.** Account and client numbers and balances are not masked in the audit
   log, matching reporting's precedent. Custodian-cash exposes any client's balance to the Athena
   role, which the user accepted (Q2). `04_security` should review both.
10. **Sizing.** `service.yaml` is unchanged. A 5000-item batch means about 10,000 sequential Oracle
    round-trips on a 10-connection pool. `02_plan` should confirm batch frequency. The upgrade path
    is a `batchUpdate`, which would need a DB sequence (or a block of `SWEEP_NO`s reserved under a lock).

**Verify items before go-live** (these do not block the build)
- **F-09:** confirm the live column set and order of legacy `getCustodianPositions`. Port the exact
  join predicates from the IS adapter `getCustodianPositions` (`node.ndf`). The spec maps by name, but
  the join itself is inferred.
- **F-16:** confirm that `USR_CODE = 3` and market codes 5/8/6 are still correct.
- **Appendix E #8 (types):** confirm the column type of `CB_CLIENT.CL_CLIENT_ID` (the spec binds a
  String), the return type of `F_Get_Balance_As_Of` (the spec reads `cashBalance` as BigDecimal), and
  the types of `F_PORT.QTY` / `PR_C_PRICE` (the spec reads `quantity`/`closePrice` as BigDecimal).
- **F-12:** `UPD_TIME` is a DATE (the spec writes `SYSDATE`).
- **Appendix E #6:** confirm the meanings of `W`/`I`/`O`/`P`.
- **Appendix E #10:** identify the other `SWEEP_FACILITY` writers.
- **The account join predicate.**
- **Jackson dates.** `LocalDate` must serialise as `yyyy-MM-dd` even though `JacksonConfig` uses a
  plain `new ObjectMapper()` with no JavaTimeModule. `03_implement` must check this in a MockMvc test.

**Weakest part.** Two pieces:
- **§6.2's per-operation mapping** of Spring/Jackson input-binding exceptions to `MMF###` codes.
  Utility's `ErrorCode.forField` is keyed on field only, but here field names are reused across
  operations. The behaviour of `HandlerMethodValidationException` on repeated query parameters and
  `List` bodies also differs between Spring versions. `02_plan` should design and test this first.
  The fallback is explicit service-layer validation that throws `ApplicationException`.
- **The inferred custodian-positions SQL** (see F-09 above).
