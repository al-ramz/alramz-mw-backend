# Feature Modules

One directory per feature, named as a kebab-case slug of the feature request
(e.g. `add-iban-bulk-validation-endpoint`), directly under
`.claude/modules/features/<feature-slug>/`. Each SDLC pipeline stage writes its
own artifact into that same directory, so all output for a given feature
stays together:

| Stage | Artifact |
|---|---|
| `01_elicit` | `spec.md` |
| `02_plan` | `execution_plan.json` |
| `03_implement` | `implementation-report.md` |
| `04_security` | `security_report.md` |
| `05_pr` | *(defined once that stage exists)* |
