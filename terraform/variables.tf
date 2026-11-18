variable "project_name" {
  type    = string
  default = "clouddevops"
}

variable "cluster_name" {
  type    = string
  default = "clouddevops-eks"
}

variable "vpc_cidr" {
  type    = string
  default = "10.0.0.0/16"
}

variable "public_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_subnet_cidrs" {
  type    = list(string)
  default = ["10.0.11.0/24", "10.0.12.0/24"]
}
variable "admin_cidr" {
  type = string
}

variable "public_key_path" {
  type    = string
  default = "~/.ssh/clouddevops-key.pub"
}

variable "jenkins_instance_type" {
  type    = string
  default = "c7i-flex.large"
}
