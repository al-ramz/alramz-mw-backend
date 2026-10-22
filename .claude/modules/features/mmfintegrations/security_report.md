# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-10-19 (re-run, applying the user's decisions on B1–B4)
- **Feature:** mmfintegrations. Ports the 7 MMF / Athena integration operations (account lookup, sweep-in/out listing, sweep-in confirmation, profit distributions, custodian cash, custodian positions) into `wealth-management-service`.
- **Target Service(s):** `services/wealth-management-service`
- **Files Audited:** 31. This is every file in `implementation-report.md` §2 "Files Touched". The `git status` scope check found no extra files; the remediation edits stayed inside 5 of those 31 files.
- **Audit Status:** **PASSED WITH FIXES** (conditional on the deployment prerequisites below)

**What changed in this re-run (user decisions, applied without re-asking):**
- **B2 fixed:** `cipher.password` in wealth preprod/prod is now `${CIPHER_MASTER_KEY}` with no default, per `multi-datasource-and-jpa.md`.
- **B3 fixed:** `jwt.secret` in wealth preprod/prod is now `${JWT_SECRET}` with no default.
- **B4 fixed:** actuator exposure in all five wealth profiles (dev, docker, preprod, prod, test) is now `health,info,prometheus` instead of `"*"`.
- **B1 accepted for this feature:** the `/api/auth/register` self-role-assignment issue lives in the shared starter, which must not be changed here. It is recorded as a separate platform ticket. Go-live depends on confirming that the API Gateway blocks `/api/auth/**` externally.
- **A1 (custodian-cash, High):** still accepted by the user (Q2 = B).
- No other service and no file in `alramz-api-starter`, `alramz-common-bom`, `infra/`, `k8s/` or `.github/` was touched.

**Deployment prerequisites (preprod/prod). Without these, wealth will fail to start:**
1. `CIPHER_MASTER_KEY`: a 16/24/32-byte AES key from Key Vault/CI. The old literal is public in git history, so pick a **new** value. If the value differs from the old hard-coded key, **re-encrypt every encrypted datasource password with it** (`PWProtector.encrypt`) and update the matching `*_PASSWORD` / `*_PASSWORD_IV` secrets. That covers `COMPANY_DATASOURCE_MIDDLEWARE_*`, `COMPANY_DATASOURCE_BROK_*` and `COMPANY_DATASOURCE_INTEGRATION_*`; middleware is encrypted with the same key too.
2. `JWT_SECRET`: must be set, and must be the same signing key the other services and the gateway use, or tokens will not validate across services.
3. The existing MMF integration env vars (A8): `COMPANY_DATASOURCE_INTEGRATION_URL` / `_USERNAME` / `_PASSWORD` / `_PASSWORD_IV`, and `MMF_CUSTODIAN_CLIENT_IDS`.
4. None of the above is wired in `k8s/` or `.github/` yet. That wiring is out of scope for this stage and needs an owner.

**Final gate:** `mvn -pl services/wealth-management-service -am clean test` exited **0** (wealth 190 tests, starter 130 tests, 0 failures, 0 errors).
- The first two post-fix runs failed at test-compile with `class file for MmfBackendException not found` / `class file for ApplicationException not found`. This is the known VS Code Java language-server race on `target/classes` (implementation-report D3). No Java source was changed in this re-run.
- The third run, with no change in between, exited 0.

## Severity Summary

**A. Findings introduced by this feature (in its diff)**

| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 1 | 0 | 1 (risk accepted by user, Q2 = B; not blocking) |
| Medium | 7 | 0 | 7 (residual recommendations) |
| Low | 5 | 0 | 5 (residual recommendations) |

**B. Pre-existing findings that weaken this feature (not in its diff)**

| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 4 | 3 (B2, B3, B4; wealth config only) | 1 (B1: accepted for this feature, separate platform ticket, gateway confirmation required) |
| High | 0 | 0 | 0 |
| Medium | 0 | 0 | 0 |
| Low | 0 | 0 | 0 |

## Detailed Findings & Remediation

