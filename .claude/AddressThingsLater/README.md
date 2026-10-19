# Address later: non-dev profile secrets & actuator exposure

Raised by the automated security review on 2026-10-19, after `reporting-management-service` was
scaffolded. **Not fixed yet.** Applying the fix changes how preprod/prod start up: pods fail fast
unless the secrets below are set in their k8s Secrets.

## Findings

| # | Severity | Issue | Pattern in `application-{preprod,prod}.yml` |
|---|---|---|---|
| 1 | High | JWT signing key falls back to a publicly known placeholder if `JWT_SECRET` is unset, so anyone can forge tokens | `secret: ${JWT_SECRET:defaultSecretKeyChangeMeInProduction…}` |
| 2 | High | Cipher key hardcoded in the repo | `cipher.password: UseMostSecretKey` |
| 3 | Medium | DB password used as plaintext, with a hardcoded default. `alramz-service-scaffold` §4 already forbids `plainPassword` outside dev | `plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD:<hardcoded>}` (and some files hardcode `brok` passwords) |
| 4 | Medium | All actuator endpoints are exposed (env, heapdump, loggers, …) | `management.endpoints.web.exposure.include: "*"` |

## Where it occurs (as of 2026-10-19)

| File(s) | 1 JWT | 2 cipher | 3 plainPassword | 4 actuator `*` |
|---|---|---|---|---|
| `.claude/skills/alramz-service-scaffold/templates/application-profile.yml.template` (root cause: every new service inherits these) | ✔ | ✔ | ✔ | ✔ |
| `onboarding-service` preprod + prod | ✔ | ✔ | ✔ | ✔ |
| `reference-data-service` preprod + prod | ✔ | ✔ | ✔ | ✔ |
| `reporting-management-service` preprod + prod | ✔ | ✔ | ✔ | ✔ |
| `utility-service` preprod + prod | ✔ | ✔ | – | ✔ |
| `wealth-management-service` preprod + prod | ✔ | ✔ | – | ✔ |
| `alramz-notification-service` preprod + prod | – | – | – | – |

The docker profile (`application-docker.yml` / `.template`) probably has the same patterns too. Check it when fixing.

## Suggested fix (preprod/prod only; dev stays as is)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus       # 4: not "*"
  # optional: management.server.port on a separate port the ingress doesn't route

cipher:
  password: ${CIPHER_PASSWORD}                 # 2: no default

company:
  jwt:
    secret: ${JWT_SECRET}                      # 1: no default → startup fails if missing
  datasource:
    middleware:
      password: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD}         # 3: encrypted value
      passwordVector: ${COMPANY_DATASOURCE_MIDDLEWARE_PASSWORD_VECTOR}
      # no plainPassword
```

Optionally, harden the starter (`alramz-api-starter` → `JwtProperties` / JWT auto-config) to refuse
startup when `secret` equals the placeholder or is shorter than 256 bits.

## Order of work

1. Fix the scaffold template first, so new services stop inheriting the problem.
2. Add `JWT_SECRET`, `CIPHER_PASSWORD` and the encrypted DB password/vector to each env's k8s Secrets / Key Vault
   (`infra/` and `k8s/` changes need explicit sign-off per CLAUDE.md).
3. Update each service's preprod/prod files from the table above.
4. Rotate anything already encrypted or signed with the old committed keys, and change the DB passwords
   that were committed. They're in git history, so treat them as compromised.
5. Verify: `mvn -pl services/<svc> -am test`, then a preprod deploy with the secrets set.
