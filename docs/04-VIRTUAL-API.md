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
GET  /api/v1/admin/virtual/videos
PUT  /api/v1/admin/virtual/videos/{videoId}
DELETE /api/v1/admin/virtual/videos/{videoId}
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
después.

Billing referencia los cursos por valor, sin clave foránea, así que la base no impide el
borrado: lo impide `virtual`, que le pregunta a Billing por el puerto
`VirtualCourseReferencePort` de `shared` (#856). Si algún plan (activo o no) o alguna
suscripción (vigente, cancelada o vencida) incluye el curso, responde `409 COURSE_IN_USE`,
no borra nada y no escribe auditoría; las referencias históricas también cuentan. Es la
última comprobación, después de `COURSE_HAS_PROGRESS`. Un pago de suscripción virtual cita
el plan y no el curso, así que lo cubre la consulta de planes y suscripciones.

## Biblioteca de videos (#840 y #841, ADR-0049)

`GET /api/v1/admin/virtual/videos?page=&size=&search=&orderBy=` lista los videos de la biblioteca de
Bunny Stream con las lecciones que los usan. Lo pueden llamar `ADMIN` e `INSTRUCTOR`. Renombrar
(`PUT .../videos/{videoId}`) también es de ambos; borrar (`DELETE .../videos/{videoId}`) es sólo de `ADMIN`
(ver más abajo). Cualquier otro método o ruta del prefijo sigue siendo de `ADMIN`.

- **Paginación:** `page` desde 0 (como `/admin/users`), `size` de 10 a 100 (20 por defecto; fuera de rango
  responde `400`, no se recorta; el mínimo 10 es el de Bunny). La respuesta trae `items`, `page`, `size`,
  `totalElements` y `totalPages`. `search` filtra por título (sin distinguir mayúsculas, hasta 100 caracteres);
  `orderBy` es `date` (más nuevo primero, por defecto) o `title` (A-Z). Cualquier otro valor responde `400`.
- **Ítem:** `videoId` (el `guid` de Bunny, el mismo que guardan las lecciones), `title`, `status`
  (`CREATED`, `UPLOADED`, `PROCESSING`, `TRANSCODING`, `FINISHED`, `ERROR`, `UPLOAD_FAILED`, `JIT_SEGMENTING`,
  `JIT_PLAYLISTS_CREATED`, `UNKNOWN`), `durationSeconds`, `thumbnailUrl`, `hasMp4Fallback` (sin MP4 de respaldo el
  video no se reproduce con la configuración actual del CDN), `availableResolutions`, `missingInLibrary` y
  `lessons` (curso, módulo y lección que lo usan, con sus ids y títulos).
- **Visibilidad:** el `ADMIN` ve todo lo que sabe la base de datos más toda la biblioteca: cada video de la
  biblioteca (con o sin lecciones, en el orden de Bunny) y, al final, cada `videoId` que usa alguna lección de
  cualquier curso y que la biblioteca ya no tiene. El `INSTRUCTOR` también ve toda la biblioteca (#861, decisión
  transitoria: la restricción por instructor se revisará más adelante), pero en `lessons` sólo aparecen las
  lecciones de sus propios cursos (salen de `findByProfessorId`, nunca de Bunny): un video que usan únicamente
  cursos de otro instructor se lista con `lessons` vacío, sin títulos ajenos, y un `videoId` ausente de la
  biblioteca sólo se marca si lo usa una lección suya. Como Bunny no tiene una consulta "estos ids", para los
  dos roles la API recorre la biblioteca entera (a lo sumo 10 pedidos de 1000 videos) y pagina el resultado en
  memoria, con `search` y `orderBy` pasados a Bunny.
  Si la biblioteca es tan grande que no alcanza, responde `503` en vez de adivinar.
- **Video que ya no existe:** si una lección apunta a un `videoId` que la biblioteca no tiene, el ítem se
  devuelve igual, al final, con `missingInLibrary: true`, sin título, estado, duración ni miniatura y con las
  lecciones que lo usan (curso, módulo y lección). No se marca cuando hay `search` (no coincidir no es no existir).
- **Errores:** `401`/`403` los resuelve `SecurityConfig`; `400 INVALID_REQUEST` por un parámetro inválido;
  `503 VIDEO_LIBRARY_UNAVAILABLE` si Bunny no responde, tarda más que los timeouts (2 s de conexión y 3 s de
  lectura, como Mercado Pago, configurables con `app.cdn.bunny-net.api-connect-timeout-ms` y
  `api-read-timeout-ms`), contesta con error o rechaza la clave. El detalle nunca incluye lo que dijo Bunny; la
  causa (por ejemplo `status 401`) queda sólo en el log de la API, sin ningún secreto.
- **Credencial:** `app.cdn.bunny-net.api-key` (`APP_CDN_BUNNYNET_APIKEY`), la clave de API de la biblioteca
  (header `AccessKey`), distinta de la clave de firma de tokens (`security-key`). Falla el arranque si falta en un
  perfil real. Nunca llega al BFF ni al navegador, ni se imprime en logs o `toString`. Con los perfiles
  `e2e-bunny-net`, `e2e-catalog-content`, `test` e `integration-test` (y con `demo` sin clave) se usa un doble en
  memoria sin red.

### Renombrar y borrar (#841)

`PUT /api/v1/admin/virtual/videos/{videoId}` con `{"title": "..."}` renombra el video en Bunny (que actualiza con
`POST /library/{libraryId}/videos/{videoId}`; la API expone `PUT`) y responde `200` con el video (la misma forma
que un ítem del listado, con el título nuevo). `DELETE /api/v1/admin/virtual/videos/{videoId}` lo borra y responde
`204` sin cuerpo. Un `videoId` que no es un identificador simple (letras, dígitos, `-` y `_`, hasta 128) responde
`400 INVALID_REQUEST` antes de llamar a Bunny: el id termina en la ruta de un pedido que modifica.

- **Renombrar:** el `ADMIN` renombra cualquier video de la biblioteca, lo use o no una lección. El `INSTRUCTOR`
  sólo un video que usa alguna lección de sus propios cursos (la pertenencia sale de los cursos en la base de
  datos, nunca de Bunny, y se compara el id exacto): en otro caso, también si ninguna lección lo usa o si no existe,
  `403 VIDEO_NOT_OWNED`. `title` no puede estar en blanco ni superar 255 caracteres (como los títulos de curso,
  módulo y lección); los espacios de los extremos se quitan; si no, `400 INVALID_REQUEST`. Un video que la
  biblioteca no tiene responde `404 VIDEO_NOT_FOUND` (el `ADMIN` siempre; el `INSTRUCTOR` sólo si una lección suya
  lo usa). En `lessons` el `INSTRUCTOR` ve únicamente sus lecciones.
- **Borrar:** sólo `ADMIN` (decisión de #832): un `INSTRUCTOR` recibe siempre `403`, aun para un video que ninguna
  lección usa. Sólo se borra un video que **ninguna lección usa, de ningún curso y en ningún estado** (borrador o
  publicado). Si alguna lo usa responde `409 VIDEO_IN_USE` con el miembro `lessons` (`courseId`, `courseTitle`,
  `moduleId`, `moduleTitle`, `lessonId`, `lessonTitle` de cada una, la misma forma que el listado) para que el BFF
  las muestre; no se llama a Bunny, no se audita nada y el video **nunca se desasocia solo**: hay que cambiar el
  video de esas lecciones primero. La comprobación sale de `virtual_lessons.video_id` (índice
  `idx_virtual_lessons_video_id`, V33), con la comparación de la base: en MySQL ignora mayúsculas, lo que sólo
  puede hacer que se rechace un borrado, nunca que se permita uno indebido.
- **Borrar es idempotente:** un `404` de Bunny al borrar (o repetir el pedido, o un video que ya no está) se trata
  como "ya no existe" y responde `204`. Bunny oculta el video de sus consultas enseguida, pero la reproducción puede
  seguir un tiempo. Que Bunny responda `404` en un segundo borrado **no está documentado ni se probó contra una
  biblioteca real** (ver la nota de #841 en ADR-0049).
- **Errores:** `401`/`403` los resuelve `SecurityConfig` (`403 ACCESS_DENIED` para otro rol); `400
  INVALID_REQUEST`, `403 VIDEO_NOT_OWNED`, `404 VIDEO_NOT_FOUND` y `409 VIDEO_IN_USE` como arriba; `503
  VIDEO_LIBRARY_UNAVAILABLE` si Bunny no responde, con los mismos timeouts que el listado. Nunca se devuelve ni se
  loguea el cuerpo de Bunny ni la clave.
- **Auditoría:** una fila por operación en `virtual_video_audit` (V34): `RENAME_VIDEO` (`title=<anterior>` y
  `title=<nuevo>`) y `DELETE_VIDEO` (`videoId=<id>, title=<título>` o `missingInLibrary=true`; sin valor nuevo),
  con el actor y el instante del puerto `Clock`. No se usa `virtual_course_audit` porque su `course_id` es
  obligatorio y un video no pertenece a un curso. La fila se escribe **después** de la llamada a Bunny, que no va
  dentro de una transacción de base de datos: si Bunny falla no queda fila de algo que no pasó; si falla sólo el
  insert de la fila, el renombre o el borrado ya ocurrió (y repetir el pedido deja la fila).
- **Carrera conocida:** entre la comprobación "ninguna lección lo usa" y el borrado en Bunny otra persona podría
  asignar ese video a una lección; no hay un candado que abarque la base y Bunny, y la ventana es de milisegundos.
