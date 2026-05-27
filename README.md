# 🔥 infraforge

> **Turn developer intent into production-grade AWS infrastructure — automatically.**

infraforge is an AI-powered Internal Developer Platform (IDP) that eliminates the infrastructure bottleneck in modern engineering organisations. Developers describe what they need in plain language. An agentic AI pipeline — grounded in your organisation's security policies and cost constraints — generates, validates, and deploys production-grade Terraform via a fully auditable GitHub CI/CD workflow.

No Terraform expertise required. No support tickets. No waiting.

---

## The Problem

Platform engineering teams face a structural tension:

- **The Expertise Gap** — Application developers are experts at writing code, not cloud architecture. Asking them to write production-grade Terraform leads to misconfigurations, security vulnerabilities (publicly exposed S3 buckets, open security groups, missing encryption), and deployment delays.
- **The Template Trap** — Static, pre-approved IDP templates cannot account for every unique architectural permutation an application needs. Teams either bypass them or submit manually processed support tickets, grinding engineering velocity to a halt.

The result is a bottleneck: the platform team becomes a gatekeeper rather than an enabler.

---

## The Solution

infraforge shifts the model. Platform engineers encode their expertise **once** — as policy documents, approved Terraform modules, and compliance rules. The AI applies that expertise **every time**, for every team, at zero marginal cost.

```
Developer:  "I need a Java Spring Boot service on ECS, connected to a
             PostgreSQL RDS instance, behind an ALB, in the payments
             team's VPC. Expected load: ~500 req/s peak."

infraforge: ✔ Parsed intent & retrieved org policies
            ✔ Generated Terraform using approved modules
            ✔ Security scan: PASSED
            ✔ Estimated cost: $340/month (within team budget)
            ✔ PR opened — ready to merge and deploy
```

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Developer Interface                       │
│              (Web Portal  /  CLI  /  Slack Bot)                  │
└───────────────────────────┬─────────────────────────────────────┘
                            │  Submit intent (natural language)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                     Control Plane                                │
│                  Spring Boot / Java                              │
│                                                                  │
│   ┌──────────────┐   ┌─────────────────┐   ┌────────────────┐  │
│   │  REST API    │   │ Workflow Engine  │   │  GitHub PR     │  │
│   │  /requests   │   │ (State Machine)  │   │  Service       │  │
│   │  /status     │   │                 │   │                │  │
│   │  /approve    │   │ SUBMITTED        │   │  Create branch │  │
│   └──────────────┘   │ → ANALYZING      │   │  Commit files  │  │
│                      │ → GENERATING     │   │  Open PR       │  │
│   ┌──────────────┐   │ → VALIDATING     │   │  Monitor CI    │  │
│   │  Audit &     │   │ → [APPROVING]    │   └────────────────┘  │
│   │  Events      │   │ → PR_CREATED     │                       │
│   │  Service     │   │ → DEPLOYED       │   ┌────────────────┐  │
│   └──────────────┘   └─────────────────┘   │  AWS / Policy  │  │
│                                             │  Store Client  │  │
│                                             └────────────────┘  │
└───────────────────────────┬─────────────────────────────────────┘
                            │  Invoke agent (HTTP)
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AI Agent Layer                              │
│                    LangGraph (Python)                            │
│                                                                  │
│  [intake] → [context_fetch] → [generate] → [validate]           │
│                                                ↓        ↓       │
│                                           [refine]  [cost_est]  │
│                                              ↑           ↓      │
│                                         (retry)   [approval?]   │
│                                                        ↓        │
│                                               [package_output]  │
│                                                                  │
│  • Bedrock (Claude) for LLM inference                           │
│  • Bedrock Knowledge Base (RAG) for org policies                 │
│  • tfsec / checkov for deterministic security validation         │
│  • AWS Cost Explorer API for cost estimation                     │
└───────────────────────────┬─────────────────────────────────────┘
                            │  Generated Terraform committed to PR
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GitHub + CI/CD Pipeline                       │
│                                                                  │
│   Pull Request                      Merge to main               │
│   ───────────                       ─────────────               │
│   terraform fmt                     terraform apply             │
│   terraform validate                → post webhook to           │
│   tfsec / checkov (2nd pass)          Control Plane             │
│   terraform plan (posted to PR)     → DynamoDB state: DEPLOYED  │
│   infracost estimate (posted to PR) → notify developer          │
│                                                                  │
│   OIDC → IAM Role (no long-lived credentials)                   │
│   PR branches: plan-only permissions                             │
│   main branch: apply permissions                                 │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                      AWS Target Environment                      │
│         ECS  •  RDS  •  S3  •  VPC  •  ALB  •  IAM  •  ...     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Agent Graph Detail

