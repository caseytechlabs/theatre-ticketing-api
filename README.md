# Theatre Ticketing API

Spring Boot REST API for managing theatre vouchers and bookings, backed entirely by Redis.

## Tech Stack

- **Java 21** + Spring Boot 3
- **Redis** — sole data store (Hashes + Sets, no SQL)
- **Spring Security 6** + JWT (HMAC-SHA256)
- **Lua scripts** — atomic voucher state transitions

## Prerequisites

- Java 21+
- Redis running on `localhost:6379`

## Running

```bash
./gradlew bootRun
```

API available at `http://localhost:8080`  
Swagger UI at `http://localhost:8080/swagger-ui.html`

## Default Users (seeded on startup)

| Username | Password | Role   |
|----------|----------|--------|
| admin    | admin123 | ADMIN  |
| client1  | client123| CLIENT |
| client2  | client123| CLIENT |

## Voucher Lifecycle

```
AVAILABLE → PENDING_CLAIM → CLAIMED
              └── (10-min TTL or payment failure) → AVAILABLE
```

State transitions are enforced atomically via Lua scripts. CLAIMED vouchers are kept permanently as audit records.

## Key Endpoints

| Method | Path | Access | Description |
|--------|------|--------|-------------|
| POST | `/api/v1/auth/login` | Public | Get JWT token |
| GET | `/api/v1/vouchers/my` | CLIENT | My available vouchers |
| GET | `/api/v1/vouchers` | ADMIN | All vouchers |
| POST | `/api/v1/vouchers` | ADMIN | Create voucher |
| POST | `/api/v1/bookings/initiate` | Authenticated | Reserve a voucher |
| POST | `/api/v1/bookings/confirm` | Authenticated | Confirm/decline payment |
| GET | `/api/v1/bookings/my` | CLIENT | My bookings |
| GET | `/api/v1/bookings` | ADMIN | All bookings |

## Frontend

The React/TypeScript frontend lives in `theatre-ticketing-portal`. Run separately:

```bash
cd ../theatre-ticketing-portal
npm install
npm run dev
```

Frontend at `http://localhost:5173`
