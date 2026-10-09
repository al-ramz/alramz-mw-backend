# Database Migrations with Liquibase

## 1. What this pattern is / when to use it

Every schema change in this repo — new table, new column, new index, seed/reference data, a rename — goes in as a **Liquibase "formatted SQL" changeset**, never as a hand-run `ALTER TABLE` or an ORM auto-DDL. This is the *only* sanctioned way to evolve a service's schema.

Two services currently use it:

- `services/data-validation-service` — the original adopter (H2 in tests, Postgres-compatible SQL in real profiles).
- `services/reference-data-service` — adopted the identical pattern later (confirmed by reading its `db/changelog/` tree — it mirrors `data-validation-service` file-for-file in structure).

If you're scaffolding a new service that needs its own schema (not just reading another service's DB), copy this structure from `reference-data-service` — it's the cleaner, more recent example.

## 2. Where it lives in this repo

Both services use the same shape:

```
services/<service>/src/main/resources/db/changelog/
├── dev/
│   ├── db.changelog-master.yaml
│   └── sql/
│       ├── 001-schedule-job.sql
│       ├── 002-...sql
│       └── ...
├── docker/
│   ├── db.changelog-master.yaml
│   └── sql/...
├── preprod/
│   ├── db.changelog-master.yaml
│   └── sql/...
├── prod/
│   ├── db.changelog-master.yaml
│   └── sql/...
└── (test master + sql live under src/test/resources/db/changelog/test/, not src/main)
```

Real example — `services/reference-data-service/src/main/resources/db/changelog/dev/db.changelog-master.yaml`:

```yaml
databaseChangeLog:
  - includeAll:
      path: db/changelog/dev/sql
```

Each profile's master file **only** points at its own profile's `sql/` folder — see §5 for why this matters.

The active master is selected per Spring profile in `application-<profile>.yml`:

```yaml
# services/reference-data-service/src/main/resources/application-dev.yml:2-3
spring:
  liquibase:
    change-log: classpath:db/changelog/dev/db.changelog-master.yaml
```

Every profile (`dev`, `docker`, `preprod`, `prod`) has this same two-line block pointing at its own master. The `test` profile is configured from `src/test/resources/application.yml`:

```yaml
# services/data-validation-service/src/test/resources/application.yml:4-5
spring:
  liquibase:
    change-log: classpath:db/changelog/test/db.changelog-master.yaml
```

with its own changelog tree under `src/test/resources/db/changelog/test/sql/`.

