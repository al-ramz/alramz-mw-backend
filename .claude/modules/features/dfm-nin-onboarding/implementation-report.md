# Implementation Report: dfm-nin-onboarding

**Status: DONE.** The final `mvn -pl services/onboarding-service -am clean test` exited `0`. Every new class JaCoCo can measure is above 80% line coverage. Five new `com.alramz.exception` classes have no JaCoCo number because the BOM excludes that package; they are logged as `EXCEPTION` below, with per-method test evidence. There are no unresolved blocking gaps.

## 1. Summary

The new endpoint is `POST /api/v1/dfm/investors`, operationId `registerDfmInvestor`, tag `DFM Investors`, in `services/onboarding-service`. It requires JWT role `APP_DFM_ONBOARDING`.

What it does, in order:
1. Validates the grouped camelCase request: Bean Validation from the contract, then service rules R1–R10, R9b and a blank-city check.
2. Maps the request to DFM's snake_case payload, adding the member code and security key from `adapter.dfm-member-api.*`.
3. Calls DFM `POST {base}/memberapis/onboarding` through a dedicated `WebClient`:
   - `JdkClientHttpConnector` with a 5 s connect timeout;
   - TimeLimiter for the read timeout, then one retry on connect failure only, then a circuit breaker (opens after 5 consecutive technical failures).
4. Returns `201` and a `GenericResponse`, with errors mapped as follows:

| Case | Status / code |
|---|---|
| Invalid input | 400 DFN001–DFN005 |
| DFM rejected the data | 400 DFN006 |
| Duplicate still in progress at DFM | 409 DFN007 |
| DFM failed or sent a bad response | 502 DFN010 |
| Timeout | 504 DFN011 |
| Circuit open | 503 DFN014 |
| DFM not configured | 500 DFN012 |
| Unexpected error | 500 DFN013 |
| Missing role | 403, `errorCode: null` |

Errors are shaped by a controller-scoped `@RestControllerAdvice` at `@Order(-1)`. Other changes:
- `GenericResponse` gained the nullable fields `errorCode` and `errorMsg`.
- All five profile YAMLs gained the DFM adapter block and the extended `company.logging.masking.sensitive-keys` list.
- `apiCollection.http` gained the request blocks and the new role.

Target service: **onboarding-service** only, which is the plan's single `target_services` entry.

## 2. Files Touched