The LangGraph agent is a **stateful, multi-node graph** with conditional routing — not a simple prompt-response chain.

| Node | Role |
|---|---|
| `intake_node` | Parses natural language into structured intent (app type, scaling, data sensitivity, networking) |
| `context_fetch_node` | RAG over policy knowledge base — retrieves security baselines, VPC conventions, tagging standards, team budgets |
| `generate_node` | LLM generates Terraform by composing platform-approved modules, grounded in retrieved policies |
| `validate_node` | Deterministic checks (tfsec, checkov, HCL AST) + LLM review pass for subtle issues |
| `refine_node` | Iterates on validation failures (max 3 retries before human escalation) |
| `cost_estimate_node` | Queries AWS Cost Explorer API; compares against team's configured budget ceiling |
| `human_approval_node` | Routes high-risk or over-budget requests to platform team with AI-generated justification |
| `package_output_node` | Assembles final Terraform files, PR description, cost summary, and runbook |

**Tiered auto-approval:** Low-risk requests (ECS task, no sensitive data, within budget) auto-approve. High-risk (VPC peering, cross-account IAM, large multi-region deployments) escalate to a human reviewer with full AI-generated context.

---

## Tech Stack

### Control Plane
| Component | Technology |
|---|---|
| Language & Framework | Java 21 / Spring Boot 3 |
| Workflow State Machine | Spring Statemachine |
| Persistence | AWS DynamoDB |
| Configuration & Secrets | AWS SSM Parameter Store / Secrets Manager |
| GitHub Integration | GitHub REST API (via Octokit / raw HTTP) |
| Async Job Queue | AWS SQS |
| Observability | AWS CloudWatch + X-Ray |
| Hosting | AWS ECS Fargate |

### AI Agent Layer
| Component | Technology |
|---|---|
| Agent Framework | LangGraph (Python) |
| LLM Inference | AWS Bedrock (Claude) |
| Policy RAG | AWS Bedrock Knowledge Base + S3 |
| Security Validation | tfsec, checkov (subprocess) |
| Cost Estimation | AWS Cost Explorer API |
| Hosting | AWS ECS Fargate / AWS Lambda |

### CI/CD & Deployment
| Component | Technology |
|---|---|
| Source Control | GitHub |
| CI/CD Pipelines | GitHub Actions |
| AWS Authentication | GitHub OIDC → IAM Role (no long-lived credentials) |
| IaC Language | Terraform (HCL) |
| Terraform State | AWS S3 + DynamoDB (state locking) |
| Plan-time Policy | OPA / Sentinel |
| Cost Delta | Infracost |

### AWS Infrastructure
| Concern | Service |
|---|---|
| LLM inference | Amazon Bedrock |
| Knowledge base / RAG | Bedrock Knowledge Bases + S3 |
| Request state & audit log | DynamoDB |
| Terraform remote state | S3 + DynamoDB |
| Async decoupling | SQS |
| Secrets | Secrets Manager |
| Config & budgets | SSM Parameter Store |
| OIDC Federation | IAM Identity Provider |
| Observability | CloudWatch + X-Ray |
| Hosting (control plane & agent) | ECS Fargate |

---

## Repository Structure

