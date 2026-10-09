# API Documentation Deployment

This guide explains how API documentation is automatically deployed to GitHub Pages using the Scalar library with authentication support.

## Overview

The API documentation system provides:

- **Interactive API Explorer**: Test API endpoints directly from the docs using Scalar
- **Authentication**: Supports both Basic Auth (username/password) and JWT Bearer tokens
- **Multi-API Support**: Automatically discovers and serves all OpenAPI specs from the monorepo
- **Auto-deployment**: Deploys to GitHub Pages on every spec change

## Architecture

```
services/
├── alramz-notification-service/
│   └── specs/
│       └── openapi.yaml
├── data-validation-service/
│   └── specs/
│       └── apiSpecs.yaml
└── ... (other services)

GitHub Pages (Deployed)
├── index.html (Auth layer + API selector)
├── specs/ (All OpenAPI specifications)
│   ├── alramz-notification-service/
│   ├── data-validation-service/
│   └── manifest.json
```

## Workflow: `api-docs-deploy.yml`

### Triggers

The workflow automatically runs when:

- OpenAPI spec files change (`services/**/specs/*.yaml`)
- The workflow file itself changes
- Manually triggered via `workflow_dispatch`

### Jobs

#### 1. `detect-specs`
- Discovers all OpenAPI specification files in the monorepo
- Generates a manifest with service names and spec paths
- Outputs JSON containing service metadata

#### 2. `build`
- Sets up Node.js environment
- Copies all OpenAPI specs to build directory
- Generates `manifest.json` with spec metadata
- Creates interactive `index.html` with:
  - Authentication layer (Basic Auth & JWT)
  - API selector dropdown
  - Embedded Scalar reference viewer

#### 3. `deploy`
- Uploads build artifacts to GitHub Pages
- Provides deployment URL in summary

## Usage

### Accessing the Documentation

1. Navigate to your GitHub Pages URL (set in repository settings)
2. You'll see the authentication screen

### Authentication

#### Basic Auth
1. Click on "Basic Auth" tab
2. Enter username and password
3. Click "Authenticate"
4. Select an API from the dropdown

#### JWT Token
1. Click on "JWT Token" tab
2. Paste your JWT token
3. Click "Authenticate"
4. Select an API from the dropdown

### Using the API Explorer

Once authenticated and an API is selected:

- **Browse**: Explore all endpoints and schemas
- **Try It Out**: Click the "Try it out" button on any endpoint
- **Execute**: Send real requests with authentication headers
- **View Responses**: See live responses from your API

The authentication token (Basic or JWT) is automatically included in all requests made from the docs.

## Adding New APIs

### Step 1: Create OpenAPI Spec

Create a spec file in your service:

```bash
services/my-new-service/specs/openapi.yaml
```

### Step 2: Commit and Push

```bash
git add services/my-new-service/specs/openapi.yaml
git commit -m "Add OpenAPI spec for my-new-service"
git push
```

### Step 3: Automatic Deployment

The workflow automatically:
1. Detects the new spec
2. Builds updated documentation
3. Deploys to GitHub Pages
4. Creates deployment summary

Your new API will appear in the API selector dropdown.

## OpenAPI Specification Guidelines

### Minimum Requirements

```yaml
openapi: 3.0.3

info:
  title: "Service Name API"
  version: "1.0.0"
  description: "Clear description of your API"

servers:
  - url: http://localhost:8080  # Local dev
  - url: https://api.prod.example.com  # Production

paths:
  /api/v1/endpoint:
    post:
      tags:
        - Category
      summary: "Brief summary"
      description: "Detailed description"
      operationId: operationName
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/RequestSchema'
      responses:
        '200':
          description: Success response
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ResponseSchema'
        '400':
          description: Bad request
        '401':
          description: Unauthorized
        '500':
          description: Server error

components:
  schemas:
    RequestSchema:
      type: object
      required:
        - field
      properties:
        field:
          type: string
          example: "example value"

  securitySchemes:
    basicAuth:
      type: http
      scheme: basic
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

security:
  - basicAuth: []
  - bearerAuth: []
```

### Best Practices

1. **Use clear operation IDs**: `operationId: sendEmail` instead of `op123`
2. **Include examples**: Add example requests and responses
3. **Document errors**: Include all possible HTTP status codes
4. **Use tags**: Organize endpoints with logical tags
5. **Describe parameters**: Include descriptions for all parameters
6. **Define schemas**: Create reusable component schemas

