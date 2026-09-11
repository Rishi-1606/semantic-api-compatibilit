# Semantic API Compatibility & Automatic Transformation Framework

## 1. Project Overview

Build a full-stack software project called:

**Semantic API Compatibility & Automatic Transformation Framework**

The goal is to create a middleware/framework that allows clients using one version of a REST API to communicate with another API version even when their request and response schemas have changed.

The system must use:

- AI
- NLP
- REST APIs
- Spring Boot
- Node.js

The core idea is:

```text
Client using API V1
        ↓
Node.js API Gateway
        ↓
Semantic Compatibility Layer
        ↓
Transformation Engine
        ↓
API V2
```

The system should identify semantically equivalent fields between different API versions, generate transformation mappings, validate them for safety, and apply approved transformations automatically at runtime.

---

# 2. Core Problem

API versions frequently evolve.

For example, API V1 may accept:

```json
{
  "name": "Rishi",
  "mobile": "9876543210"
}
```

while API V2 expects:

```json
{
  "fullName": "Rishi",
  "phoneNumber": "9876543210"
}
```

A client written for V1 should not necessarily need to be rewritten just because the backend API has changed.

The framework must be able to determine:

```text
name   → fullName
mobile → phoneNumber
```

and automatically transform:

```json
{
  "name": "Rishi",
  "mobile": "9876543210"
}
```

into:

```json
{
  "fullName": "Rishi",
  "phoneNumber": "9876543210"
}
```

The same framework should be capable of transforming the V2 response back into the V1 response format.

---

# 3. Important Design Principle

Do NOT make the AI/LLM directly control production API traffic.

AI/NLP should be used to:

1. Analyze schemas
2. Identify potential semantic relationships
3. Generate mapping suggestions
4. Produce confidence scores

Deterministic validation must then verify the proposed mappings.

Only validated/approved mappings may be used for runtime transformation.

Architecture:

```text
API Schemas
     ↓
NLP Preprocessing
     ↓
AI Semantic Analysis
     ↓
Candidate Mappings
     ↓
Confidence Scores
     ↓
Deterministic Validation
     ↓
Human Approval if Required
     ↓
Approved Transformation Rules
     ↓
Runtime Transformation
```

This safety mechanism is a core requirement.

---

# 4. Technology Stack

## Backend

Use **Spring Boot with Java** for the main backend.

Responsibilities:

- API registration
- API version management
- endpoint management
- schema management
- compatibility analysis
- NLP/AI orchestration
- mapping management
- transformation-rule management
- validation
- database access

Use:

- Spring Web
- Spring Data JPA
- Bean Validation
- appropriate JSON processing libraries
- Maven or Gradle

Prefer Maven unless there is a strong reason to use Gradle.

---

## Runtime Gateway

Use **Node.js** for the API gateway/proxy.

Responsibilities:

- receive client API requests
- identify source and target API versions
- retrieve appropriate transformation configuration
- transform requests
- forward requests to target APIs
- receive target responses
- transform responses back to the source format
- return the transformed response to the client

Use Express.js or another lightweight and well-supported Node.js HTTP framework.

---

## AI/NLP

Implement an explicit NLP pipeline.

The system should not be described as simply "calling an AI API."

The project must demonstrate NLP concepts such as:

- field-name normalization
- tokenization
- camelCase/snake_case handling
- abbreviation handling
- lexical similarity
- synonym awareness
- semantic similarity
- contextual comparison
- confidence scoring

Use an external AI/embedding service for advanced semantic understanding if appropriate.

However, design the AI component behind a clean interface so that it can later be replaced by a local model.

Do not make the entire application dependent on one AI provider.

---

## Database

Use PostgreSQL unless there is a strong technical reason otherwise.

Store:

- APIs
- API versions
- endpoints
- schemas
- schema fields
- compatibility analyses
- semantic mappings
- transformation rules
- approval status
- confidence scores

---

## Frontend

Use React.

The frontend should provide a clean developer dashboard.

It should allow users to:

- register APIs
- view APIs
- compare API versions
- view schema differences
- run compatibility analysis
- view semantic mappings
- view confidence scores
- approve/reject mappings
- view generated transformation rules
- test transformations
- view transformation history

