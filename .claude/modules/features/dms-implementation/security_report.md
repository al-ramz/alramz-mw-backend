# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-10-30
- **Feature:** dms-implementation — Document Management as a thin, JWT-secured wrapper over the Open Bee Portal Web API (list filing categories, save, search, download).
- **Target Service(s):** `document-management-service`
- **Files Audited:** 80. That is 51 main files (41 Java, `pom.xml`, `specs/apiSpecs.yaml`, `apiCollection.http`, `README.md`, base `application.yml` and the 5 profile YAMLs) plus 29 test files (the test `application.yml` and 28 Java files, including the unplanned `MutableClock.java`). The `git status` check found no extra files.
- **Audit Status:** **ACTION REQUIRED**

**Why ACTION REQUIRED.** The audit found no Critical or High defect in the code this feature added or changed, so no remediation edit was needed. The status comes from:
- **One unresolved High (F1).** Anyone can self-register through the starter and squat a DMS consumer username. The root cause is in the starter and at the gateway/ingress, outside this feature's scope. Several materially different fixes exist, `AskUserQuestion` was not available, and so a human must decide.
- **Pre-existing High-severity secret fallbacks (P1).** These sit in profile YAMLs this feature touched, but this diff did not add them. They need human sign-off before `05_pr`.

**Final gate:** `mvn -pl services/document-management-service -am clean test` exited **0** (starter 130 and DMS 327 tests, 0 failures/errors/skips). No production or test file was edited during this stage.

## Severity Summary
Feature findings only: from the manual review and from tooling, filtered to in-scope files. Pre-existing findings and generated code are listed separately below.

| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 1 (F1, manual) | 0 | 1 (blocked: needs an out-of-scope decision) |
| Medium | 77 (1 manual + 6 Checkstyle + 11 PMD + 59 SpotBugs) | 0 | 77 (non-blocking, recorded) |
| Low | 692 (8 manual + 1 Checkstyle + 682 PMD + 1 SpotBugs) | 0 | 692 (non-blocking, recorded) |

How the tool counts were mapped and filtered:
- Tool findings were mapped to severity using the stage contract's rules.
- PMD and SpotBugs findings in out-of-scope or generated files were excluded:
  - PMD: 14 findings in `DocumentManagementServiceApplication`, `ApplicationController` and `JacksonConfig`.
  - SpotBugs: 24 `EI_EXPOSE_REP*` findings in the generated `com.alramz.model.*`.
- The tool Mediums are almost entirely `EI_EXPOSE_REP*` (Spring beans, records and DTOs), `GuardLogStatement` and complexity metrics. None of them is a security defect (see Automated Tooling Summary).

**Pre-existing findings in in-scope files, not introduced by this diff** (reported, not fixed; see P1):

| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| High | 1 (P1, three committed secret fallbacks in deployed profiles) | 0 | 1 |

## Detailed Findings & Remediation

### F1. Open self-registration lets a caller claim a DMS consumer identity (03_implement flag 2: CONFIRMED)
- **Severity / OWASP category:** High / A01:2021 Broken Access Control, A07:2021 Identification & Authentication Failures.
- **Location:**
  - Starter, out of scope: `services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java:87-89` makes `register-url` `permitAll`.
  - Starter, out of scope: `services/alramz-api-starter/src/main/java/com/alramz/jwt/service/AuthService.java:82-91` stores the roles the caller supplies.
  - DMS: `services/document-management-service/src/main/java/com/alramz/service/ConsumerContextResolver.java:26-30` resolves the consumer as JWT subject == `dms.consumers` key (spec decision Q1).
  - k8s, out of scope: `service.yaml` has `ingress.external: true`.
- **Issue:** `/api/auth/register` is public and lets the caller choose both the username and the roles.
  - Usernames are first-come-first-served (`existsByUsername`).
  - Suppose an attacker can reach the endpoint in an environment where `onboarding-portal` or `crm` has not yet been registered (a new environment, or after a DB reset).
  - The attacker can then register that username with `APP_DOCUMENT_MANAGEMENT_SERVICE_USER` and obtain a valid token.
  - DMS would then call Open Bee with that consumer's real API key. That gives read, search and download access to client documents, which are KYC PII, plus write access.
  - Registering any other username gives only 403 DMS109, so the exposure is limited to squatting a configured consumer name. It is still a full takeover of that consumer.
- **Fix applied:** **None. This is blocking and unresolved.** Every fix is outside `services/document-management-service`:
  - in the starter;
  - in the API gateway;
  - in `k8s` ingress.

  A service-local `SecurityFilterChain` is forbidden by `jwt-security-and-public-endpoints.md`, and the brief excludes editing those files. `AskUserQuestion` is not available in this invocation. These are the options a human needs to choose from, recommended first:
  1. **Platform/gateway:** block `/api/auth/**` (at least `register`) from external callers in every environment. Also register `onboarding-portal`/`crm` as part of provisioning, before DMS is reachable. This is already recorded as deployment prerequisite §5.4 #4 in spec.md and in the README, and it carries over from the mmfintegrations `security_report.md`. **It must be confirmed done per environment before DMS is exposed.**
  2. **Starter change:** stop accepting caller-supplied roles on self-registration. Roles would be assigned by an admin or provisioning path. This affects every service.
  3. **Starter change:** disable or `denyAll` the register URL outside `dev`/`docker` profiles.
  4. **Ingress:** set DMS `ingress.external: false` if only internal consumers call it.

  Until one of these is confirmed, this High remains open.

### M1. Upload spooling can exhaust ephemeral disk (03_implement flag 3: CONFIRMED, residual)
- **Severity / OWASP category:** Medium / A04:2021 Insecure Design (resource exhaustion).
- **Location:** `services/document-management-service/src/main/java/com/alramz/service/UploadSpooler.java:33-49`; base `application.yml` multipart block.
- **Issue:** Each in-flight save holds up to 10 MB in `dms.upload.temp-dir`, plus Tomcat's own multipart temp file (`file-size-threshold` 0). With the default 200 Tomcat threads that is roughly 4 GB worst case on `/tmp`, enough for a pod to be evicted. Only authenticated consumers can upload (two server-side consumers today), and cleanup in `finally` was verified on every path.
- **Fix applied:** None (Medium). Recommendations:
  - set an `emptyDir.sizeLimit` / `ephemeral-storage` limit;
  - consider a semaphore capping concurrent uploads.

  This is already listed as a README/spec §5.6 operations prerequisite.

### L1. Unauthenticated requests get 403, not 401 (03_implement flag 1 / D1: ACCEPTED, recorded)
- **Severity / OWASP category:** Low / A07:2021 (status-code semantics only, no access granted).
- **Location:** starter `JwtAutoConfiguration.jwtSecurityFilterChain` has no `AuthenticationEntryPoint`.
- **Issue:** A missing or invalid token gets a 403 with an empty body, where spec §5.1 says 401. The request is still rejected before DMS and never calls Open Bee (pinned by `DmsApiEndToEndTest#noToken_*` / `#invalidToken_*`).
- **Fix applied:** None, per the caller's instruction. This is starter-wide behaviour, and a service-local `SecurityFilterChain` is forbidden. It is recorded in the README "Known gaps" and in the `DmsUnauthenticated` contract response.

