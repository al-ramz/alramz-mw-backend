# `apiCollection.http` — Manual Request Collection

## 1. What this pattern is / when to use it

Every service ships a single `apiCollection.http` at its **root** (`services/<service>/apiCollection.http`,
sibling to `pom.xml`, not under `src/`) — a plain-text collection in the IntelliJ HTTP
Client / VS Code REST Client format: `@variable=value` declarations, requests separated by `###`,
each preceded by a one-line `### <description>` comment. This is the copy-paste-ready way a human
exercises the service's endpoints locally (`http://localhost:<port>`) without hand-typing `curl` or
opening Postman — it is **not** consumed by any build step, test, or CI job; it's a developer
convenience file, and it's tracked in git so the whole team gets it.

Use this doc whenever a feature adds or changes a REST endpoint — the new/changed operation needs
a matching block here, the same way it needs an entry in `specs/apiSpecs.yaml`
(`openapi-contract-first-controllers.md`). Skip it for non-HTTP work (a scheduled job, an
internal-only class) unless that work also exposes a new management endpoint (e.g. a new scheduler
job doesn't need its own block — the shared scheduler-management block already covers it — but a
new `/api/secured/scheduler/...`-style endpoint would).

## 2. Where it lives in this repo

| Service | File |
|---|---|
| `data-validation-service` | `services/data-validation-service/apiCollection.http` |
| `reference-data-service` | `services/reference-data-service/apiCollection.http` |
| `alramz-notification-service` | `services/alramz-notification-service/apiCollection.http` |

All three are real, tracked files (`git ls-files` confirms none are gitignored) — treat edits to
them with the same "don't commit secrets" discipline as any other tracked file (see §5).

## 3. How it works

**Shape, consistent across all three files:**
1. `@baseUrl=http://localhost:<port>` — the service's own local port (`8080`/`8081`/`8082`).
2. `@authToken=<...>` — a variable holding a bearer token, referenced later as
   `Authorization: Bearer {{authToken}}`. Only defined in `data-validation-service` and
   `reference-data-service` today (see the `alramz-notification-service` gap in §5).
3. `GET {{baseUrl}}/api/v1/info` first — the one endpoint every service has, always public.
4. `POST {{baseUrl}}/api/auth/register` then `POST {{baseUrl}}/api/auth/login` — the one-time
   setup flow to obtain a real token for this service's role, if the service has any
   `@JwtSecured` endpoints. `alramz-notification-service` skips this block because its one real
   endpoint (`/api/v1/email/send`) is public.
5. One `### <Name>` block per real business endpoint, in the same order `specs/apiSpecs.yaml`
   declares them, each with `Content-Type: application/json` and (if secured)
   `Authorization: Bearer {{authToken}}`. Where a validation failure is a meaningful case to
   exercise by hand, a **paired negative/edge-case block** immediately follows (e.g. "Validate
   IBAN" + "Validate IBAN -ve test case" with an empty `IBAN` field) — this repo's established
   convention, not just a nice-to-have.
6. A trailing **"Scheduler Management APIs"** block, byte-for-byte the same six requests
   (`stat`/`stop`/`restart`/`refresh/jobs`/`unregister/jobs`/`hardstop`) in every service that has
   `company.scheduler.management-endpoints.enabled` — copied across services rather than shared,
   since this is a flat text file with no include mechanism.

**Request syntax**: `METHOD {{baseUrl}}/path`, then headers (one per line, no blank line between
them), then — for a body — one blank line, then raw JSON (or a bare JSON array for the scheduler
`unregister/jobs` calls). `###` on its own line ends one request and starts the next; the IDE
client parses each `###`-delimited block as an independently runnable request.

## 4. How to add a request for a new/changed endpoint

1. Find the right insertion point: business endpoints are grouped in `specs/apiSpecs.yaml`'s
   order; add your new block near its sibling operations (same tag), before the trailing
   Scheduler Management section.
2. Copy the verb, bare path, and `Content-Type`/`Authorization` header shape straight from what
   you just wrote in `specs/apiSpecs.yaml` and the controller — don't invent a different path or
   forget the prefix the controller's class-level `@RequestMapping` adds (both the spec and this
   file omit it identically, e.g. `{{baseUrl}}/api/v1/iban/validate`).
