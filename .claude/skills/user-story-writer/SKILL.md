---
name: user-story-writer
description: >
  Produce a full BA/PM module specification — Document control, Provenance tags,
  Decisions log, Scope, Actors, Ownership boundary, Key concepts, Open questions &
  conflicts, Business rules, Requirements traceability, Validation matrix, and User
  stories with Given/When/Then acceptance criteria plus a Test-cases table — in the
  exact structure, ID conventions, and provenance-tagging discipline used across this
  program's BA handoffs (modeled on the "Funds" module doc: Add Funds, Withdraw Funds,
  Bank Details, Transfer History). Use whenever asked to "write user stories," "write a
  BRD/spec," "turn these screenshots/notes into user stories," "document requirements
  for module X," or to revise such a doc after a reviewer/PO gives new answers. Works
  from raw inputs: screenshots, BA notes (docx/bullets), reviewer clarifications given
  across rounds, a solution-design excerpt, or commercial/NFR notes — any subset, since
  gaps are handled by open questions with proposed defaults rather than blocking.
---

# BA/PM module specification writer

This skill turns raw functional inputs (screenshots, BA notes, reviewer answers, a
design excerpt) into the same rigorous, versioned module-specification format used
elsewhere in this program. The defining trait of that format is **total provenance
discipline**: every fact is tagged with where it came from, every gap becomes an open
question with a non-blocking default, and every round of reviewer feedback is logged
rather than silently overwritten. Read this whole file before drafting — the ID and
versioning conventions below are what make a v0.4 doc still legible as a history of
decisions, not just a snapshot.

A worked skeleton lives at `templates/module-spec.md.template` — copy it and fill in
placeholders (`__MODULE_NAME__`, `__CODE__`, etc.) rather than reconstructing the
section order from memory.

## 1. Before drafting: fix the module identity

Ask, or infer from the request, before writing anything:

| Item | Meaning | Example |
|---|---|---|
| `__MODULE_NAME__` | Human title of the module/deliverable | `Funds — Add Funds, Withdraw Funds, Bank Details & Transfer History` |
| `__CODE__` | 2–4 letter prefix used in every ID in the doc | `FN` |
| `__EPIC_REF__` | Epic/ticket link, or `TBD` if unnumbered | `hub#TBD` |

The `__CODE__` prefix is load-bearing: it appears in `BR-__CODE__-N` (business rules),
`US-__CODE__-N` (user stories), `__CODE__-R-N` (requirements), `AC-N.M` (acceptance
criteria, no code — scoped by story number instead), and `TC-NN.NN` (test cases). Pick
it once and never change it — IDs are referenced by other modules and must stay stable
across every future revision (see §5).

If several logically-adjacent screens are being intentionally combined into one
un-numbered module (as Funds combines four flows), record that as the **first row** of
the Decisions log (§3) with the explicit instruction that justified it — don't let the
scope-combination decision go unstated.

## 2. Source intake and provenance tags

Classify every input the requester gives you, and tag every factual claim in the
finished doc with exactly one of:

| Tag | Meaning |
|---|---|
| `[S]` | BA spec / notes (a `.docx`, bullet list, or written requirement) |
| `[SC]` | Screenshot (web or mobile capture) |
| `[D]` | Solution design / technical excerpt (GraphQL/REST operation names, schema) |
| `[R]` | Reviewer / PO decision (an answer given inline, closing an open question) |
| `[X]` | Derived by the writer — an assumption or default, **not yet confirmed** |

If **no solution-design excerpt was supplied**, say so explicitly in the Document
control table (`Source — technical: Not provided. Every GraphQL/REST operation name and
schema shape remains [X]`) rather than inventing operation names — every "Operation"
line in a user story then literally reads `[X]` until a real one is confirmed. The same
honesty applies to unset NFRs, an unconsulted commercial source, or a missing
screenshot for a specific state — record the gap in Document control, don't paper over
it.

**Never assert a fact without a tag.** If you don't have a source for a behavior you
need to describe to keep drafting moving, mark it `[X]` and add a row to the Open
questions table (§2.6 of the template) with a **proposed default** — the doc must never
stall waiting for an answer; it proceeds on the default and gets corrected later.

## 3. Versioning and the Decisions log

