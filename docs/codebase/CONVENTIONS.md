# Coding Conventions

## Core Sections (Required)

### 1) Naming Rules

| Item | Rule | Example | Evidence |
|------|------|---------|----------|
| Files | PascalCase Java class per file, matching the public type | `DataValidationController.java`, `JwtAutoConfiguration.java`, `SensitiveDataMasker.java` | package listings under `services/*/src/main/java/com/alramz/**` |
| Packages | lowercase, package-by-feature (`com.alramz.<feature>.{config,controller(s),service,repository,model,exception}`) | `com.alramz.jwt.config`, `com.alramz.scheduler.service.impl` | CLAUDE.md §4; `services/alramz-api-starter/src/main/java/com/alramz/` listing |
| Functions/methods | camelCase, verb-first (`validateIBAN`, `tryAcquireLock`, `resolveAzureAccessToken`) | `services/data-validation-service/src/main/java/com/alramz/controllers/DataValidationController.java` | as above |
| Types/config classes | `@ConfigurationProperties` classes are Lombok `@Getter @Setter` with a `public static final String PREFIX` constant | `JwtProperties.PREFIX = "company.jwt"` | `services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtProperties.java` |
| Constants/env vars | `SCREAMING_SNAKE_CASE` for env var placeholders in YAML (`${JWT_SECRET:...}`, `${COMPANY_DATASOURCE_MIDDLEWARE_URL:...}`) | `services/data-validation-service/src/main/resources/application-dev.yml` | as above |
| Liquibase changesets | `NNN-short-description.sql`, zero-padded, one file per change | `001-schedule-job.sql` … `006-insert-global-configuration-settings.sql` | `services/data-validation-service/src/main/resources/db/changelog/dev/sql/` |

### 2) Formatting and Linting

- Formatter: none configured (no `.editorconfig`/Spotless/Prettier found in the scan).
- Linter: Checkstyle 10.21.0 via `config/checkstyle.xml` (37 `<module>` rules — import hygiene, naming, cyclomatic/NPath/boolean-expression complexity, method length, parameter count) — **console-only, never fails the build** (`checkstyle.failOnViolation=false` in `alramz-common-bom/pom.xml`, confirmed by CLAUDE.md §2 caveat).
- PMD (`config/pmd.xml`, 5 rule refs) and SpotBugs (`config/spotbugs-exclude.xml`) are wired the same way, but **skip differs per module**: `alramz-api-starter` keeps both `<skip>true</skip>`; the 3 runnable services (`data-validation-service`, `alramz-notification-service`, `reference-data-service`) each override to `<skip>false</skip>` in their own `pom.xml`, so PMD/SpotBugs *do* run for those three (still non-blocking, `failOnViolation`/`failOnError=false`).
- Run commands: `mvn -pl services/<service> -am verify -Dcheckstyle.consoleOutput=true -Dpmd.consoleOutput=true -Dspotbugs.consoleOutput=true -Djacoco.skip=false` (CLAUDE.md §2).

### 3) Import and Module Conventions

- Import grouping/order: not enforced by tooling beyond Checkstyle's `AvoidStarImport`/`RedundantImport`/`UnusedImports` modules; no import-order module configured (`config/checkstyle.xml`).
- Alias vs relative import policy: N/A — plain Java package imports; Maven reactor coordinates (`groupId:artifactId`) resolve cross-module dependencies (e.g. every service depends on `com.alramz:alramz-api-starter:1.0-SNAPSHOT`).
- Public exports/barrel policy: N/A (Java, not JS/TS) — the starter's public surface is whatever is `public` plus what's registered in `AutoConfiguration.imports`; anything not registered there is dead code from a consuming service's point of view (CLAUDE.md §3/§4).

### 4) Error and Logging Conventions

