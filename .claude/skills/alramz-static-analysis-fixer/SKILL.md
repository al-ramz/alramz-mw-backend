---
name: alramz-static-analysis-fixer
description: Run Checkstyle, PMD and SpotBugs (already wired into alramz-common-bom) across every module of the alramz-mw-oss monorepo, fix the real violations without breaking the build, then build, test and startup-check every service and write a dated before/after comparison, changelog and build/startup status under .claude/modules/static-analysis-scans/. Use when asked to "fix pmd", "fix checkstyle", "fix spotbugs", "static analysis cleanup", "lint violations", "code quality violations report", or to refresh a previous static-analysis scan.
---

# Static analysis (Checkstyle / PMD / SpotBugs) scan & fix

Goal: every finding is either fixed in code, or explicitly accepted (a ruleset/filter change the user
approved, a justified method-scoped suppression, or a deferred refactor with a reason). Every module still
builds, its tests pass, and every service starts as well as it did before. Never trade a green build for a
lower count.

## 0. Output layout (create first)

`DATE=$(date +%d-%m-%Y)`; root `R=.claude/modules/static-analysis-scans/static-analysis-scan-$DATE/`
(if it exists for today append `-2`, `-3`… — never overwrite a previous run).

```
$R/before/            scan of the untouched code: findings.json, summary.md, raw/*.xml, build.log
$R/before/build/      build-summary (clean verify + tests) of HEAD;  $R/before/startup/  startup check
$R/after-config/      (optional) scan after ruleset/filter changes only — separates config from code effect
$R/queue/<module>.tsv work queue per module (severity, rule, file:line, message)
$R/remediation/<module>.md  per-module log: Fixed (before/after snippet) / Suppressed / Deferred / Verification
$R/after/             same scan after fixes; after/build/, after/startup/
$R/comparison/        comparison.{md,json}: totals, by tool+severity, by module, by rule, NEW findings
$R/CHANGELOG.md       one row per change: file, rule(s), before -> after, why (config changes first)
$R/BUILD-STATUS.md    per module: build/tests before & after, startup before & after
$R/REPORT.md          summary: counts before/after, what was fixed, accepted, deferred + reasons
```

## 1. Tooling (bundled)

`S=.claude/skills/alramz-static-analysis-fixer/scripts`

```
$S/scan.sh <out>        rsync repo -> snapshot, install BOM, mvn clean verify -DskipTests with
                        -Dpmd.skip=false -Dspotbugs.skip=false, then lint_report.py collect -> <out>
                        (RUN_TESTS=1 to also run tests; SNAP=<dir> to choose the snapshot dir)
$S/lint_report.py collect --repo <root> --out <dir>      parse target/{checkstyle-result,pmd,spotbugsXml}.xml
$S/lint_report.py compare --before <d> --after <d> --out <d>   fixed/new matched line-insensitively
```

Build + startup status reuse the Sonar skill's scripts (don't duplicate them):
`.claude/skills/alramz-sonar-remediation/scripts/{build-all.sh,startup-check.sh}` with
`REPO_ROOT=<snapshot> REPORTS_DIR=$R/<before|after>` → `<dir>/build/build-summary.md`, `<dir>/startup/startup-summary.md`.

Where the analysers are configured (all in `services/alramz-common-bom/pom.xml` + `config/`):
- Checkstyle `config/checkstyle.xml`, runs at `validate`, all modules, main sources only, `failOnViolation=false`.
- PMD `config/pmd.xml` (whole categories bestpractices/codestyle/design/errorprone/security, with excludes),
  `pmd.skip=true` by default, excludes `target/generated-sources`.
- SpotBugs runs at `verify`, `spotbugs.skip=true` by default, threshold Low, filter `config/spotbugs-exclude.xml`.
- `alramz-api-starter` hard-codes `<skip>true</skip>` for PMD and SpotBugs → only Checkstyle covers it.

## 2. Baseline (before)

```bash
$S/scan.sh $R/before                                   # findings of the untouched tree
SNAP_HEAD=<scratch>/snap-head; git archive HEAD | tar -x -C $SNAP_HEAD   # status of committed code
REPO_ROOT=$SNAP_HEAD REPORTS_DIR=$PWD/$R/before .claude/skills/alramz-sonar-remediation/scripts/build-all.sh
REPO_ROOT=$SNAP_HEAD REPORTS_DIR=$PWD/$R/before .claude/skills/alramz-sonar-remediation/scripts/startup-check.sh
```

Read `$R/before/summary.md` (by tool/severity, module, rule). Uncommitted WIP that breaks the build is
recorded, not "fixed" — ask the user.

## 3. Triage — decide policy before touching code

