# Deployment & Promotion Guide — GitHub Actions + GitOps + ArgoCD + AKS

How `alramz-mw-backend` services get from a merged PR to production across four isolated AKS
clusters: **dev → qa → preprod → prod**.

> Identifiers (subscription/tenant IDs, client IDs) are shown as `<PLACEHOLDERS>`. Never paste real
> values into this file, workflows, or commits — they belong in GitHub Secrets / Key Vault.

---

## 1. Current state (what this repo does today)

| Area | Today | Verdict |
|---|---|---|
| Build | `cicd.yml` → `detect-services` → `mvn -pl services/<svc> -am package` → `build-service` (buildx, push, outputs **digest**) | **Keep** — build-once-by-digest is exactly what GitOps promotion needs |
| Image tag | `<sha>` on `feature/*`, `dev-<sha8>` on `dev`, semver on `workflow_dispatch version=` | **Keep** |
| Registry | Defaults to `GITHUB_REGISTRY` (GHCR); ACR `alramzregistry.azurecr.io` optional | **Change** — use ACR for all clusters (private, AKS-attached, no pull secrets) |
| Trivy scan | `scan-trivy: false` | **Change** — enable, fail on CRITICAL |
| Deploy | `deploy-aks` action: `az aks get-credentials` + `kubectl apply` (push model), dev only | **Replace** with GitOps commit + ArgoCD pull |
| Deploy templates | `deploy-aks` calls `.github/deployment/scripts/render-template.sh` | **Broken** — script does not exist in this repo |
| Drift | `drift-detect` action compares cluster vs register | **Retire** — ArgoCD `selfHeal` + OutOfSync status does this |
| Catalogue | `service-register.yml` updated by CI with tag/digest/history | **Keep** as audit trail of builds (the GitOps repo becomes source of truth for *deployed* state) |
| Promotion | `create-promotion-prs` opens code PRs `feature/*→dev→qa→preprod→prod` | **Replace** — promotion becomes a digest-bump PR in the GitOps repo; no code re-merge per env |
| Env config | `.github/config/environments.json` has **both** `test` and `qa` | **Fix** — `qa` is canonical; delete `test` |
| Branches | `dev`, `test`, `preprod`, `main` | **Simplify** — see §2.3 |
| K8s manifests | None in this repo; Kustomize base/overlays + ArgoCD apps exist in `alramz-mw-oss/k8s/` but target one cluster (`kubernetes.default.svc`) and a personal repo | **Port** into new GitOps repo, per-cluster |
| Secrets | `application-prod.yml` has hard-coded fallbacks for `ETRADE_CLIENT_SECRET`, `JWT_SECRET`, `COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD` | **Fix before prod** — remove defaults, source from Key Vault (§8) |

Deployable services (keys of `service-register.yml`, sizing from each `services/<svc>/service.yaml`):

| Service | Port | CPU / Mem | Replicas |
|---|---|---|---|
| onboarding-service | 8080 | 0.5 / 1Gi | 1–5 |
| alramz-notification-service | **8082** | 0.5 / 1Gi | 1–5 |
| reference-data-service | 8080 | 0.5 / 1Gi | 1–5 |
| utility-service | 8080 | 0.5 / 1Gi | 1–5 |
| reporting-management-service | 8080 | 0.5 / 1Gi | 1–5 |
| wealth-management-service | 8080 | 0.5 / 1Gi | 1–5 |

`alramz-common-bom` and `alramz-api-starter` are libraries (published to GitHub Packages by
`publish-maven`), never deployed. Note: `wealth-management-service` and `reporting-management-service`
have a `service.yaml` but are **not yet keys in `service-register.yml`**, so CI will not build them
until they are added there.

---

## 2. Target architecture

```
                    ┌──────────────────────────── alramz-mw-backend (app repo) ───────────────────────────┐
  PR merged to main │ detect-services → mvn package → docker build → Trivy → push ACR (digest)          │
                    │ → update-gitops-dev: commit digest to gitops envs/dev   (GitHub App token)        │
                    └───────────────────────────────────────────────┬─────────────────────────────────────┘
                                                                    │ git commit
                    ┌──────────────────────────── alramz-mw-gitops (GitOps repo) ─────────────────────────┐
                    │ apps/base/<svc>/          shared manifests                                          │
                    │ envs/dev/      ← CI writes                                                         │
                    │ envs/qa/       ← promote.yml PR (QA lead approves)                                  │
                    │ envs/preprod/  ← promote.yml PR (release manager approves)                          │
                    │ envs/prod/     ← promote.yml PR (CAB + env approval)                                │
                    └──────┬──────────────────┬───────────────────┬────────────────────┬─────────────────┘
                     pull  │            pull  │             pull  │              pull  │
               ┌───────────▼───┐   ┌──────────▼────┐   ┌──────────▼─────┐   ┌──────────▼────┐
               │ alramz-dev-aks│   │ alramz-qa-aks │   │alramz-preprod- │   │alramz-prod-aks│
               │  ArgoCD (own) │   │  ArgoCD (own) │   │aks ArgoCD (own)│   │ ArgoCD (own)  │
               │  auto-sync    │   │  auto-sync    │   │  auto-sync     │   │ MANUAL sync   │
               └───────┬───────┘   └───────┬───────┘   └────────┬───────┘   └───────┬───────┘
                       └───────────────────┴──── pull images by digest ─────────────┘
                                         alramzregistry.azurecr.io (ACR)
```