```
infraforge/
├── control-plane/               # Spring Boot orchestrator
│   ├── src/main/java/
│   │   └── io/infraforge/
│   │       ├── api/             # REST controllers
│   │       ├── workflow/        # State machine + orchestration
│   │       ├── github/          # GitHub PR service
│   │       ├── aws/             # DynamoDB, SQS, SSM clients
│   │       └── audit/           # Event publishing
│   └── pom.xml
│
├── agent/                       # LangGraph AI agent (Python)
│   ├── graph/
│   │   ├── nodes/               # One file per graph node
│   │   ├── state.py             # Shared agent state definition
│   │   └── graph.py             # Graph topology & edge conditions
│   ├── tools/                   # tfsec, checkov, cost API wrappers
│   ├── prompts/                 # Node-level prompt templates
│   └── requirements.txt
│
├── infra-modules/               # Platform-approved Terraform modules
│   ├── ecs-service/
│   ├── rds-postgres/
│   ├── api-gateway/
│   ├── s3-bucket/
│   └── vpc/
│
├── policies/                    # Org policy documents (RAG source)
│   ├── security-baseline.md
│   ├── networking-standards.md
│   ├── tagging-requirements.md
│   └── approved-regions.md
│
├── .github/
│   └── workflows/
│       ├── pr-plan.yml          # Plan + scan on pull request
│       └── main-apply.yml       # Apply on merge to main
│
└── docs/
    ├── architecture.md
    ├── adding-policies.md
    └── adding-modules.md
```

---

## Developer Experience

```bash
# Option 1: CLI
infraforge request \
  --description "Java Spring Boot service on ECS, PostgreSQL RDS, ALB, payments VPC" \
  --team payments \
  --env staging

# Option 2: REST API
curl -X POST https://infraforge.internal/requests \
  -H "Content-Type: application/json" \
  -d '{
    "description": "Java Spring Boot service on ECS, PostgreSQL RDS, ALB, payments VPC",
    "team": "payments",
    "environment": "staging"
  }'

# Response
{
  "requestId": "req_8f3a2c1d",
  "status": "ANALYZING",
  "trackingUrl": "https://infraforge.internal/requests/req_8f3a2c1d"
}
```

Within a few minutes, the developer receives a GitHub PR containing:

- ✅ Generated Terraform using approved modules
- ✅ `terraform plan` output posted as a PR comment
- ✅ Security scan results (tfsec + checkov)
- ✅ Estimated monthly cost (via Infracost)
- ✅ Auto-generated runbook and architecture notes

Merge the PR. Infrastructure deploys automatically.

---

## Platform Team Experience

Platform engineers shift from **doing** to **governing**:

- Maintain the **module library** — the trusted, reusable Terraform building blocks
- Maintain the **policy knowledge base** — the rules the AI retrieves and applies
- Review **escalated requests** — high-risk changes with full AI-generated context
- Tune **risk tier thresholds** — what auto-approves vs. what requires human sign-off
- Analyse **audit logs** — spot patterns, improve policies, track compliance

---

## Roadmap

- [ ] Core LangGraph agent with generate → validate → refine loop
- [ ] Spring Boot control plane with DynamoDB state machine
- [ ] GitHub PR automation (create, commit, monitor CI)
- [ ] OIDC trust setup (GitHub → AWS, plan vs. apply scoping)
- [ ] Bedrock Knowledge Base integration (RAG over policy docs)
- [ ] Cost estimation node (Cost Explorer API + Infracost)
- [ ] Human approval workflow with Slack notifications
- [ ] Web portal (developer submission + request tracking UI)
- [ ] Multi-environment promotion (dev → staging → prod)
- [ ] Drift detection (periodic reconciliation of live state vs. intent)

---

## Contributing

infraforge is being built as an open exploration of AI-powered platform engineering. Contributions, ideas, and war stories from platform teams are very welcome.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/your-idea`)
3. Commit your changes
4. Open a PR — with a description of the problem you're solving

---

## License

MIT
