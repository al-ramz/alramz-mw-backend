# Startup check (profile: dev)

| Service | Port | Result | Health | Seconds | Detail |
|---|---|---|---|---|---|
| alramz-notification-service | 18080 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | STARTUP_FREE_PORT: ran on 18080; context started; /actuator/health is not in the JWT permit-all list (secured) |
| onboarding-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 22 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=aHNsOqItSGWOMezD9Le9fQ==) |
| reference-data-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 24 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=5IkWghguRkKVv6hITjCzrQ==) |
| reporting-management-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 23 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=izUm833HSqi89vFa7N7z2w==) |
| risk-management-service | 18080 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | STARTUP_FREE_PORT: ran on 18080; context started; /actuator/health is not in the JWT permit-all list (secured) |
| utility-service | 18080 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 13 | STARTUP_FREE_PORT: ran on 18080; context started; /actuator/health is not in the JWT permit-all list (secured) |
| wealth-management-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 23 | STARTUP_FREE_PORT: ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=QG5oBVMpRc6SltWfHHMd1Q==) |
