# infraforge module: gcp/cloud-run
# Cloud Run service with optional VPC connector and IAM bindings.
# Stub — Phase 5+ implementation.

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}
