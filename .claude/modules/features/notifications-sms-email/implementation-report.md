# Implementation Report: notifications-sms-email

## 1. Summary

This feature was built in `services/alramz-notification-service` only, in plan stages A to E, with the full suite run at the end of each stage.

- **A. Codegen fix.** `useTags` now sits directly under `configOptions`. The generated interfaces are `EmailApi`, `TemplatedEmailApi` and `SmsApi`; `ApiApi` is gone.
- **B. First persistence setup in this service.**
  - Liquibase, Postgres and Oracle drivers; H2 is now test scope.
  - `DatasourceConfiguration`.
  - 5 per-profile changelog masters, with byte-identical `001`/`002`/`003-notification-*.sql` in every profile folder (15 files).
  - The middleware datasource in every profile.
  - `sql-queries.xml`.
- **C. Templated email.** `POST /api/v1/email/template/send` runs template lookup, placeholder rendering (HTML-escaped for HTML bodies), attachment rules, the Asia/Dubai daily limit, gateway exception mapping and the `email_log` audit.
- **D. SMS gateway.** `POST /api/v1/sms/send` and `GET /api/v1/sms/report/{messageId}`:
  - Etisalat/Waselney router with VeriPhone routing; simulated by default.
  - Etisalat, Waselney and VeriPhone clients on a static `RestClient.builder()`.
  - XXE-hardened report parser.
- **E. `INSIGHT.SMS_TABLE` dispatch job.**
  - `@EnableScheduler(jobGroupName = "notification")` plus the `smsDispatchJobRunner` `schedule_job` row (FIXED_DELAY, 15000 ms).
  - Brok-gated `SmsTableRepository` with the ROWNUM-in-IN-subquery + outer `FOR UPDATE SKIP LOCKED` claim.
  - Dispatch is off by default.

Final result: 209 tests in the service. The only failure is the pre-existing baseline `ProfileSecurityConfigTest[application-dev.yml]` (section 6, D-0).

## 2. Files Touched

All paths are under `services/alramz-notification-service/`.

Modified:
- `pom.xml`: `useTags` fix; liquibase/postgresql/ojdbc11/orai18n added; h2 changed to test scope; `modelsToGenerate` now lists the 10 models.
- `specs/openapi.yaml`: 3 paths, 6 schemas, generalised `GenericResponse` descriptions.
- `apiCollection.http`: 6 new blocks appended before Scheduler Management. User edits (real recipient, Register/Login) left untouched; see D-7.
- `src/main/java/com/alramz/AlramzNotificationServiceApplication.java`: `@EnableScheduler`, `SmsProperties` binding.
- `src/main/java/com/alramz/controllers/EmailController.java`: `implements EmailApi`.
- `src/main/java/com/alramz/exception/GlobalExceptionHandler.java`: `TemplatedEmailException` and `SmsException` arms, plus a shared `codedOrLogged` helper. Existing arms unchanged.
- `src/main/java/com/alramz/config/RequestSizeLimitConfig.java`: template path gives EML007.
- `src/main/resources/application.yml`: `sql.file`, `app.sms.*` (including `dispatch`), and the `company.scheduler` pool/thread settings.
- `src/main/resources/application-{dev,docker,test,preprod,prod}.yml`: H2 `spring.datasource` replaced by `spring.liquibase.change-log`, `company.datasource.middleware` and `company.datasource.brok`, plus `cipher.password`. The dev `JWT_SECRET` line is unchanged.
- `src/test/java/com/alramz/AlramzNotificationServiceApplicationTests.java`: middleware-on-H2 properties, `cipher.password`, test changelog.
- `src/test/java/com/alramz/config/ProfileSecurityConfigTest.java`: the 3 new paths (and wildcards) must not be permit-all.
- `src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java`: EML007 interceptor tests; registration asserts both paths.
- `src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java`: EML/SMS arms, plus E4 on `TemplatedEmailRequest`.

