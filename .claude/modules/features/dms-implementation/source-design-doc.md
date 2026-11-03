# Feature request (verbatim from user)

> dms-implementation I would like to implement document management services. If you are not sure about anything, don't assume ask questions before you start implementation

Target module (orchestrator note, from repo state): `services/document-management-service` — already scaffolded in commit `f445d0b` ("scafolded dms") on branch `feature/dms`.

---

# DMS Service — Implementation Brief for Claude Code

**Purpose of this file:** a complete, self-contained specification for building the DMS (Document Management System) integration module inside an existing Spring Boot project, end to end. It replaces the legacy Software AG webMethods `DMSIntegration` package with a thin wrapper over the **Open Bee Portal Web API**.

**Audience:** Claude Code (or any engineer) working in the target project with no other context. Everything needed is in this file: requirements, decisions already taken, business logic, the native Open Bee API specs, error handling, tests and a phased plan.

**Version:** 1.0 · 2026-10-29

---

## 0. How to use this document (read first)

1. **Read the whole file before writing code.** Sections 4 to 9 are normative: implement them as written. Section 13 lists the only open points, which are settled by testing, not by guessing.
2. **Discover the target project's conventions first (Phase 0, section 12).** Do NOT guess any of the following. Find them in the existing codebase:
   - Java version, Spring Boot version, build tool (Maven or Gradle), module layout and root package name.
   - The standard response envelope class and how controllers return it.
   - The global exception handler pattern and existing error-code enums or registries.
   - The HTTP client already in use for outbound calls (`RestClient`, `WebClient`, `RestTemplate`, Feign) and how it is configured (timeouts, interceptors, TLS).
   - How secrets are read (Azure Key Vault, Spring Cloud Vault, environment variables, Kubernetes secrets, etc.).
   - The header the API Gateway uses to forward the authenticated consumer (client) identity to backend services.
   - The API base path and versioning convention (e.g. `/v1/...`, `/api/v1/...`).
   - Correlation-ID handling and the logging setup (MDC keys, log format, log sink).
   - Resilience libraries already present (Resilience4j, Spring Retry), cache library (Caffeine, Spring Cache), test stack (JUnit 5, WireMock, Testcontainers, MockMvc/WebTestClient).
   - Configuration style (`application.yml` vs `.properties`, `@ConfigurationProperties` naming, profiles).

   **If any item above cannot be determined from the project, stop and ask the user. Do not invent a value.**
3. **Follow the project's conventions over the examples in this file.** Class names, package names, property names and the envelope field names shown here are illustrative. Where the project already has an equivalent, reuse it.
4. **Work phase by phase (section 12).** Each phase ends with passing tests and a short summary to the user of what was built and anything still unverified.
5. **Never call the real Open Bee from automated tests.** Use WireMock stubs built from section 10 and Appendix C. Live calls are only in opt-in integration tests (section 11.4).

---

## 1. Context

- The organisation is migrating its integration layer from Software AG webMethods Integration Server 10.7 to Java / Spring Boot.
- The legacy `DMSIntegration` package saved documents through a middleware **template table** (`getDMSTemplate`) that resolved file names, folder paths and metadata IDs per "service name". Most documents were saved by file name and path only, without Open Bee filing categories or metadata.
- The legacy package had serious defects (an unauthenticated endpoint minting DMS tokens with hardcoded credentials, a new Open Bee API key created on every call, error codes overwritten to 500, missing audit logging, substring-based metadata validation, global lower-casing of metadata values). **It is NOT being ported.** The new module is a clean redesign.
- The DMS product is **Open Bee** (Open Bee Portal Web API, spec versions 6.11.x; Advanced Search 7.3.0).

## 2. Goals and scope

### 2.1 Goals

- Provide **four REST endpoints** that wrap Open Bee: list filing categories, save a document, search documents, download a document's file.
- Classify, place and name documents using **Open Bee filing categories and metadata**. Folder paths and naming come from each category's own rules configured in Open Bee, not from the middleware.
- **No database.** No middleware tables. Filing category definitions are read from Open Bee and cached in memory.
- **Per-consumer access is enforced by Open Bee itself.** Each consumer has its own Open Bee service account and API key. Open Bee's permissions on each filing category decide what that consumer can see and save, and Open Bee's audit trail records every action per consumer.
- Follow the project's standard response envelope, error-code conventions, logging and security practices.

### 2.2 Non-goals (do NOT build)

- Any port of the webMethods template logic: template table, `%KEY%` / `%YEAR%` / `%DATE%` placeholder substitution, folder walks, filename-alias tables.
- `getDocumentsByUID` (legacy endpoint): **not migrated**, retired with the legacy package.
- Backward compatibility endpoints for legacy documents. Legacy documents will be re-categorised in Open Bee by a separate one-off backfill job, **out of scope for this brief**.
- Any Open Bee administration from code: creating, editing or deleting filing categories, metadata, lists, permissions, users or API keys. This stays in the Open Bee portal.
- The legacy `SP_FUND_AGREEMENT_TC` "sweep" side effect after saving. Dropped.
- A token-minting endpoint of any kind. API keys are never returned to callers.
- Full-text search, aggregations, workflow filters, favourites, bulk actions, delete, versions, links, comments.

## 3. Decisions already taken (do not revisit)

| # | Decision |
|---|---|
| D1 | Thin wrapper over Open Bee. Four endpoints only (section 5). |
| D2 | No database. Filing categories and list values cached in memory. |
| D3 | Clients reference filing categories and metadata by **Open Bee numeric ID**. IDs differ per environment; consumers obtain them from the filing-categories endpoint in each environment. |
| D4 | Files are uploaded as `multipart/form-data` and downloaded as a **binary stream**. No base64 in JSON. |
| D5 | The document's folder path is decided by the filing category's rule in Open Bee. The save request never carries a path. |
| D6 | `fileName` is optional on save; a category naming rule may rename the document (section 6.2). |
| D7 | One Open Bee service account and API key **per consumer**. Open Bee enforces category permissions and keeps the audit trail. The service only maps consumer ID → API key. |
| D8 | Open Bee authentication header: **`Authorization: Key <apiKey>`** (confirmed). Do not use `X-API-Key`. |
| D9 | The filing-categories call uses `permissions=use` only: each consumer sees exactly the categories it may save into. No second call for `read`. |
| D10 | Search results are always sorted by creation date, newest first. There is no `latestOnly` flag; a client wanting the latest document sends `pageSize: 1`. |
| D11 | `overwrite` defaults to `true`. In Open Bee this adds a **new version** to an existing document with the same name in the same path; it never deletes anything. |
| D12 | New document ID is read from the **`Id-Document`** response header of the Open Bee upload. |
| D13 | Download uses Open Bee REST v2 **`GET /ws/v2/file/{idDocument}`**, which returns the file bytes. |
| D14 | Response envelope: the project's standard envelope (section 7.1). |
| D15 | No automatic retry on save (a retry after a timeout could add a duplicate version). Reads may retry. |
| D16 | Single-shot upload by default. Chunked upload is implemented only if real files exceed Open Bee's single-request limit (section 10.7). |

## 4. Architecture

### 4.1 Components

```
Internal consumers
      │
      ▼
API Gateway  ── authenticates the caller, forwards consumer ID header
      │
      ▼
DMS module (Spring Boot)
  ├─ Request logging / correlation filter   (project's existing one if present)
  ├─ DmsController                          4 endpoints, request parsing, bean validation, envelope
  ├─ ConsumerContextResolver                consumer ID header → ConsumerContext (id + API key)
  ├─ DocumentService                        orchestration and business rules (section 6)
  ├─ MetadataValidator                      validates metadata against the cached category (section 8)
  ├─ FilingCategoryCatalog                  per-consumer cache of categories + list values (section 9.1)
  ├─ OpenBeeClient                          the ONLY class that knows Open Bee URLs, headers, formats
  └─ GlobalExceptionHandler / DmsErrorCode  one error table, one envelope (section 7)
      │
      ▼  HTTPS, Authorization: Key <consumer's API key>
Open Bee Portal Web API
  ├─ GET  /filingCategories/ws/filingCategories
  ├─ GET  /filingCategories/ws/filingCategory/{id}      (optional, refresh one)
  ├─ GET  /filing/ws/list/{idList}
  ├─ POST /browse/ws/document
  ├─ PUT  /browse/ws/document/chunked                   (only if needed)
  ├─ POST /advancedSearch/ws/advanced-search
  ├─ GET  /ws/v2/file/{idDocument}
  └─ GET  /ws/v2/document/{idDocument}                  (only if download needs the file name)
```

### 4.2 Suggested package layout

Adapt to the project's structure (existing module, root package, naming).

```
<root>.dms
├─ api        DmsController, request/response DTOs
├─ service    DocumentService, FilingCategoryCatalog, MetadataValidator, ConsumerContextResolver
├─ client     OpenBeeClient, Open Bee DTOs (request/response), OpenBeeProperties
├─ error      DmsErrorCode, DmsException (+ subclasses if the project prefers), handler wiring
└─ config     HTTP client bean(s), cache config, consumer key configuration
```

### 4.3 Rules for `OpenBeeClient`

- It is the only place with Open Bee paths, query parameters, multipart field names and header names.
- Every method takes the consumer's API key (or a `ConsumerContext`) as an argument. There is no default or shared key.
- It translates Open Bee HTTP errors into a typed exception carrying: HTTP status, Open Bee error code(s) and message(s) from the `{"error":[{"code","message"}]}` body when present (section 10.1), and the endpoint called. Mapping to DMS codes happens in one place (section 7.3).
- It never logs request bodies, file bytes, metadata values or API keys.

