# onboarding-service — static-analysis remediation (03-11-2026)

Queue: 66 actionable rows (FieldNamingConventions rows ignored). 42 fixed, 1 suppressed (queue item), 23 deferred.
Paths below are relative to `services/onboarding-service/src/main/java/com/alramz/`.

## Fixed

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| config/ETradeTokenProvider.java | RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE, NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `if (!"0".equals(response.path("Error_code").asText())) {` | `if (response == null \|\| !"0".equals(response.path("Error_code").asText())) {` | `block()` can return null (empty body); now goes to the existing "return Optional.empty()" path instead of NPE-then-generic-catch |
| config/ETradeTokenProvider.java | AvoidDeeplyNestedIfStmts | `if (log.isDebugEnabled()) { log.debug(...); }` | `log.debug("Returning cached eTrade token (age={}s)", elapsed);` | Guard is redundant with SLF4J placeholders (GuardLogStatement is excluded in config/pmd.xml) |
| OnboardingServiceApplication.java | AvoidDuplicateLiterals | `log.info("   ✓ {} → Type: {} ...", ...)` x5 | `log.info(SETTING_LOG_FORMAT, ...)` | One `private static final` constant |
| OnboardingServiceApplication.java | UseExplicitTypes | `var mapData = refDataRepository.findAsMap(...)` | `Map<String, List<String>> mapData = ...` | Explicit type |
| config/WebClientConfig.java | UseExplicitTypes | `var decoder = new Jackson2JsonDecoder(objectMapper);` | `Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(objectMapper);` | Explicit type |
| config/LiquibaseConfiguration.java | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (x3) | `if (p.getContexts() != null && !p.getContexts().isEmpty())` | `List<String> contexts = p.getContexts(); if (contexts != null && !contexts.isEmpty())` | Getter called twice; null check now covers the dereferenced value (same for labelFilter, parameters) |
| config/ValidationDefinition.java | EI_EXPOSE_REP, EI_EXPOSE_REP2 | `Map<String, String> requestMapping) {}` | `requestMapping = requestMapping == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(requestMapping));` | Defensive copy in compact ctor; LinkedHashMap keeps order and tolerates null values from config binding |
| dfm/DfmOnboardingPayload.java | EI_EXPOSE_REP, EI_EXPOSE_REP2 (Csr) | `record Csr(List<CsrCountry> countries) {}` | `countries = countries == null ? null : List.copyOf(countries);` | Elements are always non-null (`csrCountry()` never returns null) |
| dfm/DfmMemberApiClient.java | CompareObjectsWithEquals, NullAssignment | `t = t.getCause() == t ? null : t.getCause()` | `t = t.getCause()` | `Throwable.getCause()` already returns null when cause==this; guard was redundant |
| dfm/DfmMemberApiClient.java | AvoidLiteralsInIfCondition | `status.value() == 401 \|\| status.value() == 403` | `status.value() == HttpStatus.UNAUTHORIZED.value() \|\| status.value() == HttpStatus.FORBIDDEN.value()` | Named constants (also `"200"` -> `SUCCESS_RESPONSE_CODE` on the next if) |
| exception/GlobalExceptionHandler.java | AvoidDuplicateLiterals (x3) | `setResponseCode("400")`, `"503"`, `Map.of("code", ...)` | `BAD_REQUEST_CODE`, `SERVICE_UNAVAILABLE_CODE`, `CODE_KEY` | `private static final` constants next to existing `ERRORS_KEY` |
| exception/GlobalExceptionHandler.java | ControlStatementBraces (x2) | `if (field == null) return defaultMessage;` | `if (field == null) { return defaultMessage; }` | Braces |
| exception/OnboardingExceptionHandler.java | ControlStatementBraces (x2) | `if (field == null) return DEFAULT_INTERNAL_ERROR_CODE;` | `if (field == null) { return DEFAULT_INTERNAL_ERROR_CODE; }` | Braces |
| exception/ExternalSystemException.java | (enabler for PreserveStackTrace) | only `(String message)` | `+ ExternalSystemException(String message, Throwable cause)` | Repo exception needed a cause ctor |
| exception/TechnicalException.java | (enabler for PreserveStackTrace) | only `(String)`, `(String, String)` | `+ TechnicalException(String message, String errorCode, Throwable cause)` | Repo exception needed a cause ctor |
| client/ETradeClient.java | PreserveStackTrace | `new ExternalSystemException("... unavailable: " + e.getMessage())` | `new ExternalSystemException("... unavailable: " + e.getMessage(), e)` | Keep cause |
| service/impl/DuplicateCheckHelper.java | PreserveStackTrace (x2) | `new ExternalSystemException("NIN duplicate check failed: " + e.getMessage())` | `..., e)` (also "eTrade duplicate check service unavailable", e) | Keep cause |
| service/impl/DuplicateCheckHelper.java | AvoidLiteralsInIfCondition | `if (e.getResponseCode() == 401)` | `if (e.getResponseCode() == HttpStatus.UNAUTHORIZED.value())` | Named constant |
| service/impl/ValidationServiceImpl.java | AvoidLiteralsInIfCondition | `if (e.getResponseCode() == 401)` | `if (e.getResponseCode() == HttpStatus.UNAUTHORIZED.value())` | Named constant |
| service/impl/ValidationServiceImpl.java | PreserveStackTrace | `new ExternalSystemException("eTrade validation service unavailable")` | `..., e)` | Keep cause |
| service/impl/IBANValidationServiceImpl.java | PreserveStackTrace (x2) | `new TechnicalException("Internal Server Error")` | `new TechnicalException("Internal Server Error", "ONB011", e)` | Keep cause; "ONB011" is what the 1-arg ctor already defaulted to (ExternalSystemException likewise gets `, e`) |
| service/impl/PhoneValidationServiceImpl.java | PreserveStackTrace (x2) | same as IBAN | same as IBAN | Keep cause |
| service/impl/OnboardingServiceImpl.java | PreserveStackTrace | `new TechnicalException("Failed to persist ...: " + e.getMessage(), "ONB011")` | `..., "ONB011", e)` | Keep cause |
| service/impl/OnboardingServiceImpl.java | UnusedImports (checkstyle), UnnecessaryImport (PMD) | `import com.alramz.exception.ExternalSystemException;` | (removed) | Unused |
| service/impl/OnboardingServiceImpl.java | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (x2) | `request.getEidNationality() != null && !request.getEidNationality().isBlank()` | `!isBlank(request.getEidNationality())` | Reuses existing `isBlank` helper; single getter call (same for pp) |
| service/impl/OnboardingServiceImpl.java | UseVarargs | `private void logDuplicates(boolean[] duplicates)` | `private void logDuplicates(boolean... duplicates)` | Private method, no API impact |
| service/impl/DfmInvestorServiceImpl.java | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (x2) | `if (r.getCrs() != null && r.getCrs().getCountries() != null) { List<DfmCrsCountry> countries = r.getCrs().getCountries();` | `com.alramz.model.DfmCrs crs = r.getCrs(); List<DfmCrsCountry> countries = crs == null ? null : crs.getCountries(); if (countries != null) {` | Single getter call; FQN used to avoid tipping ExcessiveImports |
| service/impl/DfmInvestorServiceImpl.java | PreserveStackTrace | `throw invalid(field, field + " must be valid Base64");` | `ApplicationException ex = invalid(...); ex.initCause(e); throw ex;` | Keep cause without changing ApplicationException's API |

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| OnboardingServiceApplication.java:globalConfigurationSettingsDemoRunner | PMD.AvoidCatchingGenericException | Startup ApplicationRunner (demo): any failure must be logged, never abort application startup (queue item) |
| config/ValidationDefinition.java:compact ctor | PMD.UnusedAssignment, PMD.NullAssignment | Introduced by the EI fix: compact-ctor parameter reassignment is how a record stores a defensive copy (PMD false positive); null kept intentionally. An `Optional.orElse(null)` variant avoids PMD but SpotBugs then re-flags EI_EXPOSE_REP |
| dfm/DfmOnboardingPayload.java:Csr compact ctor | PMD.UnusedAssignment, PMD.NullAssignment | Same as above |

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| config/ETradeTokenProvider.java (invalidate) | NullAssignment | 2 | null is the "no cached token" sentinel read by `getToken()`; removing needs a holder/Optional refactor of the cache state |
| dfm/DfmOnboardingOutcome.java (Unavailable) | EI_EXPOSE_REP, EI_EXPOSE_REP2 | 2 | Throwable field cannot be copied — accepted |
| service/ValidationRequestMapper.java | EI_EXPOSE_REP2 | 1 | Constructor-injected Spring `ObjectMapper` singleton (false positive). The config/spotbugs-exclude.xml bean regex doesn't include `Mapper`; lead could add `\|Mapper` to that regex. No SpotBugs annotations on classpath |
| dfm/DfmOnboardingPayload.java (from) | CyclomaticComplexity, NPathComplexity | 2 | Flat field mapping with null-ternaries; splitting is a refactor |
| exception/GlobalExceptionHandler.java, OnboardingExceptionHandler.java | CyclomaticComplexity | 4 | Switch-mapping methods (`friendlyMessage`, `mapFieldToInternalErrorCode`); would need a lookup-map refactor |
| exception/DfmInvestorExceptionHandler.java, GlobalExceptionHandler.java, service/impl/DfmInvestorServiceImpl.java | TooManyMethods | 3 | Design-level; out of scope |
| service/impl/DfmInvestorServiceImpl.java | CouplingBetweenObjects | 1 | Design-level |
| repository/DfmOnboardingRepositoryImpl.java | NcssCount | 1 | Long SQL parameter binding method; would need splitting |
| repository/DfmOnboardingRequest.java | TooManyFields | 1 | DTO mirrors DB/JSON shape |
| service/DuplicateCheckService.java, service/impl/DuplicateCheckServiceImpl.java | UseObjectForClearerAPI | 2 | Public interface signature |
| service/impl/IBANValidationServiceImpl.java, PhoneValidationServiceImpl.java, NinTradingNumberValidationService.java, OnboardingServiceImpl.java | CyclomaticComplexity | 4 | Validation branching; would need extraction refactors |

## Verification

Command (in snapshot `/private/tmp/claude-502/-Users-gtalele-Library-CloudStorage-OneDrive-AlRamz-Desktop-workspace-alramz-mw-backend/a07b0375-ec16-4b2d-824f-2dc8d14b9d94/scratchpad/snap-onboarding`):

```
mvn -B -o -pl services/onboarding-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true
```

- Exit code: 0 (BUILD SUCCESS)
- Surefire: Tests run: 259, Failures: 0, Errors: 0, Skipped: 1
- Remaining findings: Checkstyle 0, PMD 20, SpotBugs 3 — all listed under Deferred.
