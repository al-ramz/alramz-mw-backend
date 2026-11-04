# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-11-04
- **Feature:** risk-compliance-endpoints — RiskCompliance country list / country ratings (2 GET routes on the new contract and 2 legacy-shape POST routes), proxied to the KnowYourCountry vendor.
- **Target Service(s):** risk-management-service
- **Files Audited:** 30. These are the 30 entries in `implementation-report.md` "Files Touched": 21 `target_files` and 9 `test_files`. No undisclosed feature files were found (see Scope Check).
- **Audit Status:** **PASSED WITH FIXES**

## Severity Summary
| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 2 | 2 | 0 |
| Medium | 4 | 1 | 3 |
| Low | 8 | 0 | 8 |

Each finding below is listed once, at the severity it was assigned after triage. Scanner leads that triage dismissed, and pre-existing scaffold issues, are listed separately. They are not counted.

## Detailed Findings & Remediation

### H-1. Legacy body `correlationID`: log injection (CR/LF) and unbounded value in MDC and the outbound header (flag 1)
- **Severity / OWASP:** High. Skill ID `L4` (IMPORTANT), A09 Security Logging and Alerting Failures.
- **Location:** `services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java` `resolveCorrelationId` (was line 137-144).
- **Issue:**
  - The JSON body value was echoed verbatim and passed to `MDCUtil.putCorrelationId`. It had no charset or length limit.
  - The starter's logback `CLASSIC_PATTERN` writes `[%X{correlationId}]` unescaped on every line. `company.logging.json.enabled` is `false` in every profile, including preprod and prod.
  - JSON-decoded `\r\n` therefore forges log lines in console, file and Seq for the rest of the request. This includes the `@Loggable` aspect, the outbound-call log lines and the error lines.
  - `WebClientLoggingFilter.withCorrelationId` (starter) also copies the MDC value into the outbound `X-Correlation-ID` header sent to KnowYourCountry. The request that carries it also carries our vendor ApiKey.
  - The `X-Correlation-ID` header path (C1) is bounded by Tomcat's CR/LF rejection and its header-size limit. This body path had neither, so it was a wider, new vector.
  - Exploitation needs a JWT with `APP_RISK_MANAGEMENT`.
- **Fix applied:**
  - A body value is now accepted only if it matches `\p{Print}{1,128}`, i.e. printable ASCII, 1 to 128 chars.
  - Any other non-blank value is treated exactly like a blank one under the existing E15 rule. It falls back to the header/MDC value, or to a generated UUID, and a WARN is logged without the rejected value.
  - Legitimate IDs (UUIDs, `abc-123`, `test-corr-001`) behave as before.
  - Tests added to `RiskComplianceLegacyControllerTest`:
    - `shouldFallBackForABodyCorrelationIdWithControlOrNonAsciiChars` (CR/LF, U+2028, TAB).
    - `shouldAcceptA128CharBodyCorrelationIdButFallBackFor129`.
- **Not a blocking gap:** the shape reuses the existing E15 fallback, changes no API schema, and stays within this service. It is a single, cheaply reversible constant.
  - **Reviewer note:** the 128-char cap is a chosen value. If captured legacy crm/Middleware traffic shows longer IDs, raise it during the A1/A2 legacy-fidelity check.

### H-2. SpotBugs `BC_UNCONFIRMED_CAST` (SpotBugs High) in the unscoped framework handler (flag 4)
- **Severity / OWASP:** High (SpotBugs High maps to High). A10 Mishandling of Exceptional Conditions.
- **Location:** `services/risk-management-service/src/main/java/com/alramz/exception/FrameworkExceptionHandler.java:33` (before the fix).
- **Issue:**
  - The handler did `(ErrorResponse) ex` on a parameter typed `Exception`.
  - All four declared exception types implement `ErrorResponse` today, so the cast is safe now.
  - A future edit to the `@ExceptionHandler` list would turn it into a `ClassCastException` inside an unscoped, service-wide error handler. That would fall through to the starter's 500 map, which echoes the URI.
- **Fix applied:** replaced the cast with `instanceof ErrorResponse errorResponse`. Any other type now fails closed with the generic `GenericResponse` 500 (`"Internal Server Error"`, no message).
  - Test: `FrameworkExceptionHandlerTest.nonErrorResponseException_shouldFailClosedWithAGeneric500`.
  - Re-running SpotBugs confirms the finding is gone (6 bugs, now 5).

