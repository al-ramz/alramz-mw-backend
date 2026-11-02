# document-management-service (DMS)

A thin wrapper over the **Open Bee Portal Web API**. Internal consumers save, find and fetch documents classified
by Open Bee **filing categories and metadata**. Open Bee's own category rules decide the folder path and the
file naming; Open Bee enforces per-consumer permissions and keeps the audit trail. Each consumer uses **its own**
Open Bee service account and API key; this service only maps consumer to key.

It replaces the legacy webMethods `DMSIntegration` package (retired, not ported).

## Endpoints

All four are JWT-secured (role `APP_DOCUMENT_MANAGEMENT_SERVICE_USER`). Contract: `specs/apiSpecs.yaml`.
Manual requests: `apiCollection.http`.

| Method + path | operationId | Purpose |
|---|---|---|
| `GET /api/v1/dms/filing-categories` | `listDmsFilingCategories` | Categories the consumer may save into (Open Bee `use`), with metadata definitions, list values, path and naming rules |
| `POST /api/v1/dms/documents` (multipart form fields, see below) | `saveDmsDocument` | Save a document under a filing category with metadata; **201** with `documentId` |
| `POST /api/v1/dms/documents/search` | `searchDmsDocuments` | Exact-match search by category + metadata, newest first, paginated; empty result is 200 |
| `GET /api/v1/dms/documents/{documentId}/content` | `downloadDmsDocumentContent` | Streams the file bytes (not wrapped in the envelope) |

**Save form fields** (`multipart/form-data`; no part needs its own Content-Type):

| Field | Required | Value |
|---|---|---|
| `file` | yes | The document (max 10 MB) |
| `filingCategoryId` | yes | Category ID from the filing-categories endpoint |
| `fileName` | no | Name with extension; defaults to the uploaded file's name |
| `description` | no | Max 1000 chars; defaults to the resolved file name |
| `overwrite` | no | `true` (default: new version if the name exists) or `false` (DMS106 if it exists) |
| `metadata` | no | JSON object of metadata ID to value or array, e.g. `{"11900":"FIT-1","11901":"2026-01-15","11902":true,"11888":["1001","102"]}` |

**Free-entry lists:** a LIST metadata with `allowNewValue: true` (Open Bee `addNewValue`) accepts any value, and
Open Bee adds new values to its list. Its list is not fetched (it holds every value ever entered) and
`listValues` is null: clients show a free-text input. For `allowNewValue: false`, the value must be one of
`listValues` (by name or id).

JSON responses use the repo `GenericResponse` envelope (`responseCode` = HTTP status, `correlationId`,
`errorCode` = `DMS###` on errors). Error codes:

| Code | HTTP | Meaning |
|---|---|---|
| DMS101 | 400 | Invalid request (missing/invalid part or field, page bounds, file name, documentId) |
| DMS102 | 400 | Filing category not available to this consumer, or it has no active folder rule in Open Bee (save) |
| DMS103 | 400 | Metadata ID not defined on the category |
| DMS104 | 400 | Mandatory metadata missing |
| DMS105 | 400 | Invalid metadata value (type, array, read-only, list value) |
| DMS106 | 409 | Document exists and `overwrite` is false |
| DMS107 | 413 | File larger than `dms.upload.max-file-size` |
| DMS108 | 404 | Document not found (download) |
| DMS109 | 403 | Consumer not configured, or Open Bee denied permission |
| DMS110 | 400 | Metadata type not supported (USER_GROUP, FILE) |
| DMS201 | 502 | Unexpected Open Bee response |
| DMS202 | 502 | Open Bee rejected the consumer's API key (configuration problem, not the caller's) |
| DMS203 | 503 | Open Bee unreachable, timed out, 5xx, or circuit open |
| DMS299 | 500 | Unexpected internal error |

A role-check failure is 403 with `errorCode` null. A missing/invalid token is rejected by the starter's security
chain before DMS code runs (currently 403 with an empty body; see "Known gaps").

**Download `Accept`:** send `Accept: */*` or `application/octet-stream, application/json`. A client accepting
only a specific file type (e.g. `application/pdf`) gets 406, and one accepting only octet-stream cannot receive
the JSON error envelope. `Range` requests are not supported.

## Identity

