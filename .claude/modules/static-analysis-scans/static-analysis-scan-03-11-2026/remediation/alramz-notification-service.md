# alramz-notification-service: static-analysis remediation

Scope: `services/alramz-notification-service` only. FieldNamingConventions rows are ignored because config already resolves them.
Before: 78 findings (Checkstyle 5, PMD 63, SpotBugs 10). After: 40 (Checkstyle 4, PMD 33, SpotBugs 3).

## Fixed

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| config/SmsProperties.java (compact ctor) | Checkstyle NPathComplexity (1024) | `connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;` (x7 ternaries) | `connectTimeout = Objects.requireNonNullElse(connectTimeout, Duration.ofSeconds(5));` (`...ElseGet` for the nested records) | Same defaults with no branches. The nested-record defaults stay lazy. |
| config/SmsProperties.java `Defaults` | SpotBugs BX_UNBOXING_IMMEDIATELY_REBOXED | `expiryInSecs == null ? 300 : expiryInSecs` | `Objects.requireNonNullElse(expiryInSecs, 300)` | The mixed int/Integer ternary unboxed and then reboxed the value. |
| config/SmsProperties.java:84 | PMD AvoidLiteralsInIfCondition | `dispatch.batchSize() < 1` | `dispatch.batchSize() <= 0` | Same condition on an int. 0 is an allowed literal. |
| repository/OtpRepository.java | PMD FieldDeclarationsShouldBeAtStartOfClass (x4) | nested records declared before the fields | fields first, then the records | Declaration order only. |
| repository/OtpRepository.java | PMD AvoidDuplicateLiterals | `new MapSqlParameterSource("ids", ids)` (x4) | `new MapSqlParameterSource(IDS, ids)` | `private static final String IDS = "ids"` |
| repository/OtpRepository.java:92 | SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `keyHolder.getKey().longValue()` | `Objects.requireNonNull(keyHolder.getKey(), "otp_management insert returned no generated key").longValue()` | A missing key now fails with a message instead of a bare NPE. |
| repository/SmsTableRepository.java | PMD FieldDeclarationsShouldBeAtStartOfClass (x3) | `record SmsTableRow` before the fields | fields first | Declaration order only. |
| scheduler/job/SmsDispatchJob.java | SpotBugs CT_CONSTRUCTOR_THROW (x2) | `public class SmsDispatchJob` | `public final class SmsDispatchJob` | The fail-fast constructor check stays. A final class removes the finalizer-attack vector. No AOP advises the class (the starter aspects match on annotations or `com.alramz.client..`) and no test mocks it. |
| scheduler/job/SmsDispatchJob.java:119 | PMD AvoidLiteralsInIfCondition | `"SMS002".equals(e.getCode())` | `INVALID_MOBILE_CODE.equals(e.getCode())` | Named constant. |
| service/OtpService.java | SpotBugs CT_CONSTRUCTOR_THROW (x2) | `public class OtpService` | `public final class OtpService` | Same as above. The class has no `@Transactional` (it uses TransactionTemplate), so there is no proxy. |
| service/OtpService.java:313 | PMD AvoidInstanceofChecksInCatchClause | `catch (RuntimeException e) { ... e instanceof SmsException sms ? sms.getCode() : ...` | `catch (SmsException e) {...} catch (RuntimeException e) {...}` | Separate catch blocks. The log output is unchanged. |
| service/OtpService.java:330 | PMD AvoidInstanceofChecksInCatchClause | `e instanceof EmailServiceException gateway ? gateway.getCode() : ...` | `catch (EmailServiceException e) {...} catch (RuntimeException e) {...}` | Same. The existing EmailValidationException catch stays first. |
| service/impl/SmsServiceImpl.java:114 | SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `request.getExpiryInSecs() == null ? ... : request.getExpiryInSecs()` | `final Integer requested = request.getExpiryInSecs(); ... requested == null ? ... : requested` | Reads the getter once, so the unboxed value is the one that was null-checked. |
| service/impl/EmailServiceImpl.java:141,159,163 | PMD AvoidLiteralsInIfCondition (x3) | `status == 401` / `status == 429` | `status == HTTP_UNAUTHORIZED` / `HTTP_TOO_MANY_REQUESTS` | Named constants. |
| exception/GlobalExceptionHandler.java:135 | PMD AvoidLiteralsInIfCondition | `if (size < 3)` | `if (size < ATTACHMENT_CONTENT_PATH_DEPTH)` | Named constant (attachments / [n] / content). |
| service/impl/EtisalatSmsClient.java | PMD FieldDeclarationsShouldBeAtStartOfClass (x4) | `private record Token` before the fields | fields first | Declaration order only. |
| service/impl/EtisalatSmsClient.java | PMD AvoidFieldNameMatchingMethodName | `private Token token;` | `private Token cachedToken;` | Private field only. Nothing outside the class binds to it. |
| service/impl/EtisalatSmsClient.java:75 | PMD PreserveStackTrace | `catch (Unauthorized e) { try { ...token(true)... } catch (RestClientException retry) {...} }` | `catch (Unauthorized e) { log } catch (RestClientException e) {...}`, then `try { submit(token(true)...) } catch (RestClientException retry) {...}` | The retry moves out of the catch block with the same flow: a 401 refreshes once and anything else fails immediately. |
| service/TemplatedEmailService.java:61 | PMD AvoidFieldNameMatchingMethodName | `private static final Pattern LOGGABLE` | `private static final Pattern LOGGABLE_VALUE` | Private constant. The `loggable()` method (used by OtpService) is unchanged. |

