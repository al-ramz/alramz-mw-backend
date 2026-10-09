---
name: alramz-service-scaffold
description: >
  Scaffold a brand-new Spring Boot microservice inside the alramz-mw-oss Maven-reactor monorepo,
  wired into alramz-common-bom / alramz-api-starter and integrated with its shared frameworks
  (JWT security, request/response + DB audit logging, multi-datasource, scheduler, OpenAPI-generator
  contract-first controllers, Liquibase, Docker, k8s service sizing). Use whenever asked to create a
  new service, bootstrap a microservice, add a module to services/, or "make a service like
  data-validation-service / reference-data-service / alramz-notification-service". Modeled directly
  on services/reference-data-service (the newest, cleanest minimal scaffold in this repo) plus the
  variations found in data-validation-service (JPA/Liquibase-heavy) and alramz-notification-service
  (DB-less, external-integration-heavy).
---

# Al Ramz microservice scaffold

This skill turns the pattern already used by `services/reference-data-service`,
`services/data-validation-service`, and `services/alramz-notification-service` into a repeatable
procedure for standing up a new `services/<service-name>` module. Read this whole file before
touching disk — the order of operations matters (the reactor and the JWT filter both fail closed
if a step is skipped).

Templates referenced below live in `templates/` next to this file, with `__PLACEHOLDER__` tokens.
Copy them, then substitute placeholders (a single `sed` pass per file is enough) rather than
retyping content — this keeps the new service byte-for-byte consistent with the established
pattern instead of drifting.

## 1. Gather inputs before generating anything

Ask (or infer from the request) — don't guess silently on anything load-bearing:

| Placeholder | Meaning | Example |
|---|---|---|
| `__SERVICE_NAME__` | kebab-case Maven artifactId / directory name | `reward-engine-service` |
| `__SERVICE_DISPLAY_NAME__` | Title Case name for the OpenAPI title | `Reward Engine` |
| `__APP_CLASS__` | PascalCase + `Application` | `RewardEngineServiceApplication` |
| `__THREAD_PREFIX__` | short PascalCase prefix for scheduler thread names | `RewardEngine` |
| `__PORT__` | dev port (see §2 for how to pick it) | `8083` |
| `__SERVICE_URL_SEGMENT__` | path segment used in the OpenAPI `servers:` URLs | `reward-engine` |

Also decide, from the request or by asking:
- **Does this service own a database?** Default yes (JPA + Liquibase + middleware datasource), matching
  `reference-data-service`/`data-validation-service`. If no (a pure integration/notification-style
  service like `alramz-notification-service`), use the "DB-less services" variant in §5.
- **Which datasources?** `middleware` only, unless the service explicitly needs `brok` (Oracle) or
  `integration` too — each is a separate Hikari pool requiring its own `@Qualifier` everywhere it's
  injected (CLAUDE.md §4). Don't enable one "just in case."
- **Does it need to call other Al Ramz services, Azure Service Bus/Blob/Key Vault, MS Graph, etc.?**
  If so, note the extra dependencies to add (see `alramz-notification-service/pom.xml` for the
  Azure/Graph dependency set) — this skill only covers the shared-framework baseline, not
  service-specific integrations.

Picking `__PORT__`: grep existing `server.port` values (`grep -rn "port: 80" services/*/src/main/resources/application*.yml`) and pick the next unused `808x`. `data-validation-service` is `5001`, `alramz-notification-service` is `8082`; `reference-data-service` dev is `8081`. Non-dev profiles all bind `8080` inside their own container — the Kubernetes Service is what makes the external port distinct, not `application-prod.yml`.

## 2. Directory + file checklist

Create `services/__SERVICE_NAME__/` with this layout (paths relative to that root). For each row,
copy the named template from `templates/`, substitute placeholders, and write it to the target path.

