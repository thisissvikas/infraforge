import uuid

from fastapi import FastAPI, Header
from langchain_core.messages import HumanMessage
from pydantic import BaseModel

from agent.graph.graph import graph
from agent.graph.state import AgentState

app = FastAPI(title="infraforge-agent")


class ChatRequest(BaseModel):
    session_id: str | None = None
    message: str
    user_id: str = "local-user"
    user_email: str = "dev@local"
    team_id: str = "default"
    target_cloud: str = "AWS"


class ChatResponse(BaseModel):
    session_id: str
    message: str
    request_id: str | None = None


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
async def chat(
    req: ChatRequest,
    authorization: str | None = Header(default=None),
) -> ChatResponse:
    session_id = req.session_id or str(uuid.uuid4())
    thread_config = {"configurable": {"thread_id": session_id}}

    # Resume or start graph
    current = graph.get_state(thread_config)
    if current and current.values:
        # Resuming interrupted graph (confirm step)
        state_update: dict = {
            "messages": [HumanMessage(content=req.message)],
            "clarification_needed": [],  # clear so after_confirm can re-evaluate
        }
        graph.update_state(thread_config, state_update)
        result = await graph.ainvoke(None, thread_config)
    else:
        # New or continuing conversation
        initial: AgentState = {
            "messages": [HumanMessage(content=req.message)],
            "session_id": session_id,
            "user_id": req.user_id,
            "user_email": req.user_email,
            "team_id": req.team_id,
            "raw_intent": req.message,
            "target_cloud": req.target_cloud,
            "parsed_intent": {},
            "intent_confidence": 0.0,
            "clarification_needed": [],
            "retrieved_policies": [],
            "team_budget_usd": 1000.0,
            "approved_modules": [],
            "generated_terraform": None,
            "terraform_files": {},
            "validation_errors": [],
            "validation_passed": False,
            "refine_attempt": 0,
            "estimated_monthly_cost_usd": 0.0,
            "cost_within_budget": True,
            "request_id": None,
            "submitted": False,
        }
        result = await graph.ainvoke(initial, thread_config)

    # Extract last assistant message
    messages = result.get("messages", []) if isinstance(result, dict) else []
    ai_messages = [m for m in messages if hasattr(m, "type") and m.type == "ai"]
    last_message = ai_messages[-1].content if ai_messages else "I'm processing your request."

    return ChatResponse(
        session_id=session_id,
        message=last_message,
        request_id=result.get("request_id") if isinstance(result, dict) else None,
    )
