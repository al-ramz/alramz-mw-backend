# JaCoCo coverage: 04-11-2026

Fix scope: risk-management-service. Skill: `.claude/skills/alramz-jacoco-coverage-check/SKILL.md`.
No tests were added (every module already passes), so there is no `after/` folder. The Sonar step's final build re-measures coverage with the same code.

| Service | Line | Branch | Instruction | Lines | Gate |
|---|---|---|---|---|---|
| alramz-api-starter | 80.9% | 66.6% | 81.3% | 2471/3056 | PASS (thin margin) |
| alramz-notification-service | 98.4% | 94.1% | 98.4% | 1275/1296 | PASS |
| onboarding-service | 96.8% | 84.3% | 96.9% | 1052/1087 | PASS |
| reference-data-service | 95.9% | 82.1% | 94.3% | 518/540 | PASS |
| reporting-management-service | 93.0% | 86.8% | 92.9% | 133/143 | PASS |
| **risk-management-service** | **95.6%** | **92.5%** | **95.8%** | **217/227** | **PASS** |
| utility-service | 98.7% | 75.0% | 98.1% | 148/150 | PASS |
| wealth-management-service | 93.9% | 95.7% | 95.1% | 261/278 | PASS |

Remaining risk-management-service gaps (10 lines): ApplicationController `/api/v1/info` (5), RiskComplianceService (2), the startup banner runner (2), KnowYourCountryClient (1).
None of these is worth a test just for coverage.

Findings: alramz-api-starter is 0.9 points above the gate (out of scope). The BOM `check` minimums are still 0.00, which is a separate decision for the user.

Reproduce: `mvn -B -fae clean test` on a snapshot, then `python3 .claude/skills/alramz-jacoco-coverage-check/scripts/coverage_summary.py <out> --classes` from the snapshot root.
