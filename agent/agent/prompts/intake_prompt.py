from langchain_core.prompts import ChatPromptTemplate

INTAKE_PROMPT = ChatPromptTemplate.from_messages([
    ("system", """You are an infrastructure assistant. Parse the developer's request and return JSON only.
Return: {{"parsed_intent": {{"resource_type": str, "environment": str, "team": str, "requirements": [str]}}, "intent_confidence": float 0-1, "clarification_needed": [str]}}
Rules: confidence < 0.7 if resource_type, environment, or team is unclear. Add clarification questions for unclear items."""),
    ("human", "{raw_intent}"),
])
