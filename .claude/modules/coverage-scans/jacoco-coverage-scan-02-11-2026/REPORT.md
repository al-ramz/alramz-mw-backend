# JaCoCo coverage report: 02-11-2026

Branch `feature/project-setup`. The gate is **80% line coverage per service**. All 7 code modules now pass.

## Coverage before vs after

| Service | Line before | Line after | Branch before → after | Instruction before → after | Gate |
|---|---|---|---|---|---|
| alramz-api-starter | 37.9% (1148/3032) | **80.7%** (2447/3032) | 29.7% → 65.7% | 38.5% → 81.3% | FAIL → PASS |
| alramz-notification-service | n/a, 0 tests ran | **98.3%** (1248/1269) | n/a → 94.6% | n/a → 98.4% | FAIL → PASS |
| onboarding-service | 71.0% (788/1110) | **96.8%** (1075/1110) | 68.0% → 80.9% | 69.9% → 96.3% | FAIL → PASS |
| reference-data-service | 29.3% (157/535) | **95.7%** (512/535) | 6.0% → 81.0% | 23.8% → 94.4% | FAIL → PASS |
| utility-service | 56.6%* (82/145) | **98.6%** (141/143) | 19.2% → 76.9% | 50.8% → 98.6% | FAIL* → PASS |
| reporting-management-service | 93.0% | 93.0% | 86.8% → 86.8% | 92.9% → 92.9% | PASS |
| wealth-management-service | 93.7% | 93.8% | 95.8% → 95.8% | 95.1% → 94.7% | PASS |

\* A measurement artifact, not a real gap: JaCoCo logged `Execution data for class FinouxClient does not
match`. No tests were added for utility-service.

Small changes in total line counts (wealth 271→275, reporting 142→143) most likely come from source edits in the working tree during the run. No test change can alter a line total.

Details: `before/summary.md`, `after/summary.md`, per-service `*-jacoco.csv`. The HTML report for each service
is at `services/<svc>/target/site/jacoco/index.html`.

## What moved the numbers

1. **Build fix (notification-service).** A local surefire 3.2.5 pin silently ran zero JUnit 6 tests. Removing
   the pin brought 260 tests back, all green, at 98.3%. These tests were already written and passing; they
   had just never been run in CI.
2. **New tests**: +211 test executions (starter +114, onboarding +42, reference-data +55) (see `CHANGELOG.md`). No
   production code was changed.

## Production bugs found by the new tests (not fixed, need an owner decision)

| Sev | Where | Problem |
|---|---|---|
| High | `reference-data-service` `RedisConfig.createAzureRedisConnectionFactory()` | `factory.setUseSsl(true)` is called on an immutable `LettuceClientConfiguration` and throws `IllegalStateException`. The service cannot start with `company.redis.deployment.mode=azure`. Fix: `LettuceClientConfiguration.builder().useSsl()`. The test `redisConnectionFactory_azureModeCurrentlyFailsBecauseSslIsSetOnImmutableClientConfig` pins the current behavior; flip it when fixing. |
| Medium | `alramz-api-starter` `ApiAuditLogService.cleanupExpired()` | `CLEANUP_SQL` uses a positional `?` but runs through `NamedParameterJdbcTemplate` with key `retentionDays`. The parameter never binds and the error is swallowed, so expired `api_audit_log` rows are never deleted. |
| Medium | `alramz-api-starter` `JobScheduleManager.schedule()` | `future` isn't reset per job. An invalid job after a valid one is reported SUCCEED and mapped to the previous job's future. |
| Medium | `reference-data-service` `RedisCacheService.releaseLock` | It marks the lock COMPLETED, but `tryAcquireLock` only takes over expired IN_PROGRESS rows. A reload is refused for about a minute after a successful one. |
| Medium | `onboarding-service` `IBANValidationServiceImpl.applyValidationRules()` | It computes 6 validation codes and never applies them, so no IBAN rule is enforced. |
| Low | `reference-data-service` `RedisCacheService.getEntry(mapping, key)` | It ignores `key` and returns every entry. |
| Low | `reference-data-service` `CacheAuditService` / callers | Free text is passed as `triggerSource`, `details` is always null, and a cache miss is logged as EVICTED. |
| Low | `onboarding-service` `ETradeTokenProvider.fetchAndCacheToken()` | `response.path()` runs before the null check. An NPE is caught generically, and the result is still correct. |
| Low | `alramz-api-starter` `JobScheduleManager.unregisterJobs()` | The log line prints the array's identity instead of the ids. |
| Note | `reference-data-service` `loadAllFromDatabase` | A table name from config is concatenated into SQL without validation. |

## Remaining risks and gaps

- **The starter margin is thin (80.7%).** Next targets: UserRepository, RefreshTokenRepository,
  LoggingAutoConfiguration, JwtAutoConfiguration, SchedulerConfiguration/Condition, TracingQueryExecutionListener,
  SqlQueriesManager, CorrelationIdResponseBodyAdvice. Starter branch coverage is 65.7%, below the BOM's 70%
  branch property. utility-service branch coverage is 76.9%.
- **The gate is not enforced.** `alramz-common-bom` defines `jacoco.line.coverage=0.80`, but the `check`
  execution hard-codes `<minimum>0.00</minimum>`. Wiring the properties in would make CI fail below 80%. This
  is left to the owner because it changes CI behavior for every service.
- **Two deliberately loose starter tests:** `stopCancelsAllTasksAndReportsCompletion` accepts either message
  because of timing, and the span-tagging tests only check that no exception is thrown (no OpenTelemetry test
  SDK on the classpath).
- **OneDrive flakes** make full-reactor runs fail randomly. See `BUILD-STATUS.md`.
- Generated `org.openapitools.configuration.*` classes are counted in coverage. They add a few lines per
  service; the excludes are left unchanged.

## Reproduce

Follow `.claude/skills/jacoco-coverage-gate/SKILL.md`, or:

```bash
mvn -B -pl services/alramz-common-bom,services/alramz-api-starter install -DskipTests
mvn -B -fae clean test
python3 .claude/skills/jacoco-coverage-gate/scripts/coverage_summary.py <out_dir> --classes
```
