# Architecture

## Core Sections (Required)

### 1) Architectural Style

- Primary style: **Package-by-feature layered microservices**, sharing cross-cutting concerns through a single Spring Boot auto-configuration library (`alramz-api-starter`) rather than through inheritance or a shared runtime process.
- Why this classification: each of the 3 runnable services (`data-validation-service`, `alramz-notification-service`, `reference-data-service`) is an independent Spring Boot app with its own `controllers → service → repository/client` package stack (evidence: package listings in STRUCTURE.md §3), but they all pull in `alramz-api-starter` as a Maven dependency and inherit JWT security, request/response/audit logging, multi-datasource wiring, and a DB-backed scheduler purely through Spring Boot's `@AutoConfiguration` + `AutoConfiguration.imports` mechanism (`services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).
- Primary constraints: (1) a feature is inert in every consuming service unless registered in that one `AutoConfiguration.imports` file (CLAUDE.md §3); (2) each auto-config defaults "off" and activates via `@ConditionalOnProperty`, so upgrading the starter must never silently change consumer behavior; (3) contract-first controllers — the OpenAPI YAML in `services/<service>/specs/*.yaml` is the source of truth, and `*Api` interfaces are code-generated at build time (`openapi-generator-maven-plugin`), so controllers implement generated interfaces rather than declaring their own `@RequestMapping` signatures from scratch.

### 2) System Flow

```text
[HTTP request] -> [JwtAuthFilter (starter, addFilterBefore UsernamePasswordAuthenticationFilter)]
               -> [CorrelationIdFilter / RequestLoggingFilter (starter servlet filters)]
               -> [Controller implementing generated *Api interface, annotated @JwtSecured + @Loggable]
               -> [Service layer (business/validation logic, e.g. IBANValidationServiceImpl)]
               -> [Repository / AbstractRestClient (JDBC via named datasource, or outbound HTTP to external system)]
               -> [GlobalExceptionHandler (@RestControllerAdvice) shapes error responses per path prefix]
               -> [ResponseLoggingFilter + ApiAuditLogFilter (async, masked) write to api_audit_log via the `middleware` datasource]
```

