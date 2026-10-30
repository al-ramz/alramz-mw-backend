# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-10-26
- **Feature:** notifications-sms-email. It adds templated email (`POST /api/v1/email/template/send`), an SMS gateway (`POST /api/v1/sms/send`, `GET /api/v1/sms/report/{messageId}`) and the `INSIGHT.SMS_TABLE` dispatch job to the notification service.
- **Target Service(s):** alramz-notification-service
- **Files Audited:** 65 = 50 `target_files` + 15 `test_files` from `execution_plan.json`. That matches every file in implementation-report.md's Files Touched list, with the 5 profile folders expanded (5 masters and 15 changesets). For the files shared with feature 1 (`GlobalExceptionHandler`, `EmailController`, `RequestSizeLimitConfig`, the 6 yml files, `pom.xml`, `openapi.yaml`, `apiCollection.http`), only this feature's changes were audited. No scope-creep file was found (see Scope Check).
- **Audit Status:** **PASSED WITH FIXES.** This feature has no Critical findings. Its one High (H-1) is fixed. Two Medium log-hardening items were fixed alongside it. Remaining Medium/Low items are recorded below and none of them blocks. Some open items predate this feature or were already accepted: the dev `JWT_SECRET` baseline (linked to feature 1's C-1) and the accepted C-2 / shared `jwt_user` risks. They are recorded under "Existing and accepted findings" and are not counted against this feature.

## Severity Summary
| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 1 | 1 | 0 |
| Medium | 8 | 2 | 6 |
| Low | 9 | 0 | 9 |

## Detailed Findings & Remediation

### H-1. A placeholder at the start of a text template could turn the mail into unescaped HTML (FIXED)
- **Severity / OWASP category:** High / A05 Injection (skill I4: HTML injection into Al Ramz-branded mail)
- **Location:** `services/alramz-notification-service/src/main/java/com/alramz/service/TemplatedEmailService.java`, `render(...)` (originally line 212)
- **Issue:**
  - **Two checks that disagreed.** The service HTML-escaped placeholders only when the *stored* body started with `<`. The gateway (`EmailServiceImpl:278`) chooses `BodyType.HTML` from the *rendered* body.
  - **The bypass.** Take a text template that opens with a placeholder, such as `%1%, your statement is ready`, and a placeholder value of `<a href="https://evil.example">Click</a>`. The value was not escaped, the rendered body started with `<`, and Graph sent the result as HTML.
  - **Why it matters.** Placeholder values are often relayed from end users (names, references). Anyone holding an `APP_NOTIFICATION` token could therefore put arbitrary links and markup into mail sent from the allow-listed Al Ramz mailbox. Under accepted risk C-2, anyone can mint such a token. This defeats the stated purpose of spec decision 12 / section 573 ("blocks caller-supplied markup or links from being injected into Al Ramz-branded mail").
- **Fix applied:**
  - `render` now does a raw replace first. If the result would be treated as HTML (its stripped value starts with `<`), it re-renders with every placeholder `HtmlUtils.htmlEscape`d.
  - Behaviour is unchanged for real HTML templates (stored body starts with `<`) and for text templates whose output does not start with `<`.
  - An escaped value can never start with `<`, so the malicious case now goes out as TEXT showing the escaped markup.
  - The fix has one obvious shape, and it implements the spec's own stated intent, so no question was raised.
  - Regression test: `TemplatedEmailServiceTest#send_escapesPlaceholdersWhenALeadingPlaceholderWouldMakeATextBodyHtml`.

### M-1. Provider-supplied response text reached log lines and the logged SMS009 message (FIXED)
- **Severity / OWASP category:** Medium / A09 (skill L4 log injection; conditional L2 secret-in-log)
- **Location:**
  - `WaselneySmsClient.java` `send` (the first `|`-token of the response);
  - `EtisalatSmsClient.java` `submit` (`statusCode` from the response JSON);
  - both flow into `GlobalExceptionHandler.codedOrLogged`, which logs the SMS009 message at error level.
- **Issue:**
  - Waselney's "result code" was `tokens[0]` of whatever body came back, with no length or character limit. A response with no `|` is logged whole. That covers a proxy or WAF error page, a provider error that echoes the request line, or CR/LF.
  - The Waselney request line carries `username`/`password` in the query string, so an echoing error page would have written the password into both the warn log and the 502 error log.
  - Etisalat's `statusCode` (an arbitrary JSON value) had the same problem.
  - This is the one path that focus item 1 ("no log line or exception message ever contains them") did not cover.
- **Fix applied:**
  - New `SmsServiceImpl.providerCode(Object)` keeps a value only if it matches `^[A-Za-z0-9._-]{1,16}$`. Otherwise it returns `unexpected`, or `empty` for a missing value. Both clients now use it.
  - Real codes (`1702`, `400`) are unchanged, and the existing tests still pass.
  - Regression test: `WaselneySmsClientTest#unexpectedResponseBodyIsNeverEchoedIntoTheMessage`, which feeds a body echoing `password=secret` and CRLF.

### M-2. Caller-supplied `templateName`/`language` were logged raw (FIXED)
- **Severity / OWASP category:** Medium / A09 (skill L4 log injection)
- **Location:** `TemplatedEmailService.java`, the outcome log line in `send`'s `finally` block.
- **Issue:**
  - The line runs on every outcome, including EML001, before `language` is validated.
  - `templateName` has no `maxLength` in the contract. The console appender uses a plain `%msg%n` pattern, so CR/LF in either field could forge log lines in stdout, which k8s collects. Very long values were logged in full.
  - This was 03_implement's flag 4.
- **Fix applied:** new package-private `TemplatedEmailService.loggable(String)` keeps a value only if it matches `^[A-Za-z0-9 ._-]{1,100}$` and logs `<invalid>` otherwise. Test: `TemplatedEmailServiceTest#loggableDropsControlCharactersAndOverlongValues`.

### M-3. The per-recipient daily limit can be bypassed by reordering or re-casing recipients
- **Severity / OWASP category:** Medium / A06 (skill AP1)
- **Location:** `TemplatedEmailService.checkDailyLimit`, plus `email.log.countPerRecipient` (`to_email = :to`, exact string match).
- **Issue:** The count key is the joined `to` string exactly as the caller sent it. Each of these is a "new" recipient that resets the non-bulk `max_attempts` counter:
  - `a@x.com,b@x.com` vs `b@x.com,a@x.com`;
  - `User@x.com` vs `user@x.com`;
  - adding a second address.

  `invoker` also comes from the token's `application` claim, which C-2 lets a self-registrant choose. The limit is a business guard (the legacy behaved the same), not a security boundary.
- **Fix applied:** none (Medium). Recommendation: normalise before counting and storing (lower-case, sort, de-duplicate). If it is meant to be a real anti-abuse control, count per individual address.

### M-4. The attachment type check doesn't stop a misleading file extension for most types
- **Severity / OWASP category:** Medium / A05 (skill AP3)
- **Location:** `TemplatedEmailService.checkAttachments`.
- **Issue:**
  - The declared `contentType` code must be in the 9-code allow-list and in the template's `type_of_attachments`. The extension is checked only for MSWORD/MSEXCEL.
  - A caller can declare `PDF` with `fileName: "invoice.exe"` or `statement.html`. The file goes out with MIME `application/pdf` but the dangerous name, and many mail clients open by extension.
  - Content is never sniffed. The template's own count and size caps still apply.
- **Fix applied:** none (Medium). Recommendation: require the extension to match the code for every code (`jpg/jpeg`, `png`, `pdf`, `csv`, `txt/trn`). Optionally check the magic bytes for PDF/PNG/JPEG. Confirm this against the legacy contract first, because it can reject requests the legacy accepted.

### M-5. No rate limit or quota on `POST /api/v1/sms/send`
- **Severity / OWASP category:** Medium / A06 (skill AP1)
- **Location:** `SmsController.sendSms` / `SmsServiceImpl.sendWithOutcome`.
- **Issue:**
  - Each call can send up to 1,500 characters to 50 numbers in any country, under the "AL RAMZ" sender ID, with no per-invoker or per-day cap. That allows SMS pumping (toll fraud) and sender-ID phishing by any `APP_NOTIFICATION` token holder.
  - This does **not** re-open accepted risk C-2. It records that C-2's impact grows once live SMS is enabled: a self-registered token can now send bank-branded SMS.
  - Live sending is off by default (`SMS_LIVE_SEND_ENABLED=false`), so nothing is exposed until ops enables it.
- **Fix applied:** none (Medium, design decision). Recommendation before `SMS_LIVE_SEND_ENABLED=true`:
  - a per-invoker daily quota, or a gateway or APIM rate limit;
  - a country allow-list (for example `971`), if the business allows it.

### M-6. Checkstyle complexity in validation code (security-relevant paths)
- **Severity / OWASP category:** Medium / A06 (step 4 mapping: complexity → Medium)
- **Location:** Checkstyle `NPathComplexity` (max 200):
  - `SmsProperties.java:38` (11,264; the E44/E45 fail-fast checks);
  - `SmsServiceImpl.java:55` (9,216; input validation and routing);
  - `TemplatedEmailService.java:80` (294), `:137` (720) and `:164` (5,512): field and attachment validation.
- **Issue:** This is input-validation and fail-closed code, so the complexity makes it harder to audit. Each branch is covered by tests (98% line / branch coverage per the implementation report).
- **Fix applied:** none. Residual. Split the validation helpers in a later refactor.

### M-7. PMD priority-1/2 rules (non-security)
- **Severity / OWASP category:** Medium by the step 4 mapping (PMD P1-2), with no security impact.
- **Location:**
  - `GuardLogStatement` (P2) ×16: `SmsDispatchJob` ×7, `SmsServiceImpl` ×3, `TemplatedEmailService` ×3, `GlobalExceptionHandler:113`, `VeriPhoneClient:47`, `WaselneySmsClient:131`;
  - `FieldNamingConventions` (P1) ×1: Lombok `log` in `AlramzNotificationServiceApplication.java:21`.
- **Issue:** Performance and style only. With SLF4J parameterised logging, the guard is redundant.
- **Fix applied:** none. Residual.

### M-8. SpotBugs Normal-priority findings in in-scope classes
- **Severity / OWASP category:** Medium by mapping (SpotBugs Normal); all were reviewed as non-exploitable.
- **Location / review:**
  - `CT_CONSTRUCTOR_THROW`, `SmsDispatchJob.java:44,49-50`. This is the intended E44 fail-fast in a Spring singleton. There is no finalizer and no subclassing, so it is not an attack surface.
  - `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE`, `SmsServiceImpl.java:84`. A false positive: `Defaults`' compact constructor guarantees a non-null `expiryInSecs`.
  - `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` on `SmsService.Outcome`, `SmsProperties.Etisalat` (lists already immutable via `.toList()`), `EmailTemplateRepository`/`SmsTableRepository` (injected templates), and the generated `TemplatedEmailRequest`, `TemplateAttachment`, `SmsSendRequest`, `SmsSendResult` and `SmsReportResult`. These are DTOs and beans with no trust boundary.
  - `BX_UNBOXING_IMMEDIATELY_REBOXED`, `SmsProperties.java:113`. Cosmetic.
- **Fix applied:** none. Residual.

### L-1. `cipher.password: ${CIPHER_MASTER_KEY:UseMostSecretKey}` in dev/docker/test (focus item 4)
- **Severity / OWASP category:** Low / A04 (skill S1/C4, in its non-exploitable form)
- **Location:** `application-dev.yml:60`, `application-docker.yml:27`, `application-test.yml:27`.
- **Issue and decision:**
  - **The key protects nothing today.** These 3 profiles authenticate both datasources with `plainPassword` from env vars. `AbstractDataSourceAutoConfiguration.resolvePassword` (starter) returns `plainPassword` and never calls `PWProtector.decrypt`. The starter only needs the bean to exist (D-1). `PWProtector` has no other consumer in the starter (verified by grep).
  - **What the key would expose.** Knowing it reveals no secret in this service.
  - **Failing closed now** would need `CIPHER_MASTER_KEY` provisioned in the dev and test AKS namespaces before the next deploy, for no security gain.
  - **Decision:** do not fail closed now. A default is acceptable *only while* these profiles use `plainPassword`.
  - **Hard rule for the future:** the moment any dev/test datasource switches to `password` + `passwordVector`, the default must be removed (`${CIPHER_MASTER_KEY}`, as in preprod/prod). Otherwise those passwords would be encrypted under a publicly committed key.
  - The sibling services are worse. reference-data and reporting commit the literal `UseMostSecretKey` even in preprod/prod (out of scope; flagged for human review).
- **Fix applied:** none.

### L-2. Live send doesn't require `waselney.report-url`
- **Severity / OWASP category:** Low / A10
- **Location:** `SmsProperties` compact constructor (E45 checks); `WaselneySmsClient.report`.
- **Issue:** With `SMS_LIVE_SEND_ENABLED=true` and no `SMS_WASELNEY_REPORT_URL`, `UriComponentsBuilder.fromUriString(null)` throws `IllegalArgumentException`. The catch-all turns that into a 500 with a stack trace in the log, which contains no URL or secret. It fails closed, but late and noisily, which goes against the "fail fast at startup" intent of focus item 10.
- **Fix applied:** none. Recommendation: add `require(problems, waselney.reportUrl(), "app.sms.waselney.report-url")` in the live-send block.

### L-3. `email_log.ip_address` trusts `X-Forwarded-For`
- **Severity / OWASP category:** Low / A09
- **Location:** `TemplatedEmailController` → `LoggingHelper.extractClientIp` (starter).
- **Issue:** An authenticated caller can set this audit column to any value, up to 45 characters. The value is bound as a parameter, so this is not injection. It only affects audit integrity.
- **Fix applied:** none. Use it as an informational field only, or trust XFF only from the ingress.

### L-4. Provider responses are read into memory with no size cap
- **Severity / OWASP category:** Low / A06 (skill AP5)
- **Location:** `WaselneySmsClient.send`/`report` (`body(String.class)`), `EtisalatSmsClient`, `VeriPhoneClient` (`body(Map.class)`).
- **Issue:**
  - A hostile or broken provider could return a very large body. The read timeout (10 s) limits this only indirectly. The provider URLs come from config, so they are trusted.
  - XML entity expansion is **not** possible: DOCTYPE is disallowed, and `FEATURE_SECURE_PROCESSING` caps the remaining limits.
- **Fix applied:** none.

### L-5. Static `RestClient` follows GET redirects
- **Severity / OWASP category:** Low / A01 (skill I5, in its non-exploitable form)
- **Location:** `SmsServiceImpl.restClientBuilder` (`SimpleClientHttpRequestFactory`, `HttpURLConnection`).
- **Issue:**
  - `HttpURLConnection` follows same-protocol redirects for GET. If the configured Waselney host ever issued a 30x, the request would be replayed to the new location with `username`/`password` in its query string.
  - The scanner's I5 hits (`WaselneySmsClient:73,98`) are otherwise false positives. The base URLs come from config, `messageId` is regex-checked, and every query value is a strictly encoded URI variable.
- **Fix applied:** none. Optional hardening: override `prepareConnection` to call `setInstanceFollowRedirects(false)`.

### L-6. No automated proof that provider calls stay out of tracing
- **Severity / OWASP category:** Low / A09
- **Location:** `SmsServiceImpl.restClientBuilder`.
- **Issue:**
  - The claim checks out in code. The clients use the static `RestClient.builder()`, whose observation registry defaults to NOOP. They do not use the Boot `RestClient.Builder` bean, so they get no Boot customizers, no `ObservationRegistry` and no starter logging interceptor.
  - Nothing else instruments `HttpURLConnection`: there is no `RestClientCustomizer` in the starter, and no OpenTelemetry `-javaagent` in the Dockerfile, `service.yaml` or `k8s/`.
  - The starter's SQL `TracingQueryExecutionListener` records only `db.query.text` (statement text, not bind values), so SMS bodies and addresses never reach spans.
  - Still, no test pins this down, and no running pod was inspected. A future change (adding the OTel java agent, or switching to the Boot builder) would silently leak the Waselney password and the VeriPhone key into Jaeger.
- **Fix applied:** none. Recommendation:
  - check a running dev pod's Jaeger traces once (03_implement's flag 1);
  - add a unit test asserting that `restClientBuilder(...)` builds without an observation registry;
  - treat "no OTel java agent" as a documented constraint for this service.

