---
name: create-agentsmd-java
description: 'Generate a complete, accurate AGENTS.md for a Java repository (Maven or Gradle, single-module or multi-module, plain Java, Spring Boot, Quarkus, Micronaut, Jakarta EE, or Android/Kotlin-mixed). Use this whenever the user asks to create, write, update, or improve an AGENTS.md, CLAUDE.md, agent instructions, or "README for agents" in a project containing pom.xml, build.gradle, build.gradle.kts, settings.gradle, mvnw, or gradlew, even if they do not mention Java explicitly.'
---

# Create a high-quality AGENTS.md for a Java project

You are a code agent. Your task is to create a complete, accurate `AGENTS.md` at the root of this Java repository that follows the public guidance at https://agents.md/.

AGENTS.md is an open format that gives coding agents the context and instructions they need to work effectively on a project. It is a "README for agents": it complements `README.md` with the precise technical detail an agent needs (exact build commands, JDK version, test tiers, style gates) that would clutter a human-focused README.

Every command you write must come from the repository itself. Never invent a Maven goal, Gradle task, profile, or property. If you cannot confirm something, leave it out or mark it clearly as unverified.

## Key principles

- **Agent-focused**: exact commands, flags, and file paths an automated tool can run as written.
- **Wrapper-first**: always document `./mvnw` or `./gradlew` when a wrapper exists, never a globally installed `mvn` or `gradle`.
- **Complements README.md**: do not repeat marketing or onboarding prose; link to it instead.
- **Standardized location**: root `AGENTS.md`, plus nested ones in large multi-module builds.
- **Verified**: every command listed has been run (or its source file located) during analysis.

## Step 1 — Discover the build system and toolchain

Run these checks first. The answers decide almost every section of the file.

```bash
# Build tool and wrapper
ls pom.xml mvnw build.gradle build.gradle.kts settings.gradle settings.gradle.kts gradlew 2>/dev/null
cat .mvn/wrapper/maven-wrapper.properties gradle/wrapper/gradle-wrapper.properties 2>/dev/null

# Required JDK version (check all of these; they can disagree)
cat .java-version .sdkmanrc .tool-versions 2>/dev/null
grep -nE "maven.compiler.(release|source|target)|<java.version>|<release>" pom.xml
grep -nE "languageVersion|sourceCompatibility|targetCompatibility|jvmToolchain" build.gradle* 2>/dev/null

# Modules
grep -n "<module>" pom.xml
grep -nE "include\(|include '" settings.gradle* 2>/dev/null

# Version catalogs / BOMs
ls gradle/libs.versions.toml 2>/dev/null
grep -n "<dependencyManagement>" -A 20 pom.xml | grep -E "artifactId|bom"

# Framework
grep -rlE "spring-boot|quarkus|micronaut|jakarta|dropwizard|helidon" pom.xml build.gradle* */pom.xml */build.gradle* 2>/dev/null

# CI
ls .github/workflows .gitlab-ci.yml Jenkinsfile azure-pipelines.yml .circleci 2>/dev/null
```

Record for the file:

- Build tool, wrapper presence, and wrapper version.
- Minimum and target JDK (e.g. "requires JDK 21; code compiles with `--release 17`"). If a Gradle toolchain is configured, say Gradle will provision the JDK.
- Whether it is a multi-module build, and the module list with a one-line purpose each.
- Framework and its version (Spring Boot 3.x vs 2.x matters: `jakarta.*` vs `javax.*`).
- Kotlin or Groovy sources alongside Java, if any.

## Step 2 — Identify the real workflows

Read these sources, in this order, and extract only what exists:

