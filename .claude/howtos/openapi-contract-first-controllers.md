# OpenAPI Contract-First Controllers

Reference services: `services/reference-data-service`, `services/data-validation-service`.

## 1. What this pattern is / when to use it

Every REST endpoint in this repo is **API-first**: you hand-write an OpenAPI 3.0 spec describing
the contract (paths, request/response schemas, security), and the `openapi-generator-maven-plugin`
generates a Java interface + DTO models from it at build time. Your controller class then
`implements` the generated interface and fills in the method bodies.

You never hand-write a `@RestController` with ad hoc `@PostMapping`/`@GetMapping` methods and
inline request/response POJOs. The spec is the source of truth for the wire contract; the
generated interface is what guarantees the controller actually matches it (the build fails to
compile if a controller's method signature drifts from the spec).

Use this pattern for **every** new HTTP endpoint you add to a service, whether it's a brand new
controller or one more operation on an existing one.

## 2. Where it lives in this repo

| Piece | Path |
|---|---|
| Hand-written spec | `services/<service>/specs/apiSpecs.yaml` (one file per service, all endpoints in it) |
| Codegen plugin config | `services/<service>/pom.xml` — `openapi-generator-maven-plugin` block |
| Generated interfaces | `services/<service>/target/generated-sources/openapi/src/main/java/com/alramz/api/*.java` (build output, gitignored, regenerated every build) |
| Generated models | `services/<service>/target/generated-sources/openapi/src/main/java/com/alramz/model/*.java` (build output, gitignored) |
| Real controllers implementing them | `services/data-validation-service/src/main/java/com/alramz/controllers/DataValidationController.java`, `services/reference-data-service/src/main/java/com/alramz/controllers/RedisCacheController.java` |

Both services' plugin config is byte-for-byte identical (`data-validation-service/pom.xml:166-199`,
`reference-data-service/pom.xml:179-212`) — this is the canonical config to copy for a new service:

