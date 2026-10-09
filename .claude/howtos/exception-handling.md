# Exception Handling

## 1. What this pattern is / when to use it

Every failure mode a service can hit — bad input, a downstream system being down, an internal bug — should be represented as a **typed exception**, never thrown or caught as a bare `RuntimeException`. Typed exceptions are then translated into structured HTTP responses by one or more centralized `@RestControllerAdvice` classes, so controllers stay free of `try/catch` blocks and response-shaping logic.

There are two layers in this repo:

1. **Starter-provided safety net** (`alramz-api-starter`) — a last-resort handler that every service gets automatically, with zero code.
2. **Service-owned typed exceptions + handler(s)** — the domain-specific layer you write per service.

Use this pattern whenever a controller or service method can fail in a way the caller needs to see as a specific HTTP status / error code, not just a generic 500.

## 2. Where it lives in this repo

**Starter (foundation layer, applies to every service automatically):**
- `services/alramz-api-starter/src/main/java/com/alramz/exceptions/ApiCallFailedException.java` — thrown by the starter's own HTTP client helpers when a downstream call fails.
- `services/alramz-api-starter/src/main/java/com/alramz/exceptions/InvalidHttpRequestException.java` — generic "this request was malformed" exception, reusable by any service.
- `services/alramz-api-starter/src/main/java/com/alramz/logging/exception/LoggingExceptionHandler.java` — a `@RestControllerAdvice` registered at `@Order(Ordered.LOWEST_PRECEDENCE)`, auto-configured via `com.alramz.logging.config.LoggingAutoConfiguration` (listed in `AutoConfiguration.imports`), gated by `company.logging.exception.enabled` (default `true`, see `LoggingExceptionHandler.java:33`).

**`data-validation-service` (the only service that currently implements this layer — see the note in §5):**
- `services/data-validation-service/src/main/java/com/alramz/exception/ApplicationException.java`
- `services/data-validation-service/src/main/java/com/alramz/exception/ValidationException.java`
- `services/data-validation-service/src/main/java/com/alramz/exception/TechnicalException.java`
- `services/data-validation-service/src/main/java/com/alramz/exception/ExternalSystemException.java`
- `services/data-validation-service/src/main/java/com/alramz/exception/IbanValidationException.java`
- `services/data-validation-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java` — service-wide `@RestControllerAdvice`.
- `services/data-validation-service/src/main/java/com/alramz/exception/OnboardingExceptionHandler.java` — a second, narrower `@RestControllerAdvice` (see the pitfall in §5 before copying this one).

**`reference-data-service`:** has **no** `exception` package, no typed exceptions, and no `GlobalExceptionHandler` at all today. If you scaffold from it, you get only the starter's last-resort handler — see §4 for what to add.

## 3. How it works

1. A controller or service method throws a typed exception (e.g. `new ValidationException("cust_email", "must not be blank")`).
2. Spring's `ExceptionHandlerExceptionResolver` walks all applicable `@ControllerAdvice` beans **in ascending `@Order`**, and uses the *first* bean whose `@ExceptionHandler` methods can handle that exception type (matching the type or a superclass).
3. In `data-validation-service`, `GlobalExceptionHandler` is `@RestControllerAdvice(basePackages = "com.alramz.controllers")` at `@Order(0)` — it applies to **every** controller in the service, including `OnboardingController`. Inside each handler method it branches on the request path (`isValidationRequest` / `isOnboardingRequest`) to shape the response as a `GenericResponse` (generic validation API) or an `OnboardingResponse` (onboarding API) — both are OpenAPI-generated model classes (`target/generated-sources/openapi/.../model/{GenericResponse,OnboardingResponse}.java`), not hand-written DTOs. See [openapi-contract-first-controllers.md](openapi-contract-first-controllers.md) for how those models get generated.
4. If nothing service-specific matches, the request eventually reaches the starter's `LoggingExceptionHandler` (`@Order(LOWEST_PRECEDENCE)`), which logs the uncaught exception once with the correlation id and returns a generic `{timestamp, status, error, message, correlationId}` body. This is your safety net — it fires even if you write zero exception-handling code in a new service.
5. Typed exceptions carry just enough structured data for the handler to build a response — e.g. `ApplicationException`/`ValidationException` carry a `field` and an optional `serviceErrorResponseCode`; `TechnicalException`/`IbanValidationException` carry an `errorCode`/`code`. They do **not** carry HTTP status directly — the handler decides the status per exception type.

