# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-10-26
- **Feature:** sms-feature — OTP send / validate / configuration lookup (`POST /api/v1/otp/send`, `POST /api/v1/otp/validate`, `GET /api/v1/otp/configurations`)
- **Target Service(s):** alramz-notification-service
- **Files Audited:** 29 (the full "Files Touched" list in `implementation-report.md`). In the shared files (`GlobalExceptionHandler`, the 6 yml files, `sql-queries.xml`, `openapi.yaml`, `TemplatedEmailService`, `SmsServiceImpl`, `pom.xml`, `apiCollection.http`, `AlramzNotificationServiceApplication`), only this feature's changes were audited. The step 2 check found no scope-creep files.
- **Audit Status:** **PASSED WITH FIXES**

**Accepted risks, decided by the user and recorded only (not re-litigated):**
- C-2: starter open registration.
- The shared `jwt_user`.
- E-1: the dev `JWT_SECRET` fallback in `application-dev.yml`.

## Severity Summary
| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 2 | 2 | 0 |
| Medium | 9 | 0 | 9 (residual, see below) |
| Low | 5 | 0 | 5 (residual) |

## Detailed Findings & Remediation

### H-1. Fixed test OTP could be switched on in preprod (OTP bypass on a deployed environment)
- **Severity / OWASP:** High. A07 Authentication Failures / A02 Security Misconfiguration.
- **Location:**
  - `services/alramz-notification-service/src/main/java/com/alramz/service/OtpService.java:112-116` (startup guard)
  - `src/main/resources/application-preprod.yml:26`
  - `src/main/resources/application-prod.yml:26`
- **Issue:** The startup guard only checked `matchesProfiles("prod")`.
  - Preprod relied on a yml literal `false`, and its comment claimed "literal: no env override". That claim is wrong. Spring relaxed binding lets the OS env var `APP_OTP_FIXEDTESTOTPENABLED=true`, or `SPRING_APPLICATION_JSON` or a `--` arg, override any yml value.
  - One env var on the deployed preprod would make every OTP `1234`/`123456`. Nothing would be delivered, and the validate endpoint would accept the public hash `03ac6742…` (it is in `apiCollection.http`). That is a full OTP bypass on preprod.
- **Fix applied:**
  - The guard is now `matchesProfiles("prod | preprod")`, with the message "must be false in prod and preprod". Spec D8 already says the flag is false in preprod/prod, so behaviour is unchanged when config is correct.
  - The incorrect yml comments in preprod and prod were corrected.
  - Test: `OtpServiceTest#e23_fixedTestOtpInProdFailsStartup` now also asserts that preprod with the flag on throws.
- **Also confirmed:** with the flag on, there is no SMS or email call (`delivered.addAll(all)` without touching `smsService`/`emailService`; test `#e9_*`).

### H-2. A successful validate reset the daily send cap for every listed recipient, so any victim's cap could be bypassed
- **Severity / OWASP:** High. A06 Insecure Design (unrestricted resource consumption, SMS pumping/bombing) plus OTP brute-force amplification (AU4).
- **Location:**
  - `OtpService.java:240-259` (`check`)
  - `repository/OtpRepository.java:35,113-117`
  - `src/main/resources/sql/sql-queries.xml` (`otp.management.lockCandidates`)
- **Issue:** On a match, `resetAttempts(serviceName, recipients)` ran over all recipients in the request, not only the matched ones. Any `APP_NOTIFICATION` caller (reachable by anyone under C-2) could clear a victim's cap:
  1. Send an OTP to an address they own (E).
  2. Validate with `recipients = "E|VICTIM"` and E's correct hash.
  3. `markValidated` touches only E's row, but `otp_attempts` for VICTIM is reset to 0.

  Repeating this makes the per-recipient daily `max_attempts` cap ineffective:
  - Unlimited SMS/email to any victim, which means toll-fraud and harassment risk.
  - Unlimited fresh OTPs for VICTIM, at about 2 guesses per cycle before OTP008. That makes a 4-digit OTP brute-forceable over time.
