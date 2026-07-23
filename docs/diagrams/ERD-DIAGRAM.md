# Diagrama Entidad-Relación

Modelo de datos del sistema Menta Dance con boundaries de módulos.

## Visión General por Módulos

```mermaid
flowchart TB
    subgraph AUTH["Auth Module (auth_*)"]
        direction TB
        auth_users
        auth_roles
        auth_users_roles
        auth_refresh_tokens
        auth_revoked_access_tokens
        auth_password_reset_tokens
        auth_security_state
        auth_outbox
    end

    subgraph BILLING["Billing Module (billing_*)"]
        direction TB
        billing_plans
        billing_plan_courses
        billing_subscriptions
        billing_payments
        billing_payment_proofs
        billing_purchases
        billing_quotes
        billing_webhook_inbox
        billing_reconciliation_tasks
        billing_audit_log
        billing_physical_course_pricing
        billing_physical_course_pricing_revisions
    end

    subgraph VIRTUAL["Virtual Module (virtual_*)"]
        direction TB
        virtual_courses
        virtual_modules
        virtual_lessons
        virtual_videos
        virtual_lesson_materials
        virtual_lesson_progress
        virtual_course_progress
        virtual_course_audit
    end

    subgraph PHYSICAL["Physical Module (physical_*)"]
        direction TB
        physical_courses
        physical_sessions
        physical_capacity_assignments
        physical_capacity_holds
        physical_attendance
        physical_qr_devices
    end

    AUTH -.->|"user_id (valor)"| BILLING
    AUTH -.->|"user_id (valor)"| VIRTUAL
    AUTH -.->|"user_id (valor)"| PHYSICAL
    BILLING -.->|"course_id (valor)"| PHYSICAL

    style AUTH fill:#e3f2fd
    style BILLING fill:#fff3e0
    style VIRTUAL fill:#e8f5e9
    style PHYSICAL fill:#fce4ec
```

**Nota**: Las líneas punteadas indican referencias por valor (no FK). Cada módulo solo tiene FKs hacia sus propias tablas.

---

## Auth Module

```mermaid
erDiagram
    auth_users {
        bigint id PK
        varchar email UK
        varchar password_hash
        varchar name
        int token_version
        boolean active
        timestamp created_at
        timestamp updated_at
    }

    auth_roles {
        bigint id PK
        varchar name UK "ALUMNO|PROFESOR|RECEPCIONISTA|ADMIN"
    }

    auth_users_roles {
        bigint user_id PK,FK
        bigint role_id PK,FK
    }

    auth_refresh_tokens {
        bigint id PK
        bigint user_id FK
        char token_hash UK "SHA-256"
        uuid family_id
        timestamp issued_at
        timestamp expires_at
        timestamp revoked_at
        timestamp rotated_at
        varchar device_info
    }

    auth_revoked_access_tokens {
        varchar jti PK
        timestamp expires_at
        timestamp revoked_at
    }

    auth_password_reset_tokens {
        bigint id PK
        bigint user_id FK
        char token_hash UK
        timestamp expires_at
        timestamp used_at
    }

    auth_security_state {
        varchar key PK "SINGLETON"
        varchar state "NORMAL|AUTH_DEGRADED"
        timestamp updated_at
    }

    auth_outbox {
        bigint id PK
        varchar event_type
        json payload
        varchar status "PENDING|SENT|FAILED"
        int attempt_count
        timestamp next_attempt_at
        timestamp created_at
    }

    auth_users ||--o{ auth_users_roles : has
    auth_roles ||--o{ auth_users_roles : assigned_to
    auth_users ||--o{ auth_refresh_tokens : owns
    auth_users ||--o{ auth_password_reset_tokens : requests
```

---

## Billing Module