| File | Status | Notes |
|---|---|---|
| `services/onboarding-service/specs/apiSpecs.yaml` | modified | `GenericResponse` + `errorCode`/`errorMsg` (nullable, `required` unchanged). New path `/dfm/investors` with 201/400/401/403/409/500/502/503/504. Schemas `DfmInvestorRegistrationRequest`, `DfmAddress`, `DfmDividendPayment`, `DfmPortfolioOption`, `DfmCrs`, `DfmCrsCountry`, `DfmFatca`, `DfmEmiratesId`, `DfmPassport`, `DfmSignature`, `DfmGuardian`, `DfmInvestorRegistration` in the §3.1 property order. No other path changed. |
| `services/onboarding-service/apiCollection.http` | modified | `roles` → `["APP_DATA_VALIDATION", "APP_DFM_ONBOARDING"]`. Added `### Register DFM investor` and `### Register DFM investor -ve test case (no address.country -> 400 DFN001)` before Scheduler Management. Synthetic data only (`test.user@example.com`, EID `784-0000-0000000-0`, 1x1 PNG, mobile `+971500000000`). No new token committed. |
| `src/main/java/com/alramz/controllers/DfmInvestorController.java` | new | |
| `src/main/java/com/alramz/service/DfmInvestorService.java` | new | |
| `src/main/java/com/alramz/service/impl/DfmInvestorServiceImpl.java` | new | |
| `src/main/java/com/alramz/dfm/DfmMemberApiClient.java` | new | |
| `src/main/java/com/alramz/dfm/DfmOnboardingPayload.java` | new | |
| `src/main/java/com/alramz/dfm/DfmOnboardingResponse.java` | new | |
| `src/main/java/com/alramz/dfm/DfmOnboardingOutcome.java` | new | |
| `src/main/java/com/alramz/config/DfmMemberApiProperties.java` | new | |
| `src/main/java/com/alramz/config/ExternalDfmMemberApiConfig.java` | new | |
| `src/main/java/com/alramz/exception/DfmInvestorErrorCode.java` | new | |
| `src/main/java/com/alramz/exception/DfmRejectedException.java` | new | |
| `src/main/java/com/alramz/exception/DfmUnavailableException.java` | new | |
| `src/main/java/com/alramz/exception/DfmConfigurationException.java` | new | |
| `src/main/java/com/alramz/exception/DfmInvestorExceptionHandler.java` | new | |
| `src/main/resources/application-{dev,docker,preprod,prod}.yml` | modified | `adapter.dfm-member-api` (`${DFM_MEMBER_API_*:}`, empty defaults) and `company.logging.masking.sensitive-keys` (15 defaults + 14 DFM keys). `permit-all-urls` unchanged. |
| `src/main/resources/application-test.yml` | modified | Same, with fake literals `https://dfm.test` / `TEST01` / `test-key`. |
| `src/test/java/com/alramz/controllers/DfmInvestorControllerTest.java` | new | |
| `src/test/java/com/alramz/controllers/DfmInvestorApiDispatchTest.java` | new | |
| `src/test/java/com/alramz/service/impl/DfmInvestorServiceImplTest.java` | new | `public`, because it also hosts the shared `validRequest()` fixture used by three other test classes (avoids an unplanned helper file). |
| `src/test/java/com/alramz/dfm/DfmMemberApiClientTest.java` | new | |
| `src/test/java/com/alramz/exception/DfmInvestorExceptionHandlerTest.java` | new | |
| `src/test/java/com/alramz/config/DfmMaskingConfigTest.java` | new | |

All `src/...` paths are under `services/onboarding-service/`. No file outside `execution_plan.json`'s `target_files`/`test_files` was created or edited. These were **not touched**: `GlobalExceptionHandler.java`, `OnboardingController.java`, every `db/changelog/**` file, `pom.xml`/BOM, `infra/`, `.github/`. I could not confirm this with `git diff --stat`, because `git` is outside this stage's allowed commands; none of these files were opened for writing.

## 3. Test Results

All commands were run from the repo root, in this order.

