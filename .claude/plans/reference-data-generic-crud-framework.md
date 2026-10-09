# Reference Data Service — Generic CRUD Framework + Angular Admin Portal

Implementation plan. Supersedes the originally pasted design doc — the decisions below were
reached by reviewing that doc against this repo's actual state (as of 2026-09-23) and correcting
it where it conflicted with existing conventions or with decisions made during design review.

## 1. Why this differs from the original doc

The original doc was written without awareness of two things already true in this repo:

1. `alramz-api-starter` already has a generic, JDBC-backed reference-lookup mechanism
   (`com.alramz.globalconfigurationsettings`, renamed from `ReferenceData` on 2026-09-23,
   commit `67f89f0`). This plan builds a **separate, larger** system for tabular reference data
   (country/currency/document_type/etc. with many columns, ACL, audit, CSV import). The two are
   not the same thing and are not being merged — `GlobalConfigurationSettings` stays as-is for
   simple identifier/type/text lookups; this plan adds a new `com.alramz.referencedata` feature
   for full tabular reference data management. **Confirm this separation is intended** before
   starting — if the two should actually be unified, Part 6 below changes.
2. Liquibase changelogs in this repo are **per-profile only** — there is no shared
   `db/changelog/sql/` directory. Each of `dev/`, `docker/`, `preprod/`, `prod/` has its own
   `sql/` folder and its own `db.changelog-master.yaml` doing `includeAll`. Every new *system*
   changeset (see Part 4) must be added to all four profile folders (plus
   `src/test/resources/db/changelog/test/sql/` for tests), not one shared location.

## 2. Key design decisions (confirmed during review)

| Decision | Choice | Rationale |
|---|---|---|
| Reference-data table schema source | **CSV-only.** No YAML `tables:` block, no `DynamicTableService.createTableIfNotExists(schema)` from config. | Maximum flexibility — every table, including the three "starter" tables, is created through the same CSV input process. |
| Liquibase for reference-data tables | **Not used.** Reference-data tables (`country`, `currency`, any admin-uploaded table) are created/altered entirely by the CSV input process at runtime. | Explicit decision — these tables are business data, not schema, and change on an operational cadence Liquibase reviews can't keep up with. |
| Liquibase for *system* tables | **Still used**, normally, for `entity_acl`, `entity_metadata`, `reference_data_audit_log`, and `jwt_user` seed rows. | These have fixed schemas known at compile time and are part of the application's own contract, unlike reference-data tables. |
| Primary key for every reference-data table | **Always the surrogate `id BIGSERIAL`.** CSV columns are never treated as primary/unique keys. | Removes the need to infer or ask for a business key at import time; every table behaves identically for update/delete targeting. |
| Bootstrap tables (`country`, `currency`, `document_type`) | Just the first three entries in a `seed-data` list, imported through the exact same `CsvImportService` path an admin's upload goes through. | One creation code path instead of two. |
| Import modes | `APPEND` (add rows, reconcile new columns additively) and `REPLACE` (replace row data) — selected via UI radio button. See Part 5.3 for exact semantics (flagged as an assumption to confirm). | Per your instruction: "import data or replace data based on the radio button selected." |
| UI metadata (display name, icon, searchable fields, soft-delete, sort order) | **New `entity_metadata` table** in the middleware DB, Liquibase-managed, written to at CSV-import time. Served to Angular via `GET /api/reference-data/ui-config`, fetched at Angular bootstrap (APP_INITIALIZER), not baked into the Angular build. | Chosen over a static `UIconfig.json` Angular asset, over reusing `GlobalConfigurationSettings` (its `IDENTIFIER_TEXT` is `VARCHAR(500)`, too small), over Redis (not wired into any service today — no dependency, no connection config, no Key Vault secret in any environment), and over Blob Storage (needs new Terraform + client + secret for what is structurally just metadata rows). Matches every existing pattern in this codebase — `entity_acl`, `jwt_user`, `reference_data_audit_log` are all plain Postgres tables via the `middleware` datasource. |
| Generic layer module placement | `data-validation-service`, **not** `alramz-api-starter`. New feature package `com.alramz.referencedata`. | The whole feature (Angular UI, POI export, entity_acl/entity_metadata/audit schema) is specific to this one service. Putting it in the shared starter would force `alramz-notification-service` to carry config surface it will never use, unlike the starter's existing modules (JWT, datasource, logging, scheduler, global-config-settings), which are all genuinely reusable. **This is a deviation from the originally pasted doc — flagging explicitly in case a second consumer is actually planned.** |

