# Security Audit & Vulnerability Report

## Executive Summary
- **Audit Date:** 2026-10-28
- **Feature:** dfm-nin-onboarding: `POST /api/v1/dfm/investors` (`registerDfmInvestor`) validates a grouped investor registration and registers the investor with DFM's Member API (`/memberapis/onboarding`).
- **Target Service(s):** onboarding-service
- **Files Audited:** 27 (every row of `implementation-report.md` "Files Touched"; the step 2 scope check found no extra files)
- **Audit Status:** PASSED WITH FIXES

**Result in brief.** One High finding was fixed in scope: FATCA/CRS tax identification numbers were logged and audited in clear. Every finding handed forward by earlier stages has been confirmed or dismissed below. No Critical finding exists in feature code. No blocking gap remains. The final gate `mvn -pl services/onboarding-service -am clean test` exited `0`.

## Severity Summary
| Severity | Found | Fixed | Remaining |
|---|---|---|---|
| Critical | 0 | 0 | 0 |
| High | 1 | 1 | 0 |
| Medium | 7 | 0 | 7 (residual, see below) |
| Low | 7 | 0 | 7 (residual, see below) |

Pre-existing issues in files this feature did not change, or in lines of in-scope files it did not change, are **not** counted above. They are listed under Residual Risks, in "Pre-existing, out of scope".

## Detailed Findings & Remediation

### H-1. FATCA/CRS tax identification numbers (TIN) logged and audited unmasked: FIXED
- **Severity / OWASP:** High / A09 Security Logging and Alerting Failures (skill L2, secrets/PII in logs).
- **Location:**
  - `services/onboarding-service/src/main/resources/application-{dev,docker,test,preprod,prod}.yml`, in `company.logging.masking.sensitive-keys`;
  - inbound keys `fatca.tin` and `crs.countries[].tin` (`specs/apiSpecs.yaml`, about lines 1288 and 1303);
  - outbound keys `fatca_tin` and `csr_tin` (`dfm/DfmOnboardingPayload.java:64,87`).
- **Issue:** The masking list in spec §5 covers EID number, passport number, NIN, IBANs and DOB, but no key matches `tin`. In every profile, prod included, the following run with payloads switched on:
  - the request logging filter (log lines and span tags);
  - the `api_audit_log` filter (`database-logging.include-request-payload: true`);
  - `WebClientLoggingFilter` (outbound `http.request.body` span tag).

  So the investor's tax ID went in clear to Seq, the OTLP traces and the DB audit table (7-day retention). For a US person the FATCA TIN *is* the SSN/ITIN. The starter's SSN regex catches only the dashed `ddd-dd-dddd` form, not 9 bare digits. This identifier class is as sensitive as `eidNumber`/`passportNumber`, which the spec itself masks.
- **Fix applied:**
  - Added `- tin` to `sensitive-keys` in all five profile YAMLs, with an inline comment.
  - `LogMaskingUtil` matches by substring and is case-insensitive, so this one key covers `tin`, `fatca_tin`, `csr_tin` and the pre-existing `/dfm/onboarding` `fatcaTin`.
  - Accepted over-masking, the same trade-off spec §5 accepted for `image` and `nin`: `hasTin`, `noTinReason`, `whyNoTinIssued`, `csr_havetin` and `csr_notinreason` are also masked. No other key in this service's contract contains `tin`.
  - `DfmMaskingConfigTest` now requires `tin` in every profile and asserts that `tin`, `fatca_tin` and `csr_tin` values are masked.
- **Why this was not a blocking gap:** There is one obviously-correct shape: the existing, spec-sanctioned masking mechanism. The change only hardens logs; it changes no API or contract and touches only in-scope files.

### M-1. Truncate-then-mask leaves a partial Base64 ID-document fragment unmasked (handed-forward item 1): CONFIRMED, residual
- **Severity / OWASP:** Medium / A09.
- **Location:** These starter classes are outside this feature's scope:
  - `alramz-api-starter/.../audit/SensitiveDataMasker.java:97` (`LogMaskingUtil.mask(truncate(body, 5000))`);
  - `.../logging/util/LoggingHelper.java:157-160`;
  - `.../logging/interceptor/WebClientLoggingFilter.java:116-118`.

  It is triggered by this feature's request and outbound payload.