- **Fix applied:**
  - `OtpCandidate` now carries `mobileEmail`, and `lockCandidates` selects `mobile_email`.
  - `resetAttempts` is called only with the distinct recipients of the matched rows.
  - Normal use is unchanged: one send to `mobile|email` stores the same hash on both rows, so both match and both reset. This matches spec E18 ("reset for those recipients").
  - Regression test: `OtpServiceTest#matchResetsTheDailyCounterOnlyForTheMatchedRecipient`. `OtpRepositoryTest#lockActiveCandidates_*` now asserts `mobile_email`.

### M-1. Validate is "any listed recipient matches" (cross-recipient validation)
- **Severity / OWASP:** Medium. A01/A07 (depends on the caller).
- **Location:** `OtpService.java:245-252`
- **Issue:** `valid:true` is returned if any listed recipient's row matches. A consuming app that forwards end-user-controlled `recipients` could therefore be satisfied by the user's own OTP while listing someone else. Callers are trusted apps, and this is the spec's multi-channel semantic (the same OTP goes to mobile and email).
- **Recommendation:** Document for consuming apps that `recipients` must come from the server-side account record, never from user input. Requiring all listed recipients to match would be a contract change; raise it with the PO if needed.

### M-2. No per-caller or per-IP throttle on the 3 endpoints
- **Severity / OWASP:** Medium. AP1 / A06.
- **Issue:** The limits that exist are:
  - per-OTP validation (default 3)
  - per-recipient daily sends (`max_attempts`, now non-bypassable after H-2)
  - 5 recipients per request

  Under C-2 any registered client can spray sends across many distinct numbers, or across several `service_name` configs that each have their own counter.
- **Recommendation:** Add a per-`JwtContext` application rate limit (for example Bucket4j or a gateway policy) before go-live. Not Critical, so not built here.

### M-3. `SensitiveDataMasker` has no `otp` / `hashedOtp` / `recipients` keys
- **Severity / OWASP:** Medium. L2 / A09.
- **Issue:** Today there is no `@Loggable` on `OtpController` and payload logging is off, so nothing leaks. If request/response payload logging is enabled later, the validate hash would be logged in clear. The hash is SHA-256 of 4-6 digits, so it is trivially reversible (see M-4). Recipients (PII) would be logged too.
- **Recommendation:** Add `hashedOtp,hashedotp,otp,recipients` to `company.logging.masking.sensitive-keys` before anyone enables payload logging.

### M-4. Stored OTP is unsalted SHA-256 of a 4-10 digit value
- **Severity / OWASP:** Medium. A04 (spec G11, out of scope).
- **Issue:** Anyone with read access to `otp_management.otp` can reverse an active OTP instantly (at most 10^6 hashes for 6 digits). The impact is bounded by expiry and by DB access controls.
- **Recommendation:** HMAC-SHA256 with a server key in a future iteration, once legacy hash parity (decision 6) is no longer needed.

### M-5. SpotBugs: possible null dereference of `keyHolder.getKey()`
- **Severity / OWASP:** Medium (SpotBugs Normal). A10.
- **Location:** `OtpRepository.java:90` (`NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`)
- **Issue:** A null key would cause an NPE inside tx1. The transaction rolls back and the caller gets a generic 500, so this is not security-relevant.
- **Recommendation:** `Objects.requireNonNull(keyHolder.getKey(), "no generated id")`.

### M-6. SpotBugs: `CT_CONSTRUCTOR_THROW` on both `OtpService` constructors
- **Severity / OWASP:** Medium (SpotBugs mapping). A10.
- **Issue:** This is the finalizer-attack pattern. `OtpService` is a Spring singleton that is never subclassed or deserialized, so it is not exploitable. The throws are the intended E23 startup guards.
- **Recommendation:** Accept, or make the class `final`.

### M-7. SpotBugs: `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` on the generated `OtpSendResult` and `OtpConfigurationList`
- **Severity / OWASP:** Medium (SpotBugs mapping). Not exploitable.
- **Issue:** This is openapi-generator output, the same pattern as every other generated model in the module.
- **Recommendation:** Exclude `com.alramz.model.*` EI_* in `config/spotbugs-exclude.xml` (repo-wide decision).

### M-8. Checkstyle complexity in `OtpService`
- **Severity / OWASP:** Medium (complexity rule mapping).
- **Location:**
  - `OtpService.java:396` `parse` (`NPathComplexity` 2,808): this is input validation.
  - `OtpService.java:60` (`ClassFanOutComplexity` 36 > 30).
