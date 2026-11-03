# Spec — dms-implementation (Document Management: thin wrapper over Open Bee)

Source request: `.claude/modules/features/dms-implementation/source-design-doc.md`. This is the user's
verbatim request plus the "DMS Service — Implementation Brief" v1.0, 2026-10-29, referred to below as
**the brief**. Brief §§4–10 and Appendices A–C are normative and are incorporated by reference wherever
this spec says "per brief §x". Where this spec differs from the brief, **this spec wins**: the brief
itself says to follow project conventions (brief §0.3), and every difference is listed in §2.3.

Target: the **existing scaffold** `services/document-management-service` (commit `f445d0b`, branch
`feature/dms`).

---

## 1. Feature Objective & Scope

**Problem.** Today, internal consumers save, find and fetch documents through the legacy webMethods
`DMSIntegration` package. That package has the following defects (brief §1):
- an unauthenticated token-minting endpoint with hardcoded credentials;
- a new Open Bee API key on every call;
- error codes overwritten to 500;
- no audit;
- value lower-casing.

It is retired, **not ported**. This feature delivers a clean redesign: a thin wrapper over the
**Open Bee Portal Web API**.

Documents are classified by Open Bee **filing categories and metadata**:
- Open Bee's own category rules decide the folder path and file naming.
- Open Bee enforces per-consumer permissions and keeps the audit trail.

Each consumer has its own Open Bee service account and API key. This service only maps
consumer → key (brief D7).

**Target service.** `services/document-management-service` (port 8086 in dev).
- No new service.
- No new `@AutoConfiguration` or starter change.
- No new datasource and no new Liquibase changeset (see §4).

**Trigger.** Synchronous HTTP calls from internal consumers. Each call carries a starter-issued JWT bearer
token.

**In scope: 4 endpoints** (brief D1, §6). Each sits under the controller prefix `/api/v1`:

| # | Method + full path | operationId | Purpose |
|---|---|---|---|
| 1 | `GET /api/v1/dms/filing-categories` | `listDmsFilingCategories` | Categories the consumer may save into (Open Bee `permissions=use`), with metadata definitions, list values, path and naming rules |
| 2 | `POST /api/v1/dms/documents` (multipart) | `saveDmsDocument` | Save a document under a filing category with metadata |
| 3 | `POST /api/v1/dms/documents/search` | `searchDmsDocuments` | Search by category + metadata, newest first, paginated |
| 4 | `GET /api/v1/dms/documents/{documentId}/content` | `downloadDmsDocumentContent` | Stream the document's file bytes (not wrapped in the envelope) |

Also in scope:
- Per-consumer in-memory catalog cache with Caffeine.
- Open Bee client with timeouts, read retries and a circuit breaker.
- Typed `DMS###` errors.
- Logging and audit hardening for this service's profiles.
- Narrowing the actuator exposure.
- The `apiSpecs.yaml` amendment.
- `apiCollection.http` entries.
- A module `README.md`.
- Unit, WireMock and MockMvc tests.
- Opt-in `@Tag("openbee-live")` tests.

**Done** means:
- An authenticated consumer whose JWT subject is configured in `dms.consumers` can do all of the
  following through Open Bee, using **its own** API key:
  - list its categories;
  - save a document of up to 10 MB;
  - search;
  - download.
- All JSON responses use the repo's `GenericResponse` envelope with `DMS###` codes.
- No file content, metadata value, file name or API key appears in:
  - logs;
  - `api_audit_log` rows;
  - span tags;
  - responses.

**Out of scope (explicit)**
- Everything in brief §2.2 (Non-goals):
  - template/placeholder logic;
  - `getDocumentsByUID`;
  - legacy back-compat endpoints;
  - the legacy backfill job;
  - Open Bee administration from code;
  - the `SP_FUND_AGREEMENT_TC` sweep;
  - any token-minting endpoint;
  - full-text search, aggregations, workflow, favourites, bulk, delete, versions, links, comments.
- **Brief Phase 5 (live verification against Open Bee).** It appears here only as opt-in tests tagged
  `@Tag("openbee-live")`. They are skipped unless env vars provide a test base URL, a key and a category
  (brief §11.4), and they never run in CI.
- **Brief Phase 6 (chunked upload, brief §10.7 / D16).** `dms.upload.chunked-threshold` is **not**
  implemented.
- **Key Vault → pod secret wiring** (decision Q5).
  - Mapping the Key Vault secrets `DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL`, `DMS_OPENBEE_API_KEY_CRM` and
    `DMS_OPENBEE_BASE_URL` into the pod's environment is a **deployment prerequisite**.
  - That wiring lives in `.github/deployment/scripts/render-template.sh`, the k8s manifests and `infra/`.
    None of these are in this checkout, and this feature edits none of `.github/`, `infra/`, `k8s/`.
- Registering the JWT users `onboarding-portal` and `crm` in each environment. This is an operational
  step through the starter's `/api/auth/register`; see §5.4.
- Any change to `alramz-api-starter`, including `SensitiveDataMasker`'s key set (see §5.3).
- Resolving verification items **V1–V6** (brief §13). They stay **open/unverified**, and the
  config-driven defaults below are used until Phase 5 settles them. They are not blockers.

---

## 2. Repository Grounding

### 2.1 Discovered conventions (brief Phase 0, §0 step 2)

| Brief Phase 0 item | Finding | Evidence |
|---|---|---|
| Java / Boot / build | Java 21, Spring Boot **4.1.0**, Spring Framework 7.0.8, Spring Cloud 2025.1.2, Maven reactor; build from repo root | `services/alramz-common-bom/pom.xml` (parent `spring-boot-starter-parent` 4.1.0, `java.version` 21) |
| Module layout / root package | Root package `com.alramz`, flat **package-by-layer**: `controllers`, `service` (+ `service/impl`), `config`, `exception`. Generated code goes to `com.alramz.api` / `com.alramz.model`. External adapters get their own package (`com.alramz.finoux`) | `services/utility-service/src/main/java/com/alramz/**`; `services/wealth-management-service/src/main/java/com/alramz/**` |
| Contract-first | openapi-generator 7.23.0, `interfaceOnly`, `useTags`, `performBeanValidation`, `useResponseEntity`, `skipDefaultInterface`, `openApiNullable=false`. A hand-written `@RestController` implements the generated `<Tag>Api` | `services/document-management-service/pom.xml` lines 161–196; `.claude/howtos/openapi-contract-first-controllers.md` |
| Response envelope | `GenericResponse { responseCode, responseMessage, response, correlationId(uuid), errorCode(nullable), errorMsg(nullable) }`. `responseCode` mirrors the HTTP status | `services/utility-service/specs/apiSpecs.yaml` `components.schemas.GenericResponse`; `CommunityPostController.java` |
| Exception handler / error codes | Per-service `com.alramz.exception.ErrorCode` enum with codes of the form prefix + 3 digits (`FNX0xx`, `MMF0xx`, `ONB0xx`). `GlobalExceptionHandler` is `@RestControllerAdvice(basePackages = "com.alramz.controllers") @Order(0)` with a `buildResponse(status, ErrorCode, msg)` helper. An explicit `AccessDeniedException` → 403 with `errorCode` null. A catch-all → 500 that never leaks the message | `services/utility-service/.../exception/{ErrorCode,ApplicationException,GlobalExceptionHandler}.java`; `services/wealth-management-service/.../exception/*` |
| Create status | **201** with `responseCode "201"` | utility `createCommunityPost` (`apiSpecs.yaml` `'201'`, `ResponseEntity.created(...)`); wealth `createMmfProfitDistributions` |
| Outbound HTTP client | **WebClient** built from the Boot-managed `WebClient.Builder` (the starter brings `spring-webflux` + `spring-boot-webclient`), with the starter's `alramzWebClientLoggingFilterFunction`. **Resilience4j** `CircuitBreaker`/`Retry`/`TimeLimiter` beans named per client, from the registries | `services/alramz-api-starter/pom.xml` lines 80–83, 163–168, 203–207; `services/utility-service/.../config/ExternalFinouxServiceConfig.java` |
| `AbstractRestClient` | **Not usable for Open Bee.** Its `execute()` calls `headers.setBearerAuth(callerToken)` and then `headers.addAll(customHeaders)`. That would send two `Authorization` values to Open Bee and leak the caller's JWT. It also cannot return response headers (`Id-Document`) or stream a body | `services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java` lines 57–67 |
| Secrets | Env-var placeholders in profile YAML, e.g. `adapter.finoux.api-key: ${FINOUX_API_KEY:}`. In Azure, runtime secrets come from Key Vault via the CSI driver (`SecretProviderClass` rendered by the deploy action) | `services/utility-service/src/main/resources/application-dev.yml` lines 113–116; `.github/actions/deploy-aks/action.yaml` lines 5, 35–41; `docs/codebase/INTEGRATIONS.md` line 31 |
| Consumer identity | **No API-gateway consumer header exists in the repo.** Identity is the starter JWT: its subject is the logged-in **consumer** (`AuthService.login(consumer, consumerPassword)`), read back through `UserRequestContext.get().getUserId()`. Roles come from the `roles` claim (`ROLE_` prefix) and are checked by `@JwtSecured`. Decision Q1 = JWT subject | `services/alramz-api-starter/.../jwt/service/AuthService.java` line 35; `.../jwt/filter/JwtAuthFilter.java`; `.../client/UserRequestContext.java`; `.claude/howtos/jwt-security-and-public-endpoints.md` |
| Role | `APP_DOCUMENT_MANAGEMENT_SERVICE_USER` (already registered by the scaffold) | `services/document-management-service/apiCollection.http` line 18 |
| Base path / versioning | Bare paths in `apiSpecs.yaml`; the controller adds `@RequestMapping("/api/v1")` | `CommunityPostController.java` line 20; `.claude/howtos/new-endpoint-elicitation-checklist.md` §4.3 |
| Correlation ID / MDC | The starter's `CorrelationIdFilter` uses header `X-Correlation-ID` (`LoggingConstants.DEFAULT_CORRELATION_ID_HEADER`) and MDC key `correlationId`. `MDCUtil.getCorrelationIdAsUuid()` fills the envelope. `WebClientLoggingFilter` already **propagates** the header on outbound WebClient calls, so it reaches Open Bee | `.../logging/constants/LoggingConstants.java` lines 14, 32; `.../logging/interceptor/WebClientLoggingFilter.java` lines 39–46 |
| Logging / audit | The starter's request/response filters, `ApiAuditLogFilter` (inbound rows in `api_audit_log` on the middleware DB), `ApiAuditAspect` (outbound rows for **`com.alramz.client..*` only**; it serialises method **arguments** whatever the payload flags), and `@Loggable` (arguments and return values logged by default) | `.../audit/ApiAuditAspect.java` line 36, `buildRequestMap`; `.../logging/aspect/Loggable.java`; `.claude/howtos/request-response-and-db-audit-logging.md` |
| Response buffering | `ResponseLoggingFilter` and `ApiAuditLogFilter` wrap **every non-excluded response** in `ContentCachingResponseWrapper`, which buffers the whole body | `.../logging/filter/ResponseLoggingFilter.java` line 46; `.../audit/ApiAuditLogFilter.java` lines 70–71 |
| Masking | `LogMaskingUtil.maskHeader` masks the whole value of a header whose name is a sensitive key (`authorization` is a default key). `SensitiveDataMasker`'s key set is hardcoded in the starter | `.../logging/util/LogMaskingUtil.java` lines 123–131; `.claude/howtos/request-response-and-db-audit-logging.md` §2.4 |
| Resilience | Resilience4j through `spring-cloud-starter-circuitbreaker-reactor-resilience4j` (starter, transitive). No Spring Retry | `services/alramz-api-starter/pom.xml` line 166 |
| Cache library | **None in the repo** (no Spring Cache/Caffeine; reference-data uses Redis). Per brief §9.1, use **Caffeine** | grep across `services/**/pom.xml` |
| Test stack | JUnit 5 + AssertJ + Mockito, `spring-boot-starter-webmvc-test` (MockMvc), H2 for the test context. **WireMock is not present anywhere**; the existing outbound precedent stubs `ExchangeFunction` (`FinouxClientTest`). This feature adds test-scoped WireMock (brief §11.2) | `services/document-management-service/pom.xml`; `services/utility-service/src/test/java/com/alramz/finoux/FinouxClientTest.java` |
| Config style | `application.yml` + `application-{dev,docker,test,preprod,prod}.yml`. A `@Getter @Setter @Validated @ConfigurationProperties(prefix = X.PREFIX)` class with `public static final String PREFIX`. Feature prefixes are top-level (`mmf`, `adapter.finoux`) | `.claude/howtos/configuration-properties.md` §3; `services/wealth-management-service/.../config/MmfProperties.java` (`PREFIX = "mmf"`); `services/utility-service/.../config/FinouxProperties.java` |
| Jackson | The service's `JacksonConfig` defines a **Jackson 2** `ObjectMapper` with SNAKE_CASE, used by the starter's audit and `AbstractRestClient`. HTTP (de)serialisation uses Boot's own Jackson 3 setup (`tools.jackson.*`), and generated models carry explicit `@JsonProperty("<yamlName>")`, so YAML camelCase names go on the wire | `services/document-management-service/.../config/JacksonConfig.java`; `services/utility-service/.../config/JacksonConfig.java` (javadoc); generated `GenericResponse.java` `@JsonProperty("responseCode")`; wealth `GlobalExceptionHandler` imports `tools.jackson.core.JacksonException` |
| Actuator | The scaffold exposes `"*"`. Wealth uses `health,info,prometheus` ("never '*': heapdump/env/configprops leak…") | `services/document-management-service/src/main/resources/application-*.yml`; `services/wealth-management-service/src/main/resources/application-dev.yml` line 29 |
| Multipart limits | No service sets `spring.servlet.multipart.*`, so Boot's defaults apply today (1 MB per file / 10 MB per request) | grep over `services/**/application*.yml` |

