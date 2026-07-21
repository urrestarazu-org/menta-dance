# ADR-0023: Resilience4j para Integraciones Externas

**Estado:** Aceptado
**Fecha:** 2026-07-21
**Decisores:** Equipo de desarrollo

## Contexto y Problema

El sistema integra con servicios externos que pueden fallar o degradarse:

| Servicio | Uso | Criticidad |
|----------|-----|------------|
| **Mercado Pago** | Procesamiento de pagos | Alta |
| **Bunny.net CDN** | Streaming de videos | Alta |
| **Google Calendar** | Sincronización de horarios | Media |
| **Servidor SMTP** | Envío de emails | Media |

Estas integraciones necesitan patrones de resiliencia para:
- Evitar cascadas de fallos cuando un servicio externo no responde
- Proporcionar degradación graceful al usuario
- Reintentar operaciones transitorias de forma inteligente

> **Nota:** El ADR-0012 original (Circuit Breaking entre microservicios) fue
> reemplazado por ADR-0020 (monolito modular). Este ADR define el uso de
> Resilience4j específicamente para integraciones externas, no entre módulos.

## Factores Clave

* Bunny.net y Mercado Pago son críticos para el core del negocio
* Los timeouts de red pueden bloquear threads del servidor
* El usuario debe recibir feedback claro cuando un servicio externo falla
* El monolito no necesita circuit breakers internos (ADR-0020)

## Opciones Consideradas

### Opción 1: Resilience4j

* **Descripción:** Librería Java nativa con soporte para circuit breaker, retry,
  rate limiter, bulkhead y time limiter. Integración directa con Spring Boot.
* **Pros:**
  * Integración nativa con Spring Boot y Actuator
  * Decoradores declarativos (@CircuitBreaker, @Retry)
  * Métricas exportables a Micrometer/Prometheus
  * Configuración externalizada en YAML
* **Contras:**
  * Curva de aprendizaje inicial para configurar umbrales

### Opción 2: Spring Retry + Timeouts manuales

* **Descripción:** Usar @Retryable de Spring con timeouts en RestTemplate/WebClient.
* **Pros:**
  * Menos dependencias
  * Simple para casos básicos
* **Contras:**
  * Sin circuit breaker (no evita llamadas a servicios caídos)
  * Sin bulkhead (no aísla pools de conexiones)
  * Sin métricas integradas

### Opción 3: No implementar resiliencia

* **Descripción:** Confiar en timeouts default de HTTP.
* **Pros:**
  * Cero complejidad adicional
* **Contras:**
  * Fallos en cascada si Mercado Pago no responde
  * Mala UX (usuarios esperando indefinidamente)
  * Sin visibilidad de problemas

## Decisión

Elegimos **Resilience4j** porque proporciona todos los patrones de resiliencia
necesarios con integración nativa en Spring Boot y métricas observables.

## Justificación

1. **Circuit Breaker:** Evita llamar repetidamente a un servicio caído,
   permitiendo recuperación más rápida
2. **Retry:** Reintenta fallos transitorios (errores de red, 503) con backoff
3. **Time Limiter:** Garantiza que ninguna llamada externa bloquee más de N segundos
4. **Bulkhead:** Aísla el pool de conexiones de cada integración para que un
   servicio lento no agote recursos de otros

## Implementación

### Dependencia

```kotlin
// build.gradle.kts (api:shared o api:app)
implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
implementation("io.github.resilience4j:resilience4j-micrometer:2.2.0")
```

### Configuración por Servicio

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      mercadopago:
        registerHealthIndicator: true
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
      bunny:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
      googleCalendar:
        slidingWindowSize: 5
        failureRateThreshold: 60
        waitDurationInOpenState: 120s

  retry:
    instances:
      mercadopago:
        maxAttempts: 3
        waitDuration: 500ms
        retryExceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
      bunny:
        maxAttempts: 2
        waitDuration: 1s

  timelimiter:
    instances:
      mercadopago:
        timeoutDuration: 10s
      bunny:
        timeoutDuration: 5s
      googleCalendar:
        timeoutDuration: 15s
```

### Uso en Código

```java
@Service
@RequiredArgsConstructor
public class MercadoPagoAdapter implements PaymentGatewayPort {

    private final MercadoPagoClient client;

    @Override
    @CircuitBreaker(name = "mercadopago", fallbackMethod = "paymentFallback")
    @Retry(name = "mercadopago")
    @TimeLimiter(name = "mercadopago")
    public CompletableFuture<PaymentResult> processPayment(PaymentRequest request) {
        return CompletableFuture.supplyAsync(() -> client.createPayment(request));
    }

    private CompletableFuture<PaymentResult> paymentFallback(
            PaymentRequest request, Throwable t) {
        log.warn("MercadoPago unavailable, queuing for retry: {}", t.getMessage());
        return CompletableFuture.completedFuture(
            PaymentResult.deferred("Pago en procesamiento, recibirás confirmación por email")
        );
    }
}
```

### Métricas Expuestas

| Métrica | Descripción |
|---------|-------------|
| `resilience4j_circuitbreaker_state` | Estado actual (closed/open/half_open) |
| `resilience4j_circuitbreaker_failure_rate` | Tasa de fallos |
| `resilience4j_retry_calls_total` | Intentos de retry |
| `resilience4j_timelimiter_calls_total` | Llamadas con timeout |

## Consecuencias

### Positivas

* Fallos en servicios externos no bloquean el sistema
* Usuarios reciben feedback inmediato cuando hay problemas
* Métricas visibles en dashboard de observabilidad
* Recuperación automática cuando el servicio vuelve

### Negativas / Deuda Técnica

* Configuración inicial de umbrales requiere tuning basado en datos reales
* Tests de integración deben mockear estados del circuit breaker

### Riesgos y Reversibilidad

* **Riesgo Principal:** Umbrales mal configurados pueden abrir el circuit breaker
  innecesariamente
* **Plan de Mitigación:** Comenzar con umbrales conservadores, ajustar con métricas
* **Reversibilidad:** Alta — las anotaciones se pueden remover sin cambiar lógica

## Referencias y Decisiones Relacionadas

* [Resilience4j Documentation](https://resilience4j.readme.io/docs)
* Complementa a: [ADR-0020](./0020-modular-monolith.md) (monolito modular)
* Reemplaza concepto de: ADR-0012 (circuit breakers entre microservicios — obsoleto)
