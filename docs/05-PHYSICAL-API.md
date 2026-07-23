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
requiere `studentId` y actor `RECEPCIONISTA`; `QR` requiere credenciales QR de
dispositivo y obtiene el alumno desde claims firmados, sin aceptar `studentId`.
Ambas variantes son excluyentes. El alumno debe poseer una
`physical_capacity_assignment` confirmada para esa sesión. El check-in es
idempotente por `sessionId + studentId`; una repetición, sea manual o por QR,
devuelve `200 OK` con la asistencia existente. Credenciales inválidas, dispositivo
revocado o falta de autorización permanecen errores `application/problem+json`.

`MANUAL` valida primero rol `RECEPCIONISTA`, `studentId`, asignación y sesión; no
evalúa firma, dispositivo ni ventana QR. Luego usa el lock de asistencia común
`checkin:attendance:{sessionId}:{studentId}` con nonce y Lua compare-and-delete.
`QR` valida credenciales del lector, firma, expiración, ventana y claims; deriva
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