### 2.2 Files this spec builds on or amends

| Concern | Artefact |
|---|---|
| Contract (amend) | `services/document-management-service/specs/apiSpecs.yaml`. Today it has only `/api/v1/info` and a 3-field `GenericResponse`. |
| Controller placement | New `com.alramz.controllers.DmsController implements com.alramz.api.DmsApi`, `@RequestMapping("/api/v1")`. `ApplicationController` (`/api/v1/info`, `@PermitAll`) is untouched; it does not implement the generated `InfoApi`. |
| Exceptions (new package) | `com.alramz.exception.{ErrorCode, DmsException, GlobalExceptionHandler}`. DMS has no `exception` package yet. |
| Open Bee adapter (new package) | `com.alramz.openbee.*`. It follows `com.alramz.finoux` and is deliberately **outside `com.alramz.client`**, so `ApiAuditAspect` never serialises its arguments (API key, metadata values, file) into `api_audit_log`. |
| Config | `com.alramz.config.{DmsProperties, OpenBeeClientConfig, DmsCacheConfig}`; `src/main/resources/application.yml` + the 5 profile files; `src/test/resources/application.yml`. |
| Jackson | `com.alramz.config.JacksonConfig` stays unchanged. The Open Bee WebClient must **not** install it as its JSON codec (onboarding's `WebClientConfig` does that and must not be copied). Open Bee JSON is camelCase. |
| Liquibase | `src/main/resources/db/changelog/{dev,docker,preprod,prod}/db.changelog-master.yaml` (`includeAll` of an absent `sql/`, `errorIfMissingOrEmpty: false`) and test `sql/001-api-audit-log.sql`, `002-jwt-tables.sql`. **Unchanged.** |
| JWT public URLs | `company.jwt.permit-all-urls` contains only `/api/v1/info` in all 5 profiles. **Unchanged.** |
| Sizing | `services/document-management-service/service.yaml`: 0.5 CPU / 1Gi, 1–5 replicas, `-Xmx768m`. Unchanged; see §5.6. |
| Postman/HTTP collection | `services/document-management-service/apiCollection.http` (add the 4 requests per `.claude/howtos/api-collection-http-requests.md`). |

### 2.3 Where this spec overrides the brief (project convention or user decision wins)

| Brief | This spec | Why |
|---|---|---|
| Consumer ID from an API-Gateway header; `dms.consumer-id-header`; "only reachable through the gateway" | Consumer ID = **JWT subject** (`UserRequestContext.get().getUserId()`); endpoints are JWT-secured. `dms.consumer-id-header` is **removed**. Identity comes from the starter JWT, not a trusted header | Decision Q1 (§7) |
| Envelope field `correlationID` | `correlationId` (uuid) | Repo `GenericResponse` |
| Paths `/v1/dms/...` | `/api/v1/dms/...` (YAML bare `/dms/...`) | Repo base-path convention |
| Create status "201 or 200" | **201** | Repo precedent |
| Package layout `<root>.dms.{api,service,client,error,config}` | Flat layout (§2.2), adapter in `com.alramz.openbee` | Repo layout + audit-aspect pointcut |
| Appendix A `MetadataValues` = `additionalProperties: oneOf[...]` | `additionalProperties: {}` (free-form → `Map<String, Object>`), with types validated in code | Avoids generator `oneOf` model classes; same wire shape |
| `dms.openbee.upload-boolean-format` values `1/0` / `true/false` | enum `ONE_ZERO` (default) / `TRUE_FALSE` | Bindable enum names; same meaning |
| Unspecified retry backoff | initial 500 ms, multiplier 2 | Residual assumption (§9) |

---

## 3. Interface Contracts

### 3.0 Common to all four operations

**Contract file.** This **amends** `services/document-management-service/specs/apiSpecs.yaml`:
- Add 4 paths, written bare (`/dms/...`). `DmsController` adds `/api/v1`.
- Tag `Dms` → one generated interface `com.alramz.api.DmsApi`.
- Every new schema name is prefixed `Dms` to avoid collisions in `com.alramz.model`.
- The existing `/api/v1/info` entry is left exactly as is.

**Envelope.** Amend `components.schemas.GenericResponse` to match utility-service exactly. Add:
- `correlationId: {type: string, format: uuid}`
- `errorCode: {type: string, nullable: true}`
- `errorMsg: {type: string, nullable: true}`

Success and error bodies:
- **Success:** `errorCode`/`errorMsg` null; `response` carries the payload.
- **Error:** `response` absent/null; `errorCode` = `DMS###`. Two exceptions:
  - 401, whose body is produced by the starter's security chain, not our handler;
  - role-check 403, where `errorCode` is null (repo precedent).
- `responseMessage` = the HTTP reason phrase.
- `errorMsg` = the DMS message. It never contains metadata values, file names, Open Bee messages, URLs or
  keys (brief §7.1, §8.2).

**Security (decision Q1).**
- Every method carries `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")` and
  `@Loggable(logArgs = false, logReturn = false)`.
- The spec-root `security: - BearerAuth: []` already applies to all operations.
- No path is added to `permit-all-urls`.
- `'401'` and `'403'` are listed per operation. The 401 is description-only (utility precedent). The 403
  has a body example (DMS109).

**Headers.**
- `Authorization: Bearer <jwt>` is required.
- `X-Correlation-ID` is optional; it is echoed as `correlationId` and propagated to Open Bee.

**503 / 502.**
- Every operation calls Open Bee, so 502 (`DMS201`/`DMS202`) and 503 (`DMS203`) can genuinely occur.
- 502 follows the brief §7.2. Utility uses 500 for upstream failures, but the brief's split ("Open Bee
  rejected/garbled" = 502 vs our own bug = 500 `DMS299`) is kept (accepted by the user).

**Shared error examples** (referenced from every path via `components.examples`). Abbreviated here; each
has the full envelope with `correlationId`:

```yaml
components:
  examples:
    Dms109:  { value: { responseCode: "403", responseMessage: "Forbidden", errorCode: "DMS109",
               errorMsg: "Consumer is not allowed to perform this operation",
               correlationId: "3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11" } }
    Dms201:  { value: { responseCode: "502", responseMessage: "Bad Gateway", errorCode: "DMS201",
               errorMsg: "Unexpected response from the document management system", correlationId: "…" } }
    Dms202:  { value: { responseCode: "502", responseMessage: "Bad Gateway", errorCode: "DMS202",
               errorMsg: "Document management system rejected the service credentials", correlationId: "…" } }
    Dms203:  { value: { responseCode: "503", responseMessage: "Service Unavailable", errorCode: "DMS203",
               errorMsg: "Document management system unavailable", correlationId: "…" } }
    Dms299:  { value: { responseCode: "500", responseMessage: "Internal Server Error", errorCode: "DMS299",
               errorMsg: "Internal Server Error", correlationId: "…" } }
```

**Status codes per operation** (each one is backed by an edge case in §6):

| Status | listDmsFilingCategories | saveDmsDocument | searchDmsDocuments | downloadDmsDocumentContent |
|---|---|---|---|---|
| 200 | yes | – | yes | yes (binary) |
| 201 | – | yes | – | – |
| 400 | – (no input) | DMS101/102/103/104/105/110 | DMS101/102/103/105/110 | DMS101 |
| 401 | yes (starter) | yes | yes | yes |
| 403 | role (null code) / DMS109 | same | same | same |
| 404 | – | – | – | DMS108 |
| 409 | – | DMS106 | – | – |
| 413 | – | DMS107 | – | – |
| 500 | DMS299 | DMS299 | DMS299 | DMS299 |
| 502 | DMS201/202 | DMS201/202 | DMS201/202 | DMS201/202 |
| 503 | DMS203 | DMS203 | DMS203 | DMS203 |

### 3.1 New `components.schemas`

```yaml
DmsListValue:
  type: object
  properties:
    id:        { type: integer, format: int64 }
    name:      { type: string }
    sublistId: { type: integer, format: int64, nullable: true }
DmsMetadataDefinition:
  type: object
  properties:
    id:               { type: integer, format: int64 }
    name:             { type: string }
    type:             { type: string, example: VARCHAR, description: "Open Bee type name, passed through (brief §8.1)" }
    mandatory:        { type: boolean }
    multiple:         { type: boolean }
    readOnly:         { type: boolean, description: "Open Bee autoincrement; client must not send it on save" }
    parentMetadataId: { type: integer, format: int64, nullable: true }
    listId:           { type: integer, format: int64, nullable: true }
    listValues:       { type: array, nullable: true, items: { $ref: '#/components/schemas/DmsListValue' } }
DmsFilingCategory:
  type: object
  properties:
    id:              { type: integer, format: int64 }
    name:            { type: string }
    path:            { type: string, nullable: true }
    autoFiling:      { type: boolean, nullable: true }
    fileNamePattern: { type: string, nullable: true }
    metadata:        { type: array, items: { $ref: '#/components/schemas/DmsMetadataDefinition' } }
DmsFilingCategoriesResponse:
  type: object
  properties:
    filingCategories: { type: array, items: { $ref: '#/components/schemas/DmsFilingCategory' } }
DmsMetadataValues:
  type: object
  description: "Map of metadata ID (numeric string) to a value (string, number, boolean) or an array of values."
  additionalProperties: {}
DmsSaveDocumentRequest:
  type: object
  required: [filingCategoryId]
  properties:
    filingCategoryId: { type: integer, format: int64, minimum: 1 }
    fileName:         { type: string, minLength: 1, maxLength: 255 }
    description:      { type: string, maxLength: 1000 }
    metadata:         { $ref: '#/components/schemas/DmsMetadataValues' }
    overwrite:        { type: boolean, default: true }
DmsSaveDocumentResponse:
  type: object
  properties:
    documentId:       { type: integer, format: int64 }
    filingCategoryId: { type: integer, format: int64 }
DmsSearchRequest:
  type: object
  required: [filingCategoryId]
  properties:
    filingCategoryId: { type: integer, format: int64, minimum: 1 }
    metadata:         { $ref: '#/components/schemas/DmsMetadataValues' }
    page:             { type: integer, minimum: 1, default: 1 }
    pageSize:         { type: integer, minimum: 1, description: "Default dms.search.default-page-size (25); max dms.search.max-page-size (100), enforced in code" }
DmsDocumentMetadataValue:
  type: object
  properties:
    id:    { type: integer, format: int64 }
    name:  { type: string, nullable: true }
    value: { type: string }
DmsFilingCategoryRef:
  type: object
  properties:
    id:   { type: integer, format: int64 }
    name: { type: string }
DmsDocumentSummary:
  type: object
  properties:
    documentId:       { type: integer, format: int64 }
    fileName:         { type: string }
    extension:        { type: string, nullable: true }
    sizeBytes:        { type: integer, format: int64 }
    creationDate:     { type: string, format: date-time, nullable: true }
    modificationDate: { type: string, format: date-time, nullable: true }
    filingCategory:   { $ref: '#/components/schemas/DmsFilingCategoryRef' }
    metadata:         { type: array, items: { $ref: '#/components/schemas/DmsDocumentMetadataValue' } }
    path:             { type: string, nullable: true }
DmsSearchResponse:
  type: object
  properties:
    documents:  { type: array, items: { $ref: '#/components/schemas/DmsDocumentSummary' } }
    page:       { type: integer }
    pageSize:   { type: integer }
    totalCount: { type: integer, format: int64 }
    totalPages: { type: integer }
```

`pageSize` has no YAML `maximum`. The configured `dms.search.max-page-size` is authoritative and is
checked in `DocumentServiceImpl` (DMS101).

### 3.2 `GET /dms/filing-categories`, operationId `listDmsFilingCategories`

**Request:** none (consumer from the JWT).

**Logic, field mapping and "do not expose" list:** per brief §6.1, with:
- `taxonomies=Core`, `permissions=use`, `itemsPerPage=50`;
- pages followed to `pagination.nbPages`;
- `order=name&direction=ASC`;
- each distinct `idList` fetched once with `recursive=false`.

The mapping must accept both wrapped (`results[].filingCategory`) and unwrapped items (V6).

**200 example:**
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "correlationId": "3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11",
  "response": { "filingCategories": [ {
    "id": 42, "name": "Customer Passport", "path": "/Clients/[CLIENT_ID]", "autoFiling": true,
    "fileNamePattern": "PASSPORT_[CLIENT_ID]",
    "metadata": [
      { "id": 11329, "name": "CLIENT_ID", "type": "VARCHAR", "mandatory": true, "multiple": false,
        "readOnly": false, "parentMetadataId": null, "listId": null, "listValues": null },
      { "id": 11330, "name": "DOCUMENT_TYPE", "type": "LIST", "mandatory": true, "multiple": false,
        "readOnly": false, "parentMetadataId": null, "listId": 7,
        "listValues": [ { "id": 101, "name": "Passport", "sublistId": null },
                        { "id": 102, "name": "Emirates ID", "sublistId": null } ] } ] } ] } }
