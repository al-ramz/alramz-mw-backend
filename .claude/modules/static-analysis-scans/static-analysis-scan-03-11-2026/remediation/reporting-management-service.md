# reporting-management-service — static-analysis remediation

## Fixed

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| exception/RelationshipManagerLookupException.java, InvalidDateRangeException.java | (enabler for PreserveStackTrace) | only `(String message)` ctor | added `(String message, Throwable cause)` ctor | lets callers chain the cause; message unchanged |
| repository/RelationshipManagerRepositoryImpl.java:61,64,92,95 | PreserveStackTrace x4 | `throw new RelationshipManagerLookupException("Unable to retrieve ...");` | `throw new RelationshipManagerLookupException("Unable to retrieve ...", e);` | keep the root cause. Client-facing message unchanged |
| repository/RelationshipManagerRepositoryImpl.java:105 | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `for (String name : params.getParameterNames()) ... params.getValue(name)` | `for (Map.Entry<String, Object> param : params.getValues().entrySet()) ... param.getValue()` | `getParameterNames()` is nullable-annotated; `getValues()` is the backing LinkedHashMap (non-null, same order). Log-only rendering is unchanged |
| service/impl/RelationshipManagerServiceImpl.java:82 | PreserveStackTrace | `throw new InvalidDateRangeException(fieldName + ": must be ...");` | `throw new InvalidDateRangeException(fieldName + ": must be ...", e);` | keep the DateTimeParseException cause |

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| — | — | none |

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| — | — | 0 | — |

## Verification

- Command: `rsync repo -> snap-small (excl. target/, .git/); cd snap-small && mvn -B -o -pl services/alramz-api-starter,services/utility-service,services/reporting-management-service,services/wealth-management-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true`
- Exit code: 0 (BUILD SUCCESS)
- Tests: 33 run, 0 failures, 0 errors, 0 skipped
- Remaining: Checkstyle 0, PMD 0, SpotBugs 0
