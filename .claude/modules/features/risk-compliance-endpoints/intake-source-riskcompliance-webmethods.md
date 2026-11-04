<!-- Source: pasted by the user into the /alramz-sdlc session on 2026-11-04. Reproduced verbatim (images not available). -->

# RiskCompliance Service

**API Documentation & Integration Reference**  
Software AG webMethods → Spring Boot 3.x / Java 21 Migration  
AL RAMZ CAPITAL | MIDDLEWARE MIGRATION PROGRAMME

## Document Control

| Attribute | Detail |
| --- | --- |
| Source package | `RiskCompliance` v1.0 (Software AG webMethods Integration Server), manifest dated 2026-07-20 |
| Services covered | `countriesList`, `countriesRatings` |
| Other sources | Confluence: Release v75.0 Release Note and Deployment Plan; Country Risk Rating |
| Document version | v1.0 — Initial Reverse-Engineered Documentation |
| Date | 2026-11-02 |
| Prepared for | Software AG webMethods → Azure Cloud / Spring Boot migration |

## 1. Overview

### 1.1 Purpose

This document defines the API contract, business rules and integration behaviour of the **RiskCompliance** package — two read-only REST APIs that expose country reference data and country risk ratings sourced from the KnowYourCountry vendor — as implemented in Software AG webMethods. It is the technical reference for migrating this service to Spring Boot 3.x / Java 21 on Azure Cloud.

Everything described as legacy behaviour was reverse-engineered from the package's `flow.xml` and `node.ndf` files. Deployment facts that are not in the package (gateway routing, consumer applications, vendor host) come from the Release v75.0 pages on Confluence and are labelled as such.

### 1.2 Scope

**In scope**

- The REST contract of both operations on resource `RiskCompliance.restAPI:riskCompliance`: `POST /countries` and `POST /countryRatings`.
- The Flow services `RiskCompliance.services:countriesList` and `RiskCompliance.services:countriesRatings`, including the risk-band translation rule.
- The vendor calls to KnowYourCountry and the internal calls to `commonUtility` and `SEQDatalust`.
- Target REST design, response envelope, error-code tables and process flows for Spring Boot.
- Findings surfaced during source analysis (Appendix D).

**Out of scope**

- Internals of `commonUtility` and `SEQDatalust`. Neither package is in this export, so only their call signatures, as seen from these Flows, are documented.
- The email template `KYC_COUNTRY_RISK_UPDATE_NOTIFICATION`. Release v75.0 deploys it alongside this package, but no service in this package sends email (Section 3.3).
- The vendor's own API specification. Vendor fields are documented only as far as this package's source evidences them.

### 1.3 Executive Summary

The package exposes two parameterless APIs. `countriesList` returns every country's name and ISO codes. `countriesRatings` returns the same identity fields plus a two-part risk classification. Both follow one pattern: resolve the vendor host from static data, call KnowYourCountry over HTTPS with a GET, translate the JSON array, and return a `correlationID` / `responseCode` / `responseMessage` / `response` envelope. The package owns no database tables and holds no state.

The only business logic is the ratings translation: the vendor's five `risk_level` labels collapse into three bands — `20`/`H`, `9`/`M` and `1`/`L` (Section 5.3).

The most urgent finding is not about migration: **a live vendor API key is hardcoded in plain text in both services** and in both `.bak` files, so anyone holding the package holds the key (Finding F-01). The most significant behavioural finding is that a country whose `risk_level` does not exactly match one of the five labels is **silently omitted** from the ratings response while the call still reports success (Finding F-02) — relevant for a compliance data feed.

### 1.4 Key Migration Notes

> [!CAUTION]
> **Rotate the vendor API key now**
>
> The KnowYourCountry key is a literal in `flow.xml` and `flow.xml.bak` of both services. This is independent of the migration timeline: rotate it and move it to a secret store. The value is deliberately redacted throughout this document (Section 3.4, Finding F-01).

> [!WARNING]
> **Decide what an unknown risk level means**
>
> The legacy branch has no default case, so an unrecognised label drops the country with no error or log marker. Compliance should decide the target behaviour before build (Section 5.3, Finding F-02).

> [!WARNING]
> **Breaking changes for `crm` and `Middleware`**
>
> The target moves from POST to GET, from a blanket transport 200 to real status codes, and from string `rating` to integer `rating`. Appendix E lists every consumer-visible change.

> [!NOTE]
> **Legacy transport status is always 200**
>
> Neither Flow calls `pub.flow:setResponseCode`, so vendor failures and internal errors reach the caller as HTTP 200 with the failure only in the inner `responseCode`. All non-200 statuses in this document are target-design proposals.

## 2. Solution Architecture

### 2.1 As-Built Service Map

One REST resource with two operations maps directly onto two Flow services with near-identical structure: a TRY block, a CATCH block, and a closing sequence (commented "Finally") that logs and trims the pipeline. The API Gateway routes to Integration Server through the `restv2` directive.

*(Figure 1 — Source Flow (As-Built), not available)*

> [!NOTE]
> **Inactive `.bak` files**
>
> Each service ships a `flow.xml.bak` that Integration Server does not execute. They are useful evidence only: they show the package was previously namespaced `RiskComplaince.knowyourcountry` and reveal vendor fields that the active Flows ignore (Section 6.1).

