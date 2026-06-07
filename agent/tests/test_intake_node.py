import pytest
from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
from langchain_core.messages import AIMessage as _AIMessage
from langchain_core.messages import HumanMessage

from agent.graph.nodes.intake import intake_node


@pytest.mark.asyncio
async def test_high_confidence_returns_parsed_intent() -> None:
    fake_llm = GenericFakeChatModel(messages=iter([
        _AIMessage(content='{"parsed_intent": {"resource_type": "s3"}, "intent_confidence": 0.9, "clarification_needed": []}')
    ]))
    state = {
        "raw_intent": "create an S3 bucket",
        "messages": [HumanMessage(content="create an S3 bucket")],
    }
    result = await intake_node(state, llm=fake_llm)
    assert result["intent_confidence"] >= 0.7
    assert result["parsed_intent"]["resource_type"] == "s3"


@pytest.mark.asyncio
async def test_low_confidence_sets_clarification() -> None:
    fake_llm = GenericFakeChatModel(messages=iter([
        _AIMessage(content='{"parsed_intent": {}, "intent_confidence": 0.4, "clarification_needed": ["What environment?"]}')
    ]))
    state = {"raw_intent": "something", "messages": []}
    result = await intake_node(state, llm=fake_llm)
    assert result["intent_confidence"] < 0.7
    assert len(result["clarification_needed"]) > 0
