# SonarQube remediation — final report (alramzit_alramz-mw-backend)

Generated 2026-11-02 18:03. Every number below is read from files under `.claude/modules/sonar-scans/`; nothing is typed in by hand.

## A. SonarQube comparison

| Metric | Before | After | Status |
|---|---|---|---|
| Reliability | C | A | A ✅ |
| Security | D | A | A ✅ |
| Maintainability | A | A | A ✅ |
| Hotspots Review | A | A | A ✅ |
| Bugs | 11 | 0 | ▼ better |
| Vulnerabilities | 12 | 0 | ▼ better |
| Code Smells | 481 | 28 | ▼ better |
| Security Hotspots | 0 | 0 | = |
| Blocker issues | 5 | 0 | ▼ better |
| Critical issues | 56 | 0 | ▼ better |
| Open issues (total) | 504 | 28 | ▼ better |
| Technical debt (min) | 3049 | 345 | ▼ better |
| Coverage % | 77.2 | 77.3 | ▲ better |
| Duplications % | 4.0 | 4.1 | ▲ worse |
| Quality Gate | NONE | OK | ✅ |

## B. Remediation summary

- Issues identified (baseline): **504**
- Fixed (closed by SonarQube on re-analysis): **476**
- Pending (still open): **28**
- Changed (open, but moved or re-rated): **5**
- New issues introduced: **0**
- Hotspots: 0 → 0 (0 still TO_REVIEW)

### Pending issues by rule

| Rule | Count | Files |
|---|---|---|
| java:S5738 | 10 | ETradeTokenProviderTest.java, ExternalETradeServiceConfig.java, ExternalIbanServiceConfig.java, ExternalVeriPhoneServiceConfig.java, WebClientConfig.java |
| java:S1172 | 9 | ApiAuditAspectTest.java, AuditProbeClient.java, AuditProbeController.java, DuplicateCheckServiceImpl.java, IBANValidationServiceImpl.java, MethodExecutionLoggingAspectTest.java, PhoneValidationServiceImpl.java |
| java:S2139 | 4 | JdbcGlobalConfigurationSettingsRepository.java, RedisCacheService.java |
| java:S2143 | 2 | DataValidationUtil.java, TokenProvider.java |
| java:S1075 | 2 | GlobalExceptionHandler.java |
| java:S107 | 1 | ScheduleInfoBean.java |

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

Full lists: `.claude/modules/sonar-scans/comparison/comparison.md` (fixed, pending, new, changed) and `.claude/modules/sonar-scans/remediation/`.

## C. Change log

See `CHANGELOG-SONAR-REMEDIATION.md` (one row per modified file: problem, fix, rule, tests).

## D. Build report

- Overall: **SUCCESS**
- Reactor modules: 9; succeeded: 9; failed: 0; skipped: 0
- Tests: 1090 run, 0 failures, 0 errors, 1 skipped
- Spring Boot executable jars: 6

Per module: `.claude/modules/sonar-scans/build/build-summary.md`.

## E. Startup report

| Service | Port | Result | Health | Duration (s) | Detail |
|---|---|---|---|---|---|
| alramz-notification-service | 8082 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 13 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| onboarding-service | 8080 | BLOCKED_BY_DEPENDENCY | n/a | 13 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=nNl9cp2WRC2/652qxhy5Vw==) |
| reference-data-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 19 | configured port 8081 in use by another process; ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=dTI0p8mESUSsU+M1qPuDyA==) |
| reporting-management-service | 8084 | BLOCKED_BY_DEPENDENCY | n/a | 12 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=IL0S4IfVRKCQQCYHRHEoog==) |
| utility-service | 8083 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| wealth-management-service | 8085 | BLOCKED_BY_DEPENDENCY | n/a | 13 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=ajVW4pObTA2pXOLE679SkA==) |
