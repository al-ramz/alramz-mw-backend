# Configuration Properties

How externalized configuration is modeled in this repo: every toggle, URL, pool size, or credential a service needs is bound to a typed `@ConfigurationProperties` class — never scattered `@Value("${...}")` injections. This doc is the reference for adding a new one, whether inside `alramz-api-starter` (a feature every service can opt into) or inside a single service (feature-local config).

## 1. What this pattern is / when to use it

Any time you introduce a new piece of externalized config — a new integration's base URL, a new feature flag, a new pool size — it becomes a `@ConfigurationProperties` class bound to a `company.*` (starter/shared) or feature-specific (e.g. `adapter.*`) YAML prefix, not a bag of `@Value` fields spread across beans.

Two flavors exist side by side in this repo:

- **Starter-level** (`alramz-api-starter`): shared, cross-service features — JWT, multi-datasource, logging, scheduler, encryption. Every consuming service gets these for free once the starter is on the classpath, gated by `@ConditionalOnProperty` so they default to *safe/off* per CLAUDE.md §3.
- **Service-level**: a single service's own integration or feature config (e.g. `data-validation-service`'s external IBAN/phone/eTrade adapters, `reference-data-service`'s Redis cache config).

Both flavors use the same underlying Spring Boot mechanism (`@ConfigurationProperties` + `@EnableConfigurationProperties`), but you'll see two different concrete coding styles in this repo — see §5 for which one to follow.

## 2. Where it lives in this repo

**Starter properties classes** — `services/alramz-api-starter/src/main/java/com/alramz/...`:

| Class | Prefix | File |
|---|---|---|
| `JwtProperties` | `company.jwt` | `jwt/config/JwtProperties.java:15` |
| `DatasourceProperties` | `company.datasource` | `datasource/config/DatasourceProperties.java:12` |
| `EncryptionProperties` | `cipher` | `datasource/config/EncryptionProperties.java:13` |
| `LoggingProperties` | `company.logging` | `logging/config/LoggingProperties.java:25` |
| `SchedulerProperties` | `company.scheduler` | `scheduler/config/SchedulerProperties.java:11` |

**Binding points** (the paired `@AutoConfiguration` classes) — e.g. `jwt/config/JwtAutoConfiguration.java:23`, `datasource/config/MiddlewareDataSourceAutoConfiguration.java:21`, `logging/config/LoggingAutoConfiguration.java:45`, `scheduler/config/SchedulerAutoConfiguration.java:11` — each carries `@EnableConfigurationProperties(XProperties.class)`.

**Registration** — `services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` lists every one of those auto-config classes; a class left off this file never loads (CLAUDE.md §4).

**Service-level properties classes** — real examples to model from:

| Class | Prefix | File |
|---|---|---|
| `IbanServiceProperties` | `adapter.external-iban-service` | `data-validation-service/src/main/java/com/alramz/config/IbanServiceProperties.java` |
| `PhoneServiceProperties` | (veriphone adapter) | `data-validation-service/src/main/java/com/alramz/config/PhoneServiceProperties.java` |
| `ETradeProperties` | `adapter.etrade` | `data-validation-service/src/main/java/com/alramz/config/ETradeProperties.java` |
| `CompanyRedisProperties` | `company.redis` | `reference-data-service/src/main/java/com/alramz/config/CompanyRedisProperties.java:10` |
| `CacheMappingProperties` | (nested under `company.redis.cache`) | `reference-data-service/src/main/java/com/alramz/config/CacheMappingProperties.java` |
| `MiddlewareDataSourceProperties` | — | `reference-data-service/src/main/java/com/alramz/config/MiddlewareDataSourceProperties.java` |

Each service-level properties class is bound by its own dedicated `@Configuration` class right next to where it's consumed, e.g. `ExternalIbanServiceConfig.java:27` (`@EnableConfigurationProperties(IbanServiceProperties.class)`), `ExternalVeriPhoneServiceConfig.java:27`, `ExternalETradeServiceConfig.java:27` — same binding mechanism as the starter, just declared locally instead of inside an `@AutoConfiguration` class.

## 3. How it works

**The starter convention** (the one to copy for new code): a `@Getter @Setter` class, a `public static final String PREFIX = "..."` constant passed into `@ConfigurationProperties(prefix = ...)`, and nested `public static class` groups for structured sub-config. Real example, `DatasourceProperties.java`:

