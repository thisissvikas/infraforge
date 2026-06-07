from langchain_core.prompts import ChatPromptTemplate

GENERATE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """You are a Terraform expert. Generate Terraform HCL for the developer's request.
Use ONLY modules from the approved list. Follow all policy constraints.
Return HCL code blocks with filename comments like: # main.tf
Policies: {policies}
Approved modules: {approved_modules}"""),
    ("human", "Generate Terraform for: {parsed_intent}"),
])
