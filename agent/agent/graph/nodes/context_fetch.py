import asyncio
from typing import Any

from agent.graph.state import AgentState
from agent.tools.control_plane import ControlPlaneClient


async def context_fetch_node(state: AgentState, client: ControlPlaneClient) -> dict[str, Any]:
    team_id = state.get("team_id") or "default"

    policies_res, budget_res = await asyncio.gather(
        client.get_policies(team_id),
        client.get_budget(team_id),
        return_exceptions=True,
    )

    policies = policies_res.policies if not isinstance(policies_res, Exception) else []
    approved = policies_res.approved_modules if not isinstance(policies_res, Exception) else []
    budget = budget_res.ceiling_usd if not isinstance(budget_res, Exception) else 1000.0

    return {
        "retrieved_policies": policies,
        "approved_modules": approved,
        "team_budget_usd": budget,
    }
