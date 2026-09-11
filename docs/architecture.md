# Architecture — Semantic API Compatibility Framework

## System Overview

```
                         ┌───────────────────┐
                         │      React        │
                         │    Dashboard      │  :5173
                         └─────────┬─────────┘
                                   │ REST API
                                   ↓
                         ┌───────────────────┐
                         │    Spring Boot    │
                         │     Backend       │  :8080
                         └─────────┬─────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ↓                    ↓                    ↓
       API Registry          Compatibility         Transformation
       (Phase 3)              Engine                  Engine
                              (Phase 7)              (Phase 8)
                                   │
                                   ↓
                             NLP Pipeline
                             (Phase 5)
                                   │
                                   ↓
                             AI Semantic
                              Analyzer
                              (Phase 6)
                                   │
                                   ↓
                              PostgreSQL  :5432


                 Runtime API Traffic (Phase 10+)

Client
   │
   │ V1 Request
   ↓
┌───────────────────┐
│ Node.js Gateway   │  :3001
└─────────┬─────────┘
          │
          ↓
Transformation Engine (approved rules only)
          │
          │ V2 Request
          ↓
     Target API V2
          │
          │ V2 Response
          ↓
Transformation Engine
          │
          │ V1 Response
          ↓
        Client
```

## Safety Model

AI/NLP generates **candidate mappings** with confidence scores.

Only mappings that pass deterministic validation and meet the confidence threshold are applied at runtime.

```
Confidence ≥ 0.90  → Auto-approved
Confidence 0.70–0.89 → Requires human approval
Confidence < 0.70   → Rejected / flagged
```

These thresholds are configurable via application.yml.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Backend language | Java + Spring Boot | Specified in spec; strong JPA + validation ecosystem |
| Gateway | Node.js + Express | Specified; lightweight, ideal for proxy |
| Frontend | React + Vite | Specified; Vite for fast DX |
| Database | PostgreSQL (prod) / H2 (dev) | PostgreSQL specified; H2 for local dev without Docker |
| Build tool | Maven + Wrapper | Spec prefers Maven; wrapper avoids global install requirement |
| Migration | Flyway | Standard, works with both H2 and PostgreSQL |
| AI interface | Clean service interface | Allows swapping provider without changing business logic |
