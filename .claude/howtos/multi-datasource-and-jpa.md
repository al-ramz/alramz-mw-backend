# Multi-Datasource Access (`company.datasource.*`)

Reference implementations: `services/data-validation-service` (uses `middleware` + `brok`), `services/reference-data-service` (uses `middleware` only). Shared plumbing lives in `services/alramz-api-starter`.

## 1. What this pattern is / when to use it

The starter can wire up to **three independently-configured named datasources** per service — `middleware` (Postgres, the service's own DB), `brok` and `integration` (Oracle, external/legacy systems) — each as its own HikariCP pool with its own `JdbcTemplate`, `NamedParameterJdbcTemplate`, `DataSourceTransactionManager`, and health indicator bean.

Use this pattern for **any** database access in a new service. Do **not**:
- Hand-roll a `DataSource`/`HikariConfig` bean in the service itself.
- Add a new named datasource type (a 4th pool) without extending the starter — the three names are fixed by `DatasourceProperties`.

Each named datasource is independently toggled — a service only pays for the pools it enables (`enabled: false` is the default for all three).

## 2. Where it lives in this repo

All in `services/alramz-api-starter/src/main/java/com/alramz/datasource/config/`:

| Class | Role |
|---|---|
| `DatasourceProperties.java` | `@ConfigurationProperties(prefix = "company.datasource")` — holds `middleware`, `brok`, `integration`, each a `DatasourceConfig` (url/username/password/plainPassword/passwordVector/driverClassName/pool/oracle/postgres/sqlLogging/startupValidation). |
| `AbstractDataSourceAutoConfiguration.java` | Shared Hikari-building logic: password resolution, pool properties, Oracle/Postgres-specific tuning, startup connection validation, SQL-logging proxy wrapping. |
| `MiddlewareDataSourceAutoConfiguration.java` | `@AutoConfiguration` + `@ConditionalOnProperty(prefix = "company.datasource.middleware", name = "enabled", havingValue = "true")`. Builds `middlewareDataSource`, `middlewareJdbcTemplate`, `middlewareNamedParameterJdbcTemplate`, `middlewareTransactionManager`, `middlewareHealthIndicator`. Default driver: `org.postgresql.Driver`. |
| `BrokDataSourceAutoConfiguration.java` | Same shape, prefix `company.datasource.brok`, bean names prefixed `brok*`, default driver `oracle.jdbc.OracleDriver`. |
| `IntegrationDataSourceAutoConfiguration.java` | Same shape, prefix `company.datasource.integration`, bean names prefixed `integration*`, default driver `oracle.jdbc.OracleDriver`. |
| `EncryptionProperties.java` / `EncryptionAutoConfiguration.java` | `@ConditionalOnProperty(prefix = "cipher", name = "password")` — registers a `PWProtector` bean from `cipher.password` when that property is set. |
| `com.alramz.utils.PWProtector` | AES/GCM encrypt/decrypt helper used to resolve `password` + `passwordVector` into a plaintext password at datasource-build time. |

All four autoconfig classes are registered in `services/alramz-api-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

Each consuming service adds its own small `DatasourceConfiguration` (e.g. `services/data-validation-service/src/main/java/com/alramz/config/DatasourceConfiguration.java`, mirrored in `reference-data-service`) that re-exposes the `middleware` beans as Spring's `@Primary` `DataSource`/`PlatformTransactionManager`.

## 3. How it works

**Bean naming.** Each autoconfig class produces beans named `<dbname>DataSource`, `<dbname>JdbcTemplate`, `<dbname>NamedParameterJdbcTemplate`, `<dbname>TransactionManager`, `<dbname>HealthIndicator` — e.g. `middlewareDataSource`, `brokNamedParameterJdbcTemplate`. With more than one enabled, Spring cannot pick one unqualified, so **every injection point must specify `@Qualifier("<dbname>...")`** — this is why the CLAUDE.md rule exists.

**`@Primary` re-export.** Both reference services define this in their own `config` package:

```java
@Configuration
public class DatasourceConfiguration {

    @Bean
    @Primary
    public DataSource primaryDataSource(@Qualifier("middlewareDataSource") DataSource middlewareDataSource) {
        return middlewareDataSource;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("middlewareTransactionManager") DataSourceTransactionManager middlewareTransactionManager) {
        return middlewareTransactionManager;
    }
}
```

This makes `middleware` the implicit default: `@Transactional`, Liquibase, and (if you choose to use them) unqualified Spring Data JPA repositories (`interface FooRepository extends JpaRepository<Foo, Long>`) all resolve to the middleware pool automatically, with **no `@Qualifier` needed**. `brok`/`integration` are never `@Primary` — any direct `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource` injection against them (or against `middleware`, if you're not going through the `@Primary` re-export) **must** carry an explicit `@Qualifier`.

> Note: as of this writing, neither reference service actually has a `JpaRepository` in it — both do direct SQL through `NamedParameterJdbcTemplate` (see `DfmOnboardingRepositoryImpl` below) with SQL text loaded from `sql-queries.xml`/`SqlQueriesManager`. The `@Primary` wiring exists so that either style works; don't assume a JPA entity layer is already in use — check before copying JPA-specific code from elsewhere.

**Password resolution** (`AbstractDataSourceAutoConfiguration.resolvePassword`): for each named datasource, at startup:
1. If `plainPassword` is set (and non-blank) → used directly, and a `WARN` is logged ("should only be used in local/dev profiles").
2. Else if `password` + `passwordVector` are both set → `PWProtector.decrypt(password, passwordVector)` (AES/GCM, key = `cipher.password`).
3. Else → `null` (Hikari will fail to connect).

`PWProtector` only exists as a bean when `cipher.password` is set (`EncryptionAutoConfiguration` is `@ConditionalOnProperty(prefix = "cipher", name = "password")`), so any profile using encrypted `password`/`passwordVector` must also set `cipher.password`.

**Oracle/Postgres tuning**: `brok`/`integration` datasources apply `oracle.*` connection properties (`connectTimeout`, `readTimeout`, `defaultRowPrefetch`); `middleware` applies `postgres.*` (`ssl` — auto-disabled for `localhost`/`127.0.0.1`/`mem:` URLs — and `prepareThreshold`).

## 4. How to add datasource access to a new service

### Step 1 — enable the datasource(s) you need, per profile

In `application-dev.yml` (dev-only, `plainPassword` is fine and expected):

```yaml
company:
  datasource:
    middleware:
      enabled: true
      url: ${COMPANY_DATASOURCE_MIDDLEWARE_URL:jdbc:postgresql://localhost:5432/<your-dev-db>}
      username: ${COMPANY_DATASOURCE_MIDDLEWARE_USERNAME:<dev-user>}
      plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD:<dev-only-placeholder>}
      driverClassName: ${COMPANY_DATASOURCE_MIDDLEWARE_DRIVERCLASSNAME:org.postgresql.Driver}
      startup-validation:
        enabled: false
      pool:
        maximum-pool-size: 10
        minimum-idle: 2
      sql-logging:
        enabled: false
```

Only set `brok`/`integration` blocks if the service actually talks to those systems.

In `application-preprod.yml`/`application-prod.yml`, **never use `plainPassword`** — use the encrypted pair instead:

```yaml
cipher:
  password: ${CIPHER_MASTER_KEY}   # 16/24/32-byte AES key, from Key Vault/CI secret — never hardcode

company:
  datasource:
    middleware:
      enabled: true
      url: ${COMPANY_DATASOURCE_MIDDLEWARE_URL}
      username: ${COMPANY_DATASOURCE_MIDDLEWARE_USERNAME}
      password: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD}          # AES/GCM ciphertext, base64
      passwordVector: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD_IV} # AES/GCM IV, base64
      driverClassName: org.postgresql.Driver
