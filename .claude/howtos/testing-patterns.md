# Testing Patterns

How this repo actually writes tests, grounded in the real test suites of `data-validation-service` (rich, mature suite) and `reference-data-service` (new scaffold, minimal suite so far). Use this to add tests consistently to any service in `services/`.

## 1. What this pattern is / when to use it

This repo uses three test layers, but leans almost entirely on the first two today:

| Layer | What it tests | How it's done here | When to use it |
|---|---|---|---|
| **Unit tests (service/helper layer)** | Business logic in isolation | JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`, manual constructor wiring) — **no Spring context** | Default choice for any `service`/`service.impl`/helper class. This is the dominant pattern in `data-validation-service`. |
| **Controller unit tests** | Request/response mapping and delegation to services | Same Mockito style — the controller is `new`'d directly with mocked service dependencies, **not** `@WebMvcTest`/`MockMvc` | Any REST controller. See §2. |
| **Application context smoke test** | The Spring context wires up (beans, auto-config, profiles) | `@SpringBootTest` with `spring.profiles.active=test`, single `contextLoads()` test | One per service, already scaffolded — don't delete it. |

Notably **absent** in the current codebase: no `@WebMvcTest`, no `@DataJpaTest`, no `MockMvc`, no `TestEntityManager` in actual test classes, even though `reference-data-service`'s `pom.xml` already pulls in `spring-boot-starter-webmvc-test` and `spring-boot-starter-data-jpa-test` as test-scope dependencies (see §3) — these are available if a future service needs true web-layer or repository-layer tests, but don't invent usage of them just to look thorough; follow the Mockito-first convention already established unless you have a concrete reason (e.g. testing a custom `@ExceptionHandler` wired through Spring's actual dispatch, or a non-trivial JPQL/native query).

## 2. Where it lives in this repo

`data-validation-service` (`services/data-validation-service/src/test/java/com/alramz/`) is the reference suite:

- **Controller test**: `controllers/DataValidationControllerTest.java` — constructs the controller directly with mocked service fields, stubs the mock, asserts on `ResponseEntity`.
- **Service test**: `service/impl/ValidationServiceImplTest.java` — the richest example: constructor injection of mocks, a `@BeforeEach` building real config objects (`ETradeProperties`, `ValidationDefinitionRegistry`) rather than mocking them, exception-path assertions with AssertJ's `assertThatThrownBy(...).hasFieldOrPropertyWithValue(...)`, and retry-logic assertions with `verify(mock, times(n))`.
- **Exception handler test**: `exception/GlobalExceptionHandlerTest.java` and `exception/OnboardingExceptionHandlerTest.java` — the handler is instantiated as a plain object (`new GlobalExceptionHandler()`), `HttpServletRequest` is a bare Mockito `mock(...)` (not `@Mock`, since the class has no `@ExtendWith(MockitoExtension.class)`), and assertions check the `GenericResponse`/`OnboardingResponse` body fields directly.
- **Scheduler job test**: `scheduler/job/DataValidationScheduledJobTest.java` — mocks the one collaborator (`SqlQueriesManager`), runs the job synchronously, asserts on the mutated bean.
- **Config/registry test**: `config/ValidationDefinitionRegistryTest.java` — tests a config-derived registry built from `@ConfigurationProperties` objects constructed by hand in the test.
- **Application smoke test**: `DataValidationServiceApplicationTests.java`.

`reference-data-service` (`services/reference-data-service/src/test/java/com/alramz/reference_data_service/`) currently only has:
- `ReferenceDataServiceApplicationTests.java` — the smoke test, and the canonical form to copy for a brand-new service:

```java
package com.alramz.reference_data_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.profiles.active=test")
class ReferenceDataServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Note the profile is activated via `@SpringBootTest(properties = "spring.profiles.active=test")`, **not** `@ActiveProfiles("test")` — match this convention for consistency (both achieve the same result; this repo already picked one).

## 3. How it works

**Test profile & H2.** Every service's `src/test/resources/application.yml` runs Liquibase/JPA against an in-memory H2 database, e.g. (`data-validation-service`):

