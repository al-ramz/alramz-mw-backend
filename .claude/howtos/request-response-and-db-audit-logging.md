# Request/Response Logging, DB Audit Logging & Masking

This doc covers four independent, starter-provided logging/audit concerns, all owned by
`alramz-api-starter`'s `com.alramz.logging` and `com.alramz.audit` packages and all toggled by
`company.logging.*` properties — **never hand-rolled** in a consuming service:

1. **Request/response logging** — always-on HTTP filters (method, URI, headers, payload, status, duration).
2. **DB audit logging** — every call to a controller/client/select validation services persisted to an
   `api_audit_log` table via the `middleware` datasource.
3. **Method-execution (AOP) logging** — opt-in `@Loggable` annotation for tracing entry/exit/args/return of
   any method.
4. **Masking** — a single `SensitiveDataMasker` (backed by `LogMaskingUtil`) that redacts sensitive
   values before anything reaches a log line or an audit row.

Reference services: `data-validation-service` (uses all four, including DB audit logging) and
`reference-data-service` (uses 1, 3, 4, but deliberately leaves DB audit logging **off** even though it
has the `middleware` datasource enabled).

## 1. Where it lives in this repo

All in `services/alramz-api-starter/src/main/java/com/alramz/`:

| Concern | Class | Path |
|---|---|---|
| Properties | `LoggingProperties` | `logging/config/LoggingProperties.java` |
| Auto-config (wires everything below) | `LoggingAutoConfiguration` | `logging/config/LoggingAutoConfiguration.java` |
| Request filter | `RequestLoggingFilter` | `logging/filter/RequestLoggingFilter.java:28` |
| Response filter | `ResponseLoggingFilter` | `logging/filter/ResponseLoggingFilter.java:30` |
| `@Loggable` annotation | `Loggable` | `logging/aspect/Loggable.java:15` |
| AOP aspect for `@Loggable` | `MethodExecutionLoggingAspect` | `logging/aspect/MethodExecutionLoggingAspect.java:16` |
| DB audit row model | `ApiAuditLog` | `audit/ApiAuditLog.java` |
| DB audit persistence | `ApiAuditLogService` | `audit/ApiAuditLogService.java:17` |
| DB audit HTTP filter (inbound calls) | `ApiAuditLogFilter` | `audit/ApiAuditLogFilter.java:26` |
| DB audit AOP (outbound/internal calls) | `ApiAuditAspect` | `audit/ApiAuditAspect.java:20` |
| Masking | `SensitiveDataMasker` | `audit/SensitiveDataMasker.java:16` |

Registered in `services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` as `com.alramz.logging.config.LoggingAutoConfiguration`.

`api_audit_log` table (Liquibase, `data-validation-service` only — see `liquibase-migrations.md`):
`services/data-validation-service/src/main/resources/db/changelog/{dev,docker,preprod,prod}/sql/004-api-audit-log.sql`.

## 2. How it works

### 2.1 Request/response logging (always on by default)

- `RequestLoggingFilter` (`@Order(HIGHEST_PRECEDENCE + 20)`) and `ResponseLoggingFilter`
  (`@Order(HIGHEST_PRECEDENCE + 10)`) are each registered only if their own property is `true`
  (`company.logging.request.enabled` / `company.logging.response.enabled`, both **default `true`**).
- Response filter is ordered *before* request filter numerically (10 < 20) but both wrap the same call so
  the response log line is emitted after the request log line — "a natural request-then-response order."
- Both wrap the request/response in `ContentCachingRequestWrapper`/`ContentCachingResponseWrapper` so the
  body can be read after the fact without consuming the stream, then log method, URI, query params, client
  IP, selected headers, status, size, duration, and the payload if `includePayload` is on.
- `company.logging.excludedPaths` (default `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`) skips noisy
  paths entirely (`shouldNotFilter`).
- Whatever gets logged is masked first — see §2.4.

### 2.2 DB audit logging (opt-in, hard dependency on the `middleware` datasource)

Four beans in `LoggingAutoConfiguration`, **all** gated by
`@ConditionalOnProperty(name = "company.logging.database-logging.enabled", havingValue = "true")`
(default `false`):

