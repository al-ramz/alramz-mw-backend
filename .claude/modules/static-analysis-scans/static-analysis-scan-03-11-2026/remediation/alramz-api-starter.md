# alramz-api-starter — static-analysis remediation

Only Checkstyle applies (starter pom hardcodes pmd/spotbugs skip=true). No public/protected API changed.

## Fixed

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

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| — | — | none |

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| audit/ApiAuditLogFilter.java | NPathComplexity (321) | 1 | split doFilterInternal into request/response/audit helpers; filter is on every request path, not trivial |
| logging/aspect/MethodExecutionLoggingAspect.java | NPathComplexity (1,150) | 1 | needs a refactor of the around-advice branching into helpers; behaviour-sensitive |
| logging/util/LoggingHelper.java:255 | NPathComplexity (225) | 1 | method-split refactor |
| scheduler/service/impl/JobScheduleManager.java:400 | NPathComplexity (225) | 1 | method-split refactor |
| logging/config/LoggingAutoConfiguration.java | ClassFanOutComplexity (35) | 1 | split auto-config into separate @AutoConfiguration classes — changes registered auto-config class names (public API for consumers) |

## Verification

- Command: `rsync repo -> snap-small (excl. target/, .git/); cd snap-small && mvn -B -o -pl services/alramz-api-starter,services/utility-service,services/reporting-management-service,services/wealth-management-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true` (snapshot build, offline, no install)
- Exit code: 0 (BUILD SUCCESS)
- Tests: 252 run, 0 failures, 0 errors, 0 skipped
- Remaining: Checkstyle 5 (all deferred complexity items above), PMD/SpotBugs skipped by module pom