- Error strategy by layer: each service defines typed exceptions under its own `exception` package (e.g. `ApplicationException`, `ExternalSystemException`, `TechnicalException`, `IbanValidationException` in `data-validation-service`) and a single `@RestControllerAdvice GlobalExceptionHandler` per service that maps each exception type to a `ResponseEntity`. `data-validation-service` additionally branches its *shape* of error response by request path (`/api/v1/existing-data/validation` → `GenericResponse`, `/api/v1/dfm/onboarding` → `OnboardingResponse` with a `memberReferenceNumber`/`internalErrorCode`) inside the same handler methods rather than via two separate advices (`services/data-validation-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java`).
- Logging style and required context fields: Lombok `@Slf4j` (or explicit `LoggerFactory.getLogger`) everywhere — no hand-written loggers per CLAUDE.md §4. The starter's `LoggingAutoConfiguration` wires request/response filters, a correlation-ID filter/response-header, and optional JSON log encoding (`AlramzJsonEncoder`) plus a Seq appender (`SeqAppender`), all gated by `company.logging.*` properties. Correlation IDs are read from `X-Correlation-Id`, falling back to a `correlationId` query param, falling back to a fresh `UUID` (duplicated logic in both `DataValidationController` and `GlobalExceptionHandler` — see CONCERNS.md).
- Sensitive-data redaction rules: `SensitiveDataMasker` (starter) hardcodes a lowercase key set (`client_secret`, `api_key`, `access_token`, `authorization`, `password`, `cookie`, EID/passport/mobile fields, etc.) and masks matching values to `***`, truncates long base64 image payloads, and is applied to audit-logged request/response bodies and headers. CLAUDE.md §3/§4 direct extending this key set rather than hand-rolling masking elsewhere — **note**: `GraphConfig.graphServiceClient()` in `alramz-notification-service` logs a raw Azure AD access token at `INFO` via `LOG.info("Access Token: {}", accessToken.getToken())`, bypassing this masking entirely (see CONCERNS.md — this is outside the audit-log path the masker covers).

### 5) Testing Conventions

- Test file naming/location rule: co-located under `src/test/java/...`, mirroring the main package, suffixed `Test` (unit) or `IT`/integration style names for Spring-context tests (e.g. `JdbcGlobalConfigurationSettingsRepositoryIT.java` vs. `...RepositoryTest.java`).
- Mocking strategy norm: Mockito (`mockito-core`, `mockito-junit-jupiter`) + AssertJ in `alramz-notification-service`; Spring Boot Test slices (`spring-boot-starter-*-test` artifacts, e.g. `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`) in `data-validation-service`/`reference-data-service`.
- Coverage expectation: `[TODO]` — JaCoCo instruments and reports, but the enforced `check` minimums are hardcoded to `0.00` regardless of the `jacoco.line.coverage`/`jacoco.branch.coverage`/`jacoco.instruction.coverage` properties defined in `alramz-common-bom/pom.xml` (those properties are set to 0.80/0.70/0.75 but never referenced by the actual `<limit>` blocks — see CONCERNS.md). No real coverage threshold is enforced anywhere in the reactor.

### 6) Evidence

- `config/checkstyle.xml`, `config/pmd.xml`, `config/spotbugs-exclude.xml`
- `services/alramz-common-bom/pom.xml` (plugin config + unused coverage properties)
- `services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java`
- `services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java`
- `services/data-validation-service/src/main/resources/db/changelog/{dev,docker,preprod,prod}/sql/*.sql` (identical duplicated files — diff-verified for `001-schedule-job.sql`)

## Extended Sections (Optional)

### Known convention violations to clean up

- CLAUDE.md documents a Liquibase layout with a **shared** `db/changelog/sql/` folder included by every profile master plus per-profile-only additions. That shared folder does not exist: `data-validation-service` instead has byte-identical `sql/001-schedule-job.sql` … `006-*.sql` duplicated under `dev/`, `docker/`, `preprod/`, and `prod/` — a future schema change must be hand-applied to 4+ folders or it will silently diverge between environments.
