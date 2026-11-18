variable "project_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_id" {
  type = string
}

variable "instance_type" {
  type = string
}

variable "admin_cidr" {
  type        = string
  description = "Your public IP in CIDR form, allowed to reach SSH and Jenkins"
}

variable "public_key_path" {
  type = string
}
