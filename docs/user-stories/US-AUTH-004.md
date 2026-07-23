# US-AUTH-004: Renovación de sesión

## Historia
Como usuario autenticado, quiero renovar mi sesión sin volver a ingresar
credenciales mientras mi sesión siga siendo válida.

## Aceptación

- `POST /api/v1/auth/refresh` recibe el refresh en cookie gestionada por BFF
  para Web o `X-Refresh-Token` para Android; nunca en body JSON.
- Cada renovación rota obligatoriamente el refresh y revoca el anterior.
- La reutilización de un refresh rotado revoca todas las sesiones del usuario,
  incrementa `auth_users.token_version`, invalida sus access tokens mediante
  una transacción MySQL-first. Redis refleja la versión; si no se reconcilia,
  `AUTH_DEGRADED` bloquea login, refresh y tráfico autenticado hasta reparación
  por outbox. Si MySQL no está disponible, Auth falla cerrada. Se registra un
  incidente de seguridad.
- El refresh se valida por `token_hash` en `auth_refresh_tokens`; errores usan
  `application/problem+json` y no exponen el token.

## Hecho cuando

Pruebas unitarias e integración cubren rotación, expiración, reutilización y
revocación global.
