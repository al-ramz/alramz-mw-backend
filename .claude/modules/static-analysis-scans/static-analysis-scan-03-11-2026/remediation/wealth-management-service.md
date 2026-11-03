# wealth-management-service — static-analysis remediation

## Fixed

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| controllers/MmfController.java:96 | ShortMethodName | `private static ResponseEntity<GenericResponse> ok(Object payload)` | `okResponse(Object payload)` (+ 6 call sites) | private helper rename |
| exception/ErrorCode.java:90,129,130 | FieldDeclarationsShouldBeAtStartOfClass x3 | `private record Rule(...)` before `RULES`; `code`/`defaultMessage` fields after the static block | `RULES`, `code`, `defaultMessage` declared before `record Rule` and the static initializer | reorder only. Static init order is unchanged (`RULES` is still initialised before the static block) |
| exception/GlobalExceptionHandler.java:247 | AvoidLiteralsInIfCondition | `if ("NotNull".equals(constraint))` / `"Size".equals(constraint)` | `NOT_NULL_CONSTRAINT.equals(constraint)` / `SIZE_CONSTRAINT.equals(...)` | named Bean Validation constraint constants |
| exception/GlobalExceptionHandler.java:253 | UseVarargs | `private static String lastCode(String[] codes)` | `lastCode(String... codes)` | private, existing array callers unaffected |
| repository/MmfRepositoryImpl.java:63 | AvoidLiteralsInIfCondition | `if (rows.size() > 1)` | `if (rows.size() > SINGLE_ROW)` | named constant |
| repository/MmfRepositoryImpl.java:161 | ExceptionAsFlowControl | `try { sql = load(); if (sql == null) throw new MmfBackendException(...); return sql; } catch (IOException e) {...}` | `try { sql = load(); } catch (IOException e) {...}` then the null check outside the try | the throw is no longer inside a try. Behaviour is identical (MmfBackendException was never caught by the IOException catch) |

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| — | — | none |

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| exception/ErrorCode.java | ExcessivePublicCount | 1 | enum of ~50 public error-code constants by design (API contract) |
| exception/ErrorCode.java | AvoidFieldNameMatchingMethodName (`code`, `defaultMessage`) | 2 | record-style public accessors; renaming is a public API change |
| exception/GlobalExceptionHandler.java | CouplingBetweenObjects, ExcessiveImports, ClassFanOutComplexity (CS), GodClass, TooManyMethods | 5 | central @RestControllerAdvice mapping every exception type; splitting needs multiple advices with ordering, a design change |
| exception/GlobalExceptionHandler.java:166 | CognitiveComplexity (15) | 1 | handleNotReadable branch refactor; deferred per policy |
| service/impl/MmfServiceImpl.java | CouplingBetweenObjects, ExcessiveImports, GodClass, TooManyMethods | 4 | split per sub-domain (account / sweeps / profit / custodian) services: a structural refactor |
| service/impl/MmfServiceImpl.java:99 | CyclomaticComplexity (10) | 1 | listSweeps date-defaulting/validation extraction; deferred per policy |

## Verification

- Command: `rsync repo -> snap-small (excl. target/, .git/); cd snap-small && mvn -B -o -pl services/alramz-api-starter,services/utility-service,services/reporting-management-service,services/wealth-management-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true`
- Exit code: 0 (BUILD SUCCESS)
- Tests: 191 run, 0 failures, 0 errors, 0 skipped
- Remaining: Checkstyle 1 (ClassFanOutComplexity, deferred), PMD 13 (all deferred structural rules above), SpotBugs 0
