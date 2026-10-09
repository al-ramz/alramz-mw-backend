# AGENTS.md

## Project overview

Al Ramz Middleware & Infrastructure: a Maven-reactor monorepo of Java/Spring Boot microservices (payload validation, notifications, reference data) plus the Terraform + Kubernetes IaC that deploys them to Azure. See `README.md` for the full architecture narrative; this file is the precise, agent-facing build/test/style reference — treat `README.md`'s embedded Azure subscription/tenant/client IDs as sensitive and never copy them anywhere.

- Stack: Java 21, Maven 3.9+ (reactor from repo root), Spring Boot 4.1.0, Spring Framework 7.0.8, Liquibase, HikariCP, JWT (`jjwt`), Lombok.
- Architecture: package-by-feature per service (`com.alramz.<feature>.{config,controller(s),service,repository,model|entity,exception}`), not by layer.
- Reactor modules (`pom.xml` at repo root, `<modules>`):
  - `services/alramz-common-bom` — parent POM + BOM + shared plugin config (Checkstyle/PMD/SpotBugs/JaCoCo/Enforcer versions and defaults). No source code.
  - `services/alramz-api-starter` — reusable Spring Boot auto-config starter (JWT security, request/response + DB audit logging, multi-datasource, scheduler, global config settings). A library, not a runnable app.
  - `services/data-validation-service` — payload/IBAN/phone validation service, port `8080` (`:5001` externally per k8s/ingress). The only module using Liquibase + JPA + H2/Postgres/Oracle.
  - `services/alramz-notification-service` — email/notification service via MS Graph + Azure Service Bus/Blob/Key Vault, port `8082`. No datasource (uses H2 as a runtime-only scratch dependency, not Liquibase-managed).
  - `services/reference-data-service` — reference/global-config data service with Redis caching, port `8081` (dev) / `8080` (docker/preprod/prod). Newest scaffold; note its base package is `com.alramz.reference_data_service` (underscore), unlike the plain `com.alramz` package used by the other two services — this is an existing inconsistency, not something to "fix" incidentally.
- Base package: `com.alramz` (all services and the starter), except `reference-data-service`'s own `...Application`/test class, which lives under `com.alramz.reference_data_service`.
- Dependency direction: `alramz-common-bom` (parent/BOM) ← `alramz-api-starter` (depends on the BOM) ← each runnable service (depends on the starter + the BOM as parent). Services never depend on each other.

## Setup

- JDK 21 required (`maven.compiler.release=21` in `services/alramz-common-bom/pom.xml`, enforced by `maven-enforcer-plugin` — build fails under a different JDK or Maven < 3.9.0).
- No root Maven wrapper. `data-validation-service` and `reference-data-service` each carry their own `./mvnw` (Maven 3.9.16, Apache-distributed, see their `.mvn/wrapper/maven-wrapper.properties`) for standalone use; `alramz-notification-service` has no wrapper — use the system `mvn`. **Prefer running Maven from the repo root** so the reactor resolves `alramz-common-bom`/`alramz-api-starter` without needing GitHub Packages auth (both are published to GitHub Packages, declared as a `<repository>` in every service `pom.xml`, but the reactor build resolves them locally without touching that repository).
- If you must build a service in isolation (not `-pl ... -am` from root), install the BOM/starter first:
  ```
  mvn -pl services/alramz-common-bom install -DskipTests
  mvn -pl services/alramz-api-starter -am install -DskipTests
  ```
- Local infrastructure (Postgres + Redis, for `data-validation-service` / `reference-data-service` / `alramz-notification-service` against real Postgres instead of H2):
  ```
  cd release && cp .env.example .env   # fill in values, never commit the filled .env
  docker compose --env-file .env up
  ```
  Required `.env` keys (names only — see `release/.env.example`): `REGISTRY_URL`, `REGISTRY_NAMESPACE`, `REGISTRY_USERNAME`, `REGISTRY_PASSWORD`, `RELEASE_VERSION`, `DATA_VALIDATION_SERVICE_TAG`, `NOTIFICATION_SERVICE_TAG`, `POSTGRES_IMAGE`, `REDIS_IMAGE`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `IBAN_API_KEY`, `VERIPHONE_API_KEY`, `ETRADE_BASE_URL`, `ETRADE_CLIENT_ID`, `ETRADE_CLIENT_SECRET`, `JWT_SECRET`, `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD`, `CIPHER_PASSWORD`.
