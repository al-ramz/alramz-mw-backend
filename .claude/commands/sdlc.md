---
description: >
  Orchestrates the 4-stage SDLC pipeline (01_elicit -> 02_plan -> 03_implement -> 04_security) for
  a single feature in the alramz-mw-oss Java/Spring Boot monorepo, with human-in-the-loop approval
  gates between planning and implementation, and between security and hand-off. Invokes each stage
  as its own scoped subagent so each keeps only the tools its contract file grants it.
argument-hint: <feature request description>
---

# SDLC Pipeline Orchestrator

## Description

Runs one feature through the four populated SDLC stages end-to-end, stopping for explicit human
approval at two gates (post-plan, post-security) and stopping hard — no silent fallback, no
guessing — the moment any stage reports **BLOCKED**. Stage 5 (`05_pr`) is not implemented yet
(`.claude/agents/05_pr.md` is an intentional empty stub) — this command ends after Gate 2 with the
working tree ready for a human to review and commit/PR manually; it never auto-commits, pushes, or
opens a PR.

Each stage is invoked via the `Agent` tool using its registered `subagent_type` (`01_elicit`,
`02_plan`, `03_implement`, `04_security`) — never by inlining that file's instructions into this
session — so each stage runs with exactly the tools its own frontmatter grants (e.g. `01_elicit`
never gets `Bash`). Run stages **in the foreground, one at a time**: each depends on the previous
stage's artifact and the next action here (a gate question, a BLOCKED stop) depends on its result.

Work one feature at a time. Two features sharing a service concurrently can collide on Liquibase
changeset numbering or on the same shared module (`alramz-common-bom`, `alramz-api-starter`) — if
`.claude/modules/features/` already has another feature mid-pipeline, that's a signal to finish or
explicitly park it before starting a new one, not a blocker to enforce automatically.

## User Input

Feature Request: $ARGUMENTS

## Step 0: Resume Check

Derive a candidate kebab-case feature-slug from the request (same rule `01_elicit` uses) and check
`.claude/modules/features/<candidate-slug>/` for existing artifacts (`Glob`/`Read` — read-only,
no side effects). If a matching directory already has partial artifacts (e.g. `spec.md` but no
`execution_plan.json`), use `AskUserQuestion` before doing anything else:

> This feature looks like it may already be in progress at `.claude/modules/features/<slug>/`
> (found: `<list what's there>`). How do you want to proceed?
- **Resume from the next stage after what's already there (Recommended)** — skip stages whose
  artifact already exists, start from the first missing one.
- **Restart from Step 1** — re-run `01_elicit` and overwrite the existing spec (only if the
  request has materially changed).
- **Treat as a different feature** — pick a distinct slug and start clean.

If nothing exists yet for this request, skip straight to Step 1.

## Step 1: Requirement Elicitation

Invoke the `Agent` tool with `subagent_type: 01_elicit`, `run_in_background: false`, prompt
containing the full feature request text from `$ARGUMENTS` (plus, on resume, a note of the
existing feature-slug to reuse).

**After it returns:**
- If its response reports **BLOCKED**, stop the whole pipeline here. Surface its exact question(s)
  and recommended options to the user verbatim — do not attempt to answer them yourself or guess a
  resolution and re-invoke.
- Otherwise, extract the exact `.claude/modules/features/<feature-slug>/` path it states in its
  summary — this is the shared feature directory every later step must be given explicitly (don't
  make later stages rediscover it). Verify `spec.md` exists there.

## Step 2: Architecture & Planning

