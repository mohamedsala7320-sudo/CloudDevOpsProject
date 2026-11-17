# CloudDevOpsProject

End-to-end DevOps project built as the final project of the NTI / iVolve DevOps training.
A microservices web application (registration, login and a DevOps roadmap page) is containerized with Docker, provisioned on AWS with Terraform, configured with Ansible, built by Jenkins and deployed to Kubernetes (EKS) with ArgoCD.

## Project status

- [x] 1. GitHub repository
- [x] 2. Containerization with Docker Compose
- [ ] 3. Infrastructure provisioning with Terraform
- [ ] 4. Configuration management with Ansible
- [x] 5. Container orchestration with Kubernetes
- [ ] 6. Continuous integration with Jenkins
- [ ] 7. Continuous deployment with ArgoCD
- [ ] 8. Documentation

## Application architecture

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
├── auth-service/          # Flask app + Dockerfile
├── frontend/              # Express app + Dockerfile
├── roadmap-service/       # Java app + Dockerfile
├── docs/screenshots/      # Screenshots used in this README
├── docker-compose.yml
├── .env.example
└── README.md
```

## Part 2: Containerization with Docker Compose

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
| Secrets in G`.env` with `:?` checks | Compose stops early with a clear message if a variable is missing |


## Part 5: Container orchestration with Kubernetes

The application runs on Kubernetes in the `ivolve` namespace. It was first tested on a local kubeadm cluster (1 control plane + 2 workers) before moving to EKS.

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
| `k8s/base/08-ingress.yaml` | Ingress for the frontend |
| `k8s/storage/vms-local-path.yaml` | StorageClass `ivolve-mysql-sc` for the local cluster |
| `k8s/ingress/traefik-values-vms.yaml` | Helm values for the Traefik ingress controller |

### Design decisions

| Decision | Reason |
|---|---|
| StatefulSet + headless Service for MySQL | Stable pod name (`mysql-0.mysql`) and its own volume |
| StorageClass `ivolve-mysql-sc` with `Retain` | Data is kept if the claim is deleted. The same name will be backed by EBS gp3 on EKS |
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

### Evidence

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