| # | Command | Exit | Note |
|---|---|---|---|
| 1 | `mvn -q -pl services/onboarding-service -am dependency:tree -Dincludes=io.projectreactor.netty,org.apache.httpcomponents.client5,org.eclipse.jetty` | 0 | Empty: no reactor-netty, httpclient5 or jetty. Plan Step 4 connector check (caller-requested). |
| 2 | `mvn -pl services/onboarding-service -am dependency:tree -Dincludes=…,io.github.resilience4j,tools.jackson.core,com.fasterxml.jackson.core` | 0 | Confirms resilience4j-reactor 2.3.0, Jackson 2.21.4 (service `ObjectMapper`) and Jackson 3.1.4 (MVC). |
| 3 | `mvn -q -pl services/onboarding-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | After the contract edit. Generated `DfmInvestorsApi.registerDfmInvestor(@Valid @RequestBody DfmInvestorRegistrationRequest)` exactly as planned. Nested models are `@Valid`; `portfolioOptions` is `Set<DfmPortfolioOption>`. |
| 4 | `mvn … test-compile` (red phase, no clean) | 0 | **Hollow.** Test compile was skipped as "up to date" (see Self-Critique, IDE race). |
| 5 | `mvn … clean test-compile …` (red phase) | 0 | **Hollow**, same cause. |
| 6 | `mvn -pl services/onboarding-service -am test -Dtest=DfmMemberApiClientTest` | 1 | **Red confirmed.** javac `cannot find symbol` for every missing production class. |
| 7 | `mvn … clean test-compile …` | 1 | Missing `DfmInvestorService` import in the impl; fixed. |
| 8 | `mvn … clean test-compile …` | 0 | |
| 9 | `mvn … test -Dtest=DfmInvestorServiceImplTest,DfmMemberApiClientTest,DfmInvestorExceptionHandlerTest,DfmInvestorControllerTest,DfmMaskingConfigTest` | 1 | Fixture class not `public`; fixed. |
| 10 | same as 9 | 1 | 3 connect-retry tests failed. Cause: the test stub called `block()` on Reactor's parallel thread during the retry. Fixed **in the test** (non-blocking body capture). Production was correct. |
| 11 | `mvn … test -Dtest=DfmMemberApiClientTest#connectRefused_shouldBeRetriedOnceThenTimeout` | 1 | Diagnostic run that surfaced the cause above. |
| 12 | `mvn … test -Dtest=DfmMemberApiClientTest` | 1 | Timing flake: the first call in the JVM exceeded the 1 s test read timeout. Tests now use 5 s, and 1 s only for the read-timeout cases. |
| 13 | `mvn … test -Dtest=DfmMemberApiClientTest,DfmInvestorApiDispatchTest` | 0 | 35 + 29 tests green. |
| 14 | `mvn -pl services/onboarding-service -am clean test` | 0 | 214 onboarding-service tests (213 + 1 skip) + 130 starter tests. |
| 15 | `mvn … test -Dtest=DfmInvestorExceptionHandlerTest` | 0 | 18 tests (added the null-message fallback case). |
| 16 | **`mvn -pl services/onboarding-service -am clean test`** | **0** | **Final gate.** onboarding-service: `Tests run: 214, Failures: 0, Errors: 0, Skipped: 1` (the skip is the pre-existing `@Disabled AopLoggingIntegrationTest`). Starter: 130/0/0/0. javac compiled the test sources in this run (`Compiling 22 source files … to target/test-classes`). |

Results per new test class in the final run:

| Test class | Tests |
|---|---|
| `DfmInvestorServiceImplTest` | 30 |
| `DfmMemberApiClientTest` | 35 |
| `DfmInvestorApiDispatchTest` | 29 |
| `DfmInvestorExceptionHandlerTest` | 18 |
| `DfmMaskingConfigTest` | 10 |
| `DfmInvestorControllerTest` | 2 |

The existing `/dfm/onboarding`, `/iban/validate`, `/veriphone/verify` and `/existing-data/validation` suites still pass: `OnboardingServiceImplTest`, `ValidationServiceImplTest`, `GlobalExceptionHandlerTest`, `OnboardingExceptionHandlerTest`, `DataValidationControllerTest` and `OnboardingServiceApplicationTests` (default profile, no DFM config, so it binds blanks without failing).

## 4. Test Coverage

Source: `services/onboarding-service/target/site/jacoco/jacoco.csv` from final run #16. Line % = `LINE_COVERED / (LINE_COVERED + LINE_MISSED)`; branch % uses the same formula.