### B1. Public self-registration lets the caller choose their own roles (accepted for this feature; platform ticket)
- **Severity / OWASP category:** Critical. AZ5 privilege escalation via client-supplied role; A01 Broken Access Control.
- **Location:**
  - `services/alramz-api-starter/src/main/java/com/alramz/jwt/controller/AuthController.java:27-36`
  - `services/alramz-api-starter/src/main/java/com/alramz/jwt/service/AuthService.java:82-98`
  - `register-url` is permitted without authentication by `JwtAutoConfiguration.java:88`.
  - wealth sets `register-url: /api/auth/register` in `application-{dev,preprod,prod}.yml`.
  - This is a shared starter file and pre-existing.
- **Issue:**
  - `POST /api/auth/register` needs no authentication.
  - It stores `request.roles()` exactly as sent, then returns a signed token carrying those roles.
  - So anyone who can reach the service can register with `roles: ["APP_WEALTH_MANAGEMENT_SERVICE_USER"]` and call all 7 MMF endpoints, including the money-moving writes and custodian-cash for any `CL_CLIENT_ID`.
- **Decision (user):** do **not** change `alramz-api-starter` in this feature. Record this as a **separate platform ticket** covering every service that uses the starter.
- **Fix applied:** none in code, by user decision. Before go-live:
  - **Required:** confirm, and record in the PR, that the API Gateway blocks `/api/auth/**` (at least `/api/auth/register`) from external callers in preprod/prod.
  - **Platform ticket options:** (a) require an admin role on `register`; (b) assign roles server-side from an allow-list; (c) disable `register-url` in preprod/prod.
- **Status:** accepted risk for this feature. It is not silently downgraded: it stays listed as a Critical remaining item, and go-live is gated on the gateway confirmation.

### B2. Hard-coded `cipher.password` (AES master key) in preprod/prod (FIXED)
- **Severity / OWASP category:** Critical. S1 hard-coded secret / C4 hard-coded key; A04 Cryptographic Failures.
- **Location:** `services/wealth-management-service/src/main/resources/application-preprod.yml:61` and `application-prod.yml:61`.
- **Issue:** the AES key that decrypts the middleware, brok and new integration datasource passwords was a literal committed to git (`UseMostSecretKey`).
- **Fix applied:** replaced in both files with `password: ${CIPHER_MASTER_KEY}`, with no default, exactly as `multi-datasource-and-jpa.md` prescribes. A missing key now fails startup instead of silently using a public key.
  - The literal is still in git history, so the new `CIPHER_MASTER_KEY` must be a **new** value, and all encrypted datasource passwords must be re-encrypted with it (see Deployment prerequisites).
  - The dev, docker and test profiles keep their local/test keys, which is acceptable for non-deployed or dev-only profiles. Other services still carry the literal in preprod/prod (out of scope, see Residual Risks).

### B3. Insecure default fallback for `jwt.secret` in preprod/prod (FIXED)
- **Severity / OWASP category:** Critical. S1 / AU1 (token forgery); A07 Authentication Failures.
- **Location:** `application-preprod.yml:103` and `application-prod.yml:103`.
- **Issue:** `${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}` meant that if the env var was missing, tokens were signed and verified with a key that is public in git, so anyone could forge `APP_WEALTH_MANAGEMENT_SERVICE_USER` tokens.
- **Fix applied:** changed to `secret: ${JWT_SECRET}` with no default. A missing secret now fails startup. The dev, docker and test profiles keep the fallback (local only). Other services still have it (out of scope).

### B4. Actuator exposed every endpoint (`include: "*"`) in every profile (FIXED)
- **Severity / OWASP category:** Critical per the scanner (S3; A02 Security Misconfiguration).
- **Location:** `management.endpoints.web.exposure.include` in `application-{dev,docker,preprod,prod,test}.yml` (line 29 in dev/preprod/prod, 14 in docker, 10 in test).
- **Issue:** `/actuator/heapdump`, `env`, `configprops`, `threaddump` and others were reachable by any token holder. A heap dump contains the decrypted datasource passwords and the cipher key.
- **What the observability stack needs:**
  - **Prometheus** scrapes `/actuator/prometheus`. `alramz-api-starter/pom.xml:193` adds `micrometer-registry-prometheus` "for the Grafana Spring Boot dashboard", and `utility-service` / `reference-data-service` comment "scraped by Prometheus".
  - **Traces and logs** are **pushed** over OTLP (`management.opentelemetry.*`), so they need no actuator endpoint.
  - OTLP metrics export is disabled.
  - **`.github/actions/smoke-test`** uses `/actuator/health`.
  - Nothing in the repo uses `/actuator/metrics`, so it was **not** kept.
