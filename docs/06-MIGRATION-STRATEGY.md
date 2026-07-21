# Estrategia de Migración y Despliegue (Big Bang Release)

[← Volver al índice](./README.md) | [← Physical API](./05-PHYSICAL-API.md)

> [!CAUTION]
> **HISTÓRICO — PENDIENTE DE ALINEACIÓN. NO IMPLEMENTAR.** La estrategia y la
> topología de microservicios descritas aquí no son vigentes. La entrega actual
> es un JAR API modular y un JAR BFF según ADR-0020. Los contratos funcionales
> se revisarán en fase 2.

---

## Resumen

Dado que Menta Virtual Academy se encuentra actualmente en **fase beta (versión 0)** sin usuarios en producción activos, la estrategia de migración abandona el patrón *Strangler Fig* (migración gradual).

Adoptaremos una estrategia de **"Big Bang Release" (Reescritura y Lanzamiento Conjunto)**.

Desarrollaremos los 4 microservicios (Auth, Virtual, Physical, Billing) y el BFF en paralelo o secuencialmente en un entorno de desarrollo/staging, y una vez listos, **deprecaremos y reemplazaremos el monolito actual por completo en una sola ventana de despliegue**.

**Principios guía:**

- Cero tolerancia al código legacy (Clean Slate).
- Aprovechamiento total de Java 21 (Virtual Threads) y Spring Boot 3 desde el día 1.
- No hay necesidad de mantener sincronización de datos o dual-writes (Dual-Write) entre el monolito y los microservicios.

---

## Fases de Desarrollo (No de Migración)

Al no haber usuarios productivos, las "Fases" se refieren al orden lógico de construcción del nuevo sistema.

| Fase | Componente | Enfoque Principal |
|------|------------|-------------------|
| **0** | **Infraestructura Base** | Docker Compose, schemas MySQL (`auth_schema`, `virtual_schema`, `physical_schema`, `billing_schema`), NGINX. |
| **1** | **Auth API** | Sistema de registro, login, roles y validación JWT. Es el cimiento de los demás servicios. |
| **2** | **Billing API** | Catálogo de planes, suscripciones e integración completa con Mercado Pago (Webhooks). |
| **3** | **Virtual API** | Catálogo de cursos, módulos, lecciones e integración con Bunny.net. Consumirá estados de suscripción. |
| **4** | **Physical API** | Gestión presencial, códigos QR, salones y horarios. |
| **5** | **BFF Web** | Agregación de endpoints, renderizado de plantillas Thymeleaf. |
| **6** | **Android App** | App móvil que consumirá los endpoints públicos del API Gateway directamente. |

---

## Estrategia de Datos (Tabula Rasa)

### Abandono de Dual-Writes

En un entorno con usuarios de producción, tendríamos que haber implementado sincronización bidireccional entre el monolito y los nuevos microservicios.
Al estar en beta, **no haremos sincronización de datos**.

### Poblando la Nueva Base de Datos

Cualquier dato semilla (Seed Data) de prueba o catálogos iniciales de la versión beta se migrarán utilizando scripts SQL estáticos (`V1__init_data.sql` en Flyway) directamente sobre los nuevos esquemas separados.

---

## Ventajas de esta Estrategia

1. **Velocidad de Desarrollo:** No perdemos semanas construyendo proxies inversos complejos, feature flags temporales, ni scripts de sincronización de bases de datos.
2. **Deuda Técnica Cero:** El monolito actual se descarta por completo. No arrastraremos librerías viejas ni dependencias obsoletas.
3. **Diseño Puro:** Las APIs se pueden diseñar siguiendo estrictamente las prácticas REST y Domain-Driven Design sin tener que mantener retrocompatibilidad con contratos de interfaz viejos.

---

## Checklist de Salida a Producción (Go-Live)

Cuando todos los componentes de la Fase 1 a 5 estén listos:

1. **Apagado del Monolito:** Detener los contenedores del monolito beta actual.
2. **Reset de Datos (Opcional):** Si los datos actuales eran solo de pruebas, se vacía la base de datos de producción y se aplican las migraciones Flyway de los nuevos microservicios.
3. **Despliegue Multi-Container:** Levantar el nuevo `docker-compose.yml` con la Auth API, Virtual API, Physical API, Billing API y el BFF Web.
4. **Validación NGINX:** Asegurar que NGINX rutea el tráfico `/api/v1/auth`, `/api/v1/virtual`, etc., a las APIs, y la raíz `/` al BFF.
5. **Smoke Tests:** Validar el registro de un alumno, el pago simulado y la reproducción de un video.

---

[Siguiente: Consideraciones Técnicas →](./07-TECHNICAL-CONSIDERATIONS.md)
