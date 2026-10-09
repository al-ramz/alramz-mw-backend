#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

const username = process.env.DOCS_USERNAME || 'admin';
const password = process.env.DOCS_PASSWORD || 'changeme';

const outputPath = path.join(__dirname, 'dist', 'index.html');

const htmlContent = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>API Documentation</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }

        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background: #f5f5f5;
        }

        #docs-container {
            width: 100%;
            height: 100vh;
            border: none;
        }

        .auth-overlay {
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 9999;
            padding: 20px;
        }

        .auth-container {
            background: white;
            border-radius: 8px;
            box-shadow: 0 10px 40px rgba(0, 0, 0, 0.2);
            padding: 40px;
            width: 100%;
            max-width: 400px;
        }

        .auth-header {
            text-align: center;
            margin-bottom: 30px;
        }

        .auth-header h1 {
            font-size: 24px;
            color: #333;
            margin-bottom: 10px;
        }

        .auth-header p {
            color: #666;
            font-size: 14px;
        }

        .auth-form {
            display: flex;
            flex-direction: column;
            gap: 15px;
        }

        .form-group {
            display: flex;
            flex-direction: column;
            gap: 5px;
        }

        label {
            color: #333;
            font-weight: 500;
            font-size: 14px;
        }

        input[type="text"],
        input[type="password"] {
            padding: 10px 12px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 14px;
            transition: border-color 0.3s;
        }

        input[type="text"]:focus,
        input[type="password"]:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }

        button {
            padding: 10px 16px;
            background: #667eea;
            color: white;
            border: none;
            border-radius: 4px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            transition: background 0.3s;
            margin-top: 10px;
        }

        button:hover {
            background: #5568d3;
        }

        button:active {
            transform: scale(0.98);
        }

        .error {
            color: #d32f2f;
            font-size: 12px;
            padding: 10px;
            background: #ffebee;
            border-radius: 4px;
            border-left: 3px solid #d32f2f;
            display: none;
        }

        .error.show {
            display: block;
        }

        .hidden {
            display: none !important;
        }

        .jwt-section {
            margin-top: 20px;
            padding-top: 15px;
            border-top: 1px solid #ddd;
        }

        .jwt-section label {
            display: block;
            font-weight: 600;
            font-size: 12px;
            color: #333;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .jwt-section textarea {
            width: 100%;
            min-height: 60px;
            padding: 8px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 11px;
            font-family: monospace;
            resize: vertical;
            margin-bottom: 8px;
        }

        .jwt-section textarea:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 2px rgba(102, 126, 234, 0.1);
        }

        .jwt-buttons {
            display: flex;
            gap: 8px;
        }

        .jwt-buttons button {
            flex: 1;
            padding: 8px;
            font-size: 12px;
            margin-top: 0;
        }

        .jwt-buttons button.secondary {
            background: #f5f5f5;
            color: #333;
            border: 1px solid #ddd;
        }

        .jwt-buttons button.secondary:hover {
            background: #e8e8e8;
        }

        .jwt-status {
            font-size: 11px;
            padding: 8px;
            border-radius: 4px;
            margin-top: 8px;
            display: none;
        }

        .jwt-status.active {
            display: block;
            background: #e8f5e9;
            color: #2e7d32;
            border-left: 3px solid #2e7d32;
        }

        .jwt-status.error {
            display: block;
            background: #ffebee;
            color: #d32f2f;
            border-left: 3px solid #d32f2f;
        }

        .sidebar {
            width: 260px;
            background: #fff;
            border-right: 1px solid #ddd;
            padding: 20px;
            overflow-y: auto;
        }

        .sidebar h3 {
            margin-top: 0;
            color: #333;
            font-size: 14px;
            margin-bottom: 15px;
        }

        .service-list {
            list-style: none;
            padding: 0;
            margin: 0;
            margin-bottom: 20px;
        }

        .service-list li {
            margin-bottom: 10px;
        }

        .service-list li:first-child {
            margin-bottom: 10px;
        }

        .service-btn {
            width: 100%;
            text-align: left;
            padding: 10px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            font-weight: 500;
            font-size: 13px;
        }

        .sidebar-divider {
            margin: 20px 0;
            border: none;
            border-top: 1px solid #ddd;
        }

        .logout-btn {
            width: 100%;
            padding: 10px;
            background: #f5f5f5;
            border: 1px solid #ddd;
            border-radius: 4px;
            cursor: pointer;
            color: #d32f2f;
            font-size: 13px;
            font-weight: 500;
        }

        .logout-btn:hover {
            background: #efefef;
        }

        .docs-wrapper {
            flex: 1;
        }
    </style>