New:
- `src/main/java/com/alramz/config/DatasourceConfiguration.java` (verbatim from wealth) and `src/main/java/com/alramz/config/SmsProperties.java`.
- `src/main/java/com/alramz/exception/TemplatedEmailException.java` and `src/main/java/com/alramz/exception/SmsException.java`.
- `src/main/java/com/alramz/model/EmailTemplate.java` and `src/main/java/com/alramz/model/EmailLogEntry.java`.
- `src/main/java/com/alramz/repository/EmailTemplateRepository.java` and `src/main/java/com/alramz/repository/SmsTableRepository.java`.
- `src/main/java/com/alramz/service/TemplatedEmailService.java` and `src/main/java/com/alramz/service/SmsService.java`.
- `src/main/java/com/alramz/service/impl/{SmsServiceImpl,EtisalatSmsClient,WaselneySmsClient,VeriPhoneClient}.java`.
- `src/main/java/com/alramz/controllers/TemplatedEmailController.java` and `src/main/java/com/alramz/controllers/SmsController.java`.
- `src/main/java/com/alramz/scheduler/job/SmsDispatchJob.java`.
- `src/main/resources/sql/sql-queries.xml`.
- `src/main/resources/db/changelog/{dev,docker,test,preprod,prod}/db.changelog-master.yaml` (5 files).
- `src/main/resources/db/changelog/{dev,docker,test,preprod,prod}/sql/00{1,2,3}-notification-*.sql` (15 files, byte-identical per name, verified by md5).
- Tests: `TemplatedEmailServiceTest`, `TemplatedEmailControllerTest`, `EmailTemplateRepositoryTest`, `SmsServiceImplTest`, `SmsControllerTest`, `EtisalatSmsClientTest`, `WaselneySmsClientTest`, `VeriPhoneClientTest`, `SmsPropertiesTest`, `SmsDispatchJobTest`, `SmsTableRepositoryTest`.

Every file is in `execution_plan.json`. No file outside the plan was touched.

## 3. Test Results

All commands ran from the repo root. `-pl services/alramz-notification-service -am` is omitted below.

| # | Command | Exit | Result |
|---|---|---|---|
| 1 | Baseline `clean test` | 1 | 66 run, 1 failure: baseline `ProfileSecurityConfigTest[2]` (dev) |
| 2 | Stage A `clean test-compile -Dmaven.compiler.useIncrementalCompilation=false` | 0 | `EmailApi.java` generated, no `ApiApi.java` |
| 3 | Stage A `test` | 1 | 66 run, baseline failure only |
| 4 | Stage B `test` | 1 | contextLoads error: no `PWProtector` bean (see D-1) |
| 5 | `test -Dtest=AlramzNotificationServiceApplicationTests` | 0 | 001/002 applied on H2 |
| 6 | Stage B `test` | 1 | 66 run, baseline failure only |
| 7 | Stage C red `clean test-compile` | 1 | expected: production classes missing |
| 8 | Stage C targeted `clean test -Dtest=...` | 1 | transient "class file not found" on the OneDrive tree; no code change between this and #9 |
| 9 | Stage C targeted `test -Dtest=TemplatedEmail*Test,EmailTemplateRepositoryTest,GlobalExceptionHandlerTest,RequestSizeLimitConfigTest` | 0 | 83 run |
| 10 | Stage C `test` | 1 | 127 run, baseline failure only |
| 11 | Stage D targeted `test -Dtest=Sms*Test,...ClientTest,GlobalExceptionHandlerTest` | 1 | 9 failures: my test fixtures built live `SmsProperties` without credentials, so the E45 check fired. Fixtures fixed. |
| 12 | `test -Dtest=SmsControllerTest,VeriPhoneClientTest` | 0 | 11 run |
| 13 | Stage D `test` | 1 | 191 run, baseline failure only |
| 14 | Stage E targeted `test -Dtest=SmsDispatchJobTest,SmsTableRepositoryTest,AlramzNotificationServiceApplicationTests` | 1 | H2 rejected `ON CONFLICT (cols) DO NOTHING` (see D-2) |
| 15 | `test -Dtest=AlramzNotificationServiceApplicationTests` | 0 | 001-003 applied; scheduler loaded `smsDispatch` from the DB |
| 16 | `clean test` | 1 | 207 run, baseline failure only |
| 17 | `test -Dtest=!ProfileSecurityConfigTest` (coverage) | 0 | 201 run, 0 failures |
| 18 | `test -Dtest=SmsControllerTest,GlobalExceptionHandlerTest` | 0 | 25 run (2 binding tests added) |
| 19 | **FINAL `clean test`** | **1** | **209 run, 1 failure = baseline `ProfileSecurityConfigTest[2]` only.** It fails at line 25 (`expected "${JWT_SECRET}" but was "${JWT_SECRET:defaultSecretKeyChangeMeInProduction1234567890}"`), before the permit-all assertions. |
| 20 | **Coverage `test -Dtest=!ProfileSecurityConfigTest`** | **0** | **203 run, 0 failures, 0 errors**. `jacoco.csv` generated. |

