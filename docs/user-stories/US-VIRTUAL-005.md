# US-VIRTUAL-005: Guardar y completar progreso de lección

## Historia
Como alumno, quiero guardar mi posición de reproducción y decidir cuándo
completo una lección para retomar y medir mi avance del curso.

## Aceptación

- `PUT /api/v1/virtual/lessons/{lessonId}/progress` guarda de forma idempotente
  la posición de la lección para el alumno autenticado.
- `GET /api/v1/virtual/lessons/{lessonId}/progress` devuelve el punto de
  reanudación; `GET /api/v1/virtual/courses/{courseId}/progress` devuelve el
  agregado derivado por curso.
- Sólo `POST /api/v1/virtual/lessons/{lessonId}/complete` marca completada una
  lección. Ver 90% de video no produce completitud automática.
- Repetir completar no cambia el resultado. El alumno sólo modifica/consulta su
  propio progreso y los errores usan `application/problem+json`.

## Hecho cuando

Pruebas cubren posición, finalización explícita, derivación de porcentaje e
idempotencia.
