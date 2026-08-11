# BFF Session Custody — Bruno Test Flow

This collection verifies the complete BFF session lifecycle in a fixed order:

1. Login creates a server-side session.
2. The authenticated dashboard renders HTML.
3. Logout invalidates the session and expires the cookie.
4. The dashboard redirects to login after logout.

## Prerequisites

- JDK 21, Docker Compose, and the project Gradle wrapper.
- A configured repository-root `.env` file.
- A test user registered in the Auth API.

Start the local stack from the repository root:

```bash
./scripts/dev.sh start
./scripts/dev.sh status
```

The helper starts the infrastructure, Auth API, and BFF. To start components
manually instead, use `docker compose up -d`, `./gradlew :api:app:bootRun`, and
`./gradlew :bff:bootRun` in separate terminals.

## Environment

Open `bruno/BFF-Session-Custody`, select the `Local` environment, and review:

```text
bff_url: http://localhost:8080
auth_url: http://localhost:8081
email: student@example.com
password: password123
```

Edit `email` and `password` if your local test user uses different credentials.
Requests read these environment values directly; request-scoped variables do not
override them.

If the user does not exist, register it with the existing
`bruno/api/auth/register.bru` request after adapting its values, or use:

```bash
curl -i -X POST "http://localhost:8081/api/v1/users/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"password123","role":"STUDENT"}'
```

## Run the collection

Run the entire collection sequentially with the `Local` environment. Do not run
requests in parallel because requests 2 and 3 depend on the session captured by
request 1.

### 1. Login

Expected assertions:

- `302 Found`
- `Location` resolves to `/dashboard`
- A non-empty `SESSION` cookie is returned
- Cookie attributes include `HttpOnly`, `Secure`, and `SameSite=Lax`

### 2. Dashboard (Authenticated)

Expected assertions:

- `200 OK`
- `Content-Type` is `text/html`

The dashboard is implemented. A `500` response is a failure, not an acceptable
placeholder result.

### 3. Logout

Expected assertions:

- `302 Found`
- `Location` resolves to `/login?logout`
- `SESSION` is expired with `Max-Age=0`

### 4. Dashboard (After Logout)

Expected assertions:

- `302 Found`
- `Location` resolves to `/login`

## Important local HTTP cookie behavior

The current backend intentionally emits the SESSION cookie with `Secure`. Bruno,
like a browser, must not automatically send a Secure cookie to an `http://` URL.
For this local-only flow, request 1 captures the opaque SESSION value in an
ephemeral runtime variable, and requests 2 and 3 send an explicit `Cookie` header.
Request 4 does not send that header.

This bypass verifies BFF session behavior but does **not** prove automatic browser
cookie transport. Use an HTTPS `bff_url` to verify the real cookie jar/browser
behavior. Do not weaken the production cookie policy merely to satisfy an HTTP
test client.

## Manual Redis verification

Redis is not an HTTP service and is deliberately not represented as a Bruno HTTP
request. Inspect it manually while the flow is running:

```bash
# After request 1: list session keys without blocking Redis with KEYS.
docker exec menta-redis redis-cli --scan --pattern 'spring:session:sessions:*'

# Inspect one session. Replace <session-id> with the captured cookie value.
docker exec menta-redis redis-cli HGETALL \
  'spring:session:sessions:<session-id>'

# After request 3: confirm that the same session key no longer exists.
docker exec menta-redis redis-cli EXISTS \
  'spring:session:sessions:<session-id>'
# Expected: 0
```

Do not use `FLUSHALL`; it destroys unrelated Redis data.

## Troubleshooting

### Login redirects to `/login?error=true`

The credentials are invalid or the user does not exist. Check the selected
`Local` environment and register the same email/password pair if necessary.

### Request 2 reports an unresolved `session_id`

Run request 1 first, or run the whole collection sequentially. The value is a
runtime variable and is intentionally not persisted to the environment file.

### Connection refused

Check the managed services and logs:

```bash
./scripts/dev.sh status
./scripts/dev.sh logs api
./scripts/dev.sh logs bff
```

### Redis is unavailable

```bash
docker compose ps redis
docker exec menta-redis redis-cli PING
```

See `SETUP.md` for a shorter setup checklist and `TESTING-CURL.md` for the
equivalent manual HTTP flow.
