# Spec: risk-compliance-endpoints

Stage: `01_elicit` (re-run, 2026-11-04) · Target service: `services/risk-management-service` only.

**Provenance tags** used throughout. The original request said not to invent business rules, so
every rule carries one of these:
- `[DOC §x]`: `intake-source-riskcompliance-webmethods.md` (legacy webMethods RiskCompliance doc), section x.
- `[REPO path]`: an existing file in this repo.
- `[USER Dn]`: a user decision from the decisions log (§7).
- `[DEFAULT]`: a repo standard default from `.claude/howtos/new-endpoint-elicitation-checklist.md`.
- `[ASSUMPTION]`: a residual, non-blocking call made by this stage (listed in §9).

---

## 1. Feature Objective & Scope

**Problem.** The webMethods `RiskCompliance` package has two parameterless read APIs,
`countriesList` and `countriesRatings`. Both proxy the KnowYourCountry vendor and return country
identity data and a 3-band risk classification `[DOC §1.1, §1.3]`. They must move to
`risk-management-service` (Spring Boot) without inventing new business rules. The legacy package
hardcodes the vendor key (F-01), drops unknown risk levels silently (F-02) and has no timeouts (F-07)
`[DOC App. D]`.

**Deliverables** (all in `services/risk-management-service`):
1. **New contract (GET)**: `GET /api/v1/countries` and `GET /api/v1/country-ratings`. These use real
   HTTP statuses, `GenericResponse`, an integer `rating`, the `OK` reason phrase, `correlationId`, and an
   empty array when there is no data `[DOC §4, §5; USER D2]`.
2. **Legacy-compatible contract (POST)**: `POST /api/v1/countries` and `POST /api/v1/countryRatings`.
   These return the exact legacy wire shape (always HTTP 200, `correlationID`, string `rating`, inner
   codes `200`/`1018`/`500`). APIM rewrites `/gateway/RiskCompliance/1.0/*` to them `[USER D2, D3]`.
3. A KnowYourCountry vendor client on the starter's `AbstractRestClient`, with config-driven base
   URL, API key and timeout, circuit breaker, and one retry `[DOC §6.1, F-07; REPO utility-service FinouxClient]`.
4. Band mapping in code: a fixed enum with the 5 exact, case-sensitive labels `[DOC §5.3; USER D4]`.
5. Unknown or missing `risk_level`: omit the country, log a WARN and increment a Micrometer counter `[USER D1]`.

**Trigger:** synchronous HTTP only. There is no scheduler and no events.

**Out of scope:**
- Caching. It stays OFF until Compliance agrees a staleness window `[DOC Open item 5]`.
- A KnowYourCountry readiness/health indicator. The doc makes it optional (indicator only) `[DOC §3.3]`;
  add it when ops asks for it.
- UUID-format validation of `X-Correlation-ID`. It is a starter-wide change to `CorrelationIdFilter`
  and is recorded as a conflict in §5 and §7.
- APIM policy and the path rewrite, Key Vault secret provisioning, k8s secret/env wiring, and rotating
  the leaked vendor key (F-01, an ops action outside this repo).
- The `KYC_COUNTRY_RISK_UPDATE_NOTIFICATION` email template `[DOC §1.2, Open item 6]`.
- Any new table, entity or Liquibase changeset `[DOC App. C]`.
- Unused vendor fields: `score`, `score_direction`, `risk_colour`, `scoring_profile`, `override` `[DOC §6.1]`.
- New datasources, new `@AutoConfiguration`, or changes to `alramz-api-starter`.

---

## 2. Repository Grounding

