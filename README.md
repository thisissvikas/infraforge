# 🔥 infraforge

> **Talk to your infrastructure. Ship it in minutes.**

infraforge is an AI-powered Internal Developer Platform (IDP) that eliminates the infrastructure bottleneck in modern engineering organisations. Developers describe what they need in a natural conversation. An AI agent — grounded in your organisation's security policies and cost constraints — gathers intent, resolves ambiguity, explains constraints, and submits production-grade Terraform to a fully auditable GitHub CI/CD workflow. The long-running deployment lifecycle is then owned by an async Control Plane that keeps working whether the developer's session is open or not.

No Terraform expertise required. No support tickets. No waiting.

---

## The Problem

Platform engineering teams face a structural tension:

- **The Expertise Gap** — Application developers are experts at writing code, not cloud architecture. Asking them to write production-grade Terraform leads to misconfigurations, security vulnerabilities (publicly exposed S3 buckets, open security groups, missing encryption), and deployment delays.
- **The Template Trap** — Static, pre-approved IDP templates cannot account for every unique architectural permutation an application needs. Teams either bypass them or submit manually processed support tickets, grinding engineering velocity to a halt.

The result is a bottleneck: the platform team becomes a gatekeeper rather than an enabler.

---

## The Solution

infraforge shifts the model. Platform engineers encode their expertise **once** — as policy documents, approved Terraform modules, and compliance rules. The AI applies that expertise **every time**, for every team, through a natural conversation.

```
Developer:  "I need a payments service on ECS with PostgreSQL and an ALB"

Agent:      "Got it. A couple of quick questions — are you expecting
             this to handle PCI data? And is this for prod or staging?"

Developer:  "Yes PCI, staging first but needs to be prod-ready architecture"

Agent:      "In that case I'll apply PCI-DSS network isolation rules even
             on staging — that means no public subnets, everything routed
             through the internal ALB. Checking your team's budget..."

             [retrieves payments team policy + budget]

Agent:      "You have $800/month headroom. This architecture comes in at
             ~$340/month. Security scan passed. Want me to submit the PR?"

Developer:  "Yes, go ahead"

Agent:      "Submitted. I'll notify you when the PR is ready — usually
             a few minutes."

             ... [async CI/CD pipeline runs] ...

Agent:      "Your PR is ready: github.com/your-org/infra/pull/482
             Plan: $340/month | Security: PASSED | Ready to merge."
```

---

## Architecture

infraforge is built on a **chat-first, hybrid model**. The two phases of infrastructure provisioning operate on fundamentally different timescales and are handled by different parts of the system.

| Phase | Timescale | Owner |
|---|---|---|
| Intent gathering, clarification, policy explanation, generation | Seconds | Chat Agent (LangGraph) |
| PR creation, CI/CD, approval workflows, apply, deploy | Minutes to hours | Control Plane (Spring Boot) |

The Chat Agent is the developer's front door. The Control Plane is the back-office workflow engine that keeps running regardless of whether the developer's session is open.

---

### High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                     Developer Interface                           │
│              (Web Chat  /  Slack  /  CLI)                         │
└──────────────────────────┬───────────────────────────────────────┘
                           │  Natural language conversation
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Chat Agent (LangGraph)                         │
│                                                                   │
│   Conversational loop — the developer's interface                 │
│                                                                   │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│   │ intake &     │  │  policy &    │  │   generation &       │  │
│   │ clarify      │  │  RAG         │  │   validation loop    │  │
│   │              │  │  retrieval   │  │                      │  │
│   │ Gathers      │  │              │  │  Generates Terraform │  │
│   │ intent via   │  │  Fetches org │  │  using approved      │  │
│   │ conversation │  │  policies,   │  │  modules. Validates, │  │
│   │              │  │  budgets,    │  │  refines, estimates  │  │
│   │ Asks only    │  │  VPC ranges, │  │  cost. Explains      │  │
│   │ what it      │  │  approved    │  │  violations and      │  │
│   │ doesn't know │  │  patterns    │  │  offers alternatives │  │
│   └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                                                                   │
│   Tool calls (agent ──► Control Plane):                          │
│   get_team_policies()  check_budget()  validate_architecture()   │
│   submit_request()     get_request_status()                      │
│                                                                   │
│   Webhook (Control Plane ──► Agent):                             │
│   "PR ready"  "Approval needed"  "Deploy failed"                 │
└──────────────────────────┬───────────────────────────────────────┘
                           │  submit_request() tool call
                           │  (agent hands off; conversation continues)
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                  Control Plane (Spring Boot)                      │
│                                                                   │
│   Async workflow engine — runs independently of chat session      │
│                                                                   │
│   ┌─────────────────────────────────────────────────────────┐   │
│   │  State Machine                                           │   │
│   │  SUBMITTED → PR_CREATED → PLAN_RUNNING → PLAN_APPROVED  │   │
│   │  → APPLYING → DEPLOYED / FAILED                         │   │
│   └─────────────────────────────────────────────────────────┘   │
│                                                                   │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│   │  GitHub PR   │  │  Approval    │  │  Audit & Events      │  │
│   │  Service     │  │  Router      │  │                      │  │
│   │              │  │              │  │  Every state         │  │
│   │  Creates     │  │  Low risk →  │  │  transition          │  │
│   │  branch,     │  │  auto-approve│  │  persisted to        │  │
│   │  commits     │  │              │  │  DynamoDB.           │  │
│   │  Terraform,  │  │  High risk → │  │  Published to        │  │
│   │  opens PR,   │  │  platform    │  │  EventBridge for     │  │
│   │  monitors CI │  │  team review │  │  compliance          │  │
│   └──────────────┘  └──────────────┘  └──────────────────────┘  │
│                                                                   │
│   Webhook back to agent on every state change                     │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                   GitHub + CI/CD Pipeline                         │
│                                                                   │
│   Pull Request                       Merge to main               │
│   ───────────                        ─────────────               │
│   terraform fmt + validate           terraform apply             │
│   tfsec / checkov                    → webhook to Control Plane  │
│   terraform plan (→ PR comment)      → DynamoDB: DEPLOYED        │
│   infracost estimate (→ PR comment)  → agent notifies developer  │
│                                                                   │
│   OIDC → IAM Role (no long-lived credentials ever)               │
│   PR branches: plan-only permissions                              │
│   main branch: apply permissions                                  │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│                    AWS Target Environment                         │
│          ECS  •  RDS  •  S3  •  VPC  •  ALB  •  IAM  •  ...     │
└──────────────────────────────────────────────────────────────────┘
```

---

### Chat Agent — LangGraph Graph Detail

The agent is a **stateful, multi-node graph** with conditional routing. The conversation is the clarification loop.

```
START
  │
  ▼
