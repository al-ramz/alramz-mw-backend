# Scheduled / Background Jobs

## 1. What this pattern is, and when to use it

`alramz-api-starter` ships a full **DB-driven dynamic scheduler**, not plain Spring `@Scheduled` methods. A service enables it once at the application class, and from then on the actual jobs (which bean to run, on what cron/interval, enabled or not) live as **rows in a `schedule_job` table**, loaded and (re)scheduled at runtime — with a REST management API to stop/restart/refresh jobs without redeploying.

Use this pattern whenever a service needs periodic background work: cache refresh/reload, polling an external system, housekeeping/cleanup, health-check style self-tests, batch reconciliation, etc.

Do **not** reach for plain `@Scheduled(cron = "...")` methods in this codebase — that bypasses the starter's pooling, DB-driven enable/disable, and the `/api/secured/scheduler/*` management endpoints that ops already knows how to use.

Two real, currently-shipping examples exist:
- **`reference-data-service`** — `cacheReloadJobRunner`, reloads a Redis cache from Postgres on a cron trigger, with a DB-backed lock to avoid overlapping runs across pods. Added in commit `359e12a` ("Scheduled Jobs for Cache Upload").
- **`data-validation-service`** — `dataValidationJobRunner`, a lighter example that just runs a SQL lookup on a cron trigger.

## 2. Where it lives in this repo

**Starter infrastructure** (`services/alramz-api-starter/src/main/java/com/alramz/scheduler/`):

| File | Role |
|---|---|
| `EnableScheduler.java` | Marker annotation: `@EnableScheduler(jobGroupName = "...")` on the app's `@SpringBootApplication` class. |
| `config/SchedulerAutoConfiguration.java` | `@AutoConfiguration`, registered in `AutoConfiguration.imports`; enables `SchedulerProperties` + imports `SchedulerConfiguration`. |
| `config/SchedulerConfigCondition.java` / `ConditionOnScheduler.java` | The scheduler beans only activate if some bean in the context carries `@EnableScheduler` with a non-blank `jobGroupName` (read via `-DJobGroupName=...` VM arg or the annotation). |
| `config/SchedulerConfiguration.java` | Defines the `threadPoolTaskScheduler` bean, the `ISchedulerService` bean (`JobScheduleManager`), and an `ApplicationRunner` that calls `schedule(jobGroupName)` on startup. |
| `config/SchedulerProperties.java` | `@ConfigurationProperties(prefix = "company.scheduler")` — pool size, thread naming, `management-endpoints.enabled`, and a `jobs` list (YAML fallback path, see §3). |
| `service/Schedulable.java` | The interface **your job class implements**: `void run(ScheduleInfoBean scheduleInfoBean)`. |
| `service/impl/JobScheduleManager.java` | Loads job definitions (DB first, YAML fallback), wires each to `ThreadPoolTaskScheduler` as `CRON_EXP` / `FIXED_RATE` / `FIXED_DELAY` / `ONE_TIME`, and exposes stop/restart/refresh/unregister operations. |
| `entity/ScheduleJobEntity.java` + `repository/ScheduleJobRepository.java` | JPA view of the `schedule_job` table (used elsewhere in the starter; `JobScheduleManager` itself queries the table directly via `NamedParameterJdbcTemplate` — see §3). |
| `controller/SchedulerManagerController.java` | `/api/secured/scheduler/{stop,restart,refresh/jobs,unregister/jobs,stat,hardstop}` — only exposed when `company.scheduler.management-endpoints.enabled=true`. |

**Real usage — reference-data-service** (`services/reference-data-service/src/main/java/com/alramz/`):
- `ReferenceDataServiceApplication.java:14-15` — `@EnableScheduler(jobGroupName = "referenceData")`.
- `scheduler/job/CacheReloadScheduledJob.java` — `@Component("cacheReloadJobRunner")`, `implements Schedulable`.
- `service/SchedulerLockService.java` — DB-row-based distributed lock (`application_workflow_locks` table) so only one pod runs the job at a time.
- `service/RedisCacheService.java` — the actual work the job triggers (`reloadGlobalConfig(mappingName)`).
- `src/main/resources/db/changelog/dev/sql/001-schedule-job.sql`, `003-rename-to-application-workflow-locks.sql`, `004-scheduler-job.sql` — table + seed row (mirrored under `docker/`, `preprod/`, `prod/`, `test/`).
- `src/main/resources/application-dev.yml:38-43` (and per-profile equivalents) — `company.scheduler.*` properties.