## 4. How to add exception handling to a new service

### Step 1 — create the `exception` package

```
src/main/java/com/alramz/exception/
```

(Package-by-feature convention: if your service has multiple distinct feature areas with different error shapes, you can nest under a feature package instead, e.g. `com.alramz.<feature>.exception` — `data-validation-service` keeps everything in one flat `com.alramz.exception` package because it only has one response shape family.)

### Step 2 — write typed exceptions for your real failure modes

Follow the exact shape used in `data-validation-service` — plain `RuntimeException` subclasses, no shared base class, with just the fields the handler needs:

```java
package com.alramz.exception;

public class ValidationException extends RuntimeException {

    private final String field;
    private final String serviceErrorResponseCode;

    public ValidationException(String field, String message) {
        this(field, message, null);
    }

    public ValidationException(String field, String message, String serviceErrorResponseCode) {
        super(message);
        this.field = field;
        this.serviceErrorResponseCode = serviceErrorResponseCode;
    }

    public ValidationException(String message) {
        this(null, message, null);
    }

    public String getField() { return field; }
    public String getServiceErrorResponseCode() { return serviceErrorResponseCode; }
}
```

For downstream/integration failures, follow `ExternalSystemException` (simple message-only) or `TechnicalException` (message + error code, defaulting the code so callers don't have to supply one every time). Reuse the starter's `ApiCallFailedException`/`InvalidHttpRequestException` for HTTP-client-level failures instead of redefining them.

Add one exception class per **distinct failure mode**, not one per endpoint. `data-validation-service` needed 5 for validation + onboarding; a simpler service may only need 2–3 (e.g. `ValidationException`, `ExternalSystemException`).

### Step 3 — write one `GlobalExceptionHandler`

```java
package com.alramz.exception;

import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.alramz.controllers")
@Order(0)
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Object> handleValidation(ValidationException ex) {
        // build your service's error response shape (an OpenAPI-generated
        // model, per the contract-first pattern) and return the right status
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(/* ... */);
    }

    @ExceptionHandler(ExternalSystemException.class)
    public ResponseEntity<Object> handleExternalSystem(ExternalSystemException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(/* ... */);
    }

    // one @ExceptionHandler method per typed exception you defined in Step 2,
    // plus MethodArgumentNotValidException / ConstraintViolationException for
    // @Valid request-body failures, plus a catch-all Exception handler
}
```

Rules of thumb, taken directly from `data-validation-service`'s `GlobalExceptionHandler`:
- Scope it with `basePackages = "com.alramz.controllers"` (or your controller package) so it doesn't leak onto other services' advice beans in tests.
- Give it `@Order(0)` — an explicit, low order number — so its precedence relative to any second handler (Step 4) and the starter's `LoggingExceptionHandler` (`LOWEST_PRECEDENCE`) is unambiguous.
- Map each exception to the HTTP status that reflects the failure: `400` for bad input/validation, `503` for downstream/technical failures, `500`/generic for the catch-all.
- Always include a correlation id in the response body if your API contract has a field for it — pull it from the `X-Correlation-Id` header (see `extractCorrelationId` in `GlobalExceptionHandler.java:393-402`) so callers can trace a failure end-to-end.
- Always keep a `@ExceptionHandler(Exception.class)` catch-all in your own handler — don't rely solely on the starter's generic handler if your API contract requires a specific response body shape (the starter's shape is a plain `{timestamp, status, error, message, correlationId}` map, which won't match a typed OpenAPI response model).

### Step 4 — only add a second, narrower handler if you have a genuinely different response contract

`data-validation-service` has two APIs with two different response DTOs (`GenericResponse` for `/existing-data/validation`, `OnboardingResponse` for `/dfm/onboarding`). Its actual solution was to branch on request path *inside* `GlobalExceptionHandler` (see `isValidationRequest`/`isOnboardingRequest`, `GlobalExceptionHandler.java:383-391`) — **not** the second `OnboardingExceptionHandler` class, which is dead weight (see §5). If you have multiple response contracts in one service, prefer the path-branching approach that already works, or split into two `@RestControllerAdvice` beans scoped by `assignableTypes = SomeController.class` **and give the narrower one the lower `@Order` value** so it actually wins.

### Step 5 — verify wiring

- Confirm `company.logging.exception.enabled` isn't set to `false` in your `application*.yml` (it defaults to `true`, giving you the starter's safety net).
- Write a controller test that triggers each typed exception and asserts the HTTP status + body shape.

