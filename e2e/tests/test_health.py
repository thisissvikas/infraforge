"""
Health checks — verify every service in the stack is up and responding.
These are the first tests that should pass; if any fail, nothing else will work.
"""
import pytest
import httpx

from conftest import (
    CP_URL, AGENT_URL, UI_URL, LOCALSTACK_URL, OPA_URL,
    requires_service,
)


@pytest.mark.health
@requires_service(LOCALSTACK_URL, "/_localstack/health")
def test_localstack_is_healthy():
    r = httpx.get(f"{LOCALSTACK_URL}/_localstack/health", timeout=5)
    assert r.status_code == 200
    body = r.json()
    # All configured services should be "running" or "available"
    services = body.get("services", {})
    for svc in ("dynamodb", "sqs", "s3"):
        status = services.get(svc, "missing")
        assert status in ("running", "available"), f"LocalStack service {svc!r} not ready: {status}"


@pytest.mark.health
@requires_service(OPA_URL, "/health")
def test_opa_is_healthy():
    r = httpx.get(f"{OPA_URL}/health", timeout=5)
    assert r.status_code == 200


@pytest.mark.health
@requires_service(CP_URL, "/actuator/health")
def test_control_plane_is_healthy():
    r = httpx.get(f"{CP_URL}/actuator/health", timeout=5)
    assert r.status_code == 200
    assert r.json().get("status") == "UP"


@pytest.mark.health
@requires_service(AGENT_URL, "/health")
def test_agent_is_healthy():
    r = httpx.get(f"{AGENT_URL}/health", timeout=5)
    assert r.status_code == 200
    assert r.json().get("status") == "ok"


@pytest.mark.health
@requires_service(UI_URL)
def test_ui_is_reachable():
    # The UI redirects unauthenticated users to /login — 200 or 307 both mean it's up
    r = httpx.get(UI_URL, timeout=5, follow_redirects=True)
    assert r.status_code == 200
