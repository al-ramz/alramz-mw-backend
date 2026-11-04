# risk-management-service: static-analysis remediation (04-11-2026)

Queue: `queue/risk-management-service.tsv` (32 findings: Checkstyle 20, PMD 7, SpotBugs 5). No ruleset or filter change.

## Fixed (24)

| File | Rule(s) | Before | After |
|---|---|---|---|
| RiskManagementServiceApplication.java | Checkstyle FileTabCharacter ×20 | tab indentation | 4-space indentation (`expand -t 4`); whitespace-only diff |
| controllers/ApplicationController.java:25 | PMD SignatureDeclareThrowsException, SpotBugs THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION | `public Map<String, Object> info() throws Exception {` | `public Map<String, Object> info() {` (the body throws nothing checked; matches the other services' ApplicationController) |
| controllers/RiskComplianceController.java:38 | PMD ShortMethodName | `private static ResponseEntity<GenericResponse> ok(Object payload)` | `okResponse(Object payload)` (+2 call sites); same fix as MmfController in the 03-11 run |
| controllers/RiskComplianceLegacyController.java:129 | SpotBugs NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `fromValue(rating.getRiskLevel().getValue())` | `fromValue(Objects.requireNonNull(rating.getRiskLevel(), "riskLevel").getValue())`. The service always sets riskLevel, and a null still ends in the route's catch as the legacy "500"; the failure now names the field |

## Suppressed

None.

## Deferred (8)

| File | Rule | Count | Reason |
|---|---|---|---|
| exception/ErrorCode.java (`status`, `message`) | PMD AvoidFieldNameMatchingMethodName | 2 | Record-style accessors on an enum, used by GlobalExceptionHandler and tests; same decision as ErrorCode in the 03-11 run |
| knowyourcountry/RiskBand.java (`rating`, `code`) | PMD AvoidFieldNameMatchingMethodName | 2 | Same record-style accessor convention (`band.rating()`, `band.code()`) |
| knowyourcountry/KnowYourCountryClient.java:74 `classify` | PMD CyclomaticComplexity (10, threshold 10) | 1 | Flat outcome classification (Rejected / Malformed / Unavailable / rethrow); splitting is a refactor, deferred by policy |
| knowyourcountry/KnowYourCountryClient.java:89 | SpotBugs THROWS_METHOD_THROWS_RUNTIMEEXCEPTION (Low) | 1 | Intentional rethrow of an unclassified failure, so RiskComplianceService maps it to xxx004 |
| knowyourcountry/KnowYourCountryResult.java:26 `Unavailable(Throwable cause)` | SpotBugs EI_EXPOSE_REP, EI_EXPOSE_REP2 | 2 | A Throwable cannot be defensively copied; carrying the cause is the point. Same as onboarding `DfmOnboardingOutcome.Unavailable` (03-11 run). No filter entry added |

## Verification

- `mvn -B -pl services/risk-management-service -am clean test` on a snapshot: BUILD SUCCESS, 101 tests, 0 failures.
- After-scan: risk-management-service 32 → 8, 0 new findings (`comparison/comparison.md`).
