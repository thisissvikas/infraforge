# infraforge — Implementation Plan

Checkbox-based tracker. Update as each item is completed.

---

## Phase 0 — Bootstrap ✅

> Goal: mono-repo skeleton, build tooling for all three stacks, local dev environment.

- [x] Mono-repo directory structure (`agent/`, `control-plane/`, `ui/`, `policies/`, `infra-modules/`, `docs/`)
- [x] `control-plane/` — Gradle 8.12 build (`build.gradle.kts`, `libs.versions.toml`, `gradle-wrapper.properties`)
- [x] `agent/` — Python 3.13 project (`pyproject.toml` with uv, ruff, mypy)
- [x] `ui/` — Next.js 15 setup (`package.json`, `tsconfig.json`, `next.config.ts`, Tailwind 4, Auth.js v5)
- [x] `docker-compose.yml` — LocalStack (DynamoDB, SQS, S3, SES, EventBridge, Secrets Manager) + OPA
- [x] `scripts/localstack-init.sh` — creates all AWS resources on LocalStack startup
- [x] `.pre-commit-config.yaml` — hooks for Java, Python, TypeScript, Terraform, OPA
- [x] `.gitignore` — covers all three stacks
- [x] `README.md` updated (Gradle, Java 25, `ui/` section, `policies/rego/` section)
- [x] `PLAN.md` — this file
- [x] `docs/architecture.md` — high-level architecture (Mermaid)
- [x] Per-component READMEs (`agent/README.md`, `control-plane/README.md`, `ui/README.md`)

---

## Phase 1 — Control Plane: Auth & API Skeleton ✅

> Goal: Spring Boot skeleton with hexagonal architecture, all port interfaces, AWS + local adapters, GitHub OAuth, JWT, stubbed REST API.

### Domain Model
- [x] `RequestState` — sealed interface with 7 nested records (`Submitted`, `PrCreated`, `PlanRunning`, `PlanApproved`, `Applying`, `Deployed`, `Failed`)
- [x] `InfraRequest` — immutable record with `create()` factory and transition helpers (`withState`, `withPr`, `asFailed`, etc.)
- [x] `WorkflowEvent` — SQS message envelope
- [x] `AuditEvent` — EventBridge event envelope
- [x] `NotificationMessage` — email notification record
- [x] `User` — JWT principal record

### Port Interfaces
- [x] `StateStorePort` (+ `transition()` default method)
- [x] `MessageQueuePort`
- [x] `SecretStorePort` (+ `SecretNotFoundException`)
- [x] `EventBusPort`
- [x] `NotificationPort`
- [x] `ObjectStoragePort` (+ `ObjectNotFoundException`)

### AWS Adapters (`@Profile("aws", "local")`)
- [x] `DynamoDbInfraRequestEntity` — DynamoDB Enhanced Client bean (PK: `requestId`, GSI: `userId-createdAt-index`)
- [x] `DynamoDbStateStoreAdapter` — full serialisation of sealed `RequestState` to/from JSON
- [x] `SqsMessageQueueAdapter` — long-poll consumer on virtual threads
- [x] `AwsSecretsAdapter` — 5-minute cache, JSON map support
- [x] `EventBridgeEventBusAdapter` — fire-and-forget, never propagates failures
- [x] `SesNotificationAdapter` — HTML + plain-text email
- [x] `S3ObjectStorageAdapter`

### Local / In-Memory Adapters (`@Profile("test")`)
- [x] `InMemoryStateStoreAdapter` — `ConcurrentHashMap`, sorted `findByUserId`
- [x] `InMemoryMessageQueueAdapter` — `LinkedBlockingQueue`, virtual thread consumer
- [x] `InMemorySecretStoreAdapter` — seeded from properties at startup
- [x] `NoOpEventBusAdapter` — logs to stdout
- [x] `LoggingNotificationAdapter` — logs to stdout
- [x] `InMemoryObjectStorageAdapter`

### Spring Configuration
- [x] `InfraforgeProperties` — typed `@ConfigurationProperties` record (JWT, service key, AWS, GitHub)
- [x] `AwsClientConfig` — profile-split AWS SDK clients (aws → real, local → LocalStack)
- [x] `AwsAdapterConfig` — wires AWS adapters to ports
- [x] `LocalAdapterConfig` — wires in-memory adapters to ports
- [x] `SecurityConfig` — 3 ordered filter chains (internal/api/auth), CORS

