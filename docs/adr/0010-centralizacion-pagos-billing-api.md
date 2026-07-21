# ADR-0010: Centralización de Pagos (Billing API) vs Pagos Descentralizados

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura / Negocio

## Vigencia actual

La centralización financiera continúa aceptada como **ownership del módulo Billing** dentro del único JAR API. Billing posee planes, combos, precios, suscripciones, pagos y auditoría. Virtual y Physical consultan sus capacidades mediante interfaces Java; no mantienen réplicas por eventos ni usan HTTP/RabbitMQ internamente. El BFF presenta información y llama a la API por HTTP, pero no calcula precios ni descuentos. La topología histórica fue reemplazada por [ADR-0020](./0020-modular-monolith.md).


## Contexto y Problema

El proyecto Menta se ha dividido en dos dominios principales de negocio: Virtual (online) y Physical (presencial). Inicialmente, se planeó que cada API manejara sus propios pagos (la `Virtual API` manejando suscripciones a videos y la `Physical API` manejando cobros de clases presenciales).

Sin embargo, el equipo de negocio requiere un sistema donde todos los planes, costos y auditoría de pagos (Mercado Pago, Efectivo, Transferencia) estén centralizados y fuertemente controlados. ¿Debemos mantener los pagos descentralizados en cada dominio o crear un sistema centralizado de cobros desde el Día 1?

## Factores Clave (Decision Drivers)

* Consolidación de la auditoría financiera (un solo punto de verdad para todos los ingresos).
* Capacidad futura para ofrecer "paquetes híbridos" combinados (Ej. Suscripción Virtual + Pase Libre Físico en un solo pago).
* Reutilización de la integración con Mercado Pago y comprobantes manuales.
* **Hardware de 8GB RAM confirmado:** Con la garantía del servidor de 8GB RAM, es técnicamente viable desplegar un quinto microservicio (`Billing API`) sin comprometer la estabilidad del sistema.

## Opciones Consideradas

### Opción 1: Pagos Descentralizados (Virtual API y Physical API autónomos)

* **Descripción:** Virtual API procesa cobros virtuales, Physical API procesa cobros físicos. El BFF es quien une y consolida visualmente el historial de pagos para la UI.
* **Pros:** Evita la creación de un nuevo microservicio. No requiere sincronización de pagos entre APIs. No precisa de Event Sourcing ni RabbitMQ estricto para el MVP.
* **Contras:** La lógica de Mercado Pago y auditorías de transferencias bancarias tiene que ser programada dos veces (o extraída a una librería compartida y mantenida en paralelo). Dificulta la venta cruzada (paquetes) en un solo flujo de "carrito de compras".

### Opción 2: Billing API (Servicio Centralizado de Pagos)

* **Descripción:** Crear un microservicio `Billing API` encargado exclusivamente de gestionar los Planes, Suscripciones, Pagos, Webhooks de Mercado Pago y Notificaciones Financieras.
* **Pros:** Punto único de verdad financiero (Clean Architecture). Reutilización completa de la pasarela de pagos. Escalabilidad garantizada para futuros modelos híbridos. Auditoría 100% confiable y centralizada.
* **Contras:** Aumenta la complejidad del sistema (un microservicio extra y un esquema BD adicional). Requiere usar eventos asíncronos (RabbitMQ) para informar a la Virtual API y Physical API cuando un usuario ya pagó y tiene acceso habilitado.

## Decisión

Elegimos la **Opción 2: Billing API (Servicio Centralizado de Pagos) desde el Día 1**.

## Justificación (Rationale)

A nivel de negocio, la trazabilidad del dinero es el dominio más crítico. Tener un solo servicio `Billing API` facilita la auditoría (que es un requerimiento explícito del proyecto) y previene bugs financieros que podrían surgir por duplicar lógica de cobro. Dado que contamos con 8GB de RAM confirmados, la infraestructura puede soportarlo sin riesgo de colapso de memoria.

## Especificaciones Técnicas

### Entidades de Billing API

| Entidad | Descripción |
|---------|-------------|
| `Plan` | Definición de planes (Virtual, Physical, Híbrido) |
| `Subscription` | Suscripción activa de un usuario a un plan |
| `Payment` | Registro de cada pago (status, método, monto) |
| `Invoice` | Factura/comprobante generado |
| `MercadoPagoRedirection` | Tracking de redirecciones desde MP |

### Integración con Mercado Pago

#### Webhooks

```
POST /api/v1/billing/webhooks/mercadopago
```

**Idempotencia de Webhooks:**
Mercado Pago puede enviar el mismo webhook múltiples veces. Para evitar procesamiento duplicado:

```java
@PostMapping("/webhooks/mercadopago")
public ResponseEntity<Void> handleWebhook(
    @RequestBody MercadoPagoWebhookDto webhook,
    @RequestHeader("X-Request-Id") String requestId
) {
    // 1. Verificar si ya procesamos este webhook
    if (webhookIdempotencyService.isProcessed(webhook.getId())) {
        log.info("Webhook {} ya procesado, retornando OK", webhook.getId());
        return ResponseEntity.ok().build();
    }

    // 2. Procesar el webhook
    paymentService.processWebhook(webhook);

    // 3. Marcar como procesado (TTL 7 días)
    webhookIdempotencyService.markProcessed(webhook.getId(), Duration.ofDays(7));

    return ResponseEntity.ok().build();
}
```

