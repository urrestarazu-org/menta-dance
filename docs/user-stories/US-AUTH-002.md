# US-AUTH-002: Inicio de sesión

## Historia
Como usuario registrado, quiero iniciar sesión para acceder a mis permisos y
sesión de forma segura.

## Aceptación

- Auth verifica primero la contraseña y revela el estado de la cuenta **solo
  cuando las credenciales son correctas**. Email desconocido y contraseña
  incorrecta (con cualquier estado de cuenta) responden el mismo
  `401 INVALID_CREDENTIALS`, sin revelar cuál dato falló.
- Con credenciales correctas: `PENDING_ACTIVATION` responde `403
  ACCOUNT_NOT_ACTIVATED`; `LOCKED` responde `423 USER_LOCKED`; `INACTIVE` y
  `SUSPENDED` se mantienen en `401 INVALID_CREDENTIALS` (revelar una suspensión
  administrativa es una decisión de producto aparte).
- Esos rechazos con credenciales correctas no consumen el presupuesto de fallos
  (ADR-0035) y se auditan con un resultado propio, sin registrar secretos.
- Emite access JWT de 15 minutos con `jti` y refresh UUID opaco de 7 días,
  hasheado en `auth_refresh_tokens`.
- Para Web, el BFF recibe/custodia tokens y el navegador no los recibe ni los
  almacena. Para Android, el refresh queda en Keystore/EncryptedSharedPreferences.
- La respuesta de error sigue `application/problem+json`; intentos fallidos se
  limitan y auditan sin registrar secretos.

## Hecho cuando

Existen pruebas de credenciales, estado de cuenta tras la contraseña, bloqueo, custodia por cliente y formato de
error; el contrato queda documentado en OpenAPI.
