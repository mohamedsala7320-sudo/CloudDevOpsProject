variable "cluster_name" {
  type = string
}

variable "subnet_ids" {
  type        = list(string)
  description = "All subnets of the cluster (public and private)"
}

variable "node_subnet_ids" {
  type        = list(string)
  description = "Private subnets for the worker nodes"
}

variable "node_instance_type" {
  type = string
}

variable "node_desired_size" {
  type    = number
  default = 2
}
