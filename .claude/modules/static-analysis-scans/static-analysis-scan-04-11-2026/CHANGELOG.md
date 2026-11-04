# Changelog: static analysis 04-11-2026

Fix scope: risk-management-service. No config, ruleset or filter changes (`config/`, BOM untouched).

| File | Rule(s) | Before → after | Why |
|---|---|---|---|
| services/risk-management-service/src/main/java/com/alramz/RiskManagementServiceApplication.java | Checkstyle FileTabCharacter ×20 | tabs → 4 spaces | Mechanical; whitespace-only diff |
| services/risk-management-service/src/main/java/com/alramz/controllers/ApplicationController.java | PMD SignatureDeclareThrowsException; SpotBugs THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION | `info() throws Exception` → `info()` | Body throws nothing checked |
| services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceController.java | PMD ShortMethodName | `ok(Object)` → `okResponse(Object)` | Private helper rename |
| services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java | SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `rating.getRiskLevel().getValue()` → `Objects.requireNonNull(rating.getRiskLevel(), "riskLevel").getValue()` | Explicit null contract; same outcome (legacy "500") on null |
