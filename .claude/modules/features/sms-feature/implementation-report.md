# Implementation Report: sms-feature (OTP send / validate / configurations)

## 1. Summary

This adds OTP capability to `services/alramz-notification-service`, the only target service:

- **Changeset `004-notification-otp-tables`** creates `otp_configuration`, `otp_management` and `otp_attempts`, byte-identical in all 5 profile folders.
- **`OtpRepository`** reads and writes those tables on the middleware datasource through `otp.*` SQL keys.
- **`OtpProperties`** holds `app.otp.fixed-test-otp-enabled` and `app.otp.email-from`.
- **`OtpException`** plus one `handleOtp` arm in the existing `@Order(0)` `GlobalExceptionHandler`.
- **Three contract-first endpoints** in `OtpController implements OtpApi`: `POST /api/v1/otp/send`, `POST /api/v1/otp/validate` and `GET /api/v1/otp/configurations`, all secured with `@JwtSecured(roles = "APP_NOTIFICATION")`.
- **`OtpService`** does the work. Send runs: checks -> tx1 (deactivate prior OTPs, insert PENDING active rows) -> SMS/email with no transaction open -> tx2 (SENT/FAILED, daily counters). Validate holds `FOR UPDATE` only for lock -> compare -> update, and throws OTP007 after commit.

Stage D (cleanup job and changeset 005) was dropped per Gate 1.

This run resumed after an API rate-limit interruption. None of the planned files existed on disk, so every stage (A-C) was carried out in this run.

## 2. Files Touched

All paths are under `services/alramz-notification-service/`.

| File | Status | Note |
|---|---|---|
| `src/main/resources/db/changelog/dev/sql/004-notification-otp-tables.sql` | new | |
| `src/main/resources/db/changelog/docker/sql/004-notification-otp-tables.sql` | new | byte-identical (md5 `b1205d24…` x5) |
| `src/main/resources/db/changelog/test/sql/004-notification-otp-tables.sql` | new | byte-identical |
| `src/main/resources/db/changelog/preprod/sql/004-notification-otp-tables.sql` | new | byte-identical |
| `src/main/resources/db/changelog/prod/sql/004-notification-otp-tables.sql` | new | byte-identical |
| `src/main/resources/sql/sql-queries.xml` | modified | header comment + 13 `otp.*` keys |
| `src/main/java/com/alramz/repository/OtpRepository.java` | new | nested records `OtpConfig`, `OtpRow`, `OtpCandidate` |
| `src/main/java/com/alramz/config/OtpProperties.java` | new | |
| `src/main/java/com/alramz/AlramzNotificationServiceApplication.java` | modified | `OtpProperties.class` added to `@EnableConfigurationProperties` |
| `src/main/resources/application.yml` | modified | `app.otp.*` (2 keys) |
| `src/main/resources/application-dev.yml` | modified | `fixed-test-otp-enabled: ${OTP_FIXED_TEST_OTP_ENABLED:true}`; JWT fallback line untouched |
| `src/main/resources/application-docker.yml` | modified | same as dev |
| `src/main/resources/application-test.yml` | modified | same as dev |
| `src/main/resources/application-preprod.yml` | modified | literal `false` |
| `src/main/resources/application-prod.yml` | modified | literal `false` + `email-from: ${OTP_EMAIL_FROM:cm@alramz.ae}` |
| `src/main/java/com/alramz/exception/OtpException.java` | new | |
| `src/main/java/com/alramz/exception/GlobalExceptionHandler.java` | modified | `handleOtp` arm only |
| `specs/openapi.yaml` | modified | 3 paths (tag `Otp`), 7 schemas, `GenericResponse` descriptions |
| `pom.xml` | modified | 7 models appended to `modelsToGenerate` (no version change) |
| `src/main/java/com/alramz/service/TemplatedEmailService.java` | modified | `EMAIL` widened `private` -> package-private |
| `src/main/java/com/alramz/service/impl/SmsServiceImpl.java` | modified | `public static isValidMobile` added |
| `src/main/java/com/alramz/service/OtpService.java` | new | |
| `src/main/java/com/alramz/controllers/OtpController.java` | new | |
| `apiCollection.http` | modified | 6 blocks added before Scheduler Management; the user's Register/Login and "Send Email - Simple" edits are untouched |
| `src/test/java/com/alramz/repository/OtpRepositoryTest.java` | new | |
| `src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java` | modified | 2 OTP tests |
| `src/test/java/com/alramz/service/OtpServiceTest.java` | new | |
| `src/test/java/com/alramz/controllers/OtpControllerTest.java` | new | |
| `src/test/java/com/alramz/config/ProfileSecurityConfigTest.java` | modified | 4 OTP paths added to `doesNotContain` |

