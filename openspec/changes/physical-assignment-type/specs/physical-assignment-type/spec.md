# Physical Assignment Type Specification (delta)

## Purpose

Every `physical_capacity_assignments` row records why the student holds the
seat: `MONTHLY` or `INDIVIDUAL`. The type is written at assignment time by
both write paths and is exposed, always non-null, by `GET
/api/v1/physical/sessions/me`.

## ADDED Requirements

### Requirement: A neutral AssignmentType exists in shared

`:api:shared` MUST define `AssignmentType` with exactly the values `MONTHLY`
and `INDIVIDUAL`. `api:physical` MUST NOT import any `billing` type
(including `PurchaseType`) to obtain it, and `api:app` MUST map `PurchaseType`
to `AssignmentType` with an exhaustive switch (no default branch).

#### Scenario: Physical stays isolated from billing

- GIVEN the compiled `api:physical` module
- WHEN the architecture tests run
- THEN no class in `com.menta.physical..` depends on `com.menta.billing..`

#### Scenario: Mapping is exhaustive

- GIVEN `PurchaseType` has values `MONTHLY` and `INDIVIDUAL`
- WHEN the api:app mapping to `AssignmentType` is compiled
- THEN each `PurchaseType` value maps to the `AssignmentType` of the same name
- AND adding a new `PurchaseType` value without a mapping fails compilation

### Requirement: Assignment type is stored NOT NULL with no database default

Migration V26 MUST add `assignment_type VARCHAR NOT NULL` to
`physical_capacity_assignments`. Existing rows MUST be backfilled with
`MONTHLY` through a temporary default, whose assumption (V0, no real data)
MUST be stated in the migration header; the default MUST then be dropped so
the column has no default afterwards. No check constraint is added.

#### Scenario: Existing rows are labelled MONTHLY

- GIVEN assignment rows that exist before V26 is applied
- WHEN V26 is applied
- THEN every such row has `assignment_type = 'MONTHLY'`

#### Scenario: Insert without a type fails

- GIVEN V26 applied
- WHEN a row is inserted into `physical_capacity_assignments` without
  `assignment_type`
- THEN the insert fails with a not-null violation

#### Scenario: Default is dropped

- GIVEN V26 applied
- WHEN the column metadata is inspected
- THEN `assignment_type` is `NOT NULL` and has no default value

### Requirement: Migration has a proven rollback script

A rollback script under `db/rollback/` MUST drop `assignment_type`, and a
migration integration test MUST apply V26, then the script, and assert the
column is gone and prior data is intact.

#### Scenario: Rollback removes the column

- GIVEN V26 applied with assignment rows present
- WHEN the rollback script runs
- THEN `physical_capacity_assignments` has no `assignment_type` column
- AND the rows and their other columns are unchanged

### Requirement: Both write paths persist the resolved type

Held-hold conversion and the legacy `assignAll` path MUST write the
caller-supplied `AssignmentType` on every assignment row they insert. No
write path MAY infer or default the type.

#### Scenario: Held MONTHLY purchase persists MONTHLY

- GIVEN an active hold batch for a purchase resolved as `MONTHLY`
- WHEN the conversion runs
- THEN every inserted assignment row has `assignment_type = 'MONTHLY'`

#### Scenario: Held INDIVIDUAL purchase persists INDIVIDUAL

- GIVEN an active hold batch for a purchase resolved as `INDIVIDUAL`
- WHEN the conversion runs
- THEN every inserted assignment row has `assignment_type = 'INDIVIDUAL'`

#### Scenario: Legacy assignAll persists the command type

- GIVEN a `MultiSessionCapacityAssignmentCommand` carrying type `INDIVIDUAL`
- WHEN `assignAll` executes
- THEN every inserted assignment row has `assignment_type = 'INDIVIDUAL'`

### Requirement: Demo seed assigns INDIVIDUAL

The demo seed MUST create its assignments with `AssignmentType.INDIVIDUAL`.

#### Scenario: Seeded assignments carry INDIVIDUAL

- GIVEN the demo seed has run
- WHEN seeded assignment rows are read
- THEN each has `assignment_type = 'INDIVIDUAL'`

### Requirement: my-sessions exposes assignmentType, never null

`GET /api/v1/physical/sessions/me` MUST include `assignmentType` on every
item, serialized as `MONTHLY` or `INDIVIDUAL`, never null.

#### Scenario: Monthly session

- GIVEN a student with an assignment of type `MONTHLY`
- WHEN the student calls `GET /api/v1/physical/sessions/me`
- THEN the item for that session has `assignmentType = "MONTHLY"`

#### Scenario: Individual session

- GIVEN a student with an assignment of type `INDIVIDUAL`
- WHEN the student calls `GET /api/v1/physical/sessions/me`
- THEN the item for that session has `assignmentType = "INDIVIDUAL"`

#### Scenario: Mixed types in one response

- GIVEN a student holding one `MONTHLY` and one `INDIVIDUAL` assignment
- WHEN the student calls `GET /api/v1/physical/sessions/me`
- THEN each item carries its own row's type and none is null

### Requirement: Contract artifacts stay in sync

The OpenAPI document, the Bruno collection, and `docs/05-PHYSICAL-API.md`
MUST describe `assignmentType` on the my-sessions item as a required,
non-null enum of `MONTHLY` and `INDIVIDUAL`.

#### Scenario: Docs and contract match the response

- GIVEN the implemented endpoint
- WHEN OpenAPI, the Bruno request/assertions, and the docs section are read
- THEN all three list `assignmentType` with values `MONTHLY`, `INDIVIDUAL`
  and mark it non-null
