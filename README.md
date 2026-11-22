# CloudDevOpsProject

End-to-end DevOps project built as the final project of the NTI / iVolve DevOps training.
A microservices web application (registration, login and a DevOps roadmap page) is containerized with Docker, provisioned on AWS with Terraform, configured with Ansible, built by Jenkins and deployed to Kubernetes (EKS) with ArgoCD.

Repository: https://github.com/mohamedsala7320-sudo/CloudDevOpsProject

## Project status

- [x] 1. GitHub repository
- [x] 2. Containerization with Docker Compose
- [x] 3. Infrastructure provisioning with Terraform
- [x] 4. Configuration management with Ansible
- [x] 5. Container orchestration with Kubernetes
- [x] 6. Continuous integration with Jenkins
- [x] 7. Continuous deployment with ArgoCD
- [x] 8. Documentation (this README)

## Table of contents

1. [Deliverables checklist](#deliverables-checklist)
2. [Architecture overview](#architecture-overview)
3. [Repository structure](#repository-structure)
4. [Setup instructions (from zero to running)](#setup-instructions-from-zero-to-running)
5. [Part 1: GitHub repository](#part-1-github-repository)
6. [Part 2: Containerization with Docker Compose](#part-2-containerization-with-docker-compose)
7. [Part 3: Infrastructure provisioning with Terraform](#part-3-infrastructure-provisioning-with-terraform)
8. [Part 4: Configuration management with Ansible](#part-4-configuration-management-with-ansible)
9. [Part 5: Container orchestration with Kubernetes](#part-5-container-orchestration-with-kubernetes)
10. [Part 6: Continuous integration with Jenkins](#part-6-continuous-integration-with-jenkins)
11. [Part 7: Continuous deployment with ArgoCD](#part-7-continuous-deployment-with-argocd)
12. [Troubleshooting](#troubleshooting)
13. [Cost control and cleanup](#cost-control-and-cleanup)
14. [Known limitations and improvements](#known-limitations-and-improvements)

## Deliverables checklist

| # | Requirement | Where |
|---|---|---|
| 1 | GitHub repository | This repository |
| 2 | `docker-compose.yml` | [`docker-compose.yml`](docker-compose.yml) |
| 3 | Terraform modules (network, server, EKS, ECR) + S3 backend | [`terraform/`](terraform) |
| 4 | Ansible inventory, playbook and roles (Java, Jenkins, Docker, Trivy) | [`ansible/`](ansible) |
| 5 | Kubernetes YAML files | [`k8s/`](k8s) |
| 6 | Jenkinsfiles and shared library | `*/Jenkinsfile` and [`vars/`](vars) |
| 7 | ArgoCD Application | [`argocd/application.yaml`](argocd/application.yaml) |
| 8 | Documentation | This README |

## Architecture overview

### Application architecture

```text
Browser
   |
   v
Frontend (Node.js, :3000)
   |-----------------------> Auth Service (Python/Flask, :5000) ---> MySQL (:3306)
   |
   '-----------------------> Roadmap Service (Java, :8080)
```

The frontend is the only entry point. Only the Auth Service talks to the database.

| Service | Technology | Port | Responsibility |
|---|---|---|---|
| frontend | Node.js 22, Express, EJS | 3000 | Web UI, user session, calls the backend services |
| auth-service | Python 3.12, Flask, gunicorn, bcrypt | 5000 | Signup and login, stores users in MySQL |
| roadmap-service | Java 21, Maven, Spring Boot | 8080 | Serves the DevOps roadmap data |
| mysql | MySQL 8.4 | 3306 | `users` table (created automatically by the auth service) |

> **Note:** the upstream application README lists the Auth Service as Java and the Roadmap Service as Python.
> The source code shows the opposite, so this documentation follows the code.

### AWS infrastructure (Terraform)

```text
                               Internet
                                  |
                          Internet Gateway
                                  |
  VPC 10.0.0.0/16 ------------------------------------------------------
  |   AZ eu-north-1a                       AZ eu-north-1b              |
  |                                                                    |
  |   Public subnet 10.0.1.0/24            Public subnet 10.0.2.0/24   |
  |     - NAT Gateway                        - (load balancers)        |
  |     - Jenkins EC2 (Ubuntu 24.04)                                   |
  |                                                                    |
  |   Private subnet 10.0.11.0/24          Private subnet 10.0.12.0/24 |
  |     - EKS worker node                    - EKS worker node         |
  ----------------------------------------------------------------------
        EKS control plane (managed by AWS)      ECR (3 repositories)
```

### CI/CD flow

```text
Developer --> GitHub (source + k8s manifests)
                 |
                 |  Jenkins (EC2, configured by Ansible) runs one pipeline per service:
                 |  Build Image -> Scan Image (Trivy) -> Push Image (ECR)
                 |  -> Delete Image Locally -> Update Manifests -> Push Manifests
                 v
              GitHub (k8s/base updated with the new image tag, commit "ci: <service> -> <build>")
                 |
                 v
              ArgoCD (in EKS) detects the commit and syncs k8s/base into the `ivolve` namespace
                 |
                 v
              Traefik ingress (AWS load balancer) --> frontend --> auth-service / roadmap-service --> MySQL
```

Git is the single source of truth: Jenkins never talks to the cluster, it only changes the manifests. ArgoCD applies them.

### Endpoints used by the frontend

| Service | Method | Path |
|---|---|---|
| auth-service | POST | `/api/auth/signup` |
| auth-service | POST | `/api/auth/login` |
| auth-service | GET | `/health` |
| roadmap-service | GET | `/api/roadmap` |

### Environment variables

| Service | Variable | Description |
|---|---|---|
| auth-service | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | MySQL connection (all required except `DB_PORT`, default 3306) |
| frontend | `AUTH_SERVICE_URL` | URL of the Auth Service |
| frontend | `ROADMAP_SERVICE_URL` | URL of the Roadmap Service |
| frontend | `SESSION_SECRET` | Secret used to sign the session cookie |

## Repository structure

```text
.
├── auth-service/          # Flask app + Dockerfile + Jenkinsfile
├── frontend/              # Express app + Dockerfile + Jenkinsfile
├── roadmap-service/       # Java app + Dockerfile + Jenkinsfile
├── vars/
│   └── ivolvePipeline.groovy   # Jenkins shared library (the pipeline logic)
├── ansible/
│   ├── ansible.cfg
│   ├── inventory/aws_ec2.yml   # dynamic inventory
│   ├── playbook.yml
│   ├── requirements.yml
│   └── roles/             # java, jenkins, docker, trivy
├── k8s/
│   ├── base/              # namespace, config, database, services, ingress (00 to 08)
│   ├── ingress/           # Traefik Helm values (local cluster)
│   ├── storage/           # StorageClass for the local cluster
│   └── storage-eks/       # StorageClass (EBS gp3) for EKS
├── argocd/
│   └── application.yaml   # ArgoCD Application
├── terraform/
│   ├── backend.tf         # S3 backend + AWS provider
│   ├── main.tf            # calls the modules
│   ├── variables.tf
│   ├── outputs.tf
│   └── modules/
│       ├── network/       # VPC, subnets, IGW, NAT, route tables, NACLs
│       ├── jenkins_server/# EC2 + security group + key pair
│       ├── eks/           # EKS cluster, node group, EBS CSI driver
│       └── ecr/           # container registries
├── docs/screenshots/      # Screenshots used in this README
├── docker-compose.yml
├── .env.example
└── README.md
```

## Setup instructions (from zero to running)

**Tools:** Docker and Docker Compose, Terraform 1.10 or newer, AWS CLI v2, Ansible, kubectl, Helm, Git, an SSH key pair (`~/.ssh/clouddevops-key`). Developed on Ubuntu 24.04 under WSL2.

| Step | What | Details |
|---|---|---|
| 1 | Run the app on one machine (optional) | [Part 2](#part-2-containerization-with-docker-compose) |
| 2 | Create the AWS infrastructure | [Part 3](#part-3-infrastructure-provisioning-with-terraform) |
| 3 | Configure the Jenkins server | [Part 4](#part-4-configuration-management-with-ansible) |
| 4 | Configure Jenkins and run the 3 pipelines | [Part 6](#part-6-continuous-integration-with-jenkins) |
| 5 | Install the StorageClass, Traefik and ArgoCD on EKS | [Part 7](#part-7-continuous-deployment-with-argocd) |

Short version of the full flow:

```bash
# 1. Infrastructure (the state is stored in S3)
cd terraform
terraform init
terraform apply -var="admin_cidr=$(curl -s https://checkip.amazonaws.com)/32"
aws eks update-kubeconfig --region eu-north-1 --name clouddevops-eks --kubeconfig ~/.kube/eks-config
export KUBECONFIG=~/.kube/eks-config

# 2. Jenkins server (dynamic inventory finds the EC2 by its tags)
cd ../ansible
ansible-galaxy install -r requirements.yml
ansible-playbook playbook.yml

# 3. Install AWS CLI v2 on the Jenkins server (see Known limitations), then in the Jenkins UI:
#    credentials -> shared library -> 3 pipeline jobs -> Build Now

# 4. Cluster add-ons and GitOps
cd ..
kubectl apply -f k8s/storage-eks/ivolve-mysql-sc.yaml
helm repo add traefik https://traefik.github.io/charts && helm repo update
helm install traefik traefik/traefik -n traefik --create-namespace
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl apply -f argocd/application.yaml
```

Open the application at the hostname of the Traefik load balancer:

```bash
kubectl get svc traefik -n traefik -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'; echo
```

> Jenkins pushes commits to `main` (`ci: <service> -> <build number>`), so always run `git pull` before you push your own changes.

## Part 1: GitHub repository

The repository `CloudDevOpsProject` is created on GitHub and initialized with a README. All the work of the following parts is committed to the `main` branch.

URL: https://github.com/mohamedsala7320-sudo/CloudDevOpsProject

![GitHub repository](docs/screenshots/01-github-repo.png)

## Part 2: Containerization with Docker Compose

The source code comes from https://github.com/IbrahimAdel15/iVolveFinalProject.git. Each service has its own Dockerfile and `docker-compose.yml` starts all services with the database.

### Run locally

```bash
git clone https://github.com/mohamedsala7320-sudo/CloudDevOpsProject.git
cd CloudDevOpsProject
cp .env.example .env      # then set your own values in .env
docker compose up --build -d
docker compose ps
```

Open http://localhost:3000, create an account and log in.

The `.env` file is ignored by Git. Only `.env.example` is committed.

![docker compose ps](docs/screenshots/02-docker-compose-ps.png)
![Signup page](docs/screenshots/03-app-signup-page.png)
![Roadmap page](docs/screenshots/04-app-roadmap-page.png)

### Verify the data in MySQL

Passwords are stored as bcrypt hashes, never as plain text.

```bash
docker compose exec mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE" -e "SELECT id, username, password_hash FROM users;"'
```

![Users table](docs/screenshots/05-mysql-users-table.png)

### Data persistence

User data lives in the `mysql-data` volume and survives `docker compose down`.

![Data persistence](docs/screenshots/06-data-persistence.png)

### Design decisions

| Decision | Reason |
|---|---|
| gunicorn instead of the Flask development server | The development server is not meant for production |
| Non-root user in every image | Limits the impact of a container compromise |
| Multi-stage build for the Java service | The final image has only a JRE and the jar, so it is smaller and has fewer vulnerabilities |
| `.dockerignore` for each service | Faster builds and no local files copied into images |
| Two networks (`app-net`, `db-net`) | The frontend cannot reach the database directly |
| Healthchecks with `depends_on: service_healthy` | Services start in the right order |
| Secrets in `.env` with `:?` checks | Compose stops early with a clear message if a variable is missing |

## Part 3: Infrastructure provisioning with Terraform

All AWS resources are created with Terraform in `eu-north-1` (Stockholm), split into four modules and stored in a remote S3 state.

| Module | Path | What it creates |
|---|---|---|
| Network | `terraform/modules/network` | VPC, 2 public and 2 private subnets in 2 AZs, Internet Gateway, NAT Gateway, route tables, Network ACLs |
| Server | `terraform/modules/jenkins_server` | EC2 instance for Jenkins, security group, SSH key pair |
| EKS | `terraform/modules/eks` | EKS cluster, managed node group (2 nodes), IAM roles, EBS CSI driver |
| ECR | `terraform/modules/ecr` | 3 private repositories (one per microservice) |

### 1. AWS preparation

- IAM user `terraform-admin` with the `AdministratorAccess` policy and a CLI access key (no console access, so no password). The access key is stored only in `~/.aws/credentials` and is never committed.
- AWS CLI configured for `eu-north-1`.
- A zero-spend budget with an email alert.

```bash
aws configure                      # region: eu-north-1, output: json
aws sts get-caller-identity        # must show user/terraform-admin
```

![IAM user](docs/screenshots/19-iam-user-terraform-admin.png)
![AWS CLI identity](docs/screenshots/20-aws-cli-identity.png)
![AWS budget](docs/screenshots/22-aws-budget.png)

### 2. Remote state in S3

The bucket cannot be created by the same code that uses it, so it is created once with the CLI. It has versioning (to roll back a bad state), default encryption and all public access blocked.

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET=clouddevops-tfstate-$ACCOUNT_ID

aws s3api create-bucket --bucket $BUCKET --region eu-north-1 \
  --create-bucket-configuration LocationConstraint=eu-north-1
aws s3api put-bucket-versioning --bucket $BUCKET \
  --versioning-configuration Status=Enabled
aws s3api put-bucket-encryption --bucket $BUCKET \
  --server-side-encryption-configuration '{"Rules":[{"ApplyServerSideEncryptionByDefault":{"SSEAlgorithm":"AES256"}}]}'
aws s3api put-public-access-block --bucket $BUCKET \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true
```

`terraform/backend.tf` points to this bucket. State locking uses the native S3 lock file (`use_lockfile = true`, Terraform 1.10 or newer), so no DynamoDB table is needed.

```hcl
backend "s3" {
  bucket       = "clouddevops-tfstate-<ACCOUNT_ID>"
  key          = "clouddevops/terraform.tfstate"
  region       = "eu-north-1"
  encrypt      = true
  use_lockfile = true
}
```

![S3 state bucket](docs/screenshots/21-s3-state-bucket.png)
![terraform init](docs/screenshots/23-terraform-init-backend.png)

### 3. Network module

| Resource | Value |
|---|---|
| VPC | `10.0.0.0/16` with DNS support and hostnames |
| Public subnets | `10.0.1.0/24` (AZ a), `10.0.2.0/24` (AZ b) |
| Private subnets | `10.0.11.0/24` (AZ a), `10.0.12.0/24` (AZ b) |
| Internet Gateway | Default route of the public route table |
| NAT Gateway | One NAT in the first public subnet, default route of the private route table |

Subnets carry the tags EKS needs to place load balancers (`kubernetes.io/role/elb` on public, `kubernetes.io/role/internal-elb` on private).

Network ACLs (stateless, so return traffic on ephemeral ports must be allowed):

| NACL | Inbound | Outbound |
|---|---|---|
| Public | TCP 80, 443, 22, 8080 from anywhere, TCP 1024-65535 (return traffic), all traffic from the VPC | All |
| Private | All traffic from the VPC, TCP 1024-65535 (return traffic from the NAT) | All |

The NACLs are the coarse subnet-level layer. Fine-grained access is enforced by security groups (see the server module).

![Network plan](docs/screenshots/24-terraform-network-plan.png)
![Network apply](docs/screenshots/25-terraform-network-apply.png)
![VPC resource map](docs/screenshots/26-vpc-resource-map.png)
![Network destroy](docs/screenshots/27-terraform-network-destroy.png)

### 4. Server module (Jenkins EC2)

| Setting | Value |
|---|---|
| AMI | Latest Ubuntu 24.04 (Canonical), found with a `data` source |
| Instance type | `c7i-flex.large` (4 GB RAM, one of the types allowed on the AWS Free plan) |
| Subnet | First public subnet, with a public IP |
| Disk | 30 GB gp3 |
| Security group | SSH (22) and Jenkins UI (8080) only from `admin_cidr` (your IP), all outbound |
| Metadata | IMDSv2 required |
| Tags | `Name=clouddevops-jenkins`, `Role=jenkins` (used by the Ansible dynamic inventory) |

`admin_cidr` has no default. Pass your current public IP every time you apply, because it changes between sessions:

```bash
terraform apply -var="admin_cidr=$(curl -s https://checkip.amazonaws.com)/32"
```

![Jenkins plan](docs/screenshots/28-terraform-jenkins-plan.png)
![Jenkins apply](docs/screenshots/29-terraform-jenkins-apply.png)
![SSH to the Jenkins server](docs/screenshots/30-ssh-jenkins-server.png)
![EC2 instance](docs/screenshots/31-ec2-jenkins-instance.png)
![Jenkins destroy](docs/screenshots/32-terraform-jenkins-destroy.png)

### 5. EKS and ECR modules

**EKS**

| Setting | Value |
|---|---|
| Cluster | Public and private API endpoint, creator gets admin through an EKS access entry |
| Subnets | Both public and private, so load balancers can use the public ones |
| Node group | 2 managed nodes (`c7i-flex.large`, Amazon Linux 2023, 20 GB), placed in the 2 private subnets, so each node is in a different AZ |
| Node IAM role | `AmazonEKSWorkerNodePolicy`, `AmazonEKS_CNI_Policy`, `AmazonEC2ContainerRegistryReadOnly` |
| Add-ons | `eks-pod-identity-agent` and `aws-ebs-csi-driver` (needed for the MySQL volume) |

**ECR**

Three private repositories: `ivolve-auth-service`, `ivolve-roadmap-service`, `ivolve-frontend`. Images are scanned on push, and `force_delete` lets `terraform destroy` remove repositories that still hold images.

```bash
aws eks update-kubeconfig --region eu-north-1 --name clouddevops-eks --kubeconfig ~/.kube/eks-config
export KUBECONFIG=~/.kube/eks-config
kubectl get nodes -o wide
kubectl get pods -n kube-system
```

![EKS and ECR plan](docs/screenshots/33-terraform-eks-ecr-plan.png)
![EKS apply](docs/screenshots/34-terraform-eks-apply.png)
![kubectl get nodes](docs/screenshots/35-kubectl-get-nodes.png)
![EKS cluster](docs/screenshots/36-eks-cluster-console.png)
![ECR repositories](docs/screenshots/37-ecr-repositories.png)

### Variables

| Variable | Default | Description |
|---|---|---|
| `admin_cidr` | none (required) | Your public IP in CIDR form, allowed to reach SSH and Jenkins |
| `project_name` | `clouddevops` | Prefix for resource names |
| `cluster_name` | `clouddevops-eks` | EKS cluster name |
| `vpc_cidr` | `10.0.0.0/16` | VPC range |
| `public_subnet_cidrs` | `10.0.1.0/24`, `10.0.2.0/24` | Public subnets |
| `private_subnet_cidrs` | `10.0.11.0/24`, `10.0.12.0/24` | Private subnets |
| `public_key_path` | `~/.ssh/clouddevops-key.pub` | SSH public key for the Jenkins server |
| `jenkins_instance_type` | `c7i-flex.large` | Jenkins server size |
| `eks_node_instance_type` | `c7i-flex.large` | EKS node size |

Outputs: `jenkins_public_ip`, `eks_cluster_name`, `ecr_repository_urls`, `vpc_id`, `public_subnet_ids`, `private_subnet_ids`.

State files, `.terraform/` and `*.tfvars` are listed in `.gitignore`. The provider lock file (`.terraform.lock.hcl`) is committed.

### Design decisions

| Decision | Reason |
|---|---|
| One module per component, called from the root | Each part can be reused and changed on its own |
| S3 backend with versioning, encryption and native locking | Safe shared state without extra services |
| One NAT Gateway | Cheaper than one per AZ. It is a single point of failure, which is acceptable for a project environment |
| Worker nodes in private subnets only | Nodes are not reachable from the internet |
| Security group limited to the admin IP | Jenkins and SSH are not exposed to the whole internet |
| EKS Pod Identity for the EBS CSI driver | The driver gets its own IAM role instead of using the node role |
| No community modules | Every resource is written explicitly, which makes the design easy to explain |

## Part 4: Configuration management with Ansible

Ansible configures the Jenkins EC2 instance created by Terraform. Everything is in the `ansible/` folder.

```text
ansible/
├── ansible.cfg            # inventory path, roles path, SSH user and key
├── inventory/aws_ec2.yml  # dynamic inventory (aws_ec2 plugin)
├── playbook.yml           # applies the 4 roles to the Jenkins group
├── requirements.yml       # collections needed by the dynamic inventory
└── roles/
    ├── java/              # OpenJDK 21 + fontconfig
    ├── jenkins/           # Jenkins apt repository, install, enable and start
    ├── docker/            # Docker Engine, adds the ubuntu and jenkins users to the docker group
    └── trivy/             # Trivy apt repository and install
```

| Topic | How it is done |
|---|---|
| Dynamic inventory | The `aws_ec2` plugin queries AWS for running instances and groups them by the `Role` tag, so the Jenkins server is found automatically in the group `role_jenkins`. No IP is written in any file, which matters because the IP changes after every rebuild |
| Roles | One role per tool (Java, Jenkins, Docker, Trivy), so each one can be reused and tested on its own |
| Connection | SSH user `ubuntu` with the key `~/.ssh/clouddevops-key`, privilege escalation with `sudo` (see `ansible.cfg`) |
| Idempotent | Running the playbook again changes nothing when the server is already configured |
| Jenkins start | Jenkins is restarted by a handler, and the playbook prints the initial admin password for the first login |

```bash
cd ansible
ansible-galaxy install -r requirements.yml
ansible-inventory --graph          # the Jenkins server must appear under role_jenkins
ansible role_jenkins -m ping
ansible-playbook playbook.yml
```

![Ansible installed](docs/screenshots/38-ansible-installed.png)
![Ansible structure](docs/screenshots/39-ansible-structure.png)
![Dynamic inventory](docs/screenshots/40-ansible-dynamic-inventory.png)
![Ansible ping](docs/screenshots/41-ansible-ping.png)
![Playbook recap](docs/screenshots/42-ansible-playbook-recap.png)

Versions of the installed tools on the server:

![Tools versions](docs/screenshots/43-ansible-tools-versions.png)

## Part 5: Container orchestration with Kubernetes

The application runs on Kubernetes in the `ivolve` namespace. It was first tested on a local kubeadm cluster (1 control plane + 2 workers) and then deployed to EKS, where ArgoCD manages it (Part 7).

### Manifests

| File | Purpose |
|---|---|
| `k8s/base/00-namespace.yaml` | `ivolve` namespace |
| `k8s/base/01-configmap.yaml` | Non-secret environment variables |
| `k8s/base/02-secret.yaml` | Passwords and session secret |
| `k8s/base/03-mysql-service.yaml` | Headless Service for the database |
| `k8s/base/04-mysql-statefulset.yaml` | MySQL StatefulSet with a PVC per pod |
| `k8s/base/05-auth-service.yaml` | Deployment (2 replicas) + Service |
| `k8s/base/06-roadmap-service.yaml` | Deployment (2 replicas) + Service |
| `k8s/base/07-frontend.yaml` | Deployment (2 replicas) + Service |
| `k8s/base/08-ingress.yaml` | Ingress for the frontend (class `traefik`) |
| `k8s/storage/vms-local-path.yaml` | StorageClass `ivolve-mysql-sc` for the local cluster |
| `k8s/storage-eks/ivolve-mysql-sc.yaml` | StorageClass `ivolve-mysql-sc` backed by EBS gp3 for EKS |
| `k8s/ingress/traefik-values-vms.yaml` | Helm values for Traefik on the local cluster |

### Design decisions

| Decision | Reason |
|---|---|
| StatefulSet + headless Service for MySQL | Stable pod name (`mysql-0.mysql`) and its own volume |
| StorageClass `ivolve-mysql-sc` | Same name on both clusters. Local cluster: `Retain`. EKS: EBS gp3 with `WaitForFirstConsumer` (the volume is created in the AZ of the pod), `Delete` and volume expansion |
| Explicit env variables per service | Each service only receives the keys it needs |
| Readiness on `/health` (auth), `/api/roadmap` (roadmap), `/` (frontend) | Traffic only reaches ready pods |
| Liveness on TCP only | A database outage does not restart the auth pods |
| Resource requests and limits, no privilege escalation, all capabilities dropped | Safer and predictable scheduling |
| Traefik instead of ingress-nginx | The community ingress-nginx controller was retired in March 2026 |
| Sticky-session cookie (`ivolve_affinity`) on the frontend Service | The frontend keeps sessions in memory, so each user must stay on the same pod when running 2 replicas |
| Secret committed with lab-only values | Needed for GitOps. Production should use Sealed Secrets or External Secrets |

### Deploy on the local cluster

```bash
kubectl apply -f k8s/storage/vms-local-path.yaml
helm repo add traefik https://traefik.github.io/charts && helm repo update
helm upgrade --install traefik traefik/traefik -n traefik --create-namespace -f k8s/ingress/traefik-values-vms.yaml
kubectl apply -f k8s/base/
kubectl get pods,svc,ingress -n ivolve
```

The application is available on `http://<node-ip>:30080`.

### Evidence (local cluster)

Cluster nodes and Docker Hub images:

![Nodes](docs/screenshots/07-k8s-cluster-nodes.png)
![Docker Hub](docs/screenshots/08-dockerhub-images.png)

Namespace, ConfigMap and Secret:

![Config](docs/screenshots/09-k8s-namespace-config.png)

MySQL StatefulSet, DNS and data persistence after deleting the pod:

![StatefulSet](docs/screenshots/10-mysql-statefulset.png)
![DNS and database](docs/screenshots/11-mysql-dns-and-database.png)
![Persistence](docs/screenshots/12-mysql-data-persistence.png)

Deployments, Services and the running application:

![Deployments](docs/screenshots/13-k8s-deployments-services.png)
![Roadmap page](docs/screenshots/14-k8s-app-roadmap-page.png)

Ingress with Traefik, two frontend replicas and the sticky cookie:

![Traefik](docs/screenshots/15-traefik-installed.png)
![Ingress](docs/screenshots/16-app-via-ingress.png)
![Two replicas](docs/screenshots/17-frontend-2-replicas.png)
![Sticky cookie](docs/screenshots/18-sticky-cookie.png)

## Part 6: Continuous integration with Jenkins

Jenkins runs on the EC2 instance configured by Ansible (Part 4). There is one pipeline per microservice, and all of them use the same shared library.

### First login and credentials

1. Open `http://<jenkins_public_ip>:8080` and unlock Jenkins with the initial admin password printed by the playbook (or `sudo cat /var/lib/jenkins/secrets/initialAdminPassword` on the server).
2. Install the suggested plugins and create the admin user.
3. Create an IAM user for the pipelines with the minimum permissions, only to push images:

```bash
aws iam create-user --user-name jenkins-ecr
aws iam attach-user-policy --user-name jenkins-ecr \
  --policy-arn arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryPowerUser
aws iam create-access-key --user-name jenkins-ecr    # keep the output private
```

4. Add two credentials in **Manage Jenkins > Credentials > System > Global** (kind: Username with password):

| ID | Username | Password | Used for |
|---|---|---|---|
| `aws-ecr-creds` | Access key ID of `jenkins-ecr` | Secret access key | `aws ecr get-login-password` and `docker push` |
| `github-creds` | GitHub user | GitHub personal access token (`repo` scope) | Cloning the repository and pushing the manifest update |

Secrets are only stored in Jenkins and are masked in the build logs.

![Jenkins unlock page](docs/screenshots/44-jenkins-unlock-page.png)
![Jenkins dashboard](docs/screenshots/45-jenkins-dashboard.png)
![Tools on the Jenkins server](docs/screenshots/46-jenkins-server-tools.png)
![Jenkins IAM user](docs/screenshots/47-jenkins-iam-user.png)
![Jenkins credentials](docs/screenshots/48-jenkins-credentials.png)

### Shared library

The pipeline logic is written once in `vars/ivolvePipeline.groovy` and registered in **Manage Jenkins > System > Global Trusted Pipeline Libraries**:

| Setting | Value |
|---|---|
| Name | `ivolve-shared-lib` |
| Default version | `main` |
| Retrieval method | Legacy SCM, Git |
| Repository | `https://github.com/mohamedsala7320-sudo/CloudDevOpsProject.git` |
| Credentials | `github-creds` |

Each service has its own `Jenkinsfile` (`auth-service/Jenkinsfile`, `frontend/Jenkinsfile`, `roadmap-service/Jenkinsfile`) that loads the library and calls the shared pipeline for that service. A change in the shared library applies to all three pipelines.

![Shared library file](docs/screenshots/49-shared-library-file.png)
![vars folder on GitHub](docs/screenshots/50-github-vars-folder.png)
![Shared library configuration](docs/screenshots/51-jenkins-shared-library-config.png)
![Jenkinsfiles on GitHub](docs/screenshots/52-jenkinsfiles-on-github.png)

### Pipeline stages

| # | Stage | What it does |
|---|---|---|
| 1 | Build Image | `docker build` of the service, tagged `<account>.dkr.ecr.eu-north-1.amazonaws.com/ivolve-<service>:<BUILD_NUMBER>` |
| 2 | Scan Image | `trivy image --severity HIGH,CRITICAL` on the new image (report only, it does not fail the build) |
| 3 | Push Image | Logs in to ECR with `aws-ecr-creds` and pushes the image |
| 4 | Delete Image Locally | Removes the image from the Jenkins server to save disk space |
| 5 | Update Manifests | Changes the image tag in `k8s/base/<service>.yaml` to the new build number |
| 6 | Push Manifests | Commits as `ci: <service> -> <build number>` and pushes to `main` with `github-creds` |

### Pipeline jobs

Three jobs of type **Pipeline script from SCM** (Git, branch `*/main`, credentials `github-creds`), one per service:

| Job | Script path |
|---|---|
| `auth-service-pipeline` | `auth-service/Jenkinsfile` |
| `frontend-pipeline` | `frontend/Jenkinsfile` |
| `roadmap-service-pipeline` | `roadmap-service/Jenkinsfile` |

![auth-service pipeline](docs/screenshots/53-auth-service-success.png)
![frontend pipeline](docs/screenshots/54-frontend-pipeline-success.png)
![roadmap-service pipeline](docs/screenshots/55-roadmap-pipeline-success.png)
![Jenkins jobs](docs/screenshots/56-jenkins-jobs-list.png)

Result: the manifest commits made by Jenkins and the images in ECR.

![CI commits on GitHub](docs/screenshots/57-github-ci-commits.png)
![Images in ECR](docs/screenshots/58-ecr-images-pushed.png)

### Design decisions

| Decision | Reason |
|---|---|
| Shared library in `vars/` | One place for the pipeline logic, the Jenkinsfiles stay tiny and identical in structure |
| Image tag = build number | Every image is traceable to a build, and the manifest always points to an exact version (never `latest`) |
| Jenkins only changes Git, never the cluster | GitOps: the cluster state is always what is in the repository |
| Dedicated IAM user with ECR PowerUser | Least privilege: the pipeline can push images and nothing else |
| Trivy in report-only mode | Findings in base images are visible in every build without blocking the delivery. Switching to a gate only needs `--exit-code 1` |
| Image deleted at the end of the build | The Jenkins disk (30 GB) does not fill up |

## Part 7: Continuous deployment with ArgoCD

ArgoCD runs inside the EKS cluster, watches `k8s/base` on `main` and keeps the `ivolve` namespace identical to Git.

### 1. StorageClass on EKS

The StorageClass used on the local cluster is for VMs only. On EKS the same name `ivolve-mysql-sc` is backed by EBS gp3 through the EBS CSI driver (installed by Terraform). It is applied once by hand because it is cluster infrastructure and is not part of `k8s/base`.

```bash
export KUBECONFIG=~/.kube/eks-config
kubectl apply -f k8s/storage-eks/ivolve-mysql-sc.yaml
kubectl get sc
```

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: ivolve-mysql-sc
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
  fsType: ext4
volumeBindingMode: WaitForFirstConsumer
reclaimPolicy: Delete
allowVolumeExpansion: true
```

![EKS nodes and default StorageClass](docs/screenshots/59-eks-nodes-and-default-sc.png)
![EBS gp3 StorageClass](docs/screenshots/60-eks-storageclass-gp3.png)

### 2. Traefik ingress controller

Traefik creates an AWS load balancer in the public subnets, and the frontend Ingress (`ingressClassName: traefik`) routes the traffic to the frontend Service.

```bash
helm repo add traefik https://traefik.github.io/charts
helm repo update
helm install traefik traefik/traefik -n traefik --create-namespace
kubectl get pods,svc -n traefik
```

![Traefik on EKS](docs/screenshots/61-traefik-installed.png)

### 3. Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl get pods -n argocd
```

`--server-side` is needed because the ArgoCD CRDs are too large for a normal client-side apply.

![ArgoCD pods](docs/screenshots/62-argocd-pods-running.png)

Open the ArgoCD UI (user `admin`):

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
kubectl port-forward svc/argocd-server -n argocd 8081:443     # then open https://localhost:8081
```

### 4. ArgoCD Application

`argocd/application.yaml`:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: ivolve-app
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/mohamedsala7320-sudo/CloudDevOpsProject.git
    targetRevision: main
    path: k8s/base
  destination:
    server: https://kubernetes.default.svc
    namespace: ivolve
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

| Setting | Meaning |
|---|---|
| `path: k8s/base` | ArgoCD deploys the 9 manifests (namespace, config, MySQL, 3 services, ingress) |
| `automated` | Every new commit is deployed without a manual sync |
| `prune: true` | Resources removed from Git are removed from the cluster |
| `selfHeal: true` | Manual changes in the cluster are reverted to the Git state |
| `CreateNamespace=true` | The `ivolve` namespace is created if it does not exist |

```bash
kubectl apply -f argocd/application.yaml
kubectl get applications -n argocd
kubectl get all,pvc,ingress -n ivolve
```

The repository is public, so no repository credentials are needed in ArgoCD.

![ArgoCD application synced](docs/screenshots/63-argocd-application-synced.png)
![Resources in the ivolve namespace](docs/screenshots/64-ivolve-resources-running.png)

### 5. Verify the application

```bash
kubectl get svc traefik -n traefik -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'; echo
```

Open `http://<load-balancer-hostname>`, create an account and log in. This proves that the frontend, the auth service and MySQL (on an EBS volume) work together.

![Frontend through the load balancer](docs/screenshots/65-frontend-browser.png)
![Signup and login work](docs/screenshots/66-app-signup-login-works.png)

### 6. Verify the full GitOps loop

1. Run `frontend-pipeline` in Jenkins. Every stage must be green, down to Push Manifests.
2. Jenkins pushes the commit `ci: frontend -> <build number>` to `main`.
3. ArgoCD detects the commit (it checks the repository about every 3 minutes, or press **Refresh**) and syncs it. The frontend Deployment moves to the new image tag with a rolling update.

```bash
git pull
kubectl describe deployment frontend -n ivolve | grep Image
```

![Jenkins frontend pipeline](docs/screenshots/67-jenkins-frontend-pipeline-success.png)
![CI commit on GitHub](docs/screenshots/68-github-ci-commit.png)
![Deployment on the new image tag](docs/screenshots/69-argocd-synced-after-jenkins.png)
![ArgoCD application tree after the sync](docs/screenshots/70-argocd-app-details-after-sync.png)

In the last screenshot the sync was made by the commit `ci: frontend -> 2` (author `jenkins`), the `frontend` Deployment is at revision 2 with a new ReplicaSet, and all resources are `Synced` and `Healthy`.

## Troubleshooting

**EBS CSI controller in `CrashLoopBackOff`.** In the first version the EBS CSI policy was attached to the node IAM role. The `ebs-csi-controller` pods kept crashing and the add-on stayed in `CREATING`. Moving to EKS Pod Identity fixed it: the `eks-pod-identity-agent` add-on, a dedicated IAM role for `ebs-csi-controller-sa` and a `pod_identity_association` on the add-on. After the change, the controller pods are `6/6 Running`.

**SSH to the Jenkins server hangs.** The public IP of the workstation changed, so the security group no longer matched. Re-run `terraform apply` with the current IP (see Part 3).

**`kubectl` tries to connect to `127.0.0.1`.** The `KUBECONFIG` variable is set per terminal. Run `export KUBECONFIG=~/.kube/eks-config` in every new terminal.

**Push Image fails with `aws: not found`.** The AWS CLI v2 is not installed on the Jenkins server (see Known limitations):

```bash
ssh -i ~/.ssh/clouddevops-key ubuntu@<jenkins_public_ip>
sudo apt-get install -y unzip
curl -s "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o awscliv2.zip
unzip -q awscliv2.zip && sudo ./aws/install && aws --version
```

**Pods in `ImagePullBackOff` after rebuilding the infrastructure.** The new ECR repositories are empty. Run the 3 pipelines again: they push the images and update the manifests, and ArgoCD syncs the new tags.

**`git push` is rejected.** Jenkins committed to `main` in the meantime. Run `git pull` and push again.

## Cost control and cleanup

The NAT Gateway, the EKS control plane, the EKS nodes, the load balancer and the Jenkins instance are billed per hour. The environment is created for each working session and removed when it is no longer needed. Everything is rebuilt from code, and the state stays in S3.

```bash
kubectl delete application ivolve-app -n argocd     # removes the app and the EBS volume
kubectl delete svc traefik -n traefik               # removes the AWS load balancer before the destroy
cd terraform
terraform destroy -var="admin_cidr=$(curl -s https://checkip.amazonaws.com)/32"
```

The `jenkins-ecr` IAM user is not part of Terraform. Delete its access keys when the project is finished.

## Known limitations and improvements

| Limitation | Improvement |
|---|---|
| AWS CLI v2 is installed by hand on the Jenkins server | Add an `awscli` role to the Ansible playbook |
| Jenkins configuration (credentials, shared library, jobs) lives only on the server and must be redone after a rebuild | Jenkins Configuration as Code (JCasC) and job definitions in Git |
| The Kubernetes Secret has lab-only values in Git | Sealed Secrets or External Secrets with AWS Secrets Manager |
| Trivy only reports and does not fail the build | Fail the pipeline on CRITICAL findings |
| One NAT Gateway and one MySQL replica | One NAT per AZ and a managed database (RDS) for high availability |
| No HTTPS on the ingress | cert-manager with a real domain |
| Jenkins and ArgoCD are not exposed publicly | Restricted by design (admin IP only, port-forward) |

---

## Created by

**Mohamed Salah Shawky**
Junior DevOps Engineer