Not touched, per plan or Gate 1:
- `AlramzNotificationServiceApplicationTests` needed no change: the context loads 004 and binds `OtpProperties` with the defaults.
- All Stage D files (`005-*.sql` x5, `OtpCleanupJob`, `OtpCleanupJobTest`) were dropped.

No file outside the plan was touched. Nothing in `infra/`, `.github/`, `k8s/`, other services or `alramz-api-starter` changed, and changesets 001-003 were not edited.

## 3. Test Results

All commands ran from the repo root, in this order:

| # | Command | Exit |
|---|---|---|
| 0 | `mvn -pl services/alramz-notification-service -am clean test` (baseline before any edit) | 0 (212 tests; exit code confirmed by the re-run in #1) |
| 1 | `mvn -pl services/alramz-notification-service -am test` (baseline re-run to capture the exit code) | 0 (212 tests, 0 failures) |
| 2 | `mvn -pl services/alramz-notification-service -am test -Dtest=OtpRepositoryTest,AlramzNotificationServiceApplicationTests` (Stage A) | 0 (12 tests) |
| 3 | `mvn -pl services/alramz-notification-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` (Stage C codegen; `OtpApi` + 7 models generated) | 0 |
| 4 | `mvn -pl services/alramz-notification-service -am test -Dtest=OtpServiceTest,OtpControllerTest,ProfileSecurityConfigTest,GlobalExceptionHandlerTest,OtpRepositoryTest,AlramzNotificationServiceApplicationTests` (Stages B + C) | 0 (70 tests) |
| 5 | **`mvn -pl services/alramz-notification-service -am clean test`** (final gate) | **0 (259 tests, 0 failures, 0 errors, 0 skipped)** |

Red phases: each new test class was written before its production class, so its red phase was the expected compile failure (missing `OtpRepository`, `OtpException`/`handleOtp`, `OtpService`, `OtpController`). The red phases were not run as separate Maven invocations, and the plan's intermediate per-stage full runs were folded into the single final full run (#5).

## 4. Test Coverage

From `services/alramz-notification-service/target/site/jacoco/jacoco.csv` after run #5:

| Class | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| com.alramz.service.OtpService | new | 100% (239/239) | 96.7% (145/150) | OtpServiceTest | PASS |
| com.alramz.service.OtpService.Recipients | new | 100% (2/2) | — | OtpServiceTest | PASS |
| com.alramz.repository.OtpRepository | new | 96.4% (53/55) | 50% (1/2) | OtpRepositoryTest (H2 MODE=PostgreSQL, real 004 + sql-queries.xml) | PASS |
| com.alramz.repository.OtpRepository.OtpConfig / OtpRow / OtpCandidate | new | 100% each | — | OtpRepositoryTest | PASS |
| com.alramz.controllers.OtpController | new | 100% (4/4) | — | OtpControllerTest | PASS |
| com.alramz.config.OtpProperties | new | 100% (1/1) | — | contextLoads, OtpServiceTest | PASS |
| com.alramz.exception.OtpException | new | n/a: excluded by BOM JaCoCo `**/exception/**` | n/a | GlobalExceptionHandlerTest#otpCallerErrorsCarryCodes / #otpBackendErrorsHaveNoCodes, OtpServiceTest | EXCEPTION (no row: the BOM excludes the package; behaviour is asserted by the cited tests) |
| com.alramz.exception.GlobalExceptionHandler (handleOtp) | modified | n/a: excluded by BOM | n/a | GlobalExceptionHandlerTest#otpCallerErrorsCarryCodes, #otpBackendErrorsHaveNoCodes (all 4 switch arms) | PASS |
| com.alramz.service.TemplatedEmailService (EMAIL visibility) | modified | n/a (pre-existing), 98.8% | 98.4% | OtpServiceTest#e4_*, #emailIsSentOncePerAddress* | PASS |
| com.alramz.service.impl.SmsServiceImpl#isValidMobile | modified | n/a (pre-existing), 97.1% | 91.9% | OtpServiceTest#e4_*, #e6_* | PASS |
| com.alramz.AlramzNotificationServiceApplication | modified | n/a (pre-existing), 88.2% | 50% | AlramzNotificationServiceApplicationTests#contextLoads (binds OtpProperties) | PASS |

Every new class with a JaCoCo row is above 80% line coverage. The one EXCEPTION, `OtpException`, has no JaCoCo row because the BOM excludes the `exception` package; it is not a coverage gap.

Coverage notes:
- `OtpRepository`'s missed branch is the `max == null` guard: `COALESCE` never returns null.
- `OtpService`'s 5 missed branches are short-circuit combinations of the `sent_via` booleans and the `fixedTestOtp && prod` guard.

## 5. Acceptance Criteria Verification

**Contract and codegen**
- [x] Three paths with tag `Otp` and the declared statuses with examples (401 is a description only). Proven by the generated `OtpApi`, which has `sendOtp`, `validateOtp` and `getOtpConfigurations` (run #3).
- [x] 7 schemas, and the `key`/`value`/`hashedOTP` aliases bind. Proven by `OtpControllerTest#legacyAliasesBindAndUnknownCorrelationIdIsIgnored`.
- [x] `modelsToGenerate` includes the 7 models, `OtpController implements OtpApi`, and test-compile passes (run #3).

**Persistence**
- [x] `004` is in all 5 folders, byte-identical (md5 x5), with changeset id = stem and `--rollback`. 001-003 were not edited (no tool wrote them; `git diff` was not run because this stage forbids git). **`005` is not created** (Stage D dropped, Gate 1).
- [x] Columns, CHECKs, PK and UNIQUE match spec section 4, plus PENDING in the status CHECK. Proven by `OtpRepositoryTest#schemaRejectsValuesOutsideTheConstraints` and `#insertOtp_returnsDistinctIdsAndStoresActivePendingRows`. One index only (see section 6).
- [x] `contextLoads` applies 004 on H2. The scheduler log criterion for `otpCleanup` is void because Stage D was dropped.

**Send** (`OtpServiceTest`)
- [x] E1, E3-E8: `#e1_*`, `#e3_unknownServiceIsOtp002`, `#e4_*`, `#e5_*`, `#e6_*`, `#e7_*` and `#e8_*` each assert no channel call and no insert or deactivate.
- [x] The OTP length equals `token_length`, and hash = `sha256Hex(otp)` in lower case (`#e10_*`). Fixed-test mode gives 1234 / 123456 with zero channel calls (`#e9_*` x2).
- [x] SMS is sent once with `msgCategory` 4.5 and `expiryInSecs` 300 (`#e10_*`). Email is sent once per address with `from` = email-from, the subject/body split on `^+^`, and every `%OTP%` replaced (`#emailIsSentOncePerAddress*`, `#englishEmailReplacesEveryOtpPlaceholder`).
- [x] E10 partial delivery gives 200 with `[SMS SENT, EMAIL FAILED]`, `markSent`(SMS id), `markFailed`(email id) and an upsert for every recipient (`#e10_*`).
- [x] E11 all-failed: OTP009 after tx2 commits, FAILED rows, no upsert (`#e11_*` x3).
- [x] Deactivate and insert run in the same transaction (tx1), committed before any channel call. Proven by the `InOrder` in `#e10_*`, and `getTransaction` is called exactly twice.
- [x] The daily limit uses the Dubai `dayStart`, with a UTC/Dubai day mismatch in the fixed clock (`#e7_*`). Yesterday's counter does not block and resets to 1 on H2 (`OtpRepositoryTest#maxAttemptsSince_*`, `#upsertAttempt_*`).

**Validate**
- [x] E2 `#e2_*`, E3 `#e3_validateUnknownServiceIsOtp002Not500`, E14 `#e14_*`, E15 `#e15_*`, E16 `#e16_*` (increment, then commit, never rollback, then OTP007), E17/E18 `#e17_e18_*`.
- [x] On H2, only the given ids are VALIDATED and the increment persists (`OtpRepositoryTest#markValidatedTouchesOnlyGivenIdsAndIncrementPersists`). Lock filtering is proven by `#lockActiveCandidates_*`.
- [x] E19 (concurrent validates) is not provable on H2; it stays a manual Postgres check (G9).
- [x] A null `max_validation_attempts` uses 3 (`#e15_*`), and a configured 5 allows a 4th attempt (`#configuredLimitAboveThreeAllowsAFourthAttempt`).

**Lookup**
- [x] Every row is returned in `service_name` order (`OtpRepositoryTest#findAllConfigs_isOrderedByServiceName`), mapped without `maxValidationAttempts`, and an empty table gives `[]` (`OtpServiceTest#e24_*`).

**Handler, security and config**
- [x] `GlobalExceptionHandlerTest#otpCallerErrorsCarryCodes` and `#otpBackendErrorsHaveNoCodes`.
- [x] `@JwtSecured(APP_NOTIFICATION)` is on all 3 methods (`OtpControllerTest#everyOperationRequiresAppNotificationRole`). `ProfileSecurityConfigTest` covers the OTP paths in all 6 yml files, and all 6 pass (there is no baseline failure now).
- [x] `app.otp.*` has 2 keys per profile, as amended by G6. The E23 guards are tested in `#e23_fixedTestOtpInProdFailsStartup` (prod+on throws; prod+off and dev+on start) and `#e23_emailFromOutsideTheAllowListFailsStartup` (not allowed or null throws; case and whitespace are ignored).
- [x] No log line contains the OTP, the hash, an address, the rendered text or a mobile number (`OtpServiceTest#logsNeverCarryTheOtpHashAddressesTextOrNumbers`, ListAppender).

**Cleanup job**
- [-] Dropped at Gate 1 (Stage D).

**Manual / final**
- [x] `apiCollection.http` has a positive and a negative block per endpoint, using `{{authToken}}` and synthetic `example.com` / `971500000010` values.
- [x] The full suite exits 0 (run #5).

## 6. Deviations & Decisions

No new blocking gap was found, so `AskUserQuestion` was not used.

The Gate 1 decisions were applied as given:

| Decision | Applied as |
|---|---|
| G1/G2 | `token_length INTEGER CHECK 4-10`; `sent_via VARCHAR(20) NOT NULL CHECK IN ('SMS','EMAIL','EMAIL|SMS')`. The other planned constraints are in place too (PK, UNIQUE service_name, expiry 1-86400, max_attempts >= 1, max_validation NULL or >= 1). `OtpConfiguration.sentVia` is an enum in the contract. |
| G3 | `OtpService.MAX_RECIPIENTS = 5` (constant). |
| G4 | Invalid tokens are rejected with OTP004 if they contain `@`, otherwise OTP003. |
| G5 | `DEFAULT_MAX_VALIDATION_ATTEMPTS = 3` (constant). |
| Stage D dropped | No 005, no `OtpCleanupJob`, no `deactivateExpired`. **The expiry index `idx_otp_management_expiry` was not added to 004.** Validate's lock query filters on `(service_name, mobile_email, active)`, which is served by `idx_otp_management_lookup`; the expiry predicate is a residual filter on a few rows per recipient. The expiry index only served the job's table-wide scan. |
| Save-then-send | Implemented as planned (tx1 PENDING/active -> send -> tx2). A tx2 failure is logged at ERROR by class name only, and the response still reflects actual delivery. |
| Postgres-safe upsert | `INSERT … ON CONFLICT DO NOTHING` (target-less) followed by the `CASE` `UPDATE`, with no `DuplicateKeyException` retry. |
| No `@Transactional` | None on `OtpService` or `OtpController`; both transactions are `TransactionTemplate`s. |

Implementation judgment calls (not blocking):
- **`OtpRow`** drops the spec's `active` and `status` fields. Inserts are always `'1'`/`'PENDING'` (literals in the SQL), so those fields would be dead.
- **The transaction manager** is injected with `@Qualifier("middlewareTransactionManager")` instead of relying on `@Primary`. It is the same bean, but this makes the explicit qualified injection the stage rules require.
- **An unknown `sent_via`** gives OTP010. This is a defensive branch the DB CHECK makes unreachable.
- **Validate compares** the lower-cased caller hash with the stored hash as-is. The server only ever stores lower case.

Coordinator request: the resume message asked for `git status`. This stage's contract forbids every `git` command, so the working tree was checked by reading each planned file instead. None of them existed, so nothing was redone or duplicated.

## 7. Howtos Consulted

- `.claude/howtos/testing-patterns.md`. Pitfalls applied: no `@WebMvcTest`/MockMvc (`OtpControllerTest` constructs the controller directly); real `OtpProperties`/`EmailProperties`/`MockEnvironment` objects instead of mocks; `assertThatThrownBy(...).hasFieldOrPropertyWithValue("code", …)`.
- The plan's `howtos_consulted` list was relied on for the remaining howtos (openapi-contract-first, api-collection-http, configuration-properties, jwt-security, multi-datasource, exception-handling, liquibase, logging, scheduler). Those were not re-read in full this run because of the earlier interruption. Their key pitfalls are enforced in the code, and each was checked against the actual diff:
  - The generated `OtpApi` is implemented; no endpoint is hand-written.
  - One arm was added to the single `@Order(0)` advice; the `OnboardingExceptionHandler` pattern was not repeated.
  - Changeset 004 is copied into 5 explicit profile folders; no shared root folder is assumed.
  - Datasource and transaction-manager injection is `@Qualifier`-qualified.
  - No `@Loggable` and no starter masker change.
  - No permit-all change.
  - Every `apiCollection.http` value is synthetic.
- Also used: the `spring-boot-testing` skill. It recommended `@WebMvcTest` for controllers; that was overridden by repo convention because the controller only delegates. Service tests are plain Mockito with a real `TransactionTemplate` over a mocked `PlatformTransactionManager`. The repository uses the existing H2 + ScriptUtils precedent rather than `@DataJpaTest`, since this is not JPA.

## 8. Self-Critique

**Confidence: high** for everything unit- and H2-provable; **medium** for Postgres-only behaviour.

Residual risks, non-blocking:
1. **G9:** concurrent validate re-check under Postgres READ COMMITTED `FOR UPDATE` is unproven on H2. Do the manual two-session psql check before go-live.
2. **Shared middleware DB:** if a legacy-shaped `otp_*` table already exists, `CREATE TABLE IF NOT EXISTS` will silently keep it. Check before the first apply.
3. **tx2 failure:** leaves PENDING active rows and an uncounted daily attempt. This is the accepted trade, and the condition is visible in ops logs (`OTP record failed`).
4. **Send-limit race:** concurrent sends can both pass the limit (legacy parity; marked `ponytail:` in the code). G10 deadlock aborts are possible but rare and self-healing.
5. **G11:** the legacy hash encoding is unconfirmed; the hash is unsalted SHA-256.
6. **No retention** for `otp_management` / `otp_attempts` (out of scope).
7. **Process deviation:** besides Maven, read-only shell commands (`grep`/`awk`/`md5`/`ls`) were used to read the Maven log, `jacoco.csv`, the generated sources and the md5 of the 004 copies. Nothing was written through the shell; every file edit used Edit/Write.

**Flag for 04_security:**
- Three new authenticated endpoints that take caller input. Recipient parsing is regex-validated, the IP address is pattern-checked, and the hash is a 64-hex pattern. All SQL uses named binds.
- `SecureRandom` OTP generation; `MessageDigest.isEqual` constant-time compare.
- The fixed test OTP is enabled by default in dev/docker/test. It is hard-guarded only in prod; preprod uses a literal `false` but has no startup guard.
- `SensitiveDataMasker` still lacks `otp`/`hashedotp` keys. Payload logging is off and there is no `@Loggable`, but enabling payload logging later would expose hashes.
- Per-OTP brute-force limit (3 by default) and per-recipient daily send cap; no per-IP or per-caller throttle.
- Emails are sent upper-cased (legacy normalisation).
- The `apiCollection.http` validate block includes the SHA-256 of `1234`, a public test value.
