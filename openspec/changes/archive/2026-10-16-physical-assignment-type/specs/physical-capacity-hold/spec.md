# Physical Capacity Hold Specification (delta)

## MODIFIED Requirements

### Requirement: Conversion uses exactly the held session set and is idempotent

Converting a hold batch to assignments MUST use exactly the sessions that
batch reserved — never a recomputed set — and MUST convert at most once per
correlation reference, atomically consuming the hold rows as it inserts the
assignment rows. Conversion MUST receive the `AssignmentType` from its
caller and MUST write it on every inserted row; the hold rows themselves
MUST NOT store a type. A redelivered conversion MUST ignore the supplied
type for already-converted rows.
(Previously: conversion took only payment and student identifiers and the
assignment rows carried no type.)

#### Scenario: First conversion produces one typed assignment per held session

- GIVEN an active hold batch for reference `R` covering `{S1, S2, S3}`
- WHEN conversion is requested for `R` with type `MONTHLY`
- THEN exactly 3 assignment rows are inserted, one per held session, each
  with `assignment_type = 'MONTHLY'`
- AND the 3 hold rows for `R` no longer count as active

#### Scenario: Redelivered conversion for the same reference is a no-op

- GIVEN a hold batch for reference `R` already converted to assignments of
  type `MONTHLY`
- WHEN conversion is requested again for `R` with any type
- THEN the outcome is `AlreadyConverted`
- AND no assignment row is inserted or modified, and the stored type is
  unchanged

#### Scenario: A held session vanishes before conversion — all-or-nothing residual

- GIVEN a hold batch for reference `R` whose set includes a session that is
  no longer `SCHEDULED` at conversion time
- WHEN conversion is requested for `R`
- THEN zero assignment rows are inserted for `R`
- AND the outcome falls to the existing all-or-nothing `EXCEPTION` residual,
  never a partial conversion

#### Scenario: No hold exists falls through to legacy

- GIVEN no hold batch exists for reference `R`
- WHEN conversion is requested for `R`
- THEN the outcome is `HoldNotFound` and no row is written
- AND the caller continues on the legacy `assignAll` path reusing the quote
  it already loaded; outcomes are unchanged except that a missing quote
  reports `QUOTE_NOT_FOUND` instead of `TARGET_NOT_SCHEDULED`
