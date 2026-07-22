# US-AUTH-007: Logout y revocación

## Historia
Como usuario autenticado, quiero cerrar mi sesión para invalidar de inmediato
mis credenciales actuales.

## Aceptación

- `POST /api/v1/auth/logout` requiere `Authorization: Bearer <access>`; Web
  entrega el refresh al BFF por cookie y Android en `X-Refresh-Token`.
- La misma transacción MySQL revoca el refresh aplicable e inserta el evento de
  blacklist del `jti` en `auth_outbox`.
- Un worker interno publica `blacklist:jti:{jti}` en Redis con TTL hasta `exp`.
  No se confirma logout hasta asegurar ese reflejo; si Redis falla, MySQL activa
  `AUTH_DEGRADED` y el outbox queda durable para recuperación.
- Las rutas autenticadas consultan MySQL como fuente de verdad para `jti` y
  `tokenVersion`, y exigen Redis reconciliado. MySQL inaccesible o
  `AUTH_DEGRADED` bloquea la solicitud. Las respuestas usan
  `application/problem+json`.

## Hecho cuando

Hay pruebas de outbox, retry idempotente, blacklist, fallo de Redis y revocación
de refresh.
