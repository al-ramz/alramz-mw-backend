# Local Setup Guide - API Documentation

Complete step-by-step guide to run API documentation locally with authentication and interactive Scalar explorer.

---

## 📋 Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start (5 minutes)](#quick-start-5-minutes)
3. [Detailed Steps](#detailed-steps)
4. [Troubleshooting](#troubleshooting)
5. [Next Steps](#next-steps)

---

## Prerequisites

### Required

- **Python 3** (built-in on most systems)
- **Git** (to clone/work with repo)
- **Node.js 18+** (optional, for advanced features)
- **jq** (optional, for regenerating docs)

### Check if You Have Them

```bash
# Check Python
python3 --version

# Check Git
git --version

# Check Node.js (optional)
node --version

# Check jq (optional)
jq --version
```

### Install Missing Tools

**macOS:**
```bash
# Install Homebrew if not installed
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install jq
brew install jq

# Install Node.js (optional)
brew install node
```

**Linux (Ubuntu/Debian):**
```bash
# Install jq
sudo apt-get install jq

# Install Node.js (optional)
sudo apt-get install nodejs npm
```

**Windows:**
- Use WSL2 (Windows Subsystem for Linux 2)
- Or install Python and Node.js from official websites

---

## Quick Start (5 minutes)

### Step 1: Navigate to Project

```bash
cd /Users/gtalele/Library/CloudStorage/OneDrive-AlRamz/Desktop/workspace/alramz-mw-oss
```

### Step 2: Start Local Server

```bash
cd api-docs-build
python3 -m http.server 8000
```

**Output should show:**
```
Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ...
```

### Step 3: Open in Browser

Open this URL in your browser:
```
http://localhost:8000
```

### Step 4: Login

**Credentials:**
```
Username: admin
Password: SecurePass123!
```

### Step 5: Explore APIs

1. Select API from sidebar
2. Choose environment from server dropdown
3. Paste JWT token (optional)
4. Click "Try it out" on any endpoint

---

## Detailed Steps

### Step 1: Clone/Update Repository

```bash
# Navigate to project directory
cd /Users/gtalele/Library/CloudStorage/OneDrive-AlRamz/Desktop/workspace/alramz-mw-oss

# Check git status
git status

# Pull latest changes (if needed)
git pull origin main
```

### Step 2: Regenerate Documentation (Optional)

If you've changed OpenAPI specs, regenerate docs:

```bash
# From project root
./scripts/generate-api-docs.sh

# Or with custom credentials
DOCS_USERNAME=youruser DOCS_PASSWORD=yourpass ./scripts/generate-api-docs.sh
```

### Step 3: Create Auth Wrapper (Optional)

If you want different login credentials:

```bash
cd api-docs-build

DOCS_USERNAME=myuser DOCS_PASSWORD=mypass \
node ../scripts/create-auth-wrapper.js .

# Rename files
mv index.html docs.html
mv auth-index.html index.html
```

### Step 4: Start the Server

**Option A: Python (Recommended)**

```bash
cd api-docs-build
python3 -m http.server 8000
```

**Option B: Node.js HTTP Server**

```bash
cd api-docs-build
npx http-server -p 8000
```

**Option C: Python 2 (if Python 3 not available)**

```bash
cd api-docs-build
python -m SimpleHTTPServer 8000
```

### Step 5: Access Documentation

```
http://localhost:8000
```

### Step 6: Test Features

#### Test Login
```
Username: admin
Password: SecurePass123!
Click "Access Documentation"
```

#### Test JWT Bearer Token
```
1. After login, you'll see Authorization field
2. Paste any JWT token (for testing)
3. Select API from sidebar
4. All requests will include Bearer token
```

#### Test Server Selection
```
1. Open API spec
2. Look for "Server:" dropdown (top of Scalar)
3. Switch between Dev/Test/Prod/Local
4. Requests use selected server URL
```

#### Test "Try it Out"
```
1. Select an endpoint
2. Click "Try it out" button
3. Edit request parameters
4. Click "Send"
5. See response in real-time
```

---

## Directory Structure

```
api-docs-build/                    ← Run server from here
├── index.html                     ← Login gate
├── docs.html                      ← API documentation
├── viewer-notification.html       ← Notification Service viewer
├── viewer-validation.html         ← Validation Service viewer
└── specs/
    ├── manifest.json              ← API metadata
    ├── alramz-notification-service/
    │   └── openapi.yaml           ← Email API spec
    └── data-validation-service/
        └── apiSpecs.yaml          ← IBAN Validation spec
```

---

## Common Commands

### Start Server

```bash
# Navigate to docs directory
cd api-docs-build

# Start Python server
python3 -m http.server 8000

# Press Ctrl+C to stop
```

### Regenerate Docs

```bash
# From project root
./scripts/generate-api-docs.sh

# With custom username/password
DOCS_USERNAME=user DOCS_PASSWORD=pass ./scripts/generate-api-docs.sh
```

### Create Auth Wrapper

```bash
# From project root
DOCS_USERNAME=admin DOCS_PASSWORD=SecurePass123! \
node scripts/create-auth-wrapper.js api-docs-build

# Rename files
cd api-docs-build
mv index.html docs.html
mv auth-index.html index.html
```

### Check Port Usage

```bash
# macOS/Linux - Check what's using port 8000
lsof -i :8000

# Windows - Check what's using port 8000
netstat -ano | findstr :8000
```

### Kill Process on Port 8000

```bash
# macOS/Linux
kill -9 $(lsof -t -i:8000)

# Or using pkill
pkill -f 'http.server 8000'
```

---

## Troubleshooting

### Error: "Port 8000 already in use"

**Solution 1: Use different port**
```bash
cd api-docs-build
python3 -m http.server 8001
# Then visit: http://localhost:8001
```

**Solution 2: Kill process on port 8000**
```bash
pkill -f 'http.server 8000'
```

### Error: "jq command not found"

**Solution: Install jq**

```bash
# macOS
brew install jq

# Linux
sudo apt-get install jq

# Or skip jq and use generated docs
cd api-docs-build
python3 -m http.server 8000
```

### Login not working

**Check:**
1. Clear browser cache (Ctrl+Shift+Delete)
2. Try incognito/private window
3. Check console (F12) for errors
4. Verify credentials: admin / SecurePass123!

### API specs not loading

**Check:**
1. Are the spec files present?
   ```bash
   ls -la api-docs-build/specs/*/
   ```

2. Refresh browser (F5)

3. Check browser console (F12) for errors

4. Try regenerating docs:
   ```bash
   ./scripts/generate-api-docs.sh
   ```

### "Cannot read properties of null" error

**Solution:**
- Clear browser cache
- Use different browser
- Try incognito mode
- Refresh page (F5)

### Server shows "Serving HTTP" but page won't load

**Check:**
1. URL is correct: `http://localhost:8000`
2. Server is actually running (check terminal)
3. Try different port (8001, 8002, etc.)
4. Firewall isn't blocking port 8000

---

## Environment Variables

### Test with Different Credentials

```bash
# Generate docs with custom login
cd /path/to/project
DOCS_USERNAME=testuser DOCS_PASSWORD=testpass123 \
./scripts/generate-api-docs.sh

cd api-docs-build
mv index.html docs.html
mv auth-index.html index.html
python3 -m http.server 8000
```

### Use Different Server URLs

Edit `api-docs-build/specs/*/openapi.yaml`:

```yaml
servers:
  - url: http://your-local-api:8080
    description: Your Local API
  - url: https://your-dev-api.com
    description: Your Dev API
```

---

## Performance Tips

### Faster Loading

1. **Use Python 3 (not 2)**
   ```bash
   python3 -m http.server 8000
   ```

2. **Clear browser cache**
   - Ctrl+Shift+Delete (Windows/Linux)
   - Cmd+Shift+Delete (macOS)

3. **Use Chrome DevTools**
   - F12 → Network tab
   - Check slow requests
   - Disable cache if testing

### Slower Machines

If experiencing slowness:

```bash
# Use Node.js server (sometimes faster)
npx http-server api-docs-build -p 8000

# Or use Busybox (if available)
busybox httpd -f -p 8000 -h api-docs-build
```

---

## Advanced Setup

### Behind Proxy/VPN

```bash
# Access from another machine on network
# Find your IP
ifconfig | grep "inet "

# Start server (bind to all interfaces)
python3 -m http.server 8000 --bind 0.0.0.0

# Others can access via:
http://your-machine-ip:8000
```

### Docker Setup (Optional)

```dockerfile
FROM python:3.11-slim

WORKDIR /app
COPY api-docs-build /app

EXPOSE 8000

CMD ["python", "-m", "http.server", "8000"]
```

**Build and run:**
```bash
docker build -t api-docs .
docker run -p 8000:8000 api-docs
```

### HTTPS/SSL (Optional)

For production-like testing:

```bash
# Using Python with SSL
python3 << 'EOF'
import http.server
import ssl

server = http.server.HTTPServer(('localhost', 8443), http.server.SimpleHTTPRequestHandler)
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.check_hostname = False
context.load_cert_chain('cert.pem', 'key.pem')  # Needs cert files
server.socket = context.wrap_socket(server.socket, server_side=True)
server.serve_forever()
EOF
```

---

## Next Steps

### 1. Configure for Your Environment

Update server URLs in OpenAPI specs:

```bash
# Edit spec files
vim services/*/specs/*.yaml

# Update servers section with your URLs:
servers:
  - url: http://your-local-api:8080
  - url: https://dev.yourcompany.com
  - url: https://prod.yourcompany.com
```

### 2. Test with Real APIs

1. Start your backend service
2. Update server URLs to point to it
3. Add JWT token in docs
4. Test "Try it out" on endpoints

### 3. Deploy to GitHub Pages

When ready to share with team:

```bash
# Push specs to repository
git add services/*/specs/*.yaml
git commit -m "Update API specs with servers"
git push origin main

# GitHub Actions automatically:
# 1. Detects changes
# 2. Generates docs
# 3. Creates auth wrapper with secrets
# 4. Deploys to GitHub Pages
```

### 4. Share with Team

```
Share this URL: https://github.com/your-org/your-repo/deployments/github-pages

Team members will:
1. See login page
2. Enter username/password (from GitHub Secrets)
3. Explore interactive API docs
4. Test with "Try it out"
```

---

## Keyboard Shortcuts

In browser:
- **F5** - Refresh page
- **F12** - Open developer console
- **Ctrl+Shift+Delete** - Clear cache
- **Ctrl+L** - Focus address bar

In Scalar:
- **Ctrl/Cmd + K** - Search endpoints
- **Ctrl/Cmd + Shift + L** - Toggle sidebar

---

## Getting Help

### Check Logs

```bash
# Terminal output when server runs shows requests:
127.0.0.1 - - [18/Sep/2024 12:00:00] "GET / HTTP/1.1" 200 -
```

### Browser Console

Press **F12** and check **Console** tab for errors:
```javascript
// Example error message
Uncaught TypeError: Cannot read properties of null
```

### Files Exist?

```bash
# Check docs structure
ls -la api-docs-build/
ls -la api-docs-build/specs/*/

# Should show:
# - index.html (login gate)
# - docs.html (api docs)
# - specs/ folder with OpenAPI files
```

---

## Summary

```
Quick Start:
  1. cd api-docs-build
  2. python3 -m http.server 8000
  3. Open http://localhost:8000
  4. Login: admin / SecurePass123!
  5. Explore APIs!

To Stop:
  Press Ctrl+C in terminal

To Restart:
  python3 -m http.server 8000

That's it! 🚀
```

---

**Questions?** Check the browser console (F12) or see [API-DOCS.md](API-DOCS.md) for full documentation.
