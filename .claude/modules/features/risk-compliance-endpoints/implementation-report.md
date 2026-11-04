# Implementation Report: risk-compliance-endpoints

Stage: `03_implement` · Date: 2026-11-04 · Service: `services/risk-management-service` · Status: **DONE**

## 1. Summary

Implemented the four RiskCompliance routes in `risk-management-service`, contract-first from the amended
`specs/apiSpecs.yaml`.
- **GET routes (new contract):** `GET /api/v1/countries` and `GET /api/v1/country-ratings` return a `GenericResponse`.
  Status codes are 200, 403, 502 (CLS/CRT 001-002), 503 (xxx003) and 500 (xxx004).
- **POST routes (legacy shape):** `POST /api/v1/countries` and `POST /api/v1/countryRatings` always return HTTP 200.
  The outcome is in the inner `responseCode` (`200` / `1018` / `500`). The body uses the `correlationID` spelling and a string `rating`, and omits `response` when there is no data or on failure.
- **Vendor client:** `KnowYourCountryClient` extends `AbstractRestClient`. It uses a dedicated WebClient whose filter sets
  `Authorization: ApiKey <key>`, replacing the inbound bearer token. It has a circuit breaker, one retry (connection errors and 5xx only), and a TimeLimiter from config.
- **Band mapping:** a fixed `RiskBand` enum. Unmapped `risk_level` values cause a WARN, increment `risk.compliance.country.rating.unmapped`, and the country is omitted.
- **Config:** a `knowyourcountry.*` block in every profile. The api-key has no default outside test, so a missing key fails startup.
- **Error routing:**
  - a scoped `GlobalExceptionHandler` at `@Order(0)`.
  - the user-approved unscoped `FrameworkExceptionHandler` at `@Order(1)`, so that `PUT /api/v1/countries` returns 405, not 500.

## 2. Files Touched

| File | Status | In plan? |
|---|---|---|
| `services/risk-management-service/specs/apiSpecs.yaml` | modified | yes (see Deviations D-1 for schema naming) |
| `services/risk-management-service/apiCollection.http` | modified | yes |
| `services/risk-management-service/src/main/resources/application-dev.yml` | modified | yes |
| `services/risk-management-service/src/main/resources/application-docker.yml` | modified | yes |
| `services/risk-management-service/src/main/resources/application-preprod.yml` | modified | yes |
| `services/risk-management-service/src/main/resources/application-prod.yml` | modified | yes |
| `services/risk-management-service/src/main/resources/application-test.yml` | modified | yes |
| `services/risk-management-service/src/test/resources/application.yml` | modified | yes |
| `.../src/main/java/com/alramz/config/KnowYourCountryProperties.java` | new | yes |
| `.../src/main/java/com/alramz/config/KnowYourCountryConfig.java` | new | yes |
| `.../src/main/java/com/alramz/knowyourcountry/KnowYourCountryRecord.java` | new | yes |
| `.../src/main/java/com/alramz/knowyourcountry/KnowYourCountryResult.java` | new | yes |
| `.../src/main/java/com/alramz/knowyourcountry/KnowYourCountryClient.java` | new | yes |
| `.../src/main/java/com/alramz/knowyourcountry/RiskBand.java` | new | yes |
| `.../src/main/java/com/alramz/exception/ErrorCode.java` | new | yes |
| `.../src/main/java/com/alramz/exception/RiskComplianceException.java` | new | yes |
| `.../src/main/java/com/alramz/exception/GlobalExceptionHandler.java` | new | yes |
| `.../src/main/java/com/alramz/exception/FrameworkExceptionHandler.java` | new | yes (approved at HITL Gate 1) |
| `.../src/main/java/com/alramz/service/RiskComplianceService.java` | new | yes |
| `.../src/main/java/com/alramz/controllers/RiskComplianceController.java` | new | yes |
| `.../src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java` | new | yes |
| `.../src/test/java/com/alramz/knowyourcountry/RiskBandTest.java` | new | yes |
| `.../src/test/java/com/alramz/knowyourcountry/KnowYourCountryClientTest.java` | new | yes |
| `.../src/test/java/com/alramz/config/KnowYourCountryPropertiesTest.java` | new | yes |
| `.../src/test/java/com/alramz/service/RiskComplianceServiceTest.java` | new | yes |
| `.../src/test/java/com/alramz/controllers/RiskComplianceControllerTest.java` | new | yes |
| `.../src/test/java/com/alramz/controllers/RiskComplianceLegacyControllerTest.java` | new | yes |
| `.../src/test/java/com/alramz/model/LegacyEnvelopeContractTest.java` | new | yes |
| `.../src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java` | new | yes |
| `.../src/test/java/com/alramz/exception/FrameworkExceptionHandlerTest.java` | new | yes |

