"""Generate HS256 JWTs signed with the local-dev secret for E2E tests."""
import time
from jose import jwt

# Matches infraforge.jwt.secret in application-local.yml
LOCAL_JWT_SECRET = "local-dev-secret-key-min-32-chars-for-hmac256"


def make_test_token(
    user_id: str = "e2e-test-user",
    login: str = "e2e",
    email: str = "e2e@infraforge.local",
    secret: str = LOCAL_JWT_SECRET,
    ttl_seconds: int = 3600,
) -> str:
    """Return a signed JWT accepted by the local control-plane."""
    now = int(time.time())
    payload = {
        "sub": user_id,
        "email": email,
        "login": login,
        "iat": now,
        "exp": now + ttl_seconds,
    }
    return jwt.encode(payload, secret, algorithm="HS256")
