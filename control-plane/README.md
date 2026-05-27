# infraforge — Control Plane

The Control Plane is the **async workflow engine** of infraforge. It runs independently of the developer's chat session and owns everything that happens after the agent calls `submit_request()`: opening a GitHub PR, monitoring CI, driving state transitions, sending email notifications, and publishing an immutable audit trail.

---

## Responsibilities

| Owns | Does NOT own |
|---|---|
| Request lifecycle state machine | LLM inference / Terraform generation |
| GitHub PR creation and CI monitoring | Conversational UX |
| Developer email notifications (SES) | Policy RAG |
| Audit event publishing (EventBridge) | |
| JWT issuance + GitHub OAuth | |
| Internal API for agent tool calls | |
| Public API for UI (request history + status) | |

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Java 25 LTS |
| Framework | Spring Boot 3.5 |
| Build | Gradle 8.12 (version catalog: `gradle/libs.versions.toml`) |
| State machine | Spring Statemachine 4.x (Phase 2) |
| State persistence | AWS DynamoDB via SDK v2 Enhanced Client |
| Async queue | AWS SQS |
| Email | AWS SES v2 |
| Audit events | AWS EventBridge |
| Secrets | AWS Secrets Manager |
| Terraform artefacts | AWS S3 |
| Auth | GitHub OAuth 2.0 → JWT (HS256 / JJWT 0.12) |

---

## Architecture

### Hexagonal Architecture (Ports & Adapters)

The domain layer has zero AWS imports. All I/O is behind interfaces in `io.infraforge.ports`.

```mermaid
graph TD
    subgraph API["API Layer"]
        RC[RequestController]
        IC[InternalController]
        WC[WebhookController]
        AC[AuthController]
    end

    subgraph Domain["Domain Layer"]
        IR[InfraRequest]
        RS[RequestState sealed]
        WS[WorkflowService Phase 2]
    end

    subgraph Ports["Ports"]
        P1[StateStorePort]
        P2[MessageQueuePort]
        P3[SecretStorePort]
        P4[EventBusPort]
        P5[NotificationPort]
        P6[ObjectStoragePort]
    end

    subgraph AWS["AWS Adapters — @Profile aws or local"]
        AD1[DynamoDbStateStoreAdapter]
        AD2[SqsMessageQueueAdapter]
        AD3[AwsSecretsAdapter]
        AD4[EventBridgeEventBusAdapter]
        AD5[SesNotificationAdapter]
        AD6[S3ObjectStorageAdapter]
    end

    subgraph Local["Local Adapters — @Profile test"]
        LA1[InMemoryStateStoreAdapter]
        LA2[InMemoryMessageQueueAdapter]
        LA3[InMemorySecretStoreAdapter]
        LA4[NoOpEventBusAdapter]
        LA5[LoggingNotificationAdapter]
        LA6[InMemoryObjectStorageAdapter]
    end

    RC --> IR
    IC --> IR
    WC --> IR
    IC --> P2
    WC --> P2
    WS --> P1
    WS --> P2
    WS --> P4
    WS --> P5

    P1 --> AD1
    P2 --> AD2
    P3 --> AD3
    P4 --> AD4
    P5 --> AD5
    P6 --> AD6

    P1 --> LA1
    P2 --> LA2
    P3 --> LA3
    P4 --> LA4
    P5 --> LA5
    P6 --> LA6
```

---

### Request State Machine

Every `InfraRequest` progresses through a sealed state hierarchy. Pattern matching at compile time makes missing transitions impossible.

```mermaid
stateDiagram-v2
    direction LR
    [*] --> SUBMITTED : POST /internal/requests

    SUBMITTED --> PR_CREATED    : GitHub PR opened\n+ Terraform committed
    SUBMITTED --> FAILED        : GitHub API error

    PR_CREATED --> PLAN_RUNNING : check_run started\n(webhook)
    PR_CREATED --> FAILED       : CI never starts

    PLAN_RUNNING --> PLAN_APPROVED : plan success\n+ cost within budget\n(webhook)
    PLAN_RUNNING --> FAILED        : plan failure\nor security violation

    PLAN_APPROVED --> APPLYING  : PR merged to main\n(webhook)

    APPLYING --> DEPLOYED       : apply success\n(webhook)
    APPLYING --> FAILED         : apply failure\n(webhook)

    FAILED --> [*]
    DEPLOYED --> [*]

    note right of SUBMITTED     : SQS event enqueued
    note right of PLAN_APPROVED : Email sent to developer
    note right of DEPLOYED      : Email sent to developer
    note right of FAILED        : Email sent to developer
```

---

### Authentication & Security Filter Chains

Three independent Spring Security filter chains, ordered by specificity:

```mermaid
flowchart TD
    REQ([Incoming Request])

    REQ --> M1{"/internal/**?"}
    M1 -->|yes| SK[ServiceKeyAuthenticationFilter\nX-Service-Key header]
    SK -->|valid| I_GRANTED[Internal API — agent tool calls]
    SK -->|invalid| I_401[401 Unauthorized]

    REQ --> M2{"/api/**?"}
    M2 -->|yes| JWT[JwtAuthenticationFilter\nAuthorization: Bearer]
    JWT -->|valid| A_GRANTED[Public API — UI requests]
    JWT -->|invalid| A_401[401 Unauthorized]

    REQ --> M3{"/auth/**?"}
    M3 -->|yes| OAUTH[Spring Security OAuth2\nGitHub login flow]
    OAUTH --> ISSUE[OAuth2SuccessHandler\nIssue CP JWT → httpOnly cookie]
    OAUTH -.->|POST /auth/token| TOKEN[AuthController\nGitHub token → CP JWT]
```

