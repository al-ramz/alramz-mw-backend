# Worked examples

These are complete, real Postman item JSON blocks built from this repo's actual specs.
Adapt them by analogy to whatever endpoint you're generating for — don't treat the
placeholders as a rigid fill-in-the-blanks form; the three examples cover the three
shapes of response this repo actually has (GenericResponse, a bespoke typed response,
and the legacy snake_case shape), and most new endpoints will match one of them.

## Example A — secured POST, GenericResponse shape, with a 401 sibling

Source: `services/data-validation-service/specs/apiSpecs.yaml`, `/iban/validate`
(`operationId: validateIBAN`). Real request path is `/api/v1/iban/validate` — the spec's
`paths:` key omits the `/api/v1` prefix that `DataValidationController`'s class-level
`@RequestMapping("/api/v1")` adds (see SKILL.md step 2 on why you must verify this per
service rather than trust the spec's path key literally).

```json
{
  "name": "IBAN validate - 200 valid IBAN",
  "event": [
    {
      "listen": "prerequest",
      "script": {
        "type": "text/javascript",
        "exec": [
          "pm.sendRequest({",
          "    url: pm.collectionVariables.get(\"baseurl\") + \"/api/auth/login\",",
          "    method: \"POST\",",
          "    header: { \"Content-Type\": \"application/json\" },",
          "    body: {",
          "        mode: \"raw\",",
          "        raw: JSON.stringify({",
          "            consumer: pm.collectionVariables.get(\"v_consumer\"),",
          "            consumerPassword: pm.collectionVariables.get(\"v_consumerPassword\")",
          "        })",
          "    }",
          "}, function (err, response) {",
          "    if (err) {",
          "        console.error(\"auth prerequest failed:\", err);",
          "        return;",
          "    }",
          "    pm.collectionVariables.set(\"v_access_token\", response.json().accessToken);",
          "});"
        ]
      }
    },
    {
      "listen": "test",
      "script": {
        "type": "text/javascript",
        "exec": [
          "const jsonData = pm.response.json();",
          "",
          "pm.test(\"HTTP status code is 200\", function () {",
          "    pm.response.to.have.status(200);",
          "});",
          "",
          "pm.test(\"Response has the GenericResponse envelope\", function () {",
          "    pm.expect(jsonData).to.have.all.keys(\"responseCode\", \"responseMessage\", \"response\");",
          "});",
          "",
          "pm.test(\"responseCode is '200'\", function () {",
          "    pm.expect(jsonData.responseCode).to.eql(\"200\");",
          "});",
          "",
          "pm.test(\"response.bank_data is present\", function () {",
          "    pm.expect(jsonData.response).to.have.property(\"bank_data\");",
          "});"
        ]
      }
    }
  ],
  "request": {
    "method": "POST",
    "header": [
      { "key": "Authorization", "value": "Bearer {{v_access_token}}", "type": "text" },
      { "key": "Content-Type", "value": "application/json", "type": "text" }
    ],
    "body": {
      "mode": "raw",
      "raw": "{\n    \"IBAN\": \"AE460260001015862843301\"\n}",
      "options": { "raw": { "language": "json" } }
    },
    "url": {
      "raw": "{{baseurl}}/api/v1/iban/validate",
      "host": ["{{baseurl}}"],
      "path": ["api", "v1", "iban", "validate"]
    }
  },
  "response": []
}
```

Why these choices:
- The prerequest script reads `{{baseurl}}`/`{{v_consumer}}`/`{{v_consumerPassword}}` from
  **collection** variables (not environment, since Postman resolves either transparently,
  but `pm.collectionVariables` matches what the existing example collection already does)
  and is copy-pasteable into any item without depending on another item having run first —
  this is deliberate: Newman/CI runs items in file order by default, and a shared "run
  jwt-auth first" folder item is one accidental reorder away from silently breaking every
  other test.
- `.to.have.all.keys(...)` is used for the GenericResponse envelope because that schema's
  three documented properties are the *complete* set the spec promises (it's a closed
  envelope) — use `.to.have.property(...)` per key instead when a schema is open
  (`additionalProperties` allowed, or you're only asserting a few of many documented
  fields) so the test doesn't start failing the day a new, legitimate field gets added.
- The 400 sibling for this endpoint would come from stripping the one `required` field
  (`IBAN`) and asserting the status/response the spec's own `400` response block
  documents — build it the same way as Example C's 401 sibling, substituting the status
  and the stripped field.

## Example B — public GET, no auth, GenericResponse shape

Source: `services/reference-data-service/specs/apiSpecs.yaml`, `/info`
(`operationId: getInfo`). Confirmed public via
`company.jwt.permit-all-urls: [/api/v1/info]` in
`application-dev.yml` — so this item has **no** prerequest script at all, and no
`Authorization` header. Don't add either just out of habit; a public endpoint that
suddenly "requires" a bearer token in your generated test is actively misleading about
what the real contract is.

```json
{
  "name": "Service info - 200",
  "event": [
    {
      "listen": "test",
      "script": {
        "type": "text/javascript",
        "exec": [
          "const jsonData = pm.response.json();",
          "",
          "pm.test(\"HTTP status code is 200\", function () {",
          "    pm.response.to.have.status(200);",
          "});",
          "",
          "pm.test(\"Response has the GenericResponse envelope\", function () {",
          "    pm.expect(jsonData).to.have.all.keys(\"responseCode\", \"responseMessage\", \"response\");",
          "});",
          "",
          "pm.test(\"response.service is reference-data-service\", function () {",
          "    pm.expect(jsonData.response.service).to.eql(\"reference-data-service\");",
          "});"
        ]
      }
    }
  ],
  "request": {
    "method": "GET",
    "header": [],
    "url": {
      "raw": "{{baseurl}}/api/v1/info",
      "host": ["{{baseurl}}"],
      "path": ["api", "v1", "info"]
    }
  },
  "response": []
}
```

## Example C — secured POST with a bespoke response, plus 401/400 siblings taken straight from the spec

Source: `services/alramz-notification-service/specs/openapi.yaml`, `/api/v1/email/send`
(`operationId: sendEmail`). Note the spec's `paths:` key here already includes
`/api/v1` — this service's controller hardcodes the full path per `@GetMapping`/mapping
annotation rather than using a class-level `@RequestMapping("/api/v1")` prefix, so do
**not** blindly apply the "+/api/v1" rule from Example A; always verify per service
(SKILL.md step 2).

This endpoint's spec documents concrete `401` and `400` response examples
(`ErrorResponse` schema) — when that's true, build the negative items straight from
those documented examples instead of guessing what an error body looks like:

```json
{
  "name": "Send email - 400 missing required field",
  "event": [
    {
      "listen": "prerequest",
      "script": {
        "type": "text/javascript",
        "exec": [
          "pm.sendRequest({",
          "    url: pm.collectionVariables.get(\"baseurl\") + \"/api/auth/login\",",
          "    method: \"POST\",",
          "    header: { \"Content-Type\": \"application/json\" },",
          "    body: {",
          "        mode: \"raw\",",
          "        raw: JSON.stringify({",
          "            consumer: pm.collectionVariables.get(\"v_consumer\"),",
          "            consumerPassword: pm.collectionVariables.get(\"v_consumerPassword\")",
          "        })",
          "    }",
          "}, function (err, response) {",
          "    if (!err) pm.collectionVariables.set(\"v_access_token\", response.json().accessToken);",
          "});"
        ]
      }
    },
    {
      "listen": "test",
      "script": {
        "type": "text/javascript",
        "exec": [
          "const jsonData = pm.response.json();",
          "",
          "pm.test(\"HTTP status code is 400\", function () {",
          "    pm.response.to.have.status(400);",
          "});",
          "",
          "pm.test(\"responseCode is '400'\", function () {",
          "    pm.expect(jsonData.responseCode).to.eql(\"400\");",
          "});",
          "",
          "pm.test(\"error message mentions the missing field\", function () {",
          "    pm.expect(jsonData.error).to.be.a(\"string\").and.not.empty;",
          "});"
        ]
      }
    }
  ],
  "request": {
    "method": "POST",
    "header": [
      { "key": "Authorization", "value": "Bearer {{v_access_token}}", "type": "text" },
      { "key": "Content-Type", "value": "application/json", "type": "text" }
    ],
    "body": {
      "mode": "raw",
      "raw": "{\n    \"subject\": \"Test Email from Spring Boot\",\n    \"from\": \"sender@example.com\",\n    \"body\": \"<h1>Hello</h1><p>This is a test email body.</p>\"\n}",
      "options": { "raw": { "language": "json" } }
    },
    "url": {
      "raw": "{{baseurl}}/api/v1/email/send",
      "host": ["{{baseurl}}"],
      "path": ["api", "v1", "email", "send"]
    }
  },
  "response": []
}
```

The required `to` field was dropped from the body to trigger this 400 (`EmailRequest`'s
schema marks it required). The test asserts `error` is a non-empty string rather than
matching the spec's exact example wording (`"Phone number is required"` in the spec's
`missingField` example, which doesn't even match this field) — exact error-message
copy is usually the first thing to drift when someone tweaks a validation message, and
asserting it word-for-word would make the generated suite flaky for no real benefit.

## Example D — path parameters

Source: `services/reference-data-service/specs/apiSpecs.yaml`,
`/cache/entries/{cacheKey}/flush` (`operationId: flushCache`). Postman path variables
use `:name` syntax in both `url.raw` and `url.path`, plus a matching entry in
`url.variable`:

```json
"url": {
  "raw": "{{baseurl}}/api/v1/cache/entries/:cacheKey/flush",
  "host": ["{{baseurl}}"],
  "path": ["api", "v1", "cache", "entries", ":cacheKey", "flush"],
  "variable": [
    { "key": "cacheKey", "value": "relationship-managers" }
  ]
}
```

Pick the path-variable's example `value` from the spec's own parameter example if it has
one; otherwise use one of the spec's own other documented cache-mapping names (as above)
rather than a made-up string, so the request has a realistic chance of returning a real
`200` instead of a `404` for an unknown key.