## 5. Consumer identity and access

### 5.1 Flow

1. The API Gateway authenticates the caller and forwards its consumer ID in a header. **Discover the header name from the project** (gateway docs, existing filters, other services). Do not guess.
2. `ConsumerContextResolver` reads that header on every DMS request.
   - Header missing or blank → `403` `DMS109`.
   - Consumer ID not present in the consumer configuration → `403` `DMS109`.
3. It looks up the consumer's Open Bee API key from configuration backed by the project's secret store (section 5.2) and builds a `ConsumerContext { consumerId, apiKey }`.
4. Every Open Bee call for that request uses that consumer's key.
5. Open Bee enforces the consumer's permissions. A permission failure from Open Bee maps to `403` `DMS109` (section 7.3).

The DMS module must only be reachable through the API Gateway, because it trusts the forwarded consumer header. Note this in the module README / deployment notes; network restriction itself is outside the code.

### 5.2 Consumer configuration

Configuration maps consumer ID → reference to its API key in the secret store. Example shape (adapt naming and secret syntax to the project):

```yaml
dms:
  consumers:
    onboarding-portal:
      api-key: ${secret-store-reference-for-onboarding-portal-key}
    crm:
      api-key: ${secret-store-reference-for-crm-key}
```

- Keys are never in source, never in plain `application.yml` values, never logged, never returned.
- Adding a consumer = Open Bee admin creates a service account + API key + category permissions; the team adds one config entry and one secret. No code change.
- Validate at startup that every configured consumer has a non-blank key; fail fast with a clear message (without printing the key).

### 5.3 What Open Bee enforces

