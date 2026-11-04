# Build status: static analysis 04-11-2026

Before = untouched working tree, built and startup-checked in the security step of this pass
(`.claude/modules/security-scans/vulnerability-security-scan-04-11-2026/after/{build,startup}/`). A `git archive HEAD` build
was not used: risk-management-service is untracked, so HEAD doesn't contain it.
After = `after/build/build-summary.md`, `after/startup/startup-summary.md` (isolated snapshot, `clean verify` with tests).

| Module | Build before / after | Tests before / after (fail/err/skip) | Startup before / after |
|---|---|---|---|
| alramz-api-starter | SUCCESS / SUCCESS | 252 / 252 (0/0/0) | n/a (library) |
| onboarding-service | SUCCESS / SUCCESS | 259 / 259 (0/0/1) | BLOCKED_BY_DEPENDENCY (Oracle) / same |
| alramz-notification-service | SUCCESS / SUCCESS | 260 / 260 | started, health 403 / same |
| reference-data-service | SUCCESS / SUCCESS | 59 / 59 | BLOCKED_BY_DEPENDENCY / same |
| utility-service | SUCCESS / SUCCESS | 36 / 36 | started, health 403 / same |
| reporting-management-service | SUCCESS / SUCCESS | 33 / 33 | BLOCKED_BY_DEPENDENCY / same |
| wealth-management-service | SUCCESS / SUCCESS | 191 / 191 | BLOCKED_BY_DEPENDENCY / same |
| risk-management-service | SUCCESS / SUCCESS | 101 / 101 (0/0/0) | started, health 403 (JWT filter) / same |

No regressions. The 403s and Oracle blocks are pre-existing and environment-driven.
