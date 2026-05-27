variable "name"             { type = string; description = "DB identifier" }
variable "instance_class"   { type = string; default = "db.t3.micro" }
variable "engine_version"   { type = string; default = "16" }
variable "allocated_storage"{ type = number; default = 20 }
variable "multi_az"         { type = bool;   default = false }
variable "vpc_id"           { type = string }
variable "subnet_ids"       { type = list(string) }
variable "environment"      { type = string; default = "dev" }
variable "tags"             { type = map(string); default = {} }
