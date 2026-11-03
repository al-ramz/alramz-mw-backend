# Startup check (profile: dev)

| Service | Port | Result | Health | Seconds | Detail |
|---|---|---|---|---|---|
| alramz-notification-service | 8082 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 18 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| onboarding-service | 18080 | BLOCKED_BY_DEPENDENCY | n/a | 26 | configured port 8080 in use by another process; ran on 18080; Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=H3l99fnWRBOieHBfEjCPkQ==) |
| reference-data-service | 8081 | BLOCKED_BY_DEPENDENCY | n/a | 29 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=C7OmAtevSAKvUNYuxCY4TA==) |
| reporting-management-service | 8084 | BLOCKED_BY_DEPENDENCY | n/a | 24 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=ZOtajv5eSlCnBgk/Rtj0Jw==) |
| utility-service | 8083 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 12 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| wealth-management-service | 8085 | BLOCKED_BY_DEPENDENCY | n/a | 22 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=Qh+AiWheTC2RRuumF2j46w==) |
