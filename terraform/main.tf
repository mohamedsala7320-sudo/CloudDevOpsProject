module "network" {
  source = "./modules/network"

  project_name         = var.project_name
  cluster_name         = var.cluster_name
  vpc_cidr             = var.vpc_cidr
  public_subnet_cidrs  = var.public_subnet_cidrs
  private_subnet_cidrs = var.private_subnet_cidrs
}
module "jenkins_server" {
  source = "./modules/jenkins_server"

  project_name    = var.project_name
  vpc_id          = module.network.vpc_id
  subnet_id       = module.network.public_subnet_ids[0]
  instance_type   = var.jenkins_instance_type
  admin_cidr      = var.admin_cidr
  public_key_path = var.public_key_path
}
