# Changelog: JaCoCo coverage scan 02-11-2026

No production code (`src/main`), JaCoCo exclude or threshold was changed. Every change is new or extended
unit tests (JUnit 6 + Mockito + AssertJ, no Spring context unless noted), plus one build fix.

## Build fix

| File | Before → After | Why | Effect |
|---|---|---|---|
| `services/alramz-notification-service/pom.xml` | `maven-surefire-plugin` `<version>3.2.5</version>` → removed (inherits 3.5.2 from `alramz-common-bom`) | Surefire 3.2.5 discovers no JUnit Jupiter 6 tests, so the module ran 0 tests and still showed BUILD SUCCESS | 0 → 260 tests executed, 98.3% line coverage, all passing |

## alramz-api-starter: 37.9% → 80.7% line (+1299 lines covered)

Path prefix: `services/alramz-api-starter/src/test/java/com/alramz/`

| File | Status | Covers |
|---|---|---|
| `logging/logback/SeqAppenderTest.java` | new | SeqAppender against a local JDK HttpServer: JSON payload, MDC key renames, exceptions, API-key header, retry/backoff, circuit breaker open → half-open → closed, disabled path |
| `scheduler/service/impl/JobScheduleManagerTest.java` | new | JobScheduleManager: YAML/DB job loading, SUCCEED/INVALID/REJECTED, bad cron, stop/hard stop/reinit/unregister |
| `scheduler/controller/SchedulerManagerControllerTest.java` | new | every scheduler endpoint, success and failure |
| `scheduler/model/SchedulerModelTest.java` | new | ScheduleJobEntity, SchedulerProperties, ScheduleInfoBean equals/hashCode, request models |
| `audit/SensitiveDataMaskerTest.java` | new | nested map/list masking, base64 image truncation, header/body masking, disabled mode |
| `audit/ApiAuditLogServiceTest.java` | new | insert parameters, masked JSON, MDC during the log line, DB and cleanup failures swallowed |
| `logging/interceptor/RestTemplateLoggingInterceptorTest.java` | new | correlation-id propagation, payload and slow-call logging, rethrow |
| `logging/filter/ResponseLoggingFilterTest.java` | new | masking/truncation, body copied back, exception paths, excluded paths |
| `logging/filter/RequestLoggingFilterTest.java` | new | request wrapping, correlation id, exception and skip rules |
| `logging/util/MethodExecutionLoggingHelperTest.java` | new | exclusions, value serialization, masking, truncation, fallback, `formatArgs` |
| `jwt/filter/JwtAuthFilterTest.java` | new | a real token authenticates with roles; invalid, missing or non-Bearer headers pass through |
| `jwt/aspect/JwtSecuredAspectTest.java` | new | unauthenticated, role and environment checks |
| `jwt/context/JwtContextTest.java` | new | claims from the Bearer token; null cases |
| `client/AbstractRestClientTest.java` | new | Bearer from Jwt principal, headers/query params, `local` profile, error → ApiCallFailedException / InvalidHttpRequestException |
| `client/UserRequestContextTest.java` | new | `get()` per principal type, `validateUser` |
| `globalconfigurationsettings/service/GlobalConfigurationSettingsServiceTest.java` | new | repository delegation, blank identifiers rejected |
| `utils/DataValidationUtilTest.java` | new | every helper |
| `jwt/service/AuthServiceTest.java` | extended | unknown user, register success/duplicate, logout, refresh rotation and rejections |
| `logging/LoggingHelperTest.java` | extended | client-IP priority, masked query/headers, payload truncation, MDC scope, slow-request warning, exclusions |
| `audit/ApiAuditAspectTest.java` | extended | endpoint/method resolution, argument masking, `fetchAndCacheToken`, failure path |

`jwt/controller/AuthControllerTest.java` already had uncommitted changes before this run and was not touched.

## onboarding-service: 71.0% → 96.8% line (+287)

Path prefix: `services/onboarding-service/src/test/java/com/alramz/`

| File | Status | Covers |
|---|---|---|
| `repository/DfmOnboardingRepositoryImplTest.java` | new | field → insert parameter mapping, JSON columns, "NEW" status, null defaults, serialization failure, missing SQL |
| `service/impl/IBANValidationServiceImplTest.java` | new | happy path, missing IBAN (1069), missing config, HTTP error/empty body → ExternalSystemException, transport → TechnicalException |
| `service/impl/PhoneValidationServiceImplTest.java` | new | same matrix for POST `/v2/verify` |
| `config/ETradeTokenProviderTest.java` | new | caching, `invalidate()`, failures not cached, every error → empty |
| `client/ETradeClientTest.java` | new | `access-token` header, response parsing, failures → ExternalSystemException |
| `OnboardingServiceApplicationRunnersTest.java` | new | the three startup runners |
| `controllers/DataValidationControllerTest.java` | extended | 400/503 error paths, correlation-id resolution (header → query → UUID) |

## reference-data-service: 29.3% → 95.7% line (+355)

Path prefix: `services/reference-data-service/src/test/java/com/alramz/`, all new:
`service/RedisCacheServiceTest.java` (hit/miss/fallback, flush, reload with lock + staging-key rename,
status, health), `service/SchedulerLockServiceTest.java`, `service/CacheAuditServiceTest.java`,
`scheduler/job/CacheReloadScheduledJobTest.java`, `controllers/RedisCacheControllerTest.java`,
`controllers/ApplicationControllerTest.java`, `config/CacheWarmupRunnerTest.java`, `config/RedisConfigTest.java`,
`config/MiddlewareDataSourcePropertiesTest.java` (H2), `config/CompanyRedisPropertiesTest.java`,
`config/CacheMappingPropertiesTest.java`.

## utility-service: no change

The 56.6% baseline was a JaCoCo execution-data mismatch on `FinouxClient`. The existing `FinouxClientTest` already
covers it (100% lines), and a clean rerun gives 98.6%.

## Tooling

- `.claude/skills/alramz-jacoco-coverage-check/SKILL.md`: a repeatable procedure for this scan.
- `.claude/skills/alramz-jacoco-coverage-check/scripts/coverage_summary.py`: per-service jacoco.csv → summary.md/json with gate and worst-classes list.
