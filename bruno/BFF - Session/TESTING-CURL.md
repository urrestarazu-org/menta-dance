# BFF Session Custody with curl

These commands reproduce the Bruno flow for debugging. They use one temporary
cookie file and never modify database or Redis data directly.

## 1. Check services

```bash
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/health
```

## 2. Use an existing active local user

Public registration is temporarily disabled while `auth-account-activation` is
completed. Set the email and password in the commands below to an existing
active local account.

## 3. Login without following the redirect

```bash
curl -i --max-redirs 0 -X POST "http://localhost:8080/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=student@example.com" \
  --data-urlencode "password=password123" \
  -c /tmp/menta-bff-cookies.txt
```

Expected: `302`, `/dashboard`, and a SESSION cookie with `HttpOnly`, `Secure`,
and `SameSite=Lax`.

## 4. Extract the local session value

The current backend marks SESSION as Secure. Standards-compliant clients do not
send that cookie over HTTP, so the local HTTP debug flow must pass it explicitly:

```bash
SESSION_ID=$(awk '$6 == "SESSION" { print $7 }' /tmp/menta-bff-cookies.txt)
test -n "$SESSION_ID"
```

This is a local test bypass, not a production browser simulation. Use HTTPS to
test automatic Secure cookie transport.

## 5. Open the authenticated dashboard

```bash
curl -i --max-redirs 0 "http://localhost:8080/dashboard" \
  -H "Cookie: SESSION=${SESSION_ID}"
```

Expected: `200 OK` with `Content-Type: text/html`.

## 6. Inspect Redis optionally

```bash
docker exec menta-redis redis-cli EXISTS \
  "spring:session:sessions:${SESSION_ID}"
# Expected: 1
```

## 7. Logout

```bash
curl -i --max-redirs 0 -X POST "http://localhost:8080/logout" \
  -H "Cookie: SESSION=${SESSION_ID}"
```

Expected: `302`, `/login?logout`, and SESSION expiration with `Max-Age=0`.

## 8. Verify the session is gone

```bash
docker exec menta-redis redis-cli EXISTS \
  "spring:session:sessions:${SESSION_ID}"
# Expected: 0

curl -i --max-redirs 0 "http://localhost:8080/dashboard"
# Expected: 302 with Location ending in /login
```

## Troubleshooting

- `Connection refused`: run `./scripts/dev.sh status` and inspect managed logs.
- `/login?error=true`: confirm the email/password pair in the selected Bruno
  environment matches the registered user.
- Missing Redis session: confirm login returned a non-empty SESSION cookie before
  inspecting the corresponding key.
- Never use root database credentials, destructive SQL, `FLUSHALL`, or `kill -9`
  to troubleshoot this flow.
