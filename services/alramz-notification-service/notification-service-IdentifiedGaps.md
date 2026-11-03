# Notification Service — Identified Gaps

Gaps found while migrating legacy Oracle `MIDDLEWARE` data/scripts into this service's
Liquibase changelogs (`src/main/resources/db/changelog/<env>/sql/`). Each gap is kept as-is in
the migrated data unless stated otherwise; resolution needs a business/security owner decision.

| ID | Area | Source | Gap | Impact | Status |
|----|------|--------|-----|--------|--------|
| GAP-001 | OTP config | `005-notification-otp-configuration-data.sql` | `FORGOT_PASSWORD` (101) / `FORGOT_PASSWORD_` (335): English text says "valid for 2 mins", Arabic says 5 mins, `expiry_in_seconds` = 300. | Customer told wrong validity window. | Open |
| GAP-002 | OTP config | `005-...` | `ADD_NEW_SUBSCRIPTION_PLAN` (261) / `ADD_NEW_SUBSCRIPTION_PLAN_` (345): text says 3 mins, `expiry_in_seconds` = 300. | Customer told wrong validity window. | Open |
| GAP-003 | OTP config | `005-...` | `MOBILE_VERIFICATION_GENERIC` (301/338) and `KYC_NON_FINOUX_USERS` (281/346): `sms_message_arabic` contains English text. | Arabic-language users receive English SMS. | Open |
| GAP-004 | OTP config | `005-...` | `CHANGE_PASSWORD` (121/334) messages had leading/trailing newlines in the Oracle export. | Stray line breaks in SMS. | Fixed (trimmed in changelog) |
| GAP-005 | OTP security | `005-...` | Original 13 rows use 4-digit OTPs (10,000 combinations); `_`-suffixed rows use 6 digits. | Weaker brute-force resistance. | Open — security review |
| GAP-006 | OTP security | `005-...` | High `max_validation_attempts`: `ADD_NEW_PRODUCT` (181/342) = 300, `MANUAL_WITHDRAWAL_VERIFICATION` (241/344) = 30. With 4 digits, 300 tries ≈ 3% guess chance. | Brute-force risk on product add / withdrawal. | Open — security review |
| GAP-007 | OTP security | `005-...` | `max_attempts` (resends) = 100000 on 143, 162, 201, 301, 336, 338, 343 — effectively unlimited. Not verified whether code enforces per-window limits or cumulative lockout across resends. | SMS-pumping cost and brute-force across resends. | Open — security review |
| GAP-008 | SMS config | `006-notification-sms-configurations.sql` | Every `ar_body` is English text (ID 22 is also a different message from its `en_body`). | Arabic-language users receive English SMS. | Open |
| GAP-009 | SMS config | `006-...` | Oracle `ID` was auto-filled by `SMS_CONFIGURATIONS_TRIGGER` + `SMS_CONFIGURATIONS_SEQUENCE`; not migrated (sequence DDL wasn't in the export, `ID` is `VARCHAR`). New rows must supply `id` explicitly. | Insert without `id` fails. | Open — decide if auto-id needed |
| GAP-010 | SMS config | `006-...` | Oracle stored numeric/flag columns as `VARCHAR2(20)`; migrated as `INTEGER` (`number_of_placeholders`, `max_attempts`) and `VARCHAR(1)` with `'0'/'1'` checks (`is_bulk`, `mask_placeholders`). Added `UNIQUE (template_name)` (not in Oracle). | Any consumer expecting strings or duplicate template names will differ. | Decided — confirm with consumers |
| GAP-011 | SMS config | `006-...` | `TEST_SMS` (ID 1) test template is seeded into every env including prod. | Test data in prod. | Open |
| GAP-012 | SMS config | `006-...` | `DFM_ONBOARDING_PASSWORD` (61) sends a temporary password in plain SMS (masked in logs via `mask_placeholders = '1'`). | Credential exposure over SMS. | Open — security review |
| GAP-013 | SMS config | `006-...` | No Java code in this service reads `sms_configurations` yet. | Table is seeded but unused. | Open |
| GAP-014 | Email config | `007-notification-email-configurations-data.sql` | Oracle export has no `en_body` / `ar_body`; all 92 rows are seeded with NULL bodies. `TemplatedEmailService` rejects a template with no EN subject or body ("Template misconfiguration"). | **No templated email can be sent** until bodies are loaded. | Open — blocker |
| GAP-015 | Email config | `007-...` | Data is a UAT export: subjects carry a `[UAT]` prefix (except 603, 604, 961) and `default_recipient` holds UAT/dev personal mailboxes. The same file is seeded into every env, including prod. | Prod emails show `[UAT]` and copy internal staff. Personal email addresses are now committed to the repo. | Open — need per-env values |
| GAP-016 | Email config | `007-...` | `default_recipient` is not selected in `sql-queries.xml` and not used by any code. | Legacy default-recipient behavior isn't migrated. | Open |
| GAP-017 | Email config | `007-...` | `default_recipient` on 581 and 681 mixes `,` and `;` separators and repeats `qa@finoux.com`. | Inconsistent parsing if the column is ever used. | Open |
| GAP-018 | Email config | `007-...` | `WASATA_BOT_GENERIC_ERROR` (906) had `is_bulk = NULL`; the table requires `is_bulk` to be non-null. | Insert would fail. | Fixed (set to 0) |
| GAP-019 | Email config | `007-...` | `WASATA_BOT_ADNOC_REPORTS` (602) allows `ZIP` attachments; `TemplatedEmailService` doesn't recognize `ZIP`. | ZIP attachments rejected with a type mismatch. | Open |
| GAP-020 | Email config | `007-...` | Attachment settings disagree: `HTML_TEST` (721) has attachments enabled but `number_of_attachments = 0`. 244/245/246 list attachment types with attachments disabled. 441 allows 1000 attachments with no `attachments_max_size`. | Attachments always rejected for 721. Any attachment sent to 441 fails with "attachments_max_size is not a number". | Open |
| GAP-021 | Email config | `007-...` | Test templates `HTML_TEST` (721) and `TESTING_LAYOUT` (903) are seeded into every env including prod. | Test data in prod. | Open |
| GAP-022 | Email config | `007-...` | Subject defects: 604 EN subject `Insiders Trading_Report` (stray `_`), 461 subject has a trailing space, 561 is missing a space after `[UAT]`. Every `ar_subject` is English text. | Cosmetic / Arabic users get English subjects. | Open |
