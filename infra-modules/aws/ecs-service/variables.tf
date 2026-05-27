variable "name"             { type = string; description = "Service name" }
variable "image"            { type = string; description = "Container image URI" }
variable "cpu"              { type = number; default = 256; description = "Task CPU units" }
variable "memory"           { type = number; default = 512; description = "Task memory MiB" }
variable "desired_count"    { type = number; default = 1 }
variable "vpc_id"           { type = string }
variable "subnet_ids"       { type = list(string) }
variable "environment"      { type = string; default = "dev"; description = "dev | staging | prod" }
variable "tags"             { type = map(string); default = {} }