### 2.1 Principles
1. **Build once, promote the digest.** The exact `image@sha256:…` tested in dev is what reaches prod.
   No rebuilds per environment, no per-environment Maven profiles baked into images.
2. **Git is the only deploy API.** Nobody (and no pipeline) runs `kubectl apply` against qa/preprod/prod.
   CI has *no* credentials to any cluster.
3. **Pull, not push.** Each cluster's ArgoCD pulls only its own `envs/<env>` path. A compromised dev
   cluster cannot touch prod.
4. **Promotion = PR.** Every environment change is a reviewable, revertable commit with an approver.
5. **Config by environment, code by image.** Spring profile, replicas, resources, Key Vault name,
   hostnames live in the overlay; the jar is identical.

### 2.2 Why ArgoCD per cluster
Each AKS cluster runs its own ArgoCD, managing only `https://kubernetes.default.svc`. There is no hub
holding prod credentials, prod outages in ArgoCD cannot be caused by lower envs, and each ArgoCD can
have its own SSO group mapping (e.g. only platform team can sync prod).

### 2.3 Branching (app repo)
With GitOps, environments are **folders in the GitOps repo, not branches in the app repo**.

- `main` — trunk; every merge builds and deploys to dev.
- `feature/*` — short-lived; CI builds/tests (image tagged `<sha>`), **does not deploy**.
- `release/x.y` *(optional)* — only if you need to hotfix prod while `main` has moved on.
- Retire the long-lived `dev` / `test` / `preprod` code branches (see §10). Keeping them means
  re-merging code per environment, which re-triggers builds and breaks "build once".

If the team wants a staging branch for integration, keep **only** `dev` as an integration branch and
treat it as the trunk; do not create `qa`/`preprod` branches.

---

## 3. GitOps repo layout (`al-ramz/alramz-mw-gitops`)

Port from `alramz-mw-oss/k8s/apps` — same Kustomize base/overlay idea, extended for multi-cluster.

```
alramz-mw-gitops/
├── apps/base/
│   ├── onboarding-service/
│   │   ├── kustomization.yaml
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── serviceaccount.yaml        # workload identity
│   │   ├── hpa.yaml
│   │   ├── pdb.yaml
│   │   ├── ingress.yaml
│   │   └── secretproviderclass.yaml   # Key Vault CSI
│   ├── alramz-notification-service/ …
│   └── … (one dir per deployable service)
├── envs/
│   ├── dev/
│   │   ├── kustomization.yaml         # images: digests, namespace, patches
│   │   ├── env-patch.yaml             # SPRING_PROFILES_ACTIVE, KV name, host
│   │   └── onboarding-service-patch.yaml …
│   ├── qa/ …
│   ├── preprod/ …
│   └── prod/ …
├── argocd/
│   ├── dev/      { project.yaml, root-app.yaml }
│   ├── qa/       { … }
│   ├── preprod/  { … }
│   └── prod/     { … }
├── .github/
│   ├── CODEOWNERS
│   └── workflows/{promote.yml, validate.yml}
└── README.md
```

### 3.1 Base — `apps/base/onboarding-service/`

`kustomization.yaml`
```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
labels:
  - pairs:
      app.kubernetes.io/name: onboarding-service
      app.kubernetes.io/part-of: alramz-mw
    includeSelectors: false
resources:
  - serviceaccount.yaml
  - deployment.yaml
  - service.yaml
  - hpa.yaml
  - pdb.yaml
  - ingress.yaml
  - secretproviderclass.yaml
```

`deployment.yaml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: onboarding-service
spec:
  replicas: 1                     # HPA owns this after first sync
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxSurge: 1, maxUnavailable: 0 }
  selector:
    matchLabels: { app: onboarding-service }
  template:
    metadata:
      labels:
        app: onboarding-service
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: onboarding-service
      securityContext: { runAsNonRoot: true, runAsUser: 1000, fsGroup: 1000 }
      containers:
        - name: onboarding-service
          image: onboarding-service          # rewritten by envs/<env> images:
          ports: [{ name: http, containerPort: 8080 }]
          env:
            - { name: SPRING_PROFILES_ACTIVE, value: dev }   # patched per env
            - { name: JAVA_TOOL_OPTIONS, value: "-XX:MaxRAMPercentage=75" }
          envFrom:
            - secretRef: { name: onboarding-service-kv }     # synced from Key Vault by CSI
          volumeMounts:
            - { name: kv, mountPath: /mnt/secrets, readOnly: true }
          resources:
            requests: { cpu: 500m, memory: 1Gi }
            limits:   { memory: 1Gi }
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 5
            failureThreshold: 36            # up to 3 min for Spring + Liquibase
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 20
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 10
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: false   # Spring writes to /tmp; mount emptyDir if you flip this
      volumes:
        - name: kv
          csi:
            driver: secrets-store.csi.k8s.io
            readOnly: true
            volumeAttributes: { secretProviderClass: onboarding-service-kv }
```
> Spring Boot enables `/actuator/health/{liveness,readiness}` automatically when it detects
> Kubernetes; `spring-boot-starter-actuator` is already on the classpath via `alramz-api-starter`.
> The existing `smoke-test` action already probes `/actuator/health`.

