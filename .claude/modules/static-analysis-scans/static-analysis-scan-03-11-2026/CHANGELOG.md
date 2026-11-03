# CHANGELOG — static-analysis-scan 03-11-2026

Config/ruleset changes first (team decision, approved by the user in-session), then mechanical fixes, then per-module code fixes (copied from `remediation/<module>.md`). Cross-checked against `git diff --name-only` (110 files).

## 1. Configuration (reductions here are accepted, not fixed)

| File | Change | Before -> After | Why |
|---|---|---|---|
| config/pmd.xml | exclude codestyle `MethodArgumentCouldBeFinal`, `LocalVariableCouldBeFinal`, `LongVariable`, `ShortVariable`, `OnlyOneReturn`, `AtLeastOneConstructor`, `CommentDefaultAccessModifier`; design `LawOfDemeter` | `<rule ref="category/java/codestyle.xml"/>` -> `<rule ref=...><exclude .../></rule>` | style-opinion rules, ~1,580 findings of pure churn; user chose "exclude in pmd.xml" |
| config/pmd.xml | exclude bestpractices `GuardLogStatement` | whole category -> category minus rule | every hit already used SLF4J `{}` placeholders with cheap getter args |
| config/pmd.xml | `FieldNamingConventions` exclusions `serialVersionUID,serialPersistentFields,log,logger,auditLogger` | default -> + logger names | repo convention `log` (Lombok @Slf4j, 30 classes) / `logger` |
| config/checkstyle.xml | `ConstantName` format allows `log|logger|auditLogger` | default -> `^([A-Z][A-Z0-9]*(_[A-Z0-9]+)*|log|logger|auditLogger)$` | same logger convention |
| services/alramz-common-bom/pom.xml | spotbugs `<excludeFilterFile>${project.basedir}/../../config/spotbugs-exclude.xml</excludeFilterFile>` | filter file existed but unused -> wired | integration bug: `config/spotbugs-exclude.xml` was never referenced |
| config/spotbugs-exclude.xml | EI_EXPOSE_REP*, NM_CONFUSING, SIC_INNER_SHOULD_BE_STATIC_ANON for `com.alramz.model`/`com.alramz.api` | reported -> excluded | OpenAPI-generated sources (target/generated-sources), not editable |
| config/spotbugs-exclude.xml | EI_EXPOSE_REP2 on constructors of `*Repository|*Service|*Client|*Provider|*Controller|*Job|*Config|*Configuration|*Mapper` | reported -> excluded | Spring singletons storing constructor-injected collaborators |
| config/spotbugs-exclude.xml | EI_EXPOSE_REP* on `*Properties` | reported -> excluded | `@ConfigurationProperties` binding needs the live accessors |

## 2. Mechanical (scripted, no behavior change)

Tabs -> 4 spaces (5 `*Application.java`), CRLF -> LF + newline at EOF (31 files — **whole-file diffs**), trailing whitespace (4 lines), `private static final long serialVersionUID = 1L;` on 13 exception classes. Files touched only by this pass:

- `services/alramz-api-starter/src/main/java/com/alramz/exceptions/ApiCallFailedException.java`
- `services/alramz-api-starter/src/main/java/com/alramz/exceptions/InvalidHttpRequestException.java`
- `services/alramz-api-starter/src/main/java/com/alramz/models/UserRequestDetails.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/EnableScheduler.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/entity/ScheduleJobEntity.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/model/SchedStatus.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/model/ScheduleInfoBean.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/repository/ScheduleJobRepository.java`
- `services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/Schedulable.java`
- `services/onboarding-service/src/main/java/com/alramz/controllers/OnboardingController.java`
- `services/onboarding-service/src/main/java/com/alramz/exception/DfmConfigurationException.java`
- `services/onboarding-service/src/main/java/com/alramz/exception/DfmRejectedException.java`
- `services/onboarding-service/src/main/java/com/alramz/exception/DfmUnavailableException.java`
- `services/onboarding-service/src/main/java/com/alramz/exception/IbanValidationException.java`
- `services/onboarding-service/src/main/java/com/alramz/scheduler/job/DataValidationScheduledJob.java`
- `services/reference-data-service/src/main/java/com/alramz/ReferenceDataServiceApplication.java`
- `services/reporting-management-service/src/main/java/com/alramz/ReportingManagementServiceApplication.java`
- `services/utility-service/src/main/java/com/alramz/UtilityServiceApplication.java`
- `services/utility-service/src/main/java/com/alramz/exception/FinouxIntegrationException.java`
- `services/utility-service/src/main/java/com/alramz/exception/FinouxUnavailableException.java`
- `services/wealth-management-service/src/main/java/com/alramz/WealthManagementServiceApplication.java`