## 5. Common pitfalls / anti-patterns

- **Never throw or catch a bare `RuntimeException`.** Per CLAUDE.md §4: "add new failure modes as typed exceptions, not generic `RuntimeException`." A bare `RuntimeException` can't be given a distinct HTTP status or a stable error code, and it risks being swallowed by an unrelated catch-all handler.
- **`reference-data-service` has zero exception handling today** — no `exception` package, no `GlobalExceptionHandler`. If you scaffold a new service from it, you inherit *only* the starter's generic `LoggingExceptionHandler` safety net. Do not assume "no errors happen" — add the layer described in §4 as soon as the service has real failure modes (invalid input, downstream calls, etc.). `data-validation-service` is the pattern to copy, not `reference-data-service`.
- **Verified dead code in `data-validation-service`: `OnboardingExceptionHandler` never actually runs.** `GlobalExceptionHandler` is `@RestControllerAdvice(basePackages = "com.alramz.controllers")` at `@Order(0)`, which already matches `OnboardingController` and already has handlers for every exception type `OnboardingExceptionHandler` also declares (`ApplicationException`, `MethodArgumentNotValidException`, `ExternalSystemException`, generic `Exception`). Spring's `ExceptionHandlerExceptionResolver` picks the *first* applicable advice bean in ascending `@Order`, so the `@Order(0)` `GlobalExceptionHandler` always wins over the `@Order(1)` `OnboardingExceptionHandler` for every exception they both handle — the latter's logic (including its slightly different `friendlyMessage`/error-code mapping) is unreachable. **Do not copy `OnboardingExceptionHandler` as a pattern.** If you need per-controller-scoped handling, either give the narrower advice bean the *lower* `@Order` value, or fold the branching into one handler the way `GlobalExceptionHandler` already does for its two response shapes.
- **Inconsistent response shapes across handler methods invite copy-paste drift.** `GlobalExceptionHandler` repeats the same three-way `isValidationRequest`/`isOnboardingRequest`/generic branching in nearly every method (see e.g. `handleApplication`, `handleExternalSystem`, `handleTechnical`). If your service only has one response contract, you don't need this branching at all — write one handler method per exception type with one return shape, as shown in Step 3 above. Only reach for the branching pattern if you truly have multiple response contracts in one service, and consider extracting the repeated logic into a small private helper if you do.
- **Don't put HTTP status logic inside the exception class.** Exceptions in this repo carry data (`field`, `errorCode`, `code`), not `HttpStatus` — the mapping to a status code belongs in the handler, so the same exception can be reused if two endpoints need different statuses for it.

## 6. Checklist

- [ ] `exception` package created under the right feature/service package.
- [ ] One typed exception class per distinct failure mode (not per endpoint), extending `RuntimeException` directly, carrying only the fields the handler needs.
- [ ] Reused `ApiCallFailedException`/`InvalidHttpRequestException` from `alramz-api-starter` instead of redefining HTTP-client-level exceptions.
- [ ] One `GlobalExceptionHandler` (`@RestControllerAdvice(basePackages = "com.alramz.controllers")`, explicit `@Order`) with an `@ExceptionHandler` method per typed exception, plus `MethodArgumentNotValidException`/`ConstraintViolationException` for `@Valid` failures, plus a catch-all `Exception` handler.
- [ ] Correlation id (from `X-Correlation-Id` header) included in every error response body, if your API contract has a field for it.
- [ ] If a second, narrower handler is genuinely needed: its `@Order` is lower (higher precedence) than the general handler's, verified by a test that actually exercises it — don't assume it fires.
- [ ] `company.logging.exception.enabled` left at its default (`true`) so the starter's last-resort handler backstops anything your own handler misses.
- [ ] A controller test per typed exception asserting status code + response body shape; run via `mvn -pl services/<service> -am test`.
