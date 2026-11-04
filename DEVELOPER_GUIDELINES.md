# Developer Guidelines

How to set up your machine, install Claude Code, and drive this repo's Claude skills to
build a new service or feature. Repo rules for humans and agents alike live in
[`CLAUDE.md`](CLAUDE.md) — read it once; Claude reads it automatically every session.

## 1. Local prerequisites

| Tool | Version / note | macOS (Homebrew) | Windows (Chocolatey) |
|---|---|---|---|
| JDK | 21 (`java.version` in `alramz-common-bom`) | `brew install openjdk@21` | `choco install temurin21` |
| Maven | 3.9+ (or each service's `./mvnw` / `mvnw.cmd`) | `brew install maven` | `choco install maven` |
| Git | any recent (Windows: also provides Git Bash, which Claude Code and the `.sh` skill scripts need) | `brew install git` | `choco install git` |
| Python 3 | used by skill scripts (`render_template.py`) | preinstalled / `brew install python` | `choco install python` |
| Node.js LTS | only for Newman (`e2e/`) | `brew install node` | `choco install nodejs-lts` |
| Trivy | only for security scans | `brew install trivy` | `choco install trivy` |
| VS Code | recommended IDE; `.vscode/launch.json` is local-only (gitignored) | `brew install --cask visual-studio-code` | `choco install vscode` |

### 1a. Windows setup with Chocolatey

> **You need local Administrator rights.** Chocolatey itself and every `choco install` below
> write to `C:\ProgramData\chocolatey` and `C:\Program Files`, and change the system `PATH`, so
> they must run in **PowerShell opened with "Run as administrator"**. If you don't have admin
> rights on your laptop, raise an IT request to get them (or to have IT install these packages)
> before you start.

1. Open the Start menu, type **PowerShell**, right-click it → **Run as administrator**.
2. Install Chocolatey (official command from <https://chocolatey.org/install>):
   ```powershell
   Set-ExecutionPolicy Bypass -Scope Process -Force
   [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
   iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
   ```
3. Close and reopen the **administrator** PowerShell so `choco` is on `PATH`, then check it:
   ```powershell
   choco --version
   ```
4. Install the tools (same admin window; `-y` accepts the prompts):
   ```powershell
   choco install -y temurin21 maven git python nodejs-lts trivy vscode
   ```
5. Allow long paths in Git (Maven `target/` paths in this repo get deep), still as administrator:
   ```powershell
   git config --system core.longpaths true
   ```
6. Close the admin window and open a **normal** (non-admin) PowerShell or Git Bash for daily
   work. Verify:
   ```powershell
   java -version      # 21.x
   mvn -version       # 3.9+, and "Java version: 21"
   git --version
   python --version   # 3.x
   trivy --version
   ```
   If `java -version` shows another version, set `JAVA_HOME` to the Temurin 21 folder under
   `C:\Program Files\Eclipse Adoptium\` (System Properties → Environment Variables) and reopen
   the terminal.

Windows notes:
- Use `mvnw.cmd` instead of `./mvnw` in `cmd`/PowerShell (`./mvnw` works in Git Bash).
- Skill scripts are invoked as `python3`; on Windows that can open the Microsoft Store stub. If
  it does, turn off the `python3.exe` alias in **Settings → Apps → Advanced app settings → App
  execution aliases**, or run the scripts with `python`.
- Shell scripts such as `startup-check.sh` need Git Bash (installed with `git`).
- To update everything later: `choco upgrade -y all` (admin PowerShell).

### 1b. First build (all platforms)

First build — run from the **repo root** so the reactor resolves `alramz-common-bom` and
`alramz-api-starter` without GitHub Packages auth:

```bash
mvn -DskipTests dependency:resolve            # warm the local Maven cache
mvn -pl services/<service> -am clean test     # build + test one service
```

Optional — warm Trivy and run a local scan (the `alramz-security-vuln-fixer` skill does this for you):

```bash
trivy image --download-db-only
trivy fs --scanners vuln,misconfig,secret --severity HIGH,CRITICAL \
  --skip-dirs '**/target' --skip-dirs logs .
```

## 2. Install and start Claude Code

1. Install (pick one):
   ```bash
   # macOS / Linux / WSL
   curl -fsSL https://claude.ai/install.sh | bash   # native installer (recommended)
   brew install --cask claude-code                   # Homebrew
   ```
   ```powershell
   # Windows (PowerShell) — install Git first (step 1a): Claude Code uses Git Bash on Windows
   irm https://claude.ai/install.ps1 | iex
   ```
   The Claude Code installer itself installs per-user and does not need admin rights; only the
   Chocolatey steps in 1a do.
2. Start it from the **repo root** (so it picks up `CLAUDE.md` and `.claude/`):
   ```bash
   cd alramz-mw-backend
   claude
   ```
   First run opens a browser to log in with your company Claude account.
3. Optional: install the **Claude Code** extension in VS Code to run it in the editor side panel.
4. Check it sees this repo's tooling: type `/` and you should see `alramz-sdlc`,
   `alramz-service-scaffold`, `alramz-code-quality`, etc.

### What's in `.claude/`

| Path | What it is |
|---|---|
| `.claude/skills/alramz-service-scaffold` | Creates a new service wired into the BOM/starter, then proves it builds, tests and starts |
| `.claude/commands/alramz-sdlc.md` + `.claude/agents/01..04_*.md` | Feature pipeline: spec → plan → implement → security review, with two approval gates |
| `.claude/skills/endpoint-intake-extraction` | Turns a legacy (e.g. webMethods) endpoint doc into a structured feature request |
| `.claude/skills/alramz-e2e-api-tests-generator` | Postman requests/tests from a service's OpenAPI spec |
| `.claude/commands/alramz-code-quality.md` | Trivy → Checkstyle/PMD/SpotBugs → JaCoCo 80% → SonarCloud, in that order |
| `.claude/howtos/` | Subsystem notes (JWT, datasources, Liquibase, exceptions, testing…) |
| `.claude/modules/features/<slug>/` | Per-feature artifacts: `spec.md`, `execution_plan.json`, reports |

## 3. Creating a new service

### Step 1 — Generate the skeleton on start.spring.io

At <https://start.spring.io>: **Maven**, **Java 21**, Spring Boot version matching the other
services, Group `com.alramz`, Artifact `<name>-service` (kebab-case). No extra dependencies
needed — the scaffold supplies them.

### Step 2 — Put it in `services/`

Unzip so you get `services/<name>-service/`. Don't commit the zip or the Initializr files;
the scaffold deletes the Initializr leftovers (`HELP.md`, `application.properties`, …) and
replaces every file.

### Step 3 — Run the scaffold skill

```
/alramz-service-scaffold Use the skill to create a new risk-management-service within the existing maven monorepo, following established architectural and coding conventions.
```

Say up front if the service has **no database**, needs extra datasources (`brok`, `integration`),
or calls external systems — otherwise it defaults to the middleware DB only. The skill:

- picks the next free dev port, adds the module to the root `pom.xml` and a VS Code launch entry;
- generates dev/docker/test/preprod/prod profiles — preprod/prod with **no secret defaults**;
- adds `DatasourceConfiguration` (the `transactionManager` bean JPA `save()` needs) plus a test
  that fails without it;
- is only done when **gate A** (`mvn clean test` + `package`) and **gate B** (the jar starts and
  answers `/api/v1/info`) both pass.

### Step 4 — Build features with the SDLC pipeline

```
/alramz-sdlc risk-compilance-endpoints Focus exclusively on the Risk Management Service. Thoroughly review the provided request context, existing implementation, business requirements, and related dependencies before proceeding. Do not make assumptions or invent business rules, logic, validations, or expected behaviour. If anything is unclear or missing, ask specific clarification questions and proceed.
```

Then paste the requirement source (legacy API doc, BA notes) in the same or next message. The
pipeline runs `01_elicit` → `02_plan` → **Gate 1 (you approve the plan)** → `03_implement` →
`04_security` → **Gate 2 (you approve the diff)**. It never commits; you stage and commit yourself.

## 4. How to prompt well

**Shape of a good prompt:** *command* + *slug or target* + *scope* + *source material* + *constraints*.

| Do | Why |
|---|---|
| Name the service: "Focus exclusively on `risk-management-service`" | Stops edits leaking into other services or the starter |
| Paste the real source (legacy doc, sample request/response, error codes) | Claude must not invent business rules; give it the facts |
| Say "ask if unclear, don't assume" | Turns guesses into questions you answer at the gate |
| Answer clarifying questions with the concrete choice | The answers are recorded in `spec.md` / `execution_plan.json` |
| Reuse the same slug to resume a feature | `/alramz-sdlc` picks up from the last finished stage |
| `/clear` between unrelated tasks | Keeps context focused and cheaper |
| Paste the full stack trace when something breaks | e.g. "register returns 500" + trace beats "it's broken" |

| Don't | Instead |
|---|---|
| Paste real API keys, passwords, tokens into chat or yml | Put them in `.vscode/launch.json` `env` (gitignored); yml uses `${ENV_VAR}` |
| Ask it to "fix everything" | One service / one concern per prompt |
| Let it touch `infra/` or `.github/workflows/` incidentally | Ask explicitly, in a separate prompt, if you really need it |
| Approve a gate without reading the digest | Gates are your only review before code is written / handed over |

### Prompt examples

```text
# Legacy endpoint migration — extract first, then run the pipeline
Use endpoint-intake-extraction on the attached webMethods doc for reference-data-service.
<paste doc>

# Bug fix
risk-management-service: POST /api/auth/register returns 500 in the dev profile. Find the root
cause and fix it. Stack trace:
<paste trace>

# Quality pass, scoped
/alramz-code-quality lint coverage services: risk-management-service

# API tests
Generate Postman tests for every endpoint in risk-management-service's specs/apiSpecs.yaml,
including a 401 negative case.
```

## 5. Before you commit

1. `mvn -pl services/<service> -am clean test` passes (Claude runs it too — check the output).
2. Review `git status` / `git diff` yourself.
3. Stage explicitly: `git add services/<service> pom.xml` — **never** `git add .` / `git add -A`
   (stray tracked `target/` files).
4. No secrets in any `application-*.yml`; preprod/prod use `${ENV_VAR}` with no default.

## 6. Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `NoClassDefFoundError` / `ClassNotFoundException` on startup, or "Unresolved compilation problems" | VS Code's Java compiler wrote broken classes into `target/`. Run `mvn -pl services/<service> -am clean compile`; if it recurs, Command Palette → **Java: Clean Java Language Server Workspace** |
| `/api/auth/register` → 403 in dev | `company.jwt.registration-enabled: true` missing from `application-dev.yml` (default is `false`) |
| `No bean named 'transactionManager'` | Service is missing `config/DatasourceConfiguration` |
| App answers on the wrong port / old code | Another instance already holds the port; stop it (`lsof -i :<port>`) |
