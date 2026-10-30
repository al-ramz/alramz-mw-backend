# Implementation Report: notification-graph-gateway

## 1. Summary

This feature hardens `services/alramz-notification-service` into the shared Microsoft Graph email gateway. It is the only service touched. Both `POST /api/v1/email/send` and the in-process `EmailService#sendEmail(EmailRequest)` now go through one validator in `EmailServiceImpl`, so both paths return the same MGE-V codes:
- multi-recipient to/cc/bcc;
- the sender allow-list (fails closed);
- the cap on total decoded attachment size.

The endpoint is JWT-secured (`@JwtSecured(roles = "APP_NOTIFICATION")`) and is no longer in `permit-all-urls` in any of the 6 profiles. Responses use the `GenericResponse` + `EmailSendResult(correlationId)` envelope with real statuses (200/400/403/413/429/500/502). The startup token fetch and the `Access Token` log line are gone.

Gate 1 amendment (authoritative): `GraphConfig` now builds the Graph OkHttp client with a `RetryHandler` whose `RetryOptions` behave as follows:
- They retry **only HTTP 429**, with at most 2 retries and a 1 s base delay.
- They stop retrying when `Retry-After` is over 5 s or is not numeric. That second rule also stops RetryHandler's `Long.parseLong` from crashing on an HTTP-date value.

If 429 persists, the gateway returns HTTP 429, logs MGE-B-002 and passes through a numeric `Retry-After`.

## 2. Files Touched

| File | Status | Note |
|---|---|---|
| `services/alramz-notification-service/specs/openapi.yaml` | modified | sendEmail contract, GenericResponse/EmailSendResult, examples per status, 429 `Retry-After` header, `x-field-extra-annotation` aliases (confirmed emitted by 7.23.0) |
| `services/alramz-notification-service/pom.xml` | modified | `modelsToGenerate`, `useBeanValidation=false` |
| `services/alramz-notification-service/src/main/java/com/alramz/model/EmailRequest.java` | deleted | now generated |
| `services/alramz-notification-service/src/main/java/com/alramz/model/EmailAttachment.java` | deleted | now generated |
| `services/alramz-notification-service/src/main/java/com/alramz/controllers/EmailController.java` | modified | GenericResponse, `@JwtSecured`, no `@Loggable` |
| `services/alramz-notification-service/src/main/java/com/alramz/service/EmailService.java` | modified | `void sendEmail(EmailRequest)`, in-process contract Javadoc |
| `services/alramz-notification-service/src/main/java/com/alramz/service/impl/EmailServiceImpl.java` | modified | rewrite |
| `services/alramz-notification-service/src/main/java/com/alramz/exception/EmailServiceException.java` | modified | `(code, message[, cause, retryAfter])` |
| `services/alramz-notification-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java` | modified | rewrite, `@Order(0)` |
| `services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java` | modified | token fetch/log removed; 429-only `RetryOptions` (Gate 1 amendment) |
| `services/alramz-notification-service/src/main/java/com/alramz/config/EmailProperties.java` | modified | `maxTotalAttachmentSizeBytes`, `allowedSenders` |
| `services/alramz-notification-service/src/main/java/com/alramz/config/RequestSizeLimitConfig.java` | new | Content-Length interceptor |
| `services/alramz-notification-service/src/main/resources/application.yml`, `-dev`, `-docker`, `-test`, `-preprod`, `-prod` | modified | app.email keys; `/api/v1/email/send` removed from permit-all |
| `services/alramz-notification-service/apiCollection.http` | modified | `@authToken`, two secured send blocks, -ve block |
| `services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java` | new | |
| `services/alramz-notification-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java` | new | |
| `services/alramz-notification-service/src/test/java/com/alramz/controllers/EmailControllerTest.java` | new | |
| `services/alramz-notification-service/src/test/java/com/alramz/model/EmailRequestBindingTest.java` | new | |
| `services/alramz-notification-service/src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java` | new | also holds the `GraphRetryPolicy` `@Nested` tests for GraphConfig (caller asked for GraphConfig tests in planned test files) |