### M-1. Vendor-supplied `risk_level` / `iso2` in a WARN line (flag 2)
- **Severity / OWASP:** Medium (skill `L4`, downgraded because the source is the TLS-authenticated vendor, not the caller). A09.
- **Location:** `services/risk-management-service/src/main/java/com/alramz/service/RiskComplianceService.java:63-64`.
- **Issue:** SLF4J placeholders do not neutralize CR/LF. A compromised or misbehaving vendor could forge plain-text log lines.
- **Fix applied:** this cheap hardening was applied even though it is Medium. A new `forLog(...)` helper replaces `\p{Cntrl}` with `_` and caps the value at 64 chars. Normal labels log unchanged, so the existing WARN-text assertion still passes.
  - Test: `RiskComplianceServiceTest.forLog_shouldNeutralizeControlCharsAndCapLength`.

### M-2. SpotBugs `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` (SpotBugs Normal)
- **Severity / OWASP:** Medium. A10.
- **Location:** `RiskComplianceLegacyController.java:129` (`toLegacy`, `rating.getRiskLevel().getValue()`).
- **Issue:** the generated `CountryRating.getRiskLevel()` is nullable. The service always sets it, and an NPE would be caught by the legacy route's `RuntimeException` boundary and returned as legacy `"500"`. There is no security impact.
- **Fix:** not fixed (Medium, residual).

### M-3 / M-4. SpotBugs `EI_EXPOSE_REP` / `EI_EXPOSE_REP2` (SpotBugs Normal)
- **Severity / OWASP:** Medium (tool mapping). Real impact is negligible.
- **Location:** `services/risk-management-service/src/main/java/com/alramz/knowyourcountry/KnowYourCountryResult.java:26` (`Unavailable(Throwable cause)`).
- **Issue:** the record stores and returns a mutable `Throwable`. This is internal-only and never serialized to a caller.
- **Fix:** not fixed (residual). It could be suppressed in `config/spotbugs-exclude.xml`, but that file is outside this service's scope.

### Low findings (recorded, not fixed)
- **PMD:**
  - `CyclomaticComplexity` P3 on `KnowYourCountryClient.java:74` `classify` (CC 10). This is error classification, which is a security-adjacent path. It is still readable and fully covered by `KnowYourCountryClientTest`.
  - `ShortMethodName` P3 on `RiskComplianceController.java:38`.
  - `AvoidFieldNameMatchingMethodName` P3 at four sites:
    - `ErrorCode.java:22`
    - `ErrorCode.java:23`
    - `RiskBand.java:11`
    - `RiskBand.java:12`
- **SpotBugs:** `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` Low on `KnowYourCountryClient.java:89`. It is a deliberate rethrow of an unclassified failure, which the service turns into xxx004.
- **apiCollection.http:** the `consumerPassword: "password123"` sample in the local register/login blocks is a synthetic value from the scaffold. The feature changed only `roles`, and `@authToken` is empty.

### Required flags from `implementation-report.md`: outcome
| # | Flag | Outcome |
|---|---|---|
| 1 | Legacy body `correlationID` (CR/LF, length, outbound header) | **Confirmed, High, fixed** (H-1). |
| 2 | Vendor `risk_level` / `iso2` in WARN | **Confirmed, Medium, hardened** (M-1). |
| 3 | Outbound `Authorization: ApiKey` masking | **Dismissed (verified from code). See the analysis after this table.** |
| 4 | Unscoped `FrameworkExceptionHandler` leaks internals? | **No leak** (see the analysis after this table). One related SpotBugs High was fixed (H-2). |
| 5 | Caller JWT never forwarded to KnowYourCountry | **Confirmed safe** (see the analysis after this table). |

**Flag 3: why the outbound `Authorization` header is never exposed.**
- The `apiKeyFilter` is registered before `alramzWebClientLoggingFilterFunction`, so it is outermost. The logging and audit filters therefore see the ApiKey value.
- `WebClientLoggingFilter` writes only method, URL and correlationId to logs.
- Headers reach only span tags (`include-headers: true`) via `LogMaskingUtil.maskHeader`. That check is case-insensitive and matches "authorization" in the default `sensitiveKeys`, so the value becomes `********`.
- `WebClientAuditFilter` stores no request headers in `api_audit_log`. It records URL without the query, method, masked bodies and status only.
- `AbstractRestClient` logs only `[method uri]`.

