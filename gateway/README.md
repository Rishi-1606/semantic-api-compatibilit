# Semantic API Compatibility — Gateway

Node.js API Gateway that proxies and transforms requests between API versions.

## Responsibilities

| Phase | Feature |
|-------|---------|
| 1 | Health check, routing skeleton |
| 10 | Request transformation (V1 → V2) |
| 11 | Response transformation (V2 → V1) |

## Tech Stack

- Node.js 20+
- Express 4
- Axios (HTTP client for backend calls)
- Morgan (request logging)
- dotenv (environment config)

## Running Locally

```bash
npm install
npm start          # production
npm run dev        # with nodemon auto-restart
```

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `3001` | Gateway listen port |
| `BACKEND_URL` | `http://localhost:8080` | Spring Boot backend URL |
| `NODE_ENV` | `development` | Node environment |

## Health Check

```bash
curl http://localhost:3001/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "gateway",
  "version": "1.0.0",
  "phase": "Phase 1 - Foundation",
  "timestamp": "..."
}
```

## Proxy Endpoint (Phase 10)

```
POST /proxy/:endpoint/:version
```

Example:
```
POST /proxy/customer/v1
Body: { "name": "Rishi", "mobile": "9876543210" }
```

Currently returns `501 Not Implemented`.
