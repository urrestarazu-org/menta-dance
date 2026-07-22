# API de Academia Virtual

## Contratos canónicos

```text
GET  /api/v1/admin/virtual/courses
GET  /api/v1/admin/virtual/courses/{courseId}
POST /api/v1/admin/virtual/courses
PUT  /api/v1/admin/virtual/courses/{courseId}
DELETE /api/v1/admin/virtual/courses/{courseId}
PUT  /api/v1/admin/virtual/courses/{courseId}/publish
PUT  /api/v1/admin/virtual/courses/{courseId}/unpublish
POST /api/v1/admin/virtual/courses/{courseId}/modules
PUT  /api/v1/admin/virtual/courses/{courseId}/modules/reorder
POST /api/v1/admin/virtual/modules/{moduleId}/lessons
PUT  /api/v1/admin/virtual/modules/{moduleId}
PUT  /api/v1/admin/virtual/lessons/{lessonId}
GET  /api/v1/virtual/lessons/{lessonId}
GET  /api/v1/virtual/lessons/{lessonId}/stream
GET  /api/v1/virtual/materials/{materialId}
GET  /api/v1/virtual/courses/{courseId}/progress
GET  /api/v1/virtual/lessons/{lessonId}/progress
PUT  /api/v1/virtual/lessons/{lessonId}/progress
POST /api/v1/virtual/lessons/{lessonId}/complete
```

Las lecturas de cursos para alumnos y visitantes usan exclusivamente
`/api/v1/catalog/courses`; las rutas `/admin/virtual/courses` son de gestión de
contenido para `ADMIN` o el `PROFESOR` propietario.

`/stream` devuelve una URL firmada de Bunny con TTL limitado; no expone el
origen del video. `GET /api/v1/virtual/lessons/{lessonId}` devuelve metadatos de una lección
protegida antes de solicitar el stream. Todos los errores usan
`application/problem+json`.

## Materiales descargables

La metadata de una lección autorizada puede incluir materiales con una URL de
API, por ejemplo `/api/v1/virtual/materials/{materialId}`; nunca incluye una
URL directa del almacenamiento. `GET /api/v1/virtual/materials/{materialId}` valida el
derecho de acceso a la lección asociada mediante el mismo puerto de Billing que
usa el stream. Un visitante puede descargar materiales de una lección gratuita;
un material de lección premium requiere una suscripción activa. Si el acceso es
válido, responde `200 OK` con `{ "url", "expiresAt" }`, donde `url` es una URL
firmada y temporal para descargar el archivo. Si no lo es, responde `403`; si el
material no existe, `404`. Las URLs firmadas no se cachean ni se reutilizan como
identificadores de acceso.

## Progreso

El modelo principal es un resumen por **alumno + curso**. Se deriva del detalle
por **alumno + lección** y nunca se actualiza como contador independiente.

- `GET /api/v1/virtual/courses/{courseId}/progress` devuelve porcentaje, lecciones
  completadas, total y última lección.
- `GET`/`PUT /api/v1/virtual/lessons/{lessonId}/progress` guarda posición para
  retomar video.
- Una lección sólo pasa a completada cuando el alumno ejecuta
  `POST /api/v1/virtual/lessons/{lessonId}/complete`. Repetirlo es idempotente; el porcentaje
  visto no completa automáticamente una lección.

Virtual consulta el derecho de acceso mediante un puerto de Billing y reacciona
a eventos internos de suscripción. No hay RabbitMQ, HTTP interno ni caché
autoritativa de suscripciones.

## Administración

`ADMIN` gestiona cualquier curso. `PROFESOR` puede crear un curso del que queda
como propietario y gestionar exclusivamente sus propios cursos; la verificación
de ownership se aplica también al resolver `moduleId` y `lessonId`. Las consultas del alumno se
limitan a sus cursos y progreso propios. Crear, editar, publicar, despublicar,
reordenar y borrar son contratos administrativos canónicos; publicar invalida la
proyección de catálogo.
