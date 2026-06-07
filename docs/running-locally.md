# Running infraforge Locally

Two options depending on how much you want to run:

| Mode | What runs | Use when |
|---|---|---|
| **Dev** | Each service in your terminal | Active development on a component |
| **Full stack** | Everything in Docker Compose | End-to-end testing, demoing |

---

## Prerequisites

| Tool | Version | Install |
|---|---|---|
| Docker + Docker Compose | 24+ | [docs.docker.com](https://docs.docker.com/get-docker/) |
| Java | 21 (for running Gradle) | `brew install openjdk@21` |
| Python | 3.13 | `brew install python@3.13` |
| uv | latest | `curl -LsSf https://astral.sh/uv/install.sh \| sh` |
| Node.js | 22 | `brew install node@22` |
| GitHub OAuth App | — | [Create one](#github-oauth-app-setup) |

> **macOS note:** If `node` crashes with a dylib error, use `PATH="/opt/homebrew/opt/node@22/bin:$PATH"` to pick up the correct build.

---

## GitHub OAuth App Setup

You need this for the UI login flow regardless of which mode you use.

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**
2. Fill in:
   - **Homepage URL**: `http://localhost:3000`
   - **Authorization callback URL**: `http://localhost:3000/api/auth/callback/github`
3. Copy the **Client ID** and generate a **Client Secret**

---

## Option A — Full Stack (Docker Compose)

The simplest way to run everything end-to-end.

### 1. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and fill in the three required values:

```bash
GITHUB_CLIENT_ID=your-github-client-id
GITHUB_CLIENT_SECRET=your-github-client-secret
AUTH_SECRET=$(openssl rand -base64 32)   # run this and paste the output
```

### 2. Start everything

```bash
docker compose --profile full up --build
```

This starts five services in order:

```
LocalStack  :4566   → DynamoDB, SQS, S3, SES, EventBridge, Secrets Manager
OPA         :8181   → Policy evaluation
control-plane :8080 → Spring Boot (SPRING_PROFILES_ACTIVE=local)
agent       :8000   → FastAPI + LangGraph (USE_FAKE_LLM=true)
ui          :3000   → Next.js
```

The first build takes a few minutes (JDK 25 download + npm install). Subsequent starts use cached layers.

### 3. Try it

Open **http://localhost:3000**, sign in with GitHub, and type:

> "Create an S3 bucket for the platform team in dev"

The agent responds, submits the request, and the status card in the chat polls from SUBMITTED → PR_CREATED → PLAN_APPROVED → DEPLOYED.

### Stopping

```bash
docker compose --profile full down          # stop and remove containers
docker compose --profile full down -v       # also wipe LocalStack data
```

---

## Option B — Dev Mode (Services in Terminals)

Run each service directly for faster iteration. You still need LocalStack running.

### Terminal 1 — LocalStack + OPA

```bash
docker compose up localstack opa
```

Wait for LocalStack to print `Ready.` (the init script creates the DynamoDB table, SQS queue, S3 bucket, and EventBridge bus).

### Terminal 2 — Control Plane

```bash
cd control-plane

# First time only — generates gradlew if missing:
# gradle wrapper --gradle-version 8.12

JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home \
  ./gradlew bootRun --args='--spring.profiles.active=local'
```

Starts on **http://localhost:8080**. Health check: `curl http://localhost:8080/actuator/health`

> The `local` profile points all AWS SDK clients at LocalStack (`http://localhost:4566`) and uses the secrets seeded by the init script.

### Terminal 3 — Agent

```bash
cd agent
uv sync
USE_FAKE_LLM=true \
CONTROL_PLANE_URL=http://localhost:8080 \
INFRAFORGE_SERVICE_KEY=local-service-key \
  uv run uvicorn agent.api.main:app --reload --port 8000
```

Starts on **http://localhost:8000**. Health check: `curl http://localhost:8000/health`

> `USE_FAKE_LLM=true` makes all LLM calls return canned responses — no AWS Bedrock credentials needed.

### Terminal 4 — UI

```bash
cd ui
cp .env.example .env.local   # if not already done
# Fill in GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, AUTH_SECRET in .env.local

npm install
npm run dev
```

Starts on **http://localhost:3000**.

> `next.config.ts` rewrites `/cp/**` → control-plane and `/agent/**` → agent, so the browser never needs to know the backend URLs.

---

## Running Tests

### Control Plane

```bash
cd control-plane
JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home \
  ./gradlew test
```

Uses the `test` Spring profile — in-memory adapters only, no Docker needed.

### Agent

```bash
cd agent
uv sync --extra dev
uv run pytest
```

Uses `GenericFakeChatModel` and mocked HTTP clients — no Docker needed.

### UI (type-check)

```bash
cd ui
npm run type-check
```

---

## Environment Variables Reference

### Control Plane

| Variable | Profile | Default (local) | Description |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | — | `local` | Use `local` for LocalStack, `aws` for real AWS |
| `INFRAFORGE_JWT_SECRET` | all | set in compose | HS256 signing key (≥ 32 chars) |
| `INFRAFORGE_SERVICE_KEY` | all | `local-service-key` | Pre-shared key for agent → control-plane calls |
| `GITHUB_CLIENT_ID` | all | — | GitHub OAuth App client ID |
| `GITHUB_CLIENT_SECRET` | all | — | GitHub OAuth App client secret |
| `AWS_ENDPOINT_OVERRIDE` | local | `http://localhost:4566` | LocalStack endpoint |

### Agent

| Variable | Default | Description |
|---|---|---|
| `CONTROL_PLANE_URL` | `http://localhost:8080` | Control Plane base URL |
| `INFRAFORGE_SERVICE_KEY` | `local-service-key` | Must match control-plane value |
| `USE_FAKE_LLM` | `true` | Set `false` + AWS creds to use real Bedrock |
| `AWS_REGION` | `us-east-1` | AWS region (only matters when `USE_FAKE_LLM=false`) |

### UI

| Variable | Description |
|---|---|
| `GITHUB_CLIENT_ID` | GitHub OAuth App client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App client secret |
| `AUTH_SECRET` | NextAuth session encryption key (`openssl rand -base64 32`) |
| `CONTROL_PLANE_URL` | Default: `http://localhost:8080` |
| `AGENT_API_URL` | Default: `http://localhost:8000` |

---

## Simulating the Full Workflow Manually

If you want to drive state transitions without the UI, use the API directly:

```bash
# 1. Get a JWT (replace with a real GitHub token or use the /auth/token endpoint)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"githubAccessToken": "<your-github-pat>"}' | jq -r .token)

# 2. Submit a request via the agent internal endpoint
curl -s -X POST http://localhost:8080/internal/requests \
  -H "X-Service-Key: local-service-key" \
  -H "Content-Type: application/json" \
  -d '{"userId":"u-001","userEmail":"dev@local","teamId":"platform","rawIntent":"S3 bucket","targetCloud":"AWS"}' \
  | jq .

# 3. Check status
REQUEST_ID=<from step 2>
curl -s http://localhost:8080/api/requests/$REQUEST_ID \
  -H "Authorization: Bearer $TOKEN" | jq .

# 4. Simulate a CI webhook (PLAN_COMPLETED)
curl -s -X POST http://localhost:8080/api/webhooks/github \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: check_run" \
  -H "X-Hub-Signature-256: sha256=<hmac>" \
  -d '{"action":"completed","check_run":{"id":1,"name":"terraform-plan","status":"completed","conclusion":"success","head_branch":"infraforge/'$REQUEST_ID'","html_url":"https://github.com/stub/infra/runs/1"},"repository":{"name":"infra","full_name":"stub/infra"}}'
```

> The webhook requires a valid HMAC-SHA256 signature. For local testing, use `local-webhook-secret` (set in `application-local.yml`) to compute the signature.

---

## Troubleshooting

**LocalStack not ready**
```
curl http://localhost:4566/_localstack/health
```
If services are missing, re-run the init script: `docker compose restart localstack`

**Control plane can't connect to LocalStack**
Verify the `local` profile is active and `AWS_ENDPOINT_OVERRIDE` / `infraforge.aws.endpoint-override` points to `http://localhost:4566`.

**Agent returns errors about control plane**
Make sure the control plane is running and `CONTROL_PLANE_URL` + `INFRAFORGE_SERVICE_KEY` match.

**GitHub OAuth redirect mismatch**
The callback URL in your GitHub OAuth App must exactly match `http://localhost:3000/api/auth/callback/github`.

**Gradle fails with Java version error**
Set `JAVA_HOME` to Java 21 explicitly — Gradle 8.12's Kotlin DSL cannot parse Java 25's version string. The toolchain still downloads JDK 25 for compilation automatically.