1. **CI configuration** (`.github/workflows/*.yml`, `Jenkinsfile`, etc.). CI shows the command that actually gates merges; treat it as the source of truth for "the full check".
2. **Build file plugins**. In `pom.xml`, look at `<plugins>` and `<profiles>`; in Gradle, at `plugins { }` and custom `tasks.register`. Note the plugins listed in the table below.
3. **Gradle task list**: `./gradlew tasks --all` (fast, and reveals custom tasks).
4. **Maven effective setup** when profiles are involved: `./mvnw help:all-profiles` and, if needed, `./mvnw help:effective-pom -q`.
5. **Docs**: `README.md`, `CONTRIBUTING.md`, `docs/`, `.editorconfig`.
6. **Runtime config**: `src/main/resources/application*.yml|properties`, `.env.example`, `docker-compose*.yml`, `compose.yaml`.

Plugins to look for and what they mean for AGENTS.md:

| Plugin / tool | Implication |
|---|---|
| maven-surefire / Gradle `test` | Unit tests; document single-test syntax |
| maven-failsafe / `integrationTest` source set | Separate integration tier, usually `*IT.java`, run via `verify` |
| Testcontainers dependency | Integration tests need a running Docker daemon |
| JaCoCo | Coverage report path and any enforced minimum (`jacoco:check` rules) |
| Spotless | Formatter; document `spotless:apply` / `spotlessApply` and that `check` fails on unformatted code |
| Checkstyle, PMD, SpotBugs, Error Prone, NullAway | Static analysis gates; name the config file location |
| maven-enforcer | Enforced JDK/Maven versions or banned dependencies |
| OpenAPI / jOOQ / MapStruct / Lombok / protobuf generators | Generated sources; tell agents not to edit them and how to regenerate |
| Flyway / Liquibase | Migration location and naming rules; never edit applied migrations |
| Jib / spring-boot `build-image` / Dockerfile | Container build command |
| ArchUnit | Architecture rules enforced in tests; agents must respect layer boundaries |

## Step 3 — Write the sections

### Project overview

- One or two sentences on what the application or library does.
- Architecture: layers (e.g. `controller` → `service` → `repository`), hexagonal ports/adapters, or module graph for multi-module builds.
- Tech stack with versions: JDK, framework, build tool, database, messaging, key libraries.

### Setup

- JDK requirement and how to get it (`sdk env install` if `.sdkmanrc` exists, or toolchain auto-provisioning).
- Dependency resolution: `./mvnw -q dependency:go-offline` or `./gradlew dependencies`.
- Private repositories: point to `~/.m2/settings.xml` or `gradle.properties` keys *by name only*, never values.
- Local infrastructure: `docker compose up -d` if a compose file exists, with which services it starts.
- Required environment variables, taken from `.env.example` or `application.yml` placeholders.

### Development workflow

- Run the app: `./mvnw spring-boot:run`, `./gradlew bootRun`, `./mvnw quarkus:dev`, `./gradlew run`, or the `java -jar` form, whichever applies.
- Active profile selection (`-Dspring-boot.run.profiles=local`, `SPRING_PROFILES_ACTIVE=local`, `-Dquarkus.profile=dev`).
- Hot reload: Spring DevTools, Quarkus dev mode, or `./gradlew -t` continuous build.
- Fast compile check without tests: `./mvnw -q compile` / `./gradlew compileJava`.
- Offline/fast flags that the project tolerates (`-o`, `--offline`, `-T 1C`, `--parallel`, `--build-cache`).

### Testing

Document each tier separately and give the exact command for each:

- All tests: the CI command (commonly `./mvnw verify` or `./gradlew check`).
- Unit tests only: `./mvnw test` / `./gradlew test`.
- Integration tests: `./mvnw verify -DskipUTs` (only if configured), `./mvnw failsafe:integration-test`, or `./gradlew integrationTest`.
- Single class and single method:
  - Maven: `./mvnw test -Dtest=OrderServiceTest` and `./mvnw test -Dtest='OrderServiceTest#cancelsExpiredOrder'`
  - Gradle: `./gradlew test --tests 'com.example.order.OrderServiceTest'` and `--tests '*OrderServiceTest.cancelsExpiredOrder'`