**Flag 4: why the unscoped handler leaks nothing.**
- The response body contains only the status, the reason phrase, a null `errorCode` and a correlationId.
- Headers come from the `ErrorResponse` (`Allow` / `Accept`). There is no message, stack trace or URI.
- It declares 4 types and no catch-all. At `@Order(1)` it runs ahead of the starter `LoggingExceptionHandler` (`LOWEST_PRECEDENCE`), and it has no overlap with the scoped `GlobalExceptionHandler` at `@Order(0)`.

**Flag 5: why the caller JWT is never forwarded.**
- `AbstractRestClient` sets `Bearer <jwt>` outside the `local` profile. `apiKeyFilter` then calls `headers.set(AUTHORIZATION, ...)`, which replaces every value.
- `KnowYourCountryClientTest.shouldSendOnlyTheApiKeyAndNeverForwardTheInboundBearer` uses:
  - a real `Jwt` principal in `SecurityContextHolder`;
  - a non-`local` `MockEnvironment`;
  - the real `knowYourCountryWebClient` bean method.

  It asserts `containsExactly("ApiKey test-api-key")`.

### Scanner leads dismissed / out of scope (not counted)
- **`S1` `knowyourcountry.api-key: test-api-key`** (`application-test.yml:110`): false positive. It is a synthetic literal pointing at an `.invalid` host, by plan.
- **`E3` catch-all `@ExceptionHandler(Exception.class)`** (`GlobalExceptionHandler.java:33`): false positive. `AccessDeniedException` has its own explicit handler, and the catch-all never echoes `ex.getMessage()`.
- **Pre-existing scaffold, not edited by this feature, so out of scope and not fixed:**
  - `S1` `cipher.password: UseMostSecretKey` in `application-dev.yml:63` and `application-test.yml:40`.
  - `S3` actuator `include: "*"` in `application-dev.yml:31`, `application-docker.yml:14` and `application-test.yml:10`.
  - The `JWT_SECRET` default in dev/docker/test.

  These are dev/docker/test only. preprod/prod are hardened and were not regressed (see below).

## Repo Convention Checks (step 5)
- **JWT: public vs secured matches the spec.** PASS. All 4 methods carry `@JwtSecured(roles = "APP_RISK_MANAGEMENT")`, and the spec §5 says all routes are secured.
- **JWT: `permit-all-urls` in every profile.** PASS / N/A. No route is public. `permit-all-urls` is `[/api/v1/info]`, unchanged, in dev, docker, test, preprod and prod.
- **JWT: no hand-rolled `SecurityFilterChain` / `@EnableWebSecurity`.** PASS. Neither appears in the in-scope files.
- **JWT: role follows `APP_<SERVICE_CONCERN>`.** PASS (`APP_RISK_MANAGEMENT`, and `apiCollection.http` matches).
- **Masking: the new sensitive field is routed through the masking keys.** PASS. The only new secret is the vendor ApiKey, carried in the `Authorization` header. That header is covered by the `LogMaskingUtil` key "authorization", the `SensitiveDataMasker` key `authorization`, and `sensitiveHeaders: Authorization`. Nothing is hand-masked. Verified in code (flag 3).
- **Exception handling: no stack trace, SQL or path in responses.** PASS. Both handlers return a generic `GenericResponse`, and the legacy routes return a fixed `"500"` envelope.
- **Exception handling: no spoofable `HttpStatus` on the exception class.** PASS. The status lives on the `ErrorCode` enum. `RiskComplianceException` carries only the code and a cause.
- **Exception handling: the expected handler actually fires (`@Order`).** PASS:
  - `GlobalExceptionHandler`: scoped to `com.alramz.controllers`, `@Order(0)`.
  - `FrameworkExceptionHandler`: unscoped, `@Order(1)`, no catch-all.
  - Starter `LoggingExceptionHandler`: `LOWEST_PRECEDENCE`.
  - The legacy controller-local `@ExceptionHandler` methods win for that controller's own handler-resolved exceptions.
  - There is no `OnboardingExceptionHandler`-style dead handler.
