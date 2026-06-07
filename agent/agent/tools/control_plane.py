import httpx
from pydantic import BaseModel, Field, ConfigDict, alias_generators


class PolicyContext(BaseModel):
    model_config = ConfigDict(alias_generator=alias_generators.to_camel, populate_by_name=True)
    policies: list[str] = []
    approved_modules: list[str] = []


class BudgetContext(BaseModel):
    model_config = ConfigDict(alias_generator=alias_generators.to_camel, populate_by_name=True)
    ceiling_usd: float = Field(default=1000.0, alias="ceilingUsd")
    current_spend_usd: float = Field(default=0.0, alias="currentSpendUsd")
    headroom_usd: float = Field(default=1000.0, alias="headroomUsd")


class ValidationResult(BaseModel):
    passed: bool = True
    violations: list[str] = []


class SubmitResult(BaseModel):
    model_config = ConfigDict(alias_generator=alias_generators.to_camel, populate_by_name=True)
    request_id: str
    state: str


class ControlPlaneClient:
    def __init__(self, base_url: str, service_key: str) -> None:
        self._base = base_url.rstrip("/")
        self._key = service_key

    def _headers(self) -> dict[str, str]:
        return {"X-Service-Key": self._key, "Content-Type": "application/json"}

    async def get_policies(self, team_id: str) -> PolicyContext:
        async with httpx.AsyncClient() as c:
            r = await c.get(
                f"{self._base}/internal/policies",
                params={"teamId": team_id},
                headers=self._headers(),
                timeout=10.0,
            )
            r.raise_for_status()
            return PolicyContext(**r.json())

    async def get_budget(self, team_id: str) -> BudgetContext:
        async with httpx.AsyncClient() as c:
            r = await c.get(
                f"{self._base}/internal/budget",
                params={"teamId": team_id},
                headers=self._headers(),
                timeout=10.0,
            )
            r.raise_for_status()
            return BudgetContext(**r.json())

    async def validate_architecture(self, plan_json: str) -> ValidationResult:
        async with httpx.AsyncClient() as c:
            r = await c.post(
                f"{self._base}/internal/validate",
                content=plan_json,
                headers=self._headers(),
                timeout=30.0,
            )
            r.raise_for_status()
            return ValidationResult(**r.json())

    async def submit_request(self, payload: dict) -> SubmitResult:
        async with httpx.AsyncClient() as c:
            r = await c.post(
                f"{self._base}/internal/requests",
                json=payload,
                headers=self._headers(),
                timeout=30.0,
            )
            r.raise_for_status()
            return SubmitResult(**r.json())

    async def get_request_status(self, request_id: str) -> dict:
        async with httpx.AsyncClient() as c:
            r = await c.get(
                f"{self._base}/internal/requests/{request_id}",
                headers=self._headers(),
                timeout=10.0,
            )
            r.raise_for_status()
            return r.json()
