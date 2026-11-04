# Sonar remediation changelog: 04-11-2026

From `remediation/lead.md`. Cross-checked: `git diff --name-only` shows no tracked file changed by this run; every edit is inside the untracked `services/risk-management-service/`.

| File | Rule | Change |
|---|---|---|
| services/risk-management-service/src/main/java/com/alramz/service/RiskComplianceService.java | java:S6878 ×2 | Record deconstruction patterns in the result switch |
| services/risk-management-service/src/test/java/com/alramz/knowyourcountry/KnowYourCountryClientTest.java | java:S5778 | One throwing call inside `assertThatThrownBy` |
| services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java | java:S1075 ×2 | Legacy route matcher built from the generated `PATH_*` constants plus a shared `BASE_PATH` |

Earlier steps in this pass (static analysis) also changed `RiskManagementServiceApplication.java`, `ApplicationController.java`, `RiskComplianceController.java` and `RiskComplianceLegacyController.java`;
see `.claude/modules/static-analysis-scans/static-analysis-scan-04-11-2026/CHANGELOG.md`.
