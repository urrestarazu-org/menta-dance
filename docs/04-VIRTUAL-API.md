# API de Academia Virtual (Cursos Online)

[← Volver al índice](./README.md) | [← Auth API](./03-AUTH-API.md)

> [!NOTE]
> **Módulo Virtual dentro del monolito.** Estos endpoints son parte de `:api:app`
> (ver [ADR-0020](./adr/0020-modular-monolith.md)). El módulo `:api:virtual`
> gestiona cursos, módulos y lecciones. Para planes, suscripciones y pagos, ver
> [06-BILLING-API.md](./06-BILLING-API.md).

---

## Diseño de Endpoints REST

> **Convención de URLs:** `/api/v1/{module}/{resource}`
> Ver [ADR-0008](./adr/0008-api-versioning-url-vs-header.md).

### Cursos (Contenido)

```
GET    /api/v1/virtual/courses                    # Listar cursos (público)
GET    /api/v1/virtual/courses/{hashId}           # Detalle de curso
GET    /api/v1/virtual/courses/{hashId}/modules   # Módulos del curso
GET    /api/v1/virtual/courses/{hashId}/access    # Verificar acceso del usuario

GET    /api/v1/virtual/modules/{hashId}           # Detalle de módulo
GET    /api/v1/virtual/modules/{hashId}/lessons   # Lecciones del módulo

GET    /api/v1/virtual/lessons/{hashId}           # Detalle de lección
GET    /api/v1/virtual/lessons/{hashId}/stream    # URL firmada para streaming (Bunny.net)
POST   /api/v1/virtual/lessons/{hashId}/progress  # Actualizar progreso de video
GET    /api/v1/virtual/lessons/{hashId}/progress  # Obtener progreso del usuario
POST   /api/v1/virtual/lessons/{hashId}/complete  # Marcar como completada
```

### Streaming y Progreso

```
# Streaming de video (URL firmada con TTL)
GET  /api/v1/virtual/lessons/{hashId}/stream
     → { streamUrl: "https://cdn.bunny.net/video/xxx?token=...", expiresIn: 3600 }

# Progreso del usuario en una lección
GET  /api/v1/virtual/lessons/{hashId}/progress
     → { watchedSeconds: 450, totalSeconds: 1200, percentComplete: 37.5 }

POST /api/v1/virtual/lessons/{hashId}/progress
     ← { watchedSeconds: 600 }
     → 204 No Content

# Completar lección (requiere >= 90% visto o watchedSeconds >= 90% de duration)
POST /api/v1/virtual/lessons/{hashId}/complete
     → { completedAt: "2026-07-21T15:30:00Z", nextLesson: { hashId: "xyz", title: "..." } }
```

### Administración

```
# Usuarios (Admin) — módulo Auth
GET    /api/v1/auth/admin/users                # Listar usuarios
GET    /api/v1/auth/admin/users/{id}           # Detalle usuario
PATCH  /api/v1/auth/admin/users/{id}           # Actualizar usuario
PATCH  /api/v1/auth/admin/users/{id}/status    # Cambiar estado

# Contenido (Admin) — módulo Virtual
POST   /api/v1/virtual/admin/courses              # Crear curso
PUT    /api/v1/virtual/admin/courses/{id}         # Actualizar curso
DELETE /api/v1/virtual/admin/courses/{id}         # Eliminar curso
# (Similar para modules, lessons, videos)

# Suscripciones y Pagos (Admin) → ver 06-BILLING-API.md
```

---

## Estructura de Respuestas

### Respuesta Exitosa

```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "timestamp": "2026-04-26T10:30:00Z",
    "requestId": "abc123"
  }
}
```

### Respuesta con Paginación

```json
{
  "success": true,
  "data": [ ... ],
  "pagination": {
    "page": 1,
    "pageSize": 20,
    "totalItems": 150,
    "totalPages": 8
  }
}
```

### Respuesta de Error

```json
{
  "success": false,
  "error": {
    "code": "SUBSCRIPTION_EXPIRED",
    "message": "Tu suscripción ha expirado",
    "details": {
      "expiredAt": "2026-04-01T00:00:00Z"
    }
  },
  "meta": {
    "timestamp": "2026-04-26T10:30:00Z",
    "requestId": "abc123"
  }
}
```

---

## DTOs de Request/Response

```java
// Ejemplo: CourseResponse
public record CourseResponse(
    String hashId,
    String title,
    String subtitle,
    String description,
    String imageUrl,
    VideoSummary video,
    List<ModuleSummary> modules,
    AccessInfo access
) {}

public record AccessInfo(
    boolean hasFullAccess,
    boolean isPreviewOnly,
    LocalDate expiresAt
) {}

// Ejemplo: LessonCompletionRequest
public record LessonCompletionRequest(
    @NotNull String lessonHashId,
    Integer watchedSeconds  // opcional, para tracking de progreso
) {}
```

---

[Siguiente: API Academia Física →](./05-PHYSICAL-API.md)