- **Issue:** `parse` was read line by line and is correct. Every token must match the email regex or the mobile regex, and the cap is enforced after channel filtering.
- **Recommendation:** Split `parse` into tokenise / classify / filter when it is next touched.

### M-9. PMD priority-2 `GuardLogStatement`, 6 hits in `OtpService`
- **Severity / OWASP:** Medium (PMD P2 mapping). Style only.
- **Location:** `OtpService.java` lines 182, 218, 293, 308, 323, 325.
- **Issue:** All six are SLF4J parameterised calls. There is no PII and no security relevance.

### L-1. Error codes reveal limited state
- **Severity:** Low. Enumeration.
- **Issue:**
  - "No active OTP" and "wrong OTP" both return OTP007, which is good: no existence oracle on the first guess.
  - OTP008 (429) shows that an active OTP exists with its attempts exhausted, but only after the caller has made the failing guesses itself.
  - OTP006 shows that the recipient reached today's send cap.
  - None of the messages echo the recipient or the service name.
- **Recommendation:** Acceptable for trusted app callers.

### L-2. Send-limit check-then-act race
- **Severity:** Low. Concurrent sends can both pass `max_attempts`.
- **Issue:** This matches legacy behaviour and is marked `ponytail:` in the code. It is bounded by the 5-recipient cap and the per-OTP validation limit.

### L-3. Rare Postgres deadlock between overlapping multi-recipient send and validate (G10)
- **Severity:** Low. It fails closed: a 500 with nothing sent or written.

### L-4. `apiCollection.http` contains the SHA-256 of `1234`
- **Severity:** Low. This is a public test value. It only matters together with H-1, which is now fixed.

### L-5. PMD P3 and Checkstyle style findings
- **Severity:** Low. Naming, final locals, literal duplication and so on in the in-scope files.
- **Pre-existing, not this feature's change:** the `log` `ConstantName` / PMD P1 `FieldNamingConventions` hit in `AlramzNotificationServiceApplication.java:22`.

## Focus-Area Verdicts (from the task)
1. **OTP generation.** Pass.
   - Uses `static SecureRandom` with `nextInt(10)` per digit. `nextInt(bound)` uses rejection sampling, so it is uniform with no modulo bias.
   - The plain OTP is never logged, never returned (the response is channels + correlationId) and never stored; only `sha256Hex(otp)` lower-case is stored.
   - The downstream `SmsServiceImpl` and `EmailServiceImpl` log only masked numbers, counts and codes, and persist no body.
2. **Brute force.** Pass after H-2.
   - `lockCandidates` uses `FOR UPDATE` inside one `TransactionTemplate`.
   - OTP007 is thrown after `execute()` returns, so the increment is committed.
   - OTP008 is thrown before any write.
   - A new send deactivates the old rows and inserts fresh rows (a new secret). It does **not** reset validation attempts on an existing OTP, and it increments only the daily send counter. So the guess budget per recipient per day is `max_attempts × limit`; H-2 had made that unbounded.
   - Expired rows (`expiry_date_time > :now`) and inactive or FAILED rows (`active='1'`) are excluded from matching.
   - A match deactivates only the matched row ids.
   - Postgres concurrency (G9) is still a manual check before go-live.
3. **Timing-safe compare.** Pass.
   - `MessageDigest.isEqual` on equal-length (64-byte) ASCII is constant-time.
   - The client supplies a hash it computed from a guess. Leaking stored-hash bytes would not help an attacker, because every probe consumes a validation attempt. Severity of any residual: Informational.
   - The case-insensitive compare (caller value is lower-cased; the server stores lower case) is as decided.
4. **Test-OTP flag.** Fixed (H-1).
   - prod and preprod are now hard-guarded.
   - dev/docker/test keep `${OTP_FIXED_TEST_OTP_ENABLED:true}`.
   - With the flag on, there is no SMS or email send.
5. **Enumeration and abuse.** See L-1 and M-2.
   - The 5-recipient cap is enforced after channel filtering.
   - Daily `MAX_ATTEMPTS` per recipient now holds (H-2).
   - Per-caller throttle: recommended (M-2), not built.