### 2.2 End-to-End Journey

A separate journey diagram is omitted because KnowYourCountry is the only external business system in the chain, so it would repeat the single hop in Figure 1.

### 2.3 Target Architecture — Spring Boot 3.x on Azure

Both endpoints consolidate into one stateless Spring Boot service behind Azure API Management. The vendor credential moves to Azure Key Vault, the vendor host to externalised configuration, and logging to structured logs keyed by correlation ID.

*(Figure 2 — Target Architecture, not available)*

### 2.4 Target Response & Result Model

The legacy vendor calls have no typed outcome: the Flow branches on the raw `header/status` and relies on exceptions for everything else. The target wraps the internal vendor call in a typed result so the service layer decides the HTTP outcome in one place. This wrapper is internal only and never serialized to callers.

```java
public sealed interface OperationResult<T> {
    record Success<T>(T value) implements OperationResult<T> {}
    record Failure<T>(FailureKind kind, String logDetail) implements OperationResult<T> {}
}

public enum FailureKind {
    UPSTREAM_REJECTED,   // non-200 from vendor        -> 502
    UPSTREAM_MALFORMED,  // 200 but not a JSON array   -> 502
    UPSTREAM_UNAVAILABLE // timeout / connection error -> 503
}

public record ApiResponse<T>(
    String correlationID,
    String responseCode,    // duplicates the HTTP status, e.g. "200"
    String responseMessage, // HTTP reason phrase, e.g. "OK"
    String errorCode,       // null: these services have no client-input errors
    String errorMsg,        // null: these services have no client-input errors
    T response) {}
```

## 3. Prerequisites & Static Configuration

### 3.1 Integration Server Global Variables

None. Neither Flow reads an Integration Server global variable. The only `%...%` substitution is the pipeline variable `%host%` in the vendor URL.

### 3.2 Static / Feature-Flag Configuration

Configuration is read per request through `commonUtility.services:getStaticData`. There are no feature flags.

| Application / Key | Used by | Legacy value | Target |
| --- | --- | --- | --- |
| `MIDDLEWARE` / `KNOWYOURCOUNTRY_HOST` | Both services | `https://api.knowyourcountry.com` (PRE-PROD and PROD, Release v75.0 deployment plan) | `knowyourcountry.base-url` in externalised configuration |
| Vendor API key | Both services | Literal in `flow.xml` — not static data (redacted, F-01) | Azure Key Vault secret, injected at start-up |

The deployment plan seeds `MIDDLEWARE.STATIC_DATA` and then requires clearing the IS cache *Administration > Settings > Caching > IntegrationsCache > Static Data* and re-running `commonUtility.services:generateStaticDataCache` on every node. The target removes that manual step.

### 3.3 Upstream / Downstream Dependencies

The dependencies below are the candidates for target health checks.

| Dependency | Purpose | Criticality | Target health-check treatment |
| --- | --- | --- | --- |
| KnowYourCountry API | Source of all response data | Critical | Readiness **indicator only**, not a hard gate: avoid spending vendor quota on probes, and report rather than remove the pod during a vendor outage. |
| `commonUtility` package | Host lookup and GUID generation | Critical (legacy only) | Removed: replaced by configuration and `java.util.UUID`. |
| `SEQDatalust` package | Request/response log ingestion | Non-critical | Removed: replaced by the platform logging pipeline. |
| Azure Key Vault (target) | Vendor API key | Critical at start-up | Fail fast at start-up. |

The package manifest declares `requires` as null even though both Flows call `commonUtility` and `SEQDatalust` (Finding F-08).

> [!NOTE]
> **Email template shipped in the same release**
>
> Release v75.0 also deploys `KYC_COUNTRY_RISK_UPDATE_NOTIFICATION`. No Flow in this package invokes an email service, so the component that sends it lies outside this export. Confirm its owner so it is not lost in migration (Open item 6).

### 3.4 Security Notes

| Concern | Legacy state (evidence) | Target design |
| --- | --- | --- |
| Caller authentication | Enforced at the API Gateway only: API key for registered applications (`x-Gateway-APIKey`), with `crm` and `Middleware` registered (deployment plan). The Flows perform no authentication. | Azure API Management subscription key or OAuth 2.0 client credentials. Service reachable only from APIM. |
| Integration Server ACL | `check_internal_acls` = `no` on both services; `listACL` is null in the manifest. The export assigns no explicit ACL. Effective ACLs live in server configuration — verify on the server. | Network isolation plus APIM policy; no anonymous route to the service. |
| Vendor credential | **Hardcoded** `Authorization: ApiKey vlcn_1.0_****` set as a literal on `pub.client:http`. Same value in 2 `flow.xml` and 2 `flow.xml.bak` files. | Rotated key in Azure Key Vault; never in source, logs or diagnostics. |
| HTTP methods | The REST resource exposes POST only. The services' own `allowedHTTPMethods` lists all eight verbs, so the resource and gateway are the effective limit. | GET only on each route; other verbs return 405. |
| Input validation | `svc_in_validator_options` = `none`. The only input read is the undeclared optional `correlationID`. | Validate the `X-Correlation-ID` format; generate one if invalid, to avoid log injection. |
| Error exposure | `pub.flow:clearPipeline` preserves only four fields, so `lastError` does not reach the caller, but the Flow copies it to the log. | Unchanged principle: failure detail stays server-side, keyed by correlation ID. |