| Concern | Existing artifact this spec builds on | Use |
|---|---|---|
| Contract | `services/risk-management-service/specs/apiSpecs.yaml` | **Amend.** Add 4 operations and new schemas. Extend `GenericResponse` (today it has only 3 fields: `responseCode`, `responseMessage`, `response`) with `correlationId` (`format: uuid`), `errorCode` and `errorMsg` (nullable), exactly as in `services/utility-service/specs/apiSpecs.yaml`. Leave the existing `/api/v1/info` entry unchanged. |
| Codegen | `services/risk-management-service/pom.xml` (`openapi-generator-maven-plugin` 7.23.0, `interfaceOnly`, `useTags`, `useResponseEntity`, `apiPackage com.alramz.api`, `modelPackage com.alramz.model`) | Tags `RiskCompliance` and `RiskComplianceLegacy` generate `RiskComplianceApi` and `RiskComplianceLegacyApi`. Generated models carry explicit `@JsonProperty` (see `target/generated-sources/.../GenericResponse.java`), so the snake_case `JacksonConfig` does not affect them. |
| Controller pattern | `services/utility-service/.../controllers/CommunityPostController.java` | `@RestController @RequestMapping("/api/v1")` implements the generated interface, with `@JwtSecured(roles=...)` and `@Loggable`. It sets `correlationId` from `MDCUtil.getCorrelationIdAsUuid()`. YAML paths are written bare. |
| Exception handler | `services/utility-service/.../exception/GlobalExceptionHandler.java` (`@RestControllerAdvice(basePackages="com.alramz.controllers") @Order(0)`, `buildResponse(status, errorCode, errorMsg)`, explicit `AccessDeniedException`→403, catch-all→500) and `ErrorCode.java` | Copy into `com.alramz.exception` of the risk service. It has none today. |
| 502 precedent | `services/alramz-notification-service/.../exception/GlobalExceptionHandler.java` (`EML009`/`SMS009`/`OTP009` → `BAD_GATEWAY`); `services/onboarding-service/.../DfmInvestorExceptionHandler.java` (`DFN010` → 502, `DFN014` → 503) | Vendor non-200 maps to 502. Note: utility maps Finoux failures to 500 (`FNX011`). This spec follows the 502 precedent `[DOC §6.1]`. |
| Vendor client | `services/alramz-api-starter/.../client/AbstractRestClient.java`; `services/utility-service/.../finoux/FinouxClient.java`, `FinouxResult.java` (sealed result), `config/ExternalFinouxServiceConfig.java`, `config/FinouxProperties.java` | Same structure: `@Qualifier`-named WebClient, CircuitBreaker, Retry and TimeLimiter beans, plus a sealed result type. Resilience4j comes in through the starter (`spring-cloud-starter-circuitbreaker-reactor-resilience4j`), so the pom needs no change (utility-service has the same dependency set). |
| Correlation ID | `alramz-api-starter/.../logging/filter/CorrelationIdFilter.java` (reuses a non-blank `X-Correlation-ID`, otherwise a random UUID; no format check); `.../logging/advice/CorrelationIdResponseBodyAdvice.java` (reflectively calls `setCorrelationId(String\|UUID)` on any body; swallows all failures); `.../logging/util/MDCUtil.getCorrelationIdAsUuid()` (returns a **fresh** UUID if the MDC value is not a UUID) | Used unchanged. §3.3 explains how the legacy body avoids the advice. |
| JWT | `alramz-api-starter/.../jwt/annotation/JwtSecured.java`; `JwtSecuredAspect` (exact match on `ROLE_<role>`); `company.jwt.permit-all-urls` in `application-{dev,docker,test,preprod,prod}.yml` (only `/api/v1/info` today) | All 4 routes are secured with `APP_RISK_MANAGEMENT`. `permit-all-urls` is not changed. |
| Masking | `alramz-api-starter/.../audit/SensitiveDataMasker.java` (keys `authorization`, `api_key`, `apikey`, ...), `LoggingProperties` masked headers (`Authorization`, `X-API-Key`) | They already cover `Authorization: ApiKey ...`. No additions. |
| Config | `src/main/resources/application.yml` (base), `application-{dev,docker,test,preprod,prod}.yml`, `src/test/resources/application.yml` (what `@SpringBootTest` boots; it does not activate a profile) | New top-level `knowyourcountry.*` block in every profile file and in the test resources file `[DOC App. C]`. |
| Jackson | `services/risk-management-service/.../config/JacksonConfig.java` (a Jackson 2 `ObjectMapper`, SNAKE_CASE). utility's identical class notes that it is used only by audit logging and `AbstractRestClient` error parsing, while HTTP uses Boot's own Jackson | The vendor DTO uses explicit `@JsonProperty` and `@JsonIgnoreProperties(ignoreUnknown = true)`, so it does not depend on either mapper's naming strategy. |
| Persistence | `src/main/resources/db/changelog/{dev,docker,preprod,prod}/db.changelog-master.yaml`, `src/test/resources/db/changelog/test/sql/{001-api-audit-log,002-jwt-tables}.sql`, the `company.datasource.middleware` block | Unchanged. The middleware datasource stays only for the starter JWT and audit tables. |
| Role in API collection | `services/risk-management-service/apiCollection.http` registers `APP_RISK_MANAGEMENT_SERVICE_USER` | **Conflict.** It must change to `APP_RISK_MANAGEMENT` to match `@JwtSecured` (§5). |
| Sizing | `services/risk-management-service/service.yaml` (0.5 CPU / 1Gi, 1–5 replicas, `ingress.external: true`) | Sizing is fine for two light proxy GETs. The ingress note is in §5. |

---

## 3. Interface Contracts

**Contract file:** amend `services/risk-management-service/specs/apiSpecs.yaml`. It is not a new
file. Paths are bare, and the controllers add `/api/v1` `[REPO utility-service pattern]`. The
existing `security: - BearerAuth: []` at the spec root applies.

### 3.1 Shared facts (all 4 operations)

| Fact | Value | Provenance |
|---|---|---|
| Security | JWT-secured, `@JwtSecured(roles = "APP_RISK_MANAGEMENT")`, not in `permit-all-urls` | `[DEFAULT]` secured-by-default; role name from the previous run, accepted by the user |
| Request params | None. GET has no body. Legacy POST has an optional body (§3.3) | `[DOC §4.2, §5.2]` |
| Headers | `Authorization: Bearer <jwt>` is required. `X-Correlation-ID` is optional and read by the starter `CorrelationIdFilter`, which echoes it back in the response header | `[REPO CorrelationIdFilter]` |
| Vendor call | `GET {knowyourcountry.base-url}/api/v3/countries_list` or `/api/v3/country_ratings`, header `Authorization: ApiKey <key>` | `[DOC §6.1]` |
| Field mapping | `name`→`name`, `iso_alpha2`→`iso2`, `iso_alpha3`→`iso3`. Ratings also map `risk_level`→(`rating`, `riskLevel`) using the band table. Values pass through unvalidated (no ISO check) | `[DOC §7.1, §7.2, §4.5 Open item 7]` |
| Ordering | Vendor array order is preserved | `[DOC App. A]` (loop order) |

**Band table** (in code: an exact, case-sensitive `String` match with no trimming) `[DOC §5.3; USER D4]`:

| Vendor `risk_level` | `rating` | `riskLevel` |
|---|---|---|
| `Higher Risk`, `Medium-High Risk` | 20 | `H` |
| `Medium Risk`, `Medium-Low Risk` | 9 | `M` |
| `Lower Risk` | 1 | `L` |
| anything else, `null`, or missing | the country is omitted, with a WARN and a counter increment (§5) | `[USER D1]` |

### 3.2 New contract: GET routes (tag `RiskCompliance`)