## 3. Code fixes per module

### alramz-api-starter

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| audit/ApiAuditAspect.java | AvoidStarImport | `import java.util.*;` | `import java.util.LinkedHashMap; ...Map; ...Set; ...UUID;` | explicit imports |
| audit/ApiAuditAspect.java | LineLength (361) | `@Around("execution(...) \|\| execution(...) ...")` | same pointcut split into 5 `" \|\| execution(...)"` concatenated string constants | wrap; pointcut string identical |
| audit/SensitiveDataMasker.java | AvoidStarImport | `import java.util.*;` | `ArrayList, Collection, Enumeration, HashSet, LinkedHashMap, List, Map, Set` | explicit imports |
| client/AbstractRestClient.java | AvoidStarImport | `import org.springframework.http.*;` | `HttpHeaders, HttpMethod, HttpStatus, HttpStatusCode` | explicit imports |
| client/AbstractRestClient.java | LineLength (232) | `return Optional.ofNullable(...).filter(...).map(...).orElseGet(...);` | one call per line | wrap |
| datasource/config/AbstractDataSourceAutoConfiguration.java:54 | LineLength | `logger.info("...", dbName, maskUrl(..), config.getPool()..., ...); // NOPMD` | args wrapped onto 2nd line | wrap |
| datasource/config/AbstractDataSourceAutoConfiguration.java:137 | LineLength | `protected DataSource wrapWithProxy(DataSource, SqlLoggingProperties, String, ObjectProvider<..>) {` | last param on next line | wrap (signature unchanged) |
| datasource/config/AbstractDataSourceAutoConfiguration.java:158 | LineLength | `SLF4JSlowQueryListener slowQueryListener = new SLF4JSlowQueryListener(...)` | wrapped after `=` | wrap |
| globalconfigurationsettings/model/GlobalConfigurationSettings.java | AvoidStarImport | `import jakarta.persistence.*;` | `Column, Entity, GeneratedValue, GenerationType, Id, Index, Table` | explicit imports |
| jwt/annotation/JwtSecured.java, PermitAll.java, RefreshToken.java | AvoidStarImport | `import java.lang.annotation.*;` | `ElementType, Retention, RetentionPolicy, Target` | explicit imports |
| logging/aspect/Loggable.java | AvoidStarImport | `import java.lang.annotation.*;` | `+ Documented` | explicit imports |
| jwt/config/JwtAutoConfiguration.java | AvoidStarImport | `import ...autoconfigure.condition.*;` | `ConditionalOnClass, ConditionalOnWebApplication` | explicit imports |
| jwt/config/JwtAutoConfiguration.java | UnusedImports | `.addFilterBefore(jwtAuthFilter, org.springframework...UsernamePasswordAuthenticationFilter.class)` | `.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` | import was unused because the FQN was used inline; now the import is used |
| jwt/controller/AuthController.java | AvoidStarImport | `import ...web.bind.annotation.*;` | `PostMapping, RequestBody, RestController` | explicit imports |
| jwt/controller/AuthController.java | BooleanExpressionComplexity (9) | `if (request.consumer() == null \|\| request.consumer().isBlank() \|\| ... 10 terms)` | `if (anyBlank(request.consumer(), request.email(), ...))` + private static `anyBlank(String...)` | trivial extraction, same null/blank semantics |
| jwt/service/TokenProvider.java | UnusedImports | `import java.util.Map;` | removed | unused |
| logging/config/LoggingAutoConfiguration.java | AvoidStarImport | `import ...autoconfigure.condition.*;` | `ConditionalOnBean, ConditionalOnClass, ConditionalOnProperty, ConditionalOnWebApplication` | explicit imports |
| logging/logback/AlramzJsonEncoder.java | UnusedImports | `import org.slf4j.MDC;` | removed | unused (only mentioned in Javadoc prose) |
| logging/util/LogMaskingUtil.java | ConstantName x2 | `private static final ... sensitiveKeys / customPatterns` | `SENSITIVE_KEYS / CUSTOM_PATTERNS` (+ all usages) | private constants, UPPER_SNAKE |
| logging/util/LoggingHelper.java | AvoidStarImport | `import java.util.*;` | `ArrayList, Collections, Enumeration, LinkedHashMap, List, Locale, Map` | explicit imports |
| logging/util/MethodExecutionLoggingHelper.java | UnusedImports, AvoidStarImport | `import ...LoggingConstants;` / `import java.util.*;` | removed / `ArrayList, Collection, LinkedHashMap, List, Map` | unused + explicit imports |
| scheduler/service/ISchedulerService.java | RedundantModifier | `reintializeScheduler(final String jobGroupName)` | `reintializeScheduler(String jobGroupName)` | `final` on interface param is meaningless; signature/binary API unchanged |

