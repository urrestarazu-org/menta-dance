# US-VIRTUAL-008: Continuar curso

## Historia
Como alumno, quiero retomar una lección para continuar mi curso desde la última
posición registrada.

## Aceptación

- `GET /api/v1/virtual/courses/{courseId}/progress` devuelve el agregado del
  curso: porcentaje derivado, contadores y última lección.
- `GET`/`PUT /api/v1/virtual/lessons/{lessonId}/progress` conserva posición por
  lección para retomar reproducción.
- La lección sólo se marca completada mediante
  `POST /api/v1/virtual/lessons/{lessonId}/complete`; el avance de video no la
  completa automáticamente.
- El BFF muestra “Continuar” desde el agregado/última lección y el alumno sólo
  consulta su propio progreso.

## Hecho cuando

Pruebas cubren derivación del agregado, actualización idempotente, finalización
explícita y autorización.
