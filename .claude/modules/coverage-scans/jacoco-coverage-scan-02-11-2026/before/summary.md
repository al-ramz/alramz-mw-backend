| Service | Line | Branch | Instruction | Lines covered/total | 80% line gate |
|---|---|---|---|---|---|
| alramz-api-starter | 37.9% | 29.7% | 38.5% | 1148/3032 | FAIL |
| alramz-notification-service | n/a (0 tests executed) | n/a | n/a | 0/? | FAIL |
| onboarding-service | 71.0% | 68.0% | 69.9% | 788/1110 | FAIL |
| reference-data-service | 29.3% | 6.0% | 23.8% | 157/535 | FAIL |
| reporting-management-service | 93.0% | 86.8% | 92.9% | 132/142 | PASS |
| utility-service | 56.6% | 19.2% | 50.8% | 82/145 | FAIL |
| wealth-management-service | 93.7% | 95.8% | 95.1% | 254/271 | PASS |

### alramz-api-starter: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 232 | com.alramz.logging.logback.SeqAppender | 0.0% |
| 220 | com.alramz.scheduler.service.impl.JobScheduleManager | 0.0% |
| 110 | com.alramz.logging.util.LoggingHelper | 43.0% |
| 82 | com.alramz.audit.ApiAuditLogService | 1.2% |
| 79 | com.alramz.scheduler.controller.SchedulerManagerController | 0.0% |
| 62 | com.alramz.audit.SensitiveDataMasker | 43.1% |
| 46 | com.alramz.jwt.service.AuthService | 38.7% |
| 44 | com.alramz.client.AbstractRestClient | 0.0% |
| 42 | com.alramz.logging.interceptor.RestTemplateLoggingInterceptor | 0.0% |
| 40 | com.alramz.scheduler.entity.ScheduleJobEntity | 0.0% |
| 39 | com.alramz.logging.filter.ResponseLoggingFilter | 0.0% |
| 39 | com.alramz.jwt.filter.JwtAuthFilter | 0.0% |
| 38 | com.alramz.jwt.aspect.JwtSecuredAspect | 0.0% |
| 37 | com.alramz.logging.util.MethodExecutionLoggingHelper | 57.0% |
| 35 | com.alramz.audit.ApiAuditAspect | 61.1% |

### onboarding-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 103 | com.alramz.repository.DfmOnboardingRepositoryImpl | 5.5% |
| 52 | com.alramz.config.ETradeTokenProvider | 11.9% |
| 49 | com.alramz.service.impl.IBANValidationServiceImpl | 7.5% |
| 37 | com.alramz.OnboardingServiceApplication | 44.8% |
| 24 | com.alramz.service.impl.PhoneValidationServiceImpl | 14.3% |
| 23 | com.alramz.controllers.DataValidationController | 11.5% |
| 7 | com.alramz.client.ETradeClient | 30.0% |
| 4 | com.alramz.service.impl.DuplicateCheckHelper | 90.5% |
| 4 | com.alramz.scheduler.job.DataValidationScheduledJob | 76.5% |
| 3 | com.alramz.service.impl.OnboardingServiceImpl | 95.6% |
| 3 | com.alramz.config.LiquibaseConfiguration | 87.0% |
| 3 | com.alramz.client.ETradeClient.new HttpHeaders() {...} | 0.0% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration.new Converter() {...} | 0.0% |
| 2 | org.openapitools.configuration.EnumConverterConfiguration | 0.0% |
| 2 | com.alramz.service.ValidationRequestMapper | 84.6% |

### reference-data-service: most missed lines
| Missed | Class | Line % |
|---|---|---|
| 163 | com.alramz.service.RedisCacheService | 5.2% |
| 35 | com.alramz.service.SchedulerLockService | 12.5% |
| 31 | com.alramz.controllers.RedisCacheController | 8.8% |
| 20 | com.alramz.scheduler.job.CacheReloadScheduledJob | 23.1% |
| 17 | com.alramz.config.RedisConfig | 61.4% |
| 17 | com.alramz.config.CompanyRedisProperties.Cache.CacheMapping | 0.0% |
| 17 | com.alramz.config.CacheMappingProperties.CacheMapping | 0.0% |
| 13 | com.alramz.service.CacheAuditService | 23.5% |
| 13 | com.alramz.config.MiddlewareDataSourceProperties | 65.8% |
| 13 | com.alramz.config.CacheWarmupRunner | 27.8% |
| 10 | com.alramz.config.CompanyRedisProperties.Connection | 52.4% |
| 6 | com.alramz.config.CompanyRedisProperties | 53.8% |
| 5 | com.alramz.controllers.ApplicationController | 16.7% |
| 3 | com.alramz.config.CacheMappingProperties | 25.0% |
| 2 | com.alramz.config.RedisConfig.new RedisStandaloneConfiguration() {...} | 0.0% |

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
| 61 | com.alramz.finoux.FinouxClient | 0.0% |
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
| 1 | com.alramz.repository.MmfRepositoryImpl | 98.6% |

> alramz-notification-service: surefire was pinned to 3.2.5 in the service pom, which finds no JUnit 6 tests, so its 21 test classes never ran and no jacoco.csv was produced. See ../CHANGELOG.md.

> utility-service: the 56.6% is a measurement artifact. JaCoCo logged `Execution data for class com/alramz/finoux/FinouxClient does not match` (the class file changed between test run and report, an OneDrive I/O issue), so FinouxClient (61 lines) was reported at 0% even though `FinouxClientTest` ran and passed 9 tests. A clean re-run gives 98.6% (141/143) with no new tests.
