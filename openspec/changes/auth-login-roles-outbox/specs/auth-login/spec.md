# auth-login Specification

## Purpose
Inicio de sesión y auto-registro en `:api:auth`. Emite el par de tokens
definido en ADR-0025 (access JWT + refresh opaco) y restringe el auto-registro
público exclusivamente a cuentas con rol `STUDENT`. La regla **fail-closed**
(ADR-0025, ADR-0026) bloquea emisión cuando el reconciliador de outbox está
atrasado.

> **Scope note**: este slice introduce el valor `LOCKED` en el enum `UserStatus` (actual: `ACTIVE|INACTIVE|SUSPENDED`) vía migración V2. Ver `design.md` File Changes.

## Requirements

### Requirement: Login emite par de tokens válidos
El sistema MUST emitir access + refresh cuando credenciales son válidas, la
cuenta está activa y el reconciliador está al día. Access es JWT HMAC-SHA256
(ADR-0025) con `jti`, `tokenVersion` y expiración de 15 min. Refresh es UUID
opaco, SHA-256, expiración 7 días.

#### Scenario: Credenciales válidas y reconciliador al día
- GIVEN una cuenta `status=ACTIVE` y password válido
- AND el reconciliador terminó OK dentro de los últimos 30 s
- WHEN el cliente envía POST /auth/login con email+password
- THEN el sistema responde 200 con `{access_token, refresh_token, token_type, expires_in}`
- AND el access incluye `jti` y `tokenVersion` matching al usuario

#### Scenario: Cuenta LOCKED rechaza login
- GIVEN una cuenta con `status=LOCKED`
- WHEN el cliente envía `POST /auth/login` con credenciales correctas
- THEN el sistema responde 423 Locked
- AND no emite token ni escribe en `common_outbox_events`

#### Scenario: Credenciales inválidas responde 401 sin discriminar
- GIVEN un email conocido registrado
- WHEN el cliente envía `POST /auth/login` con password incorrecto OR email desconocido
- THEN el sistema responde 401 Unauthorized
- AND el cuerpo del error es idéntico en ambos casos
- AND el tiempo de respuesta es indistinguible

### Requirement: Register público solo acepta rol STUDENT
El sistema MUST rechazar el auto-registro de cualquier rol distinto a
`STUDENT` en la capa de dominio, antes de persistir.

#### Scenario: Registro con rol STUDENT exitoso
- GIVEN email y password válidos que cumplen la política de complejidad
- WHEN el cliente envía `POST /auth/register` con `{email, password}` (sin `role`)
- THEN el sistema crea `role=STUDENT`, `status=ACTIVE`, `token_version=1`
- AND responde 201 con el identificador

#### Scenario: Registro con rol distinto a STUDENT rechazado
- GIVEN un payload de registro válido
- WHEN el cliente envía `POST /auth/register` con `role=ADMIN` o `role=INSTRUCTOR`
- THEN el sistema responde 400 Bad Request
- AND no persiste entidad `auth_users`
- AND no genera fila en `common_outbox_events`

### Requirement: Login fail-closed cuando reconciliación está atrasada
El sistema MUST responder 503 con `Retry-After: 30` cuando el reconciliador
detecta eventos `PENDING` por más de 30 s en `common_outbox_events`.

#### Scenario: Reconciliador atrasado produce 503
- GIVEN eventos `AuthUserLoggedIn` con `status=PENDING` por más de 30 s
- WHEN el cliente envía `POST /auth/login` con credenciales válidas
- THEN el sistema responde 503
- AND el header `Retry-After` vale `30`
- AND no emite ningún token

### Requirement: Logout invalida refresh con evento post-commit
El sistema MUST revocar el refresh, escribir `common_outbox_events` con
`event_type=UserLoggedOut` y COMMITear ambas en la misma transacción
(ADR-0027).

#### Scenario: Logout exitoso publica evento atómicamente
- GIVEN un refresh `ACTIVE` asociado a sesión autenticada
- WHEN el cliente envía `POST /auth/logout` con `X-Refresh-Token`
- THEN el sistema marca el refresh como `REVOKED`
- AND persiste `common_outbox_events` con `event_type=UserLoggedOut`
- AND el COMMIT de ambas escrituras es atómico
- AND responde 204 No Content

### Requirement: Refresh comprometido activa revocación de familia
El sistema MUST tratar como comprometida la presentación de un refresh con
`status=ROTATED` o `REVOKED`: incrementa `auth_users.token_version` en 1,
revoca la familia completa y publica `RefreshRevoked` en outbox.

#### Scenario: Refresh ya rotado activa revocación de familia
- GIVEN un refresh con `status=ROTATED` presentado en cualquier filtro autenticado
- WHEN el sistema procesa la solicitud
- THEN incrementa `auth_users.token_version += 1`
- AND marca TODOS los refresh activos de la familia como `REVOKED`
- AND escribe `common_outbox_events` con `event_type=RefreshRevoked`
- AND responde 401 con cuerpo genérico