Invoke `Agent` with `subagent_type: 02_plan`, `run_in_background: false`, prompt explicitly
stating the feature directory path captured in Step 1 (e.g. "Feature directory:
`.claude/modules/features/<feature-slug>/`. Read `spec.md` from there and write
`execution_plan.json` there.").

**After it returns:**
- If **BLOCKED**, stop the whole pipeline here and surface its question(s) verbatim.
- Otherwise verify `execution_plan.json` exists at that path and read it in full.

## Stage Gate 1: Plan Verification

Don't just dump the raw JSON — present a digest the user can actually approve or reject on:

- **Target services** (`target_services`) — which of `data-validation-service` /
  `alramz-notification-service` / `reference-data-service` / `alramz-api-starter` /
  `alramz-common-bom` this touches. CI is path-filtered per service, so this is what will actually
  build/deploy once committed. **Call out explicitly** if `alramz-api-starter` or
  `alramz-common-bom` is in this list — a change there affects every consuming service, not just
  the one the feature is nominally "for."
- **Target files** (`target_files`) and **test files** (`test_files`), grouped by service.
- **Liquibase changeset** (`liquibase_changeset`), if present — flag prominently, since per
  CLAUDE.md an applied changeset can never be edited later, only appended to.
- **Config changes** (`config_changes`) — any new/changed `@ConfigurationProperties` or
  `application*.yml` keys.
- **Technical gaps and plan critique** (`technical_gaps`, `plan_critique`), including any
  `user_decisions` already recorded during planning.

Then use `AskUserQuestion`:

> 🚧 **HITL GATE 1** — Do you approve this execution plan?
- **Approve — proceed to implementation (Recommended)**
- **Request changes** — describe what to change; re-run `02_plan` with that feedback appended to
  its input, then re-present this gate. Loop until approved or rejected.
- **Reject** — stop the pipeline here. Nothing has been implemented yet; `spec.md` and
  `execution_plan.json` are left in place for reference.

Do not proceed to Step 3 without an explicit **Approve** here.

## Step 3: Implementation & Local Test Loop

Invoke `Agent` with `subagent_type: 03_implement`, `run_in_background: false`, prompt stating the
same feature directory path (it reads `spec.md` and `execution_plan.json` from there itself).

**After it returns:**
- If **BLOCKED**, stop the whole pipeline here and surface its question(s) verbatim.
- Otherwise verify `implementation-report.md` exists, and that its Test Results section reports the
  targeted `mvn -pl services/<service> -am clean test` run(s) exiting 0 with the JaCoCo coverage
  gate passed for every new file — per `03_implement`'s own contract it shouldn't report done
  otherwise, but confirm rather than assume before moving on.

## Step 4: Security & Hardening

Invoke `Agent` with `subagent_type: 04_security`, `run_in_background: false`, prompt stating the
feature directory path, **plus this explicit scoping note**: "No commits have been made yet for
this feature — its diff is still uncommitted working-tree changes. Scope your `git diff`
verification to the working tree (`git status`, `git diff --stat`, `git diff --name-only` with no
ref) rather than a `<base-branch>...HEAD` comparison, since there is no meaningful base-branch
diff to compare against yet."

**After it returns:**
- If **BLOCKED**, stop the whole pipeline here and surface its question(s) verbatim.
- Otherwise verify `security_report.md` exists and read its **Audit Status** line.
- If Audit Status is **ACTION REQUIRED**, don't go straight to Gate 2 — present the specific
  findings to the user and use `AskUserQuestion` first:
  > `04_security` flagged issues it could not resolve on its own: `<summarize findings>`. How do
  > you want to proceed?
  - **Send back to `04_security` with guidance (Recommended)** — describe the fix, re-invoke.
  - **Accept as-is and continue to Gate 2** — only if the findings are genuinely acceptable risk.
  - **Reject — stop the pipeline here.**

## Stage Gate 2: Security & Diff Verification

Show the `security_report.md` summary (Audit Status + key findings) and a **diff scoped to this
feature only** — `git status -- <target_files ∪ test_files from execution_plan.json>` and
`git diff --stat -- <same paths>` — not a bare repo-wide `git diff --stat`. This repo's working
tree already carries unrelated pre-existing modifications (README.md, db-seed.sql, stray tracked
`target/` artifacts, etc.); an unscoped diff would bury this feature's actual changes in noise.

Then use `AskUserQuestion`:

> 🚧 **HITL GATE 2** — Do you approve this security audit and diff?
- **Approve — ready for manual commit/PR (Recommended)**
- **Request changes** — describe what's wrong; re-run the relevant stage (`03_implement` or
  `04_security`) with that feedback, then re-present this gate.
- **Reject** — stop here; leave the working tree untouched for manual review.

## After Gate 2 Approval

Stop here. **Do not** run `git add`, `git commit`, `git push`, or open a PR automatically — those
are exactly the kind of hard-to-reverse/shared-visibility actions that need a separate, explicit
go-ahead each time, and stage 5 (`05_pr`) doesn't exist yet to own that step. Instead, tell the
user:
- The feature directory with all artifacts: `.claude/modules/features/<feature-slug>/`.
- The exact scoped file list to stage (never `git add -A`/`git add .` per CLAUDE.md — this repo
  has stray tracked `target/` files).
- That PR creation is currently a manual step until `05_pr` is implemented.

## Abort / BLOCKED Handling (applies to every step above)

A stage reporting **BLOCKED** is not a gate — it's a hard stop. Do not attempt to resolve its
question yourself, do not re-invoke it with a guessed answer, and do not skip ahead. Report the
blocking question(s) and the stage's own recommended options to the user verbatim, and end the
command. The user can re-run `/sdlc` for the same feature once they've decided; Step 0's resume
check will pick up from the last completed artifact.
