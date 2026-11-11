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

## Endpoints planificados

La superficie Auth v1 implementada se detalla en el contrato HTTP de abajo.
Los restantes endpoints de esta lista son parte del diseño funcional, no rutas
expuestas todavía:

```text
POST /api/v1/auth/register
GET  /api/v1/auth/activate/{token}
POST /api/v1/auth/activate
POST /api/v1/auth/resend-activation
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/logout-all
POST /api/v1/auth/change-password
GET  /api/v1/auth/me
GET  /api/v1/auth/me/roles
```

Todas las respuestas de error usan `application/problem+json`.

## Contrato HTTP implementado (v1)

Las rutas canónicas están bajo `/api/v1/auth`; no existe compatibilidad para el
prefijo legado `/auth`. Durante la migración de registro se conserva solamente
el alias temporal `POST /api/v1/users/register`, con el mismo comportamiento
que la ruta canónica.

| Endpoint | Autenticación de entrada | Respuesta exitosa |
| --- | --- | --- |
| `POST /login` | JSON `{ "email", "password" }` | `200` con el par de tokens para el cliente de confianza (BFF o Android). El estado de la cuenta se revela sólo con la contraseña correcta: `403 ACCOUNT_NOT_ACTIVATED` (pendiente), `423 USER_LOCKED` (bloqueada); `INACTIVE`/`SUSPENDED` y toda contraseña incorrecta responden `401 INVALID_CREDENTIALS` |
| `POST /refresh` | `X-Refresh-Token` obligatorio; nunca body JSON | `200` con el par rotado |
| `POST /logout` | `Authorization: Bearer <access>` y `X-Refresh-Token` obligatorios | `204 No Content` |
| `POST /register` | JSON `{ "email", "password", "role": "STUDENT", "firstName", "lastName", "phone", "birthDate"? }` (`birthDate` es opcional) | `202 Accepted`, sin cuerpo y siempre genérico; un cuerpo inválido responde `400 INVALID_REQUEST` |
| `GET /activate/{token}` | token opaco en el path; sólo valida sin consumir | `204 No Content`; `200` con `{ "passwordRequired": true }` si la cuenta la creó un administrador y el titular aún debe elegir contraseña |
| `POST /activate` | JSON `{ "token", "password"? }` (`password` sólo se exige si la validación respondió `passwordRequired`) | `204 No Content`; consume el token y activa la cuenta. Cuenta creada por un administrador: sin `password` `400 PASSWORD_REQUIRED`, con una débil `400 WEAK_PASSWORD`; en ambos casos el token no se consume |
| `POST /resend-activation` | JSON `{ "email" }` | `202 Accepted`, sin cuerpo y siempre genérico |
| `POST /forgot-password` | JSON `{ "email" }` | `202 Accepted`, sin cuerpo y siempre genérico |
| `POST /reset-password` | JSON `{ "token", "newPassword" }` | `200 OK`, sin cuerpo |

