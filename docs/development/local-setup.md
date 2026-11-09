# NexusOps – Local Development Environment Setup Guide

**Document Version:** 1.0.0  
**Status:** Approved  
**Author:** NexusOps Architecture Team  

---

## 1. Prerequisites

Before running NexusOps locally, ensure you have the following installed on your machine:
- **Operating System:** Windows 10/11 (with WSL2 or native PowerShell), macOS, or Linux.
- **Java Development Kit (JDK):** JDK 21 LTS (or JDK 25 LTS). Verify with: `java -version`.
- **Node.js & npm:** Node.js v20+ or v22 LTS with npm 10+. Verify with: `node -v` and `npm -v`.
- **Docker Desktop:** Docker 24+ with Docker Compose v2+. Verify with: `docker --version`.

---

## 2. Infrastructure Port Allocations

All local infrastructure runs within Docker Compose on dedicated ports:

| Service | Host Port | Internal Port | Purpose / Access |
| :--- | :--- | :--- | :--- |
| **Frontend** | `5173` | `5173` | React application (Vite Dev Server) |
| **Backend API** | `8080` | `8080` | Spring Boot REST API & Actuator |
| **PostgreSQL** | `5432` | `5432` | PostgreSQL 16 + pgvector (`nexusops_db`) |
| **Redis** | `6379` | `6379` | Cache & session storage |
| **Apache Kafka** | `9092` | `9092` | KRaft Event Broker |
| **MinIO (API)** | `9000` | `9000` | S3-compatible document storage endpoint |
| **MinIO (Console)**| `9001`| `9001` | MinIO Web Dashboard (`minioadmin` / `miniopassword`) |
| **Mailpit (SMTP)**| `1025` | `1025` | Local SMTP inbound server |
| **Mailpit (Web UI)**| `8025`| `8025` | Local Email Inbox Web Viewer |
| **Ollama (Optional)**| `11434`| `11434` | Local LLM inference server (optional `--profile ai`) |

---

## 3. Step-by-Step Setup Instructions

### Step 3.1: Clone and Configure Environment Variables
Copy `.env.example` to `.env` in the project root:
```bash
cp .env.example .env
```
Default credentials in `.env.example` are pre-configured for seamless local execution.

### Step 3.2: Start Docker Infrastructure Services
Start the core infrastructure containers (PostgreSQL, Redis, Kafka, MinIO, Mailpit):
```bash
# Start core containers in the background
docker compose -f infrastructure/docker-compose.yml up -d

# Verify that all containers are healthy
docker compose -f infrastructure/docker-compose.yml ps
```

*Note on RAM Conservation:* If you do not need Kafka or MinIO in an early phase, you can start only PostgreSQL and Redis:
```bash
docker compose -f infrastructure/docker-compose.yml up -d postgres redis
```

To run with local Ollama enabled:
```bash
docker compose -f infrastructure/docker-compose.yml --profile ai up -d
```

### Step 3.3: Run Backend (Spring Boot)
Open a terminal in `nexusops-backend`:
```bash
cd nexusops-backend

# Build and run verification test
./mvnw clean test

# Run Spring Boot application locally
./mvnw spring-boot:run
```
The API is live at `http://localhost:8080/api/v1`.  
Swagger / OpenAPI docs: `http://localhost:8080/swagger-ui.html`  
Actuator health check: `http://localhost:8080/actuator/health`

### Step 3.4: Run Frontend (React + Vite)
Open a new terminal in `nexusops-frontend`:
```bash
cd nexusops-frontend

# Install dependencies
npm install

# Start development server
npm run dev
```
Open your browser at `http://localhost:5173`.

---

## 4. Operational Inspection URLs

- **NexusOps Web Application:** [http://localhost:5173](http://localhost:5173)
- **Spring Boot Actuator Health:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)
- **Mailpit Email Inspector:** [http://localhost:8025](http://localhost:8025)
- **MinIO Storage Browser:** [http://localhost:9001](http://localhost:9001) (User: `minioadmin`, Password: `miniopassword`)

---

## 5. Troubleshooting Common Issues

1. **Port 5432 or 6379 already in use:**
   - If you have local PostgreSQL or Redis installed natively on your host machine, stop those services or change `POSTGRES_PORT` / `REDIS_PORT` in your `.env` file.
2. **Docker permissions / Docker Daemon not running:**
   - Make sure Docker Desktop is launched and the Linux engine status indicator is green.
3. **Database extension errors:**
   - If PostgreSQL complains about `vector` extension, ensure the container image is `pgvector/pgvector:pg16` which includes pre-compiled vector extensions.
