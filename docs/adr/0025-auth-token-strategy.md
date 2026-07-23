# ADR-0025: Estrategia de Tokens y Sesiones

**Estado:** Aceptado

## Decisión

El access token es JWT corto (15 min) con `jti` y `tokenVersion`; el refresh es
UUID opaco (7 días) y se almacena como SHA-256 en MySQL. La rotación de refresh
es obligatoria. La reutilización de un refresh rotado, logout-all y reset/cambio
de contraseña revocan todas las sesiones e incrementan `auth_users.token_version`
en una transacción MySQL-first. MySQL es la autoridad para versión, `jti` revocado
y estado global `AUTH_DEGRADED`; Redis sólo refleja esos datos. Si Redis no puede
confirmar la reflexión, Auth persiste `AUTH_DEGRADED` y bloquea login, refresh y
rutas autenticadas hasta que el worker de outbox lo repare. Si MySQL no está
disponible, los mismos flujos fallan cerrados.

El BFF es custodio exclusivo de tokens para Web: el navegador nunca recibe ni
almacena tokens. Android usa Keystore/EncryptedSharedPreferences y
`X-Refresh-Token`.

Logout revoca el refresh y escribe un evento de blacklist en `auth_outbox` dentro
de la misma transacción MySQL. Un worker interno aplica/reintenta
`blacklist:jti:{jti}` en Redis con TTL hasta `exp`. La operación no confirma éxito
sin la seguridad requerida; el evento durable permite recuperación.

MySQL es autoritativo para validar rutas autenticadas. El filtro valida `jti` y
compara la `tokenVersion` del JWT con MySQL, y rechaza durante `AUTH_DEGRADED`.
Redis debe permanecer reconciliado como réplica; cualquier indisponibilidad de
MySQL o desincronización de Redis falla cerrada. No existe política fail-open.
