# US-AUTH-002: Inicio de sesión

## Historia
Como usuario registrado, quiero iniciar sesión para acceder a mis permisos y
sesión de forma segura.

## Aceptación

- Auth valida credenciales y estado de cuenta sin revelar cuál dato falló.
- Emite access JWT de 15 minutos con `jti` y refresh UUID opaco de 7 días,
  hasheado en `auth_refresh_tokens`.
- Para Web, el BFF recibe/custodia tokens y el navegador no los recibe ni los
  almacena. Para Android, el refresh queda en Keystore/EncryptedSharedPreferences.
- La respuesta de error sigue `application/problem+json`; intentos fallidos se
  limitan y auditan sin registrar secretos.

## Hecho cuando

Existen pruebas de credenciales, bloqueo, custodia por cliente y formato de
error; el contrato queda documentado en OpenAPI.