```

**Other responses:**
- 401 (description only).
- 403 (`Dms109`; role failure gives `errorCode` null).
- 500 `Dms299`.
- 502 `Dms201`/`Dms202`.
- 503 `Dms203`.

There is no 400.

### 3.3 `POST /dms/documents`, operationId `saveDmsDocument`

**Request:** `multipart/form-data`, both parts required:

```yaml
requestBody:
  required: true
  content:
    multipart/form-data:
      schema:
        type: object
        required: [file, request]
        properties:
          file:    { type: string, format: binary }
          request: { $ref: '#/components/schemas/DmsSaveDocumentRequest' }
      encoding:
        request: { contentType: application/json }
```

`request` part example:
```json
{ "filingCategoryId": 42, "fileName": "passport.pdf",
  "metadata": { "11329": "FIT-100234", "11330": "Passport" }, "overwrite": true }
```

**Logic:** per brief §6.2. Specifics:
- **Size check.** `file.getSize() > dms.upload.max-file-size` → DMS107 (413). The container-level limit
  is in §5.2.
- **Category lookup.** `FilingCategoryCatalog.find`, using the rate-limited forced refresh on a miss
  (brief §9.1); still absent → DMS102.
- **Metadata validation.** `MetadataValidator.validateForSave`, all 7 rules of brief §8.2. An unknown
  metadata ID also triggers the single rate-limited forced refresh before DMS103.
- **File-name resolution.** Per brief §6.2. The client path is stripped to its base name.
- **Rejected names (DMS101):**
  - blank after trim;
  - containing `/` or `\`;
  - containing any ISO control character;
  - longer than 255 characters.
- **Description.** `description` falls back to the resolved name.
- **Spooling.** The upload is spooled to a temp file in `dms.upload.temp-dir` while SHA-256 is computed
  (lowercase hex). The temp file name must **not** contain the client's file name: use a random UUID with
  the `.upload` suffix. It is deleted in `finally` on every path.
- **Open Bee call.** `OpenBeeClient.addDocument` sends multipart fields:
  - `file` (with `filename=<name>`), `name`, `description`, `type`=filingCategoryId;
  - `metadatas[<id>]` (or repeated `metadatas[<id>][]` for multiples, brief §8.3);
  - `overwrite`;
  - headers `OB-CHECKSUM`, `OB-CHECKSUM-ALGO: sha256`.
- **Never sent:** `path`, `idParentFolder`, `signature`, `textLayer`.
- **No transformation.** Text values are not lower-cased or transformed.
- **No retry.**
- **Result.** `Id-Document` response header (case-insensitive) → `documentId`. If the header is missing
  or not a positive integer → DMS201.

**201 example:**
```json
{ "responseCode": "201", "responseMessage": "Created", "errorCode": null, "errorMsg": null,
  "correlationId": "3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11",
  "response": { "documentId": 88213, "filingCategoryId": 42 } }
