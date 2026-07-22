# API de Autenticación y Sesiones

## Reglas de custodia

- **Web:** el BFF realiza login, refresh y logout contra Auth. El navegador sólo
  recibe cookies de sesión del BFF `HttpOnly`, `Secure` y `SameSite`; nunca
  access/refresh tokens ni JavaScript los almacena.
- **Android:** guarda el refresh sólo con Android Keystore/
  EncryptedSharedPreferences y lo envía en `X-Refresh-Token`. Nunca se loguea.
- El access token es JWT de 15 minutos con `sub`, `roles`, `jti`, `tokenVersion`,
  `iat`, `exp`, `iss` y `aud`. El refresh es UUID opaco, hasheado SHA-256 en
  `auth_refresh_tokens` y dura 7 días.

## Endpoints

```text
POST /api/v1/auth/register
GET  /api/v1/auth/activate/{token}
POST /api/v1/auth/resend-activation
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
POST /api/v1/auth/forgot-password
POST /api/v1/auth/reset-password
POST /api/v1/auth/change-password
GET  /api/v1/auth/me
GET  /api/v1/auth/me/roles
```

Todas las respuestas de error usan `application/problem+json`.

## Renovación y reutilización

`POST /refresh` rota obligatoriamente el refresh: revoca el token presentado y
emite un UUID nuevo. La reutilización de un token rotado, `POST /logout-all` y
todo reset/cambio de contraseña revocan **todas** las sesiones, incrementan
`auth_users.token_version` y exigen login nuevo. MySQL es la autoridad absoluta:
la transacción de revocación se confirma primero en MySQL y los filtros consultan
su versión/estado antes de aceptar un JWT. Redis sólo refleja blacklist y versión.
Si Redis no confirma esa reflexión, Auth marca el estado global MySQL como
`AUTH_DEGRADED`; login, refresh y cualquier ruta autenticada quedan bloqueados
hasta que el worker repare Redis y restablezca `NORMAL`. Si MySQL no está
disponible, se niegan esos mismos flujos. `auth_outbox` aporta reintento y
reparación, no autoridad. La respuesta web queda mediada por el BFF; Android
recibe el nuevo token sólo en el header seguro definido para la app.

## Logout y revocación

`POST /logout` requiere `Authorization: Bearer <access>`. En la misma unidad
transaccional se revoca el refresh, se persiste el `jti` revocado y se inserta un
evento en `auth_outbox` para reflejar `blacklist:jti:{jti}` en Redis hasta `exp`.
Logout no confirma éxito hasta que Redis lo refleje. Si Redis no puede hacerlo,
Auth activa `AUTH_DEGRADED`; si MySQL está caído, deniega la operación.

En cada ruta autenticada, `OncePerRequestFilter` valida firma/claims y consulta
MySQL por `AUTH_DEGRADED`, `jti` revocado y `tokenVersion`; Redis es una réplica
que debe coincidir. MySQL inaccesible, `AUTH_DEGRADED` o Redis no reconciliado
devuelven 503 y bloquean la solicitud; no existe modo fail-open.

## Recuperación de contraseña

Los tokens son aleatorios criptográficamente, de un solo uso y con vencimiento
corto (una hora). MySQL guarda sólo `token_hash`, vencimiento y uso; el token en
claro existe únicamente en el enlace enviado al usuario. Restablecer o cambiar la
contraseña aplica la revocación global MySQL-first y exige Redis reconciliado.

## Roles

Los únicos roles de personas son `ALUMNO`, `PROFESOR`, `RECEPCIONISTA` y `ADMIN`.
Un lector QR se autentica como dispositivo técnico y no es un rol.

## Seguridad y auditoría

Nunca se registran passwords, JWT, refresh tokens, cookies ni encabezados de
autorización. Los eventos de login, refresh, revocación, reutilización y denegación
incluyen `correlationId`, usuario/dispositivo cuando corresponda y resultado.
