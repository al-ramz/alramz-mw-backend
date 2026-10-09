# New REST Endpoint — Elicitation Checklist

This doc is upstream of [`openapi-contract-first-controllers.md`](openapi-contract-first-controllers.md).
That doc explains the codegen mechanics once a contract is known; this one is the checklist for
**figuring out the contract in the first place** — the exact set of facts you need pinned down,
in a business/product conversation, before a single line of `specs/apiSpecs.yaml` gets written.
Use this whenever a feature request is (or includes) "add a new endpoint" and the request itself
doesn't already answer every question below.

## 1. What this checklist is for

A feature request that says "add an endpoint to check X" is not implementation-ready. Before it
can become a `spec.md` (and later an `execution_plan.json` entry touching `specs/apiSpecs.yaml`),
every fact in §2 must be pinned down. **Resolve each one yourself, in this order, before ever
asking the user:**

1. **The feature request states it explicitly** — use that.
2. **An existing operation in the target service's `specs/apiSpecs.yaml` sets a direct
   precedent** for this exact kind of endpoint — match it.
3. **This repo has a stated standard/recommended default** for it (§2's "Standard default"
   column) — apply it, and say in `spec.md` that it was defaulted (so a reviewer can see the
   assumption), rather than leaving it implicit.

Only escalate to `AskUserQuestion` when **none of the three** resolve it, or the request
explicitly conflicts with the standard default in a way that needs the requester's confirmation
(e.g. they asked for something to be public, which overrides the secured-by-default standard, and
you want to confirm that was intentional). This is the same three-part blocking-gap test
`01_elicit`'s "Resolving Blocking Gaps" section already uses — don't treat "the user didn't say"
as automatically blocking when a standard default exists to fall back on. A row that's genuinely
unresolved after all three (or a request that conflicts with a default) is expensive to guess
wrong on — a public API shape, once a controller is generated against it, isn't a cheap fix.

## 2. The facts you need, mapped to real examples in this repo

| Fact | Where it shows up in `apiSpecs.yaml` | Standard default (apply automatically per §1, unless the request/precedent overrides it) | Real example |
|---|---|---|---|
| **HTTP verb + path** | `paths.<path>.<verb>` | No generic default — must come from the request or precedent | `POST /iban/validate` (`data-validation-service`) |
| **Tag / feature grouping** | `tags: [...]` | No generic default — pick the feature's own name, matching the file's existing tag style | `tags: [IBAN]` |
| **`operationId`** (becomes the Java method name verbatim) | `operationId` | No generic default — `camelCase` verb+noun matching the file's existing naming | `validateIBAN` |
| **Security** — public or JWT-secured, and if secured, which role | not in the YAML operation itself — decided via `company.jwt.permit-all-urls` (public) vs `@JwtSecured(roles = "...")` on the controller method (secured) | **Secured**, role `APP_<SERVICE_CONCERN>` matching the service's existing role name if one exists — apply this unless the request explicitly asks for a public endpoint | `/api/v1/info` is public (explicit exception); `validateIBAN` is secured with `APP_DATA_VALIDATION` (the default applied) |
| **Request schema** — every field, which are required, types, format | `requestBody.content.application/json.schema` → `components.schemas.<X>Request` | No generic default — must come from the request or precedent | `IBANRequest { IBAN: string, required }` |
| **Success response schema (200)** | `responses.'200'.content...schema` + a filled-in `example` | **`GenericResponse`** wrapping a `response` object — apply this unless the data genuinely can't nest under `response` (see §3) | `GenericResponse`, or a custom envelope like `OnboardingResponse` |
| **Every other status the endpoint can actually produce** | one `responses.'<code>'` entry each | `401`+`400`+`500` always apply (every secured JSON endpoint can hit them); `403` if role-restricted; `503` only if this endpoint actually calls a downstream/external system — apply this rule, don't template a fixed list | `401` (bad/missing token), `400` (validation failure), `403` (role check failed), `500` (unexpected), `503` (downstream/external system unavailable) |
| **Internal/business error codes for failure responses** | inside the failure response's `example`, e.g. an `internal_error_code`/`errorCode` field | Only if the chosen envelope carries one (custom envelopes do, plain `GenericResponse` operations in this repo today don't) — no cross-feature code prefix to reuse, mint a new one for this feature | Onboarding op: `internal_error_code: "ONB011"` for a missing-field failure |
| **Headers** | `Authorization: Bearer <token>` implied by `security: - BearerAuth: []` at the spec root; `X-Correlation-Id` is read (not required) by the exception handler for tracing, per [`exception-handling.md`](exception-handling.md) | Apply as-is — this is fixed starter behavior, not a per-endpoint decision | — |

## 3. How it works — applying the standard defaults yourself

- **Public or secured?** Apply **secured** automatically — every endpoint is authenticated by
  default in this repo (see
  [`jwt-security-and-public-endpoints.md`](jwt-security-and-public-endpoints.md)). Don't ask
  "should this be public or secured?" as an open question; instead, only flip to public if the
  request explicitly says so (e.g. "this is a public health-check endpoint"), and state in
  `spec.md` that the default was applied either way, so the decision is visible without having
  cost a round-trip to the requester.
- **If secured, which role?** Apply the `APP_<SERVICE_CONCERN>` convention already used in the
  target service (`APP_DATA_VALIDATION`, `APP_REFERENCE_DATA`) automatically if the feature is a
  natural fit for an existing role; only raise a genuine question if the feature is a distinct
  enough concern that reusing the existing role would be wrong (e.g. it needs a narrower
  permission the existing role doesn't imply) — that's a real design decision, not a default.
- **Which response envelope?** Default is `GenericResponse` (`responseCode`/`responseMessage`/
  `response`/`correlationId`) — reuse it unless the data genuinely doesn't fit nested under
  `response`, the way `OnboardingResponse` legitimately needed top-level
  `member_reference_number`/`internal_error_code` for **every** status code, not just 200. Don't
  invent a bespoke envelope by default; that's the exception, not the rule (see
  `openapi-contract-first-controllers.md` §5).
