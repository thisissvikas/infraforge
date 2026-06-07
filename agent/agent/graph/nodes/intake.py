import json
import re
from typing import Any

from langchain_core.messages import HumanMessage

from agent.graph.state import AgentState
from agent.prompts.intake_prompt import INTAKE_PROMPT


async def intake_node(state: AgentState, llm: Any) -> dict[str, Any]:
    raw = state.get("raw_intent") or ""
    if not raw:
        msgs = state.get("messages", [])
        raw = msgs[-1].content if msgs and hasattr(msgs[-1], "content") else ""

    chain = INTAKE_PROMPT | llm
    result = await chain.ainvoke({"raw_intent": raw})
    content = result.content if hasattr(result, "content") else str(result)

    try:
        match = re.search(r"\{.*\}", content, re.DOTALL)
        data = json.loads(match.group() if match else content)
        return {
            "parsed_intent": data.get("parsed_intent", {}),
            "intent_confidence": float(data.get("intent_confidence", 0.9)),
            "clarification_needed": data.get("clarification_needed", []),
            "raw_intent": raw,
        }
    except Exception:
        return {
            "parsed_intent": {},
            "intent_confidence": 0.0,
            "clarification_needed": ["Could you clarify your request?"],
        }
