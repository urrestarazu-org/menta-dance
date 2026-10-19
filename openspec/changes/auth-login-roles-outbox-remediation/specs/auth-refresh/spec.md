# Delta for auth-refresh

## MODIFIED Requirements

### Requirement: Valid refresh rotates the pair atomically

The system MUST rotate a valid active refresh token by marking the presented token `USED`, inserting its replacement in the same family, and persisting `RefreshRotated` in one MySQL transaction. The refresh endpoint contract MUST remain unchanged.

(Previously: rotation required the status transition and replacement insert together but did not require the outbox append in that transaction.)

#### Scenario: Valid refresh commits rotation and outbox together
- GIVEN an unexpired active refresh whose family and token version match the user
- WHEN the client calls `/auth/refresh` using the existing contract
- THEN the existing 200 token response is returned after one atomic commit
- AND the old token is `USED`, its replacement exists, and `RefreshRotated` exists

#### Scenario: Refresh outbox append fails
- GIVEN a valid refresh whose `RefreshRotated` row cannot be persisted
- WHEN rotation is processed
- THEN the presented token remains `ACTIVE`
- AND no replacement refresh is persisted

### Requirement: Compromised refresh revokes its family and persists token version

The system MUST, for a presented `USED`, `ROTATED`, or `REVOKED` refresh, durably increment `auth_users.token_version`, revoke the family, and persist `RefreshRevoked` in one MySQL transaction without issuing tokens.

(Previously: compromise handling required a token-version bump but persistence did not retain it.)

#### Scenario: Reused refresh persists the bumped version
- GIVEN a `USED` refresh and a user with token version 1
- WHEN the client presents that refresh again
- THEN the existing 401 response is returned without tokens
- AND a subsequent user load shows token version 2 and the family is revoked

#### Scenario: Stale refresh version remains detectable after reload
- GIVEN an active refresh with version 1 and a persisted user version 2
- WHEN the client presents the refresh after the user is reloaded
- THEN the existing compromise response is returned
- AND the family is revoked without changing user version 2
