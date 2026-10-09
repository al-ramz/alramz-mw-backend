# Technology Stack

## Core Sections (Required)

### 1) Runtime Summary

| Area | Value | Evidence |
|------|-------|----------|
| Primary language | Java 21 | `services/alramz-common-bom/pom.xml` (`<java.version>21</java.version>`, `<maven.compiler.release>21</maven.compiler.release>`) |
| Runtime + version | Spring Boot 4.1.0 on Spring Framework 7.0.8 | `services/alramz-common-bom/pom.xml` (parent `spring-boot-starter-parent:4.1.0`, `<spring.framework.version>7.0.8</spring.framework.version>`) |
| Package manager / build system | Maven 3.9+ (multi-module reactor) | root `pom.xml` (`<modules>` lists 5 modules); `alramz-common-bom/pom.xml` enforces `requireMavenVersion 3.9.0` |
| Module/build system | Maven reactor with a shared parent BOM (`alramz-common-bom`) and a shared auto-config starter (`alramz-api-starter`) | root `pom.xml`; `services/data-validation-service/pom.xml` and siblings all declare `<parent>com.alramz.bom:alramz-common-bom</parent>` |

### 2) Production Frameworks and Dependencies

| Dependency | Version | Role in system | Evidence |
|------------|---------|----------------|----------|
| Spring Boot Web (MVC) | 4.1.0 (via BOM) | REST controllers for all 3 runnable services | `spring-boot-starter-webmvc` in each service `pom.xml` |
| Spring Data JPA + Hibernate | via Spring Boot BOM | ORM for `data-validation-service`, `alramz-notification-service`, `reference-data-service`, and the `alramz-api-starter`'s JWT user/refresh-token tables | `spring-boot-starter-data-jpa` dependencies |
| Liquibase | 4.27.0 | Schema migrations for `data-validation-service` and `reference-data-service` only | `services/data-validation-service/pom.xml`, `services/reference-data-service/pom.xml` (`spring-boot-starter-liquibase`, `liquibase-maven-plugin`) |
| HikariCP | 7.0.2 | Connection pooling for the starter's multi-datasource framework | `alramz-common-bom/pom.xml` (`<hikaricp.version>`), `alramz-api-starter/pom.xml` |
| jjwt (`io.jsonwebtoken`) | 0.12.6 | JWT issuing/parsing inside `alramz-api-starter`'s JWT auto-configuration | `alramz-common-bom/pom.xml` (`<jjwt.version>`), `services/alramz-api-starter/src/main/java/com/alramz/jwt/**` |
| Spring Security | via Spring Boot BOM | Stateless JWT `SecurityFilterChain`, method security | `services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java` |
| Lombok | 1.18.38 | Boilerplate reduction (getters/setters/builders) across all modules | `alramz-common-bom/pom.xml`; used pervasively, e.g. `@Getter @Setter` on config/property classes |
| OpenAPI Generator (Maven plugin) | 7.23.0 | Contract-first: generates Spring `*Api` interfaces + models from `specs/*.yaml` at build time | `services/data-validation-service/pom.xml`, `services/alramz-notification-service/pom.xml`, `services/reference-data-service/pom.xml` |
| springdoc-openapi | 2.8.9 | Serves live Swagger UI / OpenAPI docs at runtime | `alramz-common-bom/pom.xml` (`springdoc-openapi-starter-webmvc-ui.version`) |
| net.ttddyy `datasource-proxy` | 1.10 | Wraps datasources for SQL logging in the multi-datasource framework | `alramz-common-bom/pom.xml`, `alramz-api-starter/pom.xml` |
| Azure SDK (`azure-messaging-servicebus`, `azure-storage-blob`, `azure-identity`, `azure-security-keyvault-secrets`) | 1.15.0 / 7.17.6 / 12.25.0 / 4.8.0 | Azure integration surface for `alramz-notification-service` (Service Bus, Blob, Key Vault, managed-identity auth) | `services/alramz-notification-service/pom.xml` |
| Microsoft Graph SDK (legacy) | 3.8.0 | Sends email via MS Graph as `alramz-notification-service`'s email provider | `services/alramz-notification-service/pom.xml`; `com/alramz/config/GraphConfig.java` |
| Spring Data Redis (Lettuce) + `azure-identity` | via Spring Boot BOM / 1.15.0 | `reference-data-service`'s cache layer, supports both local Redis and Azure Managed Redis via AAD token auth | `services/reference-data-service/pom.xml`; `com/alramz/config/RedisConfig.java` |
| PostgreSQL JDBC driver / Oracle `ojdbc11` | via Spring Boot BOM | Runtime drivers for the `middleware` (Postgres) and `brok` (Oracle) named datasources | `services/data-validation-service/pom.xml`, `services/reference-data-service/pom.xml` |
| H2 | 2.2.224 | In-memory DB for local dev/tests (see STACK caveat below — used as `runtime`, not only `test`, in `alramz-notification-service`) | `alramz-common-bom/pom.xml`; `services/alramz-notification-service/pom.xml` |

