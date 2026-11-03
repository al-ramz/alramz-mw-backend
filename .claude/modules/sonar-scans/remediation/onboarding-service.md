# onboarding-service: Sonar code-smell remediation

Verification: `mvn -B -pl services/onboarding-service clean test` gives **Tests run: 259, Failures: 0, Errors: 0, Skipped: 1** and BUILD SUCCESS. The baseline had 257 tests. Two tests were added to ValidationServiceImplTest.

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| main/.../client/ETradeClient.java | S1068 (1), S2147 (1) | unused `etradeProperties` field; the `WebClientResponseException` catch had the same body as the `Exception` catch | removed the field and its constructor parameter and import; dropped the redundant catch (the generic catch already handles it the same way) | ETradeClientTest |
| main/.../config/ETradeTokenProvider.java | S1068 (1), S3776 (1) | unused `objectMapper`; `fetchAndCacheToken` complexity 16 | removed the field and its constructor parameter; extracted `cacheToken(JsonNode resData)` | ETradeTokenProviderTest |
| main/.../controllers/ApplicationController.java | S112 (1), S1130 (1) | `info()` declared `throws Exception` but never throws | removed the `throws` clause | ApplicationControllerTest |
| main/.../controllers/DataValidationController.java | S1128 (5) | unused imports | removed | DataValidationControllerTest |
| main/.../dfm/DfmMemberApiClient.java | S3776 (1) | `classify` complexity 16 | extracted the 2xx branch into `classifySuccessStatus(...)` | DfmMemberApiClientTest |
| main/.../dfm/DfmOnboardingPayload.java | S3776 (1) | `from` complexity 18 | extracted `paymentMethod`, `csr`, `passportImageType` | DfmMemberApiClientTest (payload asserts) |
| main/.../exception/GlobalExceptionHandler.java | S1192 (6) | repeated "ONB011", "errors", "message", "Invalid request", "Malformed JSON request", "Internal server error" | added `private static final` constants with the same values; no response body or status changed | GlobalExceptionHandlerTest |
| main/.../exception/OnboardingExceptionHandler.java | S1192 (1) | repeated "ONB011" | `DEFAULT_INTERNAL_ERROR_CODE` constant | OnboardingExceptionHandler tests |
| main/.../service/impl/DfmInvestorServiceImpl.java | S6878 (2) | type patterns over records | record patterns `Success(var body)` and `Unavailable(var reason, var cause)` | DfmInvestorServiceImplTest |
| main/.../service/impl/DuplicateCheckHelper.java | S1192 (1) | repeated "Reference_No" | `REFERENCE_NO_KEY` constant | DuplicateCheckHelperTest |
| main/.../service/impl/IBANValidationServiceImpl.java | S1128 (2), S1068 (1), S2147 (1), S1854 (6), S1481 (6) | unused imports and a shadowing `objectMapper` field; duplicate catch bodies; six dead `*Code` locals in `applyValidationRules` | removed the dead code (the method now returns the same object it built before); multi-catch `ApiCallFailedException \| WebClientResponseException` | IBANValidationServiceImplTest |
| main/.../service/impl/OnboardingServiceImpl.java | S1157 (2) | `"Y".equals(v.trim().toUpperCase())` | `equalsIgnoreCase` | OnboardingServiceImplTest |
| main/.../service/impl/PhoneValidationServiceImpl.java | S1068 (1), S2147 (1) | shadowing `objectMapper` field; duplicate catch bodies | removed; multi-catch | PhoneValidationServiceImplTest |
| main/.../service/impl/ValidationServiceImpl.java | S1068 (1), S3776 (1), S6880 (1) | unused `etradeProperties`; `validateRequest` complexity 32, if/else chain | removed the field and its constructor parameter; replaced the chain with an enum `switch` (`case null, default` keeps the null/unsupported behaviour) and a `requireField` helper with identical messages | ValidationServiceImplTest (+2 tests: whitespace-only field message, null type message) |
| main/.../service/ValidationRequestMapper.java | S1128 (1) | unused import | removed | ValidationRequestMapperTest |
| test/.../AopLoggingIntegrationTest.java | S1128 (1) | unused import | removed | itself |
| test/.../client/ETradeClientTest.java | (follow-on) | constructor lost the `ETradeProperties` argument | removed the now-unused PROPS and its import | itself |
| test/.../config/DfmMaskingConfigTest.java | S5853 (1) | two assertions on the same subject | chained | itself |
| test/.../config/ETradeTokenProviderTest.java | (follow-on) | constructor lost the `ObjectMapper` argument | updated the call | itself |
| test/.../controllers/ApplicationControllerTest.java | S1128 (1), S5838 (2) | unused import; `get(k)).isEqualTo(v)` | removed; `containsEntry` chain | itself |
| test/.../dfm/DfmMemberApiClientTest.java | S1128 (1), S5961 (1), S5778 (2) | unused import; 34 assertions in one test; client construction and request building inside `assertThatThrownBy` | 23 field asserts collapsed into one expected-vs-actual map comparison (same JSON pointers and values, and a mismatch shows the diff) plus one `noneMatch(body::has)` for the four omitted fields; setup moved out of the lambdas | itself |
| test/.../exception/GlobalExceptionHandlerTest.java | S1128 (1), S8924 (2) | unused import; FQN `Mockito.mock` | removed; static `mock` | itself |
| test/.../repository/DfmOnboardingRepositoryImplTest.java | S5778 (1) | `new OnboardingRequest()` inside the lambda | moved out | itself |
| test/.../scheduler/job/DataValidationScheduledJobTest.java | S1128 (3) | unused imports | removed | itself |
| test/.../service/impl/DfmInvestorServiceImplTest.java | S5778 (4) | `validRequest()` inside the lambda | moved to a local (and the identical unflagged line 301) | itself |
| test/.../service/impl/DuplicateCheckHelperTest.java | S1128 (1), S8924 (4), S4144 (1) | same-package import; FQN mock; `helperForETrade` identical to `helperWithoutNinService` | removed; static `mock`; callers now use `helperWithoutNinService` and the duplicate helper is deleted (no test removed) | itself |
| test/.../service/impl/DuplicateCheckServiceImplTest.java | S1128 (10), S1144 (1), S5853 (1) | unused imports; unused `mockResData`; split assertions | removed; chained `hasSize(4).containsExactly(...)` | itself |
| test/.../service/impl/IBANValidationServiceImplTest.java | S5778 (6) | `request(...)` inside the lambda | moved to a local | itself |
| test/.../service/impl/NinTradingNumberValidationServiceTest.java | S1128 (1) | unused import | removed | itself |
| test/.../service/impl/OnboardingServiceImplTest.java | S1128 (4) | unused imports | removed | itself |
| test/.../service/impl/PhoneValidationServiceImplTest.java | S5778 (6) | `request(...)` inside the lambda | moved to a local | itself |
| test/.../service/impl/ResponseMappersTest.java | S1128 (2) | unused imports | removed | itself |
| test/.../service/impl/ValidationServiceImplTest.java | S1128 (4) | unused imports | removed (plus the constructor call update and the 2 new tests) | itself |
| test/.../service/ValidationRequestMapperTest.java | S1128 (4), S5838 (2) | unused imports; `get(k)).isEqualTo(v)` | removed; `containsEntry` chain | itself |
| test/.../service/ValidationResponseMapperRegistryTest.java | S1128 (3) | unused imports | removed | itself |

