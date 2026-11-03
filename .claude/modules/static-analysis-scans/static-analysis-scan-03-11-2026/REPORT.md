# REPORT — Checkstyle / PMD / SpotBugs scan & fix (03-11-2026)

Skill: `.claude/skills/alramz-static-analysis-fixer`. Scope: all 8 reactor modules (PMD/SpotBugs skip `alramz-api-starter` by its own pom — Checkstyle only there).

## Headline

| | Findings |
|---|---|
| Before | **2,431** (Checkstyle 272, PMD 1,883, SpotBugs 276) |
| Accepted via ruleset/filter change (user-approved) | 1,966 — PMD opinion rules 1,678, OpenAPI-generated code 190, logger naming 49, Spring-bean/@ConfigurationProperties EI 49 |
| Actionable after triage | 465 |
| Cleared by code changes | 366 (incl. 17 method-scoped, justified suppressions) |
| After | **99** (Checkstyle 10, PMD 78, SpotBugs 11) — every one listed as deferred below |
| HIGH severity | PMD 68 -> 0, SpotBugs 1 -> 0 |
| New findings introduced | 0 |
| Build / tests | green before and after; 1,090 tests, identical counts per module ([BUILD-STATUS.md](BUILD-STATUS.md)) |
| Startup | identical before and after (2 started — health 403 behind JWT; 4 blocked by no local Oracle) |

## Before / after
## Totals

| Tool | Before | After | Delta |
|---|---|---|---|
| checkstyle | 272 | 10 | -262 |
| pmd | 1883 | 78 | -1805 |
| spotbugs | 276 | 11 | -265 |

## By tool and severity

| Tool | Severity | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | MEDIUM | 272 | 10 | -262 |
| pmd | HIGH | 68 | 0 | -68 |
| pmd | LOW | 6 | 3 | -3 |
| pmd | MEDIUM | 1809 | 75 | -1734 |
| spotbugs | HIGH | 1 | 0 | -1 |
| spotbugs | LOW | 41 | 1 | -40 |
| spotbugs | MEDIUM | 234 | 10 | -224 |

## By tool and module

| Tool | Module | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | alramz-api-starter | 77 | 5 | -72 |
| checkstyle | alramz-notification-service | 6 | 4 | -2 |
| checkstyle | onboarding-service | 95 | 0 | -95 |
| checkstyle | reference-data-service | 30 | 0 | -30 |
| checkstyle | reporting-management-service | 21 | 0 | -21 |
| checkstyle | utility-service | 21 | 0 | -21 |
| checkstyle | wealth-management-service | 22 | 1 | -21 |
| pmd | alramz-notification-service | 252 | 33 | -219 |
| pmd | onboarding-service | 770 | 20 | -750 |
| pmd | reference-data-service | 288 | 10 | -278 |
| pmd | reporting-management-service | 109 | 0 | -109 |
| pmd | utility-service | 115 | 2 | -113 |
| pmd | wealth-management-service | 349 | 13 | -336 |
| spotbugs | alramz-notification-service | 53 | 3 | -50 |
| spotbugs | onboarding-service | 140 | 2 | -138 |
| spotbugs | reference-data-service | 30 | 0 | -30 |
| spotbugs | reporting-management-service | 14 | 0 | -14 |
| spotbugs | utility-service | 14 | 6 | -8 |
| spotbugs | wealth-management-service | 25 | 0 | -25 |

Full per-rule deltas: [comparison/comparison.md](comparison/comparison.md). Every change: [CHANGELOG.md](CHANGELOG.md).

## Notable real fixes

- **Null dereferences (SpotBugs NP/RCN, 1 HIGH):** ETradeTokenProvider, LiquibaseConfiguration, OnboardingServiceImpl, DfmInvestorServiceImpl, RedisConfig (Azure token `block()`), OtpRepository generated key, SmsServiceImpl expiry, FinouxClient, RelationshipManagerRepositoryImpl.
- **Lost stack traces (PreserveStackTrace):** 16 sites now chain the cause; cause constructors added to `ExternalSystemException`, `TechnicalException`, `RelationshipManagerLookupException`, `InvalidDateRangeException`. Messages/error codes unchanged.
- **Catch(Exception) narrowed:** 20 sites in reference-data-service (DataAccessException / IllegalStateException / DateTimeParseException); cache still falls back to DB.
- **Defensive copies:** ValidationDefinition, DfmOnboardingPayload.Csr, FinouxData, FinouxInsertPostDataRequest.
- **Wiring bug:** `config/spotbugs-exclude.xml` existed but was never passed to the plugin.
- Mechanical: tabs, CRLF->LF/EOF newline (31 files, whole-file diffs), star/unused imports, long lines, serialVersionUID.

## Behavior changes to review