Total fixed: 31 findings (Checkstyle 1, PMD 23, SpotBugs 7). Fixed 31 + suppressed 7 + deferred 40 = baseline 78.

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| config/SmsProperties.java: `SmsProperties` compact ctor | PMD.UnusedAssignment (x3) | False positive: Java assigns compact-constructor parameter values to the record fields implicitly, and PMD does not see that. |
| config/SmsProperties.java: `Defaults` compact ctor | PMD.UnusedAssignment (x2) | Same false positive. |
| config/SmsProperties.java: `Dispatch` compact ctor | PMD.UnusedAssignment (x1) | Same false positive. |
| config/EmailProperties.java: compact ctor | PMD.UnusedAssignment (x1) | Same false positive. |

Every suppression is constructor-scoped `@SuppressWarnings("PMD.UnusedAssignment")` with a one-line reason comment. Total: 4 annotations covering 7 findings.

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| service/TemplatedEmailService.java:207,245,248,249,251 | PMD PreserveStackTrace | 5 | Deliberate design: TemplatedEmailException has no cause constructor ("gateway exceptions are mapped by code, never chained") so gateway messages never reach logs. Fixing it needs a security decision. |
| service/impl/WaselneySmsClient.java:132 | PMD PreserveStackTrace | 1 | Deliberate design: SmsException has no cause constructor ("provider exceptions embed credential-bearing URLs and are never chained"). |
| service/OtpService.java:180,216; service/TemplatedEmailService.java:129 | PMD ExceptionAsFlowControl | 5 | Catch-record-rethrow pattern: the catch records `e.getCode()` for the `finally` audit log and rethrows the same exception. Restructuring it would mean an outcome holder or duplicated logging. Not simple. |
| config/RequestSizeLimitConfig.java:52 | SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION (Low) | 1 | Intentional: the interceptor throws the endpoint's own exception (EmailValidationException or TemplatedEmailException, which share no base type) for GlobalExceptionHandler to map. SuppressFBWarnings is not on the classpath. |
| service/SmsService.java:24 `Outcome` | SpotBugs EI_EXPOSE_REP / EI_EXPOSE_REP2 | 2 | `SmsSendResult` is a generated, mutable OpenAPI model with no copy constructor. A deep copy would add code for an internal value carrier. Accepted. |
| controllers/OtpController.java:39, SmsController.java:62 | PMD ShortMethodName | 2 | Method names come from the generated OpenAPI interface (public API). |
| service/impl/EtisalatSmsClient.java:57 | PMD UseObjectForClearerAPI | 1 | Public signature change. |
| GlobalExceptionHandler, OtpService, TemplatedEmailService, EmailServiceImpl, SmsServiceImpl | PMD GodClass | 5 | Structural refactor. |
| GlobalExceptionHandler, OtpRepository | PMD TooManyMethods | 2 | Structural refactor. |
| OtpService, EmailServiceImpl | PMD CouplingBetweenObjects | 2 | Structural refactor. |
| TemplatedEmailService, EmailServiceImpl | PMD ExcessiveImports | 2 | Structural refactor. |
| OtpService | Checkstyle ClassFanOutComplexity | 1 | Structural refactor. |
| GlobalExceptionHandler (x3), EmailServiceImpl (x2), EtisalatSmsClient (x1) | PMD CyclomaticComplexity | 6 | Code-to-status switch tables and validation chains. A split would be a refactor beyond one small extraction. |
| TemplatedEmailService:140, EmailServiceImpl:205 | PMD NPathComplexity | 2 | Validation chains. Same reason. |
| TemplatedEmailService:83,140, EmailServiceImpl:205 | Checkstyle NPathComplexity | 3 | Same. |

Total deferred: 40 findings (matches the remaining count).

## Verification

- Command (in snapshot `.../scratchpad/snap-notification`, offline): `mvn -B -o -pl services/alramz-notification-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true`
- Exit code: 0 (BUILD SUCCESS)
- Tests: 260 run, 0 failures, 0 errors, 0 skipped. The baseline before any change was also 260/0/0.
- Remaining findings (FieldNamingConventions excluded): Checkstyle 4, PMD 33, SpotBugs 3 (Medium 2, Low 1). Baseline: 5 / 63 / 10.
