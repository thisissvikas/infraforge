from langchain_core.language_models import BaseChatModel


def get_llm(settings: object) -> BaseChatModel:
    if settings.use_fake_llm:  # type: ignore[attr-defined]
        from langchain_core.language_models.fake_chat_models import GenericFakeChatModel
        from langchain_core.messages import AIMessage

        responses = iter([
            AIMessage(content='{"parsed_intent": {"resource_type": "s3_bucket", "team": "platform", "environment": "dev"}, "intent_confidence": 0.9, "clarification_needed": []}'),
            AIMessage(content='Here is the Terraform:\n```hcl\n# main.tf\nresource "aws_s3_bucket" "main" {\n  bucket = "infraforge-platform-dev"\n}\n```'),
            AIMessage(content='{"validation_passed": true, "validation_errors": []}'),
            AIMessage(content="Your infrastructure plan looks good! Estimated cost: $5.00/month. Shall I proceed? (yes/no)"),
            AIMessage(content='{"request_id": "req-stub-001", "submitted": true}'),
        ])
        return GenericFakeChatModel(messages=responses)
    from langchain_aws import ChatBedrock

    return ChatBedrock(model_id=settings.bedrock_model_id, region_name=settings.aws_region)  # type: ignore[attr-defined]
