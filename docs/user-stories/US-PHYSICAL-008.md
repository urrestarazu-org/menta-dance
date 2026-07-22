# US-PHYSICAL-008: Check-in manual por recepcionista

**ID:** US-PHYSICAL-008
**Módulo:** Physical
**Prioridad:** Must Have
**Estado:** Draft

## Historia de usuario

> Como recepcionista, quiero registrar manualmente la asistencia de un alumno
> cuando el check-in por QR no es posible o práctico.

## Criterios de aceptación

**Escenario 1: check-in manual válido**

- **Dado** un alumno con cupo asignado en la sesión y un usuario con rol
  `RECEPCIONISTA`.
- **Cuando** envía `POST /api/v1/physical/sessions/{sessionId}/check-ins` con
  variante `MANUAL` y `studentId`.
- **Entonces** Physical registra la asistencia y responde `201 Created`.

**Escenario 2: sin cupo confirmado**

- **Dado** un alumno sin asignación de cupo para la sesión.
- **Cuando** el recepcionista intenta registrar su asistencia.
- **Entonces** responde `403 Forbidden` con `CAPACITY_ASSIGNMENT_REQUIRED`.

**Escenario 3: reintento idempotente**

- **Dado** una asistencia ya registrada para el mismo alumno y sesión.
- **Cuando** el recepcionista reintenta el check-in manual.
- **Entonces** devuelve la asistencia existente con `200 OK` sin duplicar.

**Escenario 4: rol incorrecto**

- **Dado** un usuario sin rol `RECEPCIONISTA` ni `ADMIN`.
- **Cuando** intenta registrar un check-in manual.
- **Entonces** responde `403 Forbidden` con `INSUFFICIENT_ROLE`.

**Escenario 5: sesión no vigente**

- **Dado** una sesión pasada o cancelada.
- **Cuando** el recepcionista intenta registrar asistencia.
- **Entonces** responde `409 Conflict` con `SESSION_NOT_ACTIVE`.

**Escenario 6: alumno no existe**

- **Dado** un `studentId` que no corresponde a ningún usuario.
- **Cuando** el recepcionista intenta el check-in.
- **Entonces** responde `404 Not Found` con `STUDENT_NOT_FOUND`.

## Contrato

```json
{
  "type": "MANUAL",
  "studentId": "student-uuid"
}
```

El request `MANUAL` no puede incluir campos de QR (`qrCredentials`, `deviceId`,
`deviceToken`). Physical rechaza solicitudes mixtas antes de cualquier
validación.

## Diferencias con check-in QR

| Aspecto | MANUAL | QR |
|---------|--------|-----|
| Actor | RECEPCIONISTA | Dispositivo técnico |
| Identificación alumno | `studentId` explícito | Claims firmados del QR |
| Lock Redis | Solo `checkin:attendance:{sessionId}:{studentId}` | Primero `checkin:qr:{qrJti}`, luego attendance |
| Validación de firma | No aplica | Obligatoria |
| Ventana de tiempo | No aplica | Obligatoria |

## Notas Técnicas

- Ambos flujos comparten la unicidad final `(session_id, student_id)` en MySQL.
- El flujo MANUAL no evalúa firma, dispositivo ni ventana de QR.
- La compensación Redis usa compare-and-delete con nonce propio.

## Definition of Done

- [ ] Pruebas unitarias de validación de rol y asignación.
- [ ] Pruebas de idempotencia del check-in manual.
- [ ] Pruebas de rechazo por sesión inactiva.
- [ ] Prueba de request mixto rechazado.
- [ ] Contrato OpenAPI actualizado.