## 4. Primary API — Get Countries

### 4.1 Endpoint Summary

| Attribute | Detail |
| --- | --- |
| HTTP method (legacy) | POST |
| Gateway path (legacy) | `/gateway/RiskCompliance/1.0/countries` |
| Integration Server route | `${IntegrationServer}/restv2/RiskCompliance.restAPI:riskCompliance/countries` |
| Source service | `RiskCompliance.services:countriesList` |
| Request body | None |
| Target endpoint | `GET /api/v1/countries` |

> [!NOTE]
> **Legacy vs. target endpoint**
>
> The legacy path is already a plural noun; the defect is the verb. A parameterless read has no reason to be a POST, so the target is `GET /api/v1/countries` returning `200 OK`. It stays a collection endpoint, not a single-resource lookup.

### 4.2 Request Schema

The declared input signature (`sig_in`) is empty and no body field is read.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `correlationID` | String | No | Undeclared optional pipeline input. If it contains at least one non-space character (`/[^ ]/`) it is reused; otherwise the Flow generates a GUID. |
| | | | **Legacy vs. target field format:** the legacy value arrives as an undeclared pipeline variable. The target reads it from the standard `X-Correlation-ID` request header, typed as a UUID string, and generates one when absent or invalid. |

### 4.3 Business Logic Summary

1. Reuse `correlationID` if supplied; otherwise generate one with `commonUtility.java:GenerateGUID`.
2. Record `requestTimestamp` (`yyyy-MM-dd HH:mm:ss.SSS`, GMT+4).
3. Read `KNOWYOURCOUNTRY_HOST` through `commonUtility.services:getStaticData`.
4. Call `GET %host%/api/v3/countries_list` with the hardcoded API key. No timeout input is mapped.
5. Convert the body bytes to a UTF-8 string and hold it as `nativeResponse`.
6. If `header/status` is `200`: parse the JSON, map `$rootArray` to `response.countryList` (`name`, `iso_alpha2`→`iso2`, `iso_alpha3`→`iso3`) and set `200` / `success`.
7. For any other status: set `1018` / `Failed to get countrylist`, with no `response`.
8. If anything in the TRY block throws (connection failure, timeout, unparsable JSON): set `500` / `Internal Server Error` and copy the error text to `nativeResponse`.
9. Always: record the response time, call `SEQDatalust.services:asynchronousIngestion`, then clear the pipeline except `correlationID`, `responseCode`, `responseMessage` and `response`.

> [!NOTE]
> **Business-logic observations for the target design**
>
> **(a)** A 200 from the vendor whose body is not a JSON array produces no `$rootArray`, so the Flow appears to report `200` / `success` with no data (inferred from the Flow, not executed — F-04). The target treats this as malformed. **(b)** This service passes no `serviceName` to the logger (F-05). **(c)** Country data changes rarely and legacy caching is off (`caching` = `no`), so every call reaches the vendor; a short-lived cache is proposed (Open item 5).

### 4.4 Sample Request

```http
GET /api/v1/countries HTTP/1.1
Accept: application/json
X-Correlation-ID: a3eac7fd-8693-468a-b194-48a90ce9a3b7
```

Legacy equivalent: `POST /gateway/RiskCompliance/1.0/countries` with header `x-Gateway-APIKey` and no body.

### 4.5 Response Schema

| Field | Type | Description |
| --- | --- | --- |
| `correlationID` | String (UUID) | Caller-supplied or generated trace identifier. |
| `responseCode` | String | Duplicates the HTTP status. |
| | | **Legacy vs. target field format:** legacy returns `200`, `1018` or `500` here over transport 200. The target value always equals the real HTTP status. |
| `responseMessage` | String | HTTP reason phrase. |
| | | **Legacy vs. target field format:** legacy returns `success`, `Failed to get countrylist` or `Internal Server Error`. The target returns the reason phrase, for example `OK`. |
| `errorCode` | String, nullable | New in target. Exists for envelope consistency but **never populated by this service** — it has no client-input errors. |
| `errorMsg` | String, nullable | New in target. Never populated by this service, as above. |
| `response.countryList[]` | Array of object | One entry per country. Empty array when the vendor returns none. |
| | | **Legacy vs. target field format:** legacy omits the array entirely when there is no data. The target always returns an array on success. |
| `response.countryList[].name` | String | Country name, UTF-8 (for example `Åland Islands`). |
| `response.countryList[].iso2` | String | ISO 3166-1 alpha-2 code. |
| | | **Legacy vs. target field format:** declared as an unconstrained string in legacy. Documented as ISO 3166-1 alpha-2 in the target but not rejected if non-conforming, because the vendor's full value set is unverified (Open item 7). |
| `response.countryList[].iso3` | String | ISO 3166-1 alpha-3 code. Same note as `iso2`. |

### 4.6 HTTP Status Code Reference

Success is `200 OK` (legacy transport 200, inner `200` / `success`). In every row below, the legacy transport status is **200**: the HTTP statuses shown are proposed target values, and the legacy service does not return them today.

#### 4.6.1 Client Input Errors

None. The endpoint accepts no business input. As a collection endpoint, an empty result is `200` with an empty array rather than `404`, and the legacy source has no "no data" code for this service. Authentication failures (401/403) and unsupported methods (405) are raised by APIM or the framework before service code runs.

