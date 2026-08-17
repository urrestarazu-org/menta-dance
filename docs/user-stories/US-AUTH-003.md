# US-AUTH-003: Activación de cuenta

## Historia
Como usuario recién registrado, quiero activar mi cuenta desde un enlace de
correo para poder iniciar sesión.

## Aceptación

- `GET /api/v1/auth/activate/{token}` sólo valida un token de un solo uso y
  expiración; no modifica `auth_users` ni consume el token.
- `POST /api/v1/auth/activate` recibe JSON `{ "token" }`, activa `auth_users`
  e invalida el token. Una pantalla intermedia en el BFF para retirar el token
  de la URI queda fuera de alcance.
- `POST /api/v1/auth/resend-activation` emite un token nuevo sin revelar si el
  email existe.
- `POST /api/v1/auth/register` crea el usuario pendiente y responde `202` sin
  revelar si el email ya estaba registrado; `/api/v1/users/register` es un alias
  temporal con el mismo contrato.
- Registro y reenvío devuelven `429 Retry-After` ante límite y `503 Retry-After`
  si Redis no está disponible; el token no aparece en logs ni en respuestas.
- El envío de correo usa un puerto/worker interno, sin RabbitMQ ni HTTP interno.
- Errores siguen `application/problem+json`; tokens y datos personales no se
  incluyen en logs.

## Hecho cuando

Pruebas cubren registro → outbox → entrega → activación → login, rollback
atómico, activación concurrente, token válido/vencido/reutilizado, reenvío y
rate limiting. Bruno mantiene ejemplos sin tokens ni credenciales versionados.