- **Issue:** `LogMaskingUtil.JSON_PAIR` masks only complete `"key": "value"` pairs.
  - When the 5000-char cut falls inside a Base64 value, that value has no closing quote, so it is left **unmasked**. This applies to `emiratesId.frontImage` inbound and `eid_attachment_front` outbound.
  - About 3-4 KB of the start of an ID-document image therefore reaches Seq, spans and `api_audit_log`.
  - A client can also send keys in any order, so the spec's property ordering is a mitigation, not a guarantee.

  Confirmed:
  - **The security key is not exposed.** It is the 2nd outbound component, far inside the prefix. The `securitykey` header is masked by key in the header span tags.
  - **Exposure is bounded:** at most one partial binary prefix per row. Most of that is JPEG/PNG header data, but a PDF prefix can carry readable text.
- **Why it was not fixed here:** Both complete fixes need a policy decision outside this feature:
  - (a) mask-then-truncate in the shared starter, which affects every service;
  - (b) add `/api/v1/dfm/investors` to `company.logging.excluded-paths` and `database-logging.excluded-paths`. This removes the audit row that spec §4 relies on.

  The spec explicitly accepted this exposure by precedent: `/dfm/onboarding` has it too.
- **Recommendation:** Starter owner: mask before truncating, or replace the truncated tail of an unterminated string with the mask. This is the highest-value follow-up from this audit.

### M-2. No request body size cap below the gateway; about 7 MB Base64 bodies against a 768 MiB heap (item 2): CONFIRMED, residual
- **Severity / OWASP:** Medium / A06 (skill AP5).
- **Location:**
  - `specs/apiSpecs.yaml`: `maxLength` 2796204 ×4 and 1398104 ×1, applied only after the body is parsed;
  - `services/onboarding-service/service.yaml`: `-Xmx768m`, `maxReplicas: 5`;
  - there is no servlet or Tomcat body limit.
- **Issue:**
  - Spring Security (`anyRequest().authenticated()`) rejects unauthenticated requests before the body is read.
  - But `@JwtSecured` checks the role in an aspect *after* Jackson has deserialized the body, so **any** valid JWT, whatever its role, can send large bodies.
  - Jackson 2's per-string limit is 20 M chars. Five image fields can therefore pin up to about 200 MB of `String` per request before Bean Validation rejects it.
  - A few concurrent requests can OOM a pod.
  - The starter's `ContentCachingRequestWrapper`s are capped at 5000/8192 bytes, so they do not multiply the heap use.
- **Why it was not fixed here:** The right limit (about 13 MB, the sum of the field maxima plus overhead) and where to enforce it (ingress annotation, gateway, or a new servlet filter) is an infra or design choice. I found no ingress body-size setting in `k8s/`.
- **Recommendation:** Before go-live, confirm or add an ingress/gateway body limit of about 15 MB for this path. Optionally, add a `Content-Length` guard filter in the service.

### M-3. Other investor identifiers logged in clear: `familyId`, `email`, `mobile`
- **Severity / OWASP:** Medium / A09.
- **Location:** `DfmInvestorRegistrationRequest.familyId`/`email`/`mobile`; outbound `cust_familyid`, `cust_email`, `cust_mobile`.
- **Issue:** `familyId` is the UAE family-book number, a government identifier. Spec §9 explicitly accepted email and mobile in clear; `familyId` was not discussed.
- **Recommendation:** The product owner should decide whether to add `familyId`/`familyid` to `sensitive-keys`. It is a one-line change in each profile.

### M-4. Checkstyle NPathComplexity 612 (max 200) in `DfmMemberApiClient.classify`
- **Severity:** Medium (complexity mapping). **Location:** `dfm/DfmMemberApiClient.java:128`.
- **Issue:** The method is complex, but it is fully branch-tested (93% branch coverage in the implementation report). It classifies DFM responses; it is not an authn/authz or input-validation path. Not blocking.

### M-5. SpotBugs EI_EXPOSE_REP/EI_EXPOSE_REP2 (Normal → Medium)
- **Location:**
  - `config/DfmMemberApiProperties.java:12-13`;
  - `dfm/DfmMemberApiClient.java:55,60`;
  - `dfm/DfmOnboardingOutcome.java:12`;
  - `dfm/DfmOnboardingPayload.java:79-80`;
  - generated `com.alramz.model.Dfm*` models.
- **Issue:** Mutable references are stored or returned: injected Spring beans, a `List` in a record, and the generated DTO getters. These are false positives for security in this design: singleton beans and request-scoped DTOs that are never shared across trust boundaries. No action needed.

### M-6. SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (Normal → Medium): false positive
- **Location:** `service/impl/DfmInvestorServiceImpl.java:97-98`.
- **Issue:** `r.getCrs()` is null-checked and then called again. SpotBugs cannot prove the second call returns the same value. On a single-threaded request DTO it does, so no NPE is reachable.

