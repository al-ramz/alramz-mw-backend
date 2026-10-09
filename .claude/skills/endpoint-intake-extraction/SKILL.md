---
name: endpoint-intake-extraction
description: >
  Extracts the exact facts needed to build a new REST endpoint in this repo's Spring Boot
  services (data-validation-service, alramz-notification-service, reference-data-service) from a
  raw, un-vetted intake/migration document — legacy webMethods/SOAP/other-system API
  documentation, a BA handoff doc, or any free-form write-up of "what this endpoint used to do."
  Cross-references every extracted fact against this repo's own real conventions (howtos, the
  target service's actual GenericResponse schema, actual apiSpecs.yaml precedent) instead of
  trusting the source document's own stated target design, and explicitly separates "preserve this
  behavior" from "known defect in the source, do not replicate silently" from "the source doc's
  assumed target convention doesn't match what this repo actually does." Produces a structured
  extraction written to .claude/modules/features/<feature-slug>/intake-extraction.md, meant to be
  handed to 01_elicit as a feature request that needs far less back-and-forth. Use whenever asked
  to migrate/port/reimplement an endpoint from a legacy system's documentation, or to "figure out
  what this new endpoint needs to do" from an existing spec/intake file rather than a one-line
  request.
---

# Endpoint Intake Extraction

This skill turns a document that was **not** written for this repo — a legacy system's API doc, a
migration-programme intake spec, a BA handoff — into the exact fact set
[`new-endpoint-elicitation-checklist.md`](../../howtos/new-endpoint-elicitation-checklist.md)
requires before a new endpoint can go into `spec.md`. That howto assumes you already know the
answers and just need to know where they go in `apiSpecs.yaml`; this skill is the step before it —
**pulling the answers out of a foreign document and translating them into this repo's actual
conventions**, not the source document's own assumed target design.

The single biggest risk this skill guards against: **a migration intake doc's "target design"
section describes *its own* migration programme's standard, not necessarily this repo's.** Two
documents can both say "Spring Boot 3.x target" and still disagree on the response envelope, the
error-code scheme, or the auth model. Every fact below gets checked against this repo's *actual*
code, not copied from the doc's stated intentions.

## 1. Before extracting: fix the placement

Resolve these first — they're not optional defaults:

- **Which service does this land in?** An existing one (`data-validation-service`,
  `alramz-notification-service`, `reference-data-service`) or a brand-new one? If brand-new, stop
  here and hand off to the `alramz-service-scaffold` skill first — this skill assumes
  `services/<service>/specs/apiSpecs.yaml` already exists to amend.
- **Is this one endpoint or several?** A single intake doc (like a migration-programme API
  reference) often documents multiple operations (e.g. two GET/POST pairs). Extract each
  operation separately through this procedure — don't merge them into one feature unless they
  share one clear business capability.

If either is unclear from the request and the doc itself, this is a genuine blocking gap — ask,
don't guess (same three-part test as the checklist: request states it / repo precedent settles it
/ a stated default applies — none of those resolve "which service," so it's a real question).

## 2. Extraction procedure

Work through these in order, always preferring **this repo's real code** over the source
document's own stated conventions when the two disagree:

