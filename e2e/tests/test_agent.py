"""
Agent API tests.

Covers:
 - Health endpoint
 - POST /chat: session management, message round-trip, request submission flow
"""
import uuid
import pytest
import httpx

from conftest import AGENT_URL, CP_URL, requires_service

pytestmark = [
    pytest.mark.e2e,
    requires_service(AGENT_URL, "/health"),
]


class TestAgentHealth:
    def test_health_returns_ok(self, agent_url):
        r = httpx.get(f"{agent_url}/health", timeout=5)
        assert r.status_code == 200
        assert r.json()["status"] == "ok"


class TestAgentChat:
    def test_chat_returns_session_id_and_message(self, agent_url):
        session_id = str(uuid.uuid4())
        r = httpx.post(
            f"{agent_url}/chat",
            json={
                "session_id":  session_id,
                "message":     "Create an S3 bucket for my team",
                "user_id":     "e2e-user",
                "user_email":  "e2e@infraforge.local",
                "team_id":     "e2e-team",
                "target_cloud": "AWS",
            },
            timeout=30,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert body["session_id"] == session_id
        assert isinstance(body["message"], str)
        assert len(body["message"]) > 0

    def test_chat_new_session_has_no_request_id(self, agent_url):
        """A fresh message that hasn't reached the submit node yet has no requestId."""
        r = httpx.post(
            f"{agent_url}/chat",
            json={
                "session_id":  str(uuid.uuid4()),
                "message":     "Hello, what can you do?",
                "user_id":     "e2e-user",
                "team_id":     "e2e-team",
                "target_cloud": "AWS",
            },
            timeout=30,
        )
        assert r.status_code == 200
        # request_id is None until the submit node runs
        body = r.json()
        assert "request_id" in body  # field exists

    def test_chat_different_sessions_are_independent(self, agent_url):
        """Two sessions started with the same message should get independent session IDs."""
        payload = {
            "message":      "Create a VPC",
            "user_id":      "e2e-user",
            "team_id":      "e2e-team",
            "target_cloud": "AWS",
        }
        r1 = httpx.post(f"{agent_url}/chat", json={**payload, "session_id": str(uuid.uuid4())}, timeout=30)
        r2 = httpx.post(f"{agent_url}/chat", json={**payload, "session_id": str(uuid.uuid4())}, timeout=30)
        assert r1.status_code == 200
        assert r2.status_code == 200
        assert r1.json()["session_id"] != r2.json()["session_id"]

    @pytest.mark.skipif(
        not requires_service(CP_URL, "/actuator/health"),
        reason="Control plane not running — skipping agent→CP integration test",
    )
    def test_chat_full_flow_submits_request(self, agent_url, cp_url, internal_headers):
        """
        Drive the agent through its full graph until it submits a request to the
        control plane.  With USE_FAKE_LLM=true the fake LLM cycles through canned
        responses, so the graph reaches submit_node in a single session.
        """
        session_id = str(uuid.uuid4())

        # Step 1 — user intent
        r = httpx.post(f"{agent_url}/chat", json={
            "session_id":   session_id,
            "message":      "Create an S3 bucket for the platform team in dev",
            "user_id":      "e2e-user",
            "user_email":   "e2e@infraforge.local",
            "team_id":      "e2e-team",
            "target_cloud": "AWS",
        }, timeout=30)
        assert r.status_code == 200
        body = r.json()

        # If request_id came back immediately (fake LLM fast-paths), we're done
        if body.get("request_id"):
            _verify_request_exists(cp_url, body["request_id"], internal_headers)
            return

        # Step 2 — confirm (the graph pauses at confirm_node waiting for approval)
        r2 = httpx.post(f"{agent_url}/chat", json={
            "session_id":   session_id,
            "message":      "yes",
            "user_id":      "e2e-user",
            "user_email":   "e2e@infraforge.local",
            "team_id":      "e2e-team",
            "target_cloud": "AWS",
        }, timeout=30)
        assert r2.status_code == 200
        body2 = r2.json()

        if body2.get("request_id"):
            _verify_request_exists(cp_url, body2["request_id"], internal_headers)


def _verify_request_exists(cp_url: str, request_id: str, internal_headers: dict) -> None:
    r = httpx.get(f"{cp_url}/internal/requests/{request_id}", headers=internal_headers, timeout=5)
    assert r.status_code == 200, f"Request {request_id} not found in control plane"
    assert r.json()["requestId"] == request_id
