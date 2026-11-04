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
  (DB-less, external-integration-heavy). Generates secure-by-default preprod/prod config and is only
  done when the new service builds, passes its tests, and its packaged jar starts and answers a smoke
  check (via the shared startup-check.sh).
---

# Al Ramz microservice scaffold

This skill turns the pattern already used by `services/reference-data-service`,
`services/data-validation-service`, and `services/alramz-notification-service` into a repeatable
procedure for standing up a new `services/<service-name>` module. Read this whole file before
touching disk — the order of operations matters (the reactor and the JWT filter both fail closed
if a step is skipped).

Templates referenced below live in `templates/` next to this file, with `__PLACEHOLDER__` tokens.
Render them with `scripts/render_template.py <template> <output> KEY=VALUE…` (KEY without the
underscores) rather than retyping content — this keeps the new service byte-for-byte consistent with
the established pattern instead of drifting. Use the script, not `sed`: several values contain
`$ { } | # * /` (a `#` inside a value broke a `sed s#…#…#` pass in practice), it substitutes longer
keys first (so `__SERVICE_NAME__` can't clobber `__SERVICE_NAME_UPPER__`), and it exits non-zero
naming any placeholder left unfilled.

`services/__SERVICE_NAME__/` may already exist as an uncommitted Spring Initializr skeleton
(`HELP.md`, `.vscode/`, `application.properties`, empty `static/`/`templates/`). Check `git status`
first; if it's untracked Initializr output, delete those leftovers before rendering — the scaffold
replaces every file Initializr creates.

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

Picking `__PORT__`: grep existing dev ports (`grep -rn "^  port:" services/*/src/main/resources/application-dev.yml`) and pick the next unused `808x` — don't trust a hardcoded list here; on 04-11-2026 8080–8086 were taken (onboarding, reference-data, notification, utility, reporting, wealth, risk). Non-dev profiles all bind `8080` inside their own container — the Kubernetes Service is what makes the external port distinct, not `application-prod.yml`.

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
| `src/main/java/com/alramz/config/DatasourceConfiguration.java` | `DatasourceConfiguration.java.template` (copy verbatim; DB-owning services only) — exposes `middleware` as the `@Primary` `DataSource` and as the bean named `transactionManager`. Without it the starter's JPA repositories (`UserRepository.save` on `/api/auth/register`) fail at runtime with `No bean named 'transactionManager'`. `contextLoads` and even `/api/auth/login` still work without it (derived finders skip that lookup) — only the wiring test in `ApplicationTests.java.template` catches it |
| `src/main/resources/application.yml` | `application.yml.template` |
| `src/main/resources/application-{dev,preprod,prod}.yml` | `application-profile.yml.template`, once per profile, with the values in **Per-profile values** below |
| `src/main/resources/application-docker.yml` | `application-docker.yml.template` |
| `src/main/resources/application-test.yml` | `application-test.yml.template` |
| `src/main/resources/db/changelog/{dev,docker,preprod,prod}/db.changelog-master.yaml` | `db.changelog-master.yaml.template` (one per profile) |
| `src/main/resources/db/changelog/dev/sql/001-*.sql` (+ same file mirrored into `docker/`, `preprod/`, `prod/` sql folders) | `001-example-changeset.sql.template` — only if the service owns real schema on day one; otherwise skip and leave the `sql/` folders to be created by the first real feature PR |
| `src/test/resources/application.yml` | `application-test-resources.yml.template` — includes `company.datasource.middleware` on H2; `@SpringBootTest` boots with no profile, so without that block `DatasourceConfiguration` has no `middlewareDataSource` to wire and the context fails |
| `src/test/resources/db/changelog/test/db.changelog-master.yaml` | `db.changelog-master.yaml.template` (`__PROFILE__=test`) |
| `src/test/resources/db/changelog/test/sql/{001-api-audit-log,002-jwt-tables}.sql` | copy verbatim from `services/utility-service/src/test/resources/db/changelog/test/sql/` — the starter's JPA entities (`jwt_user`, `jwt_refresh_token`) fail `ddl-auto: validate` on the test H2 DB without them. **Test folder only**: the shared middleware DB already has these tables (created by onboarding-service's changesets), so don't add them to the main-profile `sql/` folders |
| `src/test/java/com/alramz/__APP_CLASS__Tests.java` | `ApplicationTests.java.template` — `contextLoads` plus `middlewareIsWiredAsPrimaryDataSourceAndTransactionManager` (DB-less services: delete the second test) |

