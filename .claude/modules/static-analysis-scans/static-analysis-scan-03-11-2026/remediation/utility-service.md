# utility-service — static-analysis remediation

## Fixed

| File | Rule(s) | Before | After | Why |
|---|---|---|---|---|
| finoux/FinouxClient.java:63 | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | `if (request.getTagSymbol() != null && !request.getTagSymbol().isBlank())` | `String tagSymbol = request.getTagSymbol(); if (tagSymbol != null && !tagSymbol.isBlank())` | getter called twice; read once into a local |
| finoux/FinouxData.java | EI_EXPOSE_REP, EI_EXPOSE_REP2 | `public record FinouxData(List<Map<String, Object>> table) {}` | explicit canonical ctor: `this.table = table == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(table));` | defensive, null-element-safe copy. Explicit (not compact) ctor because the compact form triggers PMD UnusedAssignment/NullAssignment false positives. Null -> empty list is equivalent for the only reader (`FinouxClient` checks `table() == null \|\| isEmpty()`). Jackson deserialization verified in a scratch run (`{"table":[{"a":1},null]}` -> `[{a=1}, null]`; `{}` -> `[]`) |
| finoux/FinouxInsertPostDataRequest.java | EI_EXPOSE_REP, EI_EXPOSE_REP2 | `record FinouxInsertPostDataRequest(..., List<FinouxParam> param) {}` | explicit canonical ctor copying `param` into an unmodifiable list | defensive copy; serialization verified unchanged: `{"METHOD":"M","Version":"1","PARAM":[{"Key":"k","Value":"v"}]}` |
| service/impl/CommunityPostServiceImpl.java | UseUnderscoresInNumericLiterals | `MAX_POST_DESC_LENGTH = 10000;` | `MAX_POST_DESC_LENGTH = 10_000;` | readability |

## Suppressed (justified)

| File:method | Rule | Reason |
|---|---|---|
| — | — | none (no SpotBugs annotation dependency on the classpath, and config/pom are out of scope) |

## Deferred

| File | Rule | Count | Reason / what it would take |
|---|---|---|---|
| finoux/FinouxResult.java (Posted, Unavailable) | EI_EXPOSE_REP, EI_EXPOSE_REP2 | 4 | False positive by design: `Posted` hands the generated, mutable `CommunityPost` DTO to the caller, and `Unavailable` carries the exception cause. Neither can or should be copied. Fix belongs in `config/spotbugs-exclude.xml` (e.g. match `com.alramz.finoux.FinouxResult$*`), or in `spotbugs-annotations` + `@SuppressFBWarnings`. Both are outside this agent's scope (no config/pom edits) |
| config/FinouxProperties.java:30 | ShortClassName (`Post`) | 1 | nested @ConfigurationProperties type, public, used as `FinouxProperties.Post`; renaming is a public-member rename |
| exception/ErrorCode.java:19 | AvoidFieldNameMatchingMethodName (`code`/`code()`) | 1 | record-style public accessor; renaming changes the public API used by handlers/tests |

## Verification

- Command: `rsync repo -> snap-small (excl. target/, .git/); cd snap-small && mvn -B -o -pl services/alramz-api-starter,services/utility-service,services/reporting-management-service,services/wealth-management-service clean verify -Dpmd.skip=false -Dspotbugs.skip=false -Djacoco.skip=true`
- Exit code: 0 (BUILD SUCCESS)
- Tests: 36 run, 0 failures, 0 errors, 0 skipped
- Remaining: Checkstyle 0, PMD 2 (ShortClassName, AvoidFieldNameMatchingMethodName, both deferred), SpotBugs 4 (FinouxResult, deferred)