6. **SQL.** Pass.
   - All 13 `otp.*` keys use `:named` binds via `NamedParameterJdbcTemplate`, including the target-less `ON CONFLICT DO NOTHING` upsert, the `CASE` update and `FOR UPDATE`.
   - `IN (:recipients)` / `IN (:ids)` are expanded by Spring as bind lists.
   - There is no concatenation, and `SqlQueriesManager` does no text substitution.
7. **Logging and PII.** Pass, with M-3.
   - The outcome logs carry counts, codes, correlationId and `loggable()`-sanitised serviceName/language/invoker only.
   - Failure logs carry the class name or code only.
8. **Authorisation.** Pass.
   - All 3 methods carry `@JwtSecured(roles = "APP_NOTIFICATION")` (`OtpControllerTest#everyOperationRequiresAppNotificationRole`).
   - No `/api/v1/otp` path is in `permit-all-urls` in any of the 6 yml files (`ProfileSecurityConfigTest`).
9. **Template injection.** Pass.
   - Templates come from `otp_configuration` (ops-controlled), selected by `service_name`.
   - The only substitution is `%OTP%` with a digits-only value, so a caller cannot inject HTML.
   - The recipient is regex-validated before it reaches the `to` field.
   - `email-from` is checked against `app.email.allowed-senders` at startup (E23, case-insensitive), and `EmailServiceImpl` enforces the allow-list again at send time (MGE-V-007, which becomes a FAILED channel).

## Repo Convention Checks (step 5)
- **JWT:** Pass.
  - All 3 endpoints are secured, as the spec requires, with `APP_NOTIFICATION` (the `APP_<CONCERN>` convention).
  - None are in permit-all in any of the 6 profile yml files.
  - No hand-rolled `SecurityFilterChain` or `@EnableWebSecurity`.
- **Masking:** Pass, with residual M-3.
  - No hand-masking: mobiles go through the existing `SmsServiceImpl.mask`, and strings through `TemplatedEmailService.loggable`.
  - No new logged or audited field carries the OTP, the hash or a recipient.
  - The `otp`/`hashedOtp` masker keys are absent, but no payload logging or `@Loggable` is present.
- **Exception handling:** Pass.
  - One `handleOtp` arm in the single `@Order(0)` `GlobalExceptionHandler` (no second advice, so the `OnboardingExceptionHandler` pitfall is avoided).
  - `OtpException` carries only `code`, with no `HttpStatus`. The status is chosen by the handler's switch.
  - 5xx codes (OTP009, OTP010, default) go through `codedOrLogged`, which logs server-side and returns a null code and message. There is no stack trace, SQL or path in the response.
- **Datasource credentials:** Pass / not applicable.
  - This feature added no credential config.
  - `OtpRepository` uses `@Qualifier("middlewareNamedParameterJdbcTemplate")`, and `OtpService` uses `@Qualifier("middlewareTransactionManager")`. Both are correct, because the otp tables are on middleware.
- **SQL:** Pass. Bound parameters only (see focus area 6).
- **Liquibase:** Pass.
  - 004 is DDL only, with no seed data, secrets or PII.
  - `--rollback` drops the 3 tables in dependency order.
  - It is byte-identical across the 5 profile folders, and 001-003 are untouched.

## Scope Check (step 2)
- **What was compared:** per the coordinator's instruction, the working tree (`git status`, `git diff --stat`), not `main...HEAD`, because there are no commits yet.
- **Result:** every file in "Files Touched" is present.
  - Modified tracked files: `pom.xml`, `openapi.yaml`, `apiCollection.http`, `AlramzNotificationServiceApplication.java`, `GlobalExceptionHandler.java`, the 6 yml files.
  - The rest sit inside untracked directories (`repository/`, `db/`, `sql/`, `service/`, the test dirs). The files `TemplatedEmailService.java` and `SmsServiceImpl.java` are themselves untracked, because they were created by the earlier features. Git therefore cannot isolate this feature's one-line edits in them; those edits were confirmed by reading the files (`EMAIL` package-private at line 51; `isValidMobile` at line 169).
- **Other working-tree changes:** they belong to the two already-audited features (`notification-graph-gateway`, `notifications-sms-email`) or to the service scaffold (`mvnw`, `.mvn/`, `.gitignore`, `.gitattributes`, `DatasourceConfiguration`, the SMS clients and so on). None is attributable to sms-feature, so there is **no undisclosed scope creep**.
- **Files changed by this audit** (all already in scope):
  - `OtpService.java`
  - `OtpRepository.java`
  - `sql-queries.xml` (the `lockCandidates` select list only)
  - `application-preprod.yml` / `application-prod.yml` (comment only)
  - `OtpServiceTest.java`
  - `OtpRepositoryTest.java`
