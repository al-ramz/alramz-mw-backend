# Static analysis (Checkstyle / PMD / SpotBugs): 04-11-2026

Fix scope: risk-management-service. Scans cover the whole reactor. Skill: `.claude/skills/alramz-static-analysis-fixer/SKILL.md`.
Triage: no ruleset or filter change. The 03-11 decisions apply unchanged, so there is no `after-config/` step.

## Headline

| | Before | After | Δ |
|---|---|---|---|
| Reactor total | 131 | 107 | −24 |
| Checkstyle | 30 | 10 | −20 |
| PMD | 85 | 83 | −2 |
| SpotBugs | 16 | 14 | −2 |
| **risk-management-service** | **32** (CS 20 / PMD 7 / SB 5) | **8** (CS 0 / PMD 5 / SB 3) | **−24** |

Reduction by config: 0. Reduction in code: 24. New findings introduced: 0 (`comparison/comparison.md`).
The other modules didn't change (99 findings, out of scope).

## Fixed in code (24)

Tabs ×20 (Checkstyle FileTabCharacter); `info() throws Exception` (PMD SignatureDeclareThrowsException + SpotBugs THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION);
`ok` → `okResponse` (PMD ShortMethodName); explicit `requireNonNull` on `riskLevel` in the legacy mapper (SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE).
Details: `remediation/risk-management-service.md`, `CHANGELOG.md`.

## Suppressed

None.

## Deferred, risk-management-service (8)

| Rule | Count | Where | Reason |
|---|---|---|---|
| PMD AvoidFieldNameMatchingMethodName | 4 | ErrorCode (`status`, `message`), RiskBand (`rating`, `code`) | Record-style enum accessors; same decision as ErrorCode on 03-11 |
| PMD CyclomaticComplexity | 1 | KnowYourCountryClient.classify (10) | Flat outcome classifier; split = refactor |
| SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION (Low) | 1 | KnowYourCountryClient:89 | Intentional rethrow → service maps it to xxx004 |
| SpotBugs EI_EXPOSE_REP / EI_EXPOSE_REP2 | 2 | KnowYourCountryResult.Unavailable(Throwable) | Throwable can't be copied; same as DfmOnboardingOutcome.Unavailable on 03-11 |

## Verification

All 9 modules build, 1,191 tests, 0 failures, and startup is unchanged (`BUILD-STATUS.md`).