### onboarding-service

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

### alramz-notification-service

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| config/SmsProperties.java (compact ctor) | Checkstyle NPathComplexity (1024) | `connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;` (x7 ternaries) | `connectTimeout = Objects.requireNonNullElse(connectTimeout, Duration.ofSeconds(5));` (`...ElseGet` for the nested records) | Same defaults with no branches. The nested-record defaults stay lazy. |
| config/SmsProperties.java `Defaults` | SpotBugs BX_UNBOXING_IMMEDIATELY_REBOXED | `expiryInSecs == null ? 300 : expiryInSecs` | `Objects.requireNonNullElse(expiryInSecs, 300)` | The mixed int/Integer ternary unboxed and then reboxed the value. |
| config/SmsProperties.java:84 | PMD AvoidLiteralsInIfCondition | `dispatch.batchSize() < 1` | `dispatch.batchSize() <= 0` | Same condition on an int. 0 is an allowed literal. |
| repository/OtpRepository.java | PMD FieldDeclarationsShouldBeAtStartOfClass (x4) | nested records declared before the fields | fields first, then the records | Declaration order only. |
| repository/OtpRepository.java | PMD AvoidDuplicateLiterals | `new MapSqlParameterSource("ids", ids)` (x4) | `new MapSqlParameterSource(IDS, ids)` | `private static final String IDS = "ids"` |
| repository/OtpRepository.java:92 | SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `keyHolder.getKey().longValue()` | `Objects.requireNonNull(keyHolder.getKey(), "otp_management insert returned no generated key").longValue()` | A missing key now fails with a message instead of a bare NPE. |
| repository/SmsTableRepository.java | PMD FieldDeclarationsShouldBeAtStartOfClass (x3) | `record SmsTableRow` before the fields | fields first | Declaration order only. |
| scheduler/job/SmsDispatchJob.java | SpotBugs CT_CONSTRUCTOR_THROW (x2) | `public class SmsDispatchJob` | `public final class SmsDispatchJob` | The fail-fast constructor check stays. A final class removes the finalizer-attack vector. No AOP advises the class (the starter aspects match on annotations or `com.alramz.client..`) and no test mocks it. |
| scheduler/job/SmsDispatchJob.java:119 | PMD AvoidLiteralsInIfCondition | `"SMS002".equals(e.getCode())` | `INVALID_MOBILE_CODE.equals(e.getCode())` | Named constant. |
| service/OtpService.java | SpotBugs CT_CONSTRUCTOR_THROW (x2) | `public class OtpService` | `public final class OtpService` | Same as above. The class has no `@Transactional` (it uses TransactionTemplate), so there is no proxy. |
| service/OtpService.java:313 | PMD AvoidInstanceofChecksInCatchClause | `catch (RuntimeException e) { ... e instanceof SmsException sms ? sms.getCode() : ...` | `catch (SmsException e) {...} catch (RuntimeException e) {...}` | Separate catch blocks. The log output is unchanged. |
| service/OtpService.java:330 | PMD AvoidInstanceofChecksInCatchClause | `e instanceof EmailServiceException gateway ? gateway.getCode() : ...` | `catch (EmailServiceException e) {...} catch (RuntimeException e) {...}` | Same. The existing EmailValidationException catch stays first. |
| service/impl/SmsServiceImpl.java:114 | SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `request.getExpiryInSecs() == null ? ... : request.getExpiryInSecs()` | `final Integer requested = request.getExpiryInSecs(); ... requested == null ? ... : requested` | Reads the getter once, so the unboxed value is the one that was null-checked. |
| service/impl/EmailServiceImpl.java:141,159,163 | PMD AvoidLiteralsInIfCondition (x3) | `status == 401` / `status == 429` | `status == HTTP_UNAUTHORIZED` / `HTTP_TOO_MANY_REQUESTS` | Named constants. |
| exception/GlobalExceptionHandler.java:135 | PMD AvoidLiteralsInIfCondition | `if (size < 3)` | `if (size < ATTACHMENT_CONTENT_PATH_DEPTH)` | Named constant (attachments / [n] / content). |
| service/impl/EtisalatSmsClient.java | PMD FieldDeclarationsShouldBeAtStartOfClass (x4) | `private record Token` before the fields | fields first | Declaration order only. |
| service/impl/EtisalatSmsClient.java | PMD AvoidFieldNameMatchingMethodName | `private Token token;` | `private Token cachedToken;` | Private field only. Nothing outside the class binds to it. |
| service/impl/EtisalatSmsClient.java:75 | PMD PreserveStackTrace | `catch (Unauthorized e) { try { ...token(true)... } catch (RestClientException retry) {...} }` | `catch (Unauthorized e) { log } catch (RestClientException e) {...}`, then `try { submit(token(true)...) } catch (RestClientException retry) {...}` | The retry moves out of the catch block with the same flow: a 401 refreshes once and anything else fails immediately. |
| service/TemplatedEmailService.java:61 | PMD AvoidFieldNameMatchingMethodName | `private static final Pattern LOGGABLE` | `private static final Pattern LOGGABLE_VALUE` | Private constant. The `loggable()` method (used by OtpService) is unchanged. |