| | Countries | Country ratings |
|---|---|---|
| Verb + bare path | `GET /countries` | `GET /country-ratings` |
| operationId | `getCountries` | `getCountryRatings` |
| 200 `response` schema | `CountryListResponse { countryList: Country[] }` | `CountryRatingResponse { countryRating: CountryRating[] }` |
| Error-code prefix | `CLS` | `CRT` |

**Envelope:** `GenericResponse` `[DEFAULT; REPO utility-service]`. Its fields are `responseCode`
(the HTTP status as a string), `responseMessage` (the HTTP reason phrase), `errorCode`, `errorMsg`,
`correlationId` (UUID) and `response`. The spelling is `correlationId`, not the doc's
`correlationID`, to match `CorrelationIdResponseBodyAdvice` `[USER D2 / previous-run default]`.
When there is no data, the response holds an empty array (`countryList: []` or `countryRating: []`)
`[DOC §4.5, §5.5; USER D2]`.

**Schemas to add:**
```yaml
GenericResponse:            # extended: add the three properties below to the existing 3
  correlationId: { type: string, format: uuid }
  errorCode:     { type: string, nullable: true }
  errorMsg:      { type: string, nullable: true }
Country:              { name: string, iso2: string, iso3: string }          # iso2/iso3: described as ISO 3166-1 alpha-2/3, no pattern
CountryListResponse:  { countryList: array of Country }
CountryRating:        { name: string, iso2: string, iso3: string,
                        rating: { type: integer, format: int32, description: "20 | 9 | 1" },   # no OpenAPI enum: avoids generated NUMBER_20 enum
                        riskLevel: { type: string, enum: [H, M, L] } }
CountryRatingResponse:{ countryRating: array of CountryRating }
```

**Status codes and examples** (every row also needs a matching example in `apiSpecs.yaml`):

| Status | When | `responseCode`/`responseMessage` | `errorCode` (countries / ratings) | `errorMsg` | Provenance |
|---|---|---|---|---|---|
| 200 | vendor 200 + JSON array (possibly empty) | `"200"` / `"OK"` | `null` | `null` | `[DOC §4.7, §5.7]` |
| 401 | missing/invalid JWT (starter filter, before controller) | description only | — | — | `[REPO utility apiSpecs 401]` |
| 403 | JWT lacks `APP_RISK_MANAGEMENT` | `"403"` / `"Forbidden"` | `null` | `"Access denied"` | `[REPO utility GlobalExceptionHandler]` |
| 500 | unexpected failure | `"500"` / `"Internal Server Error"` | `CLS004` / `CRT004` | `"Internal Server Error"` | `[DOC §4.6.2, §5.6.2; USER D5]` |
| 502 | vendor non-2xx status | `"502"` / `"Bad Gateway"` | `CLS001` / `CRT001` | `"Country data provider returned an error"` | `[DOC §6.1; USER D5]` |
| 502 | vendor 2xx with an empty, unparsable or non-array body | `"502"` / `"Bad Gateway"` | `CLS002` / `CRT002` | `"Country data provider returned an invalid response"` | `[DOC §4.3(a), F-04; USER D5]` |
| 503 | vendor timeout, connection error, or circuit open | `"503"` / `"Service Unavailable"` | `CLS003` / `CRT003` | `"Country data provider unavailable"` | `[DOC §6.1; USER D5]` |

No `400` row: the GET routes take no input `[DOC §4.6.1]`. `405` (for example `PUT`) is produced
by the framework. The `ErrorResponse` handler in §6 keeps that as a 405 instead of letting the
catch-all turn it into a 500.

Example 200 (ratings):
```json
{ "responseCode": "200", "responseMessage": "OK", "errorCode": null, "errorMsg": null,
  "correlationId": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "response": { "countryRating": [
    { "name": "Afghanistan", "iso2": "AF", "iso3": "AFG", "rating": 20, "riskLevel": "H" },
    { "name": "Åland Islands", "iso2": "AX", "iso3": "ALA", "rating": 1, "riskLevel": "L" } ] } }
```
Example 503 (countries):
```json
{ "responseCode": "503", "responseMessage": "Service Unavailable", "errorCode": "CLS003",
  "errorMsg": "Country data provider unavailable",
  "correlationId": "a3eac7fd-8693-468a-b194-48a90ce9a3b7", "response": null }
```
The 200 (countries, including the empty-array case), 403, 500 and 502 (both codes) examples follow
the same shape with the values from the table.

### 3.3 Legacy-compatible contract: POST routes (tag `RiskComplianceLegacy`, `deprecated: true`)

| | Countries | Country ratings |
|---|---|---|
| Verb + bare path | `POST /countries` | `POST /countryRatings` |
| operationId | `getCountriesLegacy` | `getCountryRatingsLegacy` |
| Failure message on vendor non-2xx | `Failed to get countrylist` | `Failed to get countryRating` |
| 200 body schema | `LegacyCountryListEnvelope` | `LegacyCountryRatingEnvelope` |

`POST /countries` shares its path with `GET /countries`. Spring routes the two by verb.

**Request** (optional, `requestBody.required: false`):
`LegacyRiskComplianceRequest { correlationID: string (optional) }`. This stands in for the legacy
undeclared pipeline input `[DOC §4.2, §5.2; ASSUMPTION A1]`. An absent body, an empty body, or a
body with no `Content-Type` are all accepted.

**Correlation ID rule** `[DOC §4.3 step 1; USER D3]`:
- If the body's `correlationID` contains at least one non-space character (legacy regex `/[^ ]/`),
  use that exact value.
- Otherwise use the `CorrelationIdFilter` value from MDC. That is the caller's non-blank
  `X-Correlation-ID`, or a generated UUID.

When the body value is used, the controller also calls `MDCUtil.putCorrelationId(value)` so that
later log lines carry it.