`serviceaccount.yaml`
```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: onboarding-service
  annotations:
    azure.workload.identity/client-id: "<MANAGED_IDENTITY_CLIENT_ID>"   # patched per env
```

`service.yaml`
```yaml
apiVersion: v1
kind: Service
metadata:
  name: onboarding-service
spec:
  selector: { app: onboarding-service }
  ports: [{ name: http, port: 80, targetPort: http }]
```

`hpa.yaml`
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: onboarding-service
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: onboarding-service }
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource: { name: cpu, target: { type: Utilization, averageUtilization: 70 } }
```

`pdb.yaml`
```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: onboarding-service
spec:
  maxUnavailable: 1
  selector:
    matchLabels: { app: onboarding-service }
```

`ingress.yaml`
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: onboarding-service
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt   # or your corporate issuer
spec:
  ingressClassName: webapprouting.kubernetes.azure.com   # AKS app-routing add-on; or nginx
  tls: [{ hosts: [onboarding-service.example.invalid], secretName: onboarding-service-tls }]
  rules:
    - host: onboarding-service.example.invalid            # patched per env
      http:
        paths:
          - path: /
            pathType: Prefix
            backend: { service: { name: onboarding-service, port: { name: http } } }
```

`secretproviderclass.yaml`
```yaml
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata:
  name: onboarding-service-kv
spec:
  provider: azure
  parameters:
    usePodIdentity: "false"
    clientID: "<MANAGED_IDENTITY_CLIENT_ID>"   # patched per env
    keyvaultName: "<KEYVAULT>"                 # patched per env
    tenantId: "<AZURE_TENANT_ID>"
    objects: |
      array:
        - |
          objectName: jwt-secret
          objectType: secret
        - |
          objectName: middleware-db-password
          objectType: secret
        - |
          objectName: etrade-client-secret
          objectType: secret
  secretObjects:
    - secretName: onboarding-service-kv
      type: Opaque
      data:
        - { objectName: jwt-secret,             key: JWT_SECRET }
        - { objectName: middleware-db-password, key: COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD }
        - { objectName: etrade-client-secret,   key: ETRADE_CLIENT_SECRET }
```

Repeat per service. For `alramz-notification-service` set `containerPort: 8082`.

### 3.2 Environment overlay — `envs/dev/kustomization.yaml`

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: alramz-dev
resources:
  - ../../apps/base/onboarding-service
  - ../../apps/base/alramz-notification-service
  - ../../apps/base/reference-data-service
  - ../../apps/base/utility-service
  # - ../../apps/base/reporting-management-service
  # - ../../apps/base/wealth-management-service
patches:
  - path: env-patch.yaml
    target: { kind: Deployment }
  - path: onboarding-service-patch.yaml
# ── written by CI (dev) / promote.yml (qa, preprod, prod). Do not hand-edit. ──
images:
  - name: onboarding-service
    newName: alramzregistry.azurecr.io/onboarding-service
    digest: sha256:<digest>
  - name: alramz-notification-service
    newName: alramzregistry.azurecr.io/alramz-notification-service
    digest: sha256:<digest>
```

`envs/dev/env-patch.yaml` (applied to every Deployment)
```yaml
- op: replace
  path: /spec/template/spec/containers/0/env/0/value
  value: dev
```

`envs/dev/onboarding-service-patch.yaml` (per-service host/identity/KV)
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata: { name: onboarding-service }
spec:
  tls: [{ hosts: [onboarding-service.dev.alramz.io], secretName: onboarding-service-tls }]
  rules:
    - host: onboarding-service.dev.alramz.io
      http:
        paths:
          - path: /
            pathType: Prefix
            backend: { service: { name: onboarding-service, port: { name: http } } }
---
apiVersion: secrets-store.csi.x-k8s.io/v1
kind: SecretProviderClass
metadata: { name: onboarding-service-kv }
spec:
  provider: azure
  parameters:
    keyvaultName: alramz-dev-key-vault
    clientID: "<DEV_MANAGED_IDENTITY_CLIENT_ID>"
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: onboarding-service
  annotations:
    azure.workload.identity/client-id: "<DEV_MANAGED_IDENTITY_CLIENT_ID>"
```

