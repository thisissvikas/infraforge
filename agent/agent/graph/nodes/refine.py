import re
from typing import Any

from agent.graph.state import AgentState
from agent.prompts.refine_prompt import REFINE_PROMPT


async def refine_node(state: AgentState, llm: Any) -> dict[str, Any]:
    chain = REFINE_PROMPT | llm
    result = await chain.ainvoke({
        "validation_errors": "\n".join(state.get("validation_errors", [])),
        "terraform_files": str(state.get("terraform_files", {})),
    })
    content = result.content if hasattr(result, "content") else str(result)

    terraform_files: dict[str, str] = {}
    blocks = re.findall(r"#\s*(\S+\.tf)\n```(?:hcl)?\n(.*?)```", content, re.DOTALL)
    for filename, hcl in blocks:
        terraform_files[filename] = hcl.strip()
    if not terraform_files:
        terraform_files = state.get("terraform_files") or {}

    return {
        "terraform_files": terraform_files,
        "refine_attempt": (state.get("refine_attempt") or 0) + 1,
    }
