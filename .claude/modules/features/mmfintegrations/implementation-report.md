# Implementation Report: mmfintegrations

## 1. Summary
I built all seven MMF / Athena operations in `services/wealth-management-service`, following the approved
plan. The work covers:
- **Contract and API:** the contract-first `MmfApi`, implemented by `MmfController` under `/api/v1/mmf/...`
  and secured with `@JwtSecured(APP_WEALTH_MANAGEMENT_SERVICE_USER)`.
- **Error handling:** a new `com.alramz.exception` package. It has an `ErrorCode` table keyed by operation,
  five typed exceptions, and a single `@Order(0)` `GlobalExceptionHandler`.
- **Business logic:** `MmfServiceImpl`. Confirmation locks per book, nesting CAPITAL as the outer
  transaction and MMF as the inner one, and detects MMF037. Profit distribution handles each item
  separately, keeps a list of unreachable books, and works out the overall 201/207/409/500/503 result.
  Custodian positions use a `Clock` for the future-date check.
- **Data access:** `MmfRepositoryImpl`, using the two `@Qualifier`'d templates and SQL from
  `sql/sql-queries.xml` (9 keys, binds only).
- **Configuration:** `MmfProperties` and `MmfConfiguration`, plus the `integration` datasource block and
  `mmf.custodian.client-ids` in every profile YAML.
- **Manual requests:** 7 request/negative-case pairs in `apiCollection.http`.

Only one service was touched: `wealth-management-service`. The starter, the BOM, `infra/`, `k8s/` and
`.github/` were left alone.

## 2. Files Touched
All paths are under `services/wealth-management-service/`.

| File | Status | Note |
|---|---|---|
| `specs/apiSpecs.yaml` | modified | 7 bare `/mmf/...` paths, 17 `Mmf*` schemas, `GenericResponse` extended |
| `src/main/java/com/alramz/exception/ErrorCode.java` | new | |
| `src/main/java/com/alramz/exception/ApplicationException.java` | new | |
| `src/main/java/com/alramz/exception/MmfNotFoundException.java` | new | |
| `src/main/java/com/alramz/exception/MmfConflictException.java` | new | |
| `src/main/java/com/alramz/exception/MmfBackendException.java` | new | |
| `src/main/java/com/alramz/exception/MmfBackendUnavailableException.java` | new | |
| `src/main/java/com/alramz/exception/GlobalExceptionHandler.java` | new | Adds a `ConstraintViolationException` handler not in the plan (see §6 D2) |
| `src/main/java/com/alramz/config/MmfProperties.java` | new | |
| `src/main/java/com/alramz/config/MmfConfiguration.java` | new | |
| `src/main/java/com/alramz/controllers/MmfController.java` | new | Explicit constructor, not `@RequiredArgsConstructor` (see §6 D3) |
| `src/main/java/com/alramz/service/MmfService.java` | new | |
| `src/main/java/com/alramz/service/impl/MmfServiceImpl.java` | new | |
| `src/main/java/com/alramz/repository/MmfRepository.java` | new | |
| `src/main/java/com/alramz/repository/MmfRepositoryImpl.java` | new | |
| `src/main/resources/sql/sql-queries.xml` | new | |
| `src/main/resources/application.yml` | modified | `sql.file`, `spring.jackson.deserialization.accept-float-as-int: false` |
| `src/main/resources/application-dev.yml` | modified | `integration` block (enabled default false), `mmf.custodian.client-ids` |
| `src/main/resources/application-docker.yml` | modified | same as dev |
| `src/main/resources/application-preprod.yml` | modified | `integration` block (password + passwordVector, enabled default true), `mmf.custodian.client-ids` |
| `src/main/resources/application-prod.yml` | modified | same as preprod |
| `src/main/resources/application-test.yml` | modified | `integration.enabled: false` |
| `apiCollection.http` | modified | 7 blocks plus 7 `-ve test case` blocks; reuses the existing `@authToken` |
| `src/test/resources/application.yml` | modified | `accept-float-as-int: false`, plus an H2 `company.datasource.middleware` block (see §6 D1) |
| `src/test/java/com/alramz/exception/ErrorCodeTest.java` | new | |
| `src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java` | new | |
| `src/test/java/com/alramz/controllers/MmfApiDispatchTest.java` | new | |
| `src/test/java/com/alramz/controllers/MmfControllerTest.java` | new | |
| `src/test/java/com/alramz/service/impl/MmfServiceImplTest.java` | new | |
| `src/test/java/com/alramz/repository/MmfRepositoryImplTest.java` | new | |
| `src/test/java/com/alramz/config/MmfPropertiesTest.java` | new | |