```mermaid
erDiagram
    billing_plans {
        bigint id PK
        varchar name
        varchar type "VIRTUAL_MONTHLY|PHYSICAL_MONTHLY"
        decimal base_price
        boolean active
        timestamp created_at
    }

    billing_plan_courses {
        bigint plan_id PK,FK
        uuid course_id PK "Catalog ref by value"
        timestamp created_at
    }

    billing_subscriptions {
        bigint id PK
        bigint plan_id FK
        bigint user_id "Auth ref"
        varchar status "ACTIVE|CANCELLED|EXPIRED"
        date start_date
        date end_date
        timestamp created_at
    }

    billing_quotes {
        bigint id PK
        bigint user_id "Auth ref"
        uuid course_id "Physical/Virtual ref"
        varchar quote_type "MONTHLY|INDIVIDUAL"
        decimal amount
        json price_snapshot
        timestamp valid_until
        timestamp created_at
    }

    billing_payments {
        bigint id PK
        bigint user_id "Auth ref"
        bigint quote_id FK
        varchar idempotency_key UK
        varchar provider_payment_id
        varchar principal_status "PENDING|COMPLETED|REJECTED|CANCELLED|EXPIRED"
        varchar substatus "AWAITING_PROVIDER|AWAITING_MANUAL_VERIFICATION|RECONCILIATION_REQUIRED"
        varchar payment_method
        decimal amount
        varchar currency
        timestamp created_at
        timestamp updated_at
    }

    billing_payment_proofs {
        bigint id PK
        bigint payment_id FK,UK
        varchar storage_key
        varchar content_type
        bigint size_bytes
        char checksum
        timestamp uploaded_at
        timestamp replaced_at
    }

    billing_purchases {
        bigint id PK
        bigint payment_id FK,UK
        varchar fulfillment_status "PENDING_FULFILLMENT|ASSIGNED|EXCEPTION"
        date coverage_start
        date coverage_end
        timestamp created_at
        timestamp updated_at
    }

    billing_webhook_inbox {
        bigint id PK
        varchar data_id UK
        varchar x_request_id
        char payload_hash
        varchar status "RECEIVED|PROCESSING|PROCESSED|FAILED"
        json provider_response
        int attempt_count
        timestamp next_attempt_at
        timestamp created_at
    }

    billing_reconciliation_tasks {
        bigint id PK
        bigint payment_id FK
        bigint inbox_id FK
        varchar reason
        varchar status "OPEN|RESOLVED"
        varchar resolution
        bigint resolved_by "Auth ref"
        timestamp created_at
        timestamp resolved_at
    }

    billing_audit_log {
        bigint id PK
        bigint payment_id FK
        varchar action
        varchar actor_type "SYSTEM|ADMIN|PROVIDER"
        bigint actor_id
        json previous_state
        json new_state
        varchar reason
        json evidence
        timestamp created_at
    }

    billing_physical_course_pricing {
        bigint id PK
        uuid course_id UK "Physical ref"
        decimal monthly_price
        decimal individual_surcharge_pct
        int version
        timestamp updated_at
    }

    billing_physical_course_pricing_revisions {
        bigint id PK
        uuid course_id
        bigint professor_id "Auth ref"
        int version
        decimal old_monthly_price
        decimal new_monthly_price
        decimal old_surcharge_pct
        decimal new_surcharge_pct
        varchar reason
        timestamp created_at
    }

    billing_outbox {
        bigint id PK
        varchar event_type
        json payload
        varchar status "PENDING|SENT|FAILED"
        int attempt_count
        timestamp next_attempt_at
        timestamp created_at
    }

    billing_plans ||--o{ billing_plan_courses : includes
    billing_plans ||--o{ billing_subscriptions : offers
    billing_quotes ||--o| billing_payments : initiates
    billing_payments ||--o| billing_purchases : creates
    billing_payments ||--o| billing_payment_proofs : has_current
    billing_payments ||--o{ billing_audit_log : tracks
    billing_payments ||--o{ billing_reconciliation_tasks : may_require
    billing_webhook_inbox ||--o{ billing_reconciliation_tasks : may_trigger
```

---

## Virtual Module

```mermaid
erDiagram
    virtual_courses {
        uuid id PK "Global unique"
        varchar name
        varchar description
        varchar level "BEGINNER|INTERMEDIATE|ADVANCED"
        boolean published
        timestamp created_at
    }

    virtual_modules {
        bigint id PK
        uuid course_id FK
        varchar name
        int order_index
        timestamp created_at
    }

    virtual_lessons {
        bigint id PK
        bigint module_id FK
        varchar name
        int order_index
        int duration_seconds
        timestamp created_at
    }

    virtual_videos {
        bigint id PK
        bigint lesson_id FK
        varchar provider "BUNNY"
        varchar provider_id
        varchar quality "SD|HD|4K"
        int duration_seconds
    }

    virtual_lesson_materials {
        bigint id PK
        bigint lesson_id FK
        varchar name
        varchar content_type
        varchar storage_key
        bigint size_bytes
        timestamp created_at
    }

    virtual_lesson_progress {
        bigint id PK
        bigint user_id "Auth ref"
        bigint lesson_id FK
        int position_seconds
        timestamp completed_at
        timestamp updated_at
    }

    virtual_course_progress {
        bigint id PK
        bigint user_id "Auth ref"
        uuid course_id FK
        int completed_lessons
        int total_lessons
        decimal percentage
        bigint last_lesson_id
        timestamp updated_at
    }

    virtual_course_audit {
        bigint id PK
        uuid course_id FK
        bigint actor_user_id "Auth ref by value"
        varchar action
        varchar reason
        int version
        json previous_values
        json new_values
        timestamp created_at
    }

    virtual_outbox {
        bigint id PK
        varchar event_type
        json payload
        varchar status "PENDING|SENT|FAILED"
        int attempt_count
        timestamp next_attempt_at
        timestamp created_at
    }

    virtual_courses ||--o{ virtual_modules : contains
    virtual_modules ||--o{ virtual_lessons : contains
    virtual_lessons ||--o{ virtual_videos : has
    virtual_lessons ||--o{ virtual_lesson_materials : provides
    virtual_lessons ||--o{ virtual_lesson_progress : tracks
    virtual_courses ||--o{ virtual_course_progress : summarizes
    virtual_courses ||--o{ virtual_course_audit : audits
```

