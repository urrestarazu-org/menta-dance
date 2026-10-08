# Demo Seed Data Specification

## Purpose

A `demo` Spring profile seeds role test users and a coherent catalog on
startup so the app can be shown and tested end to end. Seeding is
idempotent per entity, respects module boundaries, and never runs in
production-like environments.

## Requirements

### Requirement: Demo profile guard

The application MUST fail startup when profile `demo` is active together
with `prod`, `production`, or `staging`. Profile matching MUST be
case-insensitive. The guard MUST live in `api:app` and be the single
source of this rule for demo seeding.

#### Scenario: Demo combined with a production-like profile

- GIVEN active profiles `demo` and `prod`
- WHEN the application context starts
- THEN startup MUST fail before any fixture runs

#### Scenario: Case-insensitive matching

- GIVEN active profiles `DEMO` and `Staging` (any casing of `production` behaves the same)
- WHEN the application context starts
- THEN startup MUST fail

#### Scenario: Demo alone is accepted

- GIVEN active profile `demo` only
- WHEN the application context starts
- THEN the guard MUST NOT fail startup

### Requirement: Demo-only configuration

README default passwords and dev secrets (including the bank-transfer
proof-token secret) MUST be defined only in `application-demo.yml` and
MUST be overridable by environment variables. Java sources MUST NOT hold
default passwords as constants.

#### Scenario: Default password used

- GIVEN profile `demo` and no password env var set
- WHEN users are seeded
- THEN each user's password MUST equal its README default from `application-demo.yml`

#### Scenario: Env var overrides default

- GIVEN profile `demo` and a password env var set for a user
- WHEN users are seeded
- THEN that user's password MUST be the env var value

#### Scenario: No demo config without the profile

- GIVEN profile `demo` is not active
- WHEN the context starts
- THEN no demo password or dev secret MUST be loaded

### Requirement: Seeded role users

Auth MUST seed 4 ACTIVE users: admin@menta.dance (ADMIN),
profesor@menta.dance (INSTRUCTOR), alumno@menta.dance (STUDENT),
recepcion@menta.dance (RECEPTIONIST). Seeding MUST NOT emit outbox events
or send email.

#### Scenario: All users can log in

- GIVEN an empty database and profile `demo`
- WHEN the application starts
- THEN the 4 users MUST exist as ACTIVE with their roles
- AND each MUST authenticate with its configured password

### Requirement: Per-entity idempotency

Every seeded entity MUST be upserted by a stable key. A second start MUST
create no duplicates and MUST NOT fail. There MUST NOT be a global
"empty DB" gate.

#### Scenario: Second start

- GIVEN a database already seeded by a previous start
- WHEN the application starts again
- THEN row counts of users, courses, modules, lessons, plans, links and prices MUST be unchanged

#### Scenario: Partially seeded database

- GIVEN only some seeded entities exist
- WHEN the application starts
- THEN the missing entities MUST be created and existing ones left untouched

### Requirement: Virtual catalog

Virtual MUST seed published courses "Bachata Sensual" and "Bachata
Tradicional", each with 2 modules, containing both free and restricted
lessons. Seeded video references MUST work with the local Bunny adapter.

#### Scenario: Catalog visible

- GIVEN profile `demo` after startup
- WHEN published courses are listed
- THEN both courses MUST appear with 2 modules each
- AND each course MUST include at least one free and one restricted lesson

#### Scenario: Local stream

- GIVEN a seeded free lesson
- WHEN its stream is requested
- THEN a local signed URL MUST be returned with no Bunny.net credential

### Requirement: Physical catalog and sessions

Physical MUST seed "Bachata Sensual Presencial" (Tue/Thu 19:00, capacity
12) and "Bachata Tradicional Presencial" (Sat 11:00, capacity 15), both
owned by the instructor user. It MUST create sessions for the next 4
weeks plus one rolling session about 10 minutes in the future. The
rolling session MUST be reused or replaced on each start, without
accumulating.

#### Scenario: Four weeks of sessions

- GIVEN profile `demo` after startup
- WHEN sessions are listed
- THEN each course MUST have sessions on its scheduled weekdays for the next 4 weeks

#### Scenario: Rolling session is current