```

There is **no `Location` header**. The repo's content path is not a resource GET for the document
metadata, so none is emitted (the utility `Location` precedent does not apply).

**Error examples** (each a full envelope):

| Status | errorCode | errorMsg example |
|---|---|---|
| 400 | DMS101 | `"fileName must not contain path separators or control characters"` |
| 400 | DMS102 | `"Filing category 42 is not available to this consumer"` |
| 400 | DMS103 | `"Metadata 99999 is not defined on filing category 42"` |
| 400 | DMS104 | `"Mandatory metadata 11329 (CLIENT_ID) is missing"` |
| 400 | DMS105 | `"Invalid value for metadata 11331 (EXPIRY_DATE)"` |
| 400 | DMS110 | `"Metadata 11500 (OWNER) has a type not supported by this service"` |
| 403 | DMS109 | `Dms109` |
| 409 | DMS106 | `"A document with this name already exists and overwrite is false"` |
| 413 | DMS107 | `"File exceeds the maximum size of 10 MB"` |
| 500 | DMS299 | `Dms299` |
| 502 | DMS201 / DMS202 | `Dms201` / `Dms202` |
| 503 | DMS203 | `Dms203` |

### 3.4 `POST /dms/documents/search`, operationId `searchDmsDocuments`

**Request:** `application/json` `DmsSearchRequest`, e.g.
`{ "filingCategoryId": 42, "metadata": { "11329": "FIT-100234" }, "page": 1, "pageSize": 25 }`.

**Logic:** per brief §6.3 and §8.4:
- **Filters.** Always send the `FILING_CATEGORY EQUALS` filter and `TYPE IN [PDF,OFFICE,IMAGE,AUDIO,OTHERS]`,
  plus one `METADATA` filter per entry (brief §8.4 shapes).
- **Date filters.** `DATE_RANGE` start = the date as `dd/MM/yyyy`; end = the next day.
- **Sort.** `sort: [{CREATION_DATE, DESC}]`.
- **Validation.** `MetadataValidator.validateForSearch`: rules 1, 4, 5, 6, 7 only. Arrays are allowed
  only for the Text/List/Sublist groups.
- **Folders.** Items with non-null `idFolder` or null `idDocument` are dropped.
- **Timestamps.** Open Bee's local timestamps are parsed as `LocalDateTime`, placed in zone
  `dms.openbee.time-zone` (Asia/Dubai), and output as ISO-8601 with offset.
- **Metadata values.** `idMetadata` (string) is parsed to a long, and `name` comes from the cached
  category (null if unknown).
- **Totals.** `totalCount = pagination.count`, `totalPages = pagination.nbPages`.
- **Retries.** Reads are retried (§5.2).

**200 example** (also the empty case: `documents: []`, `totalCount: 0`, `totalPages: 0`; never 404):
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "correlationId": "3f2c1a9e-7b4d-4c1e-9a6f-2d8e5b7c0a11",
  "response": { "documents": [ {
    "documentId": 88213, "fileName": "PASSPORT_FIT-100234.pdf", "extension": "pdf", "sizeBytes": 248331,
    "creationDate": "2026-10-28T10:15:00+04:00", "modificationDate": "2026-10-28T10:15:00+04:00",
    "filingCategory": { "id": 42, "name": "Customer Passport" },
    "metadata": [ { "id": 11329, "name": "CLIENT_ID", "value": "FIT-100234" } ],
    "path": "/Clients/FIT-100234" } ],
    "page": 1, "pageSize": 25, "totalCount": 1, "totalPages": 1 } }
```

**Errors:**
- 400: DMS101 (`"pageSize must be between 1 and 100"`), DMS102, DMS103, DMS105
  (`"Multiple values are not supported for metadata 11331 (EXPIRY_DATE)"`), DMS110.
- 401; 403 `Dms109`; 500 `Dms299`; 502 `Dms201`/`Dms202`; 503 `Dms203`.

### 3.5 `GET /dms/documents/{documentId}/content`, operationId `downloadDmsDocumentContent`

**Request:** path `documentId: {type: integer, format: int64, minimum: 1}`.

**200:**
- `content: application/octet-stream: schema {type: string, format: binary}`. The generator produces
  `ResponseEntity<org.springframework.core.io.Resource>`.
- Headers:
  - `Content-Type`: from Open Bee, falling back to `application/octet-stream`.
  - `Content-Length`: when Open Bee sends it.
  - `Content-Disposition: attachment; filename="<ascii-safe>"; filename*=UTF-8''<percent-encoded>`.
- The body is not wrapped in the envelope.

**Name chain** (brief §6.4):
1. Open Bee's `Content-Disposition` filename.
2. Otherwise `GET /ws/v2/document/{id}` (`OpenBeeClient.getDocumentName`). A failure here is swallowed
   and logged at WARN.
3. Otherwise `document-<documentId>`.

**Streaming:**
- `OpenBeeClient.downloadFile` maps the status **before** any byte is written.
- It returns headers plus a `Flux<DataBuffer>`.
- The controller wraps that flux in an `InputStreamResource` over
  `DataBufferUtils.subscriberInputStream(flux, demand)`, so there is no full buffering.
- Read timeout between buffers = `dms.openbee.transfer-read-timeout`.

**Error responses** must declare `content: application/json` (`GenericResponse`) so the generated
`produces` includes `application/json` and the error envelope can be written:
- 400 DMS101 (`"documentId must be a positive integer"`);
- 401; 403 `Dms109`;
- 404 DMS108 (`"Document not found"`);
- 500; 502; 503.

### 3.6 Java signatures (new)

```java
// com.alramz.service
public record ConsumerContext(String consumerId, String apiKey) {
    @Override public String toString() { return "ConsumerContext[consumerId=" + consumerId + ", apiKey=***]"; }
}
public class ConsumerContextResolver {                    // @Component
    ConsumerContext resolve();                            // UserRequestContext.get().getUserId() → dms.consumers lookup; else DmsException(DMS109)
}
public interface DocumentService {
    DmsFilingCategoriesResponse listFilingCategories(ConsumerContext consumer);
    DmsSaveDocumentResponse save(ConsumerContext consumer, MultipartFile file, DmsSaveDocumentRequest request);
    DmsSearchResponse search(ConsumerContext consumer, DmsSearchRequest request);
    DmsDocumentContent download(ConsumerContext consumer, long documentId);   // headers + InputStream-backed Resource
}
// com.alramz.service.impl.DocumentServiceImpl implements DocumentService
public class FilingCategoryCatalog {                      // @Component, Caffeine-backed
    List<CatalogCategory> get(ConsumerContext consumer);                       // lazy load, TTL, stale-on-failure
    Optional<CatalogCategory> find(ConsumerContext consumer, long filingCategoryId); // refresh-once (rate-limited) on miss
    boolean refreshIfAllowed(ConsumerContext consumer);                         // forced refresh, ≤ 1 per dms.cache.forced-refresh-min-interval
}
public class MetadataValidator {                          // @Component
    List<ValidatedMetadata> validateForSave(CatalogCategory category, Map<String, Object> metadata);
    List<ValidatedMetadata> validateForSearch(CatalogCategory category, Map<String, Object> metadata);
}
// com.alramz.openbee — the ONLY place with Open Bee paths, params, multipart field names and header names
public class OpenBeeClient {                              // @Component
    OpenBeeCategoryPage listFilingCategories(ConsumerContext c, int page, int itemsPerPage); // retried
    OpenBeeList getList(ConsumerContext c, long idList);                                     // retried
    long addDocument(ConsumerContext c, OpenBeeUpload upload);                               // NOT retried; returns Id-Document
    OpenBeeSearchResponse advancedSearch(ConsumerContext c, OpenBeeSearchRequest body);      // retried
    OpenBeeFile downloadFile(ConsumerContext c, long idDocument);                            // NOT retried once streaming
    Optional<String> getDocumentName(ConsumerContext c, long idDocument);                    // retried
}
public class OpenBeeException extends RuntimeException {  // httpStatus (0 = no response), obCodes, endpoint, kind
    enum Kind { HTTP_ERROR, TIMEOUT, CONNECTION, CIRCUIT_OPEN, BAD_RESPONSE }
}
public final class OpenBeeErrorMapper { static DmsException toDms(OpenBeeException e, OpenBeeOperation op, boolean overwrite); } // brief §7.3, one place
public class MetadataEncoder { /* brief §8.3, config switches */ }
public class SearchFilterBuilder { /* brief §8.4 */ }
// com.alramz.exception
public enum ErrorCode { DMS101(BAD_REQUEST, "...") … DMS299(INTERNAL_SERVER_ERROR, "..."); String code(); HttpStatus status(); String defaultMessage(); }
public class DmsException extends RuntimeException { ErrorCode getErrorCode(); }
```

`OpenBeeClient` may construct the `Authorization: Key <apiKey>` header only from the `ConsumerContext`
argument. There is no default or shared key, and no `@Value` key.

---

## 4. Data Model & Persistence Changes

- **No new tables, entities or Liquibase changesets.** No file is added under
  `db/changelog/{dev,docker,preprod,prod}/` or test `sql/`.
- **Reconciling "No database" (brief D2) with the scaffold.** The `middleware` Postgres datasource, JPA
  and Liquibase dependencies stay, because the starter needs them for:
  - the JWT `users` / refresh-token tables, which the consumer login of decision Q1 requires;
  - `api_audit_log` (DB audit is enabled).

  The DMS changelog masters point at an absent `sql/` with `errorIfMissingOrEmpty: false`, so DMS creates
  no schema of its own (the test context creates `001-api-audit-log.sql` / `002-jwt-tables.sql` in H2).
  "No database" therefore means **no DMS business data**, and that holds.
- **In-memory state** (Caffeine, `DmsCacheConfig`):
  - **Categories:** key = consumerId → `CatalogEntry { List<CatalogCategory>, Instant loadedAt }`.
    - TTL `dms.cache.categories-ttl` (15m).
    - The last successful entry must be **retained past its TTL** so it can be served stale when a
      refresh fails. Plain `expireAfterWrite` alone cannot do this; use an explicit `loadedAt` check
      (or `refreshAfterWrite`) with no hard eviction.
    - At most one concurrent load per consumer.
  - **Lists:** key = `idList` → list values, shared across consumers. Same TTL and stale rules,
    `dms.cache.lists-ttl` (15m).
  - **Forced-refresh rate limit:** per consumer, last forced refresh `Instant`; at most one per
    `dms.cache.forced-refresh-min-interval` (60s).
  - **Loading:** lazy, never at startup, so the service starts while Open Bee is down.

