variable "name"                 { type = string }
variable "image"                { type = string }
variable "resource_group_name"  { type = string }
variable "location"             { type = string }
variable "environment"          { type = string; default = "dev" }