#### 4.6.2 Backend/Provider Errors

`errorCode` and `errorMsg` stay `null` on these responses. The service error code is a server-side log key.

| HTTP Status | Service Error Code | Description | Legacy Error Code |
| --- | --- | --- | --- |
| 502 | `CLS001` | Vendor returned a non-200 status. | `1018` — Failed to get countrylist |
| 502 | `CLS002` | Vendor returned 200 with an unparsable or non-array body. | `500` — Internal Server Error (unparsable); none (non-array, F-04) |
| 503 | `CLS003` | Vendor unreachable or timed out. | `500` — Internal Server Error |
| 500 | `CLS004` | Any other unexpected failure. | `500` — Internal Server Error |

> [!NOTE]
> **Inferred statuses**
>
> `1018` has no explicit HTTP equivalent in source. `502` is an inference, because the failure is a bad upstream response. The programme's Error-Code-to-HTTP-Status-Mapping registry could not be located for this round, so check `1018` against it (Open item 1). Legacy merges `CLS002`–`CLS004` into one `500`; the split is a target proposal.

### 4.7 Example Target Response Envelopes

**Success (200)**

```json
{
  "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "responseCode": "200",
  "responseMessage": "OK",
  "errorCode": null,
  "errorMsg": null,
  "response": {
    "countryList": [
      { "name": "Afghanistan", "iso2": "AF", "iso3": "AFG" },
      { "name": "Åland Islands", "iso2": "AX", "iso3": "ALA" }
    ]
  }
}
```

**Success, empty result (200)** — shown in place of a client-input error, which this endpoint does not have.

```json
{
  "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "responseCode": "200",
  "responseMessage": "OK",
  "errorCode": null,
  "errorMsg": null,
  "response": { "countryList": [] }
}
```

**Backend/provider error (502)**

```json
{
  "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "responseCode": "502",
  "responseMessage": "Bad Gateway",
  "errorCode": null,
  "errorMsg": null,
  "response": null
}
```

### 4.8 Target Process Flow (Spring Boot)

*(Figure 3 — Target Process Flow, Get Countries, not available)*

## 5. Primary API — Get Country Ratings

### 5.1 Endpoint Summary

| Attribute | Detail |
| --- | --- |
| HTTP method (legacy) | POST |
| Gateway path (legacy) | `/gateway/RiskCompliance/1.0/countryRatings` |
| Integration Server route | `${IntegrationServer}/restv2/RiskCompliance.restAPI:riskCompliance/countryRatings` |
| Source service | `RiskCompliance.services:countriesRatings` |
| Request body | None |
| Target endpoint | `GET /api/v1/country-ratings` |

> [!NOTE]
> **Legacy vs. target endpoint**
>
> This is a judgment call rather than a mechanical rename. `/countries/ratings` was rejected because it would collide with any future `/countries/{iso2}` route. Kebab-case `country-ratings` keeps it a plain collection resource. The method changes from POST to GET and success is `200 OK`.

### 5.2 Request Schema

The declared input signature (`sig_in`) is empty and no body field is read.

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `correlationID` | String | No | Undeclared optional pipeline input. If it contains at least one non-space character (`/[^ ]/`) it is reused; otherwise the Flow generates a GUID. |
| | | | **Legacy vs. target field format:** the legacy value arrives as an undeclared pipeline variable. The target reads it from the standard `X-Correlation-ID` request header, typed as a UUID string, and generates one when absent or invalid. |

### 5.3 Business Logic Summary

1. Reuse or generate `correlationID`, as in Section 4.3.
2. Record `requestTimeStamp` (`yyyy-MM-dd HH:mm:ss.SSS`, GMT+4). Note the capital S, which breaks the logging hand-off (F-05).
3. Read `KNOWYOURCOUNTRY_HOST` through `commonUtility.services:getStaticData`.
4. Call `GET %host%/api/v3/country_ratings` with the hardcoded API key. No timeout input is mapped.
5. Convert the body bytes to a UTF-8 string and hold it as `nativeResponse`.
6. If `header/status` is `200`: parse the JSON and loop over `$rootArray`. For each element, evaluate `risk_level` against the band table below, map `name`, `iso_alpha2` and `iso_alpha3`, set the band's `rating` and `riskLevel`, and append to `response.countryRating`. Then set `200` / `success`.
7. For any other status: set `1018` / `Failed to get countryRating`, with no `response`.
8. If anything in the TRY block throws: set `500` / `Internal Server Error` and copy the error text to `nativeResponse`.
9. Always: record the response time, call `SEQDatalust.services:asynchronousIngestion`, then clear the pipeline except `correlationID`, `responseCode`, `responseMessage` and `response`.

**Risk-band translation** — literal values from the three label-expression branches in `flow.xml`:

| Vendor `risk_level` (exact, case-sensitive) | `rating` | `riskLevel` | Meaning |
| --- | --- | --- | --- |
| `Higher Risk` or `Medium-High Risk` | 20 | H | High |
| `Medium Risk` or `Medium-Low Risk` | 9 | M | Medium |
| `Lower Risk` | 1 | L | Low |
| Anything else, or missing | — | — | **Country omitted; no error or log marker** |

