# Physical Session Availability Specification

## Purpose

The physical module's read of scheduled sessions with live availability for a course (`PhysicalCourseAvailabilityPort.listSessions(courseId, from, to)`), consumed by billing coverage planning and by the public catalog detail. This spec fixes its range semantics (half-open), filtering, ordering and the availability arithmetic, so the documented contract holds at the `to` edge. Holds themselves stay internal (see `physical-capacity-hold`); this read only derives `availableSpots` from them.

## Requirements

### Requirement: Range is half-open `[from, to)`

The read MUST return sessions whose `scheduledAt` satisfies `from <= scheduledAt < to`. A session exactly at `from` MUST be included; a session exactly at `to` MUST be excluded. The read MUST return only sessions of the requested course.

#### Scenario: Session exactly at from is included

- GIVEN a SCHEDULED session of course `C` at instant `T`
- WHEN availability is read for `C` with `from` = `T`
- THEN the session is returned

#### Scenario: Session exactly at to is excluded

- GIVEN a SCHEDULED session of course `C` at instant `T`
- WHEN availability is read for `C` with `to` = `T`
- THEN the session is not returned

#### Scenario: Other courses never leak in

- GIVEN scheduled sessions in range for courses `C` and `D`
- WHEN availability is read for `C`
- THEN only sessions of `C` are returned

### Requirement: Empty or inverted range yields an empty list

When no session satisfies the range, including `from` = `to` and `from` after `to`, the read MUST return an empty list and MUST NOT fail. A course with no sessions MUST also yield an empty list.

#### Scenario: Empty range

- GIVEN sessions exist for `C` outside `[from, to)`
- WHEN availability is read for `C`
- THEN the result is an empty list

#### Scenario: Zero-width and inverted range

- GIVEN a session of `C` at `T`
- WHEN the read is made with `from` = `to` = `T`, and again with `from` after `to`
- THEN both results are empty lists

### Requirement: Only SCHEDULED sessions, ascending by scheduledAt

The read MUST return only sessions in SCHEDULED status; CANCELLED sessions MUST NEVER appear. Results MUST be ordered ascending by `scheduledAt`.

#### Scenario: Cancelled session excluded

- GIVEN a CANCELLED and a SCHEDULED session of `C`, both in range
- WHEN availability is read
- THEN only the SCHEDULED session is returned

#### Scenario: Ascending order

- GIVEN three SCHEDULED sessions inserted out of chronological order
- WHEN availability is read
- THEN they are returned ordered by `scheduledAt` ascending

### Requirement: availableSpots = max(0, capacity - assigned - activeHolds)

For each returned session, `availableSpots` MUST equal `max(0, capacity - assignedSpots - activeCapacityHolds)`, where `assignedSpots` is the number of assignments to the session and an active hold is one that is neither converted nor expired. Expired and converted holds MUST NOT reduce availability. `availableSpots` MUST NOT be negative.

#### Scenario: Assignments and active holds reduce availability

- GIVEN a session with capacity 20, 2 assignments and 1 active hold
- WHEN availability is read
- THEN `availableSpots` = 17

#### Scenario: Expired and converted holds are ignored

- GIVEN a session with capacity 20, 1 expired hold and 1 converted hold
- WHEN availability is read
- THEN `availableSpots` = 20 minus the assignments only (the converted hold is counted through its assignment)

#### Scenario: Over-committed session floors at zero

- GIVEN a session whose assigned plus active holds exceed capacity
- WHEN availability is read
- THEN `availableSpots` = 0

### Requirement: Availability agrees with the checkout hold invariant

A session reported with `availableSpots` >= 1 MUST accept a single-spot hold under the same state, and a session reported with `availableSpots` = 0 MUST reject it (hold rejected when `assigned + activeHolds + 1 > capacity`).

#### Scenario: Reported free spot is holdable

- GIVEN a session reported with `availableSpots` = 1
- WHEN a checkout attempts a one-spot hold
- THEN the hold is accepted

#### Scenario: Reported full session rejects a hold

- GIVEN a session reported with `availableSpots` = 0
- WHEN a checkout attempts a one-spot hold
- THEN the hold is rejected
