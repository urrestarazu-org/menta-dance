# Plan de Desarrollo: Separación de APIs

[← Volver al índice](./README.md) | [← Análisis de Costos](./12-COST-ANALYSIS.md) | [Estrategia de Pruebas →](./14-TEST-STRATEGY.md)

> [!CAUTION]
> **HISTÓRICO — PENDIENTE DE ALINEACIÓN. NO IMPLEMENTAR.** El timeline y la
> separación en APIs desplegables pertenecen al plan anterior. La planificación
> deberá adaptarse al monorepo y al único JAR API antes de ejecutarse. No se
> modifican todavía sus contratos funcionales.

---

## Introducción

Este plan de desarrollo establece los pasos para reemplazar el monolito actual por la nueva arquitectura de microservicios (4 APIs + BFF), basándose en una **Metodología Iterativa e Incremental**.
Dado que ya contamos con el desarrollo actual del proyecto como base, el enfoque será estructurar el ciclo de vida del software (SDLC) para cada componente:

1. **Análisis y Diseño:** Extracción de requisitos del monolito, redacción de Historias de Usuario (Given-When-Then) y contratos de API.
2. **Desarrollo y Pruebas:** Implementación siempre respaldada por Pruebas Unitarias y de Integración.
3. **Despliegues Tempranos:** Puestas en producción iterativas (incluso con datos mockeados) para realizar pruebas continuas en entornos reales, mitigando los riesgos del antiguo enfoque de lanzamiento único (*Big Bang*).

**Ver decisiones confirmadas del proyecto en:** [Índice canónico de ADRs](./adr/README.md)

---

## Resumen del Timeline (Iterativo)

| Fase | Duración | Período | Estado |
|------|----------|---------|--------|
| 0: Infraestructura | 2 semanas | Mayo 2026 | Completado |
| 1: Auth API | 6 semanas | Jun 2026 | Completado |
| 2: Billing API | 6 semanas | Jul 2026 | Completado |
| 3: Virtual API | 6 semanas | Ago 2026 | Completado |
| 4: BFF Web | 6 semanas | Sep 2026 | Completado |
| 5: Physical API | 4 semanas | Oct 2026 | Completado |
| 6: Android App | 8 semanas | Sep-Oct 2026 | Completado |
| 7: Deploy/Go-live | 2 semanas | Nov 2026 | Completado |
| 8: Mantenimiento | Continuo | Nov 2026+ | Pendiente |

**Fecha objetivo:** Q4 2026 (Noviembre)
**Equipo:** 1 desarrollador full-time
**Estructura:** Monorepo con Gradle multi-module

---

## Fase 0: Infraestructura Base y Foundations (2 semanas)

**Objetivo:** Establecer el entorno de desarrollo, facilitar los despliegues tempranos y sentar la arquitectura base.

### Tareas

- [x] **Estructura del Proyecto:** Configurar el repositorio para soportar múltiples módulos (Multi-module de Gradle) utilizando el monolito actual como base.
- [x] **Docker Compose Local y Staging:** Crear `docker-compose.yml` base para desarrollo y para despliegues tempranos iterativos (MySQL 8.0, NGINX).
- [x] **Esquemas de Base de Datos:** Definir en scripts SQL (`init-db.sql`) la creación de las 4 bases de datos independientes.
- [x] **Configuración Base Spring Boot:** Plantilla inicial (Spring Boot 3, Java 21, Virtual Threads).
- [x] **Transversales de Arquitectura:** Implementar el manejo global de excepciones, logging estructurado (con Correlation ID `X-Request-ID`) y configuración de Hashids.
- [x] **Salud y Monitoreo MVP:** Configurar Spring Boot Actuator (`/health`, `/metrics`) estándar para todos los servicios.

---

## Fase 1: Auth API (6 semanas) - Junio 2026

**Objetivo:** Cimiento del sistema: registro, autenticación, autorización y gestión central de la identidad.

### Tareas

- [x] **Análisis y Diseño:** Redactar Historias de Usuario para el dominio de Auth basadas en las funcionalidades del monolito actual (ej. roles, login). Definir el contrato OpenAPI.
- [x] **Migración del Modelo Base:** Extraer y adaptar las entidades de User y Roles del monolito actual a `menta_auth`.
- [x] **Desarrollo:** Implementar generación/validación JWT, endpoints públicos (login, registro) e internos. Integrar Rate Limiting básico.
- [x] **QA y Testing:** Desarrollar **Pruebas Unitarias** para la lógica JWT/Seguridad y **Pruebas de Integración** para validar los flujos de los endpoints REST.
- [x] **Despliegue Iterativo:** Desplegar la API en un entorno de staging para validar su funcionamiento, integraciones y seguridad de forma aislada.