## 3. Package layout (`data-validation-service`)

Following this repo's package-by-feature convention (see `com.alramz.globalconfigurationsettings`
in the starter, or any feature package in `data-validation-service`):

```
com.alramz.referencedata/
├── config/
│   ├── ReferenceDataProperties.java        # company.reference-data.* (api-key, angular-ui toggle only — no tables:/seed-data schema binding beyond source/mode/enabled/displayName)
│   └── ReferenceDataAutoConfiguration.java # @ConditionalOnProperty(company.reference-data.enabled)
├── controller/
│   ├── GenericCrudController.java          # POST /api/reference-data/crud
│   ├── CsvImportController.java            # POST /api/reference-data/import-csv
│   ├── AclController.java                  # GET  /api/reference-data/acl
│   ├── AuditLogController.java             # GET  /api/reference-data/audit
│   ├── DashboardController.java            # GET  /api/reference-data/dashboard
│   ├── UiConfigController.java             # GET  /api/reference-data/ui-config
│   └── ReferenceDataExportController.java  # GET  /api/reference-data/export
├── service/
│   ├── GenericCrudService.java
│   ├── DynamicTableService.java            # DDL executor (CREATE/ALTER only, never DROP column)
│   ├── CsvImportService.java               # type inference + bulk insert + entity_metadata upsert
│   ├── AclService.java
│   ├── AuditLogger.java
│   └── ExportService.java                  # Apache POI SXSSFWorkbook
├── repository/
│   ├── GenericTableRepository.java         # single JDBC repo for ALL reference-data tables
│   ├── EntityMetadataRepository.java
│   ├── EntityAclRepository.java            # Spring Data JPA
│   └── ReferenceDataAuditLogRepository.java
├── model/
│   ├── EntityMetadataEntity.java           # @Entity, table entity_metadata
│   ├── EntityAclEntity.java                # @Entity, table entity_acl
│   └── ColumnDef.java                      # inferred-from-CSV column metadata (name, sqlType, length)
├── dto/
│   ├── GenericCrudRequest.java / GenericCrudResponse.java / GenericCrudErrorResponse.java
│   ├── CsvImportRequest.java / CsvImportResponse.java / ImportMode.java
│   ├── EntityAcl.java (DTO) / DashboardResponse.java / TableSummary.java
│   └── UiConfigEntry.java
└── exception/
    ├── ReservedTableNameException.java
    ├── InvalidIdentifierException.java
    ├── AccessDeniedException.java (or reuse an existing typed exception if one already fits)
    └── SchemaConflictException.java        # header/column mismatch on APPEND, see Part 5.3
```

All JDBC access uses `@Qualifier("middlewareNamedParameterJdbcTemplate")` /
`@Qualifier("middlewareJdbcTemplate")`, matching the existing convention (see
`JdbcGlobalConfigurationSettingsRepository`). `EntityMetadataEntity` / `EntityAclEntity` can be
plain JPA repositories since `DatasourceConfiguration` marks the middleware datasource `@Primary`
(no qualifier needed there per the existing exception already documented in CLAUDE.md).

## 4. System tables (Liquibase-managed)

Next available changeset file number in `dev/sql/` (and mirrored in `docker/`, `preprod/`,
`prod/`, and `src/test/resources/db/changelog/test/sql/`) is **007** — current files run
`001`–`006` (`001-schedule-job.sql` … `006-insert-global-configuration-settings.sql`).

