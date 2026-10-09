# Al Ramz Middleware & Infrastructure

Java/Spring Boot microservices (payload validation, reference data, notifications) plus the
Terraform + Kubernetes IaC that deploys them to Azure, in one Maven-reactor monorepo.

## Services

| Module | What it does |
|---|---|
| [`alramz-common-bom`](services/alramz-common-bom) | Parent POM + dependency BOM + shared plugin config — every other module inherits from it |
| [`alramz-api-starter`](services/alramz-api-starter) | Shared Spring Boot auto-config starter (library, not a runnable app): JWT auth, multi-datasource, scheduler, request/DB audit logging, feature flags |
| [`data-validation-service`](services/data-validation-service) | Payload/IBAN/phone validation — only module using Liquibase |
| [`alramz-notification-service`](services/alramz-notification-service) | Email/notification delivery via MS Graph and Azure Service Bus |
| [`reference-data-service`](services/reference-data-service) | Reference/global-config data, backed by Redis cache |

## Repository Layout

```
services/   Maven-reactor modules listed above
infra/      Terraform: modules/, stacks/<env>/, environments/<env>.tfvars
k8s/        Kustomize base + overlays/<env>, environments/<env> (namespace manifests), argocd/
.github/    workflows/, composite actions/, service-deployment-catalogue/, SECRETS.md
config/     Shared checkstyle.xml, pmd.xml, spotbugs-exclude.xml
release/    docker-compose.yml, .env.example, release.sh
```

## Build & Run

Run Maven from the **repo root** — the reactor resolves `alramz-common-bom`/`alramz-api-starter`
without needing GitHub Packages auth.

```bash
mvn -pl services/<service> -am clean test          # build/test one service
mvn -pl services/<service> -am clean test-compile  # fast compile check
cd services/data-validation-service && ./mvnw spring-boot:run
```

Full CLI reference: [`docs/COMMANDS.md`](docs/COMMANDS.md). Local Postgres/Redis/Seq stack:
[`release/README.md`](release/README.md) and [`docs/LOCAL-SETUP.md`](docs/LOCAL-SETUP.md).

## Infrastructure & Deployment

- **Terraform** (`infra/`) provisions Azure resources per environment (dev/qa/preprod/prod/
  shared-platform). Only `infra/stacks/dev` is for routine iteration.
- **Kubernetes** (`k8s/`) — Kustomize base + environment overlays, delivered via ArgoCD. See
  [`k8s/README.md`](k8s/README.md).
- **CI/CD** — GitHub Actions in `.github/workflows/`, path-filtered per changed service, builds
  and pushes images to ACR.

## Documentation Map

- [`CLAUDE.md`](CLAUDE.md) / [`AGENTS.md`](AGENTS.md) — operational rules for AI coding agents working in this repo
- [`docs/codebase/`](docs/codebase) — stack, architecture, structure, conventions, integrations, testing, known concerns
- [`docs/COMMANDS.md`](docs/COMMANDS.md) — full CLI command reference
- [`.claude/howtos/`](.claude/howtos/README.md) — subsystem how-tos (JWT, datasource, Liquibase, masking, secrets)
