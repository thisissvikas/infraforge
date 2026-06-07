"""
Full end-to-end workflow simulation.

Flow under test:
  POST /internal/requests  →  SUBMITTED
       WorkflowService processes REQUEST_SUBMITTED  →  PR_CREATED  (stub)
  POST /api/webhooks/github  (terraform-plan success)  →  PLAN_APPROVED
  POST /api/webhooks/github  (terraform-apply success)  →  DEPLOYED

All state transitions are driven through the real HTTP APIs.  The webhook
endpoint no longer requires a JWT (HMAC-only auth — an empty webhook secret
means the signature check is skipped in local mode).
"""
import time
import pytest
import httpx

from conftest import CP_URL, requires_service
from utils.webhook import plan_completed_payload, apply_completed_payload, sign_payload

pytestmark = [
    pytest.mark.e2e,
    pytest.mark.workflow,
    requires_service(CP_URL, "/actuator/health"),
]

POLL_INTERVAL = 0.25   # seconds between state polls
POLL_TIMEOUT  = 10.0   # maximum seconds to wait for a transition


def _wait_for_state(
    cp_url: str,
    request_id: str,
    expected_state: str,
    internal_headers: dict,
    timeout: float = POLL_TIMEOUT,
) -> dict:
    """Poll GET /internal/requests/{id} until state matches or timeout."""
    deadline = time.monotonic() + timeout
    last_state = None
    while time.monotonic() < deadline:
        r = httpx.get(f"{cp_url}/internal/requests/{request_id}", headers=internal_headers, timeout=5)
        if r.status_code == 200:
            body = r.json()
            last_state = body.get("state")
            if last_state == expected_state:
                return body
        time.sleep(POLL_INTERVAL)
    raise AssertionError(
        f"Request {request_id} did not reach state {expected_state!r} within {timeout}s. "
        f"Last observed state: {last_state!r}"
    )


def _send_webhook(cp_url: str, body: str, webhook_secret: str) -> httpx.Response:
    headers = {
        "Content-Type": "application/json",
        "X-GitHub-Event": "check_run",
    }
    if webhook_secret:
        headers["X-Hub-Signature-256"] = sign_payload(body, webhook_secret)
    return httpx.post(f"{cp_url}/api/webhooks/github", content=body, headers=headers, timeout=5)


# ── Individual transition tests ───────────────────────────────────────────────

class TestSubmitTransition:
    def test_submit_reaches_submitted_state(self, cp_url, internal_headers):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={
                "userId":    "e2e-workflow-user",
                "userEmail": "workflow@infraforge.local",
                "teamId":    "workflow-team",
                "rawIntent": "create a VPC for workflow test",
                "targetCloud": "AWS",
            },
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200
        body = r.json()
        assert body["state"] == "SUBMITTED"
        assert "requestId" in body

    def test_submit_transitions_to_pr_created(self, cp_url, internal_headers):
        """After submit, WorkflowService processes the event and creates a stub PR."""
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={
                "userId":    "e2e-workflow-user",
                "userEmail": "workflow@infraforge.local",
                "teamId":    "workflow-team",
                "rawIntent": "create an RDS instance for PR_CREATED test",
                "targetCloud": "AWS",
            },
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200
        request_id = r.json()["requestId"]

        state = _wait_for_state(cp_url, request_id, "PR_CREATED", internal_headers)
        assert state["githubPrUrl"] is not None
        assert state["githubBranch"] == f"infraforge/{request_id}"


class TestPlanWebhook:
    @pytest.fixture(autouse=True)
    def request_in_pr_created(self, cp_url, internal_headers):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={
                "userId":    "e2e-workflow-user",
                "userEmail": "workflow@infraforge.local",
                "teamId":    "workflow-team",
                "rawIntent": "plan webhook test",
                "targetCloud": "AWS",
            },
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200
        self.request_id = r.json()["requestId"]
        # Wait until PR_CREATED before sending the plan webhook
        _wait_for_state(cp_url, self.request_id, "PR_CREATED", internal_headers)

    def test_plan_success_webhook_transitions_to_plan_approved(
        self, cp_url, internal_headers, webhook_secret
    ):
        body = plan_completed_payload(self.request_id, conclusion="success")
        r = _send_webhook(cp_url, body, webhook_secret)
        assert r.status_code == 200

        state = _wait_for_state(cp_url, self.request_id, "PLAN_APPROVED", internal_headers)
        assert state["state"] == "PLAN_APPROVED"

    def test_plan_failure_webhook_transitions_to_failed(
        self, cp_url, internal_headers, webhook_secret
    ):
        # Submit a fresh request for this test
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={"userId": "e2e-workflow-user", "userEmail": "w@w.com",
                  "teamId": "workflow-team", "rawIntent": "plan failure test", "targetCloud": "AWS"},
            headers=internal_headers, timeout=10,
        )
        req_id = r.json()["requestId"]
        _wait_for_state(cp_url, req_id, "PR_CREATED", internal_headers)

        body = plan_completed_payload(req_id, conclusion="failure")
        _send_webhook(cp_url, body, webhook_secret)

        state = _wait_for_state(cp_url, req_id, "FAILED", internal_headers)
        assert state["state"] == "FAILED"


