# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-10-26
- **Feature:** notification-graph-gateway: harden `alramz-notification-service` into the shared, JWT-secured Microsoft Graph email gateway. It adds multi-recipient send, a sender allow-list, an attachment size cap, the GenericResponse envelope and 429-only Graph retry.
- **Target Service(s):** `alramz-notification-service`
- **Files Audited:** 24 from `implementation-report.md` Files Touched: 12 source/contract/pom files including the 2 deleted models, 6 profile YAMLs, `apiCollection.http` and 5 test classes. `git diff` found no scope-creep files. The remediation added 1 file (`ProfileSecurityConfigTest.java`).
- **Audit Status:** **ACTION REQUIRED**. One Critical finding (C-2) is blocking and unresolved. The fix belongs in the shared `alramz-api-starter`, and `AskUserQuestion` was not available in this invocation. All other Critical/High findings are fixed.

## Severity Summary
| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 2 | 1 | 1 (C-2, blocking: needs a decision) |
| High | 0 | 0 | 0 |
| Medium | 5 | 0 | 5 (residual, recorded) |
| Low | 6 | 0 | 6 (residual, recorded) |

## Detailed Findings & Remediation

### C-1. The service validated JWTs with the starter's public default HMAC key (FIXED)
- **Severity / OWASP category:** Critical. A04 Cryptographic Failures / A07 Authentication Failures. Skill IDs S1/C4 (hardcoded key) and AU1.
- **Location:** `services/alramz-notification-service/src/main/resources/application.yml` and `application-{dev,docker,test,preprod,prod}.yml`, under `company.jwt`. The fallback value is in `alramz-api-starter/.../jwt/config/JwtProperties.java:19`.
- **Issue:** No profile of this service set `company.jwt.secret`. `JwtAuthFilter` therefore verified tokens with the starter's literal default `defaultSecretKeyChangeMeInProduction1234567890`, and that value is in source. Nothing in this repo injects `COMPANY_JWT_SECRET` (searched `.github/` and the service resources). This feature's main new control is `@JwtSecured(roles = "APP_NOTIFICATION")` on `/api/v1/email/send`. Anyone could defeat it by signing their own HS256 token with `roles: ["APP_NOTIFICATION"]` and then sending arbitrary mail as `notification@alramz.ae` (prod: `cm@alramz.ae`) through the real Graph tenant. That is phishing from a corporate mailbox. `dev` and `test` are deployed AKS namespaces (`.github/config/environments.json`) with real Graph credentials, so they were exposed too.
- **Fix applied:** Added `secret: ${JWT_SECRET}` under `company.jwt` in all 6 profiles, with **no default**. This uses the env-var name that sibling services already use (`reference-data-service`, `onboarding-service`). It follows the same no-default pattern as `client-secret: ${AZURE_CLIENT_SECRET}` in the same files. An unset secret now fails startup instead of silently using a public key, and a key shorter than 256 bits also fails fast in `Keys.hmacShaKeyFor`. New regression check `src/test/java/com/alramz/config/ProfileSecurityConfigTest.java` asserts two things for every profile: `company.jwt.secret == "${JWT_SECRET}"`, and `/api/v1/email/send` is not in `permit-all-urls`.
- **Deployment impact (needs ops action before `05_pr` merges):** every environment running this service must now provide `JWT_SECRET`. It must be the same key that signs the callers' tokens, or callers' tokens will be rejected. Without it the pod will not start. I did not treat this as a blocking design gap, because a startup failure is loud and cheap to reverse, and the shape follows existing repo precedent.
- **Out-of-scope note (not fixed):** sibling services use `${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}`, which falls back to the public key even in `preprod`/`prod`. That is the same Critical pattern in files this feature didn't touch. Fix it separately.

