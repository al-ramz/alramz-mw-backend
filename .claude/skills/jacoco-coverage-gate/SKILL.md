---
name: jacoco-coverage-gate
description: Measure JaCoCo coverage for every module in the alramz-mw-oss Maven reactor, add real unit tests to bring each service up to the 80% line-coverage gate, and write a dated before/after coverage summary, per-service build status and changelog under .claude/modules/coverage-scans/. Use when asked for a "coverage report", "jacoco summary", "raise test coverage", "80% coverage gate", "which services are under-tested", or to refresh a previous coverage-scan report.
---

# JaCoCo coverage gate (80% line)

Goal: every service module shows ≥ 80% JaCoCo **line** coverage, reached with tests that assert real
behavior. A green build matters more than a percentage. Never trade one for the other.

JaCoCo is already wired: `alramz-common-bom/pom.xml` holds the `prepare-agent` / `report` (test phase) /
`check` (verify) executions. Each service pom adds the plugin. Report: `services/<svc>/target/site/jacoco/`
(`index.html`, `jacoco.csv`). The `check` minimums in the BOM are still `0.00`. Raising them is a
separate decision for the user to make: it would fail CI for any service under the gate.

## 0. Output layout (create first)

`DATE=$(date +%d-%m-%Y)`, then `R=.claude/modules/coverage-scans/jacoco-coverage-scan-$DATE/`

```
$R/before/          build-before.log, <svc>-jacoco.csv, summary.md/json (baseline)
$R/after/           the same files after the new tests are in
$R/CHANGELOG.md     one row per file changed: file, what it tests / why changed, coverage delta
$R/BUILD-STATUS.md  per-service table: build + tests before/after, test count, gate result
$R/REPORT.md        before vs after coverage table, remaining gaps, findings, how to reproduce
```

If today's folder already exists, append `-2`, `-3`… Never overwrite an earlier run.

## 1. Baseline (before)

From the repo root:

```bash
mvn -B -fae clean test > $R/before/build-before.log 2>&1      # -fae: one red module doesn't hide the rest
python3 .claude/skills/jacoco-coverage-gate/scripts/coverage_summary.py $R/before --classes
```

`coverage_summary.py` copies every `services/*/target/site/jacoco/jacoco.csv`, totals the line, branch and
instruction counts per service, marks the 80% line gate, and with `--classes` lists the 15 classes per
service with the most missed lines. That list is your work queue.

Check the per-module surefire lines too (`grep "Tests run:" ` / `grep "surefire:"`). **If a module with test
classes has no jacoco.csv, its tests probably did not run.** Find the cause before you write any test
(see Gotchas).

## 2. Close the gap (per service under 80%)

- Add tests only under `src/test/java`. Do not edit `src/main`, the poms or the JaCoCo excludes to move the
  number. Two exceptions: a build/test-runner misconfiguration that stops tests from running, which is a
  real fix, and a production bug, which you record in REPORT.md and leave unfixed unless the user asks.
- Load `spring-boot-testing` to pick a technique. Prefer plain Mockito + AssertJ unit tests. Use
  `MockRestServiceServer` for HTTP clients and mocked `RedisTemplate`/`JdbcTemplate` for data access. Don't
  use `@SpringBootTest` just to reach coverage.
- Go largest-missed-first, using the `--classes` list. Every test asserts something: a return value, an
  interaction, masking output or an exception. Tests with no assertions are coverage gaming.
- Aim for about 85% so a small refactor doesn't drop the service under the gate.
- Independent services can be worked in parallel, one agent per module. Run
  `mvn -pl services/alramz-common-bom,services/alramz-api-starter install -DskipTests` once first. After
  that, each agent runs `mvn -B -pl services/<svc> test` **without `-am`**, so two agents never rebuild the
  same `target/`.

## 3. Verify (after)

```bash
mvn -B -pl services/alramz-common-bom,services/alramz-api-starter install -DskipTests
mvn -B -fae clean test > $R/after/build-after.log 2>&1        # must be green
python3 .claude/skills/jacoco-coverage-gate/scripts/coverage_summary.py $R/after --classes
```

Then write CHANGELOG.md, BUILD-STATUS.md and REPORT.md. The REPORT table puts before and after side by side
for line, branch and instruction coverage, with the gate result.

## Gotchas seen in this repo (02-11-2026 run)

- **Surefire pins silently skip JUnit 6 tests.** `alramz-notification-service` pinned
  `maven-surefire-plugin` 3.2.5, which finds no JUnit Jupiter 6 tests. Its 21 test classes never ran, it
  produced no report, and the build still showed SUCCESS. The fix was to drop the version and inherit 3.5.2
  from the BOM. Check this with `grep "surefire:" build.log | sort | uniq -c`: every module should show the
  BOM version.
- **The repo lives in OneDrive.** A transient `NoSuchFileException` / `bad class file` in `target/classes`,
  `Error assembling JAR` or a JaCoCo `Operation timed out` is a file-lock or I/O problem. Rerun that module
  with `clean` before you debug. In the 02-11 run, a full `-fae clean test` failed at random modules both
  before and after, with `cannot access <Class>`, `ClassNotFoundException`, or ~100 test errors on code that
  wasn't touched. Every one of them passed on a serial `mvn -B -pl services/<svc> clean test` rerun. Keep
  both logs and judge a module on its rerun.
- **A class at 0% even though its test passed** usually means a JaCoCo class mismatch, not missing tests.
  Run `grep "does not match" build.log`. In the 02-11 baseline, utility-service's `FinouxClient` showed 0%
  (56.6% overall) because of this. A clean rerun of that module gave 98.6% with no new tests. Rerun any
  module with a mismatch before you count its numbers or write tests for it.
- **Stale parent in ~/.m2.** Services declare the BOM with `<relativePath/>`. After a BOM edit, install it
  first.
- Generated `org.openapitools.configuration.*` classes are counted, because the excludes only cover
  `**/model/**` and `**/api/**`. They add a few lines per service. Leave them as they are unless the user
  wants the excludes changed.

## Rules

- Stage files explicitly and never use `git add .` (CLAUDE.md). Don't commit unless asked.
- Don't delete or `@Disabled` failing tests to make the build green. Report them.
