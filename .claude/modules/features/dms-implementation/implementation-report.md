# Implementation Report — dms-implementation

Stage: 03_implement. Service: `services/document-management-service`. Status: **DONE**. The final
`mvn -pl services/document-management-service -am clean test` exited 0 (327 DMS tests and 130 starter tests, 0
failures). Every measurable new class is above 80% line coverage. One acceptance criterion could not be met inside
this feature's scope: a request with no token gets 401. See Deviations D1, which needs a user decision but did not
block the work.

## 1. Summary

The service is now a thin wrapper over the Open Bee Portal Web API. It exposes 4 JWT-secured endpoints under
`/api/v1/dms`:
- `listDmsFilingCategories`;
- `saveDmsDocument` (multipart, 201);
- `searchDmsDocuments`;
- `downloadDmsDocumentContent` (a streamed binary body).

All four implement the generated `com.alramz.api.DmsApi`. The rest of the work:
- **Contract.** The `apiSpecs.yaml` amendment, including the `GenericResponse` envelope fields and `Dms`-prefixed schemas and examples.
- **Identity.** The consumer is the JWT subject, matched exactly against `dms.consumers`, which maps it to that consumer's own Open Bee key.
- **Catalog.** A Caffeine-backed per-consumer catalog, with lazy loading, a TTL checked against each entry's `loadedAt`, stale data served on outage, a single load per consumer at a time, and a rate-limited forced refresh.
- **Open Bee adapter.** Lives in `com.alramz.openbee`, not `com.alramz.client`, and does not extend `AbstractRestClient`. It uses:
  - the JDK connector;
  - a Resilience4j circuit breaker that counts only transient failures;
  - read retries, and no retry on upload or download;
  - a TimeLimiter for JSON calls, and a transfer timeout for upload, download headers and each download buffer.
- **Save path.** `MetadataValidator`, `MetadataEncoder`, `SearchFilterBuilder`, and upload spooling with SHA-256.
- **Errors.** Typed `DMS###` errors in a single `GlobalExceptionHandler` at `@Order(0)`.
- **Observability.** A single structured summary log line per request, and a health indicator that only reports counts.
- **Config hardening** in all 5 profiles: actuator exposure, payloads and headers off, excluded paths.
- **Docs.** `apiCollection.http` entries and a module README.

No new datasource or Liquibase changeset, and no edits outside `services/document-management-service`.

## 2. Files Touched

Main (all `services/document-management-service/...`):

| File | Status | In plan |
|---|---|---|
| `pom.xml` | modified | yes. Adds Caffeine, test-scoped `wiremock-standalone` 3.13.1 and the surefire `excludedGroups=${dms.test.excludedGroups}` setting. No `typeMappings` were needed. |
| `specs/apiSpecs.yaml` | modified | yes. Adds the 4 paths, the envelope fields, the schemas, the shared responses/examples and a `DmsUnauthenticated` response (see D1). `/api/v1/info` is unchanged. |
| `apiCollection.http` | modified | yes |
| `README.md` | new | yes |
| `src/main/resources/application.yml` | modified | yes (multipart block) |
| `src/main/resources/application-{dev,docker,test,preprod,prod}.yml` | modified (5 files) | yes |
| `config/DmsProperties.java` | new | yes |
| `config/OpenBeeClientConfig.java` | new | yes |
| `config/DmsCacheConfig.java` | new | yes |
| `config/DmsHealthIndicator.java` | new | yes |
| `config/DmsWebMvcConfig.java` | new | yes |
| `config/DmsRequestSummaryInterceptor.java` | new | yes |
| `exception/ErrorCode.java` | new | yes |
| `exception/DmsException.java` | new | yes |
| `exception/GlobalExceptionHandler.java` | new | yes |
| `controllers/DmsController.java` | new | yes |
| `service/ConsumerContext.java` | new | yes |
| `service/ConsumerContextResolver.java` | new | yes |
| `service/DocumentService.java` | new | yes |
| `service/impl/DocumentServiceImpl.java` | new | yes |
| `service/FilingCategoryCatalog.java` | new | yes |
| `service/CatalogCategory.java` | new | yes |
| `service/CatalogMetadata.java` | new | yes |
| `service/MetadataTypeGroup.java` | new | yes |
| `service/MetadataValidator.java` | new | yes |
| `service/ValidatedMetadata.java` | new | yes |
| `service/FileNameResolver.java` | new | yes |
| `service/UploadSpooler.java` | new | yes |
| `service/SpooledUpload.java` | new | yes |
| `service/OpenBeeTimestampConverter.java` | new | yes |
| `service/ContentDispositionFormatter.java` | new | yes |
| `service/DmsDocumentContent.java` | new | yes |
| `service/DmsRequestSummary.java` | new | yes (created in Phase 1, see D4) |
| `openbee/OpenBeeClient.java` | new | yes |
| `openbee/OpenBeeException.java` | new | yes |
| `openbee/OpenBeeOperation.java` | new | yes |
| `openbee/OpenBeeErrorBody.java` | new | yes |
| `openbee/OpenBeeErrorMapper.java` | new | yes |
| `openbee/OpenBeeCategoryPage.java` | new | yes |
| `openbee/OpenBeeFilingCategory.java` | new | yes |
| `openbee/OpenBeeList.java` | new | yes |
| `openbee/OpenBeeUpload.java` | new | yes |
| `openbee/MetadataEncoder.java` | new | yes |
| `openbee/OpenBeeSearchRequest.java` | new | yes |
| `openbee/OpenBeeSearchResponse.java` | new | yes |
| `openbee/SearchFilterBuilder.java` | new | yes |
| `openbee/OpenBeeFile.java` | new | yes |

Tests (`src/test/...`):

| File | Status | In plan |
|---|---|---|
| `resources/application.yml` | modified (dms block with fake values only) | yes |
| `config/DmsPropertiesTest`, `DmsProfileConfigTest`, `DmsHealthIndicatorTest`, `DmsRequestSummaryInterceptorTest` | new | yes |
| `exception/ErrorCodeTest`, `GlobalExceptionHandlerTest` | new | yes |
| `controllers/DmsControllerTest`, `DmsApiDispatchTest`, `DmsApiEndToEndTest`, `DmsLoggingHygieneTest` | new | yes |
| `service/ConsumerContextResolverTest`, `FilingCategoryCatalogTest`, `MetadataValidatorTest`, `FileNameResolverTest`, `UploadSpoolerTest`, `OpenBeeTimestampConverterTest`, `ContentDispositionFormatterTest`, `impl/DocumentServiceImplTest` | new | yes |
| `openbee/OpenBeeErrorMapperTest`, `MetadataEncoderTest`, `SearchFilterBuilderTest`, `OpenBeeWireMockSupport`, `OpenBeeClientCategoriesTest`, `OpenBeeClientUploadTest`, `OpenBeeClientSearchTest`, `OpenBeeClientDownloadTest`, `OpenBeeLiveVerificationTest` | new | yes |
| `service/MutableClock.java` | new | **NO**. A test-only `Clock` that is moved by hand, used by the TTL and rate-limit tests so they never sleep (D2). |