---

### GitHub Webhook Flow

```mermaid
sequenceDiagram
    participant GH as GitHub Actions
    participant WC as WebhookController
    participant Q as MessageQueuePort
    participant WE as WorkflowEngine (Phase 2)
    participant CP as StateStore + Notification

    GH->>WC: POST /api/webhooks/github\nX-Hub-Signature-256: sha256=...\nX-GitHub-Event: check_run
    WC->>WC: Verify HMAC-SHA256 signature
    WC->>WC: Parse branch name → requestId\n(convention: infraforge/{requestId})
    WC->>Q: publish(WorkflowEvent{PLAN_COMPLETED | APPLY_COMPLETED | APPLY_FAILED})
    Q->>WE: Deliver event (virtual thread)
    WE->>CP: transition(requestId, newState)
    WE->>CP: send NotificationMessage (email)
    WE->>CP: publish AuditEvent
```

---

## API Reference

### Public API (`/api/**`) — JWT required

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/requests` | List all requests for authenticated user |
| `GET` | `/api/requests/{id}` | Get single request status |
| `GET` | `/api/me` | Current user profile |
| `POST` | `/api/webhooks/github` | GitHub Actions webhook receiver |

### Internal API (`/internal/**`) — Service key required

| Method | Path | Description |
|---|---|---|
| `POST` | `/internal/requests` | Submit a new infra request (agent tool) |
| `GET` | `/internal/requests/{id}` | Get request status (agent tool) |
| `GET` | `/internal/policies?teamId=` | Retrieve team policies (stub → Phase 3) |
| `GET` | `/internal/budget?teamId=` | Check team budget (stub → Phase 3) |
| `POST` | `/internal/validate` | OPA policy validation (stub → Phase 3) |

### Auth (`/auth/**`) — Public

| Method | Path | Description |
|---|---|---|
| `GET` | `/auth/login` | Initiate GitHub OAuth flow |
| `GET` | `/auth/callback` | GitHub OAuth callback (Spring Security) |
| `POST` | `/auth/token` | Exchange GitHub token for CP JWT |

---

## Package Structure

```
src/main/java/io/infraforge/
├── InfraforgeApplication.java
├── domain/            # Pure domain — zero framework dependencies
│   ├── InfraRequest.java        # Immutable record
│   ├── RequestState.java        # Sealed interface + 7 nested records
│   ├── WorkflowEvent.java
│   ├── AuditEvent.java
│   ├── NotificationMessage.java
│   └── User.java
├── ports/             # Cloud-agnostic interfaces
│   ├── StateStorePort.java
│   ├── MessageQueuePort.java
│   ├── SecretStorePort.java
│   ├── EventBusPort.java
│   ├── NotificationPort.java
│   └── ObjectStoragePort.java
├── adapters/
│   ├── aws/           # AWS SDK v2 implementations
│   └── local/         # In-memory implementations (dev/test)
├── config/            # Spring @Configuration classes
│   ├── AwsClientConfig.java     # SDK clients (profile-split)
│   ├── AwsAdapterConfig.java    # Wires AWS adapters (@Profile aws,local)
│   ├── LocalAdapterConfig.java  # Wires local adapters (@Profile test)
│   ├── SecurityConfig.java      # 3 filter chains
│   └── InfraforgeProperties.java
├── auth/              # JWT, OAuth, security filters
├── api/               # REST controllers + DTOs
└── workflow/          # State machine (Phase 2)
```

---

## Running Locally

```bash
# Option A — In-memory only (no Docker needed)
./gradlew test                                    # all tests pass, zero deps

# Option B — LocalStack (realistic AWS locally)
docker compose up localstack opa                  # from repo root
./gradlew bootRun --args='--spring.profiles.active=local'

# Option C — Against real AWS
export SPRING_PROFILES_ACTIVE=aws
export AWS_REGION=us-east-1
# ... set all env vars from application-aws.yml
./gradlew bootRun
```

> **First run:** Generate the Gradle wrapper binary once:
> ```bash
> gradle wrapper --gradle-version 8.12
> ```

---

## Environment Variables

| Variable | Profile | Description |
|---|---|---|
| `GITHUB_CLIENT_ID` | all | GitHub OAuth App client ID |
| `GITHUB_CLIENT_SECRET` | all | GitHub OAuth App client secret |
| `INFRAFORGE_JWT_SECRET` | all | HS256 signing key (≥ 32 chars) |
| `INFRAFORGE_SERVICE_KEY` | all | Pre-shared key for agent auth |
| `GITHUB_APP_TOKEN` | aws | GitHub App installation token (PR creation) |
| `GITHUB_WEBHOOK_SECRET` | aws | Secret for validating GitHub webhooks |
| `DYNAMODB_TABLE` | aws | DynamoDB table name |
| `SQS_WORKFLOW_QUEUE_URL` | aws | SQS queue URL |
| `S3_TERRAFORM_BUCKET` | aws | S3 bucket for Terraform files |
| `SES_FROM_EMAIL` | aws | Verified SES sender address |
| `EVENTBRIDGE_BUS_NAME` | aws | EventBridge event bus name |
