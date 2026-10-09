---
name: alramz-e2e-api-tests-generator
description: >
  Generates Postman collection items (request + pre-request auth script + post-response
  validation tests) for alramz-mw-oss microservice endpoints, grounded in each service's
  own OpenAPI spec (services/<service>/specs/*.yaml) rather than invented request/response
  shapes. Use whenever asked to write Postman requests or tests for a service endpoint, add
  e2e/API tests, build or extend a Postman collection under e2e/, or verify that an endpoint
  "is working fine" / "is passing" via Postman — whether for one endpoint, a named
  operationId, or every endpoint in data-validation-service, alramz-notification-service, or
  reference-data-service. Also use to add a negative-path (401/400) test alongside an
  existing happy-path Postman item.
---

# Al Ramz Postman e2e test generator

This skill turns a service's real OpenAPI spec into Postman items that are actually worth
running: a request built from the spec's own schema/examples, a self-contained
pre-request auth step, and a test script that checks the things the spec actually
promises — not a scaffold of placeholder assertions someone has to go fill in later.

**Scope boundary** (per this repo's CLAUDE.md §0): only ever write or update files under
`e2e/`. Read `services/<service>/specs/*.yaml` and the service's
`src/main/resources/application*.yml` / controller source for facts — never edit them from
this skill. If a fact you need (a response shape, a public-vs-secured status) isn't in the
spec, go verify it in the actual service source rather than guessing — this skill exists
specifically so the generated tests reflect what's real, not what's plausible.

## 1. Figure out the scope

- **One endpoint / one operationId**: find it directly in the service's spec
  (`services/<service>/specs/apiSpecs.yaml` for `data-validation-service` and
  `reference-data-service`; `services/<service>/specs/openapi.yaml` for
  `alramz-notification-service`).
- **"All endpoints in `<service>`"**: run
  `python3 scripts/list_endpoints.py services/<service>/specs/<file>.yaml` first. It prints
  method/path/operationId/security/path-params/query-params/documented-response-codes for
  every endpoint in one pass — far more reliable than eyeballing a 500-900 line YAML file,
  and gives you a checklist to work through so nothing gets silently skipped.
- **"Add a negative test for X"**: read the existing item for X in whatever
  `e2e/*.postman_collection.json` already has it, and add a sibling item next to it (same
  pattern as the 401/400 siblings in §5 below) rather than restating the whole happy path.

## 2. Pin down the real request path (don't trust the spec's `paths:` key literally)

This repo's three services disagree on whether the spec's path already includes the
`/api/v1` prefix the real controller uses:

| Service | Spec path example | Real request path | Why |
|---|---|---|---|
| `data-validation-service` | `/iban/validate` | `/api/v1/iban/validate` | Controller has class-level `@RequestMapping("/api/v1")` |
| `reference-data-service` | `/info` | `/api/v1/info` | Same — class-level `@RequestMapping("/api/v1")` |
| `alramz-notification-service` | `/api/v1/email/send` | `/api/v1/email/send` | Mapping annotations hardcode the full path; no class-level prefix |

Treat this table as a starting hypothesis, not gospel — re-verify per service with
`grep -rn "@RequestMapping" services/<service>/src/main/java` (or, faster, cross-check
against `company.jwt.permit-all-urls` / `login-url` in the service's
`application-dev.yml`, which lists real, fully-qualified paths). Getting this wrong means
every generated test 404s against a real server while still looking plausible on paper.

## 3. Determine the security requirement — spec and runtime can disagree

An endpoint needs a bearer token unless it's listed in that service's
`company.jwt.permit-all-urls` (see CLAUDE.md §3). The spec's own `security:` block
(top-level, or an operation-level override, including an explicit `security: []` meaning
"none") is a good first signal from `list_endpoints.py`'s output, but
`permit-all-urls` in `application-dev.yml` is the actual source of truth for what the
running service enforces — check both, and trust the YAML over the spec if they disagree.

- **Secured**: build the pre-request auth script (§4) and an `Authorization: Bearer
  {{v_access_token}}` header.
- **Public**: no prerequest script, no `Authorization` header. Don't add either out of
  habit — see Example B in `references/postman-item-examples.md` for why that's actively
  misleading about the real contract.

## 4. Pre-request script: self-contained per item, not folder-ordering-dependent

Every secured item gets its own copy of the login prerequest script (POST
`{{baseurl}}/api/auth/login` with `{{v_consumer}}`/`{{v_consumerPassword}}`, storing the
result in the `v_access_token` **collection** variable). `company.jwt.login-url` is
`/api/auth/login` in every one of this repo's three services — confirmed by grepping
`company.jwt.login-url` across all `application*.yml` files, so this part never varies
per service.

Copy it verbatim from Example A in `references/postman-item-examples.md` rather than
reinventing it — the only repo precedent for this pattern
(`e2e/alramz-onbaording-apis-uie.postman_collection.json`'s `dfm-onboarding` item) does it
exactly this way, specifically so each item still works when run alone or in any order,
not just inside its original collection.

## 5. Test script: assert what the spec actually promises

For each endpoint, build:

1. **Status code** — the spec's primary documented success status (usually `200`).
2. **Envelope/shape check** — read the response schema the spec points at for that status:
   - `GenericResponse` (`required: [responseCode, responseMessage, response]`, defined per
     service in that service's own spec under `components/schemas/GenericResponse`): assert
     `.to.have.all.keys("responseCode", "responseMessage", "response")` since that's a
     closed envelope, plus `responseCode` equals the expected status as a string, plus
     whatever the spec's own `example:` block shows under `response` (don't invent fields
     that aren't in the example).
   - Any other named schema (e.g. `EmailSendResponse`, `ErrorResponse`): assert
     `.to.have.property(...)` per field the schema marks `required`, using
     `.to.have.property` rather than `.to.have.all.keys` whenever the schema isn't a
     provably closed set of fields.
   - If a field's format is `uuid` (e.g. `correlationId`), assert it with a UUID regex
     rather than just presence — cheap and catches a real class of bug (wrong field
     serialized, or a non-UUID placeholder).
3. **Negative-path siblings**, added only when the spec gives you real material for them
   — never invent an error shape:
   - **401** — straightforward for any secured endpoint: send the request with no/garbage
     `Authorization` header, expect `401`. If the spec documents a `401` response schema/
     example for that operation (or inherited from a shared `ErrorResponse` schema), assert
     against it; if not, only assert the status code — don't guess a body shape the spec
     never promised.
   - **400** — only when the spec's `requestBody` schema lists `required` fields *and* the
     spec documents a `400` response for that operation. Drop one required field, assert
     the documented status/shape. Skip this sibling entirely if either condition isn't met,
     rather than fabricating a plausible-looking validation error.

Full worked examples (GenericResponse POST with 401 sibling, public GET, bespoke-schema
POST with a 400 sibling built straight from the spec's own example, path-parameter URLs):
`references/postman-item-examples.md`. Read it before writing the first item of a new
session — it's shorter to adapt a real example than to derive the Postman JSON shape from
scratch each time.

## 6. Assemble and merge, don't hand-splice

- **New collection for a service** (none exists yet at
  `e2e/<service>-api-tests.postman_collection.json`): write the full skeleton yourself
  (`info` block with a fresh `_postman_id` — generate one, e.g. `python3 -c "import uuid;
  print(uuid.uuid4())"` — `name`, `schema`; empty top-level `event`; a `variable` array
  seeded with `v_access_token` only, matching
  `e2e/alramz-onbaording-apis-uie.postman_collection.json`'s pattern), then populate `item`
  directly — there's nothing yet to merge into.
- **Adding/updating items in an existing collection**: write only the new/changed items to
  a scratch JSON file (`{"item": [...]}`), then run
  `python3 scripts/merge_postman_json.py --target e2e/<file>.postman_collection.json --add
  <scratch-file>`. This upserts by item `name`, so regenerating one endpoint's test after a
  spec change replaces it in place instead of leaving a stale duplicate. Don't use the
  `Edit`/`Write` tool directly on a large existing collection file for this — a single
  misplaced brace invalidates the whole file silently until something tries to open it in
  Postman.
- **Environment variables**: if a new variable is needed beyond
  `baseurl`/`v_consumer`/`v_consumerPassword`/`v_access_token`, add it to
  `e2e/local.postman_environment.json` the same way: a scratch file with
  `{"values": [{"key": "...", "value": "...", "type": "default", "enabled": true}]}`, merged
  via the same script (it also matches `values`/`variable` entries, by `key`).
- Keep `baseurl` pointed at the service's **dev** port (see CLAUDE.md §1's port table —
  `data-validation-service` 5001, `alramz-notification-service` 8082, `reference-data-service`
  8081) in `e2e/local.postman_environment.json`; it's one shared environment file for local
  runs against whichever service you're hitting, not one file per service.

## 7. Validate before reporting done

- `python3 -m json.tool e2e/<file>.postman_collection.json > /dev/null` — confirms the file
  is still valid JSON after merging. Do this for every file you touched.
- If `newman` is available (`npx -y newman --version`) and a real local instance of the
  service is actually running, offer to run
  `npx -y newman run e2e/<file>.postman_collection.json -e
  e2e/local.postman_environment.json` as a live check — but this is optional and requires
  real local credentials/DB state (`release/docker-compose.yml` + a filled-in
  `release/.env`), so don't block on it or treat a skipped run as a failure. A valid,
  spec-grounded collection that hasn't been live-run is still useful output.
- Summarize, per endpoint, which status codes you covered (happy path + which negative
  siblings, if any, and why a negative sibling was skipped if it was) — this is the part a
  reviewer actually needs to sanity-check your work against the spec.
