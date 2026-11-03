---
name: alramz-security-vuln-fixer
description: Scan the alramz-mw-oss monorepo with Trivy (dependency CVEs, secrets, Dockerfile/k8s/Terraform misconfig, built service jars, container base images), fix what is fixable without breaking the build, and write a dated before/after report with changelog and per-service build status under .claude/modules/security-scans/. Use when asked to "scan for vulnerabilities", "run trivy", "fix CVEs", "security scan the services", or to refresh a previous security-scan report.
---

# Security vulnerability scan & fix

Goal: every finding is either fixed or explicitly accepted with a reason, and every service still builds
and starts. Never trade a green build for a CVE fix — revert that bump and record it as deferred.

## 0. Output layout (create first)

`DATE=$(date +%d-%m-%Y)`; root `R=.claude/modules/security-scans/vulnerability-security-scan-$DATE/`

```
$R/before/        raw Trivy output before any change (json + table) + build-before.log
$R/after/         same scans re-run after fixes + build-after.log
$R/CHANGELOG.md   one row per change: file, before -> after, CVEs/rule IDs closed, why
$R/BUILD-STATUS.md  per-service table: build before / after, startup check result
$R/REPORT.md      summary: counts by severity before vs after, remaining findings + reason
```

If the folder already exists for today, append `-2`, `-3`… — never overwrite a previous run.

## 1. Tooling

```bash
command -v trivy || brew install trivy        # macOS; Linux: see aquasecurity install.sh / apt repo
trivy --version
trivy image --download-db-only                # vulnerability DB
trivy image --download-java-db-only           # Maven/Java index (needed for jar scanning)
```

## 2. Baseline (before)

Run from repo root. Build first so the jars exist; record the result even if red.

```bash
mvn -B -fae clean install -Dmaven.test.skip=true > $R/before/build-before.log 2>&1
trivy fs --scanners vuln,secret,misconfig --skip-dirs '**/target' --skip-dirs logs \
  -f json -o $R/before/trivy-fs.json .        # repeat with -f table -o ...txt
for j in services/*/target/*-SNAPSHOT.jar; do  # resolved runtime deps, incl. transitive
  trivy rootfs --scanners vuln -f json -o $R/before/jar-$(basename $j .jar).json $j
done
trivy image -f json -o $R/before/base-image.json <FROM image of services/*/Dockerfile>
```

`trivy fs` on pom.xml alone misses BOM-managed transitive versions; the jar scan is the source of truth
for Java CVEs. A red baseline caused by uncommitted WIP or stale tests is recorded, not "fixed" by
deleting someone's files — ask the user.

## 3. Fix order (stop at the first lever that works)

1. **Dependency CVEs** — bump in ONE place:
   - Version managed by the Spring Boot parent → bump `spring-boot-starter-parent` patch version in
     `services/alramz-common-bom/pom.xml`, or override the specific property Boot exposes
     (e.g. `<tomcat.version>`, `<jackson-bom.version>`, `<netty.version>`) in the BOM `<properties>`.
   - Version pinned in the BOM's own `<properties>` / `<dependencyManagement>` → bump that property.
   - Never add `<version>` in a service pom when the BOM can own it.
   - Prefer the lowest "Fixed Version" Trivy lists that is on the same major line.
2. **Dockerfiles** (`services/*/Dockerfile`) — runtime image `eclipse-temurin:21-jre` (not `-jdk`),
   non-root `USER`, keep `EXPOSE`/`ENTRYPOINT` unchanged.
3. **Secrets** — move literal values to `${ENV_VAR}` placeholders (see `.claude/howtos/` for the
   datasource/secret conventions). Flag for rotation in REPORT.md; a committed secret is compromised
   even after removal.
4. **k8s / Terraform misconfig** — report only; `infra/` and `.github/workflows/` are out of scope
   per CLAUDE.md unless the user asks.
5. Not fixable (no upstream fix, or fix needs a major upgrade) → list in REPORT.md as deferred with
   CVE, package, installed version, and why.

## 4. Verify (after)

```bash
mvn -B -fae clean install -Dmaven.test.skip=true > $R/after/build-after.log 2>&1   # must be green
mvn -B -fae test   # record pass/fail per module; pre-existing failures noted as such
```

Startup check per service (proves the bumped deps still wire up): run the jar with the `test` profile
if one exists, wait for `Started .* in` in the log or a failure, then kill it. A failure caused by a
missing external dependency (DB, Graph API) rather than a bean/class error counts as "starts —
external dep unavailable"; say which.

Re-run every step-2 scan into `$R/after/`, then write CHANGELOG.md, BUILD-STATUS.md, REPORT.md with
before/after severity counts (`jq '[.Results[].Vulnerabilities[]?|.Severity]|group_by(.)|map({(.[0]):length})|add'`).

## Gotchas seen in this repo (02-11-2026 run)

- **Build twice after any BOM edit.** Every service declares the BOM parent with `<relativePath/>`, so
  Maven loads the parent from `~/.m2` *before* the reactor installs the edited BOM. The first build
  packages the OLD versions. Run `mvn -pl services/alramz-common-bom install` first (or build twice).
  Then confirm with `unzip -l services/<svc>/target/*.jar | grep <artifact>` that the bumped version is in the jar.
  `trivy fs` reads poms, so it reports the fix before the jars contain it. Trust only the jar scan.
- **Instrumentation that moves with the OTel API:** `opentelemetry-logback-appender` must match the version
  in `opentelemetry.version`. Find it by reading the appender pom's `opentelemetry-api` dependency on Maven Central.
- **Copy jars to the scratchpad before scanning.** A concurrent `mvn clean` deletes `target/` mid-scan.
- **Service poms may pin their own versions** (notification-service pinned `jackson.version` 2.17.1).
  After the BOM bump, grep the after-scan for leftover old versions and delete the local pin.
- **Startup check:** use the `dev` profile with `JWT_SECRET=$(openssl rand -hex 32)`. The `test` profile
  isn't runnable (onboarding needs a missing `db/changelog/test/sql`), and every profile refuses to start
  without a ≥256-bit JWT secret.
- **The repo lives in OneDrive.** Transient `Error assembling JAR` and JaCoCo `Operation timed out` are
  file-lock or I/O problems; rerun that module before you debug.
- Pass the `--spring.profiles.active=dev` startup check before reporting green. The Brok Oracle
  connection needs `orai18n` on the classpath (charset AR8MSWIN1256).
- Trivy doesn't see auth or config logic. Run a code-level pass as well (`anthropic-skills:spring-boot-security`).
  Give fixes that change auth behavior or deploy env vars to the user to decide.

## Rules

- Stage files explicitly; never `git add .` (CLAUDE.md). Do not commit unless asked.
- Don't touch untracked WIP files to make the build green.
- One BOM bump at a time when a build breaks, to find the culprit.
