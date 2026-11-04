# SonarQube snapshot — alramzit_alramz-mw-backend

Captured: 2026-11-04T17:34:20+0530  
Quality gate: **OK**

| Metric | Value |
|---|---|
| alert_status | OK |
| blocker_violations | 0 |
| bugs | 0 |
| code_smells | 33 |
| coverage | 77.6 |
| critical_violations | 0 |
| duplicated_lines_density | 4.1 |
| info_violations | 2 |
| line_coverage | 77.4 |
| major_violations | 26 |
| minor_violations | 5 |
| ncloc | 19127 |
| new_violations | n/a |
| reliability_rating | A |
| security_hotspots | 0 |
| security_hotspots_reviewed | 100.0 |
| security_rating | A |
| security_review_rating | A |
| software_quality_maintainability_issues | 33 |
| software_quality_reliability_issues | 0 |
| software_quality_security_issues | 0 |
| sqale_index | 420 |
| sqale_rating | A |
| violations | 33 |
| vulnerabilities | 0 |

## Quality gate conditions

| Metric | Status | Actual | Threshold |
|---|---|---|---|
| new_reliability_rating | OK | 1 | GT 1 |
| new_security_rating | OK | 1 | GT 1 |
| new_maintainability_rating | OK | 1 | GT 1 |
| new_coverage | OK | 80.4 | LT 80 |
| new_duplicated_lines_density | OK | 2.0 | GT 3 |
| new_security_hotspots_reviewed | OK | 100.0 | LT 100 |

## Open issues: 33

By type: {"CODE_SMELL": 33}

By legacy severity: {"MAJOR": 26, "MINOR": 5, "INFO": 2}

By impact severity: {"MEDIUM": 26, "LOW": 5, "INFO": 2}

### By rule

| Rule | Count |
|---|---|
| java:S5738 | 11 |
| java:S1172 | 9 |
| java:S2139 | 5 |
| java:S1075 | 4 |
| java:S2143 | 2 |
| java:S2147 | 1 |
| java:S107 | 1 |

### By file

| File | Count |
|---|---|
| services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java | 3 |
| services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java | 3 |
| services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java | 3 |
| services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java | 3 |
| services/risk-management-service/src/main/java/com/alramz/knowyourcountry/KnowYourCountryClient.java | 2 |
| services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalETradeServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalIbanServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/service/TokenProvider.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/model/ScheduleInfoBean.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/utils/DataValidationUtil.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/client/AuditProbeClient.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/controllers/AuditProbeController.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImpl.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/IBANValidationServiceImpl.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/PhoneValidationServiceImpl.java | 1 |

## Security hotspots: 0

By status: {}

| Rule | File:Line | Probability | Status |
|---|---|---|---|
