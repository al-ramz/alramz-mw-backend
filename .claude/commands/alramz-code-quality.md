---
description: >
  Run the alramz-code-quality agent, which applies the security (Trivy), JaCoCo coverage and SonarCloud
  remediation skills to the alramz-mw-oss monorepo in that order, and returns one consolidated report.
argument-hint: "[all | security | coverage | sonar ...] [services: <svc>,<svc>]"
---

# Code quality pass

User input: $ARGUMENTS

1. Work out the checks from the input. If it names none of `security`, `coverage` or `sonar`, run
   `all`. Any `services:` list narrows fixes to those services; the scans still cover the whole
   reactor.
2. Invoke the `Agent` tool once, with `subagent_type: alramz-code-quality` and
   `run_in_background: false`. Pass a prompt that states the checks to run (in the agent's fixed
   order: security → coverage → sonar), any service scope, and the user's full input verbatim. Don't
   inline the agent's or the skills' instructions; the agent loads the skills itself.
3. When it returns, relay its consolidated summary to the user as it is: per-skill status and report
   paths, before/after numbers, final build result, and changed files. If it reports **BLOCKED** or
   lists decisions for the user, put those first, quote its evidence, and stop. Don't retry or pick
   an option on the user's behalf.
4. Commit nothing. The working tree stays as the agent left it, for the user to review.
