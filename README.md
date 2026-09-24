# AcademiaX – Higher Education Course Enrollment & Tuition Processing System

A complete microservices-based digital learning platform built with **Java 25**, **Spring Boot 4**, **Spring Cloud**, **PostgreSQL**, and **JWT authentication**.

---

## Architecture

```
Client
  └── API Gateway (8080)
        ├── Auth Service (8081)
        ├── User Service (8082)
        ├── Course Service (8083)
        ├── Enrollment Service (8084)
        └── Payment Service (8085)

Eureka Server (8761) — Service Discovery
```

---

## Microservices

| Service | Port | Responsibility |
|---|---|---|
| eureka-server | 8761 | Service registry & discovery |
| api-gateway | 8080 | JWT validation, routing |
| auth-service | 8081 | Register, login, JWT generation |
| user-service | 8082 | Student & Instructor management |
| course-service | 8083 | Course management (validates instructor) |
| enrollment-service | 8084 | Enrollment (validates student+course, auto-creates payment) |
| payment-service | 8085 | Tuition payment management |

---

## Technology Stack

- **Java 25**
- **Spring Boot 4.1.1**
- **Spring Cloud 2025.1.3**
- **Spring Security + JWT (JJWT)**
- **Spring Data JPA / Hibernate**
- **PostgreSQL 18**
- **Netflix Eureka** (Service Discovery)
- **Spring Cloud Gateway** (API Gateway)
- **OpenFeign** (Service-to-Service HTTP)
- **Maven** (Build tool)

---

## Service-to-Service Validation

```
POST /courses       → validates instructorId exists   → User Service
POST /enrollments   → validates studentId exists       → User Service
                    → validates courseId exists        → Course Service
                    → prevents duplicate enrollment
                    → auto-creates PENDING payment     → Payment Service
POST /payments      → validates enrollmentId exists    → Enrollment Service
                    → prevents duplicate payment per enrollment
```

---

## Database Schema (PostgreSQL)

Shared database: `academiax_db`

Key tables:
- `users` — `user_id VARCHAR PRIMARY KEY` (= studentId or instructorId, manually set)
- `courses` — `id BIGINT PK`, `instructor_id VARCHAR` (references user_id)
- `enrollments` — `id BIGINT PK`, UNIQUE(`student_id`, `course_id`)
- `payments` — `id BIGINT PK`, UNIQUE(`enrollment_id`)

---

## API Endpoints

### Auth Service
| Method | Path | Description |
|---|---|---|
| POST | /auth/register | Register a user |
| POST | /auth/login | Login and get JWT |

### User Service
| Method | Path | Description |
|---|---|---|
| POST | /users | Create student or instructor |
| GET | /users | List all users |
| GET | /users/{userId} | Get user by ID |
| GET | /users/{userId}/exists | Existence check (used internally) |
| PUT | /users/{userId} | Update user |
| DELETE | /users/{userId} | Delete user |

### Course Service
| Method | Path | Description |
|---|---|---|
| POST | /courses | Create course (validates instructor) |
| GET | /courses | List all courses |
| GET | /courses/{id} | Get course by ID |
| PUT | /courses/{id} | Update course |
| DELETE | /courses/{id} | Delete course |

### Enrollment Service
| Method | Path | Description |
|---|---|---|
| POST | /enrollments | Enroll student (validates student+course) |
| GET | /enrollments | List all enrollments |
| GET | /enrollments/{id} | Get enrollment by ID |
| GET | /enrollments/{id}/exists | Existence check (used internally) |
| PUT | /enrollments/{id} | Update enrollment |
| DELETE | /enrollments/{id} | Delete enrollment |

### Payment Service
| Method | Path | Description |
|---|---|---|
| POST | /payments | Create payment (validates enrollment, prevents duplicates) |
| GET | /payments | List all payments |
| GET | /payments/{id} | Get payment by payment ID |
| GET | /payments/by-enrollment/{enrollmentId} | Find payment by enrollment |
| PUT | /payments/{id} | Update payment status (e.g. PENDING → PAID) |
| DELETE | /payments/{id} | Delete payment |

---

## User Identifier Design

| Role | ID Field | Example |
|---|---|---|
| STUDENT | `studentId` (manually supplied) | `"2400032678"` |
| INSTRUCTOR | `instructorId` (manually supplied) | `"INS001"` |

No auto-generated IDs for users. The `userId` in the database PK equals the manually supplied role-specific ID.

---

## Running Locally

### Prerequisites
- Java 25
- Maven 3.9+
- PostgreSQL 18 (create database: `academiax_db`)

### Environment Variables (optional — defaults provided)
```
DB_URL=jdbc:postgresql://localhost:5432/academiax_db
DB_USERNAME=postgres
DB_PASSWORD=your_password
JWT_SECRET=YourJWTSecretKey
```

### Startup Order
```bash
# 1. Start Eureka Server
cd eureka-server && ./mvnw spring-boot:run

# 2. Start remaining services (any order after Eureka)
cd auth-service      && ./mvnw spring-boot:run
cd user-service      && ./mvnw spring-boot:run
cd course-service    && ./mvnw spring-boot:run
cd enrollment-service && ./mvnw spring-boot:run
cd payment-service   && ./mvnw spring-boot:run
cd api-gateway       && ./mvnw spring-boot:run
```

### Build All
```bash
# In each service directory:
./mvnw package -DskipTests
```

---

## Typical Workflow

```
1. POST /auth/register   → register
2. POST /auth/login      → get JWT
3. POST /users           → create student (studentId = "2400032678")
4. POST /users           → create instructor (instructorId = "INS001")
5. POST /courses         → create course with instructorId = "INS001"
6. POST /enrollments     → enroll student (auto-creates PENDING payment)
7. GET  /payments/by-enrollment/{enrollmentId}  → find the payment
8. PUT  /payments/{paymentId}                   → update status to PAID
```

---

## Error Codes

| HTTP | Code | Meaning |
|---|---|---|
| 400 | BAD_REQUEST | Missing/invalid input |
| 401 | Unauthorized | Missing or invalid JWT |
| 404 | USER_NOT_FOUND | User not found |
| 404 | COURSE_NOT_FOUND | Course not found |
| 404 | ENROLLMENT_NOT_FOUND | Enrollment not found |
| 404 | PAYMENT_NOT_FOUND | Payment not found |
| 404 | INSTRUCTOR_NOT_FOUND | Instructor doesn't exist when creating course |
| 404 | REFERENCE_NOT_FOUND | Student/course doesn't exist at enrollment |
| 409 | CONFLICT | Duplicate user ID |
| 409 | ALREADY_ENROLLED | Student already enrolled in that course |
| 409 | DUPLICATE_PAYMENT | Payment already exists for this enrollment |
| 503 | SERVICE_ERROR | Downstream service unavailable |

---

## License

Academic Project — AcademiaX Digital Learning Platform
