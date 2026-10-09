# JWT Security & Public Endpoints

## 1. What this pattern is / when to use it

Every service in this monorepo gets its authentication for free from `alramz-api-starter` — you never hand-write a `SecurityFilterChain` in a service. The starter wires a single, stateless, JWT-based filter chain that authenticates **every** request by default. To make an endpoint public, you configure it (declaratively, via YAML), you don't code around security.

Use this doc when you're standing up a new service and need to:
- know whether JWT security is already on and what it protects,
- expose a new public (unauthenticated) endpoint,
- restrict an endpoint to specific roles/environments,
- get the calling user's identity or forward their bearer token to a downstream call.

## 2. Where it lives in this repo

All of the following are in `alramz-api-starter` (shared, not per-service):

| Concern | File |
|---|---|
| Config properties (`company.jwt.*`) | `services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtProperties.java` |
| Auto-config (filter chain + beans) | `services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java` |
| Token-validating filter | `services/alramz-api-starter/src/main/java/com/alramz/jwt/filter/JwtAuthFilter.java` |
| Per-request claim/context helper | `services/alramz-api-starter/src/main/java/com/alramz/jwt/context/JwtContext.java` |
| Role/environment method guard | `services/alramz-api-starter/src/main/java/com/alramz/jwt/annotation/JwtSecured.java` + `.../jwt/aspect/JwtSecuredAspect.java` |
| "Public endpoint" marker annotation | `services/alramz-api-starter/src/main/java/com/alramz/jwt/annotation/PermitAll.java` |
| Current-caller identity (userId + bearer token) | `services/alramz-api-starter/src/main/java/com/alramz/client/UserRequestContext.java` |
| Built-in `/api/auth/validate` endpoint | `services/alramz-api-starter/src/main/java/com/alramz/jwt/controller/ValidateController.java` |

Registered as an auto-configuration in `services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
```
com.alramz.jwt.config.JwtAutoConfiguration
```
If you ever write a similar starter-level config class, remember it does **nothing** unless it's listed in that file.

## 3. How it works

### 3.1 Configuration (`JwtProperties`, prefix `company.jwt`)

`services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtProperties.java:12-42`:

```java
@Getter @Setter
@ConfigurationProperties(prefix = JwtProperties.PREFIX)
public class JwtProperties {
    public static final String PREFIX = "company.jwt";
    private boolean enabled = true;
    private String secret = "defaultSecretKeyChangeMeInProduction1234567890";
    private long accessTokenExpirationMs = 15 * 60 * 1000;
    private long refreshTokenExpirationMs = 7 * 24 * 60 * 60 * 1000;
    private String loginUrl = "/api/auth/login";
    private String registerUrl = "/api/auth/register";
    private String refreshUrl = "/api/auth/refresh";
    private String logoutUrl = "/api/auth/logout";
    private String validateUrl = "/api/auth/validate";
    private List<String> permitAllUrls = new ArrayList<>();
    private String rolePrefix = "ROLE_";
    private String environmentClaimName = "environment";
    private String applicationClaimName = "application";
}
```

Real config, identical shape in both reference services (`application-{dev,test,docker,preprod,prod}.yml`):

```yaml
company:
  jwt:
    enabled: true
    secret: ${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}
    login-url: /api/auth/login
    register-url: /api/auth/register
    refresh-url: /api/auth/refresh
    logout-url: /api/auth/logout
    validate-url: /api/auth/validate
    permit-all-urls:
      - /api/v1/info
```
(`alramz-notification-service` extends the same list with its own public route: `- /api/v1/email/send`.)

### 3.2 The filter chain (`JwtAutoConfiguration`)

`services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java:74-94`:

```java
@Bean
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SecurityFilterChain.class)
SecurityFilterChain jwtSecurityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, JwtProperties properties) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
        .authorizeHttpRequests(auth -> {
            auth.requestMatchers(properties.getLoginUrl()).permitAll()
                .requestMatchers(properties.getRegisterUrl()).permitAll()
                .requestMatchers(properties.getRefreshUrl()).permitAll();
            for (String url : properties.getPermitAllUrls()) {
                auth.requestMatchers(url).permitAll();
            }
            auth.anyRequest().authenticated();
        })
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

The whole class is gated by `@AutoConfiguration` + `@EnableConfigurationProperties(JwtProperties.class)` — it only activates when `alramz-api-starter` is on the classpath (i.e. always, for both reference services). `@ConditionalOnClass`/`@ConditionalOnWebApplication` mean this specific bean only fires for a servlet web app, so **there is no `company.jwt.enabled` gate on the filter chain bean itself** — `enabled` is read by the bean but the bean is otherwise always created when the starter is present. Effectively: everything is authenticated except `login-url`, `register-url`, `refresh-url`, and whatever you list in `permit-all-urls`.