```yaml
spring:
  application:
    name: data-validation-service
  liquibase:
    change-log: classpath:db/changelog/test/db.changelog-master.yaml

company:
  datasource:
    middleware:
      enabled: true
      url: jdbc:h2:mem:testdb
      driverClassName: org.h2.Driver
      username: sa
      password:
      plainPassword: sa
      startup-validation:
        enabled: false
```

and `reference-data-service` (plain Spring `spring.datasource.*` + JPA, no multi-datasource starter properties, since it doesn't need named pools):

```yaml
spring:
  application:
    name: reference-data-service
  liquibase:
    change-log: classpath:db/changelog/test/db.changelog-master.yaml
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: validate
    database-platform: org.hibernate.dialect.H2Dialect
```

Both set `cipher.password` to a throwaway 16-char dev value so `PWProtector` auto-config doesn't fail startup, and both use `plainPassword`/no-password against H2 — this is **test-only**; never copy this into a `dev`/`preprod`/`prod` profile (CLAUDE.md §4: don't hardcode datasource passwords in non-dev profiles).

**Test-only Liquibase changesets.** The `test` profile's changelog master lives at `src/test/resources/db/changelog/test/db.changelog-master.yaml` (test resources, *not* under `src/main/resources/db/changelog/test/` — that `main` path also exists per-service for env-specific *runtime* changesets like `dev`/`docker`/`preprod`/`prod`, but `test` under `src/test/resources` is the one actually picked up when the `test` profile runs, since test resources are appended to the classpath ahead of main for the test source set). It's a thin include:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/test/sql
```

`data-validation-service`'s test SQL folder (`src/test/resources/db/changelog/test/sql/`) mirrors the same numbered-file Liquibase convention as production changesets (`001-schedule-job.sql`, `002-jwt-tables.sql`, ... `006-insert-global-configuration-settings.sql`) — same rules as the production changelog apply here: one file per change, `NNN-short-description.sql`, `--liquibase formatted sql` header, never edit an applied one (see the Liquibase howto).

**Mocking convention.** Class-level `@ExtendWith(MockitoExtension.class)` + field-level `@Mock`, manual `new TargetClass(mock1, mock2, ...)` construction (constructor injection, no `@InjectMocks` in the examples read) either inline per test or in `@BeforeEach`. Assertions are AssertJ (`assertThat`, `assertThatThrownBy`), not Hamcrest or raw JUnit asserts.

## 4. How to add tests for a new feature in a new/existing service

### Controller test skeleton (copy `DataValidationControllerTest.java`'s shape)

```java
package com.alramz.<feature>.controller;

import com.alramz.<feature>.model.YourRequest;
import com.alramz.<feature>.model.YourResponse;
import com.alramz.<feature>.service.YourService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YourControllerTest {

    @Mock
    private YourService yourService;

    @Test
    void yourEndpoint_shouldReturnOkResponse() {
        YourController controller = new YourController(yourService);

        YourRequest request = new YourRequest();
        YourResponse expected = new YourResponse();

        when(yourService.handle(any(YourRequest.class))).thenReturn(expected);

        ResponseEntity<YourResponse> response = controller.yourEndpoint(request);

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(expected);
        verify(yourService).handle(request);
    }
}
```

### Service test skeleton (copy `ValidationServiceImplTest.java`'s shape)

```java
package com.alramz.<feature>.service.impl;

