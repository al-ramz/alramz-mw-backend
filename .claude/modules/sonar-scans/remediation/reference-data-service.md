# reference-data-service - Sonar code-smell remediation

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| config/CacheWarmupRunner.java | S1128 (1) | same-package import | removed import | `mvn -B -pl services/reference-data-service test` |
| config/CompanyRedisProperties.java | S1128 (1) | unused `DefaultValue` import | removed import | same |
| config/JpaConfig.java | S1220 (1) | 0-byte file, no package | deleted the empty file (`git rm`) | same |
| controllers/ApplicationController.java | S112, S1130 (2) | `info()` declared `throws Exception` that is never thrown | removed `throws` clause | same |
| scheduler/job/CacheReloadScheduledJob.java (+ test) | S1068 (1) | unused `companyRedisProperties` field | removed field and its constructor parameter (Spring bean; only caller is the test, updated) | same |
| service/CacheAuditService.java | S1128 (1) | unused `LocalDateTime` import | removed import | same |
| service/RedisCacheService.java | S1854, S1481 x2, S1141 x2, S8688 x2, S1192 (8) | unused `deleted`/`lastReloaded` locals; nested try blocks in reload and repopulate; zone-less `now()`; `"mappingName"` x3 | dropped unused locals; extracted `writeViaStagingKey`, `repopulateCache`, shared `writeWithTtl`; `now(ZoneId.systemDefault())`; `MAPPING_NAME` constant | same |
| service/SchedulerLockService.java | S1192, S8688 x2 (3) | `"jobId"` x3; zone-less `now()` | `JOB_ID` constant; `now(ZoneId.systemDefault())` | same |
| test config/CacheWarmupRunnerTest.java | S1130 (1) | `throws Exception` never thrown | removed | same |

Result: Tests run: 60, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
| rule | path:line | reason |
|---|---|---|
| S2077 | service/RedisCacheService.java:208 | already fixed by the lead (TABLE_NAME pattern) |
| S1172 | service/RedisCacheService.java:62 | `key` is a parameter of public method `getEntry(String, String)`; removing it changes a public signature (brief: private members only) |
| S2139 | service/RedisCacheService.java:198 | log-and-rethrow: a test asserts the original `DataAccessException` type propagates, and the GlobalExceptionHandler does not log, so dropping the log loses the only error trace and wrapping changes the exception type. Needs a design decision |
