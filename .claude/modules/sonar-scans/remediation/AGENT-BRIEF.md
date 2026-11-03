# Sonar code-smell remediation brief (shared by all module agents)

Repo: /Users/gtalele/Library/CloudStorage/OneDrive-AlRamz/Desktop/workspace/alramz-mw-backend (Java 21, Spring Boot 4, Maven reactor).
Read `CLAUDE.md` first. Its rules apply (minimal diffs, no `git add`/commit, stay out of infra/ and .github/).

## Your input
`.claude/modules/sonar-scans/remediation/queue/<module>.tsv`: one SonarCloud finding per line, from the baseline scan:
`TYPE  SEVERITY  RULE  path:line  message`. Line numbers are from the baseline. Re-locate each issue by its
message if the file has changed since.

## Goal
Fix the root cause of every finding in your queue in the source code, and keep every test green.

## Hard rules (the user forbids these)
- No `//NOSONAR`, no `@SuppressWarnings` to silence a Sonar rule, no edits to the Sonar or quality-profile
  config, no file exclusions.
- Don't delete tests, assertions or production behavior to lose a finding. Don't weaken tests.
- Don't change public API signatures, REST contracts, JSON field names, config property names, or bean names.
  This matters most in `alramz-api-starter`, because every service compiles against it.
- Don't edit generated code (`target/generated-sources`, openapi-generated `api`/`model` packages). If an
  issue lives there, skip it and say why.
- Edit only files under your own `services/<module>/`.
- Preserve the user's existing uncommitted changes in those files. Edit on top of them.

## Already fixed by the lead (skip these if they show up in your queue)
Bugs and vulnerabilities in SeqAppender, LogMaskingUtil, LoggingHelper, JwtProperties, AuthService,
JobScheduleManager (log lines 144/175 and the double-brace map at 401), SchedulerManagerController (whole
file, including S1192, S6813 and S3457), the 5 S2387 `logger` fields in the starter filters,
EtisalatSmsClient:96, ETradeClient:37, and RedisCacheService:208 (S2077). Don't touch the CSRF line in
JwtAutoConfiguration (S4502). The lead is handling that one.

## How to fix (common rules)
- S1128 unused imports: delete them. S1068/S1481/S1854/S1172 unused code: delete it, but only when it's truly
  unused (grep first), and for private members only.
- S5778 (lambda in assertThrows): move the setup calls outside the lambda so only the throwing call remains.
- S5838/S5853/S5958/S8924/S6068/S9016 (AssertJ/Mockito style): apply the suggested idiom.
- S1186/S108 empty methods or blocks: add a short comment saying why it's empty (that is the rule's
  documented fix), or implement it.
- S2925 Thread.sleep in tests: use Awaitility if it's already on the test classpath, otherwise use Mockito
  `timeout()` or restructure. Don't add dependencies.
- S1192 duplicated literals: use a `private static final` constant in that class.
- S3457/S2629 log concatenation: use `{}` placeholders.
- S3776 cognitive complexity: extract well-named private methods. Behavior must stay identical, and existing
  tests must cover the path. Add a test if they don't.
- S112 generic exceptions: throw an existing domain exception or a more specific JDK one. Check callers and
  handlers so the HTTP response doesn't change.
- S2065 transient on a non-Serializable class: remove `transient`.
- S8688 `now()` without a zone: if the class has a Clock or ZoneId convention, use it. Otherwise pass
  `ZoneId.systemDefault()` explicitly, so behavior doesn't change.
- S5738 deprecated-for-removal: use the documented replacement.
- If a fix needs a design decision you can't take safely (a public API change, a behavior change), skip it
  and record why.

## Verify (from the repo root)
`mvn -B -pl services/<module> test` (**no `-am`**, because the starter is already installed in `~/.m2`). For
`alramz-api-starter` use `mvn -B -pl services/alramz-api-starter test` (NOT install). Run it after each
batch of fixes. When you finish, the module must be green with the same or more tests than the baseline.
The repo lives in OneDrive, so on a transient "Error assembling JAR" or an I/O timeout, rerun once before
debugging.

## Record
Append to `.claude/modules/sonar-scans/remediation/<module>.md` a table with one row per modified file:
`| file | rules fixed (count) | original problem | fix applied | tests run |`
Then add a section "Skipped" listing `rule | path:line | reason`.

## Report back (final message, short)
Counts fixed per rule, the skipped list with reasons, the final test totals from the Maven output
(`Tests run: N, Failures: 0, Errors: 0`), and anything risky the lead should review.
