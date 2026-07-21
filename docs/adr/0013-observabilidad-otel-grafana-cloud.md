# ADR-0013: Observabilidad Distribuida con OpenTelemetry y Grafana Cloud

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

OpenTelemetry y Grafana Cloud continúan aceptados para observar el BFF, el único JAR API y las integraciones externas. Las métricas y alertas históricas de RabbitMQ, circuit breakers y aplicaciones por dominio no son vigentes. La correlación BFF→API por HTTP sí permanece dentro del alcance. Ver [ADR-0020](./0020-modular-monolith.md).


## Contexto y Problema

Con 5 aplicaciones Spring Boot ejecutándose (Auth API, Virtual API, Physical API, Billing API, BFF), rastrear un error que involucra peticiones cruzadas es muy difícil. Necesitamos trazabilidad distribuida para entender el flujo completo de una petición desde el navegador hasta las APIs backend. Sin embargo, estamos limitados a 8GB de RAM, por lo que no podemos hospedar herramientas locales pesadas de observabilidad.

## Factores Clave (Decision Drivers)

* Capacidad de rastrear peticiones a través de múltiples servicios (distributed tracing).
* Correlación de logs entre servicios.
* Bajo consumo de recursos locales (servidor de 8GB RAM).
* Costo accesible para el MVP (idealmente tier gratuito).
* Integración nativa con Spring Boot 3 y Micrometer.

## Opciones Consideradas

### Opción 1: Stack Local (Jaeger + ELK + Prometheus)

* **Descripción:** Desplegar Jaeger para tracing, Elasticsearch + Logstash + Kibana para logs, y Prometheus + Grafana para métricas.
* **Pros:**
  * Control total sobre los datos.
  * Sin dependencias de servicios externos.
* **Contras:**
  * Consumo de memoria prohibitivo (2-4GB mínimo) para el entorno MVP.
  * Complejidad operativa alta.

### Opción 2: Zipkin In-Memory Local

* **Descripción:** Desplegar Zipkin con almacenamiento in-memory.
* **Pros:**
  * Menor consumo que el stack completo.
  * Fácil de desplegar.
* **Contras:**
  * Los datos de trazas se pierden al reiniciar.
  * Sin capacidad de logs centralizados.

### Opción 3: OpenTelemetry + SaaS (Grafana Cloud)

* **Descripción:** Instrumentar las aplicaciones con OpenTelemetry (OTel) y enviar trazas, métricas y logs directamente a Grafana Cloud usando su tier gratuito.
* **Pros:**
  * Cero consumo de RAM local para almacenamiento.
  * Dashboards profesionales listos para usar.
  * Estandarización con OTel (vendor-neutral).
  * Tier gratuito generoso (50GB logs, 10K series métricas).
* **Contras:**
  * Dependencia de servicio SaaS.
  * Requiere conexión de salida a internet.

## Decisión

Elegimos **Opción 3: OpenTelemetry + Grafana Cloud** porque **brinda capacidades de observabilidad de nivel corporativo sin penalizar la memoria de nuestro servidor, usando el tier gratuito de Grafana Cloud**.

## Justificación (Rationale)

Esta solución nos brinda capacidades de observabilidad de nivel corporativo (gráficos de latencia de red, mapas de dependencias de servicios) sin penalizar la memoria de nuestro servidor. El agente OpenTelemetry en Spring Boot (vía Micrometer) es eficiente en recursos, e inyecta los `TraceId` (Correlation ID) en los logs automáticamente para trazabilidad básica, mientras envía los datos detallados a la nube para su análisis visual.

## Especificaciones Técnicas

### Dependencias

```gradle
// build.gradle
implementation 'io.micrometer:micrometer-tracing-bridge-otel'
implementation 'io.opentelemetry:opentelemetry-exporter-otlp'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

### Configuración OpenTelemetry

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling para MVP (bajo tráfico)
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:https://otlp-gateway-prod-us-central-0.grafana.net/otlp}
    metrics:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:https://otlp-gateway-prod-us-central-0.grafana.net/otlp}
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics

otel:
  service:
    name: ${spring.application.name}
  resource:
    attributes:
      service.namespace: menta
      deployment.environment: ${ENVIRONMENT:development}
```

### Variables de Entorno