| Fact | Where it lives in a typical intake doc | How to resolve it for *this* repo |
|---|---|---|
| **HTTP verb + path** | An "Endpoint Summary" table | Strip any legacy prefix; write the bare path per this repo's convention (controllers add `/api/v1` at class level — see the checklist). |
| **Tag / `operationId`** | Not usually stated explicitly | Derive from the feature/service name, matching the target file's existing tag/casing style — never copy the source system's internal service name (e.g. `getRelationshipManagers`) verbatim without checking it fits this file's naming. |
| **Security** | A "Security Notes" section, often describing the *source* system's (lack of) access control | **Never carry the source system's auth model over, silently or otherwise.** Every endpoint in this repo is JWT-secured by default (`jwt-security-and-public-endpoints.md`). If the source doc says "no ACL enforced," that describes the *legacy* perimeter, not a target requirement — apply this repo's default (secured, `APP_<SERVICE_CONCERN>` role) unless the doc's own migration notes explicitly recommend public, and even then flag it as a decision to confirm, not a default to apply silently. |
| **Request schema** | A "Request Schema" table, often with the source doc's *own* "legacy vs. target format" notes already worked out | Trust the doc's own legacy→target format calls (comma-string→array, `YYYYMMDD`→ISO date, string-typed-numeric→`Integer`) — they're usually already-considered decisions, not something to re-litigate. Still verify the target *type* makes sense against this repo's own Jackson/OpenAPI conventions. |
| **Response envelope** | A "Target Response Model" section describing the doc's own migration-programme envelope | **Do not trust this section blindly.** `Read` the target service's actual `specs/apiSpecs.yaml` → `components.schemas.GenericResponse` first. This repo's real envelope is `responseCode` / `responseMessage` / `response` / `correlationId` — **no separate `errorCode`/`errorMsg` pair**, unlike some migration-programme docs that assume one (confirmed against `GlobalExceptionHandler`, which only ever sets `responseCode`/`responseMessage`/`response`/`correlationId`, never a distinct error-code field, for any `GenericResponse`-based endpoint). If the intake doc's assumed envelope doesn't match the real one, **the real schema wins** — note the discrepancy in the extraction, don't silently pick either one. |
| **Status codes** | Split "Client Input Errors" / "Backend/Provider Errors" tables | Reuse this split directly — it maps onto `spec.md`'s "Edge Cases & Error Behaviors" and the checklist's own status-code guidance. Only keep codes the *new* implementation can actually produce: e.g. a malformed JSON body still trips this repo's own `MethodArgumentNotValidException` → 400 regardless of what the legacy system did, so 400 belongs even if the source doc's own analysis concluded it has "no remaining client-input-error condition" (that conclusion was about the *legacy Flow's* validation, not Spring's). |
| **Business rules / logic** | A numbered "Business Logic Summary" | Extract verbatim into a numbered list, then tag every rule with exactly one of: **Preserve** (required behavior), **Defect — do not replicate silently** (the doc itself calls out a bug, e.g. an AND that should be OR), or **Target-design decision already made upstream** (e.g. a legacy error code reclassified to a different HTTP status). A defect tag is never resolved by silently fixing *or* silently replicating it — always carry it forward as an Open Question for `01_elicit`/the requester to decide, since "faithfully reproduce a known bug" vs. "fix it" is a product call, not an implementation default. |
| **Data mapping** | A "Data Mapping Reference" / table/column appendix | Check `multi-datasource-and-jpa.md` for whether one of this repo's named datasources (`middleware`/`brok`/`integration`) already points at the source schema. If none does, that's a blocking gap (a new datasource needs configuring), not something to default past. |
| **Anti-patterns the source doc itself flags** | A "Findings & Recommendations" appendix | List every one explicitly under "Do NOT carry into Spring Boot" (below) — these are usually genuine wins already identified by whoever wrote the intake doc (e.g. string-substituted SQL instead of bind variables, float-based money arithmetic instead of `BigDecimal`, no access control). Cross-reference against this repo's own equivalent convention so the extraction says what to do *instead*, not just what to avoid. |

## 3. Common pitfalls / anti-patterns

- **Treating the intake doc's "target design" as this repo's target design.** It's the *source
  migration programme's* target — cross-check every structural assumption (envelope shape, error
  taxonomy, correlation ID) against this repo's actual code before accepting it.
- **Silently fixing a source-verified defect.** Recommending a fix is fine; making the fix without
  it surfacing as an explicit decision in `spec.md` is not — a defect in *this repo's* new
  implementation is now something the team owns, not a footnote inherited from the legacy system.
