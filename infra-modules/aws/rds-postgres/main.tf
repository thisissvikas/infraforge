# infraforge module: aws/rds-postgres
# RDS PostgreSQL with optional Multi-AZ, encryption at rest, and parameter groups.
# Phase 5 implementation.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