`JwtAuthFilter` (`.../jwt/filter/JwtAuthFilter.java:42-72`) runs before `UsernamePasswordAuthenticationFilter`: if there's no `Authorization: Bearer <token>` header, or the token doesn't validate, it just calls `filterChain.doFilter(...)` and moves on **without rejecting the request** — rejection happens downstream, at `anyRequest().authenticated()`, if the URL isn't permitted. If the token is valid, it populates `SecurityContextHolder` with a `UsernamePasswordAuthenticationToken(username, null, authorities)`, where `authorities` come from the token's `roles` claim, each prefixed `ROLE_`.

### 3.3 Getting the caller's identity: `JwtContext` vs `UserRequestContext`

Two different helpers, two different jobs — don't confuse them:

- **`JwtContext`** (`.../jwt/context/JwtContext.java`) — reads a specific **claim** straight off the raw `Authorization` header of the *current inbound request* (`getCurrentApplication()`, `getCurrentEnvironment()`, `getClaim(name)`). It's mainly an internal collaborator of `JwtSecuredAspect` for the `environment=` check below; you'd only inject it yourself if you need a custom claim.
- **`UserRequestContext`** (`services/alramz-api-starter/src/main/java/com/alramz/client/UserRequestContext.java`, package `com.alramz.client`, *not* `com.alramz.jwt`) — a static helper that reads Spring Security's `SecurityContextHolder` (not the raw header) and returns a `UserRequestDetails{userId, bearerToken}`. This is the one actually used in this repo:
  - `services/alramz-api-starter/src/main/java/com/alramz/jwt/controller/ValidateController.java:16` — the starter's built-in `/api/auth/validate` endpoint returns `UserRequestContext.get().getUserId()`.
  - `services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:112-122` — when a service calls another service over `WebClient`, it forwards the **caller's own bearer token** (not a service-account token) via `UserRequestContext.get().getBearerToken()`, falling back to it only if the principal isn't already a `Jwt`. This is how request-scoped auth propagates through inter-service calls.

### 3.4 Role/environment gating: `@JwtSecured` vs `@PermitAll`

Two separate, unrelated annotations, both from `com.alramz.jwt.annotation`:

- **`@JwtSecured(roles = {...}, environment = {...})`** — a **custom** AOP guard (`JwtSecuredAspect`, `@Before("@annotation(jwtSecured)")`), *not* Spring's `@PreAuthorize`. It checks the current `Authentication` is authenticated, then (if `roles` given) that the caller has at least one matching `ROLE_<role>` authority, then (if `environment` given) that `JwtContext.getCurrentEnvironment()` matches one of them. Real usage:
  ```java
  // services/data-validation-service/.../DataValidationController.java:45
  @Override
  @JwtSecured(roles = "APP_DATA_VALIDATION")
  @Loggable
  public ResponseEntity<GenericResponse> validateIBAN(IBANRequest ibANRequest) { ... }
  ```
  ```java
  // services/reference-data-service/.../RedisCacheController.java:29
  @Override
  @JwtSecured(roles = "APP_REFERENCE_DATA")
  @Loggable
  public ResponseEntity<GenericResponse> flushCache(String cacheKey) { ... }
  ```
- **`@PermitAll`** — a thin meta-annotation: `@PreAuthorize("permitAll()")`, activated by `@EnableMethodSecurity` on `JwtAutoConfiguration`. Real usage, identical in both services:
  ```java
  // services/reference-data-service/.../ApplicationController.java (same in data-validation-service)
  @GetMapping("/api/v1/info")
  @PermitAll
  @Loggable
  public Map<String, Object> info() { ... }
  ```

**Important nuance:** `@PermitAll` is method-level security, evaluated only *after* the request has already passed the filter chain's `authorizeHttpRequests`. Since that filter runs `anyRequest().authenticated()` for anything not explicitly permitted, an unauthenticated request to a `@PermitAll`-annotated endpoint will still be rejected with 401/403 **unless its URL is also in `company.jwt.permit-all-urls`**. Both reference services always do both — the annotation self-documents intent at the code level, and the YAML entry is what actually makes the endpoint reachable without a token. Treat `@PermitAll` as documentation you must pair with the YAML entry, not a substitute for it.

## 4. How to add a new public endpoint / consume the JWT context in a new service