**Per-profile values** for `application-profile.yml.template` (preprod/prod use the no-default forms so
a missing secret fails startup instead of falling back to a value committed in git — this is what
every existing service's preprod/prod already does; generating them insecure and hardening by hand
later is how a scaffold shipped `JWT_SECRET`/DB-password defaults and `include: "*"` to prod):

| Placeholder | dev | preprod / prod |
|---|---|---|
| `PROFILE` | `dev` | `preprod` / `prod` |
| `PORT` | the 808x picked in §1 | `8080` |
| `MIDDLEWARE_DB` | `alramz-mw-dev-db` | `alramzmwdb` |
| `TRACING_OTLP_DEFAULT` | `http://172.25.21.114:4318/v1/traces` | *(empty)* |
| `LOGS_OTLP_DEFAULT` | `http://172.25.21.114:3100/otlp/v1/logs` | *(empty)* |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | `86400000` | `900000` |
| `ACTUATOR_EXPOSURE` | `"*"` | `health,info,prometheus` |
| `CIPHER_PASSWORD` | `UseMostSecretKey` | `${CIPHER_MASTER_KEY}` |
| `JWT_SECRET` | `${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}` | `${JWT_SECRET}` |
| `REGISTRATION_ENABLED` | `true` | `false` |
| `MIDDLEWARE_PASSWORD_LINE` | `plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD:alramz!321*}` | `password: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD}` |
| `MIDDLEWARE_VECTOR_LINE` | `# passwordVector: not used with plainPassword` | `passwordVector: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD_VECTOR}` |

Plus `SERVICE_NAME` and `THREAD_PREFIX` for all three. Example (prod):
```bash
S=.claude/skills/alramz-service-scaffold; R=services/__SERVICE_NAME__/src/main/resources
python3 $S/scripts/render_template.py $S/templates/application-profile.yml.template $R/application-prod.yml \
  SERVICE_NAME=__SERVICE_NAME__ THREAD_PREFIX=__THREAD_PREFIX__ PROFILE=prod PORT=8080 MIDDLEWARE_DB=alramzmwdb \
  TRACING_OTLP_DEFAULT= LOGS_OTLP_DEFAULT= JWT_ACCESS_TOKEN_EXPIRATION_MS=900000 \
  'ACTUATOR_EXPOSURE=health,info,prometheus' 'CIPHER_PASSWORD=${CIPHER_MASTER_KEY}' 'JWT_SECRET=${JWT_SECRET}' \
  REGISTRATION_ENABLED=false 'MIDDLEWARE_PASSWORD_LINE=password: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD}' \
  'MIDDLEWARE_VECTOR_LINE=passwordVector: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD_VECTOR}'
```
Single-quote any value containing `$`. docker and test profiles have their own templates and keep
local defaults (they never reach a shared environment) with registration left off.

Also **copy verbatim** (don't template — these are the Maven Wrapper, and must match the working
wrapper exactly) from `services/reference-data-service/` (any existing service has the same wrapper):
- `mvnw`, `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`

