# alramz-api-starter — Sonar code-smell remediation

Baseline: `mvn -B -pl services/alramz-api-starter test` → Tests run: 246, Failures: 0, Errors: 0.
After: Tests run: 250, Failures: 0, Errors: 0 (+4 new tests in `AbstractDataSourceAutoConfigurationTest`).

Paths are relative to `services/alramz-api-starter/src/`. "tests run" = full module test suite (`mvn -B -pl services/alramz-api-starter test`, green).

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| main/.../scheduler/service/impl/JobScheduleManager.java | S3776 (1), S3457 (13), S5145 (5), S1128 (1), S9438 (1), S112 (1), S135 (1), S1141 (1), S1155 (2), S1135 (1), S125 (1) | `schedule()` cognitive complexity 113; string-concatenated log lines; `jobGroupName` logged raw; unused import; non-final map; `throw new RuntimeException`; break/continue loops; nested try; `size() > 0`; commented-out TODO | `schedule()` split into `loadJobs`/`loadDbJobs`/`loadYamlJobs`/`filterDbJobs`/`matchesJobIds`/`scheduleJob`/`scheduleByMode`/`registerScheduledTask`/`logFailedJobs`; `{}` placeholders (stack traces passed as throwable arg); `LoggingHelper.stripCrlf` on logged group name/job ids; `IllegalStateException` (subclass of RuntimeException, controller/tests unaffected); `isEmpty()`; constants `CRON_EXP`, `CANCELLING_TASK`. The original's carry-over of the previous job's `future` across loop iterations is preserved exactly (see notes) | JobScheduleManagerTest (11) + full suite |
| main/.../scheduler/constants/SchedulerConstants.java | S1214 (1) | constants in an interface | `final class` with private ctor; `SchedulerConstants.X` usage unchanged (only users are in the starter) | full suite |
| main/.../scheduler/config/SchedulerAutoConfiguration.java | S1128 (2) | same-package imports | removed | full suite |
| main/.../scheduler/config/SchedulerConfigCondition.java | S1128 (1) | unused import | removed | full suite |
| main/.../scheduler/config/SchedulerConfiguration.java | S1128 (1) | unused import | removed | full suite |
| test/.../scheduler/service/impl/JobScheduleManagerTest.java | S8924 (1) | qualified `Mockito.verify` | static import | full suite |
| main/.../logging/aspect/MethodExecutionLoggingAspect.java | S3776 (1) | complexity 38 | private `LogOptions`/`Invocation` records + `logEntry`/`onException`/`endSpan`/`isExitLogged`; same helper calls, same log output | MethodExecutionLoggingAspectTest + full suite |
| main/.../logging/util/MethodExecutionLoggingHelper.java | S3776 (1), S1126 (1) | `isExcluded` complexity 17; if/return true/false | `anyMatch(list, predicate)` helper, single boolean return | full suite |
| main/.../logging/config/LoggingAutoConfiguration.java | S106 (4), S1128 (1) | `System.out.println` diagnostics; unused import | slf4j `log.info`; import removed | full suite |
| main/.../logging/config/LoggingProperties.java | S1192 (3) | default excluded paths literal repeated 3x | `DEFAULT_EXCLUDED_PATHS` constant (audit list = `/api/v1/info` + defaults, same order) | full suite |
| main/.../logging/config/SeqLoggingConfig.java | S1192 (1) | "Information" duplicated | uses `DEFAULT_LEVEL` | full suite |
| main/.../logging/constants/LoggingConstants.java | S1124 (1) | `public final static` | `public static final` | full suite |
| main/.../logging/filter/CorrelationIdFilter.java | S1128 (1) | unused `@Slf4j` import (+ slf4j imports left unused by lead's logger removal) | removed | full suite |
| main/.../logging/filter/RequestLoggingFilter.java | S6880 (1) | instanceof if/else rethrow chain | pattern `switch` statement with `throw` per case | full suite |
| main/.../logging/filter/ResponseLoggingFilter.java | S6880 (1), S1128 (1), S6201 (1) | same chain; unused import; instanceof + cast | pattern switch; import removed; `instanceof Long nanos` | full suite |
| main/.../logging/interceptor/RestTemplateLoggingInterceptor.java | S1066 (1) | nested if | merged | full suite |
| main/.../logging/interceptor/WebClientLoggingFilter.java | S3776 (1), S1066 (3), S1128 (1) | complexity 20; duplicated nested log guards; unused import | `withCorrelationId`/`logResponse`/`logError` extracted; redundant inner guards removed/merged | WebClientLoggingFilterTest + full suite |
| main/.../logging/logback/AlramzJsonEncoder.java | S1117 (1), S1172 (1) | local `json` hides field; unused `cause` param (private method) | renamed `rendered`; param removed | full suite |
| main/.../logging/logback/SeqAppender.java | S2065 (9), S7158 (2), S135 (1), S1128 (1) | `transient` on non-Serializable appender; `length() > 0`; 2 breaks in retry loop; unused import | `transient` removed; `isEmpty()`; loop driven by `keepTrying` + `sleepBackoff()` (returns false on interrupt, flag restored) | SeqAppenderTest + full suite |
| main/.../logging/otel/OpenTelemetryTraceContextExtractor.java | S1181 (2) | `catch (Throwable)` | `catch (RuntimeException \| LinkageError)` (keeps the "OTel classes missing" guard, stops swallowing VM errors) | full suite |
| main/.../logging/util/LoggingHelper.java | S1066 (1) | nested if in else | `else if` | full suite |
| main/.../logging/util/LogMaskingUtil.java | S5869 (4), S1128 (1) | `A-Za-z` under `(?i)`; redundant `\\.` in class; unused import | `A-Z` only (case-insensitive flag already matches lowercase); import removed | LogMaskingUtilTest + full suite |
| main/.../audit/ApiAuditAspect.java | S1128 (3), S3776 (1) | unused imports; complexity 20 | removed; `appendFrames` extracted | ApiAuditAspectTest + full suite |
| main/.../audit/ApiAuditLogFilter.java | S1128 (1), S1192 (1), S6880 (1) | unused imports; "FAILURE" x3; instanceof chain | removed; constant; pattern switch with `case null, default` | full suite |
| main/.../audit/ApiAuditLogService.java | S6485 (1) | `new HashMap<>(16)` | `HashMap.newHashMap(16)` | full suite |
| main/.../audit/SensitiveDataMasker.java | S1068 (1), S6485 (1), S6880 (1) | unused constant; ctor sizing; instanceof chain | removed; `LinkedHashMap.newLinkedHashMap`; pattern switch | SensitiveDataMaskerTest + full suite |
| main/.../audit/WebClientAuditFilter.java | S6213 (1) | private method named `record` | `recordOutcome` | full suite |
| main/.../client/AbstractRestClient.java | S119 (10), S107 (1) | type vars `RQ`/`RS`; private `execute` with 8 params | `T`/`R` (source-compatible); private `CallOptions` record | AbstractRestClientTest + full suite |
| main/.../datasource/config/AbstractDataSourceAutoConfiguration.java | S3776 (1), S1066 (1) | `applyDatabaseSpecificProperties` complexity 28; nested if | private `dataSourceProperties`/`applyOracleProperties`/`applyPostgresProperties`; protected signature unchanged | new AbstractDataSourceAutoConfigurationTest (4) + full suite |
| main/.../datasource/config/IntegrationDataSourceAutoConfiguration.java | S1192 (1) | "integration" x5 | `DB_NAME` constant (bean names unchanged) | full suite |
| main/.../datasource/config/MiddlewareDataSourceAutoConfiguration.java | S1192 (1) | "middleware" x5 | `DB_NAME` constant (bean names unchanged) | full suite |
| main/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java | S6204 (1) | `collect(toUnmodifiableList())` | `.toList()` | full suite |
| main/.../jwt/annotation/JwtSecured.java | S1128 (1) | unused import | removed | full suite |
| main/.../jwt/config/JwtAutoConfiguration.java | S112 (1), S1130 (1) | `throws Exception` never thrown | removed (`HttpSecurity.build()` throws no checked exception in Security 7.1) | full suite |
| main/.../jwt/filter/JwtAuthFilter.java | S1068 (1) | unused `properties` field | removed; constructor signature unchanged | full suite |
| main/.../jwt/model/TokenPair.java, User.java | S1128 (2) | unused imports | removed | full suite |
| main/.../jwt/repository/RefreshTokenRepositoryOps.java | S1128 (1) | unused import | removed | full suite |
| main/.../jwt/repository/UserRepository.java | S8688 (1), S1128 (1) | `LocalDateTime.now()` | `now(ZoneId.systemDefault())` | full suite |
| main/.../jwt/service/TokenProvider.java | S2143 (1) | `java.util.Date` arithmetic | `Instant` arithmetic, `Date.from` only at the jjwt boundary | full suite |
| main/.../tracing/config/TracingAutoConfiguration.java | S3358 (1) | nested ternary | private `boundRequest()` | full suite |
| main/.../utils/DataValidationUtil.java | S1118 (1) | implicit public ctor | private ctor (no instantiations found under services/) | full suite |
| main/.../utils/PWProtector.java | S1068 (1) | unused logger | removed | full suite |
| main/.../utils/SqlQueriesManager.java | S1192 (1), S108 (1) | "sql.file" x3; empty finally | constant; empty try/finally removed | full suite |
| test/.../audit/ApiAuditAspectTest.java, ApiAuditLogFilterTest.java | S5838 (2) | `toString()).isEqualTo` | `hasToString` | full suite |
| test/.../audit/SensitiveDataMaskerTest.java | S5838 (4) | `get(k)).isEqualTo(v)` | `containsEntry` | full suite |
| test/.../audit/WebClientAuditFilterTest.java | S1128 (1), S5838 (1), S5958 (3) | unused import; toString compare; bare assertThatThrownBy | removed; `hasToString`; exception type / root cause asserted | full suite |
| test/.../client/AbstractRestClientTest.java | S5778 (1) | setup in throwing lambda | setup hoisted | full suite |
| test/.../datasource/*AutoConfigurationTest.java | S1128 (3) | unused imports | removed | full suite |
| test/.../datasource/config/DatasourcePropertiesTest.java | S5838 (3) | `isEqualTo(0L)` | `isZero()` | full suite |
| test/.../globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java | S6213 (2), S5838 (1), S5853 (1) | var named `record`; hashCode compare; split asserts | `settings`; `hasSameHashCodeAs`; chained | full suite |
| test/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryIT.java | S1128 (1), S5778 (1) | unused import; setup in lambda | removed; hoisted | full suite |
| test/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryTest.java | S5778 (2), S5838 (1) | setup in lambdas; `isEqualTo(0)` | hoisted; `isZero()` | full suite |
| test/.../jwt/aspect/JwtSecuredAspectTest.java | S1186 (2), S9016 (1), S1130 (2) | empty methods; inline mock; needless throws | comments; local mock; throws removed | full suite |
| test/.../jwt/controller/AuthControllerTest.java | S5778 (1), S1186 (2) | setup in lambda; empty stubs | hoisted; comments | full suite |
| test/.../jwt/service/AuthServiceTest.java | S1186 (6) | empty stubs | comments | full suite |
| test/.../logging/AlramzJsonEncoderTest.java, LogMaskingUtilTest.java | S5853 (3) | split asserts | chained | full suite |
| test/.../logging/LoggingHelperTest.java | S6068 (3) | all-`eq()` matchers | raw values | full suite |
| test/.../logging/MethodExecutionLoggingAspectTest.java | S8924 (11), S5853 (2), S108 (2), S9016 (1) | qualified Mockito calls; split asserts; empty catches; inline mock | static imports; chained; comments; local | full suite |
| test/.../logging/filter/RequestLoggingFilterTest.java, ResponseLoggingFilterTest.java | S8924 (2) | qualified `times`/`never` | static imports | full suite |
| test/.../logging/interceptor/WebClientLoggingFilterTest.java | S5838 (2) | `get(k)).isEqualTo(v)` | `containsEntry` | full suite |
| test/.../logging/logback/SeqAppenderTest.java | S2925 (5) | `Thread.sleep` | Awaitility (already on classpath via spring-boot-starter-test) | full suite |
| test/.../datasource/config/AbstractDataSourceAutoConfigurationTest.java (new) | — | no coverage for refactored S3776 path | 4 tests (Oracle w/ and w/o config, non-positive skip, Postgres SSL local/remote, missing config) | full suite |

## Skipped

| rule | path:line | reason |
|---|---|---|
| S112 (x5) | main/.../scheduler/service/ISchedulerService.java:11,13,15,17,21 | `throws Exception` on a public interface; removing it is a public API change for implementors in other services |
| S107 | main/.../scheduler/model/ScheduleInfoBean.java:24 | public 9-arg constructor; changing it breaks callers |
| S1172 | main/.../exceptions/ApiCallFailedException.java:12 | public constructor used by onboarding-service and AbstractRestClient |
| S2139 (x3) | main/.../globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:50,72,122 | log-and-rethrow is the only error trace; wrapping changes the exception type callers/tests depend on — needs a decision |
| S2143 | main/.../utils/DataValidationUtil.java:0 | public methods take `java.util.Date`; switching SimpleDateFormat→DateTimeFormatter changes parsing leniency |
| S5843 / S5998 | main/.../logging/util/LogMaskingUtil.java:30 | `JSON_PAIR` already rewritten with possessive quantifiers by the lead (removes the stack-overflow risk); further simplifying risks changing masking behaviour — re-check on next scan |
| S1172 (x9) | test/.../ApiAuditAspectTest.java:32,36,40; AuditProbeClient.java:6; AuditProbeController.java:9; MethodExecutionLoggingAspectTest.java:220; DuplicateCheckServiceImpl/IBANValidationServiceImpl/PhoneValidationServiceImpl.java:10 | test fixtures whose parameters are looked up by reflection / captured and asserted by the audit & logging aspects |
| S4502 | main/.../jwt/config/JwtAutoConfiguration.java:83 | lead owns the CSRF line |
| (lead-fixed) | SchedulerManagerController (all 16), JobScheduleManager:144/175 (S3457, S5145) and :401 (S3599), SeqAppender S3078/S2142, LoggingHelper S2259, JwtProperties/AuthService S2184, S2387/S1068 `logger` fields in ApiAuditLogFilter/CorrelationIdFilter/RequestLoggingFilter/ResponseLoggingFilter/JwtAuthFilter | already fixed by the lead per brief |

## Notes for review

- `JobScheduleManager.schedule()`: the original declared `future` outside the job loop and never reset it, so a job that is invalid/rejected *after* a successful one is also counted in `SUCCEED` and registered in `scheduledTasks` with the previous job's future. The refactor keeps this exactly (commented in code). It looks like a latent bug; the existing test orders invalid jobs first to avoid it. Fixing it is a behaviour change for the lead to decide.
- `SchedulerConstants` changed from interface to final class: source-compatible for constant access; only users are inside the starter.
- Several error logs now pass the exception as the last slf4j argument instead of `ExceptionUtils.getStackTrace(e)` in the message (same convention the lead used in SchedulerManagerController) — log text format differs slightly.