No file outside the plan was touched. `EmailValidationException` and `EmailConfigurationException` are unchanged, as the plan says.

## 3. Test Results

All commands were run from the repo root. Steps 1, 2, 4 and 5 failed for expected or fixable reasons, as noted.

| # | Command | Exit | Note |
|---|---|---|---|
| 1 | `mvn -pl services/alramz-notification-service -am clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 1 | Expected. Models were regenerated, and old callers still referenced the removed `EmailSendResponse`. |
| 2 | same | 1 | `HandlerInterceptor` is not a functional interface. Fixed with an anonymous class. |
| 3 | same | 0 | |
| 4 | `mvn -pl services/alramz-notification-service -am clean test` | 1 | `RequestSizeLimitConfigTest`: `MockHttpServletRequest.getContentLengthLong()` reads the content, not the header. Test fixed. |
| 5 | same | 1 | `EmailServiceImplTest#sendEmail_graphErrorWithoutServiceErrorIsB003` exposed a real bug: `GraphServiceException.getServiceError()` throws NPE when there is no parsed error body. Fixed in `EmailServiceImpl` (reads `getError()` first). |
| 6 | `mvn -pl services/alramz-notification-service -am clean test` | **0** | 60 tests, 0 failures, 0 errors (the 59 new tests plus `AppTest`); BUILD SUCCESS |

Greps after step 6:
- `Access Token|getToken(` in `src/main`: none.
- `/api/v1/email/send|max-attachment-size-bytes` in `src/main/resources`: none.

## 4. Test Coverage

Source: `services/alramz-notification-service/target/site/jacoco/jacoco.csv` from run 6.

Classes under `**/exception/**`, `**/model/**` and `**/api/**` have no JaCoCo numbers because the BOM's JaCoCo config excludes them (`alramz-common-bom/pom.xml`). Their gate below rests on per-method test mapping.

| Class | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| com.alramz.config.RequestSizeLimitConfig (+ anonymous HandlerInterceptor) | new | 100% (4/4 + 4/4) | 100% (2/2) | RequestSizeLimitConfigTest | PASS |
| com.alramz.service.impl.EmailServiceImpl (+ SendContext) | modified | 100% (173/173) | 100% (92/92) | EmailServiceImplTest (30 tests) | PASS |
| com.alramz.controllers.EmailController | modified | 100% (7/7) | 100% (2/2) | EmailControllerTest | PASS |
| com.alramz.config.GraphConfig | modified | 97.7% (42/43) | 80% (24/30) | RequestSizeLimitConfigTest$GraphRetryPolicy (`RETRY_ONLY_429`, `RETRY_OPTIONS`, `parseRetryAfterSeconds`, client build with no token fetch) | PASS |
| com.alramz.config.EmailProperties | modified | 100% (3/3) | 100% (2/2) | EmailServiceImplTest#sendEmail_emptyAllowListRejectsEverySender, all tests | PASS |
| com.alramz.exception.GlobalExceptionHandler | modified | n/a (BOM-excluded) | n/a | GlobalExceptionHandlerTest: every handler method and every switch arm (V-001..V-008, B-001..B-004, Retry-After present/absent, NotReadable V-005/V-006/null-cause/non-content path, AccessDenied, catch-all, UUID fallback) | PASS |
| com.alramz.exception.EmailServiceException | modified | n/a (BOM-excluded) | n/a | EmailServiceImplTest (getCode/getRetryAfter asserted), GlobalExceptionHandlerTest | PASS |
| com.alramz.service.EmailService | modified | n/a (interface) | — | EmailControllerTest, EmailServiceImplTest | PASS |
| com.alramz.model.EmailRequest / EmailAttachment / GenericResponse / EmailSendResult | generated | n/a (BOM-excluded, generated) | — | EmailRequestBindingTest (aliases, Base64 path) | PASS |

## 5. Acceptance Criteria Verification