**Response: always HTTP 200** `[USER D3]`. Body:
`{ correlationID, responseCode, responseMessage, response? }`. There is no `errorCode` and no
`errorMsg`.

| Outcome | `responseCode` | `responseMessage` | `response` |
|---|---|---|---|
| Vendor 200 with ≥1 output row | `"200"` | `"success"` | `{ "countryList": [...] }` / `{ "countryRating": [...] }` |
| Vendor 200 with 0 output rows (empty array, or every rating omitted under D1) | `"200"` | `"success"` | **omitted** `[USER D3; ASSUMPTION A2]` |
| Vendor non-2xx (`CLS001`/`CRT001`) | `"1018"` | `Failed to get countrylist` / `Failed to get countryRating` | omitted |
| Anything else (`xxx002`, `xxx003`, `xxx004`, an unreadable body or unsupported media type) | `"500"` | `"Internal Server Error"` | omitted |

Legacy item schemas:
- `countryList[]` reuses `Country` (`name`, `iso2`, `iso3`), which is unchanged on the wire.
- `countryRating[]` uses a new `LegacyCountryRating { name, iso2, iso3, rating: string ("20"|"9"|"1"),
  riskLevel: string enum [H,M,L] }` `[DOC §5.5, App. A]`.

**How null fields are omitted:** the legacy envelope schemas carry
`x-class-extra-annotation: "@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)"`.
Because of that, a null `response` is left out of the body, not written as `null`. If generator
7.23.0 does not emit this annotation, the fallback is to hand-write records for the two envelopes
with the same annotation (§9, A3).

**How the legacy shape bypasses the starter advice:** `CorrelationIdResponseBodyAdvice.supports()`
returns `true` for every body. It then looks up `setCorrelationId(String)` or
`setCorrelationId(UUID)` through `Class.getMethod`, which is case-sensitive. The legacy envelope's
property `correlationID` generates the setter `setCorrelationID`, which does not match, so the
advice leaves the body unchanged. With the record fallback there are no setters at all, so the
result is the same. The starter needs no change and no exclusion. The legacy controller sets
`correlationID` itself, using the rule above.

Example (ratings, success):
```json
{ "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7", "responseCode": "200", "responseMessage": "success",
  "response": { "countryRating": [ { "name": "Afghanistan", "iso2": "AF", "iso3": "AFG", "rating": "20", "riskLevel": "H" } ] } }
```
Example (countries, vendor non-2xx):
```json
{ "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7", "responseCode": "1018", "responseMessage": "Failed to get countrylist" }
```
The YAML also documents `401` and `403`. These come from the starter JWT filter or aspect, so they
are **not** in the legacy shape (see §5, conflict C2).

### 3.4 Java components (package-by-feature, mirroring utility-service)

| Class | Package | Responsibility |
|---|---|---|
| `KnowYourCountryProperties` | `com.alramz.config` | `@Validated @ConfigurationProperties("knowyourcountry")`: `@NotBlank baseUrl`, `@NotBlank apiKey`, `Duration requestTimeout = 30s` |
| `KnowYourCountryConfig` | `com.alramz.config` | Beans: `@Bean("knowYourCountryWebClient")`, built with **(1)** an `ExchangeFilterFunction` that sets `Authorization: ApiKey <key>`, replacing every existing value, and then **(2)** `alramzWebClientLoggingFilterFunction`. Also `knowYourCountryCircuitBreaker` (`registry.circuitBreaker("knowyourcountry")`), `knowYourCountryRetry` (`maxAttempts(2)`; retries only `WebClientRequestException`, or `ApiCallFailedException` with status ≥ 500), and `knowYourCountryTimeLimiter` (`requestTimeout`) |
| `KnowYourCountryRecord` | `com.alramz.knowyourcountry` | `@JsonIgnoreProperties(ignoreUnknown=true) record(@JsonProperty("name") String name, @JsonProperty("iso_alpha2") String isoAlpha2, @JsonProperty("iso_alpha3") String isoAlpha3, @JsonProperty("risk_level") String riskLevel)` |
| `KnowYourCountryResult` | `com.alramz.knowyourcountry` | `sealed interface`: `Success(List<KnowYourCountryRecord> rows)`, `Rejected(int httpStatus)`, `Malformed(String detail)`, `Unavailable(Throwable cause)` (the internal typed result from `[DOC §2.4]`) |
| `KnowYourCountryClient extends AbstractRestClient` | `com.alramz.knowyourcountry` | `KnowYourCountryResult fetchCountries()` and `KnowYourCountryResult fetchCountryRatings()` (see the note below the table) |
| `RiskBand` | `com.alramz.knowyourcountry` | `enum { HIGH(20,"H"), MEDIUM(9,"M"), LOW(1,"L"); static Optional<RiskBand> fromVendorLabel(String label) }`: a `switch` on the 5 literals, with every other value returning `Optional.empty()` |
| `RiskComplianceService` | `com.alramz.service` | `List<Country> getCountries()` and `List<CountryRating> getCountryRatings()` (see the note below the table) |
| `ErrorCode` | `com.alramz.exception` | `CLS001..004`, `CRT001..004`, each with `HttpStatus status()`, `String code()` and `String message()` (§3.2 table) |
| `RiskComplianceException extends RuntimeException` | `com.alramz.exception` | Carries an `ErrorCode`. It is the only typed failure in the service |
| `GlobalExceptionHandler` | `com.alramz.exception` | Copy of the utility handler, with the changes listed in §6 |
| `RiskComplianceController implements RiskComplianceApi` | `com.alramz.controllers` | `@RequestMapping("/api/v1")`, `@JwtSecured(roles="APP_RISK_MANAGEMENT")` and `@Loggable` on each method. Builds a `GenericResponse` with 200 / `"OK"`, `correlationId = MDCUtil.getCorrelationIdAsUuid()`, and `response` set to the list wrapper |
| `RiskComplianceLegacyController implements RiskComplianceLegacyApi` | `com.alramz.controllers` | Same annotations. Calls the same service methods and catches `RiskComplianceException`: `xxx001` gives `1018`, any other code gives `500`. Also catches `RuntimeException`, which gives `500`. Converts `rating` with `String.valueOf`. Has a controller-local `@ExceptionHandler({HttpMessageNotReadableException.class, HttpMediaTypeNotSupportedException.class})` that returns the legacy `500` body with HTTP 200 |

