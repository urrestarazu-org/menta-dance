# US-AUTH-003: Activación de cuenta

## Historia
Como usuario recién registrado, quiero activar mi cuenta desde un enlace de
correo para poder iniciar sesión.

## Aceptación

- `GET /api/v1/auth/activate/{token}` sólo valida un token de un solo uso y
  expiración; no modifica `auth_users` ni consume el token.
- `POST /api/v1/auth/activate` recibe JSON `{ "token" }`, activa `auth_users`
  e invalida el token.
- El enlace del correo apunta al BFF (`GET /activar/{token}`), que valida el
  token, muestra el botón «Activar cuenta» y, al confirmar (`POST /activar`),
  llama a la API con el token en el cuerpo y redirige a `/cuenta-activada`. Un
  token inválido, vencido o usado muestra un mensaje en español; 429 y 503
  permiten reintentar. El reenvío desde esa pantalla se agrega en #359 (paso 3).
- `POST /api/v1/auth/resend-activation` emite un token nuevo sin revelar si el
  email existe.
- `POST /api/v1/auth/register` crea el usuario pendiente y responde `202` sin
  revelar si el email ya estaba registrado; `/api/v1/users/register` es un alias
  temporal con el mismo contrato.
- Registro y reenvío devuelven `429 Retry-After` ante límite y `503 Retry-After`
  si Redis no está disponible; el token no aparece en logs ni en respuestas.
- Cuentas creadas por un administrador (#378) reutilizan este flujo (mismo
  token, correo y reenvío), pero nacen sin contraseña utilizable ni por defecto: `GET /activate/{token}` responde `200
  { "passwordRequired": true }` en lugar de `204`, y `POST /activate` exige
  `password` (misma política que el registro y el reseteo), la guarda y activa
  la cuenta en una sola transacción. Sin `password` responde `400
  PASSWORD_REQUIRED`; con una débil, `400 WEAK_PASSWORD`; en ambos casos el
  token no se consume. Para cuentas del registro público nada cambia. El campo
  de contraseña en la pantalla del BFF llega en el PR de `/gestion/usuarios`.
- El envío de correo usa un puerto/worker interno, sin RabbitMQ ni HTTP interno.
- Errores siguen `application/problem+json`; tokens y datos personales no se
  incluyen en logs.

## Hecho cuando

Pruebas cubren registro → outbox → entrega → activación → login, rollback
atómico, activación concurrente, token válido/vencido/reutilizado, reenvío y
rate limiting. Bruno mantiene ejemplos sin tokens ni credenciales versionados.
