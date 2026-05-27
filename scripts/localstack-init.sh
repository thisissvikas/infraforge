#!/bin/bash
# LocalStack initialisation — runs once after LocalStack is ready.
# Creates the AWS resources needed for local development.
set -euo pipefail

REGION="us-east-1"
ENDPOINT="http://localhost:4566"
AWS="aws --endpoint-url=$ENDPOINT --region=$REGION"

echo "==> Creating DynamoDB table: infraforge-requests"
$AWS dynamodb create-table \
  --table-name infraforge-requests \
  --attribute-definitions \
    AttributeName=requestId,AttributeType=S \
    AttributeName=userId,AttributeType=S \
    AttributeName=createdAt,AttributeType=S \
  --key-schema AttributeName=requestId,KeyType=HASH \
  --global-secondary-indexes '[
    {
      "IndexName": "userId-createdAt-index",
      "KeySchema": [
        {"AttributeName": "userId", "KeyType": "HASH"},
        {"AttributeName": "createdAt", "KeyType": "RANGE"}
      ],
      "Projection": {"ProjectionType": "ALL"}
    }
  ]' \
  --billing-mode PAY_PER_REQUEST || echo "Table may already exist"

echo "==> Creating SQS queue: infraforge-workflow"
$AWS sqs create-queue --queue-name infraforge-workflow || true

echo "==> Creating S3 bucket: infraforge-terraform-local"
$AWS s3api create-bucket --bucket infraforge-terraform-local || true

echo "==> Creating EventBridge event bus: infraforge-audit"
$AWS events create-event-bus --name infraforge-audit || true

echo "==> Seeding Secrets Manager: infraforge/jwt-secret"
$AWS secretsmanager create-secret \
  --name infraforge/jwt-secret \
  --secret-string "local-dev-secret-key-min-256-bits-long-xx" || true

echo "==> LocalStack init complete"