The following were not touched: any file outside these, `pom.xml`, `alramz-api-starter`, `alramz-common-bom`,
`infra/`, `.github/`, `k8s/`, any Liquibase changeset, and `RiskManagementServiceApplicationTests`.

## 3. Test Results

All commands were run from the repo root. Targeted runs added `-Dsurefire.failIfNoSpecifiedTests=false`.
That flag is needed because `-am` also builds the starter/BOM modules, which have no matching test classes. It only changes how surefire behaves.

| # | Command | Exit | Note |
|---|---|---|---|
| 1 | `mvn -q -pl services/risk-management-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | After the YAML amendment. Generated interfaces and models were inspected (A3 confirmed). |
| 2 | `mvn -pl services/risk-management-service -am test -Dtest=RiskBandTest ...` | 1 | **Red phase**: the new tests do not compile because no production class exists yet (expected). |
| 3 | `mvn -q -pl services/risk-management-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | After implementation. |
| 4 | `mvn -pl services/risk-management-service -am test -Dtest=<all 9 new classes> ...` | 1 | Two failures: (a) a `text/html` 200 arrives wrapped in `WebClientResponseException`, so the classifier was fixed; (b) Jackson 3 sorts properties alphabetically, so the test assertion was changed to any order (see Self-Critique). |
| 5 | `mvn -pl services/risk-management-service -am test -Dtest=KnowYourCountryClientTest,LegacyEnvelopeContractTest ...` | 0 | Green. |
| 6 | **`mvn -pl services/risk-management-service -am clean test`** | **0** | **Final gate.** 92 tests in risk-management-service (0 failures, 0 errors), including `RiskManagementServiceApplicationTests.contextLoads`. 252 starter tests are also green. |

## 4. Test Coverage

Source: `services/risk-management-service/target/site/jacoco/jacoco.csv` from run #6.

**Important caveat:** the BOM's JaCoCo config (`alramz-common-bom/pom.xml`, `<excludes>`) excludes
`**/exception/**`, `**/model/**` and `**/api/**` from instrumentation. Because of that, the four
`com.alramz.exception` classes produce no coverage row. Changing the BOM is out of scope, so those rows are marked `EXCEPTION`. Each one has a dedicated test class that exercises every public method and every branch listed below.