</head>
<body>
    <div id="docs-container"></div>
    <div class="auth-overlay" id="auth-overlay">
        <div class="auth-container">
            <div class="auth-header">
                <h1>API Documentation</h1>
                <p>Please sign in to access</p>
            </div>
            <form class="auth-form" id="auth-form" onsubmit="handleLogin(event)">
                <div class="form-group">
                    <label for="username">Username</label>
                    <input type="text" id="username" placeholder="Enter username" autocomplete="username" required autofocus>
                </div>
                <div class="form-group">
                    <label for="password">Password</label>
                    <input type="password" id="password" placeholder="Enter password" autocomplete="current-password" required>
                </div>
                <div class="error" id="error"></div>
                <button type="submit">Sign In</button>
            </form>
        </div>
    </div>

    <script>
        const CREDENTIALS = {
            username: '${username}',
            password: '${password}'
        };

        function checkAuth() {
            return sessionStorage.getItem('apiDocsAuth') === 'true';
        }

        function handleLogin(e) {
            e.preventDefault();
            const uname = document.getElementById('username').value;
            const pwd = document.getElementById('password').value;
            const errorEl = document.getElementById('error');

            if (uname === CREDENTIALS.username && pwd === CREDENTIALS.password) {
                sessionStorage.setItem('apiDocsAuth', 'true');
                showDocs();
            } else {
                errorEl.textContent = 'Invalid username or password';
                errorEl.classList.add('show');
                document.getElementById('password').value = '';
                document.getElementById('username').focus();
            }
        }

        function showDocs() {
            document.getElementById('auth-overlay').classList.add('hidden');

            const container = document.getElementById('docs-container');
            const savedToken = sessionStorage.getItem('jwtToken') || '';
            
            container.innerHTML = '<div style="display: flex; height: 100vh; background: #f5f5f5;"><div class="sidebar"><h3>Services</h3><ul class="service-list"><li><button class="service-btn" id="notif-btn" onclick="loadService(\\'notification-service\\', \\'Notification Service\\', this)" style="background: #667eea; color: white;">Notification Service</button></li><li><button class="service-btn" id="data-btn" onclick="loadService(\\'data-validation-service\\', \\'Data Validation Service\\', this)">Data Validation Service</button></li></ul><div class="jwt-section"><label>JWT Token (Optional)</label><textarea id="jwtToken" placeholder="Paste your JWT token here...">' + savedToken + '</textarea><div class="jwt-buttons"><button onclick="setJwtToken()" style="margin-top: 0;">Save Token</button><button onclick="clearJwtToken()" class="secondary" style="margin-top: 0;">Clear</button></div><div id="jwtStatus" class="jwt-status"></div></div><hr class="sidebar-divider"><button class="logout-btn" onclick="logout()">Logout</button></div><div class="docs-wrapper"><iframe id="docs-iframe" src="./notification-service.html" style="width: 100%; height: 100%; border: none;"></iframe></div></div>';

            if (savedToken) {
                showJwtStatus('✓ Token loaded', 'active');
            }
        }

        window.loadService = function(service, title, btn) {
            document.getElementById('docs-iframe').src = './' + service + '.html';
            document.querySelectorAll('.service-btn').forEach(b => {
                b.style.background = '';
                b.style.color = '';
                b.style.border = '';
            });
            btn.style.background = '#667eea';
            btn.style.color = 'white';
            btn.style.border = 'none';
        };

        window.setJwtToken = function() {
            const token = document.getElementById('jwtToken').value.trim();
            if (!token) {
                showJwtStatus('Please enter a JWT token', 'error');
                return;
            }
            sessionStorage.setItem('jwtToken', token);
            showJwtStatus('✓ JWT token saved! Add it to your API requests', 'active');
        };

        window.clearJwtToken = function() {
            document.getElementById('jwtToken').value = '';
            sessionStorage.removeItem('jwtToken');
            showJwtStatus('Token cleared', 'error');
        };

        window.showJwtStatus = function(message, type) {
            const status = document.getElementById('jwtStatus');
            status.textContent = message;
            status.className = 'jwt-status ' + type;
        };

        window.logout = function() {
            sessionStorage.removeItem('apiDocsAuth');
            sessionStorage.removeItem('jwtToken');
            window.location.reload();
        };

        if (checkAuth()) {
            showDocs();
        } else {
            document.getElementById('username').focus();
        }
    </script>
</body>
</html>
`;

try {
  fs.writeFileSync(outputPath, htmlContent, 'utf8');
  console.log('✓ Authentication index created successfully');
  console.log('✓ Credentials: ' + username + ' / ****');
  console.log('✓ JWT token input added to sidebar');
  process.exit(0);
} catch (error) {
  console.error('✗ Error creating authentication index:', error.message);
  process.exit(1);
}