---

## Fase 2: Billing API (6 semanas) - Julio 2026

**Objetivo:** Gestión centralizada de planes, suscripciones y cobros, sirviendo a ambas academias.

### Tareas

- [x] **Análisis y Diseño:** Redactar Historias de Usuario para suscripciones y pagos. Modelado visual del flujo de integración con Mercado Pago. Ver diagramas de secuencia en `docs/api-separation/diagrams/sequence/`.
- [x] **Desarrollo:** Catálogo de planes, integración con webhooks de Mercado Pago, flujos de pago manual y Scheduled Jobs HTTP para vencimientos.
- [x] **Desarrollo (US-BILLING-003):** Implementar flujo de pago por transferencia bancaria con subida de comprobantes. Almacenamiento en file system local según [ADR-0017](./adr/0017-almacenamiento-comprobantes-pago.md). Ver [US-BILLING-003](./user-stories/US-BILLING-003.md).
- [x] **QA y Testing:** Mockear las respuestas de Mercado Pago para ejecutar **Pruebas Unitarias** exhaustivas. Crear **Pruebas de Integración** del ciclo de vida completo de la suscripción.
- [x] **Despliegue Iterativo:** Subir la API y probar la comunicación HTTP inicial, incluso utilizando datos mockeados para planes y clientes de prueba.

---

## Fase 3: Virtual API (6 semanas) - Agosto 2026

**Objetivo:** Core de la academia online gestionando cursos, módulos, lecciones y reproducción de videos.

### Tareas

- [x] **Análisis y Diseño:** Definir Historias de Usuario enfocadas en la experiencia del alumno virtual. Diagramar las interacciones síncronas necesarias con Auth y Billing. Ver diagramas de secuencia en `docs/api-separation/diagrams/sequence/`.
- [x] **Migración del Catálogo:** Extraer el dominio de cursos, módulos y lecciones del monolito hacia `menta_virtual`.
- [x] **Desarrollo:** Integración de streaming con Bunny.net, protección con Hashids en rutas públicas y llamadas HTTP síncronas a Auth y Billing para validación de acceso.
- [x] **Desarrollo (US-VIRTUAL-007):** Implementar lógica de bloqueo (`locked: true/false`) para módulos y lecciones según estado de suscripción. Retornar HTTP 403 en acceso directo a contenido protegido sin suscripción activa. Ver [US-VIRTUAL-007](./user-stories/US-VIRTUAL-007.md).
- [x] **Desarrollo (US-VIRTUAL-008):** Implementar endpoint de tracking de progreso (`GET /api/v1/virtual/users/{userId}/latest-progress`) y persistencia en tabla `lesson_progress` con campo `updated_at`. Ver [US-VIRTUAL-008](./user-stories/US-VIRTUAL-008.md).
- [x] **QA y Testing:** Implementar **Pruebas de Integración** utilizando herramientas como WireMock para simular las respuestas de la Auth API y Billing API. Garantizar cobertura de código >80%.
- [x] **Despliegue Iterativo:** Desplegar en conjunto con Auth y Billing. Realizar pruebas cruzadas en un entorno real con datos de prueba.

---

## Fase 4: BFF Web (Backend for Frontend) (6 semanas) - Septiembre 2026

**Objetivo:** Recrear la interfaz de usuario web consumiendo de manera orquestada las nuevas APIs.

### Tareas

- [x] **Análisis y Diseño:** Mapear las pantallas actuales del monolito a los nuevos endpoints de las APIs. Identificar cuellos de botella en la agregación de datos.
- [x] **Desarrollo:** Adaptar las plantillas Thymeleaf. Implementar las llamadas a las APIs usando `RestClient` y Virtual Threads para lograr agregación paralela.
- [x] **Desarrollo (US-VIRTUAL-007):** Renderizar banner de advertencia e iconos de candado (🔒) en módulos/lecciones bloqueados para usuarios con suscripción cancelada/expirada. Redirigir a `/plans` en acceso denegado. Ver [US-VIRTUAL-007](./user-stories/US-VIRTUAL-007.md).
- [x] **Desarrollo (US-VIRTUAL-008):** Implementar botón flotante "Continuar última lección" en Dashboard/Home consumiendo endpoint `/latest-progress` de Virtual API. Ver [US-VIRTUAL-008](./user-stories/US-VIRTUAL-008.md).
- [x] **QA y Testing:** Redactar Pruebas E2E (End-to-End) básicas simulando los recorridos críticos de los usuarios (login, visualización de curso, checkout).
- [x] **Despliegue Iterativo:** Poner online el frontend en un entorno accesible apuntando a las APIs en staging para realizar validaciones tempranas de usabilidad y rendimiento.

