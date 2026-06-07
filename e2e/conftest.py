"""
Shared fixtures and service-availability guards for the infraforge E2E suite.

All tests are skipped automatically if the target service is not reachable,
so the suite is safe to run in CI without a live stack — failing tests indicate
a real problem, not a missing environment.
"""
import os
import pytest
import httpx

from utils.jwt import make_test_token

# ── Service URLs (overridable via env) ────────────────────────────────────────
CP_URL         = os.getenv("CP_URL",         "http://localhost:8080")
AGENT_URL      = os.getenv("AGENT_URL",      "http://localhost:8000")
UI_URL         = os.getenv("UI_URL",         "http://localhost:3000")
LOCALSTACK_URL = os.getenv("LOCALSTACK_URL", "http://localhost:4566")
OPA_URL        = os.getenv("OPA_URL",        "http://localhost:8181")

# ── Local dev secrets ─────────────────────────────────────────────────────────
SERVICE_KEY    = os.getenv("INFRAFORGE_SERVICE_KEY", "local-service-key")
# Webhook secret — empty string means the control-plane skips HMAC verification
WEBHOOK_SECRET = os.getenv("GITHUB_WEBHOOK_SECRET", "")

# ── Helpers ───────────────────────────────────────────────────────────────────

def _reachable(url: str, path: str = "/") -> bool:
    try:
        httpx.get(f"{url}{path}", timeout=3.0)
        return True
    except Exception:
        return False


def requires_service(url: str, path: str = "/") -> pytest.MarkDecorator:
    """Skip the test if the service at url+path is not reachable."""
    return pytest.mark.skipif(
        not _reachable(url, path),
        reason=f"Service not reachable: {url}{path}",
    )


# ── Session-scoped fixtures ───────────────────────────────────────────────────

@pytest.fixture(scope="session")
def cp_url() -> str:
    return CP_URL

@pytest.fixture(scope="session")
def agent_url() -> str:
    return AGENT_URL

@pytest.fixture(scope="session")
def service_key() -> str:
    return SERVICE_KEY

@pytest.fixture(scope="session")
def webhook_secret() -> str:
    return WEBHOOK_SECRET

@pytest.fixture(scope="session")
def test_jwt() -> str:
    """HS256 JWT signed with the local dev secret — accepted by the control-plane."""
    return make_test_token()

@pytest.fixture(scope="session")
def cp_headers(test_jwt: str) -> dict:
    return {"Authorization": f"Bearer {test_jwt}"}

@pytest.fixture(scope="session")
def internal_headers(service_key: str) -> dict:
    return {"X-Service-Key": service_key, "Content-Type": "application/json"}