- **Which failure statuses are real, not boilerplate?** Don't just copy `401`/`400`/`500` as
  placeholders. Determine this yourself from what the endpoint actually does: a read-only lookup
  against no external system can't produce `503`; an endpoint calling a downstream service can.
  Match the response list to real failure modes from `spec.md`'s eventual "Edge Cases & Error
  Behaviors" section, not a generic template — this is a determination to make, not a question to
  put to the requester.
- **Internal error codes** — if the endpoint's envelope carries one (as `OnboardingResponse`
  does), every distinct business failure needs its own code, following whatever prefix
  convention the service already uses (`ONB0xx` for onboarding). If the envelope is plain
  `GenericResponse`, the repo's existing convention is looser — most `GenericResponse`-based
  operations only give a `description` for `400`/`500`/`503` with no example body (see §5,
  pitfall 1) — decide explicitly whether this new endpoint should be more complete than that
  precedent, and say so in `spec.md` rather than silently copying the sparser style.

## 4. How to turn the answers into `specs/apiSpecs.yaml`

1. **Check whether the file already exists** for the target service
   (`services/<service>/specs/apiSpecs.yaml`) — for both current services, it does. This is
   almost always an **amendment**, not a new file: add a new `paths.<path>` entry (and any new
   `components.schemas` entries it needs) to the existing file, matching its existing `tags`,
   `operationId` casing, and envelope conventions — don't restructure what's already there.
2. **Only treat it as a new file** if the target is a brand-new service that doesn't have one yet
   — in which case defer to the `alramz-service-scaffold` skill or
   `openapi-contract-first-controllers.md` §4b's minimal starter spec, don't hand-roll one from
   scratch inside a feature spec.
3. **Write the path bare**, with no `/api/v1` prefix — both reference services' controllers add
   that prefix at the class level (`@RequestMapping("/api/v1")`); the spec path is just
   `/iban/validate`, not `/api/v1/iban/validate`.
4. **Reuse `GenericResponse`** unless §3 concluded a custom envelope is genuinely warranted.
5. **Fill in a real `example` for every response**, not just `200` — if you can't write a
   plausible example body for a status code, that's a sign the status code, or your understanding
   of when it fires, isn't actually settled yet; go back to §2.
6. **Add `'401'` (and `'403'` if role-restricted)** whenever `security: - BearerAuth: []` applies,
   matching the spec's existing convention of at least a `description` for these.

## 5. Common pitfalls / anti-patterns

- **Treating an endpoint as "obviously public" without confirming it.** Every endpoint is
  authenticated by default in this repo; "public" is a decision, not a default for anything that
  merely sounds harmless.
- **This repo's own inconsistency: some `GenericResponse` operations skip response bodies for
  errors.** `validateIBAN`/`verifyPhone`/`validateExistingData` declare `400`/`401`/`500` with
  only a `description`, no `content` schema or example — the real error body shape is whatever
  `GlobalExceptionHandler` produces at runtime, not mirrored in the spec. This is a real gap in
  the existing spec, not a pattern to aspire to — for a **new** endpoint, prefer specifying a real
  example body per status (as `onboard` does) so `03_implement`/tests have something concrete to
  build and verify against, even if older operations in the same file don't.
- **Copying `internal_error_code` conventions across unrelated features.** `ONB0xx` belongs to
  onboarding; a new feature area needs its own prefix, not a borrowed one, unless it's genuinely
  extending onboarding's own error taxonomy.
- **Forgetting `security: - BearerAuth: []` is spec-root, not per-operation.** You don't repeat it
  per path — it's declared once at the top of `apiSpecs.yaml` and applies to everything unless the
  controller/YAML says otherwise (public endpoints are an *application-layer* decision via
  `permit-all-urls`, not a per-operation OpenAPI `security: []` override — this repo doesn't use
  the latter).
- **Deciding the contract *after* writing the controller.** The whole point of contract-first is
  that the YAML is the source of truth — if you're elicit-ing a new endpoint and find yourself
  wanting to "just write the controller and figure out the spec after," that's the wrong order for
  this repo.

## 6. Checklist

- [ ] HTTP verb + bare path (no `/api/v1` prefix) confirmed.
- [ ] Tag and `operationId` chosen, consistent with the target file's existing casing/naming.
- [ ] Public vs. secured resolved — defaulted to secured unless the request explicitly asked for
      public; role name follows `APP_<SERVICE_CONCERN>` if secured, and the default (if applied)
      is stated in `spec.md`, not left implicit.
- [ ] Request schema fully specified: every field, required vs. optional, type, format, one
      realistic example.
- [ ] Response envelope decided: `GenericResponse` (default) or a justified custom one.
- [ ] Every status code the endpoint can *actually* produce is listed (not a boilerplate
      401/400/500) — informed by the eventual "Edge Cases & Error Behaviors" section of `spec.md`.
- [ ] Every failure response has a real example body with an internal error code, if the
      envelope carries one.
- [ ] Confirmed whether `services/<service>/specs/apiSpecs.yaml` already exists — amend it if so;
      only scaffold a new one for a brand-new service.
- [ ] Every fact was resolved via the request, an existing spec precedent, or a stated standard
      default (§1/§3) — applied and noted, not silently invented. Only what none of the three
      resolved was raised as a blocking gap (`01_elicit`) or a `technical_gaps`/blocking item
      (`02_plan`).
