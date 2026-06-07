from typing import Any

from agent.graph.state import AgentState
from agent.tools.cost_explorer import estimate_cost


async def cost_estimate_node(state: AgentState) -> dict[str, Any]:
    cost = await estimate_cost(state.get("terraform_files") or {})
    budget = state.get("team_budget_usd") or 1000.0
    return {
        "estimated_monthly_cost_usd": cost,
        "cost_within_budget": cost <= budget,
    }