Per-environment values (from `.github/config/environments.json`):

| | dev | qa | preprod | prod |
|---|---|---|---|---|
| Cluster | alramz-dev-aks | alramz-qa-aks | alramz-preprod-aks | alramz-prod-aks |
| Namespace | alramz-dev | alramz-qa | alramz-preprod | alramz-prod |
| Spring profile | dev | qa *(add `application-qa.yml`; today only `test` exists)* | preprod | prod |
| Key Vault | alramz-dev-key-vault | alramz-qa-key-vault | alramz-preprod-key-vault | alramz-prod-key-vault |
| Domain | dev.alramz.io | qa.alramz.io | preprod.alramz.io | alramz.io |
| HPA min/max | 1/2 | 1/3 | 2/5 | 3/10 |
| ArgoCD sync | auto + selfHeal | auto + selfHeal | auto + selfHeal | **manual** (or sync window) |

> **Profile mismatch:** services ship `application-test.yml`, not `application-qa.yml`. Either rename
> to `application-qa.yml` (preferred, matches the chosen env name) or set `SPRING_PROFILES_ACTIVE=test`
> in the qa overlay until renamed.

### 3.3 ArgoCD bootstrap — `argocd/<env>/`

`argocd/prod/project.yaml`
```yaml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata: { name: alramz-mw, namespace: argocd }
spec:
  description: Al Ramz middleware — prod
  sourceRepos: ["https://github.com/al-ramz/alramz-mw-gitops.git"]
  destinations:
    - { server: "https://kubernetes.default.svc", namespace: alramz-prod }
  clusterResourceWhitelist:
    - { group: "", kind: Namespace }
  namespaceResourceWhitelist:
    - { group: "*", kind: "*" }
  syncWindows:                    # prod only: deploys allowed Sun–Thu 06:00–14:00 UTC
    - kind: allow
      schedule: "0 6 * * 0-4"
      duration: 8h
      applications: ["*"]
      manualSync: true
  roles:
    - name: prod-deployers
      policies:
        - p, proj:alramz-mw:prod-deployers, applications, sync, alramz-mw/*, allow
      groups: ["<ENTRA_GROUP_OBJECT_ID_PLATFORM_TEAM>"]
```

`argocd/prod/root-app.yaml`
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: alramz-mw-prod
  namespace: argocd
  finalizers: [resources-finalizer.argocd.argoproj.io]
spec:
  project: alramz-mw
  source:
    repoURL: https://github.com/al-ramz/alramz-mw-gitops.git
    targetRevision: main
    path: envs/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: alramz-prod
  syncPolicy:
    # prod: NO automated block → an operator clicks Sync after the PR merges.
    syncOptions: [CreateNamespace=true, PruneLast=true]
  revisionHistoryLimit: 10
```
For dev/qa/preprod, same file with `path: envs/<env>`, `namespace: alramz-<env>` and:
```yaml
  syncPolicy:
    automated: { prune: true, selfHeal: true }
    syncOptions: [CreateNamespace=true, PruneLast=true]
```
One Application per environment (all services in it) is enough at 6 services. Switch to an
`ApplicationSet` with a git-directory generator (one app per service) only when you need per-service
sync/rollback in the ArgoCD UI.

---

## 4. One-time platform setup (repeat per cluster)

Prereqs: cluster exists (Terraform `infra/stacks/<env>` in `alramz-mw-oss`), you have `az`, `kubectl`,
`helm`, `argocd` CLI, and cluster-admin on that cluster.

```bash
ENV=dev                                 # dev | qa | preprod | prod
RG=alramz-$ENV-rg AKS=alramz-$ENV-aks

# 1. Cluster add-ons
az aks update -g $RG -n $AKS --enable-oidc-issuer --enable-workload-identity
az aks enable-addons -g $RG -n $AKS --addons azure-keyvault-secrets-provider
az aks approuting enable -g $RG -n $AKS             # managed nginx ingress
az aks update -g $RG -n $AKS --attach-acr alramzregistry   # AcrPull for kubelet identity; no pull secrets

# 2. Per-env managed identity → Key Vault, federated to each service's ServiceAccount
az identity create -g $RG -n alramz-$ENV-workload-mi
MI_CLIENT_ID=$(az identity show -g $RG -n alramz-$ENV-workload-mi --query clientId -o tsv)
MI_PRINCIPAL=$(az identity show -g $RG -n alramz-$ENV-workload-mi --query principalId -o tsv)
KV_ID=$(az keyvault show -n alramz-$ENV-key-vault --query id -o tsv)
az role assignment create --assignee-object-id $MI_PRINCIPAL --assignee-principal-type ServicePrincipal \
  --role "Key Vault Secrets User" --scope $KV_ID
