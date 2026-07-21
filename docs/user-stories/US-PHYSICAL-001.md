# US-PHYSICAL-001: Check-in con código QR

**ID:** US-PHYSICAL-001  
**Módulo:** Physical  
**Prioridad:** Must Have  
**Estado:** Draft

## Historia de usuario

> Como alumno con una asignación de cupo confirmada, quiero hacer check-in con
> código QR para registrar mi asistencia a una sesión concreta.

## Criterios de aceptación

**Escenario 1: check-in válido**

- **Dado** un alumno con cupo asignado en la sesión y dentro de la ventana de acceso.
- **Cuando** envía `POST /api/v1/physical/sessions/{sessionId}/check-ins`.
- **Entonces** Physical registra la asistencia una sola vez y responde `201 Created`.

**Escenario 2: sin cupo confirmado**

- **Dado** un alumno sin asignación mensual o individual para la sesión.
- **Cuando** intenta hacer check-in.
- **Entonces** responde `403` con `CAPACITY_ASSIGNMENT_REQUIRED`; un pago pendiente
  o un hold técnico no habilitan el acceso.

**Escenario 3: QR inválido, duplicado o fuera de ventana**

- **Dado** un QR con firma inválida, expirado, fuera de ventana o cuyos claims no
  coinciden con usuario y sesión.
- **Cuando** el lector intenta procesarlo.
- **Entonces** Physical lo rechaza antes de adquirir cualquier lock Redis.

**Escenario 4: reintento idempotente**

- **Dado** una asistencia ya persistida para el mismo `qrJti`, usuario y sesión.
- **Cuando** el lector reintenta el request.
- **Entonces** devuelve la asistencia existente con `200 OK` y no inserta otra fila.

**Escenario 5: fallo después de adquirir el lock**

- **Dado** validaciones correctas y un lock adquirido con nonce propio.
- **Cuando** falla el `INSERT` o la transacción de asistencia.
- **Entonces** Physical ejecuta compare-and-delete del lock solo si conserva ese
  nonce, y permite un reintento seguro.

## Contrato

```json
{
  "qrCode": "signed-qr",
  "deviceId": "puerta-principal",
  "deviceToken": "dvc_xxx"
}
```

La autorización consulta la asignación de cupo de Physical, no tablas financieras
ni una “reserva” del alumno. Véase
[Pagos de Clases Presenciales](../28-PHYSICAL-CLASS-PAYMENTS.md).

El orden obligatorio es: autenticar lector; validar firma, expiración, ventana,
claims, asignación y sesión; consultar idempotencia; adquirir
`SET checkin:{qrJti}:{userId}:{sessionId} {lockNonce} NX EX <ttl>`; insertar
`attendance` con unique `(user_id, session_id)`. La compensación Redis usa
compare-and-delete para no borrar un lock ajeno.

## Definition of Done

- [ ] Pruebas unitarias para firma, ventana y asignación de cupo.
- [ ] Pruebas de integración e idempotencia del check-in.
- [ ] Prueba de compensación cuando falla el insert después de adquirir el lock.
- [ ] Contrato OpenAPI actualizado.