- `use` permission on a category: the consumer may save documents with that category, and the category appears in its filing-categories list.
- `read` permission on a category: the consumer may search documents with that category.
- Download: Open Bee checks the consumer's rights on the document.
- How Open Bee reports a denial on search (error vs. fewer results) and download (403 vs. 404) is verified in Phase 5 (section 13). The service must handle both: an Open Bee `403` or a `PERMISSION_DENIED` / `NOT_HAVE_PERMISSION` code → `DMS109`. An Open Bee `401` / `BAD_CREDENTIALS` means the configured key is wrong or revoked → `DMS202` (a server-side problem, not the caller's).

## 6. API contract and business logic

Base path and versioning: **use the project's convention** (shown here as `/v1/dms`). All JSON responses are wrapped in the project's standard envelope (section 7.1). Field names below are the `response` payload.

| # | Method and path | Purpose |
|---|---|---|
| 1 | `GET /v1/dms/filing-categories` | Categories the consumer may save into, with metadata definitions, list values and the configured path and naming rules |
| 2 | `POST /v1/dms/documents` | Save a document (multipart) under a filing category with metadata |
| 3 | `POST /v1/dms/documents/search` | Find documents by filing category and metadata, newest first, paginated |
| 4 | `GET /v1/dms/documents/{documentId}/content` | Stream the document's file |

`POST` is used for search because filters are a structured object and client identifiers must not appear in URLs (access logs).

The full OpenAPI definition of these endpoints is in **Appendix A**.

### 6.1 `GET /v1/dms/filing-categories`

**Request:** no parameters. Consumer from the gateway header.

**Response payload:**

```json
{
  "filingCategories": [
    {
      "id": 42,
      "name": "Customer Passport",
      "path": "/Clients/[CLIENT_ID]/[YEAR]",
      "autoFiling": true,
      "fileNamePattern": "PASSPORT_[CLIENT_ID]_[DATE]",
      "metadata": [
        {
          "id": 11329,
          "name": "CLIENT_ID",
          "type": "VARCHAR",
          "mandatory": true,
          "multiple": false,
          "readOnly": false,
          "parentMetadataId": null,
          "listId": null,
          "listValues": null
        },
        {
          "id": 11330,
          "name": "DOCUMENT_TYPE",
          "type": "LIST",
          "mandatory": true,
          "multiple": false,
          "readOnly": false,
          "parentMetadataId": null,
          "listId": 7,
          "listValues": [
            { "id": 101, "name": "Passport", "sublistId": null },
            { "id": 102, "name": "Emirates ID", "sublistId": null }
          ]
        }
      ]
    }
  ]
}
```

The `path` and `fileNamePattern` values above are illustrative; they are whatever Open Bee returns in `rule.grammarTree` and `rule.grammarName`.

**Field mapping from Open Bee** (`FilingCategory_output` / `FilingCategoryMetadata_output`, section 10.2):

| Our field | Open Bee field | Notes |
|---|---|---|
| `id` | `filingCategory.id` | |
| `name` | `filingCategory.name` | |
| `path` | `filingCategory.rule.grammarTree` | Path pattern Open Bee applies; null if absent |
| `autoFiling` | `filingCategory.rule.active` | Whether the category's rules are applied on save |
| `fileNamePattern` | `filingCategory.rule.grammarName` | Naming rule; when present, clients may omit `fileName` on save |
| `metadata[].id` | `metadata[].id` | |
| `metadata[].name` | `metadata[].name` | |
| `metadata[].type` | `metadata[].type` | Pass through Open Bee's type name (section 8) |
| `metadata[].mandatory` | `metadata[].mandatory` | |
| `metadata[].multiple` | `metadata[].multiple` | |
| `metadata[].readOnly` | `metadata[].autoincrement` | `true` = Open Bee assigns the value; client must not send it |
| `metadata[].parentMetadataId` | `metadata[].parentMetadata` | For SUBLIST |
| `metadata[].listId` | `metadata[].idList` | |
| `metadata[].listValues` | from `GET /filing/ws/list/{idList}` | `id`, `name`, `idSublist` → `sublistId`; null when the metadata has no list |

**Do not expose:** `filingCategory.type` (taxonomy, always Core), `rule.reapply`, `creationDate`, `idCreator`, `hasRestrictedUsers`, `usersAndGroupsRestriction`, `defaultValues`, `addNewValue`, `enableListPerfMode`, list value `image`.

**Logic:**

```
resolve ConsumerContext (5.1)
categories = FilingCategoryCatalog.get(consumer)        // cache hit → return
   on miss:
     page = 1
     loop:
       resp = OpenBeeClient.listFilingCategories(key, taxonomies=Core, permissions=use,
                                                 itemsPerPage=50, page)
       collect resp.results[].filingCategory
       if page >= resp.pagination.nbPages: break
       page++
     for each distinct idList across all metadata (non-null):
       list = ListCache.get(idList) or OpenBeeClient.getList(key, idList, recursive=false)
     map to DTOs (table above); store in cache for this consumer
return envelope(200, { filingCategories })
```

- Order: as returned by Open Bee (or by `name` ascending if Open Bee order is unstable; pass `order=name&direction=ASC`).
- Errors: Open Bee `401` → `DMS202`; timeout / 5xx after retries → `DMS203`, **unless a previous cached copy exists for this consumer, in which case return the stale copy** and log a warning.
- SUBLIST values: v1 exposes `sublistId` on list values only; it does not fetch sublists recursively (the vendor warns `recursive=true` is expensive).

### 6.2 `POST /v1/dms/documents` (save)

**Request:** `multipart/form-data` with two parts:

| Part | Type | Required | Notes |
|---|---|---|---|
| `file` | binary | Yes | The document. Its own filename (Content-Disposition) is used when `fileName` is omitted |
| `request` | JSON (`application/json`) | Yes | Fields below |

`request` fields:

| Field | Type | Required | Rules |
|---|---|---|---|
| `filingCategoryId` | integer | Yes | Must be in the consumer's cached category list, else `DMS102` |
| `fileName` | string | No | Name with extension, e.g. `passport.pdf`. Max 255 chars. No path separators (`/`, `\`), no control characters → else `DMS101` |
| `description` | string | No | Max 1000 chars. Defaults to the resolved file name (Open Bee requires a description) |
| `metadata` | object | Depends on category | Map of metadata ID (string key) → value (string, number, boolean) or array of values for `multiple` fields. Validated per section 8 |
| `overwrite` | boolean | No | Default `true` (new version if a document with the same name exists in the target path). `false` → `DMS106` if it exists |

Example `request` part:

```json
{
  "filingCategoryId": 42,
  "fileName": "passport.pdf",
  "metadata": { "11329": "FIT-100234", "11330": "Passport" },
  "overwrite": true
}
```

**Response payload** (HTTP status per project convention for creation: `201` if the project uses it for creates, else `200`):

```json
{ "documentId": 88213, "filingCategoryId": 42 }
```

No file name is returned: a category naming rule may rename the document; search returns the stored name.

**Logic:**

```
resolve ConsumerContext
validate parts present; file not empty                      → DMS101
validate file size ≤ configured max                         → DMS107 (413)
category = FilingCategoryCatalog.find(consumer, filingCategoryId)
   if absent: force one refresh for this consumer (rate-limited, 9.1) and retry lookup
   if still absent                                          → DMS102
MetadataValidator.validateForSave(category, request.metadata)   // section 8.2 → DMS103/104/105/110
name = resolveFileName(request.fileName, file.originalFilename)
   - request.fileName present → use it; if it has no extension and the original filename has one,
     append the original extension
   - else original filename present → use it (strip any client path, keep base name)
   - else                                                   → DMS101
   - sanitise: trim, reject separators/control chars        → DMS101
description = request.description or name
spool the upload to a temp file while computing SHA-256 (hex, lowercase)
   (the checksum header must be sent before the body, so the file is read twice: once to hash,
    once to send; never hold the whole file in memory)
resp = OpenBeeClient.addDocument(key,
         file=tempFile (filename=name), name=name, description=description,
         type=filingCategoryId, metadatas=encode(category, metadata)   // section 8.3
         overwrite=request.overwrite ?: true,
         headers: OB-CHECKSUM=<hex>, OB-CHECKSUM-ALGO=sha256)
   201 → documentId = resp header "Id-Document" (case-insensitive); missing → DMS201 + log
   errors → section 7.3 mapping (no retry)
delete temp file (finally)
return envelope(201|200, { documentId, filingCategoryId })
```

- Never send `path` or `idParentFolder` (decision D5). If Open Bee rejects uploads without a path for a category with an active rule, report it to the user (verification item V2, section 13); do not add a path workaround on your own.
- Never send `signature` or `textLayer`.
- Values are sent exactly as received after type normalisation (section 8). **No lower-casing or other transformation** of text values.
- Do not retry on timeout. Log the timeout with the correlation ID so operations can check Open Bee for a possible duplicate version.

### 6.3 `POST /v1/dms/documents/search`

**Request (JSON):**

| Field | Type | Required | Rules |
|---|---|---|---|
| `filingCategoryId` | integer | Yes | Must be in the consumer's cached category list, else `DMS102` |
| `metadata` | object | No | Map of metadata ID → value or array of values. Each entry is an exact match; entries are combined with AND; an array means "any of" (OR) where the type allows it (section 8.4) |
| `page` | integer | No | Default 1, min 1 |
| `pageSize` | integer | No | Default from config (25), min 1, max from config (100) → else `DMS101` |

```json
{
  "filingCategoryId": 42,
  "metadata": { "11329": "FIT-100234" },
  "page": 1,
  "pageSize": 25
}
```

**Response payload:**

```json
{
  "documents": [
    {
      "documentId": 88213,
      "fileName": "PASSPORT_FIT-100234_20261028.pdf",
      "extension": "pdf",
      "sizeBytes": 248331,
      "creationDate": "2026-10-28T10:15:00+04:00",
      "modificationDate": "2026-10-28T10:15:00+04:00",
      "filingCategory": { "id": 42, "name": "Customer Passport" },
      "metadata": [
        { "id": 11329, "name": "CLIENT_ID", "value": "FIT-100234" }
      ],
      "path": "/Clients/FIT-100234/2026"
    }
  ],
  "page": 1,
  "pageSize": 25,
  "totalCount": 1,
  "totalPages": 1
}
```

- No match → `200` with `documents: []`, `totalCount: 0`. Never 404 for an empty search.
- Results are always sorted by creation date, newest first. Latest document = `pageSize: 1`.

**Field mapping from Open Bee `ResultItem`** (section 10.5):

| Our field | Open Bee field | Notes |
|---|---|---|
| `documentId` | `idDocument` | |
| `fileName` | `name` | Stored name, after any naming rule |
| `extension` | `extension` | |
| `sizeBytes` | `size` | |
| `creationDate`, `modificationDate` | `creationDate`, `modificationDate` | Open Bee sends local time without offset (e.g. `2023-11-14T08:25:25`). Interpret in the configured Open Bee time zone (`dms.openbee.time-zone`, required config) and output ISO 8601 with offset |
| `filingCategory.id` / `.name` | `idFilingCategory` / `nameFilingCategory` | |
| `metadata[]` | `metadata[]` (`idMetadata`, `value`) | `idMetadata` is a string in Open Bee → parse to integer; `name` from the cached category definition (omit `name` if unknown) |
| `path` | `documentPath` | |

**Do not expose:** `idDocumentVersion`, `creator`, `modifiedBy` (internal staff names), `idFolder`, `idParentFolder`, `folderType`, `isHidden`, `aggregations`.

**Logic:**

```
resolve ConsumerContext
validate body (page, pageSize bounds)                           → DMS101
category = FilingCategoryCatalog.find(consumer, filingCategoryId) (refresh-once rule) → DMS102
MetadataValidator.validateForSearch(category, request.metadata)   // section 8.2 (no mandatory check) → DMS103/105/110
body = {
  "filters": [
    { "field": "FILING_CATEGORY", "filter": { "type": "EQUALS", "value": filingCategoryId } },
    { "field": "TYPE", "filter": { "type": "IN", "values": ["PDF","OFFICE","IMAGE","AUDIO","OTHERS"] } },
    ...one METADATA filter per metadata entry, built per section 8.4
  ],
  "pagination": { "itemsPerPage": pageSize, "page": page },
  "sort": [ { "field": "CREATION_DATE", "order": "DESC" } ]
}
resp = OpenBeeClient.advancedSearch(key, body)                  // retry allowed (read)
items = resp.results where idFolder is null and idDocument is not null   // defensive: documents only
map items (table above)
return envelope(200, { documents, page, pageSize,
                       totalCount = resp.pagination.count, totalPages = resp.pagination.nbPages })
```

### 6.4 `GET /v1/dms/documents/{documentId}/content`

**Request:** `documentId` path variable, positive integer → else `DMS101`.

**Response:** `200` with the raw file bytes streamed (not wrapped in the envelope). Errors use the normal JSON envelope.

Headers on success:

| Header | Value |
|---|---|
| `Content-Type` | From Open Bee's response; fallback `application/octet-stream` |
| `Content-Length` | From Open Bee's response when present |
| `Content-Disposition` | `attachment; filename="<ascii-safe name>"; filename*=UTF-8''<percent-encoded name>` |

**Logic:**

```
resolve ConsumerContext
validate documentId                                             → DMS101
resp = OpenBeeClient.downloadFile(key, documentId)              // GET /ws/v2/file/{idDocument}, thumb=false
   404 → DMS108;  403 / PERMISSION_DENIED → DMS109;  401 → DMS202; others → 7.3
name = filename from Open Bee's Content-Disposition, if present
   else OpenBeeClient.getDocumentInfo(key, documentId) → name (GET /ws/v2/document/{idDocument})
   else "document-<documentId>"
stream resp body → client response (no full buffering; StreamingResponseBody or equivalent)
```

- Whether Open Bee's file response carries `Content-Disposition` and `Content-Type` is verification item V4 (section 13). Implement the fallback chain above; remove the extra call later if not needed.
- Never return Open Bee URLs to the caller.
- Download may take longer than other calls: use a separate, longer read timeout (`dms.openbee.download-read-timeout`).

## 7. Errors

### 7.1 Envelope

Use the project's standard envelope. The migration programme's documented shape, for reference only (the project's existing implementation wins):

```json
{
  "correlationID": "string",
  "responseCode": "string",
  "responseMessage": "string",
  "errorCode": "string | null",
  "errorMsg": "string | null",
  "response": { }
}
```

- `responseCode` mirrors the real HTTP status.
- `errorCode` / `errorMsg` carry the `DMS` code and a human-readable message on errors; `response` is null on errors.
- Never include stack traces, Open Bee URLs, API keys or raw Open Bee bodies in responses.

### 7.2 DMS error codes

| HTTP | Code | When |
|---|---|---|
| 400 | DMS101 | Malformed request, missing required field or part, invalid value (page bounds, file name, documentId) |
| 400 | DMS102 | Filing category ID not available to this consumer (not in its `use` list) |
| 400 | DMS103 | Metadata ID not defined on this filing category |
| 400 | DMS104 | Mandatory metadata missing (save only) |
| 400 | DMS105 | Metadata value invalid for its type (including array for a non-multiple field, value sent for a read-only field, LIST value not in the list) |
| 409 | DMS106 | Document already exists and `overwrite` is `false` |
| 413 | DMS107 | File larger than the configured maximum |
| 404 | DMS108 | Document not found on download |
| 403 | DMS109 | Consumer not allowed: header missing, unknown consumer ID, or Open Bee permission denied |
| 400 | DMS110 | Metadata type not supported by this service (USER_GROUP, FILE) |
| 502 | DMS201 | Open Bee returned an unexpected error, or an unexpected response (e.g. missing `Id-Document`) |
| 502 | DMS202 | Open Bee rejected the service's credentials (consumer API key invalid, expired or revoked) |
| 503 | DMS203 | Open Bee unreachable, timed out, or 5xx after retries |
| 500 | DMS299 | Unexpected internal error |

If the project already has an error-code registry, register these there with the same meaning. If the project's registry uses a different numbering scheme, keep the `DMS` prefix and meanings and ask the user before renumbering.

### 7.3 Mapping Open Bee errors

Open Bee errors carry `{"error":[{"code":"...","message":"..."}]}` (section 10.1). Map in one place:

| Open Bee response | DMS code |
|---|---|
| `401`, or code `BAD_CREDENTIALS` | DMS202 |
| `403`, or code `PERMISSION_DENIED` / `NOT_HAVE_PERMISSION` | DMS109 |
| `404` on `GET /ws/v2/file/{id}` or `/ws/v2/document/{id}` | DMS108 |
| `400` with code `FILING_CATEGORY_IS_NOT_FOUND` | DMS102 |
| `400` with code `METADATA_NOT_EXIST` | DMS103 |
| Upload rejected because the document exists while `overwrite=false` (exact code to be identified, V5) | DMS106 |
| `400` with `INVALID_CHECKSUM_ALGO` / `MISSING_CHECKSUM` | DMS201 (our bug; log at ERROR) |
| Any other `4xx` | DMS201 |
| `5xx`, connection error, timeout | DMS203 |

Always log the Open Bee status and error code(s) at WARN/ERROR with the correlation ID. Do not echo Open Bee's message to the caller verbatim; use the DMS message.

## 8. Metadata handling

### 8.1 Types

Open Bee type names differ slightly between its specs. Treat these as equivalent groups:

| Group | Open Bee names | Value accepted from client | Supported |
|---|---|---|---|
| Text | `VARCHAR`, `TEXT`, `EMAIL`, `ADDRESS` | string, non-blank, max 4000 chars | Yes |
| Integer | `INT`, `INTEGER`, `AUTOINCREMENT` | integer (JSON number or numeric string) | Yes (`autoincrement` = read-only) |
| Decimal | `DECIMAL` | number (JSON number or numeric string), `.` decimal separator | Yes |
| Date | `DATE` | ISO date string `yyyy-MM-dd` | Yes |
| Boolean | `BOOLEAN` | `true` / `false` | Yes |
| List | `LIST`, `MULTIPLE_CHOICE` | the list value's `name` (string) or `id` (integer) — see 8.3 | Yes |
| Sublist | `SUBLIST` | string (value from the parent value's sublist) | Yes, validated as non-blank string only |
| User/group | `USER_GROUP` | — | No → DMS110 |
| File | `FILE` | — | No → DMS110 |

Unknown type names → treat as Text and log a WARN once per type.

### 8.2 Validation rules

For **save** (`validateForSave`):

1. Every key must be a numeric string that matches a metadata `id` on the category → else `DMS103`.
2. Every `mandatory` metadata that is not `readOnly` must be present and non-empty → else `DMS104`.
3. A `readOnly` (autoincrement) metadata must not be sent → `DMS105`.
4. Arrays only for `multiple: true` metadata; an empty array counts as absent → else `DMS105`.
5. Each value must match its type group (8.1) → else `DMS105`.
6. LIST: the value must match a list value of that metadata (by `name`, case-sensitive exact match, or by `id`) → else `DMS105`. The client may send either; the service normalises to the form Open Bee expects (8.3).
7. USER_GROUP / FILE metadata sent → `DMS110`. (A mandatory USER_GROUP/FILE metadata makes the category unusable through this service; return `DMS110` naming the metadata.)

For **search** (`validateForSearch`): rules 1, 4 (arrays allowed for any type that supports OR in 8.4), 5, 6, 7. No mandatory check; `readOnly` metadata may be used as a filter.

Error messages name the metadata ID and name, never the value (values can be personal data).

### 8.3 Encoding metadata on upload (Open Bee multipart)

Per the Open Bee spec, `metadatas` is "a key => value array of metadatas for the document. Keys of the array are metadatas' ID". The legacy implementation, which worked in production, sent:

- Single value: one multipart field `metadatas[<id>]` = value.
- Multiple values: one multipart field `metadatas[<id>][]` per value (repeated).

Implement exactly this, behind a small encoder in `OpenBeeClient`. The value **formats** are not documented by Open Bee and are verification item V1 (section 13). Make each one configurable with these defaults:

| Type group | Default wire format | Config switch |
|---|---|---|
| Text | as received | — |
| Integer / Decimal | plain number, `.` separator | — |
| Date | `yyyy-MM-dd` | `dms.openbee.upload-date-format` (e.g. `dd/MM/yyyy`) |
| Boolean | `1` / `0` | `dms.openbee.upload-boolean-format` (`1/0` or `true/false`) |
| List | the value's `name` | `dms.openbee.upload-list-value` (`NAME` or `ID`) |

### 8.4 Building search filters (Advanced Search)

One filter per metadata entry: `{ "field": "METADATA", "idMetadata": <id>, "filter": { ... } }`.

Open Bee states `EQUALS` is **not applicable** to metadata fields. Use:

| Type group | Single value | Array (any of) |
|---|---|---|
| Text, List, Sublist | `{"type":"IN","values":["v"]}` | `{"type":"IN","values":["v1","v2"]}` |
| Integer, Decimal | `{"type":"COMPARISON","comparator":"=","value":n}` | not supported → `DMS105` |
| Date | `{"type":"DATE_RANGE","start":"dd/MM/yyyy","end":"dd/MM/yyyy (next day)"}` (start inclusive, end exclusive, format `d/m/Y`) | not supported → `DMS105` |
| Boolean | `{"type":"BOOLEAN","includeTrue":v,"includeFalse":!v,"includeEmpty":false}` | not supported → `DMS105` |

- For LIST values in search filters, the expected form (label or list-value ID) is part of verification V1; use the same `dms.openbee.upload-list-value` setting unless testing shows search needs the other form, in which case add `dms.openbee.search-list-value`.
- Always add the `FILING_CATEGORY` `EQUALS` filter and the `TYPE` `IN` filter (6.3).

## 9. Cross-cutting

### 9.1 Caching

- **Filing categories:** one entry per consumer ID, containing the mapped category list (with metadata and list values). TTL from config (`dms.cache.categories-ttl`, propose 15 minutes; confirm with the user). Loaded on first use (lazy), never at startup, so the service starts when Open Bee is down.
- **Lists:** one entry per `idList`, shared across consumers (list values are not permission-scoped in the specs). TTL from config (`dms.cache.lists-ttl`, same default).
- **Forced refresh:** when a request references a category ID or metadata ID not in the cached entry, refresh that consumer's entry once, then re-check. Rate-limit forced refreshes per consumer (`dms.cache.forced-refresh-min-interval`, propose 60 seconds) so invalid IDs cannot hammer Open Bee.
- **Stale on failure:** if a refresh fails and an older entry exists, keep serving it and log a WARN.
- Use the project's cache library if present; otherwise Caffeine.

### 9.2 Resilience

- Connect timeout and read timeout on every Open Bee call (config). Separate longer read timeout for download and upload.
- Retry with exponential backoff **only** for: list filing categories, get list, get filing category, advanced search, document info. Retry on connection errors, timeouts and 5xx; never on 4xx. Default max 3 attempts (config).
- **No retry** for upload (`POST /browse/ws/document`) or download streaming once bytes have started.
- Circuit breaker around `OpenBeeClient` if the project already uses one (e.g. Resilience4j); open circuit → `DMS203`.
- Health indicator (Spring Actuator, if the project uses it): a cheap Open Bee reachability check that does not use any consumer key in a way that leaks it; if no safe endpoint exists, report only cache and config health. Do not fail readiness solely because Open Bee is down unless that is the project's convention.

### 9.3 Logging, audit and security

- Use the project's correlation-ID mechanism; propagate the correlation ID to Open Bee only if the project already does that for outbound calls (Open Bee does not document a header for it).
- One structured log line per request: correlation ID, consumer ID, endpoint, filing category ID, document ID (when known), Open Bee status, DMS code, duration.
- **Never log:** file content, metadata values, file names (they can contain client identifiers; log the extension only), API keys, `Authorization` headers, full Open Bee response bodies.
- Mask the `Authorization` header in any HTTP client wire logging; keep wire logging off by default.
- Validate `Content-Type` of the `request` part is JSON; reject other content types → `DMS101`.
- Limit multipart size via the project's multipart settings aligned with `dms.upload.max-file-size`.
- Temp files: created in the configured temp directory, deleted in `finally`, never logged by path with the client file name.

### 9.4 Configuration properties

Names are illustrative; follow the project's property naming.

| Property | Purpose | Default |
|---|---|---|
| `dms.openbee.base-url` | Open Bee host, e.g. `https://<host>` (all paths in section 10 are relative to it) | none, required |
| `dms.openbee.time-zone` | Zone Open Bee uses for timestamps without offset | none, required (ask the user) |
| `dms.openbee.connect-timeout` | | 5s |
| `dms.openbee.read-timeout` | JSON calls | 15s |
| `dms.openbee.transfer-read-timeout` | upload and download | 120s |
| `dms.openbee.retry.max-attempts` | read calls only | 3 |
| `dms.openbee.upload-date-format` | 8.3 | `yyyy-MM-dd` |
| `dms.openbee.upload-boolean-format` | 8.3 | `1/0` |
| `dms.openbee.upload-list-value` | 8.3 | `NAME` |
| `dms.consumers.<id>.api-key` | secret reference per consumer (5.2) | none |
| `dms.consumer-id-header` | gateway header carrying the consumer ID (discover the value) | none, required |
| `dms.cache.categories-ttl` | 9.1 | 15m |
| `dms.cache.lists-ttl` | 9.1 | 15m |
| `dms.cache.forced-refresh-min-interval` | 9.1 | 60s |
| `dms.upload.max-file-size` | 6.2 | ask the user; until known, align with the project's multipart limit |
| `dms.upload.chunked-threshold` | 10.7; disabled when unset | unset |
| `dms.search.default-page-size` | 6.3 | 25 |
| `dms.search.max-page-size` | 6.3 | 100 |

The proposed defaults above are design values from the solution design, not project conventions; confirm timeouts with the user if the project has standard values.

## 10. Open Bee native API specification (only what this module uses)

Source: Open Bee OpenAPI specs (Filing Categories 6.11.5, Filing 6.11.3, Document/Browse 6.11.3, Authentication 6.11.3, Advanced Search 7.3.0) and the Open Bee REST v2 "Documents" help page. A machine-readable subset is in **Appendix B**. Where the vendor material is silent or inconsistent, this section says so and points to a verification item (section 13).

### 10.1 General

| Aspect | Value |
|---|---|
| Base URL | `https://<host>` from `dms.openbee.base-url`. All paths below are relative to it, including the REST v2 paths under `/ws/v2/` |
| Authentication | Header `Authorization: Key <apiKey>` on every call (decision D8). One key per consumer |
| Content types | JSON for categories, lists and search; `multipart/form-data` for upload; binary for download |
| Error body | `{"error":[{"code":"FILING_CATEGORY_IS_NOT_FOUND","message":"Filing category was not found"}]}` on the Filing Categories and Authentication APIs. Advanced Search, Filing and REST v2 document all document status codes but no error body schema: parse the body defensively (it may be empty or not JSON) |
| Common codes | `401 BAD_CREDENTIALS`; `403 PERMISSION_DENIED`, `NOT_HAVE_PERMISSION` |
| Pagination (categories) | Query `itemsPerPage` (default 50), `page` (default 1); response `pagination: { count, nbPages }` |
| Timestamps | Local time without offset, e.g. `2023-11-14T08:25:25` |
| Search dates | Filter dates use `d/m/Y`, e.g. `09/10/2025` |

Do not call `POST /auth/ws/apikey` or any other authentication endpoint at runtime. Keys are provisioned once by the Open Bee admin.

### 10.2 List filing categories

`GET /filingCategories/ws/filingCategories`

| Query parameter | Value used | Notes |
|---|---|---|
| `taxonomies` | `Core` | `Core` = filing categories, `Form` = forms. Default returns both |
| `permissions` | `use` | `use` = may classify documents; `read` = may search; `edit` = may modify. Default `use` |
| `itemsPerPage` | 50 | |
| `page` | 1..nbPages | |
| `order` / `direction` | `name` / `ASC` (optional) | |
| `searchFilingCategory` | not used | name filter |

Response `200`:

```json
{
  "pagination": { "count": 2, "nbPages": 1 },
  "results": [
    {
      "filingCategory": {
        "id": 42,
        "name": "Customer Passport",
        "type": "Core",
        "metadata": [ { "...": "FilingCategoryMetadata_output" } ],
        "rule": { "active": true, "reapply": false, "grammarTree": "...", "grammarName": "..." },
        "creationDate": "2026-10-01T09:00:00",
        "idCreator": 3
      }
    }
  ]
}
```

`FilingCategoryMetadata_output`:

| Field | Type | Meaning |
|---|---|---|
| `id` | integer | Metadata ID (used as key in uploads and search filters) |
| `name` | string | |
| `type` | string | `VARCHAR`, `INT`, `DECIMAL`, `DATE`, `BOOLEAN`, `LIST`, `SUBLIST`, `USER_GROUP` (the Filing API adds `TEXT`, `FILE`) |
| `mandatory` | boolean | |
| `multiple` | boolean | Accepts multiple values |
| `autoincrement` | boolean | INT only; Open Bee assigns the value |
| `addNewValue` | boolean | LIST/SUBLIST: users may add values (not used) |
| `parentMetadata` | integer | SUBLIST: parent metadata ID |
| `idList` | integer | LIST/SUBLIST: attached list |
| `hasRestrictedUsers`, `usersAndGroupsRestriction`, `defaultValues` | | Not used |

`Rule`: `active` (filing category rules active), `reapply` (re-apply rules when metadata is modified), `grammarTree` (grammar that sets the document path automatically), `grammarName` (grammar that renames the document automatically).

Errors: `401 BAD_CREDENTIALS`.

The vendor schema wraps each result in a `filingCategory` object. Confirm the real shape in Phase 5 (V6) and keep the mapping tolerant (accept both wrapped and unwrapped items).

### 10.3 Get one filing category (optional)

`GET /filingCategories/ws/filingCategory/{idFilingCategory}` → `200` `FilingCategory_output` (same shape as one `results[]` item). Errors: `400 FILING_CATEGORY_IS_NOT_FOUND`, `401`. May be used to refresh a single category; the full-list refresh is sufficient for v1.

### 10.4 Get a list's values

`GET /filing/ws/list/{idList}?recursive=false`

Response `200`:

```json
{
  "id": 7,
  "name": "Document types",
  "values": [
    { "id": 101, "name": "Passport", "image": null, "idSublist": null },
    { "id": 102, "name": "Emirates ID", "image": null, "idSublist": null }
  ]
}
```

- `recursive=true` also returns each value's `sublist`; the vendor warns it can have a huge performance impact. Always send `false`.
- Errors: `404` list does not exist.

### 10.5 Advanced search

`POST /advancedSearch/ws/advanced-search` with JSON body `SearchRequest`.

`SearchRequest`:

| Field | Type | Notes |
|---|---|---|
| `filters` | `FieldFilter[]` | required |
| `pagination` | `{ itemsPerPage (min 1, default 25), page (min 1, default 1) }` | |
| `sort` | `[{ field, order: ASC\|DESC }]` | Fields in UPPERCASE: `NAME`, `FILING_CATEGORY`, `CREATION_DATE`, or `METADATA_<id>` |
| `aggregations` | | not used |

`FieldFilter`: `{ field, idMetadata?, filter: FilterClause, searchIn? }`

- `field`: a system field (`FILING_CATEGORY`, `TYPE`, `FOLDER`, `CREATOR`, `WORKFLOW`, `STATE_IN_WORKFLOW`, `CREATION_DATE`, `MODIFICATION_DATE`, `IS_LINKED`, `IS_FAVORITE`, `IS_COMMENTED`, `IS_SHARED`, `IS_SIGNED`, `KEYWORD`) or `METADATA` (then `idMetadata` is required).
- `TYPE` values: `PDF`, `FOLDER`, `OFFICE`, `IMAGE`, `AUDIO`, `OTHERS`.

`FilterClause` operators:

| `type` | Shape | Applies to |
|---|---|---|
| `EQUALS` | `value` | System fields `KEYWORD`, `IS_*`, `WORKFLOW`, `FILING_CATEGORY`, `FOLDER`. **Not applicable to metadata** |
| `IN` / `NOT_IN` | `values[]` | `STATE_IN_WORKFLOW`, `CREATOR`, metadata of type TEXT, EMAIL, ADDRESS, LIST, SUBLIST, MULTIPLE_CHOICE, AUTOINCREMENT, USER_GROUP (`"user:<id>"`, `"group:<id>"`) |
| `DATE_RANGE` | `start`, `end` (format `d/m/Y`; start inclusive, end exclusive by default; `end` may be null) | `CREATION_DATE`, `MODIFICATION_DATE`, DATE metadata |
| `NUMERIC_RANGE` | `min`, `max` | INTEGER / DECIMAL metadata |
| `COMPARISON` | `comparator` (`=`, `!=`, `>`, `>=`, `<`, `<=`), `value` | INTEGER / DECIMAL metadata |
| `PERIOD` | `amount`, `unit` (`DAYS`, `WEEKS`, `MONTHS`, `YEARS`) | dates |
| `BOOLEAN` | `includeTrue`, `includeFalse`, `includeEmpty` | BOOLEAN metadata |
| `EMPTY` | | |

Response `200` `SearchResponse`:

| Field | Notes |
|---|---|
| `results[]` | `ResultItem` |
| `pagination` | `{ count (total matches), nbPages, itemsPerPage, page }` |
| `aggregations[]` | not used |

`ResultItem`: `idFolder` (null for documents), `idDocumentVersion`, `idDocument`, `name`, `creationDate`, `creator {fullname, id}`, `modifiedBy {fullname, id}`, `size` (bytes), `modificationDate`, `idFilingCategory`, `nameFilingCategory`, `extension` (null for folders), `folderType`, `idParentFolder`, `documentPath`, `isHidden`, `metadata[] { idMetadata (string), value (string) }`.

Errors: `400` invalid parameter, `401`, `500`.

Do not use the deprecated `POST /advancedSearch/ws/search`.

### 10.6 Upload a document

`POST /browse/ws/document` — `multipart/form-data`

Headers:

| Header | Required | Value |
|---|---|---|
| `Authorization` | yes | `Key <apiKey>` |
| `OB-CHECKSUM` | yes | Hash of the uploaded document (lowercase hex SHA-256) |
| `OB-CHECKSUM-ALGO` | yes | `md5`, `sha1` or `sha256` (use `sha256`) |

Form fields:

| Field | Required | Used | Notes |
|---|---|---|---|
| `file` | no* | yes | The document (binary). *Either `file` or `uploadId` |
| `uploadId` | no* | chunked only | Commits a chunked upload session (10.7). Then `name` must include the extension |
| `name` | yes | yes | Document name |
| `description` | yes | yes | |
| `type` | yes | yes | Filing category ID |
| `metadatas` | no | yes | Key => value array, keys are metadata IDs. Encoding in section 8.3 |
| `overwrite` | no | yes | Default `true`: if the document already exists, create a new version; `false`: return an error |
| `idParentFolder` | no | **never** | Folder ID (decision D5) |
| `path` | no | **never** | Full path of the parent folder (decision D5) |
| `signature` | no | never | Mobile signing coordinates |
| `textLayer` | no | never | OCR text layer |

Response `201`: body `{"Result":"Document added successfully"}`; header **`Id-Document`** = the new document's ID (confirmed). The REST v2 page also documents `Content-Location` (URI of the created document) and, when published under several directories, `Document-Ids`; do not depend on these.

Errors: `400` `MISSING_CHECKSUM` (checksum headers missing), `INVALID_CHECKSUM_ALGO` (hash does not match); `404` `UPLOADID_NOT_FOUND`, `UPLOADID_EXPIRED` (chunked commit only). The error for "exists and `overwrite=false`" is not documented (V5).

### 10.7 Chunked upload (only if needed, decision D16)

`PUT /browse/ws/document/chunked?uploadId=<id>&offset=<bytes>` — `multipart/form-data` with one field `file` (the chunk).

1. First call: no `uploadId` (or `offset=0`). Response `200` `{ "uploadId": "...", "offset": <bytes received>, "expires": "<date>" }`.
2. Next calls: same `uploadId`, `offset` = total bytes already sent (from the last response). Optional per-chunk `OB-CHECKSUM` / `OB-CHECKSUM-ALGO` headers.
3. Commit: `POST /browse/ws/document` with `uploadId` instead of `file`, `name` with extension, and the checksum headers of the **whole** file.

Errors: `400 OFFSET_INVALID` (returns the expected offset), `404 UPLOADID_NOT_FOUND`, `UPLOADID_EXPIRED` (sessions last 1 day), `FILE_REQUIRED`. The maximum size per request is Open Bee's `max_post_size` (V3). Implement only if V3 shows real files exceed it; threshold = `dms.upload.chunked-threshold`.

### 10.8 Download a document's file

`GET /ws/v2/file/{idDocument}`

| Query parameter | Used | Notes |
|---|---|---|
| `version` | no | Specific version ID; default = current version |
| `thumb` | `false` (or omitted) | `true` serves a thumbnail for images or a PDF for Office files |

Response `200`: the body is the file content. Response headers (content type, file name) and error statuses are not documented (V4); expect `404` for an unknown document and `401`/`403` for credential and permission problems.

### 10.9 Document information (fallback for the file name)

`GET /ws/v2/document/{idDocument}` → `200` with `{ "document": { ... }, "comments": { ... }, "links": { ... }, "metadatas": { ... }, "versions": { ... } }`. The fields of the `document` object (including its name) are defined in Open Bee's "common schema", which was not provided; confirm in V4. Use only if the download response gives no file name.

### 10.10 Open Bee endpoints explicitly not used

Category, metadata, list and permission administration (create/edit/delete/duplicate/translate); all authentication endpoints (API key creation, JWT login, 2FA, password, devices, SSO); folder search, saved/favourite/recent searches, search traces, criteria, columns, bulk actions, export, bulk delete; `bulkHide` / `bulkShow`; REST v2 `POST /ws/v2/document` (deprecated upload), `POST /ws/v2/document/{id}` (update; reserved for the separate backfill job), `PUT` new version, `DELETE`, links, `mergeImage`, FILE-metadata downloads.

## 11. Testing

### 11.1 Unit tests

- `MetadataValidator`: every rule in 8.2, for save and search, including LIST by name and by id, read-only, multiple, unsupported types, unknown IDs, mandatory.
- File-name resolution (6.2): explicit name, missing extension, fallback to original filename, path stripping, invalid characters.
- Metadata encoder (8.3): each type group, multi-value repetition, each config switch.
- Search filter builder (8.4): exact JSON per type; date range end = next day in `dd/MM/yyyy`; arrays rejected for non-text types.
- Error mapping (7.3): every row.
- Date conversion: Open Bee local timestamp + configured zone → ISO 8601 with offset.

### 11.2 Open Bee client tests (WireMock)

Stub each endpoint with the payloads in Appendix C. Required scenarios:

- **Categories:** two pages fetched and merged; correct query parameters (`taxonomies=Core`, `permissions=use`); `Authorization: Key <consumer key>` sent; shared `idList` fetched once; results cached per consumer (second request = no Open Bee call); different consumers use different keys and caches; stale entry served when refresh fails; forced refresh is rate-limited.
- **Save:** exact multipart field names and values; **no** `path` / `idParentFolder` field; `OB-CHECKSUM` equals the SHA-256 of the bytes sent; `OB-CHECKSUM-ALGO: sha256`; `overwrite` default `true`; `documentId` read from `Id-Document` (case-insensitive); missing header → `DMS201`; `401` → `DMS202`; `403` → `DMS109`; `500` → `DMS203` with **exactly one** call (no retry); temp file deleted in all paths.
- **Search:** exact request JSON (category filter, TYPE filter, metadata filters, pagination, sort); folders dropped from results; empty results → `200` with empty list; pagination mapping; `500` retried up to max attempts then `DMS203`.
- **Download:** bytes streamed unchanged; `Content-Type` passed through or defaulted; name from `Content-Disposition`, else from document info, else default; `404` → `DMS108`.

### 11.3 API tests (MockMvc / WebTestClient, per project convention)

- Consumer header missing / unknown → `403 DMS109`.
- Envelope shape on success and error matches the project's envelope.
- Validation errors produce the right `DMS1xx` code and never echo metadata values.
- File over the limit → `413 DMS107`.
- Download success response is raw bytes, not wrapped.

### 11.4 Live integration tests (opt-in)

Tagged (e.g. `@Tag("openbee-live")`) and skipped unless environment variables provide: Open Bee test base URL, a test consumer API key, and a test filing category ID with known metadata. They run the verification checklist in section 13 and print findings. Never run in CI by default. Never use production.

## 12. Implementation plan

Each phase ends with all tests green and a short summary to the user: what was built, decisions made from project conventions, anything unverified.

1. **Phase 0 — Discovery (no code).**
   - Inspect the project for every item in section 0, step 2.
   - Report a "Project conventions" summary to the user with file references for each finding.
   - List anything not found and **ask**; wait for answers before Phase 1.
   - Acceptance: user confirms the conventions summary.
2. **Phase 1 — Foundation and filing categories.**
   - Configuration properties, consumer configuration bound to the secret store, startup validation of consumer keys.
   - `ConsumerContextResolver`, `DmsErrorCode`, exception types, handler integration, envelope usage.
   - `OpenBeeClient` base: HTTP client bean, auth header, timeouts, Open Bee error parsing, read retries.
   - `FilingCategoryCatalog` (per-consumer cache, list cache, forced refresh, stale-on-failure).
   - Endpoint 1 + tests (11.1–11.3 for this scope).
   - Acceptance: endpoint 1 returns mapped categories from WireMock; consumer isolation proven by tests.
3. **Phase 2 — Save.**
   - Multipart endpoint, `MetadataValidator` (save rules), file-name resolution, spooling + SHA-256, metadata encoder, upload call, `Id-Document` handling, error mapping.
   - Acceptance: all save scenarios in 11.2 pass; no retry on upload proven.
4. **Phase 3 — Search and download.**
   - Search endpoint with filter builder, result mapping, pagination, sorting.
   - Download endpoint with streaming and name fallback chain.
   - Acceptance: all search and download scenarios in 11.2 pass; download memory use does not grow with file size (stream test with a large stubbed body).
5. **Phase 4 — Hardening and documentation.**
   - Circuit breaker (if used in the project), health indicator, logging review against 9.3, multipart limits, OpenAPI docs per project convention (Appendix A as the contract).
   - Module README: configuration, how to onboard a consumer (Open Bee account + permissions + key + config entry), operational notes (no retry on save, stale cache behaviour, trusted gateway header).
6. **Phase 5 — Live verification (with the user, Open Bee test environment).**
   - Run section 13 items through the opt-in live tests; adjust config defaults (8.3) and mappings (7.3) to what Open Bee actually does; record findings in the module README.
7. **Phase 6 — Chunked upload (only if V3 requires it).**

## 13. Verification items (settled by testing, not by guessing)

| ID | Question | How to settle | Affects |
|---|---|---|---|
| V1 | Metadata value formats: DATE on upload, BOOLEAN on upload, LIST as label or list-value ID (upload and search), multi-value encoding `metadatas[<id>][]` | One live upload per type, then fetch via search and compare | 8.3, 8.4 config defaults |
| V2 | Does an upload without `path` / `idParentFolder` work for a category with an active rule? What happens for a category without a path rule? | Live upload to both kinds of category | 6.2 (report to user if it fails) |
| V3 | Maximum single upload size (`max_post_size`) | Ask the Open Bee admin; optionally a live upload near the limit | Phase 6, `dms.upload.max-file-size` |
| V4 | Download response headers (`Content-Type`, `Content-Disposition`) and error statuses; `document` object fields in `GET /ws/v2/document/{id}` | Live download of a known document and of an unknown ID | 6.4 fallback chain |
| V5 | Error code when `overwrite=false` and the document exists; how a permission denial appears on save, search and download | Live upload twice with `overwrite=false`; calls with a consumer lacking permissions | 7.3 mapping |
| V6 | Real JSON shape of the categories list (`results[].filingCategory` wrapper), `idMetadata` type in search results; whether API keys expire | Live calls; ask the Open Bee admin about key expiry | 10.2 mapping, key rotation runbook |

---

## Appendix A — OpenAPI for the DMS module

Wrap responses in the project's envelope; the schemas below describe the `response` payload. Adjust the base path to the project's convention.

```yaml
openapi: 3.0.3
info:
  title: DMS Service
  version: 1.0.0
  description: Thin wrapper over Open Bee. Consumer identity comes from the API Gateway header.
paths:
  /v1/dms/filing-categories:
    get:
      summary: Filing categories the calling consumer may save into
      responses:
        "200":
          description: OK
          content:
            application/json:
              schema: { $ref: "#/components/schemas/FilingCategoriesResponse" }
        "403": { $ref: "#/components/responses/Error" }
        "502": { $ref: "#/components/responses/Error" }
        "503": { $ref: "#/components/responses/Error" }
  /v1/dms/documents:
    post:
      summary: Save a document under a filing category
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file, request]
              properties:
                file:
                  type: string
                  format: binary
                request:
                  $ref: "#/components/schemas/SaveDocumentRequest"
            encoding:
              request:
                contentType: application/json
      responses:
        "201":
          description: Created (or 200, per project convention)
          content:
            application/json:
              schema: { $ref: "#/components/schemas/SaveDocumentResponse" }
        "400": { $ref: "#/components/responses/Error" }
        "403": { $ref: "#/components/responses/Error" }
        "409": { $ref: "#/components/responses/Error" }
        "413": { $ref: "#/components/responses/Error" }
        "502": { $ref: "#/components/responses/Error" }
        "503": { $ref: "#/components/responses/Error" }
  /v1/dms/documents/search:
    post:
      summary: Search documents by filing category and metadata, newest first
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/SearchRequest" }
      responses:
        "200":
          description: OK (empty list when nothing matches)
          content:
            application/json:
              schema: { $ref: "#/components/schemas/SearchResponse" }
        "400": { $ref: "#/components/responses/Error" }
        "403": { $ref: "#/components/responses/Error" }
        "502": { $ref: "#/components/responses/Error" }
        "503": { $ref: "#/components/responses/Error" }
  /v1/dms/documents/{documentId}/content:
    get:
      summary: Stream a document's file
      parameters:
        - name: documentId
          in: path
          required: true
          schema: { type: integer, format: int64, minimum: 1 }
      responses:
        "200":
          description: File bytes
          headers:
            Content-Disposition: { schema: { type: string } }
          content:
            application/octet-stream:
              schema: { type: string, format: binary }
        "400": { $ref: "#/components/responses/Error" }
        "403": { $ref: "#/components/responses/Error" }
        "404": { $ref: "#/components/responses/Error" }
        "502": { $ref: "#/components/responses/Error" }
        "503": { $ref: "#/components/responses/Error" }
components:
  responses:
    Error:
      description: Error in the project's standard envelope, errorCode = DMSxxx (section 7.2)
  schemas:
    FilingCategoriesResponse:
      type: object
      properties:
        filingCategories:
          type: array
          items: { $ref: "#/components/schemas/FilingCategory" }
    FilingCategory:
      type: object
      properties:
        id: { type: integer }
        name: { type: string }
        path: { type: string, nullable: true }
        autoFiling: { type: boolean, nullable: true }
        fileNamePattern: { type: string, nullable: true }
        metadata:
          type: array
          items: { $ref: "#/components/schemas/MetadataDefinition" }
    MetadataDefinition:
      type: object
      properties:
        id: { type: integer }
        name: { type: string }
        type: { type: string, example: VARCHAR }
        mandatory: { type: boolean }
        multiple: { type: boolean }
        readOnly: { type: boolean }
        parentMetadataId: { type: integer, nullable: true }
        listId: { type: integer, nullable: true }
        listValues:
          type: array
          nullable: true
          items: { $ref: "#/components/schemas/ListValue" }
    ListValue:
      type: object
      properties:
        id: { type: integer }
        name: { type: string }
        sublistId: { type: integer, nullable: true }
    MetadataValues:
      type: object
      description: Map of metadata ID to a value or an array of values
      additionalProperties:
        oneOf:
          - { type: string }
          - { type: number }
          - { type: boolean }
          - type: array
            items:
              oneOf: [ { type: string }, { type: number }, { type: boolean } ]
    SaveDocumentRequest:
      type: object
      required: [filingCategoryId]
      properties:
        filingCategoryId: { type: integer }
        fileName: { type: string, maxLength: 255 }
        description: { type: string, maxLength: 1000 }
        metadata: { $ref: "#/components/schemas/MetadataValues" }
        overwrite: { type: boolean, default: true }
    SaveDocumentResponse:
      type: object
      properties:
        documentId: { type: integer, format: int64 }
        filingCategoryId: { type: integer }
    SearchRequest:
      type: object
      required: [filingCategoryId]
      properties:
        filingCategoryId: { type: integer }
        metadata: { $ref: "#/components/schemas/MetadataValues" }
        page: { type: integer, minimum: 1, default: 1 }
        pageSize: { type: integer, minimum: 1, maximum: 100, default: 25 }
    SearchResponse:
      type: object
      properties:
        documents:
          type: array
          items: { $ref: "#/components/schemas/DocumentSummary" }
        page: { type: integer }
        pageSize: { type: integer }
        totalCount: { type: integer }
        totalPages: { type: integer }
    DocumentSummary:
      type: object
      properties:
        documentId: { type: integer, format: int64 }
        fileName: { type: string }
        extension: { type: string, nullable: true }
        sizeBytes: { type: integer, format: int64 }
        creationDate: { type: string, format: date-time }
        modificationDate: { type: string, format: date-time }
        filingCategory:
          type: object
          properties:
            id: { type: integer }
            name: { type: string }
        metadata:
          type: array
          items:
            type: object
            properties:
              id: { type: integer }
              name: { type: string, nullable: true }
              value: { type: string }
        path: { type: string, nullable: true }
```

## Appendix B — Open Bee API subset (OpenAPI)

Trimmed from the vendor specs to the operations and fields this module uses. Security for all operations: `Authorization: Key <apiKey>`.

```yaml
openapi: 3.0.3
info:
  title: Open Bee Portal Web API (subset used by the DMS module)
  version: 6.11.x
servers:
  - url: https://{host}
    variables:
      host: { default: test.openbeecloud.com }
security:
  - apiKey: []
paths:
  /filingCategories/ws/filingCategories:
    get:
      parameters:
        - { name: taxonomies, in: query, schema: { type: string, enum: [Core, Form] } }
        - { name: permissions, in: query, schema: { type: string, enum: [edit, use, read], default: use } }
        - { name: searchFilingCategory, in: query, schema: { type: string } }
        - { name: itemsPerPage, in: query, schema: { type: integer, default: 50 } }
        - { name: page, in: query, schema: { type: integer, default: 1 } }
        - { name: order, in: query, schema: { type: string, enum: [name] } }
        - { name: direction, in: query, schema: { type: string, enum: [ASC, DESC] } }
      responses:
        "200":
          content:
            application/json:
              schema:
                type: object
                properties:
                  pagination: { $ref: "#/components/schemas/Pagination" }
                  results:
                    type: array
                    items: { $ref: "#/components/schemas/FilingCategory_output" }
        "401": { $ref: "#/components/responses/ObError" }
  /filingCategories/ws/filingCategory/{idFilingCategory}:
    get:
      parameters:
        - { name: idFilingCategory, in: path, required: true, schema: { type: integer } }
      responses:
        "200":
          content:
            application/json:
              schema: { $ref: "#/components/schemas/FilingCategory_output" }
        "400": { $ref: "#/components/responses/ObError" }
        "401": { $ref: "#/components/responses/ObError" }
  /filing/ws/list/{idList}:
    get:
      parameters:
        - { name: idList, in: path, required: true, schema: { type: integer } }
        - { name: recursive, in: query, schema: { type: boolean, default: false } }
      responses:
        "200":
          content:
            application/json:
              schema: { $ref: "#/components/schemas/List" }
        "404": { description: List does not exist }
  /browse/ws/document:
    post:
      parameters:
        - { name: OB-CHECKSUM, in: header, required: true, schema: { type: string } }
        - { name: OB-CHECKSUM-ALGO, in: header, required: true, schema: { type: string, enum: [md5, sha1, sha256] } }
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [name, description, type]
              properties:
                uploadId: { type: string }
                file: { type: string, format: binary }
                idParentFolder: { type: integer }
                path: { type: string }
                name: { type: string }
                description: { type: string }
                type: { type: integer, description: Filing category ID }
                metadatas:
                  type: object
                  description: "Sent as metadatas[<id>]=value or repeated metadatas[<id>][]=value"
                signature: { type: string }
                overwrite: { type: boolean, default: true }
                textLayer: { type: string }
      responses:
        "201":
          description: Document added. New document ID in the Id-Document response header
          headers:
            Id-Document: { schema: { type: integer } }
          content:
            application/json:
              schema:
                type: object
                properties:
                  Result: { type: string, example: Document added successfully }
        "400": { description: "MISSING_CHECKSUM, INVALID_CHECKSUM_ALGO" }
        "404": { description: "UPLOADID_NOT_FOUND, UPLOADID_EXPIRED" }
  /browse/ws/document/chunked:
    put:
      parameters:
        - { name: uploadId, in: query, schema: { type: string } }
        - { name: offset, in: query, schema: { type: string } }
        - { name: OB-CHECKSUM, in: header, schema: { type: string } }
        - { name: OB-CHECKSUM-ALGO, in: header, schema: { type: string } }
      requestBody:
        required: true
        content:
          multipart/form-data:
            schema:
              type: object
              required: [file]
              properties:
                file: { type: string, format: binary }
      responses:
        "200":
          content:
            application/json:
              schema:
                type: object
                properties:
                  uploadId: { type: string }
                  offset: { type: integer }
                  expires: { type: string }
        "400": { description: OFFSET_INVALID }
        "404": { description: "UPLOADID_NOT_FOUND, UPLOADID_EXPIRED, FILE_REQUIRED" }
  /advancedSearch/ws/advanced-search:
    post:
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: "#/components/schemas/SearchRequest" }
      responses:
        "200":
          content:
            application/json:
              schema: { $ref: "#/components/schemas/SearchResponse" }
        "400": { description: Invalid request parameter }
        "401": { description: Unauthorized }
        "500": { description: Internal server error }
  /ws/v2/file/{idDocument}:
    get:
      parameters:
        - { name: idDocument, in: path, required: true, schema: { type: integer } }
        - { name: version, in: query, schema: { type: integer } }
        - { name: thumb, in: query, schema: { type: boolean, default: false } }
      responses:
        "200":
          description: File content
          content:
            application/octet-stream:
              schema: { type: string, format: binary }
  /ws/v2/document/{idDocument}:
    get:
      parameters:
        - { name: idDocument, in: path, required: true, schema: { type: integer } }
      responses:
        "200":
          description: "document, comments, links, metadatas, versions (document fields: verify, V4)"
components:
  securitySchemes:
    apiKey:
      type: apiKey
      in: header
      name: Authorization
      description: "Authorization: Key <apiKey>"
  responses:
    ObError:
      description: Open Bee error
      content:
        application/json:
          schema:
            type: object
            properties:
              error:
                type: array
                items:
                  type: object
                  properties:
                    code: { type: string }
                    message: { type: string }
  schemas:
    Pagination:
      type: object
      properties:
        count: { type: integer }
        nbPages: { type: integer }
    FilingCategory_output:
      type: object
      properties:
        filingCategory:
          type: object
          properties:
            id: { type: integer }
            name: { type: string }
            type: { type: string, enum: [Core, Form] }
            metadata:
              type: array
              items: { $ref: "#/components/schemas/FilingCategoryMetadata_output" }
            rule: { $ref: "#/components/schemas/Rule" }
            creationDate: { type: string, format: date-time }
            idCreator: { type: integer }
    FilingCategoryMetadata_output:
      type: object
      properties:
        id: { type: integer }
        name: { type: string }
        type: { type: string, enum: [VARCHAR, INT, DECIMAL, DATE, BOOLEAN, LIST, SUBLIST, USER_GROUP] }
        mandatory: { type: boolean }
        multiple: { type: boolean }
        autoincrement: { type: boolean }
        addNewValue: { type: boolean }
        parentMetadata: { type: integer }
        idList: { type: integer }
        hasRestrictedUsers: { type: boolean }
        defaultValues: { type: object }
    Rule:
      type: object
      properties:
        active: { type: boolean }
        reapply: { type: boolean }
        grammarTree: { type: string }
        grammarName: { type: string }
    List:
      type: object
      properties:
        id: { type: integer }
        name: { type: string }
        values:
          type: array
          items:
            type: object
            properties:
              id: { type: integer }
              name: { type: string }
              image: { type: string }
              idSublist: { type: integer }
    SearchRequest:
      type: object
      required: [filters]
      properties:
        filters:
          type: array
          items:
            type: object
            required: [field, filter]
            properties:
              field: { type: string }
              idMetadata: { type: integer }
              searchIn: { type: array, items: { type: string } }
              filter:
                type: object
                required: [type]
                properties:
                  type: { type: string, enum: [EQUALS, IN, NOT_IN, DATE_RANGE, NUMERIC_RANGE, COMPARISON, PERIOD, BOOLEAN, EMPTY] }
                  value: {}
                  values: { type: array, items: {} }
                  comparator: { type: string }
                  start: { type: string }
                  end: { type: string, nullable: true }
                  min: { type: number }
                  max: { type: number }
                  amount: { type: number }
                  unit: { type: string, enum: [DAYS, WEEKS, MONTHS, YEARS] }
                  includeTrue: { type: boolean }
                  includeFalse: { type: boolean }
                  includeEmpty: { type: boolean }
        pagination:
          type: object
          properties:
            itemsPerPage: { type: integer, minimum: 1, default: 25 }
            page: { type: integer, minimum: 1, default: 1 }
        sort:
          type: array
          items:
            type: object
            required: [field, order]
            properties:
              field: { type: string }
              order: { type: string, enum: [ASC, DESC] }
    SearchResponse:
      type: object
      properties:
        results:
          type: array
          items:
            type: object
            properties:
              idFolder: { type: integer, nullable: true }
              idDocumentVersion: { type: integer }
              idDocument: { type: integer }
              name: { type: string }
              creationDate: { type: string }
              creator: { type: object, properties: { fullname: { type: string }, id: { type: integer } } }
              modifiedBy: { type: object, properties: { fullname: { type: string }, id: { type: integer } } }
              size: { type: integer }
              modificationDate: { type: string }
              idFilingCategory: { type: integer }
              nameFilingCategory: { type: string }
              extension: { type: string, nullable: true }
              folderType: { type: string }
              idParentFolder: { type: integer, nullable: true }
              documentPath: { type: string }
              isHidden: { type: boolean }
              metadata:
                type: array
                items:
                  type: object
                  properties:
                    idMetadata: { type: string }
                    value: { type: string }
        pagination:
          type: object
          properties:
            count: { type: integer }
            nbPages: { type: integer }
            itemsPerPage: { type: integer }
            page: { type: integer }
```

## Appendix C — Sample Open Bee payloads (for WireMock stubs)

IDs and names are test data.

**C.1 `GET /filingCategories/ws/filingCategories?taxonomies=Core&permissions=use&itemsPerPage=50&page=1`**

```json
{
  "pagination": { "count": 2, "nbPages": 1 },
  "results": [
    {
      "filingCategory": {
        "id": 42,
        "name": "Customer Passport",
        "type": "Core",
        "rule": { "active": true, "reapply": true, "grammarTree": "/Clients/[CLIENT_ID]", "grammarName": "PASSPORT_[CLIENT_ID]" },
        "metadata": [
          { "id": 11329, "name": "CLIENT_ID", "type": "VARCHAR", "mandatory": true, "multiple": false, "autoincrement": false },
          { "id": 11330, "name": "DOCUMENT_TYPE", "type": "LIST", "mandatory": true, "multiple": false, "autoincrement": false, "idList": 7 },
          { "id": 11331, "name": "EXPIRY_DATE", "type": "DATE", "mandatory": false, "multiple": false, "autoincrement": false },
          { "id": 11332, "name": "REFERENCE_NO", "type": "INT", "mandatory": false, "multiple": false, "autoincrement": true }
        ]
      }
    },
    {
      "filingCategory": {
        "id": 43,
        "name": "Signed Agreement",
        "type": "Core",
        "rule": { "active": true, "reapply": false, "grammarTree": "/Clients/[CLIENT_ID]/Agreements", "grammarName": null },
        "metadata": [
          { "id": 11400, "name": "CLIENT_ID", "type": "VARCHAR", "mandatory": true, "multiple": false, "autoincrement": false },
          { "id": 11401, "name": "PRODUCTS", "type": "LIST", "mandatory": false, "multiple": true, "autoincrement": false, "idList": 7 },
          { "id": 11402, "name": "SIGNED", "type": "BOOLEAN", "mandatory": false, "multiple": false, "autoincrement": false }
        ]
      }
    }
  ]
}
```

**C.2 `GET /filing/ws/list/7?recursive=false`**

```json
{
  "id": 7,
  "name": "Document types",
  "values": [
    { "id": 101, "name": "Passport", "image": null, "idSublist": null },
    { "id": 102, "name": "Emirates ID", "image": null, "idSublist": null }
  ]
}
```

**C.3 `POST /browse/ws/document` — expected multipart (for request matching)**

```
Authorization: Key test-key-onboarding
OB-CHECKSUM: <sha256 hex of the file bytes>
OB-CHECKSUM-ALGO: sha256
Content-Type: multipart/form-data; boundary=...

file              (binary; filename="passport.pdf")
name              passport.pdf
description       passport.pdf
type              42
metadatas[11329]  FIT-100234
metadatas[11330]  Passport
overwrite         true
```

Response:

```
HTTP/1.1 201 Created
Id-Document: 88213
Content-Type: application/json

{"Result":"Document added successfully"}
```

**C.4 `POST /advancedSearch/ws/advanced-search` — expected request**

```json
{
  "filters": [
    { "field": "FILING_CATEGORY", "filter": { "type": "EQUALS", "value": 42 } },
    { "field": "TYPE", "filter": { "type": "IN", "values": ["PDF", "OFFICE", "IMAGE", "AUDIO", "OTHERS"] } },
    { "field": "METADATA", "idMetadata": 11329, "filter": { "type": "IN", "values": ["FIT-100234"] } },
    { "field": "METADATA", "idMetadata": 11331, "filter": { "type": "DATE_RANGE", "start": "31/12/2027", "end": "01/01/2028" } }
  ],
  "pagination": { "itemsPerPage": 25, "page": 1 },
  "sort": [ { "field": "CREATION_DATE", "order": "DESC" } ]
}
```

Response:

```json
{
  "results": [
    {
      "idFolder": null,
      "idDocumentVersion": 99120,
      "idDocument": 88213,
      "name": "PASSPORT_FIT-100234.pdf",
      "creationDate": "2026-10-28T10:15:00",
      "creator": { "fullname": "svc onboarding", "id": 12 },
      "modifiedBy": { "fullname": "svc onboarding", "id": 12 },
      "size": 248331,
      "modificationDate": "2026-10-28T10:15:00",
      "idFilingCategory": 42,
      "nameFilingCategory": "Customer Passport",
      "extension": "pdf",
      "folderType": null,
      "idParentFolder": 5501,
      "documentPath": "/Clients/FIT-100234",
      "isHidden": false,
      "metadata": [
        { "idMetadata": "11329", "value": "FIT-100234" },
        { "idMetadata": "11330", "value": "Passport" },
        { "idMetadata": "11331", "value": "2027-12-31" }
      ]
    }
  ],
  "pagination": { "count": 1, "nbPages": 1, "itemsPerPage": 25, "page": 1 },
  "aggregations": []
}
```

**C.5 `GET /ws/v2/file/88213`**

```
HTTP/1.1 200 OK
Content-Type: application/pdf
Content-Disposition: attachment; filename="PASSPORT_FIT-100234.pdf"

<binary>
```

(Also stub a variant without `Content-Disposition` to exercise the name fallback.)

**C.6 Error bodies**

```
HTTP/1.1 401 Unauthorized
{"error":[{"code":"BAD_CREDENTIALS","message":"Invalid login or password"}]}

HTTP/1.1 403 Forbidden
{"error":[{"code":"PERMISSION_DENIED","message":"You are not allowed to perform this operation"}]}

HTTP/1.1 400 Bad Request
{"error":[{"code":"FILING_CATEGORY_IS_NOT_FOUND","message":"Filing category was not found"}]}
```