### Auth Layer
- [x] `JwtService` — HS256 issue + validate (upgrade path to RS256 documented)
- [x] `JwtAuthenticationFilter` — `Authorization: Bearer` → `SecurityContext`
- [x] `ServiceKeyAuthenticationFilter` — `X-Service-Key` → `SecurityContext`
- [x] `AuthenticatedUser` — Spring Security `Authentication` wrapping `User` record
- [x] `OAuth2SuccessHandler` — issues CP JWT as httpOnly cookie after GitHub OAuth
- [x] `AuthController` — `POST /auth/token` for Auth.js JWT callback
- [x] `GitHubUserResolver` — validates GitHub token via `/user` API, returns `User`

### API Controllers & DTOs
- [x] `RequestController` — `GET /api/requests`, `GET /api/requests/{id}`
- [x] `UserController` — `GET /api/me`
- [x] `WebhookController` — `POST /api/webhooks/github` (HMAC-SHA256 signature verification)
- [x] `InternalController` — all 5 agent tool endpoints (policies + budget + validate stubs, submit + status real)
- [x] `InfraRequestDto`, `SubmitRequestDto`, `GitHubWebhookPayload` DTOs

### Configuration Files
- [x] `application.yml` — base config (virtual threads, JWT, GitHub OAuth, management endpoints)
- [x] `application-aws.yml` — AWS resource names from env vars
- [x] `application-local.yml` — LocalStack URLs + dev defaults
- [x] `application-test.yml` — in-memory config, no external dependencies

### Tests
- [x] `InfraforgeApplicationTests` — Spring context loads with test profile
- [x] `InMemoryStateStoreAdapterTest` — 5 unit tests (save, find, sort, transition, duplicate)
- [x] `JwtServiceTest` — round-trip, tamper detection, garbage input
- [x] `RequestControllerTest` — auth boundary tests (JWT required, user isolation)

---

## Phase 2 — Control Plane: Workflow Engine ⬜

> Goal: SQS consumer driving the state machine, GitHub PR automation, email notifications on state changes, audit events on every transition.

- [ ] `WorkflowService` — SQS consumer subscribing at startup, dispatching to handlers
- [ ] `RequestLifecycleOrchestrator` — handles `REQUEST_SUBMITTED` → creates GitHub PR
- [ ] `GitHubPrService` — create branch, commit Terraform files from S3, open PR
- [ ] `CiMonitorService` — handles `PLAN_COMPLETED` / `APPLY_COMPLETED` / `APPLY_FAILED` webhook events
- [ ] `ApprovalRouter` — auto-approve low-risk requests, queue high-risk for platform review
- [ ] `AuditService` — publishes `AuditEvent` to EventBridge on every state transition
- [ ] `NotificationService` — sends SES email on `PR_CREATED`, `PLAN_APPROVED`, `DEPLOYED`, `FAILED`
- [ ] Spring Statemachine configuration (replace manual switch in workflow handlers)
- [ ] Integration tests against LocalStack (full SUBMITTED → DEPLOYED flow)

---

## Phase 3 — Chat Agent ⬜

> Goal: Full LangGraph graph with all 8 nodes, Bedrock RAG, tfsec/checkov, OPA, Cost Explorer, FastAPI endpoint.

- [ ] `graph.py` — graph topology, conditional edges, retry limits
- [ ] `intake_node` — intent parsing, confidence scoring, clarification question generation
- [ ] `context_fetch_node` — Bedrock KB retrieval, `GET /internal/policies`, `GET /internal/budget`
- [ ] `generate_node` — Terraform assembly from approved modules, LLM grounded in policy context
- [ ] `validate_node` — tfsec subprocess, checkov subprocess, `POST /internal/validate` (OPA)
- [ ] `refine_node` — LLM-driven fix loop (max 3 attempts)
- [ ] `cost_estimate_node` — Cost Explorer API, budget comparison
- [ ] `confirm_node` — human-in-the-loop, waits for developer approval
- [ ] `submit_node` — `POST /internal/requests`, returns `requestId`
- [ ] `BedrockKnowledgeBaseAdapter` — implements `PolicyStorePort`
- [ ] `control_plane.py` tool — async httpx client for all 5 internal endpoints
- [ ] `terraform_lint.py` tool — tfsec + checkov subprocess wrappers
- [ ] `cost_explorer.py` tool — boto3 Cost Explorer client
- [ ] `api/main.py` — FastAPI app with `POST /chat`, session management
- [ ] Prompt templates for each node (`prompts/`)
- [ ] Unit tests for each node (mock LLM + tool calls)
- [ ] Integration test: full conversation → submit → `requestId` returned

---

## Phase 4 — UI: Chat + History ⬜

> Goal: Working developer portal — chat interface with live status cards, request history with timeline.