1. utility-service: `FinouxData.table` / `FinouxInsertPostDataRequest.param` null -> empty list (sole reader treats both the same; JSON unchanged).
2. reference-data-service: narrowed catches now propagate unexpected RuntimeExceptions that were swallowed. If one hits `reloadGlobalConfig`, the reload lock stays IN_PROGRESS until its 1-minute lease expires.
3. notification-service: `OtpService`, `SmsDispatchJob` made `final` (no proxies/mocks today).
4. Exceptions now carry causes (onboarding, reporting) — logs will show deeper stack traces.

## Found, not fixed (needs a decision)

- **onboarding 401-retry likely dead:** `ETradeClient.callETrade` catches every Exception and wraps it in `ExternalSystemException`, so the "retry once after 401" in `ValidationServiceImpl`/`DuplicateCheckHelper` probably never triggers.
- **notification PreserveStackTrace ×6 kept on purpose:** `SmsException`/`TemplatedEmailException` have no cause constructor so provider exceptions carrying credential URLs never reach logs. Security call — leave as is unless you decide otherwise.
- PMD reports a `LoosePackageCoupling` config error ("no packages specified") from the design category — harmless; exclude or configure it.

## Suppressed with justification (17)

| Module | Where | Rule | Reason |
|---|---|---|---|
| alramz-api-starter | — | — | none |
| onboarding-service | OnboardingServiceApplication.java:globalConfigurationSettingsDemoRunner | PMD.AvoidCatchingGenericException | Startup ApplicationRunner (demo): any failure must be logged, never abort application startup (queue item) |
| onboarding-service | config/ValidationDefinition.java:compact ctor | PMD.UnusedAssignment, PMD.NullAssignment | Introduced by the EI fix: compact-ctor parameter reassignment is how a record stores a defensive copy (PMD false positive); null kept intentionally. An `Optional.orElse(null)` variant avoids PMD but SpotBugs then re-flags EI_EXPOSE_REP |
| onboarding-service | dfm/DfmOnboardingPayload.java:Csr compact ctor | PMD.UnusedAssignment, PMD.NullAssignment | Same as above |
| alramz-notification-service | config/SmsProperties.java: `SmsProperties` compact ctor | PMD.UnusedAssignment (x3) | False positive: Java assigns compact-constructor parameter values to the record fields implicitly, and PMD does not see that. |
| alramz-notification-service | config/SmsProperties.java: `Defaults` compact ctor | PMD.UnusedAssignment (x2) | Same false positive. |
| alramz-notification-service | config/SmsProperties.java: `Dispatch` compact ctor | PMD.UnusedAssignment (x1) | Same false positive. |
| alramz-notification-service | config/EmailProperties.java: compact ctor | PMD.UnusedAssignment (x1) | Same false positive. |
| reference-data-service | scheduler/job/CacheReloadScheduledJob.java:run | PMD.AvoidCatchingGenericException | Scheduler job boundary. It logs every failure so the scheduler thread never dies, and `finally` releases the lock. |
| reference-data-service | config/CacheWarmupRunner.java:warmupRunner | PMD.AvoidCatchingGenericException | Startup warmup boundary. One failing mapping must not abort startup or stop the other mappings from warming. |
| reference-data-service | config/RedisConfig.java:resolveAzureAccessToken | PMD.AvoidCatchingGenericException | Boundary over Azure identity and Reactor errors (several unrelated RuntimeException types). It rethrows them as one IllegalStateException with context. |
| reference-data-service | config/RedisConfig.java:createLocalRedisConnectionFactory | PMD.CloseResource | False positive. `Connection` is the `CompanyRedisProperties.Connection` config POJO, not a closeable resource. No Redis connection is opened here. |
| reference-data-service | config/RedisConfig.java:createAzureRedisConnectionFactory | PMD.CloseResource | Same false positive as above |
| reference-data-service | service/RedisCacheService.java:loadFromDatabaseAndRepopulate | PMD.IdenticalCatchBranches | Introduced by the fix. Merging the two catches into a multi-catch makes the rethrow a RuntimeException, which re-triggers SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION. The two rules conflict here. |
| utility-service | — | — | none (no SpotBugs annotation dependency on the classpath, and config/pom are out of scope) |
| reporting-management-service | — | — | none |
| wealth-management-service | — | — | none |

## Deferred (45 rows = the 99 remaining findings)

Mostly design-size/complexity rules (GodClass, TooManyMethods, Coupling, Cyclomatic/NPath) that need structural refactors, plus renames of public/JSON/SQL-bound members.