The criteria are as amended by the approved plan and the Gate 1 amendment.

Contract and codegen:
- [x] The contract has the `EmailRequest` per 3.2, no `format: email`, and aliases. Proof: the generated `EmailAttachment` carries `@JsonAlias`, and EmailRequestBindingTest `bindsLegacyLowercaseAliases` and `bindsLegacyNameAlias` pass.
- [x] Responses 200/400(x6)/401/403(x2)/413/429 (with `Retry-After` header)/500/502 are declared with examples. Proof: `specs/openapi.yaml`; the generated `ApiApi` compiles.
- [x] `EmailSendResponse`/`ErrorResponse` are removed, and `GenericResponse`/`EmailSendResult` are added.
- [x] `modelsToGenerate` is updated.
- [x] The hand-copied models are deleted.
- [x] test-compile passes (run 3).

Security and config:
- [x] There is no `getToken(` or `Access Token` in `src/main` (grep). GraphRetryPolicy#buildsClientWithoutFetchingAToken builds the bean offline.
- [x] `/api/v1/email/send` appears in no `application*.yml` (grep).
- [x] `@JwtSecured(roles = "APP_NOTIFICATION")`. Proof: EmailControllerTest#sendEmail_requiresAppNotificationRole. Role failure gives 403 with null codes: GlobalExceptionHandlerTest#accessDeniedIsForbiddenWithoutErrorCode. The no-JWT 401 path is not tested (planned amendment; see Self-Critique).
- [x] All 6 files have `allowed-senders` and `max-total-attachment-size-bytes: 3000000`, and none has `max-attachment-size-bytes`. Per the approved plan, `max-subject-length`/`max-body-length`/`max-attachments` are kept.
- [x] `EmailConfigurationException` is kept per the approved plan and maps to 500. Proof: GlobalExceptionHandlerTest#unexpectedExceptionsAreInternalServerError and EmailServiceImplTest#sendEmail_noGraphClientIsConfigurationError.

Behaviour:
- [x] The success envelope is 200 with a correlationId equal to the MDC/X-Correlation-ID value: EmailControllerTest#sendEmail_returnsOkEnvelopeWithRequestCorrelationId.
- [x] A send produces 2 to, 1 cc and 1 bcc recipients, and a blank cc produces no cc recipients: `sendEmail_buildsMultiRecipientMessageAndSavesToSentItems`, `sendEmail_blankCcAndBccProduceNoRecipients`.
- [x] TEXT vs HTML: `sendEmail_plainBodyIsText`, `sendEmail_bodyStartingWithTagIsHtml`.
- [x] `saveToSentItems=true`: `sendEmail_buildsMultiRecipientMessageAndSavesToSentItems`.
- [x] Error cases E3-E8 and E10-E12 (status, code and text):

  | Case | Covering tests |
  |---|---|
  | E3 | GlobalExceptionHandlerTest#malformedJsonIsV006 |
  | E4 | EmailServiceImplTest#sendEmail_missingFieldsAreListedInOrder |
  | E5 | EmailServiceImplTest#sendEmail_invalidAddressesAreListedInOrder |
  | E6 | EmailServiceImplTest#sendEmail_subjectTooLongInProcessIsRejected, sendEmail_bodyTooLongIsRejected |
  | E7 | EmailServiceImplTest#sendEmail_tooManyAttachmentsIsRejected |
  | E8 | EmailServiceImplTest#sendEmail_badAttachmentItemsReportOneBasedPosition, GlobalExceptionHandlerTest#invalidBase64InAttachmentContentIsV005WithPosition |
  | E10 | EmailServiceImplTest#sendEmail_totalAttachmentSizeOverCapIsRejected |
  | E11 | RequestSizeLimitConfigTest#rejectsContentLengthOverDerivedLimit |
  | E12 | EmailServiceImplTest#sendEmail_senderNotAllowedIsRejected, sendEmail_emptyAllowListRejectsEverySender, sendEmail_senderMatchIsCaseInsensitive |

  The status mapping for these codes is proved in GlobalExceptionHandlerTest.
