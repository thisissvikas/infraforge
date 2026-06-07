import pytest
from unittest.mock import AsyncMock, MagicMock

from agent.graph.nodes.submit import submit_node
from agent.tools.control_plane import SubmitResult


@pytest.mark.asyncio
async def test_submit_sends_correct_payload() -> None:
    mock_client = MagicMock()
    mock_client.submit_request = AsyncMock(
        return_value=SubmitResult(request_id="req-001", state="SUBMITTED")
    )

    state = {
        "user_id": "u-001",
        "user_email": "test@test.com",
        "team_id": "team-a",
        "raw_intent": "create S3",
        "target_cloud": "AWS",
        "generated_terraform": "resource {}",
        "messages": [],
    }
    result = await submit_node(state, client=mock_client)

    assert result["request_id"] == "req-001"
    assert result["submitted"] is True
    mock_client.submit_request.assert_called_once()
    payload = mock_client.submit_request.call_args[0][0]
    assert payload["userId"] == "u-001"
    assert payload["targetCloud"] == "AWS"
