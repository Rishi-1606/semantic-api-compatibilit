# Semantic API Compatibility & Automatic Transformation Framework

A middleware system that enables clients using one API version to communicate transparently with a newer version, even when request/response schemas have changed. The system uses NLP and AI to identify semantically equivalent fields, generates validated transformation rules, and applies them at runtime through a Node.js gateway.

---

## Architecture

```
React Dashboard  →  Spring Boot Backend  →  PostgreSQL
                          ↓ (NLP + AI analysis)
                    Compatibility Engine
                    Transformation Rules

Client (V1)  →  Node.js Gateway  →  Transform  →  Target API (V2)
                                           ↓
                            Transform V2 Response → V1 Response → Client
```

---

## Services & Ports

| Service    | Technology         | Port |
|------------|--------------------|------|
| Backend    | Spring Boot / Java | 8080 |
| Gateway    | Node.js / Express  | 3001 |
| Frontend   | React / Vite       | 5173 |
| Database   | PostgreSQL 15      | 5432 |
| Mock API V1| Node.js (Phase 2)  | 8081 |
| Mock API V2| Node.js (Phase 2)  | 8082 |

---

## Prerequisites

- Java 17+  
- Node.js 18+  
- npm 9+  
- Docker & Docker Compose (optional, for one-command startup)

Maven is bundled via the Maven Wrapper (`./mvnw`) — no global install needed.

---

## Quick Start (Docker — recommended)

```bash
# 1. Copy and configure environment variables
cp .env.example .env
# Edit .env with your real values

# 2. Start all services
docker compose up --build

# 3. Verify
curl http://localhost:8080/api/health
curl http://localhost:3001/health
open http://localhost:5173
```

---

## Manual Start (without Docker)

### 1. PostgreSQL

Start a local PostgreSQL instance (or use the Docker service alone):

```bash
docker compose up postgres -d
```

Or configure your local PostgreSQL and apply the migration:

```bash
psql -U semantic_user -d semantic_api_db -f database/migrations/V1__init.sql
```

### 2. Backend (Spring Boot)

```bash
cd backend
cp ../.env.example .env   # configure DB credentials
./mvnw spring-boot:run
# Windows: mvnw.cmd spring-boot:run
```

Health check: `curl http://localhost:8080/api/health`

### 3. Gateway (Node.js)

```bash
cd gateway
cp .env.example .env
npm install
npm start
```

Health check: `curl http://localhost:3001/health`

### 4. Frontend (React)

```bash
cd frontend
cp .env.example .env
npm install
npm run dev
```

Open: `http://localhost:5173`

---

## Health Checks

| Endpoint                              | Expected Response                        |
|---------------------------------------|------------------------------------------|
| `GET http://localhost:8080/api/health`| `{"status":"UP","service":"backend",...}`|
| `GET http://localhost:8080/actuator/health` | Spring Actuator health              |
| `GET http://localhost:3001/health`    | `{"status":"UP","service":"gateway",...}`|
| `http://localhost:5173`               | React dashboard loads                    |

---

## Project Structure

```
semantic-api-compatibility/
├── backend/            # Spring Boot — API registry, compatibility engine, transformation rules
├── gateway/            # Node.js — Runtime proxy and transformation
├── frontend/           # React — Developer dashboard
├── mock-apis/          # Phase 2: V1 and V2 demo customer APIs
├── database/
│   └── migrations/     # SQL migration scripts
├── docs/               # Architecture, API spec, transformation rule docs
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Development Phases

| Phase | Description | Status |
|-------|-------------|--------|
| 1 | Foundation — monorepo, services, health checks | ✅ Complete |
| 2 | Mock APIs V1 & V2 | ⏳ Pending |
| 3 | API Registry (persistence) | ⏳ Pending |
| 4 | Schema Analyzer | ⏳ Pending |
| 5 | NLP Pipeline | ⏳ Pending |
| 6 | AI Semantic Matching | ⏳ Pending |
| 7 | Compatibility Engine | ⏳ Pending |
| 8 | Transformation Generator | ⏳ Pending |
| 9 | Safety & Approval | ⏳ Pending |
| 10 | Runtime Gateway | ⏳ Pending |
| 11 | Response Transformation | ⏳ Pending |
| 12 | Frontend Dashboard | ⏳ Pending |
| 13 | Testing | ⏳ Pending |

---

## Security Notes

- Never commit `.env` files.
- AI API keys must be in environment variables only.
- Low-confidence transformation mappings are never auto-applied (safety threshold: 90%).

---

## License

Academic project — Semantic API Compatibility & Automatic Transformation Framework.