The consumer is the **starter JWT subject** (`UserRequestContext.get().getUserId()`), matched **exactly** and
case-sensitively against the keys of `dms.consumers`. There is no gateway consumer header. An authenticated
subject that is not configured gets 403 DMS109 and no Open Bee call is made. The caller's JWT is never forwarded
to Open Bee: every Open Bee call carries only `Authorization: Key <that consumer's key>`.

## Configuration

`DmsProperties` (`dms.*`). Only the first three rows are set per profile; the rest are Java defaults.

| Property | Default | Notes |
|---|---|---|
| `dms.openbee.base-url` | required | `${DMS_OPENBEE_BASE_URL:}` in every profile |
| `dms.openbee.time-zone` | required | `Asia/Dubai`; Open Bee timestamps have no offset |
| `dms.consumers."[<consumer>]".api-key` | none | Bracket notation keeps the key verbatim (e.g. `-`, `_`) |
| `dms.openbee.connect-timeout` | 5s | JDK HttpClient connector |
| `dms.openbee.read-timeout` | 15s | JSON calls (TimeLimiter) |
| `dms.openbee.transfer-read-timeout` | 120s | Upload; download headers and each body buffer |
| `dms.openbee.retry.max-attempts` | 3 | Read calls only (categories, lists, search, document info) |
| `dms.openbee.retry.initial-backoff` | 500ms | Exponential, multiplier 2 |
| `dms.openbee.upload-date-format` | `yyyy-MM-dd` | V1 |
| `dms.openbee.upload-boolean-format` | `ONE_ZERO` | `ONE_ZERO` (1/0) or `TRUE_FALSE`; V1 |
| `dms.openbee.upload-list-value` | `NAME` | `NAME` or `ID`; also used for search LIST filters; V1 |
| `dms.cache.categories-ttl` | 15m | Per consumer |
| `dms.cache.lists-ttl` | 15m | Per idList, shared |
| `dms.cache.forced-refresh-min-interval` | 60s | Per consumer |
| `dms.cache.refresh-ahead` | 5m | Background reload this long before the TTL; must be shorter than `categories-ttl` |
| `dms.cache.list-load-concurrency` | 8 | Parallel list calls per catalog load (1-32) |
| `dms.cache.lock-wait` | 20s | Longest wait behind another request's catalog load |
| `dms.cache.load-deadline` | 2m | A catalog load running longer is abandoned |
| `dms.upload.max-file-size` | 10MB | Aligned with `spring.servlet.multipart.max-file-size` (base `application.yml`, `max-request-size: 11MB`, `resolve-lazily: true`) |
| `dms.upload.temp-dir` | `${java.io.tmpdir}` | Upload spool directory |
| `dms.search.default-page-size` | 25 | |
| `dms.search.max-page-size` | 100 | Enforced in code (DMS101) |

### Fail-fast startup and the required environment variables

Startup **fails** if `dms.openbee.base-url` is blank, `dms.openbee.time-zone` is missing/invalid, or any configured
consumer has a blank `api-key` (the message names the property path, e.g. `consumers[crm].apiKey`, never a key).
Every deployed profile (dev, docker, test, preprod, prod) therefore needs:

- `DMS_OPENBEE_BASE_URL`
- `DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL`
- `DMS_OPENBEE_API_KEY_CRM`

Locally (`spring.profiles.active: dev`) export all three; any non-blank key starts the app, real calls need real
test-environment keys. Automated tests use fake values from `src/test/resources/application.yml`.

## Onboarding a consumer

1. Open Bee admin: create a service account and API key for the consumer, with `use` (save) and `read` (search)
   permission on the right filing categories.
2. Store the key as a Key Vault secret and map it into the pod environment (deployment templates / `infra`).
3. Add one bracketed entry to `dms.consumers` in **every** `application-<profile>.yml`:
   ```yaml
   dms:
     consumers:
       "[new-consumer]":
         api-key: ${DMS_OPENBEE_API_KEY_NEW_CONSUMER:}
   ```
4. Register the JWT user in each environment via the starter's `POST /api/auth/register`, with `consumer` (the
   username) **equal to the `dms.consumers` key** and role `APP_DOCUMENT_MANAGEMENT_SERVICE_USER`.

No code change is needed.

