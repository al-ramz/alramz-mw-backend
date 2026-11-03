# SonarQube snapshot — alramzit_alramz-mw-backend

Captured: 2026-11-02T15:16:41+0530  
Quality gate: **NONE**

| Metric | Value |
|---|---|
| blocker_violations | 5 |
| bugs | 11 |
| code_smells | 481 |
| coverage | 77.2 |
| critical_violations | 56 |
| duplicated_lines_density | 4.0 |
| info_violations | 10 |
| line_coverage | 77.3 |
| major_violations | 220 |
| minor_violations | 213 |
| ncloc | 17963 |
| reliability_rating | C |
| security_hotspots | 0 |
| security_hotspots_reviewed | 100.0 |
| security_rating | D |
| security_review_rating | A |
| software_quality_maintainability_issues | 482 |
| software_quality_reliability_issues | 18 |
| software_quality_security_issues | 12 |
| sqale_index | 3049 |
| sqale_rating | A |
| violations | 504 |
| vulnerabilities | 12 |

## Quality gate conditions

| Metric | Status | Actual | Threshold |
|---|---|---|---|

## Open issues: 504

By type: {"CODE_SMELL": 481, "VULNERABILITY": 12, "BUG": 11}

By legacy severity: {"MAJOR": 220, "MINOR": 213, "CRITICAL": 56, "INFO": 10, "BLOCKER": 5}

By impact severity: {"MEDIUM": 217, "LOW": 212, "HIGH": 56, "INFO": 10, "BLOCKER": 7, "LOW,MEDIUM": 2}

### By rule

| Rule | Count |
|---|---|
| java:S1128 | 74 |
| java:S5778 | 71 |
| java:S1192 | 26 |
| java:S3457 | 24 |
| java:S5838 | 22 |
| java:S8924 | 21 |
| java:S3776 | 17 |
| java:S5853 | 15 |
| java:S1172 | 12 |
| java:S1068 | 12 |
| java:S112 | 12 |
| java:S119 | 11 |
| java:S1186 | 10 |
| java:S5738 | 10 |
| javasecurity:S5145 | 10 |
| java:S1130 | 9 |
| java:S2065 | 9 |
| java:S1481 | 8 |
| java:S1854 | 7 |
| java:S6068 | 6 |
| java:S6880 | 6 |
| java:S1066 | 6 |
| java:S2925 | 5 |
| java:S6878 | 5 |
| java:S2387 | 5 |
| java:S8688 | 5 |
| java:S1141 | 4 |
| java:S2143 | 4 |
| java:S2139 | 4 |
| java:S2184 | 4 |
| java:S106 | 4 |
| java:S5869 | 4 |
| java:S6353 | 3 |
| java:S6213 | 3 |
| java:S108 | 3 |
| java:S5958 | 3 |
| java:S2147 | 3 |
| java:S9016 | 2 |
| javabugs:S2259 | 2 |
| java:S6485 | 2 |
| java:S107 | 2 |
| java:S7158 | 2 |
| java:S135 | 2 |
| java:S1181 | 2 |
| java:S1155 | 2 |
| java:S3599 | 2 |
| java:S1075 | 2 |
| java:S1157 | 2 |
| java:S1220 | 1 |
| java:S3398 | 1 |
| java:S5961 | 1 |
| java:S6204 | 1 |
| java:S4502 | 1 |
| java:S1124 | 1 |
| java:S6201 | 1 |
| java:S1117 | 1 |
| java:S3078 | 1 |
| java:S2142 | 1 |
| java:S5998 | 1 |
| java:S5843 | 1 |
| java:S1126 | 1 |
| java:S1214 | 1 |
| java:S6813 | 1 |
| java:S9438 | 1 |
| java:S125 | 1 |
| java:S1135 | 1 |
| java:S3358 | 1 |
| java:S1118 | 1 |
| java:S1452 | 1 |
| java:S4144 | 1 |
| java:S1144 | 1 |
| java:S2077 | 1 |
| java:S1168 | 1 |

### By file

