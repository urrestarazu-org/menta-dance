# common-outbox Specification

## Purpose
Bus durable-local de eventos cross-module. Tabla `common_outbox_events` MySQL
post-commit (ADR-0027) + reconciliador scheduled `OutboxBlacklistReconciler`
en `:api:app` aplica side-effects a Redis (`blacklist:jti:*`). MySQL es
autoridad: indisponibilidad o desincronización hacen fallar cerrados los
flujos auth (ADR-0026). Redis NO es bus. El marker `OutboxListener` en
`:api:shared` define el contrato Java para consumers cross-module futuros
(fuera de scope este slice).

## Requirements

### Requirement: Eventos se persisten post-commit en la misma transacción
El sistema MUST escribir filas en `common_outbox_events` dentro de la misma
transacción MySQL que la mutación de dominio, con COMMIT atómico. El marker
`OutboxListener` en `:api:shared` define el contrato para consumers futuros.

#### Scenario: Login emite AuthUserLoggedIn post-commit
- GIVEN un login exitoso
- WHEN el caso de uso completa la mutación de tokens
- THEN el COMMIT incluye fila en `common_outbox_events` con
  `event_type=AuthUserLoggedIn`, `aggregate_id=jti`,
  `payload={token_version}`, `status=PENDING`
- AND la fila es visible al reconciliador tras el commit

#### Scenario: Logout emite UserLoggedOut atómicamente
- GIVEN un logout solicitado
- WHEN el caso de uso marca el refresh como REVOKED
- THEN la misma transacción inserta `common_outbox_events` con
  `event_type=UserLoggedOut`, `aggregate_id=jti`
- AND el COMMIT de ambas escrituras es atómico

### Requirement: Tabla tiene UNIQUE constraint para idempotencia
El sistema MUST garantizar UNIQUE sobre `(aggregate_id, event_type)` para que
reintentos del productor o del reconciliador no dupliquen side-effects.

#### Scenario: Inserción duplicada rechazada por la base
- GIVEN `(aggregate_id, event_type)` ya presente con `status=COMPLETED`
- WHEN un reintento intenta insertar el mismo par
- THEN MySQL rechaza con violación UNIQUE
- AND el reconciliador trata el rechazo como idempotencia: no re-aplica
  side-effects

### Requirement: Reconciler pull-batch procesa PENDING
El sistema MUST ejecutar `OutboxBlacklistReconciler` scheduled que pulling
eventos `PENDING`, aplica side-effect Redis (`SET blacklist:jti:{jti} 1 EX
{ttl}`) y marca `status=COMPLETED` con timestamp. Side-effects permitidos
este slice: solo blacklist de jti.

#### Scenario: Reconciler procesa lote y marca COMPLETED
- GIVEN N filas `PENDING` elegibles
- WHEN el scheduled tick ejecuta el reconciler
- THEN para cada fila aplica side-effect en Redis
- AND actualiza `status=COMPLETED`, `processed_at=now()`
- AND respeta batch size configurable (límite duro)

### Requirement: Falla side-effect marca FAILED con backoff
El sistema MUST mover la fila a `status=FAILED` con backoff cuando el
side-effect Redis falla, sin perder el evento. Reintenta respetando
`next_retry_at`. Crash entre commit y reconciler retoma en el próximo tick.

#### Scenario: Redis caído marca FAILED con backoff
- GIVEN una fila `PENDING` y Redis indisponible
- WHEN el reconciler intenta aplicar el side-effect
- THEN la fila queda `status=FAILED` con `last_error` y
  `next_retry_at = now() + backoff`
- AND el evento NO se pierde

#### Scenario: Crash entre commit y reconciler retoma
- GIVEN una fila `PENDING` confirmada antes del crash
- WHEN el siguiente tick arranca
- THEN la fila sigue `PENDING` y el reconciler la retoma
- AND el side-effect se aplica sin duplicar (UNIQUE ya protege)

### Requirement: Reconciliador atrasado expone AUTH_DEGRADED
El sistema MUST detectar una fila `PENDING` sin procesar más de 30 s y
exponer `AUTH_DEGRADED=true`. Auth usa este estado para responder 503 con
`Retry-After: 30` en login, refresh y requests autenticados (ADR-0026).

#### Scenario: AUTH_DEGRADED se activa a 30 s
- GIVEN una fila `PENDING` sin procesar más de 30 s
- AND la última corrida del reconciler data de más de 30 s
- WHEN el reloj cruza la ventana de 30 s
- THEN el sistema expone `AUTH_DEGRADED=true`
- AND los flujos auth responden 503 con `Retry-After: 30`
- AND cuando la fila pasa a `COMPLETED`, `AUTH_DEGRADED` se desactiva

### Requirement: Marker OutboxListener reside en api:shared
El sistema MUST definir `OutboxListener` y `OutboxEvent` en `:api:shared`
como APIs públicas sin Spring ni JPA (ADR-0021).

#### Scenario: Marker compilable y libre de frameworks
- GIVEN `:api:shared` con dependencias mínimas (sin Spring, sin JPA)
- WHEN el módulo compila
- THEN `OutboxListener` y `OutboxEvent` están disponibles para cualquier
  módulo que quiera escuchar eventos cross-module en fases futuras
