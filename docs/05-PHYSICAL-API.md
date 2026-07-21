# API de Academia Física

[← Volver al índice](./README.md) | [← Virtual API](./04-VIRTUAL-API.md) | [Pagos presenciales →](./28-PHYSICAL-CLASS-PAYMENTS.md)

> [!IMPORTANT]
> Physical posee calendario, sesiones, capacidad y asistencia. Billing posee
> precios, quotes, Purchases y Payments. La autoridad funcional es
> [Pagos de Clases Presenciales](./28-PHYSICAL-CLASS-PAYMENTS.md).

---

## Resumen

La **Physical API** gestiona cursos recurrentes, profesores, sesiones, capacidad,
check-in/check-out y asistencia. No posee precios ni pagos.

**Características principales:**

- Sistema de check-in automatizado con lector QR
- Check-in manual para casos especiales (recepcionista)
- Gestión de horarios y disponibilidad
- Control de asistencia con correcciones
- Asignación automática de cupos confirmados por Billing
- Integración con Google Calendar para recordatorios
- Push notifications via Firebase FCM

---

## Diseño de Endpoints REST

### Cursos Presenciales

```
GET    /api/v1/physical/courses                    # Listar cursos presenciales
GET    /api/v1/physical/courses/{id}               # Detalle de curso
GET    /api/v1/physical/courses/{id}/schedules     # Horarios del curso
GET    /api/v1/physical/courses/{id}/availability  # Disponibilidad de cupos
```

### Check-in / Check-out

```
POST   /api/v1/physical/checkin/scan               # Check-in automático (lector QR)
POST   /api/v1/physical/checkin/manual             # Check-in manual (recepcionista)
POST   /api/v1/physical/checkout/scan              # Check-out automático
GET    /api/v1/physical/checkin/status/{userId}    # Estado actual del usuario
GET    /api/v1/physical/checkin/today              # Check-ins del día (admin)
```

### Código QR (App Android)

```
GET    /api/v1/physical/sessions/{sessionId}/check-in-qr         # Generar QR temporal
POST   /api/v1/physical/sessions/{sessionId}/check-in-qr/refresh # Refrescar QR expirado
```

### Asistencia

```
GET    /api/v1/physical/attendance/me              # Mi historial de asistencia
GET    /api/v1/physical/attendance/me/stats        # Mis estadísticas de asistencia
GET    /api/v1/physical/attendance/course/{id}     # Asistencia por curso (profesor)
GET    /api/v1/physical/attendance/search          # Buscar asistencias (recepcionista)
PATCH  /api/v1/physical/attendance/{id}            # Corregir asistencia (recepcionista)
```

### Horarios

```
GET    /api/v1/physical/schedules                  # Todos los horarios
GET    /api/v1/physical/schedules/today            # Horarios de hoy
GET    /api/v1/physical/schedules/week             # Horarios de la semana
GET    /api/v1/physical/schedules/course/{id}      # Horarios por curso
```

### Administración

```
# Cursos (Admin)
POST   /api/v1/physical/admin/courses              # Crear curso
PUT    /api/v1/physical/admin/courses/{id}         # Actualizar curso
DELETE /api/v1/physical/admin/courses/{id}         # Eliminar curso

# Horarios (Admin)
POST   /api/v1/physical/admin/schedules            # Crear horario
PUT    /api/v1/physical/admin/schedules/{id}       # Actualizar horario
DELETE /api/v1/physical/admin/schedules/{id}       # Eliminar horario

# Asistencia (Admin)
GET    /api/v1/physical/admin/attendance/report    # Reporte de asistencia
GET    /api/v1/physical/admin/attendance/export    # Exportar asistencia (CSV)
```

---

## Estructura de Respuestas