### M-7. PMD GuardLogStatement (priority 2 → Medium)
- **Location:**
  - `dfm/DfmMemberApiClient.java:86,89,132,150`;
  - `exception/DfmInvestorExceptionHandler.java:87,100`.
- **Issue:** These are performance and style findings, not security. They use parameterised SLF4J calls, so there is no log-injection concern beyond L-2.

### L-1. `@Loggable(logArgs = false)` still logs the return value (item 3)
- **Severity:** Low / A09. **Location:** `controllers/DfmInvestorController.java:25`.
- **Issue:**
  - `MethodExecutionLoggingAspect` serializes the `ResponseEntity<GenericResponse>` to JSON and passes it through `LogMaskingUtil.mask`, so `nin` is masked.
  - `cashTradingNumber`, `marginTradingNumber` and `trackingNumber` stay in clear. The spec intends this, and `DfmMaskingConfigTest` asserts it.
  - The same values are already logged by the response logging filter and stored in `api_audit_log` (`include-response-payload: true`). Setting `logReturn = false` alone would therefore remove only a duplicate.
- **Recommendation:** Optionally set `logReturn = false` to cut duplication. It is not a security control on its own.

### L-2. DFM `response_msg` logged at INFO and echoed to the caller (item 4; scanner S6 hits)
- **Severity:** Low / A09, A10.
- **Location:**
  - `dfm/DfmMemberApiClient.java:132`;
  - `exception/DfmInvestorExceptionHandler.java:76,82`, flagged by scanner S6.
- **Issue:**
  - **Line 76:** `ApplicationException` messages are fixed rule texts built in `DfmInvestorServiceImpl`, with no user values echoed. Dismissed.
  - **Line 82:** this is DFM's own `response_msg`, returned only for DFN006/DFN007, as spec §4.3 and the design doc require. 5xx bodies use fixed texts, which tests verify with sentinel strings.
  - **Residual:** if DFM ever echoes PII or CRLF in `response_msg`, it reaches logs and callers. SLF4J parameterisation limits log forging to what the appender allows.

### L-3. Masking-key substring blast radius (item 6)
- **Severity:** Low (availability of logs, not confidentiality).
- **Issue:** Matching is case-insensitive `contains`, so some unrelated keys get masked:
  - `nin` masks `warning`, `running` and `remaining`;
  - `image` masks `imageType`;
  - `tin` (new) masks `hasTin`/`noTinReason`;
  - the pre-existing default `pan` masks `company`/`span`.

  This only over-masks; it never under-masks. Accepted by spec §5.

### L-4. Record auto-`toString` would print the security key and identifiers if ever logged
- **Severity:** Low / A09 (latent).
- **Location:**
  - `dfm/DfmOnboardingPayload.java` (`securityKey`, all images);
  - `dfm/DfmOnboardingOutcome.Success` (`custNin`).
- **Issue:** The class Javadoc says "no custom toString", but a Java record's *default* `toString` prints every component. Nothing logs these objects today: I checked every `log.*` call in scope. A future `log.debug("{}", payload)` would leak the key.
- **Recommendation:** Override `toString()` to redact, or leave a code-review note.

### L-5. Outbound auditing must stay off (item 5)
- **Severity:** Low (configuration guard).
- **Issue:** `company.logging.database-logging.outbound-enabled` is unset, which defaults to `false`, in all five profiles; I grep-verified this. If it were enabled, the full DFM payload (key, images) would be written to `api_audit_log` OUTBOUND rows through the truncate-then-mask path (M-1).
- **Recommendation:** Keep it off for onboarding-service until M-1 is fixed in the starter.

### L-6. PMD priority-3 findings (→ Low)
- **Location:** all in-scope main classes.
- **Issue:** About 240 Code Style/Design/Error Prone hits:
  - `MethodArgumentCouldBeFinal`, `LocalVariableCouldBeFinal`, `OnlyOneReturn`, `LawOfDemeter`, `ShortVariable`, `LongVariable`;
  - `MissingSerialVersionUID` on the 3 new exceptions;
  - `CognitiveComplexity`/`CyclomaticComplexity`/`NPathComplexity` in `DfmMemberApiClient` and `DfmOnboardingPayload`;
  - `PreserveStackTrace` at `DfmInvestorServiceImpl.java:132`. This one is intentional: the Base64 decoder exception is not chained into the 400 response;
  - `CompareObjectsWithEquals` at `DfmMemberApiClient.java:97`. Also intentional: a cause-cycle guard.

  None indicates a security defect.

