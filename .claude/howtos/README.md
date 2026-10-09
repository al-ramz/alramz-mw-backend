# Howtos

Pattern-focused, copy-paste-ready reference docs for building a new service (or a new feature in an
existing service) in this monorepo. Each doc is grounded in the **real code** of the two reference
services — `services/reference-data-service` (newest, cleanest scaffold) and
`services/data-validation-service` (JPA/Liquibase-heavy, most mature) — plus the shared plumbing in
`services/alramz-api-starter`. Every claim in these docs was verified against the actual source, not
inferred from convention alone; where the two reference services disagree or where the repo has a
real bug/gap, the doc says so explicitly instead of presenting it as the pattern to copy.

These are narrower and more prescriptive than `docs/codebase/` (a repo-wide architecture survey) —
each doc here answers "how do I add *this specific thing* to my service, step by step."

Out of scope for now (skipped by request): a dedicated `configuration-file-layout`/service-scaffolding
doc and a Docker/Kubernetes sizing doc. Until those exist, see the `alramz-service-scaffold` skill
(`.claude/skills/alramz-service-scaffold/`) for scaffolding a brand-new service end to end, and
`service.yaml` in each service for deployment sizing.

## Docs

| Doc | Covers |
|---|---|
| [`configuration-properties.md`](configuration-properties.md) | Typed `@ConfigurationProperties` classes: `PREFIX` constant, nested static groups, `@EnableConfigurationProperties` binding, `@ConditionalOnProperty` default-off gating. Start here before adding any new toggle/setting. |
| [`jwt-security-and-public-endpoints.md`](jwt-security-and-public-endpoints.md) | The starter's stateless JWT filter chain, `company.jwt.permit-all-urls`, `@JwtSecured(roles=...)` role gating, `UserRequestContext` for the current caller. |
| [`multi-datasource-and-jpa.md`](multi-datasource-and-jpa.md) | The three named datasources (`middleware`/`brok`/`integration`), qualified `NamedParameterJdbcTemplate`/`JdbcTemplate` beans, when plain JPA repos don't need a qualifier. |
| [`request-response-and-db-audit-logging.md`](request-response-and-db-audit-logging.md) | Request/response logging, DB audit logging to `api_audit_log`, `@Loggable` AOP logging, and the masking layer (`company.logging.masking.*` + `SensitiveDataMasker`). |
| [`exception-handling.md`](exception-handling.md) | Typed exceptions + `@RestControllerAdvice` handlers, the starter's free fallback `LoggingExceptionHandler`, and how handler ordering (`@Order`) actually resolves. |
| [`liquibase-migrations.md`](liquibase-migrations.md) | Changeset naming/numbering, the formatted-SQL header, profile-specific masters, rollback statements, and why you never edit an applied changeset. |
| [`new-endpoint-elicitation-checklist.md`](new-endpoint-elicitation-checklist.md) | The requirements-gathering checklist for a new REST endpoint *before* writing the spec: HTTP verb, security (public vs. role), request/response schemas, every real status code + internal error codes. Upstream of the doc below. |
| [`openapi-contract-first-controllers.md`](openapi-contract-first-controllers.md) | `specs/*.yaml` → generated interface/models (`generate-sources`) → controller `implements` the interface. |
| [`scheduler-jobs.md`](scheduler-jobs.md) | The starter's DB-driven dynamic scheduler (`schedule_job` table, `Schedulable` beans, `SchedulerLockService` distributed locking) — not plain `@Scheduled`. |
| [`testing-patterns.md`](testing-patterns.md) | The Mockito-first testing style actually used (no `MockMvc`/`@DataJpaTest` in either service today), test skeletons modeled on real classes. |
| [`api-collection-http-requests.md`](api-collection-http-requests.md) | The per-service root `apiCollection.http` manual request collection: `@baseUrl`/`@authToken` variables, `###`-delimited blocks, the positive/negative-pair and trailing scheduler-block conventions, and how to add a block for a new endpoint. |

## New-service / new-feature checklist

Work through these in roughly this order — later ones depend on earlier ones (e.g. logging/audit
needs a datasource, migrations need a schema before code reads it):