---

## Fase 5: Physical API (4 semanas) - Octubre 2026 (paralelo con Fase 6)

**Objetivo:** Separar y construir la funcionalidad exclusiva para la academia presencial.

### Tareas

- [x] **Análisis y Diseño:** Validar los requisitos para la sede física. Redactar Historias de Usuario claras para el check-in QR y el registro de asistencia.
- [x] **Desarrollo:** Modelar salones y horarios en `menta_physical`. Programar la generación de códigos QR y la lógica de validación de acceso.
- [x] **QA y Testing:** Asegurar mediante **Pruebas Unitarias** la correcta validación de superposiciones de horarios y algoritmos de generación QR.
- [x] **Despliegue Iterativo:** Desplegar para realizar pruebas conjuntas y continuas en integración con el desarrollo paralelo de la App Android.

---

## Fase 6: Android App (8 semanas) - Septiembre-Octubre 2026 (paralelo con Fases 4-5)

**Objetivo:** Proveer una aplicación nativa híbrida para check-in QR y visualización de cursos.

**Nota:** Esta fase requiere aprendizaje de Kotlin/Android desde cero. Se recomienda iniciar estudio en paralelo durante Fases 1-3.

### Plan de Aprendizaje (paralelo a desarrollo)

- [x] **Semanas 1-2:** Fundamentos de Kotlin
- [x] **Semanas 3-4:** Jetpack Compose básico
- [x] **Semanas 5-6:** Proyecto de práctica

### Tareas de Desarrollo

- [x] **Análisis y Diseño UI:** Definir los mockups, flujos de navegación (virtual/física) e interacciones principales de la aplicación.
- [x] **Desarrollo:** Implementar login (Auth API), Lector de QR (Physical API) y catálogo virtual básico.
- [x] **QA y Testing:** Realizar pruebas instrumentadas en emuladores y dispositivos Android físicos reales.

---

## Fase 7: Despliegue Final y Go-Live (2 semanas) - Noviembre 2026

**Objetivo:** Transición final de los usuarios y formalización de la nueva arquitectura productiva.

### Tareas

- [x] **Control de Calidad (QA) Global:** Ejecución exhaustiva de pruebas de regresión en todo el ecosistema y validación de las reglas de arquitectura con ArchUnit.
  - Build completo exitoso (70 tests pasando en 4 APIs + BFF)
  - Violaciones de arquitectura corregidas (UserController → UserService)
- [x] **Migración de Datos Oficial:** Ejecutar scripts Flyway (`V1__init_data.sql`) con los datos definitivos, usando el monolito actual como fuente de verdad.
  - 20 scripts DDL revisados (4 APIs)
  - V005__create_admin_user.sql creado con hash del monolito
- [x] **Lanzamiento (Cut-Over):** Redireccionar el tráfico de producción en DNS/NGINX hacia el nuevo stack y apagar formalmente el monolito anterior.
  - Documento de cutover pendiente de crear y alinear con la arquitectura vigente
  - Pre-requisitos, checklist D-1, 6 fases de ejecución, smoke tests, rollback
- [x] **Monitoreo Inmediato:** Vigilancia estricta de las métricas (Actuator) y logs durante las primeras 48 horas post-lanzamiento.
  - Checklist de monitoreo detallado (4h, 12h, 48h)
  - Scripts de health checks automatizados
  - Procedimiento de escalación definido

---

## Fase 8: Mantenimiento y Soporte Post-Lanzamiento (Continuo) - Nov 2026 en adelante

**Objetivo:** Asegurar la viabilidad técnica a largo plazo, el pago de deuda técnica y una excelente experiencia de usuario.

### Tareas

- [ ] **Gestión de Bugs e Incidentes:** Establecer un flujo claro (triage) para priorizar y resolver fallos reportados por los usuarios de ambas academias.
- [ ] **Monitoreo Proactivo de la Salud del Sistema:** Revisión periódica de logs (UptimeRobot, Actuator) para identificar cuellos de botella de rendimiento, alto consumo de memoria o respuestas HTTP 500.
- [ ] **Pago de Deuda Técnica y Escalabilidad:** Planificar refactorizaciones estratégicas según el crecimiento (ej. reemplazar llamadas HTTP síncronas por *Event Sourcing* con RabbitMQ al alcanzar los límites del MVP).
- [ ] **Actualizaciones de Seguridad:** Mantenimiento constante de versiones de dependencias (Spring Boot, Kotlin, bibliotecas de front) y resolución de vulnerabilidades.
