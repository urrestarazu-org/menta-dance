# Physical Capacity Hold Specification

## Purpose

Technical, invisible reservation lifecycle owned by `api:physical`: an
atomic, all-or-nothing hold over an ordered set of eligible sessions,
created before an asynchronous payment is persisted, counted against
availability alongside real assignments, expiring on a bounded TTL,
releasable on failure, and convertible to assignments exactly once.

## Requirements

### Requirement: Hold creation is atomic and all-or-nothing over the ordered session set

Creating a hold for N eligible sessions MUST insert one hold row per session
or zero rows — never a partial subset. Sessions MUST be claimed in the same
stable order `(scheduledAt ASC, sessionId ASC)` used elsewhere for
multi-session capacity commands.

#### Scenario: Every session has capacity — full hold created

- GIVEN N eligible sessions, each with a free spot
- WHEN a hold is requested for the ordered set
- THEN exactly N hold rows are inserted, one per session
- AND each session's `availableSpots` drops by 1 immediately

#### Scenario: One session is full — zero hold rows created

- GIVEN N eligible sessions where one already has zero free spots
- WHEN a hold is requested for the ordered set
- THEN zero hold rows are inserted for any session in the set
- AND no partial hold survives the failed attempt

### Requirement: Active holds count against availability on every assignment write path

Any path that writes `physical_capacity_assignments` MUST evaluate its
capacity invariant as `assigned + activeHolds + 1 > capacity`, using a
locking read of active holds. A hold and a fresh assignment MUST NOT both
succeed for the same last spot.

#### Scenario: Assignment is blocked by an opposing active hold

- GIVEN a session with one free spot and an active hold occupying it
- WHEN an unrelated assignment attempt targets that same session
- THEN the assignment invariant rejects it
- AND the active hold's spot is not overwritten

### Requirement: Every hold row carries a caller-supplied correlation reference

A hold batch MUST be created against an opaque correlation reference
(payment-id-shaped) so the batch belonging to one purchase is individually
addressable and distinct from any other purchase's holds.

#### Scenario: Hold batch is retrievable by its correlation reference

- GIVEN a hold batch created with reference `R` covering sessions
  `{S1, S2}`
- WHEN the batch is looked up by `R`
- THEN both hold rows for `{S1, S2}` are returned and no row from another
  reference is included

### Requirement: A hold expires on a bounded TTL without requiring a sweep

Each hold row MUST carry an `expires_at` set at creation time, bounded by a
configured TTL sized for synchronous provider checkout (MERCADO_PAGO
Checkout Pro) only. An expired hold MUST stop counting against availability
immediately at read time, independent of any housekeeping sweep having run.

#### Scenario: Expired hold is invisible before any sweep runs

- GIVEN a hold whose `expires_at` is in the past and no sweep has executed
  since
- WHEN a session's `availableSpots` is read
- THEN the expired hold is not counted
- AND the spot it held is available again

### Requirement: A hold is released explicitly when checkout fails after creation

If checkout fails after a hold is created (payment persistence or provider
call fails), the hold MUST be released so it stops counting immediately,
not only via TTL.

#### Scenario: Failed checkout after hold creation frees the spot immediately

- GIVEN a hold created for a checkout that then fails before completing
- WHEN the failure is handled
- THEN the hold is released
- AND `availableSpots` for its sessions recovers without waiting for TTL

### Requirement: Conversion uses exactly the held session set and is idempotent

Converting a hold batch to assignments MUST use exactly the sessions that
batch reserved — never a recomputed set — and MUST convert at most once per
correlation reference, atomically consuming the hold rows as it inserts the
assignment rows.

#### Scenario: First conversion produces one assignment per held session

- GIVEN an active hold batch for reference `R` covering `{S1, S2, S3}`
- WHEN conversion is requested for `R`
- THEN exactly 3 assignment rows are inserted, one per held session
- AND the 3 hold rows for `R` no longer count as active

#### Scenario: Redelivered conversion for the same reference is a no-op

- GIVEN a hold batch for reference `R` already converted to assignments
- WHEN conversion is requested again for `R`
- THEN no additional assignment rows are inserted
- AND the session spot count is unchanged from after the first conversion

#### Scenario: A held session vanishes before conversion — all-or-nothing residual

- GIVEN a hold batch for reference `R` whose set includes a session that is
  no longer `SCHEDULED` at conversion time
- WHEN conversion is requested for `R`
- THEN zero assignment rows are inserted for `R`
- AND the outcome falls to the existing all-or-nothing `EXCEPTION` residual,
  never a partial conversion

### Requirement: The hold is invisible to the student

No endpoint, DTO, BFF view, or Android screen MUST expose, extend, or allow
cancellation of a hold. Displayed availability in a quote remains a
non-binding projection; the hold is the only real reservation.

#### Scenario: No API surface exposes a hold

- GIVEN the complete set of student-facing routes
- WHEN it is enumerated
- THEN no route reads, lists, extends, or cancels a capacity hold
