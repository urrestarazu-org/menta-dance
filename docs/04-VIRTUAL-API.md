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
DELETE /api/v1/admin/virtual/lessons/{lessonId}
GET  /api/v1/virtual/lessons/{lessonId}
GET  /api/v1/virtual/lessons/{lessonId}/stream
GET  /api/v1/virtual/materials/{materialId}
GET  /api/v1/virtual/courses/{courseId}/progress
GET  /api/v1/virtual/courses/me/in-progress
GET  /api/v1/virtual/lessons/{lessonId}/progress
PUT  /api/v1/virtual/lessons/{lessonId}/progress
POST /api/v1/virtual/lessons/{lessonId}/complete
```

Las lecturas de cursos para alumnos y visitantes usan exclusivamente
`/api/v1/catalog/courses`; las rutas `/admin/virtual/courses` son de gestión de
contenido para `ADMIN` o el `PROFESOR` propietario.

`GET /api/v1/admin/virtual/courses/{courseId}` devuelve el curso con sus módulos y
lecciones en cualquier estado, con lo necesario para precargar los formularios de
edición: `shortDescription` y `professorId` del curso, `preview` de cada módulo y
`description` de cada lección. Comprueba la propiedad del curso (`403
COURSE_NOT_OWNED`) y por eso `professorId` sólo lo ve quien ya puede leer el detalle.

`/stream` devuelve una URL firmada de Bunny con TTL limitado; no expone el
origen del video. `GET /api/v1/virtual/lessons/{lessonId}` devuelve metadatos de una lección
protegida antes de solicitar el stream. Todos los errores usan
`application/problem+json`.

Una lección sin video (`videoId` nulo o en blanco, que un curso publicado puede
tener porque sólo exige una lección completa) no existe para los alumnos ni para
el público: el detalle del curso no la lista, no cuenta en los totales ni en el
denominador del progreso (el porcentaje puede llegar a 100) y el detalle, el
stream y el progreso de esa lección responden `404 LESSON_NOT_FOUND`, igual que
un id inexistente. Esa comprobación va antes de la decisión de acceso, así que
una lección protegida sin video responde `404` y nunca `403`. Los endpoints de
gestión (`/api/v1/admin/virtual/**`) siguen mostrándola.

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
- `GET /api/v1/virtual/courses/me/in-progress` lista, en una sola consulta, los
  cursos que el alumno del token está cursando (#765), para la sección
  "Continuar" del panel:
  - entra un curso publicado con al menos una lección con progreso, que no está
    completo (`percentage < 100`) y al que el alumno **hoy** tiene acceso, con la
    misma regla que el progreso por curso (suscripción vigente). Un curso al que
    perdió acceso no aparece, en vez de responder `403`;
  - orden por actividad más reciente primero (el último guardado de posición
    en el curso); `limit` opcional, por defecto 10 y máximo 50. Un `limit` que no
    es un entero entre 1 y 50 responde `400` con `code: INVALID_REQUEST`;
  - cada elemento es
    `{courseId, courseTitle, completedLessons, totalLessons, percentage, resumeLesson: {lessonId, moduleId, lessonTitle, positionSeconds}}`,
    con `percentage` y `resumeLesson` iguales a los del progreso por curso; sin
    cursos en progreso responde `200 []`, y sin token `401`.
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

`DELETE /api/v1/admin/virtual/lessons/{lessonId}` responde `204` y se permite también
en un curso publicado (sólo borrar un curso entero exige `DRAFT`). En la misma
transacción borra el progreso de todos los alumnos en esa lección, renumera las
lecciones restantes del módulo de forma contigua (`order` 0..n-1) y deja una fila de
auditoría `DELETE_LESSON`.

`DELETE /api/v1/admin/virtual/courses/{courseId}` responde `204` sólo si el curso está en
`DRAFT` y ningún alumno tiene progreso en sus lecciones (#834). Un curso publicado puede
volver a borrador al despublicarlo, así que el estado no alcanza: con progreso responde
`409 COURSE_HAS_PROGRESS`, no borra nada (ni el curso, ni sus módulos y lecciones, ni el
progreso) y no escribe auditoría. Primero se comprueba que exista (`404`), que sea del
usuario (`403`) y que esté en borrador (`409 COURSE_NOT_DRAFT`); el progreso se mira
después. Las referencias de Billing al curso son por valor, sin clave foránea, así que
no bloquean este borrado.