| Target path | Template |
|---|---|
| `pom.xml` | `pom.xml.template` |
| `Dockerfile` | `Dockerfile.template` |
| `service.yaml` | `service.yaml.template` |
| `.gitignore` | `gitignore.template` |
| `.gitattributes` | `gitattributes.template` |
| `apiCollection.http` | `apiCollection.http.template` |
| `specs/apiSpecs.yaml` | `apiSpecs.yaml.template` |
| `src/main/java/com/alramz/__APP_CLASS__.java` | `Application.java.template` |
| `src/main/java/com/alramz/controllers/ApplicationController.java` | `ApplicationController.java.template` |
| `src/main/java/com/alramz/config/JacksonConfig.java` | `JacksonConfig.java.template` |
| `src/main/resources/application.yml` | `application.yml.template` |
| `src/main/resources/application-dev.yml` | `application-profile.yml.template` (`__PROFILE__=dev`) |
| `src/main/resources/application-preprod.yml` | `application-profile.yml.template` (`__PROFILE__=preprod`, `__PORT__=8080`) |
| `src/main/resources/application-prod.yml` | `application-profile.yml.template` (`__PROFILE__=prod`, `__PORT__=8080`) |
| `src/main/resources/application-docker.yml` | `application-docker.yml.template` |
| `src/main/resources/application-test.yml` | `application-test.yml.template` |
| `src/main/resources/db/changelog/{dev,docker,preprod,prod}/db.changelog-master.yaml` | `db.changelog-master.yaml.template` (one per profile) |
| `src/main/resources/db/changelog/dev/sql/001-*.sql` (+ same file mirrored into `docker/`, `preprod/`, `prod/` sql folders) | `001-example-changeset.sql.template` — only if the service owns real schema on day one; otherwise skip and leave the `sql/` folders to be created by the first real feature PR |
| `src/test/resources/application.yml` | `application-test-resources.yml.template` |
| `src/test/resources/db/changelog/test/db.changelog-master.yaml` | `db.changelog-master.yaml.template` (`__PROFILE__=test`) |
| `src/test/java/com/alramz/__APP_CLASS__Tests.java` | `ApplicationTests.java.template` |

Also **copy verbatim** (don't template — these are the Maven Wrapper, and must match the working
wrapper exactly) from `services/data-validation-service/`, which has the wrapper checked in
(`reference-data-service` also has one you can copy from):
- `mvnw`, `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`

Use package `com.alramz` (not `com.alramz.<service_name>`) for the application class, controllers,
and config — every service in this repo shares that top-level package by convention (see CLAUDE.md
§4 "package-by-feature", and compare `data-validation-service`'s and `reference-data-service`'s
`com.alramz.*` layouts). The one place to actively avoid copying a mistake: `reference-data-service`'s
test class ended up in package `com.alramz.reference_data_service` (a Spring Initializr default it
never cleaned up, documented in its own `HELP.md`) — put the new service's test class in plain
`com.alramz`, matching `data-validation-service`, not that.

## 3. Wire it into the Maven reactor

**This step is easy to miss** — `reference-data-service` itself is not yet listed in the root
`pom.xml`'s `<modules>`, which means `mvn clean package` from the repo root currently skips it
entirely. Don't repeat that for the new service: add

```xml
<module>services/__SERVICE_NAME__</module>
```

to the root `pom.xml`'s `<modules>` block, in the same position other service modules appear.

## 4. Framework integration checklist

Walk this list for every new service — each row is a place a new service silently breaks if
skipped, because `alramz-api-starter`'s auto-configurations default to "off" or "deny" (CLAUDE.md §3):

- **JWT / public endpoints**: `company.jwt.enabled: true` is already in every profile template.
  Every endpoint that must be reachable without a bearer token (health/info, webhooks, etc.) needs
  BOTH `@PermitAll` on the controller method AND its path added to `company.jwt.permit-all-urls` in
  *every* profile file — `/api/v1/info` is already wired as the example. Never write ad hoc Spring
  Security config to open an endpoint instead.
- **Startup banner**: `Application.java.template`'s `ApplicationRunner` bean logs application name,
  active profile (environment), host, and port on every startup — a repo-wide convention (see
  `reference-data-service/src/main/java/com/alramz/ReferenceDataServiceApplication.java`). Keep this
  bean when writing the new service's `Application` class; don't drop it as unused boilerplate.
- **Datasources**: only enable the ones this service actually uses (§1). Every
  `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource`/`TransactionManager` injection needs an
  explicit `@Qualifier("middlewareJdbcTemplate")` (or `brok…`/`integration…`) — the one documented
  exception is plain Spring Data JPA repositories, which don't need a qualifier because
  `DatasourceConfiguration` marks `middleware` `@Primary`.
- **Non-dev secrets**: `application-preprod.yml`/`application-prod.yml` must use `cipher.password` +
  encrypted `password`/`passwordVector`, never `plainPassword` — `plainPassword` is dev-only and logs
  a warning if used elsewhere. (Note: `reference-data-service`'s own `application-prod.yml` currently
  hardcodes a plaintext Oracle `brok` password for illustration/history — don't copy that particular
  block; follow the encrypted-password instruction in `application-profile.yml.template` instead.)
- **DB audit logging** (`company.logging.database-logging.enabled`): on by default in every profile
  template; it writes to `api_audit_log` through the `middleware` datasource specifically and
  no-ops if that datasource bean is absent — harmless to leave on even for DB-less services, but
  confirm `middleware` really is enabled if you expect audit rows to appear.
- **Sensitive fields**: never log/audit passwords, tokens, EID/passport-style fields directly —
  extend `SensitiveDataMasker`'s key set (`company.logging.masking.*`) instead of hand-rolling
  masking in the new service.