```java
@Getter
@Setter
@ConfigurationProperties(prefix = DatasourceProperties.PREFIX)
public class DatasourceProperties {

    public static final String PREFIX = "company.datasource";

    private DatasourceConfig middleware = new DatasourceConfig();
    private DatasourceConfig brok = new DatasourceConfig();
    private DatasourceConfig integration = new DatasourceConfig();

    @Getter
    @Setter
    public static class DatasourceConfig {
        private boolean enabled = false;
        private String url;
        private String username;
        private String password;
        private String passwordVector;
        private String plainPassword;
        private String driverClassName;
        private StartupValidation startupValidation = new StartupValidation();
        private PoolProperties pool = new PoolProperties();
        // ... sqlLogging, oracle, postgres sub-groups
    }

    @Getter @Setter
    public static class PoolProperties {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        // ...
    }
}
```

`LoggingProperties` shows the same shape at a larger scale — a top-level `enabled` master switch plus eight nested groups (`request`, `response`, `masking`, `json`, `aspect`, `performance`, `correlationId`, `exception`, `databaseLogging`, `seq`), each defaulting to the *conservative* value (masking on, payload logging off) — this is the "default to safe/off" convention from CLAUDE.md §4 in practice.

**Binding**: the properties class is bound via `@EnableConfigurationProperties(XProperties.class)` on the class's paired `@AutoConfiguration` (starter) or `@Configuration` (service-level) class — never via `@Component` + field injection of `@Value`, and (in this repo) never via `@ConfigurationPropertiesScan` either; every binding you'll find is an explicit `@EnableConfigurationProperties(...)`.

**Activation gating**: a paired `@AutoConfiguration` class uses `@ConditionalOnProperty` against the properties' own fields to decide whether to wire beans at all, e.g.:

```java
// MiddlewareDataSourceAutoConfiguration.java:20
@ConditionalOnProperty(prefix = "company.datasource.middleware", name = "enabled",
        havingValue = "true", matchIfMissing = false)
```

```java
// LoggingAutoConfiguration.java — one gate per sub-feature, most default ON:
@ConditionalOnProperty(name = "company.logging.request.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "company.logging.aspect.enabled", havingValue = "true") // no matchIfMissing => defaults OFF
@ConditionalOnProperty(name = "company.logging.database-logging.enabled", havingValue = "true") // defaults OFF
```

Note the pattern: request/response/exception logging default **on** (`matchIfMissing = true`, low-risk, no side effects), while database-logging and the AOP aspect default **off** (no `matchIfMissing`, so absence = disabled) because they have real side effects (DB writes, verbose logs) — pick the same posture when you add a new gated feature.

**YAML shape mirrors the nested static classes** — real excerpt from `reference-data-service/src/main/resources/application-dev.yml`:

```yaml
company:
  jwt:
    enabled: true
    secret: ${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}
    login-url: /api/auth/login
    permit-all-urls:
      - /api/v1/info
  logging:
    request:
      enabled: true
      include-headers: true
    database-logging:
      enabled: true
      retention-days: 7
  datasource:
    middleware:
      enabled: true
      url: ${COMPANY_DATASOURCE_MIDDLEWARE_URL:jdbc:postgresql://localhost:5432/mydb}
      plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD:changeme}   # dev-only, see multi-datasource-and-jpa.md
```