- `apiAuditSensitiveDataMasker` — always created when the property is true.
- `apiAuditLogService` (`ApiAuditLogService`) — **additionally** gated by
  `@ConditionalOnBean(name = "middlewareNamedParameterJdbcTemplate")`.
- `apiAuditLogFilter` (`ApiAuditLogFilter`, inbound HTTP calls) — same additional `@ConditionalOnBean` guard.
- `apiAuditAspect` (`ApiAuditAspect`, AOP) — **not** guarded by the bean check itself, but it takes
  `ApiAuditLogService` as a constructor argument, so it can't be created unless that bean exists either.

**This is the silent no-op**: if you set `company.logging.database-logging.enabled=true` but the
`middleware` datasource (`company.datasource.middleware.enabled`) isn't on, `middlewareNamedParameterJdbcTemplate`
never exists, so none of the audit beans are created — no error, no warning, audit logging simply never
fires. See `multi-datasource-and-jpa.md` for enabling the middleware datasource.

Two capture paths, both funnel into `ApiAuditLogService.log(ApiAuditLog)`, which masks + serializes the
request/response to JSON and inserts one row into `api_audit_log` via `middlewareNamedParameterJdbcTemplate`:

- **`ApiAuditLogFilter`** (`@Order(HIGHEST_PRECEDENCE + 5)`, servlet filter) — captures every **inbound**
  HTTP request/response pair (direction `INBOUND`), status, duration, exception info if the response is
  ≥400. Payload capture is separately gated by
  `company.logging.database-logging.includeRequestPayload` / `includeResponsePayload` (both default
  `false` — the row is still written, just without the raw body, unless you turn these on).
  Respects `company.logging.database-logging.excludedPaths` (default `/api/v1/info`, `/actuator/**`,
  `/swagger-ui/**`, `/v3/api-docs/**`).
- **`ApiAuditAspect`** (`@Around` AOP) — captures **outbound/internal** calls via an explicit pointcut:
  `com.alramz.client..*`, `ETradeTokenProvider.fetchAndCacheToken`, `IBANValidationServiceImpl.validate`,
  `PhoneValidationServiceImpl.validate`, `DuplicateCheckServiceImpl..*`, and `com.alramz.controllers..*`
  (direction `OUTBOUND` unless the declaring class is a `@RestController`/`@Controller`, in which case
  `INBOUND`). Note the pointcut hardcodes specific service class names from `data-validation-service` —
  if a new service wants aspect-based audit coverage of its own client/service classes beyond the generic
  `com.alramz.client..*`/`com.alramz.controllers..*` patterns, those class names would need to be added to
  this pointcut in the starter (this is shared starter code, so treat that as a cross-cutting change, not
  a per-service override).
- A `@Scheduled` cleanup job on `ApiAuditLogService.cleanupExpired()` deletes rows older than
  `company.logging.database-logging.retentionDays` (default 7) on `company.logging.database-logging.cleanupCron`
  (default `0 0 2 * * *`, i.e. 2am daily).

Real, working example — `data-validation-service` (`application-dev.yml`) enables it fully:

```yaml
company:
  logging:
    database-logging:
      enabled: true
      include-request-payload: true
      include-response-payload: true
      cleanup-cron: "0 0 2 * * *"
      retention-days: 7
      excluded-paths:
        - /api/v1/info
        - /actuator/**
        - /swagger-ui/**
        - /v3/api-docs/**
  datasource:
    middleware:
      enabled: true
      # ... url/username/plainPassword/driverClassName/pool — see multi-datasource-and-jpa.md
```

Contrast — `reference-data-service` (`application-dev.yml`) has the `middleware` datasource enabled too,
but its `company.logging` block has **no `database-logging` section at all**, so it stays off by default:
audit logging is an explicit opt-in, not something you get "for free" just because the datasource exists.

### 2.3 Method-execution (AOP) logging via `@Loggable`

- Gated by `company.logging.aspect.enabled` (default `false`). When on, `MethodExecutionLoggingAspect`
  advises every method annotated `@Loggable` (`@Around("@annotation(com.alramz.logging.aspect.Loggable)")`),
  logging entry (with args), exit (with return value and elapsed ms), and exceptions. Per-method flags
  (`logEntry`/`logExit`/`logArgs`/`logReturn`/`logExceptions`) override the global
  `company.logging.aspect.*` defaults.