- **Fix applied:** set `include: health,info,prometheus` in all five profiles.
  - No test references `/actuator`; the test profile is unaffected (gate green).
  - Narrowing dev and docker too was deliberate: the dev server is shared, and a heap dump there still leaks dev credentials. To restore extra endpoints for local debugging, override `management.endpoints.web.exposure.include` locally rather than committing `"*"`.
- **Pre-existing, noted only:** wealth's `permit-all-urls` lists only `/api/v1/info`, so in this service `/actuator/prometheus` and `/actuator/health` require a JWT. Prometheus scraping and the smoke test will get 401 unless they present a token.
  - Other services add `/actuator/prometheus` to `permit-all-urls`. Whether to do the same for wealth (in every profile) is an observability decision, not a security fix, so it was not changed here.

### A1. custodian-cash returns any client's balance to the Athena role (risk accepted)
- **Severity / OWASP category:** High. AZ3-style missing object-level authorization; A01. **Risk accepted by the user (spec decision Q2 = B).** This is recorded, not "fixed", as instructed.
- **Location:**
  - `services/wealth-management-service/src/main/java/com/alramz/service/impl/MmfServiceImpl.java:291-293`
  - `MmfRepositoryImpl.java:126-134`
  - `sql/sql-queries.xml` `mmf.custodian.cash`
- **Issue:** any holder of `APP_WEALTH_MANAGEMENT_SERVICE_USER` can read the cash balance of any `CL_CLIENT_ID`, up to 500 per call. The injection from the legacy flow is gone (all values are bound), but the exposure remains. B1 makes it considerably worse, because the role can be self-assigned.
- **Fix applied:** none, by user decision. Revisit together with B1.

### A2. Account numbers, referenceIds and client data logged and audited without masking
- **Severity / OWASP category:** Medium. L2; A09.
- **Location:**
  - `MmfServiceImpl.java:98` (404 message), `:164` (MMF037 ERROR line with sweep numbers), `:237` and `:245` (`referenceId`)
  - `MmfRepositoryImpl.java:66` (`accountNumber`)
  - request/response logging plus DB audit rows of payloads (names, balances, positions)
- **Issue:**
  - `SensitiveDataMasker` and `company.logging.masking.sensitive-keys` cover only credential and national-ID keys, not account numbers or balances.
  - The spec (§5, §9) chose not to extend the masker, following reporting-management-service's precedent. Extending it would be a change to the shared starter.
  - Nothing is hand-masked, so the masking convention is not violated. All logged identifiers are digit-only after `@Pattern` validation (`^\d{1,9}$`, `^\d{1,15}$`) or enums, so there is no log-injection (CRLF) path.
