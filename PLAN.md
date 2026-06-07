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

## Phase 1.5 — Simplification & Multi-Cloud Target Support ✅

> Goal: simplify the ports & adapters wiring; add `targetCloud` so the agent can generate Terraform for AWS, GCP, or Azure.

- [x] Deleted `AwsAdapterConfig.java` and `LocalAdapterConfig.java` — wiring config files are gone
- [x] `@Component @Profile({"aws","local"})` on all 6 AWS adapters (self-registering)
- [x] `@Component @Profile("test")` on all 6 local adapters; `InMemorySecretStoreAdapter` seeds JWT secret via `@PostConstruct`
- [x] `AwsClientConfig` simplified: 12 profile-split bean methods → 6 single beans controlled by `infraforge.aws.endpoint-override`
- [x] `InfraforgeProperties.Aws` — added `endpointOverride` field (`@DefaultValue("")`)
- [x] `application-local.yml` — added `endpoint-override: http://localhost:4566`
- [x] `domain/CloudProvider.java` — `enum CloudProvider { AWS, GCP, AZURE }` with `fromString()`
- [x] `domain/InfraRequest.java` — added `targetCloud: CloudProvider` field; updated `create()` and all transition helpers
- [x] `adapters/aws/DynamoDbInfraRequestEntity.java` — added `targetCloud` String field
- [x] `adapters/aws/DynamoDbStateStoreAdapter.java` — maps `targetCloud` in `toEntity()` / `toDomain()`
- [x] `api/dto/SubmitRequestDto.java` — added `@NotBlank String targetCloud`
- [x] `api/dto/InfraRequestDto.java` — added `String targetCloud`
- [x] `api/InternalController.java` — parses `CloudProvider.fromString(body.targetCloud())`
- [x] Tests updated (`InMemoryStateStoreAdapterTest`, `RequestControllerTest`) to pass `CloudProvider.AWS`
- [x] `agent/agent/graph/state.py` — added `target_cloud: str` to `AgentState`
- [x] `infra-modules/` created with provider-scoped structure:
  - `aws/` — ecs-service, rds-postgres, vpc, s3-bucket, api-gateway (stub `main.tf` + `variables.tf`)
  - `gcp/` — cloud-run, cloud-sql, vpc (stubs)
  - `azure/` — container-app, azure-sql (stubs)

---

## Phase 2 — Control Plane: Workflow Engine ✅

> Goal: SQS consumer driving the state machine, GitHub PR automation (stub), email notifications on state changes, audit events on every transition.

- [x] `WorkflowService` — SQS/in-memory consumer (`@PostConstruct`), dispatches all 5 event types
- [x] `RequestLifecycleOrchestrator` — handles `REQUEST_SUBMITTED` → stub PR creation → `PR_CREATED`
- [x] `GitHubPrPort` + `StubGitHubPrAdapter` — stub PR adapter (`@Profile aws,local,test`); real adapter in Phase 5
- [x] `CiMonitorService` — handles `PLAN_COMPLETED` / `APPLY_COMPLETED` / `APPLY_FAILED` webhook events
- [x] `AuditService` — publishes `AuditEvent` to EventBridge on every state transition (fire-and-forget)
- [x] `NotificationService` — sends SES email on `PR_CREATED`, `PLAN_APPROVED`, `DEPLOYED`, `FAILED`
- [x] `WorkflowServiceTest`, `RequestLifecycleOrchestratorTest`, `CiMonitorServiceTest`, `NotificationServiceTest`
- [ ] `ApprovalRouter` — auto-approve low-risk requests (deferred to Phase 5 — needs real cost data)
- [ ] Spring Statemachine configuration (deferred to Phase 5 — replace manual switch)
- [ ] Integration tests against LocalStack (deferred to Phase 6)

---

## Phase 3 — Chat Agent ✅

> Goal: Full LangGraph graph with all 8 nodes, stub LLM + adapters, tfsec/checkov wrappers, FastAPI endpoint.

