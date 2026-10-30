# Feature request (verbatim from user)

> i want you to implement email and sms send endpoints in alramz-notification-service email sending is already there i want to additionally develop the logic where in email and OTP templates can be used.

User-supplied feature name: `notificatons-sms-email` (slug normalised to `notifications-sms-email`).

## Source specification

The user re-pasted the full "Notification Services — Consolidated API Specification" (Parts 1–4).
It is byte-for-byte the same document already saved at
`.claude/modules/features/notification-graph-gateway/source-design-doc.md` (section "Source
specification"). Read it there; it is not duplicated here.

## Live DDL supplied by the user (authoritative over the spec's §4.2 / §2.1 column tables)

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
);

CREATE TABLE "MIDDLEWARE"."EMAIL_CONFIGURATIONS"
(   "ID" VARCHAR2(20) NOT NULL ENABLE,
    "TEMPLATE_NAME" VARCHAR2(100),
    "NUMBER_OF_PLACEHOLDERS" NUMBER,
    "MAX_ATTEMPTS" NUMBER,
    "FROM_EMAIL" VARCHAR2(50),
    "HAS_ATTACHMENTS" NUMBER,
    "AR_SUBJECT" VARCHAR2(200),
    "EN_SUBJECT" VARCHAR2(200),
    "ATTACHMENTS_MAX_SIZE" VARCHAR2(20),
    "NUMBER_OF_ATTACHMENTS" NUMBER,
    "TYPE_OF_ATTACHMENTS" VARCHAR2(50),
    "IS_BULK" NUMBER,
    "AR_BODY" CLOB,
    "EN_BODY" CLOB,
    "DEFAULT_RECIPIENT" VARCHAR2(200),
    CONSTRAINT "EMAIL_CONFIGURATIONS_PK" PRIMARY KEY ("ID")
);
```

## Context from the previous notification feature (already implemented, Gate 2 approved, uncommitted)

`notification-graph-gateway` (feature 1) hardened the MS Graph send path in
`services/alramz-notification-service`: `POST /api/v1/email/send` returns the programme envelope
(`GenericResponse`), uses MGE-V-0xx/MGE-B-00x codes, requires JWT role `APP_NOTIFICATION`, has a
sender allow-list (`app.email.allowed-senders`), and an in-process `EmailService#sendEmail(EmailRequest)`
designed for templated-email and OTP features to call. See that feature directory's `spec.md` /
`implementation-report.md` / `security_report.md`.

Earlier programme-level decisions (recorded in feature 1's source-design-doc.md) that may now
conflict with this request and must be re-confirmed, not assumed:
- "4 separate features" split (templated email / SMS gateway / OTP) — this request bundles
  email templates + SMS send + OTP templates into one feature.
- "Service owns schema via Liquibase" — the user has now supplied DDL for tables that already
  exist in the shared `MIDDLEWARE` Oracle schema, which suggests DBA-owned existing tables.
- "BOTH contracts" (new `/api/v1` + legacy-path adapters) per feature.