[intake_node]
  Parses initial request. Scores confidence on each intent dimension.
  Underspecified → asks conversationally (not a form, just a question).
  Fully specified → proceeds directly.
  │
  ▼
[context_fetch_node]
  Decides which policies to retrieve based on parsed intent.
  "payments team + PostgreSQL + prod" →
    fetches PCI-DSS rules, RDS encryption requirements,
    multi-AZ policy, payments VPC CIDRs, approved instance classes.
  │
  ▼
[generate_node]
  LLM composes Terraform by assembling platform-approved modules.
  Grounded in retrieved policies. Does not invent net-new resources.
  │
  ▼
[validate_node]
  Deterministic: tfsec, checkov, HCL AST parsing.
  LLM pass: subtle architectural issues, policy drift.
  │
  ├── issues found ──► [refine_node] ──► back to [validate_node]
  │                     Fixes failures using full accumulated context.
  │                     Max 3 retries.
  │                     Unresolvable → explains to developer in chat,
  │                     offers compliant alternatives.
  │
  └── clean ──────────► [cost_estimate_node]
                          Queries AWS Cost Explorer.
                          Compares against team budget ceiling.
                          │
                          ├── over budget ──► explains in chat,
                          │                   offers cheaper alternatives,
                          │                   routes to platform approval
                          │                   if developer wants to proceed
                          │
                          └── within budget ──► [confirm_node]
                                                  Summarises plan in chat.
                                                  Waits for "go ahead".
                                                      │
                                                      ▼
                                                [submit_node]
                                                  Calls submit_request()
                                                  on Control Plane.
                                                  Returns request ID.
                                                  Chat shifts to
                                                  status / notification mode.
```

---

### How Policy Violations Work

When a request violates policy and the refine loop cannot resolve it — because it is an architectural constraint, not a code issue — the agent explains this in the conversation and offers alternatives directly:

```
Developer:  "I need an EC2 instance with a public IP in the payments VPC"

Agent:      "I can't provision that. The payments VPC has a hard rule
             against resources with public IPs (NET-PAYMENTS-001),
             enforced because of PCI-DSS network isolation requirements.

             Two compliant alternatives that achieve the same thing:

             1. Private EC2 behind an internal ALB  (+$18/month)
                Runs in a private subnet, reachable only via the ALB.

             2. ECS Fargate  (-$12/month vs your original request)
                ★ Recommended — already the approved pattern for the
                payments team. No public IP concern at all.

             Want to go with one of these, or talk through the
             requirements a bit more?"
```

No tickets. No forms. No waiting for a human to explain the rule.

---

### Why the Control Plane Still Exists

The Control Plane is not the orchestrator in this model — it is the **async workflow engine**. It exists because infrastructure provisioning spans timescales that no chat session can hold open.

```
Developer submits and closes the laptop.

  ├── terraform plan runs        (~3 minutes)
  ├── platform team reviews      (could be hours)
  ├── terraform apply runs       (~8 minutes)
  └── post-deploy health checks  (~2 minutes)