- [x] `config.py` — `pydantic_settings.BaseSettings` with `USE_FAKE_LLM`, service key, CP URL
- [x] `llm.py` — LLM factory: `GenericFakeChatModel` (local) or `ChatBedrock` (Phase 5)
- [x] `graph.py` — full graph topology, conditional edges, `MemorySaver` checkpointer, `interrupt_before=["confirm"]`
- [x] `intake_node` — intent parsing, confidence scoring, clarification routing
- [x] `context_fetch_node` — concurrent `get_policies` + `get_budget` via asyncio.gather
- [x] `generate_node` — Terraform generation, HCL block parsing
- [x] `validate_node` — tfsec + checkov subprocesses + OPA via `/internal/validate`
- [x] `refine_node` — LLM-driven fix loop (max 3 attempts)
- [x] `cost_estimate_node` — stub returns 0.0 (Phase 5: real Cost Explorer)
- [x] `confirm_node` — human-in-the-loop summary, LangGraph interrupt
- [x] `submit_node` — `POST /internal/requests`, sets `request_id`
- [x] `StubPolicyStoreAdapter` — hardcoded policies + 4 approved modules (Phase 5: BedrockKbAdapter)
- [x] `control_plane.py` tool — async httpx client for all 5 internal endpoints
- [x] `terraform_lint.py` tool — tfsec + checkov subprocesses (graceful skip if not installed)
- [x] `cost_explorer.py` tool — stub 0.0 (Phase 5: boto3 Cost Explorer)
- [x] `api/main.py` — FastAPI `POST /chat` + `GET /health`, in-memory session management
- [x] Prompt templates: `intake_prompt.py`, `generate_prompt.py`, `refine_prompt.py`
- [x] Tests: `test_intake_node.py`, `test_submit_node.py`, `test_api.py` (5 tests, all pass)
- [ ] `BedrockKnowledgeBaseAdapter` — deferred to Phase 5
- [ ] `cost_explorer.py` real implementation — deferred to Phase 5

---

## Phase 4 — UI: Chat + History ✅

> Goal: Working developer portal — chat interface with live status cards, request history with timeline.

- [x] `providers.tsx` — TanStack Query `QueryClientProvider` + NextAuth `SessionProvider`
- [x] Root layout updated with `<Providers>` wrapper
- [x] `page.tsx` updated to redirect to `/chat/<uuid>` (bookmarkable sessions)
- [x] `chat/[sessionId]/page.tsx` — chat page with auto-scroll, empty state prompt
- [x] `ChatInput.tsx` — textarea, Enter submits, Shift+Enter newline, disabled while loading
- [x] `MessageBubble.tsx` — role-based alignment, `react-markdown` for assistant content
- [x] `StatusCard.tsx` — live state badge, PR link, cost via `useRequestStatus`
- [x] `useChatSession` hook — message list, loading state, `requestId` tracking
- [x] `useRequestStatus` hook — TanStack Query, 10s poll, stops at DEPLOYED/FAILED
- [x] `requests/page.tsx` — Server Component, fetches all requests
- [x] `RequestList.tsx` — colour-coded state badges, truncated intent, cost, date
- [x] `requests/[requestId]/page.tsx` — Server Component, request detail
- [x] `RequestTimeline.tsx` — vertical stepper, live polling when in-flight
- [ ] UI end-to-end test: login → chat → see status update (deferred to Phase 6)

---

## Phase 5 — Real External Dependencies ⬜

> Goal: Replace all stubs from Phases 2–4 with production implementations. No new features — existing flows just become real.

- [ ] `GitHubRestApiAdapter` — GitHub REST API: create branch, commit Terraform files from S3, open PR (`@Profile("aws")`)
- [ ] `BedrockKbAdapter` — Bedrock Knowledge Bases policy retrieval (replace `StubPolicyStoreAdapter`)
- [ ] `cost_explorer.py` real impl — boto3 `ce.get_cost_forecast` (replace stub 0.0)
- [ ] `ApprovalRouter` — real budget + risk scoring after cost data is available
- [ ] `InternalController.validateTerraform()` — real OPA eval at `http://opa:8181` (replace stub)
- [ ] `policies/rego/security_baseline.rego` — first real OPA policy
- [ ] tfsec + checkov in `agent/Dockerfile` — make validation mandatory, not optional
- [ ] Redis / DynamoDB session checkpointer — replace `MemorySaver()` in `graph.py`
- [ ] Spring Statemachine — replace manual switch in `WorkflowService`

---

## Phase 6 — Infrastructure & CI/CD ⬜

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