The UI should prioritize clarity and usability over unnecessary visual complexity.

---

# 5. High-Level Architecture

Implement the following architecture:

```text
                         ┌───────────────────┐
                         │      React        │
                         │    Dashboard      │
                         └─────────┬─────────┘
                                   │
                                   │ REST
                                   ↓
                         ┌───────────────────┐
                         │    Spring Boot    │
                         │     Backend       │
                         └─────────┬─────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              │                    │                    │
              ↓                    ↓                    ↓
       API Registry          Compatibility         Transformation
                              Engine                  Engine
                                   │
                                   ↓
                             NLP Pipeline
                                   │
                                   ↓
                             AI Semantic
                              Analyzer
                                   │
                                   ↓
                              PostgreSQL


                 Runtime API Traffic

Client
   │
   │ V1 Request
   ↓
┌───────────────────┐
│ Node.js Gateway   │
└─────────┬─────────┘
          │
          ↓
Transformation Engine
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

---

# 6. Functional Requirements

## 6.1 API Registration

The user must be able to register an API.

Required information:

```text
API name
Version
Base URL
Description
```

Example:

```text
Name: Customer API
Version: V1
Base URL: http://localhost:8081
```

and:

```text
Name: Customer API
Version: V2
Base URL: http://localhost:8082
```

---

# 6.2 Endpoint Registration

An API can contain multiple endpoints.

Store:

```text
HTTP method
Path
Request schema
Response schema
```

Example:

```text
POST /customer
GET /customer/{id}
PUT /customer/{id}
```

For the MVP, prioritize POST endpoints with JSON request/response bodies.

---

# 6.3 Schema Analysis

The system must parse JSON schemas and extract:

- field names
- data types
- required/optional status
- nested objects
- arrays
- paths
- enum values where available

Example:

```json
{
  "name": "string",
  "mobile": "string",
  "age": "integer"
}
```

should become an internal representation such as:

```text
name
  type: string
  path: name

mobile
  type: string
  path: mobile

age
  type: integer
  path: age
```

---

# 6.4 NLP Preprocessing

Implement a preprocessing pipeline before semantic analysis.

The pipeline should handle examples such as:

```text
phoneNumber
phone_number
phone-number
PHONE_NO
phone no
```

and normalize them into comparable representations.

Handle:

- camelCase
- PascalCase
- snake_case
- kebab-case
- spaces
- abbreviations
- common API naming conventions

Example:

```text
phoneNumber
```

can become:

```text
phone number
```

and:

```text
mob_no
```

can become:

```text
mobile number
```

where appropriate.

---

# 6.5 Semantic Matching

Compare fields between two API versions.

Example:

```text
V1                         V2

name                  →    fullName
mobile                →    phoneNumber
email                 →    emailAddress
```

Generate a confidence score for each candidate.

Example:

```text
name → fullName
confidence: 0.96

mobile → phoneNumber
confidence: 0.93

email → emailAddress
confidence: 0.95
```

Do not rely exclusively on string similarity.

Use semantic meaning and schema context.

---

# 6.6 Context Awareness

The system must consider context.

For example:

```text
customerId
productId
orderId
```

all contain:

```text
id
```

but they do not necessarily mean the same thing.

Therefore, semantic matching must consider:

- complete field name
- parent object
- field path
- surrounding fields
- data type
- schema structure
- endpoint context

Avoid unsafe mappings based purely on partial name matches.

---

# 6.7 Compatibility Classification

Classify API compatibility.

Use at least:

```text
COMPATIBLE
PARTIALLY_COMPATIBLE
INCOMPATIBLE
```

Example:

```text
Overall compatibility: 94%
Status: COMPATIBLE
```

or:

```text
Overall compatibility: 68%
Status: PARTIALLY_COMPATIBLE
```

---

# 6.8 Transformation Types

The transformation engine must support at least:

### Rename

```text
name → fullName
```

### Move/Nesting

```text
mobile → contact.phone
```

### Flattening

```text
contact.phone → phoneNumber
```

### Combine

```text
firstName + lastName → fullName
```

### Split

```text
fullName → firstName + lastName
```

### Type Conversion

Example:

```text
"21" → 21
```

### Enum Mapping

Example:

```text
male → M
female → F
```

The system must explicitly identify the operation required for each mapping.

---

# 6.9 Transformation Rule Format

Use a structured representation for transformation rules.

Example:

```json
{
  "sourcePath": "name",
  "targetPath": "fullName",
  "operation": "RENAME",
  "confidence": 0.96,
  "status": "APPROVED"
}
```

Nested example:

```json
{
  "sourcePath": "mobile",
  "targetPath": "contact.phone",
  "operation": "MOVE",
  "confidence": 0.91,
  "status": "APPROVED"
}
```

Combination example:

```json
{
  "sourcePaths": [
    "firstName",
    "lastName"
  ],
  "targetPath": "fullName",
  "operation": "CONCATENATE",
  "confidence": 0.94,
  "status": "APPROVED"
}
```

Design the model so additional transformation operations can be added later.

---

# 7. Safety Model

Implement configurable confidence thresholds.

Default:

```text
90% and above
→ eligible for automatic approval