| File | Content |
|---|---|
| `007-create-entity-metadata-table.sql` | `entity_metadata` — see schema below |
| `008-create-entity-acl-table.sql` | `entity_acl` — as originally specified |
| `009-create-reference-data-audit-log-table.sql` | `reference_data_audit_log` (JSONB old/new values) |
| `010-seed-users.sql` | Seed `superadmin` / `admin` / `user` into the **existing** `jwt_user` table (see note below — no seed rows exist there today) |
| `011-seed-acl-rules.sql` | Idempotent `INSERT ... WHERE NOT EXISTS` ACL rows — but **not** for `country`/`currency`/`document_type` by name anymore, since those tables don't exist until someone imports them (see Part 5.4) |

### `entity_metadata`

```sql
CREATE TABLE entity_metadata (
    id BIGSERIAL PRIMARY KEY,
    table_name VARCHAR(63) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL,
    icon VARCHAR(100),
    searchable_fields JSONB,                 -- e.g. ["code","name"]
    soft_delete BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    is_dynamic BOOLEAN NOT NULL DEFAULT TRUE, -- always true under CSV-only creation; kept for future-proofing
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_entity_metadata_table_name ON entity_metadata(table_name);
CREATE INDEX idx_entity_metadata_sort_order ON entity_metadata(sort_order);
```

`table_name` is `VARCHAR(63)` to match Postgres's identifier length limit — validated at import
time (Part 5.2), not just at the DB layer.

### `entity_acl` and `reference_data_audit_log`

Unchanged from the originally pasted doc (Parts 3.1 and 6.1 there) — reproduced here for
completeness in the actual implementation, not re-derived.

### Seeding `jwt_user`

No seed data exists for `jwt_user` today (verified — no `INSERT INTO jwt_user` anywhere in the
repo). The table's actual columns are `id, username, email, password, roles, enable, application,
environment, created_at` — note `enable` is `VARCHAR(1)` (`'Y'`/`'N'`), not boolean, and `roles`
is a single delimited string column (see `User.java`/`JwtAuthFilter` — roles become
`ROLE_<value>` authorities), not a separate roles table. Seed three rows
(`superadmin`/`admin`/`user`) with BCrypt-hashed passwords, `enable='Y'`.

## 5. Reference-data tables (no Liquibase — CSV-driven)

### 5.1 Base structure

Every reference-data table, whenever created, gets:

```sql
CREATE TABLE IF NOT EXISTS {table_name} (
    id BIGSERIAL PRIMARY KEY,
    {csv_inferred_columns...},
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Executed via `JdbcTemplate` (DDL statements aren't parameterizable — see 5.2 for how identifiers
are made safe instead).

### 5.2 Identifier safety (this is now the only safety net — no Liquibase review gate)

Before any `CREATE TABLE` / `ALTER TABLE ... ADD COLUMN` runs, both the table name and every
column name (all sourced from user-controlled CSV headers or the import form) must pass:

1. **Format check**: `^[a-z][a-z0-9_]{0,62}$` (lowercase, starts with a letter, ≤63 chars —
   Postgres identifier limit). Headers are normalized (lowercased, spaces/hyphens → `_`) before
   this check; anything that still fails is rejected with a per-column error, not silently
   dropped or renamed.
2. **Reserved word check**: reject if the identifier is a Postgres/SQL reserved word.
3. **Existing-object check**: reject table names matching any object already in the middleware
   schema — `jwt_user`, `jwt_refresh_token`, `global_configuration_settings`, `entity_acl`,
   `entity_metadata`, `reference_data_audit_log`, `api_audit_log`, `schedule_job`,
   `dfm_onboarding_requests`, plus Liquibase's own tracking tables (`databasechangelog`,
   `databasechangeloglock`). Sourced from a maintained constant list, not a live schema query
   (avoids a race and keeps the check fast).
4. Column names go through the same three checks, scoped to reserved words that would break the
   generated SQL (e.g. `id`, `created_at`, `updated_at`, `is_active` are reserved for the base
   columns above and rejected as CSV header names).

This is the change that replaces Liquibase's review gate for these tables — treat it as
security-critical, not a formatting nicety.

### 5.3 APPEND vs REPLACE — proposed semantics (confirm before implementing)

The instruction was "import data or replace data based on the radio button selected." Read
literally this is about **data**, not schema, so the default proposed here is:

