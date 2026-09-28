# US-PHYSICAL-008: Check-in manual por recepcionista

**ID:** US-PHYSICAL-008
**Módulo:** Physical
**Prioridad:** Must Have
**Estado:** Implementado (#45)

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

**Escenario 5: sesión no vigente (divergencia deliberada, ver Decisiones)**

- **Dado** una sesión **cancelada**.
- **Cuando** el recepcionista intenta registrar asistencia.
- **Entonces** responde `409 Conflict` con `SESSION_NOT_ACTIVE`.

> Este escenario originalmente pedía "sesión pasada o cancelada". La
> implementación final sólo bloquea sesiones `CANCELLED`; una sesión ya
> transcurrida pero no cancelada SÍ acepta el check-in manual (carga
> retroactiva, sin límite de tiempo). Ver D7/D8 en "Decisiones de
> implementación" abajo — divergencia resuelta con el usuario, no
> reabierta.

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
- ~~La compensación Redis usa compare-and-delete con nonce propio.~~
  **Superado por D1** — ver "Decisiones de implementación" abajo.

## Decisiones de implementación (#45)

Estas decisiones se tomaron con el usuario durante el diseño de #45 y no
se reabren. Documentadas también en `openspec/changes/physical-manual-checkin/proposal.md`
(D1, D7, D8).

- **D1 — Estrategia de lock Redis.** Este documento (arriba, "Notas
  Técnicas") pedía compensación por compare-and-delete con un nonce propio.
  La implementación final **reutiliza el mismo patrón acquire-or-expire que
  ya usa el flujo QR** sobre la clave
  `checkin:attendance:{sessionId}:{studentId}` — sin compare-and-delete.
  Esto es consistente con la decisión ya tomada (y ya shippeada) para el
  flujo QR, que rechazó explícitamente ese mismo patrón de dos fases por no
  justificarse para un escaneo de pasillo. Reusar la misma clave, en lugar
  de inventar una nueva, es lo que hace que un escaneo QR y una carga MANUAL
  para el mismo alumno y sesión compitan por la misma fila en vez de generar
  filas duplicadas.
- **D7 — Orden de validación asertado.** rol → existencia de `studentId` →
  asignación de cupo confirmada → sesión no `CANCELLED` → lectura
  idempotente → lock único → INSERT. Este orden es intencional, no
  incidental: decide qué error responde una solicitud con múltiples
  violaciones a la vez (p. ej. un `studentId` desconocido en una sesión
  cancelada responde `404 STUDENT_NOT_FOUND`, no `409`). Una consecuencia
  notable: un `{sessionId}` inexistente responde
  `403 CAPACITY_ASSIGNMENT_REQUIRED`, nunca `404`, porque una asignación
  confirmada no puede existir para una sesión que no existe.
- **D8 — Regla de "sesión vigente" acotada, diverge del Escenario 5
  original.** El Escenario 5 original pedía rechazar tanto una sesión
  cancelada como una sesión "pasada". La implementación **sólo** rechaza
  `CANCELLED` (`409 SESSION_NOT_ACTIVE`); una sesión ya transcurrida pero no
  cancelada acepta el check-in manual sin límite de tiempo. Esto habilita
  la carga retroactiva de asistencia (p. ej. el recepcionista olvidó
  registrarla durante la sesión) — un objetivo de producto explícito,
  verificado con el usuario, que la redacción original del escenario no
  contemplaba.

## Definition of Done

- [x] Pruebas unitarias de validación de rol y asignación.
- [x] Pruebas de idempotencia del check-in manual.
- [x] Pruebas de rechazo por sesión inactiva.
- [x] Prueba de request mixto rechazado.
- [x] Contrato OpenAPI actualizado.
