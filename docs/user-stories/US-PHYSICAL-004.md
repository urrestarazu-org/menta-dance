# US-PHYSICAL-004: Compra y asignación automática de cupo

**ID:** US-PHYSICAL-004  
**Módulos:** Billing y Physical  
**Prioridad:** Must Have  
**Estado:** Draft

## Historia de usuario

> Como alumno, quiero pagar una mensualidad o una sesión individual para recibir
> automáticamente el cupo correspondiente, sin realizar una reserva manual.

## Criterios de aceptación

**Escenario 1: compra mensual**

- **Dado** una cotización `MONTHLY` vigente y con cupo para todas sus sesiones.
- **Cuando** el proveedor confirma el movimiento externo.
- **Entonces** `api:app` solicita primero que Physical asigne al alumno un cupo
  en cada sesión del snapshot y, solo después, Billing completa Payment y
  Purchase. Las sesiones pertenecen a
  `[coverageStartDate, coverageEndExclusive)` fijado por el quote.

**Escenario 2: compra individual**

- **Dado** una cotización `INDIVIDUAL` vigente para una sesión concreta.
- **Cuando** el proveedor confirma el movimiento externo.
- **Entonces** la confirmación orquestada asigna exactamente un cupo en esa
  sesión antes de completar Payment.

**Escenario 3: pago asíncrono**

- **Dado** un medio de pago asíncrono y capacidad disponible.
- **Cuando** `api:app` inicia la Purchase.
- **Entonces** Physical puede crear un hold técnico breve antes del checkout; el
  alumno no puede verlo, renovarlo ni cancelarlo.
- **Y** al confirmar el movimiento externo, el hold se convierte
  idempotentemente en asignaciones antes de completar Payment; al fallar o
  expirar, se libera.

**Escenario 4: sin capacidad**

- **Dado** que no puede garantizarse cupo para todo el snapshot.
- **Cuando** el alumno intenta iniciar la Purchase.
- **Entonces** no se crea el pago y se responde
  `409 COURSE_CAPACITY_UNAVAILABLE`.

**Escenario 5: reintento o webhook duplicado**

- **Dado** que `paymentId` y `quoteId` ya fueron procesados.
- **Cuando** llega un reintento o webhook duplicado.
- **Entonces** se devuelven las mismas asignaciones sin consumir cupos adicionales.

**Escenario 6: movimiento externo sin capacidad**

- **Dado** que el movimiento externo fue confirmado pero el snapshot ya no puede
  asignarse por completo.
- **Cuando** `api:app` intenta convertir el hold.
- **Entonces** Payment queda `REQUIRES_MANUAL_REVIEW`, Purchase `EXCEPTION` y no
  se ejecuta ninguna política automática de reembolso.

No existen endpoints `/enroll`, `/waitlist`, `/reservations` ni reservas manuales.
El inicio público del flujo es
`POST /api/v1/billing/physical-purchases` con `quoteId`, `paymentMethod` y clave de
idempotencia.

## Requisitos No Funcionales y Restricciones

- La asignación multi-sesión es todo-o-nada y bloquea sesiones en orden estable.
- Un CapacityHold cuenta contra disponibilidad y expira por TTL configurable.
- Ningún core depende de otro: `api:app` orquesta contratos neutrales de `shared`.

## Notas Técnicas

Physical no conoce entidades Payment o Purchase. Recibe IDs y comandos neutrales,
aplica unicidad por Payment/sesión y devuelve CapacityAssignments tipadas.

## Definition of Done

- [ ] Pruebas concurrentes para el último cupo y compras mensuales multi-sesión.
- [ ] Pruebas de expiración y conversión idempotente del hold técnico.
- [ ] Ningún pago queda completado sin asignación de cupo.
- [ ] Contratos OpenAPI de Billing y Physical actualizados.