70%–89%
→ human approval required

Below 70%
→ reject/flag
```

These values must be configurable.

The system must never automatically execute a low-confidence transformation.

Before a transformation rule becomes active, validate:

- source field exists
- target field exists
- data types are compatible or convertible
- required fields are not lost
- transformation operation is supported
- mapping is not ambiguous
- confidence threshold is satisfied
- approval status permits execution

---

# 8. Runtime Gateway

The Node.js gateway must provide a proxy mechanism.

Example client request:

```http
POST /proxy/customer/v1
```

with:

```json
{
  "name": "Rishi",
  "mobile": "9876543210"
}
```

The gateway determines:

```text
Source Version = V1
Target Version = V2
```

It retrieves the approved transformation rules.

It transforms the request into:

```json
{
  "fullName": "Rishi",
  "phoneNumber": "9876543210"
}
```

Then forwards the request to the V2 API.

---

# 9. Response Transformation

If V2 responds:

```json
{
  "fullName": "Rishi",
  "phoneNumber": "9876543210"
}
```

the gateway should transform it into:

```json
{
  "name": "Rishi",
  "mobile": "9876543210"
}
```

and return it to the V1 client.

The client should not need to know that the backend is actually using V2.

---

# 10. Framework REST API

Spring Boot should expose APIs similar to:

```http
POST /api/apis
GET /api/apis
GET /api/apis/{id}
```

API management.

```http
POST /api/endpoints
GET /api/endpoints/{id}
```

Endpoint management.

```http
POST /api/compatibility/analyze
GET /api/compatibility/{id}
```

Compatibility analysis.

```http
POST /api/transformations/generate
GET /api/transformations
```

Transformation management.

```http
POST /api/transformations/{id}/approve
POST /api/transformations/{id}/reject
```

Approval workflow.

```http
POST /api/transform/test
```

Test a transformation without forwarding it to an external API.

Design the API cleanly using REST conventions.

---

# 11. Example Compatibility Analysis Request

Example:

```json
{
  "sourceApiVersionId": 1,
  "targetApiVersionId": 2
}
```

Response:

```json
{
  "status": "PARTIALLY_COMPATIBLE",
  "overallScore": 0.91,
  "mappings": [
    {
      "source": "name",
      "target": "fullName",
      "confidence": 0.96
    },
    {
      "source": "mobile",
      "target": "phoneNumber",
      "confidence": 0.93
    }
  ]
}
```

---

# 12. Example Demonstration APIs

Create two mock REST APIs for demonstration.

## Customer API V1

Endpoint:

```http
POST /v1/customer
```

Request:

```json
{
  "name": "Rishi",
  "mobile": "9876543210",
  "email": "rishi@example.com"
}
```

Response:

```json
{
  "id": 101,
  "name": "Rishi",
  "mobile": "9876543210",
  "email": "rishi@example.com"
}
```

---

## Customer API V2

Endpoint:

```http
POST /v2/customer
```

Request:

```json
{
  "fullName": "Rishi",
  "phoneNumber": "9876543210",
  "emailAddress": "rishi@example.com"
}
```

Response:

```json
{
  "customerId": 101,
  "fullName": "Rishi",
  "phoneNumber": "9876543210",
  "emailAddress": "rishi@example.com"
}
```

The system should identify mappings such as:

```text
name       → fullName
mobile     → phoneNumber
email      → emailAddress
id         → customerId
```

Do not assume that every mapping is safe solely because names are similar. Validate each mapping.

---

# 13. Project Structure

Use a clean monorepo structure similar to:

```text
semantic-api-compatibility/
│
├── backend/
│   ├── src/
│   ├── pom.xml
│   └── README.md
│
├── gateway/
│   ├── src/
│   ├── package.json
│   └── README.md
│
├── frontend/
│   ├── src/
│   ├── package.json
│   └── README.md
│
├── mock-apis/
│   ├── api-v1/
│   └── api-v2/
│
├── database/
│   └── migrations/
│
├── docs/
│   ├── architecture.md
│   ├── api-specification.md
│   └── transformation-rules.md
│
├── docker-compose.yml
└── README.md
```

Keep modules independently runnable where practical.

---

# 14. Error Handling

Implement proper error handling.

Examples:

```text
400 → Invalid transformation request
404 → API/version/rule not found
409 → Conflicting mapping
422 → Transformation cannot be safely executed
502 → Target API unavailable
500 → Internal server error
```

Do not silently modify or discard data.

Return useful error messages.

---

# 15. Logging

Log important runtime events.

Example:

```text
Request received
Source API: Customer V1
Target API: Customer V2
Transformation rule: customer-v1-to-v2
Transformation successful
Target API response received
Response transformation successful
```

Do not log sensitive authentication tokens or unnecessary personal information.

---

# 16. Testing

Create tests for:

### Semantic matching

```text
name ↔ fullName
mobile ↔ phoneNumber
```

### Non-matching fields

```text
price ↔ quantity
```

should not be accepted.

### Rename transformation

```text
name → fullName
```

### Nested transformation

```text
mobile → contact.phone
```

### Combination

```text
firstName + lastName → fullName
```

### Type conversion

```text
"21" → 21
```

### Enum conversion

```text
male → M
```

### Low-confidence mappings

Must not automatically execute.

### Missing fields

Must return an appropriate error.

### Invalid transformations

Must be rejected.

### End-to-end gateway

Test:

```text
V1 Client
→ Node Gateway
→ Transformation
→ V2 API
→ Response Transformation
→ V1 Client
```

---

# 17. Frontend Requirements

Create these main screens:

## Dashboard

Show:

- registered APIs
- API versions
- compatibility status
- recent analyses
- active transformations

## API Management

Allow:

- register API
- add version
- add endpoint
- view schema

## Compatibility Analysis

Show:

```text
Source API: V1
Target API: V2