| Class | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| com.alramz.config.KnowYourCountryProperties | new | 100% (2/2) | — | KnowYourCountryPropertiesTest, KnowYourCountryClientTest | PASS |
| com.alramz.config.KnowYourCountryConfig | new | 100% (22/22) | 100% (6/6) | KnowYourCountryClientTest (built through the real bean methods) | PASS |
| com.alramz.knowyourcountry.KnowYourCountryRecord | new | 100% (1/1) | — | KnowYourCountryClientTest | PASS |
| com.alramz.knowyourcountry.KnowYourCountryResult (+ Success/Rejected/Malformed/Unavailable) | new | 100% (6/6) | — | KnowYourCountryClientTest, RiskComplianceServiceTest | PASS |
| com.alramz.knowyourcountry.KnowYourCountryClient | new | 96.0% (24/25) | 90.0% (18/20) | KnowYourCountryClientTest | PASS |
| com.alramz.knowyourcountry.RiskBand | new | 100% (17/17) | 100% (6/6) | RiskBandTest | PASS |
| com.alramz.service.RiskComplianceService (+ Codes) | new | 95.5% (42/44) | 100% (8/8) | RiskComplianceServiceTest | PASS |
| com.alramz.controllers.RiskComplianceController | new | 100% (8/8) | — | RiskComplianceControllerTest | PASS |
| com.alramz.controllers.RiskComplianceLegacyController (+ Outcome) | new | 100% (58/58) | 90.0% (27/30) | RiskComplianceLegacyControllerTest, FrameworkExceptionHandlerTest | PASS |
| com.alramz.exception.ErrorCode | new | n/a (excluded by BOM `**/exception/**`) | n/a | GlobalExceptionHandlerTest (all 8 codes: status/code/message), RiskComplianceLegacyControllerTest (`isVendorRejection` true and false) | EXCEPTION: not instrumented by BOM config; fully exercised by tests |
| com.alramz.exception.RiskComplianceException | new | n/a (excluded by BOM) | n/a | GlobalExceptionHandlerTest, RiskComplianceServiceTest | EXCEPTION: not instrumented by BOM config; fully exercised |
| com.alramz.exception.GlobalExceptionHandler | new | n/a (excluded by BOM) | n/a | GlobalExceptionHandlerTest (RiskCompliance x8, 403, ErrorResponse→405 + Allow header, generic 500 without leaking the message) | EXCEPTION: not instrumented by BOM config. The only untested branch is the `reasonPhrase` fallback for a non-standard status code. |
| com.alramz.exception.FrameworkExceptionHandler | new | n/a (excluded by BOM) | n/a | FrameworkExceptionHandlerTest (PUT→405, legacy POST 415→200+"500" for both routes, non-legacy 415, 406, 404) | EXCEPTION: not instrumented by BOM config; both branches exercised |
| specs/apiSpecs.yaml (generated `com.alramz.api.*`, `com.alramz.model.*`) | modified | n/a (excluded by BOM `**/model/**`, `**/api/**`) | n/a | LegacyEnvelopeContractTest (wire shape); both controller tests compile against the interfaces | PASS (generated code) |
| application-{dev,docker,preprod,prod,test}.yml, src/test/resources/application.yml | modified | n/a (config) | n/a | KnowYourCountryPropertiesTest (binding/fail-fast); contextLoads (test resources block) | PASS |
| apiCollection.http | modified | n/a (manual aid) | n/a | none (not test tooling per howto) | PASS |

Lines and branches not covered in instrumented new classes:
- **`RiskComplianceService`:** the `map()` catch that turns a mapping `RuntimeException` into xxx004. It is defensive per the plan. It is unreachable today because `Success` uses `List.copyOf`, which rejects null rows. A test for it was written, found to be unreachable, and removed rather than faked.
- **`KnowYourCountryClient`:** the `WebClientResponseException` with a null cause.
- **`RiskComplianceLegacyController`:** the `uri == null` guards.

## 5. Acceptance Criteria Verification