- **Fix applied:** none (accepted by spec #9). Recommend a repo-wide decision on financial-identifier masking.

### A3. No rate limit or batch-cost guard on the write endpoints
- **Severity / OWASP category:** Medium. AP1/AP5; A06.
- **Location:** `MmfServiceImpl.createProfitDistributions` (up to 5000 items, about 10,000 sequential DB round-trips) and `confirmSweepIns` (`FOR UPDATE` with no `WAIT`; plan TG #9).
- **Issue:** a token holder can tie up both Oracle pools with repeated maximum-size batches.
- **Fix applied:** none. Recommend gateway-level throttling for the Athena consumer.

### A4. Checkstyle NPathComplexity in `createProfitDistributions`
- **Severity / OWASP category:** Medium (complexity; A06).
- **Location:** `MmfServiceImpl.java:208`. NPath 1,200 (max 200). PMD also reports cyclomatic 18 and cognitive 27.
- **Issue:** this is business logic rather than an auth path. It is covered by 31 service tests, including the all-duplicate, unreachable-first-item and mid-batch cases.
- **Fix applied:** none (residual).

### A5. Checkstyle ClassFanOutComplexity in `GlobalExceptionHandler`
- **Severity / OWASP category:** Medium (complexity in exception handling; A10).
- **Location:** `GlobalExceptionHandler.java:44`. Fan-out 35 (max 30). PMD also reports GodClass, CouplingBetweenObjects and ExcessiveImports.
- **Issue:** the fan-out comes from the number of framework exception types handled, not from hidden branching.
  - Leakage behaviour is pinned down by `GlobalExceptionHandlerTest` (43) and `MmfApiDispatchTest` (47).
  - I re-read every handler. 500/503 bodies use fixed text (`INTERNAL_MSG` / `UNAVAILABLE_MSG`, or `ErrorCode` default messages). The null-code branch never returns `ex.getMessage()`, and the catch-all never echoes the exception.
  - Not blocking.
- **Fix applied:** none (residual).

### A6. PMD `GuardLogStatement` (priority 2) ×6
- **Severity / OWASP category:** Medium by the priority mapping. In practice this is a performance note, not a security issue.
- **Location:** `GlobalExceptionHandler.java:73,79`; `MmfRepositoryImpl.java:66`; `MmfServiceImpl.java:164,237,245`.
- **Fix applied:** none (residual).

### A7. SpotBugs `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` on generated `Mmf*` models ×22
- **Severity / OWASP category:** Medium by the priority mapping (SpotBugs Normal).
- **Location:** `target/generated-sources/.../com/alramz/model/Mmf*Response.java`, `MmfSweepConfirmationResult.java`.
- **Issue:** these are generated from the in-scope `specs/apiSpecs.yaml`. They are mutable request-scoped DTOs and carry no security relevance.
- **Fix applied:** none. The code is generated and must not be edited.

### A8. Startup coupling to the integration datasource in preprod/prod
- **Severity / OWASP category:** Medium (availability; A02).
- **Location:** `application-{preprod,prod}.yml` `company.datasource.integration` (`enabled: ...:true`; `url`/`username`/`password`/`passwordVector` have no defaults).
- **Issue:** the `COMPANY_DATASOURCE_INTEGRATION_*` and `MMF_CUSTODIAN_CLIENT_IDS` env vars are not wired in `k8s/` or `.github/` (grep found no hits). Deploying this as it stands fails startup for the **whole** wealth service (plan TG #1).
- **Fix applied:** none. This is a deployment prerequisite, and `k8s/` / `infra/` are out of scope.

### A9. Framework binding errors reach a token holder without the role before the role check
- **Severity / OWASP category:** Low. Information disclosure; A01.
- **Location:** `GlobalExceptionHandler` handlers for `MissingServletRequestParameterException`, `MethodArgumentTypeMismatchException` and `HttpMessageNotReadableException`.
- **Issue (03_implement flag 1):**
  - **Unauthenticated callers:** they never get validation detail. `JwtAutoConfiguration`'s `anyRequest().authenticated()` stops them in the filter chain, before dispatch.
  - **Authenticated callers without the role, binding errors:** argument binding happens before the controller proxy is invoked, so they can get a 400 with a fixed `MMF0xx` message for a missing parameter, bad enum or date, or unreadable JSON.
  - **Constraint violations (`ConstraintViolationException`):** from code reasoning, these occur **after** the role check. Boot's AspectJ auto-proxy creator is registered at `HIGHEST_PRECEDENCE` and so builds the proxy first. `MethodValidationPostProcessor` (`beforeExistingAdvisors=false`) then appends its advisor, so `JwtSecuredAspect`'s `@Before` runs first and returns 403.
  - This ordering was not exercised at runtime, because the MMF beans are disabled in tests.
  - What leaks is limited to fixed messages that already appear in the published OpenAPI contract. No data leaks.
- **Fix applied:** none (residual).

### A10. The advice's catch-all also covers the pre-existing `ApplicationController`
- **Severity / OWASP category:** Low (A10).
- **Location:** `GlobalExceptionHandler.java:45` (`basePackages = "com.alramz.controllers"`), `:191-197`.
- **Issue:** an unexpected failure in `/api/v1/info` now returns a generic 500 with `errorCode` MMF090 instead of the starter's `LoggingExceptionHandler` shape. This changes behaviour, not security. The response is still generic, with no leakage.
- **Fix applied:** none (residual). The advice could be scoped with `assignableTypes = MmfController.class`.

### A11. 404/409 `errorMsg` echoes identifiers the caller supplied
- **Severity / OWASP category:** Low (A10).
- **Location:** `MmfServiceImpl.java:98` ("Account n not found in book X"), `:183-184` (MMF033 sweep list), `:263-264` (MMF044 referenceIds).
- **Issue:** only the caller's own digit-validated input is echoed back, never database data. The per-item `errorCode` in 207 results is a code only (`fail()` sets `code.code()`). No ORA- text is ever placed in a response: it is only logged (`MmfServiceImpl:238`, `GlobalExceptionHandler:88-92`).
- **Fix applied:** none needed.

### A12. SpotBugs `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`
- **Severity / OWASP category:** Low.
- **Location:** `MmfServiceImpl.java:168` (rethrows the original `RuntimeException` when the inner book did not commit).
- **Issue:** intentional. The exception propagates to the handler, which classifies it.
- **Fix applied:** none.

### A13. Remaining PMD Code Style / Design findings (about 330)
- **Severity / OWASP category:** Low.
- **Location:** in-scope main classes. 302 Code Style findings (`MethodArgumentCouldBeFinal`, `LocalVariableCouldBeFinal`, and similar) plus Design findings (`LawOfDemeter`, `CyclomaticComplexity` 10–11 in `listSweeps` / `confirmSweepIns`, `ExcessivePublicCount` in `ErrorCode`), and Error Prone findings (`AvoidFieldNameMatchingMethodName` in `ErrorCode:129-130`, `AvoidLiteralsInIfCondition`, `AvoidCatchingGenericException` at `MmfServiceImpl:161`, which catches and rethrows deliberately).
- **Fix applied:** none (style).

## Repo Convention Checks (step 5)
- **JWT: public vs. secured matches the spec:** PASS. All 7 methods carry `@JwtSecured(roles = "APP_WEALTH_MANAGEMENT_SERVICE_USER")` (`MmfController.java:44-91`), as spec §5 requires. Nothing is public.
- **JWT: `permit-all-urls` in every profile:** PASS / not applicable. Nothing new is public. `permit-all-urls` still lists only `/api/v1/info` (checked in preprod/prod).
- **JWT: no hand-rolled `SecurityFilterChain` / `@EnableWebSecurity`:** PASS. None was added.
- **JWT: role follows the `APP_<SERVICE_CONCERN>` convention:** PASS. The role is `APP_WEALTH_MANAGEMENT_SERVICE_USER`. (The role's real strength is undermined by B1.)
- **Masking: new sensitive fields routed through `sensitive-keys` / `SensitiveDataMasker`:** PASS on convention (nothing is hand-masked with string manipulation). Not masked by decision (spec §5/§9); see A2.
- **Exception handling: no stack trace, SQL or path leaks:** PASS. Fixed texts for 500/503; ORA text is logged only (A5, A11).
- **Exception handling: no `HttpStatus` baked into exceptions:** PASS. The five typed exceptions carry only an `ErrorCode` and a message; the handler chooses the status.
- **Exception handling: the handler that actually fires:** PASS.
  - There is a single advice at `@Order(0)` in wealth. The starter's `LoggingExceptionHandler` is `@Order(LOWEST_PRECEDENCE)`, so wealth's advice wins.
  - There is an explicit `AccessDeniedException` → 403 handler with fixed text "Access denied", so the catch-all cannot turn a role failure into a 500, and the required role names are not echoed.
  - No `OnboardingExceptionHandler`-style second, dead advice was copied.
- **Datasource credentials: no `plainPassword` outside dev/test:** PASS for this feature's lines.
  - `plainPassword` appears only in dev and docker (docker is a local profile, and the value comes from an env var). It also appears in `src/test/resources/application.yml`, as an H2 `sa`.
  - preprod/prod use `password` + `passwordVector` from env vars.
  - **PASS (fixed in this re-run):** `cipher.password` in preprod/prod is now `${CIPHER_MASTER_KEY}` with no default (B2).
- **Datasource `@Qualifier` on every multi-datasource injection:** PASS.
  - `MmfRepositoryImpl.java:45-46` uses `brokNamedParameterJdbcTemplate` and `integrationNamedParameterJdbcTemplate`.
  - `MmfServiceImpl.java:80-81` uses `brokTransactionManager` and `integrationTransactionManager`.
  - There is no bare `@Transactional`, which would bind to the `@Primary` middleware TM.
  - Custodian reads use only CAPITAL/brok (`MmfRepositoryImpl:127,141`).
- **SQL: bound parameters only, including inside `SqlQueriesManager` files:** PASS.
  - All 9 entries in `sql/sql-queries.xml` use `:named` binds, including every `IN (:sweepNos)` / `IN (:clientIds)`, which `NamedParameterJdbcTemplate` expands.
  - No `||`, `${` or Java-side string building of SQL. The only literals are constants (`'W'`, `'I'`, `'P'`, `0`, `SYSDATE`, market codes).
  - IN-list sizes stay within Oracle's limit of 1000: up to 1000 sweepNos and up to 500 clientIds. The configured `mmf.custodian.client-ids` list has no limit, but it is operator-controlled.
  - The XML DOCTYPE is a classpath resource that `Properties.loadFromXML` resolves internally, so there is no XXE input path.
- **Liquibase: no secrets or PII in seed data; rollbacks:** Not applicable. No `db/changelog/**` file changed (confirmed via `git status`).

## Scope Check (step 2)
`git status --porcelain --untracked-files=all` shows exactly 31 changed or new files outside `.claude/`. They match `implementation-report.md` §2 one-to-one: 9 modified tracked files (`git diff --name-only`) and 22 untracked new files. There is **no undisclosed scope creep**.
- **Areas that must not change:** no changes under `infra/`, `k8s/`, `.github/`, `services/alramz-api-starter/`, `services/alramz-common-bom/`, or any `db/changelog` path (the targeted `git status` returned nothing). This confirms implementation-report §5 Housekeeping, which 03_implement could not check with git.
- **Unrelated changes:** the reference-data / reporting / relationship-manager changes mentioned in the brief were **not present** in the working tree at audit time, so there was nothing to exclude.
- **Under `.claude/`:** the only untracked files are the feature's own artefacts (`spec.md`, `execution_plan.json`, `implementation-report.md`, `source-design-doc.md`), plus this report.
- **Not in a git diff:** there is no base-branch diff yet (uncommitted working tree), so the check used `git status` / `git diff --name-only` with no ref, as the brief instructed.
- **Re-run check (after the B2–B4 remediation):** `git status --short` lists the same 9 modified and untracked paths as before. `git status --short infra k8s .github services/alramz-api-starter services/alramz-common-bom` returned nothing. The remediation edits touched only `application-{dev,docker,preprod,prod,test}.yml` in wealth, all of which are already in Files Touched.

## Automated Tooling Summary
- **`spring-boot-security` skill** (`scripts/scan.py` over `services/wealth-management-service`): 11 hits.
  - **9 Critical** pattern hits, all on pre-existing lines of in-scope YAML files:
    - S1: `cipher.password: UseMostSecretKey` in dev, preprod, prod and test (B2). Fixed in preprod/prod. The dev and test hits are local/test keys and are accepted.
    - S3: actuator `include: "*"` in 5 profiles (B4). Fixed in all 5.
  - **1 Suggestion, E3:** the catch-all `@ExceptionHandler(Exception.class)` at `GlobalExceptionHandler.java:191`. Confirmed **not** an issue: `AccessDeniedException` has its own explicit handler first.
  - **1 Suggestion, D1** (repository-level dependency advisory).
  - **Manual review beyond the regexes** found B1 (role self-assignment through `register`) and B3 (JWT secret fallback). The scanner did not catch either.
  - No injection, deserialization, SpEL, SSRF, XXE, crypto or CORS finding exists in the feature's Java code.
- **Checkstyle** (in-scope files only): 2 findings.
  - Medium: `NPathComplexity` at `MmfServiceImpl.java:208` (A4);
  - Medium: `ClassFanOutComplexity` at `GlobalExceptionHandler.java:44` (A5).
  - The other violations reported for this module are in `WealthManagementServiceApplication.java` (tabs, `ConstantName`), which is out of scope.
- **PMD 7.10.0** (`-Dpmd.skip=false`; 356 violations across the module, 337 of them in in-scope files):
  - **0 hits from the `category/java/security.xml` ruleset.**
  - In-scope breakdown: Code Style P3 ×302, Design P3 ×23, Best Practices P2 ×6 (`GuardLogStatement`, Medium, A6), Error Prone P3 ×5, Best Practices P4 ×1 (`UseVarargs`). Everything except the 6 Medium findings is Low (A13).
  - No correctness defect that matters for security (for example, a swallowed exception). The one generic catch (`MmfServiceImpl:161`) rethrows.
- **SpotBugs 4.10.5.0** (`-Dspotbugs.skip=false`; 24 bugs across the module):
  - **In-scope hand-written code:** 1 Low, `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` at `MmfServiceImpl.java:168` (A12).
  - **Generated `Mmf*` models (from in-scope `apiSpecs.yaml`):** 22 Medium `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` findings (A7).
  - **Out of scope:** `ApiUtil`, `ApplicationController`.
  - No null dereference or resource leak in an auth, token or datasource path.
  - **Tooling gap:** this is plain SpotBugs core with **no FindSecBugs**, so its security-pattern coverage is limited. This is not a claim of full coverage. Recommend adding FindSecBugs to `alramz-common-bom`.
  - The first `verify` attempt failed in the test phase (the IDE race described in the Executive Summary), so SpotBugs did not run then. The second `verify` run exited 0 with 190 wealth tests green, and the SpotBugs results above come from that run.
- **OWASP dependency-check:** **could not run.** `mvn org.owasp:dependency-check-maven:check -pl services/wealth-management-service` (plugin 13.0.0, unbound goal) failed with `UpdateException: Error updating the NVD Data — NvdApiException: Invalid API Key, length of 0`. No NVD API key is configured, and the repo registers no dependency-check plugin. No dependency CVE findings are available.

## Residual Risks & Recommendations
**Must happen before go-live:**
1. **Deployment prerequisites** (see Executive Summary): set `CIPHER_MASTER_KEY` (a new value, then re-encrypt the middleware, brok and integration passwords), `JWT_SECRET`, `COMPANY_DATASOURCE_INTEGRATION_*` and `MMF_CUSTODIAN_CLIENT_IDS` in preprod/prod. Otherwise wealth fails to start. The `k8s/` / Key Vault wiring needs an owner.
2. **B1 gateway confirmation:** confirm that the API Gateway blocks `/api/auth/**` externally, and open the platform ticket for the starter's `register` role self-assignment.

**Flagged for human review (not changed here):**
- `/actuator/prometheus` and `/actuator/health` are not in wealth's `permit-all-urls`, so Prometheus scraping and the CI smoke test will get 401 without a token. Decide whether to add `/actuator/prometheus` (and `/actuator/health`) to `permit-all-urls` in every wealth profile, as other services do.
- Other services still have the hard-coded `cipher.password`, the `JWT_SECRET` fallback and `include: "*"` in preprod/prod. This needs a repo-wide follow-up ticket; it was deliberately not touched here.

**Deferred Medium/Low items:**
- A1: accepted custodian exposure (revisit with the B1 platform ticket).
- A2: masking of financial identifiers.
- A3: gateway throttling for Athena.
- A4–A7: complexity and static-analysis noise.
- A8: deployment env-var wiring (see above).
- A9: binding 400s returned before the role check.
- A10: consider `assignableTypes = MmfController.class` on the advice.
- A11–A13: informational.

**Notes:**
- **JaCoCo exclusion:** the BOM's `**/exception/**` JaCoCo exclusion means `ErrorCode`, `GlobalExceptionHandler` and the typed exceptions are not instrumented. The >80% coverage gate is unverified for them, although they have 85 dedicated unit tests plus 47 dispatch tests. Recorded only; the BOM was not changed.
- **Inferred joins:** the inferred Oracle joins (`mmf.account.find`, `mmf.custodian.positions`) are only text-checked. Verify them against the IS adapters or preprod before go-live (plan TG #3).
- **Environment:** the VS Code Java language server rewrites `target/classes` during Maven builds in this OneDrive-hosted checkout. It caused 2 spurious failures in this re-run. Close the IDE, or exclude `target/` from its build, before relying on local runs.
- **Static analysis:** Checkstyle, PMD and SpotBugs were not re-run in this re-run. Only YAML changed, and none of the three analyzes YAML, so the earlier tooling results still apply.