Total fixed: 31 findings (Checkstyle 1, PMD 23, SpotBugs 7). Fixed 31 + suppressed 7 + deferred 40 = baseline 78.

### reference-data-service

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| service/RedisCacheService.java (getAllEntries) | AvoidCatchingGenericException | `catch (Exception e)` | `catch (DataAccessException \| IllegalStateException e)` | Redis read errors arrive as DataAccessException. A corrupt cached JSON value arrives as IllegalStateException from deserializeList. Both still fall back to the DB. |
| service/RedisCacheService.java (flushCache) | AvoidCatchingGenericException | `catch (Exception e)` | `catch (DataAccessException e)` | `redisTemplate.delete` only throws translated DataAccessException |
| service/RedisCacheService.java (reloadGlobalConfig) | AvoidCatchingGenericException | `catch (Exception e)` | `catch (DataAccessException \| IllegalStateException e)` | Covers DB load, invalid table name, (de)serialization and the Redis staging write. The lock is still released as FAILED. |
| service/RedisCacheService.java (getCacheStatus) | AvoidCatchingGenericException, AvoidDeeplyNestedIfStmts | `if (exists) { json = get(); if (json != null) {...} }` | `String json = exists ? get() : null; if (json != null) {...}` + narrowed catch | One less nesting level, same result |
| service/RedisCacheService.java (loadFromDatabaseAndRepopulate) | AvoidCatchingGenericException | `catch (Exception e) { log; throw e; }` | separate `catch (DataAccessException e)` and `catch (IllegalStateException e)`, each logs and rethrows | Logs and rethrows the same exceptions as before |
| service/RedisCacheService.java (writeViaStagingKey) | AvoidCatchingGenericException, SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION | `catch (RuntimeException e)` | `catch (DataAccessException e)` | Only Redis ops in the try. The rethrow now has a precise type. |
| service/RedisCacheService.java (repopulateCache, tryAcquireLock, releaseLock, cleanStaleLocks, isRedisAvailable, isDatabaseAvailable) | AvoidCatchingGenericException (x6) | `catch (Exception e)` | `catch (DataAccessException e)` | Each try wraps only RedisTemplate or JDBC calls, which Spring translates to DataAccessException |
| service/RedisCacheService.java (parseTtl) | AvoidCatchingGenericException | `catch (Exception e)` | `catch (DateTimeParseException e)` | `Duration.parse` on a non-null string only throws this |
| service/RedisCacheService.java (parseTtlSeconds) | AvoidCatchingGenericException | `try { parseTtl(..) } catch (Exception e) { return 0; }` | `Duration d = parseTtl(ttl); return d != null ? d.getSeconds() : 0;` | parseTtl never throws, so the catch was dead code |
| service/RedisCacheService.java | UseDiamondOperator | `new TypeReference<List<Map<String, Object>>>() {}` | `new TypeReference<>() {}` | Anonymous-class diamond (Java 9+) |
| service/SchedulerLockService.java | AvoidCatchingGenericException (x3) | `catch (Exception e)` | `catch (DataAccessException e)` | JDBC `update` only |
| service/CacheAuditService.java | AvoidCatchingGenericException | `catch (Exception e)` | `catch (DataAccessException e)` | JDBC `update` only |
| config/RedisConfig.java | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `.block().getToken()` | `Objects.requireNonNull(...block(), "Azure token request returned no token").getToken()` | `block()` can return null. The existing catch wraps it as IllegalStateException. |
| config/RedisConfig.java | AvoidLiteralsInIfCondition | `"azure".equalsIgnoreCase(` | `AZURE_MODE.equalsIgnoreCase(` | Named constant |
| config/RedisConfig.java (parseDuration) | AvoidCatchingGenericException | `catch (Exception e)` | `if (timeout == null) return defaultDuration;` + `catch (DateTimeParseException e)` | Same fallback for null and for unparseable values |
| controllers/RedisCacheController.java | AvoidDuplicateLiterals (x2) | `roles = "APP_REFERENCE_DATA"`, `setResponseCode("200")` | `REQUIRED_ROLE`, `SUCCESS_CODE` constants | Removes duplicated literals. The values are unchanged. |

