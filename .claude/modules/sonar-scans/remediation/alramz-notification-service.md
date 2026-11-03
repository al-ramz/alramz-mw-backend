# alramz-notification-service: Sonar code-smell remediation

Verification: `mvn -B -pl services/alramz-notification-service test` gives Tests run: 260, Failures: 0, Errors: 0, Skipped: 0. BUILD SUCCESS. No tests were added or removed.

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| main/config/GraphConfig.java | S1452 (1) | `GraphServiceClient<?>` bean return type | Typed as `GraphServiceClient<okhttp3.Request>`, which is the builder's native request type. Bean name unchanged; `ObjectProvider<GraphServiceClient<?>>` injection still matches. | RequestSizeLimitConfigTest, EmailServiceImplTest, AppTests |
| main/config/SmsProperties.java | S3776 (1), S3398 (1) | Compact constructor complexity 20; `clean` used only by Etisalat | Extracted `validateLiveSend` / `validateDispatch` (same checks, same order, same messages). Moved `clean` into `Etisalat`. | SmsPropertiesTest |
| main/repository/OtpRepository.java | S1192 (1) | "serviceName" x4 | `SERVICE_NAME` constant | OtpServiceTest, AppTests |
| main/scheduler/job/SmsDispatchJob.java | S6880 (1), S1141 (1) | if/else chain on the enum; nested try | Arrow `switch` on RowOutcome. Inner send/try extracted into `send(...)`. The outer try/finally (MDC clear, markResult failure) is unchanged. | SmsDispatchJobTest |
| main/service/impl/EmailServiceImpl.java | S3776 (1) | `validate` complexity 18 | Extracted `validateAttachments` (count, per-item, total). Validation order unchanged. | EmailServiceImplTest |
| main/service/impl/SmsServiceImpl.java | S6353 (3), S3776 (1), S1192 (1) | `[0-9]` in regex; `sendWithOutcome` complexity 19; "SMS009" x3 | `\d` (Java's default `\d` is ASCII 0-9, so same matches). Extracted `validateBodyAndNumbers`, `msgCategory`, `expiry`. Added `PROVIDER_REJECTED` constant. | SmsServiceImplTest |
| main/service/OtpService.java | S3776 (1) | `parse` complexity 21 | Extracted `splitRecipients` and `requireChannelRecipients` | OtpServiceTest |
| main/service/TemplatedEmailService.java | S1192 (2), S3776 (1) | "MSWORD"/"MSEXCEL" x3; `checkAttachments` complexity 18 | `MSWORD`/`MSEXCEL` constants (still compile-time constants for the `case` labels). Extracted `checkAttachment` and `attachmentsMaxSize`. | TemplatedEmailServiceTest |
| test/config/ProfileSecurityConfigTest.java | S5838 (1) | `get(key)).isEqualTo` | `containsEntry` | itself |
| test/config/RequestSizeLimitConfigTest.java | S5778 (3), S8924 (1) | Multiple calls in assertThrows lambdas; FQN `Mockito.when` | Hoisted args to locals; static import `when` | itself |
| test/config/SmsPropertiesTest.java | S1068 (1), S5778 (8) | Unused ETISALAT field; record construction inside lambdas | Deleted the field; hoisted args to locals | itself |
| test/controllers/SmsControllerTest.java | S5778 (4) | `controller(...)` built inside lambdas | Hoisted controller to a local | itself |
| test/scheduler/job/SmsDispatchJobTest.java | S5853 (1) | Separate assertions on `logs.list` | Single chain | itself |
| test/service/impl/EmailServiceImplTest.java | S5778 (6), S5853 (1) | `request()` built inside lambdas; split assertions | Hoisted requests; chained `isNotEmpty().allSatisfy` | itself |
| test/service/impl/EtisalatSmsClientTest.java | S5853 (1) | Split assertions | Chained | itself |
| test/service/impl/SmsServiceImplTest.java | S5853 (1) | Split assertions | Chained | itself |
| test/service/impl/VeriPhoneClientTest.java | S5853 (1) | Split assertions | Chained | itself |
| test/service/OtpServiceTest.java | S5778 (6) | service/request/properties built inside lambdas | Hoisted to locals | itself |
| test/service/TemplatedEmailServiceTest.java | S5778 (4), S5853 (1) | `request()` inside lambdas; split assertions | Hoisted; chained | itself |

## Skipped
| rule | path:line | reason |
|---|---|---|
| javabugs:S2259 | main/service/impl/EtisalatSmsClient.java:96 | Already fixed by the lead (per brief) |