ISSUER=$(az aks show -g $RG -n $AKS --query oidcIssuerProfile.issuerUrl -o tsv)
for SVC in onboarding-service alramz-notification-service reference-data-service utility-service; do
  az identity federated-credential create -g $RG --identity-name alramz-$ENV-workload-mi \
    --name $SVC --issuer $ISSUER --subject system:serviceaccount:alramz-$ENV:$SVC --audiences api://AzureADTokenExchange
done

# 3. ArgoCD (pin the chart version; same version on all four clusters)
az aks get-credentials -g $RG -n $AKS
helm repo add argo https://argoproj.github.io/argo-helm
helm upgrade --install argocd argo/argo-cd -n argocd --create-namespace \
  --version <PINNED_CHART_VERSION> \
  --set configs.params."server\.insecure"=false \
  --set dex.enabled=false        # use Entra ID OIDC via configs.cm.oidc.config

# 4. Give ArgoCD read-only access to the GitOps repo (GitHub App preferred over PAT)
argocd login <argocd-$ENV-host> --sso
argocd repo add https://github.com/al-ramz/alramz-mw-gitops.git \
  --github-app-id <APP_ID> --github-app-installation-id <INSTALL_ID> \
  --github-app-private-key-path ./gitops-reader.pem

# 5. Bootstrap — the only manual kubectl apply ever done on this cluster
kubectl apply -n argocd -f argocd/$ENV/project.yaml -f argocd/$ENV/root-app.yaml
```

Also fix `.github/config/environments.json` in the app repo: delete the `test` block, fill
`qa.keyVaultTenantId` / `managedIdentityClientId` via secrets rather than the file.

---

## 5. App repo CI changes (`.github/workflows/cicd.yml`)

### 5.1 Triggers
```yaml
on:
  push:
    branches: [main, 'feature/*']     # was: dev, feature/*
    paths-ignore: ['docs/**', '**.md']
  pull_request:
    branches: [main]
  workflow_dispatch: { … unchanged: version, registry-target … }
