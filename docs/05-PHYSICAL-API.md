# API de Academia Física

Physical es dueño de cursos presenciales, sesiones, capacidad, lectores QR y
asistencia. Billing es dueño de precios, quotes, pagos y sus efectos financieros.

## Rutas canónicas

```text
GET    /api/v1/admin/physical/courses
POST   /api/v1/admin/physical/courses
PATCH  /api/v1/admin/physical/courses/{courseId}
GET    /api/v1/admin/physical/courses/{courseId}/sessions
POST   /api/v1/admin/physical/courses/{courseId}/sessions
POST   /api/v1/admin/physical/courses/{courseId}/sessions/batch
PATCH  /api/v1/admin/physical/sessions/{sessionId}
GET    /api/v1/physical/attendance/me
POST   /api/v1/physical/sessions/{sessionId}/check-ins
GET    /api/v1/physical/sessions/{sessionId}/check-ins
POST   /api/v1/physical/sessions/{sessionId}/access-qr
GET    /api/v1/physical/devices
POST   /api/v1/physical/devices
POST   /api/v1/physical/devices/{deviceId}/rotate-secret
POST   /api/v1/physical/devices/{deviceId}/revoke
```

Alumnos y visitantes consultan cursos y sesiones únicamente mediante
`/api/v1/catalog/courses`; estas rutas `/admin/physical` son de gestión para
`ADMIN` o el `PROFESOR` propietario.

`GET /physical/attendance/me` es autoservicio de `ALUMNO` y admite `month`
(`YYYY-MM`) e `includeAbsent`; devuelve únicamente las sesiones asignadas al
alumno y su resumen de asistencia del período.

Una **sesión** es la ocurrencia concreta de un curso: fecha, hora y cupo. No
existen endpoints de reserva ni listas de espera: un pago confirmado crea las
asignaciones de cupo necesarias y reduce la capacidad de cada sesión.

## Check-in

`POST /sessions/{sessionId}/access-qr` permite al `ALUMNO` autenticado obtener
una credencial QR efímera para una sesión concreta. Physical valida que el
alumno tenga una `physical_capacity_assignment` confirmada y que la sesión esté
dentro de su ventana de check-in. La respuesta contiene `qrCredentials` firmadas
con `studentId`, `sessionId`, `jti` y `exp`; Android las renderiza localmente como
una imagen QR, las renueva antes de expirar y nunca recibe un secreto del lector.
La credencial no es un identificador permanente ni habilita acceso fuera de la
ventana. No hay modo offline: si la API, MySQL o Redis no están disponibles, no
se emite ni procesa el QR.

`POST /sessions/{sessionId}/check-ins` usa un request discriminado: `MANUAL`
requiere `studentId` y actor `RECEPCIONISTA`; `QR` requiere `qrCredentials` más
el `deviceId` y el `deviceToken` de un lector registrado, y obtiene el alumno
desde claims firmados, sin aceptar `studentId`.
Ambas variantes son excluyentes. El alumno debe poseer una
`physical_capacity_assignment` confirmada para esa sesión. El check-in es
idempotente por `sessionId + studentId`; una repetición, sea manual o por QR,
devuelve `200 OK` con la asistencia existente. Credenciales inválidas, dispositivo
revocado o vencido o falta de autorización permanecen errores
`application/problem+json`.

