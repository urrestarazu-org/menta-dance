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

- **Dado** un quote mensual vigente y capacidad para todas sus sesiones.
- **Cuando** el pago externo se acredita.
- **Entonces** `api:app` coordina la asignación completa, convierte el hold y solo
  después cambia `paymentStatus` a `COMPLETED` y `purchaseStatus` a
  `CAPACITY_ASSIGNED`.

**Escenario 2: pago individual confirmado**

- **Dado** un quote individual vigente para `selectedSessionId`.
- **Cuando** el proveedor confirma el movimiento externo.
- **Entonces** Physical asigna exactamente `selectedSessionId` y ningún otro, y
  Billing completa Payment únicamente después de esa asignación.

**Escenario 3: idempotencia**

- **Dado** una Purchase ya iniciada o confirmada.
- **Cuando** se repite la operación con el mismo `purchaseId`, `paymentId` o clave
  idempotente.
- **Entonces** se devuelve el mismo resultado sin consumir capacidad adicional.

## 3. Requisitos No Funcionales y Restricciones

- Solo una transacción puede tomar el último cupo.
- No existen reservas manuales ni listas de espera.
- Un hold técnico es interno, breve y expirable.
- El callback del BFF usa `purchaseId` y un `state` firmado; nunca decide el pago.

## 4. Notas Técnicas

`POST /api/v1/billing/physical-purchases` recibe `quoteId`, `paymentMethod` y una
clave de idempotencia. Los estados de Payment, Purchase y CapacityHold son
autómatas separados.

## 5. Definition of Done

- [ ] Pruebas concurrentes del último cupo y compra multi-sesión.
- [ ] Webhook duplicado e idempotencia end-to-end.
- [ ] Contratos OpenAPI y pruebas de callback firmado.