- GIVEN a start at time T
- WHEN sessions are listed
- THEN exactly one rolling session MUST exist, scheduled about 10 minutes after T

#### Scenario: Restart does not accumulate rolling sessions

- GIVEN a prior start created a rolling session
- WHEN the application starts again later
- THEN still exactly one rolling session MUST exist, scheduled relative to the new start

### Requirement: Student seat

The student MUST hold a confirmed seat in the next session of "Bachata
Sensual Presencial". The fixture MUST check for an existing confirmed
assignment before assigning.

#### Scenario: Seat created once

- GIVEN a seeded database without the seat
- WHEN the application starts
- THEN the student MUST have one confirmed seat in the next Sensual session

#### Scenario: Existing assignment

- GIVEN the student already has a confirmed assignment for that session
- WHEN the application starts again
- THEN no assignment MUST be attempted and startup MUST NOT fail

### Requirement: QR reader secret

The QR reader device MUST be seeded only when `DEMO_QR_READER_SECRET` is
set, storing only its hash. When unset, seeding MUST be skipped with a
WARN log and startup MUST succeed. When the value changes between starts,
the stored hash MUST be rotated.

#### Scenario: Secret set

- GIVEN `DEMO_QR_READER_SECRET=s1`
- WHEN the application starts
- THEN a reader MUST exist whose stored hash verifies `s1` and no plaintext is stored

#### Scenario: Secret unset

- GIVEN `DEMO_QR_READER_SECRET` is unset
- WHEN the application starts
- THEN no reader MUST be created
- AND a WARN MUST be logged and startup MUST succeed

#### Scenario: Secret changed

- GIVEN a reader seeded with `s1`
- WHEN the application starts with `DEMO_QR_READER_SECRET=s2`
- THEN the hash MUST verify `s2` and MUST NOT verify `s1`

### Requirement: Billing prices and plans

Billing MUST set physical prices of 25000 ARS (Sensual) and 20000 ARS
(Tradicional). It MUST seed "Plan Virtual" linked to both virtual courses
and "Plan Completo" linked to all 4 courses.

#### Scenario: Plans and prices

- GIVEN profile `demo` after startup
- WHEN plans and prices are read
- THEN "Plan Virtual" MUST link exactly the 2 virtual courses
- AND "Plan Completo" MUST link all 4 courses
- AND the physical prices MUST be 25000 and 20000 ARS

### Requirement: Student trial subscription

The student MUST receive a 90-day TRIAL subscription on "Plan Completo",
assigned only when the student has no active subscription.

#### Scenario: Trial assigned

- GIVEN the student has no active subscription
- WHEN the application starts
- THEN an ACTIVE TRIAL on "Plan Completo" expiring in 90 days MUST exist

#### Scenario: Active subscription present

- GIVEN the student already has an active subscription
- WHEN the application starts
- THEN no new subscription MUST be assigned

### Requirement: Run order and decoupling

Fixtures MUST run in order Auth, then Virtual and Physical, then Billing.
The seat and the subscription MUST NOT be coupled in code or data.

#### Scenario: Order respected

- GIVEN all module fixtures are present
- WHEN the application starts
- THEN Auth fixture order MUST be lower than Virtual/Physical, which MUST be lower than Billing

#### Scenario: Seat independent of subscription

- GIVEN the student's trial has expired or does not exist
- WHEN the seat fixture runs
- THEN the seat MUST still be created

### Requirement: Module boundaries and no data migrations

Classes in `..infrastructure.demo..` MUST depend only on their own module
and `api:shared`, enforced by ArchUnit. Demo data MUST NOT be created by
Flyway migrations.

#### Scenario: ArchUnit rule

- GIVEN a demo fixture importing another business module
- WHEN architecture tests run
- THEN the build MUST fail

#### Scenario: No seed migrations

- GIVEN the migration directories of all modules
- WHEN inspected
- THEN none MUST insert demo data

### Requirement: Documentation

`README.md` and `docs/26-LOCAL-DEV-SETUP-HOWTO.md` MUST document the
`demo` profile, credentials, env vars (including `DEMO_QR_READER_SECRET`),
and that real Mercado Pago adapters stay active.

#### Scenario: Docs updated

- GIVEN the change is complete
- WHEN the two documents are read
- THEN each MUST describe how to run with `demo` and the env vars above
