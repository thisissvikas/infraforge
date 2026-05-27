# infraforge — High-Level Architecture

## System Overview

infraforge is a chat-first Internal Developer Platform. Developers describe infrastructure in natural language; an AI agent generates policy-compliant Terraform and hands it off to an async workflow engine that owns the GitHub PR and CI/CD lifecycle.

```mermaid
graph TB
    Dev["🧑‍💻 Developer\n(Browser)"]

    subgraph infraforge["infraforge Platform"]
        UI["UI\nNext.js 15 · React 19\n:3000"]
        Agent["Chat Agent\nLangGraph · FastAPI\n:8000"]
        CP["Control Plane\nSpring Boot 3.5 · Java 25\n:8080"]
    end

    subgraph AWS["AWS"]
        Bedrock["Amazon Bedrock\nClaude (LLM inference)"]
        BedrockKB["Bedrock Knowledge Bases\nPolicy RAG"]
        DDB["DynamoDB\nRequest state + audit"]
        SQS["SQS\nWorkflow queue"]
        SES["SES v2\nEmail notifications"]
        S3["S3\nTerraform artefacts"]
        EB["EventBridge\nAudit event bus"]
        SM["Secrets Manager\nJWT key, tokens"]
    end

    subgraph GitHub["GitHub"]
        PR["Pull Requests"]
        CI["GitHub Actions\nplan · security · apply"]
        Repo["Infra Repo\nHCL + OPA policies"]
    end

    Dev -->|"HTTPS (Auth.js session)"| UI
    UI -->|"JWT Bearer"| CP
    UI -->|"JWT Bearer"| Agent
    Agent -->|"LLM calls"| Bedrock
    Agent -->|"RAG retrieval"| BedrockKB
    Agent -->|"Service key /internal/**"| CP
    CP -->|"State persistence"| DDB
    CP -->|"Enqueue workflow events"| SQS
    CP -->|"Developer emails"| SES
    CP -->|"Terraform files"| S3
    CP -->|"Audit events"| EB
    CP -->|"Read secrets"| SM
    CP -->|"Open PR / monitor CI"| PR
    CI -->|"check_run webhook"| CP
    CI -->|"terraform apply"| Repo
```

---

## Two-Phase Model

Infrastructure provisioning spans two fundamentally different timescales. infraforge handles each with a purpose-built component.

```mermaid
sequenceDiagram
    autonumber
    actor Dev as Developer
    participant UI
    participant Agent as Chat Agent
    participant CP as Control Plane
    participant GH as GitHub + CI
    participant AWS as AWS Target

    Note over Dev,Agent: Phase 1 — Conversational (seconds)
    Dev->>UI: "I need a payments service on ECS"
    UI->>Agent: POST /chat {message}
    Agent->>CP: GET /internal/policies?teamId=payments
    Agent->>CP: GET /internal/budget?teamId=payments
    Agent-->>UI: "Got it — PCI or non-PCI data?"
    Dev->>UI: "PCI, staging first"
    UI->>Agent: POST /chat {message}
    Agent->>Agent: generate + validate Terraform
    Agent->>CP: POST /internal/validate {plan}
    Agent-->>UI: "~$340/mo, security passed. Submit?"
    Dev->>UI: "Yes, go ahead"
    UI->>Agent: POST /chat "go ahead"
    Agent->>CP: POST /internal/requests {terraform, intent}
    CP-->>Agent: {requestId: "req-abc"}
    Agent-->>UI: "Submitted ✓ req-abc"

    Note over CP,AWS: Phase 2 — Async workflow (minutes → hours)
    CP->>GH: Create branch + commit Terraform + open PR
    GH->>GH: terraform plan · tfsec · checkov · OPA · infracost
    GH-->>CP: check_run webhook (plan complete)
    CP->>CP: Transition PLAN_RUNNING → PLAN_APPROVED
    CP->>Dev: Email "PR ready: github.com/.../pull/42"
    Dev->>GH: Review + merge PR
    GH->>GH: terraform apply
    GH-->>CP: check_run webhook (apply complete)
    CP->>CP: Transition APPLYING → DEPLOYED
    CP->>Dev: Email "Deployed ✓ req-abc"

    Note over UI,CP: UI polls for status throughout
    UI->>CP: GET /api/requests/req-abc (every 10s)
    CP-->>UI: {state: "DEPLOYED"}
```

---

## Component Responsibilities

| Component | Owns | Does NOT own |
|---|---|---|
| **Chat Agent** | Conversation, intent parsing, Terraform generation, policy RAG, cost estimation, validation loop | Async workflow, GitHub, state persistence |
| **Control Plane** | Request lifecycle state machine, GitHub PR automation, CI monitoring, email notifications, audit trail | LLM inference, Terraform generation |
| **UI** | Developer chat interface, request history display | Business logic, Terraform, CI/CD |

---

## Multi-Cloud Abstraction

The Control Plane uses **Hexagonal Architecture** (Ports & Adapters). The domain and workflow layers have zero cloud-provider imports. All I/O goes through ports.

```mermaid
graph LR
    subgraph Domain["Domain Layer (cloud-agnostic)"]
        IS[InfraRequest]
        RS["RequestState (sealed)"]
        WF["WorkflowService (Phase 2)"]
    end

    subgraph Ports["Ports Layer (interfaces)"]
        P1[StateStorePort]
        P2[MessageQueuePort]
        P3[SecretStorePort]
        P4[EventBusPort]
        P5[NotificationPort]
        P6[ObjectStoragePort]
    end

    subgraph AWS["AWS Adapters (@Profile aws/local)"]
        A1[DynamoDB]
        A2[SQS]
        A3[Secrets Manager]
        A4[EventBridge]
        A5[SES v2]
        A6[S3]
    end

    subgraph Future["Future Adapters"]
        G1[Firestore]
        G2[Pub/Sub]
        G3[GCP Secret Manager]
        G4[Cloud Audit Logs]
        G5[SendGrid]
        G6[GCS]
    end

    WF --> P1
    WF --> P2
    WF --> P3
    WF --> P4
    WF --> P5
    WF --> P6
    P1 --> A1
    P1 --> G1
    P2 --> A2
    P2 --> G2
    P3 --> A3
    P3 --> G3
    P4 --> A4
    P4 --> G4
    P5 --> A5
    P5 --> G5
    P6 --> A6
    P6 --> G6
```

Adding GCP support = write `GcpAdapterConfig.java` with `@Profile("gcp")`. No domain code changes.

---

## Security Model

```mermaid
graph LR
    subgraph "Trust Boundaries"
        Dev["Developer Browser"]
        UI["Next.js UI"]
        Agent["Chat Agent"]
        CP["Control Plane"]
        GH["GitHub Actions"]
    end

    Dev -->|"GitHub OAuth → CP JWT\n(httpOnly cookie)"| UI
    UI -->|"Bearer JWT\n/api/**"| CP
    UI -->|"Bearer JWT\n/agent/chat"| Agent
    Agent -->|"X-Service-Key\n/internal/**"| CP
    GH -->|"HMAC-SHA256\n/api/webhooks/github"| CP
    GH -->|"OIDC → IAM Role\n(no long-lived credentials)"| AWS
```

- **No long-lived AWS credentials** anywhere — GitHub Actions uses OIDC → IAM roles
- **Plan vs apply scoping** — PR branches get read-only IAM roles; main branch gets apply permissions
- **JWT rotation** — update the Secrets Manager secret and redeploy; all old tokens rejected immediately
