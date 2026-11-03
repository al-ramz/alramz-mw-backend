---
name: alramz-sonar-remediation
description: Run a SonarQube/SonarCloud analysis of the alramz-mw-oss monorepo, fix the real root causes of bugs, vulnerabilities, hotspots and code smells until every rating is A and the quality gate passes, then build all modules, startup-check every service and write an evidence-based before/after report. Use when asked for a "sonar scan", "sonarqube/sonarcloud report", "fix sonar issues", "get sonar A rating", "pass the quality gate", "code smells cleanup", or to refresh a previous Sonar remediation report.
---

# Sonar remediation (A ratings + passing gate)

Goal: Reliability, Security, Maintainability and Hotspot-review ratings all **A**, Quality Gate **OK**,
zero open Blocker/Critical issues, zero new issues, every module green, every service starts. Ratings and
issue counts are different metrics. Report both. Never claim a number SonarQube didn't return.

**Not allowed:** editing quality profiles or gates, `//NOSONAR`, `@SuppressWarnings("java:…")`,
`sonar.exclusions`/`sonar.issue.ignore.*`, marking issues *false positive* or *won't fix* without written
evidence in the remediation log, or deleting tests or code just to lose findings.

## 0. Tooling (bundled with this skill)

Scripts are in `.claude/skills/alramz-sonar-remediation/scripts/` (shown as `$S` below; run them from anywhere).
Output goes to `.claude/modules/sonar-scans/` (override with `REPORTS_DIR`).

```
$S/_lib.sh            config (every value env-overridable), token loading, private-project guard, build retry
$S/sonar_report.py    stdlib helper: export | compare | build-summary | final
$S/sonar-before.sh    clean verify + analysis -> before/   (immutable; FORCE=1 to redo)
$S/sonar-after.sh     same -> after/  (a previous after/ is kept as after-<ts>/)
$S/sonar-compare.sh   -> comparison/comparison.{md,json}: fixed / pending / new / changed
$S/build-all.sh       clean verify all modules -> build/build-summary.{md,json}
$S/startup-check.sh   java -jar each service, /actuator/health -> startup/
$S/generate-report.sh -> final/FINAL-REPORT.md
```

Defaults: SonarCloud org `alramzit`, project `alramzit_alramz-mw-backend` (**private**), token from
`$SONAR_TOKEN` or `~/.sonar/alramz-cloud-user-token`. Local server instead:
`SONAR_HOST_URL=http://localhost:9000 SONAR_ORGANIZATION= SONAR_PROJECT_KEY=alramz-mw-oss`
(`docker start sonarqube`). Never write a token into the repo. Before every upload the scripts check that
the SonarCloud project is still private. Don't bypass that check.

## 1. Baseline

`$S/sonar-before.sh`, then read `.claude/modules/sonar-scans/before/summary.md` (gate conditions, counts by
rule and file, hotspot list). `.claude/modules/sonar-scans/before/issues.json` holds every open issue with
its rule, line and message. That is the work queue. To split it per module for agents, write one TSV per module
to `.claude/modules/sonar-scans/remediation/queue/<module>.tsv`. A red build here is pre-existing. Record it, and don't "fix" someone's WIP.

## 2. Fix (priority order)

1. Vulnerabilities, then Blocker/Critical bugs.
2. Security hotspots. Fix the code, or review it with a written reason. Changing a dev-profile secret to
   `${ENV:}` changes developer setup, so ask the user first.
3. Null, resource, exception, transaction and concurrency bugs.
4. Code smells, going by rule with the highest count first. One rule across many files is a mechanical sweep.
5. Duplication and complexity. Extract a method or class only where the duplication is real.

For each fix: find the root cause, edit `src/main`, and add or adjust a test when behavior could change.
Then run `mvn -pl services/<svc> -am clean test` from the repo root. Log one row per file
(file | rule | problem | fix | test run): the lead logs in `.claude/modules/sonar-scans/remediation/lead.md`, and each
module agent logs in `.claude/modules/sonar-scans/remediation/<module>.md`. Keep diffs minimal,
leave public APIs and generated code (`target/generated-sources`, openapi `api`/`model`) alone, and stay
out of `infra/` and `.github/`.

Independent services can be fixed in parallel by one agent per service. Run
`mvn -pl services/alramz-common-bom,services/alramz-api-starter install -DskipTests` first. Agents build
with `-pl services/<svc>` and no `-am`, so they never write to the same `target/`.

## 3. Loop

`$S/sonar-after.sh` → `$S/sonar-compare.sh` → fix whatever is under **New** or **Pending** in
`.claude/modules/sonar-scans/comparison/comparison.md` → repeat. Stop when
the targets are met or every pending issue has a blocker reason written down (generated code, an API
contract change, a needed design decision). One cycle is not enough.

## 4. Verify and report

