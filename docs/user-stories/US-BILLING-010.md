# US-BILLING-010: Suscripción a un plan con pago online

**ID:** US-BILLING-010
**Título:** Alta de suscripción con pago por Mercado Pago
**Módulos:** Billing y api:app
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** alumno autenticado
> **Quiero** suscribirme a un plan pagando online
> **Para** obtener acceso a los cursos virtuales que ese plan incluye.

Es el equivalente virtual de [US-BILLING-007](US-BILLING-007.md) (compra
presencial): la vía principal de compra, frente a
[US-BILLING-003](US-BILLING-003.md), que cubre la variante por transferencia
bancaria.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: iniciar una suscripción**

- **Dado** un alumno autenticado sin suscripción vigente y un plan `ACTIVE`.
- **Cuando** envía `POST /api/v1/billing/subscriptions` con `planId`,
  `paymentMethod` y una clave de idempotencia.
- **Entonces** Billing crea una `Subscription` en `PENDING` y un `Payment` en
  `PENDING/AWAITING_PROVIDER`, **ambos asociados al usuario del token**.
- **Y** devuelve `201 Created` con el identificador de la suscripción y la URL
  de pago del proveedor.

**Escenario 2: pago confirmado**

- **Dado** una `Subscription` `PENDING` con su `Payment` asociado.
- **Cuando** el proveedor confirma el movimiento y el webhook lo verifica
  ([US-BILLING-002](US-BILLING-002.md)).
- **Entonces** el `Payment` pasa a `COMPLETED` como liquidación y la
  `Subscription` a `ACTIVE`, con `startDate` en el timestamp confirmado y
  `endDate` calculado con `durationDays` del plan.
- **Y** la suscripción **persiste un snapshot del conjunto de cursos** que el
  plan habilitaba en ese instante.
- **Y** el alumno obtiene acceso a **todos los cursos incluidos en ese
  snapshot**.

**Escenario 2b: el plan cambia después de activarse la suscripción**

- **Dado** una `Subscription` `ACTIVE` con su snapshot de cursos.
- **Cuando** un admin desactiva el plan o le quita uno de esos cursos.
- **Entonces** la suscripción **conserva su acceso íntegro hasta vencer**: el
  cambio administrativo no altera lo ya comprado.
- **Y** el cambio sí afecta a las suscripciones que se activen a partir de ese
  momento.

**Escenario 3: ya tiene una suscripción vigente**

- **Dado** un alumno con una `Subscription` en `ACTIVE`.
- **Cuando** intenta iniciar otra suscripción.
- **Entonces** responde `409 SUBSCRIPTION_ALREADY_ACTIVE` y **no** crea un
  `Payment` ni una `Subscription` nueva, ni inicia un cobro en el proveedor.
- **Y** la respuesta informa la fecha de vencimiento de la suscripción vigente.

**Escenario 4: plan inexistente o inactivo**

- **Dado** un `planId` que no existe o cuyo plan está `INACTIVE`.
- **Cuando** el alumno intenta suscribirse.
- **Entonces** responde `422 PLAN_NOT_AVAILABLE` sin crear `Payment` ni
  `Subscription`, aplicando la misma disciplina de no enumeración que el
  catálogo público: no distingue "no existe" de "no está disponible".

**Escenario 5: idempotencia**

- **Dado** una solicitud ya procesada con una clave de idempotencia.
- **Cuando** se repite con la misma clave.
- **Entonces** devuelve el mismo `subscriptionId` y `paymentId` sin iniciar un
  segundo cobro en el proveedor.

**Escenario 6: pago no acreditado**

- **Dado** una `Subscription` en `PENDING`.
- **Cuando** el proveedor reporta `rejected`, `cancelled` o `expired`.
- **Entonces** el `Payment` queda en su estado terminal y la `Subscription`
  **nunca** pasa a `ACTIVE`; no habilita acceso a ningún curso.
- **Y** el alumno puede iniciar una suscripción nueva, porque no tiene ninguna
  vigente.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Requiere access token válido.
  * La suscripción y el pago se asocian **al usuario del token**; el cliente
    nunca envía a qué usuario pertenecen.
  * El BFF nunca decide el resultado del pago: su callback usa el identificador
    de suscripción y un `state` firmado, igual que en US-BILLING-007.
