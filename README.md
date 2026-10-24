# TraceGuard: Real-Time Exception Deduplication, Dead-Letter Replay & AI Root-Cause Triage Engine  Live Link - https://traceguard-engine-l49zqktk4-pandadebansh-7753.vercel.app/

![Java 21](https://img.shields.io/badge/Java-21_LTS-orange)
![Spring Boot 3.3](https://img.shields.io/badge/Spring_Boot-3.3.4-brightgreen)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-blue)
![React 18](https://img.shields.io/badge/React-18_Vite-61DAFB)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-v4-38B2AC)

**TraceGuard** is an event-driven developer observability and reliability engine built with **Java 21**, **Spring Boot 3.3**, **PostgreSQL**, **Server-Sent Events (`SseEmitter`)**, **Google Gemini REST API**, and **React + Tailwind CSS**.

Instead of flooding engineering teams with thousands of duplicate log lines during an outage, TraceGuard normalizes incoming stack traces, computes deterministic **SHA-256 fingerprints**, collapses recurring spikes into atomic incident groups, streams live updates over persistent HTTP (`SSE`) connections, synthesizes cached **AI root-cause code patches**, and provides a **1-click Dead-Letter Webhook Replay** console.

---

## Key Engineering Highlights

1. **SHA-256 Stack-Trace Fingerprinting & Noise Reduction (`FingerprintService`)**:
   - Strips volatile lambda memory addresses (`$$Lambda$...`, `@4f8a9c`) from the top 5 stack frames and hashes `(serviceName, exceptionType, endpointPath, normalizedFrames)` into a 24-character SHA-256 hex digest.
   - Deduplicates recurring exception bursts (`ObjectOptimisticLockingFailureException`, `SQLTransientConnectionException`) into a single indexed PostgreSQL row with atomic `occurrenceCount`, `firstSeenAt`, and `lastSeenAt` tracking.
   - Automatically re-opens `RESOLVED` incidents if a regression spike with the same fingerprint is ingested.
2. **Zero-Polling Real-Time Streaming (`SseHubService` + Java 21 Virtual Threads)**:
   - Uses Spring MVC `SseEmitter` backed by `CopyOnWriteArrayList` and `spring.threads.virtual.enabled=true` to broadcast live incident ingestion and status transitions (`OPEN` -> `INVESTIGATING` -> `RESOLVED`) to connected React clients.
3. **Cached AI Root-Cause & Patch Synthesis (`GeminiTriageService`)**:
   - Uses Spring Boot 3's fluent `RestClient` to query the Google Gemini `generateContent` REST endpoint for structured JSON root-cause explanations and Spring Boot code fixes.
   - Caches the generated patch inside PostgreSQL by SHA-256 fingerprint so repeat spikes incur zero extra LLM latency or token cost.
   - Includes a deterministic offline SRE fallback engine when `GEMINI_API_KEY` is not set.
4. **Dead-Letter Payload Inspector & One-Click Replay**:
   - Captures failed webhook/HTTP JSON payloads alongside stack traces, allowing operators to edit the JSON payload in the dashboard and replay it while recording HTTP status and latency (`ms`).
5. **RFC 9457 Standardized Error Contracts (`GlobalExceptionHandler`)**:
   - Returns machine-readable `ProblemDetail` (`application/problem+json`) responses for validation and missing-resource errors.

---

## Project Structure

```text
traceguard/
├── .env.example
├── .gitignore
├── README.md
├── docker-compose.yml
├── .vscode/
│   ├── extensions.json
│   └── launch.json
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/debansh/traceguard/
│       │   │   ├── TraceGuardApplication.java
│       │   │   ├── config/WebConfig.java
│       │   │   ├── controller/IncidentController.java
│       │   │   ├── dto/
│       │   │   │   ├── DashboardSummaryDto.java
│       │   │   │   ├── ReplayRequest.java
│       │   │   │   └── TelemetryIngestRequest.java
│       │   │   ├── entity/
│       │   │   │   ├── IncidentGroup.java
│       │   │   │   ├── IncidentStatus.java
│       │   │   │   └── SeverityLevel.java
│       │   │   ├── exception/
│       │   │   │   ├── GlobalExceptionHandler.java
│       │   │   │   └── IncidentNotFoundException.java
│       │   │   ├── repository/IncidentGroupRepository.java
│       │   │   └── service/
│       │   │       ├── FingerprintService.java
│       │   │       ├── GeminiTriageService.java
│       │   │       ├── IncidentService.java
│       │   │       └── SseHubService.java
│       │   └── resources/application.yml
│       └── test/
│           └── java/com/debansh/traceguard/
│               └── IncidentControllerIntegrationTest.java
└── frontend/
    ├── Dockerfile
    ├── index.html
    ├── package.json
    ├── vite.config.js
    └── src/
        ├── App.jsx
        ├── index.css
        └── main.jsx
```

---

## Quickstart in VS Code

### Prerequisites
- **Java 21 LTS** ([Eclipse Temurin JDK 21](https://adoptium.net/temurin/releases))
- **Maven 3.9+** (`mvn -v`)
- **Node.js 20+** (`node -v` and `npm -v`)
- **Docker Desktop** (optional — you can run with the built-in `local` H2 profile if Docker is not running)

### Step 1: Start Database (or Skip for Local H2 Mode)
```bash
docker compose up -d
```

### Step 2: Run the Spring Boot 3.3 Backend
Open a terminal in `backend/`:
```bash
cd backend
# With Docker PostgreSQL running:
mvn spring-boot:run

# OR without Docker (uses in-memory H2 in PostgreSQL compatibility mode):
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```
*(Optional: Set `export GEMINI_API_KEY="your_key"` before running to enable live Google Gemini API calls; otherwise the built-in deterministic SRE engine runs automatically).*

### Step 3: Run the React + Tailwind CSS Frontend
Open a second terminal in `frontend/`:
```bash
cd frontend
npm install
npm run dev
```
Open **http://localhost:5173** in your browser:
1. Click **"Simulate Production Chaos"** in the top-right bar to fire 6 microservice exceptions across 3 services and watch them deduplicate live via `SseEmitter` (`50% noise reduction`).
2. Select any incident group and click **"Generate AI Root-Cause & Patch"**.
3. Edit the captured JSON body in the **Dead-Letter Payload Inspector** and click **"Replay Payload & Mark Resolved"**.

### Step 4: Run JUnit 5 & MockMvc Integration Tests
```bash
cd backend
mvn test
```

---

## REST API Reference

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/incidents/ingest` | Ingest a raw exception & dead-letter payload; computes SHA-256 fingerprint and broadcasts via SSE |
| `GET` | `/api/v1/incidents` | List deduplicated incident groups ordered by `lastSeenAt DESC` (optional `?status=OPEN`) |
| `GET` | `/api/v1/incidents/summary` | Retrieve total raw events, unique groups, noise-reduction %, active criticals, and resolved count |
| `GET` | `/api/v1/incidents/stream` | Subscribe to real-time `text/event-stream` (`SseEmitter`) incident updates |
| `POST` | `/api/v1/incidents/{id}/analyze` | Generate (or return cached) AI root-cause diagnosis & Spring Boot code patch |
| `POST` | `/api/v1/incidents/{id}/replay` | Replay modified dead-letter JSON payload, record HTTP latency, and resolve incident |
| `PATCH` | `/api/v1/incidents/{id}/status` | Transition incident state (`OPEN`, `INVESTIGATING`, `RESOLVED`) |
| `POST` | `/api/v1/incidents/simulate-chaos` | Fire realistic multi-service production exception bursts for instant live demo |