- The first draft is **v0.1**. Every subsequent round in which a reviewer/PO gives new
  answers (whether in one batch or several separate clarifications) bumps the version
  (v0.2, v0.3, …) and gets **one row per distinct decision** in the Decisions log table,
  even if several decisions land in the same round.
- A Decisions log row states: what was decided, and its **effect** (which BR/US/OQ IDs
  were added, corrected, or closed). If a decision **reverses** a prior default, say so
  explicitly — e.g. "This reverses v0.1's proposed default, which had assumed X" — don't
  let the correction read as if it were the original assumption.
- The Document control table's `Status` line is a running summary: `Draft v0.4 — adds
  confirmed detail on <X>. All N original open questions remain closed from vN-1; this
  round introduces M new open question(s).` Keep it accurate to the actual OQ count on
  every revision — don't let it drift out of sync with the Open questions table.
- **Never delete or renumber** a Decisions log row, a BR, a US, an AC, or a TC once
  issued. Corrections happen by rewording the row/rule and noting the correction in the
  Decisions log — same discipline as this repo's own rule against editing an
  already-applied Liquibase changeset (CLAUDE.md §4). Downstream references (other
  modules, tickets, test suites) may already point at that exact ID.

## 4. Open questions: never block, always default

Every ambiguity becomes a row in the Open questions & conflicts table:
`ID | Question | Why it's open | Proposed default | Blocking?`. Almost everything should
be `Blocking? = —` (not blocking) precisely because a proposed default lets drafting
continue — reserve an actual "Yes" only for something no default could safely paper
over (e.g., an irreversible financial action with no confirmed rule at all).

