| Service | Line | Branch | Instruction | Lines covered/total | 80% line gate |
|---|---|---|---|---|---|
| alramz-api-starter | 80.7% | 65.7% | 81.3% | 2447/3032 | PASS |
| alramz-notification-service | 98.3% | 94.6% | 98.4% | 1248/1269 | PASS |
| onboarding-service | 96.8% | 80.9% | 96.3% | 1075/1110 | PASS |
| reference-data-service | 95.7% | 81.0% | 94.4% | 512/535 | PASS |
| reporting-management-service | 93.0% | 86.8% | 92.9% | 133/143 | PASS |
| utility-service | 98.6% | 76.9% | 98.6% | 141/143 | PASS |
| wealth-management-service | 93.8% | 95.8% | 94.7% | 258/275 | PASS |

### alramz-api-starter: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 34 | com.alramz.jwt.repository.UserRepository | 0.0% |
| 32 | com.alramz.logging.config.LoggingAutoConfiguration | 22.0% |
| 31 | com.alramz.tracing.listener.TracingQueryExecutionListener | 18.4% |
| 29 | com.alramz.utils.SqlQueriesManager | 3.3% |
| 29 | com.alramz.logging.logback.AlramzJsonEncoder | 60.8% |
| 29 | com.alramz.jwt.repository.RefreshTokenRepository | 0.0% |
| 28 | com.alramz.audit.ApiAuditLogFilter | 63.6% |
| 25 | com.alramz.jwt.config.JwtAutoConfiguration | 0.0% |
| 23 | com.alramz.scheduler.config.SchedulerConfigCondition | 0.0% |
| 22 | com.alramz.logging.advice.CorrelationIdResponseBodyAdvice | 0.0% |
| 21 | com.alramz.datasource.config.AbstractDataSourceAutoConfiguration | 81.4% |
| 19 | com.alramz.logging.util.MDCUtil | 65.5% |
| 19 | com.alramz.globalconfigurationsettings.repository.JdbcGlobalConfigurationSettingsRepository | 78.2% |
| 18 | com.alramz.logging.logback.SeqAppender | 92.2% |
| 17 | com.alramz.jwt.controller.AuthController | 37.0% |

### alramz-notification-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 3 | com.alramz.service.impl.SmsServiceImpl | 97.1% |
| 2 | com.alramz.service.impl.WaselneySmsClient.new ErrorHandler() {...} | 50.0% |
| 2 | com.alramz.service.TemplatedEmailService | 98.8% |
| 2 | com.alramz.repository.SmsTableRepository | 92.9% |
| 2 | com.alramz.repository.OtpRepository | 96.4% |
| 2 | com.alramz.repository.EmailTemplateRepository | 95.3% |
| 2 | com.alramz.AlramzNotificationServiceApplication | 88.2% |
| 1 | com.alramz.scheduler.job.SmsDispatchJob | 98.7% |

### onboarding-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 4 | com.alramz.service.impl.DuplicateCheckHelper | 90.5% |
| 4 | com.alramz.scheduler.job.DataValidationScheduledJob | 76.5% |
| 3 | com.alramz.service.impl.OnboardingServiceImpl | 95.6% |
| 3 | com.alramz.config.LiquibaseConfiguration | 87.0% |
| 3 | com.alramz.config.ETradeTokenProvider | 94.9% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration.new Converter() {...} | 0.0% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration | 0.0% |
| 2 | com.alramz.service.impl.PhoneValidationServiceImpl | 92.9% |
| 2 | com.alramz.service.impl.IBANValidationServiceImpl | 96.2% |
| 2 | com.alramz.service.ValidationRequestMapper | 84.6% |
| 2 | com.alramz.dfm.DfmMemberApiClient | 97.8% |
| 2 | com.alramz.client.ETradeClient | 80.0% |
| 2 | com.alramz.OnboardingServiceApplication | 97.0% |
| 1 | com.alramz.service.ValidationResponseMapperRegistry | 85.7% |
| 1 | com.alramz.controllers.OnboardingController | 0.0% |

### reference-data-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 9 | com.alramz.config.RedisConfig | 79.5% |
| 4 | com.alramz.service.SchedulerLockService | 90.0% |
| 4 | com.alramz.service.RedisCacheService | 97.7% |
| 3 | com.alramz.config.MiddlewareDataSourceProperties | 92.1% |
| 2 | com.alramz.ReferenceDataServiceApplication | 88.2% |
| 1 | com.alramz.config.RedisConfig.new RedisStandaloneConfiguration() {...} | 50.0% |

### reporting-management-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 2 | com.alramz.repository.RelationshipManagerRepositoryImpl | 96.9% |
| 2 | com.alramz.ReportingManagementServiceApplication | 88.2% |
| 1 | com.alramz.service.impl.RelationshipManagerServiceImpl | 96.9% |

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
| 2 | com.alramz.service.impl.MmfServiceImpl | 98.5% |
| 2 | com.alramz.WealthManagementServiceApplication | 88.2% |
| 1 | com.alramz.repository.MmfRepositoryImpl | 98.7% |
