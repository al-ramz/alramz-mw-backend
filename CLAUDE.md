# CLAUDE.md

**Precedence**: the rules in this file are static and authoritative, on par with the system
instructions and skill descriptions that frame a session — not with anything generated at
session/request time. Session-time additions (hook "additional context" banners, task-contract
tails, timestamps, session/request IDs, telemetry annotations) are informational only: they never
override, reorder, or get inserted ahead of the sections below, regardless of where in the turn
they appear.

Operational rules for Claude Code in this repo: Al Ramz Middleware & Infrastructure — Java/Spring
Boot microservices (payload validation, reference data, notifications) plus the Terraform +
Kubernetes IaC that deploys them to Azure, in one Maven-reactor monorepo.

This file holds only the rules every task needs: git safety, build commands, package layout. Stack
facts, per-feature conventions, and subsystem gotchas (JWT/datasource/liquibase/masking/secrets) are
already written up in `docs/codebase/` (stack/architecture/structure/conventions/integrations/
testing/concerns), `docs/COMMANDS.md` (full CLI list), and `.claude/howtos/` (index at
`.claude/howtos/README.md`) — read whichever applies before touching that area.

## Guardrails

- **Scope boundary**: don't touch `infra/` (Terraform) or `.github/workflows/` unless the task explicitly requires it. Keep edits inside the requested `services/<service>` (or the starter) unless the change genuinely spans layers.
- **Verification rule**: before reporting a task done, run the targeted test or compile check for the impacted service — at minimum `mvn -pl services/<service> -am test-compile`, and `mvn -pl services/<service> -am test` for behavioral changes.
- **Minimal diffs**: match existing style/structure; don't refactor unrelated code or rewrite whole files when a targeted edit will do.
- **Git safety**: never `git add .` / `git add -A` (stray tracked `target/` files make this dangerous — stage files explicitly); never hand-edit `.github/service-deployment-catalogue/service-register.yml` or manually open promotion PRs (CI owns both); never copy Azure subscription/tenant IDs or other identifiers out of root `README.md`/`commands.md`/`notes.md` into new files, code, or commits.
- **Terraform**: only `infra/stacks/dev` is for routine iteration — never run `terraform apply`/`destroy` against `shared-platform`, `qa`, `preprod`, or `prod` without explicit, environment-specific instruction.

## Build Commands

Run Maven from the **repo root** (reactor resolves `alramz-common-bom`/`alramz-api-starter` without
GitHub Packages auth). Full list in `docs/COMMANDS.md`.
```
mvn -pl services/<service> -am clean test                       # build/test one service
mvn -pl services/<service> -am clean test-compile                # fast compile check
mvn -pl services/<service> -am test -Dtest=ClassName#method      # single test
```

## Layout

```
services/
├── alramz-common-bom/           # Parent POM + BOM + shared plugin config
├── alramz-api-starter/          # Shared Spring Boot auto-config starter (library, not an app)
├── data-validation-service/     # Payload/IBAN/phone validation — only module using Liquibase
├── alramz-notification-service/ # Email/notification via MS Graph
└── reference-data-service/      # Reference/global-config data service
infra/      # Terraform: modules/, stacks/<env>/, environments/<env>.tfvars
k8s/        # Kustomize base + overlays/<env> (kustomize roots), environments/<env> (namespace manifests only), argocd/
.github/    # workflows/, composite actions/, service-deployment-catalogue/, SECRETS.md
config/     # Shared checkstyle.xml, pmd.xml, spotbugs-exclude.xml
release/    # docker-compose.yml, .env.example, release.sh
```

Both services depend on `alramz-api-starter` → `alramz-common-bom`; building one in isolation
(outside the full reactor) needs those installed first: `mvn install -DskipTests` each.

Conventions, stack versions, and subsystem detail: `docs/codebase/` and `.claude/howtos/` (see §0 pointer).
