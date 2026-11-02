# Build status: JaCoCo coverage scan 02-11-2026

Command: `mvn -B -fae clean test` from the repo root. Logs are in `before/` and `after/`.

| Module | Build + tests BEFORE | Tests before | Build + tests AFTER | Tests after | 80% line gate after |
|---|---|---|---|---|---|
| alramz-common-bom | SUCCESS | n/a | SUCCESS | n/a | n/a (pom) |
| alramz-api-starter | SUCCESS | 131 | SUCCESS | 245 | PASS (80.7%) |
| onboarding-service | FAIL (I/O flake), SUCCESS on rerun | 215 (1 skipped) | FAIL in reactor (flake), SUCCESS on rerun | 257 (1 skipped) | PASS (96.8%) |
| alramz-notification-service | SUCCESS, but **0 tests executed** | 0 (21 classes skipped) | SUCCESS | 260 | PASS (98.3%) |
| reference-data-service | SUCCESS | 4 | FAIL in reactor (flake), SUCCESS on rerun | 59 | PASS (95.7%) |
| utility-service | SUCCESS (JaCoCo class mismatch) | 36 | SUCCESS | 36 | PASS (98.6%) |
| reporting-management-service | SUCCESS | 33 | FAIL in reactor (flake), SUCCESS on rerun | 33 | PASS (93.0%) |
| wealth-management-service | SUCCESS | 191 | SUCCESS | 191 | PASS (93.8%) |

**Flakes.** The full reactor run fails at random modules with `NoSuchFileException` / `bad class file` /
`cannot access <Class>` / `ClassNotFoundException` on files in `target/classes`. This is OneDrive syncing or
locking `target/` while Maven writes to it. Every module that failed this way passed on a serial
per-module `clean test` rerun with the identical tree: `after/build-after-rerun-<svc>.log` and
`before/build-before-onboarding-rerun.log`. Moving the repo out of OneDrive, or excluding `target/` from sync,
would make full-reactor runs reliable.