**Dependency + tooling** (`pom.xml`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-liquibase</artifactId>
</dependency>
```
(version managed by `alramz-common-bom`, currently `4.27.0`)

Plus a Maven plugin binding used for the `liquibase:update`/`liquibase:diff`-style CLI usage against a local H2 DB:

```xml
<plugin>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-maven-plugin</artifactId>
    <version>4.27.0</version>
    <configuration>
        <changeLogFile>src/main/resources/db/changelog/dev/db.changelog-master.yaml</changeLogFile>
        <driver>org.h2.Driver</driver>
        <classpath>src/main/resources</classpath>
        <url>jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL</url>
        <username>sa</username>
        <password></password>
    </configuration>
</plugin>
```

Note `MODE=PostgreSQL` on the H2 URL — that's what lets Postgres-flavored SQL (`BIGSERIAL`, `JSONB`, `ON CONFLICT ... DO NOTHING`) run against H2 in dev/test.

## 3. How it works

- Spring Boot's Liquibase autoconfiguration reads `spring.liquibase.change-log` at startup and runs whatever changesets in that file (and everything it `includeAll`s) haven't been applied yet, tracked in the `DATABASECHANGELOG` table Liquibase creates automatically.
- `includeAll: path: db/changelog/<profile>/sql` pulls in **every** file in that folder, applying them in **filename lexical order** — this is why the `NNN-` numeric prefix matters: it's the literal apply order, not just a label.
- Each file can hold **one or more** `--changeset <author>:<unique-id>` blocks. Liquibase tracks each changeset independently by `(author, id, filename)` and stores a checksum of its SQL. If that combination has already run, it's skipped; if the file's content changes after it already ran, Liquibase raises a checksum-mismatch error at startup — the app won't boot. This is the mechanism behind "never edit an applied changeset."
- Profiles are **fully isolated** changelog trees — `dev/sql`, `docker/sql`, `preprod/sql`, `prod/sql` are separate folders with (currently) duplicated content, not one shared set of changesets included everywhere. See §5, item 1 — this has a real consequence you need to know about.

## 4. How to add a new migration

### 4a. In an existing service (data-validation-service or reference-data-service)

**Step 1 — find the next number.** List the target profile's `sql/` folder and take the highest numeric prefix + 1. As of this writing:

| Service | Folder | Highest existing | Next file starts with |
|---|---|---|---|
| `data-validation-service` | `dev/sql`, `docker/sql`, `preprod/sql`, `prod/sql` | `006-...` | `007-` |
| `reference-data-service` | `dev/sql`, `docker/sql`, `preprod/sql`, `prod/sql` | `004-...` | `005-` |

(Always re-check by listing the folder yourself — these numbers move.)

**Step 2 — write the file**, named `NNN-short-description.sql`, e.g. `007-add-customer-status-column.sql`:

```sql
--liquibase formatted sql
--changeset <your-name>:007-add-customer-status-column

ALTER TABLE customer ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_customer_status ON customer(status);

--rollback ALTER TABLE customer DROP COLUMN status;
```

Rules for the header, taken directly from real files in this repo:
- First line is `--liquibase formatted sql` (no leading `#`/blank required, but keep it as the first content line).
- `--changeset <author>:<id>` — `<author>` has been either a real name (`gaurav`) or a shared token (`alramz`, `liquibase`) in existing files; pick your name. `<id>` must be **unique within the file** — convention in this repo is to reuse the filename's number+description as the id (e.g. `007-add-customer-status-column`), though one existing file (`data-validation-service/.../002-jwt-tables.sql`) uses changeset ids `003`, `004`, `005` that don't match its own filename number `002` — don't copy that inconsistency; keep filename number and changeset id aligned in new files.
- A single file **can** hold multiple `--changeset` blocks (see `002-jwt-tables.sql`, which has three), but prefer **one logical change per file** for anything you write now — easier to review and roll back independently.
- Add a `--rollback <SQL>;` line whenever the forward statement is reversible (`CREATE TABLE` → `DROP TABLE`, `ADD COLUMN` → `DROP COLUMN`). Real example: `data-validation-service/.../004-api-audit-log.sql` ends with `--rollback DROP TABLE IF EXISTS api_audit_log;`. Today this is the **only** file in the repo with a rollback statement — treat it as the standard to follow, not the exception.
- Use `IF NOT EXISTS` / `IF EXISTS` guards on `CREATE TABLE`, `CREATE INDEX`, and `ALTER ... RENAME` where you can — every recent changeset in `reference-data-service` does this, and it makes the changeset idempotent-safe if the changelog table and actual DB schema ever drift.

**Step 3 — decide shared vs. profile-specific, and copy to every profile that needs it.** Unlike a typical Liquibase setup, this repo does **not** have one master that all profiles share — each profile folder is independent (see §5.1). In practice, most real changes here have been copied byte-for-byte into `dev/sql/`, `docker/sql/`, `preprod/sql/`, and `prod/sql/` under the same filename. If your change is genuinely profile-only (e.g. dev-only seed data), put it *only* in that profile's folder — but if it's a real schema change meant to reach prod, **add the identical file to all four folders** (`dev`, `docker`, `preprod`, `prod`), plus the `test` profile under `src/test/resources/db/changelog/test/sql/` if your tests touch that table.

**Step 4 — wire it into the test profile too** if the table is used by any `@SpringBootTest`/repository test: add the same file under `src/test/resources/db/changelog/test/sql/`.

**Step 5 — verify.** Run the service's test suite (Liquibase runs against the in-memory H2 test DB on context load):

```
mvn -pl services/<service> -am test
```

A checksum or SQL error here means either a typo in the changeset or an accidental edit to a previously-applied file.

### 4b. Wiring Liquibase into a brand-new service that doesn't have it yet

Both current services already have it, but if you scaffold a service that needs its own writable schema:

1. Add the dependency (version comes from the BOM, no need to pin it):
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-liquibase</artifactId>
   </dependency>
   ```
2. Create the folder tree under `src/main/resources/db/changelog/{dev,docker,preprod,prod}/sql/` and a `db.changelog-master.yaml` in each profile folder containing:
   ```yaml
   databaseChangeLog:
     - includeAll:
         path: db/changelog/<profile>/sql
   ```
3. Add the corresponding two-line block to each `application-<profile>.yml`:
   ```yaml
   spring:
     liquibase:
       change-log: classpath:db/changelog/<profile>/db.changelog-master.yaml
   ```
4. Create the same tree under `src/test/resources/db/changelog/test/sql/` + `src/test/resources/db/changelog/test/db.changelog-master.yaml`, and point `src/test/resources/application.yml` at it.
5. Add the `liquibase-maven-plugin` block (copy verbatim from `data-validation-service/pom.xml` or `reference-data-service/pom.xml`) if you want local `mvn liquibase:update`/`liquibase:diff` support against H2.
6. Write your first changeset as `001-<description>.sql` in each profile folder.

## 5. Common pitfalls / anti-patterns

1. **There is no real "shared" changelog folder today, despite appearances.** `data-validation-service` has a `src/main/resources/db/changelog/sql/` folder (no profile prefix) containing `004-reference-data-table.sql` and `005-reference-data-inserts.sql.bak`, plus a root `db/changelog/db.changelog-master.yaml` that includes only that folder. **Nothing references that root master** — every `application-<profile>.yml` points straight at `db/changelog/<profile>/db.changelog-master.yaml`, which only includes its own `<profile>/sql` folder. So `004-reference-data-table.sql` is dead/orphaned unless a profile folder happens to duplicate it, and `005-reference-data-inserts.sql.bak` is disabled by its `.bak` extension (Liquibase's `includeAll` won't pick up a non-`.sql` file). **Don't build on the assumption of a shared folder** — if you want a change applied everywhere, copy the file into every profile folder explicitly (see step 4a-3), and if you're cleaning this up, either wire the root master into every profile or delete the orphaned folder.

2. **Never edit or renumber an already-applied changeset.** Liquibase stores a checksum per `(author, id, file)` in `DATABASECHANGELOG`; changing the SQL after it's shipped to any environment breaks startup there with a checksum-mismatch error. If you need to fix a mistake, add a new changeset (e.g. `ALTER TABLE ... DROP COLUMN ...` followed by a corrected `ADD COLUMN`), never touch the old file.

3. **Filename number and changeset id can drift — don't let them.** `data-validation-service/.../dev/sql/002-jwt-tables.sql` contains changesets `003-create-jwt-user-table`, `004-create-jwt-refresh-token-table`, `005-create-jwt-indexes` — none matching the `002` filename prefix. This is confusing when scanning for "what's changeset 003" and it's the reason `reference-data-service` ended up with two files both prefixed `003-` (`003-cache-audit-log.sql` and `003-rename-to-application-workflow-locks.sql`) — the numbering had already drifted from the changeset ids. Keep filename prefix == changeset id prefix in anything new.

4. **Add `--rollback` — almost nothing in this repo does, but one file shows the standard.** `data-validation-service/.../004-api-audit-log.sql` is the only changeset with a `--rollback` line today. Follow it, don't follow the majority.

5. **Postgres-only SQL syntax works because of the H2 compatibility mode**, not because the target DB is Postgres in dev. `BIGSERIAL`, `JSONB`, `CHECK (col IN (...))`, `ON CONFLICT ... DO NOTHING` all run against H2 because of `MODE=PostgreSQL` on the test JDBC URL. If you introduce a genuinely Postgres-specific feature (e.g. a Postgres-only function), verify it also works — or is conditionally skipped — against whatever the `preprod`/`prod` real database actually is before assuming dev-test parity proves it.

6. **Copy-paste across profile folders is manual and easy to forget.** Because there's no real sharing mechanism (pitfall #1), it's easy to add a changeset to `dev/sql` and forget `preprod/sql` and `prod/sql`. Grep for your new filename across all profile folders before committing:
   ```
   find services/<service>/src -path "*/db/changelog/*/sql/*<your-filename>*"
   ```
   It should appear once per profile you intend to affect (typically 4: dev, docker, preprod, prod — plus test if applicable).

## 6. Checklist

- [ ] Identified the highest existing numeric prefix in the target profile folder(s) and used the next one.
- [ ] File named `NNN-short-description.sql`, one logical change (prefer single changeset per file).
- [ ] Header is `--liquibase formatted sql` + `--changeset <author>:<id>`, with `<id>` matching the filename's number+description.
- [ ] `IF NOT EXISTS`/`IF EXISTS` guards used where applicable.
- [ ] `--rollback` statement included when the change is reversible.
- [ ] Identical file copied into every profile folder that should receive the change (`dev`, `docker`, `preprod`, `prod`), plus `src/test/resources/.../test/sql/` if the tests touch it.
- [ ] Did **not** touch any previously-applied changeset's SQL — new problems get new files.
- [ ] `mvn -pl services/<service> -am test` passes (Liquibase applies cleanly against H2 on context load).
