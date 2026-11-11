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
- Cuentas de staff creadas por un administrador (`POST /api/v1/admin/users`,
  #378) reutilizan este flujo (mismo token, correo y reenvío), pero nacen sin
  contraseña utilizable ni por defecto: `GET /activate/{token}` responde `200
  { "passwordRequired": true }` en lugar de `204`, y `POST /activate` exige
  `password` (misma política que el registro y el reseteo), la guarda y activa
  la cuenta en una sola transacción. Sin `password` responde `400
  PASSWORD_REQUIRED`; con una débil, `400 WEAK_PASSWORD`; en ambos casos el
  token no se consume. Para cuentas del registro público nada cambia.
- La pantalla del BFF `GET /activar/{token}` distingue ambos casos por la respuesta de
  la validación del token. Con `204` queda exactamente como antes (sólo el botón
  «Activar cuenta»). Con `200 { "passwordRequired": true }` muestra además
  «Contraseña» y «Repetir contraseña» (`autocomplete="new-password"`, etiquetas,
  política asociada con `aria-describedby`, resumen de errores y `aria-invalid`), las
  valida en el BFF con la misma política que el registro y la recuperación (8
  caracteres como mínimo, una mayúscula y un número) y confirma que coinciden antes de
  llamar a la API. Una contraseña débil, ausente o que no coincide (en el BFF o
  rechazada por la API con `WEAK_PASSWORD`/`PASSWORD_REQUIRED`) vuelve a mostrar el
  formulario con un mensaje en español y los campos de contraseña vacíos; el enlace
  sigue siendo reutilizable. Si el `POST /activar` llega sin los campos de contraseña
  para una cuenta que la requiere, la pantalla pasa a mostrar el formulario con
  contraseña en vez de fallar. Un token inválido, vencido o usado conserva el
  mensaje de siempre. La contraseña no se registra en logs, no se guarda en la sesión
  ni se vuelve a escribir en la página; el formulario lleva CSRF y las páginas que
  contienen el token no se cachean ni envían `Referer`.
- El envío de correo usa un puerto/worker interno, sin RabbitMQ ni HTTP interno.
- Errores siguen `application/problem+json`; tokens y datos personales no se
  incluyen en logs.

## Hecho cuando

Pruebas cubren registro → outbox → entrega → activación → login, rollback
atómico, activación concurrente, token válido/vencido/reutilizado, reenvío y
rate limiting. Bruno mantiene ejemplos sin tokens ni credenciales versionados.