```
Set `REGISTRY_TARGET` default to `AZURE_REGISTRY` so every cluster pulls from ACR.

### 5.2 Jobs (keep / change / remove)

| Job | Action |
|---|---|
| `resolve-env`, `detect-services`, `publish-maven` | keep |
| `build` | keep; add `mvn … verify` (tests) before package on `main`, set `scan-trivy: true` |
| `update-catalogue` | keep (build audit trail) |
| `deploy-dev` | **replace** with `update-gitops-dev` (below) |
| `smoke-test` | keep; make it wait for ArgoCD sync first |
| `drift-detect-dev` | **remove** (ArgoCD selfHeal) |
| `create-promotion-prs` | **remove** (moves to GitOps repo `promote.yml`) |
| `.github/actions/deploy-aks`, `drift-detect` | delete once cut over |

### 5.3 `update-gitops-dev`
```yaml
  update-gitops-dev:
    needs: [detect-services, build]
    if: github.ref == 'refs/heads/main' && needs.detect-services.outputs.has_deploy_changes == 'true'
    runs-on: ubuntu-latest
    concurrency: { group: gitops-dev, cancel-in-progress: false }   # serialize commits
    steps:
      - uses: actions/download-artifact@v4
        with: { path: build-artifacts, pattern: build-output-* }

      - id: app-token
        uses: actions/create-github-app-token@v1
        with:
          app-id: ${{ secrets.GITOPS_APP_ID }}
          private-key: ${{ secrets.GITOPS_APP_PRIVATE_KEY }}
          owner: al-ramz
          repositories: alramz-mw-gitops

      - uses: actions/checkout@v4
        with:
          repository: al-ramz/alramz-mw-gitops
          token: ${{ steps.app-token.outputs.token }}
          path: gitops

      - uses: imranismail/setup-kustomize@v2

      - name: Bump dev digests
        working-directory: gitops/envs/dev
        run: |
          set -euo pipefail
          for f in ../../../build-artifacts/*/build-output.json; do
            SVC=$(jq -r .service "$f"); DIGEST=$(jq -r .digest "$f")
            kustomize edit set image "$SVC=alramzregistry.azurecr.io/$SVC@$DIGEST"
            echo "- $SVC → $DIGEST" >> ../../../summary.md
          done
          cat ../../../summary.md >> "$GITHUB_STEP_SUMMARY"

      - name: Commit
        working-directory: gitops
        run: |
          git config user.name "alramz-gitops-bot"
          git config user.email "alramz-gitops-bot@users.noreply.github.com"
          git add envs/dev/kustomization.yaml
          git diff --cached --quiet && exit 0
          git commit -m "deploy(dev): ${{ github.repository }}@${GITHUB_SHA::8}" -m "$(cat ../summary.md)"
          git pull --rebase && git push
```
Dev deploys **without a PR**: CI commits straight to `main` of the GitOps repo, restricted to
`envs/dev/**` via a ruleset bypass for the GitHub App only.

### 5.4 Smoke test after sync
```yaml
  smoke-test:
    needs: [update-gitops-dev, detect-services]
    steps:
      - uses: actions/checkout@v4
      - name: Wait for rollout to new digest
        run: |
          for i in $(seq 1 40); do
            code=$(curl -s -o /dev/null -w '%{http_code}' https://${{ matrix.service }}.dev.alramz.io/actuator/health)
            [ "$code" = 200 ] && exit 0; sleep 15
          done; exit 1
      - uses: ./.github/actions/smoke-test
        with: { service-name: '${{ matrix.service }}', expected-url: 'https://${{ matrix.service }}.dev.alramz.io' }
```
Remove `continue-on-error: true` from the current smoke step — a failing dev smoke test must block
promotion. Stronger option: expose a read-only ArgoCD API token for dev only and run
`argocd app wait alramz-mw-dev --health --sync --timeout 600`.

### 5.5 Secrets needed in the app repo
| Secret | Purpose |
|---|---|
| `AZURE_CLIENT_ID` / `AZURE_TENANT_ID` / `AZURE_SUBSCRIPTION_ID` | OIDC login with **AcrPush only** (no AKS role any more) |
| `GITOPS_APP_ID` / `GITOPS_APP_PRIVATE_KEY` | GitHub App with `contents:write` on `alramz-mw-gitops` only |
| `GH_TOKEN` | keep for catalogue commit; can switch to the App token |

Remove `MANAGED_IDENTITY_CLIENT_ID_DEV` from CI — identity now lives in the overlay.

---

## 6. Promotion procedures

All promotions run one workflow in the GitOps repo. It **copies digests from the source env overlay
to the target env overlay** and opens a PR. Nothing is rebuilt.

### 6.1 `alramz-mw-gitops/.github/workflows/promote.yml`
```yaml
name: Promote
on:
  workflow_dispatch:
    inputs:
      from:     { type: choice, options: [dev, qa, preprod], required: true }
      services: { description: 'comma list or "all"', default: all, required: true }
      ticket:   { description: 'Change/Jira ticket (required for preprod→prod)', required: false }

permissions: { contents: write, pull-requests: write }

jobs:
  promote:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: imranismail/setup-kustomize@v2
      - name: Resolve target
        id: t
        run: |
          case "${{ inputs.from }}" in
            dev) echo "to=qa" >> $GITHUB_OUTPUT ;;
            qa) echo "to=preprod" >> $GITHUB_OUTPUT ;;
            preprod) echo "to=prod" >> $GITHUB_OUTPUT
                     [ -n "${{ inputs.ticket }}" ] || { echo "::error::ticket required for prod"; exit 1; } ;;
          esac
      - name: Copy digests
        id: copy
        run: |
          set -euo pipefail
          FROM=envs/${{ inputs.from }}/kustomization.yaml TO=envs/${{ steps.t.outputs.to }}
          if [ "${{ inputs.services }}" = all ]; then
            SVCS=$(yq -r '.images[].name' "$FROM")
          else
            SVCS=$(echo "${{ inputs.services }}" | tr ',' '\n')
          fi
          : > body.md
          for S in $SVCS; do
            NEW=$(yq -r ".images[] | select(.name==\"$S\") | .digest" "$FROM")
            OLD=$(yq -r ".images[] | select(.name==\"$S\") | .digest // \"none\"" "$TO/kustomization.yaml")
            [ "$NEW" = "$OLD" ] && continue
            (cd "$TO" && kustomize edit set image "$S=alramzregistry.azurecr.io/$S@$NEW")
            echo "| $S | \`${OLD:7:12}\` | \`${NEW:7:12}\` |" >> body.md
          done
          [ -s body.md ] || { echo "Nothing to promote"; exit 0; }
          echo "changed=true" >> $GITHUB_OUTPUT
      - name: Open PR
        if: steps.copy.outputs.changed == 'true'
        env: { GH_TOKEN: '${{ github.token }}' }
        run: |
          TO=${{ steps.t.outputs.to }}; BR=promote/$TO/$(date +%Y%m%d-%H%M%S)
          git switch -c $BR
          git -c user.name=promote-bot -c user.email=promote-bot@users.noreply.github.com \
            commit -am "promote(${{ inputs.from }}→$TO): ${{ inputs.services }} ${{ inputs.ticket }}"
          git push -u origin $BR
          { echo "Promotion **${{ inputs.from }} → $TO** ${{ inputs.ticket }}"; echo;
            echo "| service | from | to |"; echo "|---|---|---|"; cat body.md; } > pr.md
          gh pr create --base main --head $BR --title "promote: ${{ inputs.from }} → $TO" \
            --body-file pr.md --label "promote/$TO"
```

`validate.yml` (on every PR in the GitOps repo):
```yaml
on: pull_request
jobs:
  render:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: imranismail/setup-kustomize@v2
      - run: |
          for e in envs/*/; do kustomize build "$e" > /tmp/$(basename $e).yaml; done
          # Optional: kubeconform -strict -ignore-missing-schemas /tmp/*.yaml