---

## 5. Security & Config Impact

### 5.1 Authentication / authorization (decision Q1)

- `company.jwt.permit-all-urls`: **no change**. All 4 endpoints stay authenticated, and the list remains
  `[/api/v1/info]` in all 5 profiles.
- Every `DmsController` method carries `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")`.
- Consumer ID = JWT subject (`UserRequestContext.get().getUserId()`), compared **exactly**
  (case-sensitive) with the keys of `dms.consumers`.
- Outcomes:
  - Missing, invalid or expired token: 401 from the starter.
  - Role missing: 403 with `errorCode` null (`AccessDeniedException` handler).
  - Subject not configured: 403 DMS109.
  - Open Bee 403 / `PERMISSION_DENIED` / `NOT_HAVE_PERMISSION`: 403 DMS109.
- Open Bee `Authorization: Key <apiKey>` (D8). The **caller's JWT is never forwarded** to Open Bee:
  `OpenBeeClient` does not extend `AbstractRestClient` and sets `Authorization` with `set`, not `add`.

### 5.2 New configuration

**`DmsProperties`** (`com.alramz.config`, `PREFIX = "dms"`, `@Getter @Setter @Validated`). Java defaults
for everything not environment-specific:

| Property | Type | Default | Notes |
|---|---|---|---|
| `dms.openbee.base-url` | String `@NotBlank` | none (required) | Every profile: `${DMS_OPENBEE_BASE_URL:}` |
| `dms.openbee.time-zone` | `ZoneId` | none (required) | `Asia/Dubai` in every profile (decision Q2) |
| `dms.openbee.connect-timeout` | Duration | `5s` | Q4 |
| `dms.openbee.read-timeout` | Duration | `15s` | JSON calls (Q4) |
| `dms.openbee.transfer-read-timeout` | Duration | `120s` | upload + download (Q4) |
| `dms.openbee.retry.max-attempts` | int ≥ 1 | `3` | read calls only (Q4) |
| `dms.openbee.retry.initial-backoff` | Duration | `500ms` | exponential, multiplier 2 (residual assumption) |
| `dms.openbee.upload-date-format` | String (DateTimeFormatter pattern) | `yyyy-MM-dd` | V1 |
| `dms.openbee.upload-boolean-format` | enum `ONE_ZERO`/`TRUE_FALSE` | `ONE_ZERO` | V1 |
| `dms.openbee.upload-list-value` | enum `NAME`/`ID` | `NAME` | V1; also used for search LIST filters |
| `dms.consumers.<consumerId>.api-key` | Map<String, Consumer> | empty | Q5; see below |
| `dms.cache.categories-ttl` | Duration | `15m` | Q4 |
| `dms.cache.lists-ttl` | Duration | `15m` | Q4 |
| `dms.cache.forced-refresh-min-interval` | Duration | `60s` | Q4 |
| `dms.upload.max-file-size` | `DataSize` | `10MB` | Q3 |
| `dms.upload.temp-dir` | Path | `${java.io.tmpdir}` | Brief §9.3 |
| `dms.search.default-page-size` | int | `25` | |
| `dms.search.max-page-size` | int | `100` | |

`dms.consumer-id-header` **does not exist** (removed by Q1). `dms.upload.chunked-threshold` is **not
implemented** (Phase 6 out of scope).

**Consumers (decision Q5).** Exactly two placeholder entries are shipped in **each** of
`application-{dev,docker,test,preprod,prod}.yml`. Map keys use **bracket notation**, because Spring Boot's
relaxed binding would otherwise drop characters such as `_` from map keys and the key must equal the JWT
subject verbatim:

```yaml
dms:
  openbee:
    base-url: ${DMS_OPENBEE_BASE_URL:}
    time-zone: Asia/Dubai
  consumers:
    "[onboarding-portal]":
      api-key: ${DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL:}
    "[crm]":
      api-key: ${DMS_OPENBEE_API_KEY_CRM:}
```

**Fail-fast startup validation** (brief §5.2, kept; this resolves the interaction the coordinator raised):

- **When it runs and what it checks.** At context start, `DmsProperties` validation fails startup if any
  of the following holds:
  - `base-url` is blank;
  - `time-zone` is missing or invalid;
  - **any configured consumer has a blank `api-key`**.
- **Error message.** It names the offending consumer ID(s) and the property path, never a key value.
  Example: `dms.consumers[crm].api-key must not be blank`.
- **Empty map.** An empty `dms.consumers` map is valid (every call would then give DMS109). It is not
  shipped.
- **Consequence for deployed environments (dev, docker, test, preprod, prod):** the service **will not
  start until `DMS_OPENBEE_BASE_URL`, `DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL` and
  `DMS_OPENBEE_API_KEY_CRM` are all provided as environment variables.** This is intentional and is
  recorded as a deployment prerequisite (§5.4).
- **Local dev.** `application.yml` activates `dev`, so a developer must export the three variables. Any
  non-blank key lets the app start; real Open Bee calls need real test-environment keys.
- **Automated tests.** `src/test/resources/application.yml` (the test classpath copy, which shadows the
  main `application.yml`) supplies **dummy, obviously fake** values so the test context starts without
  secrets:

  ```yaml
  dms:
    openbee:
      base-url: http://localhost:65535        # WireMock tests override with @DynamicPropertySource
      time-zone: Asia/Dubai
    consumers:
      "[onboarding-portal]":
        api-key: test-key-onboarding
      "[crm]":
        api-key: test-key-crm
  ```

  The deployed `test` profile (`src/main/resources/application-test.yml`) references the env vars like the
  other profiles. It is not the automated-test config.

**Multipart (decision Q3)**, in base `src/main/resources/application.yml` (it does not vary by
environment):

```yaml
spring:
  servlet:
    multipart:
      max-file-size: ${dms.upload.max-file-size:10MB}
      max-request-size: 11MB        # 10 MB file + headroom for the JSON `request` part and multipart framing
      resolve-lazily: true
```

`resolve-lazily: true` is **required**. With eager resolution, `MaxUploadSizeExceededException` is thrown
in `DispatcherServlet.checkMultipart` before a handler is chosen. The `basePackages`-scoped
`GlobalExceptionHandler` would then not apply, and the client would get Boot's default error body instead
of the envelope. With lazy resolution, the exception arises during argument resolution of
`DmsController.saveDmsDocument` and is mapped to **413 DMS107**. The service-level check (§3.3) also gives
DMS107 for any file over `dms.upload.max-file-size` that reaches the controller.

**Open Bee HTTP client** (`OpenBeeClientConfig`, following `ExternalFinouxServiceConfig`):

- **WebClient bean.** `@Bean("openBeeWebClient")` from the Boot `WebClient.Builder`:
  - `baseUrl(dms.openbee.base-url)`;
  - `.filter(alramzWebClientLoggingFilterFunction)`, for correlation-ID propagation and URL/status log
    lines. Payload capture is off per §5.3, and `Authorization` is masked by `LogMaskingUtil.maskHeader`.
- **Codecs.** Boot's default codecs; the SNAKE_CASE Jackson 2 `objectMapper` bean must **not** be
  installed. The in-memory codec limit is raised only as far as JSON responses need (≤ 5 MB). The download
  and upload bodies are streamed, not decoded.
- **Connect timeout.** It is set on the `ClientHttpConnector` that the Boot builder actually resolves on
  this classpath. 02_plan/03_implement must check which connector is present (reactor-netty vs JDK
  `HttpClient`) with `mvn -pl services/document-management-service -am dependency:tree` and configure that
  one. Wire logging (wiretap) stays off.
- **Read timeouts.**
  - JSON calls: a Resilience4j `TimeLimiter` at `read-timeout` (Finoux precedent).
  - Upload: `transfer-read-timeout`.
  - Download: `transfer-read-timeout` until response headers arrive, then `Flux.timeout(transfer-read-timeout)`
    between data buffers.
- **Retry `openBeeReadRetry`.**
  - `maxAttempts = dms.openbee.retry.max-attempts`, exponential backoff.
  - `retryOnException`: only `OpenBeeException` of kind `TIMEOUT`/`CONNECTION`, or `HTTP_ERROR` with
    status ≥ 500.
  - **Never** on 4xx.
  - Used by `listFilingCategories`, `getList`, `advancedSearch`, `getDocumentName`.
- **Retry `openBeeWriteRetry`.** `maxAttempts(1)`, as in the Finoux non-idempotent precedent; used by
  `addDocument` (D15). Download does not retry once bytes have started.