**Real usage — data-validation-service** (simpler, no external cache, single query):
- `DataValidationServiceApplication.java:19` — `@EnableScheduler(jobGroupName = "dataValidation")`.
- `scheduler/job/DataValidationScheduledJob.java` — `@Component("dataValidationJobRunner")`, `implements Schedulable`, calls `SqlQueriesManager` for its query.
- `src/main/resources/db/changelog/dev/sql/001-schedule-job.sql` — table + seed row for `dataValidationHealthCheck`.

## 3. How it works end to end

1. **Enable it.** Your `@SpringBootApplication` class carries `@EnableScheduler(jobGroupName = "<yourGroup>")`. `SchedulerConfigCondition` checks for this at context-refresh time and, if found, sets the system property `JobGroupName` and lets `SchedulerConfiguration`'s beans activate. Without this annotation, none of the scheduler beans exist in your service — `SchedulerManagerController` and `JobScheduleManager` simply aren't created.
2. **Startup load.** `SchedulerConfiguration.schedulerStartupRunner` (an `ApplicationRunner`) reads the `JobGroupName` system property and calls `schedulerService.schedule(jobGroupName)`.
3. **Job source — DB first, YAML fallback.** `JobScheduleManager.schedule(...)` queries:
   ```sql
   SELECT job_group_name, schedule_id, worker_bean_name, job_bean_names, job_parameter,
          schedule_mode, cron_expr, delay, interval_seconds, enable
   FROM schedule_job
   WHERE job_group_name = :jobGroupName AND enable = 'Y'
   ```
   via the **middleware** `NamedParameterJdbcTemplate` (this is why the middleware datasource must be enabled — see `company.datasource.middleware.enabled: true` in `application-dev.yml`). If no DB rows are found (e.g. `middlewareNamedParameterJdbcTemplate` is null, or the table's empty), it falls back to the `company.scheduler.jobs[]` list from YAML — same shape as the DB row, just externalized differently. In both real examples, the DB row is the source of truth.
4. **Scheduling mode.** Per row, `scheduleMode` picks the Spring scheduling call:
   - `CRON_EXP` + `cronExpr` → `CronTrigger` via `threadPoolTaskScheduler.schedule(...)`
   - `FIXED_RATE` + `interval` (seconds, as millis) → `scheduleAtFixedRate(...)`
   - `FIXED_DELAY` + `delay` → `scheduleWithFixedDelay(...)`
   - `ONE_TIME` + a `startTime` → one-shot `schedule(...)`
   Both current jobs use `CRON_EXP`.
5. **Execution.** At trigger time, the manager looks up `beanFactory.getBean(workerBeanName)` — that's your `@Component("yourJobRunnerBeanName")` — casts it to `Schedulable`, and calls `run(scheduleInfoBean)`. The `scheduleInfoBean` carries `jobParameter`, which is how `CacheReloadScheduledJob` knows *which* cache mapping to reload (`scheduleInfoBean.getJobParameter()` → `mappingName`).
6. **Distributed lock (reference-data-service only).** Because multiple pods run the same cron independently, `CacheReloadScheduledJob` first calls `SchedulerLockService.tryAcquireLock(lockKey, Duration.ofMinutes(5))`, which does an `INSERT ... ON CONFLICT ... DO UPDATE ... WHERE expires_at < NOW()` against `application_workflow_locks` (middleware datasource) — an atomic "acquire only if nobody else holds it or it expired" pattern. It releases the lock in a `finally` block. `data-validation-service`'s job has no cross-pod side effect, so it skips locking entirely — **only add the lock table/service if your job writes shared state that must not run concurrently.**
7. **Management API.** With `company.scheduler.management-endpoints.enabled: true`, `SchedulerManagerController` exposes `/api/secured/scheduler/stop`, `/restart`, `/refresh/jobs`, `/unregister/jobs`, `/stat`, `/hardstop` — these sit behind the service's normal JWT security (they are **not** in `permit-all-urls` in either service), so calling them requires a valid token.

## 4. How to add a new scheduled job — step by step

Model this directly on `data-validation-service`'s simpler example, or on `reference-data-service`'s locking example if your job touches shared/external state (cache, external API, file system).

**Step 1 — enable the scheduler on your application class** (skip if already present):
```java
@SpringBootApplication
@EnableScheduler(jobGroupName = "yourServiceGroup")   // unique per service
public class YourServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourServiceApplication.class, args);
    }
}
```

**Step 2 — make sure the middleware datasource is enabled** (`application-<profile>.yml`):
```yaml
company:
  datasource:
    middleware:
      enabled: true
      url: ${COMPANY_DATASOURCE_MIDDLEWARE_URL:jdbc:postgresql://localhost:5432/<your-db>}
```
Without this, `middlewareNamedParameterJdbcTemplate` is null and the manager silently falls back to the YAML `company.scheduler.jobs[]` list — usually not what you want for anything beyond quick local testing.

**Step 3 — add `company.scheduler.*` properties** (per profile, following the existing naming convention):
```yaml
company:
  scheduler:
    management-endpoints:
      enabled: true
    pool-size: 5
    thread-group-name: YourService-Scheduled-Tasks
    thread-name-prefix: YourService-TaskScheduler-Worker-
```

**Step 4 — add a Liquibase changeset that creates the row** (in `data-validation-service`/`reference-data-service`, `db/changelog/sql/001-schedule-job.sql` creates the shared `schedule_job` table if it doesn't already exist — reuse that same DDL, then insert your job as a separate changeset, one file per profile under `db/changelog/{dev,test,preprod,prod,docker}/sql/`):
```sql
--liquibase formatted sql
--changeset <you>:001-create-schedule-job-table

CREATE TABLE IF NOT EXISTS schedule_job (
    id BIGSERIAL PRIMARY KEY,
    job_group_name VARCHAR(100) NOT NULL,
    schedule_id VARCHAR(100) NOT NULL,
    worker_bean_name VARCHAR(100) NOT NULL,
    job_bean_names VARCHAR(200),
    job_parameter VARCHAR(500),
    schedule_mode VARCHAR(20) NOT NULL,
    cron_expr VARCHAR(100),
    delay BIGINT,
    interval_seconds BIGINT,
    enable VARCHAR(1) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_schedule_job_group_schedule ON schedule_job(job_group_name, schedule_id);

--changeset <you>:002-seed-schedule-job-data

INSERT INTO schedule_job (job_group_name, schedule_id, worker_bean_name, job_bean_names,
    job_parameter, schedule_mode, cron_expr, delay, interval_seconds, enable, created_at, updated_at)
VALUES ('yourServiceGroup', 'yourJobId', 'yourJobRunnerBeanName',
    '', 'optional-job-parameter', 'CRON_EXP', '0 */5 * * * *', NULL, NULL, 'Y',
    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (job_group_name, schedule_id) DO NOTHING;
```
`job_group_name` must match your `@EnableScheduler(jobGroupName=...)` exactly. `worker_bean_name` must match your `@Component("...")` name below. Never edit this changeset once applied — add a new numbered file for schedule changes (per CLAUDE.md §4).

**Step 5 — write the job class**, implementing `Schedulable`:
```java
package com.alramz.scheduler.job;

import com.alramz.scheduler.model.ScheduleInfoBean;
import com.alramz.scheduler.service.Schedulable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("yourJobRunnerBeanName")   // must match worker_bean_name in the DB row
public class YourScheduledJob implements Schedulable {

    private static final Logger log = LoggerFactory.getLogger(YourScheduledJob.class);
    private final YourDependency yourDependency;

    public YourScheduledJob(YourDependency yourDependency) {
        this.yourDependency = yourDependency;
    }

    @Override
    public void run(ScheduleInfoBean scheduleInfoBean) {
        log.info("Executing {}: cronExpr={}, jobParameter={}",
                scheduleInfoBean.getScheduleId(), scheduleInfoBean.getCronExpr(), scheduleInfoBean.getJobParameter());
        try {
            yourDependency.doTheWork(scheduleInfoBean.getJobParameter());
        } catch (Exception e) {
            log.error("Job {} failed: {}", scheduleInfoBean.getScheduleId(), e.getMessage(), e);
        }
    }
}
```

**Step 6 — if the job mutates shared/external state across pods, add a lock** — copy `SchedulerLockService` verbatim (it's generic: `jobId` + `leaseDuration` in, boolean/void out) and call `tryAcquireLock`/`releaseLock`/`cleanStaleLocks` around your work, exactly as `CacheReloadScheduledJob` does. This needs its own `application_workflow_locks`-equivalent table — either reuse the name if you're comfortable sharing the lock table across job types (as reference-data-service does), or create your own via a new changeset.

**Step 7 — choose the schedule mode.** Prefer `CRON_EXP` (both real examples use it — clearer intent, e.g. `0 */5 * * * *` = every 5 minutes) unless you specifically need fixed-delay/fixed-rate semantics (§5).

## 5. Common pitfalls / anti-patterns

- **Using plain `@Scheduled` instead of this pattern.** It works technically, but bypasses the starter's shared thread pool, DB-driven enable/disable, and the `/api/secured/scheduler/*` ops tooling — don't do it in this repo.
- **Forgetting the middleware datasource.** If `company.datasource.middleware.enabled` isn't `true`, `middlewareNamedParameterJdbcTemplate` is absent and the manager silently falls back to YAML jobs — your DB row will never be read, with no hard error, just an info-level log line (`"middlewareNamedParameterJdbcTemplate is not available..."`). Check the logs if a job you configured in the DB never fires.
- **`worker_bean_name` / `job_group_name` mismatches.** These are plain strings resolved at runtime via `beanFactory.getBean(...)` — a typo fails silently at the row level (that job is skipped) rather than at compile time. Double check the DB row's `worker_bean_name` matches your `@Component("...")` value and `job_group_name` matches `@EnableScheduler(jobGroupName=...)`.
- **Uncaught exceptions inside `run(...)`.** `JobScheduleManager` does not wrap each execution's `Runnable` in its own try/catch beyond the *scheduling* call itself — an uncaught exception thrown from your `Schedulable.run(...)` will propagate into the `ThreadPoolTaskScheduler`'s executor and is logged by Spring's default `ErrorHandler`, but **it does not automatically disable or reschedule the job** — a `CronTrigger`-based task is safe (next cron tick still fires), but always wrap your job body in try/catch and log rather than relying on that, exactly as both real jobs do.
- **Overlapping executions.** `CRON_EXP` and `FIXED_RATE` triggers do **not** wait for the previous run to finish — if your job can run longer than its interval, use `FIXED_DELAY` (waits for completion before counting the delay) or — as `reference-data-service` does — an explicit DB lock so a slow run and the next trigger don't stomp on each other. A cron job with no lock and unpredictable runtime risks concurrent executions across pods.
- **Not indexing/deduplicating job rows.** Reuse the `idx_schedule_job_group_schedule` unique index pattern (`job_group_name, schedule_id`) and `ON CONFLICT ... DO NOTHING` seed inserts so re-running the changeset (or a fresh environment) doesn't create duplicate rows.
- **Editing an already-applied changeset to change a cron expression.** Per CLAUDE.md, add a new changeset (e.g. `UPDATE schedule_job SET cron_expr = '...' WHERE ...`) instead of modifying the seed row's original insert.
- **Exposing management endpoints without JWT protection.** `company.scheduler.management-endpoints.enabled: true` puts `/api/secured/scheduler/*` behind the service's normal JWT filter chain (note the `/secured/` path segment) — do **not** add these paths to `company.jwt.permit-all-urls`.

## 6. Checklist for a new scheduled job

- [ ] `@EnableScheduler(jobGroupName = "...")` present on the service's `@SpringBootApplication` class.
- [ ] `company.datasource.middleware.enabled: true` in every profile where the job should run from the DB.
- [ ] `company.scheduler.*` properties (pool size, thread naming, `management-endpoints.enabled`) set per profile.
- [ ] `schedule_job` table changeset present (reuse existing DDL if the table doesn't exist yet in this service).
- [ ] Seed-row changeset inserts your job with matching `job_group_name` / `worker_bean_name`, correct `schedule_mode` + cron/interval, `enable = 'Y'`, and an `ON CONFLICT ... DO NOTHING` guard.
- [ ] Job class is `@Component("<worker_bean_name>")` and `implements Schedulable`.
- [ ] Job body is wrapped in try/catch with logging — a failure must not throw past `run(...)`.
- [ ] If the job mutates shared/external state, a DB-backed lock (modeled on `SchedulerLockService`) guards it against overlapping/cross-pod execution.
- [ ] `mvn -pl services/<service> -am clean test-compile` (or `test` if you added job-level tests) passes.
