# Al Ramz API Starter

Reusable Spring Boot starter providing centralized logging, JWT authentication, scheduling, and a multi-datasource framework for Al Ramz microservices.

## Features

- **Logging**: Structured JSON logging, correlation ID, request/response logging, AOP method execution logging, and OpenTelemetry trace context extraction.
- **JWT Authentication**: Token generation, validation, password encoding, and security filter chain configuration.
- **Scheduler**: Thread-pool task scheduler with YAML-driven job definitions.
- **Datasource Framework**: Conditional auto-configuration for middleware (PostgreSQL), brok (Oracle), and integration (Oracle) datasources with HikariCP pooling, encrypted password support, health indicators, and optional SQL logging via datasource-proxy.

---

## Datasource Framework

### Quick Start

Enable the middleware datasource in your `application.yml`:

```yaml
cipher:
  password: ${CIPHER_PASSWORD:}

company:
  datasource:
    middleware:
      enabled: true
      url: jdbc:postgresql://db-host:5432/middleware
      username: ${DB_USER:}
      password: ${DB_PASSWORD:}
      password-vector: ${DB_PASSWORD_VECTOR:}
      plainPassword: ${DB_PLAIN_PASSWORD:}
      driverClassName: org.postgresql.Driver
      startup-validation:
        enabled: true
      pool:
        maximum-pool-size: 10
        minimum-idle: 2
      sql-logging:
        enabled: false
```

### Property Reference

All properties are nested under `company.datasource.<name>` where `<name>` is one of `middleware`, `brok`, or `integration`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Master switch for the datasource. |
| `url` | string | — | JDBC URL. Required when `enabled=true`. |
| `username` | string | — | Database username. Required when `enabled=true`. |
| `password` | string | — | Base64-encoded AES/GCM ciphertext. |
| `password-vector` | string | — | Base64-encoded IV (16 bytes). |
| `plainPassword` | string | — | Plaintext password (local/dev only). |
| `driverClassName` | string | `org.postgresql.Driver` (middleware), `oracle.jdbc.OracleDriver` (brok/integration) | JDBC driver class. |
| `startup-validation.enabled` | boolean | `true` | Verify connectivity on startup. Set `false` to allow graceful start when DB is temporarily unavailable. |

#### Pool Properties (`pool.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maximum-pool-size` | int | `10` | Max connections per datasource instance. |
| `minimum-idle` | int | `2` | Min idle connections maintained by HikariCP. |
| `connection-timeout` | long | `30000` | Max time (ms) to acquire a connection. |
| `idle-timeout` | long | `600000` | Max idle time (ms) before a connection is evicted. |
| `keepalive-time` | long | `30000` | Keepalive interval (ms) to prevent firewall drops. |
| `max-lifetime` | long | `1800000` | Max lifetime (ms) of a connection. |
| `leak-detection-threshold` | long | `0` | Leak detection threshold (ms). `0` = disabled. |
| `query-timeout` | long | `0` | Query timeout (seconds). |

#### PostgreSQL Properties (`postgres.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `ssl` | boolean | `true` | Enable SSL. Auto-disabled for `localhost`/`mem:` URLs. |
| `prepare-threshold` | int | `5` | Prepared statement cache threshold. |

#### Oracle Properties (`oracle.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `connect-timeout` | int | `10000` | `oracle.net.CONNECT_TIMEOUT` in ms. |
| `read-timeout` | int | `60000` | `oracle.jdbc.ReadTimeout` in ms. |
| `default-row-prefetch` | int | `100` | Row prefetch size for batch workloads. |

#### SQL Logging Properties (`sql-logging.*`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable datasource-proxy SQL logging. |
| `log-parameters` | boolean | `false` | Include bind parameters in logs. |
| `slow-query-threshold-ms` | long | `0` | Threshold for slow query WARN logs. `0` = disabled. |

### Bean Reference

Each enabled datasource creates the following beans:

| Bean Name | Type | Description |
|-----------|------|-------------|
| `{name}DataSource` | `javax.sql.DataSource` | HikariCP DataSource, wrapped with proxy if SQL logging is enabled. |
| `{name}JdbcTemplate` | `JdbcTemplate` | Spring JDBC template. |
| `{name}NamedParameterJdbcTemplate` | `NamedParameterJdbcTemplate` | Named-parameter JDBC template. |
| `{name}TransactionManager` | `DataSourceTransactionManager` | Transaction manager for programmatic transaction management. |
| `{name}HealthIndicator` | `HealthIndicator` | Pool health exposed via Actuator. |

### Transaction Management

`@Transactional` without a `transactionManager` argument uses the primary datasource's transaction manager. For operations on a non-primary datasource, specify the transaction manager explicitly:

```java
@Transactional(transactionManager = "integrationTransactionManager")
public void updateIntegration() { ... }
```

No distributed transaction coordination is provided. Cross-datasource operations require explicit transaction manager references.

### Pool Sizing

```
total DB connections = application replicas x pool size per replica
```

Warn against setting pool sizes that exceed the database's `max_connections` when multiplied by expected replica count. Production values must be derived from workload and DB capacity.

### Security

Passwords are encrypted using AES/GCM via `PWProtector`. Store the two parts separately:

```yaml
company:
  datasource:
    middleware:
      password: <base64-ciphertext>
      password-vector: <base64-iv>
```

The master key is provided via `cipher.password`. For local development, use `plainPassword` instead. A WARN log is emitted when plaintext passwords are used outside `local`/`dev` profiles.

### Driver Dependencies

- **PostgreSQL**: Transitive via `spring-boot-starter-data-jpa`.
- **Oracle**: Child services must add `com.oracle.database.jdbc:ojdbc11` to their `pom.xml` when enabling Oracle datasources (`brok`, `integration`).

### SQL Logging

When `sql-logging.enabled=true`, the `DataSource` bean is wrapped with `datasource-proxy` `ProxyDataSource` using `DefaultQueryLogEntryCreator` in multiline mode. SQL is logged at DEBUG level via SLF4J. Slow queries (above `slow-query-threshold-ms`) are logged at WARN level.

### Troubleshooting

| Issue | Cause | Resolution |
|-------|-------|------------|
| `BeanCreationException: url is required` | `enabled=true` but `url` is missing | Set `company.datasource.<name>.url`. |
| `BeanCreationException: Failed to load driver class` | JDBC driver not on classpath | Add PostgreSQL or Oracle driver dependency. |
| Connection refused | DB host/port unreachable | Verify network, firewall, and DB status. |
| Pool exhaustion | `maximum-pool-size` too low for concurrency | Increase pool size or add DB read replicas. |
| SSL errors | Missing or invalid SSL config | For Azure PostgreSQL, use `sslmode=verify-full` with certs in JDBC URL. |

---

## Existing Features

### Logging
Configure via `company.logging.*` properties. Includes correlation ID filters, request/response logging, masking of sensitive fields, and optional JSON encoding.

### JWT
Configure via `company.jwt.*` properties. Provides token generation, validation, and a stateless security filter chain.

### Scheduler
Configure via `company.scheduler.*` properties. Supports CRON and fixed-rate job scheduling with a thread pool.