**`KnowYourCountryClient`:**
- The call is `call(GET, path, Void.class, null, KnowYourCountryRecord[].class, null, null).block()`.
- Exceptions are unwrapped with `reactor.core.Exceptions.unwrap` and then classified:
  - `ApiCallFailedException` → `Rejected`.
  - `DecodingException`, or a `null` body → `Malformed`.
  - `TimeoutException`, `WebClientRequestException` or `CallNotPermittedException` → `Unavailable`.
  - Anything else is rethrown.
- Null array elements are dropped and logged at WARN.

**`RiskComplianceService`:**
- Maps `Success` to generated models.
- Throws `RiskComplianceException(xxx001|002|003)` for the three failure results.
- Wraps any other `RuntimeException` as `xxx004`.
- Applies D1 to ratings: an unmapped label means WARN plus a counter increment, and the row is skipped.
- Logs one INFO line per call with the result count. It never logs the vendor body `[DOC F-06, §7.3]`.

**Why the auth filter is needed:** `AbstractRestClient.execute` forwards the **inbound** caller's
JWT as `Authorization: Bearer ...` whenever the profile is not `local`. It also only adds custom
headers outside the `local` profile, and it uses `addAll`. Passing the API key as a per-call header
would therefore leak the caller's JWT to the vendor and send two `Authorization` values. A filter
on the dedicated WebClient that *sets* the header avoids both problems without a starter change
`[REPO AbstractRestClient.java lines 63–72]`.

---

## 4. Data Model & Persistence Changes

**None.**
- There are no new entities, tables or Liquibase changesets `[DOC App. C]`.
- The `company.datasource.middleware` datasource and the existing changelog masters
  (`db/changelog/{dev,docker,preprod,prod}/db.changelog-master.yaml`, plus the test changelog with
  `001-api-audit-log.sql` and `002-jwt-tables.sql`) stay as they are. They hold the starter
  JWT/audit tables `[previous-run default]`.
- No `JdbcTemplate` or `DataSource` injection is introduced, so no `@Qualifier` decision arises.
- Vendor data is never persisted. The only persisted trace is the starter's `api_audit_log`
  request/response audit (§5, note N3).

---

## 5. Security & Config Impact

**JWT / permit-all.** None of the routes are added to `permit-all-urls`. All 4 stay authenticated
with role `APP_RISK_MANAGEMENT` `[DEFAULT; USER D3]`.

**New config.** A top-level `knowyourcountry` prefix `[DOC App. C]`. The utility precedent uses
`adapter.finoux`; the doc and the previous-run default chose `knowyourcountry.*`.

| Key | dev / docker / preprod / prod (`application-<profile>.yml`) | `application-test.yml` (main) and `src/test/resources/application.yml` |
|---|---|---|
| `knowyourcountry.base-url` | `${KNOWYOURCOUNTRY_BASE_URL:https://api.knowyourcountry.com}` (a public vendor host, not a secret) | `https://knowyourcountry-test.example.invalid` |
| `knowyourcountry.api-key` | `${KNOWYOURCOUNTRY_API_KEY}`, with **no default** in any profile, so startup fails fast through `@NotBlank` and the unresolved placeholder | a dummy non-secret literal, for example `test-api-key` |
| `knowyourcountry.request-timeout` | `30s`. This is the TimeLimiter bound for the whole call, including the retry | `2s` |

- The key must come from Key Vault or the environment. Provisioning it is outside this repo.
- The leaked legacy key must **not** be copied anywhere. It must be rotated (F-01, ops).
- There is no resilience4j yml block. Circuit breaker defaults come from the registry, as for Finoux.

**Masking.** No `SensitiveDataMasker` additions. The `authorization`, `api_key` and `apikey` keys
and the `Authorization` header mask already cover the vendor header. Vendor data (country names,
ISO codes, bands) is not sensitive.

**Logging.**
- The D1 WARN carries the vendor label and `iso2` as log placeholders, never concatenated.
- The vendor response body is never logged by service code (F-06).
- **Metric:** `risk.compliance.country.rating.unmapped`, a Micrometer `Counter` with no label tag,
  which avoids unbounded cardinality. The label is in the WARN line instead. The common
  `application` tag comes from `management.metrics.tags`.

**Conflicts and notes, recorded (not resolved by this spec):**
- **C1. Correlation-ID format** `[DOC §3.4, §4.2]`. The doc wants `X-Correlation-ID` validated as a
  UUID, with a new one generated if it is invalid, to prevent log injection.
  - `CorrelationIdFilter` accepts any non-blank value.
  - For the GET routes, `MDCUtil.getCorrelationIdAsUuid()` returns a *fresh* UUID for a non-UUID
    header, so the body `correlationId` can differ from the response header and the logs.
  - Fixing this is a starter-wide change and is out of scope.
  - The legacy body `correlationID` is echoed verbatim and put in MDC. Its log-injection exposure is
    the same as the existing header path.