#### Idempotencia de Pagos

Para evitar cobros duplicados en caso de reintentos:

```java
@Transactional
public Payment createPayment(CreatePaymentRequest request) {
    // 1. Verificar idempotency key
    String idempotencyKey = request.getIdempotencyKey();
    Optional<Payment> existing = paymentRepository
        .findByIdempotencyKey(idempotencyKey);

    if (existing.isPresent()) {
        log.info("Pago con idempotencyKey {} ya existe", idempotencyKey);
        return existing.get();
    }

    // 2. Crear el pago
    Payment payment = new Payment();
    payment.setIdempotencyKey(idempotencyKey);
    payment.setUserId(request.getUserId());
    payment.setAmount(request.getAmount());
    payment.setStatus(PaymentStatus.PENDING);

    return paymentRepository.save(payment);
}
```

**Tabla de idempotency:**

```sql
ALTER TABLE payments
ADD COLUMN idempotency_key VARCHAR(64) UNIQUE;

CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
```

### Comunicación con Otras APIs

```
┌─────────────────────────────────────────────────────────────────┐
│                         Billing API                              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │    Plans     │  │ Subscriptions│  │      Payments        │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Eventos RabbitMQ
                              ▼
        ┌─────────────────────────────────────────┐
        │                                         │
        ▼                                         ▼
┌───────────────┐                        ┌───────────────┐
│  Virtual API  │                        │ Physical API  │
│               │                        │               │
│ has_active_   │                        │ has_active_   │
│ subscription  │                        │ subscription  │
└───────────────┘                        └───────────────┘
```

### Eventos Publicados por Billing API

| Evento | Routing Key | Consumidores |
|--------|-------------|--------------|
| `PaymentCompletedEvent` | `billing.payment.completed` | Virtual, Physical |
| `PaymentFailedEvent` | `billing.payment.failed` | Virtual |
| `SubscriptionCreatedEvent` | `billing.subscription.created` | Virtual, Physical |
| `SubscriptionExpiredEvent` | `billing.subscription.expired` | Virtual, Physical |
| `SubscriptionCancelledEvent` | `billing.subscription.cancelled` | Virtual, Physical |

### Estado Local en APIs Consumidoras

Virtual API y Physical API mantienen un estado local simplificado:

```java
// En Virtual API
@Entity
public class UserAccess {
    @Id
    private String userId;
    private boolean hasActiveSubscription;
    private LocalDateTime subscriptionExpiresAt;
    private String subscriptionPlanType; // VIRTUAL, HYBRID
}
```

Esto permite:

1. **Tolerancia a fallos:** Si Billing API está caído, los usuarios pueden seguir accediendo.
2. **Baja latencia:** No hay llamada HTTP síncrona para verificar acceso.
3. **Consistencia eventual:** Se actualiza vía eventos de RabbitMQ.

## Consecuencias

### Positivas

* Código financiero limpio, DRY (Don't Repeat Yourself) y fácilmente auditable por administradores.
* Un solo esquema de base de datos (`billing_schema`) para resguardar toda la información de transacciones y facturación de la academia completa (Virtual y Presencial).
* Facilita enormemente el lanzamiento de estrategias comerciales como combos/paquetes multicanal.
* Idempotencia garantizada en pagos y webhooks.

### Negativas / Deuda Técnica

* Incrementa el esfuerzo de configuración del MVP al requerir 5 APIs en lugar de 4.
* Obliga a implementar la infraestructura de RabbitMQ desde el MVP para mantener la consistencia eventual, exigiendo monitoreo de las Dead Letter Queues (DLQ).

### Implicaciones de Costos

* ~512MB RAM adicional para el servicio Billing API (ya contemplado en ADR-0009).
* Sin costos de infraestructura adicionales.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Si Billing API falla y los eventos no se procesan, los usuarios podrían perder acceso temporalmente aunque hayan pagado.
* **Plan de Mitigación:**
  1. Estado local en Virtual/Physical API permite acceso basado en caché.
  2. DLQ para eventos fallidos con reprocesamiento manual.
  3. Monitoreo de cola de eventos en Grafana.
* **Reversibilidad:** Media. Consolidar pagos nuevamente en las APIs de dominio requeriría migrar datos y duplicar lógica.

## Referencias y Decisiones Relacionadas

* **Topología reemplazada por:** [ADR-0020](./0020-modular-monolith.md) - Billing es un módulo del JAR API
* **Complementa a:** [ADR-0004](./0004-schemas-separados-vs-db-separadas.md) - billing_schema como cuarto esquema
* **Comunicación vigente:** puertos Java entre módulos según [ADR-0020](./0020-modular-monolith.md)
* **Complementa a:** [ADR-0009](./0009-restricciones-infraestructura-mvp.md) - Memoria asignada a Billing API
* **Mercado Pago Webhooks:** <https://www.mercadopago.com.ar/developers/es/docs/your-integrations/notifications/webhooks>
