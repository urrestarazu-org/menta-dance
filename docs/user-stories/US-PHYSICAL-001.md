# US-PHYSICAL-001: Check-in con código QR

**ID:** US-PHYSICAL-001  
**Módulo:** Physical  
**Prioridad:** Must Have  
**Estado:** Draft

## Historia de usuario

> Como alumno con una asignación de cupo confirmada, quiero hacer check-in con
> un código QR efímero mostrado en mi app para registrar mi asistencia a una
> sesión concreta.

## Criterios de aceptación

**Escenario 1: emisión y presentación del QR**

- **Dado** un alumno autenticado con cupo confirmado y una sesión dentro de su
  ventana de check-in.
- **Cuando** solicita `POST /api/v1/physical/sessions/{sessionId}/access-qr`.
- **Entonces** recibe una credencial firmada, efímera y ligada a alumno, sesión,
  `jti` y expiración; Android la renderiza localmente como imagen QR y la renueva
  antes de expirar.

**Escenario 2: check-in válido**

- **Dado** un alumno con cupo asignado en la sesión y dentro de la ventana de acceso.
- **Cuando** envía `POST /api/v1/physical/sessions/{sessionId}/check-ins` con
  variante `QR`; el alumno se obtiene de los claims firmados.
- **Entonces** Physical registra la asistencia una sola vez y responde `201 Created`.

**Escenario 3: sin cupo confirmado**

- **Dado** un alumno sin asignación mensual o individual para la sesión.
- **Cuando** intenta hacer check-in.
- **Entonces** responde `403` con `CAPACITY_ASSIGNMENT_REQUIRED`; un pago pendiente
  o un hold técnico no habilitan el acceso.

**Escenario 4: QR inválido, duplicado o fuera de ventana**

- **Dado** un QR con firma inválida, expirado, fuera de ventana o cuyos claims no
  coinciden con usuario y sesión.
- **Cuando** el lector intenta procesarlo.
- **Entonces** Physical lo rechaza antes de adquirir cualquier lock Redis.

**Escenario 5: reintento idempotente**

- **Dado** una asistencia ya persistida para el mismo alumno y sesión.
- **Cuando** el lector o Recepción reintenta el request.
- **Entonces** devuelve la asistencia existente con `200 OK` y no inserta otra fila.

**Escenario 6: fallo después de adquirir el lock**

- **Dado** validaciones correctas y un lock adquirido con nonce propio.
- **Cuando** falla el `INSERT` o la transacción de asistencia.
- **Entonces** Physical ejecuta compare-and-delete del lock solo si conserva ese
  nonce, y permite un reintento seguro.

## Contrato

### Emisión para Android

```json
{
  "qrCredentials": "signed-qr",
  "expiresAt": "2026-07-22T18:30:00Z",
  "refreshAfterSeconds": 30
}
```

Android transforma `qrCredentials` en una imagen QR local. La credencial contiene
claims firmados de alumno, sesión, `jti` y `exp`; no contiene el secreto del lector
ni se persiste en el dispositivo como un identificador reutilizable.

### Check-in del lector

```json
{
  "type": "QR",
  "qrCredentials": "signed-qr",
  "deviceId": "puerta-principal",
  "deviceToken": "dvc_xxx"
}
```

La carga manual usa exclusivamente `{ "type": "MANUAL", "studentId": "..." }`;
una solicitud no puede contener `studentId` y credenciales QR a la vez.

`MANUAL` valida Recepción, alumno, asignación y sesión antes de adquirir
`checkin:attendance:{sessionId}:{studentId}`. `QR` valida lector, firma,
expiración, ventana y claims; adquiere `checkin:qr:{qrJti}` para replay y después
el mismo lock de asistencia. Ambos dependen de la unicidad `(session_id, user_id)`.

La autorización consulta la asignación de cupo de Physical, no tablas financieras
ni una “reserva” del alumno. Véase
[Pagos de Clases Presenciales](../28-PHYSICAL-CLASS-PAYMENTS.md).

El orden QR es: autenticar lector; validar firma, expiración, ventana, claims,
asignación y sesión; consultar idempotencia; adquirir locks QR y de asistencia;
insertar `attendance` con unique `(user_id, session_id)`. El manual omite los
pasos QR y valida actor/`studentId`. La compensación Redis usa compare-and-delete
para no borrar un lock ajeno.

## Definition of Done

- [ ] Pruebas unitarias para firma, ventana y asignación de cupo.
- [ ] Pruebas de integración e idempotencia del check-in.
- [ ] Prueba de compensación cuando falla el insert después de adquirir el lock.
- [ ] Pruebas de emisión, expiración y renovación del QR visual en Android.
- [ ] Contrato OpenAPI actualizado.
