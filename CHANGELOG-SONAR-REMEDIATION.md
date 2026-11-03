# SonarQube remediation changelog: 2026-11-02

Project: SonarCloud `alramzit_alramz-mw-backend` (private). Branch: `feature/project-setup`, uncommitted.
Numbers come from `.claude/modules/sonar-scans/final/FINAL-REPORT.md`:
- Ratings went from Reliability C / Security D / Maintainability A / Hotspots A to **A / A / A / A**.
- Quality Gate: **OK**.
- Open issues: 504 → 28, with 0 new.
- Final `clean verify`: 9 of 9 modules SUCCESS, 1,090 tests, 0 failures.

Tests per row: every module's full suite ran after its changes (`mvn -pl services/<module> test`). Everything
was then re-verified by a full `clean verify` of an isolated snapshot (`.claude/modules/sonar-scans/build/build.log`).

How to reproduce: `.claude/skills/alramz-sonar-remediation/scripts/` (see `.claude/skills/alramz-sonar-remediation/SKILL.md`).

---

## Lead: vulnerabilities, bugs, blockers, cross-cutting fixes

Verified with: `mvn -pl services/alramz-common-bom,services/alramz-api-starter install` (246 tests, 0 failures;
log `cycle1-starter-2.log`), `mvn -pl services/onboarding-service,services/alramz-notification-service test`
(257 + 260 tests, 0 failures; `cycle1-services.log`), and `RedisCacheServiceTest` (24 tests, 0 failures; `cycle1-refdata.log`).