> [!WARNING]
> **Finding F-02 — unmatched labels silently drop a country**
>
> The branch has no `$default` case. If the vendor adds, renames or re-cases a label, or leaves it empty, that country disappears from the response and the call still returns `200` / `success`. A consumer cannot tell "not listed" from "not rated".

> [!NOTE]
> **Business-logic observations for the target design**
>
> **(a)** Recommended target: keep the country out of the response to preserve the contract, but raise a WARN log and a metric naming the label and country. Fail-closed (treat unknown as `H`/`20`) is the alternative, and that decision belongs to Compliance (Open item 2). **(b)** Keep the mapping in configuration rather than code, so a new vendor label needs no release. **(c)** The bands intentionally discard vendor detail: `Medium-High` becomes High and `Medium-Low` becomes Medium, and the vendor's score is unused (Section 6.1). Confirm that this remains the wanted policy.

### 5.4 Sample Request

```http
GET /api/v1/country-ratings HTTP/1.1
Accept: application/json
X-Correlation-ID: a3eac7fd-8693-468a-b194-48a90ce9a3b7
```

Legacy equivalent: `POST /gateway/RiskCompliance/1.0/countryRatings` with header `x-Gateway-APIKey` and no body.

### 5.5 Response Schema

| Field | Type | Description |
| --- | --- | --- |
| `correlationID` | String (UUID) | Caller-supplied or generated trace identifier. |
| `responseCode` | String | Duplicates the HTTP status. |
| | | **Legacy vs. target field format:** legacy returns `200`, `1018` or `500` here over transport 200. The target value always equals the real HTTP status. |
| `responseMessage` | String | HTTP reason phrase. |
| | | **Legacy vs. target field format:** legacy returns `success`, `Failed to get countryRating` or `Internal Server Error`. The target returns the reason phrase, for example `OK`. |
| `errorCode` | String, nullable | New in target. **Never populated by this service** — it has no client-input errors. |
| `errorMsg` | String, nullable | New in target. Never populated by this service, as above. |
| `response.countryRating[]` | Array of object | One entry per country with a recognised risk level. Empty array when none. |
| | | **Legacy vs. target field format:** legacy omits the array entirely when there is no data. The target always returns an array on success. |
| `response.countryRating[].name` | String | Country name, UTF-8. |
| `response.countryRating[].iso2` | String | ISO 3166-1 alpha-2 code. Not rejected if non-conforming (Open item 7). |
| `response.countryRating[].iso3` | String | ISO 3166-1 alpha-3 code. Not rejected if non-conforming (Open item 7). |
| `response.countryRating[].rating` | Integer | Enumerated score: `20`, `9` or `1`. |
| | | **Legacy vs. target field format:** declared `string` in `countryRatingResponse` and sent as `"20"`, but it is a numeric score set from literals, so the target types it as an integer. This is a breaking change for consumers (Appendix E). |
| `response.countryRating[].riskLevel` | Enum | `H`, `M` or `L`. |
| | | **Legacy vs. target field format:** declared as a free `string` in legacy. The target uses a closed enum matching the same three literals, so the wire values do not change. |

### 5.6 HTTP Status Code Reference

Success is `200 OK` (legacy transport 200, inner `200` / `success`). In every row below, the legacy transport status is **200**: the HTTP statuses shown are proposed target values, and the legacy service does not return them today.

#### 5.6.1 Client Input Errors

None. The endpoint accepts no business input. As a collection endpoint, an empty result is `200` with an empty array rather than `404`, and the legacy source has no "no data" code for this service. Authentication failures (401/403) and unsupported methods (405) are raised by APIM or the framework before service code runs.

#### 5.6.2 Backend/Provider Errors

`errorCode` and `errorMsg` stay `null` on these responses. The service error code is a server-side log key.

| HTTP Status | Service Error Code | Description | Legacy Error Code |
| --- | --- | --- | --- |
| 502 | `CRT001` | Vendor returned a non-200 status. | `1018` — Failed to get countryRating |
| 502 | `CRT002` | Vendor returned 200 with an unparsable or non-array body. | `500` — Internal Server Error (unparsable); none (non-array, F-04) |
| 503 | `CRT003` | Vendor unreachable or timed out. | `500` — Internal Server Error |
| 500 | `CRT004` | Any other unexpected failure. | `500` — Internal Server Error |

> [!NOTE]
> **Inferred statuses**
>
> As in Section 4.6: `502` for `1018` is an inference to check against the registry (Open item 1), and the split of the legacy `500` is a target proposal. An unmatched `risk_level` is deliberately not an error row: under the recommended design it is a logged data-quality event on a 200 response (Section 5.3).

### 5.7 Example Target Response Envelopes

**Success (200)** — country values from the Confluence "Country Risk Rating" sample.

```json
{
  "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "responseCode": "200",
  "responseMessage": "OK",
  "errorCode": null,
  "errorMsg": null,
  "response": {
    "countryRating": [
      { "name": "Afghanistan", "iso2": "AF", "iso3": "AFG", "rating": 20, "riskLevel": "H" },
      { "name": "Åland Islands", "iso2": "AX", "iso3": "ALA", "rating": 1, "riskLevel": "L" }
    ]
  }
}
```

**Success, empty result (200)** — shown in place of a client-input error, which this endpoint does not have.

