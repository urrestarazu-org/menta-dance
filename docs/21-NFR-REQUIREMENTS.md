# Requisitos No Funcionales (NFR)

[← Volver al índice](./README.md) | [← Historias de Usuario](./user-stories/README.md) | [Modelo de Datos →](./22-DATA-MODEL.md)

---

## 1. Introducción

Este documento especifica los requisitos no funcionales del sistema, definiendo atributos de calidad medibles y verificables.

---

## 2. Categorías de NFR

| Categoría | Código | Descripción |
|-----------|--------|-------------|
| Rendimiento | NFR-P | Tiempos de respuesta, throughput |
| Disponibilidad | NFR-A | Uptime, recuperación |
| Escalabilidad | NFR-S | Capacidad de crecimiento |
| Seguridad | NFR-SEC | Protección de datos y acceso |
| Usabilidad | NFR-U | Experiencia de usuario |
| Mantenibilidad | NFR-M | Facilidad de cambios |
| Compatibilidad | NFR-C | Integración con otros sistemas |

---

## 3. Requisitos de Rendimiento (NFR-P)

### NFR-P01: Tiempo de Respuesta de API

| Métrica | Valor MVP | Valor Producción |
|---------|-----------|------------------|
| p50 (mediana) | < 300ms | < 200ms |
| p95 | < 1s | < 500ms |
| p99 | < 2s | < 1s |
| Máximo | < 5s | < 3s |

**Medición:** Spring Boot Actuator `/actuator/metrics`

**Exclusiones:** Operaciones de video streaming (medidas separadamente)

---

### NFR-P02: Throughput

| Métrica | Valor MVP | Valor Producción |
|---------|-----------|------------------|
| Requests/segundo | 2 RPS | 10 RPS |
| Usuarios concurrentes | 50 | 200 |
| Transacciones/hora | 100 | 500 |

**Medición:** Gatling load tests

---

### NFR-P03: Tiempo de Carga de Páginas

| Página | Tiempo Máximo | Métrica |
|--------|---------------|---------|
| Landing page | 3s | First Contentful Paint |
| Catálogo de cursos | 2s | Time to Interactive |
| Reproductor de video | 4s | Time to First Frame |
| Dashboard usuario | 2s | Time to Interactive |

**Medición:** Lighthouse, Web Vitals

---

### NFR-P04: Rendimiento de Base de Datos

| Métrica | Valor Objetivo |
|---------|----------------|
| Queries simples (SELECT by PK) | < 10ms |
| Queries con JOIN | < 100ms |
| Queries de reporte | < 5s |
| Conexiones máximas | 100 |

**Medición:** MySQL slow query log, EXPLAIN

---

## 4. Requisitos de Disponibilidad (NFR-A)

### NFR-A01: Uptime

| Nivel | Uptime | Downtime/Mes |
|-------|--------|--------------|
| MVP | 99.0% | ~7.3 horas |
| Producción | 99.5% | ~3.6 horas |
| Futuro (HA) | 99.9% | ~43 minutos |

**Exclusiones:** Mantenimiento planificado (ventana dominical 3-5 AM)

---

### NFR-A02: Recuperación (RTO/RPO)

| Escenario | RTO | RPO |
|-----------|-----|-----|
| Fallo de aplicación | 15 min | 0 |
| Fallo de base de datos | 30 min | 1 hora |
| Fallo de servidor | 2 horas | 1 hora |
| Desastre completo | 4 horas | 24 horas |

**Estrategia:** Backups automáticos, scripts de restore probados

---

### NFR-A03: Degradación Graceful

El sistema debe continuar funcionando parcialmente ante fallos:

| Dependencia Externa | Comportamiento Esperado |
|---------------------|-------------------------|
| Mercado Pago | Pagos no disponibles, resto funciona |
| Bunny.net CDN | Videos no reproducen, metadata visible |
| Google Calendar | Sincronización de horarios degradada |
| SMTP | Emails encolados, notificación diferida |
| Redis | Blacklist falla open*, rate limiting laxo |

*Falla open con alerta inmediata; no bloquea usuarios legítimos.

> Ver [ADR-0023](./adr/0023-resilience4j-external-integrations.md) para patrones de resiliencia.

---

## 5. Requisitos de Escalabilidad (NFR-S)

