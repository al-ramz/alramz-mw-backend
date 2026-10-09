# CLI Commands

Full command reference for this repo. Run Maven from the **repo root** so the reactor resolves
`alramz-common-bom`/`alramz-api-starter` without needing GitHub Packages auth. See `CLAUDE.md` for
the handful of these used on every task (build/test one service, single test, compile check).

| Task | Command |
|---|---|
| Build everything | `mvn clean package` |
| Build/test one service (+ its reactor deps) | `mvn -pl services/data-validation-service -am clean test` |
| Fast compile check | `mvn -pl services/<service> -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` |
| Run a single test class | `mvn -pl services/<service> -am test -Dtest=DataValidationControllerTest` |
| Run a single test method | `mvn -pl services/<service> -am test -Dtest=DataValidationControllerTest#shouldValidateIban` |
| Install BOM/starter locally (outside full reactor) | `mvn -pl services/alramz-common-bom install -DskipTests && mvn -pl services/alramz-api-starter -am install -DskipTests` |
| Static analysis (console output) | `mvn -pl services/<service> -am verify -Dcheckstyle.consoleOutput=true -Dpmd.consoleOutput=true -Dspotbugs.consoleOutput=true -Djacoco.skip=false` |
| Run `data-validation-service` locally | `cd services/data-validation-service && ./mvnw spring-boot:run` (has wrapper) |
| Run `alramz-notification-service` locally | `cd services/alramz-notification-service && mvn spring-boot:run` (no wrapper — use system `mvn`) |
| Build a Docker image | `mvn clean package -DskipTests -pl services/<service> -am && docker build -t <service>:latest services/<service>` |
| Local Postgres/Redis stack | `cd release && cp .env.example .env` (fill in values) `&& docker compose --env-file .env up` |
| Local Seq log server | `docker run -d --name seq -e ACCEPT_EULA=Y -e SEQ_FIRSTRUN_ADMINPASSWORD=admin123 -p 5341:80 -p 5342:5341 datalust/seq:latest` |
| Dry-run a Kustomize overlay | `kubectl kustomize k8s/overlays/dev` (verified working; `k8s/environments/<env>/` holds only namespace manifests, not a kustomize root) |
| Terraform plan (per stack) | `cd infra/stacks/dev && terraform init && terraform plan -var-file="../../environments/dev.tfvars"` |

> **Caveat:** Checkstyle runs but doesn't fail the build (`failOnViolation=false`); PMD/SpotBugs are `<skip>true</skip>` by default; JaCoCo's `check` minimums are all `0.00`. A green `mvn verify` does **not** mean clean lint/coverage — read the console output.