Field Mapping
────────────────────────────
name        → fullName       96%
mobile      → phoneNumber    93%
email       → emailAddress   95%
```

Use clear visual indicators for:

- high confidence
- medium confidence
- low confidence

## Transformation Rules

Show:

```text
Source
Target
Operation
Confidence
Status
```

Allow:

```text
Approve
Reject
Disable
```

## Transformation Tester

Allow the developer to provide sample JSON and see:

```text
Original JSON
      ↓
Transformation
      ↓
Transformed JSON
```

without needing to call the target API.

---

# 18. Security

For the MVP, implement basic security practices.

Do not hard-code:

- AI API keys
- database passwords
- authentication tokens

Use environment variables.

Example:

```text
AI_API_KEY
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
```

Add `.env.example` where appropriate.

Do not commit secrets to Git.

---

# 19. Docker

Provide Docker configuration where practical.

The project should ideally be startable using:

```bash
docker compose up
```

with services such as:

```text
frontend
backend
gateway
database
mock-api-v1
mock-api-v2
```

If AI requires an external provider, document the required environment variable.

---

# 20. Important Implementation Rules

### Rule 1

Do not build fake AI functionality and describe it as real semantic analysis.

### Rule 2

Do not make the LLM directly execute arbitrary transformations.

### Rule 3

Transformation rules must be structured and deterministic.

### Rule 4

AI-generated mappings must be validated.

### Rule 5

Low-confidence mappings must not be automatically activated.

### Rule 6

The system must preserve data meaning during transformation.

### Rule 7

Do not over-engineer the first version.

Build a working MVP before adding advanced features.

---

# 21. Development Strategy

Build the system incrementally.

Do NOT attempt to generate all modules at once.

Use these phases:

## Phase 1 — Foundation

Create:

- monorepo
- Spring Boot backend
- Node.js gateway
- React frontend
- PostgreSQL
- basic Docker configuration

Verify that every service starts successfully.

---

## Phase 2 — Mock APIs

Create V1 and V2 Customer APIs.

Verify them independently using Postman/cURL.

---

## Phase 3 — API Registry

Implement API and endpoint registration.

Persist the information in PostgreSQL.

---

## Phase 4 — Schema Analyzer

Implement JSON schema parsing and field extraction.

---

## Phase 5 — NLP Pipeline

Implement normalization, tokenization, lexical similarity, and related preprocessing.

---

## Phase 6 — AI Semantic Matching

Integrate the AI/embedding component behind a clean service interface.

Return candidate mappings and confidence scores.

---

## Phase 7 — Compatibility Engine

Combine semantic similarity with:

- data type compatibility
- structural compatibility
- required-field checks
- contextual information

Generate a final compatibility result.

---

## Phase 8 — Transformation Generator

Generate structured transformation rules.

---

## Phase 9 — Safety & Approval

Implement:

- confidence thresholds
- validation
- approval/rejection
- rule activation

---

## Phase 10 — Runtime Gateway

Connect Node.js gateway to the transformation rules.

Implement:

```text
V1 request
→ transformation
→ V2 request
```

---

## Phase 11 — Response Transformation

Implement:

```text
V2 response
→ transformation
→ V1 response
```

---

## Phase 12 — Frontend

Complete the developer dashboard.

---

## Phase 13 — Testing

Perform unit, integration, and end-to-end testing.

---

# 22. First Task

Do NOT immediately implement the entire project.

Start with **Phase 1 only**.

First:

1. Create the monorepo.
2. Create the Spring Boot backend.
3. Create the Node.js gateway.
4. Create the React frontend.
5. Configure PostgreSQL.
6. Configure Docker Compose if practical.
7. Create health-check endpoints for each service.
8. Verify that all services communicate correctly.
9. Create a clear README explaining how to run the project.

After Phase 1 is complete, stop and report:

```text
Phase 1 completed
Services created
Ports
Database connection
Health-check results
Remaining phases
```

Then wait for approval before proceeding to Phase 2.

Do not silently skip phases.

---

# 23. Expected Final Demonstration

The final demonstration should show this complete scenario:

```text
1. Register Customer API V1
2. Register Customer API V2
3. Display their schemas
4. Run compatibility analysis
5. NLP/AI identifies semantic mappings
6. Show confidence scores
7. Generate transformation rules
8. Validate rules
9. Approve safe mappings
10. Send a V1 request
11. Node.js gateway intercepts it
12. Transform V1 request to V2
13. Send transformed request to V2
14. Receive V2 response
15. Transform V2 response back to V1
16. Return response to original client
17. Display logs/results
```

The final system should clearly demonstrate that the client can continue using the old API contract while the backend uses the new API contract.

---

# 24. Success Criteria

The project is considered successful when:

1. Two API versions with different schemas can be registered.
2. Their schemas can be analyzed.
3. NLP preprocessing is demonstrably used.
4. AI/semantic analysis identifies meaningful equivalent fields.
5. The system produces confidence scores.
6. Unsafe mappings are rejected or require approval.
7. Transformation rules can be generated.
8. Requests can be transformed automatically.
9. Responses can be transformed automatically.
10. The Node.js gateway can proxy the transformed request.
11. The Spring Boot backend manages API compatibility information.
12. The React dashboard allows developers to inspect and manage the process.
13. The complete V1 → V2 → V1 workflow works end-to-end.

---

# 25. Development Philosophy

Prioritize:

```text
Correctness
>
Reliability
>
Understandability
>
Security
>
Feature count
```

Build a working, explainable system rather than an unnecessarily complex one.

Every major architectural decision should be documented so that the project can be explained during a college presentation and viva.

Begin with Phase 1 only.