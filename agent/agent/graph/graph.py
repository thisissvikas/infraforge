from functools import partial
from typing import Any

from langgraph.checkpoint.memory import MemorySaver
from langgraph.graph import END, StateGraph

from agent.config import settings
from agent.graph.nodes.confirm import confirm_node
from agent.graph.nodes.context_fetch import context_fetch_node
from agent.graph.nodes.cost_estimate import cost_estimate_node
from agent.graph.nodes.generate import generate_node
from agent.graph.nodes.intake import intake_node
from agent.graph.nodes.refine import refine_node
from agent.graph.nodes.submit import submit_node
from agent.graph.nodes.validate import validate_node
from agent.graph.state import AgentState
from agent.llm import get_llm
from agent.tools.control_plane import ControlPlaneClient

MAX_REFINE_ATTEMPTS = 3


def build_graph() -> Any:
    llm = get_llm(settings)
    client = ControlPlaneClient(settings.control_plane_url, settings.service_key)

    builder: StateGraph = StateGraph(AgentState)

    builder.add_node("intake", partial(intake_node, llm=llm))
    builder.add_node("context_fetch", partial(context_fetch_node, client=client))
    builder.add_node("generate", partial(generate_node, llm=llm))
    builder.add_node("validate", partial(validate_node, client=client))
    builder.add_node("refine", partial(refine_node, llm=llm))
    builder.add_node("cost_estimate", cost_estimate_node)
    builder.add_node("confirm", confirm_node)
    builder.add_node("submit", partial(submit_node, client=client))

    builder.set_entry_point("intake")

    def after_intake(state: AgentState) -> str:
        if state.get("intent_confidence", 0) < 0.7:
            return "intake"
        return "context_fetch"

    def after_validate(state: AgentState) -> str:
        if state.get("validation_passed"):
            return "cost_estimate"
        if (state.get("refine_attempt") or 0) >= MAX_REFINE_ATTEMPTS:
            return "intake"
        return "refine"

    def after_cost(state: AgentState) -> str:
        if not state.get("cost_within_budget", True):
            return "intake"
        return "confirm"

    def after_confirm(state: AgentState) -> str:
        pending = state.get("clarification_needed") or []
        if "awaiting_confirm" not in pending:
            return "submit"
        last_msgs = state.get("messages", [])
        if last_msgs:
            last = last_msgs[-1]
            content = getattr(last, "content", "").lower()
            if any(w in content for w in ["yes", "go", "approve", "confirm", "proceed"]):
                return "submit"
        return "intake"

    builder.add_conditional_edges(
        "intake",
        after_intake,
        {"intake": "intake", "context_fetch": "context_fetch"},
    )
    builder.add_edge("context_fetch", "generate")
    builder.add_edge("generate", "validate")
    builder.add_conditional_edges(
        "validate",
        after_validate,
        {"cost_estimate": "cost_estimate", "refine": "refine", "intake": "intake"},
    )
    builder.add_edge("refine", "validate")
    builder.add_conditional_edges(
        "cost_estimate",
        after_cost,
        {"confirm": "confirm", "intake": "intake"},
    )
    builder.add_conditional_edges(
        "confirm",
        after_confirm,
        {"submit": "submit", "intake": "intake"},
    )
    builder.add_edge("submit", END)

    return builder.compile(checkpointer=MemorySaver(), interrupt_before=["confirm"])


graph = build_graph()
