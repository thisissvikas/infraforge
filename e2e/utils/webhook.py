"""Build GitHub-style HMAC-SHA256 signed webhook payloads."""
import hashlib
import hmac
import json
import time


def sign_payload(body: str, secret: str) -> str:
    """Return the X-Hub-Signature-256 header value for the given body."""
    mac = hmac.new(secret.encode(), body.encode(), hashlib.sha256)
    return f"sha256={mac.hexdigest()}"


def plan_completed_payload(request_id: str, conclusion: str = "success") -> str:
    """Return a check_run completed payload for terraform-plan."""
    payload = {
        "action": "completed",
        "check_run": {
            "id": int(time.time()),
            "name": "terraform-plan",
            "status": "completed",
            "conclusion": conclusion,
            "head_branch": f"infraforge/{request_id}",
            "html_url": f"https://github.com/stub/infra/runs/1",
        },
        "repository": {
            "name": "infra",
            "full_name": "stub/infra",
        },
    }
    return json.dumps(payload)


def apply_completed_payload(request_id: str, conclusion: str = "success") -> str:
    """Return a check_run completed payload for terraform-apply."""
    payload = {
        "action": "completed",
        "check_run": {
            "id": int(time.time()) + 1,
            "name": "terraform-apply",
            "status": "completed",
            "conclusion": conclusion,
            "head_branch": f"infraforge/{request_id}",
            "html_url": f"https://github.com/stub/infra/runs/2",
        },
        "repository": {
            "name": "infra",
            "full_name": "stub/infra",
        },
    }
    return json.dumps(payload)