- Single module: `./mvnw -pl order-service -am test` / `./gradlew :order-service:test`.
- Coverage: the report path (e.g. `target/site/jacoco/index.html`, `build/reports/jacoco/test/html/index.html`) and any enforced threshold.
- Conventions: framework (JUnit 5, TestNG, Spock), assertion library (AssertJ, Hamcrest), mocking (Mockito), naming (`*Test` vs `*IT`), location (`src/test/java` mirroring the main package), slice tests (`@WebMvcTest`, `@DataJpaTest`) versus full `@SpringBootTest`.
- Prerequisites: Docker for Testcontainers, and any test profile (`application-test.yml`).

### Code style

- Formatter and the command to apply it (`./mvnw spotless:apply`, `./gradlew spotlessApply`); state which style (Google Java Format, Palantir, Eclipse config file path).
- Static analysis commands and config locations (`config/checkstyle/checkstyle.xml`, etc.).
- Package structure and base package (e.g. `com.acme.billing`), feature-based vs layer-based packaging.
- Conventions observed in the code: constructor injection over field injection, `final` fields, records for DTOs, `Optional` usage, no wildcard imports, Lombok usage (or its absence), logging via SLF4J, exception handling pattern (`@ControllerAdvice`, custom exception hierarchy).
- Generated code directories that must not be edited by hand.

### Build and deployment

- Package: `./mvnw -DskipTests package` / `./gradlew build -x test` (only if the project uses that), and the artifact path (`target/*.jar`, `build/libs/*.jar`).
- Container image: `./mvnw spring-boot:build-image`, `./gradlew jibDockerBuild`, or `docker build .`.
- Native image, if configured (`-Pnative`, `nativeCompile`).
- Release/versioning mechanism (maven-release-plugin, axion, semantic-release, manual `<version>` bumps).
- What CI runs, in order, and which job gates merges.

### Optional but recommended sections

**Security**: where secrets come from (env vars, Vault, AWS Secrets Manager), and that agents must never commit credentials or put them in `application.yml`; Spring Security configuration location and auth model (JWT, OAuth2 resource server, session); dependency scanning (OWASP dependency-check, Snyk) and its command.

**Database and migrations**: migration folder (e.g. `src/main/resources/db/migration`), naming scheme (`V<n>__<description>.sql`), rule that applied migrations are immutable, and how to reset the local database.

**Multi-module instructions**: module map, dependency direction between modules, `-pl <module> -am` / `:module:task` patterns, and which module holds shared code.

**Pull request guidelines**: title and commit format (read from `CONTRIBUTING.md`, commitlint config, or recent `git log --oneline -20`), and the checks that must pass locally before pushing.

**Debugging and troubleshooting**: remote debug (`-Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"`, `./gradlew bootRun --debug-jvm`), log level overrides, common failures you actually observed (wrong JDK on PATH, Docker not running, stale generated sources, annotation processing not enabled).

## Example template

Start from this and remove anything that does not apply. Replace every bracket with a verified value.

```markdown
# AGENTS.md

## Project overview

[One or two sentences on what it does.]

- Stack: Java [21], [Spring Boot 3.3], [Maven 3.9 via ./mvnw], [PostgreSQL 16]
- Architecture: [layered: web → service → persistence] / [modules: api, core, persistence]
- Base package: `[com.example.app]`

## Setup

- JDK [21] required (`sdk env install` reads `.sdkmanrc`).
- Start local services: `docker compose up -d` ([postgres, redis])
- Copy `.env.example` to `.env`; required vars: `[DB_URL]`, `[DB_USER]`, `[DB_PASSWORD]`

## Development

- Run the app: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
- Compile only: `./mvnw -q compile`
- App listens on `http://localhost:[8080]`; health at `/actuator/health`

## Testing

- Full check (same as CI): `./mvnw verify`
- Unit tests: `./mvnw test`
- One class: `./mvnw test -Dtest=[ClassName]`
- One method: `./mvnw test -Dtest='[ClassName#methodName]'`
- Integration tests (`*IT.java`, need Docker for Testcontainers): `./mvnw verify -DskipUTs`
- Coverage report: `target/site/jacoco/index.html` (build fails below [80]% line coverage)
- JUnit 5 + AssertJ + Mockito. Tests mirror the main package under `src/test/java`.
- Add or update tests for every behavior change.