### Respuesta Exitosa

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-05-17T10:30:00Z",
    "requestId": "abc123"
  }
}
```

### Check-in Exitoso

```json
{
  "success": true,
  "data": {
    "checkInId": 456,
    "userId": 123,
    "userName": "Juan Pérez",
    "status": "CHECKED_IN",
    "timestamp": "2026-05-17T18:55:00Z",
    "scheduledClass": {
      "courseId": 1,
      "courseName": "Salsa Intermedio",
      "startTime": "19:00",
      "endTime": "20:00",
      "instructor": "María García"
    }
  }
}
```

### Error de Check-in

```json
{
  "success": false,
  "error": {
    "code": "CAPACITY_ASSIGNMENT_REQUIRED",
    "message": "No tienes cupo confirmado para esta sesión",
    "details": {
      "userId": 123,
      "scheduleId": 5,
      "suggestion": "Inicia una compra desde la cotización vigente"
    }
  }
}
```

### Asistencia del Alumno

```json
{
  "success": true,
  "data": {
    "attendance": [
      {
        "id": 1,
        "date": "2026-05-17",
        "courseName": "Salsa Intermedio",
        "checkInTime": "18:55:00",
        "checkOutTime": "20:05:00",
        "status": "PRESENT",
        "duration": "1h 10m"
      },
      {
        "id": 2,
        "date": "2026-05-15",
        "courseName": "Salsa Intermedio",
        "checkInTime": null,
        "checkOutTime": null,
        "status": "ABSENT",
        "duration": null
      }
    ],
    "stats": {
      "totalClasses": 20,
      "attended": 18,
      "absent": 2,
      "attendanceRate": 90.0
    }
  }
}
```

---

## DTOs de Request/Response

### Check-in Request (Lector QR)

```java
public record QRCheckInRequest(
    @NotBlank String qrCode,        // JWT codificado en QR
    @NotBlank String deviceId,      // ID del lector QR
    @NotBlank String deviceToken    // Token de autenticación del dispositivo
) {}
```

### Check-in Request (Manual)

```java
public record ManualCheckInRequest(
    @NotNull Long userId,
    @NotBlank String reason,        // Razón del check-in manual
    Long scheduleId                 // Opcional: clase específica
) {}
```

### Attendance Correction Request

```java
public record AttendanceCorrectionRequest(
    @NotNull AttendanceStatus newStatus,  // PRESENT, ABSENT, LATE, EXCUSED
    @NotBlank String reason,              // Razón de la corrección
    String notes                          // Notas adicionales
) {}

public enum AttendanceStatus {
    PRESENT,    // Asistió
    ABSENT,     // No asistió
    LATE,       // Llegó tarde
    EXCUSED     // Falta justificada
}
```

### QR Code Response

```java
public record QRCodeResponse(
    String qrCode,          // Contenido del QR (JWT)
    String qrImageBase64,   // Imagen del QR en base64
    int expiresIn,          // Segundos hasta expiración (300 = 5 min)
    LocalDateTime expiresAt // Timestamp de expiración
) {}
```

---

## Sistema de Check-in con QR

### Flujo de Generación de QR

```
┌─────────────────────────────────────────────────────────────────┐
│                    GENERACIÓN DE QR                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Alumno abre App Android                                     │
│           │                                                      │
│           ▼                                                      │
│  2. App solicita QR: GET /sessions/{sessionId}/check-in-qr     │
│           │                                                      │
│           ▼                                                      │
│  3. API genera JWT temporal:                                    │
│     {                                                            │
│       "jti": "qr-unique-id",                                    │
│       "userId": 123,                                            │
│       "sessionId": 456,                                         │
│       "type": "CHECKIN_QR",                                     │
│       "iat": 1716000000,                                        │
│       "exp": 1716000300  (5 minutos)                            │
│     }                                                            │
│           │                                                      │
│           ▼                                                      │
│  4. API retorna QR code (imagen + JWT)                          │
│           │                                                      │
│           ▼                                                      │
│  5. App muestra QR en pantalla                                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Flujo de Escaneo QR

