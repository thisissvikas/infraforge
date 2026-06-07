from agent.ports.policy_store_port import PolicyContext, PolicyStorePort


class StubPolicyStoreAdapter(PolicyStorePort):
    """STUB — replaced by BedrockKbAdapter in Phase 5."""

    async def retrieve(self, team_id: str, intent_summary: str) -> PolicyContext:
        return PolicyContext(
            chunks=[
                "All S3 buckets must have versioning enabled and public access blocked.",
                "ECS services must run in private subnets with no public IP assigned.",
            ],
            approved_modules=["aws/ecs-service", "aws/rds-postgres", "aws/vpc", "aws/s3-bucket"],
            budget_ceiling_usd=500.0,
        )