### utility-service

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| finoux/FinouxClient.java:63 | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `if (request.getTagSymbol() != null && !request.getTagSymbol().isBlank())` | `String tagSymbol = request.getTagSymbol(); if (tagSymbol != null && !tagSymbol.isBlank())` | getter called twice; read once into a local |
| finoux/FinouxData.java | EI_EXPOSE_REP, EI_EXPOSE_REP2 | `public record FinouxData(List<Map<String, Object>> table) {}` | explicit canonical ctor: `this.table = table == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(table));` | defensive, null-element-safe copy. Explicit (not compact) ctor because the compact form triggers PMD UnusedAssignment/NullAssignment false positives. Null -> empty list is equivalent for the only reader (`FinouxClient` checks `table() == null \|\| isEmpty()`). Jackson deserialization verified in a scratch run (`{"table":[{"a":1},null]}` -> `[{a=1}, null]`; `{}` -> `[]`) |
| finoux/FinouxInsertPostDataRequest.java | EI_EXPOSE_REP, EI_EXPOSE_REP2 | `record FinouxInsertPostDataRequest(..., List<FinouxParam> param) {}` | explicit canonical ctor copying `param` into an unmodifiable list | defensive copy; serialization verified unchanged: `{"METHOD":"M","Version":"1","PARAM":[{"Key":"k","Value":"v"}]}` |
| service/impl/CommunityPostServiceImpl.java | UseUnderscoresInNumericLiterals | `MAX_POST_DESC_LENGTH = 10000;` | `MAX_POST_DESC_LENGTH = 10_000;` | readability |

