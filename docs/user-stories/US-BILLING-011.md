# US-BILLING-011: Cancelación de suscripción

**ID:** US-BILLING-011
**Título:** Baja de una suscripción vigente
**Módulos:** Billing
**Prioridad (MoSCoW):** Should Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** alumno con una suscripción vigente
> **Quiero** dar de baja mi suscripción
> **Para** dejar de renovarla y conocer hasta cuándo conservo el acceso.

`Plan.cancellationPolicy` existe en el modelo desde US-BILLING-001, pero ninguna
historia definía el flujo que la ejecuta. Esta la cubre.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: cancelar una suscripción vigente**

- **Dado** un alumno con una `Subscription` en `ACTIVE`.
- **Cuando** envía `DELETE /api/v1/billing/subscriptions/me`.
- **Entonces** la suscripción pasa a `CANCELLED` con la fecha de cancelación
  registrada.
- **Y** la respuesta informa hasta qué fecha conserva el acceso.

**Escenario 2: el acceso se conserva hasta el vencimiento**

- **Dado** una suscripción `CANCELLED` cuyo `endDate` todavía no pasó.
- **Cuando** el alumno accede a un curso que su snapshot habilitaba.
- **Entonces** el acceso se concede: ya pagó ese período.
- **Y** al llegar `endDate` la suscripción pasa a `EXPIRED` y el acceso cesa.

> Cancelar significa **no renovar**, no perder lo pagado. Es una decisión
> deliberada: cortar el acceso al instante castigaría a quien avisa con
> anticipación.

**Escenario 3: cancelar habilita comprar de nuevo**

- **Dado** un alumno que canceló su suscripción.
- **Cuando** intenta iniciar una suscripción nueva
  ([US-BILLING-010](US-BILLING-010.md)).
- **Entonces** el sistema lo permite: la regla de rechazo por suscripción
  vigente aplica sólo a `ACTIVE`, nunca a `CANCELLED`.
- **Y** se crea una fila nueva; nunca se reactiva la cancelada.

**Escenario 4: sin suscripción cancelable**

- **Dado** un alumno sin suscripción `ACTIVE`.
- **Cuando** intenta cancelar.
- **Entonces** responde `404` indicando que no hay suscripción vigente.

**Escenario 5: cancelación por un administrador**

- **Dado** un administrador y la suscripción de un alumno.
- **Cuando** el administrador la cancela desde el panel.
- **Entonces** el efecto es idéntico al de la cancelación por el propio alumno.
- **Y** queda registrado quién la canceló y el motivo.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Un alumno sólo puede cancelar su propia suscripción.
  * Un administrador puede cancelar cualquiera, con auditoría de actor y motivo.
* **Integridad:**
  * La cancelación **no** dispara reembolso automático. Un reembolso es una
    operación de pago, no de suscripción, y queda fuera de esta historia.
  * `Plan.cancellationPolicy` se muestra al confirmar la baja; es informativa y
    no altera el cálculo de vigencia.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints:**
  * `DELETE /api/v1/billing/subscriptions/me` — baja por el alumno.
  * Endpoint administrativo equivalente, con auditoría.

* **Estados:** requiere que `Subscription` distinga `CANCELLED` de `EXPIRED`
  (ver US-BILLING-010 y US-BILLING-004). Son estados distintos con semántica
  distinta: `CANCELLED` es una decisión, `EXPIRED` es el paso del tiempo.
  Colapsarlos hace imposible responder "¿por qué perdió el acceso?".

* **Invalidación de caché:** la cancelación emite `SubscriptionStatusChanged`
  para el usuario afectado (ver [US-VIRTUAL-007](US-VIRTUAL-007.md)). La
  invalidación es por usuario, nunca un vaciado global.

---

## 5. Definition of Done (Criterios de Finalización)

- [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
- [ ] Prueba de que el acceso se conserva entre la cancelación y el vencimiento.
- [ ] Prueba de que tras cancelar se puede comprar de nuevo, creando fila nueva.
- [ ] Prueba de que un alumno no puede cancelar la suscripción de otro.
- [ ] Auditoría de la cancelación administrativa (actor y motivo).
- [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
- [ ] El código pasa la validación de Checkstyle y ArchUnit.