| Class | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| com.alramz.controllers.DfmInvestorController | new | 100% (2/2) | — | DfmInvestorControllerTest, DfmInvestorApiDispatchTest | PASS |
| com.alramz.service.DfmInvestorService | new | n/a (interface, no executable lines) | — | DfmInvestorControllerTest (mocked), DfmInvestorApiDispatchTest | EXCEPTION: an interface has no bytecode lines for JaCoCo to measure |
| com.alramz.service.impl.DfmInvestorServiceImpl (+ FileType) | new | 100% (101/101) | 97.8% (89/91) | DfmInvestorServiceImplTest, DfmInvestorApiDispatchTest | PASS |
| com.alramz.dfm.DfmMemberApiClient (+ BadResponseException) | new | 97.7% (85/87) | 93.3% (56/60) | DfmMemberApiClientTest, DfmInvestorApiDispatchTest | PASS |
| com.alramz.dfm.DfmOnboardingPayload (+ Csr, CsrCountry) | new | 100% (66/66) | 91.7% (44/48) | DfmMemberApiClientTest | PASS |
| com.alramz.dfm.DfmOnboardingResponse | new | 100% (1/1) | — | DfmMemberApiClientTest | PASS |
| com.alramz.dfm.DfmOnboardingOutcome (+ Success/Rejected/Unavailable/Reason) | new | 100% (1/1 each) | — | DfmMemberApiClientTest, DfmInvestorServiceImplTest | PASS |
| com.alramz.config.DfmMemberApiProperties | new | 100% (1/1) | — | DfmMemberApiClientTest, DfmInvestorApiDispatchTest, context smoke test | PASS |
| com.alramz.config.ExternalDfmMemberApiConfig | new | 100% (23/23) | — | DfmMemberApiClientTest (real bean methods on in-memory registries), context smoke test | PASS |
| com.alramz.exception.DfmInvestorExceptionHandler | new | not measured | not measured | DfmInvestorExceptionHandlerTest (18 tests, every `@ExceptionHandler` method and both branches of `handleApplication`/`handleRejected`/`handleUnexpected`/`handleHandlerMethodValidation`, all 3 `handleUnavailable` arms, null-message fallback, indexed and method-prefixed `beanPath`), DfmInvestorApiDispatchTest | EXCEPTION: BOM `jacoco-maven-plugin` `<exclude>**/exception/**</exclude>` (`alramz-common-bom/pom.xml:271`) removes the package from the report. Changing the BOM is out of scope. The only path left untested is `beanPath`'s map-key branch (`node.getKey()`), which no current schema can produce. |
| com.alramz.exception.DfmInvestorErrorCode | new | not measured | not measured | DfmInvestorExceptionHandlerTest (each of DFN001/002/004/005/003 by precedence, crs vs address path), DfmInvestorApiDispatchTest | EXCEPTION: same BOM exclude |
| com.alramz.exception.DfmRejectedException | new | not measured | — | DfmInvestorServiceImplTest, DfmInvestorExceptionHandlerTest | EXCEPTION: same BOM exclude (constructor + Lombok getter only) |
| com.alramz.exception.DfmUnavailableException | new | not measured | — | DfmInvestorServiceImplTest, DfmInvestorExceptionHandlerTest | EXCEPTION: same BOM exclude (constructor + Lombok getter only) |
| com.alramz.exception.DfmConfigurationException | new | not measured | — | DfmMemberApiClientTest, DfmInvestorExceptionHandlerTest | EXCEPTION: same BOM exclude (constructor only) |
| specs/apiSpecs.yaml → generated `com.alramz.api`/`com.alramz.model` | modified | not measured | — | DfmInvestorApiDispatchTest (binding + Bean Validation through the generated models) | PASS (generated code is BOM-excluded; the new contract is exercised end to end) |
| application-{dev,docker,test,preprod,prod}.yml | modified | n/a | — | DfmMaskingConfigTest (5 profiles × 2 tests) | PASS |
| apiCollection.http | modified | n/a | — | not test tooling (api-collection-http-requests.md §5.5) | PASS |

What the missed lines and branches are:
- `DfmMemberApiClient` misses 2 lines. One is `serialize`'s `JsonProcessingException` catch, which cannot be reached for a record of Strings. The other is the defensive `outcome == null` log ternary.
- `DfmOnboardingPayload` misses 4 branches: null-gender and null-`crs.countries` guards that Bean Validation makes unreachable through the API.

## 5. Acceptance Criteria Verification