## Operational notes

- **No retry on save.** An upload is sent exactly once. On a timeout the caller gets 503 DMS203 and a WARN is
  logged with the correlation ID ("possible duplicate version in Open Bee"): check Open Bee (search by category
  and metadata) before re-sending, because the document may have been stored.
- **Read retries.** Categories, lists, search and document info retry timeouts, connection errors and 5xx (never
  4xx) up to `dms.openbee.retry.max-attempts`. A shared circuit breaker (`openbee`) counts only timeouts,
  connection errors and 5xx, so one consumer's revoked key (401) cannot open it for everyone; open circuit gives
  DMS203.
- **Stale cache.** Categories and lists are loaded lazily (never at startup). After the TTL, a refresh that fails
  because Open Bee is unavailable serves the last good copy (WARN); with no previous copy the caller gets DMS203.
  A credential (401) or permission (403) failure is never hidden by stale data. An unknown category or metadata
  ID triggers one forced refresh, at most once per consumer per `forced-refresh-min-interval`.
- **Catalog load time.** Open Bee answers one request at a time per API key (observed live: ~1-2 s per call), and a
  catalog load is 2 category pages plus one call per distinct list that is not free-entry (42 of 45 in the test
  environment; `addNewValue` lists are skipped). The lists are
  fetched `dms.cache.list-load-concurrency` (8) at a time, but Open Bee still serves them in turn: a cold load took
  31-57 s live. Only the first call per consumer after startup pays this: every minute a background task reloads
  each cached entry once it is within `dms.cache.refresh-ahead` (5m) of its TTL, so later callers hit a fresh
  cache. A failed background reload keeps the cached copy (WARN).
- **Slow Open Bee protections.** A request never queues behind another request's catalog load for longer than
  `dms.cache.lock-wait` (20s): it then gets the cached copy (WARN), or 503 DMS203 when there is none, so a slow Open
  Bee cannot pile up request threads. A single load is abandoned after `dms.cache.load-deadline` (2m, checked
  between Open Bee calls; list calls not started yet are cancelled) and the cached copy stays in use.
- **Uploads** are spooled to `dms.upload.temp-dir` (random `<uuid>.upload` names, never the client's file name)
  while computing the SHA-256 for `OB-CHECKSUM`, and deleted in `finally`. Uploads are sent with chunked
  transfer encoding (accepted by Open Bee, verified live).
- **Downloads** stream end to end (no buffering) and hold a Tomcat thread for the duration. Document info is looked
  up **before** the file is opened: Open Bee would otherwise queue it behind the open stream (same key) and time out.
  It also gives the name (`name` + `extension`; Open Bee sends no `Content-Disposition`) and tells an unknown
  document (404, DMS108) from a forbidden one (403, DMS109): `/ws/v2/file` answers 403 for both. The file is
  requested without `thumb`: Open Bee treats any value but `0`, even `false`, as "send a PDF rendition".
- **Oversized uploads.** A file just over 10 MB gets a readable 413 DMS107. A request far above 11 MB, or a chunked
  request body, may see a connection reset instead (Tomcat `max-swallow-size` is 2 MB, unchanged).
- **Health:** `DmsHealthIndicator` is always UP with `configuredConsumers` and `cachedCategoryEntries` counts; it
  never calls Open Bee (there is no safe key-less endpoint).
- **Actuator** exposes only `health,info,prometheus` (never `*`: `env`/`configprops`/`heapdump` would expose keys).

## Logging and audit

- Request/response payloads, request headers and DB-audit payloads are off in every profile. The save path
  (`/api/v1/dms/documents`) and the content path are excluded from the starter's request/response logging: on
  Tomcat the multipart form fields (file name, metadata JSON) are request parameters that the starter would log as
  `queryParams`. The
  content path is also excluded from `api_audit_log` so downloads are not buffered.
- Instead, every DMS request produces **one** structured INFO line (`DMS request correlationId=... consumerId=...
  operation=... filingCategoryId=... documentId=... openBeeStatus=... httpStatus=... dmsCode=... durationMs=...
  extension=...`). File content, metadata values, file names, API keys, `Authorization` values and Open Bee
  bodies are never logged; Open Bee status and error codes are.
- The Open Bee adapter lives in `com.alramz.openbee`, outside `com.alramz.client`, so the starter's
  `ApiAuditAspect` never serialises its arguments.

## Deployment prerequisites (outside this service's code)

1. Key Vault secrets `DMS_OPENBEE_BASE_URL`, `DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL`, `DMS_OPENBEE_API_KEY_CRM`
   exist per environment and are mapped into the pod environment. **Until then the pod fails at startup.**
2. JWT users `onboarding-portal` and `crm` are registered per environment (`/api/auth/register`, role
   `APP_DOCUMENT_MANAGEMENT_SERVICE_USER`, username = `dms.consumers` key).
3. The Open Bee admin has created one service account + API key per consumer with `use`/`read` permissions.
4. The API Gateway blocks `/api/auth/**` (at least `register`) from external callers; otherwise anyone could
   self-register with the DMS role (`service.yaml` has `ingress.external: true`).
5. Open Bee's `max_post_size` is at least 10 MB plus multipart overhead (V3; a 9.4 MB upload passed live).
6. The container's temp directory is writable and sized for concurrent uploads x 10 MB.
7. `DMS_OPENBEE_BASE_URL` uses HTTPS outside the test environment: the API key travels in every request header
   (the test environment is plain HTTP).
8. Each consumer key belongs to its own Open Bee service account. In the test environment the key used belongs
   to `Administrator` (documents show Administrator as creator), which bypasses per-consumer permissions and audit.

## Known gaps

- **401 vs 403 for a missing token.** The starter's security chain defines no `AuthenticationEntryPoint`, so a
  request without a valid token gets Spring Security's default 403 with an empty body, not 401. Fixing it is an
  `alramz-api-starter` change.
- **405/415 become 500.** A request Spring rejects before any controller runs (wrong HTTP method, e.g. `PUT` on
  the save path; wrong Content-Type, e.g. a JSON body to save or `text/plain` to search) is answered by the
  starter's catch-all `LoggingExceptionHandler` with 500 and an ERROR log, not 405/415. Starter-wide (every
  service); the fix belongs in `alramz-api-starter`.