- [x] Aliases reach Graph `FileAttachment.name`/`contentType`: EmailRequestBindingTest plus `sendEmail_mapsAttachmentsToFileAttachments`.
- [x] A 401 followed by success makes 2 posts (`sendEmail_graph401ThenSuccessPostsTwice`). Two 401s give MGE-B-001 with a log line (`sendEmail_graph401TwiceIsBackendB001`), which the handler maps to 502.
- [x] A 429 with `Retry-After: 30` gives `retryAfter "30"` and the response header. Without the header, the value is null and no header is sent: `sendEmail_graph429WithRetryAfterIsB002WithHeaderValue`, `sendEmail_graph429WithoutRetryAfterIsB002WithoutValue`, GlobalExceptionHandlerTest#throttledPassesRetryAfterThroughWhenPresent. A malformed (HTTP-date) value still gives 429 with no header: `sendEmail_graph429WithHttpDateRetryAfterStillB002WithoutValue`.
- [x] **Gate 1 amendment, retry policy**:
  - retries only 429: GraphRetryPolicy#retriesOnly429 (503/504/500 false);
  - capped wait: retries429OnlyWhenRetryAfterIsWithinCap;
  - malformed Retry-After does not retry: doesNotRetryOnMalformedRetryAfter;
  - small retry count and delay: retryOptionsKeepRetriesAndDelaySmall;
  - header parsing: parseRetryAfterSeconds…, EmailServiceImplTest#retryAfter_readsSdkHeaderFormats.
- [x] Graph 400/503/404 give B-003 (`sendEmail_otherGraphErrorsAreB003`, `sendEmail_graphErrorWithoutServiceErrorIsB003`), which maps to 502. `ClientException`/`RuntimeException` give B-004 (`sendEmail_clientOrUnexpectedExceptionIsB004`), which maps to 500. Both have errorCode null.
- [x] In-process calls: a 256-character subject gives MGE-V-003, and a disallowed `from` gives MGE-V-007 (EmailServiceImplTest, called directly on the service).
- [x] Log hygiene: `logs_neverContainBodySubjectRecipientsOrContent` uses a logback `ListAppender` across the throttled, success and validation-failure paths.
- [x] apiCollection.http blocks carry `Authorization: Bearer {{authToken}}` and use `fileName`/`contentType`.
- [x] The full test suite passes (run 6, exit 0).

## 6. Deviations & Decisions

No blocking gap came up, so `AskUserQuestion` was not used. The decisions below were already made upstream or are minor:

1. **Gate 1 amendment (user decision, applied):**
   - Retry only 429, with at most 2 retries and a 1 s base delay.
   - Don't retry when Retry-After is over 5 s or not numeric. Pass a numeric Retry-After through.
   - All of this lives in `GraphConfig`, which replaces the plan's "keep default RetryHandler" line.
   - Worst-case sleep on the request thread is about 10 s (two waits of at most 5 s). Without Retry-After it is about 5.5 s, from RetryHandler's own backoff of 1 s + jitter, then 2.5 s + jitter.
2. **GraphConfig tests location:** they are a `@Nested` class in the planned `RequestSizeLimitConfigTest` (same `com.alramz.config` package, so package-private members are reachable), not a new test file. This follows the caller's instruction to add them to the planned test files.
3. **Non-numeric Retry-After is not passed through:** only digit-only values are echoed in the response header. An HTTP-date value gives 429 with no header. This is a small, safe choice that also avoids echoing an arbitrary upstream header value.
4. **`HttpStatus.PAYLOAD_TOO_LARGE` (deprecated in Spring 7) is used instead of `CONTENT_TOO_LARGE`.** This keeps the spec/contract reason phrase "Payload Too Large". The method carries `@SuppressWarnings("deprecation")`.
5. **`FileAttachment.oDataType = "#microsoft.graph.fileAttachment"` is now set.** Graph needs it to accept `contentBytes` on sendMail attachments, and the old code never set it. It falls inside the rewrite's scope.