### L-7. The SMS dispatch job's Oracle claim is unproven against real lock semantics
- **Severity / OWASP category:** Low / A04-adjacent (integrity: double-send risk)
- **Location:** `sql-queries.xml` `sms.claim.select`.
- **Issue:** All SQL is parameterised. The claim's only inputs are config integers (`pendingStatus`, `batchSize`), so no request data can steer which rows are claimed. Double-claiming across pods depends on Oracle `FOR UPDATE SKIP LOCKED` behaviour, which no test here can prove (A-4).
- **Fix applied:** none. Before `SMS_DISPATCH_ENABLED=true`, run the BROKQA two-session check as already required.

### L-8. Checkstyle and PMD style findings in in-scope files
- **Severity / OWASP category:** Low
- **Location:**
  - Checkstyle `ConstantName` at `AlramzNotificationServiceApplication.java:21` (Lombok `log`);
  - PMD priority-3 code-style and design rules across the in-scope classes (`OnlyOneReturn`, `LongVariable`, `LawOfDemeter`, `CommentDefaultAccessModifier`, and so on).
  - `PreserveStackTrace` (TemplatedEmailService ×5, Etisalat ×1, Waselney ×1) is **intentional**: provider and gateway exceptions are deliberately not chained because they carry credential-bearing URLs.
