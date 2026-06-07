import asyncio
import json
from typing import Any

from agent.graph.state import AgentState
from agent.tools.control_plane import ControlPlaneClient
from agent.tools.terraform_lint import run_checkov, run_tfsec


async def validate_node(state: AgentState, client: ControlPlaneClient) -> dict[str, Any]:
    tf_files = state.get("terraform_files") or {}

    tfsec_violations, checkov_violations, opa_result = await asyncio.gather(
        run_tfsec(tf_files),
        run_checkov(tf_files),
        client.validate_architecture(json.dumps(tf_files)),
        return_exceptions=True,
    )

    errors: list[str] = []
    if not isinstance(tfsec_violations, Exception):
        errors.extend(f"tfsec: {v.rule} — {v.message}" for v in tfsec_violations)
    if not isinstance(checkov_violations, Exception):
        errors.extend(f"checkov: {v.rule} — {v.message}" for v in checkov_violations)
    if not isinstance(opa_result, Exception) and not opa_result.passed:
        errors.extend(f"opa: {v}" for v in opa_result.violations)

    return {"validation_errors": errors, "validation_passed": len(errors) == 0}