```

The Control Plane owns all of this. It persists every state transition to DynamoDB. When something needs the developer's attention — PR ready, approval needed, deploy failed — it fires a webhook back to the agent, which resurfaces in the developer's original channel.

---

## Tech Stack

### Chat Agent
| Component | Technology |
|---|---|
| Agent Framework | LangGraph (Python) |
| LLM Inference | AWS Bedrock (Claude) |
| Policy RAG | AWS Bedrock Knowledge Bases + S3 |
| Security Validation | tfsec, checkov (tool calls) |
| Cost Estimation | AWS Cost Explorer API |
| Hosting | AWS ECS Fargate |

### Control Plane
| Component | Technology |
|---|---|
| Language & Framework | Java 21 / Spring Boot 3 |
| Workflow State Machine | Spring Statemachine |
| Persistence | AWS DynamoDB |
| GitHub Integration | GitHub REST API |
| Async Queue | AWS SQS |
| Config & Secrets | AWS SSM Parameter Store / Secrets Manager |
| Observability | AWS CloudWatch + X-Ray |
| Hosting | AWS ECS Fargate |

### CI/CD & Deployment
| Component | Technology |
|---|---|
| Source Control | GitHub |
| Pipelines | GitHub Actions |
| AWS Authentication | GitHub OIDC → IAM Role (no long-lived credentials) |
| IaC Language | Terraform (HCL) |
| Terraform State | AWS S3 + DynamoDB (state locking) |
| Plan-time Policy | OPA / Sentinel |
| Cost Delta | Infracost |

### AWS Infrastructure
| Concern | Service |
|---|---|
| LLM inference | Amazon Bedrock |
| Policy knowledge base | Bedrock Knowledge Bases + S3 |
| Workflow state & audit | DynamoDB |
| Terraform remote state | S3 + DynamoDB |
| Async decoupling | SQS |
| Secrets | Secrets Manager |
| OIDC federation | IAM Identity Provider |
| Observability | CloudWatch + X-Ray |
| Hosting | ECS Fargate |

---

## Repository Structure

```
infraforge/
├── agent/                        # LangGraph chat agent (Python)
│   ├── graph/
│   │   ├── nodes/                # One file per graph node
│   │   ├── state.py              # Shared agent state definition
│   │   └── graph.py              # Graph topology & conditional edges
│   ├── tools/                    # Control Plane, tfsec, cost API clients
│   ├── prompts/                  # Node-level prompt templates
│   └── requirements.txt
│
├── control-plane/                # Spring Boot async workflow engine
│   ├── src/main/java/
│   │   └── io/infraforge/
│   │       ├── api/              # REST controllers + agent webhook receiver
│   │       ├── workflow/         # State machine + orchestration
│   │       ├── github/           # PR service
│   │       ├── aws/              # DynamoDB, SQS, SSM clients
│   │       └── audit/            # Event publishing
│   └── pom.xml
│
├── infra-modules/                # Platform-approved Terraform modules
│   ├── ecs-service/
│   ├── rds-postgres/
│   ├── api-gateway/
│   ├── s3-bucket/
│   └── vpc/
│
├── policies/                     # Org policy documents (RAG knowledge base)
│   ├── security-baseline.md
│   ├── networking-standards.md
│   ├── tagging-requirements.md
│   └── approved-regions.md
│
├── .github/
│   └── workflows/
│       ├── pr-plan.yml           # Plan + security scan on pull request
│       └── main-apply.yml        # Apply on merge to main
│
└── docs/
    ├── architecture.md
    ├── adding-modules.md
    └── adding-policies.md
```

---

## Platform Team Experience

Platform engineers shift from **doing** to **governing**:

- Maintain the **module library** — trusted, reusable Terraform building blocks the agent composes from
- Maintain the **policy knowledge base** — rules the agent retrieves and applies in every conversation
- Review **escalated approvals** — high-risk changes with full AI-generated context and cost impact
- Tune **risk tier thresholds** — what auto-approves vs. what requires human sign-off
- Analyse **audit logs** — every request, every decision, every deployment persisted in DynamoDB

---

## Roadmap

- [ ] Core LangGraph chat agent with conversational clarification loop
- [ ] Policy RAG (Bedrock Knowledge Base) with context-aware retrieval
- [ ] Terraform generation using approved module library
- [ ] Validate → refine → validate loop with tfsec + checkov
- [ ] Cost estimation node (Cost Explorer API + Infracost)
- [ ] Spring Boot Control Plane with DynamoDB state machine
- [ ] GitHub PR automation (branch, commit, open PR, monitor CI)
- [ ] OIDC trust setup (GitHub → AWS, plan vs. apply permission scoping)
- [ ] Control Plane → Agent webhook (PR ready, approval needed, deploy failed)
- [ ] Slack interface for developer chat
- [ ] Web portal (chat UI + request history dashboard)
- [ ] Human approval workflow for high-risk / over-budget requests
- [ ] Multi-environment promotion (dev → staging → prod)
- [ ] Drift detection (periodic reconciliation of live state vs. submitted intent)

---

## Contributing

infraforge is being built as an open exploration of AI-powered platform engineering. Contributions, ideas, and war stories from platform teams are very welcome.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/your-idea`)
3. Commit your changes
4. Open a PR — with a description of the problem you're solving

---
