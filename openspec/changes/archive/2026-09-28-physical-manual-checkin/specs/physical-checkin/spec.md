# Delta for Physical Check-in

## ADDED Requirements

### Requirement: Check-in type is a two-valued discriminator

The check-in request's `type` field MUST accept exactly two literal values, `QR` and `MANUAL`,
selecting between the two check-in variants. Any other value MUST be rejected with
`400 INVALID_REQUEST`.

#### Scenario: An unrecognized type value is rejected

- GIVEN a check-in request with `type: "SOMETHING_ELSE"`
- WHEN the request is submitted
- THEN the system returns 400 `INVALID_REQUEST`

### Requirement: QR variant rejects MANUAL-only fields

A check-in request with `type: "QR"` MUST reject the presence of `studentId` (a MANUAL-only
field) with `400 INVALID_REQUEST`, evaluated before any use-case step runs — mirroring the MANUAL
variant's own rejection of QR-only fields (see the new `physical-manual-checkin` capability in
this change).

#### Scenario: QR request carrying studentId is rejected

- GIVEN a check-in request with `type: "QR"` and its QR credential fields
- WHEN the request additionally includes `studentId`
- THEN the system returns 400 `INVALID_REQUEST` before any use-case validation runs

## Out of Scope (Unchanged)

The QR variant's eleven-step ordering (device token → QR parse → session binding → signature →
expiry → session existence/cancelled → check-in window → capacity assignment → idempotent read →
two Redis locks → INSERT), its `SESSION_CANCELLED` (403) and `OUTSIDE_CHECK_IN_WINDOW` (403)
statuses, and every existing requirement/scenario in `openspec/specs/physical-checkin/spec.md`
are unchanged by this delta. Only the wire-contract validation above is new.