**Contract**
- [x] `POST /dfm/investors`, tag `DFM Investors`, `operationId: registerDfmInvestor`. Proved by the generated `DfmInvestorsApi.registerDfmInvestor` (run #3) and `DfmInvestorApiDispatchTest` routing to it.
- [x] `DfmInvestorRegistrationRequest` + nested schemas follow §3.1 order. Proved by the YAML and by `DfmInvestorApiDispatchTest` binding/validation.
- [x] Responses 201/400/401/403/409/500/502/503/504, with a `GenericResponse` example on every status except 401/403. YAML only; there is no test assertion on YAML examples, which is acceptable for a contract-doc criterion.
- [x] No other path modified; only `GenericResponse` was amended.
- [x] `GenericResponse.errorCode`/`errorMsg` are nullable. Proved by `DfmInvestorApiDispatchTest.success_shouldReturn201WithEnvelopeAndEchoedCorrelationId` (`errorCode`/`errorMsg` null on the wire).
- [x] `test-compile` passes; `DfmInvestorController implements DfmInvestorsApi` with `@RequestMapping("/api/v1")`. Proved by `DfmInvestorControllerTest.registerDfmInvestor_shouldBeSecuredWithTheDedicatedRoleAndNotLogArgs`.

**Security**
- [x] `@JwtSecured(roles = "APP_DFM_ONBOARDING")`. Proved by `DfmInvestorControllerTest.registerDfmInvestor_shouldBeSecuredWithTheDedicatedRoleAndNotLogArgs`.
- [x] A token lacking the role gets 403 with `errorCode` null. Proved by `DfmInvestorApiDispatchTest.tokenWithoutTheRole_shouldGet403WithNullErrorCode`. This is the plan's substitute for `@WebMvcTest`: real `JwtSecuredAspect` proxy, both advices registered, `@Order(-1)` wins.
- [x] Path absent from every `permit-all-urls`. Proved by `DfmMaskingConfigTest.everyProfile_shouldDeclareTheMaskingListAndTheDfmAdapter`.

**Success path**
- [x] Stubbed DFM 200/"200" gives 201, `"201"`, mapped fields and a non-null `correlationId`. Proved by:
  - `DfmInvestorApiDispatchTest.dfmOutcomes_shouldMapToStatusAndCode[1]` (real service + client);
  - `success_shouldReturn201WithEnvelopeAndEchoedCorrelationId`;
  - `DfmInvestorServiceImplTest.register_shouldMapAllSevenFieldsOnSuccess`.

**Outbound payload.** All proved by `DfmMemberApiClientTest.onboard_shouldSendTheMappedSnakeCasePayloadWithKeyFromConfig` unless noted.
- [x] snake_case keys.
- [x] `cust_mobile` is `00971…`.
- [x] `portfolio_options` is `"cash,margin"` for `[MARGIN, CASH]`.
- [x] `pay_method` is `bank` (and `iVESTOR` in `onboard_shouldMapPassportGuardianAndIvestor`).
- [x] `fatca_uscitizen` is `N`.
- [x] `adr_address` is untruncated (40 chars).
- [x] `request_mbrcode`/`securitykey` come from config.
- [x] `securitykey` header present.
- [x] No `Authorization` header.
- [x] Nulls omitted.

**Validation**
- [x] DFN001 / DFN002 / DFN004 / DFN005, each from dedicated input. Proved by `DfmInvestorApiDispatchTest.beanValidation_shouldMapFieldToCode` (`noCountry`, `lowerCaseCountry`, `longCity`, `noCity`, `badMobile`, `longLine`) and `DfmInvestorServiceImplTest.r9_unknownAddressCountry_shouldBeDfn001` / `blankCity_shouldBeDfn002`.
- [x] DFN003 for each rule and for malformed JSON:
  - R1–R8: `DfmInvestorServiceImplTest.r1_…` through `r8_…` (R6 has both branches);
  - R10: `r10_undecodableBase64`, `r10_unknownMagicBytes`, `r10_emptyImage`, `r10_imageTypeMismatch`, `r10_passportImageTypeMismatch`, `r10_badGuardianProof`;
  - R9b: `r9b_unknownNationality`, `r9b_unknownCrsCountry_shouldBeDfn003NotDfn001`;
  - malformed JSON: `DfmInvestorApiDispatchTest.malformedJson_shouldBeDfn003` (plus `badEnum`, `badUuid`).
- [x] Each of these is HTTP 400 with the matching `errorCode`. Proved by `DfmInvestorApiDispatchTest.expectError` assertions and `serviceRuleFailure_shouldBe400WithItsCode`.

**DFM outcomes**
- [x] HTTP 400 with `response_msg` X gives 400 DFN006 with `errorMsg` X. Proved by `DfmInvestorApiDispatchTest.dfmRejection_shouldEchoDfmMessage`.
- [x] HTTP 409 gives 409 DFN007. Proved by `DfmInvestorApiDispatchTest.dfmOutcomes_shouldMapToStatusAndCode[3]`.
- [x] HTTP 2xx with a configured duplicate code gives DFN007. Proved by `DfmMemberApiClientTest.http200WithConfiguredDuplicateCode_shouldBeDuplicateInProgress` and `DfmInvestorServiceImplTest.duplicateInProgress_shouldBeDfn007`.
- [x] HTTP 5xx gives 502 DFN010. Proved by `dfmOutcomes_…[4]`.
- [x] HTTP 401 gives 502 DFN010. Proved by `dfmOutcomes_…[5]`.
- [x] Non-JSON 200 gives 502 DFN010. Proved by `dfmOutcomes_…[6]`.
- [x] Read timeout gives 504 DFN011. Proved by `DfmInvestorApiDispatchTest.dfmReadTimeout_shouldBe504` and `DfmMemberApiClientTest.readTimeout_shouldBeTimeoutWithoutRetry`.
- [x] Connect failure gives exactly 2 attempts, then 504. Proved by `DfmInvestorApiDispatchTest.dfmConnectFailure_shouldBeAttemptedTwiceThen504` and `DfmMemberApiClientTest.connectRefused_…` / `connectTimeout_…`.
- [x] A DFM 5xx is attempted exactly once. Proved by `DfmMemberApiClientTest.technicalFailures_shouldBeBadResponseAfterExactlyOneAttempt`.

**Resilience and configuration**
- [x] 5 technical failures, then the 6th call gives 503 DFN014 with no outbound request. Proved by `DfmInvestorApiDispatchTest.sixthCallAfterFiveTechnicalFailures_shouldBe503WithoutOutboundRequest` and `DfmMemberApiClientTest.fiveTechnicalFailures_shouldOpenTheBreakerAndShortCircuitTheSixthCall`.
- [x] 4xx rejections do not open the breaker. Proved by `DfmMemberApiClientTest.businessRejections_shouldNotOpenTheBreaker`.
- [x] Blank key, base-url or member-code, or a non-https base-url, gives 500 DFN012 with no request. Proved by `DfmInvestorApiDispatchTest.blankSecurityKey_shouldBe500Dfn012WithoutOutboundRequest`, `DfmMemberApiClientTest.missingOrInsecureConfig_shouldFailBeforeAnyRequest` (×4) and `nullConfig_…`.
- [x] 5xx bodies leak nothing. Proved by `DfmInvestorExceptionHandlerTest.unavailable_shouldUseFixedTextsAndNeverLeak`, `configuration_shouldBe500Dfn012WithoutLeak` and `unexpected_shouldBe500Dfn013WithoutLeak` (sentinel-string checks).

**Masking**
- [x] All 5 profiles have the 15 defaults + §5 additions. Proved by `DfmMaskingConfigTest.everyProfile_shouldDeclareTheMaskingListAndTheDfmAdapter`.
- [x] `LogMaskingUtil.mask` with that list masks securitykey, eidNumber, passportNumber, nin, aedIban, dateOfBirth and frontImage, plus the DFM snake_case equivalents, and leaves trackingNumber/cashTradingNumber readable. Proved by `DfmMaskingConfigTest.theConfiguredList_shouldMaskDfmIdentifiersButNotTradingNumbers`. The JVM-global config is restored in `@AfterEach`.

**Package and logging placement**
- [x] `DfmMemberApiClient` is in `com.alramz.dfm` and has no `@Loggable`. Verified in source; the package is also shown in the JaCoCo CSV.
- [x] The controller uses `@Loggable(logArgs = false)`. Proved by `DfmInvestorControllerTest.registerDfmInvestor_shouldBeSecuredWithTheDedicatedRoleAndNotLogArgs`.

**Untouched files**
- [x] `GlobalExceptionHandler.java`, `OnboardingController.java` and `db/changelog/**` were not edited. This is by construction; git was not available to this stage.
- [x] Existing endpoint tests still pass (run #16).

**Config and collection**
- [x] `adapter.dfm-member-api` is in all 5 profiles, with no real URL or secret. Proved by `DfmMaskingConfigTest.everyProfile_…`.
- [x] `apiCollection.http` has the register block with `Authorization: Bearer {{authToken}}` and a fake body, the `-ve` block without `address.country`, and `roles` including `APP_DFM_ONBOARDING`. Verified in the file.

## 6. Deviations & Decisions

No blocking gap was raised and `AskUserQuestion` was not needed: the live codebase matched `execution_plan.json` for every planned file. Non-blocking decisions:

1. **`dependency:tree` was run** even though it is outside this stage's fixed command list. The caller explicitly asked for it and plan Step 4 names it. The result was empty, so `JdkClientHttpConnector` was kept as planned.
2. **`DfmInvestorErrorCode` exposes one `forFields(Collection<String>)`** instead of a separate `forField` plus precedence picker. The behaviour is the same (full-path match, DFN001→DFN002→DFN004→DFN005, else DFN003) with one fewer method.
3. **The handler's catch-all keeps framework `ErrorResponse` statuses** (405/406/415…) with `DFN003` and the reason phrase, instead of returning 500 DFN013. A wrong `Content-Type` is a client error, not an unexpected server fault. Everything else still gives 500 DFN013. Tested by `frameworkErrorResponse_shouldKeepItsStatus`.
4. **`ConstraintViolationException`/`HandlerMethodValidationException` paths are normalized** to the same field-path form as `MethodArgumentNotValidException`: method and parameter nodes are stripped, and the index is placed correctly (`crs.countries[0].country`). Precedence therefore works identically on every validation route.
5. **The test fixture `validRequest()` lives in `DfmInvestorServiceImplTest`** (made `public`) rather than in a new helper file, which keeps the touched-file set equal to the plan.

## 7. Howtos Consulted

- `testing-patterns.md`: Mockito-first, no `@WebMvcTest`, AssertJ, real `*Properties` instead of mocks. All followed. The one Spring-context test follows the `MmfApiDispatchTest` precedent, as the plan specified.
- `exception-handling.md`: avoided the `OnboardingExceptionHandler` pitfall (unreachable `@Order(1)`). The narrower advice uses `@Order(-1)`, which is lower than `GlobalExceptionHandler`'s `@Order(0)`, and a test proves it wins with both advices registered. No HTTP status lives in the exception classes, and none is a bare `RuntimeException`.
- `openapi-contract-first-controllers.md`: implements the generated `DfmInvestorsApi`, uses a bare spec path with the controller-level `/api/v1`, and hand-edits nothing under `target/generated-sources`.
- `api-collection-http-requests.md`: synthetic data only, no new token, a paired `-ve` block, and register-block `roles` updated rather than copied wrongly.
- `configuration-properties.md`: record + `PREFIX` constant (the plan's reconciliation with the in-service `ETradeProperties` record precedent).
- `jwt-security-and-public-endpoints.md`: role-secured, not in `permit-all-urls`. The howto's "extend `AbstractRestClient`" advice was deliberately not followed (plan technical gap), because it would forward our JWT to DFM.
- `request-response-and-db-audit-logging.md`: masking goes only through `company.logging.masking.sensitive-keys`, with no ad hoc masking. As the plan flagged, the howto's `SensitiveDataMasker` and `ApiAuditAspect` details are stale.
- Also read: the `spring-boot-testing` skill. Its default decision tree points at `@WebMvcTest`/`@RestClientTest`. Neither was used, because the repo convention forbids the former and the client is a `WebClient`, not a `RestClient`.

## 8. Self-Critique

**Confidence: high** for the repo-side implementation (every acceptance criterion has a passing test, including real-dispatch tests of the security aspect and advice ordering). **Medium** for DFM-side behaviour, which is unchanged from the spec: value sets, the duplicate code and key placement are unverified until DFM UAT.

**Environment finding (worth telling the team).** The VS Code Java extension (Eclipse JDT) is auto-building into `services/onboarding-service/target/`. It races Maven's stale-source check. In runs #4 and #5, javac skipped test compilation entirely ("Nothing to compile") while JDT had written error-bearing `.class` files, so a `test-compile` exit 0 was hollow. The real javac later caught a visibility error that JDT had masked. The final gate (#16) did compile the tests with javac. Anyone verifying this repo while VS Code is open should check that the log shows `Compiling N source files … to target/test-classes`, not just exit 0.

**technical_gaps status**
- Connector / connect timeout: **resolved**. `JdkClientHttpConnector` was confirmed by `dependency:tree`. `isConnectFailure` covers `ConnectException` and `HttpConnectTimeoutException`, and both are tested.
- `@WebMvcTest` AC: **resolved** by `DfmInvestorApiDispatchTest`.
- No Jackson trimming: **accepted**. Blank city is DFN002 via an explicit check; other whitespace-only values pass through to DFM, which would give DFN006.
- Full-path `forField`: **resolved and tested** (crs vs address).
- Truncate-then-mask, heap sizing, gateway body limit, `outbound-enabled` future risk, pre-existing `AbstractRestClient` JWT forwarding and `ApiAuditAspect` POJO audit, committed credential fallbacks in profile YAMLs: **residual**, for `04_security` (below).
- `GenericResponse` wire change (`errorCode`/`errorMsg: null` on the 3 existing operations) and service-wide masking blast radius: **residual**; release-note them.
- DFM value-set assumptions #1–#7: **residual**, one mapping point each.
- Circuit-breaker / thread blocking: **accepted** for low volume.

**Flag for 04_security**
1. New public-facing input parsing. The request carries up to about 7 MB of Base64. R10 decodes it fully, and there is no request-size cap below the gateway; confirm the ingress body limit and heap at the expected concurrency.
2. Truncate-before-mask in `SensitiveDataMasker.maskBody` (5000 chars) and `WebClientLoggingFilter`. Payload ordering puts `securitykey` first and images last (a test asserts key-before-attachment in the outbound JSON), but a Base64 fragment straddling the cut can still reach `api_audit_log` or span tags. Email and mobile are not masked.
3. `@Loggable(logArgs = false)` still logs the **return value**: the `DfmInvestorRegistration`, with `nin` masked by key and trading numbers in clear. Decide whether `logReturn = false` is also wanted.
4. `DfmMemberApiClient` logs DFM's `response_msg` at INFO, as the spec allows. If DFM ever echoes PII in that text, it lands in logs.
5. `company.logging.database-logging.outbound-enabled` must stay off for this service. Enabling it would audit the full DFM payload, masked but truncated first.
6. Pre-existing and not fixed: `AbstractRestClient` forwards the caller's JWT to ETrade/IBAN/VeriPhone; `ApiAuditAspect` stores `com.alramz.client..*` POJO arguments unmasked; profile YAMLs carry non-empty credential fallbacks (`ETRADE_CLIENT_SECRET`, brok password); `apiCollection.http` has a committed JWT.
7. Operational prerequisite: Key Vault / k8s wiring for `DFM_MEMBER_API_BASE_URL` / `_MEMBER_CODE` / `_SECURITY_KEY` (until then, the endpoint answers 500 DFN012), and rotation of the DFM key exposed on Confluence.