```xml
<plugin>
    <groupId>org.openapitools</groupId>
    <artifactId>openapi-generator-maven-plugin</artifactId>
    <version>7.23.0</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <inputSpec>${project.basedir}/specs/apiSpecs.yaml</inputSpec>
                <generatorName>spring</generatorName>
                <apiPackage>com.alramz.api</apiPackage>
                <modelPackage>com.alramz.model</modelPackage>
                <configOptions>
                    <interfaceOnly>true</interfaceOnly>
                    <skipDefaultInterface>true</skipDefaultInterface>
                    <removeUnusedImports>true</removeUnusedImports>
                    <useSpringBoot3>true</useSpringBoot3>
                    <useJakartaEe>true</useJakartaEe>
                    <useTags>true</useTags>
                    <performBeanValidation>true</performBeanValidation>
                    <openApiNullable>false</openApiNullable>
                    <useResponseEntity>true</useResponseEntity>
                    <useSnakeCase>true</useSnakeCase>
                </configOptions>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Key options and what they mean for you:
- `interfaceOnly=true` + `skipDefaultInterface=true` — generates **only** the `*Api` interface
  (with Swagger annotations, `@RequestMapping`, and a `PATH_*` constant per operation), not a
  default no-op implementation. You always write the implementation yourself.
- `apiPackage`/`modelPackage` are both `com.alramz.*` in every service — controllers and services
  import generated types the same way regardless of which service they're in.
- `useSnakeCase=true` — schema properties written as `snake_case` in the YAML (e.g.
  `member_reference_number`) are kept as `snake_case` getters/fields on the generated model, not
  camel-cased. Match this when adding new schema fields.
- `useResponseEntity=true` — every generated method returns `ResponseEntity<T>`, so your
  controller has full control over status code and headers.
- The `generate` goal's default Maven phase is `generate-sources` (no explicit `<phase>` needed),
  and it registers the output directory as a compile source root automatically — so a plain
  `mvn compile` (or `test-compile`, `test`, `package`) regenerates and recompiles the interfaces
  before your controller code compiles against them. There is no separate "codegen step" to
  remember to run.

## 3. How it works end to end

1. **Spec** (`specs/apiSpecs.yaml`) defines a path + `operationId` + request/response schemas, e.g.
   `data-validation-service/specs/apiSpecs.yaml:24-30`:
   ```yaml
   /iban/validate:
     post:
       tags: [IBAN]
       operationId: validateIBAN
       requestBody:
         content:
           application/json:
             schema:
               $ref: '#/components/schemas/IBANRequest'
       responses:
         '200':
           content:
             application/json:
               schema:
                 $ref: '#/components/schemas/GenericResponse'
   ```

2. **Build-time codegen** produces, from that one operation, an interface method
   (`target/generated-sources/openapi/.../api/IbanApi.java`):
   ```java
   public interface IbanApi {
       String PATH_VALIDATE_IBAN = "/iban/validate";

       @Operation(operationId = "validateIBAN", ...)
       @RequestMapping(method = RequestMethod.POST, value = IbanApi.PATH_VALIDATE_IBAN,
                        produces = { "application/json" }, consumes = { "application/json" })
       ResponseEntity<GenericResponse> validateIBAN(
           @Valid @RequestBody IBANRequest ibANRequest
       );
   }
   ```
   Note the naming: the YAML `operationId` (`validateIBAN`) becomes the Java method name verbatim.
   The `IBANRequest` parameter name is derived by decapitalizing the schema name
   (`IBANRequest` → `ibANRequest` — an all-caps-prefixed schema name produces this slightly odd
   camelCase; this is normal generator behavior, not a bug to fix).

3. **Your controller implements the interface** and does the real work
   (`data-validation-service/.../DataValidationController.java:35-49`):
   ```java
   @RestController
   @RequestMapping("/api/v1")
   @RequiredArgsConstructor
   public class DataValidationController implements IbanApi, VeriPhoneApi, ExistingDataApi {

       private final IBANValidationService ibanValidationService;

       @Override
       @JwtSecured(roles = "APP_DATA_VALIDATION")
       @Loggable
       public ResponseEntity<GenericResponse> validateIBAN(IBANRequest ibANRequest) {
           return ResponseEntity.ok(ibanValidationService.validate(ibANRequest));
       }
       // ...
   }
   ```
   A single controller class commonly `implements` **multiple** generated `*Api` interfaces (one
   per tag/feature area) and adds a class-level `@RequestMapping` prefix
   (`/api/v1`) on top of whatever paths the spec defines — the effective path is the concatenation.
   Every generated interface method must be annotated with `@Override`, plus the service's
   cross-cutting annotations:
   - `@JwtSecured(roles = "...")` (from `alramz-api-starter`, `com.alramz.jwt.annotation.JwtSecured`)
     for authorization — see the JWT/security howto for how roles are enforced.
   - `@Loggable` (from `alramz-api-starter`, `com.alramz.logging.aspect`) for request/response
     method-execution logging.
   The method body delegates to an injected `@Service`; it does not contain business logic itself.

4. **Error responses within a 200-mapped operation**: when a single `operationId` can produce
   different response shapes/status codes at runtime that the spec models as multiple `responses`
   entries (400/503/etc.), the controller catches typed exceptions and builds the
   alternate `ResponseEntity` manually rather than the framework doing it for you — see
   `DataValidationController.validateExistingData` (`data-validation-service/.../DataValidationController.java:61-82`)
   catching `ApplicationException`/`ExternalSystemException` and returning 400/503 `GenericResponse`
   bodies. Keep the exceptions typed (see the exception-handling howto) even though this method
   catches them locally instead of relying on `GlobalExceptionHandler` — do this only when the
   spec's own `responses` block requires a *different response schema* per status; if the schema
   is the same, prefer a typed exception + `GlobalExceptionHandler` instead of a local try/catch.

## 4. How to add a new endpoint

### 4a. Adding an operation to an existing service

1. Add the path/operation/schemas to `services/<service>/specs/apiSpecs.yaml`. Follow the existing
   conventions in that file:
   - `operationId` in `camelCase`, becomes the interface method name exactly.
   - Reuse `GenericResponse` as the response envelope (`responseCode`/`responseMessage`/`response`/
     `correlationId`) unless the endpoint has a genuinely different response contract (like
     `OnboardingResponse` in data-validation-service).
   - Add `'401'`/`'400'`/`'500'` response entries with descriptions if the endpoint is JWT-secured
     (matches `security: - BearerAuth: []` declared at the top of the spec).
   - Add new request/response schemas under `components.schemas`.
2. Regenerate: run `mvn -pl services/<service> -am generate-sources`, or just
   `mvn -pl services/<service> -am clean test-compile` (per CLAUDE.md's fast compile-check command)
   — either regenerates the interfaces as a side effect of any normal build phase.
3. Open the regenerated interface under
   `target/generated-sources/openapi/src/main/java/com/alramz/api/<Tag>Api.java` to see the exact
   method signature the compiler now expects.
4. Add `implements <Tag>Api` to the controller (or a new controller class) and implement the new
   method: `@Override`, `@JwtSecured(roles = "...")` if secured, `@Loggable`, delegate to a
   `@Service`.
5. Compile/test the service (`mvn -pl services/<service> -am clean test-compile`, then
   `...test` for behavioral changes, per CLAUDE.md's verification rule) to confirm the controller
   satisfies the interface.

### 4b. Wiring this pattern into a brand-new service

1. Create `services/<new-service>/specs/apiSpecs.yaml`. Minimal starter matching this repo's style:
   ```yaml
   openapi: 3.0.3
   info:
     title: <New Service> API
     version: 1.0.0
     description: <one line>
   servers:
     - url: http://localhost:8080
       description: Local Development
   security:
     - BearerAuth: []
   paths:
     /info:
       get:
         tags: [Info]
         operationId: getInfo
         responses:
           '200':
             content:
               application/json:
                 schema:
                   $ref: '#/components/schemas/GenericResponse'
   components:
     securitySchemes:
       BearerAuth:
         type: http
         scheme: bearer
         bearerFormat: JWT
     schemas:
       GenericResponse:
         type: object
         required: [responseCode, responseMessage, response]
         properties:
           responseCode: { type: string, example: "200" }
           responseMessage: { type: string, example: OK }
           response: { type: object }
           correlationId: { type: string, format: uuid }
   ```
2. Copy the `openapi-generator-maven-plugin` block from section 2 verbatim into the new service's
   `pom.xml` (same `apiPackage`/`modelPackage` — every service uses `com.alramz.api` /
   `com.alramz.model`, so there's no cross-service class collision because each service compiles
   its own copy independently).
3. Build once (`mvn -pl services/<new-service> -am clean compile`) to generate the interfaces, then
   write a controller in `com.alramz.controllers` implementing them, following section 3.
4. If the service also uses `alramz-api-starter`'s JWT auto-config, decide whether the endpoint is
   public (add its path to `company.jwt.permit-all-urls`) or role-secured (`@JwtSecured(roles=...)`)
   — see the JWT/security howto.

> The **alramz-service-scaffold** skill (`.claude/skills/alramz-service-scaffold/`) already
> automates steps 1–3 for a brand-new service scaffold. Use this section as the reference for
> *why* the generated files look the way they do, or when adding endpoints to a service that
> already exists.

## 5. Common pitfalls / anti-patterns

- **Editing generated files directly.** Anything under `target/generated-sources/` is regenerated
  (and silently overwritten) on every build. Never hand-edit files there — change the YAML spec
  instead. The generated file's own header says this explicitly (`Do not edit the class manually`).
- **Forgetting to regenerate after editing the spec.** If you edit `apiSpecs.yaml` but only run an
  IDE "build" that doesn't invoke Maven's `generate-sources`, your IDE may still show the stale
  interface from the last Maven run. Always confirm with a real `mvn ... compile` before assuming
  the new method exists.
- **`operationId` / method-name mismatches.** The generator turns `operationId` into the Java
  method name character-for-character. If you rename an `operationId` in the spec, every
  implementing controller breaks at compile time (which is the point — grep the compiler error's
  interface name to find every controller that needs updating).
- **A hand-written endpoint that shadows an unused generated interface.** This actually exists in
  this repo today and is worth knowing about rather than copying:
  `reference-data-service/specs/apiSpecs.yaml` declares `GET /info` → generates `InfoApi` with
  `getInfo()` at path `/info`, but `ApplicationController`
  (`reference-data-service/.../controllers/ApplicationController.java`) instead hand-writes a
  plain `@GetMapping("/api/v1/info")` method that never implements `InfoApi` at all — so the
  generated `InfoApi` interface is dead code and the real `/api/v1/info` endpoint isn't reflected
  in the OpenAPI spec's path (which says `/info`, no `/api/v1` prefix). **Do not copy this** for a
  new service: if an endpoint exists, its spec path must match what's actually mapped, and its
  controller must `implements` the generated interface like every other endpoint in these two
  services does. If you find yourself wanting a hand-written controller method with no interface,
  that's a signal the spec is missing the operation — add it instead.
- **Response envelope drift.** Almost every operation reuses `GenericResponse` as the schema, with
  a class-name-preserving-case field `IBANRequest` → `ibANRequest` style variable naming. Don't
  invent a bespoke response schema for a new endpoint unless the data genuinely doesn't fit
  `responseCode`/`responseMessage`/`response`/`correlationId` (e.g. `OnboardingResponse` legitimately
  diverges because it needs `member_reference_number`/`internal_error_code` at the top level for
  every status code, not nested under `response`).
- **Path prefix confusion.** The class-level `@RequestMapping("/api/v1")` on the controller is
  *added in front of* whatever path the spec/interface declares. If the spec already writes
  `/api/v1/...` paths, don't also add the controller-level prefix (this repo does not do that —
  specs write bare paths like `/iban/validate`, and the controller supplies `/api/v1`). Pick one
  approach per service and stay consistent with these two reference services.

## 6. Checklist for a new or modified endpoint

- [ ] Path, `operationId`, request/response schemas added to `specs/apiSpecs.yaml`.
- [ ] `'401'`/`'400'`/`'500'` responses declared if the endpoint requires a JWT (matches the
      service's `security: - BearerAuth: []`).
- [ ] `mvn -pl services/<service> -am clean test-compile` run to regenerate and confirm the
      interface exists at `target/generated-sources/openapi/.../api/<Tag>Api.java`.
- [ ] Controller `implements <Tag>Api`, method has `@Override`, `@JwtSecured(roles=...)` (or the
      endpoint's path is in `company.jwt.permit-all-urls` if intentionally public), and `@Loggable`.
- [ ] Method delegates to a `@Service`, not inline business logic in the controller.
- [ ] No files under `target/generated-sources/` were hand-edited.
- [ ] `mvn -pl services/<service> -am clean test` passes (CLAUDE.md verification rule).