## 7. Howtos Consulted

- `testing-patterns.md`: Mockito-first, direct construction, AssertJ, no `@WebMvcTest`/MockMvc. The handler test uses plain `new GlobalExceptionHandler()`.
- `exception-handling.md`:
  - `@Order(0)` single advice, not modelled on `OnboardingExceptionHandler`;
  - HTTP status lives in the handler, not in the exception (hence `EmailServiceException` carries a code, not a status);
  - a catch-all is kept.
- `api-collection-http-requests.md`:
  - pitfall 3: the undefined `{{authToken}}` is now defined;
  - pitfall 2: the real `gtalele@alramz.ae` recipient was replaced with synthetic `example.com` values;
  - a paired -ve block was added.
- `openapi-contract-first-controllers.md`: the controller implements the generated `ApiApi`, nothing under `target/generated-sources` was hand-edited, and the `/api/v1` full-path local precedent is kept.
- `jwt-security-and-public-endpoints.md`: the path was removed from **every** profile's `permit-all-urls`, and no ad hoc security config was added.
- `configuration-properties.md`: the record style was kept to keep the diff minimal, per the plan.
- `request-response-and-db-audit-logging.md`: no `@Loggable` on the controller, because it would log `EmailRequest.toString()`.
- `spring-boot-testing` skill: consulted. Plain JUnit + Mockito fits every class here, so no Spring slice was needed to reach the coverage gate.

## 8. Self-Critique

**Confidence: high** for the behaviour covered by tests. **Medium** for the live Graph interaction (retry, 401 refresh, attachment acceptance), which cannot be exercised offline.

Process deviations, stated honestly:
- **Test-first order was not followed strictly.** The production code was written before the test classes, so there was no formal red phase. The two real failures (run 4: test bug; run 5: a production NPE bug) were caught by the tests afterwards.
- Plan Step 0 used `unzip -p` on the Graph sources jars, which is read-only. The stage contract limits Bash to Maven, but the approved plan's Step 0 explicitly calls for this inspection.

Residual, non-blocking risks:
- The no-JWT → 401 path is not tested. No context or MockMvc test exists, which is a planned amendment. It relies on the starter's `anyRequest().authenticated()` and on the permit-all removal (grep-verified).
- Chunked requests bypass the Content-Length interceptor. This is marked with a `ponytail:` comment, and the decoded-total check still applies after binding.
- The single 401 retry probably reuses azure-identity's cached token, so it only helps with transient 401s.
- The SDK's own `DefaultLogger` (inside `CoreHttpProvider`) logs `"Graph service exception " + message` at error level on any Graph failure. In non-verbose mode that message includes the method, the URL (`/users/{from}/sendMail`) and request headers truncated to 50 characters with Authorization redacted; the body is replaced by `[...]`. This is outside our code.
- When a request arrives without a correlation id in MDC (in-process call), the service log line and the controller response each generate their own UUID. HTTP requests always have one, set by the starter filter.

**Flag for 04_security:**
1. A new authenticated endpoint contract. Confirm callers' tokens carry `ROLE_APP_NOTIFICATION`, and that removing permit-all is correct in every environment.
2. A new input-parsing surface: the Base64 attachments, the comma-split address lists and the regex. A Content-Length cap is added, but chunked bodies are bounded only after Jackson has buffered them, so there is a memory-pressure vector.
3. Log hygiene: `from`, recipient counts, the Graph error code and the invoker claim are logged. Body, subject, addresses and content are not, which the test asserts. `GraphServiceException` is never logged or attached as a cause in our code. The SDK's internal `DefaultLogger` output (see above) deserves a look.
4. The `Retry-After` response header is echoed from upstream, digits only, so there is no header-injection surface.
5. `ClientException` stack traces are logged at error level for MGE-B-004. Confirm no request content can appear in those causes.
6. `apiCollection.http` holds only a placeholder `@authToken` and no real token.
