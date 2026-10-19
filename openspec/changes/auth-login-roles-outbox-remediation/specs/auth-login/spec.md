# Delta for auth-login

## MODIFIED Requirements

### Requirement: Login emits a valid token pair

The system MUST issue access and refresh tokens for valid credentials, an active account, and a current reconciler. It MUST persist the login mutation and its `AuthUserLoggedIn` outbox row in one MySQL transaction, without changing the login endpoint contract.

(Previously: successful login issued tokens but did not require an encompassing mutation-plus-outbox transaction.)

#### Scenario: Valid credentials commit login and outbox atomically
- GIVEN an active account, valid credentials, and a healthy reconciler
- WHEN the client posts to `/auth/login`
- THEN the response remains 200 with the existing token payload
- AND the login mutation and `AuthUserLoggedIn` row commit together

#### Scenario: Login outbox append fails
- GIVEN a valid login whose required outbox row cannot be persisted
- WHEN the login transaction completes
- THEN no login mutation is committed
- AND no success response or token pair is returned

#### Scenario: Locked account is rejected
- GIVEN a locked account with correct credentials
- WHEN the client posts to `/auth/login`
- THEN the response remains 423
- AND no token or outbox row is created

#### Scenario: Invalid credentials remain indistinguishable
- GIVEN either an unknown email or an incorrect password
- WHEN the client posts to `/auth/login`
- THEN the response remains the same 401 contract in both cases
- AND no token or outbox row is created

### Requirement: Public registration accepts only the STUDENT role

The system MUST create a public registration only as `STUDENT` and MUST reject every caller-supplied non-`STUDENT` role before any persistence or outbox write. This restriction MUST apply only to the public registration flow; internal privileged-user provisioning remains unchanged.

(Previously: public registration was specified as STUDENT-only but accepted caller-controlled roles.)

#### Scenario: Public STUDENT registration succeeds
- GIVEN valid public registration data with no role or `STUDENT`
- WHEN the client posts to `/auth/register`
- THEN the system creates an active `STUDENT` user with token version 1
- AND it returns the existing 201 response contract

#### Scenario: Public privileged role is rejected without writes
- GIVEN valid public registration data with `ADMIN` or `INSTRUCTOR`
- WHEN the client posts to `/auth/register`
- THEN it returns the existing validation-error response
- AND no user or outbox row is persisted

### Requirement: Logout revokes refresh with an atomic outbox event

The system MUST revoke the presented refresh token and persist `UserLoggedOut` in one MySQL transaction, preserving the logout endpoint contract.

(Previously: atomicity was required but separate adapter transactions could commit independently.)

#### Scenario: Logout commits both writes
- GIVEN an active refresh token
- WHEN the client posts to `/auth/logout` using the existing contract
- THEN the refresh is `REVOKED` and `UserLoggedOut` is persisted atomically
- AND the response remains 204

#### Scenario: Logout outbox append fails
- GIVEN an active refresh token whose required outbox row cannot be persisted
- WHEN logout is processed
- THEN the refresh remains active
- AND no logout event is committed