```yaml
# docker-compose.yml
services:
  auth-api:
    environment:
      - OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp-gateway-prod-us-central-0.grafana.net/otlp
      - OTEL_EXPORTER_OTLP_HEADERS=Authorization=Basic ${GRAFANA_OTEL_TOKEN}
      - SPRING_APPLICATION_NAME=auth-api
      - ENVIRONMENT=production
```

### Métricas a Capturar

| Categoría | Métrica | Descripción |
|-----------|---------|-------------|
| **JVM** | `jvm.memory.used` | Uso de heap |
| **JVM** | `jvm.gc.pause` | Pausas de GC |
| **HTTP** | `http.server.requests` | Latencia y conteo de requests |
| **HTTP** | `http.server.requests.active` | Requests en vuelo |
| **Circuit Breaker** | `resilience4j.circuitbreaker.state` | Estado del CB |
| **Circuit Breaker** | `resilience4j.circuitbreaker.failure.rate` | Tasa de fallos |
| **Database** | `hikaricp.connections.active` | Conexiones activas a BD |
| **RabbitMQ** | `rabbitmq.consumed` | Mensajes consumidos |
| **RabbitMQ** | `rabbitmq.published` | Mensajes publicados |
| **Custom** | `subscription.created` | Suscripciones creadas |
| **Custom** | `payment.completed` | Pagos completados |

### Logs Correlacionados

```yaml
# logback-spring.xml
<pattern>
  %d{ISO8601} [%thread] %-5level %logger{36} - [traceId=%X{traceId}, spanId=%X{spanId}] %msg%n
</pattern>
```

**Ejemplo de log correlacionado:**

```
2026-05-17T10:30:00.123 [http-nio-8080-exec-1] INFO  c.m.d.s.CourseService - [traceId=abc123, spanId=def456] Fetching course for user xvb5D1e0
```

### Dashboard Predefinidos

| Dashboard | Métricas |
|-----------|----------|
| **Service Overview** | RPS, latencia p50/p95/p99, error rate |
| **JVM Health** | Heap usage, GC pauses, thread count |
| **Database** | Query time, connection pool, slow queries |
| **Circuit Breakers** | State transitions, failure rates |
| **Business Metrics** | Subscriptions, payments, enrollments |

### Alertas Recomendadas

```yaml
# Grafana alerting rules
groups:
  - name: menta-alerts
    rules:
      - alert: HighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected (>5%)"

      - alert: HighLatency
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P95 latency above 2 seconds"

      - alert: CircuitBreakerOpen
        expr: resilience4j_circuitbreaker_state{state="open"} == 1
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker {{ $labels.name }} is OPEN"
```

## Consecuencias

### Positivas

* Cero impacto en la RAM del servidor host para la persistencia de logs/trazas.
* Paneles visuales avanzados listos para usar.
* Estandarización total usando OpenTelemetry (facilita migración futura a otro vendor).
* Correlación automática de logs con traceId.

### Negativas / Deuda Técnica

* Dependencia de un servicio de terceros (SaaS).
* Los datos de observabilidad están fuera del control del equipo.

### Implicaciones de Costos

* **Tier gratuito de Grafana Cloud:**
  * 50 GB logs/mes
  * 10,000 series de métricas
  * 50 GB trazas/mes
* Para el volumen del MVP (1.67 RPS), esto es más que suficiente.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Si Grafana Cloud tiene una interrupción, perdemos visibilidad temporal del sistema.
* **Plan de Mitigación:** Los logs locales de Docker siguen disponibles como fallback. Alertas críticas pueden configurarse también en el servidor local.
* **Reversibilidad:** Alta. OpenTelemetry es vendor-neutral. Cambiar a Datadog, New Relic o un stack local solo requiere cambiar el endpoint de exportación.

## Referencias y Decisiones Relacionadas

* **Complementa a:** [ADR-0009](./0009-restricciones-infraestructura-mvp.md) - Restricciones de memoria del servidor
* **Topología vigente:** [ADR-0020](./0020-modular-monolith.md) - API modular y BFF
* **Correlación vigente:** BFF → API e integraciones externas por HTTP
* **OpenTelemetry Documentation:** <https://opentelemetry.io/docs/>
* **Grafana Cloud:** <https://grafana.com/products/cloud/>
* **Micrometer Tracing:** <https://micrometer.io/docs/tracing>
