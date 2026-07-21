# US-BILLING-008: Transferencia presencial y excepción de capacidad

**ID:** US-BILLING-008  
**Título:** Pago presencial por transferencia con tratamiento manual de excepción  
**Módulos:** Billing, Physical y api:app  
**Prioridad (MoSCoW):** Should Have  
**Estado:** Draft  
**Épica:** EP-03 Suscripciones y pagos

## 1. Historia de Usuario

> **Como** alumno  
> **Quiero** pagar por transferencia  
> **Para** adquirir un curso presencial con comprobante verificable.

## 2. Criterios de Aceptación (BDD)

**Escenario 1: aprobación con capacidad**

- **Dado** un comprobante válido y capacidad asignable.
- **Cuando** un administrador aprueba el pago.
- **Entonces** se asignan cupos antes de marcar Payment `COMPLETED`.

**Escenario 2: dinero acreditado pero sin capacidad**

- **Dado** que ya existió movimiento externo y el hold expiró.
- **Cuando** no puede asignarse todo el snapshot.
- **Entonces** Payment queda `REQUIRES_MANUAL_REVIEW`, Purchase queda `EXCEPTION`
  y se registra auditoría. No se infiere devolución, reemplazo ni compensación.

**Escenario 3: rechazo sin movimiento confirmado**

- **Dado** un comprobante inválido y ningún movimiento externo confirmado.
- **Cuando** un administrador rechaza el pago.
- **Entonces** Payment queda `FAILED`, Purchase `CANCELLED` y el hold se libera.

## 3. Requisitos No Funcionales y Restricciones

- Comprobantes privados, máximo 5 MB y formatos permitidos por ADR-0017.
- Aprobación/rechazo idempotente, autenticada y auditada.
- La excepción nunca se resuelve automáticamente.

## 4. Notas Técnicas

Endpoints: `POST /api/v1/billing/payments/{paymentId}/proof`,
`POST /api/v1/billing/admin/payments/{paymentId}/approve` y
`POST /api/v1/billing/admin/payments/{paymentId}/reject`.

## 5. Definition of Done

- [ ] Pruebas de hold vencido, último cupo y excepción manual.
- [ ] Pruebas de seguridad del comprobante y auditoría.
- [ ] Contrato OpenAPI actualizado.
