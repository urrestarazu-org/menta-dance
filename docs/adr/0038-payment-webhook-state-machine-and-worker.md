# ADR-0038: Máquina de estados de `Payment`, worker de verificación y puerto al proveedor

**Fecha:** 2026-08-18
**Estado:** Aceptado

## Contexto

El issue #30 (US-BILLING-002, confirmación de pago por webhook de Mercado Pago) tiene su diseño de seguridad e idempotencia ya especificado en `docs/06-BILLING-API.md` ("Estados e idempotencia" e "Incidentes de base de datos"). Ese documento resuelve el **qué** (estados, transiciones válidas, verificación HMAC, deduplicación por inbox) pero no el **cómo** en términos de tipos Java concretos. El propio issue señala que, por ser una decisión de seguridad con impacto financiero directo, conviene evaluar promoverla a ADR antes de pasar a `In progress`.

`api:billing` recién tiene `Plan`/`PlanCourse` (#29); `Payment`, `Subscription` y `Purchase` no existen todavía — se modelan por primera vez acá. Quedan tres decisiones abiertas que bloquean escribir código sin reabrir diseño a mitad de implementación:

1. Cómo representar la máquina de estados de `Payment` de forma que una transición inválida sea **irrepresentable en el tipo**, no sólo rechazada en runtime (requisito explícito del issue).
2. Qué mecanismo dispara el worker asíncrono que verifica contra Mercado Pago y aplica la transición.
3. Qué forma tiene el puerto hacia Mercado Pago y cómo se relaciona con la política de resiliencia ya fijada en ADR-0023.

## Alternativas consideradas

### 1. Representación de la máquina de estados

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| **Sealed interface `PaymentStatus` + records por estado (elegida)** | El compilador exige exhaustividad en cada `switch`; un estado terminal simplemente no expone método de transición — la transición inválida no compila, no se rechaza en runtime | Requiere Java 21 (ya es el baseline, ADR-0024) y algo más de tipos que un enum plano |
| Enum único + guard clauses (`if (status.isTerminal()) throw ...`) | Simple, mínimo boilerplate | Exactamente lo que el issue pide evitar: la transición inválida es representable (cualquier `PaymentStatus` enum admite cualquier valor) y sólo se rechaza en runtime |
| State pattern OOP clásico (interfaz `PaymentState` con clases polimórficas con lógica) | Fuerza el mismo tipo de exhaustividad | Ceremonia adicional sin beneficio sobre sealed + pattern matching, que ya es idiomático en el baseline de Java del proyecto |

### 2. Mecanismo del worker asíncrono

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| **`@Scheduled` poller + worker por-fila con `REQUIRES_NEW` (elegida)** | Mismo patrón ya probado en producción (`OutboxBlacklistReconciler` + `OutboxReconciliationWorker` en `api:app/outbox`); sin componente nuevo (colas, brokers); cada fila falla de forma aislada sin abortar el batch | Polling, no push — latencia de hasta un ciclo del scheduler antes de procesar una fila nueva |
| Cola externa (RabbitMQ, SQS) | Push inmediato | Prohibido explícitamente por ADR-0023 ("No se usa RabbitMQ ni colas externas en el MVP") y por los límites arquitectónicos del propio issue #30 |
| Procesar sincrónicamente en el hilo del webhook | Sin componente nuevo | Prohibido explícitamente por el issue: "el controller HTTP hace lo mínimo indispensable... la verificación... corre en el worker asíncrono, nunca en el hilo de la request" |

### 3. Puerto hacia Mercado Pago

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| **Puerto `port.out` mínimo, una sola lectura idempotente (elegida)** | Mismo estilo que `CourseCatalogPort` ya existente en `billing`; expone sólo lo que el worker necesita | Ninguna relevante — es el único uso real hoy |
| Cliente SDK de Mercado Pago inyectado directamente en el caso de uso | Menos código de indirección | Viola Clean Architecture: `application` dependería de un detalle de infraestructura externa |

## Decisión

### 1. `PaymentStatus` como jerarquía sellada

```java
public sealed interface PaymentStatus {
    sealed interface Pending extends PaymentStatus
        permits AwaitingProvider, AwaitingManualVerification, ReconciliationRequired {}
    record AwaitingProvider() implements Pending {}
    record AwaitingManualVerification() implements Pending {}
    record ReconciliationRequired(String reason) implements Pending {}

    record Completed(Instant confirmedAt) implements PaymentStatus {}
    record Rejected(Instant rejectedAt) implements PaymentStatus {}
    record Cancelled(Instant cancelledAt) implements PaymentStatus {}
    record Expired(Instant expiredAt) implements PaymentStatus {}
}
```

Los cuatro últimos son terminales: no implementan ningún método de transición, así que aplicarles un resultado de proveedor no compila desde ningún caller que ya sepa que tiene, por ejemplo, un `Completed`. La monotonicidad frente a un webhook tardío o duplicado —que sí puede llegar dirigido a un `Payment` cuyo tipo estático el worker sólo conoce como `PaymentStatus` genérico— se aplica en el único punto de entrada del agregado:

```java
public final class Payment {
    public Payment applyProviderOutcome(ProviderOutcome outcome) {
        return switch (status) {
            case Pending pending -> withStatus(pending.resolve(outcome));
            case Completed c -> this;   // no-op: ya liquidado, nunca se retrocede
            case Rejected r -> this;
            case Cancelled c -> this;
            case Expired e -> this;
        };
    }
}
```

Ese `switch` exhaustivo (sellado — el compilador exige los cinco brazos) es la única guarda de monotonicidad en runtime; todo lo demás lo impide el sistema de tipos. Un webhook tardío/duplicado no lanza excepción: es un no-op silencioso (loggeado en el caller), porque el propio doc de diseño lo trata como caso esperado, no como error.

### 2. `WebhookInboxReconciler` — mismo patrón que el outbox, ubicado en `api:billing`

A diferencia del outbox de `auth` (que vive en `api:app` porque despacha eventos entre módulos), la verificación de pagos es interna a `billing` — no compone puertos de otros módulos. Vive en `api:billing/infrastructure/webhook/`:

- `WebhookInboxReconciler` — `@Scheduled(fixedRateString = "${billing.webhook.reconcile-rate-ms:5000}")`, selecciona un batch de `billing_webhook_inbox` con `status = RECEIVED` o (`status = RETRY_PENDING` y `next_attempt_at <= now`).
- `WebhookVerificationWorker.process(row)` — `@Transactional(propagation = REQUIRES_NEW)` por fila, igual que `OutboxReconciliationWorker.process`: una fila que falla no aborta el batch; actualiza `attempt_count`/`next_attempt_at`/`status` según corresponda.

Aplicar el efecto de negocio (habilitar acceso, asignar cupo) sigue el mismo mandato del issue: vía puertos/eventos internos, nunca HTTP ni RabbitMQ.

### 3. `PaymentProviderPort`

```java
public interface PaymentProviderPort {
    ProviderPaymentResult fetchPayment(String providerPaymentId);
}
```

`ProviderPaymentResult` es un record con lo mínimo que el worker necesita para verificar: estado crudo del proveedor, importe, moneda, referencia externa y cuenta/merchant — nunca el payload completo de Mercado Pago cruzando la frontera de `application`. La implementación real (`infrastructure/provider/mercadopago/MercadoPagoPaymentProviderAdapter`) usa Resilience4j exclusivamente para **esta llamada de lectura**, con timeout y circuit breaker; los reintentos ante fallo NO los da Resilience4j sino el propio ciclo del `WebhookInboxReconciler` (la fila vuelve a `RETRY_PENDING` con backoff) — evita apilar dos mecanismos de reintento independientes sobre la misma llamada. Esto es exactamente lo que permite ADR-0023: "los reintentos sólo son admisibles para lecturas idempotentes o para el worker de inbox/outbox ya persistido" — `fetchPayment` es ambas cosas a la vez.

## Justificación

Las tres decisiones comparten un mismo criterio: reusar lo que el proyecto ya probó en producción (outbox de `auth`, `CourseCatalogPort` de `billing`, política de Resilience4j de ADR-0023) en vez de introducir un mecanismo nuevo para un problema que ya tiene solución conocida acá. La única pieza genuinamente nueva es la jerarquía sellada de `PaymentStatus`, exigida explícitamente por el issue y habilitada por el baseline de Java 21 (ADR-0024) — Menta Dance no tenía hasta ahora una máquina de estados con este nivel de criticidad financiera, así que es la primera vez que se justifica pagar el costo de tipos extra a cambio de que una transición inválida no compile.

## Consecuencias

### Positivas

- Cero mecanismos nuevos de infraestructura (sin colas, sin scheduler distinto al ya usado).
- Una transición de estado inválida es un error de compilación, no un bug que aparece en producción.
- El patrón de reintento de `fetchPayment` no duplica lógica de backoff entre Resilience4j y el inbox.

### Negativas

- La jerarquía sellada obliga a tocar el `switch` de `Payment.applyProviderOutcome` cada vez que se agregue un estado nuevo — es intencional (el compilador lo exige), pero es más fricción que un enum.
- El polling del `WebhookInboxReconciler` introduce hasta un ciclo (`reconcile-rate-ms`) de latencia entre la persistencia del inbox y la verificación efectiva — aceptable porque el webhook ya respondió `200 OK` antes de eso.

### Riesgos y Reversibilidad

- **Riesgo principal:** ninguno crítico nuevo — la verificación contra el proveedor sigue siendo obligatoria antes de aplicar cualquier efecto, independientemente de estos detalles de implementación.
- **Reversibilidad:** alta para el mecanismo del worker (mismo patrón ya reversible que el outbox). Media para la jerarquía sellada: migrar a otra representación más adelante requiere tocar todos los call sites que hacen `switch` sobre `PaymentStatus`, pero el compilador los señala a todos — no hay forma de dejar uno desactualizado sin que falle el build.

## Referencias

- [06-BILLING-API.md](../06-BILLING-API.md) — "Estados e idempotencia" e "Incidentes de base de datos", diseño ya especificado que este ADR formaliza en tipos concretos.
- [ADR-0023](0023-resilience4j-external-integrations.md) — política de resiliencia para integraciones externas, gobierna `PaymentProviderPort`.
- [ADR-0024](0024-technology-baseline.md) — baseline Java 21, habilita sealed interfaces + pattern matching.
- `api/app/src/main/java/com/menta/app/outbox/OutboxReconciliationWorker.java` y `OutboxBlacklistReconciler.java` — patrón de scheduler + worker por-fila ya en producción, mirror para `WebhookInboxReconciler`.
- `api/billing/src/main/java/com/menta/billing/application/port/out/CourseCatalogPort.java` — estilo de puerto `port.out` minimal ya establecido en `billing`.
- Issue #30 (US-BILLING-002).
