# SonarQube before/after comparison

Quality gate: NONE → **OK**

| Issues | Count |
|---|---|
| before | 504 |
| after | 28 |
| fixed | 476 |
| pending | 28 |
| new | 0 |
| changed | 5 |
| hotspots_before | 0 |
| hotspots_after | 0 |
| hotspots_to_review_after | 0 |

| Metric | Before | After |
|---|---|---|
| alert_status | n/a | OK |
| blocker_violations | 5 | 0 |
| bugs | 11 | 0 |
| code_smells | 481 | 28 |
| coverage | 77.2 | 77.3 |
| critical_violations | 56 | 0 |
| duplicated_lines_density | 4.0 | 4.1 |
| info_violations | 10 | 2 |
| line_coverage | 77.3 | 77.2 |
| major_violations | 220 | 24 |
| minor_violations | 213 | 2 |
| ncloc | 17963 | 18012 |
| new_violations | n/a | n/a |
| reliability_rating | C | A |
| security_hotspots | 0 | 0 |
| security_hotspots_reviewed | 100.0 | 100.0 |
| security_rating | D | A |
| security_review_rating | A | A |
| software_quality_maintainability_issues | 482 | 28 |
| software_quality_reliability_issues | 18 | 0 |
| software_quality_security_issues | 12 | 0 |
| sqale_index | 3049 | 345 |
| sqale_rating | A | A |
| violations | 504 | 28 |
| vulnerabilities | 12 | 0 |

## New (0)

| Rule | Sev | File:Line | Message |
|---|---|---|---|

## Pending (28)

| Rule | Sev | File:Line | Message |
|---|---|---|---|
| java:S2139 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:49 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S2139 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:71 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S2139 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:121 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S2143 | INFO/INFO | services/alramz-api-starter/src/main/java/com/alramz/jwt/service/TokenProvider.java:None | Use the "java.time" API for date and time. |
| java:S107 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/model/ScheduleInfoBean.java:24 | Constructor has 9 parameters, which is greater than 7 authorized. |
| java:S2143 | INFO/INFO | services/alramz-api-starter/src/main/java/com/alramz/utils/DataValidationUtil.java:None | Use the "java.time" API for date and time. |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java:32 | Remove this unused method parameter "endpoint". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java:36 | Remove these unused method parameters "path", "method", "password", "body", "unused". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java:40 | Remove this unused method parameter "clientSecret". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/client/AuditProbeClient.java:6 | Remove this unused method parameter "endpoint". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/controllers/AuditProbeController.java:9 | Remove this unused method parameter "body". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:221 | Remove these unused method parameters "arg1", "arg2". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImpl.java:10 | Remove this unused method parameter "value". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/service/impl/IBANValidationServiceImpl.java:10 | Remove this unused method parameter "value". |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/service/impl/PhoneValidationServiceImpl.java:10 | Remove this unused method parameter "value". |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalETradeServiceConfig.java:43 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalETradeServiceConfig.java:43 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalIbanServiceConfig.java:43 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalIbanServiceConfig.java:43 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java:43 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java:43 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java:18 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java:21 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S1075 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:27 | Refactor your code to get this URI from a customizable parameter. |
| java:S1075 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:28 | Refactor your code to get this URI from a customizable parameter. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java:45 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java:45 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S2139 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:173 | Either log this exception and handle it, or rethrow it with some contextual information. |

## Changed (5)

| Rule | Sev | File:Line | Message |
|---|---|---|---|
| java:S2139 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:49 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S2139 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:71 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S2139 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:121 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:221 | Remove these unused method parameters "arg1", "arg2". |
| java:S2139 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:173 | Either log this exception and handle it, or rethrow it with some contextual information. |

## Fixed (476)

