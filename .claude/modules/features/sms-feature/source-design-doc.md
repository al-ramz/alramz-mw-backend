# Feature request (verbatim from user)

> sms-feature — I want you to check what is remaining as a part of feature. I want you to provide the missing implementations in this scope. I don't see SMS configurations table As a part of any change log. Whatever things are missing, let me know, and based on that, we will plan this feature.

The user re-pasted the full 4-part "Notification Services — Consolidated API Specification". It is
identical to `.claude/modules/features/notification-graph-gateway/source-design-doc.md` ("Source
specification" section) — read Part 4 (OTP) there.

## Gap analysis shown to the user (2026-10-26)

Already built (uncommitted, Gate 2 approved): Part 2 (`notification-graph-gateway`), Part 1 + Part 3
(`notifications-sms-email`: templated email, SMS send/report, SMS_TABLE dispatch job; changesets
001–003). Missing: all of Part 4 OTP, including the `OTP_CONFIGURATION` table the user meant by
"SMS configurations table" (it holds the SMS/email OTP templates and SENT_VIA).

## User decisions (authoritative)

1. **Scope:** full Part 4 OTP flow — `otp_configuration`, `otp_management`, `otp_attempts` tables;
   `sendOTP`, `validateOTP`, `getMessagesLookup`; OTP generation/hashing; cleanup of expired OTPs /
   daily attempt reset.
2. **"SMS configurations table" = `OTP_CONFIGURATION`.** No separate `sms_configuration` table; SMS
   provider routing stays in `app.sms.*` yml/env as already built.
3. **Schema ownership:** service owns schema via Liquibase in the Postgres `middleware` DB (programme
   decision, see memory/previous features). Next changeset numbers: `004-notification-*` onward,
   `notification` token in every filename, copied into all 5 profile folders.
4. **Seed data:** none for templates (business-owned content). No OTP template rows seeded.
5. **Contract:** new `/api/v1` endpoints only, `GenericResponse` envelope, HTTP-status semantics,
   `@JwtSecured(roles = "APP_NOTIFICATION")`. No legacy-path adapters.
6. **Hash:** server stores SHA-256 as lowercase hex; `validateOTP` compares the caller's `hashedOTP`
   **case-insensitively**. Legacy encoding to be confirmed before go-live (residual risk).
7. **Partial delivery (`EMAIL|SMS`):** 200 if at least one channel delivered, response reports
   per-channel status; only the delivered channel's `otp_management` row is ACTIVE/SENT, the failed
   channel's row is FAILED. All required channels failed → 502.
8. **Test OTPs:** `app.otp.fixed-test-otp-enabled` (true in dev/docker/test, false in preprod/prod);
   startup must fail if it is true under the prod profile. When on: fixed `123456` for service names
   ending `_`, else `1234`, and no SMS/email is actually sent.

## Live DDL supplied by the user for OTP_CONFIGURATION (Oracle — translate to Postgres)

```sql
CREATE TABLE "MIDDLEWARE"."OTP_CONFIGURATION"
(   "MESSAGE_ID" NUMBER NOT NULL ENABLE,
    "SERVICE_NAME" VARCHAR2(200) NOT NULL ENABLE,
    "EXPIRY_IN_SECONDS" NUMBER NOT NULL ENABLE,
    "TOKEN_LENGTH" VARCHAR2(200) NOT NULL ENABLE,
    "SMS_MESSAGE_ARABIC" VARCHAR2(4000),
    "SMS_MESSAGE_ENGLISH" VARCHAR2(4000),
    "MAX_ATTEMPTS" NUMBER NOT NULL ENABLE,
    "SENT_VIA" VARCHAR2(200),
    "EMAIL_MESSAGE_ARABIC" CLOB,
    "EMAIL_MESSAGE_ENGLISH" CLOB,
    "MAX_VALIDATION_ATTEMPTS" NUMBER
) SEGMENT CREATION IMMEDIATE
```

`OTP_MANAGEMENT` / `OTP_ATTEMPTS`: no live DDL — use the spec's Part 4 §2.2/§2.3 columns, widening
`IP_ADDRESS` for IPv6.
