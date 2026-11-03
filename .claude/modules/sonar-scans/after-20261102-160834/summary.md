# SonarQube snapshot — alramzit_alramz-mw-backend

Captured: 2026-11-02T16:00:58+0530  
Quality gate: **OK**

| Metric | Value |
|---|---|
| alert_status | OK |
| blocker_violations | 0 |
| bugs | 0 |
| code_smells | 29 |
| coverage | 76.0 |
| critical_violations | 0 |
| duplicated_lines_density | 4.1 |
| info_violations | 2 |
| line_coverage | 75.5 |
| major_violations | 25 |
| minor_violations | 2 |
| ncloc | 18006 |
| new_violations | n/a |
| reliability_rating | A |
| security_hotspots | 0 |
| security_hotspots_reviewed | 100.0 |
| security_rating | A |
| security_review_rating | A |
| software_quality_maintainability_issues | 29 |
| software_quality_reliability_issues | 0 |
| software_quality_security_issues | 0 |
| sqale_index | 350 |
| sqale_rating | A |
| violations | 29 |
| vulnerabilities | 0 |

## Quality gate conditions

| Metric | Status | Actual | Threshold |
|---|---|---|---|
| new_reliability_rating | OK | 1 | GT 1 |
| new_security_rating | OK | 1 | GT 1 |
| new_maintainability_rating | OK | 1 | GT 1 |
| new_coverage | OK | 81.0 | LT 80 |
| new_duplicated_lines_density | OK | 1.1 | GT 3 |
| new_security_hotspots_reviewed | OK | 100.0 | LT 100 |

## Open issues: 29

By type: {"CODE_SMELL": 29}

By legacy severity: {"MAJOR": 25, "INFO": 2, "MINOR": 2}

By impact severity: {"MEDIUM": 25, "INFO": 2, "LOW": 2}

### By rule

| Rule | Count |
|---|---|
| java:S1172 | 10 |
| java:S5738 | 10 |
| java:S2139 | 4 |
| java:S2143 | 2 |
| java:S1075 | 2 |
| java:S107 | 1 |

### By file

| File | Count |
|---|---|
| services/alramz-api-starter/src/test/java/com/alramz/audit/ApiAuditAspectTest.java | 3 |
| services/alramz-api-starter/src/main/java/com/alramz/globalconfigurationsettings/repository/JdbcGlobalConfigurationSettingsRepository.java | 3 |
| services/onboarding-service/src/test/java/com/alramz/config/ETradeTokenProviderTest.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalETradeServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalIbanServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/ExternalVeriPhoneServiceConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/config/WebClientConfig.java | 2 |
| services/onboarding-service/src/main/java/com/alramz/exception/GlobalExceptionHandler.java | 2 |
| services/alramz-api-starter/src/main/java/com/alramz/exceptions/ApiCallFailedException.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/jwt/service/TokenProvider.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/scheduler/model/ScheduleInfoBean.java | 1 |
| services/alramz-api-starter/src/main/java/com/alramz/utils/DataValidationUtil.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/client/AuditProbeClient.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/controllers/AuditProbeController.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/logging/MethodExecutionLoggingAspectTest.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/DuplicateCheckServiceImpl.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/IBANValidationServiceImpl.java | 1 |
| services/alramz-api-starter/src/test/java/com/alramz/service/impl/PhoneValidationServiceImpl.java | 1 |
| services/reference-data-service/src/main/java/com/alramz/service/RedisCacheService.java | 1 |

## Security hotspots: 0

By status: {}

| Rule | File:Line | Probability | Status |
|---|---|---|---|