- **Circuit breaker `openBeeCircuitBreaker`.**
  - From the registry, id `openbee`.
  - `recordException` counts only timeouts, connection errors and 5xx. Open Bee 4xx (including one
    consumer's 401) must **not** open the breaker for every consumer.
  - Open → `OpenBeeException(CIRCUIT_OPEN)` → DMS203.

**Actuator.** In all 5 profiles, change `management.endpoints.web.exposure.include` from `"*"` to
`health,info,prometheus` (wealth precedent). With API keys in the environment, `env`/`configprops`/`heapdump`
must not be exposed.

**Health.**
- A `DmsHealthIndicator` reports **UP** with details `configuredConsumers` (count only) and
  `cachedCategoryEntries`.
- It never calls Open Bee and never reports DOWN because Open Bee is down. There is no safe key-less Open
  Bee endpoint (brief §9.2), and no service here fails readiness on a downstream.

### 5.3 Logging, audit and masking (brief §9.3)

Apply the following to **all 5** `application-{dev,docker,test,preprod,prod}.yml`.

- `company.logging.request.include-payload: false`, so multipart and search bodies (metadata values, file
  names) are never logged.
- `company.logging.response.include-payload: false`.
- `company.logging.database-logging.include-request-payload: false` and `include-response-payload: false`.
  DB audit stays `enabled: true`, so inbound rows are still written, just without bodies.
- `company.logging.database-logging.outbound-enabled`: stays unset (default `false`).
- `company.logging.excluded-paths: [/actuator/**, /swagger-ui/**, /v3/api-docs/**, /api/v1/dms/documents/*/content]`.
  Setting the list replaces the starter default, so the defaults are repeated. Excluding the content path
  stops `ResponseLoggingFilter` from buffering the download.
- `company.logging.database-logging.excluded-paths`: the existing 4 entries plus
  `/api/v1/dms/documents/*/content`, so `ApiAuditLogFilter` does not buffer the download either. Download
  auditing is covered by the structured log line below and by Open Bee's own audit trail.
- `@Loggable(logArgs = false, logReturn = false)` on every DMS controller method. No `@Loggable` with
  arguments on `DocumentServiceImpl`, `OpenBeeClient`, `MetadataValidator` or `FilingCategoryCatalog`.
- **Adapter package.** Open Bee code lives in `com.alramz.openbee`, never in `com.alramz.client`. This keeps
  it outside `ApiAuditAspect`'s argument-serialising pointcut.
- **Structured log line.** Exactly one INFO line per DMS request with:
  - `correlationId` (MDC);
  - `consumerId`;
  - `operation`;
  - `filingCategoryId`;
  - `documentId` when known;
  - `openBeeStatus`;
  - `dmsCode` (null on success);
  - `durationMs`;
  - for save, the file **extension** only.
- **Never logged anywhere:**
  - file content;
  - metadata values;
  - file names;
  - API keys;
  - `Authorization` values;
  - full Open Bee response bodies.

  Open Bee status and error **codes** are logged at WARN/ERROR with the correlation ID. Upload timeouts are
  logged at WARN with the correlation ID and the note "possible duplicate version in Open Bee".
- **`SensitiveDataMasker`: no change.** The starter is out of scope, and the measures above keep metadata
  values, file names and keys out of the audit rows and logs, so no new key is needed.
  `company.logging.masking.sensitive-keys` is also unchanged, because `authorization`/`apiKey` are already
  default keys.

### 5.4 Deployment prerequisites (outside this repo change; recorded in the module README)

1. Key Vault secrets `DMS_OPENBEE_BASE_URL`, `DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL` and
   `DMS_OPENBEE_API_KEY_CRM` exist per environment and are mapped into the pod's environment. This happens
   in the deployment templates / `infra`, **not** in this feature. **Until then, the pod fails at startup**
   (§5.2).
2. In each environment, the JWT users `onboarding-portal` and `crm` are registered through the starter's
   `/api/auth/register` with role `APP_DOCUMENT_MANAGEMENT_SERVICE_USER`. The username must equal the
   `dms.consumers` key.
3. The Open Bee admin creates one service account and API key per consumer, with `use`/`read` permissions
   on the right categories (brief §5.2–5.3).
4. Carried over from the mmfintegrations `security_report.md`: the API Gateway must block `/api/auth/**`
   (at least `register`) from external callers. Otherwise anyone could self-register with the DMS role.
   This is a platform ticket and is not fixed here.
5. Open Bee's `max_post_size` must be ≥ 10 MB plus multipart overhead (V3).

### 5.5 Dependencies (`services/document-management-service/pom.xml`)

Add:
- `com.github.ben-manes.caffeine:caffeine` (Boot-managed version);
- test-scoped `org.wiremock:wiremock-standalone`, pinned to the latest 3.x compatible with Jetty 12 /
  Boot 4. Fall back to `ExchangeFunction` stubs (`FinouxClientTest` precedent) only if no compatible
  WireMock exists, and record it in the implementation report.

No other dependency changes. The scaffold's JPA, Liquibase and Postgres dependencies stay (§4).

### 5.6 Sizing

`service.yaml` is unchanged:
- uploads spool to disk;
- downloads stream;
- the cache holds 2 consumers' catalogs.

Open question for operations (not blocking): ephemeral disk for `dms.upload.temp-dir` must allow
(concurrent uploads × 10 MB). Confirm that the container filesystem's `/tmp` is writable and sized for it.

---

## 6. Edge Cases & Error Behaviors

### 6.1 Exception types

- **`DmsException(ErrorCode, message)`.** All business and client-facing failures. The status comes from
  `ErrorCode.status()`.
- **`OpenBeeException`.** Typed, internal; it carries the HTTP status, the Open Bee codes (parsed
  defensively from `{"error":[{code,message}]}`; empty or non-JSON bodies are tolerated) and the endpoint.
  It is always converted by `OpenBeeErrorMapper` (brief §7.3) before it leaves the service layer.
- **Framework exceptions** are handled in `GlobalExceptionHandler`:
  - `MethodArgumentNotValidException`, `HandlerMethodValidationException`, `ConstraintViolationException`,
    `HttpMessageNotReadableException`, `MissingServletRequestPartException`,
    `HttpMediaTypeNotSupportedException`, `MethodArgumentTypeMismatchException` → **400 DMS101**, with a
    fixed message per field. The rejected value is never echoed.
  - `MaxUploadSizeExceededException` → **413 DMS107**.
  - `AccessDeniedException` → 403 with `errorCode` null.
  - `Exception` → 500 DMS299 (message never leaked; logged at ERROR).

`ErrorCode` table (brief §7.2, **no renumbering**: the `DMS` + 3 digit form matches the repo's prefix
scheme):

| Code | HTTP | Default message |
|---|---|---|
| DMS101 | 400 | Invalid request |
| DMS102 | 400 | Filing category is not available to this consumer |
| DMS103 | 400 | Metadata is not defined on this filing category |
| DMS104 | 400 | Mandatory metadata is missing |
| DMS105 | 400 | Invalid metadata value |
| DMS106 | 409 | A document with this name already exists and overwrite is false |
| DMS107 | 413 | File exceeds the maximum size of 10 MB (rendered from `dms.upload.max-file-size`) |
| DMS108 | 404 | Document not found |
| DMS109 | 403 | Consumer is not allowed to perform this operation |
| DMS110 | 400 | Metadata type not supported by this service |
| DMS201 | 502 | Unexpected response from the document management system |
| DMS202 | 502 | Document management system rejected the service credentials |
| DMS203 | 503 | Document management system unavailable |
| DMS299 | 500 | Internal Server Error |

Metadata-related messages name the metadata **ID and name**, never the value.

### 6.2 Enumerated failure modes

| # | Endpoint(s) | Condition | Exception | Response |
|---|---|---|---|---|
| E1 | all | No/invalid/expired bearer token | starter security chain | 401 (starter body) |
| E2 | all | Valid token without `APP_DOCUMENT_MANAGEMENT_SERVICE_USER` | `AccessDeniedException` | 403, `errorCode` null, `"Access denied"` |
| E3 | all | JWT subject not a key of `dms.consumers` | `DmsException(DMS109)` | 403 DMS109; no Open Bee call |
| E4 | all | Open Bee 403, or code `PERMISSION_DENIED`/`NOT_HAVE_PERMISSION` | `OpenBeeException` → mapper | 403 DMS109 |
| E5 | all | Open Bee 401 or `BAD_CREDENTIALS` | `OpenBeeException` → mapper | 502 DMS202; ERROR log with consumerId; circuit not affected |
| E6 | categories | 5xx/timeout/connection after 3 attempts, no cached entry | → mapper | 503 DMS203 |
| E7 | categories | Same as E6 but a previous entry exists for this consumer | – | 200 with stale data; WARN |
| E8 | categories | List fetch fails with 5xx/timeout; stale list cached → used; none cached | → mapper | 503 DMS203 (or 200 using stale) |
| E9 | categories | List 404, or 200 with an unparseable body | `OpenBeeException(BAD_RESPONSE/HTTP_ERROR 4xx)` | 502 DMS201 |
| E10 | all | Circuit breaker open | `OpenBeeException(CIRCUIT_OPEN)` | 503 DMS203 |
| E11 | save | `file` part missing | `MissingServletRequestPartException` | 400 DMS101 `"file part is required"` |
| E12 | save | `request` part missing | `MissingServletRequestPartException` | 400 DMS101 `"request part is required"` |
| E13 | save | `request` part Content-Type not JSON | `HttpMediaTypeNotSupportedException` | 400 DMS101 `"request part must be application/json"` |
| E14 | save | `request` JSON malformed or wrongly typed | `HttpMessageNotReadableException` | 400 DMS101 (fixed message) |
| E15 | save | `filingCategoryId` missing or < 1 | bean validation | 400 DMS101 |
| E16 | save | `fileName` > 255 / `description` > 1000 | bean validation | 400 DMS101 |
| E17 | save | Empty (0-byte) file | `DmsException(DMS101)` | 400 DMS101 `"file must not be empty"` |
| E18 | save | Resolved name contains `/`, `\` or a control char, or is blank | `DmsException(DMS101)` | 400 DMS101 |
| E19 | save | No `fileName` and no original filename | `DmsException(DMS101)` | 400 DMS101 `"fileName is required"` |
| E20 | save | File > 10 MB (container limit) | `MaxUploadSizeExceededException` | 413 DMS107 |
| E21 | save | File > `dms.upload.max-file-size` reaching the controller | `DmsException(DMS107)` | 413 DMS107 |
| E22 | save, search | Category not in the consumer's `use` list after one rate-limited refresh | `DmsException(DMS102)` | 400 DMS102 |
| E23 | save, search | Metadata key non-numeric or not on the category (after the refresh) | `DmsException(DMS103)` | 400 DMS103 |
| E24 | save | Mandatory, non-readOnly metadata absent/empty/empty array | `DmsException(DMS104)` | 400 DMS104 |
| E25 | save | Value sent for a readOnly (autoincrement) metadata | `DmsException(DMS105)` | 400 DMS105 |
| E26 | save, search | Array for a non-multiple field (save), or for Integer/Decimal/Date/Boolean (search) | `DmsException(DMS105)` | 400 DMS105 |
| E27 | save, search | Value does not match its type group (brief §8.1): Text blank or > 4000; non-integer; non-decimal; date not `yyyy-MM-dd`; boolean not true/false | `DmsException(DMS105)` | 400 DMS105 |
| E28 | save, search | LIST value matches no list value by exact name or id | `DmsException(DMS105)` | 400 DMS105 |
| E29 | save, search | USER_GROUP/FILE metadata sent; or (save) such metadata is mandatory on the category | `DmsException(DMS110)` | 400 DMS110 |
| E30 | save | Open Bee 201 without a valid `Id-Document` | `OpenBeeException(BAD_RESPONSE)` | 502 DMS201; ERROR log |
| E31 | save, search | Open Bee 400 `FILING_CATEGORY_IS_NOT_FOUND` | → mapper | 400 DMS102 |
| E32 | save, search | Open Bee 400 `METADATA_NOT_EXIST` | → mapper | 400 DMS103 |
| E33 | save | Open Bee 400 `MISSING_CHECKSUM`/`INVALID_CHECKSUM_ALGO` | → mapper | 502 DMS201; ERROR log ("our bug") |
| E34 | save | `overwrite=false` and Open Bee returns 409 (exact signal is V5) | → mapper | 409 DMS106 |
| E35 | all | Any other Open Bee 4xx | → mapper | 502 DMS201 |
| E36 | save | Open Bee 5xx / timeout / connection error | → mapper | 503 DMS203 after **exactly one** attempt; WARN "possible duplicate version" |
| E37 | save | Temp-file I/O failure | unexpected | 500 DMS299; temp file still deleted |
| E38 | search | `page` < 1, `pageSize` < 1 or > `dms.search.max-page-size` | bean validation / `DmsException(DMS101)` | 400 DMS101 |
| E39 | search | Body missing or malformed | `HttpMessageNotReadableException` | 400 DMS101 |
| E40 | search | No match | – | 200, `documents: []`, `totalCount: 0` |
| E41 | search | Open Bee 500 on every attempt | → mapper | 503 DMS203 after 3 attempts |
| E42 | search | Open Bee returns folder items | – | dropped silently |
| E43 | search | Open Bee timestamp unparseable | – | field null; WARN once per request |
| E44 | download | `documentId` non-numeric or < 1 | `MethodArgumentTypeMismatchException` / `ConstraintViolationException` | 400 DMS101 |
| E45 | download | Open Bee 404 (file or document info) | → mapper | 404 DMS108 |
| E46 | download | No `Content-Disposition`; document info fails | – | 200 with filename `document-<id>`; WARN |
| E47 | download | Failure after streaming started | – | connection aborted; ERROR log; no envelope possible |
| E48 | all | Any unexpected exception | `Exception` | 500 DMS299 |

Every non-2xx status in §3's matrix appears above, and every row above maps to a status declared in §3.

---

## 7. Open Questions & Decisions

The decision log below records the blocking gaps raised in the previous run of this stage. They were put
to the user by the coordinator through `AskUserQuestion` in the parent session, and the user's choices are
recorded here. Decisions D1–D16 in the brief were already taken and were not re-asked.

**Q1. Consumer identity source.** No gateway consumer header exists in the repo; the starter JWT subject
is the "consumer".
- Options:
  - **A (recommended): JWT subject.** `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")`; keys
    at `dms.consumers.<jwt-subject>.api-key`.
  - B: JWT `application` claim.
  - C: JWT plus an APIM-forwarded header.
  - D: header only, with the endpoints made public.
- **Chosen: A.**
  - 401 comes from the starter; a missing role gives 403 per the repo pattern.
  - An unconfigured subject or an Open Bee denial gives 403 DMS109.
  - `dms.consumer-id-header` is removed.
  - "Trusted gateway header" becomes "identity from the starter JWT".
  - → §3.0, §5.1, §6 E1–E4.

**Q2. Open Bee time zone (`dms.openbee.time-zone`).**
- Options: **A `Asia/Dubai` (recommended)**, B `UTC`, C other.
- **Chosen: A, `Asia/Dubai`.** → §3.4, §5.2.

**Q3. Maximum upload size.**
- Options: **A 10 MB (recommended)**, B 25 MB, C 50 MB, D other.
- **Chosen: A, 10 MB.**
  - `dms.upload.max-file-size = 10MB`.
  - `spring.servlet.multipart.max-file-size` is aligned to it, with `max-request-size` 11MB for headroom.
  - `MaxUploadSizeExceededException` → 413 DMS107 (with `resolve-lazily: true`).
  - → §5.2, §6 E20–E21.

**Q4. Cache TTL and timeouts.**
- Options:
  - **A (recommended): the brief's defaults.**
  - B: 5m TTL.
  - C: 60m TTL.
  - D: other values.
- **Chosen: A.**
  - Categories/lists TTL 15m; forced-refresh minimum interval 60s.
  - Connect 5s, read 15s, transfer 120s.
  - Read retries: max 3 attempts.
  - → §5.2.

**Q5. Shipped consumers and Key Vault wiring.**
- Options:
  - A: empty map, wiring out of scope.
  - B: named consumers, wiring out of scope.
  - C: A or B with the wiring in scope.
- **Chosen: B.**
  - Exactly `onboarding-portal` and `crm`.
  - Env placeholders `DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL` / `DMS_OPENBEE_API_KEY_CRM`, following the
    `FINOUX_API_KEY` pattern.
  - No edits to `.github/`, `infra/`, `k8s/`; the wiring is a deployment prerequisite.
  - → §1, §5.2, §5.4.

**Consequence resolved from Q5 + brief §5.2. This was not a new question.**
- The brief's fail-fast rule is kept.
- The automated-test classpath config supplies fake keys.
- Every deployed profile references the env vars.
- **A deployed environment will not start until all three variables are provided.**
- This follows mechanically from two user-confirmed inputs: named consumers with env placeholders, and
  fail-fast. Repo precedent is consistent with it (`base-url` `@NotBlank` in `FinouxProperties`). No
  further product decision was needed. → §5.2.

**Accepted from discovery (confirmed by the user, not re-asked):**
- the envelope amendment;
- the `/api/v1` prefix;
- 201 on create;
- the `ErrorCode` enum without renumbering;
- 502 for DMS201/202;
- WebClient + Resilience4j, with `OpenBeeClient` not extending `AbstractRestClient`;
- Caffeine + test-scoped WireMock;
- no new Liquibase changesets;
- the logging/audit payload fixes and path exclusions;
- narrowing the actuator exposure;
- Phases 5 and 6 out of scope (except the `@Tag("openbee-live")` tests);
- V1–V6 unverified.

**Open verification items, not blockers** (brief §13). They are settled in Phase 5 by testing and do not
block implementation:

| ID | Status | Default used until verified |
|---|---|---|
| V1 | unverified | Date `yyyy-MM-dd`, boolean `ONE_ZERO`, LIST by `NAME`, multi-value `metadatas[<id>][]` |
| V2 | unverified | Upload never sends `path`/`idParentFolder`; if Open Bee rejects this, report it, with no workaround |
| V3 | unverified | `max_post_size` assumed ≥ 10 MB (deployment prerequisite 5) |
| V4 | unverified | Download name fallback chain implemented in full |
| V5 | unverified | 409 → DMS106 only when `overwrite=false`; otherwise other 4xx → DMS201 |
| V6 | unverified | Category items accepted wrapped or unwrapped; `idMetadata` parsed from a string or a number |

---

## 8. Explicit Acceptance Criteria

**Contract and codegen**
- [ ] `services/document-management-service/specs/apiSpecs.yaml` contains:
  - the 4 bare paths `/dms/filing-categories` (GET), `/dms/documents` (POST multipart),
    `/dms/documents/search` (POST), `/dms/documents/{documentId}/content` (GET);
  - tag `Dms`;
  - operationIds `listDmsFilingCategories`, `saveDmsDocument`, `searchDmsDocuments`,
    `downloadDmsDocumentContent`;
  - the §3.1 schemas;
  - an example for every non-401 status in the §3.0 matrix.

  `/api/v1/info` is unchanged.
- [ ] `GenericResponse` in that file has `correlationId` (uuid), `errorCode` (nullable) and `errorMsg`
  (nullable).
- [ ] `mvn -pl services/document-management-service -am clean test-compile` succeeds. `com.alramz.api.DmsApi`
  is generated, and `DmsController` (`@RequestMapping("/api/v1")`) implements it.
- [ ] The download error responses declare `application/json`, and a MockMvc test with
  `Accept: application/octet-stream, application/json` receives a JSON 404 DMS108 envelope.

**Security**
- [ ] All 4 `DmsController` methods carry `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")` and
  `@Loggable(logArgs = false, logReturn = false)`.
- [ ] `company.jwt.permit-all-urls` is still exactly `[/api/v1/info]` in all 5 profiles.
- [ ] A test with no token gets 401.
- [ ] A test with a token lacking the role gets 403 with `errorCode` null.
- [ ] A test with the role but subject `unknown-consumer` gets 403 DMS109 and makes no Open Bee call
  (WireMock verifies 0 requests).
- [ ] A WireMock test proves that requests for subject `onboarding-portal` send
  `Authorization: Key test-key-onboarding`, and requests for `crm` send `Authorization: Key test-key-crm`.
  No Open Bee request ever carries a `Bearer` value or more than one `Authorization` header.
- [ ] `OpenBeeClient` and all Open Bee DTOs live in `com.alramz.openbee`; nothing new is in
  `com.alramz.client`.

**Configuration**
- [ ] `DmsProperties` (`PREFIX = "dms"`) binds every property in the §5.2 table with the stated defaults.
  `dms.consumer-id-header` and `dms.upload.chunked-threshold` do not exist.
- [ ] All 5 `application-*.yml` contain the `dms:` block from §5.2:
  - `base-url: ${DMS_OPENBEE_BASE_URL:}`;
  - `time-zone: Asia/Dubai`;
  - bracketed `"[onboarding-portal]"` / `"[crm]"` consumers with `${DMS_OPENBEE_API_KEY_ONBOARDING_PORTAL:}` /
    `${DMS_OPENBEE_API_KEY_CRM:}`.

  No real key or URL is committed.
- [ ] A unit test of the startup validation with `crm.api-key` blank fails context start. The message
  contains `crm` and the property path, and does **not** contain any key value.
- [ ] Startup also fails with blank `base-url` or missing/invalid `time-zone`.
- [ ] `src/test/resources/application.yml` supplies fake keys, base-url and `Asia/Dubai`, and
  `DocumentManagementServiceApplicationTests.contextLoads` passes.
- [ ] Base `application.yml` sets `spring.servlet.multipart.max-file-size` (10MB, from
  `dms.upload.max-file-size`), `max-request-size: 11MB`, `resolve-lazily: true`.
- [ ] All 5 profiles expose only `health,info,prometheus`.
- [ ] All 5 profiles set:
  - request/response `include-payload: false`;
  - database-logging `include-request-payload: false` / `include-response-payload: false`;
  - `/api/v1/dms/documents/*/content` in both `company.logging.excluded-paths` (with the 3 starter
    defaults repeated) and `company.logging.database-logging.excluded-paths`.
- [ ] `pom.xml` adds Caffeine and test-scoped WireMock. The JPA, Liquibase and Postgres dependencies stay.
  No changeset file is added.

**Filing categories**
- [ ] WireMock: two Open Bee pages are fetched and merged. Query parameters include `taxonomies=Core`,
  `permissions=use`, `itemsPerPage=50`, `page=1|2`.
- [ ] The response maps per brief §6.1, and the "do not expose" fields are absent from the JSON.
- [ ] A shared `idList` is fetched once with `recursive=false`.
- [ ] A second request from the same consumer within the TTL makes 0 Open Bee calls. Different consumers
  use different keys and separate cache entries.
- [ ] After the TTL, a refresh failing with 500 serves the stale entry (200) and logs WARN. With no prior
  entry the result is 503 DMS203.
- [ ] Two lookups of an unknown category within 60s trigger at most one forced Open Bee refresh.
- [ ] Wrapped and unwrapped category items both map (V6).

**Save**
- [ ] WireMock matches the multipart fields exactly as in brief Appendix C.3:
  - `file` (filename = resolved name), `name`, `description`, `type`;
  - `metadatas[11329]`, `metadatas[11330]`, `overwrite=true` (default);
  - repeated `metadatas[<id>][]` for a multiple LIST.

  There is **no** `path`, `idParentFolder`, `signature` or `textLayer` field.
- [ ] `OB-CHECKSUM` equals the lowercase hex SHA-256 of the bytes sent, and `OB-CHECKSUM-ALGO: sha256`.
- [ ] Success returns HTTP 201 with `responseCode "201"` and `response.documentId` taken from `Id-Document`.
  The header lookup is case-insensitive (test with `id-document`).
- [ ] A missing `Id-Document` gives 502 DMS201. Open Bee 401 gives 502 DMS202; 403 gives 403 DMS109.
- [ ] Open Bee 500 gives 503 DMS203 with **exactly one** request recorded by WireMock.
- [ ] The temp file is deleted on success, validation failure and Open Bee failure (assert that the temp
  dir is empty). Its name does not contain the client file name.
- [ ] Unit tests cover every brief §8.2 save rule (E22–E29), including LIST by name and by id, readOnly,
  multiple, unsupported types, unknown IDs and mandatory. Error messages contain the metadata ID/name and
  never the value.
- [ ] Unit tests cover the `MetadataEncoder` (brief §8.3): each type group, multi-value repetition, and
  each config switch (`upload-date-format`, `ONE_ZERO`/`TRUE_FALSE`, `NAME`/`ID`).
- [ ] Unit tests cover file-name resolution: explicit name, missing extension appended from the original,
  fallback to the original filename, client path stripped, invalid characters rejected, no name at all.
- [ ] A text value with mixed case is sent byte-for-byte unchanged.
- [ ] A file > 10 MB posted to a **real embedded server** (`@SpringBootTest(webEnvironment = RANDOM_PORT)`;
  MockMvc does not apply container multipart limits) returns 413 with an envelope `errorCode "DMS107"`.
  A MockMvc test hitting the service-level check also returns 413 DMS107.
- [ ] A `request` part sent as `text/plain` returns 400 DMS101.

**Search**
- [ ] WireMock matches the exact request JSON of brief Appendix C.4:
  - `FILING_CATEGORY EQUALS`;
  - `TYPE IN` with all 5 types;
  - metadata `IN` for text;
  - `DATE_RANGE` with `start "31/12/2027"`, `end "01/01/2028"` for date `2027-12-31`;
  - `COMPARISON "="` for integer;
  - `BOOLEAN` with `includeEmpty:false`;
  - pagination;
  - sort `CREATION_DATE DESC`.
- [ ] Folder items are dropped. An empty result gives 200 with `documents: []`, `totalCount: 0`.
  `page`/`pageSize`/`totalCount`/`totalPages` are mapped from Open Bee pagination.
- [ ] `creationDate` `2026-10-28T10:15:00` is returned as `2026-10-28T10:15:00+04:00`.
- [ ] `idMetadata "11329"` maps to `id: 11329` with its name from the cache. `creator`, `modifiedBy`,
  `idFolder`, `idParentFolder`, `idDocumentVersion`, `folderType` and `isHidden` are absent.
- [ ] Open Bee 500 is retried, giving exactly 3 requests and then 503 DMS203. Open Bee 400 is not retried
  (1 request).
- [ ] `pageSize: 101` gives 400 DMS101. An array for a date metadata gives 400 DMS105.

**Download**
- [ ] The bytes are returned unchanged with Open Bee's `Content-Type` (or `application/octet-stream` when
  absent) and a `Content-Disposition` carrying both `filename=` (ASCII-safe) and `filename*=UTF-8''…`.
  The body is not wrapped in the envelope.