## Skipped

| rule | path:line | reason |
|---|---|---|
| S3599 | main/.../client/ETradeClient.java:37 | already fixed by the lead (per the brief) |
| S5738 (x2 each) | main/.../config/ExternalETradeServiceConfig.java:43, ExternalIbanServiceConfig.java:43, ExternalVeriPhoneServiceConfig.java:43, WebClientConfig.java:18/21 | the replacement for `Jackson2JsonDecoder` / `jackson2JsonDecoder(...)` is the Jackson 3 `JacksonJsonDecoder` (tools.jackson). This module decodes into Jackson 2 types: `ETradeTokenProvider` reads `com.fasterxml...JsonNode`, `ETradeResponse` carries a Jackson 2 `JsonNode`, and the configs build Jackson 2 `ObjectMapper`s. Switching the decoder is a Jackson 2 to 3 migration of the WebClient stack, which is a behaviour and design decision, not a smell fix |
| S5738 (x2) | test/.../config/ETradeTokenProviderTest.java:45 | mirrors the production codec setup above, so it should move only together with that migration |
| S1075 (x2) | main/.../exception/GlobalExceptionHandler.java:27,28 | `VALIDATION_PATH`/`ONBOARDING_PATH` are route prefixes that must match the OpenAPI-generated controller mappings. Making them configurable would add a property that can drift from the real routes, which is a design change |
