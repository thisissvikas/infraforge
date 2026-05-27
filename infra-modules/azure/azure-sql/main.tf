# infraforge module: azure/azure-sql
# Azure SQL Database with geo-redundant backups and private endpoint.
# Stub — Phase 5+ implementation.

terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }
}
