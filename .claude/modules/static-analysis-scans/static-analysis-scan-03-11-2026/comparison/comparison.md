# Before / after comparison

## Totals

| Tool | Before | After | Delta |
|---|---|---|---|
| checkstyle | 272 | 10 | -262 |
| pmd | 1883 | 78 | -1805 |
| spotbugs | 276 | 11 | -265 |

## By tool and severity

| Tool | Severity | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | MEDIUM | 272 | 10 | -262 |
| pmd | HIGH | 68 | 0 | -68 |
| pmd | LOW | 6 | 3 | -3 |
| pmd | MEDIUM | 1809 | 75 | -1734 |
| spotbugs | HIGH | 1 | 0 | -1 |
| spotbugs | LOW | 41 | 1 | -40 |
| spotbugs | MEDIUM | 234 | 10 | -224 |

## By tool and module

| Tool | Module | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | alramz-api-starter | 77 | 5 | -72 |
| checkstyle | alramz-notification-service | 6 | 4 | -2 |
| checkstyle | onboarding-service | 95 | 0 | -95 |
| checkstyle | reference-data-service | 30 | 0 | -30 |
| checkstyle | reporting-management-service | 21 | 0 | -21 |
| checkstyle | utility-service | 21 | 0 | -21 |
| checkstyle | wealth-management-service | 22 | 1 | -21 |
| pmd | alramz-notification-service | 252 | 33 | -219 |
| pmd | onboarding-service | 770 | 20 | -750 |
| pmd | reference-data-service | 288 | 10 | -278 |
| pmd | reporting-management-service | 109 | 0 | -109 |
| pmd | utility-service | 115 | 2 | -113 |
| pmd | wealth-management-service | 349 | 13 | -336 |
| spotbugs | alramz-notification-service | 53 | 3 | -50 |
| spotbugs | onboarding-service | 140 | 2 | -138 |
| spotbugs | reference-data-service | 30 | 0 | -30 |
| spotbugs | reporting-management-service | 14 | 0 | -14 |
| spotbugs | utility-service | 14 | 6 | -8 |
| spotbugs | wealth-management-service | 25 | 0 | -25 |

## By rule (changed rules only)

| Tool | Rule | Before | After | Delta |
|---|---|---|---|---|
| pmd | MethodArgumentCouldBeFinal | 620 | 0 | -620 |
| pmd | LocalVariableCouldBeFinal | 433 | 0 | -433 |
| pmd | LongVariable | 217 | 0 | -217 |
| checkstyle | FileTabCharacter | 162 | 0 | -162 |
| pmd | OnlyOneReturn | 146 | 0 | -146 |
| spotbugs | EI_EXPOSE_REP2 | 148 | 4 | -144 |
| pmd | ShortVariable | 95 | 0 | -95 |
| spotbugs | EI_EXPOSE_REP | 68 | 6 | -62 |
| pmd | GuardLogStatement | 56 | 0 | -56 |
| pmd | AtLeastOneConstructor | 46 | 0 | -46 |
| pmd | CommentDefaultAccessModifier | 41 | 0 | -41 |
| checkstyle | ConstantName | 39 | 0 | -39 |
| checkstyle | NewlineAtEndOfFile | 31 | 0 | -31 |
| spotbugs | NM_CONFUSING | 30 | 0 | -30 |
| pmd | LawOfDemeter | 24 | 0 | -24 |
| pmd | AvoidCatchingGenericException | 23 | 0 | -23 |
| pmd | PreserveStackTrace | 22 | 6 | -16 |
| pmd | FieldDeclarationsShouldBeAtStartOfClass | 14 | 0 | -14 |
| pmd | MissingSerialVersionUID | 13 | 0 | -13 |
| checkstyle | AvoidStarImport | 13 | 0 | -13 |
| spotbugs | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | 13 | 0 | -13 |
| pmd | AvoidLiteralsInIfCondition | 12 | 0 | -12 |
| pmd | FieldNamingConventions | 12 | 0 | -12 |
| spotbugs | THROWS_METHOD_THROWS_RUNTIMEEXCEPTION | 8 | 1 | -7 |
| pmd | AvoidDuplicateLiterals | 7 | 0 | -7 |
| pmd | UnusedAssignment | 7 | 0 | -7 |
| checkstyle | LineLength | 5 | 0 | -5 |
| checkstyle | UnusedImports | 5 | 0 | -5 |
| checkstyle | RegexpSingleline | 4 | 0 | -4 |
| spotbugs | CT_CONSTRUCTOR_THROW | 4 | 0 | -4 |
| pmd | ControlStatementBraces | 4 | 0 | -4 |
| spotbugs | SIC_INNER_SHOULD_BE_STATIC_ANON | 3 | 0 | -3 |
| pmd | AvoidFieldNameMatchingMethodName | 5 | 3 | -2 |
| pmd | UseVarargs | 2 | 0 | -2 |
| pmd | AvoidInstanceofChecksInCatchClause | 2 | 0 | -2 |
| pmd | CloseResource | 2 | 0 | -2 |
| pmd | UseExplicitTypes | 2 | 0 | -2 |
| pmd | AvoidDeeplyNestedIfStmts | 2 | 0 | -2 |
| pmd | NullAssignment | 3 | 2 | -1 |
| pmd | CompareObjectsWithEquals | 1 | 0 | -1 |
| checkstyle | NPathComplexity | 8 | 7 | -1 |
| checkstyle | BooleanExpressionComplexity | 1 | 0 | -1 |
| pmd | ExceptionAsFlowControl | 6 | 5 | -1 |
| pmd | UseDiamondOperator | 1 | 0 | -1 |
| pmd | UseUnderscoresInNumericLiterals | 1 | 0 | -1 |
| spotbugs | RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE | 1 | 0 | -1 |
| pmd | ShortMethodName | 3 | 2 | -1 |
| pmd | UnnecessaryImport | 1 | 0 | -1 |
| checkstyle | RedundantModifier | 1 | 0 | -1 |
| spotbugs | BX_UNBOXING_IMMEDIATELY_REBOXED | 1 | 0 | -1 |

## New findings introduced (0)

| Tool | Rule | Where | Count |
|---|---|---|---|
