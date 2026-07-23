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
- `GET /api/v1/catalog/courses/{courseId}?from=&to=` devuelve las
  sesiones efectivamente programadas y su disponibilidad.
- Cada sesión informa `capacity`, `assignedSpots`, `activeCapacityHolds` y
  `availableSpots`. Los holds son datos internos y no acciones del alumno.
- No se ofrecen endpoints de reserva ni lista de espera.
- Physical no calcula ni almacena precios. La respuesta enlaza a
  `POST /api/v1/billing/physical/quotes`.

## Respuesta de ejemplo

```json
{
  "courseId": "b6bb98d6-179e-49d0-9dda-6c03a16998f0",
  "name": "Salsa inicial",
  "professor": {"id": 7, "name": "María García"},
  "recurrence": {"dayOfWeek": "TUESDAY", "startTime": "19:00"},
  "capacity": 20,
  "quoteEndpoint": "/api/v1/billing/physical/quotes"
}
```

## Definition of Done

- [ ] Pruebas para recurrencia y consultas por rango.
- [ ] Pruebas concurrentes del cálculo de disponibilidad.
- [ ] Contrato OpenAPI sin campos de precio propiedad de Physical.
