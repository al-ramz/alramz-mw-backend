# Startup check (profile: dev)

| Service | Port | Result | Health | Seconds | Detail |
|---|---|---|---|---|---|
| alramz-notification-service | 8082 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 13 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| onboarding-service | 8080 | BLOCKED_BY_DEPENDENCY | n/a | 13 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=nNl9cp2WRC2/652qxhy5Vw==) |
| reference-data-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 19 | configured port 8081 in use by another process; ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=dTI0p8mESUSsU+M1qPuDyA==) |
| reporting-management-service | 8084 | BLOCKED_BY_DEPENDENCY | n/a | 12 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=IL0S4IfVRKCQQCYHRHEoog==) |
| utility-service | 8083 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| wealth-management-service | 8085 | BLOCKED_BY_DEPENDENCY | n/a | 13 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=ajVW4pObTA2pXOLE679SkA==) |