Registro público crea una cuenta `PENDING_ACTIVATION`, un token de un uso y un
evento durable de correo en una sola transacción. También guarda los datos
personales del alta: `firstName` y `lastName` (obligatorios, sin espacios en los
extremos, hasta 100 caracteres), `phone` (obligatorio, formato E.164 ya
normalizado: `+` y 8 a 15 dígitos, el primero distinto de 0; el BFF quita
espacios, guiones y paréntesis antes de enviarlo y la API lo valida de forma
estricta) y `birthDate` (opcional, `YYYY-MM-DD`, anterior a hoy). Son datos
personales: no se registran en logs. Por ahora sólo se almacenan; ninguna
pantalla los muestra. El enlace del correo no apunta
a la API sino a la pantalla del BFF `GET /activar/{token}`
(`auth.activation.link-path`, por defecto `/activar/`): esa pantalla valida el
token con `GET /activate/{token}` y muestra el botón «Activar cuenta», que hace
`POST /activar` al BFF; el BFF llama a `POST /activate` con el token en el cuerpo
y redirige a `/cuenta-activada`, de modo que el token deja de estar en la URL.
Si `GET /activate/{token}` responde `200 { "passwordRequired": true }` (cuenta creada
por un administrador), la misma pantalla pide además «Contraseña» y «Repetir
contraseña», las valida con la política del registro y las envía en el cuerpo de
`POST /activate`; una contraseña débil o ausente vuelve a mostrar el formulario con
el mensaje en español y el mismo enlace sigue sirviendo (la API no consume el token).
La recuperación de contraseña sigue el mismo criterio: el correo enlaza a la
pantalla del BFF `GET /restablecer-contrasena/{token}`
(`auth.password-reset.link-path`, por defecto `/restablecer-contrasena/`), que
muestra el formulario de nueva contraseña; el token viaja de vuelta en el cuerpo
de `POST /restablecer-contrasena` y, si todo sale bien, el BFF redirige a
`/login?reset`. La API no tiene un endpoint para validar el token de reset, así
que la pantalla no lo comprueba antes de mostrar el formulario.
Registro duplicado conserva
el mismo `202` vacío; nunca se devuelve el email, ID o estado de la cuenta.
`resend-activation` también responde idénticamente para email inexistente,
activo o pendiente. Registro y reenvío limitados devuelven `429` con
`Retry-After`; si Redis no está disponible devuelven `503` con `Retry-After`.

Un token expirado, reutilizado o inválido en activación devuelve `400
application/problem+json` con código `ACTIVATION_TOKEN_INVALID`; el motivo no
es distinguible. El token no se persiste ni registra en claro: sólo se guarda
su hash y el material pendiente de entrega se cifra hasta que Mailpit/SMTP lo
acepta.

`forgot-password` responde `202` vacío para email inexistente, inactivo o
activo — mismo patrón anti-enumeración que `resend-activation`. Sólo una
cuenta activa invalida sus tokens de reset previos, genera uno nuevo (1 hora
de vigencia) y dispara el email. El presupuesto agotado devuelve `429` con
`Retry-After`.

`reset-password` es la excepción deliberada a la no-enumeración: el token es
un secreto de alta entropía que sólo llega al correo del dueño de la cuenta,
así que el motivo de rechazo SÍ se distingue sin filtrar nada sobre la cuenta:
`404 PASSWORD_RESET_TOKEN_NOT_FOUND` si el hash no existe, `410
PASSWORD_RESET_TOKEN_EXPIRED`/`410 PASSWORD_RESET_TOKEN_ALREADY_USED` si
expiró o ya se usó, `400 WEAK_PASSWORD` (con el detalle de qué regla no se
cumple) o `400 SAME_PASSWORD` si la nueva contraseña es igual a la actual. Un
reset exitoso invalida TODOS los refresh tokens del usuario e incrementa
`auth_users.token_version` primero en MySQL, reflejándolo en Redis vía outbox
para cerrar también los access tokens ya emitidos — si Redis no refleja la
revocación, `AUTH_DEGRADED` bloquea tráfico autenticado hasta la recuperación
durable (ver ADR-0026).

El BFF lee su cookie de sesión internamente y pasa el refresh a Auth mediante
`X-Refresh-Token`; puede ver esos valores sólo en su comunicación
servidor-a-servidor con Auth y no los reenvía al navegador. Los refresh tokens
no se escriben en logs, errores ni eventos HTTP. Un request inválido, una
credencial rechazada o un Bearer faltante/inválido se representa como RFC 9457,
por ejemplo:

```json
{
  "type": "https://menta.dance/problems/authentication_required",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Authentication is required.",
  "code": "AUTHENTICATION_REQUIRED"
}
```

## Administración de usuarios (#378)

Todas las rutas están bajo `/api/v1/admin/users` y exigen un access token con rol
`ADMIN`: las cubre la regla genérica `/api/v1/admin/**` de `SecurityConfig`, sin
una regla propia. Sin token responden `401`; con otro rol, `403`.

