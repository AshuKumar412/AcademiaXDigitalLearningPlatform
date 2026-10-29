# AcademiaX – Higher Education Course Enrollment & Tuition Processing System

A production-grade, microservices-based digital learning and tuition platform built with **Java 25**, **Spring Boot 4.1.1**, **Spring Cloud 2025.1.3**, **PostgreSQL 18**, and **JWT Authentication**.

Featuring **Saga Transaction Orchestration**, **Atomic Concurrency Control**, **Payment Idempotency & Reconciliation**, and **Multi-Layer Role-Based Access Control (RBAC)**.

---

## Architecture Overview

```
                      [ Client (Browser / Mobile / Postman) ]
                                         │
                                         ▼
                              ┌──────────────────────┐
                              │  API Gateway (:8080) │ (JWT Validation, Header Sanitization & Routing)
                              └──────────┬───────────┘
                                         │
       ┌───────────────┬─────────────────┼─────────────────┬─────────────────┐
       ▼               ▼                 ▼                 ▼                 ▼
┌──────────────┐┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Auth Service ││ User Service │  │Course Service│  │  Enrollment  │  │Payment Service│
│   (:8081)    ││   (:8082)    │  │   (:8083)    │  │Service (:8084│  │   (:8085)    │
└──────┬───────┘└──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘
       │               │                 │                 │                 │
       └───────────────┴─────────────────┼─────────────────┴─────────────────┘
                                         ▼
                             ┌──────────────────────┐
                             │ Eureka Server (:8761)│ (Service Discovery & Health Registry)
                             └──────────────────────┘
                                         ▲
                                         │
                             ┌──────────────────────┐
                             │ PostgreSQL 18 DB     │ (Central ACID Shared DB: academiax_db)
                             └──────────────────────┘
```

---

## Microservices Catalog

| Service | Port | Primary Responsibilities |
|---|---|---|
| **eureka-server** | `8761` | Service registration, heartbeat monitoring, load-balanced discovery. |
| **api-gateway** | `8080` | Edge routing, JWT signature verification, strips forged identity headers, injects trusted `X-User-Id` / `X-User-Role`. |
| **auth-service** | `8081` | User registration (case-insensitive uniqueness), authentication, BCrypt password hashing, JWT token issuance. |
| **user-service** | `8082` | Student and instructor profile lifecycle, self-profile privacy enforcement (`STUDENT`/`INSTRUCTOR`/`ADMIN`). |
| **course-service** | `8083` | Course catalog, instructor assignment validation, atomic DB seat reservation & release, capacity constraints. |
| **enrollment-service** | `8084` | Distributed Saga orchestrator, manages transitions (`PENDING_PAYMENT`, `ENROLLED`, `PAYMENT_FAILED`, `CANCELLED`), background reconciliation worker. |
| **payment-service** | `8085` | Idempotent tuition payments, deterministic mock test modes (`SUCCESS`, `FAIL`, `TIMEOUT`, `UNKNOWN`), payment reconciliation, status lockdown. |

---

## Key Engineering Capabilities

### 1. Distributed Saga Orchestration & Rollback
Enrollment follows an orchestrated Saga state machine:
1. **Validation**: Verifies authenticated student and target course.
2. **Atomic Seat Reservation**: Atomically reserves a seat in Course Service (`UPDATE courses SET enrolled_count = enrolled_count + 1 WHERE id = :id AND enrolled_count < capacity AND status = 'OPEN'`).
3. **Pending Enrollment**: Persists enrollment in `PENDING_PAYMENT` with a unique idempotency key.
4. **Idempotent Payment Initiation**: Invokes Payment Service with the idempotency key.
   - **Payment Success**: Enrollment immediately transitions to `ENROLLED`.
   - **Definitive Failure**: Enrollment transitions to `PAYMENT_FAILED` and triggers **compensating seat release** (`UPDATE courses SET enrolled_count = enrolled_count - 1 WHERE id = :id AND enrolled_count > 0`).
   - **Timeout / Unknown**: Enrollment remains in `PENDING_PAYMENT` with seat reserved. An automated background worker periodically reconciles pending payments and finalizes or rolls back state.

### 2. Multi-Layer RBAC & Security Hardening
- **Edge Header Sanitization**: API Gateway strips all client-supplied `X-User-Id` and `X-User-Role` headers before JWT validation, preventing spoofing.
- **Downstream Verification**: Every downstream microservice independently verifies JWT signatures, ensuring requests directly accessing ports `8081`–`8085` cannot bypass authorization.
- **Feign Identity Propagation**: Outgoing Feign requests carry `Authorization: Bearer <token>` and trusted identity headers across internal service boundaries.
- **Role Permissions**:
  - `STUDENT`: Can view courses, enroll themselves, view own enrollments, and check own payments.
  - `INSTRUCTOR`: Can create courses assigned to them, manage own courses.
  - `ADMIN`: Full management access across users, courses, enrollments, and payment reconciliations.