Use package `com.alramz` (not `com.alramz.<service_name>`) for the application class, controllers,
and config — every service in this repo shares that top-level package by convention (see CLAUDE.md
§4 "package-by-feature", and compare `utility-service`'s and `reference-data-service`'s
`com.alramz.*` layouts). The one place to actively avoid copying a mistake: `reference-data-service`'s
test class ended up in package `com.alramz.reference_data_service` (a Spring Initializr default it
never cleaned up, documented in its own `HELP.md`) — put the new service's test class in plain
`com.alramz`, matching `utility-service`, not that.

## 3. Wire it into the Maven reactor

**This step is easy to miss** — a module missing from the root `pom.xml`'s `<modules>` is silently
skipped by `mvn clean package` from the repo root (this happened to `reference-data-service` once).
Add

```xml
<module>services/__SERVICE_NAME__</module>
```

to the root `pom.xml`'s `<modules>` block, in the same position other service modules appear.

**Also add a VS Code launch entry** to the root `.vscode/launch.json` (create the file if it's
missing; keep entries alphabetical). Don't skip this; every service has one:

```json
{
    "type": "java",
    "name": "__SERVICE_NAME__",
    "request": "launch",
    "mainClass": "com.alramz.__APP_CLASS__",
    "projectName": "__SERVICE_NAME__",
    "cwd": "${workspaceFolder}/services/__SERVICE_NAME__",
    "vmArgs": "-Dspring.profiles.active=dev"
}
```

`.vscode/` is gitignored, so this only updates the local workspace and won't show up in `git status`.
That also makes the entry's `"env": { … }` the place for **local secret values** a service needs to
start (vendor API keys etc. — document-management-service already does this). Never put a real
secret as a `${VAR:default}` in any `application-*.yml`, dev included: the yml is committed (and this
repo lives in OneDrive, so it syncs even before a commit).

## 4. Framework integration checklist

Walk this list for every new service — each row is a place a new service silently breaks if
skipped, because `alramz-api-starter`'s auto-configurations default to "off" or "deny" (CLAUDE.md §3):

- **JWT / public endpoints**: `company.jwt.enabled: true` is already in every profile template.
  Every endpoint that must be reachable without a bearer token (health/info, webhooks, etc.) needs
  BOTH `@PermitAll` on the controller method AND its path added to `company.jwt.permit-all-urls` in
  *every* profile file — `/api/v1/info` is already wired as the example. Never write ad hoc Spring
  Security config to open an endpoint instead.
- **Self-registration** (`company.jwt.registration-enabled`): the starter defaults it to `false`
  (since the 02-11-2026 security scan — open registration let callers pick their own roles, e.g.
  ADMIN). While false, `/api/auth/register` is not permit-all, so callers get **403** (not 404). The
  template sets `true` in dev only, `false` in preprod/prod; leave docker/test unset.
- **Roles in `apiCollection.http`**: the template registers `APP___SERVICE_NAME_UPPER___USER`.
  `@JwtSecured` matches roles exactly, so when the first secured endpoint picks its role (precedent:
  `APP_UTILITY`, `APP_NOTIFICATION`, `APP_RISK_MANAGEMENT`), change the register request to that role.
- **JWT access-token expiry**: dev sets `company.jwt.access-token-expiration-ms` to 24h (`86400000`);
  preprod/prod set 15 min (`900000`); docker/test omit it and inherit the starter's 15-min default.
  All overridable via `JWT_ACCESS_TOKEN_EXPIRATION_MS`. Refresh tokens stay at the 7-day default.
- **Startup banner**: `Application.java.template`'s `ApplicationRunner` bean logs application name,
  active profile (environment), host, and port on every startup — a repo-wide convention (see
  `reference-data-service/src/main/java/com/alramz/ReferenceDataServiceApplication.java`). Keep this
  bean when writing the new service's `Application` class; don't drop it as unused boilerplate.
- **Datasources**: only enable the ones this service actually uses (§1). Every
  `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource`/`TransactionManager` injection needs an
  explicit `@Qualifier("middlewareJdbcTemplate")` (or `brok…`/`integration…`) — the one documented
  exception is plain Spring Data JPA repositories, which don't need a qualifier because
  `DatasourceConfiguration` marks `middleware` `@Primary`.