- **Silently replicating a source-verified defect.** The opposite mistake — carrying a bug forward
  just because "that's what the doc described" is exactly as wrong as silently fixing it without
  saying so. Either direction needs to be a visible, deliberate call.
- **Assuming "no auth in the source doc" means "public in the target."** This repo secures
  everything by default; the source system's perimeter (or lack of one) is not evidence about what
  this endpoint's target security should be.
- **Copying the source doc's own error-code scheme wholesale.** A `1012`/"No Data Available"-style
  legacy literal, or a bespoke `errorCode`/`errorMsg` pair, only belongs in the extraction if it
  actually matches what this repo's `GenericResponse`/`GlobalExceptionHandler` produces — verified,
  not assumed.
- **Extracting multiple operations from one intake doc into a single feature.** Keep each
  operation's extraction separable even if they share one output file, so `01_elicit` can scope
  `spec.md` correctly if they turn out to need splitting.

## 4. Output: `intake-extraction.md`

Write the extraction to `.claude/modules/features/<feature-slug>/intake-extraction.md` (same
feature-directory convention every pipeline stage uses — see
`.claude/modules/features/README.md`), with this fixed section order so it transcribes cleanly
into `spec.md`:

1. **Source Document Reference** — doc name/version, what it covers, migration source system.
2. **Target Placement** — service, new vs. existing endpoint, brand-new-service hand-off if
   applicable.
3. **Endpoint Identity** — verb, bare path, tag, candidate `operationId`.
4. **Security** — resolved default (secured/role, or public + why) with rationale.
5. **Request Schema** — fields, types, required/optional, legacy→target format notes already
   resolved.
6. **Response Envelope** — resolved against the target service's *actual* `GenericResponse` (or
   justified custom envelope), with any discrepancy from the source doc's assumed envelope called
   out explicitly.
7. **Status Codes** — client-input vs. backend/provider, only the ones the new implementation can
   really produce.
8. **Business Rules** — numbered, each tagged **Preserve** / **Defect — flagged, not resolved** /
   **Target-design decision**.
9. **Data Mapping** — source table/column → target datasource/entity, with any missing-datasource
   gap called out.
10. **Anti-Patterns to Exclude** — each with what to do instead, per this repo's own conventions.
11. **Open Questions for `01_elicit`** — only the genuinely blocking items left after applying
    request / repo precedent / stated default, per the checklist's own three-part test. Everything
    else in this document is meant to be treated as *already resolved*, not re-asked.

## 5. Handing off to `01_elicit` / `/sdlc`

`01_elicit` does not currently look for `intake-extraction.md` on its own — it only reads what its
own input hands it. Give it this document explicitly: either paste `intake-extraction.md`'s content
as the feature request text, or pass a short feature-request line plus "read
`.claude/modules/features/<feature-slug>/intake-extraction.md` for the full extraction" and confirm
`01_elicit` actually has `Read` access to that path (it does — its Allowed Tools include `Read` and
its Feature Directory is exactly this same directory). Don't assume automatic discovery.

## 6. Checklist

- [ ] Target service (existing vs. new) and operation count resolved before extracting.
- [ ] Every fact resolved against *this repo's real code* (actual `GenericResponse`, actual
      `apiSpecs.yaml` precedent, actual `GlobalExceptionHandler` behavior) — not the source
      document's own stated target design where the two could differ.
- [ ] Security defaulted to secured unless the request itself (not the source system's lack of
      auth) explicitly asks for public.
- [ ] Every business rule tagged Preserve / Defect / Target-design decision — no rule silently
      fixed or silently replicated.
- [ ] Response envelope checked against the target service's real `GenericResponse` schema; any
      mismatch with the source doc's assumed envelope stated explicitly.
- [ ] Data mapping checked against existing named datasources; missing-datasource gaps called out,
      not defaulted past.
- [ ] `intake-extraction.md` written to the feature directory, sections in the fixed order above.
- [ ] Genuinely blocking items only in the Open Questions section — everything else presented as
      already resolved.