### L-7. SpotBugs Low NM_CONFUSING in generated `DfmAddress` (getter names)
- **Severity:** Low. This is generated code; no action.

## Repo Convention Checks (step 5)
- **JWT: public vs secured matches the spec:** PASS. `@JwtSecured(roles = "APP_DATA_VALIDATION")` (changed from the spec's dedicated `APP_DFM_ONBOARDING` by user decision after Gate 2); authenticated and not public. **Accepted risk:** every consumer that already holds `APP_DATA_VALIDATION` (validation and inbound-DFM callers) can now register investors at DFM.
- **JWT: `permit-all-urls` in every profile:** N/A for adding the path (the endpoint is secured). PASS that `/dfm/investors` is absent from all 5 profiles (verified, and asserted by `DfmMaskingConfigTest`).
- **JWT: no hand-rolled `SecurityFilterChain`/`@EnableWebSecurity`:** PASS. None in scope; the starter's chain is used.
- **JWT: role follows `APP_<SERVICE_CONCERN>`:** PASS (`APP_DATA_VALIDATION`, shared with the existing validation endpoints).
- **Masking: sensitive fields routed through `sensitive-keys`, no hand masking:** PASS after the H-1 fix. No string-manipulation masking exists in feature code. `familyId`/email/mobile are a deliberate policy gap (M-3).
- **Exception handling: no stack trace, SQL or internal URL in responses:** PASS. 5xx use fixed texts; `DfmConfigurationException` names only the property key, and is logged rather than returned.
- **Exception handling: no `HttpStatus` baked into exceptions:** PASS. The status is chosen in the handler from the `DfmInvestorErrorCode` enum.
- **Exception handling: the handler that actually fires is the one expected:** PASS. `DfmInvestorExceptionHandler` is `@Order(-1)` and `GlobalExceptionHandler` is `@Order(0)` (grep-verified). `DfmInvestorApiDispatchTest` proves the narrower advice wins with both registered. `AccessDeniedException` is handled explicitly before the catch-all, which addresses scanner E3.
- **Datasource credentials (`plainPassword` outside dev/test):** N/A for this feature, which adds no datasource. Pre-existing `plainPassword` in preprod/prod is noted below.
- **Datasource `@Qualifier` on multi-bean injection:** PASS. `DfmMemberApiClient` qualifies all four ambiguous beans: `dfmMemberApiWebClient`, `…CircuitBreaker`, `…Retry`, `…TimeLimiter`.
- **SQL through bound parameters:** N/A. No SQL, entity or repository was added.
- **Liquibase (no secrets in seed data, rollback on destructive changes):** N/A. No changeset was added.
- **Secrets in new config:** PASS. All non-test profiles use `${DFM_MEMBER_API_*:}` with empty defaults. Test uses obvious fakes. `apiCollection.http` additions use `{{authToken}}` and synthetic data. An `https://` base URL is enforced at call time.
- **Caller JWT not forwarded to DFM:** PASS. The client is not an `AbstractRestClient`, and the test asserts there is no `Authorization` header.
- **SSRF (I5):** PASS. The DFM URL comes only from config; no request-controlled URL is used.

## Scope Check (step 2)
`git status --short --untracked-files=all` and `git diff --name-only` (working tree; no commits yet) show:
- 7 modified tracked files: `apiCollection.http`, `specs/apiSpecs.yaml` and the five `application-*.yml`;
- 20 untracked source/test files: 14 main and 6 test;
- the feature-directory docs.

This matches the 27 entries of `implementation-report.md` "Files Touched" **exactly**. There was **no undisclosed scope creep**, and `GlobalExceptionHandler.java`, `OnboardingController.java`, `db/changelog/**`, `pom.xml`, `infra/` and `.github/` are unchanged. Tracked `target/` noise was ignored.

This audit itself edited only in-scope files:
- the five profile YAMLs (`- tin`);
- `src/test/java/com/alramz/config/DfmMaskingConfigTest.java`;
- this report.

## Automated Tooling Summary
- **`spring-boot-security` skill (`scripts/scan.py services/onboarding-service`):** 9 CRITICAL, 2 IMPORTANT and 4 SUGGESTION leads.
  - S1 (literal secret, line 40/44 `cipher.password: UseMostSecretKey`) and S3 (`management.endpoints.web.exposure.include: "*"`, line 10/14) are on **pre-existing lines this feature did not change**. They are out of scope and listed below.
  - S6 at `DfmInvestorExceptionHandler.java:76,82` was reviewed and classified as L-2.
  - E3 (catch-all handler): `AccessDeniedException` is handled explicitly. Dismissed.
  - D1: no dependency scanner in the build. See the OWASP dependency-check entry below.
  - Manual review found what the regex cannot: H-1, M-1, M-2 and L-4.
- **Checkstyle** (`verify … -Dcheckstyle.consoleOutput=true`), in-scope files only: 1 finding. `NPathComplexity` 612 at `DfmMemberApiClient.java:128` is Medium (M-4). There are no style violations in in-scope files. The `UnusedImports` hit in `DataValidationController.java` is out of scope.
- **PMD** (`-Dpmd.skip=false`), 824 module-wide, filtered to in-scope files using `target/pmd.xml`:
  - **0 hits from `category/java/security.xml`.** The ruleset is active (`config/pmd.xml:12`), with 0 hits module-wide.
  - 6 GuardLogStatement at priority 2 (Medium, M-7).
  - About 240 priority-3 hits (Low, L-6).
  - None indicates a security defect.
- **SpotBugs** (`-Dspotbugs.skip=false`), 162 module-wide, filtered:
  - feature classes: EI_EXPOSE_REP/REP2 at Normal (Medium, M-5), and one NP_NULL_ON_SOME_PATH false positive at Normal (Medium, M-6);
  - generated `DfmAddress`: NM_CONFUSING at Low (L-7);
  - 0 High in scope.
  - This is **plain SpotBugs core with no FindSecBugs**, so security bug-pattern coverage is limited. Treat this as a tooling gap, not a claim of full coverage.
- **OWASP dependency-check** (`mvn org.owasp:dependency-check-maven:check -pl services/onboarding-service`): **could not run.** The NVD update failed (`NvdApiException: Invalid API Key, length of 0`; `NoDataException: No documents exist`). No NVD API key is configured in this environment, so there are no dependency findings to report. The feature added no new dependencies (resilience4j-reactor and webflux were already present, per `dependency:tree` in the implementation report).
- **Final gate:** `mvn -pl services/onboarding-service -am clean test` exited **0**.
  - The log shows javac `Compiling 22 source files … to target/test-classes` for onboarding-service, so this was a real compile, not the IDE race.
  - onboarding-service: 214 run, 0 failures, 0 errors, 1 skipped (the pre-existing `@Disabled AopLoggingIntegrationTest`).
  - starter: 130/0/0/0.
  - `DfmMaskingConfigTest`: 10/10, including the new TIN assertions.

## Residual Risks & Recommendations
**For human review before `05_pr`:**
1. M-1 truncate-then-mask: starter fix (mask-then-truncate). Until then, partial ID-image fragments can reach Seq, traces and `api_audit_log`.
2. M-2: confirm or add a gateway/ingress body limit (about 15 MB) for `/api/v1/dfm/investors`, and revisit heap against expected concurrency.
3. M-3: product decision on masking `familyId`.
4. Release note: the `tin` key (H-1) also masks `fatcaTin` and TIN flags in the existing `/dfm/onboarding` logs and audit rows. This is intended.
5. L-5: never enable `database-logging.outbound-enabled` on this service before M-1 is fixed.
6. Operational: wire `DFM_MEMBER_API_BASE_URL`, `_MEMBER_CODE` and `_SECURITY_KEY` from Key Vault. Until then the endpoint returns 500 DFN012. **Rotate the DFM key that was exposed on Confluence.**
7. Optional hardening: `logReturn = false` (L-1), a redacting `toString` on `DfmOnboardingPayload`/`Success` (L-4), and adding FindSecBugs to the BOM's SpotBugs config.

**Pre-existing, out of scope (documented, not fixed):**
- **Actuator `exposure.include: "*"`** in all onboarding-service profiles, prod included (scanner S3, Critical in isolation). This exposes `env`, `heapdump` and so on. It is amplified by this feature: a heap dump taken while registrations are in flight would contain full ID-document images and the DFM security key. This should be the top follow-up outside this feature.
- `cipher.password: UseMostSecretKey` literal in dev/preprod/prod/test (S1).
- `plainPassword` with literal fallbacks in preprod/prod (`alramz!321*`, brok `RAMZ`).
- The `JWT_SECRET` default fallback.
- Other non-empty credential fallbacks (`ETRADE_CLIENT_SECRET` etc.).
- `AbstractRestClient` forwards the caller's JWT to ETrade/IBAN/VeriPhone/Finoux.
- `ApiAuditAspect` stores `com.alramz.client..*` POJO arguments unmasked.
- `apiCollection.http` carries a committed JWT; this feature added none.
- SpotBugs High `RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE` in `ETradeTokenProvider.java:88` (token path).
- Unused import in `DataValidationController.java`.
