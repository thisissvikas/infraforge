variable "name"             { type = string }
variable "stage_name"       { type = string; default = "$default" }
variable "environment"      { type = string; default = "dev" }
variable "tags"             { type = map(string); default = {} }
