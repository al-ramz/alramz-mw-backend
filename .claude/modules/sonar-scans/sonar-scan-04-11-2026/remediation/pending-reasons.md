### Why each pending issue is still open (33; all MAJOR or lower; no rating affected)

Fix scope for this run: risk-management-service. Out-of-scope rows reuse the 02-11 reasons (`.claude/modules/sonar-scans/remediation/pending-reasons.md`).

| Rule | Count | Where | Blocker / reason | What it would take |
|---|---|---|---|---|
| java:S1075 | 2 | **risk-management** `KnowYourCountryClient:35,36` (`/api/v4/countries_list`, `/api/v4/country_ratings`) | Vendor contract paths. The host is already configurable (`knowyourcountry.base-url`); moving to another vendor API version also means changing `KnowYourCountryRecord`'s mapping, so a path property would be a knob that can't be turned safely on its own | Two `knowyourcountry.*-path` properties with these defaults, if the user wants them |
| java:S1075 | 2 | onboarding `GlobalExceptionHandler:27,28` | Out of scope. Route constants that must match the generated mappings (02-11) | Reuse the generated `PATH_*` constants, as done here for the risk-management legacy controller |
| java:S5738 | 11 | onboarding `External*ServiceConfig`, `WebClientConfig`, `ETradeTokenProviderTest` | Out of scope. Jackson 2 WebClient codecs; need a Jackson 3 migration (02-11) | Jackson 3 WebClient migration |
| java:S1172 | 9 | api-starter test fixtures | Out of scope. Parameters exist to be captured by the audit/logging aspects (02-11) | None recommended |
| java:S2139 | 5 | api-starter `JdbcGlobalConfigurationSettingsRepository` (3), reference-data (2) | Out of scope. Log-and-rethrow sites whose exception types tests rely on (02-11) | Per-site decision on wrap vs log |
| java:S2143 | 2 | api-starter `TokenProvider`, `DataValidationUtil` | Out of scope. jjwt takes `Date`; public `Date` API (02-11) | API change |
| java:S107 | 1 | api-starter `ScheduleInfoBean` | Out of scope. Public starter constructor (02-11) | Builder (API change) |
| java:S2147 | 1 | reference-data-service | Out of scope; not examined in this run | Merge the catch blocks |

### Accepted in SonarCloud

Unchanged from 02-11 (S4502, S2077). No new accept transitions in this run.

### Startup

risk-management-service, notification and utility start; `/actuator/health` returns 403 because it isn't in the JWT permit-all list (pre-existing).
The four Oracle-backed services are BLOCKED_BY_DEPENDENCY by design (`startup-check.sh` points BROK at `localhost:1`).
risk-management ran with a placeholder `KNOWYOURCOUNTRY_API_KEY` and `KNOWYOURCOUNTRY_BASE_URL=http://localhost:1` for the check process only.