```
┌─────────────────────────────────────────────────────────────────┐
│                    ESCANEO Y CHECK-IN                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. Alumno muestra QR al lector                                 │
│           │                                                      │
│           ▼                                                      │
│  2. Lector QR escanea y envía: POST /checkin/scan               │
│     { qrCode: "eyJ...", deviceId: "puerta-1", deviceToken }     │
│           │                                                      │
│           ▼                                                      │
│  3. API valida:                                                 │
│     ✓ Device token válido                                       │
│     ✓ Firma, expiración, ventana y claims usuario/sesión        │
│     ✓ Usuario tiene CapacityAssignment confirmada               │
│     ✓ Sesión programada y habilitada en este horario            │
│           │                                                      │
│           ▼                                                      │
│  4. Si ya existe attendance para el mismo qrJti, la devuelve    │
│     idempotentemente                                             │
│           │                                                      │
│           ▼                                                      │
│  5. SET checkin:{qrJti}:{userId}:{sessionId} nonce NX EX 300    │
│           │                                                      │
│           ▼                                                      │
│  6. INSERT attendance, unique (user_id, session_id)             │
│     Si falla: compare-and-delete del lock propio                 │
│           │                                                      │
│           ▼                                                      │
│  7. API responde 201 y envía push notification                  │
│           │                                                      │
│           ▼                                                      │
│  8. Lector muestra ✓ verde + nombre del alumno                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Validaciones de Check-in

| Validación | Error Code | Mensaje |
|------------|------------|---------|
| QR expirado | `QR_EXPIRED` | "El código QR ha expirado. Genera uno nuevo." |
| QR en procesamiento concurrente | `QR_ALREADY_USED` | "Este código QR ya está siendo procesado." |
| Sin cupo confirmado | `CAPACITY_ASSIGNMENT_REQUIRED` | "No tienes cupo asignado para esta sesión." |
| Sin clase programada | `NO_SCHEDULED_CLASS` | "No hay clase programada en este horario." |
| Ya hizo check-in | `ALREADY_CHECKED_IN` | "Ya registraste tu entrada hoy." |
| Device no autorizado | `UNAUTHORIZED_DEVICE` | "Dispositivo no autorizado." |

La adquisición Redis ocurre únicamente después de todas las validaciones. El
valor del lock es un nonce; ante fallo del `INSERT` o de la transacción, Physical
lo elimina solo si aún posee ese nonce. Un reintento del mismo `qrJti` con
`attendance` persistida devuelve el resultado existente (`200 OK`); el primer
registro devuelve `201 Created`. Un QR diferente para un usuario/sesión ya
registrado continúa respondiendo `ALREADY_CHECKED_IN`.

---

## Autenticación de Dispositivos (Lector QR)

### Registro de Dispositivo

```json
// POST /api/v1/physical/admin/devices (Admin only)
{
  "deviceId": "puerta-principal",
  "deviceName": "Lector Puerta Principal",
  "location": "Entrada",
  "type": "QR_READER"
}

// Response
{
  "deviceId": "puerta-principal",
  "deviceToken": "dvc_abc123xyz...",  // Token de larga duración
  "createdAt": "2026-05-17T10:00:00Z"
}
```

### Validación en cada Request

```java
@Service
public class DeviceAuthService {

    public boolean validateDevice(String deviceId, String deviceToken) {
        // 1. Buscar dispositivo en BD
        Device device = deviceRepository.findByDeviceId(deviceId)
            .orElseThrow(() -> new UnauthorizedDeviceException());

        // 2. Validar token
        if (!device.getToken().equals(deviceToken)) {
            throw new UnauthorizedDeviceException();
        }

        // 3. Verificar que esté activo
        if (!device.isActive()) {
            throw new DeviceDisabledException();
        }

        return true;
    }
}
```

---

## Integración con Google Calendar

### Creación de eventos después de asignar capacidad

```java
@Service
public class GoogleCalendarService {

    public void createClassReminders(CapacityAssignment assignment) {
        ClassSession schedule = assignment.session();

        // Crear evento recurrente
        Event event = new Event()
            .setSummary("Clase de " + schedule.getCourseName())
            .setLocation("Academia Menta - " + schedule.getRoom())
            .setDescription("Instructor: " + schedule.getInstructor())
            .setStart(new EventDateTime()
                .setDateTime(schedule.getStartDateTime())
                .setTimeZone("America/Argentina/Buenos_Aires"))
            .setEnd(new EventDateTime()
                .setDateTime(schedule.getEndDateTime())
                .setTimeZone("America/Argentina/Buenos_Aires"))
            .setReminders(new Event.Reminders()
                .setUseDefault(false)
                .setOverrides(List.of(
                    new EventReminder().setMethod("popup").setMinutes(60),
                    new EventReminder().setMethod("popup").setMinutes(15)
                )));

        calendarService.events()
            .insert(assignment.userEmail(), event)
            .execute();
    }
}
```

---

## Push Notifications (Firebase FCM)

### Tipos de Notificaciones

| Evento | Título | Mensaje |
|--------|--------|---------|
| Check-in exitoso | "Check-in registrado" | "Bienvenido a Clase de Salsa - 19:00" |
| Recordatorio 1h antes | "Clase en 1 hora" | "Tu clase de Salsa comienza a las 19:00" |
| Pago pendiente | "Pago pendiente" | "Tu compra presencial aún no asignó cupo" |
| Clase cancelada | "Clase cancelada" | "La clase de Salsa del 17/05 fue cancelada" |

### Implementación

```java
@Service
public class PushNotificationService {