1. **Config** — define any new toggles as a typed `@ConfigurationProperties` class ([`configuration-properties.md`](configuration-properties.md)).
2. **Security** — confirm which endpoints must be public and add them to `company.jwt.permit-all-urls`; don't rely on `@PermitAll` alone ([`jwt-security-and-public-endpoints.md`](jwt-security-and-public-endpoints.md)).
3. **Data access** — enable the named datasource(s) you need and use the correct `@Qualifier` ([`multi-datasource-and-jpa.md`](multi-datasource-and-jpa.md)).
4. **Schema** — add Liquibase changesets for any new tables/columns before writing code against them ([`liquibase-migrations.md`](liquibase-migrations.md)).
5. **API contract** — gather the full contract first (verb, security, request/response schemas, every real status code) ([`new-endpoint-elicitation-checklist.md`](new-endpoint-elicitation-checklist.md)), then write/extend the OpenAPI spec, regenerate, and implement the generated interface in a controller ([`openapi-contract-first-controllers.md`](openapi-contract-first-controllers.md)).
6. **Errors** — add typed exceptions and wire them into a `@RestControllerAdvice` ([`exception-handling.md`](exception-handling.md)).
7. **Logging/audit** — turn on request/response and DB audit logging as needed, extend `SensitiveDataMasker` for any new sensitive field ([`request-response-and-db-audit-logging.md`](request-response-and-db-audit-logging.md)).
8. **Background work** — if the feature needs periodic execution, add a `schedule_job` row + `Schedulable` bean, don't hand-roll `@Scheduled` ([`scheduler-jobs.md`](scheduler-jobs.md)).
9. **Tests** — cover the new code following the existing Mockito-first style, then run `mvn -pl services/<service> -am clean test` per CLAUDE.md before calling it done ([`testing-patterns.md`](testing-patterns.md)).
10. **Manual request collection** — add a block for the new endpoint to `apiCollection.http` ([`api-collection-http-requests.md`](api-collection-http-requests.md)).

## Real gaps and inconsistencies surfaced while writing these docs

These aren't hypothetical — each was found by reading the actual code, and each doc's "pitfalls"
section explains it in more detail. Flagging them here as a punch list, since fixing them wasn't in
scope for this documentation pass:

- **`data-validation-service`'s `OnboardingExceptionHandler` is dead code.** It's `@Order(1)` while `GlobalExceptionHandler` is `@Order(0)` with a broader `basePackages`, so the global handler always wins for every exception type they both declare.
- **`reference-data-service` has no exception handling at all** — no typed exceptions, no `@RestControllerAdvice`. It only gets the starter's generic fallback (`LoggingExceptionHandler`). Not a pattern to copy from.
- **`reference-data-service`'s `ApplicationController` hand-writes `GET /api/v1/info`** instead of implementing the generated `InfoApi` interface its own spec declares — breaks the contract-first pattern it otherwise follows.
- **Both services' `application-prod.yml` use `plainPassword`** for datasource credentials instead of encrypted `password`+`passwordVector` via `cipher.password` — CLAUDE.md explicitly calls `plainPassword` dev-only.
- **`reference-data-service`'s Liquibase changelog has a numbering/changeset-id collision** and an orphaned "shared" folder nothing includes; a `.bak`-disabled seed file also exists. See `liquibase-migrations.md` for specifics.
- **`SchedulerProperties` doesn't follow the Lombok + `PREFIX` constant convention** the other starter properties classes use, and `company.jwt.enabled` isn't actually wired as a kill switch in `JwtAutoConfiguration` despite being documented as a toggle in CLAUDE.md.
- **Neither reference service uses Spring Data JPA** (`extends JpaRepository`) despite the dependency being present — both do direct SQL via `NamedParameterJdbcTemplate` + a `SqlQueriesManager` query-file loader. JPA is documented as supported but not the pattern actually in use.
- **A real, signed JWT and extensive PII-shaped test data are hardcoded in tracked `apiCollection.http` files** (`data-validation-service`'s onboarding example body in particular — a full name, an EID-number-pattern value, a real-domain email, base64 image blobs). Low practical risk (the token is short-lived) but not a pattern to extend. See `api-collection-http-requests.md`.
- **`alramz-notification-service`'s `apiCollection.http` references `{{authToken}}` in its Scheduler Management block without ever defining `@authToken`** — a copy-paste artifact; those six requests would send the literal unresolved string as the header value if run as-is.