```

Produce the `password`/`passwordVector` pair once (offline, not checked in) with `PWProtector.encrypt(plaintext)` — it returns `[cipherTextBase64, ivBase64]` — using the same `cipher.password` master key the target environment will use at runtime.

### Step 2 — re-export middleware as `@Primary` (copy verbatim into your service)

`src/main/java/com/alramz/config/DatasourceConfiguration.java`:

```java
@Configuration
public class DatasourceConfiguration {

    @Bean
    @Primary
    public DataSource primaryDataSource(@Qualifier("middlewareDataSource") DataSource middlewareDataSource) {
        return middlewareDataSource;
    }

    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("middlewareTransactionManager") DataSourceTransactionManager middlewareTransactionManager) {
        return middlewareTransactionManager;
    }
}
```

Skip this step if the service only uses `brok`/`integration` and has no primary datasource concept.

### Step 3a — consuming `middleware` via `NamedParameterJdbcTemplate` (the pattern both reference services actually use)

```java
@Repository
@Slf4j
public class FooRepositoryImpl implements FooRepository {

    private final NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate;

    public FooRepositoryImpl(
            @Qualifier("middlewareNamedParameterJdbcTemplate") NamedParameterJdbcTemplate middlewareNamedParameterJdbcTemplate) {
        this.middlewareNamedParameterJdbcTemplate = middlewareNamedParameterJdbcTemplate;
    }

