# API Documentation on GitHub Pages

## Accessing the API Documentation

### URL

Your API documentation is hosted at:
```
https://<username>.github.io/<repository-name>
```

Example: `https://myorg.github.io/alramz-mw-oss`

### Authentication

1. **Login Page**: You'll see a login screen when you first visit
2. **Credentials**: Use the username and password provided by your administrator
3. **Session**: Once logged in, you'll stay authenticated for your browsing session
4. **Logout**: Close your browser tab/window to log out

## What's Available

### API Services

The documentation includes OpenAPI specifications for:

- **Notification Service** (`alramz-notification-service`)
  - Email sending APIs
  - Request/response examples
  - Error handling

- **Data Validation Service** (`data-validation-service`)
  - Data validation APIs
  - Schema definitions
  - Usage examples

### Features

- **Interactive API Documentation**: View all endpoints, parameters, and responses
- **Try It Out**: Make test requests directly from the documentation (if enabled)
- **Schema Definitions**: See request/response models with detailed fields
- **Code Examples**: View example requests and responses
- **Server URLs**: See which environments/servers are available

## Using the Documentation

### Finding an Endpoint

1. Look for the service name in the left sidebar
2. Scroll through the endpoints or use search (Ctrl+F / Cmd+F)
3. Click on an endpoint to see details

### Understanding an Endpoint

Each endpoint shows:

- **Method**: GET, POST, PUT, DELETE, etc.
- **Path**: The URL path (e.g., `/api/v1/email/send`)
- **Summary**: Brief description of what it does
- **Parameters**: Required and optional query/path parameters
- **Request Body**: Schema for the request data
- **Responses**: Possible response codes and their formats
- **Examples**: Sample requests and responses

### Making API Calls

To call an API endpoint:

1. **Get the endpoint details** from the documentation
2. **Get your API credentials** from your administrator
3. **Construct the URL**:
   ```
   https://api.example.com<endpoint-path>
   ```

4. **Example Request** (using curl):
   ```bash
   curl -X POST https://api.example.com/api/v1/email/send \
     -H "Content-Type: application/json" \
     -d '{
       "to": "recipient@example.com",
       "subject": "Test Email",
       "from": "sender@example.com",
       "body": "<h1>Hello</h1><p>This is a test email.</p>"
     }'
   ```

## Troubleshooting

### Can't Access the Documentation

- **Verify the URL**: Double-check the domain and repository name
- **Check your internet**: Ensure you have internet connectivity
- **Clear cache**: Try clearing your browser cache or use incognito mode
- **Wait for deployment**: New documentation can take 1-2 minutes to deploy

### Forgot Your Password

Contact your administrator to reset your credentials.

### Authentication Expired

- **Session Timeout**: Sessions are per-browser tab
- **Re-login**: Close and reopen the documentation tab
- **Different Browsers**: Each browser maintains separate sessions

### Can't Find an Endpoint

- **Use browser search**: Press Ctrl+F (Windows) or Cmd+F (Mac)
- **Check service name**: Verify you're looking in the correct service
- **API might be new**: Ask your administrator if the API is documented

## API Response Codes

Common HTTP response codes you'll see:

- **200 OK**: Request succeeded
- **201 Created**: Resource was created successfully
- **400 Bad Request**: Invalid request data
- **401 Unauthorized**: Authentication required or failed
- **403 Forbidden**: You don't have permission
- **404 Not Found**: Endpoint or resource doesn't exist
- **500 Server Error**: Internal server error

Check the documentation for service-specific response codes.

## Questions or Issues?

- **For API questions**: Contact your development team
- **For documentation issues**: Report to your documentation administrator
- **For authentication problems**: Contact your system administrator

## Additional Resources

- **Request Examples**: The documentation includes example requests and responses for each endpoint
- **Schema Details**: Scroll down to see the complete data model definitions
- **Environment URLs**: Check the server information to see which API environments are available

## Updating Your Credentials

If your administrator provided new credentials:

1. Clear your browser's session storage: Press F12, go to Application → Session Storage → Clear
2. Or simply close all browser tabs and reopen the documentation
3. Log in with your new credentials

---

**Last Updated**: Check with your documentation administrator for the latest deployment information.
