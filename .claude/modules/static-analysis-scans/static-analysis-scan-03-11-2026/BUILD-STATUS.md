# BUILD-STATUS — static-analysis-scan 03-11-2026

Before = `git archive HEAD` snapshot; after = rsync snapshot of the working tree. Both: `mvn -B -fae clean verify` (unit tests + JaCoCo gate), then `startup-check.sh` (profile `dev`, throwaway JWT secret, outbound hosts pointed at localhost).

Overall: before **BUILD SUCCESS**, after **BUILD SUCCESS**. Analysis scans (`-Dpmd.skip=false -Dspotbugs.skip=false`) exit 0 before and after.

| Module | Build before | Build after | Tests before (run/fail/err/skip) | Tests after | Startup before | Startup after | Regression? |
|---|---|---|---|---|---|---|---|
| alramz-api-starter | SUCCESS | SUCCESS | 252/0/0/0 | 252/0/0/0 | library — n/a  | library — n/a  | no |
| alramz-notification-service | SUCCESS | SUCCESS | 260/0/0/0 | 260/0/0/0 | STARTED_BUT_HEALTHCHECK_FAILED HTTP 403  | STARTED_BUT_HEALTHCHECK_FAILED HTTP 403  | no |
| onboarding-service | SUCCESS | SUCCESS | 259/0/0/1 | 259/0/0/1 | BLOCKED_BY_DEPENDENCY n/a | BLOCKED_BY_DEPENDENCY n/a | no |
| reference-data-service | SUCCESS | SUCCESS | 59/0/0/0 | 59/0/0/0 | BLOCKED_BY_DEPENDENCY n/a | BLOCKED_BY_DEPENDENCY n/a | no |
| reporting-management-service | SUCCESS | SUCCESS | 33/0/0/0 | 33/0/0/0 | BLOCKED_BY_DEPENDENCY n/a | BLOCKED_BY_DEPENDENCY n/a | no |
| utility-service | SUCCESS | SUCCESS | 36/0/0/0 | 36/0/0/0 | STARTED_BUT_HEALTHCHECK_FAILED HTTP 403  | STARTED_BUT_HEALTHCHECK_FAILED HTTP 403  | no |
| wealth-management-service | SUCCESS | SUCCESS | 191/0/0/0 | 191/0/0/0 | BLOCKED_BY_DEPENDENCY n/a | BLOCKED_BY_DEPENDENCY n/a | no |

Startup notes (identical before and after, pre-existing):
- `BLOCKED_BY_DEPENDENCY` = Spring context reached the Oracle datasource and failed with `ORA-12541` (no local Oracle; URL deliberately pointed at localhost:1). Not a bean/class wiring error.
- `STARTED_BUT_HEALTHCHECK_FAILED HTTP 403` = context started; `/actuator/health` is behind the JWT filter.
- After run: onboarding-service ran on 18080 because 8080 was taken by another local process.

Logs: `before/build/build.log`, `after/build/build.log`, `before|after/startup/<service>.log`, scan builds `before/build-before.log`, `after/build.log`.