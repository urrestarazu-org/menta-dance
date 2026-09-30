# US-BILLING-008: Transferencia presencial y excepción de capacidad

**ID:** US-BILLING-008
**Título:** Pago presencial por transferencia con tratamiento manual de excepción
**Módulos:** Billing, Physical y api:app
**Prioridad (MoSCoW):** Should Have
**Estado:** Implementado (#36)
**Épica:** EP-03 Suscripciones y pagos

---

## 1. Historia de Usuario

> **Como** alumno
> **Quiero** pagar por transferencia
> **Para** adquirir un curso presencial con comprobante verificable.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: creación del checkout por transferencia**

* **Dado que (Given):** Un alumno autenticado con una `PhysicalCourseQuote` vigente (no vencida).
* **Cuando (When):** Envía `POST /api/v1/billing/physical/purchases` con `paymentMethod: BANK_TRANSFER`.
* **Entonces (Then):** El sistema crea un único `Payment` en `AwaitingManualVerification`, con el
  CBU configurado como `expectedMerchantAccountId`.
* **Y (And):** Devuelve `201` con `bankTransferInstructions` (CBU, alias, titular, CUIT, monto,
  referencia) — `providerPreferenceId` y `checkoutUrl` quedan `null`.
* **Y (And):** No reserva capacidad (cero filas `physical_capacity_holds`) ni llama a ningún
  proveedor de pago.

**Escenario 2: aprobación con capacidad disponible**

* **Dado que (Given):** Un `Payment` `AwaitingManualVerification` con comprobante válido.
* **Cuando (When):** Un administrador aprueba el pago (`POST
  /api/v1/admin/billing/payments/{paymentId}/approve`).
* **Entonces (Then):** `Payment` pasa a `COMPLETED` como liquidación.
* **Y (And):** La capacidad se calcula recién en este momento, a partir de `confirmedAt` — nunca
  hubo un hold que pudiera vencer. Si hay cupo para el conjunto de sesiones elegibles, `Purchase`
  llega a `ASSIGNED` con una fila `physical_capacity_assignments` por sesión.

**Escenario 3: dinero acreditado pero sin capacidad al momento de la aprobación**

* **Dado que (Given):** Un `Payment` `AwaitingManualVerification` con comprobante válido, pero el
  conjunto de sesiones elegibles ya no tiene cupo cuando un administrador aprueba.
* **Cuando (When):** No puede asignarse el conjunto de sesiones calculado desde `confirmedAt`.
* **Entonces (Then):** `Payment` queda `COMPLETED` (la liquidación ya ocurrió), `Purchase` queda
  `EXCEPTION` sin asignaciones parciales (todo o nada) y se registra el residual. No se infiere
  devolución, reemplazo ni compensación.

**Escenario 4: rechazo sin movimiento confirmado**

* **Dado que (Given):** Un comprobante inválido y ningún movimiento externo confirmado.
* **Cuando (When):** Un administrador rechaza el pago (`POST
  /api/v1/admin/billing/payments/{paymentId}/reject`).
* **Entonces (Then):** `Payment` queda `REJECTED`. No se crea ninguna `Purchase` (nunca existió) y
  no hay nada que liberar — la transferencia nunca reservó capacidad.

**Escenario 5: expiración automática sin comprobante**

* **Dado que (Given):** Un `Payment` `AwaitingManualVerification` por transferencia, sin
  comprobante subido, con más de 72 horas de antigüedad.
* **Cuando (When):** Corre el barrido automático de expiración.
* **Entonces (Then):** `Payment` pasa a `EXPIRED` y no se crea ninguna `Purchase` — el mismo
  barrido que ya cubre transferencias de suscripción, sin distinguir modalidad.

**Escenario 6: cupo compartido de creación**

* **Dado que (Given):** Un alumno ya realizó 10 creaciones de transferencia bancaria en el día
  (suscripciones, compras presenciales, o una mezcla de ambas).
* **Cuando (When):** Intenta una 11ª creación de cualquiera de los dos tipos.
* **Entonces (Then):** El sistema responde `429 BANK_TRANSFER_RATE_LIMITED` — el cupo es uno solo,
  compartido entre `POST /api/v1/billing/subscriptions` y `POST
  /api/v1/billing/physical/purchases`.

---

## 3. Requisitos No Funcionales y Restricciones

* Comprobantes privados, máximo 5 MB; se aceptan sólo formatos configurados por Billing y se
  validan tipo y contenido antes de almacenarlos (ya shippeado, #31/#33, sin cambios).
* Aprobación/rechazo idempotente, autenticada y auditada (ya shippeado, #31/#33, sin cambios).
* La excepción nunca se resuelve automáticamente.
* La verificación de disponibilidad en la creación (`409`) es best-effort y no vinculante — ver D7
  en la sección de decisiones.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints involucrados:**
  * `POST /api/v1/billing/physical/purchases` con `paymentMethod: BANK_TRANSFER` — mismo endpoint
    que Checkout Pro (#41), enruta por `paymentMethod` (ver D2 abajo); sin ruta nueva.
  * `POST /api/v1/billing/payments/{paymentId}/proof` — subida de comprobante, ya shippeado
    (#31/#33), genérico sobre el destino del pago, sin cambios.
  * `POST /api/v1/admin/billing/payments/{paymentId}/approve` y
    `POST /api/v1/admin/billing/payments/{paymentId}/reject` — ya shippeados (#31/#33), genéricos
    sobre `PaymentTarget`, sin cambios de código.
* **Response body (creación, 201):**

  ```json
  {
    "paymentId": "b3f1...-uuid",
    "quoteId": "9a02...-uuid",
    "status": "PENDING",
    "providerPreferenceId": null,
    "checkoutUrl": null,
    "externalReference": "PHY-BT-...",
    "bankTransferInstructions": {
      "cbu": "0000003100000000000000",
      "alias": "menta.dance",
      "holder": "Menta Dance SRL",
      "cuit": "30-00000000-0",
      "amount": { "amount": 10000.00, "currency": "ARS" },
      "reference": "PHY-BT-..."
    }
  }
  ```

* **Nuevas clases (api:billing):** `CreateBankTransferPhysicalPurchaseUseCase(+Impl)`,
  `RoutingCreatePhysicalPurchaseCheckoutUseCase` — capa de aplicación únicamente, sin dependencia
  de `com.menta.physical..` (verificado por ArchUnit).
* **Tablas de BD:** ninguna nueva. Reutiliza `billing_payments` (con
  `target_modality = 'PHYSICAL'`) y el flujo de `physical_capacity_assignments` ya existente.
  Cero filas `physical_capacity_holds` para esta modalidad — nunca se reserva nada.

---

## 5. Decisiones de implementación (#36)

Estas decisiones se tomaron con el usuario durante el diseño de #36 y no se reabren. Documentadas
también en `openspec/changes/billing-physical-transfer-capacity-exception/proposal.md` (D1–D7).

* **D1 — Sin `CapacityHold`, diverge del texto literal del issue.** El issue original describe
  "el hold expiró" y el DoD original pedía "pruebas de hold vencido". La implementación final
  **nunca crea un hold** para esta modalidad: la capacidad se calcula recién en la aprobación
  manual, a partir de `confirmedAt`, a través del camino `HoldNotFound` que `#208` ya dejó
  construido para el residual sin hold. El equivalente probado es "capacidad no disponible al
  momento de la aprobación" (Escenario 3 arriba), no un hold que venció. Misma clase de manejo que
  el D3 de #33, el D1 de #44, el D8 de #45 y el D7 de #208. Razón: el D6 ya cerrado de #208 rechazó
  explícitamente un hold de varias horas/días para liquidación asíncrona por efectivo/transferencia
  como un costo permanente e injustificado para otros compradores.
* **D6 — Prefijo de ruta administrativa, hereda el ya shippeado.** El issue original pedía
  `/api/v1/billing/admin/payments/...`. La implementación hereda el prefijo ya shippeado por #31/#33,
  **`/api/v1/admin/billing/payments/...`** — la regla existente `/api/v1/admin/**` de
  `SecurityConfig` ya lo cubre, sin matcher nuevo.
* **D2 — Sin endpoint nuevo.** `RoutingCreatePhysicalPurchaseCheckoutUseCase` enruta por
  `paymentMethod` delante del `POST /api/v1/billing/physical/purchases` ya existente, replicando el
  patrón que `RoutingCreateSubscriptionCheckoutUseCase` ya usa en
  `POST /api/v1/billing/subscriptions` (#31).
* **D4 — Cupo de creación compartido.** `BankTransferRateLimitPort.consumeSubscriptionCreation` se
  renombró a `consumeBankTransferCreation`: un único cupo de 10 creaciones/usuario/día que ahora
  cubre **ambos** productos (suscripciones y compras presenciales), no uno independiente por
  endpoint (Escenario 6 arriba). `consumeProofUpload` queda sin cambios.
* **D5 — `PaymentFulfillmentService.release()` sigue siendo un no-op para `Physical`.** Correcto
  por diseño bajo D1: si nunca se reservó nada, no hay nada que liberar al rechazar.
* **D7 — La verificación de disponibilidad en la creación es best-effort, no vinculante.** El `409`
  de esta ruta para `BANK_TRANSFER` es una cortesía de lectura, a diferencia de la garantía
  respaldada por hold que sí tiene la rama Mercado Pago de este mismo endpoint (#208). Puede dejar
  pasar una solicitud que termine en `EXCEPTION` en la aprobación.
* **Hallazgo no anticipado en diseño — `providerPaymentId` ausente en la publicación del evento.**
  `PublishPhysicalPaymentCompletedUseCase` exigía `providerPaymentId` no nulo para publicar
  `billing.PhysicalPaymentCompleted`, una invariante válida mientras sólo Mercado Pago podía
  completar un `Payment` físico (el webhook siempre lo ataba antes de `Completed`). La aprobación
  manual de este cambio es la primera vía que alcanza `Completed` sin proveedor — se relajó la
  invariante (en `PublishPhysicalPaymentCompletedUseCase` y en
  `PaymentCompletedOutboxPayload`, `api:shared`) para aceptar `null` explícitamente en ese caso; el
  consumidor (`PhysicalCapacityAssignmentOutboxEventHandler`) nunca leía ese campo.

---

## 6. Definition of Done (Criterios de Finalización)

* [x] La lógica implementa todos los Criterios de Aceptación (Escenarios 1–6).
* [x] Se han escrito **Pruebas Unitarias** para el caso de uso de creación por transferencia (7
      escenarios: presupuesto, replay, cotización vencida, cupo no disponible, `Payment` creado
      correctamente, referencia distinta de la rama Mercado Pago, orden de colaboradores) y para el
      router que despacha por `paymentMethod`.
* [x] Se han escrito **Pruebas de Integración** (Testcontainers MySQL) para: creación con
      instrucciones de transferencia sin hold ni llamada a proveedor; la rama Mercado Pago sigue
      creando un hold exactamente como antes; cotización vencida (`410`); cupo visible-lleno
      (`409`, no vinculante); aprobación con capacidad disponible (`ASSIGNED`); aprobación sin
      capacidad disponible (`EXCEPTION`); rechazo sin liberar nada; expiración a las 72h sin
      comprobante.
* [x] Se ha escrito una **Prueba de Integración con Redis real** (Testcontainers) que prueba el
      cupo compartido de creación (D4): diez creaciones mezclando ambos productos agotan el cupo,
      y la 11ª — de cualquiera de los dos — responde `429`.
* [x] El endpoint está documentado en el contrato **OpenAPI/Swagger** (`api/openapi/billing-v1.yaml`):
      `paymentMethod: BANK_TRANSFER`, `bankTransferInstructions` y `429` agregados al checkout
      presencial.
* [x] El código pasa la validación de Checkstyle y ArchUnit (regla ampliada a las dos clases
      nuevas, sin nueva dependencia de `com.menta.physical..`).
* [x] `ArchitectureTest` confirma que el nuevo caso de uso y el router no dependen de
      `com.menta.physical..`.
* [x] Se verificó, con una prueba de caracterización, que el barrido de expiración de 72h no
      necesitaba cambios de producción para cubrir esta modalidad — el hallazgo real fue en la
      publicación del evento de confirmación (ver "Hallazgo no anticipado" en la sección anterior).