### C-2. Anonymous self-registration with caller-chosen roles mints a valid `APP_NOTIFICATION` token (UNRESOLVED: ACTION REQUIRED)
- **Severity / OWASP category:** Critical. A01 Broken Access Control. Skill ID AZ5 (privilege escalation via client-supplied role).
- **Location:** `alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java:87-89` (`registerUrl` is always `permitAll()`), `.../jwt/controller/AuthController.java:27-37` (`RegisterRequest.roles` is taken from the request body) and `.../jwt/service/AuthService.java:82-98` (it saves those roles and issues a token carrying them). The service-side trigger is that every `alramz-notification-service` profile defaults to `jdbc:h2:mem:notificationsdb`. The module ships `spring-boot-starter-data-jpa` + H2 with no schema tool, so Spring Boot's embedded-DB default `ddl-auto=create-drop` creates the `users` table.
- **Issue:** An unauthenticated caller can `POST /api/auth/register` with `{"consumer":"x","email":"x@x","consumerPassword":"x","application":"x","environment":"x","roles":["APP_NOTIFICATION"]}`. The response is a token signed with the service's own key, and it passes `@JwtSecured(roles="APP_NOTIFICATION")` on `/api/v1/email/send`. That bypasses this feature's auth requirement completely, even after C-1's fix. After C-1, these tokens are signed with the real `JWT_SECRET`. If that key is shared with other services, the minted token is valid there too. Those services expose the same starter endpoint, so the starter-wide problem already exists independently of this feature. The finding also holds if `SPRING_DATASOURCE_URL` points at a real database that has the starter's user tables.
- **Why it is blocking (all three criteria met):**
  1. There are several materially different valid fixes, and neither spec, plan nor an existing repo pattern settles which one to use.
  2. Each fix changes production behavior or touches the shared `alramz-api-starter`, which is outside this feature's scope and used by every service.
  3. Choosing wrong either leaves a silent escalation path or breaks token issuance that other services may rely on, which is hard to detect.
