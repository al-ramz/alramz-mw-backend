# Startup check (profile: dev)

| Service | Port | Result | Health | Seconds | Detail |
|---|---|---|---|---|---|
| alramz-notification-service | 8082 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 29 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| onboarding-service | 8080 | BLOCKED_BY_DEPENDENCY | n/a | 37 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=UiE8Nn+cQr+Hz2q3zDFA+w==) |
| reference-data-service | 8081 | BLOCKED_BY_DEPENDENCY | n/a | 37 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=0pt07x3aTbOlJ6oZGJm8Lg==) |
| reporting-management-service | 8084 | BLOCKED_BY_DEPENDENCY | n/a | 40 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=Su9VsPdfQLi3SFZ2Q0zppg==) |
| utility-service | 8083 | STARTED_BUT_HEALTHCHECK_FAILED | HTTP 403  | 29 | context started; /actuator/health is not in the JWT permit-all list (secured) |
| wealth-management-service | 8085 | BLOCKED_BY_DEPENDENCY | n/a | 33 | Caused by: oracle.net.ns.NetException: ORA-12541: Cannot connect. No listener at host localhost port 1. (CONNECTION_ID=HFGmvYOJTtm76L5FafLCfA==) |