    private final FirebaseMessaging firebaseMessaging;

    public void sendCheckInConfirmation(User user, Schedule schedule) {
        Message message = Message.builder()
            .setToken(user.getFcmToken())
            .setNotification(Notification.builder()
                .setTitle("Check-in registrado")
                .setBody("Bienvenido a " + schedule.getCourseName() + " - " + schedule.getStartTime())
                .build())
            .putData("type", "CHECKIN_CONFIRMATION")
            .putData("scheduleId", schedule.getId().toString())
            .build();

        firebaseMessaging.send(message);
    }

    public void sendClassReminder(User user, Schedule schedule, int minutesBefore) {
        Message message = Message.builder()
            .setToken(user.getFcmToken())
            .setNotification(Notification.builder()
                .setTitle("Clase en " + minutesBefore + " minutos")
                .setBody("Tu clase de " + schedule.getCourseName() + " comienza pronto")
                .build())
            .putData("type", "CLASS_REMINDER")
            .putData("scheduleId", schedule.getId().toString())
            .build();

        firebaseMessaging.send(message);
    }
}
```

---

## Roles y Permisos

| Endpoint | Alumno | Profesor | Recepcionista | Admin |
|----------|--------|----------|---------------|-------|
| `GET /courses` | ✓ | ✓ | ✓ | ✓ |
| `GET /capacity-assignments/me` | ✓ | ✓ | - | ✓ |
| `POST /checkin/scan` | - | - | - | Device |
| `POST /checkin/manual` | - | - | ✓ | ✓ |
| `PATCH /attendance/{id}` | - | - | ✓ | ✓ |
| `GET /attendance/course/{id}` | - | ✓ | ✓ | ✓ |
| `POST /admin/courses` | - | - | - | ✓ |

---

## Códigos de Error

| Código | HTTP Status | Descripción |
|--------|-------------|-------------|
| `COURSE_NOT_FOUND` | 404 | Curso no encontrado |
| `CAPACITY_ASSIGNMENT_REQUIRED` | 403 | Usuario sin cupo confirmado para la sesión |
| `NO_SCHEDULED_CLASS` | 400 | No hay clase programada |
| `ALREADY_CHECKED_IN` | 409 | Ya hizo check-in |
| `QR_EXPIRED` | 401 | Código QR expirado |
| `QR_ALREADY_USED` | 409 | Otro intento con el mismo QR continúa en proceso |
| `UNAUTHORIZED_DEVICE` | 401 | Dispositivo no autorizado |
| `SCHEDULE_CONFLICT` | 409 | Conflicto de horarios |
| `COURSE_CAPACITY_UNAVAILABLE` | 409 | No se garantiza cupo para todas las sesiones |

## Integración de pricing y capacidad

Physical publica cursos y sesiones; Billing expone
`POST /api/v1/billing/physical-course-quotes` y
`POST /api/v1/billing/physical-purchases`. Cada quote representa exclusivamente
`MONTHLY` o `INDIVIDUAL` y fija `coverageStartDate`, fin exclusivo y session IDs.

`api:app` orquesta Payment y capacidad mediante contratos neutrales en `shared`.
Ningún core depende de otro core. Physical publica operaciones idempotentes para
crear/expirar holds técnicos y convertirlos en `CapacityAssignment`. Billing
publica pricing, Purchase y Payment sin exponer repositorios a Physical.

No existen reservas manuales ni listas de espera. Los precios se configuran en
`PUT /api/v1/billing/physical-course-pricing/{courseId}` y Billing registra cada
cambio en un historial append-only.

---

[Siguiente: Estrategia de Migración →](./06-MIGRATION-STRATEGY.md)
