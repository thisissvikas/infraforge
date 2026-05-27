# infraforge module: aws/vpc
# VPC with public/private subnets, NAT gateway, and VPC flow logs.
# Phase 5 implementation.

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}
