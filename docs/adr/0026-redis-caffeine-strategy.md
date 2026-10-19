# ADR-0026: Redis Reflejado y Caffeine de Caché

**Estado:** Aceptado

## Decisión

MySQL es la fuente de verdad de sesiones: `tokenVersion`, `jti` revocados y el
estado global `AUTH_DEGRADED`. Redis refleja blacklist y versión, además de rate
limits y locks de concurrencia. Se configura con `maxmemory-policy noeviction`;
Caffeine contiene sólo datos reconstruibles (catálogo, planes y lecturas no
sensibles). Redis no es caché de pagos/cupos.

Toda ruta autenticada consulta MySQL y exige Redis reconciliado. Si MySQL no está
disponible o Redis no refleja el estado, `AUTH_DEGRADED` bloquea login, refresh y
tráfico autenticado hasta reparación por outbox. Los locks de QR usan `SET key
nonce NX EX ttl` y Lua compare-and-delete para no liberar el lock de otro proceso.
No se utiliza Redis como bus ni RabbitMQ.

### Corrección #88: `tokenVersion` es el mecanismo real de revocación de access tokens

Ningún evento del outbox transporta hoy un jti de access token revocable —
`LogoutCommand` ni siquiera recibe el Bearer token, y el `aggregateId` de
`REFRESH_REVOKED`/`USER_LOGGED_OUT` es un `familyId` o un id de refresh, nunca
un jti. `BlacklistOutboxEventHandler` (blacklist por jti) no reclama ningún
evento por ese motivo y queda reservado para una futura revocación por token
individual, una vez que el jti se transporte desde la capa HTTP.

El mecanismo que efectivamente invalida access tokens ya emitidos es la
proyección de `tokenVersion` en Redis vía outbox:

- `TokenVersionOutboxEventHandler` proyecta `REFRESH_REVOKED`, `USER_LOGGED_OUT`
  y `PASSWORD_RESET_COMPLETED` (reuso de refresh, logout normal y reset de
  contraseña bumpean `tokenVersion` y publican el valor nuevo).
- La escritura en Redis es monotónica: un script Lua sólo aplica el `SET` si
  la versión entrante es estrictamente mayor a la proyectada, para que un
  replay del outbox fuera de orden (retry con backoff que llega después de
  una fila más nueva) no pueda bajar la versión y reabrir un token ya
  revocado.
- `JwtAuthenticationFilter` rechaza la request si el `tokenVersion` del JWT es
  menor al proyectado, o si Redis es inalcanzable (fail-closed).
- Un logout normal invalida el access token en **todos** los dispositivos del
  usuario, no sólo el de la sesión que cerró sesión: al no existir jti
  persistido en ningún lado, `tokenVersion` (revocación global) es la única
  palanca disponible.

**Nota de test-environment**: el ADR-0030 marca `payload` como columna
`JSON`. Bajo el perfil `test` (H2), el par escritura/lectura de un `String`
Hibernate mapeado a esa columna no es simétrico — un payload guardado como
objeto JSON se relee como un nodo de texto JSON, rompiendo en silencio
`payload.get("campo")` para cualquier consumidor del outbox. Se verificó
empíricamente que MySQL real no tiene este problema (round-trip correcto).
Cualquier test que dependa de que el reconciler relea lo que escribió un caso
de uso debe correr contra MySQL real (Testcontainers, perfil
`integration-test`), no contra el slice H2 — ver
`AuthRevocationIntegrationTest`.

## Consecuencias

La capacidad y seguridad se preservan por sobre disponibilidad parcial. Alertas
operativas deben detectar MySQL/Redis no reconciliados, `AUTH_DEGRADED` y la
recuperación de outbox.
