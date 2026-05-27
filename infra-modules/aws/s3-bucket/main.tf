# infraforge module: aws/s3-bucket
# S3 bucket with encryption, versioning, lifecycle rules, and public-access-block.
# Phase 5 implementation.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