- Real usage — both reference services annotate methods directly:

  ```java
  // services/data-validation-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:54
  @Override
  @Loggable
  public GenericResponse validate(IBANRequest request) { ... }
  ```

  ```java
  // services/reference-data-service/src/main/java/com/alramz/controllers/RedisCacheController.java:30
  @Override
  @JwtSecured(roles = "APP_REFERENCE_DATA")
  @Loggable
  public ResponseEntity<GenericResponse> flushCache(String cacheKey) { ... }
  ```

  Import: `com.alramz.logging.aspect.Loggable`.

### 2.4 Masking — the one sanctioned way to redact sensitive data

Two layers exist and both matter:

- **`SensitiveDataMasker`** (`com.alramz.audit`) — used specifically by the DB-audit path
  (`ApiAuditLogFilter`, `ApiAuditAspect`, `ApiAuditLogService`). Its hardcoded, case-insensitive key set
  (`services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java:23-28`):

  ```java
  "client_secret", "api_key", "apikey", "access_token", "accesstoken",
  "authorization", "password", "consumerpassword", "secret", "cookie",
  "eid_attachment_front", "eid_attachment_back", "pinf_signatureimage", "pp_attachment",
  "cust_nin", "eid_no", "passportnumber", "pp_no"
  ```

  It also auto-detects and truncates long base64 blobs (images/signatures) even under keys not in the set,
  and masks primitive strings too. It's constructed once per audit bean, wired from
  `properties.getMasking().isEnabled()` — so the audit masker respects the same master masking switch as
  everything else.
- **`LoggingProperties.MaskingProperties`** (`company.logging.masking.*`) — used by the general request/
  response/aspect logging path (`LogMaskingUtil`, configured in `LoggingAutoConfiguration.configureMasking()`
  from `company.logging.masking.sensitiveKeys` / `customPatterns` / `maskReplacement`). Default keys:
  `password, token, authorization, accessToken, refreshToken, jwt, aadhaar, pan, ssn, creditCard, cvv,
  apiKey, secret, session, cookie`. This set is independent of `SensitiveDataMasker`'s set above — they
  protect different pipelines (general logs vs. audit rows) and both need to know about a new sensitive
  field if it should be redacted everywhere.

## 3. How to add logging/audit to a new service

### Step 1 — request/response logging (usually nothing to do)

It's on by default. Only touch it if you need to change what's logged:

```yaml
company:
  logging:
    request:
      enabled: true
      include-headers: true   # off by default
      include-payload: true   # off by default — turn on deliberately, payloads get masked but still cost log volume
    response:
      enabled: true
      include-payload: true
    excluded-paths:
      - /actuator/**
      - /swagger-ui/**
      - /v3/api-docs/**
```

### Step 2 — DB audit logging (only if the new service needs a persisted audit trail)

This requires the `middleware` datasource AND the `api_audit_log` table to exist:

1. Enable the middleware datasource (`company.datasource.middleware.enabled: true`) — see
   `multi-datasource-and-jpa.md`.
2. If the new service doesn't already have Liquibase, either add it (see `liquibase-migrations.md`) or, at
   minimum, ensure the `api_audit_log` table exists in its target schema — copy the DDL from
   `services/data-validation-service/src/main/resources/db/changelog/dev/sql/004-api-audit-log.sql`
   (adjust the `service_name` default if you want, though the code always writes the real
   `spring.application.name` value regardless of that column default).
3. Turn the feature on:

   ```yaml
   company:
     logging:
       database-logging:
         enabled: true
         include-request-payload: true
         include-response-payload: true
         cleanup-cron: "0 0 2 * * *"
         retention-days: 7
         excluded-paths:
           - /api/v1/info
           - /actuator/**
           - /swagger-ui/**
           - /v3/api-docs/**
   ```
