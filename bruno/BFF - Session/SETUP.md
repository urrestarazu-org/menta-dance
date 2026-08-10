# Local Setup for BFF Session Custody Tests

## 1. Configure the repository

From the repository root, create `.env` from `.env.example` when needed and set
the required local credentials. Do not commit `.env`.

## 2. Start and verify services

```bash
./scripts/dev.sh start
./scripts/dev.sh status

curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8080/actuator/health
docker exec menta-redis redis-cli PING
```

Expected health status is `UP`; Redis should answer `PONG`.

## 3. Prepare the test user

The `Local` Bruno environment defaults to:

```text
email: student@example.com
password: password123
```

Register that user once if it does not exist:

```bash
curl -i -X POST "http://localhost:8081/api/v1/users/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"password123","role":"STUDENT"}'
```

Alternatively, adapt and run the existing `bruno/api/auth/register.bru` request.
If you use different credentials, update only the BFF collection's `Local`
environment so every request reads the same values.

## 4. Open and run Bruno

1. Open `bruno/BFF-Session-Custody`.
2. Select the `Local` environment.
3. Run all four requests sequentially.
4. Confirm every assertion passes.

Do not enable parallel execution. The login response creates a runtime
`session_id` consumed by the authenticated dashboard and logout requests.

## 5. Optional Redis inspection

```bash
docker exec menta-redis redis-cli --scan \
  --pattern 'spring:session:sessions:*'
```

Use `HGETALL spring:session:sessions:<session-id>` to inspect only the session
under test. After logout, `EXISTS spring:session:sessions:<session-id>` should
return `0`. Never use `FLUSHALL` as test setup.

## Safe troubleshooting

Use the managed lifecycle commands instead of force-killing Java processes:

```bash
./scripts/dev.sh restart
./scripts/dev.sh logs api
./scripts/dev.sh logs bff
docker compose logs redis
docker compose logs mysql
```

The current backend emits a Secure SESSION cookie. The collection explicitly
sends the captured cookie for requests 2 and 3 only because the default local URL
uses HTTP. Verify automatic cookie transport against HTTPS; see `README.md`.
