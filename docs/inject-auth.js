#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const username = process.env.DOCS_USERNAME || 'admin';
const password = process.env.DOCS_PASSWORD || 'changeme';

const authWrapperPath = path.join(__dirname, 'auth-wrapper.html');
const indexPath = path.join(__dirname, 'dist', 'index.html');
const outputPath = path.join(__dirname, 'dist', 'auth.html');

try {
  // Read the auth wrapper template
  let authContent = fs.readFileSync(authWrapperPath, 'utf8');

  // Replace placeholders with actual credentials
  authContent = authContent
    .replace('${DOCS_USERNAME}', username)
    .replace('${DOCS_PASSWORD}', password);

  // Write to dist folder
  fs.writeFileSync(outputPath, authContent, 'utf8');

  // Read the Redocly-generated index.html and wrap it in an iframe with auth
  if (fs.existsSync(indexPath)) {
    const indexContent = fs.readFileSync(indexPath, 'utf8');

    // Create a wrapper that loads the docs only after authentication
    const wrappedContent = `<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>API Documentation</title>
</head>
<body>
    <div id="auth-container"></div>
    <div id="docs-container" style="display: none;"></div>

    <script>
        // Configuration injected at build time
        const CONFIG = {
            username: '${username}',
            password: '${password}'
        };

        function checkAuth() {
            const auth = sessionStorage.getItem('apiDocsAuth');
            if (auth === 'true') {
                showDocs();
            } else {
                showAuth();
            }
        }

        function showAuth() {
            const authContainer = document.getElementById('auth-container');
            authContainer.innerHTML = ${JSON.stringify(authContent.substring(authContent.indexOf('<body>') + 6, authContent.indexOf('</body>')))};
            document.getElementById('docs-container').style.display = 'none';

            document.getElementById('auth-form').addEventListener('submit', handleLogin);
        }

        function handleLogin(e) {
            e.preventDefault();
            const username = document.getElementById('username').value;
            const password = document.getElementById('password').value;

            if (username === CONFIG.username && password === CONFIG.password) {
                sessionStorage.setItem('apiDocsAuth', 'true');
                showDocs();
            } else {
                document.getElementById('error').textContent = 'Invalid credentials';
                document.getElementById('error').classList.add('show');
                document.getElementById('password').value = '';
            }
        }

        function showDocs() {
            document.getElementById('auth-container').innerHTML = '';
            document.getElementById('docs-container').style.display = 'block';
            document.getElementById('docs-container').innerHTML = ${JSON.stringify(indexContent)};
        }

        checkAuth();
    </script>
</body>
</html>`;

    fs.writeFileSync(indexPath, wrappedContent, 'utf8');
  }

  console.log('✓ Authentication injected successfully');
  process.exit(0);
} catch (error) {
  console.error('✗ Error injecting authentication:', error.message);
  process.exit(1);
}