4. Inbound HTTP traffic is captured automatically by `ApiAuditLogFilter` — no code changes needed.
   Outbound/internal calls are only captured if they match `ApiAuditAspect`'s hardcoded pointcut
   (`com.alramz.client..*`, `com.alramz.controllers..*`, plus the few named validation-service methods) —
   if your new service's classes don't fall under `com.alramz.client` or `com.alramz.controllers`, they
   won't be audited by the aspect even with the feature on; only the filter's inbound capture applies.

### Step 3 — method-execution logging for a specific method

```yaml
company:
  logging:
    aspect:
      enabled: true
```

```java
import com.alramz.logging.aspect.Loggable;

@Loggable
public GenericResponse validate(Request request) { ... }
```

### Step 4 — extending masking for a new sensitive field

Never write ad hoc redaction logic. Extend the property-driven key sets instead:

```yaml
company:
  logging:
    masking:
      sensitive-keys:
        - password
        - token
        - authorization
        - accessToken
        - refreshToken
        - jwt
        - aadhaar
        - pan
        - ssn
        - creditCard
        - cvv
        - apiKey
        - secret
        - session
        - cookie
        - myNewSensitiveField   # <-- add here, don't hand-mask it in application code
```

If the new field also needs to be redacted in `api_audit_log` rows specifically, it must be added to
`SensitiveDataMasker.buildSensitiveKeysSet()`'s hardcoded array in the starter
(`services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java:23-28`) — that set
is **not** externalized to a property, so this is a starter change, not a per-service yml change. Coordinate
with whoever owns `alramz-api-starter` rather than working around it in the consuming service.

## 4. Common pitfalls / anti-patterns

- **Do not log or audit sensitive fields directly.** Per CLAUDE.md: extend `SensitiveDataMasker`'s key set
  (or `company.logging.masking.sensitive-keys`) instead of bypassing masking with custom string
  concatenation/`toString()` overrides just to "make the log readable."
- **The silent DB-audit no-op.** Setting `company.logging.database-logging.enabled=true` without also
  enabling the `middleware` datasource produces zero errors and zero audit rows — nothing fails, it just
  silently does nothing (`@ConditionalOnBean(name = "middlewareNamedParameterJdbcTemplate")`). Always
  verify both are on together.
- **Aspect-based audit coverage is pointcut-limited, not universal.** Enabling `database-logging` audits
  all inbound HTTP traffic via the filter, but outbound/internal method audit coverage only applies to the
  specific packages/classes hardcoded in `ApiAuditAspect`'s pointcut — don't assume a new service's
  internal client classes are automatically audited.
- **Two separate masking key sets.** `company.logging.masking.sensitive-keys` (general logs) and
  `SensitiveDataMasker`'s hardcoded set (audit rows only) are independent — adding a key to one does not
  protect the other pipeline.
- **`include-payload`/`include-request-payload`/`include-response-payload` are all off by default** for a
  reason (volume + residual risk even with masking) — turn them on deliberately per environment, not
  reflexively.
- **`company.logging.aspect.enabled` is `false` by default** — `@Loggable` annotations on methods do
  nothing until the property is explicitly turned on for that service/profile.

## 5. Checklist

- [ ] `company.logging.request.enabled` / `response.enabled` reviewed for the new service (on by default;
      set `include-headers`/`include-payload` deliberately).
- [ ] `company.logging.excluded-paths` covers actuator/swagger/health endpoints.
- [ ] If DB audit logging is wanted: `company.datasource.middleware.enabled=true` **and**
      `company.logging.database-logging.enabled=true` are both set, and `api_audit_log` exists in the
      target schema.
- [ ] `company.logging.database-logging.excluded-paths` covers noisy/health endpoints so they don't
      flood `api_audit_log`.
- [ ] Confirmed whether outbound/internal calls that matter fall under `ApiAuditAspect`'s existing
      pointcut; if not, know that they won't be audited without a starter change.
- [ ] Any method needing execution tracing is annotated `@Loggable` and
      `company.logging.aspect.enabled=true` is set for the relevant profile(s).
- [ ] Any new sensitive field is added to `company.logging.masking.sensitive-keys` (and, if it can appear
      in an audited request/response, to `SensitiveDataMasker`'s key set in the starter) — never masked
      by hand in application code.
- [ ] Verified in logs/DB that a real request produces a masked (not raw) value for every sensitive field.