- **Non-dev secrets**: handled by the per-profile values in §2 — preprod/prod get `${CIPHER_MASTER_KEY}`,
  `${JWT_SECRET}`, encrypted `password`/`passwordVector` and actuator `health,info,prometheus`, all
  with no defaults. Any secret a feature adds later (vendor keys…) follows the same rule:
  `${VAR}` with no default in every non-test profile, local value via `launch.json` env (§3).
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
  see `utility-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java` for
  the shape (typed exceptions, not generic `RuntimeException`).
- **Observability (tracing/logs/metrics)**: the `management.tracing` / `opentelemetry` /
  `observations` / `metrics` / `otlp` block lives in *every* profile file (dev, docker, test,
  preprod, prod) and **never** in `application.yml`. Only dev carries OTLP endpoint defaults
  (`172.25.21.114`); every other profile uses `${TRACING_OTLP_ENDPOINT:}` / `${LOGS_OTLP_ENDPOINT:}`
  so the endpoints come only from env variables. Caveat: Spring Boot gates the OTLP exporters on the
  endpoint property merely *existing*, so a non-dev profile started without those env vars may fail
  to create the exporter — always set them in docker/k8s.
- **Lombok**: use `@Getter`/`@Setter`/`@Slf4j`/builders everywhere; the `pom.xml` and
  `maven-compiler-plugin` annotation-processor wiring for it is already in the template.

## 5. DB-less services (variant)

If the service has no database of its own (modeled on `alramz-notification-service`):
- In `pom.xml.template`: drop `spring-boot-starter-data-jpa`, `spring-boot-starter-liquibase`,
  `spring-boot-starter-data-jpa-test`, and the whole `liquibase-maven-plugin` `<plugin>` block.
- In every `application-*.yml`: drop the `spring.liquibase.change-log` line and the whole
  `company.datasource:` block.
- Skip the entire `db/changelog/` tree (§2) and the Liquibase row in §4.
- Skip `DatasourceConfiguration.java` (§2) — there is no `middleware` datasource to point it at —
  and with it the `middlewareIsWired…` test in `__APP_CLASS__Tests` and the `company.datasource`
  block in `src/test/resources/application.yml`.
- `spring.jpa.hibernate.ddl-auto: validate` in `application.yml.template` can also be dropped since
  there's no JPA.
- Add whatever this service actually integrates with (Azure Service Bus/Blob/Key Vault, MS Graph,
  a third-party REST client, etc.) as its own dependencies — see
  `alramz-notification-service/pom.xml` for the Azure/Graph dependency block as a reference, not a
  template to copy blindly (only add what this new service really calls).

## 6. Verify before calling it done

Not done until **both** gates pass: (A) build + tests, (B) the packaged jar starts and answers. A
green build alone has shipped services that 403/500 on their first real request.

**Build from a snapshot, not the working tree.** If the service is open in VS Code (or already
running), the Java extension compiles into the same `target/` while Maven runs: jars come out with
stale or missing classes (`ClassNotFoundException`, "Unresolved compilation problems"), and
spring-boot-devtools restarts the running instance mid-check, freeing and re-taking its port. Both
produced false passes *and* false failures when this was first tried. So:

```bash
SNAP=<scratchpad>/snap-__SERVICE_NAME__
rsync -a --delete --exclude target/ --exclude logs/ --exclude .git/ --exclude '*.zip' ./pom.xml ./services ./config "$SNAP"/
cd "$SNAP"   # config/ is required: the BOM's Checkstyle reads config/checkstyle.xml
```

**(A) Build + tests** (in `$SNAP`):
```bash
mvn -pl services/__SERVICE_NAME__ -am clean test       # must exit 0; includes the transactionManager wiring test
mvn -pl services/__SERVICE_NAME__ -am package -DskipTests   # executable jar for (B)
```
In the working tree, `mvn … test` without `clean` can also fail with "Unresolved compilation problems"
from IDE-compiled classes — that's the same race, not a code error; `clean` (or the snapshot) fixes it.