| Rule | Sev | File:Line | Message |
|---|---|---|---|
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditAspect.java:3 | Remove this unused import 'com.alramz.logging.config.LoggingProperties'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditAspect.java:5 | Remove this unused import 'com.fasterxml.jackson.databind.ObjectMapper'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditAspect.java:12 | Remove this unused import 'org.springframework.stereotype.Component'. |
| java:S3776 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditAspect.java:153 | Refactor this method to reduce its Cognitive Complexity from 20 to the 15 allowed. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogFilter.java:21 | Remove this unused import 'org.springframework.web.util.WebUtils'. |
| java:S2387 | BLOCKER/BLOCKER | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogFilter.java:29 | "logger" is the name of a field in "GenericFilterBean". |
| java:S1068 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogFilter.java:29 | Remove this unused "logger" private field. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogFilter.java:81 | Define a constant instead of duplicating this literal "FAILURE" 3 times. |
| java:S6880 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogFilter.java:106 | Replace the chain of if/else with a switch expression. |
| java:S6485 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/ApiAuditLogService.java:58 | Replace this call to the constructor with the better suited static method HashMap.newHashMap(int numMappings) |
| java:S1068 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java:36 | Remove this unused "MAX_RECURSION_DEPTH" private field. |
| java:S6485 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java:122 | Replace this call to the constructor with the better suited static method LinkedHashMap.newLinkedHashMap(int numMappings) |
| java:S6880 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/SensitiveDataMasker.java:146 | Replace the chain of if/else with a switch expression. |
| java:S6213 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/audit/WebClientAuditFilter.java:86 | Rename this method to not match a restricted identifier. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:37 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:37 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:41 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:41 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:45 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:45 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S107 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:49 | Method has 8 parameters, which is greater than 7 authorized. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:49 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:49 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:100 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:100 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S119 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/client/AbstractRestClient.java:104 | Rename this generic name to match the regular expression '^[A-Z][0-9]?$'. |
| java:S3776 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/datasource/config/AbstractDataSourceAutoConfiguration.java:83 | Refactor this method to reduce its Cognitive Complexity from 28 to the 15 allowed. |
| java:S1066 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/datasource/config/AbstractDataSourceAutoConfiguration.java:102 | Merge this if statement with the enclosing one. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/datasource/config/IntegrationDataSourceAutoConfiguration.java:28 | Define a constant instead of duplicating this literal "integration" 5 times. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/datasource/config/MiddlewareDataSourceAutoConfiguration.java:28 | Define a constant instead of duplicating this literal "middleware" 5 times. |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/exceptions/ApiCallFailedException.java:12 | Remove these unused method parameters "uri", "method". |
| java:S6204 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java:94 | Replace this usage of 'Stream.collect(Collectors.toUnmodifiableList())' with 'Stream.toList()'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/annotation/JwtSecured.java:3 | Remove this unused import 'org.springframework.security.access.prepost.PreAuthorize'. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java:81 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1130 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java:81 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S4502 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtAutoConfiguration.java:83 | Make sure disabling Spring Security's CSRF protection is safe here. |
| java:S2184 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtProperties.java:21 | Cast one of the operands of this multiplication operation to a "long". |
| java:S2184 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/config/JwtProperties.java:23 | Cast one of the operands of this multiplication operation to a "long". |
| java:S2387 | BLOCKER/BLOCKER | services/alramz-api-starter/src/main/java/com/alramz/jwt/filter/JwtAuthFilter.java:30 | "logger" is the name of a field in "GenericFilterBean". |
| java:S1068 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/jwt/filter/JwtAuthFilter.java:32 | Remove this unused "properties" private field. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/model/TokenPair.java:3 | Remove this unused import 'java.time.Instant'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/model/User.java:3 | Remove this unused import 'java.time.Instant'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/repository/RefreshTokenRepositoryOps.java:5 | Remove this unused import 'java.util.Optional'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/repository/UserRepository.java:8 | Remove this unused import 'java.time.ZoneId'. |
| java:S8688 | INFO/INFO | services/alramz-api-starter/src/main/java/com/alramz/jwt/repository/UserRepository.java:75 | Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. |
| java:S2184 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/service/AuthService.java:73 | Cast one of the operands of this multiplication operation to a "long". |
| java:S2184 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/jwt/service/AuthService.java:149 | Cast one of the operands of this multiplication operation to a "long". |
| java:S3776 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/aspect/MethodExecutionLoggingAspect.java:31 | Refactor this method to reduce its Cognitive Complexity from 38 to the 15 allowed. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingAutoConfiguration.java:26 | Remove this unused import 'org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate'. |
| java:S106 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingAutoConfiguration.java:132 | Replace this use of System.out by a logger. |
| java:S106 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingAutoConfiguration.java:139 | Replace this use of System.out by a logger. |
| java:S106 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingAutoConfiguration.java:140 | Replace this use of System.out by a logger. |
| java:S106 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingAutoConfiguration.java:141 | Replace this use of System.out by a logger. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingProperties.java:46 | Define a constant instead of duplicating this literal "/actuator/**" 3 times. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingProperties.java:46 | Define a constant instead of duplicating this literal "/v3/api-docs/**" 3 times. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/config/LoggingProperties.java:46 | Define a constant instead of duplicating this literal "/swagger-ui/**" 3 times. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/config/SeqLoggingConfig.java:54 | Use already-defined constant 'DEFAULT_LEVEL' instead of duplicating its value here. |
| java:S1124 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/constants/LoggingConstants.java:23 | Reorder the modifiers to comply with the Java Language Specification. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/CorrelationIdFilter.java:11 | Remove this unused import 'lombok.extern.slf4j.Slf4j'. |
| java:S2387 | BLOCKER/BLOCKER | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/CorrelationIdFilter.java:43 | "logger" is the name of a field in "GenericFilterBean". |
| java:S1068 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/CorrelationIdFilter.java:43 | Remove this unused "logger" private field. |
| java:S2387 | BLOCKER/BLOCKER | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/RequestLoggingFilter.java:30 | "logger" is the name of a field in "GenericFilterBean". |
| java:S6880 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/RequestLoggingFilter.java:49 | Replace the chain of if/else with a switch expression. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/ResponseLoggingFilter.java:20 | Remove this unused import 'org.springframework.http.HttpStatus'. |
| java:S2387 | BLOCKER/BLOCKER | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/ResponseLoggingFilter.java:33 | "logger" is the name of a field in "GenericFilterBean". |
| java:S6880 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/ResponseLoggingFilter.java:51 | Replace the chain of if/else with a switch expression. |
| java:S6201 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/filter/ResponseLoggingFilter.java:85 | Replace this instanceof check and cast with 'instanceof Long long' |
| java:S1066 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/RestTemplateLoggingInterceptor.java:89 | Merge this if statement with the enclosing one. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/WebClientLoggingFilter.java:14 | Remove this unused import 'org.springframework.web.reactive.function.client.ExchangeFunction'. |
| java:S3776 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/WebClientLoggingFilter.java:37 | Refactor this method to reduce its Cognitive Complexity from 20 to the 15 allowed. |
| java:S1066 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/WebClientLoggingFilter.java:55 | Merge this if statement with the enclosing one. |
| java:S1066 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/WebClientLoggingFilter.java:77 | Merge this if statement with the enclosing one. |
| java:S1066 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/interceptor/WebClientLoggingFilter.java:128 | Merge this if statement with the enclosing one. |
| java:S1117 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/AlramzJsonEncoder.java:145 | Rename "json" which hides the field declared at line 47. |
| java:S1172 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/AlramzJsonEncoder.java:162 | Remove this unused method parameter "cause". |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:4 | Remove this unused import 'ch.qos.logback.classic.Level'. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:44 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:45 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:46 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:47 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:51 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:52 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:53 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:55 | Remove the "transient" modifier from this field. |
| java:S2065 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:56 | Remove the "transient" modifier from this field. |
| java:S7158 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:166 | Use "isEmpty()" to check whether a "StringBuilder" is empty or not. |
| java:S3078 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:207 | Use an "AtomicInteger" for this field; its operations are atomic. |
| java:S135 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:223 | Reduce the total number of break and continue statements in this loop to use at most one. |
| java:S2142 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:233 | Either re-interrupt this method or rethrow the "InterruptedException" that can be caught here. |
| java:S7158 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/logback/SeqAppender.java:333 | Use "isEmpty()" to check whether a "StringBuilder" is empty or not. |
| java:S1181 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/otel/OpenTelemetryTraceContextExtractor.java:22 | Catch Exception instead of Throwable. |
| java:S1181 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/otel/OpenTelemetryTraceContextExtractor.java:36 | Catch Exception instead of Throwable. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:3 | Remove this unused import 'java.util.ArrayList'. |
| java:S5998 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:30 | Refactor this repetition that can lead to a stack overflow for large inputs. |
| java:S5843 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:30 | Simplify this regular expression to reduce its complexity from 21 to the 20 allowed. |
| java:S5869 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:33 | Remove duplicates in this character class. |
| java:S5869 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:33 | Remove duplicates in this character class. |
| java:S5869 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:33 | Remove duplicates in this character class. |
| java:S5869 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LogMaskingUtil.java:34 | Remove duplicates in this character class. |
| javabugs:S2259 | MAJOR/BLOCKER | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LoggingHelper.java:78 | Fix this access that will throw a NullPointerException when executed. |
| java:S1066 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/logging/util/LoggingHelper.java:318 | Merge this if statement with the enclosing one. |
| java:S3776 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/logging/util/MethodExecutionLoggingHelper.java:61 | Refactor this method to reduce its Cognitive Complexity from 17 to the 15 allowed. |
| java:S1126 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/logging/util/MethodExecutionLoggingHelper.java:82 | Replace this if-then-else statement by a single return statement. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerAutoConfiguration.java:7 | Remove this unnecessary import: same package classes are always implicitly imported. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerAutoConfiguration.java:8 | Remove this unnecessary import: same package classes are always implicitly imported. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerConfigCondition.java:12 | Remove this unused import 'org.springframework.beans.factory.config.ConfigurableListableBeanFactory'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/config/SchedulerConfiguration.java:3 | Remove this unused import 'java.util.Optional'. |
| java:S1214 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/scheduler/constants/SchedulerConstants.java:3 | Move constants defined in this interfaces to another class or enum. |
| java:S6813 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:36 | Remove this field injection and use constructor injection instead. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:51 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:70 | Format specifiers should be used instead of string concatenation. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:70 | Log Injection via HTTP request body in SchedulerManagerController.restartScheduler() |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:79 | Format specifiers should be used instead of string concatenation. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:81 | Define a constant instead of duplicating this literal "ERROR: Failed to restart the scheduler" 3 times. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:99 | Format specifiers should be used instead of string concatenation. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:99 | Log Injection via HTTP request body in SchedulerManagerController.restartScheduledForJobs() |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:108 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:124 | Format specifiers should be used instead of string concatenation. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:124 | Log Injection via HTTP request body in SchedulerManagerController.unregisterJobs() |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:132 | Format specifiers should be used instead of string concatenation. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:145 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1130 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:145 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:156 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/controller/SchedulerManagerController.java:179 | Format specifiers should be used instead of string concatenation. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/ISchedulerService.java:11 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/ISchedulerService.java:13 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/ISchedulerService.java:15 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/ISchedulerService.java:17 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/ISchedulerService.java:21 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:19 | Remove this unused import 'org.springframework.jdbc.core.RowMapper'. |
| java:S9438 | MINOR/LOW,MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:51 | Make this private field "final", it is never reassigned. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:65 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:115 | Format specifiers should be used instead of string concatenation. |
| java:S112 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:123 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:134 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:144 | Format specifiers should be used instead of string concatenation. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:144 | Log Injection via HTTP request body in JobScheduleManager.reintializeSchedulerForJobs() |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:154 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:162 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:175 | Format specifiers should be used instead of string concatenation. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:175 | Log Injection via HTTP request body in JobScheduleManager.unregisterJobs() |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:187 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:198 | Format specifiers should be used instead of string concatenation. |
| java:S3776 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:216 | Refactor this method to reduce its Cognitive Complexity from 113 to the 15 allowed. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:227 | Log Injection via HTTP request body in JobScheduleManager.schedule() |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:251 | Log Injection via HTTP request body in JobScheduleManager.schedule() |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:260 | Log Injection via HTTP request body in JobScheduleManager.schedule() |
| java:S135 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:262 | Reduce the total number of break and continue statements in this loop to use at most one. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:310 | Log Injection via HTTP request body in JobScheduleManager.schedule() |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:317 | Format specifiers should be used instead of string concatenation. |
| java:S1141 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:328 | Extract this nested try block into a separate method. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:354 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:361 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:367 | Format specifiers should be used instead of string concatenation. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:383 | Format specifiers should be used instead of string concatenation. |
| java:S1155 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:386 | Use isEmpty() to check whether the collection is empty or not. |
| java:S1155 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:386 | Use isEmpty() to check whether the collection is empty or not. |
| java:S3457 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:391 | Format specifiers should be used instead of string concatenation. |
| java:S125 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:394 | This block of commented-out lines of code should be removed. |
| java:S1135 | INFO/INFO | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:394 | Complete the task associated to this TODO comment. |
| javasecurity:S5145 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:397 | Log Injection via HTTP request body in JobScheduleManager.schedule() |
| java:S3599 | MINOR/LOW | services/alramz-api-starter/src/main/java/com/alramz/scheduler/service/impl/JobScheduleManager.java:401 | Use another way to initialize this instance. |
| java:S3358 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/tracing/config/TracingAutoConfiguration.java:98 | Extract this nested ternary operation into an independent statement. |
| java:S1118 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/utils/DataValidationUtil.java:8 | Add a private constructor to hide the implicit public one. |
| java:S1068 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/utils/PWProtector.java:16 | Remove this unused "logger" private field. |
| java:S1192 | CRITICAL/HIGH | services/alramz-api-starter/src/main/java/com/alramz/utils/SqlQueriesManager.java:26 | Define a constant instead of duplicating this literal "sql.file" 3 times. |
| java:S108 | MAJOR/MEDIUM | services/alramz-api-starter/src/main/java/com/alramz/utils/SqlQueriesManager.java:75 | Remove this block of code, fill it in, or add a comment explaining why it is empty. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java:97 | Use assertThat(actual).hasToString(expectedString) instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditLogFilterTest.java:67 | Use assertThat(actual).hasToString(expectedString) instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/SensitiveDataMaskerTest.java:52 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/SensitiveDataMaskerTest.java:53 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/SensitiveDataMaskerTest.java:57 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/SensitiveDataMaskerTest.java:58 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/WebClientAuditFilterTest.java:25 | Remove this unused import 'java.net.URI'. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/audit/WebClientAuditFilterTest.java:102 | Use assertThat(actual).hasToString(expectedString) instead. |
| java:S5958 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/audit/WebClientAuditFilterTest.java:118 | Test further the exception raised by this assertThatThrownBy call. |
| java:S5958 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/audit/WebClientAuditFilterTest.java:131 | Test further the exception raised by this assertThatThrownBy call. |
| java:S5958 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/audit/WebClientAuditFilterTest.java:144 | Test further the exception raised by this assertThatThrownBy call. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/client/AbstractRestClientTest.java:153 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/datasource/IntegrationDataSourceAutoConfigurationTest.java:7 | Remove this unused import 'org.springframework.context.annotation.Configuration'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/datasource/MiddlewareDataSourceAutoConfigurationTest.java:3 | Remove this unused import 'com.alramz.datasource.config.DatasourceProperties'. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/datasource/MiddlewareDataSourceAutoConfigurationTest.java:8 | Remove this unused import 'org.springframework.context.annotation.Configuration'. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/datasource/config/DatasourcePropertiesTest.java:20 | Use isZero() instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/datasource/config/DatasourcePropertiesTest.java:21 | Use isZero() instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/datasource/config/DatasourcePropertiesTest.java:26 | Use isZero() instead. |
| java:S6213 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java:158 | Rename this variable to not match a restricted identifier. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java:199 | Use assertThat(actual).hasSameHashCodeAs(expected) instead. |
| java:S6213 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java:211 | Rename this variable to not match a restricted identifier. |
| java:S5853 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/mapper/GlobalConfigurationSettingsRowMapperTest.java:215 | Join these multiple assertions subject to one assertion chain. |
| java:S1128 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryIT.java:4 | Remove this unused import 'com.alramz.globalconfigurationsettings.service.GlobalConfigurationSettingsService'. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryIT.java:151 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryTest.java:212 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryTest.java:262 | Use isZero() instead. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepositoryTest.java:308 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/aspect/JwtSecuredAspectTest.java:28 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/aspect/JwtSecuredAspectTest.java:32 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S9016 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/jwt/aspect/JwtSecuredAspectTest.java:38 | Extract this mock creation to a local variable. |
| java:S1130 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/jwt/aspect/JwtSecuredAspectTest.java:57 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S1130 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/jwt/aspect/JwtSecuredAspectTest.java:65 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/jwt/controller/AuthControllerTest.java:56 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/controller/AuthControllerTest.java:79 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/controller/AuthControllerTest.java:80 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java:43 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java:44 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java:73 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java:74 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java:99 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S1186 | CRITICAL/HIGH | services/alramz-api-starter/src/test/java/com/alramz/jwt/service/AuthServiceTest.java:100 | Add a nested comment explaining why this method is empty, throw an UnsupportedOperationException or complete the implementation. |
| java:S5853 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/AlramzJsonEncoderTest.java:85 | Join these multiple assertions subject to one assertion chain. |
| java:S5853 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/LogMaskingUtilTest.java:24 | Join these multiple assertions subject to one assertion chain. |
| java:S5853 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/LogMaskingUtilTest.java:78 | Join these multiple assertions subject to one assertion chain. |
| java:S6068 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/LoggingHelperTest.java:224 | Remove this and every subsequent useless "eq(...)" invocation; pass the values directly. |
| java:S6068 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/LoggingHelperTest.java:246 | Remove this and every subsequent useless "eq(...)" invocation; pass the values directly. |
| java:S6068 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/LoggingHelperTest.java:250 | Remove this and every subsequent useless "eq(...)" invocation; pass the values directly. |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:44 | Use a static import for "mock". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:46 | Use a static import for "mock". |
| java:S5853 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:79 | Join these multiple assertions subject to one assertion chain. |
| java:S108 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:101 | Remove this block of code, fill it in, or add a comment explaining why it is empty. |
| java:S5853 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:109 | Join these multiple assertions subject to one assertion chain. |
| java:S9016 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:118 | Extract this mock creation to a local variable. |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:119 | Use a static import for "mock". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:121 | Use a static import for "mock". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:134 | Use a static import for "verify". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:135 | Use a static import for "verify". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:136 | Use a static import for "verify". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:137 | Use a static import for "verify". |
| java:S108 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:143 | Remove this block of code, fill it in, or add a comment explaining why it is empty. |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:145 | Use a static import for "verify". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:146 | Use a static import for "verify". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java:146 | Use a static import for "times". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/filter/RequestLoggingFilterTest.java:61 | Use a static import for "times". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/filter/ResponseLoggingFilterTest.java:103 | Use a static import for "never". |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/interceptor/WebClientLoggingFilterTest.java:101 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5838 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/logging/interceptor/WebClientLoggingFilterTest.java:102 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S2925 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/logback/SeqAppenderTest.java:82 | Remove this use of "Thread.sleep()". |
| java:S2925 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/logback/SeqAppenderTest.java:179 | Remove this use of "Thread.sleep()". |
| java:S2925 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/logback/SeqAppenderTest.java:181 | Remove this use of "Thread.sleep()". |
| java:S2925 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/logback/SeqAppenderTest.java:199 | Remove this use of "Thread.sleep()". |
| java:S2925 | MAJOR/MEDIUM | services/alramz-api-starter/src/test/java/com/alramz/logging/logback/SeqAppenderTest.java:229 | Remove this use of "Thread.sleep()". |
| java:S8924 | MINOR/LOW | services/alramz-api-starter/src/test/java/com/alramz/scheduler/service/impl/JobScheduleManagerTest.java:242 | Use a static import for "verify". |
| java:S1452 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java:46 | Remove usage of generic wildcard type. |
| java:S3776 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/config/SmsProperties.java:38 | Refactor this method to reduce its Cognitive Complexity from 20 to the 15 allowed. |
| java:S3398 | MINOR/LOW | services/alramz-notification-service/src/main/java/com/alramz/config/SmsProperties.java:101 | Move this method into "Etisalat". |
| java:S1192 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/repository/OtpRepository.java:62 | Define a constant instead of duplicating this literal "serviceName" 4 times. |
| java:S6880 | MAJOR/MEDIUM | services/alramz-notification-service/src/main/java/com/alramz/scheduler/job/SmsDispatchJob.java:76 | Replace the chain of if/else with a switch expression. |
| java:S1141 | MAJOR/MEDIUM | services/alramz-notification-service/src/main/java/com/alramz/scheduler/job/SmsDispatchJob.java:102 | Extract this nested try block into a separate method. |
| java:S3776 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/OtpService.java:396 | Refactor this method to reduce its Cognitive Complexity from 21 to the 15 allowed. |
| java:S1192 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/TemplatedEmailService.java:54 | Define a constant instead of duplicating this literal "MSWORD" 3 times. |
| java:S1192 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/TemplatedEmailService.java:54 | Define a constant instead of duplicating this literal "MSEXCEL" 3 times. |
| java:S3776 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/TemplatedEmailService.java:165 | Refactor this method to reduce its Cognitive Complexity from 18 to the 15 allowed. |
| java:S3776 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/impl/EmailServiceImpl.java:203 | Refactor this method to reduce its Cognitive Complexity from 18 to the 15 allowed. |
| javabugs:S2259 | MAJOR/BLOCKER | services/alramz-notification-service/src/main/java/com/alramz/service/impl/EtisalatSmsClient.java:96 | Fix this access that will throw a NullPointerException when executed. |
| java:S6353 | MINOR/LOW | services/alramz-notification-service/src/main/java/com/alramz/service/impl/SmsServiceImpl.java:38 | Use concise character class syntax '\\d' instead of '[0-9]'. |
| java:S6353 | MINOR/LOW | services/alramz-notification-service/src/main/java/com/alramz/service/impl/SmsServiceImpl.java:39 | Use concise character class syntax '\\d' instead of '[0-9]'. |
| java:S6353 | MINOR/LOW | services/alramz-notification-service/src/main/java/com/alramz/service/impl/SmsServiceImpl.java:39 | Use concise character class syntax '\\d' instead of '[0-9]'. |
| java:S3776 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/impl/SmsServiceImpl.java:57 | Refactor this method to reduce its Cognitive Complexity from 19 to the 15 allowed. |
| java:S1192 | CRITICAL/HIGH | services/alramz-notification-service/src/main/java/com/alramz/service/impl/SmsServiceImpl.java:130 | Define a constant instead of duplicating this literal "SMS009" 3 times. |
| java:S5838 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/config/ProfileSecurityConfigTest.java:33 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java:40 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java:60 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S8924 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java:77 | Use a static import for "when". |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/RequestSizeLimitConfigTest.java:154 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1068 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:16 | Remove this unused "ETISALAT" private field. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:64 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:73 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:84 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:102 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:109 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:111 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:113 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/config/SmsPropertiesTest.java:119 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/controllers/SmsControllerTest.java:80 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/controllers/SmsControllerTest.java:89 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/controllers/SmsControllerTest.java:119 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/controllers/SmsControllerTest.java:130 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5853 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/scheduler/job/SmsDispatchJobTest.java:227 | Join these multiple assertions subject to one assertion chain. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java:407 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java:417 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java:547 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java:553 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java:569 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/OtpServiceTest.java:590 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/TemplatedEmailServiceTest.java:442 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/TemplatedEmailServiceTest.java:526 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/TemplatedEmailServiceTest.java:545 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/TemplatedEmailServiceTest.java:570 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5853 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/service/TemplatedEmailServiceTest.java:572 | Join these multiple assertions subject to one assertion chain. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:299 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:311 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:365 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:380 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:407 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5853 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:409 | Join these multiple assertions subject to one assertion chain. |
| java:S5778 | MAJOR/MEDIUM | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EmailServiceImplTest.java:417 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5853 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/service/impl/EtisalatSmsClientTest.java:221 | Join these multiple assertions subject to one assertion chain. |
| java:S5853 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/service/impl/SmsServiceImplTest.java:296 | Join these multiple assertions subject to one assertion chain. |
| java:S5853 | MINOR/LOW | services/alramz-notification-service/src/test/java/com/alramz/service/impl/VeriPhoneClientTest.java:82 | Join these multiple assertions subject to one assertion chain. |
| java:S1068 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/client/ETradeClient.java:15 | Remove this unused "etradeProperties" private field. |
| java:S3599 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/client/ETradeClient.java:37 | Use another way to initialize this instance. |
| java:S2147 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/client/ETradeClient.java:44 | Combine this catch with the one at line 42, which has the same body. |
| java:S1068 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ETradeTokenProvider.java:25 | Remove this unused "objectMapper" private field. |
| java:S3776 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/config/ETradeTokenProvider.java:66 | Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed. |
| java:S112 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/controllers/ApplicationController.java:26 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1130 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/controllers/ApplicationController.java:26 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/controllers/DataValidationController.java:3 | Remove this unused import 'com.alramz.api.DfmOnboardingApi'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/controllers/DataValidationController.java:9 | Remove this unused import 'com.alramz.model.OnboardingRequest'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/controllers/DataValidationController.java:10 | Remove this unused import 'com.alramz.model.OnboardingResponse'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/controllers/DataValidationController.java:11 | Remove this unused import 'com.alramz.service.OnboardingService'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/controllers/DataValidationController.java:27 | Remove this unused import 'jakarta.validation.Valid'. |
| java:S3776 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/dfm/DfmMemberApiClient.java:129 | Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed. |
| java:S3776 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/dfm/DfmOnboardingPayload.java:92 | Refactor this method to reduce its Cognitive Complexity from 18 to the 15 allowed. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:44 | Define a constant instead of duplicating this literal "ONB011" 12 times. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:90 | Define a constant instead of duplicating this literal "errors" 10 times. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:90 | Define a constant instead of duplicating this literal "message" 10 times. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:95 | Define a constant instead of duplicating this literal "Invalid request" 5 times. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:152 | Define a constant instead of duplicating this literal "Malformed JSON request" 3 times. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:354 | Define a constant instead of duplicating this literal "Internal server error" 3 times. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/exception/OnboardingExceptionHandler.java:32 | Define a constant instead of duplicating this literal "ONB011" 5 times. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/ValidationRequestMapper.java:10 | Remove this unused import 'com.fasterxml.jackson.core.JsonProcessingException'. |
| java:S6878 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/DfmInvestorServiceImpl.java:59 | Use the record pattern instead of this pattern match variable. |
| java:S6878 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/DfmInvestorServiceImpl.java:61 | Use the record pattern instead of this pattern match variable. |
| java:S1192 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/service/impl/DuplicateCheckHelper.java:51 | Define a constant instead of duplicating this literal "Reference_No" 3 times. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:5 | Remove this unused import 'com.alramz.exception.ApplicationException'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:32 | Remove this unused import 'java.util.List'. |
| java:S1068 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:38 | Remove this unused "objectMapper" private field. |
| java:S2147 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:84 | Combine this catch with the one at line 82, which has the same body. |
| java:S1854 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:112 | Remove this useless assignment to local variable "charsCode". |
| java:S1481 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:112 | Remove this unused "charsCode" local variable. |
| java:S1854 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:113 | Remove this useless assignment to local variable "accountCode". |
| java:S1481 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:113 | Remove this unused "accountCode" local variable. |
| java:S1854 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:114 | Remove this useless assignment to local variable "ibanCode". |
| java:S1481 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:114 | Remove this unused "ibanCode" local variable. |
| java:S1854 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:115 | Remove this useless assignment to local variable "lengthCode". |
| java:S1481 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:115 | Remove this unused "lengthCode" local variable. |
| java:S1854 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:116 | Remove this useless assignment to local variable "countrySupportCode". |
| java:S1481 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:116 | Remove this unused "countrySupportCode" local variable. |
| java:S1854 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:117 | Remove this useless assignment to local variable "structureCode". |
| java:S1481 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/IBANValidationServiceImpl.java:117 | Remove this unused "structureCode" local variable. |
| java:S1157 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/OnboardingServiceImpl.java:113 | Replace these toUpperCase()/toLowerCase() and equals() calls with a single equalsIgnoreCase() call. |
| java:S1157 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/OnboardingServiceImpl.java:120 | Replace these toUpperCase()/toLowerCase() and equals() calls with a single equalsIgnoreCase() call. |
| java:S1068 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/PhoneValidationServiceImpl.java:32 | Remove this unused "objectMapper" private field. |
| java:S2147 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/service/impl/PhoneValidationServiceImpl.java:75 | Combine this catch with the one at line 73, which has the same body. |
| java:S1068 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/ValidationServiceImpl.java:33 | Remove this unused "etradeProperties" private field. |
| java:S3776 | CRITICAL/HIGH | services/onboarding-service/src/main/java/com/alramz/service/impl/ValidationServiceImpl.java:108 | Refactor this method to reduce its Cognitive Complexity from 32 to the 15 allowed. |
| java:S6880 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/service/impl/ValidationServiceImpl.java:110 | Replace the chain of if/else with a switch expression. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/AopLoggingIntegrationTest.java:9 | Remove this unused import 'org.springframework.test.context.ActiveProfiles'. |
| java:S5853 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/config/DfmMaskingConfigTest.java:105 | Join these multiple assertions subject to one assertion chain. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/controllers/ApplicationControllerTest.java:13 | Remove this unused import 'org.mockito.Mockito.when'. |
| java:S5838 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/controllers/ApplicationControllerTest.java:36 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5838 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/controllers/ApplicationControllerTest.java:37 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/dfm/DfmMemberApiClientTest.java:7 | Remove this unused import 'com.alramz.model.DfmAddress'. |
| java:S5961 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/dfm/DfmMemberApiClientTest.java:141 | Refactor this method to reduce the number of assertions from 34 to less than 25. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/dfm/DfmMemberApiClientTest.java:401 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/dfm/DfmMemberApiClientTest.java:413 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java:12 | Remove this unused import 'jakarta.validation.ConstraintViolation'. |
| java:S8924 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java:29 | Use a static import for "mock". |
| java:S8924 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java:30 | Use a static import for "mock". |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/repository/DfmOnboardingRepositoryImplTest.java:124 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/scheduler/job/DataValidationScheduledJobTest.java:10 | Remove this unused import 'org.slf4j.Logger'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/scheduler/job/DataValidationScheduledJobTest.java:11 | Remove this unused import 'org.slf4j.LoggerFactory'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/scheduler/job/DataValidationScheduledJobTest.java:16 | Remove this unused import 'org.mockito.ArgumentMatchers.anyString'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java:8 | Remove this unused import 'org.springframework.jdbc.core.JdbcTemplate'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java:13 | Remove this unused import 'org.mockito.ArgumentMatchers.eq'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java:14 | Remove this unused import 'org.mockito.Mockito.mock'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java:15 | Remove this unused import 'org.mockito.Mockito.when'. |
| java:S5838 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java:38 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S5838 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationRequestMapperTest.java:39 | Use assertThat(actual).containsEntry(key, value) instead. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationResponseMapperRegistryTest.java:3 | Remove this unused import 'com.alramz.config.ValidationDefinition'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationResponseMapperRegistryTest.java:4 | Remove this unused import 'com.alramz.model.ValidationRequest'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/ValidationResponseMapperRegistryTest.java:18 | Remove this unused import 'org.assertj.core.api.Assertions.assertThatThrownBy'. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/DfmInvestorServiceImplTest.java:272 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/DfmInvestorServiceImplTest.java:282 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/DfmInvestorServiceImplTest.java:291 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/DfmInvestorServiceImplTest.java:315 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java:8 | Remove this unnecessary import: same package classes are always implicitly imported. |
| java:S8924 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java:44 | Use a static import for "mock". |
| java:S8924 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java:50 | Use a static import for "mock". |
| java:S4144 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java:54 | Update this method so that its implementation is not identical to "helperWithoutNinService" on line 49. |
| java:S8924 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java:55 | Use a static import for "mock". |
| java:S8924 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckHelperTest.java:61 | Use a static import for "mock". |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:5 | Remove this unused import 'com.alramz.exception.ExternalSystemException'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:6 | Remove this unused import 'com.alramz.exceptions.ApiCallFailedException'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:7 | Remove this unused import 'com.alramz.model.ETradeResponse'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:8 | Remove this unused import 'com.alramz.service.DuplicateCheckService'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:9 | Remove this unnecessary import: same package classes are always implicitly imported. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:17 | Remove this unused import 'java.util.Optional'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:20 | Remove this unused import 'org.assertj.core.api.Assertions.assertThatThrownBy'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:21 | Remove this unused import 'org.mockito.ArgumentMatchers.any'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:22 | Remove this unused import 'org.mockito.ArgumentMatchers.anyString'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:25 | Remove this unused import 'org.mockito.Mockito.times'. |
| java:S1144 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:44 | Remove this unused private "mockResData" method. |
| java:S5853 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImplTest.java:61 | Join these multiple assertions subject to one assertion chain. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java:111 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java:122 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java:131 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java:140 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java:149 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/IBANValidationServiceImplTest.java:158 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/NinTradingNumberValidationServiceTest.java:19 | Remove this unused import 'org.mockito.Mockito.verify'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/OnboardingServiceImplTest.java:4 | Remove this unused import 'com.alramz.exception.ExternalSystemException'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/OnboardingServiceImplTest.java:10 | Remove this unused import 'com.alramz.service.OnboardingService'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/OnboardingServiceImplTest.java:16 | Remove this unused import 'org.springframework.dao.DataAccessException'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/OnboardingServiceImplTest.java:23 | Remove this unused import 'org.mockito.Mockito.never'. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java:93 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java:104 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java:113 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java:122 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java:131 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/service/impl/PhoneValidationServiceImplTest.java:140 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/ResponseMappersTest.java:9 | Remove this unused import 'org.junit.jupiter.params.ParameterizedTest'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/ResponseMappersTest.java:10 | Remove this unused import 'org.junit.jupiter.params.provider.EnumSource'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/ValidationServiceImplTest.java:6 | Remove this unused import 'com.alramz.config.ValidationDefinition'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/ValidationServiceImplTest.java:19 | Remove this unused import 'com.alramz.service.ValidationService'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/ValidationServiceImplTest.java:26 | Remove this unused import 'org.springframework.web.reactive.function.client.WebClient'. |
| java:S1128 | MINOR/LOW | services/onboarding-service/src/test/java/com/alramz/service/impl/ValidationServiceImplTest.java:35 | Remove this unused import 'org.mockito.Mockito.never'. |
| java:S1128 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/config/CacheWarmupRunner.java:3 | Remove this unnecessary import: same package classes are always implicitly imported. |
| java:S1128 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/config/CompanyRedisProperties.java:4 | Remove this unused import 'org.springframework.boot.context.properties.bind.DefaultValue'. |
| java:S1220 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/config/JpaConfig.java:None | Move this file to a named package. |
| java:S112 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/controllers/ApplicationController.java:26 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1130 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/controllers/ApplicationController.java:26 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S1068 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/scheduler/job/CacheReloadScheduledJob.java:20 | Remove this unused "companyRedisProperties" private field. |
| java:S1128 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/service/CacheAuditService.java:10 | Remove this unused import 'java.time.LocalDateTime'. |
| java:S1172 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:62 | Remove this unused method parameter "key". |
| java:S1854 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:71 | Remove this useless assignment to local variable "deleted". |
| java:S1481 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:71 | Remove this unused "deleted" local variable. |
| java:S1141 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:96 | Extract this nested try block into a separate method. |
| java:S1481 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:128 | Remove this unused "lastReloaded" local variable. |
| java:S8688 | INFO/INFO | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:172 | Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. |
| java:S1141 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:184 | Extract this nested try block into a separate method. |
| java:S2077 | MAJOR/LOW,MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:208 | Make sure using a dynamically formatted SQL query is safe here. |
| java:S1192 | CRITICAL/HIGH | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:237 | Define a constant instead of duplicating this literal "mappingName" 3 times. |
| java:S8688 | INFO/INFO | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:239 | Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. |
| java:S1192 | CRITICAL/HIGH | services/reference-data-service/src/main/java/com/alramz/service/SchedulerLockService.java:35 | Define a constant instead of duplicating this literal "jobId" 3 times. |
| java:S8688 | INFO/INFO | services/reference-data-service/src/main/java/com/alramz/service/SchedulerLockService.java:38 | Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. |
| java:S8688 | INFO/INFO | services/reference-data-service/src/main/java/com/alramz/service/SchedulerLockService.java:42 | Explicitly specify the time zone by passing a ZoneId or a Clock to the .now() method. |
| java:S1130 | MINOR/LOW | services/reference-data-service/src/test/java/com/alramz/config/CacheWarmupRunnerTest.java:58 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S112 | MAJOR/MEDIUM | services/reporting-management-service/src/main/java/com/alramz/controllers/ApplicationController.java:25 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1130 | MINOR/LOW | services/reporting-management-service/src/main/java/com/alramz/controllers/ApplicationController.java:25 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S2143 | INFO/INFO | services/reporting-management-service/src/main/java/com/alramz/repository/RelationshipManagerRepositoryImpl.java:None | Use the "java.time" API for date and time. |
| java:S5778 | MAJOR/MEDIUM | services/reporting-management-service/src/test/java/com/alramz/repository/RelationshipManagerRepositoryImplTest.java:219 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/reporting-management-service/src/test/java/com/alramz/repository/RelationshipManagerRepositoryImplTest.java:229 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S6068 | MINOR/LOW | services/reporting-management-service/src/test/java/com/alramz/service/impl/RelationshipManagerServiceImplTest.java:75 | Remove this and every subsequent useless "eq(...)" invocation; pass the values directly. |
| java:S6068 | MINOR/LOW | services/reporting-management-service/src/test/java/com/alramz/service/impl/RelationshipManagerServiceImplTest.java:89 | Remove this and every subsequent useless "eq(...)" invocation; pass the values directly. |
| java:S1168 | MAJOR/MEDIUM | services/utility-service/src/main/java/com/alramz/finoux/FinouxClient.java:123 | Return an empty map instead of null. |
| java:S6878 | MAJOR/MEDIUM | services/utility-service/src/main/java/com/alramz/service/impl/CommunityPostServiceImpl.java:33 | Use the record pattern instead of this pattern match variable. |
| java:S6878 | MAJOR/MEDIUM | services/utility-service/src/main/java/com/alramz/service/impl/CommunityPostServiceImpl.java:34 | Use the record pattern instead of this pattern match variable. |
| java:S6878 | MAJOR/MEDIUM | services/utility-service/src/main/java/com/alramz/service/impl/CommunityPostServiceImpl.java:38 | Use the record pattern instead of this pattern match variable. |
| java:S5838 | MINOR/LOW | services/utility-service/src/test/java/com/alramz/controllers/CommunityPostControllerTest.java:51 | Use assertThat(actual).hasToString(expectedString) instead. |
| java:S5838 | MINOR/LOW | services/utility-service/src/test/java/com/alramz/controllers/CommunityPostControllerTest.java:76 | Use assertThat(actual).hasToString(expectedString) instead. |
| java:S5838 | MINOR/LOW | services/utility-service/src/test/java/com/alramz/exception/GlobalExceptionHandlerTest.java:145 | Use assertThat(actual).hasToString(expectedString) instead. |
| java:S5778 | MAJOR/MEDIUM | services/utility-service/src/test/java/com/alramz/service/impl/CommunityPostServiceImplTest.java:56 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/utility-service/src/test/java/com/alramz/service/impl/CommunityPostServiceImplTest.java:64 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/utility-service/src/test/java/com/alramz/service/impl/CommunityPostServiceImplTest.java:94 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/utility-service/src/test/java/com/alramz/service/impl/CommunityPostServiceImplTest.java:104 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/utility-service/src/test/java/com/alramz/service/impl/CommunityPostServiceImplTest.java:114 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S112 | MAJOR/MEDIUM | services/wealth-management-service/src/main/java/com/alramz/controllers/ApplicationController.java:25 | Replace generic exceptions with specific library exceptions or a custom exception. |
| java:S1130 | MINOR/LOW | services/wealth-management-service/src/main/java/com/alramz/controllers/ApplicationController.java:25 | Remove the declaration of thrown exception 'java.lang.Exception', as it cannot be thrown from method's body. |
| java:S1192 | CRITICAL/HIGH | services/wealth-management-service/src/main/java/com/alramz/exception/ErrorCode.java:97 | Use already-defined constant 'DATE_INVALID_MSG' instead of duplicating its value here. |
| java:S1192 | CRITICAL/HIGH | services/wealth-management-service/src/main/java/com/alramz/exception/ErrorCode.java:119 | Define a constant instead of duplicating this literal "clientId" 3 times. |
| java:S2143 | INFO/INFO | services/wealth-management-service/src/main/java/com/alramz/repository/MmfRepositoryImpl.java:None | Use the "java.time" API for date and time. |
| java:S1192 | CRITICAL/HIGH | services/wealth-management-service/src/main/java/com/alramz/repository/MmfRepositoryImpl.java:56 | Define a constant instead of duplicating this literal "CLIENT_ID" 3 times. |
| java:S3776 | CRITICAL/HIGH | services/wealth-management-service/src/main/java/com/alramz/service/impl/MmfServiceImpl.java:147 | Refactor this method to reduce its Cognitive Complexity from 16 to the 15 allowed. |
| java:S3776 | CRITICAL/HIGH | services/wealth-management-service/src/main/java/com/alramz/service/impl/MmfServiceImpl.java:183 | Refactor this method to reduce its Cognitive Complexity from 27 to the 15 allowed. |
| java:S6068 | MINOR/LOW | services/wealth-management-service/src/test/java/com/alramz/controllers/MmfApiDispatchTest.java:286 | Remove this useless "eq(...)" invocation; pass the values directly. |
| java:S5853 | MINOR/LOW | services/wealth-management-service/src/test/java/com/alramz/exception/ErrorCodeTest.java:112 | Join these multiple assertions subject to one assertion chain. |
| java:S1128 | MINOR/LOW | services/wealth-management-service/src/test/java/com/alramz/repository/MmfRepositoryImplTest.java:42 | Remove this unused import 'org.mockito.Mockito.verify'. |
| java:S1128 | MINOR/LOW | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:3 | Remove this unused import 'ch.qos.logback.classic.Level'. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:126 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:225 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:276 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:300 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:397 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:407 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:418 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:428 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
| java:S5778 | MAJOR/MEDIUM | services/wealth-management-service/src/test/java/com/alramz/service/impl/MmfServiceImplTest.java:466 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
