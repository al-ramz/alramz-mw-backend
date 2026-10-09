# Testing Patterns

## Core Sections (Required)

### 1) Test Stack and Commands

- Primary test framework: JUnit 5 (`junit-jupiter`), driven by `maven-surefire-plugin` (version pinned to 3.2.5 in `alramz-notification-service`, inherited default elsewhere).
- Assertion/mocking tools: Mockito 5.12.0 + AssertJ 3.27.7 (explicit versions in `alramz-notification-service/pom.xml`); Spring Boot Test starters/slices (`spring-boot-starter-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-webmvc-test`, `spring-boot-starter-actuator-test`) elsewhere.
- Commands:

```bash
mvn -pl services/<service> -am clean test
mvn -pl services/<service> -am test -Dtest=DataValidationControllerTest
mvn -pl services/<service> -am test -Dtest=DataValidationControllerTest#shouldValidateIban
mvn -pl services/<service> -am verify -Djacoco.skip=false
```

### 2) Test Layout

- Test file placement pattern: co-located under `services/<module>/src/test/java/...`, mirroring the main package structure.
- Naming convention: `*Test.java` for unit/slice tests, `*IT.java` for integration-style tests requiring a real context (e.g. `JdbcGlobalConfigurationSettingsRepositoryIT.java` vs. its sibling `JdbcGlobalConfigurationSettingsRepositoryTest.java`).
- Setup files and where they run: `services/data-validation-service/src/test/resources/application.yml` and `services/reference-data-service/src/test/resources/application.yml` provide test-profile overrides; `@SpringBootTest(properties = "spring.profiles.active=test")` is used directly in `ReferenceDataServiceApplicationTests`.

### 3) Test Scope Matrix

| Scope | Covered? | Typical target | Notes |
|-------|----------|-----------------|-------|
| Unit | Yes, unevenly | `alramz-api-starter` (22 test files: JWT, datasource, logging, masking) and `data-validation-service` (16 test files: controllers, services, mappers, scheduler job, exception handlers) are well covered | `find services/*/src/test -name '*.java'` counts |
| Integration | Partial | `alramz-api-starter`'s `Datasource*Test`/`DatasourceCombinationMatrixTest`/`DatasourceProxyIntegrationTest` and `JdbcGlobalConfigurationSettingsRepositoryIT` exercise real datasource wiring | `services/alramz-api-starter/src/test/java/com/alramz/datasource/*` |
| E2E | Separate, outside Maven | `e2e/alramz-onbaording-apis-uie.postman_collection.json` + `e2e/local.postman_environment.json` — a Postman collection, not run by `mvn test` or CI as far as the scanned workflows show | `e2e/*` (new/untracked at scan time per git status) |
| Service-level (`alramz-notification-service`) | **No** — only 1 test file, a placeholder `AppTest.shouldAnswerWithTrue()` (`assertTrue(true)`) despite declaring Mockito/AssertJ as test dependencies | `services/alramz-notification-service/src/test/java/com/alramz/AppTest.java` |
| Service-level (`reference-data-service`) | **No** — only 1 test file, a bare `@SpringBootTest` context-load smoke test with an empty body; `RedisConfig`, `RedisCacheService`, `CacheReloadScheduledJob`, `SchedulerLockService` have zero test coverage | `services/reference-data-service/src/test/java/com/alramz/reference_data_service/ReferenceDataServiceApplicationTests.java` |

### 4) Mocking and Isolation Strategy

- Main mocking approach: Mockito for unit-level collaborator mocking (declared in `alramz-notification-service/pom.xml`, though not yet exercised by real test cases there); Spring Boot Test slices (`@WebMvcTest`-style via `*-webmvc-test` starter, `@DataJpaTest`-style via `*-data-jpa-test`) for layer-scoped tests elsewhere.
- Isolation guarantees: H2 in-memory DB backs JPA/Liquibase-dependent tests (`test` scope in `data-validation-service`/`reference-data-service` `pom.xml`); each test class gets a fresh Spring context per the standard Spring Test caching rules — no custom test-container/reset framework observed.
- Common failure mode in tests: `[TODO]` — no flaky-test log or CI history was inspected as part of this pass.

### 5) Coverage and Quality Signals

- Coverage tool + threshold: JaCoCo 0.8.12, configured with `<minimum>0.00</minimum>` for LINE/BRANCH/INSTRUCTION in the `check` execution — the `jacoco.line.coverage`/`jacoco.branch.coverage`/`jacoco.instruction.coverage` properties (0.80/0.70/0.75) declared in `alramz-common-bom/pom.xml` are **not** wired into that `<limit>` block, so no real minimum is enforced (CLAUDE.md §2 caveat, confirmed by reading the plugin config directly).
- Current reported coverage: `[TODO]` — requires running `mvn verify` with `-Djacoco.skip=false` and reading the generated report; not executed as part of this documentation pass (CLAUDE.md's verification rule applies to code changes, not to a docs-only task).
- Known gaps/flaky areas: `alramz-notification-service` (email sending, MS Graph auth) and `reference-data-service` (Redis cache, scheduled cache reload, distributed locking) are the two runnable services with effectively no automated test coverage — see CONCERNS.md.

### 6) Evidence

- `services/alramz-notification-service/src/test/java/com/alramz/AppTest.java`
- `services/reference-data-service/src/test/java/com/alramz/reference_data_service/ReferenceDataServiceApplicationTests.java`
- `services/alramz-api-starter/src/test/java/com/alramz/datasource/*` (test file listing)
- `services/alramz-common-bom/pom.xml` (JaCoCo `check` execution block)
- `e2e/alramz-onbaording-apis-uie.postman_collection.json`

## Extended Sections (Optional)

Not populated.
