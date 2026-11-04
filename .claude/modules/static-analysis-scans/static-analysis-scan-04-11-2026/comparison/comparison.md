# Before / after comparison

## Totals

| Tool | Before | After | Delta |
|---|---|---|---|
| checkstyle | 30 | 10 | -20 |
| pmd | 85 | 83 | -2 |
| spotbugs | 16 | 14 | -2 |

## By tool and severity

| Tool | Severity | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | MEDIUM | 30 | 10 | -20 |
| pmd | LOW | 3 | 3 | +0 |
| pmd | MEDIUM | 82 | 80 | -2 |
| spotbugs | LOW | 3 | 2 | -1 |
| spotbugs | MEDIUM | 13 | 12 | -1 |

## By tool and module

| Tool | Module | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | alramz-api-starter | 5 | 5 | +0 |
| checkstyle | alramz-notification-service | 4 | 4 | +0 |
| checkstyle | risk-management-service | 20 | 0 | -20 |
| checkstyle | wealth-management-service | 1 | 1 | +0 |
| pmd | alramz-notification-service | 33 | 33 | +0 |
| pmd | onboarding-service | 20 | 20 | +0 |
| pmd | reference-data-service | 10 | 10 | +0 |
| pmd | risk-management-service | 7 | 5 | -2 |
| pmd | utility-service | 2 | 2 | +0 |
| pmd | wealth-management-service | 13 | 13 | +0 |
| spotbugs | alramz-notification-service | 3 | 3 | +0 |
| spotbugs | onboarding-service | 2 | 2 | +0 |
| spotbugs | risk-management-service | 5 | 3 | -2 |
| spotbugs | utility-service | 6 | 6 | +0 |

## By rule (changed rules only)

| Tool | Rule | Before | After | Delta |
|---|---|---|---|---|
| checkstyle | FileTabCharacter | 20 | 0 | -20 |
| spotbugs | THROWS_METHOD_THROWS_CLAUSE_BASIC_EXCEPTION | 1 | 0 | -1 |
| pmd | SignatureDeclareThrowsException | 1 | 0 | -1 |
| spotbugs | NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | 1 | 0 | -1 |
| pmd | ShortMethodName | 3 | 2 | -1 |

## New findings introduced (0)

| Tool | Rule | Where | Count |
|---|---|---|---|
