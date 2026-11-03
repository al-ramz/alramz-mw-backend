# reporting-management-service - Sonar code-smell remediation

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| controllers/ApplicationController.java | S112, S1130 (2) | `info()` declared `throws Exception` never thrown | removed `throws` clause | `mvn -B -pl services/reporting-management-service test` |
| repository/RelationshipManagerRepositoryImpl.java | S2143 (1) | `java.sql.Date` used for bind values | bind `LocalDate` directly (JDBC 4.2 / ojdbc11); log-literal renderer now matches `LocalDate` | same |
| test repository/RelationshipManagerRepositoryImplTest.java | S5778 (2) | `LocalDate.now()` calls inside `assertThatThrownBy` lambda | hoisted to a local `today` | same |
| test service/impl/RelationshipManagerServiceImplTest.java | S6068 (2) | all-`eq(...)` verify args | passed values directly | same |

Result: Tests run: 33, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
None.