## Code style

- Format before committing: `./mvnw spotless:apply` (Google Java Format). `verify` fails on unformatted code.
- Checkstyle config: `config/checkstyle/checkstyle.xml`
- Constructor injection only; no field `@Autowired`.
- DTOs are `record`s; entities live in `[..].persistence`.
- Do not edit `target/generated-sources/**`; regenerate with `./mvnw generate-sources`.

## Database

- Flyway migrations: `src/main/resources/db/migration/V<n>__<snake_case>.sql`
- Never modify a migration that is already on `main`; add a new one.

## Build and deployment

- Jar: `./mvnw -DskipTests package` → `target/[app]-<version>.jar`
- Image: `./mvnw spring-boot:build-image`
- CI: `.github/workflows/ci.yml` runs `./mvnw -B verify` on JDK [21].

## Pull requests

- Title: `[module] Short description`
- Before pushing: `./mvnw spotless:apply verify`

## Gotchas

- [Wrong JDK on PATH causes "release version 21 not supported"; check `java -version`.]
- [Integration tests hang if Docker is not running.]
```

## Worked example (Gradle multi-module)

```markdown
# AGENTS.md

## Dev environment tips

- Always use `./gradlew`; the toolchain block provisions JDK 21 automatically.
- Modules: `:api` (REST layer), `:domain` (pure Java, no Spring), `:infra` (JPA, Kafka). `:domain` must not depend on the others; ArchUnit enforces this.
- Versions live in `gradle/libs.versions.toml`; add dependencies there, not inline.
- Use `./gradlew projects` to list modules and `./gradlew :api:dependencies` to inspect one.

## Testing instructions

- CI runs `./gradlew check --build-cache` (see `.github/workflows/build.yml`).
- One module: `./gradlew :domain:test`
- One test: `./gradlew :api:test --tests '*OrderControllerTest.returns404ForUnknownOrder'`
- Integration tests are in the `integrationTest` source set: `./gradlew :infra:integrationTest` (Docker required).
- Fix every failing test and Spotless violation before finishing; run `./gradlew spotlessApply` first.

## PR instructions

- Title format: `[<module>] <Title>`
- Always run `./gradlew spotlessApply check` before committing.
```

## Multi-module and monorepo considerations

- Put the root `AGENTS.md` at the repository root with the module map and shared commands.
- Add a nested `AGENTS.md` inside a module only when it has materially different rules (e.g. a frontend module, a module with its own database, or an Android app). The closest `AGENTS.md` to a file takes precedence.
- Show how to build and test one module in isolation, including its upstream modules (`-pl X -am` in Maven).
- State dependency direction between modules so agents do not introduce cycles.

## Verify before finishing

1. Run the compile command and the unit test command you documented; confirm they succeed or note known pre-existing failures.
2. Run the single-test command against one real test class to confirm the syntax.
3. Confirm each file path you mention exists (`ls` it).
4. Confirm the JDK version in AGENTS.md matches the build file and CI.
5. Remove every placeholder bracket and every section that does not apply.

If a command is too slow or needs unavailable infrastructure (e.g. Docker), do not delete it; keep it and note the prerequisite.

## Best practices

- **Be specific**: `./mvnw -pl billing -am test`, not "run the tests for billing".
- **Prefer the CI command** as the canonical "full check".
- **Explain the why** briefly when a rule is non-obvious (e.g. "`domain` has no Spring dependency so it stays unit-testable").
- **Keep it short**: aim for something an agent reads in under a minute; link to `docs/` for depth.
- **Never include secrets**, internal hostnames, or tokens; reference variable names only.
- **Stay current**: if you change build plugins, JDK version, or module layout, update AGENTS.md in the same change.

The goal is that any coding agent can clone the repository, read `AGENTS.md`, and build, test, format, and submit a correct change without further human guidance.