- **Table doesn't exist yet** (first import for this `table_name`): infer columns from the CSV
  header row, `CREATE TABLE`, insert all rows, create the `entity_metadata` row. Mode selection is
  irrelevant on first import.
- **Table exists, mode = APPEND**: CSV headers must be a subset of, or introduce only new columns
  to, the existing table.
  - Headers matching existing columns: insert as new rows.
  - Headers not matching any existing column: `ALTER TABLE ... ADD COLUMN` (additive only — never
    drops or retypes an existing column), then insert.
  - Never truncates existing rows.
- **Table exists, mode = REPLACE**: `TRUNCATE` existing data rows (not `DROP TABLE` — preserves
  the table, its `id` sequence continuity is not guaranteed to matter since IDs aren't referenced
  externally), reconcile columns the same additive way as APPEND, then bulk insert the new file's
  rows as if starting fresh.
- **A REPLACE upload with fewer columns than the existing table**: the now-unused existing columns
  are left in place (nulled out for the new rows) rather than dropped — dropping a column
  destructively without a Liquibase-style review gate is out of scope for MVP. Surface this in the
  import preview/response so the admin sees which existing columns will go empty.

**This is the one part of the plan not yet explicitly confirmed with you — flag if the intent was
actually for REPLACE to mean "drop and recreate the table with the new file's schema."**

### 5.4 CSV import flow (`CsvImportService`)

1. Validate `tableName` (5.2), read `displayName`, `icon`, `searchableFields`, `softDelete` from
   the import form (admin-provided; for bootstrap `seed-data` entries these come from
   `application.yml`).
2. Read CSV header row → normalize → validate each header (5.2).
3. Infer type per column from the first non-null sample value in that column (string → `VARCHAR`,
   numeric → `BIGINT`, `true`/`false` → `BOOLEAN`, ISO date pattern → `TIMESTAMP`, else
   `VARCHAR(255)`).
4. `DynamicTableService.tableExists(tableName)` → branch per 5.3.
5. Stream remaining rows with `BufferedReader`, batch insert (batches of 1000).
6. Upsert the `entity_metadata` row (insert on first import, update `display_name`/`icon`/
   `searchable_fields`/`updated_at` on subsequent imports if the form values changed).
7. On ACL: if this is the table's first-ever creation, **no ACL rows are inserted automatically**
   — matches the original doc's "dynamic tables: SUPER_ADMIN only until explicit ACL rows are
   added" default. The importing admin (who is by definition `SUPER_ADMIN` or has `can_import`
   already) must separately grant ACL via direct `entity_acl` management (out of scope for MVP
   per the original doc — DB-edit only).
8. `AuditLogger` logs the import itself as an operation (`operation='IMPORT'`, `new_values` =
   summary: row count, mode, columns added) in `reference_data_audit_log`, in addition to the
   per-row audit that `GenericTableRepository` writes for CREATE/UPDATE/DELETE through the CRUD
   endpoint.

### 5.5 Startup bootstrap

`ReferenceDataAutoConfiguration` runs `CsvImportService.importCsv(...)` for each `seed-data` entry
in `application.yml` where `enabled=true`, in declared order, at application startup — using the
exact same code path as an admin's manual upload (no separate `DynamicTableService.
createTableIfNotExists(schema)` path). `application.yml` shape:

```yaml
company:
  reference-data:
    enabled: true
    angular-ui:
      enabled: true
    seed-data:
      - table: country
        display-name: Countries
        icon: pi pi-globe
        searchable-fields: [code, name]
        soft-delete: true
        source: classpath:seed-data/countries.csv
        mode: REPLACE
        enabled: true
      - table: currency
        display-name: Currencies
        icon: pi pi-money-bill
        searchable-fields: [code, name]
        soft-delete: false
        source: classpath:seed-data/currencies.csv
        mode: REPLACE
        enabled: true
    api-key:
      enabled: true
      header-name: X-Api-Key
