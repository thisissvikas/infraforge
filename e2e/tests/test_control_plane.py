"""
Control Plane API tests.

Covers:
 - Internal endpoints (service-key auth): submit, status, policies, budget, validate
 - Public endpoints (JWT auth): list requests, get request, 401 without token
"""
import pytest
import httpx

from conftest import CP_URL, requires_service

pytestmark = [
    pytest.mark.e2e,
    requires_service(CP_URL, "/actuator/health"),
]


# ── Internal API (/internal/**) ───────────────────────────────────────────────

class TestInternalSubmit:
    def test_submit_request_returns_request_id(self, cp_url, internal_headers):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={
                "userId":    "e2e-user",
                "userEmail": "e2e@infraforge.local",
                "teamId":    "e2e-team",
                "rawIntent": "create an S3 bucket for e2e testing",
                "targetCloud": "AWS",
            },
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200, r.text
        body = r.json()
        assert "requestId" in body
        assert body["state"] == "SUBMITTED"
        assert len(body["requestId"]) > 0

    def test_submit_requires_service_key(self, cp_url):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={"userId": "u", "userEmail": "u@u.com", "teamId": "t", "rawIntent": "test", "targetCloud": "AWS"},
            headers={"Content-Type": "application/json"},
            timeout=5,
        )
        assert r.status_code == 401

    def test_submit_rejects_invalid_target_cloud(self, cp_url, internal_headers):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={"userId": "u", "userEmail": "u@u.com", "teamId": "t", "rawIntent": "test", "targetCloud": "INVALID"},
            headers=internal_headers,
            timeout=5,
        )
        assert r.status_code in (400, 500)


class TestInternalStatus:
    @pytest.fixture(autouse=True)
    def submitted_request(self, cp_url, internal_headers):
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={"userId": "e2e-user", "userEmail": "e2e@infraforge.local",
                  "teamId": "e2e-team", "rawIntent": "status test request", "targetCloud": "AWS"},
            headers=internal_headers,
            timeout=10,
        )
        assert r.status_code == 200
        self.request_id = r.json()["requestId"]

    def test_get_request_status_returns_dto(self, cp_url, internal_headers):
        r = httpx.get(f"{cp_url}/internal/requests/{self.request_id}", headers=internal_headers, timeout=5)
        assert r.status_code == 200
        body = r.json()
        assert body["requestId"] == self.request_id
        assert body["state"] in ("SUBMITTED", "PR_CREATED")  # may have transitioned already
        assert body["rawIntent"] == "status test request"
        assert body["targetCloud"] == "AWS"

    def test_get_nonexistent_request_returns_404(self, cp_url, internal_headers):
        r = httpx.get(f"{cp_url}/internal/requests/does-not-exist", headers=internal_headers, timeout=5)
        assert r.status_code == 404


class TestInternalPolicies:
    def test_get_policies_returns_approved_modules(self, cp_url, internal_headers):
        r = httpx.get(f"{cp_url}/internal/policies", params={"teamId": "e2e-team"}, headers=internal_headers, timeout=5)
        assert r.status_code == 200
        body = r.json()
        assert body["teamId"] == "e2e-team"
        assert isinstance(body["approvedModules"], list)
        assert len(body["approvedModules"]) > 0

    def test_get_budget_returns_ceiling(self, cp_url, internal_headers):
        r = httpx.get(f"{cp_url}/internal/budget", params={"teamId": "e2e-team"}, headers=internal_headers, timeout=5)
        assert r.status_code == 200
        body = r.json()
        assert body["teamId"] == "e2e-team"
        assert body["ceilingUsd"] > 0
        assert "headroomUsd" in body

    def test_validate_architecture_passes_stub(self, cp_url, internal_headers):
        r = httpx.post(
            f"{cp_url}/internal/validate",
            json={"resource": "aws_s3_bucket", "name": "test"},
            headers=internal_headers,
            timeout=5,
        )
        assert r.status_code == 200
        body = r.json()
        assert body["passed"] is True
        assert body["violations"] == []


# ── Public API (/api/**) ──────────────────────────────────────────────────────

class TestPublicApi:
    def test_list_requests_requires_jwt(self, cp_url):
        r = httpx.get(f"{cp_url}/api/requests", timeout=5)
        assert r.status_code == 401

    def test_list_requests_with_jwt_returns_list(self, cp_url, cp_headers):
        r = httpx.get(f"{cp_url}/api/requests", headers=cp_headers, timeout=5)
        assert r.status_code == 200
        assert isinstance(r.json(), list)

    def test_get_me_returns_user(self, cp_url, cp_headers):
        r = httpx.get(f"{cp_url}/api/me", headers=cp_headers, timeout=5)
        assert r.status_code == 200
        body = r.json()
        assert "userId" in body
        assert body["login"] == "e2e"

    def test_get_other_users_request_returns_404(self, cp_url, cp_headers, internal_headers):
        # Submit a request as a different user via the internal API
        r = httpx.post(
            f"{cp_url}/internal/requests",
            json={"userId": "other-user", "userEmail": "other@test.com",
                  "teamId": "other-team", "rawIntent": "other users request", "targetCloud": "AWS"},
            headers=internal_headers,
            timeout=10,
        )
        request_id = r.json()["requestId"]
        # Try to fetch it as e2e-test-user — should get 404
        r2 = httpx.get(f"{cp_url}/api/requests/{request_id}", headers=cp_headers, timeout=5)
        assert r2.status_code == 404