---

## Physical Module

```mermaid
erDiagram
    physical_courses {
        uuid id PK "Global unique"
        varchar name
        varchar description
        varchar level "BEGINNER|INTERMEDIATE|ADVANCED"
        varchar day_of_week "MONDAY|TUESDAY|..."
        time start_time
        int duration_minutes
        int default_capacity
        bigint professor_id "Auth ref"
        boolean active
        timestamp created_at
    }

    physical_sessions {
        bigint id PK
        uuid course_id FK
        date session_date
        time start_time
        int capacity
        varchar status "DRAFT|SCHEDULED|IN_PROGRESS|COMPLETED|CANCELLED"
        timestamp created_at
    }

    physical_capacity_assignments {
        bigint id PK
        bigint session_id FK
        bigint user_id "Auth ref"
        bigint payment_id "Billing ref"
        varchar assignment_type "MONTHLY|INDIVIDUAL"
        timestamp created_at
    }

    physical_capacity_holds {
        bigint id PK
        bigint session_id FK
        bigint user_id "Auth ref"
        varchar idempotency_key
        varchar status "ACTIVE|CONVERTED|RELEASED|EXPIRED"
        timestamp expires_at
        timestamp created_at
    }

    physical_attendance {
        bigint id PK
        bigint session_id FK
        bigint user_id "Auth ref"
        varchar check_in_type "QR|MANUAL"
        bigint device_id FK "nullable - only for QR"
        timestamp checked_in_at
    }

    physical_qr_devices {
        bigint id PK
        varchar name
        varchar location
        char secret_hash
        varchar status "ACTIVE|EXPIRED|REVOKED"
        timestamp expires_at
        timestamp revoked_at
        timestamp rotated_at
        timestamp created_at
    }

    physical_outbox {
        bigint id PK
        varchar event_type
        json payload
        varchar status "PENDING|SENT|FAILED"
        int attempt_count
        timestamp next_attempt_at
        timestamp created_at
    }

    physical_courses ||--o{ physical_sessions : schedules
    physical_sessions ||--o{ physical_capacity_assignments : has
    physical_sessions ||--o{ physical_capacity_holds : may_have
    physical_sessions ||--o{ physical_attendance : records
    physical_qr_devices ||--o{ physical_attendance : registers
```

---

## Constraints y Unique Keys

### Auth
| Tabla | Constraint |
|-------|------------|
| `auth_users` | `UNIQUE(email)` |
| `auth_refresh_tokens` | `UNIQUE(token_hash)` |
| `auth_users_roles` | `PK(user_id, role_id)` |

### Billing
| Tabla | Constraint |
|-------|------------|
| `billing_payments` | `UNIQUE(idempotency_key)` |
| `billing_purchases` | `UNIQUE(payment_id)` |
| `billing_webhook_inbox` | `UNIQUE(data_id)` |
| `billing_physical_course_pricing` | `UNIQUE(course_id)` |

### Virtual
| Tabla | Constraint |
|-------|------------|
| `virtual_lesson_progress` | `UNIQUE(user_id, lesson_id)` |
| `virtual_course_progress` | `UNIQUE(user_id, course_id)` |

### Physical
| Tabla | Constraint |
|-------|------------|
| `physical_capacity_assignments` | `UNIQUE(session_id, user_id)` |
| `physical_attendance` | `UNIQUE(session_id, user_id)` |

---

## Referencias Cross-Module (por valor)

Estas columnas guardan IDs de otros módulos **sin FK**. La validación se hace mediante puertos.

| Tabla | Columna | Referencia a |
|-------|---------|--------------|
| `billing_subscriptions` | `user_id` | `auth_users.id` |
| `billing_quotes` | `user_id` | `auth_users.id` |
| `billing_quotes` | `course_id` | `physical_courses.id` o `virtual_courses.id` |
| `billing_payments` | `user_id` | `auth_users.id` |
| `billing_reconciliation_tasks` | `resolved_by` | `auth_users.id` |
| `billing_physical_course_pricing` | `course_id` | `physical_courses.id` |
| `billing_physical_course_pricing_revisions` | `professor_id` | `auth_users.id` |
| `virtual_lesson_progress` | `user_id` | `auth_users.id` |
| `virtual_course_progress` | `user_id` | `auth_users.id` |
| `physical_courses` | `professor_id` | `auth_users.id` |
| `physical_capacity_assignments` | `user_id` | `auth_users.id` |
| `physical_capacity_assignments` | `payment_id` | `billing_payments.id` |
| `physical_capacity_holds` | `user_id` | `auth_users.id` |
| `physical_attendance` | `user_id` | `auth_users.id` |