```json
{
  "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "responseCode": "200",
  "responseMessage": "OK",
  "errorCode": null,
  "errorMsg": null,
  "response": { "countryRating": [] }
}
```

**Backend/provider error (503)**

```json
{
  "correlationID": "a3eac7fd-8693-468a-b194-48a90ce9a3b7",
  "responseCode": "503",
  "responseMessage": "Service Unavailable",
  "errorCode": null,
  "errorMsg": null,
  "response": null
}
```

### 5.8 Target Process Flow (Spring Boot)

*(Figure 4 — Target Process Flow, Get Country Ratings, not available)*

## 6. Downstream & Backoffice Integration APIs

### 6.1 KnowYourCountry — Countries and Ratings

> [!NOTE]
> **Internal only**
>
> These vendor endpoints are called only by this service and are never re-exposed directly.

| Attribute | Detail |
| --- | --- |
| Base URL | `https://api.knowyourcountry.com` (static data, Release v75.0) |
| Countries | `GET /api/v3/countries_list` |
| Ratings | `GET /api/v3/country_ratings` |
| Authentication | Header `Authorization: ApiKey <key>` |
| Response shape | JSON array at root (surfaced as `document/$rootArray`) |
| Timeout / retry | None mapped; `retry_max` = 0 and circuit breaker disabled on both services (F-07) |

Vendor fields evidenced by source:

| Vendor field | Evidence | Use |
| --- | --- | --- |
| `name`, `iso_alpha2`, `iso_alpha3` | Active Flows (both endpoints) | Mapped to response. |
| `risk_level` | Active `countriesRatings` | Drives the risk band. |
| `score`, `score_direction`, `risk_colour`, `scoring_profile`, `override` | Disabled mapping in `countriesRatings/flow.xml.bak` only | Not used today. Types unverified; check against the vendor specification. |

**HTTP Status Code Reference** — applies to both vendor calls. The legacy Flow distinguishes only 200 from everything else.

| Vendor outcome | Legacy result | Target result |
| --- | --- | --- |
| 200, JSON array | `200` / `success` | 200 |
| Any other status | `1018` | 502 (`CLS001` / `CRT001`) |
| Unparsable body | `500` via CATCH | 502 (`CLS002` / `CRT002`) |
| Timeout or connection error | `500` via CATCH | 503 (`CLS003` / `CRT003`) |

### 6.2 commonUtility.services:getStaticData

> [!NOTE]
> **Internal only**
>
> A cross-package helper, not in this export. Replaced by externalised configuration in the target.

Input `application` = `MIDDLEWARE`, `key` = `KNOWYOURCOUNTRY_HOST`; output `response.value`, copied to `host`. Per the deployment plan the values are served from an IS cache populated from `MIDDLEWARE.STATIC_DATA`.

**HTTP Status Code Reference:** no status of its own. Any failure raises into the caller's CATCH as `500`. Internal handling of a missing key cannot be determined from this export. In the target, a missing setting fails at start-up.

### 6.3 commonUtility.java:GenerateGUID

> [!NOTE]
> **Internal only**
>
> A cross-package helper, not in this export. Replaced by `java.util.UUID.randomUUID()` in the target.

No input; output `guidID`, copied to `correlationID`. It runs only when no usable `correlationID` was supplied.

**HTTP Status Code Reference:** no status of its own. Any failure raises into the caller's CATCH as `500`.

### 6.4 SEQDatalust.services:asynchronousIngestion

> [!NOTE]
> **Internal only**
>
> A cross-package logging service, not in this export. The name suggests ingestion into Seq; confirm with its owner. Replaced by structured logging in the target.

Declared inputs, as visible from these Flows, against what each service actually supplies:

| Input | `countriesList` | `countriesRatings` |
| --- | --- | --- |
| `serviceName` | **Not supplied** (F-05) | Literal `RiskComplaince.knowyourcountry.services:countriesRatings` — **stale** (F-05) |
| `correlationID` | Same-name pipeline value | Same-name pipeline value |
| `requestTimestamp` | Same-name pipeline value | **Not supplied**: the pipeline holds `requestTimeStamp` (F-05) |
| `responseTimestamp` | Same-name pipeline value | Mapped from `responseTimeStamp` |
| `responseCode`, `responseMessage` | Same-name pipeline value | Mapped |
| `responseBody` | Mapped from `nativeResponse` | Mapped from `nativeResponse` |
| `lastError` | Same-name pipeline value | Mapped |
| `requestHeaders`, `requestBody`, `responseHeaders`, `executionTime`, `logToDatabase`, `requestBodyToLog`, `responseBodyToLog` | Not set | Not set |

**HTTP Status Code Reference:** none. The call sits in the closing sequence (`EXIT-ON="DONE"`), so a logging failure does not change the response the caller receives.

## 7. Data Mapping Reference

### 7.1 Country List Mapping

| Vendor (`countries_list`) | Response field | Target type |
| --- | --- | --- |
| `$rootArray[].name` | `response.countryList[].name` | String |
| `$rootArray[].iso_alpha2` | `response.countryList[].iso2` | String (ISO 3166-1 alpha-2) |
| `$rootArray[].iso_alpha3` | `response.countryList[].iso3` | String (ISO 3166-1 alpha-3) |

### 7.2 Country Rating Mapping