import com.alramz.<feature>.exception.ApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YourServiceImplTest {

    @Mock
    private SomeCollaborator collaborator;

    private YourServiceImpl service;

    @BeforeEach
    void setUp() {
        // build real, cheap config/value objects here (not mocks) when the
        // class under test just reads immutable properties, per ValidationServiceImplTest
        service = new YourServiceImpl(collaborator);
    }

    @Test
    void handle_shouldThrowApplicationExceptionWhenRequiredFieldMissing() {
        var request = new YourRequest();

        assertThatThrownBy(() -> service.handle(request))
                .isInstanceOf(ApplicationException.class)
                .hasFieldOrPropertyWithValue("field", "expectedFieldName");
    }

    @Test
    void handle_shouldReturnSuccessResponse() {
        when(collaborator.doSomething(anyString())).thenReturn("ok");

        var response = service.handle(new YourRequest());

        assertThat(response).isNotNull();
    }
}
```

### Exception handler test skeleton (copy `OnboardingExceptionHandlerTest.java`'s shape — only if you add a new typed exception + handler method)

```java
package com.alramz.<feature>.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class YourExceptionHandlerTest {

    private final YourExceptionHandler handler = new YourExceptionHandler();

    @Test
    void handleYourException_shouldReturnBadRequest() {
        YourException ex = new YourException("field", "message", "ERR001");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Correlation-Id")).thenReturn(null);
        when(request.getParameter("correlationId")).thenReturn(null);

        ResponseEntity<YourResponse> response = handler.handleYourException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getInternalErrorCode()).isEqualTo("ERR001");
    }
}
```

### Repository/JPA-layer test

No example exists in this codebase today — neither service has a `@DataJpaTest`. If a new feature genuinely needs to verify a custom repository query (not just CRUD from Spring Data), `reference-data-service`'s `pom.xml` already has `spring-boot-starter-data-jpa-test` on the test classpath, so `@DataJpaTest` + H2 (same `jdbc:h2:mem:testdb` already configured) is the natural fit — but treat it as a new addition to this repo's conventions, not an existing pattern to blindly imitate, and prefer testing the query through the service layer with a mocked repository (as every existing test in this repo does) unless the query logic itself is complex enough to need real DB round-tripping.

## 5. Common pitfalls / anti-patterns

- **Don't reach for `@WebMvcTest`/`MockMvc`** for controller tests in this repo — every existing controller test constructs the controller directly and calls the method. Introducing `MockMvc` here would be an inconsistent, heavier pattern with no precedent; only do it if you specifically need to test filter-chain/serialization behavior that a direct call can't exercise.
- **Don't mock immutable config/value objects** (like `*Properties` classes) if you can cheaply construct a real instance in `@BeforeEach` — `ValidationServiceImplTest` builds a real `ETradeProperties` rather than mocking it, keeping the test closer to real behavior.
- **Match the profile-activation style already used**: `@SpringBootTest(properties = "spring.profiles.active=test")`, not `@ActiveProfiles("test")` — both work, but consistency matters more than which one.
- **Don't hand-roll `HttpServletRequest` stubbing per test** beyond what's needed — the existing handler tests only stub `getRequestURI()`/`getHeader("X-Correlation-Id")`/`getParameter("correlationId")` because that's all `GlobalExceptionHandler` actually reads; stub only what your handler under test consumes.
- **`@Disabled` is used deliberately** in `AopLoggingIntegrationTest` with a reason string explaining why (full context not verified) — if you must skip a test, follow that pattern: `@Disabled("reason")`, not a silently commented-out test.
- **Never let a `test` profile's datasource config leak into a real profile** — the H2 `plainPassword: sa` / blank `password` setup is disqualifying outside `test` per CLAUDE.md's `cipher.password` rule.
- **Always run the actual verification command before calling a change done** (CLAUDE.md §0/§2/§5): at minimum
  ```
  mvn -pl services/<service> -am clean test-compile
  ```
  and for any behavioral change,
  ```
  mvn -pl services/<service> -am clean test
  ```
  For fast iteration on one class/method while writing tests:
  ```
  mvn -pl services/<service> -am test -Dtest=YourServiceImplTest
  mvn -pl services/<service> -am test -Dtest=YourServiceImplTest#handle_shouldReturnSuccessResponse
  ```

## 6. Checklist

- [ ] New/changed controller method has a Mockito-style test constructing the controller directly (§4).
- [ ] New/changed service method has a test covering: each required-field validation failure (`assertThatThrownBy` + `hasFieldOrPropertyWithValue`), the external-call failure path (if any), and the success path.
- [ ] New typed exception has a corresponding `*ExceptionHandler` test asserting HTTP status + response body fields.
- [ ] Any new scheduled job has a test mocking its collaborators and asserting on the outcome, like `DataValidationScheduledJobTest`.
- [ ] Any new Liquibase changeset needed only for tests is added under `src/test/resources/db/changelog/test/sql/NNN-*.sql`, following the same numbering/rollback conventions as production changesets — never edit an already-applied one.
- [ ] `application.yml` under `src/test/resources` still points `spring.liquibase.change-log` at the test master and keeps datasource config H2-only.
- [ ] `mvn -pl services/<service> -am clean test-compile` passes.
- [ ] `mvn -pl services/<service> -am clean test` passes for behavioral changes.
