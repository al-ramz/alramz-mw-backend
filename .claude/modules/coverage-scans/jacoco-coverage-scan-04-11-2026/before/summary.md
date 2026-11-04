| Service | Line | Branch | Instruction | Lines covered/total | 80% line gate |
|---|---|---|---|---|---|
| alramz-api-starter | 80.9% | 66.6% | 81.3% | 2471/3056 | PASS |
| alramz-notification-service | 98.4% | 94.1% | 98.4% | 1275/1296 | PASS |
| onboarding-service | 96.8% | 84.3% | 96.9% | 1052/1087 | PASS |
| reference-data-service | 95.9% | 82.1% | 94.3% | 518/540 | PASS |
| reporting-management-service | 93.0% | 86.8% | 92.9% | 133/143 | PASS |
| risk-management-service | 95.6% | 92.5% | 95.8% | 217/227 | PASS |
| utility-service | 98.7% | 75.0% | 98.1% | 148/150 | PASS |
| wealth-management-service | 93.9% | 95.7% | 95.1% | 261/278 | PASS |

### alramz-api-starter: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 34 | com.alramz.jwt.repository.UserRepository | 0.0% |
| 32 | com.alramz.logging.config.LoggingAutoConfiguration | 23.8% |
| 31 | com.alramz.tracing.listener.TracingQueryExecutionListener | 18.4% |
| 29 | com.alramz.utils.SqlQueriesManager | 3.3% |
| 29 | com.alramz.logging.logback.AlramzJsonEncoder | 60.8% |
| 29 | com.alramz.jwt.repository.RefreshTokenRepository | 0.0% |
| 28 | com.alramz.audit.ApiAuditLogFilter | 62.2% |
| 25 | com.alramz.jwt.config.JwtAutoConfiguration | 0.0% |
| 23 | com.alramz.scheduler.config.SchedulerConfigCondition | 0.0% |
| 22 | com.alramz.logging.advice.CorrelationIdResponseBodyAdvice | 0.0% |
| 21 | com.alramz.logging.logback.SeqAppender | 91.1% |
| 19 | com.alramz.logging.util.MDCUtil | 65.5% |
| 19 | com.alramz.globalconfigurationsettings.repository.JdbcGlobalConfigurationSettingsRepository | 78.2% |
| 19 | com.alramz.datasource.config.AbstractDataSourceAutoConfiguration | 83.3% |
| 18 | com.alramz.jwt.controller.AuthController | 35.7% |

### alramz-notification-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 3 | com.alramz.service.impl.SmsServiceImpl | 97.3% |
| 2 | com.alramz.service.impl.WaselneySmsClient.new ErrorHandler() {...} | 50.0% |
| 2 | com.alramz.service.TemplatedEmailService | 98.9% |
| 2 | com.alramz.repository.SmsTableRepository | 92.9% |
| 2 | com.alramz.repository.OtpRepository | 96.5% |
| 2 | com.alramz.repository.EmailTemplateRepository | 95.3% |
| 2 | com.alramz.AlramzNotificationServiceApplication | 88.2% |
| 1 | com.alramz.scheduler.job.SmsDispatchJob | 98.7% |

### onboarding-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 7 | com.alramz.service.impl.ValidationServiceImpl | 87.9% |
| 4 | com.alramz.service.impl.DuplicateCheckHelper | 90.5% |
| 4 | com.alramz.scheduler.job.DataValidationScheduledJob | 76.5% |
| 3 | com.alramz.service.impl.OnboardingServiceImpl | 95.6% |
| 3 | com.alramz.config.LiquibaseConfiguration | 88.5% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration.new Converter() {...} | 0.0% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration | 0.0% |
| 2 | com.alramz.service.ValidationRequestMapper | 84.6% |
| 2 | com.alramz.dfm.DfmMemberApiClient | 97.8% |
| 2 | com.alramz.config.ETradeTokenProvider | 96.5% |
| 2 | com.alramz.OnboardingServiceApplication | 97.0% |
| 1 | com.alramz.service.ValidationResponseMapperRegistry | 85.7% |
| 1 | com.alramz.controllers.OnboardingController | 0.0% |

### reference-data-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 10 | com.alramz.config.RedisConfig | 78.3% |
| 4 | com.alramz.service.SchedulerLockService | 90.0% |
| 3 | com.alramz.config.MiddlewareDataSourceProperties | 92.1% |
| 2 | com.alramz.service.RedisCacheService | 98.9% |
| 2 | com.alramz.ReferenceDataServiceApplication | 88.2% |
| 1 | com.alramz.config.RedisConfig.new RedisStandaloneConfiguration() {...} | 50.0% |

### reporting-management-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 2 | com.alramz.repository.RelationshipManagerRepositoryImpl | 96.9% |
| 2 | com.alramz.ReportingManagementServiceApplication | 88.2% |
| 1 | com.alramz.service.impl.RelationshipManagerServiceImpl | 96.9% |

### risk-management-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 2 | com.alramz.service.RiskComplianceService | 95.7% |
| 2 | com.alramz.RiskManagementServiceApplication | 88.2% |
| 1 | com.alramz.knowyourcountry.KnowYourCountryClient | 96.0% |

### utility-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 2 | com.alramz.UtilityServiceApplication | 88.2% |

### wealth-management-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 3 | org.openapitools.configuration.EnumConverterConfiguration | 0.0% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration.new Converter() {...} | 0.0% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration.new Converter() {...} | 0.0% |
| 2 | com.alramz.service.impl.MmfServiceImpl | 98.6% |
| 2 | com.alramz.WealthManagementServiceApplication | 88.2% |
| 1 | com.alramz.repository.MmfRepositoryImpl | 98.6% |