### L2. Download `Content-Type` is passed through from Open Bee (03_implement flag 4: CONFIRMED, low)
- **Severity / OWASP category:** Low / A03:2021 (stored-content XSS vector).
- **Location:** `services/document-management-service/src/main/java/com/alramz/controllers/DmsController.java:71`.
- **Issue:** Open Bee may return `text/html` or `image/svg+xml` for a stored document. The following mitigate it, so no realistic exploitation was found:
  - **`attachment` disposition.** `Content-Disposition` is always `attachment`.
  - **`nosniff` header.** Spring Security's default `X-Content-Type-Options: nosniff` and `Cache-Control: no-store` are present. The starter's `headers(...)` only customises `frameOptions`, so the defaults stay on. This was verified by reading the code; no test asserts it.
  - **No ambient credentials.** The API is bearer-header authenticated (no cookies), so a victim's browser cannot be made to render it at this origin with their credentials.
- **Fix applied:** None (Low). Recommendations:
  - add an assertion for `X-Content-Type-Options: nosniff` to `DmsApiEndToEndTest#download_*`;
  - optionally force `application/octet-stream` for active types (`text/html`, `image/svg+xml`, `application/xhtml+xml`) or add `Content-Security-Policy: sandbox`. That is a behaviour change, so it is left to the spec owner.

### L3. Client `X-Correlation-ID` is forwarded verbatim to Open Bee (03_implement flag 5: CONFIRMED, low)
- **Severity / OWASP category:** Low / A03:2021 (header/log injection, contained).
- **Location:**
  - `services/document-management-service/src/main/java/com/alramz/openbee/OpenBeeClient.java:301-310`;
  - the starter's `CorrelationIdFilter`, which keeps any client value after `trim()`.
- **Issue:** The value is not validated. CR/LF cannot occur, because Tomcat rejects folded or CR/LF header values and the JDK `HttpClient` rejects illegal header characters. Length is bounded by Tomcat's 8 KB header limit. A value with characters the JDK client rejects makes the Open Bee call fail with `BAD_RESPONSE` → 502 DMS201 and an ERROR log. That is a robustness quirk, not a leak.
- **Fix applied:** None (Low; the propagation behaviour is starter-wide). Recommendation: validate or regenerate a non-UUID correlation ID in the starter.

### L4. Malformed Open Bee download headers leave the response body unconsumed
- **Severity / OWASP category:** Low / A04:2021 (resource leak; the trigger is the trusted upstream only).
- **Location:** `services/document-management-service/src/main/java/com/alramz/openbee/OpenBeeClient.java:243-251`.
- **Issue:** The body flux can be left unconsumed:
  - `responseHeaders.getContentType()` throws `InvalidMediaTypeException` on a malformed `Content-Type`.
  - `getContentLength()` throws `NumberFormatException` on a malformed `Content-Length`.
  - Both run after `toEntityFlux` but before `OpenBeeFile` is built. The body flux is then never subscribed or cancelled, so the upstream connection is held until GC.
  - The caller gets a safe 500 DMS299.
- **Fix applied:** None (Low). Recommendation: wrap header parsing and cancel `entity.getBody()` on failure.

### L5. A failed temp-file deletion is silent
- **Severity / OWASP category:** Low / A09:2021 (missing log for a data-at-rest event). It overlaps PMD `EmptyCatchBlock`.
- **Location:** `services/document-management-service/src/main/java/com/alramz/service/UploadSpooler.java:60-69`.
- **Issue:** If `Files.deleteIfExists` fails, a client document (PII) stays in `/tmp` and nothing is logged. The file is mode 0600 with a random name, and it sits on ephemeral pod storage.
- **Fix applied:** None (Low). Recommendation: log a WARN with the random temp file name and the correlation ID (never the client file name).