3. **Only include `Authorization: Bearer {{authToken}}` if the endpoint is secured** — per
   `jwt-security-and-public-endpoints.md`, matching whatever `spec.md`/`execution_plan.json`
   decided. A public endpoint's block has no `Authorization` header, matching how
   `/api/v1/email/send` and `/api/v1/info` appear in these files today.
4. Write a **realistic but obviously-fake** JSON body matching the request schema — a plausible
   IBAN/phone/email shape is fine (as the existing blocks already do), but never a real person's
   data (see §5, pitfall 2).
5. If the endpoint has a validation-failure path worth exercising by hand, add the paired
   `### <Name> -ve test case` block right after, with one field emptied/invalidated — matching
   the existing convention (see the IBAN/phone/email blocks).
6. If this service doesn't yet have `@authToken` defined but your new endpoint is secured, add the
   `@authToken` variable declaration near the top and the `register`/`login` blocks (copy from
   `data-validation-service`'s or `reference-data-service`'s file, updating `consumer`/`roles`/
   `application`/`environment` to this service's own values) — don't reference `{{authToken}}`
   without ever defining it (see §5, pitfall 3).

## 5. Common pitfalls / anti-patterns (real, found in this repo)

1. **A real, signed JWT is hardcoded in `data-validation-service`'s and `reference-data-service`'s
   files today**, tracked in git. Its `iat`/`exp` claims are ~15 minutes apart, so any specific
   committed token is stale within minutes of being generated — low practical risk, but still a
   bad precedent. Don't add a fresh long-lived or production-scoped token when you update this
   file; a short-lived local-only token (or leaving the placeholder for the developer to paste
   their own after running the login request) is the safer pattern going forward.
2. **The `data-validation-service` file's DFM Onboarding example body contains extensive
   realistic-looking PII-shaped test data** — a full name, an EID-number-pattern value, an email
   at the real `@alramz.ae` domain, and base64-encoded image blobs standing in for ID/signature
   attachments. Treat this as committed test-fixture data to be cautious around, not a template to
   copy verbatim into a new onboarding-style body — use obviously-synthetic values (e.g.
   `test.user@example.com`, a placeholder name) for anything new.
3. **`alramz-notification-service`'s file references `{{authToken}}` in its Scheduler Management
   block but never defines `@authToken` anywhere in the file** — a copy-paste artifact from the
   other two services. Run as-is, those six requests would send the literal unresolved string
   `{{authToken}}` as the header value. Don't copy-paste a block referencing a variable without
   also copying (and updating) its declaration.
4. **Copying another service's `register`/`login` block without updating `consumer`/`roles`/
   `application`/`environment`** silently registers a test user under the wrong service identity —
   these four fields must match the target service, not the file you copied from.
5. **This file is not test tooling.** It never gates `03_implement`'s test-first loop or the
   `>80%` coverage requirement — it's a manual aid, updated as a matter of completeness, not a
   thing that needs to pass anything.

## 6. Checklist

- [ ] New `### <Name>` block added, matching the exact verb + bare path from `specs/apiSpecs.yaml`.
- [ ] `Authorization: Bearer {{authToken}}` present only if the endpoint is secured; omitted for a
      public one.
- [ ] Body is realistic but contains no real person's data — synthetic values only.
- [ ] A paired negative/edge-case block added if the endpoint has a validation-failure path worth
      exercising manually.
- [ ] If `@authToken` (and the `register`/`login` blocks) don't exist yet in this service's file
      but the new endpoint needs them, they were added — not referenced without being defined.
- [ ] Block placed near its sibling operations (same tag/feature area), before the trailing
      Scheduler Management section.