- [ ] `providers.tsx` — TanStack Query `QueryClientProvider`, session provider
- [ ] Root layout updated with providers + font
- [ ] `chat/[sessionId]/page.tsx` — chat page shell, session ID from URL
- [ ] `ChatInput.tsx` — textarea with submit on Enter, loading state
- [ ] `MessageBubble.tsx` — user + assistant bubbles, markdown rendering (react-markdown)
- [ ] `StatusCard.tsx` — embedded request status in chat (state badge, PR link, cost)
- [ ] `useChatSession` hook — manages message list, calls `sendChatMessage()`, handles `requestId`
- [ ] `useRequestStatus` hook — TanStack Query poll (10s interval, stops when DEPLOYED/FAILED)
- [ ] `requests/page.tsx` — request history list (Server Component)
- [ ] `RequestList.tsx` — table with state badge, intent summary, cost, timestamps
- [ ] `requests/[requestId]/page.tsx` — request detail page
- [ ] `RequestTimeline.tsx` — state machine history with timestamps + links
- [ ] UI end-to-end test: login → chat → see status update

---

## Phase 5 — Infrastructure & CI/CD ⬜

> Goal: infraforge's own AWS infrastructure as Terraform, GitHub Actions pipelines for infra PRs, OIDC setup, OPA policy enforcement.

### Terraform (infraforge's own infra)
- [ ] `infra/main.tf` — ECS cluster, VPC, ALB for control-plane + agent
- [ ] `infra/ecs.tf` — ECS services + task definitions
- [ ] `infra/dynamodb.tf` — infraforge-requests table + GSI
- [ ] `infra/sqs.tf` — workflow queue
- [ ] `infra/s3.tf` — terraform artefacts bucket + CloudFront for UI
- [ ] `infra/iam.tf` — GitHub OIDC provider, plan + apply IAM roles
- [ ] `infra/secrets.tf` — Secrets Manager secrets skeleton
- [ ] `infra/state.tf` — remote state (S3 + DynamoDB lock)

### GitHub Actions
- [ ] `.github/workflows/pr-plan.yml` — `terraform fmt`, `validate`, `tfsec`, `checkov`, `opa eval`, `terraform plan` (PR comment), `infracost diff` (PR comment)
- [ ] `.github/workflows/main-apply.yml` — `terraform apply` + webhook to Control Plane
- [ ] OIDC trust policy (plan-only role for PR branches, apply role for main)

### Terraform Modules (`infra-modules/`)
- [ ] `ecs-service/` — ECS Fargate service with ALB, security groups, CloudWatch logs
- [ ] `rds-postgres/` — RDS PostgreSQL, multi-AZ option, encrypted, parameter groups
- [ ] `vpc/` — VPC, public/private subnets, NAT gateway, VPC flow logs
- [ ] `s3-bucket/` — S3 with encryption, versioning, lifecycle, public-access-block
- [ ] `api-gateway/` — API Gateway HTTP API with Lambda integration option

### OPA Policies (`policies/rego/`)
- [ ] `security_baseline.rego` — no public IPs in private VPCs, encryption required, tagging required
- [ ] `networking.rego` — CIDR range validation, subnet rules by environment
- [ ] `cost_controls.rego` — instance type allowlists, multi-AZ rules per environment

### Policy Docs (`policies/docs/`)
- [ ] `security-baseline.md` — Bedrock KB source for agent RAG
- [ ] `networking-standards.md`
- [ ] `tagging-requirements.md`

---

## Phase 6 — Integration & Hardening ⬜

> Goal: end-to-end tested, observable, production-ready.

- [ ] End-to-end test: developer sends request → agent generates Terraform → PR created → mock CI passes → DEPLOYED state → email sent
- [ ] CloudWatch dashboards (request state counts, latency, error rates)
- [ ] X-Ray tracing in Control Plane (Spring Boot + AWS X-Ray SDK)
- [ ] Structured logging (JSON format) across all three services
- [ ] Load test: Control Plane SQS consumer under 50 concurrent requests
- [ ] Security review: OWASP top 10, no long-lived credentials, secret rotation
- [ ] Rate limiting on `/api/**` and `/internal/**`
- [ ] Helm chart or ECS task definitions for production deployment
- [ ] Runbook: common incidents (stuck state machine, SQS DLQ, Bedrock throttle)

---

## Roadmap Items (not yet phased)

- [ ] Slack interface (Chat Agent + Slack Bolt)
- [ ] Multi-environment promotion (dev → staging → prod with approval gates)
- [ ] Drift detection (periodic reconciliation of live state vs. submitted intent)
- [ ] Human approval workflow UI for high-risk / over-budget requests (platform team panel)
- [ ] Request cancellation API
- [ ] Terraform module contribution guide (`docs/adding-modules.md`)
- [ ] Policy authoring guide (`docs/adding-policies.md`)