| Vendor (`country_ratings`) | Response field | Target type |
| --- | --- | --- |
| `$rootArray[].name` | `response.countryRating[].name` | String |
| `$rootArray[].iso_alpha2` | `response.countryRating[].iso2` | String (ISO 3166-1 alpha-2) |
| `$rootArray[].iso_alpha3` | `response.countryRating[].iso3` | String (ISO 3166-1 alpha-3) |
| `$rootArray[].risk_level` (band, Section 5.3) | `response.countryRating[].rating` | Integer |
| `$rootArray[].risk_level` (band, Section 5.3) | `response.countryRating[].riskLevel` | Enum `H`, `M`, `L` |

### 7.3 Log Field Mapping

The package writes no business data to a database, so there is no request-to-column mapping. The log record is its only persisted output.

| Legacy input | Target log field | Note |
| --- | --- | --- |
| `serviceName` | `service` | From application name; fixes the missing and stale values (F-05). |
| `correlationID` | `correlationId` | Logging context (MDC) on every line. |
| `requestTimestamp`, `responseTimestamp` | `timestamp`, `durationMs` | Legacy format `yyyy-MM-dd HH:mm:ss.SSS` GMT+4 replaced by ISO 8601 with offset, for example `2026-07-20T15:51:09.000+04:00`. |
| `responseCode`, `responseMessage` | `status` | Real HTTP status. |
| `responseBody` | `resultCount` | Full vendor payload no longer logged (F-06). |
| `lastError` | `error`, `errorCode` | Exception detail with `CLS` / `CRT` key; server-side only. |

## Appendix A — Pseudocode

**countriesList**

```text
TRY
  correlationID = input.correlationID has a non-space char ? input : GenerateGUID()
  requestTimestamp = now("GMT+4")
  host = getStaticData("MIDDLEWARE", "KNOWYOURCOUNTRY_HOST").value
  http = GET host + "/api/v3/countries_list"          // Authorization: ApiKey <hardcoded>
  nativeResponse = utf8(http.body)
  IF http.status == 200
    document = parseJson(nativeResponse)
    response.countryList = document.$rootArray mapped to {name, iso2, iso3}
    responseCode, responseMessage = "200", "success"
  ELSE
    responseCode, responseMessage = "1018", "Failed to get countrylist"
CATCH
  lastError = getLastError()
  responseCode, responseMessage = "500", "Internal Server Error"
  nativeResponse = lastError.error
ALWAYS
  responseTimestamp = now("GMT+4")
  asynchronousIngestion(responseBody = nativeResponse, ...)
  keep only correlationID, responseCode, responseMessage, response
```

**countriesRatings** — differs from `countriesList` only in the path, the success mapping and the failure message.

```text
http = GET host + "/api/v3/country_ratings"
IF http.status == 200
  FOR EACH c IN parseJson(nativeResponse).$rootArray
    SWITCH c.risk_level
      "Higher Risk", "Medium-High Risk" : item = {c.identity, rating "20", riskLevel "H"}
      "Medium Risk", "Medium-Low Risk"  : item = {c.identity, rating "9",  riskLevel "M"}
      "Lower Risk"                      : item = {c.identity, rating "1",  riskLevel "L"}
      // no default: item stays empty and nothing is appended (F-02)
    append item to response.countryRating
    clear item
  responseCode, responseMessage = "200", "success"
ELSE
  responseCode, responseMessage = "1018", "Failed to get countryRating"
```

## Appendix B — Glossary

| Term | Meaning |
| --- | --- |
| APIM | Azure API Management, the target gateway. |
| Band | One of the three rating/risk-level pairs: `20`/`H`, `9`/`M`, `1`/`L`. |
| Flow service | A webMethods Integration Server service authored visually and stored as `flow.xml`. |
| IS | Software AG webMethods Integration Server. |
| ISO 3166-1 | International standard for country codes (alpha-2 and alpha-3). |
| KnowYourCountry | Third-party vendor of country risk data. |
| Pipeline | The in-memory key/value state a Flow service passes between steps. |
| `$rootArray` | The name IS gives a JSON array parsed from the root of a document. |
| `restv2` | The IS URL directive used by the gateway to reach this REST resource. |

## Appendix C — Data Definition

The package has no JDBC adapters, owns no tables, and no DDL exists in this export, so there is no source-to-target type-mapping to trace. The target service needs no database.

Its only database touchpoint is indirect: a row read through the `commonUtility` static-data cache. The source statement below is reproduced from the Release v75.0 deployment plan and is identical for PRE-PROD and PROD.

```sql
Insert into MIDDLEWARE.STATIC_DATA (APPLICATION,KEY,VALUE)
values ('MIDDLEWARE','KNOWYOURCOUNTRY_HOST','https://api.knowyourcountry.com');
commit;
```

Target equivalent, with no table required:

```yaml
knowyourcountry:
  base-url: https://api.knowyourcountry.com
  api-key: ${KNOWYOURCOUNTRY_API_KEY}   # resolved from Azure Key Vault
  connect-timeout: 5s                   # proposed; legacy has none mapped
  read-timeout: 30s                     # proposed; legacy has none mapped
```

## Appendix D — Findings & Recommendations

