# e2e — Postman collection

Run [AlramzMWCollection.postman_collection.json](AlramzMWCollection.postman_collection.json) from the
terminal with [Newman](https://github.com/postmanlabs/newman), Postman's command-line collection runner.

| File | Purpose |
|------|---------|
| `AlramzMWCollection.postman_collection.json` | Folders `onboarding-apis` (login, IBAN, veriphone, DFM onboarding) and `reference-data-apis` (health, global-config cache, relationship managers) |
| `dev.postman_environment.json` | Environment variables — values are blank on purpose; pass them with `--env-var` |

## 1. Install Newman

Requires Node.js 16+.

```bash
node --version
npm install -g newman
newman --version
```

Or run it without a global install by replacing `newman` with `npx newman` in the commands below.

## 2. Start the services

Start the services you want to test (see [docs/COMMANDS.md](../docs/COMMANDS.md)). Default local ports
with the `dev` profile: `onboarding-service` → `8080`, `reference-data-service` → `8081`.

## 3. Environment variables

| Variable | Example | Used by |
|----------|---------|---------|
| `baseurl` | `http://localhost` | all requests |
| `onboarding-service-port` | `8080` | `onboarding-apis` |
| `reference-data-service-port` | `8081` | `reference-data-apis` |
| `v_onboarding_consumer` | — | `onboarding-apis/01_login` |
| `v_consumer` / `v_consumerPassword` | — | pre-request login of `onboarding-apis/02–04` |
| `v_reference_data_consumer` / `v_consumer_password` | — | pre-request login of `reference-data-apis` |

`v_access_token` is set automatically by each request's pre-request script. Keep credentials in
shell variables — don't write them into the environment JSON or commit them.

```bash
export E2E_CONSUMER="<consumer>"
export E2E_PASSWORD="<password>"
```

## 4. Run the collection

Run from the repo root.

**Everything:**

```bash
newman run e2e/AlramzMWCollection.postman_collection.json \
  -e e2e/dev.postman_environment.json \
  --env-var "baseurl=http://localhost" \
  --env-var "onboarding-service-port=8080" \
  --env-var "reference-data-service-port=8081" \
  --env-var "v_onboarding_consumer=$E2E_CONSUMER" \
  --env-var "v_consumer=$E2E_CONSUMER" \
  --env-var "v_consumerPassword=$E2E_PASSWORD" \
  --env-var "v_reference_data_consumer=$E2E_CONSUMER" \
  --env-var "v_consumer_password=$E2E_PASSWORD"
```

**One folder** (add `--folder <name>`):

```bash
# onboarding-service only
newman run e2e/AlramzMWCollection.postman_collection.json \
  -e e2e/dev.postman_environment.json --folder onboarding-apis \
  --env-var "baseurl=http://localhost" \
  --env-var "onboarding-service-port=8080" \
  --env-var "v_onboarding_consumer=$E2E_CONSUMER" \
  --env-var "v_consumer=$E2E_CONSUMER" \
  --env-var "v_consumerPassword=$E2E_PASSWORD" \
  --env-var "v_consumer_password=$E2E_PASSWORD"

# reference-data-service only
newman run e2e/AlramzMWCollection.postman_collection.json \
  -e e2e/dev.postman_environment.json --folder reference-data-apis \
  --env-var "baseurl=http://localhost" \
  --env-var "reference-data-service-port=8081" \
  --env-var "v_reference_data_consumer=$E2E_CONSUMER" \
  --env-var "v_consumer_password=$E2E_PASSWORD"
```

**Useful flags:**

| Flag | Effect |
|------|--------|
| `--folder <folder or request>` | Run only that folder/request (repeatable) |
| `--bail` | Stop on the first failing test |
| `-k` / `--insecure` | Skip TLS verification (self-signed certs) |
| `--verbose` | Print full request/response details |
| `--delay-request <ms>` | Wait between requests |
| `-r cli,junit --reporter-junit-export e2e/results/junit.xml` | Also write a JUnit report (for CI) |

The command exits non-zero when any test fails.

### HTML report (optional)

```bash
npm install -g newman-reporter-htmlextra
newman run e2e/AlramzMWCollection.postman_collection.json -e e2e/dev.postman_environment.json \
  <--env-var flags as above> \
  -r cli,htmlextra --reporter-htmlextra-export e2e/results/report.html
```
