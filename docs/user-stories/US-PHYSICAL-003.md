# US-PHYSICAL-003: Ver clases y sesiones disponibles

**ID:** US-PHYSICAL-003
**Módulo:** Physical
**Prioridad:** Must Have
**Estado:** Draft

## Historia de usuario

> Como alumno o visitante, quiero ver cursos recurrentes y sus sesiones concretas
> para elegir una alternativa con disponibilidad.

## Criterios de aceptación

- `GET /api/v1/catalog/courses` lista cursos recurrentes con profesor, día,
  horario, nivel y capacidad.
- `GET /api/v1/catalog/courses/{courseId}` con un id presencial activo devuelve
  los datos del curso y `physical.sessions`, las próximas sesiones programadas
  con su disponibilidad. Un id virtual conserva su detalle y nunca lleva
  `sessions`.
- Cada sesión informa exactamente `sessionId`, `scheduledAt` (instante ISO-8601
  en UTC), `capacity` y `availableSpots`. Los cupos asignados y los holds
  activos son datos internos: no se publican.
- La ventana es `[ahora, ahora + W)`, con `W` tomado de
  `catalog.physical.sessions.window-days` (por defecto 30, rango 1 a 90). El
  endpoint no acepta `from`/`to`: si se envían, se ignoran.
- Las sesiones salen en orden ascendente por `scheduledAt` y se limitan a 100
  (las 100 más próximas, sin señal de truncado). Una sesión agotada se lista con
  `availableSpots` `0`; una cancelada no se lista. Sin sesiones, `sessions` es
  un arreglo vacío.
- Un curso presencial inactivo responde el mismo `404 COURSE_NOT_FOUND` que un
  id desconocido. Si falla la consulta de sesiones, responde `503
  CATALOG_DEGRADED` con `Retry-After: 30`, sin detalle parcial.
- No se ofrecen endpoints de reserva ni lista de espera.
- Physical no calcula ni almacena precios. La respuesta no incluye precios ni
  `quoteEndpoint`; las cotizaciones se piden a
  `POST /api/v1/billing/physical/quotes`.

## Respuesta de ejemplo

El listado (`GET /api/v1/catalog/courses`) no cambia: sus ítems presenciales
no llevan `sessions`.

Detalle (`GET /api/v1/catalog/courses/{courseId}`):

```json
{
  "courseId": "b6bb98d6-179e-49d0-9dda-6c03a16998f0",
  "modality": "PHYSICAL",
  "title": "Salsa inicial",
  "level": "BEGINNER",
  "physical": {
    "professorName": "María García",
    "dayOfWeek": "TUESDAY",
    "startTime": "19:00",
    "capacity": 20,
    "sessions": [
      {
        "sessionId": "0b5d2f7e-6f5c-4e0e-9a43-1d8e6c7a9b10",
        "scheduledAt": "2026-10-06T22:00:00Z",
        "capacity": 20,
        "availableSpots": 0
      }
    ]
  }
}
```

## Definition of Done

- [ ] Pruebas para recurrencia y consultas por rango.
- [ ] Pruebas concurrentes del cálculo de disponibilidad.
- [ ] Contrato OpenAPI sin campos de precio propiedad de Physical.