```

## 6. Generic CRUD layer

Unchanged in shape from the originally pasted doc (Parts 2.3–2.6): `GenericTableRepository`
builds all SQL from `ColumnDef` metadata read at request time via
`DynamicTableService.getTableColumns(tableName)` (columns are no longer known from YAML, so
`GenericCrudService` fetches them fresh — or from a short-TTL cache invalidated on
create/import — rather than from an `EntityRegistry` populated once at startup, since tables can
now appear at any time via CSV upload). All queries parameterized via `JdbcTemplate`; only
identifiers (table/column names in generated SQL text) are the ones needing the 5.2 validation,
re-checked on every request against the actual DB metadata (not just trusted from the request),
so a request naming a column that doesn't exist on the table fails cleanly instead of building
invalid SQL.

`POST /api/reference-data/crud` request/response DTOs: unchanged from the original doc (Part 2.4).

## 7. ACL

Unchanged from the original doc (Part 3), except: no special-cased default ACL rows for
`country`/`currency`/`document_type` at Liquibase-seed time, since those tables don't exist until
their `seed-data` import runs at startup — seed reasonable default ACL rows for them via
`CsvImportService` itself right after their bootstrap import (still `SUPER_ADMIN`/`ADMIN` full,
`USER` read+export, matching the original table in Part 3.5), not via a Liquibase changeset that
assumes the table pre-exists.

## 8. Audit trail

Unchanged from the original doc (Part 6), with the addition noted in 5.4 step 8 (import
operations are logged too, not just row-level CRUD).

## 9. Dashboard

Unchanged from the original doc (Part 7). Row counts via `GenericTableRepository.count()`;
table list via `EntityMetadataRepository` (replaces `EntityRegistry` as the source of "what
tables exist").

## 10. Excel export

Unchanged from the original doc (Part 10) — `poi-ooxml` in `data-validation-service/pom.xml`,
`SXSSFWorkbook` streaming export.

## 11. UI config API

New — not in the original doc, added per the `entity_metadata` decision in Part 2.

- `GET /api/reference-data/ui-config` → `List<UiConfigEntry>`, one per active `entity_metadata`
  row the caller's roles can at least `can_read`, ordered by `sort_order`. Response shape:
  `{ tableName, displayName, icon, searchableFields, softDelete }[]`.
- This is a distinct endpoint from `GET /api/reference-data/acl` (Part 3.4/7) — `ui-config` is
  display metadata, `acl` is the per-action permission matrix. Angular's `APP_INITIALIZER` calls
  both before rendering the shell (parallel, both gated behind the existing JWT filter — this
  endpoint is **not** a public/`permit-all-urls` endpoint, since the sidebar content itself is
  role-sensitive).

## 12. Angular admin portal (`services/reference-data-portal/`)

Structure carried over from the original doc (Part 9), with one change: no static
`assets/UIconfig.json`. An `APP_INITIALIZER` (or equivalent bootstrap provider) calls
`GET /api/reference-data/ui-config` (after auth, so this runs post-login, not pre-login) and
`GET /api/reference-data/acl`, caches both in `EntityConfigService`/`AclService` for the session,
before the shell/sidebar renders. This means a newly CSV-imported table appears in the UI on next
login/session-refresh with zero Angular rebuild or redeploy — the property this whole redesign
was meant to preserve.

All other Angular pieces (Part 9.4 components, Part 12 JWT flow, Part 13 API key layer) unchanged
from the original doc.

## 13. Build & CI/CD impact (requires explicit sign-off — touches `.github/workflows/` and infra concerns)

CLAUDE.md's guardrail is: don't touch `infra/` or `.github/workflows/` unless the task explicitly
requires it. This task does require it, for the following reasons — call this out before doing it:

1. `frontend-maven-plugin` in `data-validation-service/pom.xml` at `generate-resources` means
   **every** `mvn test-compile` / `mvn test` invocation — including the CLAUDE.md "fast compile
   check" command used for routine local iteration — will try to `npm install && npm run build`
   unless scoped to a Maven profile (e.g. bind it to `package` phase only, or gate behind
   `-Pfrontend`, default-off). Recommend: **default off**, only runs in the release/Docker build
   profile, so local `mvn -pl services/data-validation-service -am test` stays Java-only and fast.
2. GitHub Actions (`.github/actions/maven-test`, `.github/workflows/cicd.yml`) need a Node setup
   step + npm cache added for the path-filtered `data-validation-service` build, and the
   Dockerfile becomes multi-stage (Node build → Maven build → JRE runtime, per the original doc's
   Part 11.2).
3. None of this touches `infra/` (Terraform) — only the service's own `pom.xml`, `Dockerfile`, and
   the relevant composite GitHub Action.

## 14. Migration path (staged, not one PR)

| Stage | Scope | Verification |
|---|---|---|
| 1 | `entity_metadata`, `entity_acl`, `reference_data_audit_log` Liquibase changesets (007–009) across all four profiles + test profile; `jwt_user` seed (010) + ACL rule seed (011, minus country/currency/document_type) | `mvn -pl services/data-validation-service -am clean test` |
| 2 | `com.alramz.referencedata` skeleton in `data-validation-service`: `ReferenceDataProperties`, `ReferenceDataAutoConfiguration` (off by default), `DynamicTableService` (with 5.2 identifier validation, unit-tested against H2/Postgres-specific reserved words), `GenericTableRepository` | `mvn -pl services/data-validation-service -am clean test-compile`, then `test` once repository tests exist (H2) |
| 3 | `CsvImportService` (5.4/5.3 semantics), `CsvImportController`, wired to `entity_metadata` upsert | `CsvImportServiceTest` (H2), `CsvImportControllerIT` |
| 4 | `GenericCrudController`/`GenericCrudService`, `AclService`, `AuditLogger` wired in | `GenericCrudControllerIT` (verify 403 per entity ACL) |
| 5 | Startup bootstrap (5.5) using real seed CSVs for `country`/`currency`/`document_type` | Manual: boot the app, confirm tables + `entity_metadata` + default ACL rows appear |
| 6 | `DashboardController`, `AuditLogController`, `UiConfigController`, `ReferenceDataExportController` (+ `poi-ooxml` dependency) | `DashboardControllerIT`, `ExportControllerIT`, `AclControllerIT` |
| 7 | Angular scaffold (`reference-data-portal/`): login, shell, sidebar (driven by `ui-config` + `acl`), dashboard | Angular unit tests + manual E2E |
| 8 | Angular entity list/form, CSV import UI, audit log UI, export UI | Angular unit tests + manual E2E |
| 9 | `frontend-maven-plugin` (profile-gated per Part 13), multi-stage `Dockerfile`, GH Actions Node step | Full `mvn clean package` locally with the frontend profile active; confirm CI path-filtering still only builds this service on relevant changes |
| 10 | E2E: login as each of the three seeded roles, verify ACL enforcement, CRUD, CSV import (both modes), multi-delete, audit log, Excel export, dashboard counts, `angular-ui.enabled=false` falls back to API-only | Manual, per original doc's Part "Validation Plan" §4 |

Each stage should land as its own PR against `feature/git-governance` (or whatever the actual
feature branch ends up being) — this is a multi-week build, and CLAUDE.md's "run the targeted test
before calling it done" rule is much easier to honor per-stage than on one giant diff.

## 15. Open items to confirm before/while implementing

1. **Module placement** (`data-validation-service` vs. shared starter) — Part 2, flagged as a
   deviation from the original doc.
2. **REPLACE semantics** — Part 5.3, specifically whether a REPLACE upload with a different
   column set should drop-and-recreate the table instead of the additive-only approach proposed.
3. **`entity_metadata` exact columns** — Part 4 — in particular whether `sort_order` and
   `is_dynamic` are actually needed for MVP or are premature.
4. **ACL for bootstrap tables** — Part 7 — confirming CSV-import-time ACL seeding (vs. a
   Liquibase changeset) is the right mechanism now that those tables aren't guaranteed to exist
   at Liquibase-run time.
5. Everything under "Out of Scope" in the originally pasted doc still applies unless you say
   otherwise: no ACL management UI, no multi-tenancy, Excel-only export, no dark mode, no CSV
   column mapping UI beyond what's in 5.4, no audit retention UI, no full-text search, no
   table rename/drop from the UI.
