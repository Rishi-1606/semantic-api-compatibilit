# Semantic API Compatibility — Backend

Spring Boot 3.3 backend for the Semantic API Compatibility Framework.

## Responsibilities (full list)

| Phase | Feature |
|-------|---------|
| 1 | Health check, project structure |
| 3 | API registry, version management |
| 4 | Schema analyzer |
| 5 | NLP pipeline |
| 6 | AI semantic matching |
| 7 | Compatibility engine |
| 8 | Transformation generator |
| 9 | Safety & approval |

## Tech Stack

- Java 17 / Spring Boot 3.3.4
- Spring Web, Spring Data JPA, Validation, Actuator
- PostgreSQL (production) / H2 (local dev)
- Flyway (schema migrations)
- Maven (via wrapper — no global install needed)

## Running Locally

```bash
# Default profile — uses H2 in-memory (no external DB needed)
./mvnw spring-boot:run          # Linux/Mac
mvnw.cmd spring-boot:run        # Windows

# Docker profile — uses PostgreSQL
./mvnw spring-boot:run -Dspring-boot.run.profiles=docker
```

## Health Checks

| Endpoint | Description |
|----------|-------------|
| `GET /api/health` | Custom health endpoint |
| `GET /actuator/health` | Spring Actuator health |
| `GET /actuator/info` | Application info |
| `GET /h2-console` | H2 DB console (local only) |

## Environment Variables (Docker profile)

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | JDBC URL for PostgreSQL |
| `DATABASE_USERNAME` | DB username |
| `DATABASE_PASSWORD` | DB password |
| `AI_API_KEY` | External AI service key |
| `AI_API_BASE_URL` | Base URL for AI service |

## Package Structure

```
com.semanticapi/
├── SemanticApiApplication.java      # Entry point
├── config/
│   └── CorsConfig.java              # CORS settings
└── controller/
    ├── HealthController.java         # GET /api/health
    ├── ApiRegistryController.java    # Phase 3: API management
    └── GlobalExceptionHandler.java  # Structured error responses
```