* **Integridad:**
  * La liquidación financiera nunca espera al fulfillment — un `Payment` puede
    quedar `COMPLETED` aunque el otorgamiento de acceso falle, y ese caso se
    trata como excepción de fulfillment, no como pago no acreditado.
  * No se crea un cobro externo nuevo de forma automática ante un resultado
    incierto (ver US-BILLING-002).
* **Rendimiento / Rate Limiting:**
  * Límite por usuario para evitar la creación masiva de pagos pendientes.
* **Alcance del MVP:**
  * `autoRenew` es siempre `false` (consistente con US-BILLING-004): la
    renovación es una compra nueva, no un débito automático.
  * La cancelación de una suscripción **no** entra en esta historia; se cubre
    en una historia propia.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoint:**
  * `POST /api/v1/billing/subscriptions` — recibe `planId`, `paymentMethod` y
    clave de idempotencia.

* **Brechas que esta historia debe cerrar** (verificadas contra el código):

  1. **No existe checkout.** Hoy ningún caso de uso crea un `Payment`: sólo
     existen `PaymentVerificationService` y `WebhookVerificationWorker`, que
     verifican un pago preexistente. El javadoc de `Payment` lo declara
     explícitamente como fuera de alcance de US-BILLING-002.
  2. **Billing no guarda el usuario.** Ni `billing_payments` ni
     `billing_subscriptions` tienen columna de usuario
     (`V8__billing_payments.sql`), ni los modelos de dominio. Requiere
     migración. Sin esto, US-BILLING-004 ("el usuario sólo puede ver sus
     propias suscripciones") es irrealizable.
  3. **La suscripción apunta a un curso, no a un plan.** Hoy
     `PaymentTarget.Virtual` lleva un `courseId` suelto y
     `Subscription.virtualCourseId` también. El negocio es por plan: debe
     referenciar `planId`, y el acceso se deriva del snapshot de
     `Plan.courses` tomado al activar.

  5. **No hay snapshot de los cursos del plan.** La suscripción debe persistir
     qué cursos habilitaba el plan al activarse, para que un cambio
     administrativo posterior no le quite acceso a quien ya pagó (Escenario
     2b). Sin snapshot, el acceso se evaluaría contra el plan vivo y una
     desactivación cortaría el acceso en caliente — un defecto observado en
     otro sistema con este mismo modelo, donde la intención declarada del
     soft-delete y el comportamiento real no coincidían.
  4. **`Subscription` no tiene vigencia.** Hoy sólo modela
     `PENDING_FULFILLMENT` / `ASSIGNED` / `EXCEPTION`, sin `startDate`,
     `endDate` ni estado `ACTIVE`/`EXPIRED`. US-BILLING-004 ya especifica esos
     campos en su contrato de respuesta.

* **Consistencia con el circuito existente:**
  * La confirmación llega por el webhook ya construido en US-BILLING-002 — esta
    historia aporta la mitad de **iniciación** del circuito, no una segunda vía
    de confirmación.
  * `Plan.durationDays` pasa a usarse por primera vez: hoy existe en el modelo
    pero ningún flujo lo consume.

* **Tablas de BD:**
  * `billing_payments` — requiere columna de usuario.
  * `billing_subscriptions` — requiere usuario, plan, vigencia y estado.
  * Snapshot de cursos habilitados por suscripción — tabla nueva; no puede
    derivarse de `billing_plan_courses`, que refleja el estado **actual** del
    plan y no el del momento de la compra.
  * `billing_plans` / `billing_plan_courses` — lectura, ya existen.

---

## 5. Definition of Done (Criterios de Finalización)

- [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
- [ ] La suscripción y el pago quedan asociados al usuario autenticado, con
      prueba de que un cliente no puede suscribir a otro usuario.
- [ ] Prueba de rechazo con suscripción vigente, sin efectos colaterales
      (ni pago local ni cobro en el proveedor).
- [ ] Prueba de idempotencia sin doble cobro.
- [ ] Prueba end-to-end: alta → webhook → `ACTIVE` con vigencia correcta.
- [ ] El acceso resultante habilita exactamente los cursos del plan.
- [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
- [ ] El código pasa la validación de Checkstyle y ArchUnit.
