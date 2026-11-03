# utility-service - Sonar code-smell remediation

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| finoux/FinouxClient.java | S1168 (1) | `firstRow` returned `null` map | returns `Map.of()`; caller's `row.get("post_ID") == null` check already covers it | `mvn -B -pl services/utility-service test` |
| service/impl/CommunityPostServiceImpl.java | S6878 (3) | type patterns on records `Posted`, `Rejected`, `Unavailable` | record deconstruction patterns | same |
| test controllers/CommunityPostControllerTest.java | S5838 (2) | `x.toString()).isEqualTo(..)` | `hasToString(..)` | same |
| test exception/GlobalExceptionHandlerTest.java | S5838 (1) | same | `hasToString(..)` | same |
| test service/impl/CommunityPostServiceImplTest.java | S5778 (5) | request built inside `assertThatThrownBy` lambda | hoisted to local `postRequest` | same |

Result: Tests run: 36, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
None.