- [ ] The name comes from Open Bee's `Content-Disposition`; otherwise from `GET /ws/v2/document/{id}`;
  otherwise `document-<id>`. Each branch has a WireMock test.
- [ ] Open Bee 404 gives 404 DMS108. `documentId=0` or `abc` gives 400 DMS101.
- [ ] A streaming test serves a WireMock body much larger than the heap headroom used by the test (e.g.
  64 MB) and completes without `OutOfMemoryError`. The response is not passed through
  `ContentCachingResponseWrapper` (path excluded).

**Errors, logging, docs**
- [ ] A unit test asserts every row of the brief §7.3 mapping table through `OpenBeeErrorMapper`.
- [ ] Every error response body has `responseCode` equal to the HTTP status, a non-null `correlationId`
  (equal to the `X-Correlation-ID` request header when sent), `errorCode` per §6, and `response` null.
- [ ] An unexpected exception gives 500 DMS299 with `errorMsg "Internal Server Error"`.
- [ ] A test capturing log output for a save with metadata value `FIT-100234` and file `passport.pdf`
  asserts that neither string, nor the API key, appears in any log line. The DMS summary line contains
  `consumerId`, `filingCategoryId`, `dmsCode`, `durationMs` and the extension `pdf`.
- [ ] Opt-in tests tagged `@Tag("openbee-live")` exist, run the brief §13 checks, and are skipped unless
  their env vars are set. Surefire does not run that tag by default.
