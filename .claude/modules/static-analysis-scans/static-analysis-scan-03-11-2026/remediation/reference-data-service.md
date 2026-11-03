# reference-data-service: static-analysis remediation

Scope: `services/reference-data-service/src/main/java/com/alramz/**`. The FieldNamingConventions rows were skipped because the config already resolves them.

## Fixed

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

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| scheduler/job/CacheReloadScheduledJob.java:run | PMD.AvoidCatchingGenericException | Scheduler job boundary. It logs every failure so the scheduler thread never dies, and `finally` releases the lock. |
| config/CacheWarmupRunner.java:warmupRunner | PMD.AvoidCatchingGenericException | Startup warmup boundary. One failing mapping must not abort startup or stop the other mappings from warming. |
| config/RedisConfig.java:resolveAzureAccessToken | PMD.AvoidCatchingGenericException | Boundary over Azure identity and Reactor errors (several unrelated RuntimeException types). It rethrows them as one IllegalStateException with context. |
| config/RedisConfig.java:createLocalRedisConnectionFactory | PMD.CloseResource | False positive. `Connection` is the `CompanyRedisProperties.Connection` config POJO, not a closeable resource. No Redis connection is opened here. |
| config/RedisConfig.java:createAzureRedisConnectionFactory | PMD.CloseResource | Same false positive as above |
| service/RedisCacheService.java:loadFromDatabaseAndRepopulate | PMD.IdenticalCatchBranches | Introduced by the fix. Merging the two catches into a multi-catch makes the rethrow a RuntimeException, which re-triggers SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION. The two rules conflict here. |

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| config/CompanyRedisProperties.java, config/CacheMappingProperties.java, config/MiddlewareDataSourceProperties.java | DataClass | 6 | `@ConfigurationProperties` binding beans are meant to be data classes. Fixing it would mean converting them to records or immutable binding, which changes the binding API. |
| config/CompanyRedisProperties.java (`Ssl`), config/MiddlewareDataSourceProperties.java (`Pool`) | ShortClassName | 2 | Public nested binding types used across config and tests. Renaming them changes the public API without any benefit. |
| service/RedisCacheService.java | TooManyMethods | 1 | Fixing it means splitting the lock, health and serialization helpers into separate beans. That is a structural refactor and out of scope. |
| service/CacheAuditService.java | UseObjectForClearerAPI | 1 | Fixing it changes the public `logAuditAsync` signature used by RedisCacheService and the tests. It would need a parameter object. |

## Verification

- Command, run in snapshot `/private/tmp/claude-502/-Users-gtalele-Library-CloudStorage-OneDrive-AlRamz-Desktop-workspace-alramz-mw-backend/a07b0375-ec16-4b2d-824f-2dc8d14b9d94/scratchpad/snap-refdata`: `mvn -B -o -pl services/reference-data-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true`
- Exit code 0, BUILD SUCCESS
- Tests: 59 run, 0 failures, 0 errors, 0 skipped. No test needed changing.
- Remaining findings:
  - Checkstyle: 0
  - SpotBugs: 0
  - PMD: 10, all deferred (DataClass 6, ShortClassName 2, TooManyMethods 1, UseObjectForClearerAPI 1). There is also one `LoosePackageCoupling` configerror (rule config, not a code finding).
- Risk: the narrowed catches in RedisCacheService, SchedulerLockService and CacheAuditService now let through RuntimeExceptions that are neither DataAccessException nor IllegalStateException. One example is an untranslated driver exception. Previously these were swallowed or degraded gracefully. Spring's Redis and JDBC templates translate driver errors, so this is unlikely. If it happens on a reload, the 1-minute lock lease bounds the impact.
