import re
from typing import Any

from agent.graph.state import AgentState
from agent.prompts.generate_prompt import GENERATE_PROMPT


async def generate_node(state: AgentState, llm: Any) -> dict[str, Any]:
    chain = GENERATE_PROMPT | llm
    result = await chain.ainvoke({
        "parsed_intent": str(state.get("parsed_intent", {})),
        "policies": "\n".join(state.get("retrieved_policies", [])),
        "approved_modules": ", ".join(state.get("approved_modules", [])),
    })
    content = result.content if hasattr(result, "content") else str(result)

    terraform_files: dict[str, str] = {}
    blocks = re.findall(r"#\s*(\S+\.tf)\n```(?:hcl)?\n(.*?)```", content, re.DOTALL)
    for filename, hcl in blocks:
        terraform_files[filename] = hcl.strip()

    if not terraform_files:
        terraform_files["main.tf"] = content  # fallback

    combined = "\n\n".join(f"# {k}\n{v}" for k, v in terraform_files.items())
    return {"generated_terraform": combined, "terraform_files": terraform_files}
