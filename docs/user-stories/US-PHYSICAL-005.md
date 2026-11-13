# US-PHYSICAL-005: Gestión de cursos presenciales

**ID:** US-PHYSICAL-005
**Módulo:** Physical
**Prioridad:** Must Have
**Estado:** Draft

## Historia de usuario

> Como administrador o profesor, quiero crear y gestionar cursos presenciales
> recurrentes para que los alumnos puedan inscribirse y tomar clases.

## Criterios de aceptación

**Escenario 1: crear curso**

- **Dado** un usuario con rol `ADMIN` o `PROFESOR`.
- **Cuando** envía `POST /api/v1/admin/physical/courses` con nombre, descripción,
  día de recurrencia, horario, nivel, capacidad máxima y profesor asignado. El
  día y el horario se envían **en UTC**: el cliente de gestión convierte ambos
  desde la zona de la academia (ADR-0048, #729).
- **Entonces** Physical crea el curso con un `courseId` UUID globalmente único
  y responde `201 Created`.

**Escenario 2: listar cursos**

- **Dado** un administrador o profesor.
- **Cuando** solicita `GET /api/v1/admin/physical/courses`.
- **Entonces** recibe los cursos según su rol: `ADMIN` ve todos, `PROFESOR` solo
  los cursos donde es propietario.

**Escenario 3: actualizar curso**

- **Dado** un curso existente y un usuario autorizado.
- **Cuando** envía `PATCH /api/v1/admin/physical/courses/{courseId}` con campos
  a modificar (nombre, horario, capacidad, estado).
- **Entonces** Physical actualiza los campos especificados y responde `200 OK`.
- **Y** los cambios de capacidad no afectan sesiones ya programadas.

**Escenario 4: desactivar curso**

- **Dado** un curso activo sin sesiones futuras con cupos asignados.
- **Cuando** se envía `PATCH` con `status: INACTIVE`.
- **Entonces** el curso deja de aparecer en el catálogo público pero conserva
  su historial de sesiones y asistencia.

**Escenario 5: profesor sin permisos**

- **Dado** un profesor que no es propietario del curso.
- **Cuando** intenta modificarlo.
- **Entonces** responde `403 Forbidden` con `COURSE_NOT_OWNED`.

## Contrato

```json
{
  "name": "Salsa Intermedio",
  "description": "Curso de salsa para nivel intermedio",
  "dayOfWeek": "WEDNESDAY",
  "startTime": "01:00",
  "durationMinutes": 60,
  "level": "INTERMEDIATE",
  "capacity": 20,
  "professorId": "uuid-profesor"
}
```

## Notas Técnicas

- El `courseId` es UUID v4, globalmente único para permitir el catálogo unificado.
- Physical no almacena precios; esos datos pertenecen a Billing.
- La recurrencia define el patrón; las sesiones concretas se crean por separado.
- `dayOfWeek` y `startTime` son UTC. El ejemplo del contrato es "martes 22:00 en
  Buenos Aires" (UTC-3), que se envía como `WEDNESDAY` y `01:00`. Es exacto sólo
  para zonas sin horario de verano; ver
  [ADR-0048](../adr/0048-fechas-del-backend-en-utc.md).

## Definition of Done

- [ ] Pruebas unitarias para validaciones de horario y capacidad.
- [ ] Pruebas de autorización por rol y ownership.
- [ ] Prueba de desactivación con y sin sesiones futuras.
- [ ] Contrato OpenAPI actualizado.