- **Why it is not fixed:** this stage's contract requires `AskUserQuestion` for this kind of gap, and the tool is not available in this invocation. Per the fallback rule it is documented and left unfixed, not guessed.
- **Options to put to the user (recommended first):**
  1. **Starter fix: stop trusting client-supplied roles.** `AuthService.register` assigns no roles (or a fixed minimal default). Roles are granted only through an admin/provisioning path or seeded data. This fixes every service at once. Any client that currently self-assigns roles will break.
  2. **Starter fix: make registration opt-in.** Add a `company.jwt.registration-enabled` property (default `false`). When it is false, skip the `registerUrl` `permitAll()` and do not map `/api/auth/register`. Enable it only on the one service that is meant to be the token issuer.
  3. **Service-local fix (in scope, partial):** exclude `JwtAutoConfiguration`'s `AuthController` from this service, or disable JPA so no user store exists here. Keep only `JwtAuthFilter` + `JwtSecuredAspect` validation, so the notification service never issues tokens. Other services are not protected.
  4. **Network-level fix:** block `/api/auth/**` to this service at the ingress/gateway (infra, outside this repo's `k8s/`). Nothing changes in code, and the endpoint remains reachable from inside the cluster.
- **Question still needing an answer:** *Which of options 1-4 should be applied to stop anonymous `/api/auth/register` calls from minting tokens with caller-chosen roles (including `APP_NOTIFICATION`)?*

### M-1. Chunked request bodies bypass the size cap; the role check runs after the body is parsed
- **Severity / OWASP category:** Medium. A06 Insecure Design. Skill ID AP5 (no request body / parser limit).
- **Location:** `src/main/java/com/alramz/config/RequestSizeLimitConfig.java:32-38`.
- **Issue:** The interceptor only checks `getContentLengthLong()`. A chunked request (length `-1`) passes, and Jackson then buffers and Base64-decodes the whole body before `EmailServiceImpl`'s decoded-total check (MGE-V-008). With request logging on by default, the starter's `ContentCachingRequestWrapper` also caches the body. Tomcat has no default byte limit for a JSON body, and this repo has no ingress/k8s manifests that would add one. `@JwtSecured` is an AOP `@Before` check, so it runs after argument binding. The filter chain does block unauthenticated callers, but **any** valid token, of any role, can force this parsing.
- **Judgement:** this is not Critical/High. It needs a valid signed token, which after C-1 means a token from the real key (C-2 aside). The impact is memory pressure or availability, not data exposure. Non-chunked oversized bodies are rejected before binding as designed. The fix is not one-shape. Rejecting chunked requests (411/413) changes the API for clients that stream. A bounded `InputStream` wrapper filter or a gateway body-size limit are the alternatives.
- **Recommendation:** add a small `OncePerRequestFilter` on `/api/v1/email/send` that wraps the input stream and fails with MGE-V-008 after `maxRequestBytes`. Alternatively, set a body-size limit at the ingress/gateway. The existing `ponytail:` comment already names this upgrade path.

### M-2. `validate()` complexity in the input-validation path
- **Severity / OWASP category:** Medium (Checkstyle complexity mapping). A06.
- **Location:** `src/main/java/com/alramz/service/impl/EmailServiceImpl.java:203` (Checkstyle `NPathComplexity` 20,480 > 200; PMD `CyclomaticComplexity`/`CognitiveComplexity`/`NPathComplexity` on the same method).
- **Issue:** this is the only validation gate for HTTP and in-process callers, and it is hard to audit at this size. JaCoCo shows 100% line and branch coverage (92/92 branches), and I re-read the method by hand without finding a bypass (see the allow-list check below). Recorded as residual risk, not blocking.
- **Recommendation:** split it into `requireFields` / `requireAddresses` / `requireLimits` / `requireAllowedSender` helpers in a later change.

### M-3. PMD priority-2 findings in `EmailServiceImpl` (`GuardLogStatement` x4)
- **Severity / OWASP category:** Medium (PMD priority 2 maps to Medium). Not security-relevant: parameterised SLF4J calls with cheap arguments.
- **Location:** `EmailServiceImpl.java:94` and 3 further logger calls in the same class.
- **Recommendation:** add `log.isXxxEnabled()` guards or a PMD suppression, to match the house style in `GraphConfig`.

### M-4. SpotBugs `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` on the generated models (x7)
- **Severity / OWASP category:** Medium (SpotBugs Normal maps to Medium). A08 (weak).
- **Location:** generated `com.alramz.model.EmailAttachment` (`content` byte[]: getter, setter, fluent setter, constructor) and `com.alramz.model.EmailRequest` (`attachments` list). These are generated from the in-scope `specs/openapi.yaml`.
- **Issue:** these are request-scoped DTOs that are never shared across threads or cached, so there is no realistic exploit. They are generated code and must not be hand-edited (`openapi-contract-first-controllers.md`).
- **Recommendation:** add a `spotbugs-exclude.xml` entry for `com.alramz.model.*` EI_EXPOSE_REP*, as other generated packages do.

### M-5. No rate limit on the email-sending endpoint, and no recipient-count cap
- **Severity / OWASP category:** Medium. A06. Skill ID AP1 (email/SMS sending endpoint without rate limiting).
- **Location:** `EmailController.sendEmail` / `EmailServiceImpl.validate`.
- **Issue:** an authorised caller can send without limit until Graph throttles (429, MGE-B-002). The number of comma-separated recipients is bounded only by request size. Exchange Online rejects more than 500 recipients with a 400, which maps to MGE-B-003, so the blast radius per message is capped upstream. The spec scoped rate limiting out.
- **Recommendation:** add a per-invoker (JWT `application` claim) limit at the gateway or with Bucket4j, plus an explicit total-recipient cap such as 100, if product agrees.

### L-1. A missing JWT probably returns 403, not the contract's 401, and no test covers it
- **Severity / OWASP category:** Low. A07 (contract accuracy, not a bypass).
- **Location:** starter `JwtAutoConfiguration.jwtSecurityFilterChain` (no `authenticationEntryPoint`, so Spring Security falls back to `Http403ForbiddenEntryPoint`). Contract: `specs/openapi.yaml` declares 401.
- **Issue:** enforcement is in place. Spring Security's `anyRequest().authenticated()` rejects the request before the dispatcher, the path is absent from `permit-all-urls` in all 6 profiles, and `ProfileSecurityConfigTest` now guards that. The status code is likely 403 with an empty body, not 401. I could not confirm this here because no context/MockMvc test exists, a gap 03_implement also acknowledged.
- **Recommendation:** add one `@SpringBootTest`/MockMvc test for no-token, wrong-role and right-role. Configure a 401 entry point in the starter, which is shared.

### L-2. The Graph SDK `DefaultLogger` logs the request line and headers on Graph failures
- **Severity / OWASP category:** Low. A09.
- **Location:** inside `msgraph-sdk-java-core` `CoreHttpProvider` (not our code), triggered from `EmailServiceImpl.post`.
- **Issue:** on any Graph error the SDK logs `"Graph service exception " + message` through JUL, which the Spring Boot bridge routes into our logs. In non-verbose mode the message contains:
  - the method;
  - the URL `/users/{from}/sendMail` (the allow-listed service mailbox, not end-user PII);
  - the `IHttpRequest` headers, truncated;
  - the body replaced by `[...]`.
  The bearer token is attached later, by the OkHttp `AuthenticationHandler` interceptor, so it is not in the `IHttpRequest` header list that the exception renders. 03_implement's jar inspection also reports `Authorization` redaction. That inspection was not repeated here, because this stage's Bash scope excludes unpacking jars. Our own code never logs, traces or chains a `GraphServiceException` (confirmed by reading `EmailServiceImpl.send`/`mapGraphFailure`).
- **Recommendation:** optionally set `com.microsoft.graph` JUL/logback level to `OFF` or `WARN` without the message. Otherwise accept.

### L-3. MGE-B-004 logs full stack traces
- **Severity / OWASP category:** Low. A09.
- **Location:** `EmailServiceImpl.java:127-129`, `GlobalExceptionHandler.java:78`.
- **Issue:** the causes reaching this path are `ClientException` ("Error during http request", wrapping `IOException`), azure-identity `ClientAuthenticationException` (AAD error text, no secret) and programming errors. None of them carries request content or tokens. `GraphServiceException` is caught first and never passed as a throwable. The residual case is a third-party `RuntimeException` that wraps a `GraphServiceException` as its cause. The rendered cause chain would then include the URL and truncated headers described in L-2. No such wrapping exists on the synchronous `post()` path.
- **Recommendation:** none required. Re-check if the code moves to the async SDK API, which wraps in `CompletionException`.

### L-4. Oversize/malformed bodies are rejected before the role check
- **Severity / OWASP category:** Low. A01 (information ordering).
- **Location:** `RequestSizeLimitConfig` (413) and `GlobalExceptionHandler.handleNotReadable` (400), both of which run before the `@JwtSecured` aspect.
- **Issue:** a token without `APP_NOTIFICATION` receives 413/400 instead of 403 for an oversized or malformed body. Nothing sensitive leaks: the messages are fixed strings.

### L-5. Remaining PMD priority-3 and Checkstyle style findings (in-scope files)
- **Severity / OWASP category:** Low. Code style.
- **Location:**
  - `EmailServiceImpl.java`: 29 findings (LawOfDemeter, OnlyOneReturn, AvoidLiteralsInIfCondition, ShortVariable, GodClass/CouplingBetweenObjects/ExcessiveImports);
  - `GraphConfig.java`: 7;
  - `GlobalExceptionHandler.java`: 5;
  - `EmailProperties.java`: 2;
  - `RequestSizeLimitConfig.java`: 2.
- **Note:** `EmailProperties.java:16 UnusedAssignment` is a false positive. Reassigning a parameter inside a compact record constructor is how the normalised list gets stored.

### L-6. Raw Graph access token logged at INFO by earlier deployments (operational)
- **Severity / OWASP category:** Low (operational). A09.
- **Location:** the previous `GraphConfig` (lines 47-50 before this change) logged `Access Token: {}` at startup. The code fix is confirmed: `git grep` finds no `getToken(`/`Access Token` in `src/main`, and the startup log during tests shows only the two "Configuring/configured" lines.
- **Issue:** logs already shipped from earlier deployments (Seq, the OTLP log exporter, container stdout) may contain Graph application access tokens. Each token expires after about 60-90 minutes, but the logs persist.
- **Recommendation (no code change):** purge or restrict access to those historical log entries in Seq/OTLP storage for every environment that ran the old build. Consider rotating `AZURE_CLIENT_SECRET` if logs were broadly accessible. Review who could read them during the exposure window.

## Repo Convention Checks (step 5)
- **JWT: public-vs-secured matches the spec.** PASS. Secured, `@JwtSecured(roles = "APP_NOTIFICATION")` (`EmailController.java:26`).
- **JWT: permit-all in every profile.** PASS. `/api/v1/email/send` is removed from all 6 profiles (git diff) and guarded by `ProfileSecurityConfigTest`.
- **JWT: no hand-rolled `SecurityFilterChain`/`@EnableWebSecurity`.** PASS.
- **JWT: role naming follows `APP_<SERVICE_CONCERN>`.** PASS (`APP_NOTIFICATION`).
- **JWT: signing key.** FAIL before remediation (C-1, public default key), PASS after the fix. Token issuance is still open (C-2, unresolved).
- **Masking.** PASS. No new field goes through request/response logging: `include-payload`/`include-headers` stay at the starter's `false` defaults and no profile overrides them. `@Loggable` is deliberately omitted. Service logs carry only correlationId, invoker, `from`, counts, body type, Graph status/code and the MGE code; a logback `ListAppender` test asserts that body, subject, addresses and content are absent. Residual: if anyone ever enables `include-payload` here, `body`/`to`/`cc`/`bcc`/`content` are not in `company.logging.masking.sensitive-keys`.
- **Exception handling.** PASS.
  - No stack trace, SQL, URL or Graph message reaches a response. Backend errors return `errorCode`/`errorMsg` null, and validation messages are fixed strings.
  - `EmailServiceException` carries a code, not an `HttpStatus`.
  - `@Order(0)` on the only advice in the service beats the starter's `LoggingExceptionHandler` (LOWEST_PRECEDENCE). `AccessDeniedException` has an explicit handler, so the catch-all does not turn 403 into 500, which dismisses the skill's E3 lead.
  - Interceptor exceptions are handled because the handler bean is in `com.alramz.controllers`.
- **Datasource credentials.** N/A. The feature added no datasource and no `@Qualifier` injections apart from `@Qualifier("graphServiceClient")` on the Graph `ObjectProvider`, which is correct. The pre-existing H2 defaults are relevant to C-2.
- **SQL.** N/A. There are no queries.
- **Liquibase.** N/A. The service has no Liquibase.

### 03_implement "flag for 04_security" items
1. **Auth requirement enforced?** Partially. Path-level enforcement is confirmed. However, C-1 made tokens forgeable (fixed), and C-2 lets anyone mint them (unresolved). The 401 path is untested (L-1).
2. **Parsing surface / size cap.** The non-chunked cap holds. The chunked bypass is Medium, not Critical/High (M-1).
   - Base64 decoding is done by Jackson into `byte[]`, and invalid input maps to MGE-V-005.
   - The address regex `^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$` backtracks linearly (no nested quantifiers), so there is no ReDoS.
3. **Sender allow-list.** PASS.
   - It fails closed: a null list becomes `List.of()`, and `noneMatch` on an empty list rejects every sender.
   - It is case-insensitive (`equalsIgnoreCase`), and whitespace is stripped on both sides.
   - Display-name forms (`Name <a@b>`), comma-joined senders and non-ASCII look-alikes (e.g. dotless `ı`, which `equalsIgnoreCase` would otherwise fold to `I`) all fail the ASCII-only V-002 regex, which runs before V-007.
   - The stripped value that passed both checks is the same value passed to `graphClient.users(from)`, so no time-of-check/time-of-use (TOCTOU) gap exists.
4. **Log and PII leakage.** PASS for our code. The SDK `DefaultLogger` is Low (L-2) and B-004 stack traces are Low (L-3). The startup token log is gone. No secret or token is logged anywhere in the in-scope code.
5. **Retry policy.** PASS by code reading and unit tests (`GraphRetryPolicy`). `RetryHandler` retries only when its own 429/503/504 status check **and** our `IShouldRetry` both agree, and ours requires 429. `maxRetries=2`, `delay=1`, and a `Retry-After` above 5 s or non-numeric stops retrying. 503/504 are never retried. The live behaviour is not exercised offline.
6. **Raw-token log in earlier deployments.** Recorded as operational recommendation L-6.

## Scope Check (step 2)
`git status --short` / `git diff --name-only` / `git diff --stat` (working tree, as instructed; nothing is committed for this feature) were compared with Files Touched:
- **Matched exactly.** 16 modified files, 2 deleted, 1 new source file (`RequestSizeLimitConfig.java`) and 5 new test files, all listed in `implementation-report.md`. The three untracked feature-dir docs are pipeline artifacts.
- **No undisclosed scope creep.** At audit time, `git status` showed no changes outside `services/alramz-notification-service` and the feature directory. The unrelated changes in other services that the caller mentioned were not present in the working tree when checked. Nothing outside scope was touched.
- **Added by this stage:**
  - the six `company.jwt.secret` lines (in-scope YAMLs);
  - the new `services/alramz-notification-service/src/test/java/com/alramz/config/ProfileSecurityConfigTest.java`, a regression check for the C-1 fix and the permit-all removal.

## Automated Tooling Summary
- **`spring-boot-security` skill (scanner `scripts/scan.py` on the service):** 0 CRITICAL, 0 IMPORTANT, 2 SUGGESTION.
  - `E3`, catch-all handler: dismissed, because `AccessDeniedException` has its own handler.
  - `D1`, no dependency scanner in the build: confirmed, see below.

  Manual review against the skill's taxonomy produced C-1 (S1/C4/AU1), C-2 (AZ5), M-1 (AP5) and M-5 (AP1). None of these has a regex the scanner could match.
- **Checkstyle:** 1 finding in scope. `EmailServiceImpl.java:203 NPathComplexity` (warning severity, so the plugin printed "0 violations") maps to Medium (M-2). No style violations.
- **PMD 7.10.0 (`-Dpmd.skip=false`, read from `target/pmd.xml`):** 52 module-wide, **49 in scope**:
  - 4 priority-2 (Medium, M-3);
  - 45 priority-3 (Low, L-5).

  **No `category/java/security.xml` ruleset hit.** The 3 out-of-scope hits (`AlramzNotificationServiceApplication`, `StringUtils`) were ignored.
- **SpotBugs 4.x core (`-Dspotbugs.skip=false`):** 9 module-wide.
  - 7 in scope: EI_EXPOSE_REP/REP2 on the generated `EmailAttachment`/`EmailRequest`, Normal, mapped to Medium (M-4).
  - 2 out of scope and ignored: `ApiUtil`, `ApplicationController`.

  This is **plain SpotBugs core with no FindSecBugs**, so security-specific bug patterns (crypto misuse, injection sinks, hardcoded keys) are not covered. That is a tooling gap, not a claim of full coverage. FindSecBugs would plausibly have caught the C-1 pattern in the starter (HARD_CODE_PASSWORD/KEY). Adding it is recommended.
- **OWASP dependency-check:** **could not run.** `mvn org.owasp:dependency-check-maven:check -pl services/alramz-notification-service` failed with `NvdApiException: Invalid API Key, length of 0` / `NoDataException: No documents exist`. No NVD API key is configured and there is no local NVD cache. No dependency findings are available. Register the plugin with an NVD API key in CI.

## Residual Risks & Recommendations
Before `05_pr`:
- **Must resolve:** C-2 (choose option 1-4 above). Until it is resolved, the `APP_NOTIFICATION` requirement on `/api/v1/email/send` can be bypassed by anyone who can reach `/api/auth/register` on this service.
- **Must coordinate:** C-1 deployment. `JWT_SECRET` must be present in every environment's pod spec or secret store for this service, with the same value that signs the callers' tokens. Without it the service now refuses to start, by design.
- **Operational:** L-6, purge or restrict historical logs that may hold Graph access tokens.

Deferred (Medium/Low):
- M-1 bounded-stream filter or gateway body limit for chunked requests.
- M-2 split `validate()`.
- M-3 log guards.
- M-4 SpotBugs exclude for generated models.
- M-5 per-invoker rate limit and recipient cap.
- L-1 MockMvc 401/403 test and a starter 401 entry point.
- L-2 quiet the Graph SDK logger.
- Add FindSecBugs and dependency-check (with an NVD key) to the build.
- Out of scope, separate ticket: the `${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}` fallback in `reference-data-service`/`onboarding-service` profiles, including preprod/prod.

**Final verification:** `mvn -pl services/alramz-notification-service -am clean test` exited **0** after remediation. 66 tests ran in the service (60 previous + 6 new `ProfileSecurityConfigTest` cases), with 0 failures and 0 errors; BUILD SUCCESS.