- **C2. Legacy routes are JWT-secured.** Today `crm` and `Middleware` authenticate only with
  `x-Gateway-APIKey` at the gateway `[DOC §3.4]`. Nothing in the repo argues against securing the
  routes, so they stay secured `[USER D3]`. However, APIM or the consumers must now present a JWT
  with `APP_RISK_MANAGEMENT`, and a 401 or 403 on these routes is the starter's shape, not the
  legacy always-200 shape. The APIM policy is outside this repo.
- **C3. Role name mismatch.** `apiCollection.http` registers `APP_RISK_MANAGEMENT_SERVICE_USER`.
  `JwtSecuredAspect` matches roles exactly, so the collection must register `APP_RISK_MANAGEMENT`.
  This file is updated as part of the feature.
- **N1. Ingress.** `service.yaml` has `ingress.external: true`, while the doc's target is "reachable
  only from APIM" `[DOC §3.4]`. Not changed here; for ops/infra review.
- **N2. Vendor quota.** Every call reaches the vendor (no cache). The `service.yaml` sizing is
  adequate, but vendor rate limits are unknown `[DOC Open item 7]`.
- **N3. Audit volume.** `company.logging.database-logging.include-response-payload: true` stores
  the full country list in `api_audit_log` on every call, and the starter WebClient audit filter
  does the same for the outbound call. This is a volume concern, not a sensitivity one (F-06 spirit).
  Left unchanged. Revisit with `excluded-paths` if the table grows.

---

## 6. Edge Cases & Error Behaviors

`GlobalExceptionHandler` (new, copied from utility) has these handlers:
- `RiskComplianceException` → `buildResponse(code.status(), code, code.message())`.
- `AccessDeniedException` → 403.
- **`org.springframework.web.ErrorResponse`** → `ex.getStatusCode()` with its reason phrase and
  `errorCode` null. This covers 405, 415 and 404 so they do not become 500.
- Catch-all `Exception` → 500 with `errorCode` null and `errorMsg` `"Internal Server Error"`.
- It needs no `MethodArgumentNotValidException` or `ApplicationException` handlers, because there is
  no validated input.

| # | Failure mode | Detected in | Typed outcome | GET routes | Legacy POST routes |
|---|---|---|---|---|---|
| E1 | Vendor 4xx/5xx | `ApiCallFailedException` → `Rejected` | `RiskComplianceException(CLS001/CRT001)` | 502, `CLS001`/`CRT001` | 200, `1018` + the endpoint's message |
| E2 | Vendor 2xx with an empty body, invalid JSON, a JSON object instead of an array, or a 2xx/3xx other than 200 that decodes to nothing | `DecodingException` or `null` → `Malformed` | `xxx002` | 502, `CLS002`/`CRT002` | 200, `500` / `Internal Server Error` `[ASSUMPTION A4]` |
| E3 | Timeout (`knowyourcountry.request-timeout`), connection refused/reset, DNS failure | `TimeoutException` / `WebClientRequestException` → `Unavailable` (after 1 retry for connection errors and 5xx) | `xxx003` | 503, `CLS003`/`CRT003` | 200, `500` |
| E4 | Circuit breaker open | `CallNotPermittedException` → `Unavailable` | `xxx003` | 503 | 200, `500` |
| E5 | Any other runtime failure in client, service or mapping | rethrown, then wrapped by the service | `xxx004` | 500, `CLS004`/`CRT004` | 200, `500` |
| E6 | Vendor 200 with `[]` | `Success([])` | — | 200, empty array | 200, `200`/`success`, `response` omitted |
| E7 | `risk_level` unknown, wrong case (for example `higher risk`), has whitespace, is `null`, or is missing | `RiskBand.fromVendorLabel` → empty | WARN `Unmapped KnowYourCountry risk_level '{}' for iso2 '{}'; country omitted` + counter `risk.compliance.country.rating.unmapped` | 200, country absent | 200, country absent |
| E8 | Every rating unmapped | as E7 for each row | — | 200, `countryRating: []` | 200, `response` omitted |
| E9 | `null` array element | client drops it with a WARN | — | 200, element absent | 200, element absent |
| E10 | Non-ISO `iso2`/`iso3` or a missing `name` | not validated `[DOC §4.5]` | — | passed through | passed through |
| E11 | Missing or invalid JWT | starter JWT filter | — | 401 (starter body) | 401 (starter body, not legacy) |
| E12 | JWT without `APP_RISK_MANAGEMENT` | `JwtSecuredAspect` → `AccessDeniedException` | — | 403 `GenericResponse`, `"Access denied"` | 403 `GenericResponse` (not legacy) |
| E13 | Unsupported verb (for example `PUT /api/v1/countries`) | framework `HttpRequestMethodNotSupportedException` (`ErrorResponse`) | — | 405 `GenericResponse` | 405 |
| E14 | Legacy POST with malformed JSON or an unsupported `Content-Type` with a body | `HttpMessageNotReadableException` / `HttpMediaTypeNotSupportedException` | legacy controller-local handler | n/a | 200, `500` / `Internal Server Error` |
| E15 | Legacy body `correlationID` blank or only spaces | fallback rule | — | n/a | `correlationID` = MDC value (the header or a generated UUID) |
| E16 | `knowyourcountry.api-key` or `base-url` missing or blank | `@Validated` properties at startup | context fails to start | — | — |
| E17 | Non-UUID `X-Correlation-ID` on a GET | `MDCUtil.getCorrelationIdAsUuid()` | — | 200/5xx with a fresh UUID in the body (C1) | n/a (the string is echoed) |

---

## 7. Open Questions & Decisions

### 7.1 Decision log (blocking gaps answered by the user in the main session, 2026-11-04)

