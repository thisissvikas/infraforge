"""Shared agent state definition — passed between every LangGraph node."""
from __future__ import annotations

from typing import Annotated, Any
from typing import TypedDict

from langchain_core.messages import BaseMessage
from langgraph.graph.message import add_messages


class AgentState(TypedDict):
    """Immutable-by-convention state object threaded through the LangGraph graph."""

    # ── Conversation ──────────────────────────────────────────────────────────
    messages: Annotated[list[BaseMessage], add_messages]
    session_id: str
    user_id: str
    user_email: str
    team_id: str

    # ── Parsed intent (populated by intake_node) ──────────────────────────────
    raw_intent: str
    parsed_intent: dict[str, Any]      # structured extraction of what the user wants
    intent_confidence: float           # 0.0–1.0; < 0.7 triggers clarification loop
    clarification_needed: list[str]    # list of dimensions still underspecified

    # ── Policy context (populated by context_fetch_node) ─────────────────────
    retrieved_policies: list[str]      # chunks from Bedrock KB
    team_budget_usd: float
    approved_modules: list[str]

    # ── Generation (populated by generate_node) ───────────────────────────────
    generated_terraform: str | None
    terraform_files: dict[str, str]    # filename → HCL content

    # ── Validation (populated by validate_node / refine_node) ─────────────────
    validation_errors: list[str]
    validation_passed: bool
    refine_attempt: int                # max 3

    # ── Cost (populated by cost_estimate_node) ────────────────────────────────
    estimated_monthly_cost_usd: float
    cost_within_budget: bool

    # ── Submission (populated by submit_node) ─────────────────────────────────
    request_id: str | None
    submitted: bool
