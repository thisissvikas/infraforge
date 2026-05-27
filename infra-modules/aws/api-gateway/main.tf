# infraforge module: aws/api-gateway
# API Gateway HTTP API with optional Lambda integration and custom domain.
# Phase 5 implementation.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