- Optional local Seq log server: `docker run -d --name seq -e ACCEPT_EULA=Y -e SEQ_FIRSTRUN_ADMINPASSWORD=admin123 -p 5341:80 -p 5342:5341 datalust/seq:latest`.
- Local databases for **tests** are H2 in-memory — no Docker required just to run `mvn test`.

## Development workflow

- Run a service (dev profile, H2/in-memory unless local Postgres/Redis is up):
  - `data-validation-service`: `cd services/data-validation-service && ./mvnw spring-boot:run`
  - `alramz-notification-service`: `cd services/alramz-notification-service && mvn spring-boot:run` (no wrapper)
  - `reference-data-service`: `cd services/reference-data-service && ./mvnw spring-boot:run`
- Ports (from `application-dev.yml` per service; `docker`/`preprod`/`prod` profiles standardize on `8080` behind the ingress/gateway):
  | Service | Dev port |
  |---|---|
  | `data-validation-service` | `8080` |
  | `alramz-notification-service` | `8082` |
  | `reference-data-service` | `8081` |
- Profile selection: `SPRING_PROFILES_ACTIVE={dev,docker,test,preprod,prod}` (default `dev` per each `application.yml`'s `spring.profiles.active`).
- Fast compile check without full tests: `mvn -pl services/<service> -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false`.
- Full build (all modules): `mvn clean package` from repo root.
- Build/test one module + its reactor deps: `mvn -pl services/<service> -am clean test`.

## Testing

- **Canonical "full check" per service** (this is what CI's `maven-test` composite action runs, after installing the BOM): `mvn -B -ntp clean test` from inside `services/<service>` (or `mvn -pl services/<service> -am clean test` from repo root).
- Single test class / single test method — **do not add `-am`** once the BOM/starter are installed once (see Setup): `-am` rebuilds+retests every upstream reactor module too, and Surefire's `-Dtest` filter is applied there as well, so it hard-fails with `No tests matching pattern "<X>" were executed!` against `alramz-api-starter` (verified: reproduces for both `alramz-notification-service` and `reference-data-service`). Use:
  - `mvn -pl services/<service> test -Dtest=DataValidationControllerTest`
  - `mvn -pl services/<service> test -Dtest=DataValidationControllerTest#shouldValidateIban`
  If the BOM/starter haven't been installed locally yet, run the two `install -DskipTests` commands from Setup first, then use the `-pl` form above without `-am`.
- No separate integration-test tier / Failsafe plugin is configured — all tests run under Surefire via the `test` phase. `AopLoggingIntegrationTest` in `data-validation-service` is a Spring context integration test but still runs via `mvn test`, not a `*IT.java` + `verify` split.
- Test frameworks actually in use: JUnit 5 (Spring Boot's default), Mockito (`alramz-notification-service` pulls it explicitly; other modules get it transitively via `spring-boot-starter-test`), AssertJ (`alramz-notification-service`). Tests mirror the main package layout under `src/test/java`.
- `data-validation-service` has by far the deepest test suite (controllers, services, exception handlers, scheduler job, validation registries — see `services/data-validation-service/src/test/java/com/alramz/`). `reference-data-service` currently only has the generated `ReferenceDataServiceApplicationTests` smoke test. `alramz-notification-service` has a single `AppTest`. Treat thin test coverage as a known gap, not a pattern to imitate.
- `data-validation-service` tests run against a Liquibase-migrated H2 schema — `src/test/resources/db/changelog/test/db.changelog-master.yaml` pulls in the shared `db/changelog/sql` plus `src/test/resources/db/changelog/test/sql/`. No Docker/Testcontainers needed.
- **Known pre-existing failures on this branch (verified 2026-09-29, `feature/git-governance`)** — not introduced by an agent's own change, but don't assume a clean baseline:
  - `data-validation-service` currently fails `test-compile`: `DataValidationScheduledJobTest` and `NinTradingNumberValidationServiceTest` reference `com.alramz.utils`, `com.alramz.scheduler.model`, and a `SqlQueriesManager` class that don't currently exist in `src/main/java`. Confirm this is fixed (or was pre-existing before your change) before treating a red build as something you caused.
  - `reference-data-service`'s only test, `ReferenceDataServiceApplicationTests`, currently fails to load the Spring context — Liquibase changelog validation fails during `contextLoads`. Compiles fine; only the actual test run fails.
- Static analysis (Checkstyle/PMD/SpotBugs/JaCoCo) — mirrors CI's `maven-static-analysis` action:
  ```
  mvn -pl services/<service> -am verify -Dcheckstyle.consoleOutput=true -Dpmd.consoleOutput=true -Dspotbugs.consoleOutput=true -Djacoco.skip=false
  ```
  **This does not gate a green build.** Checkstyle's `failOnViolation` is `false`; PMD/SpotBugs are `<skip>true</skip>` in the BOM's `pluginManagement` and only turned on (`skip=false`) per-service in each runnable service's own `pom.xml` (not in `alramz-api-starter`, which stays skipped); JaCoCo's enforced `check` minimums are all `0.00` even though `jacoco.line.coverage`/`branch.coverage`/`instruction.coverage` properties (0.80/0.70/0.75) exist in the BOM — those properties are currently unused by the actual `<rules>` block. Read the console output; `mvn verify` exiting 0 proves nothing about lint or coverage.
- Config file locations: `config/checkstyle.xml`, `config/pmd.xml`, `config/spotbugs-exclude.xml` (all shared across modules via `${project.basedir}/../../config/...` in the BOM).
- Liquibase validation (used by CI, `data-validation-service` and `reference-data-service` only): `mvn -pl services/<service> -am liquibase:validate`.

## Code style

- **Package-by-feature**, not by layer, per service: `com.alramz.<feature>.{config,controller(s),service,repository,model|entity,exception}`.
- **Config classes**: `@ConfigurationProperties(prefix = "...")` on Lombok `@Getter @Setter` classes, with a `public static final String PREFIX` constant and nested static classes for grouped settings (see `DatasourceProperties`, `JwtProperties`, `LoggingProperties` in `alramz-api-starter`).
- **Auto-config classes** (in `alramz-api-starter`): `@AutoConfiguration` + `@ConditionalOnProperty`/`@ConditionalOnClass`/`@ConditionalOnBean`; default to safe/off so upgrading the starter never silently breaks a consuming service. A new `@AutoConfiguration` class is inert unless also added to `services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (currently: `LoggingAutoConfiguration`, `SchedulerAutoConfiguration`, `JwtAutoConfiguration`, `EncryptionAutoConfiguration`, `MiddlewareDataSourceAutoConfiguration`, `BrokDataSourceAutoConfiguration`, `IntegrationDataSourceAutoConfiguration`, `GlobalConfigurationSettingsAutoConfiguration`).
- **Multi-datasource injection**: always use an explicit `@Qualifier` (e.g. `@Qualifier("middlewareNamedParameterJdbcTemplate")`) when injecting `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource`/`TransactionManager` — the starter registers up to three of each (`middleware`, `brok`, `integration`), unqualified injection is ambiguous or silently wrong. Exception: standard Spring Data JPA repositories don't need this in `data-validation-service`/`reference-data-service`, since `DatasourceConfiguration` marks the middleware datasource `@Primary`.
- **Liquibase migrations** (`data-validation-service` and `reference-data-service` only — both under `src/main/resources/db/changelog/`): one file per change, named `NNN-short-description.sql`, using Liquibase "formatted SQL" (`--liquibase formatted sql` + `--changeset <author>:<unique-id>` header), pulled in via profile-specific masters under `db/changelog/{dev,test,preprod,prod,docker}/db.changelog-master.yaml`. Each profile master includes the shared `db/changelog/sql` plus a profile-specific `db/changelog/{profile}/sql/` folder for environment-only changes. Add `--rollback` statements where practical. **Never edit or renumber an already-applied changeset** — Liquibase tracks checksums; append a new one instead.
- **Error handling**: each service defines typed exceptions in its own `exception` package plus a `GlobalExceptionHandler` (`data-validation-service` also has `OnboardingExceptionHandler`) — add new failure modes as typed exceptions, not generic `RuntimeException`.
- **Request-scoped context**: use `JwtContext`/`UserRequestContext` (from `alramz-api-starter`) for the authenticated caller rather than re-parsing tokens/headers in controllers.
- **Lombok everywhere**: `@Getter`/`@Setter`/`@Slf4j`/builders — don't hand-write getters/setters/loggers.
- **Profiles**: `application.yml` + `application-{dev,docker,test,preprod,prod}.yml` per service, selected via `SPRING_PROFILES_ACTIVE`.
- **Contract-first controllers**: API contracts live in `services/<service>/specs/*.yaml` (`apiSpecs.yaml` for `data-validation-service`/`reference-data-service`, `openapi.yaml` for `alramz-notification-service`) and are compiled by `openapi-generator-maven-plugin` (`generate` goal, `generate-sources` phase) into `com.alramz.api` (interfaces only, `interfaceOnly=true`) and `com.alramz.model` under `target/generated-sources/` — **never hand-edit generated code**; edit the spec and re-run `mvn generate-sources` (or any build phase that includes it).
- Do **not** write ad hoc Spring Security config in a service to expose a new endpoint — add it to `company.jwt.permit-all-urls` instead.
- Do **not** inject `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource` without an explicit `@Qualifier` in a multi-datasource module.
- Do **not** hardcode datasource passwords in non-dev profiles — use `cipher.password` + encrypted `password`/`passwordVector`.
- Do **not** add a new `@AutoConfiguration` class without registering it in `AutoConfiguration.imports` — it will silently never load.
- Do **not** log or audit sensitive fields directly — extend `SensitiveDataMasker`'s key set (`company.logging.masking.*`) instead of bypassing masking.

## Database and migrations

- Migrations live in `services/{data-validation-service,reference-data-service}/src/main/resources/db/changelog/`, one `NNN-short-description.sql` file per change, Liquibase "formatted SQL" style, wired through profile-specific `db.changelog-master.yaml` files (see Code style above).
- Applied changesets are immutable — Liquibase tracks checksums; add a new numbered file rather than editing one already merged/deployed.
- Local reset: tests use a fresh in-memory H2 per run (nothing to reset); for a local Postgres reset, bring the `release/docker-compose.yml` stack down and remove its `postgres_data` volume.

## Build and deployment

- Package (skip tests): `mvn clean package -DskipTests -pl services/<service> -am` → artifact at `services/<service>/target/<service>-*.jar`.
- Docker image: `mvn clean package -DskipTests -pl services/<service> -am && docker build -t <service>:latest services/<service>` (each runnable service has its own `Dockerfile`).
- Deployment sizing (cpu/memory/replicas/target port), separate from Spring's `application*.yml`, lives in `services/<service>/service.yaml`.
- Dry-run a Kustomize overlay: `kubectl kustomize k8s/overlays/dev` (`k8s/environments/<env>/` holds only namespace manifests, not a kustomize root).
- Terraform plan (per stack): `cd infra/stacks/dev && terraform init && terraform plan -var-file="../../environments/dev.tfvars"`. State is a shared remote Azure Storage backend per environment — only `infra/stacks/dev` is for routine iteration; never `terraform apply`/`destroy` against `shared-platform`, `qa`, `preprod`, or `prod` without explicit, environment-specific instruction.
- CI (`.github/workflows/cicd.yml`, triggered on push to `dev`/`feature/*`): path-filtered per changed `services/*` module → installs the BOM → `mvn -B -ntp clean test` per changed service (via `.github/actions/maven-test`) → publishes to GitHub Packages → builds/pushes Docker images → deploys to AKS dev → smoke-tests → updates `.github/service-deployment-catalogue/service-register.yml` → auto-opens a promotion PR to the next stage. `.github/workflows/pr-validation.yml` additionally runs static analysis, Liquibase `validate` (for services with a changelog dir), Gitleaks, dependency-review, and Trivy on PRs into `dev`/`qa`/`preprod`/`prod`.
- Promotion flow: `feature/* → dev → qa → preprod → prod`, automated by CI. Do not hand-edit `.github/service-deployment-catalogue/service-register.yml` or manually open promotion PRs — CI owns both.

## Scope boundaries for agents

- Don't touch `infra/` (Terraform) or `.github/workflows/` unless the task explicitly requires it. Keep edits inside the requested `services/<service>` (or the starter) unless the change genuinely spans layers.
- Before reporting a task done, run the targeted test or compile check for the impacted service — at minimum `mvn -pl services/<service> -am test-compile`, and `mvn -pl services/<service> -am test` for behavioral changes.
- Match existing style/structure; don't refactor unrelated code or rewrite whole files when a targeted edit will do.
- **Never** `git add .` / `git add -A` — `services/alramz-api-starter/target/**` has stray tracked compiled classes despite `target/` being gitignored; stage files explicitly.
- Never copy the Azure subscription/tenant/client identifiers embedded in root `README.md`/`commands.md`/`notes.md` into new files, code, or commits.

## Pull requests

- Commit messages: short, imperative, present tense (e.g. `Fix spec file path resolution`); no enforced conventional-commit prefix for humans (automation uses `chore(catalogue): ...`). No commit hooks/linting configured.
- Before proposing a change as done: `mvn -pl services/<service> -am clean test` for the module you touched (CI re-runs this per changed service, plus static analysis separately).
