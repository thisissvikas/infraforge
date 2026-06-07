from langchain_core.prompts import ChatPromptTemplate

REFINE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """You are a Terraform expert fixing validation errors. Fix the provided Terraform to resolve all violations.
Return complete corrected HCL with filename comments. Do not introduce new violations."""),
    ("human", "Fix these errors:\n{validation_errors}\n\nCurrent Terraform:\n{terraform_files}"),
])
