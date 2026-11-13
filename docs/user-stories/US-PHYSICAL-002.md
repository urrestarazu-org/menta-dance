# US-PHYSICAL-002: Ver historial de asistencia

**ID:** US-PHYSICAL-002
**Módulo:** Physical
**Prioridad:** Should Have
**Estado:** Draft

## Historia de usuario

> Como alumno, quiero consultar mi asistencia por período para conocer las
> sesiones programadas que tenía asignadas y aquellas a las que asistí.

## Criterios de aceptación

- `GET /api/v1/physical/attendance/me?from=2026-09-01T03:00:00Z&to=2026-10-01T03:00:00Z`
  devuelve las sesiones asignadas que empiezan en el rango semiabierto `[from, to)`
  (aquí, septiembre en Buenos Aires), con fecha, curso, profesor y estado de
  asistencia. El rango es UTC y lo calcula el cliente (ADR-0048, #729): `from` y
  `to` son obligatorios, `from` es anterior a `to` y el rango abarca a lo sumo
  92 días; si no, `400 INVALID_REQUEST`.
- `includeAbsent=true` incluye sesiones asignadas sin check-in como `ABSENT`.
- El denominador del porcentaje es la cantidad de sesiones efectivamente
  asignadas al alumno en el período, proveniente del snapshot mensual o de pagos
  individuales; no se asume una cantidad fija por mes.
- Una sesión cancelada antes de cotizar no pertenece al snapshot. Las políticas
  por cancelaciones posteriores no se infieren en esta historia.
- El alumno solo accede a su historial; profesores y administradores requieren
  permisos explícitos.

## Respuesta de ejemplo

```json
{
  "from": "2026-09-01T03:00:00Z",
  "to": "2026-10-01T03:00:00Z",
  "scheduledSessionCount": 5,
  "attended": 4,
  "absent": 1,
  "attendanceRate": 80.00
}
```

## Definition of Done

- [ ] Pruebas para meses con cuatro y cinco sesiones.
- [ ] Pruebas de autorización, paginación y cálculo de porcentaje.
- [ ] Contrato OpenAPI actualizado.