| File | Count |
|---|---|
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java | 33 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java | 17 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java | 16 |
| services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java | 16 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java | 15 |
| services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java | 12 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java | 12 |
| services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java | 11 |
| services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java | 10 |
| services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java | 9 |
| services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java | 8 |
| services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java | 7 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java | 7 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java | 6 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java | 6 |
| services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java | 6 |
| services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java | 6 |
| services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java | 6 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java | 6 |
| services/alramz-api-starter/src/test/java/com/alramz/jwt/aspect/JwtSecuredAspectTest.java | 5 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/logback/SeqAppenderTest.java | 5 |
| services/alramz-notification-service/src/main/java/com/alramz/service/impl/SmsServiceImpl.java | 5 |
| services/alramz-notification-service/src/test/java/com/alramz/service/TemplatedEmailServiceTest.java | 5 |
| services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogFilter.java | 5 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingAutoConfiguration.java | 5 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/WebClientLoggingFilter.java | 5 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/ISchedulerService.java | 5 |
| services/alramz-api-starter/src/test/java/com/alramz/audit/WebClientAuditFilterTest.java | 5 |
| services/onboarding-service/src/main/java/com/alramz/controllers/DataValidationController.java | 5 |
| services/utility-service/src/test/java/com/alramz/service/impl/CommunityPostServiceImplTest.java | 5 |
| services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java | 4 |
| services/alramz-api-starter/src/test/java/com/alramz/audit/SensitiveDataMaskerTest.java | 4 |
| services/alramz-notification-service/src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java | 4 |
| services/alramz-notification-service/src/test/java/com/alramz/controllers/SmsControllerTest.java | 4 |
| services/onboarding-service/src/test/java/com/alramz/dfm/DfmMemberApiClientTest.java | 4 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/DfmInvestorServiceImplTest.java | 4 |
| services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditAspect.java | 4 |
| services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java | 4 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/filter/ResponseLoggingFilter.java | 4 |
| services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java | 4 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/OnboardingServiceImplTest.java | 4 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/ValidationServiceImplTest.java | 4 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/LoggingHelperTest.java | 3 |
| services/alramz-notification-service/src/main/java/com/alramz/service/TemplatedEmailService.java | 3 |
| services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java | 3 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java | 3 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingProperties.java | 3 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/filter/CorrelationIdFilter.java | 3 |
| services/alramz-api-starter/src/test/java/com/alramz/datasource/config/DatasourcePropertiesTest.java | 3 |
| services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryTest.java | 3 |
| services/alramz-api-starter/src/test/java/com/alramz/jwt/controller/AuthControllerTest.java | 3 |
| services/onboarding-service/src/main/java/com/alramz/client/ETradeClient.java | 3 |
| services/onboarding-service/src/main/java/com/alramz/service/impl/ValidationServiceImpl.java | 3 |
| services/onboarding-service/src/test/java/com/alramz/controllers/ApplicationControllerTest.java | 3 |
| services/onboarding-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java | 3 |
| services/onboarding-service/src/test/java/com/alramz/scheduler/job/DataValidationScheduledJobTest.java | 3 |
| services/onboarding-service/src/test/java/com/alramz/service/ValidationResponseMapperRegistryTest.java | 3 |
| services/reference-data-service/src/main/java/com/alramz/service/SchedulerLockService.java | 3 |
| services/utility-service/src/main/java/com/alramz/service/impl/CommunityPostServiceImpl.java | 3 |
| services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java | 2 |
| services/alramz-notification-service/src/main/java/com/alramz/config/SmsProperties.java | 2 |
| services/alramz-notification-service/src/main/java/com/alramz/scheduler/job/SmsDispatchJob.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/service/impl/DfmInvestorServiceImpl.java | 2 |
| services/wealth-management-service/src/main/java/com/alramz/exception/ErrorCode.java | 2 |
| services/wealth-management-service/src/main/java/com/alramz/repository/MmfRepositoryImpl.java | 2 |
| services/wealth-management-service/src/main/java/com/alramz/service/impl/MmfServiceImpl.java | 2 |
| services/reporting-management-service/src/main/java/com/alramz/controllers/ApplicationController.java | 2 |
| services/wealth-management-service/src/main/java/com/alramz/controllers/ApplicationController.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/datasource/config/AbstractDataSourceAutoConfiguration.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtProperties.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/filter/JwtAuthFilter.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/repository/UserRepository.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/service/AuthService.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/filter/RequestLoggingFilter.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/logback/AlramzJsonEncoder.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/otel/OpenTelemetryTraceContextExtractor.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/util/LoggingHelper.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/util/MethodExecutionLoggingHelper.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerAutoConfiguration.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/utils/DataValidationUtil.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/utils/SqlQueriesManager.java | 2 |
| services/alramz-api-starter/src/test/java/com/alramz/datasource/MiddlewareDataSourceAutoConfigurationTest.java | 2 |
| services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryIT.java | 2 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/LogMaskingUtilTest.java | 2 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/interceptor/WebClientLoggingFilterTest.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ETradeTokenProvider.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalETradeServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalIbanServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/controllers/ApplicationController.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/service/impl/OnboardingServiceImpl.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/service/impl/PhoneValidationServiceImpl.java | 2 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/ResponseMappersTest.java | 2 |
| services/reference-data-service/src/main/java/com/alramz/controllers/ApplicationController.java | 2 |
| services/reporting-management-service/src/test/java/com/alramz/repository/RelationshipManagerRepositoryImplTest.java | 2 |
| services/reporting-management-service/src/test/java/com/alramz/service/impl/RelationshipManagerServiceImplTest.java | 2 |
| services/utility-service/src/test/java/com/alramz/controllers/CommunityPostControllerTest.java | 2 |
| services/reference-data-service/src/main/java/com/alramz/config/JpaConfig.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/client/AbstractRestClientTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/filter/RequestLoggingFilterTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/filter/ResponseLoggingFilterTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/scheduler/service/impl/JobScheduleManagerTest.java | 1 |
| services/onboarding-service/src/test/java/com/alramz/repository/DfmOnboardingRepositoryImplTest.java | 1 |
| services/reference-data-service/src/test/java/com/alramz/config/CacheWarmupRunnerTest.java | 1 |
| services/alramz-notification-service/src/main/java/com/alramz/repository/OtpRepository.java | 1 |
| services/alramz-notification-service/src/main/java/com/alramz/service/OtpService.java | 1 |
| services/alramz-notification-service/src/main/java/com/alramz/service/impl/EmailServiceImpl.java | 1 |
| services/alramz-notification-service/src/main/java/com/alramz/service/impl/EtisalatSmsClient.java | 1 |
| services/alramz-notification-service/src/test/java/com/alramz/config/ProfileSecurityConfigTest.java | 1 |
| services/alramz-notification-service/src/test/java/com/alramz/scheduler/job/SmsDispatchJobTest.java | 1 |
| services/alramz-notification-service/src/test/java/com/alramz/service/impl/EtisalatSmsClientTest.java | 1 |
| services/alramz-notification-service/src/test/java/com/alramz/service/impl/SmsServiceImplTest.java | 1 |
| services/alramz-notification-service/src/test/java/com/alramz/service/impl/VeriPhoneClientTest.java | 1 |
| services/onboarding-service/src/main/java/com/alramz/dfm/DfmMemberApiClient.java | 1 |
| services/onboarding-service/src/main/java/com/alramz/dfm/DfmOnboardingPayload.java | 1 |
| services/onboarding-service/src/test/java/com/alramz/config/DfmMaskingConfigTest.java | 1 |
| services/reporting-management-service/src/main/java/com/alramz/repository/RelationshipManagerRepositoryImpl.java | 1 |
| services/wealth-management-service/src/test/java/com/alramz/controllers/MmfApiDispatchTest.java | 1 |
| services/wealth-management-service/src/test/java/com/alramz/exception/ErrorCodeTest.java | 1 |
| services/wealth-management-service/src/test/java/com/alramz/repository/MmfRepositoryImplTest.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogService.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/audit/WebClientAuditFilter.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/datasource/config/IntegrationDataSourceAutoConfiguration.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/datasource/config/MiddlewareDataSourceAutoConfiguration.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/exceptions/ApiCallFailedException.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/annotation/JwtSecured.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/model/TokenPair.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/model/User.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/repository/RefreshTokenRepositoryOps.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/service/TokenProvider.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/aspect/MethodExecutionLoggingAspect.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/config/SeqLoggingConfig.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/constants/LoggingConstants.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/RestTemplateLoggingInterceptor.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerConfigCondition.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerConfiguration.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/constants/SchedulerConstants.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/model/ScheduleInfoBean.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/tracing/config/TracingAutoConfiguration.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/utils/PWProtector.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditLogFilterTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/client/AuditProbeClient.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/controllers/AuditProbeController.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/datasource/IntegrationDataSourceAutoConfigurationTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/AlramzJsonEncoderTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImpl.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/IBANValidationServiceImpl.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/PhoneValidationServiceImpl.java | 1 |
| services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java | 1 |
| services/onboarding-service/src/main/java/com/alramz/exception/OnboardingExceptionHandler.java | 1 |
| services/onboarding-service/src/main/java/com/alramz/service/ValidationRequestMapper.java | 1 |
| services/onboarding-service/src/main/java/com/alramz/service/impl/DuplicateCheckHelper.java | 1 |
| services/onboarding-service/src/test/java/com/alramz/AopLoggingIntegrationTest.java | 1 |
| services/onboarding-service/src/test/java/com/alramz/service/impl/NinTradingNumberValidationServiceTest.java | 1 |
| services/reference-data-service/src/main/java/com/alramz/config/CacheWarmupRunner.java | 1 |
| services/reference-data-service/src/main/java/com/alramz/config/CompanyRedisProperties.java | 1 |
| services/reference-data-service/src/main/java/com/alramz/scheduler/job/CacheReloadScheduledJob.java | 1 |
| services/reference-data-service/src/main/java/com/alramz/service/CacheAuditService.java | 1 |
| services/utility-service/src/main/java/com/alramz/finoux/FinouxClient.java | 1 |
| services/utility-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java | 1 |

## Security hotspots: 0

By status: {}

| Rule | File:Line | Probability | Status |
|---|---|---|---|