Look at the by-rule table. On first runs a handful of style-opinion PMD rules dominate (03-11-2026:
`MethodArgumentCouldBeFinal` 620, `LocalVariableCouldBeFinal` 433, `LongVariable` 217, `OnlyOneReturn` 146,
`ShortVariable` 95 — 61% of all findings). **Ask the user** (AskUserQuestion) whether to exclude those in
`config/pmd.xml` or sweep them in code; same for EI_EXPOSE_REP* policy. Ruleset/filter changes are a team
decision, never a silent way to lower counts: each one gets a comment in the XML and a CHANGELOG row, and
the report shows config-driven and code-driven reductions separately (that's what `after-config/` is for).

Decisions already taken (03-11-2026 run, keep unless the user says otherwise):
- PMD excluded: the 7 opinion rules above + `AtLeastOneConstructor`, `CommentDefaultAccessModifier`,
  `LawOfDemeter`, `GuardLogStatement` (code already uses SLF4J `{}` placeholders).
- Logger names `log`/`logger`/`auditLogger` allowed (Lombok `@Slf4j` convention) in Checkstyle
  `ConstantName` and PMD `FieldNamingConventions` (`exclusions` is **comma**-separated in PMD 7).
- SpotBugs filter wired (`excludeFilterFile`); EI_EXPOSE_REP* accepted for OpenAPI-generated
  `com.alramz.model`/`com.alramz.api`, Spring-bean constructor injection (`*Repository|*Service|*Client|
  *Provider|*Controller|*Job|*Config|*Mapper`…), `*Properties` binding classes.
- Result of that run for reference: 2,431 → 99 (1,966 accepted by config, 366 cleared in code, 0 new), see
  `.claude/modules/static-analysis-scans/static-analysis-scan-03-11-2026/REPORT.md`.

Then write `$R/queue/<module>.tsv` from `$R/after-config/findings.json` (or `before/` if no config change).

## 4. Fix (priority order, per module)

1. SpotBugs correctness: NP_*, RCN_*, BX_*, CT_CONSTRUCTOR_THROW (make class final), resource leaks.
2. PMD errorprone/security: PreserveStackTrace (pass cause), CloseResource (try-with-resources),
   CompareObjectsWithEquals, UnusedAssignment, AvoidCatchingGenericException (narrow to real types).
3. EI_EXPOSE_REP* in hand-written records/DTOs: null-safe defensive copies (`List.copyOf` rejects null
   elements — use `Collections.unmodifiableList(new ArrayList<>(x))` when nulls are possible).
4. Mechanical Checkstyle: tabs, CRLF/EOF newline, trailing whitespace, star/unused imports, line length,
   serialVersionUID — scriptable; do them in one pass.
5. Code-style PMD: field order, braces, diamond, duplicate literals, literals in if.
6. Design metrics (GodClass, TooManyMethods, CouplingBetweenObjects, ExcessiveImports, DataClass,
   Cyclomatic/NPath/Cognitive, ClassFanOut) → **defer** with reason unless one small extraction fixes it.

Never: rename members bound to JSON / SQL params / `@ConfigurationProperties` / public starter API; edit
generated sources; delete or weaken tests; class-wide suppressions or bare `// NOPMD`. Allowed: a
method-scoped `@SuppressWarnings("PMD.<Rule>")` + `// reason` for deliberate boundary catches (scheduler
jobs, startup runners, cache fallbacks) or proven false positives — counted separately in the report.

Large queues: one subagent per module (disjoint files), each with its own snapshot dir, `-o -pl <module>`,
no `-am`, no `install` (others compile against the `~/.m2` starter), and no public starter API change.
Each writes `$R/remediation/<module>.md` (Fixed with before/after snippet, Suppressed, Deferred, Verification).

## 5. Verify (after) and report

```bash
$S/scan.sh $R/after
python3 -I $S/lint_report.py compare --before $R/before --after $R/after --out $R/comparison
SNAP_AFTER=<scratch>/snap-after; rsync -a --delete --exclude target/ --exclude .git/ --exclude /.claude/modules/ ./ $SNAP_AFTER/
REPO_ROOT=$SNAP_AFTER REPORTS_DIR=$PWD/$R/after .claude/skills/alramz-sonar-remediation/scripts/build-all.sh
REPO_ROOT=$SNAP_AFTER REPORTS_DIR=$PWD/$R/after .claude/skills/alramz-sonar-remediation/scripts/startup-check.sh
```

`comparison.md` "New findings introduced" must be empty or explained. Then write CHANGELOG.md (config rows
first, then each remediation log's Fixed rows; cross-check against `git diff --name-only` — agents don't
always log every file), BUILD-STATUS.md (before vs after build, tests, startup per module; a startup
`BLOCKED_BY_DEPENDENCY` for Oracle/Redis or a 403 on `/actuator/health` that was the same before is not a
regression), and REPORT.md (headline numbers, config vs code reduction, suppressed list, deferred list).

## Gotchas (03-11-2026 run)

- **Scan only on a snapshot.** VS Code's JDT LS writes into `target/` inside OneDrive; an in-place
  `clean verify` failed testCompile with "cannot find symbol" in untouched tests. `scan.sh` rsyncs first.
- **Install the BOM before scanning** — services use `<relativePath/>` and load the parent from `~/.m2`,
  so an edited plugin config in the BOM is ignored until installed (`scan.sh` does it).
- `config/spotbugs-exclude.xml` existed but was not referenced by the plugin; its `~.*Model.*` patterns
  are case-sensitive and do not match the lowercase `com.alramz.model` package — match by `<Package>`.
- SpotBugs `NM_CONFUSING` fires between generated `OnboardingRequest.getEidFullname()` and hand-written
  `DfmOnboardingRequest.getEidFullName()`; renaming the hand-written one breaks SQL parameter binding.
- `compare` masks numbers in messages: GodClass/Coupling restate metrics (`WMC=78`, `21 → 22`) after any
  edit, which otherwise shows up as "fixed + new".
- PMD 7 multi-value properties (e.g. `FieldNamingConventions` `exclusions`) are comma-separated; `|` is
  silently treated as one name.
- Normalising CRLF → LF (`NewlineAtEndOfFile`) shows as whole-file diffs; say so in the changelog.
- Startup: profile `dev` with a throwaway `JWT_SECRET`; Oracle-backed services end `BLOCKED_BY_DEPENDENCY`
  locally, and notification/utility return 403 on `/actuator/health` (JWT filter) — both pre-existing.

## Rules

- Stage files explicitly; never `git add .` (CLAUDE.md). Do not commit unless asked.
- Don't touch untracked WIP files, `infra/`, or `.github/workflows/`.
- Don't flip `failOnViolation`/`failOnError` to true without the user asking — that changes CI behavior.