**Why the final exit is 1.** It comes only from the pre-existing dev-profile `JWT_SECRET` fallback. Per the caller's instruction, that line was not changed; it is a user decision outside this feature. Every other test passes, as #20 shows. The coverage run excludes that one class because a failing test phase stops Maven before `jacoco:report`.

## 4. Test Coverage

Source: `target/site/jacoco/jacoco.csv` from run #20. The BOM's JaCoCo config excludes `**/model/**`, `**/exception/**` and `**/api/**`, so those classes have no numbers.

| Class | Status | Line % | Branch % | Covered By | Gate |
|---|---|---|---|---|---|
| com.alramz.service.TemplatedEmailService | new | 98.8% (169/171) | 98.4% (120/122) | TemplatedEmailServiceTest | PASS |
| com.alramz.controllers.TemplatedEmailController | new | 100% (6/6) | — | TemplatedEmailControllerTest | PASS |
| com.alramz.repository.EmailTemplateRepository | new | 95.3% (41/43) | 83.3% (5/6) | EmailTemplateRepositoryTest (H2, real SQL + 002 schema) | PASS |
| com.alramz.service.impl.SmsServiceImpl | new | 97.0% (96/99) | 92.2% (59/64) | SmsServiceImplTest, client tests | PASS |
| com.alramz.service.impl.EtisalatSmsClient (+ `Token`) | new | 100% (71/71), Token 100% | 84.6% (22/26) | EtisalatSmsClientTest | PASS |
| com.alramz.service.impl.WaselneySmsClient | new | 100% (82/82) | 86.1% (31/36) | WaselneySmsClientTest | PASS |
| com.alramz.service.impl.WaselneySmsClient.new ErrorHandler() {...} | new (anonymous inner) | 50% (2/4) | — | WaselneySmsClientTest (fatalError path) | EXCEPTION: `warning`/`error` callbacks are not raised by a non-validating parser for any input we can feed it. The enclosing file is 97.7% (84/86). |
| com.alramz.service.impl.VeriPhoneClient | new | 100% (21/21) | 83.3% (5/6) | VeriPhoneClientTest | PASS |
| com.alramz.controllers.SmsController | new | 100% (17/17) | 100% (14/14) | SmsControllerTest | PASS |
| com.alramz.config.SmsProperties (+ Defaults/Etisalat/Waselney/Veriphone/Dispatch/Status) | new | 100% (43/43); nested records 100% | 90.7% (49/54) | SmsPropertiesTest | PASS |
| com.alramz.service.SmsService (+ `Outcome`) | new | 100% (1/1) | — | SmsServiceImplTest#sendDefaultMethodReturnsResult | PASS |
| com.alramz.repository.SmsTableRepository (+ `SmsTableRow`) | new | 92.9% (26/28) | 100% (6/6) | SmsTableRepositoryTest | PASS |
| com.alramz.scheduler.job.SmsDispatchJob (+ `RowOutcome`) | new | 98.7% (78/79) | 95.5% (21/22) | SmsDispatchJobTest | PASS |
| com.alramz.config.DatasourceConfiguration | new | 100% (3/3) | — | AlramzNotificationServiceApplicationTests | PASS |
| com.alramz.exception.TemplatedEmailException | new | n/a (BOM-excluded) | n/a | GlobalExceptionHandlerTest, TemplatedEmailServiceTest | EXCEPTION: excluded by the BOM's `**/exception/**`; constructor and getter only |
| com.alramz.exception.SmsException | new | n/a (BOM-excluded) | n/a | GlobalExceptionHandlerTest, SmsServiceImplTest | EXCEPTION: excluded by the BOM; constructor and getter only |
| com.alramz.model.EmailTemplate | new | n/a (BOM-excluded) | n/a | EmailTemplateRepositoryTest | EXCEPTION: excluded by the BOM's `**/model/**`; record, no logic |
| com.alramz.model.EmailLogEntry | new | n/a (BOM-excluded) | n/a | EmailTemplateRepositoryTest, TemplatedEmailServiceTest | EXCEPTION: excluded by the BOM; record, no logic |
| com.alramz.exception.GlobalExceptionHandler | modified | n/a (BOM-excluded) | n/a | GlobalExceptionHandlerTest (templatedEmail*, sms*, e4_*) | PASS |
| com.alramz.config.RequestSizeLimitConfig | modified | 100% (7/7) + interceptor 100% (4/4) | 100% (2/2) | RequestSizeLimitConfigTest (templatePath*, registersInterceptorOnSendAndTemplatePaths) | PASS |
| com.alramz.controllers.EmailController | modified | 100% (7/7) | 100% (2/2) | EmailControllerTest | PASS |
| com.alramz.AlramzNotificationServiceApplication | modified | 88.2% (15/17), uncovered lines are the pre-existing `main()` | 50% | AlramzNotificationServiceApplicationTests (scheduler + SmsProperties wiring) | PASS |
| pom.xml, openapi.yaml, 6 yml files, 5 masters, 15 changesets, sql-queries.xml, apiCollection.http | new/modified | n/a (resources) | n/a | contextLoads (yml/changesets/codegen), EmailTemplateRepositoryTest and SmsTableRepositoryTest (sql-queries.xml), ProfileSecurityConfigTest (yml) | PASS |