    @Override
    public void insert(Foo foo) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", foo.getId());
        middlewareNamedParameterJdbcTemplate.update(SQL_INSERT, params);
    }
}
```

(See `services/data-validation-service/src/main/java/com/alramz/repository/DfmOnboardingRepositoryImpl.java` for a full real example, including SQL loaded via `SqlQueriesManager` from an XML query file rather than inline strings.)

### Step 3b — consuming `brok`/`integration` (non-primary, always qualify)

```java
@Service
public class LegacyLookupService {

    private final JdbcTemplate brokJdbcTemplate;

    public LegacyLookupService(@Qualifier("brokJdbcTemplate") JdbcTemplate brokJdbcTemplate) {
        this.brokJdbcTemplate = brokJdbcTemplate;
    }
}
```

### Step 3c — if you do use Spring Data JPA instead

```java
public interface FooRepository extends JpaRepository<FooEntity, Long> {
}
```

No `@Qualifier` needed — it resolves against the `@Primary` middleware `DataSource`/`TransactionManager` from Step 2. Combine with Liquibase changesets (see the Liquibase howto) for schema management — `data-validation-service` is the only current service doing this, though via native SQL migrations rather than JPA entities.

## 5. Common pitfalls / anti-patterns

- **Unqualified injection in a multi-datasource module.** Injecting `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource`/`TransactionManager` without `@Qualifier` is either a compile-time "no unique bean" error (if you didn't add the `@Primary` re-export) or silently resolves to whichever bean happens to be `@Primary` — never assume, always qualify explicitly for anything that isn't going through the Step 2 `@Primary` re-export.
- **Hardcoding passwords in non-dev profiles.** `plainPassword` is dev-only by design (it logs a `WARN` every time it's used) — `application-preprod.yml`/`application-prod.yml` must use `cipher.password` + encrypted `password`/`passwordVector`, sourced from Key Vault/CI secrets, never committed in plaintext.
- **Forgetting `enabled: true`.** Each named datasource's autoconfig is `@ConditionalOnProperty(..., havingValue = "true", matchIfMissing = false)` — omitting `enabled` (or leaving it at the property default `false`) means the beans simply never exist, and any `@Qualifier("middlewareDataSource")` injection fails at context startup with "no bean found," not a runtime DB error.
- **Adding a 4th named datasource ad hoc.** The three names (`middleware`, `brok`, `integration`) are fixed in `DatasourceProperties` — a new external system needs a starter change (new `DatasourceConfig` field + new `@AutoConfiguration` class + `AutoConfiguration.imports` entry), not a per-service workaround.
- **Copying `cipher.password` values between environments.** It's the AES key for that environment's encrypted datasource passwords — reusing dev's `cipher.password` in preprod/prod (or vice versa) either fails to decrypt or, worse, "successfully" decrypts to the wrong plaintext.

## 6. Checklist

- [ ] `application-dev.yml` enables only the named datasource(s) this service actually needs, with `plainPassword` (dev only).
- [ ] `application-{preprod,prod}.yml` use `password` + `passwordVector` + `cipher.password`, no `plainPassword`.
- [ ] If middleware is the service's own DB: `config/DatasourceConfiguration.java` re-exports it as `@Primary` `DataSource`/`PlatformTransactionManager`.
- [ ] Every `JdbcTemplate`/`NamedParameterJdbcTemplate`/`DataSource`/`TransactionManager` injection for `brok`/`integration` (and for `middleware` if not going through the `@Primary` re-export) carries an explicit `@Qualifier("<dbname>...")`.
- [ ] No datasource password is committed in plaintext outside `application-dev.yml`.
- [ ] `mvn -pl services/<service> -am clean test-compile` passes (catches missing-qualifier compile/context errors early via `@SpringBootTest` if present).
