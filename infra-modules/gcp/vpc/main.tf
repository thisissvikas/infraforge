# infraforge module: gcp/vpc
# VPC network with subnets, Cloud Router, and Cloud NAT.
# Stub — Phase 5+ implementation.

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}
