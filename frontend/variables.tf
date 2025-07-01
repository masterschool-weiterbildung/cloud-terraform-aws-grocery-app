variable "instance_name" {
  description = "name of ec2 instance"
  type        = string
}

variable "ami" {
  description = "amazon machine image to use for ec2 instance"
  type        = string
  default     = "ami-0229b8f55e5178b65"
}

variable "instance_type" {
  description = "ec2 instance type"
  type        = string
  default     = "t2.micro"
}

variable "db_user" {
  description = "username for database"
  type        = string
  default     = "grocery_user"
}

variable "db_pass" {
  description = "password for database"
  type        = string
  sensitive   = true
}
