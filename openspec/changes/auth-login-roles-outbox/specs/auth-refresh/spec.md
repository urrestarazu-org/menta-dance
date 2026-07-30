# auth-refresh Specification

## Purpose
Define la rotación del refresh token y la revocación de familia completa cuando
se detecta compromiso (ADR-0025). Estrategia **strict per-family**: cualquier
reuso de un refresh rotado invalida TODA la familia e incrementa
`auth_users.token_version`, forzando re-autenticación. El endpoint
`POST /auth/refresh` es el único que emite un nuevo par a partir de un refresh
válido.

## Requirements

### Requirement: Refresh válido emite nuevo par y marca el anterior USED
El sistema MUST emitir un nuevo par (access + refresh) cuando el refresh está
`ACTIVE`, no expirado, y su `family_id` y `token_version` coinciden con
`auth_users`. El refresh anterior MUST pasar a `status=USED` en la misma
transacción que la inserción del nuevo.

#### Scenario: Rotación exitosa emite par nuevo y conserva familia
- GIVEN un refresh `ACTIVE` con expiración dentro de 7 días
- AND `family_id` y `token_version` coinciden con `auth_users`
- WHEN el cliente envía `POST /auth/refresh` con `X-Refresh-Token`
- THEN el sistema responde 200 con nuevo `{access_token, refresh_token, expires_in}`
- AND el nuevo refresh pertenece a la misma `family_id`
- AND el refresh anterior queda en `status=USED`
- AND se publica `common_outbox_events` con `event_type=RefreshRotated`

### Requirement: Reuso de refresh revoca familia y bump token_version
El sistema MUST detectar la presentación de un refresh `USED`, `ROTATED` o
`REVOKED` y responder 401 + bump `auth_users.token_version += 1` + revocar
la familia entera, sin emitir tokens.

#### Scenario: Refresh USED dispara revocación de familia
- GIVEN un refresh en `status=USED` (presentado por segunda vez)
- WHEN el cliente envía `POST /auth/refresh` con ese token
- THEN el sistema responde 401 Unauthorized
- AND `auth_users.token_version` se incrementa en 1
- AND todos los refresh `ACTIVE` o `USED` de la misma familia pasan a `REVOKED`
- AND se publica `common_outbox_events` con `event_type=RefreshRevoked`
- AND ningún cliente obtiene tokens a partir de esa familia

### Requirement: Refresh expirado se rechaza sin emitir nada
El sistema MUST rechazar cualquier refresh cuyo `exp` sea anterior al momento
de la solicitud, sin emitir tokens y sin alterar estado en MySQL.

#### Scenario: Refresh con más de 7 días se rechaza
- GIVEN un refresh `ACTIVE` con `exp` hace 8 días
- WHEN el cliente envía `POST /auth/refresh`
- THEN el sistema responde 401 Unauthorized con código `refresh_expired`
- AND no inserta refresh nuevo
- AND no modifica el estado del refresh presentado

### Requirement: Token version desincronizado implica compromiso
El sistema MUST rechazar cualquier refresh cuyo `token_version` no coincida
con `auth_users.token_version` con la misma métrica observable que un refresh
comprometido.

#### Scenario: Refresh con tokenVersion viejo dispara familia revocada
- GIVEN un refresh `ACTIVE` con `token_version=1`
- AND `auth_users.token_version=2` (lo incrementó una revocación previa)
- WHEN el cliente envía `POST /auth/refresh`
- THEN el sistema responde 401 Unauthorized
- AND `auth_users.token_version` no se modifica (ya estaba en 2)
- AND todos los refresh de la familia se marcan `REVOKED`
- AND se publica `common_outbox_events` con `event_type=RefreshRevoked`

### Requirement: Refresh REVOKED es inmutable
El sistema MUST mantener `status=REVOKED` de manera inmutable para todo refresh
que haya pasado por flujo de revocación (logout, compromiso, bump). Ningún
endpoint puede reactivar un refresh en ese estado.

#### Scenario: Refresh REVOKED se rechaza inmutablemente
- GIVEN un refresh con `status=REVOKED`
- WHEN el cliente envía `POST /auth/refresh` con ese token
- THEN el sistema responde 401 Unauthorized
- AND el estado del refresh permanece `REVOKED` en MySQL
- AND no se publica ningún evento adicional

### Requirement: Refresh respeta reconciliación fail-closed
El sistema MUST aplicar la misma regla fail-closed que `auth-login`: cuando el
reconciliador de outbox está atrasado, `/refresh` responde 503 con
`Retry-After: 30` (ADR-0026).

#### Scenario: Reconciliador atrasado bloquea refresh
- GIVEN eventos pendientes en `common_outbox_events` por más de 30 s
- WHEN el cliente envía `POST /auth/refresh` con un refresh válido
- THEN el sistema responde 503 Service Unavailable
- AND el header `Retry-After` vale `30`
- AND no se altera el estado del refresh presentado
