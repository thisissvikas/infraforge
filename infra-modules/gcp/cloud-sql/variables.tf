variable "name"             { type = string }
variable "database_version" { type = string; default = "POSTGRES_16" }
variable "tier"             { type = string; default = "db-f1-micro" }
variable "region"           { type = string }
variable "project"          { type = string }
variable "environment"      { type = string; default = "dev" }
