### Why each pending issue is still open (all MAJOR or lower; none affects a rating)

| Rule | Count | Where | Blocker / reason | What it would take |
|---|---|---|---|---|
| java:S5738 | 10 | onboarding `External*ServiceConfig`, `WebClientConfig`, `ETradeTokenProviderTest` | The deprecated-for-removal Jackson 2 WebClient codecs. The replacement is Jackson 3, but this module decodes into Jackson 2 types (`JsonNode` in `ETradeTokenProvider`, `ETradeResponse`) | A separate Jackson 2 → 3 migration of the onboarding WebClient stack, with its own regression testing |
| java:S1172 | 9 | api-starter **test fixtures** (`ApiAuditAspectTest`, `AuditProbeClient`, `AuditProbeController`, `MethodExecutionLoggingAspectTest`, stub `*ServiceImpl`) | The parameters exist so the audit and method-logging aspects can capture and mask them by reflection. Removing them removes what the tests assert | None. They're needed as written; a reviewer could mark them accepted |
| java:S2139 | 4 | api-starter `JdbcGlobalConfigurationSettingsRepository` (3), reference-data `RedisCacheService` (1) | Log-and-rethrow. Dropping the log loses the only error trace (the reference-data handler doesn't log). Wrapping changes the exception type that callers and tests catch | A decision on the error-handling contract: wrap in a domain exception and let the handler log |
| java:S2143 | 2 | api-starter `TokenProvider`, `DataValidationUtil` | `TokenProvider`: the jjwt API takes `java.util.Date`. `DataValidationUtil`: public methods take `Date`, and switching parsers changes how lenient parsing is | `TokenProvider`: none (library API). `DataValidationUtil`: an API change for its callers |
| java:S1075 | 2 | onboarding `GlobalExceptionHandler:27,28` | Path constants that must match the generated controller routes. A property could drift from the real routes | None recommended |
| java:S107 | 1 | api-starter `ScheduleInfoBean` constructor (9 params) | Public constructor of a shared-library model | A builder, which is an API change for starter users |

### Accepted in SonarCloud (not open; approved by the user on 2026-11-02, with evidence attached as a comment)

| Rule | Location | Evidence |
|---|---|---|
| java:S4502 | api-starter `JwtAutoConfiguration` (CSRF disabled) | Stateless Bearer-header auth; no cookies or `HttpSession` in any service; enabling CSRF would reject every non-GET call |
| java:S2077 | reference-data `RedisCacheService` (`SELECT * FROM <table>`) | Table name comes from config, never the request; validated against a `[schema.]table` identifier pattern; regression test |

### Startup: BLOCKED_BY_DEPENDENCY is by design

The dev profile's Oracle BROK datasource points at shared DEV/QA databases (wealth-management defaults to
`BROKQA`). `startup-check.sh` deliberately redirects it to `localhost:1`, so verification can never touch a
shared environment. All four Oracle-backed services got through bean creation and stopped only when
opening that datasource (`ORA-12541 … localhost port 1`). To get them to STARTED_AND_HEALTHY, run a local
Oracle (or an XE container) and export `COMPANY_DATASOURCE_BROK_URL`, `COMPANY_DATASOURCE_BROK_USERNAME`
and `COMPANY_DATASOURCE_BROK_PLAINPASSWORD` before running the script. The two non-Oracle services started in
about 12 seconds; their `/actuator/health` answers 403 because it isn't in the JWT `permit-all-urls` (only
`/actuator/prometheus` is).