| Endpoint | Entrada | Respuesta exitosa |
| --- | --- | --- |
| `GET /api/v1/admin/users` | query opcional: `role`, `q`, `page` (0), `size` (20) | `200` con `{ items, page, size, totalElements, totalPages }` |
| `POST /api/v1/admin/users` | JSON `{ "email", "firstName", "lastName", "role" }` con `ADMIN`, `INSTRUCTOR` o `RECEPTIONIST` | `201` con el usuario creado (`PENDING_ACTIVATION`), sin `Location` |
| `PATCH /api/v1/admin/users/{userId}/role` | JSON `{ "role" }` con `ADMIN`, `INSTRUCTOR`, `STUDENT` o `RECEPTIONIST` | `200` con el usuario actualizado |

**Listado.** El orden es fijo: email y, a igual email, id; no hay `sort`. `size`
máximo es 50 y un valor mayor se rechaza con `400 INVALID_REQUEST`, nunca se
recorta. `role` filtra por rol. `q` busca por email, nombre o apellido sin
distinguir mayúsculas; el texto se toma de forma literal (`%`, `_` y `\` no son
comodines), un `q` vacío o en blanco equivale a no filtrar y más de 100
caracteres es `400`. Cada item es `{ id, email, firstName, lastName, role, status,
demo, createdAt }`; los nombres son `null` en cuentas sin perfil. La respuesta
nunca incluye el hash de la contraseña ni `token_version`. No hay índice para la
búsqueda: la tabla es chica y un `LIKE '%…%'` no lo aprovecharía.

**Alta de cuentas de staff.** Crea una cuenta `INSTRUCTOR`, `RECEPTIONIST` o `ADMIN`
(`STUDENT` es `400`: los alumnos se registran solos) y le envía al titular el correo
de activación del registro (US-AUTH-003), con el mismo token, cifrado de entrega y
evento de outbox; el componente que arma ese envío es compartido con el registro y
con el reenvío (`ActivationTokenIssuer`), por lo que `resend-activation` funciona
igual para estas cuentas. La cuenta nace `PENDING_ACTIVATION`, `demo = false`, con
nombre y apellido y sin teléfono (el teléfono es opcional en el perfil; el registro
público lo sigue exigiendo).

*No existe contraseña por defecto, ni constante compartida.* La cuenta se guarda
con el hash de un secreto aleatorio de 32 bytes (`SecureRandom`), distinto por
cuenta, que se descarta en el mismo instante: nadie lo conoce y no se registra ni
se devuelve. Además la fila lleva `must_set_password = TRUE` (V30). Hasta activar
no se puede iniciar sesión: el login responde `401 INVALID_CREDENTIALS` para
cualquier contraseña, porque el hash es inutilizable y además el estado es
`PENDING_ACTIVATION`.

*El titular elige su contraseña al activar.* El enlace del correo lleva a la
pantalla de activación del BFF, que valida el token con `GET /activate/{token}`: si
la cuenta requiere contraseña responde `200 { "passwordRequired": true }` (para
cuentas del registro público sigue siendo `204` vacío, como antes), y la pantalla
pide la contraseña. `POST /activate` la exige en el cuerpo, la valida con la misma
política que el registro y el reseteo (`Password`: 8 caracteres como mínimo, una
mayúscula y un número), y en una sola transacción guarda el hash real, apaga
`must_set_password`, consume el token y activa la cuenta. Una contraseña ausente
o débil se rechaza **antes** de consumir el token, así que el titular reintenta con
el mismo enlace. Para una cuenta con `must_set_password = FALSE` el comportamiento
de `POST /activate` es exactamente el anterior y cualquier `password` enviado se
ignora.

*Errores.* Email ya registrado (sin distinguir mayúsculas): `409
EMAIL_ALREADY_REGISTERED` (a diferencia del registro público, que responde `202`
para no enumerar cuentas, acá el llamante es un administrador); cuerpo inválido,
email mal formado, nombre vacío o de más de 100 caracteres, rol desconocido o
`STUDENT`: `400 INVALID_REQUEST`. No hay rate limit propio: la ruta es
sólo para administradores autenticados.

*Atomicidad y auditoría.* La cuenta, el token de activación, el evento de outbox
del correo y la fila de auditoría `USER_CREATED` (administrador, cuenta, rol
anterior `NULL` y rol nuevo) se confirman en una sola transacción. La contraseña
elegida y el hash nunca aparecen en logs, respuestas, auditoría ni eventos.

**Cambio de rol.** El destino puede ser cualquiera de los cuatro roles y el
origen puede estar en cualquier estado. En una sola transacción se cambia el rol,
se incrementa `token_version`, se revocan todos los refresh tokens del usuario, se
publica el evento `auth.UserRoleChanged` en el outbox y se registra la auditoría.
El evento lo proyecta el mismo handler que el logout y el reset de contraseña, de
modo que un access token emitido con el rol anterior deja de autorizar apenas el
worker proyecta la versión a Redis, sin esperar a que expire. Asignar el rol que
el usuario ya tiene es un no-op idempotente: `200` sin tocar sesiones, evento ni
auditoría.

**Regla del último administrador.** El sistema no puede quedarse sin un `ADMIN`
activo. Quitarle el rol a un administrador `ACTIVE` cuando no queda otro responde
`409 LAST_ADMIN_CANNOT_BE_DEMOTED`, también si se lo quita a sí mismo. Un
administrador `INACTIVE` no cuenta. Para que dos administradores que se degradan
entre sí a la vez no puedan dejar el sistema sin ninguno, la transacción bloquea
(`SELECT … FOR UPDATE`, en orden de id) todas las filas de administradores activos
y después la del usuario objetivo, y cuenta sobre lo bloqueado. Un usuario
inexistente responde `404 USER_NOT_FOUND`; un body inválido, un rol desconocido o
un `userId` que no es UUID, `400 INVALID_REQUEST`.

**Auditoría.** Cada alta y cada cambio efectivo agrega una fila a `auth_user_audit` (solo
inserción, ver [modelo de datos](22-DATA-MODEL.md)) con el usuario afectado, el
administrador que lo hizo, la acción (`USER_CREATED` en el alta, con rol anterior
`NULL`, o `ROLE_CHANGED` en el cambio de rol), el rol anterior y el nuevo y el
instante, que fija la aplicación.

La pantalla del BFF `/gestion/usuarios` ya muestra el listado de solo lectura
(ver [BFF y Tailwind](31-BFF-TAILWIND.md#usuarios-gestionusuarios)). Queda fuera y
llega en un PR posterior: el alta y el cambio de rol desde la web, incluido el
campo de contraseña de la pantalla de activación.

## Renovación y reutilización

`POST /api/v1/auth/refresh` rota obligatoriamente el refresh: revoca el token presentado y
emite un UUID nuevo. La reutilización de un token rotado, `POST /logout-all`,
todo reset/cambio de contraseña y todo cambio de rol hecho por un administrador
revocan **todas** las sesiones, incrementan
`auth_users.token_version` y exigen login nuevo. MySQL es la autoridad absoluta:
la transacción de revocación se confirma primero en MySQL y los filtros consultan
su versión/estado antes de aceptar un JWT. Redis sólo refleja blacklist y versión.
Si Redis no confirma esa reflexión, Auth marca el estado global MySQL como
`AUTH_DEGRADED`; login, refresh y cualquier ruta autenticada quedan bloqueados
hasta que el worker repare Redis y restablezca `NORMAL`. Si MySQL no está
disponible, se niegan esos mismos flujos. `auth_outbox` aporta reintento y
reparación, no autoridad. La respuesta web queda mediada por el BFF; Android,
como cliente de confianza, almacena el par rotado de la respuesta en Keystore.

## Logout y revocación

`POST /api/v1/auth/logout` requiere `Authorization: Bearer <access>` y
`X-Refresh-Token`. En la misma unidad
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
autorización. Tampoco se registra el token de activación. La API redacciona su URI en los
access logs como `/api/v1/auth/activate/[REDACTED]`. El BFF no registra URIs de
petición y sus páginas con el token (`/activar/{token}`, `/restablecer-contrasena/{token}`
y el reintento de sus `POST`) responden `Cache-Control: no-store` y `Referrer-Policy:
no-referrer`. Los access logs de NGINX redactan el token
de los enlaces del correo: `GET /activar/{token}` y
`GET /restablecer-contrasena/{token}` quedan como `/activar/[REDACTED]` y
`/restablecer-contrasena/[REDACTED]`, también en el Referer (`log_format main`
en `nginx.conf` y en `nginx.production.conf.example`). Los eventos
de login, refresh, revocación, reutilización y denegación incluyen
`correlationId`, usuario/dispositivo cuando corresponda y resultado.

### Presupuestos de login y origen web

> **Estado al 2026-08-15:** implementado y validado (PR #77, #79, #80, #81).

El flujo web `Nginx -> BFF -> API` propaga el origen del cliente en la llamada de
login del BFF, de modo que la API ya no identifica al BFF como peer ni agrupa a
todos los usuarios web en un mismo fingerprint.

[ADR-0035](adr/0035-trusted-client-origin-propagation.md) separó las
responsabilidades así:

* Nginx impone un techo de requests por origen para `POST /login` del BFF, antes
  de llegar a bcrypt o Redis. La clave se anula fuera de `POST`, así que servir
  el formulario no consume presupuesto.
* Auth conserva presupuestos semánticos de **fallos** por email y por origen,
  verificados antes de bcrypt y cobrados sólo tras un fallo contabilizable.
* Un login exitoso limpia el presupuesto del email, pero no consume ni limpia el
  presupuesto de fallos por origen: limpiarlo permitiría a un atacante con cuenta
  propia borrar su contador a voluntad.
* El BFF propaga un origen canónico sólo desde un proxy confiable —`X-Real-IP`
  saneado por Nginx— y lo envía como un `X-Forwarded-For` de un solo valor; nunca
  reenvía la cadena recibida.
* Agotar el presupuesto devuelve `429` con `Retry-After` y **nunca** cambia el
  estado de la cuenta: `LOCKED` queda reservado para acción administrativa, para
  no habilitar una denegación de servicio dirigida contra una cuenta ajena.

Los clientes directos, como Android, no atraviesan el BFF y nunca presentaron
este colapso de identidad.

### Errores de login en el BFF

El formulario web traduce cada respuesta de error de `POST /login` en un mensaje
en español (#479). Ninguna llega al usuario como error del servidor:

| Respuesta de Auth | Mensaje en `/login` |
| --- | --- |
| `401` | «Email o contraseña inválidos» (genérico) |
| `423 USER_LOCKED` | cuenta bloqueada; contactar a la academia |
| `429` con `Retry-After` | demasiados intentos, con la espera en segundos o minutos redondeados hacia arriba; sin cabecera o con valor ilegible, «unos minutos» |
| `403 ACCOUNT_NOT_ACTIVATED` | cuenta sin activar, con enlace a `/reenviar-activacion` |
| `5xx`, caída o cualquier otro estado | «El servicio no está disponible por el momento» |

El `423` y el `403` sólo llegan con la contraseña correcta, así que el mensaje no
revela si un email existe. Nunca se muestran códigos ni cuerpos de la API. El
refresh conserva su propio contrato (`423` es `RefreshTokenRevokedException`).

## Contrato OpenAPI

El contrato formal y versionado de esta superficie vive en
[`api/openapi/auth-v1.yaml`](../api/openapi/auth-v1.yaml). Este documento explica
el porqué de las decisiones; el YAML define la forma exacta de cada request,
respuesta, header y código de error. Ante una discrepancia, el YAML manda: lo
valida `redocly lint` en CI.
