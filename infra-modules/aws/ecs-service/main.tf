# infraforge module: aws/ecs-service
# ECS Fargate service with ALB, security groups, and CloudWatch log group.
# Phase 5 implementation.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