---

## API Quick Reference

### Authentication (`/auth`)
- `POST /auth/register` — Register student or instructor (`username`, `email`, `password`, `role`).
- `POST /auth/login` — Authenticate and receive signed JWT.

### Users (`/users`)
- `POST /users` — Create user record (`studentId` or `instructorId`).
- `GET /users` — List all users (ADMIN only).
- `GET /users/{userId}` — Get profile (Self or ADMIN).
- `GET /users/{userId}/exists` — Service-to-service validation.
- `PUT /users/{userId}` — Update user profile (Self or ADMIN).
- `DELETE /users/{userId}` — Delete user (ADMIN only).

### Courses (`/courses`)
- `GET /courses` — List all courses (Authenticated users).
- `GET /courses/{id}` — Get course details by ID.
- `POST /courses` — Create course (INSTRUCTOR or ADMIN; validates instructor existence).
- `PUT /courses/{id}` — Update course (Owning INSTRUCTOR or ADMIN).
- `DELETE /courses/{id}` — Delete course (Owning INSTRUCTOR or ADMIN).
- `POST /courses/{id}/reserve-seat` — Atomic seat reservation (Internal / Feign).
- `POST /courses/{id}/release-seat` — Atomic seat compensation (Internal / Feign).

### Enrollments (`/enrollments`)
- `POST /enrollments` — Enroll student (Executes Saga workflow; auto-initiates payment).
- `GET /enrollments` — List enrollments (STUDENT sees own; ADMIN sees all).
- `GET /enrollments/{id}` — Get enrollment by ID (Student owner or ADMIN).
- `GET /enrollments/{id}/exists` — Service-to-service validation.
- `POST /enrollments/{id}/cancel` — Cancel enrollment and release reserved seat.
- `POST /enrollments/{id}/reconcile` — Force reconciliation of pending payment.

### Payments (`/payments`)
- `POST /payments` — Idempotent payment creation (accepts optional header `X-Mock-Payment-Scenario: SUCCESS | FAIL | TIMEOUT | UNKNOWN`).
- `GET /payments` — List payments (ADMIN only).
- `GET /payments/{id}` — Get payment details (Owner or ADMIN).
- `GET /payments/by-enrollment/{enrollmentId}` — Find payment by enrollment ID.
- `POST /payments/reconcile/{enrollmentId}` — Reconcile pending payment status.
- `PUT /payments/{id}` — Update payment status (ADMIN only, preventing client tampering).

---

## Quickstart & Deployment

### Option 1: Run with Docker Compose (Recommended)

1. Copy `.env.example` to `.env` and configure secrets:
   ```bash
   cp .env.example .env
   ```

2. Build and launch all services:
   ```bash
   docker compose up --build -d
   ```

3. Check service health:
   ```bash
   docker compose ps
   ```

4. View logs:
   ```bash
   docker compose logs -f
   ```

---

### Option 2: Run Locally (Native)

#### Prerequisites
- Java 25 SDK
- Maven 3.9+
- PostgreSQL 18 running locally with database `academiax_db`

#### Service Startup Sequence
```bash
# 1. Eureka Server (Wait ~10s for initialization)
cd eureka-server && ./mvnw spring-boot:run

# 2. Domain Microservices
cd auth-service       && ./mvnw spring-boot:run
cd user-service       && ./mvnw spring-boot:run
cd course-service     && ./mvnw spring-boot:run
cd payment-service    && ./mvnw spring-boot:run
cd enrollment-service && ./mvnw spring-boot:run

# 3. Edge Gateway
cd api-gateway        && ./mvnw spring-boot:run
```

---

## Automated Verification & Test Suite

The project includes 53 comprehensive unit and integration tests across all microservices covering Saga rollbacks, atomic concurrency, idempotency, and RBAC authorization:

```bash
# Run test suite in all microservices
cd auth-service       && ./mvnw test   # 6 tests passed
cd api-gateway        && ./mvnw test   # 7 tests passed
cd user-service       && ./mvnw test   # 5 tests passed
cd course-service     && ./mvnw test   # 11 tests passed
cd enrollment-service && ./mvnw test   # 13 tests passed
cd payment-service    && ./mvnw test   # 10 tests passed
cd eureka-server      && ./mvnw test   # 1 test passed
```

**Total Tests**: 53 run, 53 passed, 0 failures, 0 errors.

---

## License

Academic & Higher Education Project — AcademiaX Digital Learning Platform.