- **Datasource credentials: no `plainPassword` outside dev/test; preprod/prod use `cipher` + encrypted password.** PASS / N/A. The feature added no datasource config. preprod/prod still have `password` + `passwordVector` from env and `cipher.password: ${CIPHER_MASTER_KEY}` with no default.
- **Datasource: `@Qualifier` on multi-datasource injection.** N/A. There is no datasource injection. The WebClient, CircuitBreaker, Retry and TimeLimiter injections in `KnowYourCountryClient` all carry a correct `@Qualifier`.
- **SQL: bound parameters only.** N/A. The feature adds no SQL.
- **Liquibase: no secrets/PII in seed data; rollback for destructive changes.** N/A. The feature adds no changeset.
- **Scaffold-hardening regression check (caller request).** PASS:
  - preprod/prod still expose only `health,info,prometheus`.
  - `JWT_SECRET`, `CIPHER_MASTER_KEY` and the datasource password/vector have no defaults.
  - The feature only appended the `knowyourcountry` block, where `api-key: ${KNOWYOURCOUNTRY_API_KEY}` has no default and `base-url` defaults to the public vendor host only.

## Scope Check (step 2)
- **No feature commits exist, and `services/risk-management-service/` is entirely untracked,** so `git diff` cannot isolate this feature. As instructed, scope was set to the plan's `target_files` ∪ `test_files`. All 30 match `implementation-report.md` "Files Touched" one-to-one.
- **`git status --short -- services/risk-management-service`** shows only `?? services/risk-management-service/`.
- **Other files in that directory are from the same-session scaffold.** These are:
  - `pom.xml`, `Dockerfile`, `service.yaml`, `mvnw*`, `.gitattributes`, `.gitignore`;
  - `RiskManagementServiceApplication`, `ApplicationController`, `JacksonConfig`;
  - `application.yml`;
  - the Liquibase masters and test SQL;
  - `RiskManagementServiceApplicationTests`;
  - `logs/risk-management-service.log`, which is gitignored by the root `*.log` rule.

  With no baseline, git cannot prove the feature left them untouched. Their content is scaffold-shaped and matches the report's "not touched" list. They were not audited.
- **Tracked-file changes outside the feature:**
  - Root `pom.xml` (+1 `<module>services/risk-management-service</module>`) comes from the scaffold, as the caller stated.
  - Root `README.md` (+14 lines) holds the user's personal workflow notes ("Step 1: start.spring.io ..."). It is not feature work and is not in "Files Touched". It contains no secrets. `05_pr` should not stage it unless that is intended.
  - The untracked `"services/risk-managment-service .zip"` at the repo root is not gitignored. It is not feature work, and `05_pr` must not stage it.

## Automated Tooling Summary
- **`spring-boot-security` skill** (`scripts/scan.py` on `services/risk-management-service`) returned 6 CRITICAL and 2 SUGGESTION leads:
  - In-scope leads: `S1` test-api-key and `E3` catch-all. Both are false positives on triage.
  - The rest are pre-existing scaffold `S1`/`S3` lines in the dev/docker/test profiles and the repo-level `D1` note.
  - The manual review found H-1 and M-1 (`L4`), which the regex scanner does not detect.
  - Not applicable on manual review: AuthZ (IDOR, mass assignment), SSRF (`I5`: the vendor base URL comes from config, and the paths are constants) and deserialization (`I9`: plain records, no default typing).
- **Checkstyle:** **0 findings in in-scope files.** The module's only warnings are `FileTabCharacter` in `RiskManagementServiceApplication.java`, which is scaffold and out of scope.
- **PMD** (ran with `-Dpmd.skip=false`):
  - The module has 7 violations, 6 in scope, all P3, which maps to **Low**. They are listed above.
  - **No `category/java/security.xml` hit.**
  - Out of scope: `SignatureDeclareThrowsException` in `ApplicationController.java`.
  - The count was the same before and after remediation.