| Module | File | Rule | Count | Reason / what it would take |
|---|---|---|---|---|
| alramz-api-starter | audit/ApiAuditLogFilter.java | NPathComplexity (321) | 1 | split doFilterInternal into request/response/audit helpers; filter is on every request path, not trivial |
| alramz-api-starter | logging/aspect/MethodExecutionLoggingAspect.java | NPathComplexity (1,150) | 1 | needs a refactor of the around-advice branching into helpers; behaviour-sensitive |
| alramz-api-starter | logging/util/LoggingHelper.java:255 | NPathComplexity (225) | 1 | method-split refactor |
| alramz-api-starter | scheduler/service/impl/JobScheduleManager.java:400 | NPathComplexity (225) | 1 | method-split refactor |
| alramz-api-starter | logging/config/LoggingAutoConfiguration.java | ClassFanOutComplexity (35) | 1 | split auto-config into separate @AutoConfiguration classes — changes registered auto-config class names (public API for consumers) |
| onboarding-service | config/ETradeTokenProvider.java (invalidate) | NullAssignment | 2 | null is the "no cached token" sentinel read by `getToken()`; removing needs a holder/Optional refactor of the cache state |
| onboarding-service | dfm/DfmOnboardingOutcome.java (Unavailable) | EI_EXPOSE_REP, EI_EXPOSE_REP2 | 2 | Throwable field cannot be copied — accepted |
| onboarding-service | service/ValidationRequestMapper.java | EI_EXPOSE_REP2 | 1 | Constructor-injected Spring `ObjectMapper` singleton (false positive). The config/spotbugs-exclude.xml bean regex doesn't include `Mapper`; lead could add `\|Mapper` to that regex. No SpotBugs annotations on classpath |
| onboarding-service | dfm/DfmOnboardingPayload.java (from) | CyclomaticComplexity, NPathComplexity | 2 | Flat field mapping with null-ternaries; splitting is a refactor |
| onboarding-service | exception/GlobalExceptionHandler.java, OnboardingExceptionHandler.java | CyclomaticComplexity | 4 | Switch-mapping methods (`friendlyMessage`, `mapFieldToInternalErrorCode`); would need a lookup-map refactor |
| onboarding-service | exception/DfmInvestorExceptionHandler.java, GlobalExceptionHandler.java, service/impl/DfmInvestorServiceImpl.java | TooManyMethods | 3 | Design-level; out of scope |
| onboarding-service | service/impl/DfmInvestorServiceImpl.java | CouplingBetweenObjects | 1 | Design-level |
| onboarding-service | repository/DfmOnboardingRepositoryImpl.java | NcssCount | 1 | Long SQL parameter binding method; would need splitting |
| onboarding-service | repository/DfmOnboardingRequest.java | TooManyFields | 1 | DTO mirrors DB/JSON shape |
| onboarding-service | service/DuplicateCheckService.java, service/impl/DuplicateCheckServiceImpl.java | UseObjectForClearerAPI | 2 | Public interface signature |
| onboarding-service | service/impl/IBANValidationServiceImpl.java, PhoneValidationServiceImpl.java, NinTradingNumberValidationService.java, OnboardingServiceImpl.java | CyclomaticComplexity | 4 | Validation branching; would need extraction refactors |
| alramz-notification-service | service/TemplatedEmailService.java:207,245,248,249,251 | PMD PreserveStackTrace | 5 | Deliberate design: TemplatedEmailException has no cause constructor ("gateway exceptions are mapped by code, never chained") so gateway messages never reach logs. Fixing it needs a security decision. |
| alramz-notification-service | service/impl/WaselneySmsClient.java:132 | PMD PreserveStackTrace | 1 | Deliberate design: SmsException has no cause constructor ("provider exceptions embed credential-bearing URLs and are never chained"). |
| alramz-notification-service | service/OtpService.java:180,216; service/TemplatedEmailService.java:129 | PMD ExceptionAsFlowControl | 5 | Catch-record-rethrow pattern: the catch records `e.getCode()` for the `finally` audit log and rethrows the same exception. Restructuring it would mean an outcome holder or duplicated logging. Not simple. |
| alramz-notification-service | config/RequestSizeLimitConfig.java:52 | SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION (Low) | 1 | Intentional: the interceptor throws the endpoint's own exception (EmailValidationException or TemplatedEmailException, which share no base type) for GlobalExceptionHandler to map. SuppressFBWarnings is not on the classpath. |
| alramz-notification-service | service/SmsService.java:24 `Outcome` | SpotBugs EI_EXPOSE_REP / EI_EXPOSE_REP2 | 2 | `SmsSendResult` is a generated, mutable OpenAPI model with no copy constructor. A deep copy would add code for an internal value carrier. Accepted. |
| alramz-notification-service | controllers/OtpController.java:39, SmsController.java:62 | PMD ShortMethodName | 2 | Method names come from the generated OpenAPI interface (public API). |
| alramz-notification-service | service/impl/EtisalatSmsClient.java:57 | PMD UseObjectForClearerAPI | 1 | Public signature change. |
| alramz-notification-service | GlobalExceptionHandler, OtpService, TemplatedEmailService, EmailServiceImpl, SmsServiceImpl | PMD GodClass | 5 | Structural refactor. |
| alramz-notification-service | GlobalExceptionHandler, OtpRepository | PMD TooManyMethods | 2 | Structural refactor. |
| alramz-notification-service | OtpService, EmailServiceImpl | PMD CouplingBetweenObjects | 2 | Structural refactor. |
| alramz-notification-service | TemplatedEmailService, EmailServiceImpl | PMD ExcessiveImports | 2 | Structural refactor. |
| alramz-notification-service | OtpService | Checkstyle ClassFanOutComplexity | 1 | Structural refactor. |
| alramz-notification-service | GlobalExceptionHandler (x3), EmailServiceImpl (x2), EtisalatSmsClient (x1) | PMD CyclomaticComplexity | 6 | Code-to-status switch tables and validation chains. A split would be a refactor beyond one small extraction. |
| alramz-notification-service | TemplatedEmailService:140, EmailServiceImpl:205 | PMD NPathComplexity | 2 | Validation chains. Same reason. |
| alramz-notification-service | TemplatedEmailService:83,140, EmailServiceImpl:205 | Checkstyle NPathComplexity | 3 | Same. |
| reference-data-service | config/CompanyRedisProperties.java, config/CacheMappingProperties.java, config/MiddlewareDataSourceProperties.java | DataClass | 6 | `@ConfigurationProperties` binding beans are meant to be data classes. Fixing it would mean converting them to records or immutable binding, which changes the binding API. |
| reference-data-service | config/CompanyRedisProperties.java (`Ssl`), config/MiddlewareDataSourceProperties.java (`Pool`) | ShortClassName | 2 | Public nested binding types used across config and tests. Renaming them changes the public API without any benefit. |
| reference-data-service | service/RedisCacheService.java | TooManyMethods | 1 | Fixing it means splitting the lock, health and serialization helpers into separate beans. That is a structural refactor and out of scope. |
| reference-data-service | service/CacheAuditService.java | UseObjectForClearerAPI | 1 | Fixing it changes the public `logAuditAsync` signature used by RedisCacheService and the tests. It would need a parameter object. |
| utility-service | finoux/FinouxResult.java (Posted, Unavailable) | EI_EXPOSE_REP, EI_EXPOSE_REP2 | 4 | False positive by design: `Posted` hands the generated, mutable `CommunityPost` DTO to the caller, and `Unavailable` carries the exception cause. Neither can or should be copied. Fix belongs in `config/spotbugs-exclude.xml` (e.g. match `com.alramz.finoux.FinouxResult$*`), or in `spotbugs-annotations` + `@SuppressFBWarnings`. Both are outside this agent's scope (no config/pom edits) |
| utility-service | config/FinouxProperties.java:30 | ShortClassName (`Post`) | 1 | nested @ConfigurationProperties type, public, used as `FinouxProperties.Post`; renaming is a public-member rename |
| utility-service | exception/ErrorCode.java:19 | AvoidFieldNameMatchingMethodName (`code`/`code()`) | 1 | record-style public accessor; renaming changes the public API used by handlers/tests |
| reporting-management-service | — | — | 0 | — |
| wealth-management-service | exception/ErrorCode.java | ExcessivePublicCount | 1 | enum of ~50 public error-code constants by design (API contract) |
| wealth-management-service | exception/ErrorCode.java | AvoidFieldNameMatchingMethodName (`code`, `defaultMessage`) | 2 | record-style public accessors; renaming is a public API change |
| wealth-management-service | exception/GlobalExceptionHandler.java | CouplingBetweenObjects, ExcessiveImports, ClassFanOutComplexity (CS), GodClass, TooManyMethods | 5 | central @RestControllerAdvice mapping every exception type; splitting needs multiple advices with ordering, a design change |
| wealth-management-service | exception/GlobalExceptionHandler.java:166 | CognitiveComplexity (15) | 1 | handleNotReadable branch refactor; deferred per policy |
| wealth-management-service | service/impl/MmfServiceImpl.java | CouplingBetweenObjects, ExcessiveImports, GodClass, TooManyMethods | 4 | split per sub-domain (account / sweeps / profit / custodian) services: a structural refactor |
| wealth-management-service | service/impl/MmfServiceImpl.java:99 | CyclomaticComplexity (10) | 1 | listSweeps date-defaulting/validation extraction; deferred per policy |

## Artifacts

`before/` `after-config/` `after/` (findings.json, summary.md, raw XML, build logs, build/, startup/), `queue/`, `remediation/<module>.md`, `comparison/`, `CHANGELOG.md`, `BUILD-STATUS.md`.