(Values above are illustrative placeholders, not the repo's real dev credentials — never copy real secret values out of tracked YAML into a doc or another file, per CLAUDE.md §5.)

## 4. How to add a new configuration properties class

Worked example: adding a toggleable "notification digest" feature with a URL, a batch size, and an enabled flag.

**Step 1 — write the properties class**, modeled on the starter convention (Lombok, `PREFIX` constant, nested group if you have more than a couple of related settings):

```java
package com.alramz.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = DigestProperties.PREFIX)
public class DigestProperties {

    public static final String PREFIX = "company.notification.digest";

    private boolean enabled = false;          // default OFF: new feature, has side effects
    private String targetUrl;
    private int batchSize = 50;
}
```

**Step 2 — bind it** on a `@Configuration` (service-level feature) or `@AutoConfiguration` (starter-level, cross-service feature) class, gated by its own `enabled` flag if the feature has side effects:

```java
package com.alramz.notification.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DigestProperties.class)
@ConditionalOnProperty(prefix = DigestProperties.PREFIX, name = "enabled", havingValue = "true")
public class DigestConfiguration {

    @Bean
    DigestSender digestSender(DigestProperties properties) {
        return new DigestSender(properties);
    }
}
```

If this is a starter-level auto-config, add its fully-qualified class name to `AutoConfiguration.imports` — otherwise it silently never loads (CLAUDE.md §4, non-negotiable).

**Step 3 — populate it per profile** in `application-*.yml`:

```yaml
company:
  notification:
    digest:
      enabled: true
      target-url: ${DIGEST_TARGET_URL:https://example.internal/digest}
      batch-size: 100
```

## 5. Common pitfalls / anti-patterns

- **Hand-rolled `@Value("${...}")` instead of a typed properties class.** Not found in the starter, but easy to slip into new service code — don't; it fragments config across the codebase and loses validation/IDE support.
- **Forgetting `AutoConfiguration.imports`.** A new starter-level `@AutoConfiguration` class that isn't listed there never activates, with no error — CLAUDE.md §4 calls this out explicitly. Service-level `@Configuration` classes don't need this (they're picked up by component scan on the service's base package), only starter auto-configs do.
- **Not defaulting a new gated feature to off/safe.** Follow the `database-logging`/`aspect` pattern (no `matchIfMissing`, i.e. absent = disabled) for anything with side effects; follow the `request`/`response` pattern (`matchIfMissing = true`) only for genuinely low-risk, already-battle-tested behavior.
- **Inconsistent style you'll find in this repo — pick the starter convention, not these:**
  - `SchedulerProperties` (starter) hand-writes getters/setters instead of using Lombok `@Getter/@Setter` — an outlier vs. every other starter properties class. Don't copy this for new code; use Lombok as CLAUDE.md §4 mandates ("Lombok everywhere").
  - `data-validation-service`'s own properties (`IbanServiceProperties`, `PhoneServiceProperties`, `ETradeProperties`) are Java `record`s with no `PREFIX` constant (prefix is an inline string literal). Records work fine with `@ConfigurationProperties` via constructor binding, but they diverge from the documented `PREFIX` constant + Lombok convention — prefer the constant + Lombok class shape for new service-level properties so IDEs/greps can find "the prefix for X" consistently.
  - `reference-data-service`'s `CompanyRedisProperties` uses `@Component` + hand-written getters/setters with no `PREFIX` constant either. Again, functional, but not the pattern to imitate — use `@EnableConfigurationProperties` (not `@Component` on the properties class itself) plus a `PREFIX` constant.
- **`company.jwt.enabled` looks like a kill switch but isn't fully wired as one.** `JwtProperties.enabled` exists and defaults `true`, but `JwtAutoConfiguration` itself carries no `@ConditionalOnProperty` gate on it (unlike the datasource and logging auto-configs) — so setting `company.jwt.enabled: false` does not currently disable JWT wiring. Don't assume flipping that flag off is sufficient; verify actual behavior in code before relying on it, and treat this as a known gap rather than a documented feature.

## 6. Checklist

- [ ] New config need identified → is it cross-service (goes in the starter) or single-service (goes in that service's own `config` package)?
- [ ] Properties class created: Lombok `@Getter @Setter`, `public static final String PREFIX`, nested `static class` groups if there's more than 2-3 related fields.
- [ ] Bound via `@EnableConfigurationProperties(X.class)` on a `@Configuration` (service) or `@AutoConfiguration` (starter) class.
- [ ] If starter-level: class added to `AutoConfiguration.imports`.
- [ ] If the feature has side effects (DB writes, external calls, verbose logging): gated by its own `enabled` flag via `@ConditionalOnProperty`, defaulting OFF (no `matchIfMissing`).
- [ ] If low-risk/already-safe: `matchIfMissing = true` is acceptable.
- [ ] YAML added to the relevant `application-*.yml` profile(s), using `${ENV_VAR:default}` placeholders for anything environment-specific — no real secrets committed.
- [ ] Ran `mvn -pl services/<service> -am clean test-compile` to confirm the binding compiles and the property prefix has no typos.