- [ ] `services/document-management-service/README.md` documents:
  - the configuration (§5.2);
  - the fail-fast behaviour and the 3 required env vars;
  - consumer onboarding (Open Bee account + permissions + key + JWT user + config entry + secret);
  - no retry on save (duplicate-version check);
  - stale-cache behaviour;
  - identity from the starter JWT;
  - the §5.4 prerequisites;
  - V1–V6 status.
- [ ] `apiCollection.http` has one request per new endpoint (the multipart example uses a `request` part
  with `Content-Type: application/json`).
- [ ] `mvn -pl services/document-management-service -am test` passes.

---

## 9. Self-Critique

**Confidence: medium.** The repo grounding, contract and decisions are solid. The main uncertainties are
in generated-code and connector details, and in Open Bee behaviour that only Phase 5 can confirm.

**Residual non-blocking assumptions**
- **Retry backoff.** 500 ms initial, ×2. The brief says "exponential" without values.
- **DMS106 signal.** It is mapped from Open Bee **409** only when `overwrite=false`. The real code is
  unknown (V5), so until verified it may surface as DMS201.
- **502 for DMS201/DMS202.** Kept over utility-service's 500-for-upstream precedent.
- **No `Location` header on 201.**
- **Actuator narrowing.** This changes existing scaffold behaviour. Prometheus scraping still needs a JWT,
  as today, because nothing is added to `permit-all-urls`.
- **Health indicator.** It never reports Open Bee status. This follows brief §9.2's fallback, since there
  is no safe key-less endpoint.
- **Download auditing.** Downloads are excluded from `api_audit_log` and the response logging filters, to
  keep streaming possible. Audit for downloads relies on the DMS summary log line and Open Bee's trail.
- **Upload size alignment.** `max-request-size` of 11MB assumes the JSON part plus framing is < 1 MB.
  Requests far above 11 MB may see a connection reset instead of a 413 body: Tomcat's
  `max-swallow-size` defaults to 2 MB and is not changed here.
- **WireMock version.** A Boot-4/Jetty-12 compatible WireMock 3.x is assumed to exist. The fallback is the
  `ExchangeFunction` precedent.

**Weakest parts, stated plainly**
1. **Generated signatures for multipart and binary.** openapi-generator 7.23.0 is assumed to produce
   `@RequestPart("request") @Valid DmsSaveDocumentRequest request` plus `MultipartFile file` for
   `saveDmsDocument`, and `ResponseEntity<Resource>` for the download. If it produces something else
   (e.g. `String request`), 02_plan must adjust the YAML (or add a part converter) before 03_implement.
   This is untested.
2. **HTTP-client timeouts.** It is not yet known which `ClientHttpConnector` the Boot 4 `WebClient.Builder`
   resolves in this classpath. The spec requires connect and transfer timeouts on whichever connector is
   present, but cannot name the exact API.
3. **Open Bee behaviour (V1–V6).** Value formats, permission-denial signalling and response shapes are
   config-driven or handled tolerantly, but they are unverified. The live tests are the only check, and
   they are opt-in.
4. **Deployability.** It depends on work outside this repo (§5.4): Key Vault → env wiring and JWT user
   registration. By design, the pod will not start in any deployed environment until the three env vars
   exist.