`MANUAL` valida primero rol `RECEPCIONISTA`, `studentId`, asignación y sesión; no
evalúa firma, dispositivo ni ventana QR. Luego usa el lock de asistencia común
`checkin:attendance:{sessionId}:{studentId}` con nonce y Lua compare-and-delete.
`QR` autentica al lector contra el registro de dispositivos (véase
[Dispositivos QR](#dispositivos-qr)) y valida firma, expiración, ventana y claims; deriva
`studentId`, valida asignación/sesión, adquiere primero
`checkin:qr:{qrJti}` para replay y luego el mismo lock de asistencia común. La
unicidad de `physical_attendance(session_id, student_id)` es la última defensa de
ambos flujos. Si Redis no está disponible, el check-in autenticado no se procesa.

## Dispositivos QR

Cada lector se registra como entidad técnica. Se entrega una vez un secreto
opaco; sólo se guarda su hash. El dispositivo tiene estado, expiración,
revocación, rotación y auditoría. Una credencial expirada/revocada no puede
registrar asistencia. No utiliza roles de usuario ni comparte secretos con
personas.

### Autenticación del lector en el check-in QR

El check-in `QR` autentica al lector contra este registro (#266). El campo
`deviceId` del body es el UUID del dispositivo registrado y `deviceToken` es el
secreto propio de ese dispositivo, el mismo que se entregó una vez al registrarlo
o rotarlo. Ambos siguen viajando en el body JSON; no hay header nuevo. El secreto
compartido anterior (`app.physical.checkin.device-token`) ya no autentica nada.

| Situación | Respuesta |
|---|---|
| `deviceId` desconocido, que no es UUID, o `deviceToken` incorrecto | `401 INVALID_DEVICE_TOKEN` (mismo cuerpo en los tres casos) |
| Dispositivo `REVOKED`, secreto correcto | `401 DEVICE_REVOKED` |
| Dispositivo vencido (`expiresAt <= ahora`), secreto correcto | `401 DEVICE_EXPIRED` |
| `deviceId` o `deviceToken` en blanco o ausentes | `400 INVALID_REQUEST` |

- Un `deviceId` que no es UUID se rechaza antes de consultar la base de datos.
- `DEVICE_REVOKED` y `DEVICE_EXPIRED` sólo se informan con el secreto correcto;
  con un secreto incorrecto la respuesta es siempre `INVALID_DEVICE_TOKEN`. Si el
  dispositivo está revocado y vencido, gana `DEVICE_REVOKED`.
- Un dispositivo vencido no se recupera rotando su secreto: debe registrarse de
  nuevo. `DEVICE_REVOKED` también existe como `409` al rotar el secreto de un
  dispositivo revocado (endpoint de administración); en el check-in es `401`.
- Una autenticación fallida corta las validaciones siguientes y nunca toca Redis.
- La asistencia guarda el UUID del dispositivo autenticado en `device_id`. Las
  filas históricas conservan su valor de texto libre.
- Observabilidad: cada rechazo escribe una línea `WARN` que empieza con
  `alarm=physical_checkin_device_rejected reason=<motivo> deviceId=<uuid|none>` e
  incrementa el contador `physical.checkin.device.rejected` con el tag `reason`
  (`unknown_or_invalid`, `revoked`, `expired`). Nunca se registra el secreto.

**Runbook de despliegue (corte directo, sin ventana de convivencia):**

1. Registrar cada lector con `POST /api/v1/admin/physical/devices` y guardar el
   `secret` de la respuesta (se muestra una sola vez).
2. Configurar en cada lector su `deviceId` (UUID) y su `deviceToken` (ese secreto).
3. Desplegar la versión con la autenticación por registro. Un lector sin registrar
   recibe `401 INVALID_DEVICE_TOKEN`.
4. Eliminar `app.physical.checkin.device-token` de la configuración. Si sigue
   presente, la API arranca igual y registra un `WARN` indicando que se ignora
   (nunca su valor).

`GET /api/v1/physical/devices` es exclusivo de `ADMIN` y devuelve la lista
paginada de dispositivos. Cada elemento incluye `deviceId`, `name`, `location`,
`status`, `createdAt`, `expiresAt`, `rotatedAt` y `revokedAt`; nunca incluye
`secret` ni `secretHash`. Los estados son `ACTIVE`, `EXPIRED` y `REVOKED`.
Un dispositivo se considera `EXPIRED` cuando `expiresAt` ya pasó, aunque el
estado persistido todavía no haya sido actualizado por un proceso de fondo.

## Autorización

- `PROFESOR`: sólo sus propios cursos, sesiones y cupos.
- `RECEPCIONISTA`: sólo consulta/registra check-ins.
- `ADMIN`: gestión completa, incluidos dispositivos.
- `ALUMNO`: sólo consulta su información, obtiene su QR efímero y lo presenta.

Todos los errores se devuelven como `application/problem+json`.
