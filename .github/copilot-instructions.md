# Copilot Instructions

## Project Overview

Backstage backend plugin for **Risk Management as Code (RiSc)**. Risk assessments are stored as SOPS-encrypted YAML files in GitHub repositories under `.security/risc/`. The plugin manages their lifecycle: create, edit, publish, and approve via GitHub PR workflows.

## Tech Stack

- **Kotlin** 2.3.0 + **Spring Boot** 3.5.7 (Gradle Kotlin DSL)
- **Kotlinx Serialization** + Coroutines for async processing
- **Spring Security OAuth2** with JWT/OIDC (Entra ID / Microsoft)
- **SpringDoc OpenAPI** 2.8.4 (Swagger UI at `/swagger-ui.html`)
- **ktlint** 1.6.0 for formatting
- Target JDK 25

## Build, Test, Lint

```bash
./gradlew build                          # Full build
./gradlew test                           # All tests
./gradlew test --tests no.risc.SomeTest  # Single test class
./gradlew test --tests "no.risc.SomeTest.methodName"  # Single test method
./gradlew ktlintCheck                    # Lint check
./gradlew ktlintFormat                   # Auto-format
```

**Local dev:**
```bash
cp .env.example .env.local
./run-local.sh          # Runs with spring.profiles.active=local
# OR via IntelliJ run config: "✨Local Server"
```

**Docker:**
```bash
docker-compose build app
docker-compose up app
```

Requires the Backstage frontend plugin running on port 7007 as the OAuth issuer.

## Git Workflow

- **Never commit directly to `main`.** All changes must go on a new branch.
- Branch names are lowercase and hyphen-separated, descriptive of the change (e.g., `add-slack-notification-support`, `fix-risc-validation-error`).
- Before committing, run `./gradlew ktlintFormat` to format the code.
- Commit messages are descriptive and written in the imperative mood (e.g., `Add support for v5 schema migration`, `Fix null pointer in encryption service`).
- Open a pull request from the branch into `main` when the work is ready for review.

## Architecture

### Request Flow

```
RiScController → RiScService → GitHubConnector / CryptoServiceConnector
                            → GoogleConnector (GCP KMS for key management)
                            → InitRiScConnector (template generation)
```

All external service calls use Spring `WebClient` (reactive), wrapped in coroutines (`runBlocking` / `suspend`). Controllers are synchronous Spring MVC (`@RestController`), services bridge to async via coroutines.

### Key Packages (`no.risc.*`)

| Package | Responsibility |
|---|---|
| `risc/` | Core CRUD: Controller, Service, Models, DTOs |
| `github/` | GitHub API integration, branch/PR/commit management |
| `encryption/` | SOPS encryption via external crypto service |
| `security/` | JWT validation, auth filters, token extraction |
| `validation/` | JSON schema validation for RiSc YAML content |
| `initRiSc/` | RiSc template generation via external service |
| `google/` | GCP KMS key listing, permission checks |
| `infra/` | WebClient configs, OAuth token relay, connector base classes |
| `utils/` | RiSc diff/comparison, schema migration, serialization helpers |
| `exception/` | 28+ typed exceptions + `GlobalExceptionHandler` (`@ControllerAdvice`) |

### RiSc Lifecycle & Storage

- **File location:** `.security/risc/<prefix>-<5chars>.<postfix>.yaml` in the target repo
- **Branch naming:** Branch name = RiSc ID (the `<prefix>-<5chars>` portion)
- **Approval flow:** Changes go to a branch → PR → approval via commit message flags
- **Status values:** `Draft`, `Published`, `PendingApproval` (tracked in GitHub PR state)

### API Endpoints

Base path: `/api/risc` — all requests require headers:
- `GCP-Access-Token`
- `GitHub-Access-Token`

```
GET    /{owner}/{repo}/all                    # Fetch all RiScs
GET    /{owner}/{repo}/{version}/all          # Version-specific fetch
POST   /{owner}/{repo}                        # Create RiSc
PUT    /{owner}/{repo}/{id}                   # Update RiSc
DELETE /{owner}/{repo}/{id}                   # Delete RiSc
POST   /{owner}/{repo}/publish/{id}           # Publish RiSc
POST   /{owner}/{repo}/{riscId}/difference    # Get diff
```

### Data Models & Serialization

Models use `@Serializable` (kotlinx.serialization) with data classes and sealed interfaces. Custom serializers exist for special types: `FlattenSerializer`, `KNullableOffsetDateTimeSerializer`. Enums are `@Serializable` inline in sealed interfaces.

RiSc schema versioning: v3.x/v4.x use action statuses `NOT_STARTED`, `IN_PROGRESS`, `ON_HOLD`, `COMPLETED`, `ABORTED`; v5.x uses `OK`, `NOT_OK`, `NOT_RELEVANT`. Migration utilities in `utils/` handle version upgrades.

### Exception Handling

All exceptions extend typed base classes and are caught by `GlobalExceptionHandler`. Each exception maps to a specific DTO response (`ProcessRiScResultDTO`, `RiScContentResultDTO`, `DeleteRiScResultDTO`) with a `ProcessingStatus`/`ContentStatus` enum. HTTP 401 for token/permission issues, 403 for access denied, 500 for processing errors.