Every new class with coverage numbers is above 80% line coverage. The exceptions are the anonymous `ErrorHandler` (unreachable callbacks) and four BOM-excluded classes.

## 5. Acceptance Criteria Verification

Contract and codegen:
- [x] The 3 paths, operationIds and tags are declared. Proven by compile: `TemplatedEmailApi`/`SmsApi` are implemented by `TemplatedEmailController`/`SmsController`.
- [x] Statuses and examples per 3.1/3.2/3.3, with 401 as a description only. Done by inspection of `openapi.yaml`; contract text has no test. SMS011 was dropped (amendment 3), so the 500 example carries no code.
- [x] The 6 schemas exist; the aliases work. `GlobalExceptionHandlerTest#e4_invalidBase64InTemplateAttachmentIsV005` uses the `contenttype` alias. `SmsControllerTest#requestBindsLegacyAliasesIncludingSingleStringNumber` covers `mobileNumber`.
- [x] `useTags` is direct; only `EmailApi`/`TemplatedEmailApi`/`SmsApi` are generated (run #2); `EmailController implements EmailApi`.
- [x] `modelsToGenerate` lists the 10 models.
- [x] `test-compile` passes (runs #2 and #19).

Persistence:
- [x] Dependencies and scopes as listed.
- [x] 5 masters exist, and `find ... -name '00?-notification-*.sql'` returns 15 byte-identical files (md5-checked). Changeset ids equal the filename stems.
- [x] `002` columns and constraints, plus the index and `--rollback`. Proven by `EmailTemplateRepositoryTest` (the script runs on H2) and contextLoads.
- [x] `003` inserts the row with ON CONFLICT and has a `--rollback`. **Amended:** `ON CONFLICT DO NOTHING` has no conflict target (D-2). contextLoads log: "DB Sceduled job loaded: scheduleId=smsDispatch ... FIXED_DELAY ... delay=15000".
- [x] `grep SMS_TABLE` under `db/changelog` finds nothing.
- [x] `DatasourceConfiguration` sets `@Primary`. Proven by contextLoads.
- [x] 5 profiles: middleware enabled, own change-log, no H2 block.
- [x] preprod/prod use `password`/`passwordVector` + `cipher.password: ${CIPHER_MASTER_KEY}`, with no `plainPassword`. Confirmed by grep (0 hits).
- [x] No committed datasource password default. The only `cipher.password` default (dev/docker/test) is not a datasource password (D-1).
- [x] `brok.enabled` defaults to `false`.
- [x] Every brok injection is `@Qualifier("brok…")`, and `SmsTableRepository` carries the `@ConditionalOnProperty`.
- [x] `AlramzNotificationServiceApplicationTests#contextLoads` passes on H2 with `MODE=PostgreSQL` and the test changelog.

Security and config:
- [x] None of the 3 paths is permit-all, as `ProfileSecurityConfigTest` asserts. It passes for 5 files. For dev, the run stops at the baseline secret assertion first; dev's permit-all list was not changed.
- [x] `@JwtSecured(roles="APP_NOTIFICATION")` is on each method (`TemplatedEmailControllerTest`, `SmsControllerTest#bothOperationsRequireAppNotificationRole`). Role failure gives 403 with a null code (`GlobalExceptionHandlerTest#accessDeniedIsForbiddenWithoutErrorCode`).
- [x] **Amended (amendment 1):** `app.sms.*` is defined in `application.yml` only, not in all 6 files. Env placeholders carry the per-environment values. `live-send-enabled` and `dispatch.enabled` default to `false`; the three statuses have no default. contextLoads binds it.
- [x] `@EnableScheduler(jobGroupName="notification")` is present and `SmsProperties` is bound (contextLoads: "Scheduler is started for the group: notification").
- [x] E44/E45: `SmsPropertiesTest` (e45_*, e44_*, `bothFlagsOffChecksNothing...`) and `SmsDispatchJobTest#e44_enabledWithoutBrokFailsAtStartup`.
- [x] `RequestSizeLimitConfig` covers the template path with EML007/413 (`RequestSizeLimitConfigTest#templatePathRejectsWithEml007`, `GlobalExceptionHandlerTest#templatedEmailCallerErrorsCarryCodes`).

Templated email (`TemplatedEmailServiceTest` unless noted):
- [x] E5 to E19:
  - E5–E8: `e5_*`, `e6_*`, `fieldChecks_*`, `e7_*`, `e8_*`.
  - E9–E11: `e9_*`, `e10_*`, `e11_*`.
  - E12–E14: `e12_*`, `e13_*`, `e14_*`.
  - E15–E18: `e15_*`, `e16_*`, `e17_*`, `e18_*`.
  - E19: `send_normalisesCcAndBccAndUsesArabicContent`.
  - Status mapping: `GlobalExceptionHandlerTest#templatedEmail*`.
  - E12 Content-Length: `RequestSizeLimitConfigTest`.
- [x] One `sendEmail` call with from/recipients/subject/body (`send_rendersAndSendsTemplateAndWritesOneAuditRow`), plus one MIME assertion per table row (`send_mapsAttachmentTypeCodeToMime`, 11 cases).
- [x] One `email_log` row with the SHA-256, metadata or `{}`, invoker or `unknown`, and the Dubai clock time:
  - `send_rendersAndSends...`, `sha256HexIsLowercaseHexOfUtf8`, `send_recordsAttachmentMetadata`;
  - `send_usesUnknownInvoker*`;
  - no insert on failure: `assertFails` verifies `insertLog` is never called.
- [x] Bulk vs per-recipient count with Dubai day bounds (`e14_bulkLimitCountsWithoutRecipient`, `e15_belowLimitInTheDubaiDaySends`). The clock is set so the Dubai and UTC days differ.
- [x] H2 proves the `countSentToday`/`insertLog`/`findByName` SQL (`EmailTemplateRepositoryTest`, including day boundaries).

SMS:
- [x] E20 to E29:
  - E20–E24: `SmsServiceImplTest#e20_*`..`e24_*`.
  - E25: `e25_*`.
  - E26: `e26_*`.
  - E27–E29: `SmsControllerTest#e27_*`, `#e28_*`, `#e29_*`, plus `SmsServiceImplTest#e27_*`, `#e28_*`.
  - Statuses: `GlobalExceptionHandlerTest#sms*`.
- [x] Simulated mode makes zero HTTP calls (`e26_*`: `verifyNoInteractions` on all 3 clients). The report in simulated mode makes no call (`SmsControllerTest#e34_*`).
- [x] Routing matrix plus Waselney-first ordering (`liveWaselneySendsEveryNumber...`, `etisalatWithEmptyPrefixes...`, `prefixMatchRoutesByVeriphoneCountryAndWaselneyGoesFirst` with `InOrder`, `e32_*`).
- [x] Waselney encoding, URL encoding, 1701 handling, and FAILED otherwise (`WaselneySmsClientTest#encodesMessageAsUcs2Hex`, `#sendBuildsEncodedQueryAndReturnsMessageId`, `#nonSuccessCodeIsRejected`).
- [x] Etisalat:
  - UTC `.SSS'Z'` expiry, and 201 returns `jobId` (`loginsThenSendsAndReturnsJobId`);
  - one refresh on 401 (`e30_*`), two 401s (`e31_*`);
  - token reuse until 60 s before expiry (`tokenIsReusedUntilWithinSixtySecondsOfExpiry`).
- [x] getReport: DOCTYPE gives 502 (`e35_doctypeIsRejected`), two Reports give two maps (`reportReturnsOneMapPerReport`), and E33/E34/E36 are covered.
- [x] Log capture proves no body, full number, password, key or query string is logged, including when `ResourceAccessException` carries the URL:
  - `EtisalatSmsClientTest#transportFailureIsSms010AndNeverLogsSecrets`;
  - `WaselneySmsClientTest#transportFailureIsSms010AndUrlIsNeverLogged`;
  - `VeriPhoneClientTest#failuresAreEmptyAndNeverLogTheKeyOrUrl`;
  - `SmsServiceImplTest#logsMaskNumbersAndNeverContainTheBody`;
  - `SmsDispatchJobTest#summaryAndRowLogsNeverContainBodyOrFullNumber`.

Dispatch job:
- [x] E38: `SmsDispatchJobTest#e38_disabledRunNeverTouchesTheRepository`.
- [x] E40 to E42: status, MESSAGE_ID format, truncation, increment and guard (`e40_*`, `e41_*`, `e42_*`; guard SQL in `SmsTableRepositoryTest#updatesAreGuardedByStatus`).
- [x] E43: `e43_rowException...`, `e43_markResultFailure...`, `e43_claimFailure...`.
- [x] **Amended (amendment 4):** the claim SQL contains `FOR UPDATE SKIP LOCKED` and a ROWNUM bound in an IN-subquery, has no `FETCH FIRST`, and select + markInProgress run in one brok `TransactionTemplate` (`SmsTableRepositoryTest#claimSqlLocksABoundedOrderedCandidateSetWithoutFetchFirst`, `#claimSelectsAndMarksInProgressInOneTransaction`). The original `maxRows`/`fetchSize` clause is replaced, per the Gate-1 amendment.

Manual and final:
- [x] `apiCollection.http` has a positive and a negative block per endpoint, with `{{authToken}}` and synthetic `example.com` / `97150000001x` values.
- [x] `mvn ... test`: everything passes except the recorded baseline (runs #19 and #20).

## 6. Deviations & Decisions

No new blocking gap was raised, so `AskUserQuestion` was not used. Gate-1 amendments were applied as authoritative:

- **A-1 Config placement.** `app.sms.*` (including `dispatch`) and `company.scheduler.pool-size/thread-group-name/thread-name-prefix` are in `application.yml` only. Datasource blocks are per profile. The spec criterion "All 6 yml files define app.sms.*" is amended accordingly.
- **A-2 Fixed constants.** Content type `3.1`, sender `AL RAMZ`, priority `0`, and sent/failed `88`/`1` are code constants in `SmsProperties`. These stay configurable:
  - `defaults.msg-category`/`expiry-in-secs`;
  - the pending, in-progress and invalid-mobile statuses;
  - batch size, timeouts, limits, URLs and credentials.
- **A-3 SMS011 dropped.** The handler's `default` arm still maps any unknown SMS code to 500 with a null code.
- **A-4 Oracle claim.** The ROWNUM-in-IN-subquery + outer `FOR UPDATE SKIP LOCKED` pattern is used, and the spec criterion is amended. **A manual two-session check against BROKQA is required before `SMS_DISPATCH_ENABLED=true` is ever set.** No test here can prove Oracle lock semantics. Dispatch ships disabled, so this does not block merge.
- **A-5 Shared `jwt_user`.** Register/login now use the shared Postgres `jwt_user`/`jwt_refresh_token` tables. This is accepted as a known risk, tracked with C-2. No work was done for it.

Non-blocking deviations from the plan, logged here:

- **D-0 Baseline.** `ProfileSecurityConfigTest[application-dev.yml]` fails before and after this feature because of the user's uncommitted `${JWT_SECRET:defaultSecret...}` dev fallback. The line was preserved byte-for-byte, as instructed.
- **D-1 `cipher.password` in dev/docker/test.** The plan put it only in preprod/prod. The starter's `MiddlewareDataSourceAutoConfiguration` hard-requires a `PWProtector` bean even with `plainPassword`, so contextLoads failed without it (run #4). Every sibling service sets it in every profile. Added `cipher.password: ${CIPHER_MASTER_KEY:UseMostSecretKey}` (the sibling-precedent throwaway default) to dev/docker/test; it is unused by `plainPassword`. preprod/prod keep `${CIPHER_MASTER_KEY}` with no default.
- **D-2 `ON CONFLICT DO NOTHING` without a target in `003`.** H2's PostgreSQL mode rejects `ON CONFLICT (cols)`. The plan's precedent, reference-data `004`, actually has no ON CONFLICT clause. Postgres accepts the target-less form. The only other unique constraint is the PK, so behaviour is the same. The file had never been applied anywhere.
- **D-3 Env var names.** preprod/prod use `COMPANY_DATASOURCE_{MIDDLEWARE,BROK}_PASSWORD_VECTOR`, not the plan's `_PASSWORD_IV`. This matches `wealth-management-service`, which already points at the same shared middleware DB, so ops can reuse the existing secret name. Brok credential placeholders have an empty default (`${...:}`), because brok is disabled by default and an unresolved literal would otherwise be bound. That is not a committed password.
- **D-4 `SmsProperties` written complete in Stage D**, including `Dispatch`/`Status`, to avoid editing the record twice. Stage E only added the yml.
- **D-5 No cause constructor on `TemplatedEmailException`.** The plan listed `(code, message, cause)`, but no path chains a cause (spec 3.1 step 9 says not to). An unused constructor was not added.
- **D-6 Small additions within the planned files:**
  - `sms.claim.markInProgress` also guards `AND STATUS = :pendingStatus`, and the claim has an outer `ORDER BY`.
  - Attachment codes must also be one of the 9 known codes (an "EXE" listed in a template is still EML005).
  - The client IP is truncated to 45 characters (the column width).
  - `mobileNumber` also accepts a single string (`ACCEPT_SINGLE_VALUE_AS_ARRAY`), so the legacy alias is actually usable.
  - The dispatch job reuses the router's SMS002 for invalid numbers instead of duplicating the regex.
- **D-7 `apiCollection.http` Register comment not changed.** The plan said to correct "in-memory H2", but the caller instructed that user edits (including the Register/Login blocks) be preserved and new requests only appended. The comment is now stale, because users live in the shared Postgres `jwt_user`. Flagged for the user to fix with A-5/C-2.
- **D-8 `language` is a plain string in the contract**, with `pattern: '^(EN|AR)$'` for documentation. A generated enum would turn E5's `language: "en"` into a binding error (MGE-V-006) instead of the required EML001.

## 7. Howtos Consulted

- `testing-patterns.md`: Mockito-first, direct construction, AssertJ, no `@WebMvcTest`/`MockMvc`/`@DataJpaTest`. Its advice to put the test changelog under `src/test/resources` was not followed, on purpose (plan, reference-data precedent): `test` is a deployed profile, and a test `application.yml` would shadow the one `ProfileSecurityConfigTest` reads.
- `liquibase-migrations.md`: no shared root folder, so files are copied into all 5 profile folders; `--rollback` added; filename equals changeset id.
- `scheduler-jobs.md`: `Schedulable` bean plus a `schedule_job` row, not `@Scheduled`; FIXED_DELAY for non-overlap; the job body never throws.
- `multi-datasource-and-jpa.md`: `@Primary` re-export; every brok/middleware injection qualified; encrypted pair in preprod/prod. Its pitfalls missed that `PWProtector` is mandatory (D-1).
- `exception-handling.md`: one `@Order(0)` advice extended (the `OnboardingExceptionHandler` pattern was not copied); typed exceptions carry no HttpStatus.
- `openapi-contract-first-controllers.md`: controllers implement the generated interfaces; nothing under `target/generated-sources` was edited.
- `api-collection-http-requests.md`: synthetic values only; blocks placed before Scheduler Management; `@authToken` already defined.
- `configuration-properties.md`: a record was kept, following the local `EmailProperties` precedent (the plan's recorded divergence).
- `jwt-security-and-public-endpoints.md`: no permit-all change; `JwtContext#getCurrentApplication` used for the invoker (feature 1 precedent).
- `request-response-and-db-audit-logging.md`: no `@Loggable` on the new controllers; the starter's logging interceptor is not wired into provider clients.

## 8. Self-Critique

**Confidence: medium-high.** All code paths are unit-tested, and context wiring is proven on H2. Three things stay unproven offline:
- Oracle claim semantics (A-4);
- the three provider wire protocols, written from prose: Etisalat `statusCode`/`jobId`, the `expiryDt` format (unknown formats fall back to 401-only refresh), and the Waselney XML shape;
- the first Liquibase run against the shared DB.

**Residual risks (non-blocking):**
- First deploy against the shared middleware DB: check that `email_configurations`/`email_log` don't already exist, and watch for `DATABASECHANGELOGLOCK` contention.
- The docker/test profiles now need `COMPANY_DATASOURCE_MIDDLEWARE_*` env vars to boot; there is no zero-config H2 any more.
- The daily-limit race (check-then-send-then-insert) is accepted, as in legacy.
- Rows stuck in-progress after a pod crash are not reaped (decision Q9).

**Flag for 04_security:**
1. **Provider credentials in query strings** (Waselney send/report, VeriPhone). Mitigations:
   - static `RestClient.builder()`, so there is no Boot observation (no `http.client` span with the URL) and no logging interceptor;
   - the exception message, URI and cause are never logged or chained (log-capture tests).
   Please confirm in a running pod that no OTLP span carries these URLs.
2. **XML parsing of the provider report:** doctype disallowed, external entities off, `FEATURE_SECURE_PROCESSING` on, silent error handler. `e35_doctypeIsRejected` covers it.
3. **Placeholder HTML-escaping** applies only when the stored body starts with `<` (spec decision 12). Plain-text templates pass values through, and a plain-text body is sent as text by the gateway.
4. **New logged fields:**
   - `templateName` and `language` (caller-supplied) appear in the templated-email outcome line;
   - provider `statusCode`/result code values appear in warn logs and in the SMS009 log text.
   None are secrets, but they are unescaped caller- or provider-controlled strings (log injection).
5. **Two new authenticated endpoints plus one report endpoint.** `messageId` is pattern-checked before it is built into the provider URL, and every query value is a strictly encoded URI variable.
6. **`cipher.password` default `UseMostSecretKey`** in dev/docker/test (D-1) follows sibling precedent, but it is a committed key default.
7. **Shared `jwt_user`** (A-5): consumers registered via this service are visible to every service on the DB.
