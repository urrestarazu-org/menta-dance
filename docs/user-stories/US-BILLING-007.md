# US-BILLING-007: Compra presencial y pago individual

**ID:** US-BILLING-007
**Título:** Compra mensual o de sesión individual con asignación automática
**Módulos:** Billing, Physical y api:app
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-04 Academia Física

## 1. Historia de Usuario

> **Como** alumno
> **Quiero** pagar un quote mensual o individual
> **Para** recibir automáticamente los cupos de sus sesiones.

## 2. Criterios de Aceptación (BDD)

**Escenario 1: pago mensual confirmado**

- **Dado** un quote mensual vigente y un Payment `PENDING`.
- **Cuando** el pago externo se acredita.
- **Entonces** Payment pasa a `COMPLETED` como liquidación y Purchase a
  `PENDING_FULFILLMENT`; `api:app` calcula cobertura/sesiones, revalida
  capacidad/hold y sólo entonces cambia Purchase a `ASSIGNED`.

**Escenario 2: pago individual confirmado**

- **Dado** un quote individual vigente para `selectedSessionId`.
- **Cuando** el proveedor confirma el movimiento externo.
- **Entonces** Physical asigna exactamente `selectedSessionId` y ningún otro, y
  Billing deja la Purchase `ASSIGNED` después de esa asignación.

**Escenario 3: idempotencia**

- **Dado** una Purchase ya iniciada o confirmada.
- **Cuando** se repite la operación con el mismo `purchaseId`, `paymentId` o clave
  idempotente.
- **Entonces** se devuelve el mismo resultado sin consumir capacidad adicional.

## 3. Requisitos No Funcionales y Restricciones

- Solo una transacción puede tomar el último cupo.
- No existen reservas manuales ni listas de espera.
- Un hold técnico es interno, breve y expirable.
- El retorno del BFF no usa un `state` firmado: el BFF guarda el `paymentId` en la sesión del servidor antes de
  redirigir a Mercado Pago, lee ese id (nunca parámetros de la URL) y nunca decide el pago.

## 4. Notas Técnicas

`POST /api/v1/billing/physical/purchases` recibe `quoteId`, `paymentMethod` y una
clave de idempotencia. Los estados de Payment, Purchase y CapacityHold son
autómatas separados.

## 5. Definition of Done

- [ ] Pruebas concurrentes del último cupo y compra multi-sesión.
- [ ] Webhook duplicado e idempotencia end-to-end.
- [ ] Contratos OpenAPI y pruebas del retorno del BFF (el `paymentId` sale de la sesión; los parámetros de la
      URL se ignoran).

## 6. Estado de implementación

- **Implementado (BFF web, #365):** cotizar y pagar con Mercado Pago desde la web: el alumno pide la cotización,
  el BFF inicia el checkout con una clave de idempotencia, guarda el `paymentId` en la sesión y redirige al
  proveedor. Ver [06-BILLING-API.md](../06-BILLING-API.md#cotizar-y-pagar-clases-presenciales-desde-la-web-bff-365).
- **Implementado (BFF web, #365):** la página de retorno `GET /compras/retorno` lee el `paymentId` de la sesión,
  consulta el estado a la API y muestra "Pago confirmado, estamos asignando tus lugares" (`COMPLETED`), "Estamos
  confirmando tu pago" (pendiente o desconocido) o un mensaje por pago rechazado, cancelado o vencido. Ignora los
  parámetros de la URL y nunca decide el pago; **no** afirma que las sesiones ya estén asignadas.
- **Pendiente (corte B):** mostrar las sesiones asignadas tras el pago (Purchase `ASSIGNED`) y el pago por
  transferencia bancaria desde la web.
