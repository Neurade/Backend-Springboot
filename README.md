# CeraLabs Backend — Spring Boot

A RESTful backend for **CeraLabs**, an AI-assisted e-learning platform that supports classroom management, AI-powered chatbot tutoring, automated assignment grading via OCR, and AI package subscriptions.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Architecture & Tech Stack](#architecture--tech-stack)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [API Endpoints](#api-endpoints)
- [Environment Variables](#environment-variables)
- [How to Run (Local Development)](#how-to-run-local-development)
- [How to Run (Docker)](#how-to-run-docker)
- [Deploy to Production](#deploy-to-production)
- [Database Migrations](#database-migrations)
- [Authentication & Authorization](#authentication--authorization)

---

## Project Overview

CeraLabs is a platform targeting teachers, organizations, and students. It provides:

- **Classroom Management** — create classes, invite participants, manage assignments
- **AI Chatbot** — async Q&A powered by a configurable LLM (default: Gemini), with file upload support
- **Assignment Grading** — upload assignment PDFs, extract questions via OCR, and auto-grade student answers
- **AI Packages** — purchasable AI instance packages with token budgets and rate limits per class
- **User & Admin Management** — role-based access (ADMIN, ORGANIZATION, TEACHER, STUDENT), profile management
- **File Storage** — profile pictures and assignment files stored in MinIO (S3-compatible)
- **Location Data** — Vietnam province/commune data for user profile

---

## Architecture & Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.0.2, Java 21 |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Cache | Redis 7 |
| Messaging | RabbitMQ 3 (async job processing) |
| File Storage | MinIO (S3-compatible) |
| Security | Spring Security, JWT, OAuth2 (Google) |
| API Docs | SpringDoc OpenAPI 3 (Swagger UI) |
| Containerization | Docker / Docker Compose |

### Async Job Flow

```
Client → REST API → RabbitMQ Queue → Worker (LLM/OCR service) → Redis (job status) → Client polls job status
```

Chatbot messages and assignment grading are dispatched asynchronously via RabbitMQ, allowing long-running LLM calls without blocking HTTP requests.

---

## Project Structure

```
Backend-Springboot/
├── ci/
│   └── data.csv                        # Seed data for provinces/communes
├── src/main/java/app/demo/CeraLabs/
│   ├── CeraLabsApplication.java
│   ├── configs/                        # Spring configurations
│   │   ├── MinioConfig.java
│   │   ├── OpenApiConfig.java
│   │   ├── RabbitMQConfig.java
│   │   ├── RedisConfig.java
│   │   ├── RestTemplateConfig.java
│   │   └── SecurityConfig.java
│   ├── controllers/                    # REST controllers
│   │   ├── AdminController.java
│   │   ├── AssignmentController.java
│   │   ├── ChatbotController.java
│   │   ├── ClassController.java
│   │   ├── FileController.java
│   │   ├── LocationController.java
│   │   ├── ProductController.java
│   │   └── UserController.java
│   ├── domain/
│   │   ├── dtos/                       # Data Transfer Objects (request/response)
│   │   ├── mappers/                    # Entity ↔ DTO mappers
│   │   ├── models/                     # JPA entities
│   │   └── rabbitmq/                   # RabbitMQ message payloads
│   ├── exception/                      # Global exception handling
│   ├── infrastructures/
│   │   ├── assignment_ocr/             # OCR integration
│   │   ├── chatbot_llm/               # LLM integration (Gemini etc.)
│   │   ├── rabbitmq/                   # Queue producers/consumers
│   │   └── repositories/              # Spring Data JPA repositories
│   ├── security/                       # JWT, OAuth2, auth controllers
│   └── services/                       # Business logic
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/                   # Flyway SQL migrations
├── docker-compose.yml
├── .env.example
└── pom.xml
```

---

## Database Schema

Core tables managed by Flyway migrations:

| Table | Description |
|---|---|
| `users` | User accounts (email, password hash, role, verified) |
| `user_information` | Extended profile (name, school, grade, avatar, address) |
| `roles` | Role definitions (ADMIN, ORGANIZATION, TEACHER, STUDENT) |
| `classes` | Classrooms with creator and timestamps |
| `class_participants` | Many-to-many users ↔ classes |
| `assignments` | Assignments belonging to a class |
| `assignment_questions` | Questions extracted from PDFs (with image URLs) |
| `student_answers` | Student submitted answers per question |
| `conversations` | Chatbot conversation sessions |
| `qa_entries` | Individual Q&A pairs within a conversation |
| `question_assets` | File attachments for chat messages |
| `ai_packages` | Purchasable AI subscription packages |
| `ai_packages_instances` | Purchased package instances bound to a class |
| `user_ai_instance_usages` | Per-user token usage tracking |
| `people_management` | Manager-managed user hierarchy |
| `provinces` / `communes` | Vietnam geographic data (seeded from CSV) |

---

## API Endpoints

Interactive docs available at: `http://localhost:8080/swagger-ui.html`

### Authentication — `/api/v1/auth`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/register` | Register a new user | Public |
| POST | `/login` | Login, sets JWT cookies and redirects | Public |
| GET | `/oauth2/authorization/google` | Initiate Google OAuth2 flow | Public |

### Users — `/api/v1/user`

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/{userId}` | Get user profile | Required |
| PATCH | `/{email}` | Update user profile info | Required |
| GET | `/managed` | Get users under current user's management | Required |

### Admin — `/api/v1/admin`

| Method | Path | Description | Auth |
|---|---|---|---|
| PATCH | `/users/{email}/role` | Change a user's role | ADMIN |
| PATCH | `/users/{email}/password` | Change a user's password | ADMIN |
| GET | `/statistic/all-users` | Get all users statistics | ADMIN |

### Classes — `/api/v1/class`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/` | Create a new class | ADMIN / ORGANIZATION / TEACHER |
| GET | `/` | Get all classes under management | ADMIN / ORGANIZATION / TEACHER |
| GET | `/all` | Get all classes (public listing) | Required |
| GET | `/{classId}` | Get class details | Required |
| GET | `/{classId}/participants` | Get class members | Required |
| POST | `/{classId}/invite` | Add participants to a class | ADMIN / ORGANIZATION / TEACHER |
| POST | `/{classId}/assignment` | Create an assignment | ADMIN / ORGANIZATION / TEACHER |
| POST | `/{classId}/assignment/{assignmentId}/question` | Upload PDFs and extract questions | ADMIN / ORGANIZATION / TEACHER |
| GET | `/{classId}/assignment/{assignmentId}` | Get assignment details | Required |
| POST | `/{classId}/instance-usage-limit` | Set per-user AI usage limit | ADMIN / ORGANIZATION / TEACHER |

### Assignments — `/api/v1/assignment`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/judge` | Submit answers for AI grading | Required |
| GET | `/judge/job-status/{jobId}` | Poll grading job status | Required |
| GET | `/{assignmentId}/judgement` | Get grading results | Required |

### Chatbot — `/api/v1/chatbot`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/chat` | Send a message (multipart form-data: `instanceId`, `conversationId`, `question` + optional `files`) | Required |
| GET | `/chat/job-status/{jobId}` | Poll chat job status | Required |
| GET | `/{conversationId}/history` | Get conversation history | Required |
| GET | `/conversations` | Get all conversations for current user | Required |

### Products (AI Packages) — `/api/v1/product`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/ai-model/api-key/validate` | Validate an LLM API key | Required |
| POST | `/ai-package` | Create an AI package | ADMIN |
| GET | `/ai-packages` | List all AI packages | Required |
| GET | `/ai-package/{packageId}` | Get AI package details | Required |
| POST | `/ai-package/purchase` | Purchase a package for a class | ADMIN / ORGANIZATION / TEACHER |
| GET | `/ai-package/instance/{instanceId}` | Get instance details | Required |
| POST | `/ai-package/instance` | Get instances for current user + class | Required |

### Files — `/api/v1/file`

| Method | Path | Description | Auth |
|---|---|---|---|
| POST | `/upload-pfp` | Upload profile picture | Required |

### Location — `/api/v1/location`

| Method | Path | Description | Auth |
|---|---|---|---|
| GET | `/provinces` | List all provinces | Public |
| GET | `/communes` | List all communes | Public |

---

## Environment Variables

Copy `.env.example` to `.env` and fill in all values:

```bash
cp .env.example .env
```

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `postgres` (Docker) / `localhost` (local) |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `CeraLabs` |
| `DB_USERNAME` | DB username | `postgres` |
| `DB_PASSWORD` | DB password | `secret` |
| `MINIO_HOST` | MinIO server host | `minio.example.com` |
| `MINIO_PORT` | MinIO server port | `9010` |
| `MINIO_ACCESS` | MinIO access key | — |
| `MINIO_SECRET` | MinIO secret key | — |
| `MINIO_BUCKET_PFP` | Bucket for profile pictures | `CeraLabs-pfp` |
| `MINIO_BUCKET_CHAT` | Bucket for chat attachments | `CeraLabs-chat` |
| `MINIO_BUCKET_ASSIGNMENT` | Bucket for assignment files | `CeraLabs-assignment` |
| `REDIS_HOST` | Redis host | `redis` (Docker) / `localhost` (local) |
| `REDIS_PORT` | Redis port | `6379` |
| `RABBITMQ_HOST` | RabbitMQ host | `rabbitmq` (Docker) / `localhost` (local) |
| `RABBITMQ_PORT` | RabbitMQ AMQP port | `5672` |
| `RABBITMQ_USERNAME` | RabbitMQ username | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ password | `guest` |
| `LLM_PROVIDER` | LLM provider name | `GEMINI` |
| `LLM_API_KEY` | LLM provider API key | — |
| `LLM_MODEL` | LLM model identifier | `gemini-2.5-flash` |
| `LLM_VALIDATE_ENDPOINT` | Endpoint to validate API key | `http://host:port/api/v1/validate-key` |
| `LLM_QA_ENDPOINT` | Endpoint for chatbot Q&A workflow | `http://host:port/workflow` |
| `LLM_WS_ENDPOINT` | WebSocket endpoint for streaming responses | `ws://host:port/ws` |
| `LLM_ASSIGNMENT_OCR_ENDPOINT` | Endpoint for assignment OCR extraction | `http://host:port/extract` |
| `LLM_TOP_K` | Top-K results for RAG retrieval | `5` |
| `LLM_TIMEOUT_SECONDS` | LLM call timeout in seconds | `120` |
| `JWT_SECRET_KEY` | JWT signing secret (min 256-bit) | — |
| `JWT_EXPIRATION` | Access token TTL (ms) | `86400000` (1 day) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) | `604800000` (7 days) |
| `JPA_DDL_AUTO` | Hibernate DDL mode | `validate` |
| `JPA_SHOW_SQL` | Log SQL queries | `false` |
| `JPA_FORMAT_SQL` | Format SQL in logs | `true` |
| `SERVER_OUTER_PORT` | Host port mapped to app container | `8836` |
| `CSV_FILE` | Path to location seed CSV inside container | `/docker-entrypoint-initdb.d/data.csv` |
| `FRONTEND_URL` | Frontend app URL (for CORS & redirects) | `http://localhost:3000` |
| `CORS_ALLOWED_ORIGINS` | Allowed CORS origins | `${FRONTEND_URL}` |
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID | — |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret | — |

---

## How to Run (Local Development)

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- PostgreSQL 16 running locally (or via Docker)
- Redis running locally
- RabbitMQ running locally
- MinIO running locally or accessible remotely

### Steps

1. **Clone and configure environment:**

```bash
cp .env.example .env
# Edit .env with your local service addresses (DB_HOST=localhost, REDIS_HOST=localhost, etc.)
```

2. **Export environment variables** (or set them in your IDE run configuration):

```bash
export $(grep -v '^#' .env | xargs)
```

3. **Run the application:**

```bash
./mvnw spring-boot:run
```

4. **Access the API:**
   - REST API: `http://localhost:8080`
   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Run tests

```bash
./mvnw test
```

---

## How to Run (Docker)

All services (PostgreSQL, Redis, RabbitMQ, and the app) are orchestrated via Docker Compose.

### Prerequisites

- Docker Engine 24+
- Docker Compose v2+

### Steps

1. **Configure environment:**

```bash
cp .env.example .env
# Edit .env — use service names as hostnames (DB_HOST=postgres, REDIS_HOST=redis, RABBITMQ_HOST=rabbitmq)
```

2. **Build and start all services:**

```bash
docker compose up -d --build
```

3. **Check logs:**

```bash
docker compose logs -f app
```

4. **Access the API:**
   - The app is exposed on `${SERVER_OUTER_PORT}` (default `8836`)
   - `http://localhost:8836`
   - Swagger UI: `http://localhost:8836/swagger-ui.html`

5. **Stop services:**

```bash
docker compose down
```

6. **Stop and remove volumes (database data):**

```bash
docker compose down -v
```

### Service Port Mapping (default)

| Service | Container Port | Host Port |
|---|---|---|
| Spring Boot App | 8080 | 8836 (configurable via `SERVER_OUTER_PORT`) |
| PostgreSQL | 5432 | 5463 |
| Redis | 6379 | 6380 |
| RabbitMQ AMQP | 5672 | 5673 |
| RabbitMQ Management UI | 15672 | 15673 |

---

## Deploy to Production

### Option 1: Docker Compose on a VM/VPS

1. Install Docker and Docker Compose on the server.
2. Copy project files (or clone repo) to the server.
3. Create and populate `.env` with production values.
4. Use a pre-built image instead of building on the server by editing `docker-compose.yml`:

```yaml
app:
  image: bananaonthetree/backend-CeraLabs:0.0.8  # uncomment this line
  # build: .                                     # comment this line
```

5. Start services:

```bash
docker compose up -d
```

6. Place a reverse proxy (Nginx or Caddy) in front of the app to handle SSL termination.

### Option 2: Build a JAR and run directly

```bash
./mvnw clean package -DskipTests
java -jar target/CeraLabs-0.0.1-SNAPSHOT.jar
```

Make sure all environment variables are set in the shell or passed with `-D` flags.

### Production Checklist

- [ ] Set strong `JWT_SECRET_KEY` (min 32 random bytes, base64-encoded)
- [ ] Set `JPA_DDL_AUTO=validate` (never `create` or `drop` in production)
- [ ] Enable HTTPS via a reverse proxy
- [ ] Set `CORS_ALLOWED_ORIGINS` to your exact frontend domain
- [ ] Configure RabbitMQ and MinIO with dedicated credentials (not defaults)
- [ ] Set appropriate `LLM_TIMEOUT_SECONDS` based on your LLM provider SLA
- [ ] Enable PostgreSQL regular backups

---

## Database Migrations

Flyway runs automatically on application startup. Migration scripts are located in:

```
src/main/resources/db/migration/
```

| Version | Description |
|---|---|
| V1.0.0 | Initial schema — users, roles, classes, conversations, AI packages, location tables |
| V1.0.1 | Populate data — seed provinces/communes from CSV |
| V2.0.0 | Add assignments and questions tables |
| V2.0.1 | Add answers to assignment questions |
| V2.0.2 | Add index on assignment_id in questions table |
| V2.0.3 | Add student answers table |
| V2.0.4 | Add indexes in classroom table |
| V2.0.5 | Add instance_id to conversations |
| V2.0.6 | Add user to conversations table |
| V2.0.7 | Add AI packages data |
| V2.1.0 | Allow nullable password for OAuth users |

> **Warning:** Never modify existing migration files. Create new versioned migration files for schema changes.

---

## Authentication & Authorization

### Authentication methods

- **JWT (HTTP-only cookies):** Login via `POST /api/v1/auth/login` sets `accessToken` and `refreshToken` cookies.
- **Google OAuth2:** Redirect to `/oauth2/authorization/google`. On success, `OAuth2SuccessHandler` issues JWT cookies and redirects to the frontend.

### Roles

| Role | Permissions summary |
|---|---|
| `ADMIN` | Full access — manage users, roles, AI packages, all classes |
| `ORGANIZATION` | Create classes, manage teachers under them, purchase AI packages |
| `TEACHER` | Create classes, create/grade assignments, manage their own classes |
| `STUDENT` | Join classes, submit answers, use chatbot |

### Public endpoints (no auth required)

- `POST /api/v1/auth/**`
- `GET /api/v1/location/**`
- `GET /api/v1/class/all`
- `/v3/api-docs/**`, `/swagger-ui/**`
- `/oauth2/**`