| ID | Question | Options considered | Decision |
|---|---|---|---|
| D1 | Unknown or missing `risk_level` (F-02, Open item 2) | Omit + alert (doc recommendation) / fail-closed as `H`/20 / keep the silent legacy omission | **Omit the country**, log a WARN naming the label and `iso2`, increment a Micrometer counter. The response stays 200. This applies to both the GET and POST ratings routes. |
| D2 | Breaking changes (Appendix E) | New contract only / legacy shape only / both | **Both.** The GET routes use the new contract (real statuses, integer `rating`, `OK`, `correlationId`, empty array). Legacy-compatible POST routes are kept as well. |
| D3 | Legacy POST wire shape | Exact legacy / new envelope on POST | **Exact legacy.** Always HTTP 200. Body `{correlationID, responseCode, responseMessage, response}` with codes `200`/`success`, `1018`/`Failed to get countrylist\|countryRating`, `500`/`Internal Server Error`. String `rating`. `response` omitted when there is no data or on failure. No `errorCode` or `errorMsg`. Paths `POST /api/v1/countries` and `POST /api/v1/countryRatings`, with an APIM rewrite from `/gateway/RiskCompliance/1.0/*`. The caller's correlation ID is reused if non-blank, otherwise one is generated. JWT-secured unless the repo gives a reason otherwise (none was found; see conflict C2). |
| D4 | Where the band mapping lives (the doc suggests config, §5.3(b)) | yml config / code | **Code**: a fixed enum/switch with the 5 exact, case-sensitive labels. |
| D5 | `errorCode`/`errorMsg` on GET failures (the doc says always null) | null, per the doc / populated, per repo precedent | **Populated**: `CLS001-004` / `CRT001-004` in `errorCode`, with a generic `errorMsg`, following the `FNX`/`EML`/`DFN` precedent. |

Previous-run repo defaults, re-verified in this run and kept: the `GenericResponse` envelope and
`correlationId` spelling; starter JWT with `APP_RISK_MANAGEMENT`; middleware DB and Liquibase for
starter tables only; an `AbstractRestClient` vendor client with one retry and config timeouts; the
`knowyourcountry.*` prefix with an env/Key Vault key and no default; caching OFF; the starter
`CorrelationIdFilter` unchanged; vendor non-200 → 502; bare YAML paths.

No new blocking gap was found in this run.

### 7.2 Still open, non-blocking (not decided here)

- **Open item 1:** the error-code registry check for `1018` → 502. 502 is applied from repo precedent.
- **Open item 3:** the live gateway path spelling (`RiskCompliance` vs. `RiskComplaince`, F-09) and
  whether Release v75.0 reached PROD. This affects the APIM rewrite source path only.
- **Open item 4:** consumer confirmation (`crm`, `Middleware`), the GET path names, and the
  `CLS`/`CRT` prefixes. It is partly de-risked by the legacy POST routes.
- **Open item 6:** the owner of the `KYC_COUNTRY_RISK_UPDATE_NOTIFICATION` email template.
- **Open item 7:** the vendor specification: the full `risk_level` set, ISO conformance, field types
  and rate limits.
- **Open item 8:** effective IS ACLs and the internals of `commonUtility`/`SEQDatalust`.
- **Open item 5:** the caching staleness window. Caching stays OFF until it is agreed.
- **F-01:** rotate the leaked KnowYourCountry key. This is an ops action outside this repo, and a
  prerequisite before go-live.
- **Conflicts C1–C3 and notes N1–N3** from §5.

---

## 8. Explicit Acceptance Criteria

**Contract and codegen**
- [ ] `services/risk-management-service/specs/apiSpecs.yaml` is amended, not replaced.
  - [ ] `GenericResponse` gains `correlationId` (uuid), `errorCode` and `errorMsg` (nullable).
  - [ ] The schemas `Country`, `CountryListResponse`, `CountryRating`, `CountryRatingResponse`, `LegacyRiskComplianceRequest`, `LegacyCountryRating`, `LegacyCountryListEnvelope` and `LegacyCountryRatingEnvelope` are added.
  - [ ] Bare paths are used: `/countries` (get + post), `/country-ratings` (get) and `/countryRatings` (post).
  - [ ] The operationIds are `getCountries`, `getCountryRatings`, `getCountriesLegacy` and `getCountryRatingsLegacy`.
  - [ ] Each GET has a filled example for 200, 403, 500, 502 (both codes) and 503, plus a 401 description.
  - [ ] Each POST has filled 200 examples (success, empty, `1018`, `500`) and 401/403 descriptions.
  - [ ] The existing `/api/v1/info` entry is unchanged.
- [ ] `mvn -pl services/risk-management-service -am clean test-compile` succeeds, and the generated
  `com.alramz.api.RiskComplianceApi` and `RiskComplianceLegacyApi` are implemented by
  `RiskComplianceController` and `RiskComplianceLegacyController` (both `@RequestMapping("/api/v1")`).

**Security**
- [ ] All 4 methods carry `@JwtSecured(roles = "APP_RISK_MANAGEMENT")`.
- [ ] `permit-all-urls` is unchanged in every profile.
- [ ] A request without a JWT gets 401. A JWT without the role gets 403 with the `GenericResponse` body `"Access denied"`.
- [ ] `apiCollection.http` registers `APP_RISK_MANAGEMENT` and has one request for each of the 4 routes.