## Configuration

### GitHub Pages Settings

In your repository:

1. Go to **Settings** → **Pages**
2. Select **Deploy from a branch**
3. Select branch: `gh-pages`
4. Select folder: `/ (root)`
5. Enable **Enforce HTTPS**

The workflow automatically creates the `gh-pages` branch.

### Custom Domain (Optional)

To use a custom domain:

1. Go to **Settings** → **Pages**
2. Enter custom domain (e.g., `api-docs.example.com`)
3. Add CNAME record to your DNS provider
4. Enable HTTPS enforcement

## Security Considerations

### Authentication Token Handling

- **Stored Locally**: Auth tokens are stored in browser session only
- **Not Persisted**: Tokens are cleared on browser close
- **Transmitted Securely**: Always use HTTPS (enforced in GitHub Pages)
- **Not Logged**: Tokens are not stored in workflow logs

### Best Practices

1. **Use Development Credentials**: Use test accounts for the demo docs
2. **Rotate Keys**: Regularly rotate JWT signing keys
3. **HTTPS Only**: Always enforce HTTPS on your API endpoints
4. **Rate Limiting**: Consider rate limiting on your APIs when exposed in docs
5. **CORS Configuration**: Configure CORS headers to allow requests from docs domain

### Example CORS Configuration (Spring Boot)

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("https://{github-pages-domain}")
                    .allowedMethods("GET", "POST", "PUT", "DELETE")
                    .allowedHeaders("*")
                    .exposedHeaders("Authorization")
                    .allowCredentials(true)
                    .maxAge(3600);
            }
        };
    }
}
```

## Troubleshooting

### Workflow Fails to Run

**Problem**: Workflow doesn't trigger on spec changes

**Solution**:
1. Verify spec files are in correct path: `services/*/specs/*.yaml`
2. Check branch name in workflow triggers
3. Verify file paths use correct extensions (`.yaml` or `.yml`)

### Documentation Not Updating

**Problem**: Changes to specs aren't reflected in docs

**Solution**:
1. Force push the spec changes
2. Manually trigger workflow via `workflow_dispatch`
3. Check for workflow errors in Actions tab
4. Clear browser cache (Ctrl+Shift+Delete)

### Authentication Issues

**Problem**: "Can't authenticate" or requests fail

**Solution**:
1. Verify API endpoint has CORS enabled
2. Check auth token is valid and not expired
3. Ensure API returns correct HTTP status codes
4. Check browser console for CORS errors

### Missing APIs in Selector

**Problem**: New API doesn't appear in dropdown

**Solution**:
1. Verify spec file created in correct location
2. Run workflow manually to refresh
3. Check `specs/manifest.json` in GitHub Pages

## Monitoring

### Check Deployment Status

1. Go to **Actions** tab
2. Select "Deploy API Docs" workflow
3. View recent runs and logs

### View Deployment Summary

After successful deployment:

1. Click completed workflow run
2. Scroll to "Deployment Summary" section
3. See:
   - Deployment URL
   - Authentication methods
   - Available APIs

## Examples

### Testing with cURL

```bash
# Get Bearer token
TOKEN="your-jwt-token"

# Make API request with Bearer token
curl -H "Authorization: Bearer $TOKEN" \
  https://api.example.com/api/v1/endpoint

# Make API request with Basic Auth
curl -u username:password \
  https://api.example.com/api/v1/endpoint
```

### Updating a Spec

```bash
# Edit spec file
vim services/my-service/specs/openapi.yaml

# Commit and push
git add services/my-service/specs/openapi.yaml
git commit -m "Update API spec: add new endpoint"
git push origin feature/new-endpoint

# Docs automatically update on GitHub Pages
```

## References

- [Scalar API Documentation](https://github.com/scalar/scalar)
- [OpenAPI 3.0 Specification](https://spec.openapis.org/oas/v3.0.3)
- [GitHub Pages Documentation](https://docs.github.com/en/pages)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## Support

For issues or questions:

1. Check this documentation
2. Review workflow logs in Actions tab
3. Check [Scalar documentation](https://github.com/scalar/scalar/wiki)
4. Open an issue with workflow logs and spec details