| File | Rule(s) | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `scheduler/controller/SchedulerManagerController.java` | javasecurity:S5145 ×3 (+ downstream ×7 in JobScheduleManager), S1192, S6813, S3457 | `jobGroupName`/`jobIds` from the request body were logged raw, so CR/LF could forge log lines. Literal duplicated 3×, field injection, string-concatenated logs, stack traces glued into the message | Sanitize at the trust boundary with `LoggingHelper.stripCrlf` before any use or logging. Use a constant, constructor injection, `{}` placeholders, and `log.error(msg, e)` | New `restartStripsCrlfFromJobGroupBeforeUseOrLogging`; existing controller tests |
| api-starter `logging/util/LoggingHelper.java` | javabugs:S2259 | `populateMdc` dereferenced `request` that the class elsewhere treats as nullable | Early return on null, after the service name goes into the MDC. Added `stripCrlf` (CWE-117) | Starter suite |
| api-starter `scheduler/service/impl/JobScheduleManager.java` | S3599, S3457; javasecurity:S5145 sinks | Double-brace `HashMap` subclass. The unregister log printed `[Ljava.lang.String;@…` instead of the IDs | Plain `HashMap` + `put`; `{}` placeholders with `Arrays.toString` | Starter suite |
| api-starter `logging/logback/SeqAppender.java` | S3078, S2142 | `failureCount++` on a volatile field loses increments under concurrent appends. `catch (Exception)` swallowed `InterruptedException` from `httpClient.send` | `AtomicInteger.incrementAndGet()`; a dedicated `InterruptedException` catch that restores the interrupt flag and stops retrying | Starter suite |
| api-starter `logging/util/LogMaskingUtil.java` | S5998 | Backtracking alternation in `JSON_PAIR` can overflow the stack on large JSON strings | Possessive quantifiers. The alternatives are disjoint, so matches are unchanged | Starter masking tests |
| api-starter `jwt/config/JwtProperties.java`, `jwt/service/AuthService.java` | S2184 ×4 | `int` multiplication for durations (no overflow at current values, but fragile) | `L` literal so the arithmetic is `long` | Starter JWT tests |
| api-starter `jwt/filter/JwtAuthFilter.java`, `logging/filter/RequestLoggingFilter.java`, `logging/filter/ResponseLoggingFilter.java` | S2387 (BLOCKER) | Own `logger` field shadowed `GenericFilterBean.logger` | Renamed to `log` | Starter filter tests |
| api-starter `audit/ApiAuditLogFilter.java`, `logging/filter/CorrelationIdFilter.java` | S2387 (BLOCKER) | Shadowing `logger` field, never used | Removed the field and its now-unused imports | Starter suite |
| notification `service/impl/EtisalatSmsClient.java` | javabugs:S2259 | `response.get("jobId")` was only safe through an indirect `"201".equals("null")` path | Explicit `response == null` in the rejection guard; same exception and code | Notification suite |
| onboarding `client/ETradeClient.java` | S3599 | Double-brace anonymous `HttpHeaders` subclass | Plain `HttpHeaders` + `add` | Onboarding suite |
| reference-data `service/RedisCacheService.java` | S2077 | `SELECT * FROM <table>` concatenates a configured table name (identifiers can't be bound) | Validate against a `[schema.]table` identifier pattern before building SQL. A bad value throws `IllegalStateException` | New `loadAllFromDatabase_shouldRejectTableNameThatIsNotAPlainIdentifier` |

## Reviewed, no code change

| Rule | Location | Evidence | Decision |
|---|---|---|---|
| java:S4502 (CSRF disabled) | api-starter `jwt/config/JwtAutoConfiguration.java:83` | Auth reads only the `Authorization: Bearer` header (`JwtAuthFilter:45`, `JwtContext:43`). Session policy is `STATELESS`. No service uses cookies or `HttpSession` (grep over `services/*/src/main`). Browsers never attach the token automatically, so cross-site requests can't be authenticated. Enabling CSRF would reject every non-GET API call | Keep it disabled. The user approved it on 2026-11-02. Issue `AaGbpyWsoF4k1D0H0hrO` was marked **ACCEPTED** in SonarCloud with this evidence as the comment |

## Cycle 1 follow-up (after the module agents)

| File | Rule / source | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `scheduler/service/impl/JobScheduleManager.java` | Reliability bug found while refactoring S3776 | `future` was never reset between jobs, so an invalid or rejected job that followed a successful one was also added to SUCCEED and stored in `scheduledTasks` with the previous job's future. Cancelling it would cancel the wrong task | Each job uses its own future | New `invalidJobAfterSuccessfulOneIsNotReportedAsSucceeded` (valid job first). Starter: 251 tests, 0 failures (`cycle1-starter-3.log`) |

## Cycle 2 (from the cycle 2 rescan: 46 open, 7 new)

| File | Rule(s) | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `logging/util/LogMaskingUtil.java` | S5843 (new, complexity 25 > 20) plus a **masking leak** found during review | My possessive rewrite raised the regex complexity. Worse, the value alternatives (`\d+(?:\.\d+)?\|true\|false\|null`) never matched negative or exponent numbers, so `"pin": -1234` was logged **unmasked** | Number and literal alternatives replaced by one possessive token class `[-+.\w]++`. Lower complexity, and every unquoted JSON value is now masked | New `masksSensitiveUnquotedValuesIncludingNegativeAndExponentNumbers` |
| api-starter `scheduler/service/ISchedulerService.java`, `impl/JobScheduleManager.java`, `controller/SchedulerManagerController.java` | S112 ×5, S1130 | `throws Exception` on methods that throw no checked exception. The only implementer and caller are inside the starter (grep across `services/`) | Removed the clauses. Tests now declare only what they actually throw (`InterruptedException`, `SQLException`) | Starter 252 tests, 0 failures (`cycle2-starter.log`) |
| api-starter `logging/util/LoggingHelper.java` | S1066 | Nested `if` | Merged the condition | Starter suite |
| api-starter tests `WebClientAuditFilterTest`, `SeqAppenderTest`; reference-data `ApplicationControllerTest` | S5778, S1130 ×5 (new) | Two calls inside the `assertThatThrownBy` lambda; `throws Exception` that is never thrown | Call built outside the lambda; clauses removed | Starter and reference-data suites |
| reference-data `service/RedisCacheService.java` | S1172 | Public `getEntry(mappingName, key)` ignored `key` and returned every entry. It had no callers in main code, only a test asserting it "delegates", so the name promised a lookup it didn't do | Deleted the dead, misleading method and its test | Reference-data 59 tests, 0 failures (`cycle2-refdata.log`) |
| reference-data `service/RedisCacheService.java:224` | java:S2077 | Still flagged after validation (Sonar flags any non-constant SQL) | **ACCEPTED** in SonarCloud (`AaGbpyXaoF4k1D0H0hsL`) with the user's approval and evidence: config-sourced name, identifier-pattern validation, regression test | n/a |

## Cycle 3 (from the cycle 3 rescan: 29 open, 0 new; all ratings A)

| File | Rule | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `exceptions/ApiCallFailedException.java` | S1172 | The constructor received the failed call's `uri` and `method` (passed by `AbstractRestClient`) and discarded them | Stored as fields, with Lombok getters. Additive only; the second constructor sets them to `null` | `AbstractRestClientTest` now asserts the method and URI. Starter 252 tests, 0 failures (`cycle3-starter.log`) |

### Files missing from the module logs (found by cross-checking `git diff` against this changelog)

| File | Rule | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter test `scheduler/controller/SchedulerManagerControllerTest.java` | (follows S6813, S5145) | Set the controller's field-injected `scheduler` by hand | Constructor injection; new CRLF-stripping regression test | Starter suite |
| api-starter tests `datasource/IntegrationDataSourceAutoConfigurationTest.java`, `MiddlewareDataSourceAutoConfigurationTest.java` | S1128 | Unused imports | Removed | Starter suite |
| reference-data test `scheduler/job/CacheReloadScheduledJobTest.java` | (follows S1068) | Passed the `CompanyRedisProperties` argument that was removed from the job's constructor | Updated the constructor call; dropped the unused import | Reference-data suite |
| reference-data `config/JpaConfig.java` (**deleted**) | S1220 | Empty 0-byte file in the default package | Deleted (unstaged deletion) | Reference-data suite |

---

## alramz-api-starter (code smells, module agent)


Baseline: `mvn -B -pl services/alramz-api-starter test` → Tests run: 246, Failures: 0, Errors: 0.
After: Tests run: 250, Failures: 0, Errors: 0 (+4 new tests in `AbstractDataSourceAutoConfigurationTest`).

Paths are relative to `services/alramz-api-starter/src/`. "tests run" = full module test suite (`mvn -B -pl services/alramz-api-starter test`, green).

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| main/.../scheduler/service/impl/JobScheduleManager.java | S3776 (1), S3457 (13), S5145 (5), S1128 (1), S9438 (1), S112 (1), S135 (1), S1141 (1), S1155 (2), S1135 (1), S125 (1) | `schedule()` cognitive complexity 113; string-concatenated log lines; `jobGroupName` logged raw; unused import; non-final map; `throw new RuntimeException`; break/continue loops; nested try; `size() > 0`; commented-out TODO | `schedule()` split into `loadJobs`/`loadDbJobs`/`loadYamlJobs`/`filterDbJobs`/`matchesJobIds`/`scheduleJob`/`scheduleByMode`/`registerScheduledTask`/`logFailedJobs`; `{}` placeholders (stack traces passed as throwable arg); `LoggingHelper.stripCrlf` on logged group name/job ids; `IllegalStateException` (subclass of RuntimeException, controller/tests unaffected); `isEmpty()`; constants `CRON_EXP`, `CANCELLING_TASK`. The original's carry-over of the previous job's `future` across loop iterations is preserved exactly (see notes) | JobScheduleManagerTest (11) + full suite |
| main/.../scheduler/constants/SchedulerConstants.java | S1214 (1) | constants in an interface | `final class` with private ctor; `SchedulerConstants.X` usage unchanged (only users are in the starter) | full suite |
| main/.../scheduler/config/SchedulerAutoConfiguration.java | S1128 (2) | same-package imports | removed | full suite |
| main/.../scheduler/config/SchedulerConfigCondition.java | S1128 (1) | unused import | removed | full suite |
| main/.../scheduler/config/SchedulerConfiguration.java | S1128 (1) | unused import | removed | full suite |
| test/.../scheduler/service/impl/JobScheduleManagerTest.java | S8924 (1) | qualified `Mockito.verify` | static import | full suite |
| main/.../logging/aspect/MethodExecutionLoggingAspect.java | S3776 (1) | complexity 38 | private `LogOptions`/`Invocation` records + `logEntry`/`onException`/`endSpan`/`isExitLogged`; same helper calls, same log output | MethodExecutionLoggingAspectTest + full suite |
| main/.../logging/util/MethodExecutionLoggingHelper.java | S3776 (1), S1126 (1) | `isExcluded` complexity 17; if/return true/false | `anyMatch(list, predicate)` helper, single boolean return | full suite |
| main/.../logging/config/LoggingAutoConfiguration.java | S106 (4), S1128 (1) | `System.out.println` diagnostics; unused import | slf4j `log.info`; import removed | full suite |
| main/.../logging/config/LoggingProperties.java | S1192 (3) | default excluded paths literal repeated 3x | `DEFAULT_EXCLUDED_PATHS` constant (audit list = `/api/v1/info` + defaults, same order) | full suite |
| main/.../logging/config/SeqLoggingConfig.java | S1192 (1) | "Information" duplicated | uses `DEFAULT_LEVEL` | full suite |
| main/.../logging/constants/LoggingConstants.java | S1124 (1) | `public final static` | `public static final` | full suite |
| main/.../logging/filter/CorrelationIdFilter.java | S1128 (1) | unused `@Slf4j` import (+ slf4j imports left unused by lead's logger removal) | removed | full suite |
| main/.../logging/filter/RequestLoggingFilter.java | S6880 (1) | instanceof if/else rethrow chain | pattern `switch` statement with `throw` per case | full suite |
| main/.../logging/filter/ResponseLoggingFilter.java | S6880 (1), S1128 (1), S6201 (1) | same chain; unused import; instanceof + cast | pattern switch; import removed; `instanceof Long nanos` | full suite |
| main/.../logging/interceptor/RestTemplateLoggingInterceptor.java | S1066 (1) | nested if | merged | full suite |
| main/.../logging/interceptor/WebClientLoggingFilter.java | S3776 (1), S1066 (3), S1128 (1) | complexity 20; duplicated nested log guards; unused import | `withCorrelationId`/`logResponse`/`logError` extracted; redundant inner guards removed/merged | WebClientLoggingFilterTest + full suite |
| main/.../logging/logback/AlramzJsonEncoder.java | S1117 (1), S1172 (1) | local `json` hides field; unused `cause` param (private method) | renamed `rendered`; param removed | full suite |
| main/.../logging/logback/SeqAppender.java | S2065 (9), S7158 (2), S135 (1), S1128 (1) | `transient` on non-Serializable appender; `length() > 0`; 2 breaks in retry loop; unused import | `transient` removed; `isEmpty()`; loop driven by `keepTrying` + `sleepBackoff()` (returns false on interrupt, flag restored) | SeqAppenderTest + full suite |
| main/.../logging/otel/OpenTelemetryTraceContextExtractor.java | S1181 (2) | `catch (Throwable)` | `catch (RuntimeException \| LinkageError)` (keeps the "OTel classes missing" guard, stops swallowing VM errors) | full suite |
| main/.../logging/util/LoggingHelper.java | S1066 (1) | nested if in else | `else if` | full suite |
| main/.../logging/util/LogMaskingUtil.java | S5869 (4), S1128 (1) | `A-Za-z` under `(?i)`; redundant `\\.` in class; unused import | `A-Z` only (case-insensitive flag already matches lowercase); import removed | LogMaskingUtilTest + full suite |
| main/.../audit/ApiAuditAspect.java | S1128 (3), S3776 (1) | unused imports; complexity 20 | removed; `appendFrames` extracted | ApiAuditAspectTest + full suite |
| main/.../audit/ApiAuditLogFilter.java | S1128 (1), S1192 (1), S6880 (1) | unused imports; "FAILURE" x3; instanceof chain | removed; constant; pattern switch with `case null, default` | full suite |
| main/.../audit/ApiAuditLogService.java | S6485 (1) | `new HashMap<>(16)` | `HashMap.newHashMap(16)` | full suite |
| main/.../audit/SensitiveDataMasker.java | S1068 (1), S6485 (1), S6880 (1) | unused constant; ctor sizing; instanceof chain | removed; `LinkedHashMap.newLinkedHashMap`; pattern switch | SensitiveDataMaskerTest + full suite |
| main/.../audit/WebClientAuditFilter.java | S6213 (1) | private method named `record` | `recordOutcome` | full suite |
| main/.../client/AbstractRestClient.java | S119 (10), S107 (1) | type vars `RQ`/`RS`; private `execute` with 8 params | `T`/`R` (source-compatible); private `CallOptions` record | AbstractRestClientTest + full suite |
| main/.../datasource/config/AbstractDataSourceAutoConfiguration.java | S3776 (1), S1066 (1) | `applyDatabaseSpecificProperties` complexity 28; nested if | private `dataSourceProperties`/`applyOracleProperties`/`applyPostgresProperties`; protected signature unchanged | new AbstractDataSourceAutoConfigurationTest (4) + full suite |
| main/.../datasource/config/IntegrationDataSourceAutoConfiguration.java | S1192 (1) | "integration" x5 | `DB_NAME` constant (bean names unchanged) | full suite |
| main/.../datasource/config/MiddlewareDataSourceAutoConfiguration.java | S1192 (1) | "middleware" x5 | `DB_NAME` constant (bean names unchanged) | full suite |
| main/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java | S6204 (1) | `collect(toUnmodifiableList())` | `.toList()` | full suite |
| main/.../jwt/annotation/JwtSecured.java | S1128 (1) | unused import | removed | full suite |
| main/.../jwt/config/JwtAutoConfiguration.java | S112 (1), S1130 (1) | `throws Exception` never thrown | removed (`HttpSecurity.build()` throws no checked exception in Security 7.1) | full suite |
| main/.../jwt/filter/JwtAuthFilter.java | S1068 (1) | unused `properties` field | removed; constructor signature unchanged | full suite |
| main/.../jwt/model/TokenPair.java, User.java | S1128 (2) | unused imports | removed | full suite |
| main/.../jwt/repository/RefreshTokenRepositoryOps.java | S1128 (1) | unused import | removed | full suite |
| main/.../jwt/repository/UserRepository.java | S8688 (1), S1128 (1) | `LocalDateTime.now()` | `now(ZoneId.systemDefault())` | full suite |
| main/.../jwt/service/TokenProvider.java | S2143 (1) | `java.util.Date` arithmetic | `Instant` arithmetic, `Date.from` only at the jjwt boundary | full suite |
| main/.../tracing/config/TracingAutoConfiguration.java | S3358 (1) | nested ternary | private `boundRequest()` | full suite |
| main/.../utils/DataValidationUtil.java | S1118 (1) | implicit public ctor | private ctor (no instantiations found under services/) | full suite |
| main/.../utils/PWProtector.java | S1068 (1) | unused logger | removed | full suite |
| main/.../utils/SqlQueriesManager.java | S1192 (1), S108 (1) | "sql.file" x3; empty finally | constant; empty try/finally removed | full suite |
| test/.../audit/ApiAuditAspectTest.java, ApiAuditLogFilterTest.java | S5838 (2) | `toString()).isEqualTo` | `hasToString` | full suite |
| test/.../audit/SensitiveDataMaskerTest.java | S5838 (4) | `get(k)).isEqualTo(v)` | `containsEntry` | full suite |
| test/.../audit/WebClientAuditFilterTest.java | S1128 (1), S5838 (1), S5958 (3) | unused import; toString compare; bare assertThatThrownBy | removed; `hasToString`; exception type / root cause asserted | full suite |
| test/.../client/AbstractRestClientTest.java | S5778 (1) | setup in throwing lambda | setup hoisted | full suite |
| test/.../datasource/*AutoConfigurationTest.java | S1128 (3) | unused imports | removed | full suite |
| test/.../datasource/config/DatasourcePropertiesTest.java | S5838 (3) | `isEqualTo(0L)` | `isZero()` | full suite |
| test/.../globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java | S6213 (2), S5838 (1), S5853 (1) | var named `record`; hashCode compare; split asserts | `settings`; `hasSameHashCodeAs`; chained | full suite |
| test/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryIT.java | S1128 (1), S5778 (1) | unused import; setup in lambda | removed; hoisted | full suite |
| test/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryTest.java | S5778 (2), S5838 (1) | setup in lambdas; `isEqualTo(0)` | hoisted; `isZero()` | full suite |
| test/.../jwt/aspect/JwtSecuredAspectTest.java | S1186 (2), S9016 (1), S1130 (2) | empty methods; inline mock; needless throws | comments; local mock; throws removed | full suite |
| test/.../jwt/controller/AuthControllerTest.java | S5778 (1), S1186 (2) | setup in lambda; empty stubs | hoisted; comments | full suite |
| test/.../jwt/service/AuthServiceTest.java | S1186 (6) | empty stubs | comments | full suite |
| test/.../logging/AlramzJsonEncoderTest.java, LogMaskingUtilTest.java | S5853 (3) | split asserts | chained | full suite |
| test/.../logging/LoggingHelperTest.java | S6068 (3) | all-`eq()` matchers | raw values | full suite |
| test/.../logging/MethodExecutionLoggingAspectTest.java | S8924 (11), S5853 (2), S108 (2), S9016 (1) | qualified Mockito calls; split asserts; empty catches; inline mock | static imports; chained; comments; local | full suite |
| test/.../logging/filter/RequestLoggingFilterTest.java, ResponseLoggingFilterTest.java | S8924 (2) | qualified `times`/`never` | static imports | full suite |
| test/.../logging/interceptor/WebClientLoggingFilterTest.java | S5838 (2) | `get(k)).isEqualTo(v)` | `containsEntry` | full suite |
| test/.../logging/logback/SeqAppenderTest.java | S2925 (5) | `Thread.sleep` | Awaitility (already on classpath via spring-boot-starter-test) | full suite |
| test/.../datasource/config/AbstractDataSourceAutoConfigurationTest.java (new) | — | no coverage for refactored S3776 path | 4 tests (Oracle w/ and w/o config, non-positive skip, Postgres SSL local/remote, missing config) | full suite |

## Skipped

| rule | path:line | reason |
|---|---|---|
| S112 (x5) | main/.../scheduler/service/ISchedulerService.java:11,13,15,17,21 | `throws Exception` on a public interface; removing it is a public API change for implementors in other services |
| S107 | main/.../scheduler/model/ScheduleInfoBean.java:24 | public 9-arg constructor; changing it breaks callers |
| S1172 | main/.../exceptions/ApiCallFailedException.java:12 | public constructor used by onboarding-service and AbstractRestClient |
| S2139 (x3) | main/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:50,72,122 | log-and-rethrow is the only error trace; wrapping changes the exception type callers/tests depend on — needs a decision |
| S2143 | main/.../utils/DataValidationUtil.java:0 | public methods take `java.util.Date`; switching SimpleDateFormat→DateTimeFormatter changes parsing leniency |
| S5843 / S5998 | main/.../logging/util/LogMaskingUtil.java:30 | `JSON_PAIR` already rewritten with possessive quantifiers by the lead (removes the stack-overflow risk); further simplifying risks changing masking behaviour — re-check on next scan |
| S1172 (x9) | test/.../ApiAuditAspectTest.java:32,36,40; AuditProbeClient.java:6; AuditProbeController.java:9; MethodExecutionLoggingAspectTest.java:220; DuplicateCheckServiceImpl/IBANValidationServiceImpl/PhoneValidationServiceImpl.java:10 | test fixtures whose parameters are looked up by reflection / captured and asserted by the audit & logging aspects |
| S4502 | main/.../jwt/config/JwtAutoConfiguration.java:83 | lead owns the CSRF line |
| (lead-fixed) | SchedulerManagerController (all 16), JobScheduleManager:144/175 (S3457, S5145) and :401 (S3599), SeqAppender S3078/S2142, LoggingHelper S2259, JwtProperties/AuthService S2184, S2387/S1068 `logger` fields in ApiAuditLogFilter/CorrelationIdFilter/RequestLoggingFilter/ResponseLoggingFilter/JwtAuthFilter | already fixed by the lead per brief |

## Notes for review

- `JobScheduleManager.schedule()`: the original declared `future` outside the job loop and never reset it, so a job that is invalid/rejected *after* a successful one is also counted in `SUCCEED` and registered in `scheduledTasks` with the previous job's future. The refactor keeps this exactly (commented in code). It looks like a latent bug; the existing test orders invalid jobs first to avoid it. Fixing it is a behaviour change for the lead to decide.
- `SchedulerConstants` changed from interface to final class: source-compatible for constant access; only users are inside the starter.
- Several error logs now pass the exception as the last slf4j argument instead of `ExceptionUtils.getStackTrace(e)` in the message (same convention the lead used in SchedulerManagerController) — log text format differs slightly.

---

## onboarding-service (code smells, module agent)


Verification: `mvn -B -pl services/onboarding-service clean test` gives **Tests run: 259, Failures: 0, Errors: 0, Skipped: 1** and BUILD SUCCESS. The baseline had 257 tests. Two tests were added to ValidationServiceImplTest.

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| main/.../client/ETradeClient.java | S1068 (1), S2147 (1) | unused `etradeProperties` field; the `WebClientResponseException` catch had the same body as the `Exception` catch | removed the field and its constructor parameter and import; dropped the redundant catch (the generic catch already handles it the same way) | ETradeClientTest |
| main/.../config/ETradeTokenProvider.java | S1068 (1), S3776 (1) | unused `objectMapper`; `fetchAndCacheToken` complexity 16 | removed the field and its constructor parameter; extracted `cacheToken(JsonNode resData)` | ETradeTokenProviderTest |
| main/.../controllers/ApplicationController.java | S112 (1), S1130 (1) | `info()` declared `throws Exception` but never throws | removed the `throws` clause | ApplicationControllerTest |
| main/.../controllers/DataValidationController.java | S1128 (5) | unused imports | removed | DataValidationControllerTest |
| main/.../dfm/DfmMemberApiClient.java | S3776 (1) | `classify` complexity 16 | extracted the 2xx branch into `classifySuccessStatus(...)` | DfmMemberApiClientTest |
| main/.../dfm/DfmOnboardingPayload.java | S3776 (1) | `from` complexity 18 | extracted `paymentMethod`, `csr`, `passportImageType` | DfmMemberApiClientTest (payload asserts) |
| main/.../exception/GlobalExceptionHandler.java | S1192 (6) | repeated "ONB011", "errors", "message", "Invalid request", "Malformed JSON request", "Internal server error" | added `private static final` constants with the same values; no response body or status changed | GlobalExceptionHandlerTest |
| main/.../exception/OnboardingExceptionHandler.java | S1192 (1) | repeated "ONB011" | `DEFAULT_INTERNAL_ERROR_CODE` constant | OnboardingExceptionHandler tests |
| main/.../service/impl/DfmInvestorServiceImpl.java | S6878 (2) | type patterns over records | record patterns `Success(var body)` and `Unavailable(var reason, var cause)` | DfmInvestorServiceImplTest |
| main/.../service/impl/DuplicateCheckHelper.java | S1192 (1) | repeated "Reference_No" | `REFERENCE_NO_KEY` constant | DuplicateCheckHelperTest |
| main/.../service/impl/IBANValidationServiceImpl.java | S1128 (2), S1068 (1), S2147 (1), S1854 (6), S1481 (6) | unused imports and a shadowing `objectMapper` field; duplicate catch bodies; six dead `*Code` locals in `applyValidationRules` | removed the dead code (the method now returns the same object it built before); multi-catch `ApiCallFailedException \| WebClientResponseException` | IBANValidationServiceImplTest |
| main/.../service/impl/OnboardingServiceImpl.java | S1157 (2) | `"Y".equals(v.trim().toUpperCase())` | `equalsIgnoreCase` | OnboardingServiceImplTest |
| main/.../service/impl/PhoneValidationServiceImpl.java | S1068 (1), S2147 (1) | shadowing `objectMapper` field; duplicate catch bodies | removed; multi-catch | PhoneValidationServiceImplTest |
| main/.../service/impl/ValidationServiceImpl.java | S1068 (1), S3776 (1), S6880 (1) | unused `etradeProperties`; `validateRequest` complexity 32, if/else chain | removed the field and its constructor parameter; replaced the chain with an enum `switch` (`case null, default` keeps the null/unsupported behaviour) and a `requireField` helper with identical messages | ValidationServiceImplTest (+2 tests: whitespace-only field message, null type message) |
| main/.../service/ValidationRequestMapper.java | S1128 (1) | unused import | removed | ValidationRequestMapperTest |
| test/.../AopLoggingIntegrationTest.java | S1128 (1) | unused import | removed | itself |
| test/.../client/ETradeClientTest.java | (follow-on) | constructor lost the `ETradeProperties` argument | removed the now-unused PROPS and its import | itself |
| test/.../config/DfmMaskingConfigTest.java | S5853 (1) | two assertions on the same subject | chained | itself |
| test/.../config/ETradeTokenProviderTest.java | (follow-on) | constructor lost the `ObjectMapper` argument | updated the call | itself |
| test/.../controllers/ApplicationControllerTest.java | S1128 (1), S5838 (2) | unused import; `get(k)).isEqualTo(v)` | removed; `containsEntry` chain | itself |
| test/.../dfm/DfmMemberApiClientTest.java | S1128 (1), S5961 (1), S5778 (2) | unused import; 34 assertions in one test; client construction and request building inside `assertThatThrownBy` | 23 field asserts collapsed into one expected-vs-actual map comparison (same JSON pointers and values, and a mismatch shows the diff) plus one `noneMatch(body::has)` for the four omitted fields; setup moved out of the lambdas | itself |
| test/.../exception/GlobalExceptionHandlerTest.java | S1128 (1), S8924 (2) | unused import; FQN `Mockito.mock` | removed; static `mock` | itself |
| test/.../repository/DfmOnboardingRepositoryImplTest.java | S5778 (1) | `new OnboardingRequest()` inside the lambda | moved out | itself |
| test/.../scheduler/job/DataValidationScheduledJobTest.java | S1128 (3) | unused imports | removed | itself |
| test/.../service/impl/DfmInvestorServiceImplTest.java | S5778 (4) | `validRequest()` inside the lambda | moved to a local (and the identical unflagged line 301) | itself |
| test/.../service/impl/DuplicateCheckHelperTest.java | S1128 (1), S8924 (4), S4144 (1) | same-package import; FQN mock; `helperForETrade` identical to `helperWithoutNinService` | removed; static `mock`; callers now use `helperWithoutNinService` and the duplicate helper is deleted (no test removed) | itself |
| test/.../service/impl/DuplicateCheckServiceImplTest.java | S1128 (10), S1144 (1), S5853 (1) | unused imports; unused `mockResData`; split assertions | removed; chained `hasSize(4).containsExactly(...)` | itself |
| test/.../service/impl/IBANValidationServiceImplTest.java | S5778 (6) | `request(...)` inside the lambda | moved to a local | itself |
| test/.../service/impl/NinTradingNumberValidationServiceTest.java | S1128 (1) | unused import | removed | itself |
| test/.../service/impl/OnboardingServiceImplTest.java | S1128 (4) | unused imports | removed | itself |
| test/.../service/impl/PhoneValidationServiceImplTest.java | S5778 (6) | `request(...)` inside the lambda | moved to a local | itself |
| test/.../service/impl/ResponseMappersTest.java | S1128 (2) | unused imports | removed | itself |
| test/.../service/impl/ValidationServiceImplTest.java | S1128 (4) | unused imports | removed (plus the constructor call update and the 2 new tests) | itself |
| test/.../service/ValidationRequestMapperTest.java | S1128 (4), S5838 (2) | unused imports; `get(k)).isEqualTo(v)` | removed; `containsEntry` chain | itself |
| test/.../service/ValidationResponseMapperRegistryTest.java | S1128 (3) | unused imports | removed | itself |

## Skipped

| rule | path:line | reason |
|---|---|---|
| S3599 | main/.../client/ETradeClient.java:37 | already fixed by the lead (per the brief) |
| S5738 (x2 each) | main/.../config/ExternalETradeServiceConfig.java:43, ExternalIbanServiceConfig.java:43, ExternalVeriPhoneServiceConfig.java:43, WebClientConfig.java:18/21 | the replacement for `Jackson2JsonDecoder` / `jackson2JsonDecoder(...)` is the Jackson 3 `JacksonJsonDecoder` (tools.jackson). This module decodes into Jackson 2 types: `ETradeTokenProvider` reads `com.fasterxml...JsonNode`, `ETradeResponse` carries a Jackson 2 `JsonNode`, and the configs build Jackson 2 `ObjectMapper`s. Switching the decoder is a Jackson 2 to 3 migration of the WebClient stack, which is a behaviour and design decision, not a smell fix |
| S5738 (x2) | test/.../config/ETradeTokenProviderTest.java:45 | mirrors the production codec setup above, so it should move only together with that migration |
| S1075 (x2) | main/.../exception/GlobalExceptionHandler.java:27,28 | `VALIDATION_PATH`/`ONBOARDING_PATH` are route prefixes that must match the OpenAPI-generated controller mappings. Making them configurable would add a property that can drift from the real routes, which is a design change |

---

## alramz-notification-service (code smells, module agent)


Verification: `mvn -B -pl services/alramz-notification-service test` gives Tests run: 260, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS. No tests were added or removed.

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| main/config/GraphConfig.java | S1452 (1) | `GraphServiceClient<?>` bean return type | Typed as `GraphServiceClient<okhttp3.Request>`, which is the builder's native request type. Bean name unchanged; `ObjectProvider<GraphServiceClient<?>>` injection still matches. | RequestSizeLimitConfigTest, EmailServiceImplTest, AppTests |
| main/config/SmsProperties.java | S3776 (1), S3398 (1) | Compact constructor complexity 20; `clean` used only by Etisalat | Extracted `validateLiveSend` / `validateDispatch` (same checks, same order, same messages). Moved `clean` into `Etisalat`. | SmsPropertiesTest |
| main/repository/OtpRepository.java | S1192 (1) | "serviceName" x4 | `SERVICE_NAME` constant | OtpServiceTest, AppTests |
| main/scheduler/job/SmsDispatchJob.java | S6880 (1), S1141 (1) | if/else chain on the enum; nested try | Arrow `switch` on RowOutcome. Inner send/try extracted into `send(...)`. The outer try/finally (MDC clear, markResult failure) is unchanged. | SmsDispatchJobTest |
| main/service/impl/EmailServiceImpl.java | S3776 (1) | `validate` complexity 18 | Extracted `validateAttachments` (count, per-item, total). Validation order unchanged. | EmailServiceImplTest |
| main/service/impl/SmsServiceImpl.java | S6353 (3), S3776 (1), S1192 (1) | `[0-9]` in regex; `sendWithOutcome` complexity 19; "SMS009" x3 | `\d` (Java's default `\d` is ASCII 0-9, so same matches). Extracted `validateBodyAndNumbers`, `msgCategory`, `expiry`. Added `PROVIDER_REJECTED` constant. | SmsServiceImplTest |
| main/service/OtpService.java | S3776 (1) | `parse` complexity 21 | Extracted `splitRecipients` and `requireChannelRecipients` | OtpServiceTest |
| main/service/TemplatedEmailService.java | S1192 (2), S3776 (1) | "MSWORD"/"MSEXCEL" x3; `checkAttachments` complexity 18 | `MSWORD`/`MSEXCEL` constants (still compile-time constants for the `case` labels). Extracted `checkAttachment` and `attachmentsMaxSize`. | TemplatedEmailServiceTest |
| test/config/ProfileSecurityConfigTest.java | S5838 (1) | `get(key)).isEqualTo` | `containsEntry` | itself |
| test/config/RequestSizeLimitConfigTest.java | S5778 (3), S8924 (1) | Multiple calls in assertThrows lambdas; FQN `Mockito.when` | Hoisted args to locals; static import `when` | itself |
| test/config/SmsPropertiesTest.java | S1068 (1), S5778 (8) | Unused ETISALAT field; record construction inside lambdas | Deleted the field; hoisted args to locals | itself |
| test/controllers/SmsControllerTest.java | S5778 (4) | `controller(...)` built inside lambdas | Hoisted controller to a local | itself |
| test/scheduler/job/SmsDispatchJobTest.java | S5853 (1) | Separate assertions on `logs.list` | Single chain | itself |
| test/service/impl/EmailServiceImplTest.java | S5778 (6), S5853 (1) | `request()` built inside lambdas; split assertions | Hoisted requests; chained `isNotEmpty().allSatisfy` | itself |
| test/service/impl/EtisalatSmsClientTest.java | S5853 (1) | Split assertions | Chained | itself |
| test/service/impl/SmsServiceImplTest.java | S5853 (1) | Split assertions | Chained | itself |
| test/service/impl/VeriPhoneClientTest.java | S5853 (1) | Split assertions | Chained | itself |
| test/service/OtpServiceTest.java | S5778 (6) | service/request/properties built inside lambdas | Hoisted to locals | itself |
| test/service/TemplatedEmailServiceTest.java | S5778 (4), S5853 (1) | `request()` inside lambdas; split assertions | Hoisted; chained | itself |

## Skipped
| rule | path:line | reason |
|---|---|---|
| javabugs:S2259 | main/service/impl/EtisalatSmsClient.java:96 | Already fixed by the lead (per brief) |

---

## reference-data-service (code smells, module agent)


| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| config/CacheWarmupRunner.java | S1128 (1) | same-package import | removed import | `mvn -B -pl services/reference-data-service test` |
| config/CompanyRedisProperties.java | S1128 (1) | unused `DefaultValue` import | removed import | same |
| config/JpaConfig.java | S1220 (1) | 0-byte file, no package | deleted the empty file (`git rm`) | same |
| controllers/ApplicationController.java | S112, S1130 (2) | `info()` declared `throws Exception` that is never thrown | removed `throws` clause | same |
| scheduler/job/CacheReloadScheduledJob.java (+ test) | S1068 (1) | unused `companyRedisProperties` field | removed field and its constructor parameter (Spring bean; only caller is the test, updated) | same |
| service/CacheAuditService.java | S1128 (1) | unused `LocalDateTime` import | removed import | same |
| service/RedisCacheService.java | S1854, S1481 x2, S1141 x2, S8688 x2, S1192 (8) | unused `deleted`/`lastReloaded` locals; nested try blocks in reload and repopulate; zone-less `now()`; `"mappingName"` x3 | dropped unused locals; extracted `writeViaStagingKey`, `repopulateCache`, shared `writeWithTtl`; `now(ZoneId.systemDefault())`; `MAPPING_NAME` constant | same |
| service/SchedulerLockService.java | S1192, S8688 x2 (3) | `"jobId"` x3; zone-less `now()` | `JOB_ID` constant; `now(ZoneId.systemDefault())` | same |
| test config/CacheWarmupRunnerTest.java | S1130 (1) | `throws Exception` never thrown | removed | same |

Result: Tests run: 60, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
| rule | path:line | reason |
|---|---|---|
| S2077 | service/RedisCacheService.java:208 | already fixed by the lead (TABLE_NAME pattern) |
| S1172 | service/RedisCacheService.java:62 | `key` is a parameter of public method `getEntry(String, String)`; removing it changes a public signature (brief: private members only) |
| S2139 | service/RedisCacheService.java:198 | log-and-rethrow: a test asserts the original `DataAccessException` type propagates, and the GlobalExceptionHandler does not log, so dropping the log loses the only error trace and wrapping changes the exception type. Needs a design decision |

---

## reporting-management-service (code smells, module agent)


| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| controllers/ApplicationController.java | S112, S1130 (2) | `info()` declared `throws Exception` never thrown | removed `throws` clause | `mvn -B -pl services/reporting-management-service test` |
| repository/RelationshipManagerRepositoryImpl.java | S2143 (1) | `java.sql.Date` used for bind values | bind `LocalDate` directly (JDBC 4.2 / ojdbc11); log-literal renderer now matches `LocalDate` | same |
| test repository/RelationshipManagerRepositoryImplTest.java | S5778 (2) | `LocalDate.now()` calls inside `assertThatThrownBy` lambda | hoisted to a local `today` | same |
| test service/impl/RelationshipManagerServiceImplTest.java | S6068 (2) | all-`eq(...)` verify args | passed values directly | same |

Result: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
None.

---

## utility-service (code smells, module agent)


| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| finoux/FinouxClient.java | S1168 (1) | `firstRow` returned `null` map | returns `Map.of()`; caller's `row.get("post_ID") == null` check already covers it | `mvn -B -pl services/utility-service test` |
| service/impl/CommunityPostServiceImpl.java | S6878 (3) | type patterns on records `Posted`, `Rejected`, `Unavailable` | record deconstruction patterns | same |
| test controllers/CommunityPostControllerTest.java | S5838 (2) | `x.toString()).isEqualTo(..)` | `hasToString(..)` | same |
| test exception/GlobalExceptionHandlerTest.java | S5838 (1) | same | `hasToString(..)` | same |
| test service/impl/CommunityPostServiceImplTest.java | S5778 (5) | request built inside `assertThatThrownBy` lambda | hoisted to local `postRequest` | same |

Result: Tests run: 36, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
None.

---

## wealth-management-service (code smells, module agent)


| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| controllers/ApplicationController.java | S112, S1130 (2) | `info()` declared `throws Exception` never thrown | removed `throws` clause | `mvn -B -pl services/wealth-management-service test` |
| exception/ErrorCode.java | S1192 (2) | literal duplicating `DATE_INVALID_MSG`; `"clientId"` x3 | reuse `DATE_INVALID_MSG`; `CLIENT_ID` constant | same |
| repository/MmfRepositoryImpl.java | S2143, S1192 (2) | `java.sql.Date` binds/reads; `"CLIENT_ID"` x3 | bind `LocalDate`, read with `rs.getObject(col, LocalDate.class)`; `CLIENT_ID` constant | same |
| service/impl/MmfServiceImpl.java | S3776 (2) | `validateAndConfirm` (16) and `createProfitDistributions` (27) too complex | extracted `rejectConflicts`, `confirmBook`, `validateProfitItems`, `distribute`; logic unchanged, covered by the 7 `confirm_*` and 12 `profit_*` tests | same |
| test controllers/MmfApiDispatchTest.java | S6068 (1) | lone `eq(...)` | value passed directly; unused `eq` import removed | same |
| test exception/ErrorCodeTest.java | S5853 (1) | two assertions on `codes` | chained | same |
| test repository/MmfRepositoryImplTest.java | S1128 (1) | unused `verify` import | removed; date mocks/expectations moved to `LocalDate` to follow the S2143 change | same |
| test service/impl/MmfServiceImplTest.java | S1128, S5778 x9 (10) | unused `Level` import; arguments built inside `assertThatThrownBy` lambdas | removed import; hoisted arguments to locals | same |

Result: Tests run: 191, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
None. (identified-gaps.md and apiCollection.http untouched.)

---

### Why each pending issue is still open (all MAJOR or lower; none affects a rating)

| Rule | Count | Where | Blocker / reason | What it would take |
|---|---|---|---|---|
| java:S5738 | 10 | onboarding `External*ServiceConfig`, `WebClientConfig`, `ETradeTokenProviderTest` | The deprecated-for-removal Jackson 2 WebClient codecs. The replacement is Jackson 3, but this module decodes into Jackson 2 types (`JsonNode` in `ETradeTokenProvider`, `ETradeResponse`) | A separate Jackson 2 → 3 migration of the onboarding WebClient stack, with its own regression testing |
| java:S1172 | 9 | api-starter **test fixtures** (`ApiAuditAspectTest`, `AuditProbeClient`, `AuditProbeController`, `MethodExecutionLoggingAspectTest`, stub `*ServiceImpl`) | The parameters exist so the audit and method-logging aspects can capture and mask them by reflection. Removing them removes what the tests assert | None. They're needed as written; a reviewer could mark them accepted |
| java:S2139 | 4 | api-starter `JdbcGlobalConfigurationSettingsRepository` (3), reference-data `RedisCacheService` (1) | Log-and-rethrow. Dropping the log loses the only error trace (the reference-data handler doesn't log). Wrapping changes the exception type that callers and tests catch | A decision on the error-handling contract: wrap in a domain exception and let the handler log |
| java:S2143 | 2 | api-starter `TokenProvider`, `DataValidationUtil` | `TokenProvider`: the jjwt API takes `java.util.Date`. `DataValidationUtil`: public methods take `Date`, and switching parsers changes how lenient parsing is | `TokenProvider`: none (library API). `DataValidationUtil`: an API change for its callers |
| java:S1075 | 2 | onboarding `GlobalExceptionHandler:27,28` | Path constants that must match the generated controller routes. A property could drift from the real routes | None recommended |
| java:S107 | 1 | api-starter `ScheduleInfoBean` constructor (9 params) | Public constructor of a shared-library model | A builder, which is an API change for starter users |

### Accepted in SonarCloud (not open; approved by the user on 2026-11-02, with evidence attached as a comment)

| Rule | Location | Evidence |
|---|---|---|
| java:S4502 | api-starter `JwtAutoConfiguration` (CSRF disabled) | Stateless Bearer-header auth; no cookies or `HttpSession` in any service; enabling CSRF would reject every non-GET call |
| java:S2077 | reference-data `RedisCacheService` (`SELECT * FROM <table>`) | Table name comes from config, never the request; validated against a `[schema.]table` identifier pattern; regression test |

### Startup: BLOCKED_BY_DEPENDENCY is by design

The dev profile's Oracle BROK datasource points at shared DEV/QA databases (wealth-management defaults to
`BROKQA`). `startup-check.sh` deliberately redirects it to `localhost:1`, so verification can never touch a
shared environment. All four Oracle-backed services got through bean creation and stopped only when
opening that datasource (`ORA-12541 … localhost port 1`). To get them to STARTED_AND_HEALTHY, run a local
Oracle (or an XE container) and export `COMPANY_DATASOURCE_BROK_URL`, `COMPANY_DATASOURCE_BROK_USERNAME`
and `COMPANY_DATASOURCE_BROK_PLAINPASSWORD` before running the script. The two non-Oracle services started in
about 12 seconds; their `/actuator/health` answers 403 because it isn't in the JWT `permit-all-urls` (only
`/actuator/prometheus` is).
