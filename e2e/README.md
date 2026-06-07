# infraforge — E2E Tests

End-to-end test suite that verifies the full stack is running and all components integrate correctly.

## Test Files

| File | What it tests |
|---|---|
| `tests/test_health.py` | All 5 services respond and are healthy (LocalStack, OPA, control-plane, agent, UI) |
| `tests/test_control_plane.py` | Internal API (submit, status, policies, budget, validate) + public API (JWT auth, user isolation) |
| `tests/test_agent.py` | Agent `/chat` endpoint — session management, message round-trip, agent→CP integration |
| `tests/test_workflow.py` | Full state machine: SUBMITTED → PR_CREATED → PLAN_APPROVED → DEPLOYED via real webhooks |

## Prerequisites

- [uv](https://docs.astral.sh/uv/) installed
- The full stack running (see [docs/running-locally.md](../docs/running-locally.md))

## Running

```bash
# Start the full stack first
docker compose --profile full up

# Then in another terminal, from the e2e/ directory:
cd e2e

# All tests
./run_e2e.sh

# Only health checks (fastest — confirms services are up)
./run_e2e.sh -m health

# Only workflow simulation (slowest — drives full state machine)
./run_e2e.sh -m workflow

# Everything except the full workflow
./run_e2e.sh -m "e2e and not workflow"

# Stop on first failure
./run_e2e.sh -x
```

## Auto-Skip Behaviour

Every test is guarded by a `requires_service()` check. If the target service is not reachable, the test is **skipped** (not failed). This makes it safe to run against a partial stack:

```
# Only control-plane + LocalStack running:
./run_e2e.sh
# → health/agent and health/ui SKIPPED, everything else runs normally
```

## Environment Overrides

```bash
CP_URL=http://staging:8080 \
AGENT_URL=http://staging:8000 \
  ./run_e2e.sh
```

| Variable | Default | Description |
|---|---|---|
| `CP_URL` | `http://localhost:8080` | Control Plane base URL |
| `AGENT_URL` | `http://localhost:8000` | Agent base URL |
| `UI_URL` | `http://localhost:3000` | UI base URL |
| `LOCALSTACK_URL` | `http://localhost:4566` | LocalStack base URL |
| `OPA_URL` | `http://localhost:8181` | OPA base URL |
| `INFRAFORGE_SERVICE_KEY` | `local-service-key` | Must match the running control-plane |
| `GITHUB_WEBHOOK_SECRET` | *(empty)* | Only set if control-plane is configured with one |

## How the Webhook Tests Work

The workflow tests POST synthetic GitHub `check_run` webhooks to `/api/webhooks/github`. In local mode the webhook secret is empty, so HMAC signature verification is skipped by the control-plane. For non-local environments, set `GITHUB_WEBHOOK_SECRET` to the value configured on the server.

The test payload uses the branch naming convention `infraforge/<requestId>`, which the `WebhookController` uses to route the event to the correct request.
