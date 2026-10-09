# 🚀 Quick Start - 5 Minutes

Get API docs running locally in **5 minutes or less**.

---

## Step 1: Open Terminal

```bash
# Navigate to project
cd /Users/gtalele/Library/CloudStorage/OneDrive-AlRamz/Desktop/workspace/alramz-mw-oss/api-docs-build
```

## Step 2: Start Server

```bash
python3 -m http.server 8000
```

**You should see:**
```
Serving HTTP on 0.0.0.0 port 8000 (http://0.0.0.0:8000/) ...
```

## Step 3: Open Browser

Click or paste in address bar:
```
http://localhost:8000
```

## Step 4: Login

```
Username: admin
Password: SecurePass123!
```

Click: **🔓 Access Documentation**

## Step 5: Explore

✅ **Select API** from sidebar  
✅ **Choose server** from dropdown (Dev/Test/Prod)  
✅ **Paste JWT token** (optional)  
✅ **Click "Try it out"** on any endpoint  

---

## 🛑 Stop Server

Press in terminal: **Ctrl + C**

---

## 📝 Change Login Credentials

```bash
# Regenerate with new credentials
cd ..  # Go to project root

DOCS_USERNAME=myuser DOCS_PASSWORD=mypass \
node scripts/create-auth-wrapper.js api-docs-build

# Replace files
cd api-docs-build
mv index.html docs.html
mv auth-index.html index.html

# Restart server
python3 -m http.server 8000
```

---

## 🆘 Troubleshooting

### Port 8000 already in use?

```bash
# Use different port
python3 -m http.server 8001
# Then visit: http://localhost:8001
```

### Page won't load?

```bash
# Refresh browser: F5
# Clear cache: Ctrl+Shift+Delete
# Try incognito: Ctrl+Shift+N
```

### Login button not working?

```bash
# Open console: F12
# Check for errors
# Try different browser
```

---

## 📚 Full Guide

See [LOCAL-SETUP.md](LOCAL-SETUP.md) for detailed instructions.

---

## 🎯 What You Can Do

- 📖 **Browse APIs** - All endpoints documented
- 🔐 **Add JWT** - Paste token, all requests include it
- 🌍 **Switch Servers** - Dev/Test/Prod dropdown
- 🧪 **Try It Out** - Send real requests to API
- 📋 **See Schemas** - Full request/response docs
- ✅ **Check Errors** - All error codes documented

---

## 💡 Tips

- **JWT Token Field** appears after you login and select API
- **Server Dropdown** at top of Scalar viewer
- **Try it out** sends to whichever server you selected
- **Logout** button resets session

---

**Done!** You have interactive API docs running locally! 🎉