### reporting-management-service

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| exception/RelationshipManagerLookupException.java, InvalidDateRangeException.java | (enabler for PreserveStackTrace) | only `(String message)` ctor | added `(String message, Throwable cause)` ctor | lets callers chain the cause; message unchanged |
| repository/RelationshipManagerRepositoryImpl.java:61,64,92,95 | PreserveStackTrace x4 | `throw new RelationshipManagerLookupException("Unable to retrieve ...");` | `throw new RelationshipManagerLookupException("Unable to retrieve ...", e);` | keep the root cause. Client-facing message unchanged |
| repository/RelationshipManagerRepositoryImpl.java:105 | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `for (String name : params.getParameterNames()) ... params.getValue(name)` | `for (Map.Entry<String, Object> param : params.getValues().entrySet()) ... param.getValue()` | `getParameterNames()` is nullable-annotated; `getValues()` is the backing LinkedHashMap (non-null, same order). Log-only rendering is unchanged |
| service/impl/RelationshipManagerServiceImpl.java:82 | PreserveStackTrace | `throw new InvalidDateRangeException(fieldName + ": must be ...");` | `throw new InvalidDateRangeException(fieldName + ": must be ...", e);` | keep the DateTimeParseException cause |

### wealth-management-service

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| controllers/MmfController.java:96 | ShortMethodName | `private static ResponseEntity<GenericResponse> ok(Object payload)` | `okResponse(Object payload)` (+ 6 call sites) | private helper rename |
| exception/ErrorCode.java:90,129,130 | FieldDeclarationsShouldBeAtStartOfClass x3 | `private record Rule(...)` before `RULES`; `code`/`defaultMessage` fields after the static block | `RULES`, `code`, `defaultMessage` declared before `record Rule` and the static initializer | reorder only. Static init order is unchanged (`RULES` is still initialised before the static block) |
| exception/GlobalExceptionHandler.java:247 | AvoidLiteralsInIfCondition | `if ("NotNull".equals(constraint))` / `"Size".equals(constraint)` | `NOT_NULL_CONSTRAINT.equals(constraint)` / `SIZE_CONSTRAINT.equals(...)` | named Bean Validation constraint constants |
| exception/GlobalExceptionHandler.java:253 | UseVarargs | `private static String lastCode(String[] codes)` | `lastCode(String... codes)` | private, existing array callers unaffected |
| repository/MmfRepositoryImpl.java:63 | AvoidLiteralsInIfCondition | `if (rows.size() > 1)` | `if (rows.size() > SINGLE_ROW)` | named constant |
| repository/MmfRepositoryImpl.java:161 | ExceptionAsFlowControl | `try { sql = load(); if (sql == null) throw new MmfBackendException(...); return sql; } catch (IOException e) {...}` | `try { sql = load(); } catch (IOException e) {...}` then the null check outside the try | the throw is no longer inside a try. Behaviour is identical (MmfBackendException was never caught by the IOException catch) |
