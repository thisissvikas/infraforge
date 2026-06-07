from typing import Any

from langchain_core.messages import AIMessage

from agent.graph.state import AgentState


async def confirm_node(state: AgentState) -> dict[str, Any]:
    cost = state.get("estimated_monthly_cost_usd") or 0.0
    tf_files = state.get("terraform_files") or {}
    file_list = ", ".join(tf_files.keys()) or "main.tf"

    summary = (
        f"Ready to submit your infrastructure request.\n\n"
        f"**Files**: {file_list}\n"
        f"**Estimated cost**: ${cost:.2f}/month\n"
        f"**Target cloud**: {state.get('target_cloud', 'AWS')}\n\n"
        f"Type **yes** to confirm and submit, or describe any changes."
    )
    return {
        "messages": [AIMessage(content=summary)],
        "clarification_needed": ["awaiting_confirm"],
    }