### 3) Development Toolchain

| Tool | Purpose | Evidence |
|------|---------|----------|
| Checkstyle 10.21.0 (maven-checkstyle-plugin 3.5.0) | Lint — **non-blocking** (`failOnViolation=false`) | `alramz-common-bom/pom.xml`; rules in `config/checkstyle.xml` (37 `<module>` rules) |
| PMD 7.10.0 (maven-pmd-plugin 3.24.0) | Static analysis — parent default is `<skip>true</skip>`, but `data-validation-service`, `alramz-notification-service`, `reference-data-service` each override to `<skip>false</skip>` in their own `pom.xml`; `alramz-api-starter` keeps it skipped | `alramz-common-bom/pom.xml`; per-service `<plugin>` overrides; rules in `config/pmd.xml` (5 rule refs) |
| SpotBugs 4.8.6 (spotbugs-maven-plugin) | Static analysis — same per-module skip pattern as PMD (`alramz-api-starter` skips it; the 3 runnable services enable it) | same files as above; excludes in `config/spotbugs-exclude.xml` |
| JaCoCo 0.8.12 | Coverage instrumentation + `report-aggregate` at the reactor root | root `pom.xml`; `alramz-common-bom/pom.xml` |
| Maven Enforcer 3.6.1 | Enforces Java 21 + Maven 3.9.0 at `verify` | `alramz-common-bom/pom.xml` |
| JUnit 5 / Mockito / AssertJ | Unit and integration testing | `alramz-notification-service/pom.xml` (explicit versions); Spring Boot Test starters elsewhere |
| OpenAPI Generator Maven Plugin | Generates controller interfaces/models from YAML specs during `generate-sources` | per-service `pom.xml` `<plugin>org.openapitools:openapi-generator-maven-plugin</plugin>` |

### 4) Key Commands

```bash
mvn clean package
mvn -pl services/data-validation-service -am clean test
mvn -pl services/<service> -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false
mvn -pl services/<service> -am verify -Dcheckstyle.consoleOutput=true -Dpmd.consoleOutput=true -Dspotbugs.consoleOutput=true -Djacoco.skip=false
```

### 5) Environment and Config

- Config sources: `application.yml` + `application-{dev,docker,test,preprod,prod}.yml` per service (`services/<service>/src/main/resources/`), selected via `SPRING_PROFILES_ACTIVE`.
- Required env vars (from `release/.env.example` and `application-dev.yml`): `IBAN_API_KEY`, `VERIPHONE_API_KEY`, `ETRADE_BASE_URL`, `ETRADE_CLIENT_ID`, `ETRADE_CLIENT_SECRET`, `JWT_SECRET`, `CIPHER_PASSWORD`, `AZURE_TENANT_ID`, `AZURE_CLIENT_ID`, `AZURE_CLIENT_SECRET`, `COMPANY_DATASOURCE_MIDDLEWARE_URL`/`_USERNAME`/`_PLAINPASSWORD`, `REDIS_PORT`, `POSTGRES_PASSWORD`. Evidence: `release/.env.example`, `services/data-validation-service/src/main/resources/application-dev.yml`.
- Deployment/runtime constraints: each service builds a plain `eclipse-temurin:21-jdk` Docker image (no multi-stage build in the currently committed `services/<service>/Dockerfile` files — see CONCERNS.md); Kubernetes sizing lives in `k8s/apps/base/<service>/deployment.yaml`, **not** in a `services/<service>/service.yaml` as CLAUDE.md's architecture table states (see ARCHITECTURE.md §5 and the Intent-vs-Reality note in the final summary).

### 6) Evidence

- `pom.xml` (root reactor), `services/alramz-common-bom/pom.xml`, `services/alramz-api-starter/pom.xml`
- `services/data-validation-service/pom.xml`, `services/alramz-notification-service/pom.xml`, `services/reference-data-service/pom.xml`
- `services/data-validation-service/Dockerfile`, `services/alramz-notification-service/Dockerfile`, `services/reference-data-service/Dockerfile`
- `release/.env.example`

## Extended Sections (Optional)

Not populated — the Core Sections above cover this repo's complexity without needing a separate dependency taxonomy or environment matrix table.