Traced example (`data-validation-service`, `POST /api/v1/existing-data/validation`): `DataValidationController.validateExistingData` (implements generated `ExistingDataApi`, guarded by `@JwtSecured(roles = "APP_DATA_VALIDATION")` and `@Loggable`) delegates to `ValidationService` → `ValidationServiceImpl`, which resolves the right `ValidationResponseMapper` via `ValidationResponseMapperRegistry` and calls out to the E-Trade SOAP/REST bridge through `ETradeClient` (extends the starter's `AbstractRestClient`). Failures are caught in the controller/`GlobalExceptionHandler` and mapped to a `GenericResponse` with a `responseCode`/`correlationId`, branching by request path (`/api/v1/existing-data/validation` vs. `/api/v1/dfm/onboarding`) to produce two different response shapes from the same handler class (`services/data-validation-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java`).

### 3) Layer/Module Responsibilities

| Layer or module | Owns | Must not own | Evidence |
|-----------------|------|--------------|----------|
| `alramz-api-starter` (JWT) | Stateless `SecurityFilterChain`, `@JwtSecured`/`@PermitAll` method-level enforcement via AOP (`JwtSecuredAspect`), token issuing (`TokenProvider`, `AuthService`) | Business authorization rules specific to one service | `com/alramz/jwt/config/JwtAutoConfiguration.java` |
| `alramz-api-starter` (logging) | Request/response filters, correlation IDs, DB audit log (`api_audit_log` via the `middleware` datasource only), AOP method-execution timing, Seq/JSON log encoding, `SensitiveDataMasker` | Business logic that needs its own audit schema | `com/alramz/logging/**`, `com/alramz/audit/**` |
| `alramz-api-starter` (datasource) | Up to 3 independently qualified Hikari pools (`middleware`, `brok`, `integration`), each `@ConditionalOnProperty`-gated, with `PWProtector` cipher-based decryption | Direct unqualified `DataSource`/`JdbcTemplate` injection | `com/alramz/datasource/config/*AutoConfiguration.java` |
| `alramz-api-starter` (scheduler) | DB-persisted job definitions (`ScheduleJobEntity`), `Schedulable` interface, `JobScheduleManager` runtime, `SchedulerManagerController` for runtime refresh/restart | Job-specific business logic (the job bean itself lives in the consuming service, e.g. `CacheReloadScheduledJob` in `reference-data-service`) | `com/alramz/scheduler/**`; `services/reference-data-service/src/main/java/com/alramz/scheduler/job/CacheReloadScheduledJob.java` |
| `data-validation-service` | IBAN/phone/existing-data/onboarding validation against external systems (IBAN.com, VeriPhone, E-Trade) | Notification/email sending, cache management | `services/data-validation-service/src/main/java/com/alramz/service/**` |
| `alramz-notification-service` | Email composition/sending via MS Graph, Azure Service Bus/Blob integration | Payload validation, reference-data caching | `services/alramz-notification-service/src/main/java/com/alramz/service/EmailServiceImpl.java` |
| `reference-data-service` | Redis-backed cache of `GlobalConfigurationSettings`, scheduled cache reload/warmup, distributed lock via `SchedulerLockService` | Payload validation, JWT issuing (it only *consumes* the starter's JWT auto-config) | `services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java`, `CacheAuditService.java` |

### 4) Reused Patterns

| Pattern | Where found | Why it exists |
|---------|-------------|----------------|
| Auto-configuration / feature-flagged library | `alramz-api-starter`'s `*AutoConfiguration` classes, each `@ConditionalOnProperty` | Lets 3+ independent services share JWT/logging/datasource/scheduler code without copy-paste, and lets each service opt in/out per `application.yml` flag (CLAUDE.md §3 table) |
| Contract-first codegen (OpenAPI → Spring interfaces) | `services/<service>/specs/*.yaml` + `openapi-generator-maven-plugin` in each service `pom.xml`; controllers `implements <Generated>Api` | Keeps the OpenAPI spec (used for the published docs site under `api-docs-build/`) authoritative over hand-written controller signatures |
| Registry / Strategy | `ValidationResponseMapperRegistry` picking a `ValidationResponseMapper` per validation type in `data-validation-service`; `ValidationDefinitionRegistry` | Adding a new "existing data" check (EID, passport, mobile, etc.) means adding a new mapper + registry entry, not branching in the controller |
| AOP cross-cutting concerns | `@Loggable` + `MethodExecutionLoggingAspect` (method timing), `@JwtSecured` + `JwtSecuredAspect` (authorization), `ApiAuditAspect` | Keeps security/logging out of controller/service method bodies |
| Repository wrapper over generated JPA repos | `UserRepository` wraps `UserJpaRepository`; `RefreshTokenRepository` wraps `RefreshTokenJpaRepository` (both in the starter's JWT package) | Lets the starter expose a stable `*RepositoryOps` interface to `AuthService` independent of Spring Data specifics |
| Distributed scheduler lock | `SchedulerLockService.tryAcquireLock/releaseLock/cleanStaleLocks` used by `CacheReloadScheduledJob` | Prevents duplicate cache-reload execution when a service scales to multiple pods |

### 5) Known Architectural Risks

- **Documented port/deployment facts don't match the code**: CLAUDE.md and README.md both state `data-validation-service` runs on port `5001`; every committed `application-*.yml` for that service sets `server.port: 8080` (see STRUCTURE.md §2). Anyone following the docs to hit the service locally will fail. Likewise, CLAUDE.md says per-service K8s sizing lives in `services/<service>/service.yaml`; no such file exists anywhere in `services/` — sizing is actually in `k8s/apps/base/<service>/deployment.yaml`.
- **`reference-data-service` is a 5th reactor module with no equivalent in CLAUDE.md's "Runnable services" table and no `k8s/apps/base/reference-data-service/` manifests yet** — it exists in `pom.xml`, has its own port/profiles/Dockerfile, but isn't wired into the same deployment path as the other two services (`k8s/apps/base/` only has `alramz-notification-service` and `data-validation-service`). Anyone deploying it must reconstruct the K8s manifests by analogy.
- **Azure Managed Redis auth may not survive long-lived connections**: `RedisConfig.createAzureRedisConnectionFactory()` overrides `getPassword()` to fetch a fresh AAD token, but Lettuce/`LettuceConnectionFactory` does not necessarily re-invoke `getPassword()` on an already-open connection — if the underlying token (typically ~1hr TTL) expires before Lettuce reconnects, Redis calls could start failing silently in production. (`services/reference-data-service/src/main/java/com/alramz/config/RedisConfig.java`)
- **Secondary "agent tooling" tree checked into the working copy**: `.kilo/worktrees/*` contains 4 full git worktrees (each with its own `services/`, `infra/`, `pom.xml`) nested inside the main repository tree, outside `.gitignore`'s effective reach for such content. This roughly quadruples on-disk size for anyone cloning fresh and risks accidental edits landing in the wrong worktree. See CONCERNS.md.

### 6) Evidence

- `services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `services/data-validation-service/src/main/java/com/alramz/controllers/DataValidationController.java`
- `services/data-validation-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java`
- `services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java`
- `services/reference-data-service/src/main/java/com/alramz/scheduler/job/CacheReloadScheduledJob.java`, `com/alramz/config/RedisConfig.java`
- `k8s/apps/base/data-validation-service/deployment.yaml`

## Extended Sections (Optional)

Not populated.