Every file above is listed in `execution_plan.json`. `WealthManagementServiceApplicationTests.java`
remains unchanged, as the plan says.

## 3. Test Results
Every command was run from the repo root, scoped to `services/wealth-management-service`. The
`-Dtest` runs also passed `-Dsurefire.failIfNoSpecifiedTests=false`, because `-am` pulls in the starter
module, which has no matching test.

| # | Command | Exit | Note |
|---|---|---|---|
| 1 | `mvn -pl services/wealth-management-service -am clean test` (baseline, before any edit) | 1 | **Already failing before this work:** `WealthManagementServiceApplicationTests.contextLoads` could not find a `middlewareDataSource` bean (see §6 D1) |
| 2 | `mvn ... -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 1 | OpenAPI spec validation failed: an unquoted comma in the `'401'` flow-map descriptions. Fixed. |
| 3 | `mvn ... -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | `MmfApi` generated; codegen facts recorded (§7) |
| 4 | `mvn ... -am test -Dtest=ErrorCodeTest` | 0 | 42 tests |
| 5 | `mvn ... -am test -Dtest=GlobalExceptionHandlerTest,ErrorCodeTest` | 1 | One test bug: an empty `MethodValidationResult` cannot be built. Test changed to a cross-parameter-only result. |
| 6 | `mvn ... -am test -Dtest=GlobalExceptionHandlerTest` | 0 | |
| 7 | `mvn ... -am test -Dtest=MmfApiDispatchTest` | 1 | **Red phase that drove D2:** 20 of 47 failed (constraint violations came back as 200) |
| 8 | `mvn ... -am test -Dtest=MmfApiDispatchTest` | 0 | 47 tests, after the D2 handler change and wrapping the controller in the context's `MethodValidationPostProcessor` |
| 9 | `mvn ... -am test -Dtest=GlobalExceptionHandlerTest` | 0 | 43 tests, including the new `ConstraintViolationException` cases |
| 10 | `mvn ... -am test -Dtest=MmfRepositoryImplTest,MmfPropertiesTest` | 0 | 14 + 3 tests |
| 11 | `mvn ... -am test -Dtest=MmfServiceImplTest` | 0 | 31 tests |
| 12 | `mvn ... -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | |
| 13 | `mvn -pl services/wealth-management-service -am clean test` | 1 | 56 errors, all "Unresolved compilation problem" in `MmfController.<init>` (see §6 D3) |
| 14 | `mvn -pl services/wealth-management-service -am clean test` | 1 | Same as 13; it happens every run, not intermittently |
| 15 | **`mvn -pl services/wealth-management-service -am clean test`** | **0** | **Final gate. Wealth: 190 tests, 0 failures, 0 errors (starter: 130, 0 failures). BUILD SUCCESS.** |

## 4. Test Coverage
Line and branch percentages come from `services/wealth-management-service/target/site/jacoco/jacoco.csv`,
produced by run #15.

**Exception-package gap.** The BOM's JaCoCo agent is configured with
`excludes=**/model/**:**/api/**:**/exception/**:...` (see the `argLine` in the build log). As a result,
nothing in `com.alramz.exception` is instrumented and it has no row in `jacoco.csv`. Changing the BOM is
out of scope, so these classes are marked EXCEPTION with their covering tests listed. This exclusion
means the >80% gate is unverified for 7 of the 11 new Java classes. It is not proven.

| Class / file | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| `com.alramz.controllers.MmfController` | new | 100% (21/21) | 100% | `MmfControllerTest`, `MmfApiDispatchTest` | PASS |
| `com.alramz.service.impl.MmfServiceImpl` | new | 98.6% (144/146) | 97.0% | `MmfServiceImplTest` | PASS |
| `com.alramz.repository.MmfRepositoryImpl` | new | 98.7% (78/79) | 93.8% | `MmfRepositoryImplTest` | PASS |
| `com.alramz.config.MmfProperties` (+ nested `Custodian`) | new | 100% (2/2), 100% (2/2) | — | `MmfPropertiesTest`, `MmfServiceImplTest` | PASS |
| `com.alramz.config.MmfConfiguration` | new | 100% (1/1) | — | `WealthManagementServiceApplicationTests`, `MmfApiDispatchTest` (context load) | PASS |
| `com.alramz.service.MmfService` | new | n/a (interface, no executable lines) | — | implemented by `MmfServiceImpl` | EXCEPTION: interface with only constants and abstract methods; JaCoCo has no row |
| `com.alramz.repository.MmfRepository` | new | n/a (interface) | — | implemented by `MmfRepositoryImpl` | EXCEPTION: interface with only abstract methods; JaCoCo has no row |
| `com.alramz.exception.ErrorCode` | new | not instrumented | — | `ErrorCodeTest` (42 cases: every forInput row, internal/unavailable for all 7 ops, fallbacks, uniqueness, messages) | EXCEPTION: excluded by the BOM JaCoCo `**/exception/**` rule |
| `com.alramz.exception.GlobalExceptionHandler` | new | not instrumented | — | `GlobalExceptionHandlerTest` (43), `MmfApiDispatchTest` (47) | EXCEPTION: excluded by the BOM JaCoCo `**/exception/**` rule |
| `com.alramz.exception.ApplicationException` | new | not instrumented | — | `GlobalExceptionHandlerTest`, `MmfServiceImplTest` | EXCEPTION: BOM exclusion; value holder with no branches |
| `com.alramz.exception.MmfNotFoundException` | new | not instrumented | — | `GlobalExceptionHandlerTest#notFound_shouldReturn404`, `MmfServiceImplTest#getAccount_notFound_shouldThrowMMF004` | EXCEPTION: BOM exclusion; value holder with no branches |
| `com.alramz.exception.MmfConflictException` | new | not instrumented | — | `GlobalExceptionHandlerTest#conflict_shouldReturn409`, `MmfServiceImplTest` (MMF033/MMF044) | EXCEPTION: BOM exclusion; value holder with no branches |
| `com.alramz.exception.MmfBackendException` | new | not instrumented | — | `GlobalExceptionHandlerTest#backend_*`, `MmfServiceImplTest` (037/045/065), `MmfRepositoryImplTest#sqlLoadFailure_*` | EXCEPTION: BOM exclusion; value holder with no branches |
| `com.alramz.exception.MmfBackendUnavailableException` | new | not instrumented | — | `GlobalExceptionHandlerTest#backendUnavailable_shouldReturn503`, `MmfServiceImplTest#profit_bookUnreachableOnFirstItem_*` | EXCEPTION: BOM exclusion; value holder with no branches |
| `specs/apiSpecs.yaml` | modified | n/a (contract) | n/a | build #3/#15 (codegen + compile), `MmfApiDispatchTest` (real dispatch against the generated `MmfApi`) | PASS |
| `src/main/resources/sql/sql-queries.xml` | new | n/a (resource) | n/a | `MmfRepositoryImplTest#sqlFile_shouldDefineAllKeysWithBindsOnly` | PASS |
| `application.yml` | modified | n/a | n/a | `MmfApiDispatchTest` (1.5 → MMF032 proves accept-float-as-int is active, via the shadowing test yml); `sql.file` itself is not loaded in any test (MMF beans are off) | PASS |
| `application-{dev,docker,preprod,prod}.yml` | modified | n/a | n/a | not loaded by any test; checked by parsing all 5 profiles with PyYAML and grepping `permit-all-urls` / `plainPassword` | PASS (inspection only) |
| `application-test.yml` | modified | n/a | n/a | not loaded by the test suite (no test activates profile `test`); checked by inspection | PASS (inspection only) |
| `apiCollection.http` | modified | n/a | n/a | manual file, checked by inspection | PASS (inspection only) |

**Generated code.** `org.openapitools.configuration.EnumConverterConfiguration` (0%) is emitted by the
generator because query parameters are enums. It is outside the `com.alramz` component scan and is not
hand-written, so it is not a target file.

## 5. Acceptance Criteria Verification

**Contract**
- [x] The 7 bare paths, all tagged `Mmf`, with the right operationIds: `MmfApi` is generated with
  exactly these 7 methods (run #3), and `MmfApiDispatchTest` calls every path under `/api/v1/mmf`.
- [x] Every status from §3.1–3.7 is declared, with examples (named `examples` where one status has
  several codes) and description-only `'401'`/`'403'`. Checked by inspecting `apiSpecs.yaml` and the
  generated `@ExampleObject`s.
- [x] `GenericResponse` has `correlationId` (uuid), `errorCode` (nullable) and `errorMsg` (nullable).
  Proven by the generated model and by `GlobalExceptionHandlerTest`/`MmfControllerTest` using the setters.
- [x] `Mmf*` schemas carry their required fields, patterns and min/maxItems. Codegen emitted
  `@Size(1,1000)`, `@Size(1,5000)`, `@Size(1,500)`, `List<@Valid ...>`, `List<@Pattern String>`, the path
  `@Pattern`, `@Min(1)` and `@DecimalMin(inclusive=false)`. All are exercised by `MmfApiDispatchTest`.
- [x] `mvn ... test-compile` succeeds (runs #3 and #12).
- [x] `MmfController implements MmfApi` with `@RequestMapping("/api/v1")`.

**Security and config**
- [x] All 7 controller methods carry `@JwtSecured(roles = "APP_WEALTH_MANAGEMENT_SERVICE_USER")`. Checked
  by inspecting the code; there is no runtime test because the MMF beans are off in tests.
- [x] `permit-all-urls` is unchanged: it still lists only `/api/v1/info` in dev, docker, preprod, prod and
  test (grep).
- [x] Valid JWT without the role → 403, not 500:
  `GlobalExceptionHandlerTest#accessDenied_shouldReturn403WithNullCodeAndNoRoleText`. This is the
  handler-level proof the plan accepted.
- [x] `integration` block in dev, docker, preprod and prod; no literal password in preprod/prod (they use
  `password` + `passwordVector` from env vars). Checked by grep and PyYAML parse.
- [x] `application-test.yml` has `integration.enabled: false`.
- [x] `MmfProperties` binds `mmf.custodian.client-ids`: `MmfPropertiesTest`.
- [x] `mmf.custodian.client-ids: ${MMF_CUSTODIAN_CLIENT_IDS:}` is in dev, docker, preprod and prod.
- [x] `application.yml` sets `sql.file: classpath:sql/sql-queries.xml`.
- [x] All 9 SQL keys exist: `MmfRepositoryImplTest#sqlFile_shouldDefineAllKeysWithBindsOnly`.
- [x] No concatenation in SQL (checks that no entry contains `||` or `${`); every list is a `:named` bind.
  Bind values are captured in `MmfRepositoryImplTest`.
- [x] The repository injects only the brok and integration NPJT qualifiers (constructor), and
  `MmfRepositoryImplTest` checks that each book uses only its own template.
- [x] Transaction managers are named explicitly: `MmfServiceImpl` constructor
  `@Qualifier("brokTransactionManager")` / `@Qualifier("integrationTransactionManager")`, with no
  `@Transactional`. `MmfServiceImplTest` checks commit and rollback on each TM.

**Account**
- [x] Found → 200 with `response.account`: `MmfApiDispatchTest#account_found_*`, `MmfControllerTest#getMmfAccount_shouldWrapAccount`
- [x] Not found → 404 MMF004: `MmfServiceImplTest#getAccount_notFound_shouldThrowMMF004`, `GlobalExceptionHandlerTest#notFound_shouldReturn404`
- [x] `12ab` → MMF001, `book` missing → MMF002, `book=FOO` → MMF003: `MmfApiDispatchTest#getInputErrors_*` rows 1–4
- [x] Connection failure → 503 MMF006; other `DataAccessException` → 500 MMF005: `GlobalExceptionHandlerTest#resourceFailures_*` / `#otherDataAccessErrors_*`

**Sweep-ins / sweep-outs**
- [x] Only `fromDate` → MMF010/020: `MmfServiceImplTest#listSweeps_onlyOneDate_*`
- [x] `2025-02-31` → MMF011/021: `MmfApiDispatchTest#getInputErrors_*` rows 5–7
- [x] `from > to` or 94 days → MMF012/022, and 93 days is accepted: `MmfServiceImplTest#listSweeps_fromAfterTo_orOver93Days_*`, `#listSweeps_93Days_*`
- [x] Empty → `[]`: `MmfApiDispatchTest#sweepOuts_withoutDates_shouldPassNulls`, `MmfControllerTest#listMmfSweepOuts_*`
- [x] Rows merged, tagged and sorted: `MmfServiceImplTest#listSweeps_shouldMergeAndSortByDateThenSweepNo`; source tagging in `MmfRepositoryImplTest#findSweeps_range_*`
- [x] Today path with no dates, `toDateExclusive = to + 1` for a range: `MmfServiceImplTest#listSweeps_noDates_*`, `#listSweeps_93Days_*`
- [x] DB errors → MMF015/025, unreachable → MMF016/026: `GlobalExceptionHandlerTest` parameterized rows

**Confirm sweep-ins**
- [x] `[]` and 1001 items → MMF030: `MmfApiDispatchTest#confirm_inputErrors[1]`, `#confirm_1001Items_*`
- [x] Missing `source` → MMF031: `MmfApiDispatchTest#confirm_inputErrors[2]`
- [x] `sweepNo: 0` → MMF032 (also `-5`, `1.5` and `"abc"`): `MmfApiDispatchTest#confirm_inputErrors[5..8]`
- [x] Missing or `O`/`P` in either book → 409 MMF033, no UPDATE in either book, both rolled back: `MmfServiceImplTest#confirm_anyUnknownOrNotPending_*`, `#confirm_singleBookConflict_typeP_*`
- [x] W counted, I listed, 200: `MmfServiceImplTest#confirm_bothBooks_*`; all-I: `#confirm_allAlreadyConfirmed_*`; de-duplication: `#confirm_duplicateItems_*`
- [x] The UPDATE contains `AND SWEEP_TYPE = 'W'`: `MmfRepositoryImplTest#sqlFile_*`
- [x] Second commit fails → 500 MMF037 plus an ERROR log: `MmfServiceImplTest#confirm_outerCommitFailsAfterInnerCommitted_*` (captures the logback ERROR line)

**Profit distributions**
- [x] 400s with no write: `[]` → MMF040; bad date, amount ≤ 0 or bad referenceId → MMF041; 10-digit
  account → MMF042; `source=X` → MMF043. Proven by `MmfApiDispatchTest#profit_inputErrors`. A 4-decimal
  amount → MMF041 with no repository interaction: `MmfServiceImplTest#profit_amountPrecision_*`.
- [x] All inserted → 201 / "201", results in order, each with a sweepNo: `MmfServiceImplTest#profit_allInserted_*`, `MmfApiDispatchTest#profit_allCreated_shouldReturn201`
- [x] `UX_SWEEP_PROFIT_REF` duplicate plus a success → 207 with that item MMF044: `MmfServiceImplTest#profit_duplicateRefPlusSuccess_*`, `MmfApiDispatchTest#profit_mixed_shouldReturn207`
- [x] Other DB error plus a success → MMF047: `MmfServiceImplTest#profit_otherDuplicateOrDbErrorPlusSuccess_*`
- [x] No raw DB text in results: the same test asserts `doesNotContain("ORA-")`
- [x] All duplicates → 409 MMF044: `MmfServiceImplTest#profit_allDuplicates_*`
- [x] All non-connectivity failures → 500 MMF045: `MmfServiceImplTest#profit_allNonConnectivityFailures_*`
- [x] Unreachable on the first item → 503 MMF046 with no further attempt (`times(1)`): `MmfServiceImplTest#profit_bookUnreachableOnFirstItem_*`; mid-batch case (E31): `#profit_bookUnreachableMidBatch_*`
- [x] The insert SQL uses NEXTVAL (separate key), `'P'`, `:srcType` (C/M), `SEGMENT_ID 0` and `SYSDATE`, and has no `MAX(`: `MmfRepositoryImplTest#sqlFile_*`, `#insertProfitDistribution_*`

**Custodian cash**
- [x] No `clientId` → MMF050, `12ab` → MMF051, 501 ids → MMF052, 500 ids accepted: `MmfApiDispatchTest`
- [x] De-duplicated before the repository call: `MmfServiceImplTest#custodianCash_shouldDeduplicateIdsBeforeRepositoryCall`
- [x] No matches → `[]`: `MmfControllerTest` (empty list envelope)
- [x] `cashBalance` is a JSON number: `MmfApiDispatchTest#custodianCash_500Ids_shouldBeAcceptedAndCashBalanceIsANumber`
- [x] DB error → MMF055, unreachable → MMF056: `GlobalExceptionHandlerTest` parameterized rows
- [x] Only the brok template is used: `MmfRepositoryImplTest#findCustodianCash_shouldUseBrokOnly*`

**Custodian positions**
- [x] Future date → MMF060: `MmfServiceImplTest#custodianPositions_futureDate_*`; `2026-13-01` → MMF060: `MmfApiDispatchTest`
- [x] No `asOfDate` → null passed, `Types.DATE` typed null, SQL uses `NVL(:asOfDate, TRUNC(SYSDATE) - 1)`: `MmfServiceImplTest#custodianPositions_nullDate_*`, `MmfRepositoryImplTest#findCustodianPositions_shouldTypeNullAsOfDate*`, `#sqlFile_*`
- [x] Empty client-ids → 500 MMF065 with no repository call: `MmfServiceImplTest#custodianPositions_emptyOrNullConfig_*`
- [x] Configured ids passed as a bound list: `MmfServiceImplTest#custodianPositions_nullDate_*`, `MmfRepositoryImplTest`
- [x] Mapped by name; numbers, market (or null) and priceDate set: `MmfRepositoryImplTest#findCustodianPositions_*`, `MmfApiDispatchTest#custodianPositions_shouldSerializePriceDateAndMarket`
- [x] DB error → MMF066, unreachable → MMF067: `GlobalExceptionHandlerTest` parameterized rows

**Envelope and build**
- [x] `responseCode` equals the status, the reason phrase matches, the code is the listed one, and
  `correlationId` is non-null and echoes `X-Correlation-ID`. Proven by `GlobalExceptionHandlerTest#assertError`
  (every case) and `MmfApiDispatchTest#errorBody_shouldEchoCorrelationIdHeader` /
  `#account_found_*` (real `CorrelationIdFilter`).
- [x] `mvn -pl services/wealth-management-service -am test` passes with brok and integration disabled: run #15, exit 0.

**Housekeeping**
- [x] `apiCollection.http` has one request per endpoint (7), each followed by a `-ve test case` block.
- [x] No files changed under `infra/`, `.github/workflows/` or `db/changelog/`. All edits were confined
  to the files in §2. This was not confirmed with git, because `git` is outside this stage's allowed commands.

## 6. Deviations & Decisions
`AskUserQuestion` was not used. None of the issues below met all three blocking-gap criteria: none changes
the API contract, schema or production behaviour beyond what the spec requires, and none is expensive to
reverse. They are logged as judgment calls.

- **D1. The suite was already failing.** `WealthManagementServiceApplicationTests` failed before any edit
  (run #1). `@SpringBootTest` boots with no profile, so only `src/test/resources/application.yml` loads,
  and that file had no `company.datasource.middleware` block. `DatasourceConfiguration` therefore could
  not find `middlewareDataSource`. The plan assumed this test runs under profile `test`; it does not.
  - **Fix:** in the already-planned `src/test/resources/application.yml`, I added the same H2 middleware
    block that utility-service uses for the same reason. The smoke test file itself is unchanged.
  - **Consequence:** `MmfApiDispatchTest` uses plain `@SpringBootTest`, not
    `properties="spring.profiles.active=test"` as the plan said, because `application-test.yml` points
    middleware at `localhost:5432` Postgres.
- **D2. Validation goes through AOP, not MVC built-in validation (found by the step-4 de-risk).** The
  generated `MmfApi` is annotated `@Validated`. Spring 7 therefore turns off its built-in MVC method
  validation for this controller (`HandlerMethod.shouldValidateArguments() == false`). Instead, Boot's
  `MethodValidationPostProcessor` proxy throws `jakarta.validation.ConstraintViolationException`. That
  covers path `@Pattern`, `@Size` on lists, `List<@Pattern String>`, element `@Min` and `@NotNull`, and
  `List<@Valid Item>`. Without handling it, the catch-all would have answered all of these with 500.
  - **Handler change:** added `handleConstraintViolation`. It picks the violation deterministically
    (lowest parameter, then parameter-level before element-level, then lowest item index). It resolves the
    input from the bean property, or from the parameter's `@RequestParam`/`@PathVariable`/`@RequestBody`.
  - **Test change:** `MmfApiDispatchTest` wraps the controller with the context's real
    `MethodValidationPostProcessor`, so it uses the same validation path as production.
  - **No service fallback needed:** every framework case in E1–E45 now resolves correctly. The only
    service-level validation is the planned amount precision check.
  - **Kept as defensive code:** the `MethodArgumentNotValidException` and `HandlerMethodValidationException`
    handlers, in case the generator stops emitting `@Validated`. Both are unit-tested.
- **D3. Explicit constructor in `MmfController`.** Runs #13 and #14 failed because VS Code's Java language
  server (redhat.java / JDT) rebuilds `target/classes` right after Maven compiles. Its ECJ compile of the
  Lombok `@RequiredArgsConstructor` on `MmfController` fails ("Flexible Constructor Bodies ... source level
  25"), and the broken class replaced javac's. I replaced the annotation with a 3-line explicit constructor,
  with a comment explaining why. Other classes in this repo use explicit constructors too, because they
  need `@Qualifier`. If the IDE issue is fixed, reverting to `@RequiredArgsConstructor` is a one-line change.
- **D4. Shape of `ErrorCode`.** The constants are `(code, defaultMessage)`, plus a static rule table
  keyed by `(operationId, input, kind)`. This replaces the planned 5-argument constant. Several codes map
  more than one input, each with its own message: MMF011 covers fromDate and toDate; MMF031 covers sweepNo
  MISSING and source; MMF041 covers sweepDate, amount and referenceId. I added `inputMessage()` alongside
  `forInput`, `internalFor` and `unavailableFor`. The lookup semantics are as planned: exact match, then
  ANY, then MMF090.
- **D5. Account SQL aliases.** `mmf.account.find` aliases every column (`CLIENT_ID`, `CLIENT_NAME_EN`,
  `ACCOUNT_NAME`, ...). The spec's select list has two `CLE_CLIENT_NAME` columns, which cannot be mapped
  by name.
- **D6. OpenAPI 3.0.3 encodings.**
  - `amount` uses `minimum: 0, exclusiveMinimum: true` (the 3.0 boolean form).
  - `MmfCustodianPosition.market` is a plain `$ref`, because 3.0 does not allow `nullable` next to a `$ref`.
    The value is still null for an unmapped market code.
  - The spec gives no text for a missing `sweepNo`; I used "sweepNo is required" (MMF031).
- **D7. Extra repository guard.** An unknown SQL key (the manager returns null) also raises
  `MmfBackendException(null)`, which the handler turns into the operation's 500 code.
- **D8. Where `isResourceFailure` lives.** The shared resource-failure check is
  `GlobalExceptionHandler.isResourceFailure`, which the plan allowed. The service uses it for the profit
  unreachable-book short-circuit.

## 7. Howtos Consulted
- **`testing-patterns.md`:** Mockito-first with direct construction and AssertJ, and no `@WebMvcTest`. The
  only MockMvc use is the plan-approved standalone dispatch test inside `@SpringBootTest`. It uses the
  `@SpringBootTest` style rather than `@ActiveProfiles`.
- **`exception-handling.md`:**
  - One advice at `@Order(0)` scoped to `com.alramz.controllers`; `OnboardingExceptionHandler`'s second
    advice at `@Order(1)` is not copied.
  - Exceptions carry no `HttpStatus`; the handler decides the status.
  - An explicit `AccessDeniedException` → 403 handler so the catch-all does not swallow it.
- **`openapi-contract-first-controllers.md`:** bare spec paths with the controller prefix; implements the
  generated interface; nothing under `target/generated-sources` edited. Codegen facts recorded after run #3:
  - `@Size(min,max)` on both list bodies, and `List<@Valid Item>` cascading.
  - Path `@Pattern`, and `List<@Pattern String>` for `clientId`.
  - `@DecimalMin(inclusive=false)` on amount.
  - `MmfBook.fromValue`.
  - **Missing from the plan's checklist:** interface-level `@Validated`, which is what caused D2.
- **`api-collection-http-requests.md`:** reuses the existing `@authToken` and register/login blocks; adds a
  paired `-ve` block for each endpoint; uses synthetic numbers and no token.
- **`configuration-properties.md`:** `@Getter @Setter`, a `PREFIX` constant, and a dedicated
  `@EnableConfigurationProperties` `@Configuration`, with no `@ConfigurationPropertiesScan`.
- **`multi-datasource-and-jpa.md`:** every brok/integration template and TM is injected with
  `@Qualifier`; no bare `@Transactional` (the `@Primary` TM is middleware).
- **`jwt-security-and-public-endpoints.md`:** endpoints are secured and `permit-all-urls` is unchanged.
- **`request-response-and-db-audit-logging.md`:** read; no masking keys changed, per spec §5.
- **`new-endpoint-elicitation-checklist.md`:** read; every status in the spec has an example.

## 8. Self-Critique

**Confidence: medium-high.** Everything this repo can test is tested and green, with real-dispatch proof
for the input-validation codes. Confidence is not high because some parts can't be exercised here:
- the Oracle SQL itself (the inferred joins);
- the transaction behaviour against real TMs;
- end-to-end JWT ordering with the MMF beans enabled.

**Red phase.** Each test file was written before its implementation. Only `MmfApiDispatchTest` (run #7,
20 of 47 red) and the handler test bug (run #5) produced a separately observed red run. The other test
classes were not run red before implementation; the missing production classes would have been compile
failures.

**Residual, non-blocking risks**
- **Coverage gap.** The BOM excludes `**/exception/**` from JaCoCo. The >80% gate is therefore unverified
  for 7 of the 11 new Java classes, including `GlobalExceptionHandler` and `ErrorCode`, the two classes
  with the most logic. They have 85 dedicated unit tests and 47 dispatch tests. Measuring them needs a BOM
  change, which is outside this stage's scope.
- **Wrong joins pass the tests.** The inferred SQL (account join, custodian-positions joins,
  `USR_CODE = 3`, market codes 5/8/6, unqualified `CUR_CODE`/`QTY`/`PR_PRICE_DATE` in the positions union)
  is checked only for text; a wrong join would still pass. Verify against the IS export or preprod before
  go-live (plan technical_gaps #3).
- **Startup coupling (plan TG #1):** if the integration env vars are missing in preprod/prod, the whole
  service fails to start.
- **Index name (plan TG #2):** MMF044 only works if the DBA names the index exactly `UX_SWEEP_PROFIT_REF`.
- **Lock wait (plan TG #9):** `FOR UPDATE` has no `WAIT`, so a blocked confirmation waits up to the
  60-second read timeout.
- **Batch sizing (plan TG #10):** a 5000-item profit batch is about 10,000 sequential round-trips.
- **`accept-float-as-int: false` is service-wide.** It applies to every endpoint in wealth; the impact is
  negligible (plan blast radius). Verified working on Jackson 3, which is what MVC uses.
- **D3 depends on the developer's IDE:** if the language server keeps rebuilding `target/classes`, other
  Lombok-heavy classes could hit the same ECJ problem.

**Technical gaps from the plan**
- Resolved in code:
  - #4 (validation dispatch), via D2.
  - #5 (Jackson major: Jackson 3, `tools.jackson.core.JacksonException.getPath()`; ISO dates and 1.5
    rejection both proven).
  - #7 (503 classification, including `CannotCreateTransactionException`).
  - #8 (`PRICE_DATE` selected; `asOfDate` bound with `Types.DATE`).
  - #11 (howto near-misses avoided).
- Mitigated:
  - #12 (PMD/SpotBugs). PMD runs at warning level during `validate` (192 findings across the module,
    mostly generated code; the new code triggered `ExcessiveImports`/`ExcessivePublicCount` warnings). The
    build does not fail on them. `verify`/static analysis is outside this stage.
- Still residual: #1, #2, #3, #6 (dev/docker default `enabled=false`, as planned), #9, #10.

**Flag for 04_security**
1. **Validation runs after authorization.** Because of D2, constraint validation runs in the AOP proxy.
   Its order relative to `JwtSecuredAspect` (`@Before`) is set by advisor ordering and was not verified at
   runtime. In contrast, binding and type errors (missing param, bad enum or date, unreadable JSON)
   definitely produce 400 before the role check. So a caller with a valid JWT but without the role can
   learn validation messages, though not data.
2. **Unmasked data in logs.** Account numbers, referenceIds and sweep numbers appear unmasked in WARN/ERROR
   lines in `MmfServiceImpl` and `MmfRepositoryImpl`, and in the DB audit payloads (spec #9, accepted). Raw
   ORA text is logged but never returned; this is tested.
3. **Custodian exposure.** `custodian-cash` returns the balance of any `CL_CLIENT_ID` to the Athena role
   (Q2=B, accepted).
4. **New handler path.** The `ConstraintViolationException` handler reads the violation property path and
   the parameter annotations. Only fixed `ErrorCode` messages are returned; rejected values are never
   echoed.
5. **New startup dependency.** In preprod/prod, the integration datasource uses an encrypted password
   (`password` + `passwordVector`), with no literal secret in YAML.