When a reviewer closes an OQ:
- Strike through its ID with `~~OQ-N~~` — keep the row, don't remove it.
- Rewrite the **Proposed default** cell to start with `**Closed vN.N — confirmed: …**`
  (or `**Closed vN.N — confirmed, and the proposed default was wrong: …**` when the
  original default turns out incorrect — state the correction plainly, don't bury it).
  End the cell by naming the new/updated BR and US IDs it produced.
- The Document control table's `Blocking open questions` row is then updated to reflect
  the true remaining count (`All nine are now closed`, etc.).

## 5. Section-by-section content rules

Follow the template's section order exactly: Document control → Provenance tags line →
Decisions log → Scope → Actors → Ownership boundary → Key concepts → Open questions &
conflicts → Business rules → Requirements traceability → Validation matrix → User
stories. Rules for each:

- **Scope** — one **in-scope** paragraph naming every entry point, flow, and state
  covered, each fact tagged; then an **out-of-scope table** (`Item | Why | Owner`)
  naming every adjacent flow this doc deliberately does not fully specify (typically
  because it's only referenced as a shared entry point, or belongs to a sibling
  module/team) — don't silently omit an adjacent flow, name it and say why it's out.
- **Actors** — one row per role, including every external system the module talks to
  (CRM, notification/OTP service, a payment gateway, a bank/SWIFT network, etc.), each
  tagged to its source.
- **Ownership boundary** — `Capability | Owner | Our responsibility`, tagged `[X]`
  throughout whenever no design excerpt exists to confirm it against (say this plainly
  in the table's intro line, don't imply confirmation that wasn't given).
- **Key concepts** — one bullet per business term this module introduces or that could
  be confused with a similar-sounding figure/flow elsewhere (e.g., two balance figures
  that must never be conflated). Annotate `(confirmed vN.N)` on any concept a reviewer
  round corrected or locked in.
- **Business rules** — `ID | Rule | Source`, one declarative sentence per rule,
  sequential `BR-__CODE__-N`. A rule confirmed or corrected by review gets a bolded
  `**Confirmed vN.N:**` lead-in inside the Rule cell itself, so the confirmation travels
  with the rule wherever it's quoted.
- **Requirements traceability** — `Req ID | Requirement | Stories | Source`, one
  `__CODE__-R-N` per distinct capability, each mapped to the US ID(s) that satisfy it.
  Every BR should trace to at least one requirement and every requirement to at least
  one story — treat a BR or requirement with no story as a gap, not a footnote.
- **Validation matrix** — `Validation | FE | BFF | External`, one row per check, marking
  which layer is authoritative (client-side checks are UX-only; the BFF/backend row is
  what actually enforces it) — never let a client-only check be misread as the source of
  truth.
- **User stories** — grouped under `### Epic <Letter> — <name>` headers in the order a
  user would actually move through the module (entry points, core happy path steps,
  gating/edge states, ancillary screens last). Use the exact card anatomy in §6.

## 6. User story card anatomy (exact shape)

```markdown
### US-__CODE__-<N> — <short, outcome-oriented title>

> **As a** <role>
> **I want to** <capability, phrased as an action>
> **So that** <the benefit/why>

**Satisfies:** __CODE__-R-<N> · **Operation `[tag]`:** `<graphql/rest op name, or [X] if none supplied>`
**Screen `[tag]`:** <what UI element(s) evidence this — cite screenshot/note content>

**Acceptance criteria**

* **AC-<N>.1** — Given <precondition>, when <action>, then <expected result> (<BR ref(s)>).
* **AC-<N>.2** — …

**Test cases**

|TC ID|AC|Type|Layer|Precondition|Steps|Expected|
|-|-|-|-|-|-|-|
|TC-<NN>.01|<N>.1|Functional|FE|…|…|…|
```

Rules:
- **AC numbering** is `AC-<story number>.<sequence>` — no `__CODE__` prefix, since it's
  already scoped by the story it belongs to.
- Every AC is phrased as **Given/When/Then** and ends with a parenthetical reference to
  the business rule(s) it enforces — an AC with no BR reference is a sign the rule
  table is missing something; add the rule instead of leaving the AC unanchored.
- Include at least one **negative** AC per story that has a server-authoritative gate
  (a direct backend call bypassing the FE) stating the exact error code the BFF returns
  — mirrors the pattern of `FN_PROFILE_UPGRADE_REQUIRED (403)` style codes in the
  reference doc. Don't invent an error code shape different from ones already used
  elsewhere in the same doc.
- **Test case `Type`** is one of `Functional`, `Negative`, `Localisation`. **`Layer`** is
  `FE`, `BFF`, `External`, or a combination (`FE+BFF`) when both must be exercised
  together to prove the AC.
- A story whose behavior is identical on web and mobile (per a BR like "web and mobile
  share identical business logic, only presentation differs") does **not** get separate
  web/mobile stories — say so once in Business rules and write platform differences
  into the `Screen` line and test cases instead, not as duplicate stories.
- Cross-module entry points (an "insufficient balance → Add Funds" style hyperlink
  surfaced from a sibling module) are referenced from a business rule and the Scope's
  out-of-scope table only — never write a full story for a screen this doc doesn't own.

## 7. Revising an existing spec after a new reviewer round

When asked to update a doc already in this format with new answers:
1. Read the whole existing doc first — don't regenerate from scratch; you are adding a
   version, not replacing the document.
2. Bump the version and add the new Decisions log row(s) (§3).
3. Strike through and close the relevant Open questions (§4).
4. Update only the affected BR/US/AC/Actors/Key-concepts rows — reword in place, keep
   IDs stable, and bold the `**Confirmed vN.N:**`/`(confirmed vN.N)` annotation.
5. Re-check the Requirements traceability and Validation matrix tables for any new
   requirement or validation the round's answers introduced (e.g., a new gate implies a
   new validation-matrix row and often a new negative AC/error code).
6. Update the Document control table's `Status` line and `Blocking open questions` row
   last, once the true counts are known.

## 8. Common mistakes to avoid

- Writing a business rule, actor, or AC with **no provenance tag** — every factual claim
  needs one, including `[X]` for an explicit guess.
- Treating a proposed default as settled fact once written down — it stays a **default**
  (not bolded, not "confirmed") until an actual `[R]` entry closes it.
- Silently deleting a closed OQ row, a superseded BR wording, or an old Decisions log
  entry — history is the point of this format; correct in place, don't erase.
- Renumbering any ID across a revision — always append, never renumber.
- Writing a full story for a screen that belongs to a sibling module just because this
  doc's flow links to it — reference it in Scope/out-of-scope and a business rule only.
- Skipping the negative/server-authoritative AC and its error code for any
  screen gated by KYC/profile-tier/session state — client-side gating is UX only; the
  doc must state what the backend does when the FE gate is bypassed.