- **Chunked upload** (Open Bee chunked API, for files above `max_post_size`) is not implemented.

## Verification items (verified live against the Open Bee test environment, 2026-10-30)

| ID | Question | Result |
|---|---|---|
| V1 | Upload value formats (date, boolean, list name vs id, `metadatas[<id>][]`) | Confirmed: `yyyy-MM-dd`, `1/0` (stored as true/false), list values by `NAME` (upload and search), repeated `[]` for multiple metadata |
| V2 | Upload without `path`/`idParentFolder` | Category with an active rule: filed by the rule. Category without one: Open Bee `400 MUST_SPECIFY_FOLDER_OR_RULE`, returned as 400 DMS102 (such categories stay listed) |
| V3 | Open Bee `max_post_size` | A 9.4 MB upload and its download (byte-identical) passed |
| V4 | Download headers; document-info name | No `Content-Disposition`; `Content-Type` passed through. Document info gives `name` without extension plus `extension`. `thumb=false` returns a PDF rendition (so `thumb` is not sent). Unknown ID: file 403, document info 404 |
| V5 | "Exists and overwrite=false"; permission denials | `400 {"error":{"code":"Document with the same name already exist"}}` -> DMS106. Open Bee sends `error` as one object, not the documented array; both are read |
| V6 | Categories list shape, search shape, timestamps | Categories parsed as documented. Search hits come under `items` (not `results`); timestamps carry an offset (`+00:00`); a multiple metadata value comes as JSON text (`["1001","102"]`) and is returned as `values` |
| - | Chunked multipart uploads | Accepted |

### Running the opt-in live checks

`OpenBeeLiveVerificationTest` (`@Tag("openbee-live")`) prints findings for V4/V6 and never runs by default
(surefire excludes the tag via `dms.test.excludedGroups`). Never point it at production:

```
DMS_LIVE_OPENBEE_BASE_URL=https://<openbee-test-host> \
DMS_LIVE_OPENBEE_API_KEY=<test key> \
DMS_LIVE_FILING_CATEGORY_ID=<test category id> \
mvn -pl services/document-management-service -am test -Ddms.test.excludedGroups= -Dgroups=openbee-live
```