- **Fix applied:** none.

### L-9. SpotBugs Low: `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION`
- **Severity / OWASP category:** Low
- **Location:** `RequestSizeLimitConfig.java:52`.
- **Issue:** Intentional. The interceptor throws a typed exception that the advice maps to 413.
- **Fix applied:** none.

### Existing and accepted findings (recorded, not counted above)
- **E-1. Baseline: dev `JWT_SECRET` fallback (linked to feature 1's C-1).**
  - `application-dev.yml:97` contains `${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}`, the user's uncommitted edit.
  - In the deployed dev namespace, a missing secret silently falls back to a key committed in the repo. Anyone can then forge an `APP_NOTIFICATION` token for every endpoint, including this feature's three. That is the same class of issue as C-1.
  - `ProfileSecurityConfigTest[application-dev.yml]` fails on it.
  - Per instruction it was **not changed**. It is awaiting the user's decision. Must be resolved before merge.
- **E-2. C-2 (accepted).** The starter's anonymous `/api/auth/register` accepts client-chosen roles. Recorded as accepted. See M-5 for how this feature raises its impact once live SMS is on, and M-3 for how it affects `invoker`.
- **E-3. Shared Postgres `jwt_user` (accepted, A-5).** Recorded as accepted.

## Repo Convention Checks (step 5)
- **JWT: public vs secured.** PASS. All 3 new operations carry `@JwtSecured(roles = "APP_NOTIFICATION")`:
  - `TemplatedEmailController.java:25`;
  - `SmsController.java:35,47`.

  None is public, matching the spec.
- **JWT: permit-all lists.** PASS for this feature. Every one of the 6 yml files lists only `/api/v1/info`. dev also has `/actuator/prometheus`, an observability scaffold edit outside this feature. `ProfileSecurityConfigTest` asserts that none of the new paths or wildcards is permit-all.
- **JWT: no hand-rolled security.** PASS. There is no `SecurityFilterChain` or `@EnableWebSecurity`.
- **JWT: role convention.** PASS. The `APP_<SERVICE_CONCERN>` convention gives `APP_NOTIFICATION`.
- **Masking.**
  - PASS by design. No `@Loggable` on the new controllers; it would log bodies.
  - Provider clients have no logging interceptor.
  - Numbers are masked to the last 4 digits.
  - Message bodies, placeholders, addresses and attachment content are never logged; log-capture tests cover this.
  - `email_log` stores a SHA-256 of the body, not the body.
  - No new field relies on hand masking. No `sensitive-keys` addition is needed, because no request or response logging touches these endpoints. The `to`/`cc`/`bcc` addresses are stored in plain text in `email_log`, by spec, the same as legacy.
- **Exception handling.** PASS.
  - The only advice is `@Order(0)` `GlobalExceptionHandler`; no second advice was added.
  - The 5xx arms return null code and message.
  - `SmsException`/`TemplatedEmailException` carry only a code (no `HttpStatus`) and have no cause constructor.
  - The 5xx log line contains the code and message. Messages are built from constants plus, since M-1, a sanitised provider code.
  - The catch-all stack trace is logged only for unknown codes (500) and is never returned to the caller.
- **Datasource credentials.** PASS.
  - No `plainPassword` in preprod/prod. Those profiles use `password` + `passwordVector` + `cipher.password: ${CIPHER_MASTER_KEY}` with no default.
  - dev/docker/test `plainPassword` comes from env with no committed default. The brok defaults are empty because brok is disabled.
  - `cipher.password` default: see L-1.
  - `@Qualifier`: `middlewareNamedParameterJdbcTemplate` (EmailTemplateRepository); `brokNamedParameterJdbcTemplate` + `brokTransactionManager` (SmsTableRepository). Both are correct.
  - `DatasourceConfiguration` re-exports middleware as `@Primary`.
- **SQL.** PASS.
  - All 8 statements in `sql-queries.xml` use `:named` binds through `NamedParameterJdbcTemplate`.
  - Nothing is concatenated; the XML values are static.
  - SQL tracing records statement text only.
- **Liquibase.** PASS.
  - The only seed is the `schedule_job` row in `003`, with no secret or PII.
  - `002` has `--rollback DROP TABLE IF EXISTS ...` and `003` has `--rollback DELETE ...`.
  - `001` has no rollback, on purpose (shared table).
  - The 15 files are byte-identical per name (md5: 3 hashes × 5).

## Focus-item answers (caller's list)
1. **Credentials in URLs.**
   - Confirmed: no log line, exception message, `SmsException` cause, audit row or SQL span carries the Waselney password or VeriPhone key.
   - The static builder bypasses Boot's observation; no agent or interceptor exists. See L-6 for the missing runtime proof.
   - One gap was fixed: provider response text echoed into logs (M-1).
   - `SmsProperties` `toString` redacts secrets. Actuator exposes only `health,info,prometheus`, so `configprops`/`env` are not exposed.
2. **XXE.** Hardened:
   - `disallow-doctype-decl`;
   - external general and parameter entities off;
   - `FEATURE_SECURE_PROCESSING`;
   - XInclude off, `expandEntityReferences=false`;
   - a silent error handler.

   Without a DTD, entity expansion (billion laughs) is impossible. Covered by `e35_doctypeIsRejected`.
3. **Log injection and PII.**
   - Fixed for `templateName`/`language` (M-2) and provider codes (M-1).
   - Numbers are masked. Bodies, placeholders, attachments and template text are never logged.
   - The dispatch job logs serial number, provider, masked number and code only.
4. **Committed key default.** Decision: no fail-closed needed now. The key decrypts nothing in dev/docker/test. It must be removed when those profiles adopt encrypted passwords (L-1).
5. **Datasource credentials.** PASS (see above).
6. **SQL.** PASS. The claim's only parameters are config integers. No request input reaches any `INSIGHT.SMS_TABLE` statement.
7. **Templates.**
   - Escaping bypass fixed (H-1).
   - Attachment checks: count, the 9-code allow-list, the template type list, a decoded-size cap and the Content-Length 413 are present. Weakness: extension vs type (M-4).
   - `FROM_EMAIL` is enforced: `template.fromEmail` → `EmailServiceImpl` allowed-senders → MGE-V-007 → EML010 (500).
8. **Report path.**
   - PASS. `messageId` must match `^[A-Za-z0-9-]{1,65}$` (controller, plus the `openapi.yaml` pattern) before it reaches the client.
   - The client then builds the URL with an encoded `{id}` URI variable, so `&`, `=` and `#` cannot reach the Waselney query.
   - Covered by test E33 (`abc&pwd=x` → SMS007).
9. **Authorisation.** PASS (see JWT above).
10. **Safe defaults.**
    - PASS. `live-send-enabled`, `dispatch.enabled` and `brok.enabled` all default to `false`.
    - E45 fails startup when live send lacks Waselney credentials (and Etisalat or VeriPhone credentials when applicable).
    - E44 fails startup when dispatch lacks brok or valid statuses.
    - The disabled job never touches the repository.
    - Gap: report-url is not required (L-2).

## Scope Check (step 2)
- **Method.** No commits exist yet, so this used `git status --porcelain -uall` and `git diff --stat` / `--name-only` on the working tree, as instructed. Every changed or untracked path under `services/alramz-notification-service` was compared with both features' `execution_plan.json` file lists.
- **Result.**
  - All 65 planned feature 2 files are present, and every changed file is accounted for.
  - The only paths in neither plan are the scaffold edits the caller already described: `ApplicationController.java` (modified), `src/test/java/com/alramz/AppTest.java` (deleted), `.gitattributes`, `.gitignore`, `.mvn/wrapper/maven-wrapper.properties`, `mvnw` and `mvnw.cmd`.
  - No undisclosed scope creep was attributable to this feature.
- **This audit's own edits** stayed inside the feature's files:
  - `TemplatedEmailService.java`, `SmsServiceImpl.java`, `WaselneySmsClient.java`, `EtisalatSmsClient.java`;
  - tests `TemplatedEmailServiceTest.java` and `WaselneySmsClientTest.java`.

## Automated Tooling Summary
- **`spring-boot-security` skill (`scripts/scan.py`):** 2 CRITICAL leads, 1 IMPORTANT, 2 SUGGESTION.
  - I5 SSRF at `WaselneySmsClient:73,98`: false positive. The URLs come from config and the inputs are encoded and validated; see L-5.
  - I8 XXE at `WaselneySmsClient:120`: confirmed hardened.
  - E3 catch-all at `GlobalExceptionHandler:103`: `AccessDeniedException` has an explicit 403 arm (feature 1).
  - D1 (no dependency scanner in the build): see below.
  - Manual review against the skill's taxonomy found H-1 (I4), M-1/M-2 (L4/L2), M-3/M-5 (AP1) and M-4 (AP3). None of these has a regex the scanner could match.
- **Checkstyle (in-scope only):**
  - `NPathComplexity` ×5 (Medium, M-6) at `SmsProperties:38`, `SmsServiceImpl:55` and `TemplatedEmailService:80,137,164`;
  - `ConstantName` ×1 (Low, L-8).
  - The `EmailServiceImpl:203` NPath hit belongs to feature 1 and is out of scope.
- **PMD 7.10.0** (`-Dpmd.skip=false`; 198 violations module-wide; filtered from `target/pmd.xml` to the in-scope files):
  - **Zero `category/java/security.xml` hits.**
  - P1-2: `GuardLogStatement` ×16 and `FieldNamingConventions` ×1 (Medium by mapping, M-7).
  - The rest are P3 Code Style / Design / Best Practices (Low, L-8). `PreserveStackTrace` is intentional.
  - No errorprone or bestpractices hit indicates a correctness defect.
- **SpotBugs 4.10.5.0** (`-Dspotbugs.skip=false`; 41 bugs module-wide):
  - In scope: Normal priority `CT_CONSTRUCTOR_THROW`, `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` (false positive), `EI_EXPOSE_REP/REP2` and `BX_UNBOXING_IMMEDIATELY_REBOXED` (Medium by mapping, M-8). Low `THROWS_METHOD_THROWS_RUNTIMEEXCEPTION` (L-9). None was High.
  - The `EmailAttachment`/`EmailRequest`/`ApiUtil` hits are feature 1 or generated code, out of scope.
  - **Plain SpotBugs core, no FindSecBugs**, so security bug patterns (hard-coded keys, XXE, URL credential leaks, log injection) are not covered. That is a tooling gap, not a claim of coverage. Adding FindSecBugs is recommended.
  - Note: PMD and SpotBugs are bound to `validate`. Without `clean`, the sweep analysed the classes from the implementation's final build. The post-fix code differs only in the 4 edited methods.
- **OWASP dependency-check:** **could not run.** `mvn org.owasp:dependency-check-maven:check` (13.0.0) failed with `NvdApiException: Invalid API Key, length of 0`. No NVD API key is available in this environment, so no CVE results exist. New dependencies to scan once a key is available:
  - `org.postgresql:postgresql`;
  - `com.oracle.database.jdbc:ojdbc11`;
  - `com.oracle.database.nls:orai18n`;
  - `spring-boot-starter-liquibase`.

## Test Results (step 8)
- `mvn -pl services/alramz-notification-service -am clean test -Dtest='!ProfileSecurityConfigTest' -Dsurefire.failIfNoSpecifiedTests=false` → **exit 0**: 206 run, 0 failures, 0 errors. That is 203 before, plus the 3 new regression tests.
- Full run: `mvn -pl services/alramz-notification-service -am clean test` → **exit 1**: 212 run, 1 failure. The failure is **only** the baseline `ProfileSecurityConfigTest.jwtSecretIsRequiredAndSendIsNotPublic[2]` (`expected "${JWT_SECRET}"`, dev profile; see E-1).
- One earlier full run reported 21 failures and 81 errors: `NoSuchMethod`, `NoClassDefFoundError` and "Unresolved compilation problems". Those messages come from an IDE compiler writing into `target/` on the OneDrive-synced tree at the same time. This matches the implementation report's run #8. An identical re-run with no code change gave the result above.

## Residual Risks & Recommendations
- **Before merge (human):** decide E-1, the dev `JWT_SECRET` fallback. Today the user's uncommitted line leaves every new endpoint forgeable in the dev namespace whenever `JWT_SECRET` is unset.
- **Before `SMS_LIVE_SEND_ENABLED=true`:**
  - M-5: add a per-invoker quota or a gateway rate limit, plus a country allow-list. This also matters because of C-2.
  - L-2: require `report-url`.
  - L-6: check Jaeger once for `http.client` spans to Waselney or VeriPhone.
  - Optionally L-5: disable redirect following.
- **Before `SMS_DISPATCH_ENABLED=true`:** L-7, the BROKQA two-session claim check.
- **Follow-ups (non-blocking):**
  - M-3: normalise recipients for the daily limit.
  - M-4: require the extension to match the type for every attachment code.
  - M-6: reduce the complexity of the validation code.
  - L-1: remove the `cipher.password` default as soon as any dev/test datasource uses `password` + `passwordVector`.
  - L-3: decide whether XFF is trustworthy.
  - Add FindSecBugs to `alramz-common-bom`.
  - Obtain an NVD API key for dependency-check.
- **Out of scope, flagged for human review:**
  - reference-data-service and reporting-management-service commit the literal `cipher.password: UseMostSecretKey` in **preprod/prod**.
  - `apiCollection.http` contains a real `@alramz.ae` recipient address in the user's own blocks (feature 1 / D-7).