class TestApplyWebhook:
    @pytest.fixture(autouse=True)
    def request_in_plan_approved(self, cp_url, internal_headers, webhook_secret):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={
                "userId":    "e2e-workflow-user",
                "userEmail": "workflow@infraforge.local",
                "teamId":    "workflow-team",
                "rawIntent": "apply webhook test",
                "targetCloud": "AWS",
            },
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200
        self.request_id = r.json()["requestId"]
        _wait_for_state(cp_url, self.request_id, "PR_CREATED", internal_headers)

        # Send plan success to reach PLAN_APPROVED
        body = plan_completed_payload(self.request_id, conclusion="success")
        _send_webhook(cp_url, body, webhook_secret)
        _wait_for_state(cp_url, self.request_id, "PLAN_APPROVED", internal_headers)

    def test_apply_success_webhook_transitions_to_deployed(
        self, cp_url, internal_headers, webhook_secret
    ):
        body = apply_completed_payload(self.request_id, conclusion="success")
        r = _send_webhook(cp_url, body, webhook_secret)
        assert r.status_code == 200

        state = _wait_for_state(cp_url, self.request_id, "DEPLOYED", internal_headers)
        assert state["state"] == "DEPLOYED"

    def test_apply_failure_webhook_transitions_to_failed(
        self, cp_url, internal_headers, webhook_secret
    ):
        body = apply_completed_payload(self.request_id, conclusion="failure")
        _send_webhook(cp_url, body, webhook_secret)

        state = _wait_for_state(cp_url, self.request_id, "FAILED", internal_headers)
        assert state["state"] == "FAILED"


# ── Full happy-path test ──────────────────────────────────────────────────────

class TestFullWorkflow:
    def test_submitted_to_deployed(self, cp_url, internal_headers, webhook_secret):
        """
        Complete happy-path:
          SUBMITTED → PR_CREATED → PLAN_APPROVED → DEPLOYED
        """
        # 1 — submit
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={
                "userId":    "e2e-workflow-user",
                "userEmail": "workflow@infraforge.local",
                "teamId":    "workflow-team",
                "rawIntent": "full E2E: deploy a complete ECS service",
                "targetCloud": "AWS",
                "generatedTerraform": 'resource "aws_ecs_service" "main" {}',
            },
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200
        request_id = r.json()["requestId"]

        # 2 — wait for WorkflowService to create the stub PR
        pr_state = _wait_for_state(cp_url, request_id, "PR_CREATED", internal_headers)
        assert pr_state["githubPrUrl"] is not None

        # 3 — CI: terraform plan passes
        r2 = _send_webhook(cp_url, plan_completed_payload(request_id), webhook_secret)
        assert r2.status_code == 200
        _wait_for_state(cp_url, request_id, "PLAN_APPROVED", internal_headers)

        # 4 — CI: terraform apply passes
        r3 = _send_webhook(cp_url, apply_completed_payload(request_id), webhook_secret)
        assert r3.status_code == 200
        final = _wait_for_state(cp_url, request_id, "DEPLOYED", internal_headers, timeout=15)

        assert final["state"] == "DEPLOYED"
        assert final["requestId"] == request_id
        assert final["rawIntent"] == "full E2E: deploy a complete ECS service"
        assert final["targetCloud"] == "AWS"

    def test_submitted_to_failed_on_apply(self, cp_url, internal_headers, webhook_secret):
        """
        Failure path:
          SUBMITTED → PR_CREATED → PLAN_APPROVED → FAILED (apply failure)
        """
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={"userId": "e2e-workflow-user", "userEmail": "w@w.com",
                  "teamId": "workflow-team", "rawIntent": "apply failure E2E test", "targetCloud": "GCP"},
            headers=internal_headers, timeout=10,
        )
        assert r.status_code == 200
        request_id = r.json()["requestId"]

        _wait_for_state(cp_url, request_id, "PR_CREATED", internal_headers)
        _send_webhook(cp_url, plan_completed_payload(request_id, "success"), webhook_secret)
        _wait_for_state(cp_url, request_id, "PLAN_APPROVED", internal_headers)
        _send_webhook(cp_url, apply_completed_payload(request_id, "failure"), webhook_secret)

        final = _wait_for_state(cp_url, request_id, "FAILED", internal_headers)
        assert final["state"] == "FAILED"
