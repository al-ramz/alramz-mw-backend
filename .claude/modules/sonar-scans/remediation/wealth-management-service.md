# wealth-management-service - Sonar code-smell remediation

| file | rules fixed (count) | original problem | fix applied | tests run |
|---|---|---|---|---|
| controllers/ApplicationController.java | S112, S1130 (2) | `info()` declared `throws Exception` never thrown | removed `throws` clause | `mvn -B -pl services/wealth-management-service test` |
| exception/ErrorCode.java | S1192 (2) | literal duplicating `DATE_INVALID_MSG`; `"clientId"` x3 | reuse `DATE_INVALID_MSG`; `CLIENT_ID` constant | same |
| repository/MmfRepositoryImpl.java | S2143, S1192 (2) | `java.sql.Date` binds/reads; `"CLIENT_ID"` x3 | bind `LocalDate`, read with `rs.getObject(col, LocalDate.class)`; `CLIENT_ID` constant | same |
| service/impl/MmfServiceImpl.java | S3776 (2) | `validateAndConfirm` (16) and `createProfitDistributions` (27) too complex | extracted `rejectConflicts`, `confirmBook`, `validateProfitItems`, `distribute`; logic unchanged, covered by the 7 `confirm_*` and 12 `profit_*` tests | same |
| test controllers/MmfApiDispatchTest.java | S6068 (1) | lone `eq(...)` | value passed directly; unused `eq` import removed | same |
| test exception/ErrorCodeTest.java | S5853 (1) | two assertions on `codes` | chained | same |
| test repository/MmfRepositoryImplTest.java | S1128 (1) | unused `verify` import | removed; date mocks/expectations moved to `LocalDate` to follow the S2143 change | same |
| test service/impl/MmfServiceImplTest.java | S1128, S5778 x9 (10) | unused `Level` import; arguments built inside `assertThatThrownBy` lambdas | removed import; hoisted arguments to locals | same |

Result: Tests run: 191, Failures: 0, Errors: 0, Skipped: 0 - BUILD SUCCESS

## Skipped
None. (identified-gaps.md and apiCollection.http untouched.)
