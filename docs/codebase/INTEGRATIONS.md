# External Integrations

## Core Sections (Required)

### 1) Integration Inventory

| System | Type | Purpose | Auth model | Criticality | Evidence |
|--------|------|---------|------------|-------------|----------|
| iban.com API | External REST API | IBAN validation | `api-key` header (`${IBAN_API_KEY}`) | High (core service function) | `services/data-validation-service/src/main/resources/application-dev.yml` (`adapter.external-iban-service`); `config/ExternalIbanServiceConfig.java` |
| VeriPhone API | External REST API | Phone number verification | `api-key` (`${VERIPHONE_API_KEY}`) | High | same file, `adapter.external-veriphone-service`; `config/ExternalVeriPhoneServiceConfig.java` |
| Al Ramz E-Trade system | External REST/SOAP-style API | Existing-data checks (email/passport/username/EID/TP-UUID/mobile exists) + onboarding | Client credentials → bearer token (`ETradeTokenProvider`, `client-id`/`client-secret`, `token-ttl-seconds: 3000`) | High | `application-dev.yml` (`adapter.etrade`); `client/ETradeClient.java`, `config/ETradeTokenProvider.java` |
| Microsoft Graph API | External REST API (SDK v3.8.0, legacy) | Sends email on behalf of a mailbox | Azure AD `ClientSecretCredential` (tenant/client id + secret) | High (notification service's core function) | `services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java` |
| Azure Service Bus / Blob Storage | Azure SDK | Declared as dependencies for `alramz-notification-service`; wiring beyond the POM dependency is `[TODO]` — no `@Configuration` class for these two SDKs was found alongside `GraphConfig` | `[TODO]` | `[TODO]` | `services/alramz-notification-service/pom.xml` (`azure-messaging-servicebus`, `azure-storage-blob`) |
| Azure Key Vault | Azure SDK (`azure-security-keyvault-secrets`) | Declared dependency for runtime secret retrieval | Managed identity (per CLAUDE.md §5) | Medium | `services/alramz-notification-service/pom.xml`; CLAUDE.md §5 "Runtime secrets... come from Key Vault via the platform managed identity" |
| Azure Managed Redis / local Redis | Cache | Backing store for `reference-data-service`'s `GlobalConfigurationSettings` cache | Local: password (`RedisPassword`); Azure mode: `DefaultAzureCredential` token as the Redis password | High for `reference-data-service` | `services/reference-data-service/src/main/java/com/alramz/config/RedisConfig.java` |
| Seq (log server) | Observability sink | Structured log shipping | `SEQ_FIRSTRUN_ADMINPASSWORD` (local dev only) | Medium | CLAUDE.md §2 (`docker run ... datalust/seq`); `com/alramz/logging/logback/SeqAppender.java`, `logging/config/SeqLoggingConfig.java` |
| Azure Container Registry / AKS / APIM / Key Vault (platform) | Azure infra | Image registry, hosting cluster, API gateway, secret store | Service Principal (OIDC) for CI/CD; managed identity at runtime | High (deployment path) | `infra/modules/{acr,aks,apim,key-vault}`, `.github/workflows/cicd.yml` |

### 2) Data Stores

| Store | Role | Access layer | Key risk | Evidence |
|-------|------|---------------|----------|----------|
| `middleware` datasource (Postgres in real envs, H2 for local/tests) | Primary datasource: JWT users/refresh tokens, `api_audit_log`, `schedule_job`, `global_configuration_settings`, DFM onboarding requests | `alramz-api-starter`'s `MiddlewareDataSourceAutoConfiguration` (Hikari + `NamedParameterJdbcTemplate`, `@Primary` in `data-validation-service` via `DatasourceConfiguration`) | Plaintext dev credentials in `application-dev.yml` (`plainPassword`); CLAUDE.md flags `plainPassword` as dev-only | `services/alramz-api-starter/src/main/java/com/alramz/datasource/config/MiddlewareDataSourceAutoConfiguration.java`; `application-dev.yml` |
| `brok` datasource (Oracle) | Secondary datasource for brokerage-system integration | `BrokDataSourceAutoConfiguration`, explicit `@Qualifier` required | `application-dev.yml` commits a plaintext internal IP (`172.25.1.218`), username, and password for this Oracle instance directly in the tracked dev profile | `services/alramz-api-starter/src/main/java/com/alramz/datasource/config/BrokDataSourceAutoConfiguration.java`; `application-dev.yml` |
| `integration` datasource | Third named datasource slot in the starter (enabled per-service via `company.datasource.integration.enabled`) | `IntegrationDataSourceAutoConfiguration` | `[TODO]` — not observed enabled in any committed `application-dev.yml` reviewed | `services/alramz-api-starter/src/main/java/com/alramz/datasource/config/IntegrationDataSourceAutoConfiguration.java` |
| H2 (in-memory) | Local dev/test DB for `data-validation-service`/`reference-data-service`; **runtime-scoped (not test-scoped) dependency in `alramz-notification-service`** | Spring Data JPA | H2 console reachable at `/h2-console` per README — must stay disabled/unreachable outside local dev | README.md; `services/alramz-notification-service/pom.xml` (`h2` with `<scope>runtime</scope>`, not `test`) |
| Redis (local `redis:7-alpine` or Azure Managed Redis) | `reference-data-service` cache of global configuration settings | `RedisTemplate<String,String>` via `RedisConfig` | Azure-mode AAD token used as password may not be refreshed on long-lived connections (see ARCHITECTURE.md §5) | `services/reference-data-service/src/main/java/com/alramz/config/RedisConfig.java`; `release/.env.example` (`REDIS_IMAGE`) |

### 3) Secrets and Credentials Handling

- Credential sources: local dev secrets live in `release/.env` (gitignored, templated by `release/.env.example`); CI/CD secrets are GitHub Actions secrets documented in `.github/SECRETS.md`; runtime secrets in Azure come from Key Vault via managed identity/CSI driver (CLAUDE.md §5).
- Hardcoding checks: `services/data-validation-service/src/main/resources/application-dev.yml` hardcodes several **non-placeholder, tracked-in-git** values — the E-Trade `client-secret`, the `brok` Oracle datasource plaintext password + internal IP, and `cipher.password: UseMostSecretKey`. These are dev-profile values, but they are real strings committed to the repository, not `${VAR:}` placeholders like the IBAN/VeriPhone keys in the same file. See CONCERNS.md for severity.
- `PWProtector` (`com.alramz.utils.PWProtector`) implements the `cipher.password` + encrypted `password`/`passwordVector` scheme CLAUDE.md documents for non-dev profiles — `plainPassword` triggers a dev-only warning path per CLAUDE.md §3, consistent with what's in `EncryptionAutoConfiguration`/`EncryptionProperties`.
- Rotation or lifecycle notes: `[TODO]` — no rotation policy found in-repo; `.github/SECRETS.md` documents *what* secrets exist, not rotation cadence.

### 4) Reliability and Failure Behavior

- Retry/backoff behavior: Spring Cloud Resilience4j circuit-breaker starter is a managed dependency (`spring-cloud-starter-circuitbreaker-reactor-resilience4j` in both the BOM and the starter POM), but no `@CircuitBreaker`/resilience4j usage was found in the service code reviewed — declared capability, not confirmed active usage. `[ASK USER]`
- Timeout policy: per-integration `request-timeout` config values in `application-dev.yml` (e.g. `external-iban-service.request-timeout: 10`, `etrade.request-timeout: "30"`); Oracle `brok` datasource has explicit `connectTimeout`/`readTimeout` (`config/EncryptionAutoConfiguration` siblings) at the JDBC level.
- Circuit-breaker or fallback behavior: `[TODO]` — not directly observed wired to a specific external call in the files reviewed.

### 5) Observability for Integrations

- Logging around external calls: the starter's `RestTemplateLoggingInterceptor`/`WebClientLoggingFilter` log outbound calls; `AbstractRestClient` (extended by `ETradeClient`) is the shared base for outbound REST calls.
- Metrics/tracing coverage: `TraceContextExtractor` abstraction with an `OpenTelemetryTraceContextExtractor` and a `NoopTraceContextExtractor` fallback — OpenTelemetry is an `optional` dependency in `alramz-api-starter/pom.xml`, so tracing degrades to no-op unless the consuming service adds OTel itself.
- Missing visibility gaps: the raw-access-token `LOG.info` call in `GraphConfig` (see CONVENTIONS.md/CONCERNS.md) means the one MS Graph integration's most sensitive credential is *more* visible in logs than it should be, while `SensitiveDataMasker` exists specifically to prevent this class of leak elsewhere.

### 6) Evidence

- `services/data-validation-service/src/main/resources/application-dev.yml`
- `services/alramz-api-starter/src/main/java/com/alramz/datasource/config/*.java`
- `services/alramz-notification-service/src/main/java/com/alramz/config/GraphConfig.java`
- `services/reference-data-service/src/main/java/com/alramz/config/RedisConfig.java`
- `.github/SECRETS.md`, `.github/config/environments.json`
- `release/.env.example`

## Extended Sections (Optional)

Not populated.
