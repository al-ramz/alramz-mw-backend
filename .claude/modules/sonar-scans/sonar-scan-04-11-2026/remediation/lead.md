# Sonar remediation log: 04-11-2026 (scope: risk-management-service)

Baseline `../before/` (taken after the security, lint and coverage steps): gate OK, ratings A/A/A/A, 38 open code smells, 7 of them in risk-management-service.

| File | Rule | Problem | Fix | Test run |
|---|---|---|---|---|
| services/risk-management-service/src/main/java/com/alramz/service/RiskComplianceService.java:93,96 | java:S6878 | Type pattern + accessor call on `Success` / `Unavailable` records | Record deconstruction patterns `Success(var rows)`, `Unavailable(var cause)` | `mvn -pl services/risk-management-service -am clean verify` (snapshot): 101 tests, 0 failures |
| services/risk-management-service/src/test/java/com/alramz/knowyourcountry/KnowYourCountryClientTest.java:247 | java:S5778 | `() -> success.rows().clear()`: two calls that can throw inside `assertThatThrownBy` | Take `success.rows()` first, assert on `copy::clear`; the assertion is unchanged | same |
| services/risk-management-service/src/main/java/com/alramz/controllers/RiskComplianceLegacyController.java:42,43 | java:S1075 | `"/api/v1/countries"` / `"/api/v1/countryRatings"` literals duplicated the generated route mappings and could drift | Built from the generated `RiskComplianceLegacyApi.PATH_GET_COUNTRIES_LEGACY` / `PATH_GET_COUNTRY_RATINGS_LEGACY` plus one `BASE_PATH` constant shared with the class `@RequestMapping` | same; Legacy/FrameworkExceptionHandler tests cover `isLegacyRequest` / `legacyInternalError` |
| services/risk-management-service/src/main/java/com/alramz/knowyourcountry/KnowYourCountryClient.java:35,36 | java:S1075 | Vendor endpoint paths `/api/v4/countries_list`, `/api/v4/country_ratings` | **Not changed**, see `pending-reasons.md` | n/a |