**(B) Startup + smoke**, reusing the shared startup script (don't write another one). Export every
secret the dev profile requires with no default (e.g. a vendor API key) from the `launch.json` env
first — without printing it — or the service correctly refuses to start:
```bash
REPO_ROOT="$SNAP" SERVICES=__SERVICE_NAME__ STARTUP_FREE_PORT=1 STARTUP_SMOKE=1 REPORTS_DIR=<scratchpad>/startup \
  <repo>/.claude/skills/alramz-sonar-remediation/scripts/startup-check.sh
```
- `STARTUP_FREE_PORT=1` runs on a free port ≥ 18080 so a dev instance on the configured port can't
  collide or answer the probes; probes go to `127.0.0.1` (a `0.0.0.0` IPv4 bind can coexist with
  another process on IPv6 `*:PORT` on macOS, and `localhost` → `::1` would then hit the wrong app).
- Profile is `dev`, so it needs the dev middleware DB reachable (VPN). `BLOCKED_BY_DEPENDENCY` =
  environment, not code: report it, don't "fix" it.

Pass criteria in `startup-summary.md`:
| Column | Must show |
|---|---|
| Result | `STARTED_AND_HEALTHY`, or `STARTED_BUT_HEALTHCHECK_FAILED` **only** with detail "…is not in the JWT permit-all list (secured)" (no service makes `/actuator/health` public today) |
| Detail | `smoke: /api/v1/info 200, /api/auth/login(unknown user) 4xx` |

`STARTED_BUT_SMOKE_FAILED`, `FAILED_TO_START` or a 5xx anywhere = not done; the detail column carries
the root cause. What each gate catches (verified 04-11-2026 by deleting `DatasourceConfiguration`):
the wiring test fails at (A); the smoke still passes, because the unknown-user login only runs a
derived finder, which skips the `transactionManager` lookup — so never drop the test in favour of
the smoke, or vice versa (the smoke is what proves the JWT chain, the dev DB and the `jwt_*` tables).

Optionally, against a dev instance you're running anyway: `POST /api/auth/register` (from
`apiCollection.http`) must return 200 — it exercises the transactional write path end to end, but
writes a user into the **shared** dev DB, so don't automate it.

A clean `mvn verify` does **not** mean clean lint/coverage (Checkstyle is non-blocking, PMD/SpotBugs
default `<skip>true</skip>` in the BOM even though this template turns them back on per-module,
JaCoCo minimums are 0.00) — read the console output if you also run
`mvn -pl services/__SERVICE_NAME__ -am verify -Dcheckstyle.consoleOutput=true -Dpmd.consoleOutput=true -Dspotbugs.consoleOutput=true -Djacoco.skip=false`.

Check the observability block landed per §4 (expect: no hits in `application.yml`; one tracing
block in each of the 5 profile files; OTLP endpoint defaults only in dev; no `__…__` placeholder left):
```bash
R=services/__SERVICE_NAME__/src/main/resources
grep -c "TRACING_SAMPLING_PROBABILITY" $R/application.yml $R/application-{dev,docker,test,preprod,prod}.yml
grep -l "OTLP_ENDPOINT:http" $R/application*.yml   # → only application-dev.yml
grep -l "EXPIRATION_MS:86400000" $R/application*.yml # → only application-dev.yml
grep -rn "__[A-Z_]*__" $R                           # → nothing
grep -n "include:\|password:\|secret:\|registration-enabled" $R/application-{preprod,prod}.yml
#   → health,info,prometheus / ${CIPHER_MASTER_KEY} / ${JWT_SECRET} / password+passwordVector with no
#     defaults / registration-enabled: false — no literal secret, no "*"
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