**Behaviour, verified by tests that use only the test dependencies already in `pom.xml`**
- [ ] `RiskBand.fromVendorLabel` returns `HIGH(20,"H")` for `Higher Risk` and `Medium-High Risk`, `MEDIUM(9,"M")` for `Medium Risk` and `Medium-Low Risk`, and `LOW(1,"L")` for `Lower Risk`. It returns empty for `null`, `""`, `higher risk`, `Higher Risk ` and `Low Risk`.
- [ ] `GET /api/v1/countries` with a vendor 200 array returns HTTP 200, `responseCode "200"`, `responseMessage "OK"`, null `errorCode`/`errorMsg`, a UUID `correlationId`, and `response.countryList[]` mapped as `name`/`iso2`/`iso3` in vendor order.
- [ ] `GET /api/v1/country-ratings` returns an integer `rating` and `riskLevel` H/M/L per the band table. An unmapped row is absent, one WARN is logged, and `risk.compliance.country.rating.unmapped` increases by 1 for each unmapped row.
- [ ] A vendor `[]` gives HTTP 200 with `countryList: []` (GET) or `countryRating: []` (GET).
- [ ] The GET routes return 502 + `CLS001`/`CRT001` for a vendor 4xx/5xx, 502 + `CLS002`/`CRT002` for a non-array or invalid body, 503 + `CLS003`/`CRT003` for a timeout or connection error, and 500 + `CLS004`/`CRT004` for an unexpected exception. Each has the generic `errorMsg` from §3.2 and `response: null`.
- [ ] `POST /api/v1/countries` and `POST /api/v1/countryRatings` always return HTTP 200 for every vendor outcome (E1–E10, E14), with the body exactly per §3.3:
  - [ ] the property is spelled `correlationID`
  - [ ] there are no `errorCode`/`errorMsg` properties
  - [ ] `rating` is a JSON string
  - [ ] `responseMessage` is `"success"` on success
  - [ ] `"1018"` comes with `Failed to get countrylist` or `Failed to get countryRating`
  - [ ] `"500"` comes with `Internal Server Error`
  - [ ] the `response` property is absent (not `null`) when there are no rows and on any failure
- [ ] The legacy body `{"correlationID":"abc-123"}` is echoed as `correlationID: "abc-123"`. An absent, empty, or all-spaces value results in the `X-Correlation-ID` header value (or a generated UUID if there is no header).
- [ ] The legacy body is not modified by `CorrelationIdResponseBodyAdvice`, and no `correlationId` property appears.
- [ ] Every outbound vendor request has exactly one `Authorization` header, equal to `ApiKey <configured key>`. No inbound bearer token is forwarded.
- [ ] A connection error is retried exactly once. A vendor 4xx and a decoding error are not retried.
- [ ] `PUT /api/v1/countries` returns 405, not 500.

**Config**
- [ ] `knowyourcountry.base-url`, `knowyourcountry.api-key` and `knowyourcountry.request-timeout` are present in `application-{dev,docker,test,preprod,prod}.yml` and `src/test/resources/application.yml`.
- [ ] No real key is committed. Non-test profiles use `${KNOWYOURCOUNTRY_API_KEY}` with no default.
- [ ] The context fails to start when `api-key` is blank.
- [ ] No new Liquibase changeset, entity, datasource, or `pom.xml` dependency is added. No starter file is modified.
- [ ] `mvn -pl services/risk-management-service -am test` passes, including the existing `RiskManagementServiceApplicationTests.contextLoads`.

---

## 9. Self-Critique

**Confidence: medium.** The GET contract, security, config and band logic are high-confidence:
each item is user-decided, doc-sourced, or a verified repo precedent. Overall confidence is lowered
by the legacy-fidelity assumptions below. They rest on webMethods behaviour that the doc infers but
did not execute.

**Consistency check.** Every status in §3.2 and §3.3 maps to an edge case in §6, and every edge
case maps to a status:
- 200: E6–E10, E15.
- 401: E11. 403: E12. 405: E13.
- 500: E5. 502: E1 and E2. 503: E3 and E4.
- Legacy 1018: E1. Legacy 500: E2–E5 and E14.

§4 (no data changes) and §5 (no `permit-all-urls` change) agree with §3. No blocking gap remains.
Each residual assumption below changes only an edge of the legacy wire behaviour and is cheap to
reverse.

**Residual non-blocking assumptions:**
- **A1. Legacy correlation-ID source.** The legacy pipeline input is modelled as an optional JSON
  body field `correlationID`, with the `X-Correlation-ID` header as a fallback. Legacy callers may
  instead have sent it as a query parameter, or not at all. If the body value is used, the
  `X-Correlation-ID` response header (set by the filter) may differ from the body value.
- **A2. "No data" on legacy.** "No data" means the whole `response` property is omitted, rather
  than `response: {}`. This is inferred from IS behaviour: an IS document is only created when a
  child is mapped.
- **A3. Generated `NON_NULL` annotation.** `x-class-extra-annotation` is honoured by
  openapi-generator 7.23.0 for the spring generator. If it is not, hand-written records are the
  fallback.
- **A4. Vendor 2xx/3xx other than 200.** These are classified as malformed (`xxx002`, legacy
  `"500"`) rather than `1018`. `AbstractRestClient.call` does not expose the status of a non-error
  response. Legacy would have returned `1018`.
- **A5. Legacy F-04 not reproduced.** For a non-array 200 body, the legacy route returns `"500"`
  rather than reproducing F-04 (`200`/`success` with no data), which the doc only inferred.
- **A6. Timeout model.** There is a single TimeLimiter `request-timeout` (Finoux precedent) instead
  of the doc's separate 5s connect and 30s read timeouts. In the starter's operator order the
  TimeLimiter wraps the retry, so a timed-out first attempt is not retried. Only fast connection
  failures and 5xx are retried.
- **A7. Unmapped-label metric.** The counter has no label tag, because of cardinality. The label
  appears only in the WARN line.

**Weakest part:** legacy wire fidelity (A1, A2, A4, A5). It depends on inferred webMethods pipeline
semantics, not observed traffic. Before cutover, a captured legacy response for each outcome (from
the `crm`/`Middleware` gateway logs) should be diffed against §3.3. The second risk is conflict C2:
legacy consumers currently send no JWT, so APIM must supply one or cutover breaks regardless of
body fidelity.
