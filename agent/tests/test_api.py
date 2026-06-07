import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from fastapi.testclient import TestClient
from langchain_core.messages import AIMessage


def test_health() -> None:
    from agent.api.main import app

    client = TestClient(app)
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


def test_chat_returns_response() -> None:
    from agent.api.main import app

    test_client = TestClient(app)
    mock_result = {
        "messages": [AIMessage(content="I can help with that!")],
        "request_id": None,
        "submitted": False,
    }
    with patch("agent.api.main.graph") as mock_graph:
        mock_graph.get_state.return_value = None
        mock_graph.ainvoke = AsyncMock(return_value=mock_result)
        resp = test_client.post(
            "/chat",
            json={"message": "create an S3 bucket", "session_id": "test-session"},
        )
    assert resp.status_code == 200
    data = resp.json()
    assert "message" in data
    assert "session_id" in data