- **Not touched:** `infra/`, `.github/`, the starter, changesets.
- **Environment note:** the VS Code Java language server (redhat.java) was rebuilding `target/` at the same time as Maven. That caused transient "cannot find symbol" / missing `.class` errors on two `clean test` attempts and on the first `verify` run. The source was intact, and retries passed cleanly. Pause VS Code auto-build during CI-like local runs.

## Automated Tooling Summary
- **`spring-boot-security` skill (`scripts/scan.py`):**
  - 0 hits in this feature's files.
  - The module-wide hits are all outside scope: I5 SSRF and I8 XXE leads in `WaselneySmsClient` (earlier feature), the E3 catch-all handler (pre-existing arm), and the D1 repo-level note.
  - A manual review against AU4, C1, C5, I1, I7, L2, AP1, E1 and S6 gave the findings above.
- **Checkstyle** (in scope): `OtpService` NPath 2,808 (`parse`) and ClassFanOut 36, both Medium (M-8). `SmsServiceImpl`/`TemplatedEmailService` NPath and the Application `ConstantName` are pre-existing code, not this feature's lines.
- **PMD 7.10** (ran, `-Dpmd.skip=false`; read from `target/pmd.xml`):
  - **No `category/java/security.xml` hits** in any in-scope file.
  - P2 `GuardLogStatement` ×6 in `OtpService` → Medium (M-9).
  - Everything else in `OtpService`, `OtpRepository`, `OtpController` and `OtpProperties` is P3 style/design → Low (L-5).
- **SpotBugs 4.10.5 core** (ran, `-Dspotbugs.skip=false`; 53 module-wide):
  - In-scope: `OtpRepository.insertOtp` NP (M-5), `OtpService` `CT_CONSTRUCTOR_THROW` ×2 (M-6), and EI_EXPOSE ×8 on the generated `OtpSendResult`/`OtpConfigurationList` (M-7). No High-priority bug.
  - The `SmsServiceImpl.sendWithOutcome` NP is in pre-existing code (out of scope).
  - **This is plain SpotBugs with no FindSecBugs**, so security-pattern coverage is limited. That is a tooling gap, not a claim of full coverage. Recommend adding the FindSecBugs plugin to `alramz-common-bom`.
- **OWASP dependency-check:** **could not run.** `mvn org.owasp:dependency-check-maven:check` failed with "Error updating the NVD Data" (no NVD API key / feed access). This feature added no dependency (only `modelsToGenerate` entries).
- **Final gate:**
  - `mvn -pl services/alramz-notification-service -am clean test` → **exit 0, 260 tests, 0 failures/errors/skipped** (259 baseline + 1 new regression test).
  - The `verify` sweep afterwards also ran 260/260 green.

## Residual Risks & Recommendations
Before `05_pr` / go-live:
1. **M-2:** per-caller (JwtContext application) rate limit on `/api/v1/otp/**`. This is the strongest recommendation given C-2.
2. **M-3:** add `hashedOtp,hashedotp,otp,recipients` to `company.logging.masking.sensitive-keys`.
3. **M-1:** tell consuming apps that validate `recipients` must come from the account record, not from user input.
4. **G9:** a manual two-session psql check of concurrent validate under `FOR UPDATE`.
5. Confirm that no legacy-shaped `otp_*` tables already exist on the shared middleware DB; `CREATE TABLE IF NOT EXISTS` would hide them.
6. Ops: make sure `APP_OTP_FIXEDTESTOTPENABLED` / `OTP_FIXED_TEST_OTP_ENABLED` is never set in preprod/prod secrets. Startup would now fail, which is the intended fail-closed behaviour.

Later:
- **M-4:** HMAC instead of plain SHA-256.
- **M-5:** `requireNonNull` on the generated key.
- **M-7:** a spotbugs-exclude entry for generated models.
- **M-8:** split `parse`.
- Add FindSecBugs.
- Run dependency-check with an NVD API key.
- G15: retention for `otp_management` / `otp_attempts`.
