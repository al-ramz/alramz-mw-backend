# API Docs Authentication Wrapper

This guide explains how to add a login gate to your API documentation using GitHub Secrets for secure credential management.

## Setup

### 1. Create GitHub Secrets

In your repository settings:

**Settings** → **Secrets and variables** → **Actions** → **New repository secret**

Create these secrets:

| Secret Name | Value | Example |
|---|---|---|
| `DOCS_USERNAME` | Your username | `admin` |
| `DOCS_PASSWORD` | Your password | `SecurePassword123!` |

### 2. Update Workflow

The workflow automatically injects these secrets into the auth wrapper HTML.

### 3. How It Works

```
User visits docs URL
  ↓
Login form appears (auth wrapper)
  ↓
Enter username/password from GitHub Secrets
  ↓
Credentials validated in browser using SHA256
  ↓
Access granted → View API docs + authentication for APIs
```

## Security Notes

- Credentials are hashed using SHA256 in the browser
- Original password never sent to docs server
- Works completely client-side
- Two layers of auth:
  1. **Docs access** - GitHub Secret credentials
  2. **API access** - Basic Auth or JWT from the docs

## Testing Locally

### Generate with Auth Wrapper

```bash
DOCS_USERNAME=admin DOCS_PASSWORD=password ./scripts/generate-api-docs.sh
```

### Or Set Inline

```bash
export DOCS_USERNAME=admin
export DOCS_PASSWORD=password
./scripts/generate-api-docs.sh
```

Then run the server and use those credentials to login.

## Changing Credentials

1. Update GitHub Secrets
2. Workflow automatically rebuilds with new credentials
3. Old links still work (redirect to login)

## Environment Variables for Local Dev

```bash
# .env.local (don't commit!)
DOCS_USERNAME=dev_user
DOCS_PASSWORD=dev_pass

# Load and generate
source .env.local
./scripts/generate-api-docs.sh
```

## Workflow Configuration

The workflow reads from GitHub Secrets:

```yaml
- name: Build with auth
  env:
    DOCS_USERNAME: ${{ secrets.DOCS_USERNAME }}
    DOCS_PASSWORD: ${{ secrets.DOCS_PASSWORD }}
  run: ./scripts/generate-api-docs.sh
```

Both secrets must be set for the workflow to work.
