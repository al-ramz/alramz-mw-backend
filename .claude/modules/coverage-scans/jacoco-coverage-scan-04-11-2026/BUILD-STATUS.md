# Build status: coverage 04-11-2026

Source: `before/build-before.log`, the `clean verify` (with tests and the JaCoCo report) of the post-lint working tree on an isolated snapshot
(same log as `.claude/modules/static-analysis-scans/static-analysis-scan-04-11-2026/after/build/build.log`). Surefire 3.5.2 (BOM) in every module; no JaCoCo "does not match" warnings.

| Module | Build | Tests (fail/err/skip) | Line coverage | 80% gate |
|---|---|---|---|---|
| alramz-api-starter | SUCCESS | 252 (0/0/0) | 80.9% | PASS |
| alramz-notification-service | SUCCESS | 260 (0/0/0) | 98.4% | PASS |
| onboarding-service | SUCCESS | 259 (0/0/1) | 96.8% | PASS |
| reference-data-service | SUCCESS | 59 (0/0/0) | 95.9% | PASS |
| reporting-management-service | SUCCESS | 33 (0/0/0) | 93.0% | PASS |
| risk-management-service | SUCCESS | 101 (0/0/0) | 95.6% | PASS |
| utility-service | SUCCESS | 36 (0/0/0) | 98.7% | PASS |
| wealth-management-service | SUCCESS | 191 (0/0/0) | 93.9% | PASS |
