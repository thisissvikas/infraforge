variable "name"             { type = string }
variable "region"           { type = string }
variable "project"          { type = string }
variable "subnet_cidr"      { type = string; default = "10.0.0.0/20" }
variable "environment"      { type = string; default = "dev" }