```

`CODEOWNERS`:
```
/envs/dev/        @al-ramz/backend-devs
/envs/qa/         @al-ramz/qa-leads
/envs/preprod/    @al-ramz/release-managers
/envs/prod/       @al-ramz/release-managers @al-ramz/platform-team
/apps/base/       @al-ramz/platform-team
/argocd/          @al-ramz/platform-team
```
Branch protection on GitOps `main`: require PR + CODEOWNERS review + `validate` status check; allow
only the CI GitHub App to bypass, and only for `envs/dev/**`.

### 6.2 Deploy to dev (automatic)
1. Developer opens PR `feature/xyz → main` in `alramz-mw-backend`; CI builds and tests (no deploy).
2. PR merged → CI on `main`: build → Trivy → push to ACR → `update-catalogue` → `update-gitops-dev`
   commits the new digest to `envs/dev`.
3. dev ArgoCD (auto-sync, ~3 min poll or instant via GitHub webhook to `/api/webhook`) rolls out.
4. `smoke-test` job verifies `/actuator/health` → green check on the commit.

### 6.3 Promote dev → qa
Gate: dev smoke green, feature acceptance done by devs.
1. GitOps repo → Actions → **Promote** → `from=dev`, `services=onboarding-service` (or `all`).
2. Review the PR: digest diff table. `@qa-leads` approve (CODEOWNERS on `envs/qa/`).
3. Merge → qa ArgoCD auto-syncs.
4. QA runs the regression suite. Reuse the Postman collections under `e2e/` with the qa environment
   (`newman run e2e/<collection>.json -e e2e/env/qa.json`), ideally as a post-sync job.

### 6.4 Promote qa → preprod
Gate: QA sign-off recorded on the release ticket; no open Sev-1/Sev-2.
1. Optional but recommended: cut a release version — run app-repo `cicd.yml` via
   `workflow_dispatch` with `version=1.4.0` *on the same commit*. This retags the same digest (no code
   change) and gives prod a human-readable version. (Or simply `git tag v1.4.0 <sha>` on the app repo.)
2. **Promote** → `from=qa`. Release manager approves.
3. Merge → preprod ArgoCD auto-syncs.
4. Run E2E + performance smoke against preprod; verify DB migrations (Liquibase in
   `data-validation-service`) ran cleanly against the preprod database snapshot.

### 6.5 Promote preprod → prod
Gate: preprod sign-off, approved change ticket (CAB), inside the allowed window.
1. **Promote** → `from=preprod`, `ticket=CHG-1234` (required).
2. PR needs release-manager **and** platform-team approval. Optionally make the merge depend on a
   GitHub **Environment** `production` with required reviewers by running a no-op job
   `environment: production` in `validate.yml` for `promote/prod` PRs.
3. Merge. Prod ArgoCD shows **OutOfSync** (manual sync policy).
4. Platform engineer, during the sync window:
   ```bash
   argocd app diff alramz-mw-prod          # confirm only image digests changed
   argocd app sync alramz-mw-prod
   argocd app wait alramz-mw-prod --health --timeout 900
   ```
5. Verify: `kubectl -n alramz-prod rollout status deploy/<svc>`, health endpoints, dashboards.
6. Tag the GitOps commit: `git tag prod-$(date +%Y%m%d-%H%M) && git push --tags`.

### 6.6 Hotfix to prod
1. Branch `hotfix/xyz` from the **app-repo commit currently in prod** (get the SHA from the image tag
   or `service-register.yml` history).
2. Fix, PR to `main` (and to `release/x.y` if one exists). CI builds a digest.
3. Fast-track: run Promote `dev→qa→preprod→prod` back-to-back, each with its approvals. Do **not**
   hand-edit `envs/prod` with an unbuilt/unscanned image.

---

## 7. Rollback

| Situation | Action |
|---|---|
| Bad release in any env (normal path) | `git revert <promotion-commit>` in the GitOps repo → PR → merge → ArgoCD syncs previous digest (prod: manual sync) |
| Prod is down, need seconds | `argocd app rollback alramz-mw-prod <history-id>` **then** immediately do the git revert above, otherwise selfHeal / the next sync re-applies the bad digest. (Prod has no auto-sync, so the rollback holds until the next manual sync.) |
| Single service only | Promote with `services=<svc>` from an env still on the good digest, or `kustomize edit set image` to the previous digest from `service-register.yml` `history[]` via PR |
| Bad DB migration | Image rollback does **not** undo Liquibase changesets. Ship forward-compatible (expand/contract) migrations and a rollback changeset; verify in preprod first |

---

## 8. Secrets and configuration per environment

1. **One Key Vault per environment** (`alramz-<env>-key-vault`). Secret names identical across envs;
   values differ.
2. Mounted with **Secrets Store CSI driver + workload identity** (`SecretProviderClass` in §3.1);
   synced to a k8s Secret and injected via `envFrom`. No secrets in Git, none in CI.
3. **Remove hard-coded fallbacks** in `services/*/src/main/resources/application-{preprod,prod}.yml`,
   e.g. in onboarding-service:
   ```yaml
   client-secret: ${ETRADE_CLIENT_SECRET}            # was ${ETRADE_CLIENT_SECRET:R@mZS4qR5t}
   secret: ${JWT_SECRET}                             # was ${JWT_SECRET:defaultSecretKey…}
   plainPassword: ${COMPANY_DATASOURCE_MIDDLEWARE_PLAINPASSWORD}
   ```
   With no default, a missing secret fails startup instead of running prod with a known password.
   Rotate any value that has been committed.
4. Non-secret config (URLs, feature flags) → Spring profile yml or a per-env ConfigMap patch in the
   overlay. Spring profile is selected by `SPRING_PROFILES_ACTIVE` in `envs/<env>/env-patch.yaml`.
5. Optional: add the **Reloader** controller so pods restart when a Key Vault secret rotates.

---

## 9. Governance checklist

- [ ] App repo `main`: PR required, CI green (build, tests, Trivy, gitleaks), 1+ review.
- [ ] GitOps repo `main`: PR required, CODEOWNERS, `validate` check; bypass only for CI App on `envs/dev/**`.
- [ ] GitHub Environments `qa`, `preprod`, `production` with required reviewers.
- [ ] Azure OIDC identity used by CI has **AcrPush only**; no cluster RBAC.
- [ ] Separate managed identity + Key Vault per environment; federated credentials per ServiceAccount.
- [ ] Each ArgoCD: Entra ID SSO, local admin disabled, RBAC: devs read-only on preprod/prod, sync on prod only for platform team.
- [ ] ArgoCD Notifications → Teams/Slack on sync failure and degraded health.
- [ ] Prod sync window configured; prod `syncPolicy` has no `automated` block.
- [ ] Image signing (cosign/Notation) + AKS image integrity policy (optional, phase 2).
- [ ] ArgoCD chart versions pinned and upgraded dev → qa → preprod → prod like any other change.

---

## 10. Migration plan from the current setup

1. **Create `al-ramz/alramz-mw-gitops`**; copy `alramz-mw-oss/k8s/apps/base/*` and reshape per §3
   (add SA, HPA, ingress, SecretProviderClass; actuator probes; port 8082 for notification).
   Add `envs/{dev,qa,preprod,prod}`, `argocd/{env}`, `promote.yml`, `validate.yml`, `CODEOWNERS`.
2. **Create the GitHub App** (`alramz-gitops-bot`): contents rw on GitOps repo only. Add secrets to app repo.
3. **Normalise naming to `qa`**: remove `test` from `environments.json`; add `application-qa.yml`
   (copy of `application-test.yml`) per service; retire the `test` branch.
4. **Switch registry to ACR** (`REGISTRY_TARGET: AZURE_REGISTRY`) and attach ACR to all four clusters.
5. **Bootstrap dev cluster** (§4). Seed `envs/dev` with the current dev digests from
   `service-register.yml`. Delete the old kubectl-applied resources (or let ArgoCD adopt them by
   matching names, then prune).
6. **Change `cicd.yml`** (§5): trigger on `main`, add `update-gitops-dev`, remove `deploy-dev`,
   `drift-detect-dev`, `create-promotion-prs`; enable Trivy; make smoke blocking.
7. Run one full cycle on dev. Then bootstrap qa → promote → validate; preprod; prod (manual sync).
8. Retire `dev`/`test`/`preprod` code branches after the last in-flight PRs merge to `main`.
   Delete `.github/actions/deploy-aks` and `drift-detect`.
9. Add `reporting-management-service` and `wealth-management-service` to `service-register.yml`
   (so CI builds them) and to each `envs/<env>/kustomization.yaml` when ready.

---

## 11. Cheat-sheet

```bash
# What's running where?
kustomize build envs/prod | grep image:
argocd app get alramz-mw-prod
kubectl -n alramz-prod get deploy -o custom-columns=NAME:.metadata.name,IMAGE:.spec.template.spec.containers[0].image

# Promote from CLI
gh workflow run promote.yml -R al-ramz/alramz-mw-gitops -f from=qa -f services=all
gh pr list -R al-ramz/alramz-mw-gitops --label promote/preprod

# Sync / wait / diff / rollback
argocd app diff alramz-mw-prod
argocd app sync alramz-mw-prod && argocd app wait alramz-mw-prod --health
argocd app history alramz-mw-prod
argocd app rollback alramz-mw-prod <ID>

# Debug a pod
kubectl -n alramz-qa logs deploy/onboarding-service -f
kubectl -n alramz-qa describe pod -l app=onboarding-service
kubectl -n alramz-qa get secretproviderclass,secret

# Local render check before a PR
kustomize build envs/qa | kubeconform -strict -ignore-missing-schemas
```