### NFR-S01: Escalabilidad Vertical

| Recurso | Mínimo | Recomendado | Máximo |
|---------|--------|-------------|--------|
| CPU | 2 vCPU | 4 vCPU | 8 vCPU |
| RAM | 4 GB | 8 GB | 16 GB |
| Disco | 20 GB | 30 GB | 100 GB |

---

### NFR-S02: Escalabilidad Horizontal

> Arquitectura actual: **monolito modular** ([ADR-0020](./adr/0020-modular-monolith.md)).
> Los módulos Auth, Billing, Virtual, Physical están en un único JAR.

| Componente | Instancias MVP | Instancias Máx |
|------------|----------------|----------------|
| API (`:api:app`) | 1 | 3 |
| BFF (`:bff`) | 1 | 2 |

**Prerequisito para escalar:** Load balancer (NGINX), JWT stateless, Redis compartido

---

### NFR-S03: Umbrales de Escalamiento

| Métrica | Umbral | Acción |
|---------|--------|--------|
| CPU > 70% sostenido (15 min) | Escalar | Agregar instancia o upgrade |
| Memoria > 80% | Escalar | Upgrade de plan |
| Usuarios > 500 | Evaluar | Revisar arquitectura |
| Usuarios > 1000 | Migrar | Considerar HA |

---

## 6. Requisitos de Seguridad (NFR-SEC)

### NFR-SEC01: Autenticación

| Requisito | Especificación |
|-----------|----------------|
| Algoritmo de hash | BCrypt (cost factor 10) |
| Longitud mínima contraseña | 8 caracteres |
| Complejidad contraseña | 1 mayúscula, 1 número |
| Intentos de login | 5 antes de bloqueo |
| Duración bloqueo | 30 minutos |
| Access Token TTL | 15 minutos |
| Refresh Token TTL | 7 días |

---

### NFR-SEC02: Autorización

| Requisito | Especificación |
|-----------|----------------|
| Modelo | RBAC (Role-Based Access Control) |
| Roles | USER, ADMIN |
| Validación | Por endpoint y recurso |
| Auditoría | Log de accesos denegados |

---

### NFR-SEC03: Protección de Datos

| Requisito | Especificación |
|-----------|----------------|
| Datos en tránsito | TLS 1.2+ (HTTPS obligatorio) |
| Datos en reposo | Cifrado de disco (proveedor) |
| PII | Solo email, nombre (mínimo necesario) |
| Logs | No contienen passwords ni tokens |
| Backups | Cifrados |

---

### NFR-SEC04: Headers de Seguridad

| Header | Valor |
|--------|-------|
| Content-Security-Policy | default-src 'self'; ... |
| X-Frame-Options | DENY |
| X-Content-Type-Options | nosniff |
| Strict-Transport-Security | max-age=31536000 |
| X-XSS-Protection | 1; mode=block |

---

### NFR-SEC05: Rate Limiting

| Endpoint | Límite |
|----------|--------|
| /auth/login | 5 req/15 min por IP |
| /auth/register | 3 req/hora por IP |
| /auth/forgot-password | 3 req/hora por email |
| APIs generales | 100 req/min por usuario |

---

### NFR-SEC06: Vulnerabilidades

| Requisito | Especificación |
|-----------|----------------|
| Escaneo de dependencias | Trivy en cada build |
| Vulnerabilidades HIGH | 0 toleradas |
| Vulnerabilidades CRITICAL | 0 toleradas |
| Actualización de deps | Mensual como mínimo |
| OWASP Top 10 | Mitigaciones implementadas |

---

## 7. Requisitos de Usabilidad (NFR-U)

### NFR-U01: Compatibilidad de Navegadores

| Navegador | Versiones Soportadas |
|-----------|----------------------|
| Chrome | Últimas 2 versiones |
| Firefox | Últimas 2 versiones |
| Safari | Últimas 2 versiones |
| Edge | Últimas 2 versiones |
| Mobile Safari | iOS 14+ |
| Chrome Mobile | Android 10+ |

---

### NFR-U02: Responsive Design

| Breakpoint | Ancho | Dispositivo |
|------------|-------|-------------|
| Mobile | < 640px | Smartphones |
| Tablet | 640-1024px | Tablets |
| Desktop | > 1024px | Laptops/Desktop |

