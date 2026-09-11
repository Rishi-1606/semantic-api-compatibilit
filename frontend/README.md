# Semantic API Compatibility — Frontend

React developer dashboard for the Semantic API Compatibility Framework.

## Tech Stack

- React 18 + Vite
- Vanilla CSS (dark theme, glassmorphism)
- Google Fonts (Inter, JetBrains Mono)

## Running Locally

```bash
npm install
npm run dev
```

Open: `http://localhost:5173`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `VITE_BACKEND_URL` | `http://localhost:8080` | Spring Boot backend URL |
| `VITE_GATEWAY_URL` | `http://localhost:3001` | Node.js gateway URL |

## Phase 1 Features

- Service health cards (backend + gateway + frontend)
- System architecture diagram
- Development phases tracker
- Quick links panel

## Planned Features (Phase 12)

- API registration & management
- Endpoint and schema viewer
- Compatibility analysis view
- Semantic mapping with confidence scores
- Approve/reject transformation rules
- Transformation tester (input JSON → output JSON)
- Transformation history
