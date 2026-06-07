#!/usr/bin/env bash
# Run the infraforge E2E test suite against a live stack.
#
# Usage:
#   ./run_e2e.sh                    # run all tests
#   ./run_e2e.sh -m health          # only health checks
#   ./run_e2e.sh -m workflow        # only workflow simulation tests
#   ./run_e2e.sh -m "e2e and not workflow"
#
# The suite auto-skips tests whose target service is not reachable, so it is
# safe to run with only some services up (e.g. just the control-plane).
#
# Environment overrides (optional):
#   CP_URL=http://my-host:8080
#   AGENT_URL=http://my-host:8000
#   GITHUB_WEBHOOK_SECRET=my-secret   # only if CP is configured with a webhook secret
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Install deps if venv is missing
if [ ! -d ".venv" ]; then
  echo "==> Setting up E2E virtualenv..."
  uv venv .venv
  uv pip install -e ".[dev]" 2>/dev/null || uv pip install -e .
fi

echo "==> infraforge E2E — target stack:"
echo "    Control Plane : ${CP_URL:-http://localhost:8080}"
echo "    Agent         : ${AGENT_URL:-http://localhost:8000}"
echo "    UI            : ${UI_URL:-http://localhost:3000}"
echo "    LocalStack    : ${LOCALSTACK_URL:-http://localhost:4566}"
echo "    OPA           : ${OPA_URL:-http://localhost:8181}"
echo ""

# Forward any extra args (e.g. -m health) to pytest
uv run pytest "${@}"