- **SpotBugs** (ran with `-Dspotbugs.skip=false`; plain SpotBugs core, **no FindSecBugs**, so security-pattern coverage is limited and this is a tooling gap, not a claim of full coverage):
  - First run: 6 bugs, 5 in scope.
    - `BC_UNCONFIRMED_CAST`: SpotBugs High, mapped to **High**, fixed (H-2).
    - `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`, `EI_EXPOSE_REP` and `EI_EXPOSE_REP2`: **Medium**.
    - `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`: **Low**.
    - The sixth bug is out of scope (`ApplicationController`, Low).
  - After remediation: 5 bugs, with no High remaining.
- **OWASP dependency-check:** **not run.**
  - The repo has no dependency-check plugin.
  - Running the unbound goal needs a full NVD feed download, which takes a long time without an NVD API key, and no API key is available in this environment.
  - The feature added **no dependencies** (`pom.xml` is unchanged by the feature; resilience4j and WebClient come via the starter).
  - Repo-wide CVE coverage belongs to the `alramz-security-vuln-fixer` (Trivy) skill.

## Verification
- `mvn -pl services/risk-management-service -am clean test` was re-run after all remediation and **exited 0**. risk-management-service: 98 tests, 0 failures, 0 errors, 0 skipped (92 before, plus 6 new).
- The static-analysis `verify` sweep was re-run after remediation and exited 0. Its findings were re-read from the console output, not inferred from the exit code.

## Files changed by this stage
- `services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java` (H-1).
- `services/risk-management-service/src/main/java/com/alramz/exception/FrameworkExceptionHandler.java` (H-2).
- `services/risk-management-service/src/main/java/com/alramz/service/RiskComplianceService.java` (M-1).
- `services/risk-management-service/src/test/java/com/alramz/controllers/RiskComplianceLegacyControllerTest.java` (+2 tests, 4 cases).
- `services/risk-management-service/src/test/java/com/alramz/exception/FrameworkExceptionHandlerTest.java` (+1 test).
- `services/risk-management-service/src/test/java/com/alramz/service/RiskComplianceServiceTest.java` (+1 test).

All are in scope. There were no starter/BOM, `infra/`, `.github/` or YAML edits, and no git mutations.

## Residual Risks & Recommendations
1. **Starter `CorrelationIdFilter` (C1, starter-owned, not changed).**
   - It accepts any non-blank `X-Correlation-ID`, and `WebClientLoggingFilter` forwards the MDC value verbatim to every vendor.
   - Tomcat blocks CR/LF on that path, but there is no charset/length policy beyond the 8 KB header limit.
   - Recommendation: a starter ticket applying the same `\p{Print}{1,128}` (or UUID) rule centrally.
2. **Plain-text logging in every profile** (`company.logging.json.enabled: false`, including preprod/prod). `%X{correlationId}` and `%msg` are not escaped, so any future unsanitized value can forge lines. Recommendation: enable JSON logging (skill `L3`) in preprod/prod. This is a profile/ops decision outside this feature's scope.
3. **Vendor 4xx with an empty body is misclassified as Malformed** (starter `AbstractRestClient.onStatus`, from 03_implement). A revoked key appears as CLS002 rather than CLS001. This is an alerting/triage concern. The starter fix (`.defaultIfEmpty("")`) is still recommended.
4. **Missing-JWT 401 vs 403 is unverified** (technical_gaps[1], starter-owned). Verify manually before `05_pr`.
5. **Legacy routes now require a JWT (C2).** APIM must mint/forward a JWT with role `APP_RISK_MANAGEMENT` before cutover.
6. **Operational security:**
   - Provision `KNOWYOURCOUNTRY_API_KEY` from Key Vault in every environment. The service crash-loops without it, by design.
   - **Rotate the leaked legacy vendor key (F-01) before go-live.**
7. **N3:** the full vendor country list is stored in `api_audit_log` on every inbound and outbound call. This is a volume concern, not a sensitivity one. Add the routes to `excluded-paths` if the table grows.
8. **Medium/Low deferred:** M-2, M-3/M-4 and the Low PMD/SpotBugs items above. Consider adding FindSecBugs to the BOM's SpotBugs plugin (repo-level).
9. **Human review before `05_pr`:**
   - Confirm the 128-char legacy `correlationID` cap against captured legacy traffic.
   - Do not stage `README.md` notes, the root `.zip`, or `logs/`.