---

### NFR-U03: Accesibilidad

| Requisito | Nivel |
|-----------|-------|
| WCAG | 2.1 AA (objetivo) |
| Navegación por teclado | Soportada |
| Lectores de pantalla | Compatible |
| Contraste de colores | 4.5:1 mínimo |

---

### NFR-U04: Idioma

| Elemento | Idioma |
|----------|--------|
| Interfaz de usuario | Español (Argentina) |
| Mensajes de error | Español |
| Documentación técnica | Español |
| Código | Inglés |

---

## 8. Requisitos de Mantenibilidad (NFR-M)

### NFR-M01: Cobertura de Tests

> Modelo **100/80/0** — ver [14-TEST-STRATEGY.md](./14-TEST-STRATEGY.md).

| Capa | Paquetes | Cobertura |
|------|----------|-----------|
| **Core** | `billing.domain.*`, `billing.application.*`, `auth.domain.*`, `auth.application.*` | **100%** |
| **Importancia** | `virtual.*`, `physical.*` | **80%** |
| **Infraestructura** | DTOs, configs, repositorios JPA | **0% unitarios** |

| Métrica | Mínimo |
|---------|--------|
| Tests de arquitectura (ArchUnit) | 100% pass |

---

### NFR-M02: Calidad de Código

| Métrica | Objetivo |
|---------|----------|
| SonarCloud Quality Gate | Pass |
| Code smells | < 50 por módulo |
| Duplicación | < 3% |
| Deuda técnica | < 2 días total |

---

### NFR-M03: Documentación

| Elemento | Requisito |
|----------|-----------|
| APIs | OpenAPI/Swagger |
| Arquitectura | C4 diagrams actualizados |
| Decisiones | ADRs documentados |
| Operaciones | Runbooks mantenidos |

---

### NFR-M04: Logging

| Requisito | Especificación |
|-----------|----------------|
| Formato | JSON estructurado |
| Correlation ID | X-Request-ID en todas las requests |
| Niveles | ERROR, WARN, INFO, DEBUG |
| Retención | 7 días (rotación) |

---

## 9. Requisitos de Compatibilidad (NFR-C)

### NFR-C01: Integraciones Externas

| Sistema | Protocolo | Timeout |
|---------|-----------|---------|
| Mercado Pago | REST HTTPS | 30s |
| Bunny.net CDN | HTTPS | 10s |
| SMTP (Email) | SMTPS | 30s |
| Google Calendar | REST HTTPS | 15s |

---

### NFR-C02: Comunicación Interna

| Comunicación | Mecanismo | Formato |
|--------------|-----------|---------|
| BFF → API | HTTP REST | JSON |
| Módulo → Módulo | Interfaces Java (in-process) | Objetos |
| Eventos (futuro) | Spring Events | Java Objects |

> Los módulos dentro de `:api:app` se comunican mediante interfaces Java,
> no HTTP. Ver [ADR-0020](./adr/0020-modular-monolith.md).

---

### NFR-C03: Versionado de API

| Requisito | Especificación |
|-----------|----------------|
| Esquema | URL path (/api/v1/, /api/v2/) |
| Backward compatibility | Mínimo 6 meses |
| Deprecation notice | 3 meses previo |

---

## 10. Matriz de Verificación

| NFR | Método de Verificación | Frecuencia |
|-----|------------------------|------------|
| NFR-P01 | Gatling load test | Pre-release |
| NFR-P02 | Gatling load test | Pre-release |
| NFR-A01 | UptimeRobot | Continuo |
| NFR-A02 | Drill de restore | Mensual |
| NFR-SEC01-06 | Trivy + revisión manual | Por build |
| NFR-M01 | JaCoCo | Por build |
| NFR-M02 | SonarCloud | Por build |

---

## 11. Excepciones y Waivers

| NFR | Excepción | Justificación | Fecha Expiración |
|-----|-----------|---------------|------------------|
| NFR-U03 | WCAG parcial | MVP limitado | 6 meses post-launch |
| NFR-A01 | 99% vs 99.5% | Infraestructura MVP | 6 meses post-launch |

---

*Revisar y actualizar estos requisitos trimestralmente o ante cambios significativos.*
