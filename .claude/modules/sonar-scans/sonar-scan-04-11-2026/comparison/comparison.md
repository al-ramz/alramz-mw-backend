# SonarQube before/after comparison

Quality gate: OK → **OK**

| Issues | Count |
|---|---|
| before | 38 |
| after | 33 |
| fixed | 5 |
| pending | 33 |
| new | 0 |
| changed | 0 |
| hotspots_before | 0 |
| hotspots_after | 0 |
| hotspots_to_review_after | 0 |

| Metric | Before | After |
|---|---|---|
| alert_status | OK | OK |
| blocker_violations | 0 | 0 |
| bugs | 0 | 0 |
| code_smells | 38 | 33 |
| coverage | 77.6 | 77.6 |
| critical_violations | 0 | 0 |
| duplicated_lines_density | 4.1 | 4.1 |
| info_violations | 2 | 2 |
| line_coverage | 77.4 | 77.4 |
| major_violations | 29 | 26 |
| minor_violations | 7 | 5 |
| ncloc | 19126 | 19127 |
| new_violations | n/a | n/a |
| reliability_rating | A | A |
| security_hotspots | 0 | 0 |
| security_hotspots_reviewed | 100.0 | 100.0 |
| security_rating | A | A |
| security_review_rating | A | A |
| software_quality_maintainability_issues | 38 | 33 |
| software_quality_reliability_issues | 0 | 0 |
| software_quality_security_issues | 0 | 0 |
| sqale_index | 475 | 420 |
| sqale_rating | A | A |
| violations | 38 | 33 |
| vulnerabilities | 0 | 0 |

## New (0)

| Rule | Sev | File:Line | Message |
|---|---|---|---|

## Pending (33)

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
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java:43 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java:43 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java:18 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java:18 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java:21 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S1075 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:27 | Refactor your code to get this URI from a customizable parameter. |
| java:S1075 | MINOR/LOW | services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java:28 | Refactor your code to get this URI from a customizable parameter. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java:45 | Remove this call to a deprecated method, it has been marked for removal. |
| java:S5738 | MAJOR/MEDIUM | services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java:45 | Remove this call to a deprecated class, it has been marked for removal. |
| java:S2139 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:175 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S2147 | MINOR/LOW | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:178 | Combine this catch with the one at line 175, which has the same body. |
| java:S2139 | MAJOR/MEDIUM | services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java:178 | Either log this exception and handle it, or rethrow it with some contextual information. |
| java:S1075 | MINOR/LOW | services/risk-management-service/src/main/java/com/alramz/knowyourcountry/KnowYourCountryClient.java:35 | Refactor your code to get this URI from a customizable parameter. |
| java:S1075 | MINOR/LOW | services/risk-management-service/src/main/java/com/alramz/knowyourcountry/KnowYourCountryClient.java:36 | Refactor your code to get this URI from a customizable parameter. |

## Changed (0)

| Rule | Sev | File:Line | Message |
|---|---|---|---|

## Fixed (5)

| Rule | Sev | File:Line | Message |
|---|---|---|---|
| java:S1075 | MINOR/LOW | services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java:42 | Refactor your code to get this URI from a customizable parameter. |
| java:S1075 | MINOR/LOW | services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java:43 | Refactor your code to get this URI from a customizable parameter. |
| java:S6878 | MAJOR/MEDIUM | services/risk-management-service/src/main/java/com/alramz/service/RiskComplianceService.java:93 | Use the record pattern instead of this pattern match variable. |
| java:S6878 | MAJOR/MEDIUM | services/risk-management-service/src/main/java/com/alramz/service/RiskComplianceService.java:96 | Use the record pattern instead of this pattern match variable. |
| java:S5778 | MAJOR/MEDIUM | services/risk-management-service/src/test/java/com/alramz/knowyourcountry/KnowYourCountryClientTest.java:247 | Refactor the code of the lambda to have only one invocation possibly throwing a runtime exception. |