- **Contract-first controllers**: add every endpoint to `specs/apiSpecs.yaml` first. The
  `openapi-generator-maven-plugin` execution (already in `pom.xml.template`, bound to Maven's
  `generate-sources`/`generate` phase) produces interface-only stubs into `com.alramz.api`
  (controllers to implement) and `com.alramz.model` (DTOs) on every build — write a
  `@RestController` that `implements` the generated interface rather than hand-building endpoints
  and DTOs from scratch. `ApplicationController` for `/api/v1/info` is the one exception (predates
  the generator convention in these services) — new endpoints should follow the generated pattern.
- **Liquibase changesets** (services with a DB only): one file per change, `NNN-description.sql`,
  `--liquibase formatted sql` + `--changeset <author>:<id>` header, copied into *every* profile's
  `sql/` folder (`dev`, `docker`, `preprod`, `prod`, plus `src/test/resources/db/changelog/test/sql`)
  — there is no shared changelog directory in this repo. Add `--rollback` where practical. Never
  edit or renumber a changeset that has already run anywhere.
- **Typed exceptions**: add a `com.alramz.exception` package with a `GlobalExceptionHandler`
  (`@ControllerAdvice`) as soon as the service has its first real endpoint beyond `/api/v1/info` —
  see `data-validation-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java` for
  the shape (typed exceptions, not generic `RuntimeException`).
- **Lombok**: use `@Getter`/`@Setter`/`@Slf4j`/builders everywhere; the `pom.xml` and
  `maven-compiler-plugin` annotation-processor wiring for it is already in the template.

## 5. DB-less services (variant)

If the service has no database of its own (modeled on `alramz-notification-service`):
- In `pom.xml.template`: drop `spring-boot-starter-data-jpa`, `spring-boot-starter-liquibase`,
  `spring-boot-starter-data-jpa-test`, and the whole `liquibase-maven-plugin` `<plugin>` block.
- In every `application-*.yml`: drop the `spring.liquibase.change-log` line and the whole
  `company.datasource:` block.
- Skip the entire `db/changelog/` tree (§2) and the Liquibase row in §4.
- `spring.jpa.hibernate.ddl-auto: validate` in `application.yml.template` can also be dropped since
  there's no JPA.
- Add whatever this service actually integrates with (Azure Service Bus/Blob/Key Vault, MS Graph,
  a third-party REST client, etc.) as its own dependencies — see
  `alramz-notification-service/pom.xml` for the Azure/Graph dependency block as a reference, not a
  template to copy blindly (only add what this new service really calls).

## 6. Verify before calling it done

Per CLAUDE.md §0/§5, run from the repo root:

```bash
mvn -pl services/__SERVICE_NAME__ -am test-compile
mvn -pl services/__SERVICE_NAME__ -am test
```

A clean `mvn verify` does **not** mean clean lint/coverage (Checkstyle is non-blocking, PMD/SpotBugs
default `<skip>true</skip>` in the BOM even though this template turns them back on per-module,
JaCoCo minimums are 0.00) — read the console output if you also run
`mvn -pl services/__SERVICE_NAME__ -am verify -Dcheckstyle.consoleOutput=true -Dpmd.consoleOutput=true -Dspotbugs.consoleOutput=true -Djacoco.skip=false`.

Also smoke-check locally once it compiles:
```bash
cd services/__SERVICE_NAME__ && ./mvnw spring-boot:run
curl http://localhost:__PORT__/api/v1/info
```

## 7. Explicitly out of scope for this skill

Per CLAUDE.md §0's scope boundary, generating a service does **not** include, and these need
separate, explicit instruction:
- `.github/workflows/` — `cicd.yml` path-filters and auto-builds per changed `services/*` module
  automatically; nothing to edit there for a brand-new service to get picked up by the main
  pipeline. But `feature-deploy.yml`, `feature-deploy-fast.yml`, `restart-pods.yml`, and
  `rollback.yml` each have a hardcoded service dropdown (currently only listing
  `data-validation-service`) — adding the new service to those dropdowns is a workflow-file edit
  and needs explicit sign-off, not something to do as part of scaffolding.
- `.github/service-deployment-catalogue/service-register.yml` — CI owns this; never hand-edit it.
- `infra/` (Terraform) — no Terraform changes are needed just to add a Maven module; only touch
  `infra/` if the service needs new infrastructure (a new Key Vault secret, a new AKS resource,
  etc.), and only in `infra/stacks/dev` without further instruction.
- `k8s/overlays/<env>` — only `service.yaml` (sizing, consumed by the Kustomize base) is part of
  this scaffold; wiring a new overlay entry, if one is needed beyond what the base/overlay pattern
  already picks up generically, is a separate task.

## 8. Git hygiene

Per CLAUDE.md §0: stage the new files explicitly (`git add services/__SERVICE_NAME__ pom.xml`), never
`git add -A`/`git add .` — `services/alramz-api-starter/target/**` has stray tracked compiled classes
in this repo despite `target/` being gitignored.