**Untouched:**
- `alramz-api-starter`, `alramz-common-bom`, `infra/`, `k8s/`, `.github/`;
- `JacksonConfig`, `ApplicationController`, `DocumentManagementServiceApplicationTests`;
- the Liquibase changelogs: no changeset was added;
- generated sources: nothing under `target/generated-sources` was hand-edited.

Nothing new was placed in `com.alramz.client`.

## 3. Test Results

All commands ran from the repo root, in this order. "targeted" means
`mvn -pl services/document-management-service -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=<classes>`.
The extra flag is needed with `-am`, because the starter module has no matching tests. Long runs were redirected to
scratchpad log files.

| # | Command | Exit | Note |
|---|---|---|---|
| 1 | `mvn -q -pl services/document-management-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | baseline |
| 2 | `mvn -q -pl ... -am clean generate-sources` | 0 | Step 2 codegen check (plan-prescribed). `listDmsFilingCategories()` as expected. |
| 3 | `... clean test-compile ...` | 0 | Phase 1 production code |
| 4 | `mvn -pl ... -am clean test` | 1 | test compile error: Spring 7 `MethodValidationResult.emptyResult()` takes no arguments |
| 5 | targeted (Phase 1 classes) | 1 | expected red: no token gives 403, not 401 (D1); HandlerMethodValidationException construction |
| 6 | `mvn -pl ... -am clean test` | 1 | `HandlerMethodValidationException` rejects empty results; unreachable branch removed |
| 7 | **`mvn -pl services/document-management-service -am clean test`** | **0** | **Phase 1 gate**: 131 DMS tests |
| 8 | `... clean generate-sources` | 0 | **Step 11 gate**: `saveDmsDocument(@RequestPart("file") MultipartFile, @Valid @RequestPart("request") DmsSaveDocumentRequest)`; no fallback |
| 9 | `... clean test-compile ...` | 1 | expected red: the `DocumentServiceImpl` constructor changed before the test was updated |
| 10 | targeted (Phase 1+2 classes) | 1 | validator test fixture bug; real-container 413 with a chunked client body (D5) |
| 11 | targeted `DmsApiEndToEndTest#save_*,MetadataValidatorTest` | 0 | |
| 12 | **`mvn -pl services/document-management-service -am clean test`** | **0** | **Phase 2 gate**: 244 DMS tests |
| 13 | `... clean generate-sources` | 0 | **Step 16 gate**: `ResponseEntity<org.springframework.core.io.Resource> downloadDmsDocumentContent(@Min(1L) @PathVariable Long)`, produces `{application/octet-stream, application/json}`; no `typeMappings` needed |
| 14 | `mvn -q -pl ... -am clean compile ...` | 0 | Phase 3 production code |
| 15 | `mvn -pl ... -am clean test` | 1 | test compile error: package-private test constant |
| 16 | `mvn -pl ... -am clean test` | 1 | stalled-body test: WireMock's dribble also delays the headers (D6) |
| 17 | targeted `OpenBeeClientDownloadTest` | 1 | the 500 ms test timeout tripped on a cold JVM; test defaults raised to 5 s/10 s |
| 18 | **`mvn -pl services/document-management-service -am clean test`** | **0** | **Phase 3 gate**: 306 DMS tests; `OpenBeeLiveVerificationTest` excluded |
| 19 | targeted (4 Phase 4 classes) | 1 | `MappedInterceptor.matches` needs a pre-parsed request path |
| 20 | `mvn -pl ... -am clean test` | 1 | test compile error: `getPathPatterns()` does not exist in Spring 7 |
| 21 | **`mvn -pl services/document-management-service -am clean test`** | **0** | **Phase 4 gate**: 325 DMS tests |
| 22 | `mvn -q -pl services/document-management-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | final compile check |
| 23 | **`mvn -pl services/document-management-service -am clean test`** | **0** | **FINAL**: BUILD SUCCESS; 327 DMS tests and 130 starter tests, 0 failures/errors/skips. JaCoCo report generated. |

`OpenBeeLiveVerificationTest` does not appear in any run: surefire excludes `openbee-live` by default.

## 4. Test Coverage

Source: `services/document-management-service/target/site/jacoco/jacoco.csv`, from run #23.
- Line % = LINE_COVERED / (LINE_COVERED + LINE_MISSED); branch % uses the same formula.
- Nested records and enums are folded into their outer class row.
- "—" means the class has no branches.

| Class | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| com.alramz.controllers.DmsController | new | 100% (16/16) | 100% (4/4) | DmsControllerTest, DmsApiDispatchTest, DmsApiEndToEndTest | PASS |
| com.alramz.config.DmsProperties (+OpenBee, Retry, Consumer, Cache, Upload, Search, 2 enums) | new | 100% (31/31) | — | DmsPropertiesTest, DmsProfileConfigTest | PASS |
| com.alramz.config.OpenBeeClientConfig | new | 100% (30/30) | 83% (5/6) | OpenBeeClientCategoriesTest (CB/retry/timeout), OpenBeeWireMockSupport | PASS |
| com.alramz.config.DmsCacheConfig | new | 100% (4/4) | — | FilingCategoryCatalogTest | PASS |
| com.alramz.config.DmsHealthIndicator | new | 100% (4/4) | — | DmsHealthIndicatorTest | PASS |
| com.alramz.config.DmsWebMvcConfig | new | 100% (3/3) | — | DmsRequestSummaryInterceptorTest#webMvcConfig_registersTheInterceptorForDmsPathsOnly | PASS |
| com.alramz.config.DmsRequestSummaryInterceptor | new | 100% (13/13) | 100% (4/4) | DmsRequestSummaryInterceptorTest, DmsLoggingHygieneTest | PASS |
| com.alramz.exception.ErrorCode | new | not measured | not measured | ErrorCodeTest (all 14 codes, `fileTooLargeMessage` MB/KB/bytes/null); `pageSizeMessage` via GlobalExceptionHandlerTest and DocumentServiceImplTest | EXCEPTION. `alramz-common-bom` excludes `**/exception/**` from JaCoCo (out of scope to change). Manual check: every method is exercised by the tests listed. |
| com.alramz.exception.DmsException | new | not measured | not measured | ErrorCodeTest#dmsException_defaultsToTheCodeMessage (all 3 constructors), plus every DMS-code test | EXCEPTION. Same BOM exclusion. All constructors exercised. |
| com.alramz.exception.GlobalExceptionHandler | new | not measured | not measured | GlobalExceptionHandlerTest (22 tests). Each handler is covered: DmsException, MaxUploadSize, MissingPart, Multipart, MediaType, TypeMismatch, NotValid (incl. empty and pageSize), MethodValidation (param and bean), ConstraintViolation (param/part/property/empty), NotReadable (field/malformed/no-cause), AccessDenied, generic, and the summary hook. Also DmsApiDispatchTest through real dispatch. | EXCEPTION. Same BOM exclusion. Every handler method is exercised. |
| com.alramz.service.ConsumerContext | new | 100% (2/2) | — | ConsumerContextResolverTest#toString_masksTheKey | PASS |
| com.alramz.service.ConsumerContextResolver | new | 100% (13/13) | 100% (8/8) | ConsumerContextResolverTest, DmsApiEndToEndTest | PASS |
| com.alramz.service.DocumentService | new | n/a (interface, no executable lines) | — | contract exercised via DocumentServiceImplTest, DmsControllerTest | EXCEPTION. Interface only; nothing executable. |
| com.alramz.service.impl.DocumentServiceImpl | new | 99% (149/150) | 86% (57/66) | DocumentServiceImplTest (28), DmsApiEndToEndTest | PASS |
| com.alramz.service.FilingCategoryCatalog (+CatalogEntry, ListEntry) | new | 100% (107/107) | 90% (70/78) | FilingCategoryCatalogTest (18) | PASS |
| com.alramz.service.CatalogCategory | new | 100% (4/4) | 75% (3/4) | FilingCategoryCatalogTest, MetadataValidatorTest | PASS |
| com.alramz.service.CatalogMetadata (+ListValue) | new | 100% (5/5) | 100% (2/2) | MetadataValidatorTest, FilingCategoryCatalogTest | PASS |
| com.alramz.service.MetadataTypeGroup | new | 100% (17/17) | 95% (18/19) | MetadataValidatorTest#typeGroups_fromOpenBeeNames | PASS |
| com.alramz.service.MetadataValidator | new | 96% (132/137) | 92% (121/132) | MetadataValidatorTest (46) | PASS |
| com.alramz.service.ValidatedMetadata | new | 100% (4/4) | — | MetadataValidatorTest, MetadataEncoderTest | PASS |
| com.alramz.service.FileNameResolver | new | 100% (29/29) | 100% (32/32) | FileNameResolverTest (20) | PASS |
| com.alramz.service.UploadSpooler | new | 90% (18/20) | 100% (2/2) | UploadSpoolerTest (6). The 2 missed lines are the "SHA-256 unavailable" throw, which every JRE makes unreachable. | PASS |
| com.alramz.service.SpooledUpload | new | 100% (1/1) | — | UploadSpoolerTest | PASS |
| com.alramz.service.OpenBeeTimestampConverter | new | 100% (11/11) | 100% (6/6) | OpenBeeTimestampConverterTest | PASS |
| com.alramz.service.ContentDispositionFormatter | new | 100% (15/15) | 100% (26/26) | ContentDispositionFormatterTest | PASS |
| com.alramz.service.DmsDocumentContent | new | 100% (2/2) | — | DocumentServiceImplTest, DmsControllerTest | PASS |
| com.alramz.service.DmsRequestSummary | new | 100% (10/10) | 100% (4/4) | DmsRequestSummaryInterceptorTest, GlobalExceptionHandlerTest, DocumentServiceImplTest | PASS |
| com.alramz.openbee.OpenBeeClient | new | 99% (193/195) | 85% (56/66) | OpenBeeClientCategoriesTest, OpenBeeClientUploadTest, OpenBeeClientSearchTest, OpenBeeClientDownloadTest | PASS |
| com.alramz.openbee.OpenBeeException (+Kind) | new | 100% (12/12) | 91% (20/22) | OpenBeeErrorMapperTest, OpenBee*Test | PASS |
| com.alramz.openbee.OpenBeeOperation | new | 100% (7/7) | — | OpenBeeErrorMapperTest | PASS |
| com.alramz.openbee.OpenBeeErrorBody (+Item) | new | 100% (2/2) | — | OpenBeeClientCategoriesTest#errorCodes_toleratesGarbage | PASS |
| com.alramz.openbee.OpenBeeErrorMapper | new | 100% (35/35) | 100% (39/39) | OpenBeeErrorMapperTest (27) | PASS |
| com.alramz.openbee.OpenBeeCategoryPage (+Pagination, Item) | new | 100% (6/6) | 100% (4/4) | OpenBeeClientCategoriesTest, FilingCategoryCatalogTest | PASS |
| com.alramz.openbee.OpenBeeFilingCategory (+Metadata, Rule) | new | 100% (3/3) | — | OpenBeeClientCategoriesTest | PASS |
| com.alramz.openbee.OpenBeeList (+Value) | new | 100% (2/2) | — | OpenBeeClientCategoriesTest#getList_sendsRecursiveFalse | PASS |
| com.alramz.openbee.OpenBeeUpload | new | 100% (4/4) | 50% (1/2) | OpenBeeClientUploadTest | PASS |
| com.alramz.openbee.MetadataEncoder | new | 100% (25/25) | 100% (18/18) | MetadataEncoderTest, OpenBeeClientUploadTest | PASS |
| com.alramz.openbee.OpenBeeSearchRequest (+Filter, Clause, PaginationSpec, Sort) | new | 100% (10/10) | 100% (2/2) | SearchFilterBuilderTest, OpenBeeClientSearchTest | PASS |
| com.alramz.openbee.OpenBeeSearchResponse (+Item, MetadataValue, Pagination) | new | 100% (4/4) | — | OpenBeeClientSearchTest, DocumentServiceImplTest | PASS |
| com.alramz.openbee.SearchFilterBuilder | new | 100% (20/20) | 100% (8/8) | SearchFilterBuilderTest | PASS |
| com.alramz.openbee.OpenBeeFile | new | 100% (2/2) | — | OpenBeeClientDownloadTest | PASS |
| pom.xml | modified | n/a (build) | n/a | Every test run. WireMock resolves; the surefire exclusion is effective (the live test never runs). | PASS |
| specs/apiSpecs.yaml | modified | n/a (contract) | n/a | Codegen gates #8/#13; compile; DmsApiDispatchTest | PASS |
| application.yml + 5 profile YAMLs | modified | n/a (config) | n/a | DmsProfileConfigTest (12 assertions across 5 profiles and the base yml) | PASS |
| test resources/application.yml | modified | n/a (config) | n/a | Every `@SpringBootTest` starts with fake keys; contextLoads passes | PASS |
| apiCollection.http | modified | n/a (manual aid) | n/a | not test tooling (per howto) | PASS |
| README.md | new | n/a (docs) | n/a | — | PASS |

**Overall gate: PASS.** No measured `new` class is at or below 80% line coverage. The 4 EXCEPTION rows are the 3
`com.alramz.exception` classes, which the BOM excludes from JaCoCo and which are cross-checked method by method,
and one interface with no executable code.

## 5. Acceptance Criteria Verification

**Contract and codegen**
- [x] The 4 bare paths, tag `Dms`, the 4 operationIds, the §3.1 schemas, and an example for every non-401 status. `/api/v1/info` is unchanged. Proven by the codegen gates #2/#8/#13 and by compilation of `DmsController implements DmsApi`.
- [x] `GenericResponse` has `correlationId` (uuid), `errorCode` and `errorMsg`. Shown by GlobalExceptionHandlerTest (all `assertError` checks).
- [x] `clean test-compile` succeeds, `DmsApi` is generated, and `DmsController` (`/api/v1`) implements it. Runs #22 and #23, plus `DmsControllerTest#everyEndpoint_isSecuredAndNeverLogsArgumentsOrReturnValues`.
- [x] Download errors are JSON, and `Accept: application/octet-stream, application/json` gets a JSON 404 DMS108. Covered by `DmsApiDispatchTest#download_notFound_isAJson404Dms108UnderTheBinaryAcceptHeader` and `DmsApiEndToEndTest#download_notFound_isJson404Dms108`.

**Security**
- [x] All 4 methods carry `@JwtSecured(roles = "APP_DOCUMENT_MANAGEMENT_SERVICE_USER")` and `@Loggable(logArgs=false, logReturn=false)`. Covered by `DmsControllerTest#everyEndpoint_isSecuredAndNeverLogsArgumentsOrReturnValues`.
- [x] `permit-all-urls` is exactly `[/api/v1/info]` in all 5 profiles. Covered by `DmsProfileConfigTest#securityActuatorAndLoggingHardening`.
- [ ] **A request with no token gets 401: NOT MET as written.** It gets **403 with an empty body** from the starter's security chain. See D1. The test pins the real behaviour: no envelope, no Open Bee call. Covered by `DmsApiEndToEndTest#noToken_isRejectedByTheStarterBeforeReachingDms` and `#invalidToken_...`.
- [x] A token without the role gets 403 with `errorCode` null. Covered by `DmsApiEndToEndTest#tokenWithoutRole_is403WithNullErrorCode`.
- [x] The role plus subject `unknown-consumer` gets 403 DMS109, and WireMock records 0 requests. Covered by `DmsApiEndToEndTest#unconfiguredSubject_is403Dms109WithoutCallingOpenBee`.
- [x] Each consumer sends `Key test-key-onboarding` or `Key test-key-crm`, exactly one `Authorization` header, never `Bearer`. Covered by `DmsApiEndToEndTest#categories_endToEnd_perConsumerKeyAndCache` and `OpenBeeClientCategoriesTest#eachConsumerSendsItsOwnKey_neverBearer`.
- [x] Open Bee classes live in `com.alramz.openbee` and nothing is in `com.alramz.client`. Verified by a file glob.

**Configuration**
- [x] `DmsProperties` binds the §5.2 table with its defaults, and has no `consumer-id-header` or `chunked-threshold`. Covered by `DmsPropertiesTest#defaults_matchTheSpecTable`, `#binding_bracketedKeysVerbatimAndTypedValues` and `#noConsumerIdHeaderOrChunkedThreshold`.
- [x] All 5 profiles have the `dms:` block with env placeholders, `Asia/Dubai` and bracketed consumers, and no literal key. Covered by `DmsProfileConfigTest#dmsBlock_envPlaceholdersOnly`.
- [x] A blank `crm` key fails startup; the message names `crm` and `consumers[crm].apiKey` and contains no key. Covered by `DmsPropertiesTest#blankConsumerKey_failsStartupNamingTheConsumerButNoKey`.
- [x] A blank base-url fails startup, as does a missing or invalid time-zone. Covered by `DmsPropertiesTest#blankBaseUrl_failsStartup`, `#missingTimeZone_failsStartup` and `#invalidTimeZone_failsStartup`.
- [x] The test `application.yml` supplies fake values, and `contextLoads` passes. Covered by `DocumentManagementServiceApplicationTests`.
- [x] The base `application.yml` has the multipart settings (10MB from the property, 11MB, lazy). Covered by `DmsProfileConfigTest#baseApplicationYml_multipartLimits`.
- [x] All 5 profiles expose only `health,info,prometheus`. Covered by `DmsProfileConfigTest#securityActuatorAndLoggingHardening`.
- [x] Payload flags are false, and the content path is in both excluded-path lists, with the starter defaults repeated. Covered by `DmsProfileConfigTest#securityActuatorAndLoggingHardening`.
- [x] The pom has Caffeine and test-scoped WireMock; JPA, Liquibase and Postgres are kept; no changeset added. Verified by inspection plus the build.

**Filing categories**
- [x] Two pages are merged, with query params `taxonomies=Core`, `permissions=use`, `itemsPerPage=50`, `page=n`. Covered by `FilingCategoryCatalogTest#get_mergesPagesAndFetchesASharedListOnce` and `OpenBeeClientCategoriesTest#listFilingCategories_sendsQueryParamsAndExactlyOneKeyAuthorization`.
- [x] The brief §6.1 mapping holds, and "do not expose" fields are absent. Covered by `DocumentServiceImplTest#listFilingCategories_mapsPerBrief61` and `DmsApiEndToEndTest#categories_endToEnd_perConsumerKeyAndCache`.
- [x] A shared `idList` is fetched once, with `recursive=false`. Covered by `FilingCategoryCatalogTest#get_mergesPagesAndFetchesASharedListOnce` and `OpenBeeClientCategoriesTest#getList_sendsRecursiveFalse`.
- [x] A second call within the TTL makes 0 calls, and consumers are isolated. Covered by `FilingCategoryCatalogTest#get_withinTtl_makesNoOpenBeeCall` and `#consumers_areIsolated`, and by the E2E categories test.
- [x] A failing refresh after the TTL serves stale data with a WARN; with no prior entry the result is DMS203. Covered by `FilingCategoryCatalogTest#afterTtl_failingRefresh_servesStaleWithWarn`, `#noPriorEntry_failureIsRethrown` and `DocumentServiceImplTest#listFilingCategories_openBeeUnavailable_isDms203`.
- [x] Two unknown-category lookups within 60 s make at most one forced refresh. Covered by `FilingCategoryCatalogTest#find_unknownCategory_refreshesOnce_thenRateLimits`.
- [x] Wrapped and unwrapped items both map. Covered by `FilingCategoryCatalogTest#wrappedAndUnwrappedItems_bothMap` and `OpenBeeClientCategoriesTest#unwrappedItems_areAccepted`.

**Save**
- [x] The multipart fields match Appendix C.3 exactly, with no path, idParentFolder, signature or textLayer, and with a repeated `metadatas[id][]`. Covered by `OpenBeeClientUploadTest#addDocument_sendsExactlyTheAppendixC3Fields` and `#multipleList_isRepeatedArrayField_andMixedCaseTextUnchanged`.
- [x] `OB-CHECKSUM` is the lowercase SHA-256 and `OB-CHECKSUM-ALGO` is `sha256`. Covered by `OpenBeeClientUploadTest#addDocument_sendsExactlyTheAppendixC3Fields` and `UploadSpoolerTest#spool_computesLowercaseSha256AndUsesARandomName`.
- [x] Save returns 201 with `responseCode` "201" and `documentId` from `Id-Document`, read case-insensitively. Covered by `DmsApiEndToEndTest#save_endToEnd_returns201AndDeletesTheSpoolFile` and `OpenBeeClientUploadTest#idDocument_isReadCaseInsensitively`.
- [x] A missing `Id-Document` gives DMS201; 401 gives DMS202; 403 gives DMS109. Covered by `OpenBeeClientUploadTest#missingOrInvalidIdDocument_isBadResponse`, `#unauthorizedAndForbidden_areHttpErrors` and `OpenBeeErrorMapperTest#httpErrors`.
- [x] Open Bee 500 gives 503 DMS203 with exactly 1 request. Covered by `DmsApiEndToEndTest#save_openBeeServerError_is503Dms203AfterExactlyOneAttempt` and `OpenBeeClientUploadTest#serverError_isNotRetried`.
- [x] The temp file is deleted on success, validation failure and Open Bee failure, and its name never contains the client name. Covered by the `DocumentServiceImplTest#save_*` tests (temp dir empty), the E2E save tests and `UploadSpoolerTest`.
- [x] All save rules §8.2 (E22–E29) are covered, and messages contain the ID and name but never the value. Covered by `MetadataValidatorTest` (46 tests).
- [x] Encoder: each type group, repetition and every config switch. Covered by `MetadataEncoderTest`.
- [x] File-name resolution. Covered by `FileNameResolverTest`.
- [x] Mixed-case text is sent unchanged. Covered by `OpenBeeClientUploadTest#multipleList_isRepeatedArrayField_andMixedCaseTextUnchanged` and `MetadataValidatorTest#save_mixedCaseText_isKeptByteForByte`.
- [x] A file over 10 MB on the real container gives 413 DMS107, and the service-level check gives 413 too. Covered by `DmsApiEndToEndTest#save_fileOverTheContainerLimit_is413Dms107` and `DmsApiDispatchTest#save_serviceLevelSizeCheck_is413Dms107`.
- [x] A `request` part sent as text/plain gives 400 DMS101. Covered by `DmsApiDispatchTest#save_requestPartAsTextPlain_isDms101` and `DmsApiEndToEndTest#save_requestPartNotJson_is400Dms101`.

**Search**
- [x] The exact Appendix C.4 JSON is matched with WireMock `equalToJson`, and every clause type is covered. Covered by `OpenBeeClientSearchTest#advancedSearch_sendsTheExactAppendixC4Body_andParsesTheResponse`, `SearchFilterBuilderTest` and `DmsApiEndToEndTest#search_endToEnd_exactOpenBeeBodyAndMappedResponse`.
- [x] Folders are dropped, an empty result is 200 with `[]` and 0, and pagination is mapped. Covered by `DocumentServiceImplTest#search_defaultsMapsAndDropsFolders` and `#search_emptyResult_isEmptyListAndZeroTotals`.
- [x] A local timestamp is returned as `2026-10-28T10:15:00+04:00`. Covered by `OpenBeeTimestampConverterTest`, the E2E search test and `DmsApiDispatchTest#search_returnsMappedEnvelopeWithOffsetTimestamps`.
- [x] `idMetadata` maps to its id and cached name, and the internal fields are absent. Covered by `DocumentServiceImplTest#search_defaultsMapsAndDropsFolders` and the E2E search test.
- [x] A 500 is retried 3 times and then gives 503; a 400 is sent once. Covered by `DmsApiEndToEndTest#search_openBeeServerError_isRetriedThreeTimesThen503`, `#search_openBeeBadRequest_isNotRetried` and `OpenBeeClientSearchTest`.
- [x] `pageSize` 101 gives DMS101, and a date array gives DMS105. Covered by `DmsApiEndToEndTest#search_pageSizeOverMaxAndDateArray_areRejected`.

**Download**
- [x] Bytes come back unchanged, Content-Type is passed through or defaulted, the dual Content-Disposition is set, and the body is not wrapped. Covered by `DmsApiEndToEndTest#download_nameFromContentDisposition`, `#download_nameFromDocumentInfo_thenDefault`, `DmsApiDispatchTest#download_streamsRawBytesNotAnEnvelope` and `OpenBeeClientDownloadTest`.
- [x] The name chain works at each step (header, then document info, then `document-<id>`). Covered by the E2E download tests and `DocumentServiceImplTest#download_*`.
- [x] A 404 gives DMS108; a documentId of 0 or `abc` gives DMS101. Covered by `DmsApiEndToEndTest#download_notFound_isJson404Dms108`, `#download_invalidDocumentId_isDms101` and `DmsApiDispatchTest#download_invalidDocumentId_isDms101WithoutEchoingTheValue`.
- [x] A 64 MB stream arrives unchanged (SHA-256 compared) and chunked, with no Content-Length, which shows no caching wrapper buffered it. Covered by `DmsApiEndToEndTest#download_64MegabytesStreamsUnbufferedAndUnchanged`.

**Errors, logging, docs**
- [x] Every row of the §7.3 table is asserted. Covered by `OpenBeeErrorMapperTest`.
- [x] Every error has `responseCode` equal to the status, a `correlationId` that echoes `X-Correlation-ID`, an `errorCode`, and `response` null. Covered by GlobalExceptionHandlerTest, `DmsApiDispatchTest#expectError` and `#unknownConsumer_is403Dms109`.
- [x] An unexpected exception gives 500 DMS299 with "Internal Server Error". Covered by `DmsApiDispatchTest#unexpectedException_is500Dms299` and `GlobalExceptionHandlerTest#unexpected_is500Dms299WithoutLeakingTheMessage`.
- [x] No `FIT-100234`, `passport.pdf` or API key appears in the logs, and the summary line has `consumerId`, `filingCategoryId`, `dmsCode`, `durationMs` and `pdf`. Covered by `DmsLoggingHygieneTest#saveAndSearch_neverLogValuesNamesOrKeys`.
- [x] Opt-in `@Tag("openbee-live")` tests exist, are gated on env vars, and surefire excludes them. Covered by `OpenBeeLiveVerificationTest`; absent from runs #18 and #23.
- [x] The README documents every required topic. Covered by `README.md`.
- [x] `apiCollection.http` has one block per endpoint, the multipart `request` part is `application/json`, and there are -ve cases. Covered by `apiCollection.http`.
- [x] `mvn -pl services/document-management-service -am test` passes. Run #23.

## 6. Deviations & Decisions

No `AskUserQuestion` was available or raised. None of the items below was a blocking gap in the sense of the
stage contract: each was either outside every in-scope option or an ordinary implementation choice. **D1 needs a
user decision.**

- **D1 — 401 vs 403 for a missing or invalid token (acceptance criterion not met as written).**
  - The starter's `JwtAutoConfiguration` chain configures no `AuthenticationEntryPoint`. Spring Security therefore answers an unauthenticated request with its default 403 and an empty body, not 401. This was observed in run #5.
  - The only fixes are:
    - (a) an `alramz-api-starter` change, for example `exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(UNAUTHORIZED)))`;
    - (b) a service-local `SecurityFilterChain`, which `jwt-security-and-public-endpoints.md` forbids.
  - Both are out of this stage's constraints, so no in-scope implementation could satisfy the criterion.
  - What I did:
    - kept the plan's `'401'` response in the contract, as a `DmsUnauthenticated` component that states the real behaviour;
    - pinned the actual behaviour in tests: rejected before DMS, no envelope, no Open Bee call;
    - documented it in the README under "Known gaps".
  - **Question for the user:** accept 403 for now, or raise a starter change, which affects every service?
- **D2 — Unplanned test helper `src/test/java/com/alramz/service/MutableClock.java`.** It is a hand-moved `Clock` for deterministic TTL and rate-limit tests. Test-only.
- **D3 — Unknown-metadata forced refresh.** It is triggered in `DocumentServiceImpl.categoryFor` via `MetadataValidator.firstUnknownId`, not inside `MetadataValidator`. This keeps the spec §3.6 validator signatures (`validateForSave(CatalogCategory, Map)`) pure. Behaviour is unchanged: one rate-limited refresh before DMS103.
- **D4 — `DmsRequestSummary` was created in Phase 1, not Phase 4,** so the `GlobalExceptionHandler.buildResponse` hook was written once. No behavioural difference.
- **D5 — The real-container 413 test sends a fixed `Content-Length` body** using `java.net.http`. With a chunked request body (Spring `RestClient` multipart), Tomcat aborts the connection after the 413 instead of returning a readable body. This is the G3 residual, documented in the README.
- **D6 — Per-buffer download timeout test.** It uses an `ExchangeFunction` stub (the `FinouxClientTest` precedent), because WireMock's chunked dribble delays the headers too. WireMock itself works with Boot 4.1 / JUnit 6, so the **G7 fallback was not needed** for the client tests. A separate WireMock test covers the header timeout.
- **D7 — Download uses the 1-attempt `openBeeWriteRetry`,** so a download is never retried, either before or after streaming starts. The plan said "no retry once streaming".
- **D8 — The real-server tests mirror the base `application.yml` multipart settings and the deployed logging exclusions via `@SpringBootTest(properties=...)`.** The test-classpath `application.yml` shadows the main one; per G9 no profile is activated.
- **D9 — Codegen gates.** Steps 11 and 16 matched the expected signatures, so no fallback (String part or `typeMappings`) was applied and no pom `configOptions` change was made.
- **D10 — Commands outside the contract's three forms.**
  - `generate-sources` was run for the codegen gates, as plan Steps 2, 11 and 16 prescribe.
  - `-Dsurefire.failIfNoSpecifiedTests=false` was added to targeted runs, because `-am` would otherwise fail on the starter module.
  - One `clean compile` was run.
- **D11 — `pageSizeMessage` lives on `ErrorCode`,** so the service and the handler share the text. The service does not depend on the handler.
- **D12 — The summary's `openBeeStatus` records Open Bee's actual status** (201 for an upload, 200 for a download), not a blanket 200.

## 7. Howtos Consulted

- `testing-patterns.md`.
  - Mockito-first direct construction for the service, controller and helper tests.
  - The `@WebMvcTest` pitfall was avoided. Real-dispatch tests follow wealth's `MmfApiDispatchTest`: standalone MockMvc reusing the context's converters, validator and method-validation proxy.
  - `@SpringBootTest(RANDOM_PORT)` is used only where the filter chain or container behaviour is the subject.
  - The howto's `spring.profiles.active=test` advice was **not** followed (G9): that profile needs real env vars, and the existing smoke test uses no profile.
- `openapi-contract-first-controllers.md`. Contract-first, bare paths, the controller implements the generated interface, and no hand-edited generated code. The `/api/v1/info` quirk was left alone.
- `exception-handling.md`. A single advice, `@RestControllerAdvice(basePackages="com.alramz.controllers") @Order(0)`, following wealth's handler and **not** the unreachable `OnboardingExceptionHandler`. The status lives on the `ErrorCode` enum, not in the exception class, and there is a catch-all that never leaks the message.
- `configuration-properties.md`. A Lombok `@Getter @Setter` class with a `PREFIX` constant, bound by `@EnableConfigurationProperties` on `OpenBeeClientConfig`. No `@Value`.
- `jwt-security-and-public-endpoints.md`. `@JwtSecured` on every method; no `permit-all-urls` change; identity via `UserRequestContext`. Its advice to "extend `AbstractRestClient`" was deliberately **not** followed for Open Bee: it would forward the caller's JWT and send two `Authorization` headers (the plan's drift note).
- `request-response-and-db-audit-logging.md`.
  - Payload flags off; the excluded-path lists repeat the starter defaults.
  - The adapter sits outside `ApiAuditAspect`'s `com.alramz.client..*` pointcut.
  - `SensitiveDataMasker` and `masking.sensitive-keys` are unchanged, because the data never reaches those pipelines.
- `api-collection-http-requests.md`. Synthetic values only (`TEST-000001`, `onboarding-portal@example.com`). The register/login consumer was updated to the DMS identity. Every endpoint has a -ve block. No token is committed.
- `new-endpoint-elicitation-checklist.md`. Read via the plan, for the base-path and 201 conventions.
- Not applicable: `liquibase-migrations.md` (no changeset), `multi-datasource-and-jpa.md` (no datasource change), `scheduler-jobs.md` (no periodic work).

## 8. Self-Critique

**Confidence: high** for the implemented behaviour, which is tested end to end through the real server, the
security chain and a WireMock Open Bee. **Medium** for real Open Bee interoperability (V1–V6 and chunked uploads),
which only the opt-in live tests can settle.

**technical_gaps status**

| Gap | Status |
|---|---|
| G1 | resolved: the exact save path is excluded from request/response logging; DmsLoggingHygieneTest proves no leak |
| G2 | resolved: `include-headers: false` in all 5 profiles |
| G3 | residual: a request far over 11 MB or a chunked body gets a connection reset (README) |
| G5 | resolved: both codegen gates passed without fallback |
| G6 | resolved: explicit `JdkClientHttpConnector` |
| G7 | resolved: WireMock 3.13.1 works |
| G9 | followed: no profile in tests |
| Howto drift | followed the code |
| Upload transfer encoding (chunked) | residual V-item: listed in the README and in OpenBeeLiveVerificationTest's scope |
| Download Accept / Range | documented |
| Catalog concurrency | implemented and tested (single-flight load, rate limit) |
| Numeric precision | BigDecimal/BigInteger tested |
| Sizing and deployability prerequisites | README |

**Residual, non-blocking risks**
- Checkstyle warns (warning only, the build is not failing) that `GlobalExceptionHandler` has a class fan-out complexity of 32 against a maximum of 30. I left it, because splitting the single advice would reintroduce the multi-advice ordering pitfall.
- Downloads are excluded from `api_audit_log` and from the starter's response logging. Download audit relies on the DMS summary line plus Open Bee's own trail.
- The shared circuit breaker (`openbee`) is service-wide: sustained Open Bee 5xx errors for one consumer's calls also fail other consumers fast. This is intended; 4xx errors never count.
- Startup fails in every deployed environment until the three `DMS_OPENBEE_*` env vars are wired. This is intended per the spec.

**Flag for 04_security**
1. **D1:** an unauthenticated request gets 403, not 401. This is starter-wide behaviour.
2. **Open self-registration.** `/api/auth/register` lets anyone self-register with the DMS role unless the gateway blocks it, and `service.yaml` has `ingress.external: true`. It is a deployment prerequisite (README #4) and is not enforced in code.
3. **New untrusted input parsing:**
   - multipart `file`, and the JSON `request` part with a free-form `metadata` map;
   - client file names, which are base-named and checked for separators and control characters;
   - the search JSON;
   - `documentId`.
   Uploads are spooled to `dms.upload.temp-dir` (`Files.createTempFile`, random names) and deleted in `finally`. Disk exhaustion is bounded only by the 10 MB limit times concurrency.
4. **Download response headers come from Open Bee.**
   - `Content-Type` is passed through from Open Bee and could be `text/html`.
   - `Content-Disposition` is built from a name supplied by Open Bee. It is always `attachment`, and quotes, backslashes, control characters and non-ASCII are replaced in `filename=`.
   - I relied on Spring Security's default `X-Content-Type-Options: nosniff`; please verify it is present on these responses.
5. **The client-supplied `X-Correlation-ID` value is forwarded verbatim to Open Bee,** both by the starter's behaviour and by `OpenBeeClient`'s explicit header.
6. **New outbound calls carry per-consumer API keys** (`Authorization: Key ...`). Keys are only in env vars.
   - Actuator exposure is narrowed to `health,info,prometheus`.
   - `ConsumerContext`, `DmsProperties.Consumer` and `OpenBeeUpload` mask or omit secrets in `toString()`.
   - The generated `DmsSaveDocumentRequest.toString()` does print metadata and the file name. No DMS code logs it, but any future `@Loggable(logArgs=true)` would.
7. **Open Bee error bodies are parsed with a lenient Jackson 2 mapper,** bounded by the 5 MB codec limit. Open Bee messages are never echoed to callers or logs.

## 9. Addendum — live verification against the Open Bee test environment (2026-10-30)

Run by the orchestrator after Gate 2, at the user's request, against the user's locally running service (IntelliJ,
`dev` profile, Open Bee test environment). The Open Bee key was read from the IntelliJ run configuration into a
shell variable for direct probes and never printed or stored. Test documents created in Open Bee test (synthetic,
category 11376 "test spring boot" and 11373 "Share Transfer In"): 189526 (+1 version), 189530, 189532.

### Defects found live and fixed

| # | Defect | Fix | Files |
|---|---|---|---|
| L1 | Register/login 500: no `transactionManager` bean (scaffold lacked `DatasourceConfiguration`) | Added it (same as reference-data-service); test config enables the middleware H2 datasource | `config/DatasourceConfiguration.java` (new), `src/test/resources/application.yml`, `config/DatasourceConfigurationTest.java` (new) |
| L2 | Search always returned no documents: hits are under `items`, not `results` | `@JsonAlias("items")` | `openbee/OpenBeeSearchResponse.java` |
| L3 | Non-PDF downloads returned a PDF rendition: `thumb=false` is treated as true | `thumb` no longer sent | `openbee/OpenBeeClient.java` |
| L4 | Downloaded names had no extension; no `Content-Disposition` from Open Bee | name + `extension` from document info; search `fileName` likewise (user decision) | `openbee/OpenBeeClient.java`, `service/FileNameResolver.java`, `service/impl/DocumentServiceImpl.java` |
| L5 | Unknown document gave DMS109 (file endpoint answers 403) | Document info (404) decides DMS108/DMS109 | `service/impl/DocumentServiceImpl.java` |
| L6 | Large downloads: document info queued behind the open file stream (Open Bee serves one request per key at a time), timed out 3x15 s | Document info looked up before the file is opened | `service/impl/DocumentServiceImpl.java` |
| L7 | `overwrite=false` on an existing document gave 502 DMS201: Open Bee's `error` is a single object | Error body reads object or array; code `Document with the same name already exist` -> DMS106 | `openbee/OpenBeeErrorBody.java`, `openbee/OpenBeeErrorMapper.java` |
| L8 | Save into a category without a folder rule gave 502 DMS201 (`MUST_SPECIFY_FOLDER_OR_RULE`) | 400 DMS102 with a clear message; categories stay listed (user decision) | `openbee/OpenBeeErrorMapper.java` |
| L9 | Cold catalog load 83 s (47 sequential calls) | Lists loaded in parallel (`dms.cache.list-load-concurrency`=8) + background refresh-ahead (`dms.cache.refresh-ahead`=5m) (user decision). Cold load now 31-57 s, bounded by Open Bee serving one request per key at a time | `service/FilingCategoryCatalog.java`, `config/DmsCacheConfig.java`, `config/DmsProperties.java` |
| L10 | Multiple metadata returned as JSON text in search | New `values` array (`value` null for multiple metadata) (user decision) | `specs/apiSpecs.yaml`, `service/impl/DocumentServiceImpl.java` |
| L11 | Flaky `DmsRequestSummaryInterceptorTest` (starter logs via AsyncAppender) | Await the log line | `config/DmsRequestSummaryInterceptorTest.java` |

Tests added/updated for every row; README V1-V6 table, operational notes and deployment prerequisites updated.

### Final results

- `mvn -pl services/document-management-service -am clean test`: exit 0, 348 DMS tests, 0 failures/errors.
- JaCoCo line coverage of changed classes: 99-100% (OpenBeeClient 99.0%, DocumentServiceImpl 99.4%, FilingCategoryCatalog 99.3%, others 100%).
- Live (after restart with all fixes): categories, save (single and multiple list values, 9.4 MB), search (by text and by list value), download (byte-identical, correct names), DMS101-DMS109 negative paths all as specified.

### Open (not code)

- Open Bee test URL is plain HTTP; production must use HTTPS (README prerequisite 7).
- The test key belongs to `Administrator`; production needs one service account per consumer (README prerequisite 8).
- These changes were made after the 04_security audit; the delta has not been re-audited.

## 10. Addendum — user-requested changes after live verification (2026-11-02)

| # | Change | Files |
|---|---|---|
| C1 | LIST metadata with Open Bee `addNewValue: true` (e.g. 11888 RequestID, list 11887 with 972 values): the list is no longer fetched; the filing-categories response exposes `allowNewValue: true` with `listValues: null` (user decision); save/search accept any non-blank text or whole number for it, sent as entered. A list shared with a restricted metadata is still fetched. | `openbee/OpenBeeFilingCategory.java`, `service/CatalogMetadata.java`, `service/FilingCategoryCatalog.java`, `service/MetadataValidator.java`, `service/impl/DocumentServiceImpl.java`, `specs/apiSpecs.yaml` |
| C2 | Save request: plain multipart form fields `file`, `filingCategoryId`, `fileName`, `description`, `overwrite`, `metadata` (JSON text, no part Content-Type) instead of a JSON `request` part (user decision). Same constraints and DMS101 messages; missing `filingCategoryId` -> "filingCategoryId is required"; metadata decimals bind to BigDecimal. Non-UTF-8 risk checked: Arabic file name and metadata sent without a part Content-Type reach Open Bee intact (real Tomcat test). | `specs/apiSpecs.yaml`, `controllers/DmsController.java`, `exception/GlobalExceptionHandler.java`, `apiCollection.http`, `e2e/document-management-service.postman_collection.json`, `README.md` |
| C3 | Two more tests asserting on async log output made to wait for the line (`OpenBeeTimestampConverterTest`, plus waits in catalog/service tests and `DmsLoggingHygieneTest`, whose doesNotContain checks could otherwise pass before the logs are flushed). | tests only |

`mvn -pl services/document-management-service -am clean test`: exit 0, 355 DMS tests, 0 failures. JaCoCo: DmsController 100%, MetadataValidator 96.5%, FilingCategoryCatalog 99.3%, DocumentServiceImpl 99.4%, CatalogMetadata 100%.

Live (2026-11-02, after restart): categories response 91 KB (was 173 KB), 42 list calls (3 `addNewValue` lists skipped, 11887 never fetched), cold load 32 s; 11888 RequestID `allowNewValue: true`, `listValues: null`; Exchange still lists its 3 values. Save with plain form fields (existing values, doc 189665; new value `TEST-NEW-1`, doc 189669, added by Open Bee to list 11887 with the user's consent); search by a free-entry value finds it; negatives (missing/invalid filingCategoryId, metadata not an object, restricted list value) as specified.

Found live, not fixed (starter-wide): a request Spring rejects before reaching a controller (wrong Content-Type -> 415, wrong method -> 405) is turned into 500 by the starter's `LoggingExceptionHandler` catch-all, in every service.

## 11. Addendum — security re-audit fixes (2026-11-02)

RA-H1 (fixed by 04_security: number magnitude/scale limit in `MetadataValidator`), RA-M1 (catalog lock-wait and load deadline), RA-L1 (refresh schedule survives any Throwable), RA-L2 (settings validation), RA-L3 (control characters refused in free-entry values) — details in security_report.md "Re-audit follow-up". New test file: `src/test/java/com/alramz/config/DmsCacheConfigTest.java`. Final run: exit 0, 368 DMS tests, 0 failures.
