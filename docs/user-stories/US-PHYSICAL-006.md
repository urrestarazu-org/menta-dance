# US-PHYSICAL-006: Gestión de sesiones de clase

**ID:** US-PHYSICAL-006
**Módulo:** Physical
**Prioridad:** Must Have
**Estado:** Draft

## Historia de usuario

> Como administrador o profesor, quiero crear y gestionar sesiones concretas de
> un curso para definir fechas, horarios y cupos disponibles.

## Criterios de aceptación

**Escenario 1: crear sesión**

- **Dado** un curso existente y un usuario autorizado.
- **Cuando** envía `POST /api/v1/admin/physical/courses/{courseId}/sessions` con
  fecha, hora de inicio y capacidad opcional (hereda del curso si no se especifica).
- **Entonces** Physical crea la sesión con `sessionId` único y responde `201 Created`.

**Escenario 2: crear sesiones en lote**

- **Dado** un curso con recurrencia semanal.
- **Cuando** envía `POST /api/v1/admin/physical/courses/{courseId}/sessions/batch`
  con `fromDate`, `toDate`.
- **Entonces** Physical genera automáticamente todas las sesiones según el patrón
  de recurrencia del curso, excluyendo feriados si están configurados.

**Escenario 3: listar sesiones**

- **Dado** un curso existente.
- **Cuando** solicita `GET /api/v1/admin/physical/courses/{courseId}/sessions`
  con parámetros opcionales `from` y `to`.
- **Entonces** recibe las sesiones del rango con su estado, capacidad total,
  cupos asignados, holds activos y disponibilidad.

**Escenario 4: modificar sesión**

- **Dado** una sesión sin cupos asignados.
- **Cuando** envía `PATCH /api/v1/admin/physical/sessions/{sessionId}` con
  nueva fecha, hora o capacidad.
- **Entonces** Physical actualiza la sesión y responde `200 OK`.

**Escenario 5: modificar sesión con cupos**

- **Dado** una sesión con cupos ya asignados.
- **Cuando** intenta reducir la capacidad por debajo de los cupos asignados.
- **Entonces** responde `409 Conflict` con `CAPACITY_BELOW_ASSIGNED`.

**Escenario 6: cancelar sesión**

- **Dado** una sesión futura.
- **Cuando** envía `PATCH` con `status: CANCELLED`.
- **Entonces** la sesión se marca como cancelada; los alumnos con cupo asignado
  no pueden hacer check-in. Las políticas de compensación no se definen aquí.

**Escenario 7: sesión pasada**

- **Dado** una sesión cuya fecha ya pasó.
- **Cuando** intenta modificarla.
- **Entonces** responde `409 Conflict` con `SESSION_ALREADY_OCCURRED`.

## Contrato

```json
{
  "date": "2026-09-15",
  "startTime": "19:00",
  "capacity": 18,
  "notes": "Clase especial de repaso"
}
```

## Notas Técnicas

- Una sesión hereda la capacidad del curso si no se especifica.
- La disponibilidad = `capacity - assignedSpots - activeHolds`.
- Los holds son técnicos y temporales; el alumno no los ve ni controla.

## Definition of Done

- [ ] Pruebas para creación individual y en lote.
- [ ] Pruebas de validación de fechas y capacidad.
- [ ] Pruebas de modificación con y sin cupos asignados.
- [ ] Contrato OpenAPI actualizado.