Run the final pass on an isolated snapshot (see Gotchas): `$S/build-all.sh` → `$S/startup-check.sh` →
`$S/sonar-after.sh` → `$S/sonar-compare.sh` → `$S/generate-report.sh`. Before generating the report, write
`.claude/modules/sonar-scans/remediation/pending-reasons.md` (rule | count | where | blocker | what it would take, plus any
accepted findings). The report embeds it, and its counts must match `.claude/modules/sonar-scans/after/issues.json`. Build
`CHANGELOG-SONAR-REMEDIATION.md` from the remediation logs. Then cross-check it against `git diff --name-only`:
agents don't always log every file they touch. Startup results: STARTED_AND_HEALTHY, STARTED_BUT_HEALTHCHECK_FAILED,
FAILED_TO_START, BLOCKED_BY_DEPENDENCY (no local Postgres/Oracle/Redis), NOT_RUN.

## Gotchas

From the first run (02-11-2026; measured results are in `.claude/modules/sonar-scans/final/FINAL-REPORT.md`):

- **Always build with `clean`.** VS Code's Java extension (JDT LS) compiles into `target/` inside OneDrive.
  A build without `clean` can treat broken IDE-compiled classes as up to date. Tests "pass" against them,
  or fail with "Unresolved compilation problem", and a missing `throws` compiles green. With `clean`, the race
  can delete classes mid-run (`ClassNotFoundException`, "cannot access X"), or overwrite a class with an
  Eclipse-compiled copy: `javap -v` shows `Unresolved compilation problems`, and javac reports "class file
  for X not found" for an *unqualified* X. The module that's open in the editor loses the race every time. The
  scripts retry once (`BUILD_RETRY=0` turns that off). **For the final evidence run, build a snapshot outside
  the IDE workspace:**
  ```bash
  SNAP=<scratchpad>/verify-snapshot
  rsync -a --delete --exclude target/ --exclude /logs/ --exclude /.claude/modules/sonar-scans/ ./ "$SNAP/"
  REPO_ROOT=$SNAP REPORTS_DIR=$PWD/.claude/modules/sonar-scans .claude/skills/alramz-sonar-remediation/scripts/build-all.sh   # same for startup-check/sonar-after
  ```
  Sonar file keys are relative, so a scan of the snapshot updates the same issues.
- **SonarCloud's first analysis shows the gate as `NONE`.** "Sonar way" only evaluates new code, so the gate
  reads OK or ERROR from the second analysis onward. Ratings are valid from the first one.
- Two findings are correct by design and are **ACCEPTED in SonarCloud, with the evidence as a comment**
  (approved by the user): S4502 (CSRF off: stateless Bearer-only auth, no cookies or sessions) and S2077 in
  reference-data `RedisCacheService` (config-sourced table name, validated identifier). Accept a finding only
  with the user's OK and written evidence. Use `api/issues/add_comment`, then `api/issues/do_transition transition=accept`.
- Sonar's wording can hide a real defect. Read the code before you apply the mechanical fix. The fixes in this
  run found three: a masking regex that leaked negative numbers, a scheduler that reported invalid jobs as
  SUCCEED with the previous job's future, and a `getEntry(mapping, key)` that ignored `key`.
- A regex rewrite for S5998 (possessive quantifiers) can raise S5843 complexity. Simplify the alternatives
  instead of stacking quantifiers.
- `[ERROR] Medium: … EI_EXPOSE_REP` lines in the build log are SpotBugs report output. They are not build failures.
- Agents running in parallel need: the starter installed first, one module each, `test` without `-am`, and
  the shared brief in `.claude/modules/sonar-scans/remediation/AGENT-BRIEF.md` (the forbidden-suppression rules live there).
- Pending by design (2026-11-02): S5738 Jackson 2 decoders in onboarding need a Jackson 3 WebClient
  migration. S2143 in `TokenProvider` is required by the jjwt API (`Date`). S1172 in starter test fixtures is
  reflection-bound. S2139 log-and-rethrow sites have exception types that tests depend on. S107 and S1172 sit
  on public starter constructors.

- `startup-check.sh` points the dev profile's Oracle BROK (wealth-management defaults to **BROKQA**), the
  eTrade QA and DFM test URLs, and the OTLP collector at localhost. Keep it that way. Verification must
  never touch shared environments.
- Nexus listens on 127.0.0.1:8081, which is reference-data-service's dev port. The startup check moves a
  service to a free port and doesn't kill the other process.
- Every profile refuses to start without a JWT secret of at least 256 bits. The script generates a
  throwaway one.
- The repo lives in OneDrive. Transient `Error assembling JAR` or JaCoCo I/O timeouts are file-lock
  problems, so rerun before you debug.
- The services' parent is the BOM via `<relativePath/>`, so after any BOM edit, `install` the BOM first.
