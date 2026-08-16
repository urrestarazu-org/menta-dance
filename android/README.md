# Android authentication custody

The Android client retains the access token only in the process memory of
`AuthSessionRepository`. It is never written to preferences, files, saved UI
state, analytics, crash reports, or logs.

The opaque refresh token is encrypted with a non-exportable AES-256-GCM key in
Android Keystore. The only persisted values are the ciphertext and its random
GCM nonce in private `SharedPreferences`; backup and device transfer are
disabled for the application. `replace()` and `clear()` use synchronous commits
so a completed rotation or logout leaves no previous plaintext copy.

## Lifecycle

- Refresh is sent only in `X-Refresh-Token` to `POST /api/v1/auth/refresh`.
- Login accepts credentials in its request body and reads its replacement refresh
  only from `X-Refresh-Token`; it follows the same encrypted persistence path.
- A successful rotation first persists the replacement refresh, then updates
  the in-memory access token. If local persistence fails, all local auth state
  is deleted and the user must sign in again; the previous refresh is never
  retried.
- Logout always clears local access and refresh material in `finally`, even
  when remote revocation fails.
- API errors are accepted as `application/problem+json` and classified by the
  `code` member only. For `429`, `Retry-After` is retained as a number of
  seconds. Response details, headers, and tokens are never logged.
