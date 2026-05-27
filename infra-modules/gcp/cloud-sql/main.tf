# infraforge module: gcp/cloud-sql
# Cloud SQL (PostgreSQL) with private IP, automated backups, and deletion protection.
# Stub — Phase 5+ implementation.

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}
