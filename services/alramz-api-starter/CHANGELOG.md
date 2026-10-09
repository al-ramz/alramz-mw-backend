# Changelog

All notable changes to this project will be documented in this file. This project adheres to [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Added

- Multi-datasource framework with conditional auto-configuration for `middleware` (PostgreSQL), `brok` (Oracle), and `integration` (Oracle).
- `PWProtector` AES/GCM encryption utility with split property format (`password` + `password-vector`).
- `DatasourceProperties` with nested configs, validation groups, and sensible defaults (`keepaliveTime=30000`, `leakDetectionThreshold=0`, `registerMbeans=false`).
- Per-datasource `JdbcTemplate`, `NamedParameterJdbcTemplate`, `DataSourceTransactionManager`, and `HikariPoolHealthIndicator`.
- Configurable startup validation (`startup-validation.enabled`), plaintext password fallback (`plainPassword`), database-specific driver tuning (`oracle.*`, `postgres.*`), and SQL logging via `datasource-proxy` with multiline output (`sql-logging.enabled`, `log-parameters`, `slow-query-threshold-ms`).
- Oracle-specific driver properties: `connect-timeout`, `read-timeout`, `default-row-prefetch`.
- PostgreSQL-specific properties: `ssl` (auto-disabled for local), `prepare-threshold`.

### Changed

- Metrics via Micrometer/OpenTelemetry; JMX disabled by default (`registerMbeans=false`).
- Version bump strategy: `1.0-SNAPSHOT` → `1.1.0-SNAPSHOT`.

### Deprecated

- None.

### Removed

- None.

### Fixed

- None.

### Security

- Password fields (`password`, `password-vector`) must not be exposed via Actuator `/actuator/env` or `/actuator/configprops`.
- Plaintext password fallback emits WARN log outside `local`/`dev` profiles.
