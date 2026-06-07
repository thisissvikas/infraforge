from typing import Any

from langchain_core.messages import AIMessage

from agent.graph.state import AgentState
from agent.tools.control_plane import ControlPlaneClient


async def submit_node(state: AgentState, client: ControlPlaneClient) -> dict[str, Any]:
    payload = {
        "userId": state.get("user_id") or "local-user",
        "userEmail": state.get("user_email") or "dev@local",
        "teamId": state.get("team_id") or "default",
        "rawIntent": state.get("raw_intent") or "",
        "targetCloud": state.get("target_cloud") or "AWS",
        "generatedTerraform": state.get("generated_terraform"),
    }
    result = await client.submit_request(payload)
    return {
        "request_id": result.request_id,
        "submitted": True,
        "messages": [AIMessage(content=f"Submitted! Your request ID is `{result.request_id}`. I'll track the deployment status for you.")],
    }