| ID | Severity | Finding (evidence) | Recommendation |
| --- | --- | --- | --- |
| F-01 | Critical | Vendor API key hardcoded as a `MAPSET` literal on `pub.client:http` in both `flow.xml` and both `flow.xml.bak` files. | Rotate now. Remove from the package, its `.bak` files and any deployment artefacts. Key Vault in target. |
| F-02 | High | `countriesRatings` risk branch has no default, so an unmatched or missing `risk_level` silently omits the country under `200` / `success`. | Compliance decision (Open item 2). Log and alert at minimum; keep the mapping configurable. |
| F-03 | High | Neither Flow calls `pub.flow:setResponseCode`, so `1018` and `500` reach callers on transport 200. A consumer that checks only HTTP status treats failures as success. | Real status codes in target. Confirm how `crm` handles the inner code today. |
| F-04 | Medium | A vendor 200 with a non-array JSON body creates no `$rootArray`, so the Flow appears to return `200` / `success` with no data. Inferred from Flow semantics, not executed. | Validate the shape; return 502 (`CLS002` / `CRT002`). |
| F-05 | Medium | Logging gaps: `countriesList` sends no `serviceName` (the `.bak` had one); `countriesRatings` sends a stale `RiskComplaince.knowyourcountry...` name; `countriesRatings` stores `requestTimeStamp`, so the logger's `requestTimestamp` is never supplied. `executionTime` is unset on both. | Fix in the legacy package if it will keep running for long. Structured logging in target. |
| F-06 | Medium | The entire vendor payload is logged as `responseBody` on every call, because `nativeResponse` is mapped unconditionally. | Log counts and identifiers; keep bodies only at debug level. |
| F-07 | Medium | No `timeout` or `connectTimeout` mapped on `pub.client:http`; `retry_max` = 0; circuit breaker disabled; caching off. A slow vendor holds an IS thread and every call depends on vendor availability. | Explicit timeouts, one retry on the idempotent GET, circuit breaker (Resilience4j), optional cache. |
| F-08 | Low | Manifest `requires` is null despite runtime calls to `commonUtility` and `SEQDatalust`, so package load order is unprotected. | Declare the dependencies if the package stays in service. Not applicable in target. |
| F-09 | Low | Inconsistent spelling: package and release note use `RiskCompliance`; the gateway API in the deployment plan, the DEV URL on Confluence and the `node_pkg` of both doc types use `RiskComplaince`. Two gateway base paths may therefore exist. | Confirm the live path before consumer cutover (Open item 3). Use the correct spelling in target. |
| F-10 | Low | Confluence "Country Risk Rating" omits `1018` from its error table and gives `UAE` as an ISO3 example (the ISO code is `ARE`). No equivalent page for the countries API was found. | Publish the target OpenAPI specification as the single contract. |
| F-11 | Low | Nothing clears `response` in CATCH. An exception midway through the ratings loop could return a partial list with `responseCode` `500`. Inferred; unlikely in practice. | Target builds the list fully before returning; errors carry `response: null`. |
| F-12 | Low | The deployment plan's rollback removes the email template but not the `KNOWYOURCOUNTRY_HOST` row. | Add it to the rollback if the plan is reused. |

## Appendix E — Consumer Impact & Open Items

### E.1 Consumer-Visible Changes

The deployment plan registers `crm` and `Middleware` as gateway applications. Both need to absorb these changes, or APIM must apply a transition policy.

| Item | Legacy | Target | Breaking? |
| --- | --- | --- | --- |
| Method | POST | GET | Yes |
| Paths | `/countries`, `/countryRatings` | `/api/v1/countries`, `/api/v1/country-ratings` | Yes |
| Failure transport status | 200 | 502, 503, 500 | Yes |
| `responseCode` on vendor failure | `1018` | `502` | Yes |
| `responseMessage` on success | `success` | `OK` | Yes, if compared |
| `rating` type | String `"20"` | Integer `20` | Yes |
| Empty result | Array omitted | Empty array | Low risk |
| `errorCode`, `errorMsg` | Absent | Present, always null | No |
| `riskLevel`, `iso2`, `iso3`, `name` | — | Unchanged on the wire | No |

### E.2 Open Items

1. **Registry check for `1018`.** The Error-Code-to-HTTP-Status-Mapping registry cited by sibling documents was not found in SharePoint or Confluence. `502` is inferred until checked against it.
2. **Unknown risk level policy.** Compliance to choose between omit-and-alert (recommended) and fail-closed.
3. **Live gateway path.** Confirm `RiskCompliance` or `RiskComplaince` in each environment, and whether Release v75.0 reached PROD: the deployment plan status reads "Pending".
4. **Consumer confirmation.** Confirm the actual consumers and how they read `responseCode` and `rating`. The noun-based paths and the `CLS` / `CRT` prefixes are proposals awaiting sign-off.
5. **Caching.** Compliance to confirm an acceptable staleness window. No TTL is assumed; caching stays off until agreed.
6. **Email template owner.** Identify what sends `KYC_COUNTRY_RISK_UPDATE_NOTIFICATION`, since nothing in this package does.
7. **Vendor specification.** Obtain it to confirm the full `risk_level` value set, whether all country codes conform to ISO 3166-1, the unused fields' types, and any rate limits.
8. **Effective ACL and helper internals.** Verify the service ACLs on the server, and review `commonUtility` and `SEQDatalust`, which are outside this export.
