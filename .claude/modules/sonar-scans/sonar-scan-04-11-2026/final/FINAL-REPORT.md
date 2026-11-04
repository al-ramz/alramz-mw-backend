# SonarQube remediation — final report (alramzit_alramz-mw-backend)

Generated 2026-11-04 17:34. Every number below is read from files under `.claude/modules/sonar-scans/`; nothing is typed in by hand.

## A. SonarQube comparison

| Metric | Before | After | Status |
|---|---|---|---|
| Reliability | A | A | A ✅ |
| Security | A | A | A ✅ |
| Maintainability | A | A | A ✅ |
| Hotspots Review | A | A | A ✅ |
| Bugs | 0 | 0 | = |
| Vulnerabilities | 0 | 0 | = |
| Code Smells | 38 | 33 | ▼ better |
| Security Hotspots | 0 | 0 | = |
| Blocker issues | 0 | 0 | = |
| Critical issues | 0 | 0 | = |
| Open issues (total) | 38 | 33 | ▼ better |
| Technical debt (min) | 475 | 420 | ▼ better |
| Coverage % | 77.6 | 77.6 | = |
| Duplications % | 4.1 | 4.1 | = |
| Quality Gate | OK | OK | ✅ |

## B. Remediation summary

- Issues identified (baseline): **38**
- Fixed (closed by SonarQube on re-analysis): **5**
- Pending (still open): **33**
- Changed (open, but moved or re-rated): **0**
- New issues introduced: **0**
- Hotspots: 0 → 0 (0 still TO_REVIEW)

### Pending issues by rule

| Rule | Count | Files |
|---|---|---|
| java:S5738 | 11 | ETradeTokenProviderTest.java, ExternalETradeServiceConfig.java, ExternalIbanServiceConfig.java, ExternalVeriPhoneServiceConfig.java, WebClientConfig.java |
| java:S1172 | 9 | ApiAuditAspectTest.java, AuditProbeClient.java, AuditProbeController.java, DuplicateCheckServiceImpl.java, IBANValidationServiceImpl.java, MethodExecutionLoggingAspectTest.java, PhoneValidationServiceImpl.java |
| java:S2139 | 5 | JdbcGlobalConfigurationSettingsRepository.java, RedisCacheService.java |
| java:S1075 | 4 | GlobalExceptionHandler.java, KnowYourCountryClient.java |
| java:S2143 | 2 | DataValidationUtil.java, TokenProvider.java |
| java:S107 | 1 | ScheduleInfoBean.java |
| java:S2147 | 1 | RedisCacheService.java |

### Why each pending issue is still open (33; all MAJOR or lower; no rating affected)

Fix scope for this run: risk-management-service. Out-of-scope rows reuse the 02-11 reasons (`.claude/modules/sonar-scans/remediation/pending-reasons.md`).

| Rule | Count | Where | Blocker / reason | What it would take |
|---|---|---|---|---|
| java:S1075 | 2 | **risk-management** `KnowYourCountryClient:35,36` (`/api/v4/countries_list`, `/api/v4/country_ratings`) | Vendor contract paths. The host is already configurable (`knowyourcountry.base-url`); moving to another vendor API version also means changing `KnowYourCountryRecord`'s mapping, so a path property would be a knob that can't be turned safely on its own | Two `knowyourcountry.*-path` properties with these defaults, if the user wants them |
| java:S1075 | 2 | onboarding `GlobalExceptionHandler:27,28` | Out of scope. Route constants that must match the generated mappings (02-11) | Reuse the generated `PATH_*` constants, as done here for the risk-management legacy controller |
| java:S5738 | 11 | onboarding `External*ServiceConfig`, `WebClientConfig`, `ETradeTokenProviderTest` | Out of scope. Jackson 2 WebClient codecs; need a Jackson 3 migration (02-11) | Jackson 3 WebClient migration |
| java:S1172 | 9 | api-starter test fixtures | Out of scope. Parameters exist to be captured by the audit/logging aspects (02-11) | None recommended |
| java:S2139 | 5 | api-starter `JdbcGlobalConfigurationSettingsRepository` (3), reference-data (2) | Out of scope. Log-and-rethrow sites whose exception types tests rely on (02-11) | Per-site decision on wrap vs log |
| java:S2143 | 2 | api-starter `TokenProvider`, `DataValidationUtil` | Out of scope. jjwt takes `Date`; public `Date` API (02-11) | API change |
| java:S107 | 1 | api-starter `ScheduleInfoBean` | Out of scope. Public starter constructor (02-11) | Builder (API change) |
| java:S2147 | 1 | reference-data-service | Out of scope; not examined in this run | Merge the catch blocks |

### Accepted in SonarCloud

Unchanged from 02-11 (S4502, S2077). No new accept transitions in this run.

### Startup

risk-management-service, notification and utility start; `/actuator/health` returns 403 because it isn't in the JWT permit-all list (pre-existing).
The four Oracle-backed services are BLOCKED_BY_DEPENDENCY by design (`startup-check.sh` points BROK at `localhost:1`).
risk-management ran with a placeholder `KNOWYOURCOUNTRY_API_KEY` and `KNOWYOURCOUNTRY_BASE_URL=http://localhost:1` for the check process only.

Full lists: `.claude/modules/sonar-scans/comparison/comparison.md` (fixed, pending, new, changed) and `.claude/modules/sonar-scans/remediation/`.

## C. Change log

See `CHANGELOG-SONAR-REMEDIATION.md` (one row per modified file: problem, fix, rule, tests).

## D. Build report

- Overall: **SUCCESS**
- Reactor modules: 10; succeeded: 10; failed: 0; skipped: 0
- Tests: 1191 run, 0 failures, 0 errors, 1 skipped
- Spring Boot executable jars: 7

Per module: `.claude/modules/sonar-scans/build/build-summary.md`.

## E. Startup report

| Service | Port | Result | Health | Duration (s) | Detail |
|---|---|---|---|---|---|
| alramz-notification-service | 18080 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 14 | STARTUP_FREE_PORT: ran on 18080; context started; /actuator/health is not in the JWT permit-all list (secured) |
| onboarding-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 23 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=U08BBTgLRumVVyWyMHlFMA==) |
| reference-data-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 27 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=ic2zgcYATiKuhlyWa+30zQ==) |
| reporting-management-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 23 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=PY7xSP/ESPG4iC0c3PQ75g==) |
| risk-management-service | 18080 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | STARTUP_FREE_PORT: ran on 18080; context started; /actuator/health is not in the JWT permit-all list (secured) |
| utility-service | 18080 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | STARTUP_FREE_PORT: ran on 18080; context started; /actuator/health is not in the JWT permit-all list (secured) |
| wealth-management-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 23 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=7H3kIHv1TQiys0WcCHiqCw==) |
