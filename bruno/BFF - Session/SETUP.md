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

Use an existing active Auth account. Public registration is temporarily
disabled while `auth-account-activation` is completed, so it cannot create the
fixture. If you use different credentials, provide them as runtime variables
for the selected `local` environment so every request reads the same values.

## 4. Open and run Bruno

1. Open `bruno/`.
2. Select the `local` environment.
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
