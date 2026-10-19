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

- **Dado** una cotización `MONTHLY` vigente y un pago `PENDING`.
- **Cuando** el proveedor confirma el movimiento externo.
- **Entonces** Billing completa Payment como liquidación y deja Purchase en
  `PENDING_FULFILLMENT`; `api:app` fija cobertura con el timestamp confirmado,
  calcula sesiones elegibles y asigna cupos de forma atómica. La Purchase pasa a
  `ASSIGNED` sólo con todas las asignaciones. El quote conserva sólo su precio.

**Escenario 2: compra individual**

- **Dado** una cotización `INDIVIDUAL` vigente para una sesión concreta.
- **Cuando** el proveedor confirma el movimiento externo.
- **Entonces** la confirmación orquestada asigna exactamente un cupo en esa
  sesión; Payment ya representa la liquidación y Purchase pasa a `ASSIGNED`.

**Escenario 3: pago asíncrono**

- **Dado** un medio de pago asíncrono y capacidad disponible al checkout.
- **Cuando** `api:app` inicia el checkout.
- **Entonces** Physical crea un hold técnico breve y atómico antes de crear
  Payment o iniciar el proveedor; el
  alumno no puede verlo, renovarlo ni cancelarlo.
- **Y** el deadline del proveedor no puede superar la expiración del hold.
- **Y** al confirmar el movimiento externo, el hold se convierte
  o ajusta idempotentemente al conjunto de sesiones calculado con el timestamp
  confirmado; Purchase queda `ASSIGNED` al lograrlo o `EXCEPTION` al fallar. El
  hold se libera si falla o expira.

**Escenario 4: sin capacidad al checkout**

- **Dado** que Physical no puede crear el hold atómico.
- **Cuando** `api:app` intenta iniciar el checkout.
- **Entonces** responde `409 CAPACITY_UNAVAILABLE` sin crear Payment ni iniciar
  un movimiento externo.

**Escenario 5: reintento o webhook duplicado**

- **Dado** que `paymentId` y `quoteId` ya fueron procesados.
- **Cuando** llega un reintento o webhook duplicado.
- **Entonces** se devuelven las mismas asignaciones sin consumir cupos adicionales.

**Escenario 6: movimiento externo sin capacidad final**

- **Dado** que el movimiento externo fue confirmado pero el hold expiró o las
  sesiones calculadas desde el timestamp de confirmación no pueden asignarse por
  completo.
- **Cuando** `api:app` revalida o ajusta el hold.
- **Entonces** Payment queda `COMPLETED`, Purchase `EXCEPTION` y no
  se ejecuta ninguna política automática de reembolso.

No existen endpoints `/enroll`, `/waitlist`, `/reservations` ni reservas manuales.
El inicio público del flujo es
`POST /api/v1/billing/physical/purchases` con `quoteId`, `paymentMethod` y clave de
idempotencia.

## Requisitos No Funcionales y Restricciones

- La asignación multi-sesión es todo-o-nada y bloquea sesiones en orden estable.
- Un CapacityHold cuenta contra disponibilidad y expira por TTL configurable.
- La disponibilidad mostrada en el quote es una proyección no vinculante; el hold
  creado al checkout es la única garantía temporal de capacidad.
- Ningún core depende de otro: `api:app` orquesta contratos neutrales de `shared`.

## Notas Técnicas

Physical no conoce entidades Payment o Purchase. Recibe IDs y comandos neutrales,
aplica unicidad por Payment/sesión y devuelve CapacityAssignments tipadas.

## Definition of Done

- [ ] Pruebas concurrentes para el último cupo y compras mensuales multi-sesión.
- [ ] Pruebas de expiración y conversión idempotente del hold técnico.
- [ ] Ninguna asistencia queda habilitada sin una Purchase `ASSIGNED`.
- [ ] Contratos OpenAPI de Billing y Physical actualizados.

## Estado de implementación

- **Implementado (BFF web, #365):** el alumno puede cotizar la mensualidad o una sesión individual y pagar con
  Mercado Pago desde la web; los casos de falta de cupo (`409 CAPACITY_UNAVAILABLE`) y de cotización vencida
  (`410`) tienen aviso propio. Ver
  [06-BILLING-API.md](../06-BILLING-API.md#cotizar-y-pagar-clases-presenciales-desde-la-web-bff-365).
- **Implementado (BFF web, #365):** la página de retorno informa el estado del pago que devuelve la API (confirmado,
  en proceso, rechazado, cancelado o vencido). Un pago confirmado se muestra como "estamos asignando tus lugares",
  no como sesiones ya asignadas.
- **Pendiente (corte B):** mostrar las sesiones asignadas después del pago y el pago por transferencia bancaria.
