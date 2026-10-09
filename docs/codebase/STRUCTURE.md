# Codebase Structure

## Core Sections (Required)

### 1) Top-Level Map

| Path | Purpose | Evidence |
|------|---------|----------|
| `services/` | Maven-reactor microservices + shared parent/starter | root `pom.xml` `<modules>` |
| `services/alramz-common-bom/` | Parent POM: Java/Spring/plugin version management, Checkstyle/PMD/SpotBugs/JaCoCo/Enforcer config | `services/alramz-common-bom/pom.xml` |
| `services/alramz-api-starter/` | Shared Spring Boot auto-config library (JWT, logging, multi-datasource, scheduler, audit, global config settings) — not a runnable app | `services/alramz-api-starter/src/main/java/com/alramz/App.java`; `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |
| `services/data-validation-service/` | Payload/IBAN/phone/onboarding validation microservice | `services/data-validation-service/src/main/java/com/alramz/DataValidationServiceApplication.java` |
| `services/alramz-notification-service/` | Email/notification microservice (MS Graph, Azure Service Bus/Blob) | `services/alramz-notification-service/src/main/java/com/alramz/AlramzNotificationServiceApplication.java` |
| `services/reference-data-service/` | 5th Maven module: Redis-backed reference/global-config cache service with scheduled cache reloads — **not documented in CLAUDE.md's "Runnable services" table** (see divergence note) | `services/reference-data-service/pom.xml`; `src/main/java/com/alramz/ReferenceDataServiceApplication.java` |
| `infra/` | Terraform IaC for Azure (modules + per-environment stacks) | `infra/modules/*`, `infra/stacks/*`, `infra/environments/*.tfvars` |
| `k8s/` | Kustomize base + overlays for AKS deployment, plus ArgoCD manifests and infra add-ons (postgres/redis/seq/headlamp) | `k8s/apps/base/*`, `k8s/apps/overlays/*`, `k8s/argocd/`, `k8s/infra/*` |
| `.github/` | CI/CD workflows, composite actions, deployment catalogue, secrets docs | `.github/workflows/*.yml`, `.github/actions/*`, `.github/service-deployment-catalogue/service-register.yml` |
| `config/` | Shared static-analysis configs used by every Maven module | `config/checkstyle.xml`, `config/pmd.xml`, `config/spotbugs-exclude.xml` |
| `release/` | Local Docker-Compose stack + release packaging scripts | `release/docker-compose.yml`, `release/.env.example`, `release/scripts/` |
| `docs/` | Architecture diagrams, setup guides, and (now) this codebase-knowledge base | `docs/*.svg`, `docs/QUICK-START.md`, `docs/LOCAL-SETUP.md`, `docs/codebase/` |
| `e2e/` | Postman collection + environment for cross-service API testing (untracked/new at scan time) | `e2e/alramz-onbaording-apis-uie.postman_collection.json`, `e2e/local.postman_environment.json` |
| `.claude/` | Claude Code skills, plans, and settings for this repo | `.claude/skills/*`, `.claude/plans/*` |
| `.kilo/` | A second, parallel agent-tool workspace (Kilo) with its own plan history and **git worktrees checked into the working tree** (`carbonated-kookaburra`, `meowing-tarascosaurus`, `pie-maraca`, `superb-sagittarius`) — see CONCERNS.md | `.kilo/plans/*`, `.kilo/worktrees/*` |

### 2) Entry Points

- Runnable service entry points: `DataValidationServiceApplication.java` (`@SpringBootApplication`, port 8080 in every profile — see divergence note below), `AlramzNotificationServiceApplication.java` (port 8082), `ReferenceDataServiceApplication.java` (port 8081 in dev, 8080 elsewhere).
- Starter's own harness (not a real app, used for local compilation sanity only): `services/alramz-api-starter/src/main/java/com/alramz/App.java`.
- Secondary entry points: none — no standalone CLI/worker/batch `main()` beyond the three `@SpringBootApplication` classes; background execution happens through the starter's `@EnableScheduler`-driven scheduler (`com.alramz.scheduler.service.impl.JobScheduleManager`), not a separate process.
- How entry is selected: `SPRING_PROFILES_ACTIVE` selects the environment profile (`dev`/`docker`/`test`/`preprod`/`prod`); each profile's `application-<profile>.yml` sets `server.port`. Evidence: `services/data-validation-service/src/main/resources/application-dev.yml:7` (`port: 8080`) vs. CLAUDE.md's documented port `5001` and README.md's documented `curl http://localhost:5001/...` — **neither matches the committed YAML**, which is 8080 across dev/docker/test/preprod/prod.

### 3) Module Boundaries

| Boundary | What belongs here | What must not be here |
|----------|-------------------|------------------------|
| `alramz-api-starter` | Cross-cutting, reusable auto-configuration (JWT, logging/audit, multi-datasource, scheduler, global config settings) — activates only via `AutoConfiguration.imports` + `@ConditionalOnProperty` | Service-specific business/domain logic |
| `services/<service>/controllers` | Thin controllers implementing OpenAPI-generated `*Api` interfaces, delegating to `service` layer | Direct JDBC/JPA access, validation logic |
| `services/<service>/service` (+ `service/impl`) | Business logic, orchestration, calls to `repository`/`client` layers | HTTP request/response shaping |
| `services/<service>/repository` | Data access (JPA repositories or raw JDBC via named datasources) | Business rules |
| `services/<service>/config` | `@ConfigurationProperties` classes and service-specific `@Configuration` (e.g. `DatasourceConfiguration`, `GraphConfig`, `RedisConfig`) | Request handling |
| `services/<service>/exception` + `GlobalExceptionHandler` | Typed exceptions and `@RestControllerAdvice` mapping to response shape | Silent exception swallowing |
| `infra/` (Terraform) | Azure resource provisioning only | Application code, secrets in plaintext |
| `k8s/apps/base` + `overlays/<env>` | Kustomize-composable deployment manifests | Environment-specific literal values (those belong in `overlays/<env>` patches) |

### 4) Naming and Organization Rules

- File naming pattern: PascalCase Java classes matching their public type (`DataValidationController.java`, `JwtAutoConfiguration.java`); YAML/property files kebab-case where multi-word (`application-preprod.yml`); Liquibase changesets `NNN-short-description.sql`.
- Directory organization pattern: **package-by-feature**, not by layer, at the top level (`com.alramz.jwt`, `com.alramz.logging`, `com.alramz.scheduler`, `com.alramz.datasource`, `com.alramz.globalconfigurationsettings` inside the starter; `com.alramz.<feature>` inside each service) — each feature package then splits internally into `config/controller/service/repository/model/exception` per CLAUDE.md §4. Evidence: `services/alramz-api-starter/src/main/java/com/alramz/` top-level package listing.
- Import aliasing or path conventions: none (plain Java imports, no module path aliases); Maven reactor module resolution replaces the need for path aliasing.

### 5) Evidence

- `docs/codebase/.codebase-scan.txt` (directory tree, monorepo signal, code metrics)
- root `pom.xml`; `services/*/pom.xml`
- `services/alramz-api-starter/src/main/java/com/alramz/` (full package listing obtained via `find`)
- `k8s/apps/base/data-validation-service/*` (only 2 of the 3 runnable services have base K8s manifests — `reference-data-service` has none yet)

## Extended Sections (Optional)

Not populated.
