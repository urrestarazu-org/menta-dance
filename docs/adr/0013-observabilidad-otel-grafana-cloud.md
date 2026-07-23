# ADR-0013: Observabilidad con OpenTelemetry y Grafana/Loki

**Estado:** Aceptado

## Decisión

El MVP usa Logback con salida JSON estructurada, OpenTelemetry para trazas y
Grafana/Loki para centralización y consulta. BFF, API e integraciones propagan
un `correlationId`.

## Consecuencias

- Retención mínima de 90 días para errores, auditoría de pagos e incidentes de
  base de datos.
- Se redactan tokens, cookies, contraseñas, secretos y datos de tarjeta.
- Las conciliaciones administrativas usan `correlationId`, proveedor, inbox/outbox
  y auditoría como evidencia.
- No se incorpora RabbitMQ ni servicios por módulo para observabilidad.
