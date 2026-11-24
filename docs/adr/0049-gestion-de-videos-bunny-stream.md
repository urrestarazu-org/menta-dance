# ADR-0049: La API gestiona los videos de las lecciones a través de la API de Bunny Stream

**Estado:** Aceptado
**Fecha:** 2026-11-23
**Decisores:** Dueño del proyecto / Arquitectura

## Contexto y Problema

`VirtualLesson.videoId` es una referencia opaca a un video de la biblioteca de Bunny Stream. Lo que existe hoy
es solo la mitad de la integración (ADR-0040): la API firma las URLs del CDN con la clave de firma de tokens
(`app.cdn.bunny-net.security-key`) y las sirve a quien tiene acceso. No hay ningún cliente de la API de gestión
de Bunny, ni una clave para usarla, ni un flujo documentado para subir videos: se suben a mano desde el panel de
Bunny y el administrador copia el identificador en el formulario de la lección (#367). Observado el 2026-11-22
(#832): un admin o un instructor no puede listar, renombrar ni borrar los videos, ni elegirlos de una lista.

Hechos relevantes del repo (verificados al diseñar):

- `videoId` es un texto libre (`VARCHAR(128) NULL`), sin unicidad ni índice, y no tiene relación con el estado
  real del video en Bunny. Varias lecciones pueden compartir un video. Borrar una lección o un curso nunca toca Bunny.
- Una lección es "completa" (y un curso publicable) solo si tiene `videoId` no vacío.
- El dueño de un curso es `VirtualCourse.professorId`; `LessonOwnershipGuard` y `CourseOwnershipGuard` ya
  resuelven la propiedad. Una biblioteca de Bunny no tiene noción de dueño por video.
- La regla "no se puede quitar un video asignado" solo la cumple el BFF. La API trata `null` como "sin cambios",
  pero un `videoId: ""` es un valor presente y vacía el video (deducido del código, no probado contra una API
  levantada; ver #845).
- Los tests evitan la red con dobles por perfil (`LocalBunnyNetSignatureService`); no hay doble de la gestión.

Lo que dice la documentación oficial de Bunny Stream (leída el 2026-11-23):

- Base `https://video.bunnycdn.com`, autenticación con el header `AccessKey` y una clave **por biblioteca** (no
  sirve una clave de cuenta). No hay alcances documentados para esa clave; la biblioteca tiene además una clave de
  solo lectura cuyo alcance no está documentado.
- Listar (`GET /library/{id}/videos`: `page`, `itemsPerPage` 10–1000, `search` por título, `collection`,
  `orderBy`), ver, actualizar (**`POST`**, no `PUT`: `title`, `collectionId`, `chapters`, `moments`, `metaTags`) y
  borrar. El video trae `guid`, `title`, `status`, `length`, `thumbnailUrl`, `hasMP4Fallback`,
  `availableResolutions`, `collectionId` y `metaTags`. El `videoId` que guardamos es el `guid`.
- Borrar un video lo oculta de las consultas enseguida, pero la reproducción puede seguir un tiempo. No está
  documentado si borrar dos veces es idempotente ni si hay un límite de pedidos.
- La subida es crear el video y luego subir el archivo (`PUT` directo, o TUS con una firma que se calcula en el
  servidor, `SHA256(libraryId + apiKey + expiración + videoId)`, para subir desde el navegador sin exponer la clave).
- Las colecciones tienen CRUD y el listado se puede filtrar por colección, pero **borrar una colección borra todos
  sus videos**. No está documentado si el nombre es único ni hay un límite de colecciones.
- `metaTags` se exponen públicamente en el reproductor embebido y no son buscables.
- El CDN sirve `play_720p.mp4`, que solo existe en videos subidos **después** de activar "MP4 Fallback" (ver
  `docs/BUNNY-VIDEO-403-INVESTIGATION.md`).
- Hay webhooks firmados con la clave de solo lectura (incluido el aviso de video borrado).

## Factores Clave (Decision Drivers)

* La clave de la biblioteca es poderosa (crea y borra) y nunca debe llegar al BFF ni al navegador.
* Un instructor solo debe ver lo suyo; Bunny no tiene dueño por video.
* No dejar lecciones apuntando a videos que ya no existen.
* Mantener Clean Architecture: puerto en `application`, adaptador en `infrastructure`, tests sin red.
* Entregar valor en partes chicas (API y BFF en PRs separados) sin bloquear la pantalla de estructura.

## Opciones Consideradas

### Opción 1: la API gestiona los videos con la API de Bunny Stream (elegida)

* **Descripción:** `api/virtual` agrega un puerto de biblioteca de videos y un adaptador con `RestClient` y
  timeouts; la API expone endpoints de gestión y el BFF solo consume la API.
* **Pros:** la credencial queda en el servidor; reutiliza la propiedad y los roles existentes; se prueba con un
  doble en memoria; el BFF no conoce a Bunny.
* **Contras:** un secreto nuevo que operar; dependencia de un servicio externo en las pantallas de gestión.

### Opción 2: gestionar solo los `videoId` ya asociados a lecciones, sin hablar con Bunny

* **Descripción:** el panel lista lo que hay en `virtual_lessons.video_id` y permite editarlo.
* **Pros:** sin credencial nueva ni servicio externo.
* **Contras:** no se puede renombrar ni borrar un video (que era el pedido), no se sabe si el video existe o se
  va a reproducir, y los videos sin lección no aparecen.

### Opción 3: el BFF habla directo con Bunny

* **Pros:** una capa menos.
* **Contras:** pone la clave de la biblioteca en el BFF; rompe el reparto actual (el BFF solo consume la API).
  Descartada.

### Opción 4: status quo (copiar el identificador a mano)

* **Contras:** no resuelve nada de lo pedido.

## Decisión

Elegimos **la Opción 1**, con estas reglas:

1. **Puerto y adaptador** en `api/virtual`: listar, ver, renombrar y borrar. `RestClient` con timeouts de conexión
   y lectura, errores mapeados sin exponer el cuerpo de Bunny. Doble en memoria por perfil para tests, local, demo
   y e2e; el adaptador real se prueba contra un servidor HTTP falso.
2. **Credencial nueva** `app.cdn.bunny-net.api-key`, **distinta** de la clave de firma de tokens, que no se reutiliza
   ni se resetea. Falla al arrancar si falta en un perfil real; nunca se imprime.
3. **Visibilidad:** el ADMIN ve toda la biblioteca. El INSTRUCTOR ve solo los videos usados por lecciones de sus
   cursos; esa pertenencia se deriva de `findByProfessorId` y no se consulta a Bunny.
4. **Renombrar:** ADMIN cualquier video; INSTRUCTOR solo los suyos (los que ve).
5. **Borrar: solo ADMIN, y solo si ninguna lección usa el video.** Si alguna lo usa, `409 VIDEO_IN_USE`; nunca se
   desasocia solo. Consecuencia consciente: como un instructor solo ve videos en uso, **no puede borrar videos**.
6. **No se agrega "quitar video" a la lección** por ahora (opción A de #832). La API debe rechazar un `videoId` en
   blanco al editar en vez de vaciar el video (#845). Para cambiar un video se elige otro.
7. **Elegir en vez de tipear:** el formulario de lección usa un selector alimentado por el listado (#843).
8. **Estado de reproducción:** el panel muestra el estado de cada video y avisa si no tiene MP4 de respaldo (no se
   va a reproducir con la configuración actual del CDN), y marca las lecciones cuyo video ya no existe.
9. **La subida de videos queda fuera de este alcance** (#844). Allí se decidirá la firma TUS, CORS y CSP, y el dueño
   por colección de Bunny (una por instructor; nunca se expone el borrado de colecciones). El dueño no se guarda en
   `metaTags`.

## Justificación (Rationale)

Es la única opción que mantiene la clave de la biblioteca en el servidor, reutiliza los roles y la propiedad que
ya existen y resuelve lo pedido (listar, renombrar, borrar y elegir). Derivar "mis videos" de las lecciones evita
inventar una noción de dueño que Bunny no tiene y que solo hará falta cuando exista la subida. Rechazar el borrado
de un video en uso evita lecciones rotas, y limitar el borrado al ADMIN es lo más simple y seguro mientras no
exista una forma explícita de quitar un video de una lección; se aceptó que el instructor no pueda borrar.

## Consecuencias

### Positivas

* El BFF no conoce a Bunny ni su clave; la credencial se opera como configuración de la API.
* Los videos sin MP4 o inexistentes se ven antes de que un alumno los encuentre rotos.
* La partición en issues permite entregar API y BFF por separado.

### Negativas / Deuda Técnica

* Una dependencia externa más en las pantallas de gestión: si Bunny no responde, el panel se degrada.
* Un secreto nuevo (`APP_CDN_BUNNYNET_APIKEY`) en producción, a documentar y a rotar si se filtra.
* El instructor no puede borrar videos ni liberarlos; hasta que exista la subida, quien sube es quien administra Bunny.
* No hay un límite de pedidos documentado: el listado debe paginar y se evita pedir un video por cada fila sin
  acotarlo.

### Implicaciones de Costos

* Sin infraestructura nueva. Horas de desarrollo: ver la partición. El uso de la API de Bunny no tiene costo
  adicional documentado; no se leyó la página de precios de Stream.

### Riesgos y Reversibilidad

* **Riesgo Principal:** supuestos sobre Bunny que la documentación no confirma: el filtro `search` junto con la
  paginación, si un video recién creado aparece enseguida en el listado, qué permite la clave de solo lectura y
  la idempotencia del borrado.
* **Plan de Mitigación:** probarlos contra una biblioteca de pruebas (no la de producción) en el primer issue de
  implementación (#840) y corregir este ADR si alguno falla.
* **Reversibilidad:** alta. Es una capacidad nueva y aditiva; se puede apagar quitando la clave y volviendo al
  ingreso manual del identificador.

## Partición en issues

| Issue | Tema | Tamaño |
|---|---|---|
| #840 | API: puerto, adaptador, credencial y listado de videos | M |
| #841 | API: renombrar y borrar (rechazo `VIDEO_IN_USE`) | M |
| #842 | BFF: panel para listar, renombrar y borrar | M |
| #843 | BFF: elegir el video de una lección desde una lista | S |
| #844 | Subida de videos (TUS, colecciones por instructor); empieza con un ADR de seguimiento | L |
| #845 | Bug: `PUT` de lección con `videoId` en blanco vacía el video | S |
| #846 | Docs: corregir ADR-0024 y la tabla `virtual_videos` inexistente | XS |

## Referencias y Decisiones Relacionadas

* #832 (diseño), #840, #841, #842, #843, #844, #845, #846.
* Complementa a: ADR-0040 (firmante local y real de URLs de Bunny; deja la subida fuera de alcance).
* Relacionado: ADR-0021 (Clean Architecture obligatoria), `docs/BUNNY-VIDEO-403-INVESTIGATION.md`.
* Documentación oficial de Bunny Stream consultada: https://bunny.net/docs/stream/ (HTTP API, autenticación, TUS,
  colecciones, webhooks, MP4 fallback) y su especificación OpenAPI
  https://video.bunnycdn.com/openapi/bunnynet-video-api.public.json.

## Nota de seguimiento de #840: supuestos sobre Bunny que siguen sin probarse

Fecha: 2026-11-23. #840 implementó el puerto `VideoLibraryPort`, el adaptador `BunnyStreamVideoLibrary`
(`RestClient`, timeouts de 2 s y 3 s, errores sin el cuerpo de Bunny) y el listado
`GET /api/v1/admin/virtual/videos`. Todo se probó contra un servidor HTTP falso y un doble en memoria; **ningún
supuesto de esta lista se probó contra Bunny**. Hay que probarlos contra una **biblioteca de pruebas** (no la de
producción: crear y borrar videos de prueba allí es seguro) antes de dar #840 por validado, y corregir este ADR si
alguno falla.

Lo que sí dice la especificación OpenAPI pública de Bunny (leída el 2026-11-23, `bunnynet-video-api.public.json`) y
usa el adaptador: `page` desde 1, `itemsPerPage` recortado a 10-1000, `search` es un filtro por título "sin
distinguir mayúsculas, subcadena", `orderBy` acepta `date`, `title`, `size` y `duration` (cualquier otro valor cae
en `date`; `size`/`duration` pueden dar `400` si la biblioteca no los tiene, por eso la API sólo ofrece `date` y
`title`), `status` es un entero 0 a 8, `availableResolutions` es un texto separado por comas, la lista trae
`totalItems`, y un video inexistente responde `404`.

| # | Supuesto | Qué se hizo con él | Cómo probarlo (biblioteca de pruebas) |
|---|---|---|---|
| 1 | **`search` junto con la paginación.** Se supone que `totalItems` cuenta sólo los videos que coinciden, que `page` e `itemsPerPage` se aplican después del filtro y que el orden se conserva entre páginas. | La API pide a Bunny páginas de 1000 con `search` y `orderBy` y pagina ella misma; depende de que `totalItems` y el orden entre páginas sean los supuestos (así corta el recorrido). | Subir 25 videos con títulos `prueba-01`..`prueba-25` y 5 con otro título. `GET .../videos?search=prueba&itemsPerPage=10&page=1..3`: la suma de las páginas debe dar 25 sin repetidos ni faltantes, `totalItems` debe ser 25, y la página 3 debe traer 5. Repetir con `orderBy=title` y con un `search` que no coincide con nada (`totalItems` 0, `items` vacío, no `404`). |
| 2 | **Un video recién creado aparece enseguida en el listado.** No está documentado si el índice de listado tiene demora. | Nada lo espera ni lo reintenta: si hay demora, el video nuevo tarda en verse. Importa para #844 (subida) y para elegir un video recién subido en #843. | `POST /library/{id}/videos` con un título y, **sin esperar**, listar con `search` por ese título y pedir el video por guid. Medir cuántos segundos pasan hasta que aparece en cada uno; probar también que aparece con `status` 0 o 1 (creado/subido), sin `length` ni miniatura. |
| 3 | **Qué permite la clave de sólo lectura.** El ADR no documenta su alcance. La API usa la *API key* de la biblioteca, que puede crear y borrar. | Se pidió la clave completa porque es la única que se sabe que sirve. Si la de sólo lectura alcanza para `GET /videos` y `GET /videos/{id}`, el listado podría usar la de sólo lectura y reducir el daño de una filtración. | Con la *Read-only API key* en el header `AccessKey`: listar, pedir un video, y comprobar que `POST` (crear/renombrar) y `DELETE` responden `401`/`403`. Anotar el resultado. Si el listado funciona, abrir un issue para usarla desde `app.cdn.bunny-net.api-key` en lugar de la clave completa (hoy sólo #841 y #844 necesitan escribir). |
| 4 | Una clave inválida o de otra biblioteca responde `401` (y no, por ejemplo, una lista vacía). | Cualquier estado distinto de `2xx` se traduce a `503 VIDEO_LIBRARY_UNAVAILABLE`; `404` en `GET /videos/{id}` significa "el video no existe". | Pedir con una clave equivocada y con la clave de otra biblioteca; confirmar `401` y que el log de la API diga `status 401` sin la clave. |
| 5 | El `guid` que se guarda en `virtual_lessons.video_id` coincide letra por letra con el `guid` del listado (mayúsculas y minúsculas incluidas). | Se compara con `equals`. Si Bunny devolviera otra capitalización, la lección se marcaría como `missingInLibrary` sin estarlo. | Comparar un `videoId` ya cargado en una lección con el `guid` que devuelve el listado. |
| 6 | No hay límite de pedidos documentado. | Tanto el admin como el instructor recorren la biblioteca en cada listado (hasta 10 pedidos de 1000 videos; el instructor corta al encontrar todos sus videos, el admin lee hasta agotarla) y paginan en memoria. | Observar si Bunny responde `429` con listados repetidos; si pasa, agregar caché corta del listado. |

Resultado de las pruebas: **pendiente** (queda a cargo de quien tenga la biblioteca de pruebas; anotar acá la
fecha y el resultado de cada fila).
