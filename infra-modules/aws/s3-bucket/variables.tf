variable "name"                 { type = string; description = "Bucket name" }
variable "versioning"           { type = bool;   default = true }
variable "lifecycle_days"       { type = number; default = 90; description = "Days before non-current versions expire" }
variable "environment"          { type = string; default = "dev" }
variable "tags"                 { type = map(string); default = {} }
