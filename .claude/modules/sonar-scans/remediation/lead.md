# Lead fixes: vulnerabilities, bugs, blockers (cycle 1)

Verified with: `mvn -pl services/alramz-common-bom,services/alramz-api-starter install` (246 tests, 0 failures;
log `cycle1-starter-2.log`), `mvn -pl services/onboarding-service,services/alramz-notification-service test`
(257 + 260 tests, 0 failures; `cycle1-services.log`), and `RedisCacheServiceTest` (24 tests, 0 failures; `cycle1-refdata.log`).

| File | Rule(s) | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `scheduler/controller/SchedulerManagerController.java` | javasecurity:S5145 ×3 (+ downstream ×7 in JobScheduleManager), S1192, S6813, S3457 | `jobGroupName`/`jobIds` from the request body were logged raw, so CR/LF could forge log lines. Literal duplicated 3×, field injection, string-concatenated logs, stack traces glued into the message | Sanitize at the trust boundary with `LoggingHelper.stripCrlf` before any use or logging. Use a constant, constructor injection, `{}` placeholders, and `log.error(msg, e)` | New `restartStripsCrlfFromJobGroupBeforeUseOrLogging`; existing controller tests |
| api-starter `logging/util/LoggingHelper.java` | javabugs:S2259 | `populateMdc` dereferenced `request` that the class elsewhere treats as nullable | Early return on null, after the service name goes into the MDC. Added `stripCrlf` (CWE-117) | Starter suite |
| api-starter `scheduler/service/impl/JobScheduleManager.java` | S3599, S3457; javasecurity:S5145 sinks | Double-brace `HashMap` subclass. The unregister log printed `[Ljava.lang.String;@…` instead of the IDs | Plain `HashMap` + `put`; `{}` placeholders with `Arrays.toString` | Starter suite |
| api-starter `logging/logback/SeqAppender.java` | S3078, S2142 | `failureCount++` on a volatile field loses increments under concurrent appends. `catch (Exception)` swallowed `InterruptedException` from `httpClient.send` | `AtomicInteger.incrementAndGet()`; a dedicated `InterruptedException` catch that restores the interrupt flag and stops retrying | Starter suite |
| api-starter `logging/util/LogMaskingUtil.java` | S5998 | Backtracking alternation in `JSON_PAIR` can overflow the stack on large JSON strings | Possessive quantifiers. The alternatives are disjoint, so matches are unchanged | Starter masking tests |
| api-starter `jwt/config/JwtProperties.java`, `jwt/service/AuthService.java` | S2184 ×4 | `int` multiplication for durations (no overflow at current values, but fragile) | `L` literal so the arithmetic is `long` | Starter JWT tests |
| api-starter `jwt/filter/JwtAuthFilter.java`, `logging/filter/RequestLoggingFilter.java`, `logging/filter/ResponseLoggingFilter.java` | S2387 (BLOCKER) | Own `logger` field shadowed `GenericFilterBean.logger` | Renamed to `log` | Starter filter tests |
| api-starter `audit/ApiAuditLogFilter.java`, `logging/filter/CorrelationIdFilter.java` | S2387 (BLOCKER) | Shadowing `logger` field, never used | Removed the field and its now-unused imports | Starter suite |
| notification `service/impl/EtisalatSmsClient.java` | javabugs:S2259 | `response.get("jobId")` was only safe through an indirect `"201".equals("null")` path | Explicit `response == null` in the rejection guard; same exception and code | Notification suite |
| onboarding `client/ETradeClient.java` | S3599 | Double-brace anonymous `HttpHeaders` subclass | Plain `HttpHeaders` + `add` | Onboarding suite |
| reference-data `service/RedisCacheService.java` | S2077 | `SELECT * FROM <table>` concatenates a configured table name (identifiers can't be bound) | Validate against a `[schema.]table` identifier pattern before building SQL. A bad value throws `IllegalStateException` | New `loadAllFromDatabase_shouldRejectTableNameThatIsNotAPlainIdentifier` |

## Reviewed, no code change

| Rule | Location | Evidence | Decision |
|---|---|---|---|
| java:S4502 (CSRF disabled) | api-starter `jwt/config/JwtAutoConfiguration.java:83` | Auth reads only the `Authorization: Bearer` header (`JwtAuthFilter:45`, `JwtContext:43`). Session policy is `STATELESS`. No service uses cookies or `HttpSession` (grep over `services/*/src/main`). Browsers never attach the token automatically, so cross-site requests can't be authenticated. Enabling CSRF would reject every non-GET API call | Keep it disabled. The user approved it on 2026-11-02. Issue `AaGbpyWsoF4k1D0H0hrO` was marked **ACCEPTED** in SonarCloud with this evidence as the comment |

## Cycle 1 follow-up (after the module agents)

| File | Rule / source | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `scheduler/service/impl/JobScheduleManager.java` | Reliability bug found while refactoring S3776 | `future` was never reset between jobs, so an invalid or rejected job that followed a successful one was also added to SUCCEED and stored in `scheduledTasks` with the previous job's future. Cancelling it would cancel the wrong task | Each job uses its own future | New `invalidJobAfterSuccessfulOneIsNotReportedAsSucceeded` (valid job first). Starter: 251 tests, 0 failures (`cycle1-starter-3.log`) |

## Cycle 2 (from the cycle 2 rescan: 46 open, 7 new)

| File | Rule(s) | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `logging/util/LogMaskingUtil.java` | S5843 (new, complexity 25 > 20) plus a **masking leak** found during review | My possessive rewrite raised the regex complexity. Worse, the value alternatives (`\d+(?:\.\d+)?\|true\|false\|null`) never matched negative or exponent numbers, so `"pin": -1234` was logged **unmasked** | Number and literal alternatives replaced by one possessive token class `[-+.\w]++`. Lower complexity, and every unquoted JSON value is now masked | New `masksSensitiveUnquotedValuesIncludingNegativeAndExponentNumbers` |
| api-starter `scheduler/service/ISchedulerService.java`, `impl/JobScheduleManager.java`, `controller/SchedulerManagerController.java` | S112 ×5, S1130 | `throws Exception` on methods that throw no checked exception. The only implementer and caller are inside the starter (grep across `services/`) | Removed the clauses. Tests now declare only what they actually throw (`InterruptedException`, `SQLException`) | Starter 252 tests, 0 failures (`cycle2-starter.log`) |
| api-starter `logging/util/LoggingHelper.java` | S1066 | Nested `if` | Merged the condition | Starter suite |
| api-starter tests `WebClientAuditFilterTest`, `SeqAppenderTest`; reference-data `ApplicationControllerTest` | S5778, S1130 ×5 (new) | Two calls inside the `assertThatThrownBy` lambda; `throws Exception` that is never thrown | Call built outside the lambda; clauses removed | Starter and reference-data suites |
| reference-data `service/RedisCacheService.java` | S1172 | Public `getEntry(mappingName, key)` ignored `key` and returned every entry. It had no callers in main code, only a test asserting it "delegates", so the name promised a lookup it didn't do | Deleted the dead, misleading method and its test | Reference-data 59 tests, 0 failures (`cycle2-refdata.log`) |
| reference-data `service/RedisCacheService.java:224` | java:S2077 | Still flagged after validation (Sonar flags any non-constant SQL) | **ACCEPTED** in SonarCloud (`AaGbpyXaoF4k1D0H0hsL`) with the user's approval and evidence: config-sourced name, identifier-pattern validation, regression test | n/a |

## Cycle 3 (from the cycle 3 rescan: 29 open, 0 new; all ratings A)

| File | Rule | Original problem | Fix applied | Tests |
|---|---|---|---|---|
| api-starter `exceptions/ApiCallFailedException.java` | S1172 | The constructor received the failed call's `uri` and `method` (passed by `AbstractRestClient`) and discarded them | Stored as fields, with Lombok getters. Additive only; the second constructor sets them to `null` | `AbstractRestClientTest` now asserts the method and URI. Starter 252 tests, 0 failures (`cycle3-starter.log`) |