### 4.a Expose a new public endpoint

1. Write the controller method as normal, and mark it `@PermitAll` for self-documentation:
   ```java
   @GetMapping("/api/v1/ping")
   @PermitAll
   @Loggable
   public Map<String, Object> ping() {
       return Map.of("status", "UP");
   }
   ```
2. Add the exact path to `company.jwt.permit-all-urls` in **every** `application-{profile}.yml` the service ships (dev/test/docker/preprod/prod) — copy the existing `/api/v1/info` entry as the template:
   ```yaml
   company:
     jwt:
       permit-all-urls:
         - /api/v1/info
         - /api/v1/ping
   ```
   Forgetting a profile is the most common way this silently breaks in one environment but not another — grep all profile files for `permit-all-urls` before calling it done.

### 4.b Restrict an endpoint to a role (and optionally an environment)

```java
@Override
@JwtSecured(roles = "APP_MY_SERVICE")           // caller must have ROLE_APP_MY_SERVICE
@Loggable
public ResponseEntity<GenericResponse> doSensitiveThing(Request req) { ... }

@Override
@JwtSecured(roles = "APP_MY_SERVICE", environment = "prod")  // + the token's environment claim must be "prod"
@Loggable
public ResponseEntity<GenericResponse> prodOnlyOperation(Request req) { ... }
```
Pick a role name following the existing convention (`APP_<SERVICE_CONCERN>`, e.g. `APP_DATA_VALIDATION`, `APP_REFERENCE_DATA`).

### 4.c Get the current caller's identity

```java
import com.alramz.client.UserRequestContext;
import com.alramz.models.UserRequestDetails;

UserRequestDetails caller = UserRequestContext.get();
String userId = caller.getUserId();
```
Never re-parse `Authorization` headers or reach into `SecurityContextHolder` yourself in a controller/service — go through `UserRequestContext.get()`.

### 4.d Forward the caller's token to a downstream service call

If your new service calls another internal service over HTTP, extend `AbstractRestClient` (from `com.alramz.client`) rather than hand-rolling a `WebClient` call — it already forwards `UserRequestContext.get().getBearerToken()` for you (see §3.3) and wraps circuit-breaker/retry/timeout handling.

## 5. Common pitfalls / anti-patterns

- **Do NOT write ad hoc Spring Security config** (your own `SecurityFilterChain`, `@EnableWebSecurity`, etc.) in a service to expose an endpoint. Add the path to `company.jwt.permit-all-urls` instead — a second filter chain bean will conflict with the starter's.
- **`@PermitAll` alone does not make an endpoint public.** You must also add its URL to `company.jwt.permit-all-urls` (§3.4). A controller annotated `@PermitAll` but missing from that list returns 401/403 to unauthenticated callers.
- **Health/info-style endpoints are not implicitly public.** There's no automatic exemption for `/actuator/**` or similar — if you expose one, it needs an explicit `permit-all-urls` entry like everything else.
- **A new `@AutoConfiguration` class is inert unless registered** in `AutoConfiguration.imports` — irrelevant to JWT specifically but relevant if you ever extend the starter's security setup.
- **Don't confuse `JwtContext` and `UserRequestContext`** — `JwtContext` reads a claim off the raw header (mainly for `JwtSecuredAspect`'s environment check); `UserRequestContext` reads the already-authenticated `SecurityContextHolder` and is what you actually want for "who is calling me" / "forward their token."
- **Update every profile file, not just `dev`.** `permit-all-urls` (and the rest of `company.jwt.*`) is duplicated per `application-{profile}.yml` — there's no shared base list, so a path added only to `dev` will 401 in `preprod`/`prod`.

## 6. Checklist for a new service

- [ ] `alramz-api-starter` is a declared dependency in the service's `pom.xml` (brings `JwtAutoConfiguration` transitively — nothing else to wire).
- [ ] `company.jwt.enabled: true` (or omit — defaults to `true`) is set per profile you ship.
- [ ] Every genuinely public route (info/health-style, webhook receivers, etc.) is both annotated `@PermitAll` **and** listed in `company.jwt.permit-all-urls` in **every** profile YAML.
- [ ] Every sensitive endpoint carries `@JwtSecured(roles = "APP_<YOUR_SERVICE>")`, using a role name that matches this service's naming convention.
- [ ] Any code that needs "who is calling me" uses `UserRequestContext.get()` — never manual token parsing.
- [ ] Any outbound call to another internal service extends `AbstractRestClient` so the caller's bearer token propagates automatically.