**Contract and codegen**
- [x] `apiSpecs.yaml` is amended, not replaced. `/api/v1/info` and the root `security` are unchanged. Proven by run #1 (codegen) and the existing `InfoApi` still being generated.
- [x] `GenericResponse` gains `correlationId` (uuid), `errorCode` and `errorMsg` (nullable). Proven by `GlobalExceptionHandlerTest`, which sets and asserts all three.
- [x] The 8 schemas are added: `Country`, `CountryListResponse`, `CountryRating`, `CountryRatingResponse`, `LegacyRiskComplianceRequest`, `LegacyCountryRating`, `LegacyCountryListEnvelope` and `LegacyCountryRatingEnvelope`. A helper schema, `LegacyCountryRatingData`, was also added (see D-1).
- [x] Bare paths `/countries` (get + post), `/country-ratings` (get) and `/countryRatings` (post) are used. The operationIds are `getCountries`, `getCountryRatings`, `getCountriesLegacy` and `getCountryRatingsLegacy`. Proven by the generated `PATH_*` constants and the controllers compiling.
- [x] Each GET has examples for 200 (success and empty), 403, 500, 502 (both codes) and 503, plus a 401 description. Each POST has 200 examples (success, empty, 1018, 500) and 401/403 descriptions.
- [x] `clean test-compile` succeeds. Both controllers implement the generated interfaces with `@RequestMapping("/api/v1")` (runs #1, #3 and #6).

**Security**
- [x] All 4 methods carry `@JwtSecured(roles = "APP_RISK_MANAGEMENT")` and `@Loggable` (verified in the code).
- [x] `permit-all-urls` is unchanged in every profile (only the `knowyourcountry` block was appended).
- [~] **"A request without a JWT gets 401"** is not verified by an automated test, by design (technical_gaps[1]). It is starter-owned and likely 403 today. The manual check needs a running app, which is outside this stage's allowed commands. It is carried as residual.
- [x] "A JWT without the role gets 403 with `GenericResponse` `Access denied`": `GlobalExceptionHandlerTest.handleAccessDenied_shouldReturn403`.
- [x] `apiCollection.http` registers `APP_RISK_MANAGEMENT` and has a block for each of the 4 routes, plus the PUT 405 and malformed-JSON edge cases.

**Behaviour**
- [x] Band table and empty cases (`null`, `""`, `higher risk`, `Higher Risk `, `Low Risk`): `RiskBandTest` (12 cases).
- [x] `GET /countries` returns 200 / "OK" with null errorCode and errorMsg, a UUID correlationId, and the countries mapped in vendor order:
  - `RiskComplianceControllerTest.getCountries_shouldWrapTheListInAnOkEnvelope`
  - `RiskComplianceServiceTest.getCountries_shouldMapEveryRowInVendorOrder`
- [x] `GET /country-ratings` returns an integer rating and an H/M/L riskLevel. An unmapped row is absent, with one WARN and one counter increment per row:
  - `RiskComplianceServiceTest.getCountryRatings_shouldMapEachBand`
  - `RiskComplianceServiceTest.getCountryRatings_shouldOmitUnmappedRowsWithOneWarnAndOneCounterIncrementEach` (asserts the WARN text through a logback `ListAppender` and that the counter equals 2)
- [x] A vendor `[]` gives an empty array, not null:
  - `KnowYourCountryClientTest.shouldReturnAnEmptySuccessForAnEmptyArray`
  - `RiskComplianceControllerTest.*_shouldReturnAnEmptyArrayNotNull`
- [x] GET failures return 502/502/503/500 with CLS/CRT codes, the generic `errorMsg` and `response: null`:
  - `GlobalExceptionHandlerTest.handleRiskCompliance_shouldMapEachCode` (8 rows)
  - `RiskComplianceServiceTest` (Rejected / Malformed / Unavailable / thrown → 001 / 002 / 003 / 004)
  - `KnowYourCountryClientTest` (4xx, 5xx, invalid JSON, JSON object, empty body, HTML, connection error, timeout and open circuit, each classified)
- [x] Legacy POST always returns HTTP 200 with the exact shape:
  - `RiskComplianceLegacyControllerTest` (success, empty → response null, CLS001/CRT001 → 1018 plus the endpoint's message, xxx002/003/004 and an untyped failure → 500)
  - `LegacyEnvelopeContractTest`:
    - `correlationID` spelling.
    - `response` key **absent** (not null).
    - no `errorCode`, `errorMsg` or `correlationId` keys.
    - `rating` is a JSON string.
- [x] Legacy correlation ID: `abc-123` is echoed and put in MDC. Null, `""` and `"   "` fall back to the MDC (header) value, and a UUID is generated when neither exists (`RiskComplianceLegacyControllerTest`).
- [x] `CorrelationIdResponseBodyAdvice` does not modify the legacy body: `LegacyEnvelopeContractTest.correlationIdAdvice_shouldLeaveTheLegacyCorrelationIdUnchanged` and `envelopes_shouldExposeNoSetterTheStarterAdviceCanFind`.
- [x] Exactly one `Authorization: ApiKey test-api-key`, with the inbound Jwt bearer not forwarded: `KnowYourCountryClientTest.shouldSendOnlyTheApiKeyAndNeverForwardTheInboundBearer`. The test uses a real Jwt principal and a non-local environment.
- [x] A connection error is retried exactly once (2 calls). A 4xx and a decoding error are not retried (1 call). A 5xx is retried once: `KnowYourCountryClientTest`.
- [x] `PUT /api/v1/countries` returns 405, not 500: `FrameworkExceptionHandlerTest.putOnCountries_shouldReturn405NotA500` (mapping-time path) and `GlobalExceptionHandlerTest.handleGeneric_shouldKeepAFrameworkStatusInsteadOfReturning500` (handler-resolved path). Both are unit-level. No end-to-end dispatch test exists, per the repo's convention against MockMvc.

**Config**
- [x] The `knowyourcountry.*` keys are present in `application-{dev,docker,test,preprod,prod}.yml` and in `src/test/resources/application.yml`.
- [x] No real key is committed. Non-test profiles use `${KNOWYOURCOUNTRY_API_KEY}` with no default.
- [x] The context fails to start when api-key is blank or missing, or base-url is blank: `KnowYourCountryPropertiesTest`.
- [x] No Liquibase changeset, entity, datasource, `pom.xml` dependency or starter change was added.
- [x] `mvn -pl services/risk-management-service -am clean test` passes, including `contextLoads` (run #6).

## 6. Deviations & Decisions

No blocking gap was raised in this run. `FrameworkExceptionHandler` (technical_gaps[0]) and the
fail-fast `KNOWYOURCOUNTRY_API_KEY` were approved by the user at HITL Gate 1 on 2026-11-04. Non-blocking implementation choices:

- **D-1, schema naming.** The plan described the legacy envelopes' `response` as inline objects. I made two changes instead:
  - `LegacyCountryListEnvelope.response` reuses `CountryListResponse`, which has the identical wire shape `{countryList: Country[]}`.
  - `LegacyCountryRatingEnvelope.response` references a new named schema, `LegacyCountryRatingData` `{countryRating: LegacyCountryRating[]}`. This avoids a generator-invented `LegacyCountryRatingEnvelopeResponse` class.

  The wire format is unchanged.
- **D-2, client classification.** A 2xx with a non-JSON content type arrives as a `WebClientResponseException`
  whose cause is `UnsupportedMediaTypeException`. The classifier therefore looks one cause deep before deciding
  Malformed. This is the plan's intent (technical_gaps[3]); it was discovered by test.
- **D-3, handler additions not in the plan text:**
  - `GlobalExceptionHandler` logs at WARN for `RiskComplianceException` and at ERROR for the catch-all. The utility original logs neither, so an unexpected 500 would leave no trace.
  - Framework-error responses carry the exception's own headers, for example `Allow` on 405.
  - `FrameworkExceptionHandler` reuses `GlobalExceptionHandler`'s package-private builder instead of duplicating it.
- **D-4, apiCollection order.** The PUT -ve block sits right after `### Get countries`, and the malformed-JSON -ve
  block right after `### Get countries (legacy)`. This follows the howto's paired-negative convention.

## 7. Howtos Consulted

- `testing-patterns.md`: Mockito-first. Controllers and handlers are constructed directly, with no `@WebMvcTest` or MockMvc. `contextLoads` was left untouched.
- `exception-handling.md`:
  - The pitfall about `OnboardingExceptionHandler`'s unreachable `@Order` was avoided. The broad scoped handler is `@Order(0)`, and the unscoped framework handler is `@Order(1)` and declares only four framework types with no catch-all, so it never overlaps the scoped handler.
  - The rule "no HTTP status on exception classes" was kept: the status lives on the `ErrorCode` enum, which the plan allows.
- `openapi-contract-first-controllers.md`: both controllers implement the generated interfaces. Bare YAML paths are used with a controller-level `/api/v1`. Nothing under `target/generated-sources` was edited. The existing `InfoApi` dead-interface pitfall was left as-is (out of scope) and not copied.
- `api-collection-http-requests.md`:
  - The role was fixed to `APP_RISK_MANAGEMENT` (pitfall 4).
  - `@authToken` stays an empty placeholder, so no token was committed.
  - Synthetic values only.
- `configuration-properties.md`: Lombok `@Getter`/`@Setter`, a `PREFIX` constant, bound through `@EnableConfigurationProperties` on a `@Configuration`. No `@Component` or `@Value`.
- `jwt-security-and-public-endpoints.md`: the routes are secured by `@JwtSecured`. `permit-all-urls` is unchanged in every profile.
- `request-response-and-db-audit-logging.md`: no masking keys were added, because `authorization`/`api_key` already cover the vendor header. The vendor body is never logged by service code.
- `new-endpoint-elicitation-checklist.md`: listed in the plan's `howtos_consulted`. It was not re-read in this stage because it is an elicitation-stage checklist with no implementation rules.

## 8. Self-Critique

**Confidence: high** for the GET contract, vendor client, band logic and config. **Medium** for the error-routing edge (405/415 at mapping time) and legacy wire fidelity. Both are verified only by direct unit calls, not by a real dispatcher.

**Residual and non-blocking risks:**
1. **Vendor 4xx/5xx with an EMPTY body is misclassified as Malformed (CLS002 / legacy "500"), not Rejected (CLS001 / legacy "1018").** This is newly found and not in the plan. The cause is in the starter: `AbstractRestClient`'s `onStatus` uses `response.bodyToMono(String.class).flatMap(...)`. An empty error body yields an empty Mono, and WebClient then treats the response as non-error with no body.
   - Practical case: a revoked key returning a bare 401.
   - Fix: a one-line starter change, `.defaultIfEmpty("")` before `flatMap`. That is out of scope here (no starter edits), so it is recommended as a starter ticket, alongside technical_gaps[0] and [1].
   - No test pins the current behaviour.
2. The 401-for-missing-JWT AC (technical_gaps[1]) has not been verified. The manual `apiCollection.http` check against a running app (with `KNOWYOURCOUNTRY_API_KEY` exported) is still to be done. If the result is 403, correct the 401 descriptions in the YAML.
3. **JSON property order.** Boot 4's Jackson 3 sorts properties alphabetically by default, so the legacy body is written as `correlationID, response, responseCode, responseMessage`. That may differ from the webMethods order. JSON order is not semantically meaningful, but a byte-level diff against captured legacy traffic (A1/A2 check) would show it.
4. PMD and SpotBugs were not run, because `verify` is out of this stage's scope.
   - `// NOPMD AvoidCatchingGenericException` reasons are on the three deliberate `RuntimeException` catches (client `block()`, service boundary, legacy controller).
   - The pattern variables `rejected` and `malformed` in the service's sealed switch are unused and may trigger an unused-variable rule.
5. Carried unchanged from the plan: A1, A2, A4, A5, A6, A7, C1, C2 (APIM must supply a JWT), N1-N3, and ops provisioning of `KNOWYOURCOUNTRY_API_KEY` before deploy (the service crash-loops without it). F-01 key rotation is still required.

**technical_gaps status:**
- **[0] Resolved** by `FrameworkExceptionHandler` plus the `instanceof ErrorResponse` catch-all.
- **[1] Residual.**
- **[2] Unchanged.** A body without Content-Type returns legacy "500" through the 415 path.
- **[3] Resolved** (D-2).
- **[4]–[19] Carried as documented.** The starter follow-ups recommended there are unchanged, plus risk 1 above.

**Flag for 04_security:**
- **Legacy body `correlationID`.** It is echoed verbatim, with no length or charset limit, and written to MDC, which means:
  - It reaches logs (log injection; same exposure as the existing header path, C1).
  - It can be forwarded by the starter WebClient filter as the outbound `X-Correlation-ID` to the vendor. CR/LF or very long values should be reviewed.
- **Vendor-supplied `risk_level` and `iso2`** are written into a WARN line (log-injection surface from a third party). They use placeholders, not concatenation.
- **New outbound integration.** The vendor ApiKey travels in the `Authorization` header.
  - It is masked by the existing `Authorization` header mask and by `SensitiveDataMasker`'s `authorization` key. The audit/WebClient filter's masking of an outbound request header should be verified at runtime.
  - The inbound JWT is no longer forwarded to the vendor (the filter replaces it), and a test proves this.
- **Unscoped `FrameworkExceptionHandler`.** It changes the 404/405/406/415 response shape for every path in this service, including the starter's `/api/auth/*`, from the starter's 500 map to a `GenericResponse` with the correct status. No message or stack trace is echoed.
- **`include-response-payload: true`.** The full vendor country list is stored in `api_audit_log` on every call (N3; a volume concern, not sensitive data).