### L6. Generated `DmsSaveDocumentRequest.toString()` prints the file name and metadata values (03_implement flag 6: CONFIRMED, latent)
- **Severity / OWASP category:** Low / A09:2021.
- **Location:** generated `target/generated-sources/.../model/DmsSaveDocumentRequest.java` (from `specs/apiSpecs.yaml`).
- **Issue:** No DMS code logs it (`@Loggable(logArgs=false)` on every method, and the adapter is outside `ApiAuditAspect`'s pointcut). A future `logArgs=true` or a debug log would leak PII.
- **Fix applied:** None. Keep `logArgs=false`, which `DmsControllerTest#everyEndpoint_isSecuredAndNeverLogsArgumentsOrReturnValues` already pins.

### L7. Search JSON body size is unbounded
- **Severity / OWASP category:** Low / A04:2021.
- **Location:** `POST /api/v1/dms/documents/search` (generated `DmsApi`).
- **Issue:** No servlet limit applies to a JSON body; only Jackson 3's default `StreamReadConstraints` do. An authenticated consumer could post a very large body. Each text value is capped at 4000 chars after parsing, and metadata keys must exist on the category.
- **Fix applied:** None (Low; repo-wide pattern). Recommendation: set a gateway or Tomcat body-size limit.

### L8. The catalog loader trusts Open Bee `pagination.nbPages`
- **Severity / OWASP category:** Low / A04:2021.
- **Location:** `services/document-management-service/src/main/java/com/alramz/service/FilingCategoryCatalog.java:148-164`.
- **Issue:** A broken upstream returning a huge `nbPages` would make the loader issue many sequential calls, under the per-consumer lock.
- **Fix applied:** None (Low). Recommendation: cap pages, for example at 100.

### Flag 7 (Open Bee error bodies): DISMISSED
Error bodies are read as `byte[]` and bounded by the 5 MB codec limit. Overflow becomes empty codes through `onErrorReturn`. Only `error[].code` is kept. Open Bee messages never reach callers or logs (`OpenBeeErrorMapper` logs status and codes only).

### P1 (pre-existing, not introduced by this diff). Committed secret fallbacks in deployed profiles
- **Severity / OWASP category:** High / A07:2021, A05:2021 (CWE-798).
- **Location:** in `services/document-management-service/src/main/resources/application-{dev,docker,test,preprod,prod}.yml`. All five files are in scope because this feature modified them; these lines came from scaffold commit `f445d0b`:
  - `company.jwt.secret: ${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}`;
  - `company.datasource.middleware.plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD:alramz!321*}`, also in preprod and prod;
  - `cipher.password: UseMostSecretKey`.

  The same pattern is in the prod profiles of onboarding, reference-data, reporting-management and utility services.
- **Issue:** If `JWT_SECRET` is not injected in a deployed environment, anyone can mint a token with subject `crm` and the DMS role, which gives full DMS access. `plainPassword` in preprod/prod also violates `multi-datasource-and-jpa.md`.
- **Fix applied:** None. Not fixed by this scoped audit for these reasons:
  - The lines are not part of this feature's diff.
  - The fix is repo-wide.
  - Each option changes deployment behaviour: remove the defaults so startup fails fast, or use cipher-encrypted `password`/`passwordVector`, or use Key Vault wiring.

  **Flagged for human review before `05_pr`.** At minimum, confirm `JWT_SECRET` and the datasource password are injected in every deployed environment.

## Repo Convention Checks (step 5)
- **JWT: public vs secured matches the spec.** PASS. All 4 DMS methods are secured. `permit-all-urls` is exactly `[/api/v1/info]` in all 5 profiles (verified by grep, and `DmsProfileConfigTest`).
- **JWT: no hand-rolled `SecurityFilterChain` or `@EnableWebSecurity`.** PASS. None in the DMS sources.
- **JWT: role naming.** PASS. `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")` on every method, following the `APP_<SERVICE_CONCERN>` convention.
- **JWT: caller's Bearer token not forwarded.** PASS. `OpenBeeClient` does not extend `AbstractRestClient`. It uses `headers.set(AUTHORIZATION, "Key " + apiKey)` and adds no default headers on the WebClient. Asserted by `OpenBeeClientCategoriesTest#eachConsumerSendsItsOwnKey_neverBearer` and `DmsApiEndToEndTest`.
- **Masking.** PASS. No new sensitive field reaches a logged or audited pipeline. In all 5 profiles:
  - the payload flags are false;
  - `request.include-headers: false` (G2);
  - the save path is in `company.logging.excluded-paths` (G1; the starter matches `getServletPath()`, so `;`/`//` variants still match);
  - the content path is excluded from both filter lists.

  No hand-masking of logged data:
  - The `toString()` overrides on `ConsumerContext`, `DmsProperties.Consumer`, `OpenBeeUpload`, `ValidatedMetadata`, `OpenBeeFile` and `DmsDocumentContent` omit secrets entirely.
  - `sensitive-keys` is unchanged, per the spec decision. `authorization` is already a default key for when the header is logged elsewhere.
- **Secrets: API keys only in env.** PASS.
  - No key is in source or the main YAMLs (`${DMS_OPENBEE_API_KEY_*:}`); the test YAML uses obviously fake keys.
  - Keys are not in exception messages (`OpenBeeException` carries kind, status, codes and path template only) and not in `DmsHealthIndicator` details (counts only).
  - Actuator exposure is `health,info,prometheus` in all 5 profiles. There is no `env`/`configprops`/`heapdump`, and `health.show-details` stays at Boot's default `never`.
- **Exception handling.** PASS.
  - The single advice `GlobalExceptionHandler` is `@RestControllerAdvice(basePackages="com.alramz.controllers") @Order(0)` and has an `Exception` catch-all. It therefore always wins over the starter's `LoggingExceptionHandler` (`LOWEST_PRECEDENCE`, whose `IllegalArgumentException` handler echoes `ex.getMessage()`), and that handler can never fire for DMS controllers.
  - The catch-all returns a fixed "Internal Server Error".
  - Framework 400s use fixed per-field text that never echoes values.
  - The status lives on the `ErrorCode` enum, not in the exception, and cannot be spoofed.
  - `MetadataValidator` deliberately drops causes (`PreserveStackTrace` hits) so that `NumberFormatException` texts carrying values are never logged.
- **Datasource credentials.** No new datasource or `@Qualifier` injection was added by this feature, so there is nothing to check in the diff. The profiles still contain the pre-existing `plainPassword` in preprod/prod, which **FAILS** the convention; see P1.
- **SQL.** NOT APPLICABLE. No SQL or `SqlQueriesManager` query was added. The Open Bee search JSON is built from typed records serialised by Jackson (`OpenBeeSearchRequest`), with no string concatenation, so there is no JSON injection.
- **Liquibase.** NOT APPLICABLE. No changeset was added.

Other areas checked during the manual sweep:
- **SSRF.** PASS.
  - `base-url` comes from config only.
  - IDs are `long` values expanded through `UriBuilder` variables.
  - `followRedirects(NEVER)`.
- **Content-Disposition / header injection.** PASS.
  - `ContentDispositionFormatter` replaces control characters, DEL, non-ASCII, `"` and `\` in `filename=`, and percent-encodes `filename*`. This covers names that Spring's `ContentDisposition.parse` has decoded from `%0D%0A`.
  - The upload part filename is written through Spring's escaping `ContentDisposition`.
- **File names.** PASS. Both `/` and `\` are stripped from the client path, and blank names, separators, ISO control characters and lengths over 255 are rejected with DMS101.
- **Upload limits.** PASS. The container limit is 10 MB / 11 MB with `resolve-lazily` → 413 DMS107, and a service-level check also gives DMS107.
- **Temp files.** PASS. Names are UUIDs with no client name, and files are deleted in `finally` on every path.
- **Deserialization.** PASS. No default typing, and the lenient mapper is used only for error and document-info bodies.
- **IDOR.** PASS. Every Open Bee call uses the caller's own key, so Open Bee enforces document permissions. Save and search are also restricted to categories in the consumer's `use` catalog.

## Scope Check (step 2)
The commands run were `git status --porcelain --untracked-files=all`, `git diff --stat` and `git diff --name-only`, all against the working tree with no ref, because nothing for this feature has been committed yet. The result **matches** `implementation-report.md` §2 "Files Touched" exactly:
- 10 modified tracked files: `pom.xml`, `apiCollection.http`, `specs/apiSpecs.yaml`, the base and 5 profile YAMLs, and the test YAML;
- 70 untracked files: 41 main Java, 28 test Java and `README.md`.

The only file outside the plan is `src/test/java/com/alramz/service/MutableClock.java`. It was disclosed as deviation D2, is test-only and was audited. No undisclosed scope creep. The untracked `.claude/modules/features/dms-implementation/` is this pipeline's own artefacts. This stage edited no service files.

## Automated Tooling Summary
- **`spring-boot-security` skill:** **Could not be invoked.** `Skill("anthropic-skills:spring-boot-security")` returned `Unknown skill`, and no skill of that name or a renamed equivalent appears in this session's skill listing. No other plugin prefix was guessed. The OWASP/Spring sweep was done by hand against the in-scope files instead, covering:
  - injection;
  - Spring Security configuration, JWT and outbound auth;
  - deserialization;
  - Actuator;
  - secrets;
  - crypto (SHA-256 checksum only);
  - logging;
  - SSRF;
  - file handling.

  Results are F1, M1, L1–L8 and the PASS lines above. **Tooling gap:** the scanner script and its taxonomy were not run.
- **Checkstyle** (`verify` with `-Dcheckstyle.consoleOutput=true`; warnings only, 0 errors). 7 findings in in-scope files:
  - **Medium (complexity), all residual:**
    - `ClassFanOutComplexity`: `GlobalExceptionHandler` 32, `OpenBeeClient` 42, `DocumentServiceImpl` 43.
    - `NPathComplexity`: `OpenBeeErrorMapper.codeFor` 4,608, `DocumentServiceImpl.search` 1,200, `MetadataValidator.validateForSave` 500.
    - Three of these sit in security-relevant paths: auth-error mapping, input validation and exception handling. They were reviewed line by line, and their tests cover them well:
      - `OpenBeeErrorMapper`: 100% branch coverage, 27 tests;
      - `MetadataValidator`: 92% branch, 46 tests;
      - `GlobalExceptionHandler`: every handler method tested.
    - The NPath values are inflated by flat `if`/`||` chains, not nesting. They are not blocking.
  - **Low:** `BooleanExpressionComplexity` 6, at `ContentDispositionFormatter.java:37` (the RFC 5987 attr-char test, 100% branch coverage).
  - Out of scope: tab, `ConstantName` and similar warnings in `DocumentManagementServiceApplication`, `JacksonConfig` and `ApplicationController`.
- **PMD** (`-Dpmd.skip=false`; read from `target/pmd.xml`, since the console only prints the total). 707 total, of which **693 are in in-scope files**:
  - **No `category/java/security.xml` hits.**
  - **Priority 2 → Medium (11):** all `GuardLogStatement`, a performance/style rule with no security impact, at:
    - `DmsRequestSummaryInterceptor:36`;
    - `GlobalExceptionHandler:177`;
    - `OpenBeeErrorMapper:34,37,40,43`;
    - `FilingCategoryCatalog:107,136,195`;
    - `DocumentServiceImpl:134,249`.
  - **Priority 3 → Low (682):** mostly Code Style (`MethodArgumentCouldBeFinal` 220, `LocalVariableCouldBeFinal` 168, `OnlyOneReturn` 65 and so on), plus Design metrics (`GodClass`, `TooManyMethods`, `CouplingBetweenObjects`, `LawOfDemeter`, `CyclomaticComplexity`).
  - The Error Prone and Best Practices hits that could signal a real defect were read individually:
    - `PreserveStackTrace` ×3 in `MetadataValidator`: intentional, so value-bearing causes are never logged.
    - `EmptyCatchBlock` in `OpenBeeClient:163`: the fall-through to `BAD_RESPONSE` is correct.
    - `EmptyCatchBlock` in `UploadSpooler:66`: this is finding L5.
    - `AvoidCatchingGenericException` at `DocumentServiceImpl:238`: correct release-and-rethrow.
    - `UnusedAssignment`/`NullAssignment` in compact record constructors: false positives.
  - None hides a failed auth check.
- **SpotBugs** (`-Dspotbugs.skip=false`; **plain SpotBugs core, no FindSecBugs**, so security-pattern coverage is limited). 84 total, of which **60 are in in-scope files**:
  - **Normal → Medium (59):**
    - 55 × `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` on Spring-injected beans, `@ConfigurationProperties` getters/setters, records and DTOs. This is the expected Spring pattern and is benign.
    - 2 × `BX_UNBOXING_IMMEDIATELY_REBOXED` at `DocumentServiceImpl:179-180`.
    - 2 × `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` at `DocumentServiceImpl:122` and `:153`. These are false positives: the getter is called twice on the same generated POJO, and the first call is null-checked. They are not in an auth, token or datasource path, so they were not bumped.
  - **Low (1):** `SE_TRANSIENT_FIELD_NOT_RESTORED` on `OpenBeeException.obCodes`.
  - Out of scope: 24 `EI_EXPOSE_REP*` in the generated `com.alramz.model.*`.
  - Recommendation: add FindSecBugs to `alramz-common-bom` (repo-wide).
- **OWASP dependency-check:** **Could not run.** The unbound goal `org.owasp:dependency-check-maven:13.0.0:check` failed with `NvdApiException: Invalid API Key, length of 0` and then `NoDataException: No documents exist`. There is no NVD API key and no local NVD database, and the repo has no plugin registered. This feature adds two dependencies:
  - `caffeine` (Boot-managed 3.2.4);
  - `wiremock-standalone` 3.13.1, **test scope only**.

  Neither was CVE-scanned.

## Residual Risks & Recommendations
**Must be resolved or signed off before `05_pr`:**
1. **F1 (High, blocked).** Confirm `/api/auth/**` is blocked at the gateway in every environment, and that `onboarding-portal`/`crm` are pre-registered, before DMS is reachable. Alternatively, pick one of the starter or ingress options listed under F1.
2. **P1 (High, pre-existing).** Confirm that `JWT_SECRET` and the datasource password are injected in every deployed environment. Also schedule a repo-wide removal of the committed fallbacks, and move preprod/prod to cipher-encrypted `password`/`passwordVector`.

**Deferred (Medium/Low, non-blocking):**
- M1: limit ephemeral storage and cap upload concurrency.
- L1: add a starter `AuthenticationEntryPoint` (401) through a starter change.
- L2: add a `nosniff` assertion test; consider forcing octet-stream for active content types.
- L3: validate the correlation ID in the starter.
- L4: cancel the download body on a header-parse failure.
- L5: log a WARN when temp-file deletion fails.
- L6: keep `logArgs=false` on DMS methods.
- L7: set a gateway body-size limit.
- L8: cap the catalog page count.
- Tooling: add FindSecBugs and an NVD API key to CI.
- Reduce `OpenBeeClient`/`DocumentServiceImpl` fan-out when it is next touched.
- V1–V6 Open Bee behaviours, including chunked uploads, are still unverified until the opt-in `openbee-live` tests run.

---

# Re-audit (post-implementation changes)

## Executive Summary
- **Audit Date:** 2026-11-02
- **Feature:** dms-implementation. This re-audit covers only the changes made after the first audit: `implementation-report.md` §9 (live-verification fixes L1–L11) and §10 (user-requested changes C1–C3).
- **Target Service(s):** `document-management-service`
- **Files Audited:** 35.
  - 15 main Java files: `DatasourceConfiguration`, `DmsCacheConfig`, `DmsProperties`, `DmsController`, `GlobalExceptionHandler`, `OpenBeeClient`, `OpenBeeErrorBody`, `OpenBeeErrorMapper`, `OpenBeeFilingCategory`, `OpenBeeSearchResponse`, `CatalogMetadata`, `FileNameResolver`, `FilingCategoryCatalog`, `MetadataValidator` and `DocumentServiceImpl`.
  - 4 contract and docs files: `specs/apiSpecs.yaml`, `README.md`, `apiCollection.http` and `e2e/document-management-service.postman_collection.json`.
  - 1 test resource: `src/test/resources/application.yml`.
  - 15 test classes, reviewed for secrets and real data only.
- **Audit Status (re-audit): PASSED WITH FIXES**
  - One High (RA-H1, a CPU and memory DoS through the new `metadata` form field) was found and fixed in scope. No Critical was found. Nothing is blocking.
  - F1 and P1 from the first audit are now user-decided: F1 is a deployment prerequisite and P1 is secrets injected per environment. Both are documented in the README. They are carried over below, not re-opened.
- **Final gate:** `mvn -pl services/document-management-service -am clean test` exited **0**. That is 130 starter tests and 358 DMS tests (355 before this stage plus 3 new), with 0 failures, errors or skips.

## Severity Summary (re-audit delta only)
| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 1 (RA-H1, manual) | 1 | 0 |
| Medium | 55 (1 manual + 6 Checkstyle + 11 PMD + 37 SpotBugs) | 0 | 55 (non-blocking, recorded) |
| Low | 628 (8 manual + 620 PMD) | 0 | 628 (non-blocking, recorded) |

How the tool counts were produced:
- Tool counts were filtered to the 15 main Java files above. The analysers report whole files, so some findings sit on code that predates §9/§10. Those were already counted in the first audit, so these numbers overlap with it and should not be added to it.
- The tool Mediums are `EI_EXPOSE_REP*` findings (Spring beans and records), `GuardLogStatement` findings and complexity metrics. None is a security defect.

Accepted gaps, recorded and not counted or fixed (per the caller):
- 401 vs 403 for a missing token (L1 of the first audit; starter-wide).
- 405/415 turned into 500 by the starter's `LoggingExceptionHandler` (starter-wide; README "Known gaps").
- F1, decided as a deployment prerequisite (README prerequisite 4).
- P1, decided as secrets injected per environment.

## Detailed Findings & Remediation

### RA-H1. Huge-exponent numbers in the save `metadata` field cause a CPU and memory DoS (FIXED)
- **Severity / OWASP category:** High / A04:2021 Insecure Design (OWASP API4:2023 Unrestricted Resource Consumption), CWE-400.
- **Location:**
  - `services/document-management-service/src/main/java/com/alramz/controllers/DmsController.java:46-49`: the static `JsonMapper` has `USE_BIG_DECIMAL_FOR_FLOATS`, which is new in C2.
  - `services/document-management-service/src/main/java/com/alramz/service/MetadataValidator.java`: `integer()` calls `toBigIntegerExact()`, and `newListValue()` reaches it for `allowNewValue` lists.
  - `services/document-management-service/src/main/java/com/alramz/openbee/MetadataEncoder.java`: `format()` calls `toPlainString()` on a DECIMAL value.
- **Issue:** The new form-field parser binds a JSON float such as `1e999999999` to `BigDecimal("1E+999999999")`. Parsing is cheap: the text is 11 characters, well within Jackson's 1000-character number limit, and Jackson puts no limit on a `BigDecimal` scale.
  - For an INTEGER metadata, or a free-entry LIST metadata (C1), `toBigIntegerExact()` then computes 10^999,999,999. That is about 415 MB of `BigInteger` and minutes of CPU on a Tomcat thread.
  - For a DECIMAL metadata, the validator passes the value through and `MetadataEncoder` calls `toPlainString()`. That allocates a string of about 1 GB, which would cause an `OutOfMemoryError`.
  - `1e-999999999` triggers the same expansion.
  - A few such requests from any token holding the DMS role and a configured subject could take the pod down for every consumer. The input looks harmless and passes every earlier check.
  - The old JSON `request` part bound floats to `Double`, where `1e999999999` becomes `Infinity` and was rejected, so this risk is new.
  - A related pre-existing gap: a numeric string of millions of digits (search JSON, L7) was parsed by `new BigDecimal(String)` in quadratic time.
- **Fix applied:** Same-behaviour hardening with one obvious shape, so no user decision was needed. In `MetadataValidator.decimalOf`, every number is checked by a new `withinDigitLimit`:
  - Rule: reject when `scale > 1000` or `precision - scale > 1000`. The subtraction is done in `long`, so `1E+2147483647` cannot overflow past the check.
  - Strings: `plainNumber` rejects strings longer than 1000 characters before parsing.
  - Outcome: both cases throw `NumberFormatException`, which the existing handlers turn into DMS105 "Invalid value for metadata <id> (<name>)". The value is never echoed.
  - Why 1000: it is Jackson's default maximum number length, so any number that can be written in plain notation, and every finite `double`, stays valid.
  - The fix covers save, search and free-entry list values, because all of them go through `decimalOf`.
- **Tests added:**
  - `MetadataValidatorTest#save_hugeExponentsAndOverlongNumbers_areDms105Immediately`: INTEGER, DECIMAL and `allowNewValue` LIST; `1E±999999999`, `1E+2147483647` and a 1001-digit string; save and search; DMS105 within a 5 s preemptive timeout.
  - `MetadataValidatorTest#save_numbersAtTheDigitLimit_stayValid`: a 1000-digit integer and `1E+999` are still accepted.
  - `DmsControllerTest#parseMetadata_keepsAHugeExponentAsAnUnexpandedBigDecimal`: pins the parser behaviour that made this reachable.

### RA-M1. A catalog load has no overall deadline (request threads held while Open Bee is slow)
- **Severity / OWASP category:** Medium / A04:2021 (resource exhaustion; needs a degraded upstream).
- **Location:** `services/document-management-service/src/main/java/com/alramz/service/FilingCategoryCatalog.java:160-228` (`reload`, `load` and the `join()` loop).
- **Issue:** The request thread holds the per-consumer lock and waits, with `join()` and no timeout, for every list future. Other requests from the same consumer wait on `lock.lock()` with no timeout. Each call is bounded:
  - `read-timeout` is 15 s, times 3 attempts with backoff, which is about 47 s;
  - the circuit breaker fails fast once it opens.

  The problem is an Open Bee that is slow but under the timeout. Open Bee serves one request per key at a time, and the circuit breaker's default slow-call threshold is 60 s, so these calls are not counted as failures. In that case a cold load of 42 lists across 8 threads can take several minutes, and same-consumer Tomcat threads pile up behind it.
  - Once the cache is warm, the background refresh-ahead keeps callers off this path. Stale-on-failure also still applies.
  - The loader pool is bounded: a fixed 8 threads, with at most one load per consumer and consumers fixed by config.
- **Fix applied:** None (Medium). Recommendations:
  - put an overall deadline on `load`, for example `CompletableFuture.allOf(...).orTimeout(...)` that falls back to the stale entry;
  - use `tryLock(timeout)` for waiters;
  - or set a circuit-breaker slow-call threshold below `read-timeout`.

### RA-L1. The refresh task can stop silently on an `Error`
- **Severity / OWASP category:** Low / A09:2021 (availability degradation with no signal).
- **Location:**
  - `FilingCategoryCatalog.refreshAhead()`, which catches `RuntimeException` only, inside the per-consumer loop;
  - `DmsCacheConfig.dmsCatalogRefresher`.
- **Issue:** An `Error` thrown in `refreshAhead` silently cancels every later run, because `ScheduledExecutorService` keeps the throwable in a future nobody reads. Examples are `OutOfMemoryError`, `StackOverflowError` and `LinkageError`, and also a `RuntimeException` thrown before the `try` block.
  - DMS would then fall back to the earlier behaviour, a synchronous reload on the request thread after the TTL. No data or key exposure follows.
- **Fix applied:** None (Low). Recommendation: wrap the scheduled lambda in a catch of `Throwable` that logs (with the class name only) and swallows.

### RA-L2. Refresh and pool settings are not validated against each other
- **Severity / OWASP category:** Low / A05:2021 (misconfiguration amplifies upstream load).
- **Location:** `services/document-management-service/src/main/java/com/alramz/config/DmsProperties.java` (`Cache`).
- **Issue:** Some settings can amplify the load DMS puts on Open Bee:
  - **Refresh-ahead at or above the TTL.** If `refresh-ahead >= categories-ttl`, every cached consumer is fully reloaded every 60 s, about 45 Open Bee calls per consumer per minute.
  - **No upper bound on the pool.** `list-load-concurrency` has `@Min(1)` but no maximum.
  - **Failing consumers keep retrying.** A consumer whose reload keeps failing with a non-transient error (such as a revoked key, or one list 4xx) is retried every 60 s indefinitely. That is bounded to one load attempt per minute per consumer.
  - **Defaults are safe.** The defaults (15 min / 5 min / 8) are not set in any profile, so they apply.
- **Fix applied:** None (Low). Recommendations:
  - add a cross-field check that `refresh-ahead < categories-ttl`;
  - add `@Max` on the concurrency;
  - optionally back off the background refresh after consecutive failures.

### RA-L3. Free-entry list values are stored in Open Bee exactly as entered (C1)
- **Severity / OWASP category:** Low / A04:2021 (data integrity of a shared list).
- **Location:** `services/document-management-service/src/main/java/com/alramz/service/MetadataValidator.java` (`newListValue`, then `text`).
- **Issue:** Any non-blank text up to 4000 characters is accepted, including ISO control characters, and Open Bee adds it permanently to the shared list (for example list 11887). Both consumers see it, and the other consumer may display it.
  - **No injection into DMS's own calls was found.** The value is a multipart part body under a random Spring boundary, or a Jackson-serialised JSON string in the search filter, and it never reaches a header, URL or log.
  - **Scope of the issue.** What remains is list pollution, plus any downstream rendering issue on Open Bee's side. The behaviour is a user decision (C1).
- **Fix applied:** None (Low). Recommendation: reject ISO control characters, and optionally apply a shorter maximum, for free-entry values only, after confirming with the spec owner.

### RA-L4. Document info separates "not found" from "forbidden" (DMS108 vs DMS109, L5)
- **Severity / OWASP category:** Low / A01:2021 (existence oracle).
- **Location:** `services/document-management-service/src/main/java/com/alramz/service/impl/DocumentServiceImpl.java:284-297`.
- **Issue:** A consumer can tell whether a document ID it cannot read exists (403 → DMS109) or not (404 → DMS108). This is exactly what Open Bee's document-info endpoint already tells the same key.
  - On 401, 403 and 404 the download stops before the file endpoint, and no name, metadata or content of a forbidden document is returned.
  - IDs are sequential, so existence can be enumerated, but only existence.
- **Fix applied:** None (Low; it matches the spec's error table). If existence must be hidden, map document-info 403 to DMS108 as well. That is a spec decision.

### RA-L5. Open Bee `code` strings are logged verbatim
- **Severity / OWASP category:** Low / A09:2021.
- **Location:** `services/document-management-service/src/main/java/com/alramz/openbee/OpenBeeErrorMapper.java:39-50`.
- **Issue:** The live Open Bee uses message-like text as the `code`, for example `Document with the same name already exist` (L7). The mapper logs `codes=[...]` as received, never to callers.
  - If Open Bee ever put a document name or value into `code`, it would reach the log.
  - The body is capped at 5 MB, but the number and length of codes are not.
- **Fix applied:** None (Low). Recommendation: truncate each logged code (for example to 64 characters) and limit the count.

### RA-L6. Save form fields now appear in `getParameterMap()`, so the logging exclusion carries the risk
- **Severity / OWASP category:** Low / A09:2021 (latent).
- **Location:**
  - the starter's `LoggingHelper.buildRequestLog → toQueryParams(request.getParameterMap())`, used by `RequestLoggingFilter` and copied to span tags;
  - the `company.logging.excluded-paths` entry `/api/v1/dms/documents` in the 5 profiles.
- **Issue:** With plain multipart form fields (C2), `fileName`, `description` and `metadata` are servlet parameters. The starter's request log would record them, masked only by its key and value patterns, if the save path were ever removed from `excluded-paths`. With the old JSON part this would not have happened.
  - **The exclusion is still in place.** All 5 profiles still exclude the exact save path; this was re-verified by grep, and `DmsProfileConfigTest` pins it.
  - **The logs stay clean.** `DmsLoggingHygieneTest` now posts plain form fields through the real filter chain and asserts that neither `FIT-100234` nor `passport.pdf` nor any key appears in the logs.
  - **The DB audit cannot capture the fields.** The save path is not in `database-logging.excluded-paths`, but `ApiAuditLogFilter` records only URI, method, status and duration. Payloads are off, and it never reads parameters.
  - **DMS's own log lines carry no form-field values.** The summary line has IDs, codes and the extension only.
- **Fix applied:** None needed. Keep the exclusion and the pinning tests.

### RA-L7. The DMS executor beans may switch off Boot's default `applicationTaskExecutor`
- **Severity / OWASP category:** Low / A05:2021 (informational).
- **Location:** `services/document-management-service/src/main/java/com/alramz/config/DmsCacheConfig.java:47-64`.
- **Issue:** Declaring `ExecutorService` / `ScheduledExecutorService` beans can make Spring Boot's task-execution auto-configuration back off (in its default `auto` mode). Spring would then fall back to an unbounded `SimpleAsyncTaskExecutor` for `@Async` and MVC async work.
  - A grep found no `@Async`, `@EnableAsync` or MVC async return types in DMS or the starter, so there is no impact today.
  - Both executors are daemon threads with `destroyMethod = "shutdownNow"`.
- **Fix applied:** None (Low). If async work is added later, define the `applicationTaskExecutor` explicitly or set `spring.task.execution.mode=force`.

### RA-L8. The `metadata` form field has no explicit size limit in the contract
- **Severity / OWASP category:** Low / A04:2021.
- **Location:** `specs/apiSpecs.yaml` (the save `metadata` form field); `src/main/resources/application.yml:11` (comment).
- **Issue:** The field is bounded only by the container and by Jackson defaults:
  - Tomcat's `max-http-form-post-size` (Boot default 2 MB, not overridden), which Tomcat applies to non-file multipart parts;
  - `max-request-size` 11 MB;
  - Jackson 3's default limits on nesting depth, number length and name length.

  Inside that, the validator never recurses: nested objects and arrays inside an array are DMS105, and unknown keys are DMS103 with one rate-limited refresh. Also, the base `application.yml` comment still says "headroom for the JSON `request` part" (a stale comment only).
- **Fix applied:** None (Low). Recommendations: document or set an explicit `maxLength` for `metadata`, and refresh the comment.

### Focus-area items confirmed with no finding
- **API keys on the new threads.** The pool and refresh threads are named `dms-catalog-load-N` and `dms-catalog-refresh-N`. Their log lines carry `consumerId`, the exception simple name, kind/status and the correlation ID, never a key.
  - `refreshAhead` builds `ConsumerContext` from `DmsProperties` (`toString` is masked), and only for consumers still in config.
  - `OpenBeeException` carries no key. The new document-info call goes through the same `headers(consumer)` path, with `Authorization: Key` set and never logged.
- **MDC handling.**
  - **Loader pool.** Each task installs the caller's MDC copy and restores the thread's previous map (or clears it) in `finally`, so nothing leaks between requests or consumers.
  - **Refresh thread.** `refreshAhead` sets a fresh random correlation ID per consumer and calls `MDC.clear()` in `finally`, so there is no carry-over between consumers.
  - **Summary attribute.** `DmsRequestSummary` is request-attribute based, so it is a no-op on pool threads and cannot be cross-written.
- **Executor lifecycle.** The loader is a fixed pool of `list-load-concurrency` (default 8). Its queue holds at most one consumer load's lists per consumer, because of the per-consumer lock. Both executors use daemon threads with `shutdownNow` on context close. Refresh runs every 60 s with a fixed delay, so runs never overlap.
- **Open Bee hanging.** Every call is bounded by the TimeLimiter or transfer timeout, read retries and the circuit breaker. See RA-M1 for the remaining slow-upstream case.
- **Metadata JSON errors.**
  - Every `JacksonException` becomes the fixed DMS101 `metadata must be a JSON object of metadata ID to value`, without the cause, so the input snippet in Jackson's message is never logged. PMD `PreserveStackTrace` at `DmsController:94` is intentional.
  - Trailing tokens, non-objects and `null` are handled.
  - DMS103 echoes only the parsed numeric ID (at most 18 digits) and the category ID.
- **New `MissingServletRequestParameterException` handler.** It echoes `getParameterName()`, which is the server-declared name (`filingCategoryId`), never client input.
- **Generated save signature.** Bean constraints are kept on the form fields: `@Min(1)` on `filingCategoryId`, `@Size(1..255)` on `fileName` and `@Size(max=1000)` on `description`.
- **New error codes.**
  - `MUST_SPECIFY_FOLDER_OR_RULE` gives the fixed `NO_FOLDER_RULE_MESSAGE`; `Document with the same name already exist` gives the DMS106 default message.
  - `OpenBeeErrorBody` reads a single object or an array, codes only. No Open Bee message reaches a client.
- **Open Bee data in responses.**
  - **Names from document info.** The document-info name and `extension` go through `FileNameResolver.withExtension` and then `ContentDispositionFormatter`, which is unchanged and sanitises them.
  - **Search names.** The search `fileName` is JSON-encoded.
  - **Multiple-value parsing.** `multipleValues` parses Open Bee text with a default Jackson 2 mapper, bounded by the 5 MB codec, and falls back to the raw value.
- **Search hits under `items` (L2).** `@JsonAlias` only. No newly exposed fields: the record still declares only the allowed ones.
- **Download order (L6).** The document-info lookup completes before the file stream opens, so no Open Bee body is left open across the extra call. Body cleanup on a later failure is unchanged.
- **`DatasourceConfiguration` (L1).** It is byte-identical to `reference-data-service`'s. `@Qualifier("middlewareDataSource")` and `@Qualifier("middlewareTransactionManager")` are correct, and no credential is involved.
- **Test resources and collections.**
  - **Test `application.yml`.** It has only H2 `sa` (`plainPassword` in a test profile is allowed), the pre-existing `cipher.password` test value, a `localhost:65535` base URL and keys `test-key-onboarding` / `test-key-crm`.
  - **Postman collection.** Its password, token and metadata variables are empty, and its host is `http://localhost`.
  - **`apiCollection.http`.** `password123` is pre-existing in the committed HEAD.
  - **Test classes.** No real key, host or client data: `openbee.example.test`, `openbee.invalid` and `localhost` only. `OpenBeeLiveVerificationTest` reads env vars only.
  - **No Open Bee host IP in the repo.** A scan of `services/document-management-service` (excluding `target/` and `logs/`), `e2e/` and this feature directory found only `172.25.21.114`, which is the pre-existing OTLP endpoint default in `application-dev.yml` (present in HEAD, from the scaffold). `services/document-management-service/logs/` is git-ignored (`*.log`, `.gitignore:136`).
  - **README.** It names `Administrator` only as an operational warning (prerequisite 8), not as a credential.

## Repo Convention Checks (step 5, delta)
- **JWT: public vs secured.** PASS. No new endpoint. All 4 methods keep `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")`. `permit-all-urls` is still exactly `[/api/v1/info]` in all 5 profiles (re-grepped).
- **JWT: no hand-rolled `SecurityFilterChain` or `@EnableWebSecurity`.** PASS. None was added.
- **JWT: role naming.** PASS. The role is unchanged and follows `APP_<SERVICE_CONCERN>`.
- **Masking.** PASS. No new sensitive field reaches a logged or audited pipeline (see RA-L6). There is no hand-masking, and `sensitive-keys` is unchanged.
- **Exception handling.** PASS.
  - The new handler sits in the single `@Order(0)` advice, which still wins over the starter's `LoggingExceptionHandler`.
  - The new messages are fixed.
  - No status lives in an exception.
  - The catch-all is unchanged.
- **Datasource credentials.** PASS for the delta. `DatasourceConfiguration` uses the correct `@Qualifier`s, and the test YAML's `plainPassword` is test-only. The pre-existing preprod/prod `plainPassword` stays under P1 (user-decided).
- **SQL.** NOT APPLICABLE. No SQL was added. The search filter is Jackson-serialised typed records.
- **Liquibase.** NOT APPLICABLE. No changeset was added.

## Scope Check (step 2, delta)
- **What was run.** `git status --porcelain` and `git diff --stat`, against the working tree only, because nothing for this feature is committed.
- **Result.** It shows the same 10 modified tracked files and the same untracked set as the first audit; `.claude/modules/features/dms-implementation/` is the pipeline's own artefacts.
- **What the comparison can and cannot show.**
  - **What git can show.** The new files from §9 are present and disclosed in §9 L1: `config/DatasourceConfiguration.java` and `test/.../config/DatasourceConfigurationTest.java`.
  - **What git cannot show.** Git cannot separate the pre-audit and post-audit hunks, so the delta was taken from §9/§10 as instructed. No undisclosed file was found.
- **Profile YAMLs and `pom.xml`.** They are modified in the working tree but not listed in §9/§10. Their security-relevant settings were re-checked by grep: exclusions, payload flags, permit-all and env-only keys. They match the first audit.
- **Files edited by this stage (all in scope):**
  - `src/main/java/com/alramz/service/MetadataValidator.java` (the RA-H1 fix);
  - `src/test/java/com/alramz/service/MetadataValidatorTest.java` (+2 tests);
  - `src/test/java/com/alramz/controllers/DmsControllerTest.java` (+1 test).

## Automated Tooling Summary (delta)
- **`spring-boot-security` skill:** **Not available.** No `anthropic-skills:spring-boot-security` (or renamed equivalent) appears in this session's skill listing, and no prefix was guessed. The OWASP/Spring sweep of the delta was done by hand. It covered:
  - injection (multipart and search JSON);
  - resource consumption;
  - concurrency;
  - secrets and keys;
  - MDC and logging;
  - error mapping;
  - deserialization;
  - datasource.

  That sweep gave RA-H1, RA-M1 and RA-L1 to RA-L8. Tooling gap: the scanner script was not run.
- **Checkstyle** (`verify -Dcheckstyle.consoleOutput=true`; warnings only). There are 6 findings in the 15 re-audit files, all complexity → **Medium**, residual. No style-only (Low) warnings.
  - `ClassFanOutComplexity`:
    - `GlobalExceptionHandler` 33 (was 32; +1 for the new handler);
    - `OpenBeeClient` 42;
    - `DocumentServiceImpl` 46 (was 43).
  - `NPathComplexity`:
    - `OpenBeeErrorMapper.codeFor` 11,520 (was 4,608; the new L7/L8 branches);
    - `DocumentServiceImpl.search` 1,200;
    - `MetadataValidator.validateForSave` 500.
  - `codeFor` is in a security-relevant path (auth-error mapping). It is a flat `if` chain with each new branch covered by `OpenBeeErrorMapperTest`. Not blocking.
- **PMD** (`-Dpmd.skip=false`; read from `target/pmd.xml`). 631 findings in the 15 re-audit files.
  - **No `category/java/security.xml` hits.**
  - **Priority 2 → Medium (11):** all `GuardLogStatement`, at:
    - `GlobalExceptionHandler:185`;
    - `OpenBeeErrorMapper:40,43,46,49`;
    - `FilingCategoryCatalog:116,146,177,270`;
    - `DocumentServiceImpl:139,292`.
  - **Priority 3 → Low (620):** 549 Code Style, 56 Design, 9 Error Prone and 6 Best Practices.
  - The Error Prone and Best Practices hits that could signal a real defect were read individually. None hides a security failure:
    - `PreserveStackTrace` at `DmsController:94`, `MetadataValidator:168,176,221` and `FilingCategoryCatalog:222`. These are intentional: they keep value-bearing parser messages out of logs, or unwrap a `CompletionException` to its cause.
    - `EmptyCatchBlock` at `DocumentServiceImpl:203` (`multipleValues` falls back to the raw value) and at `OpenBeeClient:164` (pre-existing).
    - `NullAssignment`/`UnusedAssignment` in the `CatalogMetadata` compact constructor: false positives.
- **SpotBugs** (`-Dspotbugs.skip=false`; **plain SpotBugs core, no FindSecBugs**, so security-pattern coverage is limited). 37 findings in the 15 re-audit files, all Normal → **Medium**. No High or Low.
  - 33 × `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` on Spring beans, `@ConfigurationProperties` and records, including the new `Executor` injection in `FilingCategoryCatalog`. These are benign.
  - 2 × `BX_UNBOXING_IMMEDIATELY_REBOXED` at `DocumentServiceImpl:184-185`.
  - 2 × `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` at `DocumentServiceImpl:127,158`. These are false positives: a double getter call on a generated POJO, with the first call null-checked, outside any auth/token/datasource path.
  - SpotBugs did not flag the `BigDecimal` expansion (RA-H1). This is consistent with the coverage gap.
- **OWASP dependency-check:** **Not re-run.** §9/§10 changed no dependency (`pom.xml` is as of the first audit). In the first audit the unbound goal could not run: there is no NVD API key and no local NVD database.

## Residual Risks & Recommendations (re-audit)
**Before `05_pr` (carried over, user-decided, not re-opened):**
- **F1.** Confirm the gateway blocks `/api/auth/**` and the consumers are pre-registered, per environment (README prerequisite 4).
- **P1.** Confirm `JWT_SECRET` and the datasource password are injected per environment.
- **Open Bee test environment only.** Plain HTTP and an `Administrator` key are acceptable only in the test environment (README prerequisites 7 and 8).

**Deferred (Medium/Low, non-blocking):**
- RA-M1: add an overall catalog-load deadline, or a lock-wait timeout, or a circuit-breaker slow-call threshold.
- RA-L1: catch and log `Throwable` around the scheduled refresh.
- RA-L2: validate `refresh-ahead < categories-ttl`, add `@Max` on `list-load-concurrency`, and back off failing background refreshes.
- RA-L3: reject control characters in free-entry list values (spec owner).
- RA-L4: decide whether document existence must be hidden (DMS108 for document-info 403).
- RA-L5: truncate the logged Open Bee codes.
- RA-L6: keep the save-path logging exclusion and its pinning tests.
- RA-L7: define `applicationTaskExecutor` explicitly if async work is added.
- RA-L8: give the `metadata` form field an explicit `maxLength`; refresh the stale multipart comment.
- Tooling: add FindSecBugs (it would not have caught RA-H1 either; consider a unit-test convention for numeric bounds) and an NVD key in CI.

## Re-audit follow-up — findings fixed after the re-audit (2026-11-02, orchestrator, at the user's request)

| Finding | Fix | Tests |
|---|---|---|
| RA-M1 (Medium) — catalog load had no overall deadline; request threads queued on the per-consumer lock without limit | `FilingCategoryCatalog`: `tryLock(dms.cache.lock-wait=20s)`; on timeout the cached copy is served (WARN) or a TIMEOUT (-> DMS203) is raised. Each load has `dms.cache.load-deadline=2m`, checked before each category page and while awaiting list futures; queued list calls are cancelled; TIMEOUT is "unavailable", so the cached copy is kept | `FilingCategoryCatalogTest#lockWait_*`, `#loadDeadline_*` (repeated 3x, stable) |
| RA-L1 (Low) — an `Error` escaping `refreshAhead` silently cancelled the schedule | `DmsCacheConfig.refreshSafely` catches `Throwable`, logs the class name only | `DmsCacheConfigTest#refreshSafely_neverLetsAnythingEscape` |
| RA-L2 (Low) — `refresh-ahead` not checked against the TTL; pool size unbounded | `@AssertTrue` refresh-ahead < categories-ttl (startup fails otherwise); `@Max(32)` on `list-load-concurrency` | `DmsPropertiesTest#refreshAheadNotShorterThanTtl_failsStartup`, `#listLoadConcurrencyAbove32_failsStartup` |
| RA-L3 (Low, partial) — free-entry list values could carry control characters into Open Bee's shared list | Control characters rejected (DMS105) for `allowNewValue` values; the 4000-char limit is unchanged | `MetadataValidatorTest#allowNewValue_rejectsControlCharacters` |

`mvn -pl services/document-management-service -am clean test`: exit 0, 368 DMS tests, 0 failures. JaCoCo: FilingCategoryCatalog 95.9%, MetadataValidator 97.4%, DmsCacheConfig and DmsProperties 100%.
Remaining from the re-audit: RA-L4..RA-L8 (Low, accepted) and the tool Medium/Low counts (non-security style findings).

- **Audit Status (after follow-up): PASSED** — no Critical, High or Medium finding open in this feature; accepted gaps (401 vs 403, 405/415 -> 500, F1, P1) unchanged and documented.
