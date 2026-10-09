# Codebase Concerns

## Core Sections (Required)

### 1) Top Risks (Prioritized)

| Severity | Concern | Evidence | Impact | Suggested action |
|----------|---------|----------|--------|-------------------|
| High | Raw Azure AD access token logged at `INFO` level | `services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java:49` — `LOG.info("Access Token: {}", accessToken.getToken());` | Any log sink (including Seq, which this repo ships) captures a live bearer token usable to call Microsoft Graph as the configured app registration until it expires | Remove the log line, or reduce to a boolean/expiry-only log; audit Seq/log retention for prior exposure |
| High | Hardcoded, tracked-in-git plaintext secrets in a dev profile | `services/data-validation-service/src/main/resources/application-dev.yml` — E-Trade `client-secret`, `brok` Oracle datasource password + internal IP `172.25.1.218`, `cipher.password: UseMostSecretKey` | Anyone with repo read access has a real (if dev-scoped) credential and an internal network address; if the `brok` host/creds are shared with non-dev environments, this is a direct credential leak | Move these to `${VAR:}` placeholders backed by `release/.env`/CI secrets, matching how `IBAN_API_KEY`/`VERIPHONE_API_KEY` are already handled in the same file |
| Medium | Documented ports/paths don't match the committed code | CLAUDE.md and README.md both say `data-validation-service` runs on port `5001`; every `application-*.yml` sets `server.port: 8080`. CLAUDE.md also documents `services/<service>/service.yaml` for K8s sizing; no such file exists — sizing is in `k8s/apps/base/<service>/deployment.yaml` | New contributors (and this very documentation-generation pass) will follow stale instructions and fail to reach the running service | Update CLAUDE.md/README.md to match `application-dev.yml`, or change the YAML back to 5001 if that was the intended contract |
| Medium | JaCoCo coverage thresholds are declared but not enforced | `services/alramz-common-bom/pom.xml` defines `jacoco.line.coverage=0.80` etc. as properties, but the actual `<limit>` blocks in the `check` execution hardcode `<minimum>0.00</minimum>` | A green `mvn verify` gives false confidence that an 80%/70%/75% bar is being held; it is not (matches CLAUDE.md §2's caveat, confirmed here at the plugin-config level) | Wire the `<limit>` values to the declared properties, or delete the unused properties to stop implying an enforced bar |
| Medium | Two of the four Maven modules have effectively no test coverage | `alramz-notification-service` has 1 test file (`AppTest.shouldAnswerWithTrue` — a placeholder); `reference-data-service` has 1 test file (an empty `contextLoads()`). Both declare Mockito/AssertJ/Spring Boot Test dependencies that go unused | Email sending (MS Graph), Redis caching, distributed scheduler locking, and cache reload logic can regress silently | Add unit tests for `EmailServiceImpl`, `RedisCacheService`, `SchedulerLockService`, `CacheReloadScheduledJob` before further feature work on these services |
| Low–Medium | Liquibase changesets are duplicated, not shared, across environment profiles | `services/data-validation-service/src/main/resources/db/changelog/{dev,docker,preprod,prod}/sql/001-schedule-job.sql` … `006-*.sql` are byte-identical across all 4 folders (diff-verified) instead of living once in a shared folder as CLAUDE.md describes | A schema change applied to one profile's copy and forgotten in another silently diverges dev/preprod/prod schemas | Introduce the shared `db/changelog/sql/` folder CLAUDE.md already documents, or update CLAUDE.md to describe the duplicated-folder reality |
| Low | `reference-data-service` has no K8s base manifests | `k8s/apps/base/` only contains `alramz-notification-service/` and `data-validation-service/`; the 5th Maven module has none | The newest service (most recent commits: "Scheduled Jobs for Cache Upload", "Reference Data Service") cannot be deployed through the same Kustomize/ArgoCD path as its siblings without first authoring these manifests | Scaffold `k8s/apps/base/reference-data-service/` from the `data-validation-service` pattern before this service ships to a real environment |
| Low | Azure Managed Redis auth may not survive long-lived Lettuce connections | `services/reference-data-service/src/main/java/com/alramz/config/RedisConfig.java` — `getPassword()` is overridden to fetch a fresh AAD token, but Lettuce may not re-invoke it once a connection is established | Possible silent Redis auth failures in Azure after the AAD token's TTL (~1hr) expires, if Lettuce doesn't reconnect/re-auth on its own | `[ASK USER]` — confirm whether Lettuce's connection pool is configured to refresh/reconnect on a timer, or add explicit periodic reconnect logic |

### 2) Technical Debt

| Debt item | Why it exists | Where | Risk if ignored | Suggested fix |
|-----------|----------------|-------|-------------------|----------------|
| Correlation-ID extraction logic duplicated | Copy-pasted between the controller and the exception handler instead of factored into a shared utility | `DataValidationController.correlationId()` and `GlobalExceptionHandler.extractCorrelationId()` (identical bodies) | Any future change to correlation-ID resolution (e.g. adding a new header name) must be made in 2+ places, or already is inconsistent with the starter's own `CorrelationIdFilter`/`MDCUtil` | Extract to a shared helper in the starter (`com.alramz.logging.util`) and reuse it |
| `GlobalExceptionHandler` branches response shape by URL-prefix string matching | Two different API contracts (validation vs. onboarding) share one `@RestControllerAdvice`, disambiguated via `request.getRequestURI().startsWith(...)` | `services/data-validation-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java` | Adding a third contract shape means another `if` branch repeated in every handler method (9 handler methods already repeat this pattern) | Split into per-controller `@RestControllerAdvice(basePackages=...)` or `@ExceptionHandler` scoped to each `*Api` interface package |
| `.kilo/worktrees/` contains 4 full nested git worktrees | A second agent-tooling workspace (Kilo) appears to have created worktrees inside the main working tree rather than alongside it | `.kilo/worktrees/{carbonated-kookaburra,meowing-tarascosaurus,pie-maraca,superb-sagittarius}/` (each has its own `services/`, `infra/`, `pom.xml`, `CLAUDE.md`) | Roughly quadruples repo size on disk for every clone; risks accidental edits or scans landing in a stale worktree instead of the real one; this codebase-knowledge scan itself had to exclude these via the scan tool's depth limit | `[ASK USER]` — confirm whether these worktrees are still needed; if not, remove them (they are git worktrees, so `git worktree remove` rather than a raw `rm -rf`) |
| `README.md` doubles as a scratch pad | Real architecture documentation is interleaved with raw personal notes, TODO lists, and (per CLAUDE.md's explicit warning) real historical Azure identifiers | `README.md` (bottom third: ad hoc task lists, ADR fragments, a stray Azure subscription/tenant ID block) | New contributors reading top-to-bottom hit noise and, per CLAUDE.md §4/§5, must never propagate those identifiers further | Move the working notes to `.claude/plans/` or an issue tracker; keep `README.md` to the architecture/setup content only |

### 3) Security Concerns

| Risk | OWASP category | Evidence | Current mitigation | Gap |
|------|------------------|----------|----------------------|-----|
| Sensitive token logged in plaintext | A09:2021 Security Logging and Monitoring Failures / A02 Cryptographic Failures (credential exposure) | `GraphConfig.java:49` | `SensitiveDataMasker` exists and masks `access_token`/`accesstoken` keys in *audited* request/response bodies | This particular log call bypasses the masker entirely — it's a raw `LOG.info`, not routed through audit logging |
| Hardcoded plaintext credentials in a tracked config file | A02:2021 Cryptographic Failures | `application-dev.yml` (E-Trade secret, `brok` datasource password, cipher password) | `PWProtector`/`cipher.password` + encrypted `password`/`passwordVector` exists for non-dev profiles (CLAUDE.md §3) | Dev profile doesn't use env-var placeholders the way IBAN/VeriPhone keys in the same file do |
| H2 database dependency scoped `runtime` (not `test`) in `alramz-notification-service` | A05:2021 Security Misconfiguration | `services/alramz-notification-service/pom.xml` (`h2` with `<scope>runtime</scope>`) | `[TODO]` — unclear if this service actually uses H2 at runtime in any profile, or if the scope is simply mis-set | `[ASK USER]` — confirm whether `alramz-notification-service` is meant to run against H2 in production, or whether this should be `scope=test` like its siblings |
| JWT default secret has an obvious placeholder fallback | A02:2021 Cryptographic Failures | `JwtProperties.secret = "defaultSecretKeyChangeMeInProduction1234567890"`; `application-dev.yml` `JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890` | The name itself warns against production use, and `release/.env.example` has an empty `JWT_SECRET=` for operators to fill in | No startup-time guard was found that refuses to boot with the default secret in a `prod`/`preprod` profile — `[TODO]` to verify |

### 4) Performance and Scaling Concerns

| Concern | Evidence | Current symptom | Scaling risk | Suggested improvement |
|---------|----------|-------------------|----------------|-------------------------|
| Azure Managed Redis token refresh under long-lived connections | `RedisConfig.createAzureRedisConnectionFactory()` | None observed yet (no load test in repo) | Cache layer could silently stop authenticating after the AAD token's TTL if Lettuce doesn't reconnect | See ARCHITECTURE.md §5; verify Lettuce reconnect behavior under Azure AD auth |
| Scheduler lock granularity is per-`mappingName`, not per-cluster-wide job | `SchedulerLockService.tryAcquireLock("cacheReloadJob-" + mappingName, ...)` in `CacheReloadScheduledJob` | Works as designed for the current single-job case | If the scheduler grows more job types, each needs its own lock-key discipline — easy to forget for a new `Schedulable` implementation | Document the lock-key convention in the scheduler module's own code comments/CLAUDE.md |

### 5) Fragile/High-Churn Areas

| Area | Why fragile | Churn signal | Safe change strategy |
|------|-------------|---------------|------------------------|
| `services/data-validation-service/src/main/java/com/alramz/DataValidationServiceApplication.java` | Highest-churn file in the last 90 days (61 changes) yet is just the `@SpringBootApplication` entry point — high churn on a normally-static file suggests repeated build/bootstrap troubleshooting | scan output "HIGH-CHURN FILES" §, rank 1 | Before editing, check recent `git log -p` on this file specifically for what kept changing (likely component-scan/exclude tuning) |
| `.github/workflows/cicd.yml` | 2nd-highest churn (49 changes); a single monolithic pipeline gating dev→qa→preprod→prod promotion | scan output "HIGH-CHURN FILES" §, rank 2; commit history shows a string of "Fix GitHub workflow ... syntax" commits | Treat as high-blast-radius; per CLAUDE.md §0, avoid touching `.github/workflows/` unless the task explicitly requires it |
| `services/data-validation-service/src/main/java/com/alramz/controllers/DataValidationController.java` | Implements 3 generated `*Api` interfaces and hand-writes the exception→response mapping inline (duplicating `GlobalExceptionHandler` logic) — rank 16 in churn, but structurally coupled to the OpenAPI spec | scan output; direct reading of the file | Regenerate `*Api` interfaces after any `specs/apiSpecs.yaml` change before touching this controller by hand |
| `services/data-validation-service/apiCollection.http` | 3rd-highest churn (18 changes) — a manual REST-client collection kept in sync by hand alongside the generated OpenAPI spec | scan output "HIGH-CHURN FILES" §, rank 3 | Prefer updating `specs/apiSpecs.yaml` first, then this file, to avoid the two drifting apart |

### 6) `[ASK USER]` Questions

1. `[ASK USER]` Should `data-validation-service`'s documented port (5001 in CLAUDE.md/README.md) or its actual configured port (8080 in every `application-*.yml`) be treated as correct? Which one should the docs/config converge on?
2. `[ASK USER]` Is `services/<service>/service.yaml` (referenced in CLAUDE.md §3 for K8s sizing) meant to exist and simply hasn't been created yet, or has sizing permanently moved to `k8s/apps/base/<service>/deployment.yaml` and CLAUDE.md should be updated instead?
3. `[ASK USER]` Are the `.kilo/worktrees/*` directories still active/needed, or safe to remove via `git worktree remove`?
4. `[ASK USER]` Is the raw access-token log line in `GraphConfig.java:49` intentional debug-only code that should have been removed before commit, or is there a reason it's needed? (Recommend removing regardless, but flagging for confirmation since it touches a shared auth-config class.)
5. `[ASK USER]` Should `reference-data-service` get its own row in CLAUDE.md's "Runnable services" table and its own `k8s/apps/base/reference-data-service/` manifests as part of upcoming work, or is it still considered pre-production/experimental?
6. `[ASK USER]` Is the `brok` Oracle datasource host/credentials in `application-dev.yml` a genuinely isolated dev-only instance, or does it point at a shared environment that should not have its address/credentials committed to source control?

### 7) Evidence

- `docs/codebase/.codebase-scan.txt` — TODO/FIXME section, HIGH-CHURN FILES section, CODE METRICS section
- `services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java`
- `services/data-validation-service/src/main/resources/application-dev.yml`
- `services/alramz-common-bom/pom.xml`
- `k8s/apps/base/` (directory listing)
- `.kilo/worktrees/` (directory listing)

## Extended Sections (Optional)

Not populated — the prioritized risk table above already surfaces the component-level items a roadmap would otherwise restate.
