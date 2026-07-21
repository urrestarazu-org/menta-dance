# Estrategia de Pruebas y Cobertura (100/80/0)

[← Volver al índice](./README.md) | [← Guía de Clean Architecture](./27-CLEAN-ARCHITECTURE-GUIDE.md) | [Matriz de Riesgos →](./15-RISK-MATRIX.md)

---

## 1. Visión General y Principios

Este documento define la estrategia unificada de testing para el proyecto Menta Dance. Nuestro objetivo es garantizar la estabilidad mediante un enfoque pragmático guiado por el riesgo de negocio.

### 1.1 Principios de Testing

* **Shift-Left Testing:** Detectar fallos lo más temprano posible en el ciclo de vida del desarrollo.
* **Test Isolation:** Cada prueba debe ser independiente, repetible y libre de efectos secundarios en la base de datos o el entorno.
* **Fast Feedback:** Optimizar la velocidad de ejecución: unitarios < 10ms, integración < 1s, contract < 500ms, y E2E < 30s.
* **Determinismo Absoluto:** Mismo input siempre produce el mismo output. Tolerancia cero a pruebas intermitentes (*flaky tests*).
* **Self-Documenting:** El código de test debe servir como documentación viva del comportamiento esperado del negocio.

---

## 2. Estrategia de Cobertura por Capas de Riesgo (Modelo 100/80/0)

No todas las partes de la aplicación conllevan el mismo nivel de riesgo de negocio. Implementamos el modelo **100/80/0** para priorizar los esfuerzos de testing donde el impacto de un fallo sea más severo.

```
                    ┌─────────────────────────────────────────┐
                    │      CAPA CORE O CRÍTICA (100%)         │ ← Cero tolerancia a fallas.
                    │   Pagos, Suscripciones, Seguridad...    │   Negociación cero.
                    └─────────────────────────────────────────┘
                          │
                          ▼
                    ┌─────────────────────────────────────────┐
                    │    CAPA DE IMPORTANCIA/UI (80%)         │ ← Flujos de usuario y vistas.
                    │   Catálogo, Navegación, Formularios...  │   Foco en la experiencia.
                    └─────────────────────────────────────────┘
                          │
                          ▼
                    ┌─────────────────────────────────────────┐
                    │     CAPA DE INFRAESTRUCTURA (0%)        │ ← Estructuras estáticas.
                    │   Configuraciones, DTOs, Constantes...  │   Validado por compilación/QA.
                    └─────────────────────────────────────────┘
```

### 2.1 Capa Core o Crítica (Objetivo: 100% de Cobertura)

Protege la lógica que mantiene operativa la academia. Un fallo en este nivel se traduce directamente en pérdida de ingresos, problemas de seguridad o quiebra técnica.

* **Qué testear en Menta Virtual Academy:**
  * **Procesamiento de Pagos:** Flujos de suscripción, integración con Mercado Pago (`MercadoPagoService`), verificación de transferencias y pagos en efectivo.
  * **Control de Acceso:** Lógica de expiración de membresías y acceso a cursos/lecciones (`SubscriptionService`, `CourseAccessControl`).
  * **Seguridad:** Generación y validación de tokens (`JwtService`), encriptación de credenciales y rate limiting.
  * **Validaciones de Datos:** Restricciones de estado y transiciones (ej. transiciones de `SubscriptionStatus` o `PaymentStatus`).
* **Regla:** **Negociación Cero**. Se exige una cobertura absoluta (100% de *lines*, *branches* y *functions*). Todos los casos extremos (*edge cases*), entradas nulas, desbordamientos de datos y escenarios de error deben ser probados rigurosamente.

### 2.2 Capa de Importancia e Interfaz (Objetivo: 80% de Cobertura)

Protege la experiencia de usuario y la interactividad. Un fallo aquí no quiebra el negocio inmediatamente, pero deteriora significativamente la confianza del cliente.

* **Qué testear:**
  * **Controladores y Endpoints:** Respuestas correctas del backend ante peticiones del cliente (`@RestController` y controllers de Thymeleaf).
  * **Componentes de UI e Interacción:** Componentes dinámicos con Alpine.js y renderizado de fragments críticos de Thymeleaf (ej. alertas, navegación del reproductor de video).
  * **Validación de Formularios:** Reglas de campos requeridos, formatos de correo, y carga de archivos (ej. comprobante de transferencia bancaria).
* **Regla:** Enfocarse en la **ruta feliz** (*happy path*) y en los flujos alternativos más probables. Es permisible omitir escenarios extremadamente raros o incompatibilidades de navegadores muy antiguos.

### 2.3 Capa de Infraestructura (Objetivo: 0% de Pruebas Unitarias)

Evita el desperdicio de tiempo y el coste de mantenimiento de tests en código boilerplate o puramente descriptivo que ya es validado por el compilador o por analizadores estáticos.

* **Qué NO testear unitariamente:**
  * **DTOs y Modelos Anémicos:** Getters, setters, constructores y métodos `toString()` o `equals()` generados automáticamente (o mediante Lombok).
  * **Clases de Configuración de Spring:** Métodos anotados con `@Bean` que solo instancian o configuran infraestructura (ej. `SecurityConfig`, `WebMvcConfig`, `DataSourceConfig`).
  * **Constantes y Enums Simples:** Clases que únicamente exponen valores estáticos o enums sin comportamiento lógico complejo.
  * **Interfaces de Repositorio:** Interfaces que heredan de `JpaRepository` sin queries manuales personalizadas (las queries complejas sí se validan con pruebas de integración).
* **Regla:** Confiar en el tipado estático del lenguaje y en las validaciones automáticas de CI/CD.

---

## 3. Configuración de Umbrales (Thresholds) y Automatización

Para que el modelo **100/80/0** sea efectivo, configuramos umbrales segmentados por paquetes utilizando JaCoCo.

### 3.1 Reglas en `build.gradle` (JaCoCo)

```groovy
jacocoTestCoverageVerification {
    violationRules {
        // Capa Core (100% de Cobertura) - Billing y Auth domain/application
        rule {
            element = 'PACKAGE'
            includes = [
                'com.menta.billing.domain.*',
                'com.menta.billing.application.*',
                'com.menta.auth.domain.*',
                'com.menta.auth.application.*',
                'com.menta.shared.domain.*'
            ]
            limit {
                counter = 'LINE'
                value = 'COVEREDRATIO'
                minimum = 1.00
            }
            limit {
                counter = 'BRANCH'
                value = 'COVEREDRATIO'
                minimum = 1.00
            }
        }

        // Capa de Importancia (80% de Cobertura) - Virtual y Physical
        rule {
            element = 'PACKAGE'
            includes = [
                'com.menta.virtual.domain.*',
                'com.menta.virtual.application.*',
                'com.menta.physical.domain.*',
                'com.menta.physical.application.*'
            ]
            limit {
                counter = 'LINE'
                value = 'COVEREDRATIO'
                minimum = 0.80
            }
        }
    }
}
```

> [!IMPORTANT]
> **Bloqueo en CI:** El pipeline de integración continua ejecuta automáticamente `jacocoTestCoverageVerification`. Si cualquier clase de la Capa Core desciende del 100%, el build **fallará** automáticamente y bloqueará la fusión del Pull Request.

---

## 4. Pirámide de Testing y Distribución de Pruebas

```
                    ┌─────────┐
                    │   E2E   │  ← Pocos, lentos, costosos (5-10%)
                    │  Tests  │     
                   ─┼─────────┼─
                  / │ Integra-│ \
                 /  │  ción   │  \  ← Moderados, Testcontainers (20-30%)
                /   └─────────┘   \
               /                   \
              /   ┌─────────────┐   \
             /    │   Unit      │    \  ← Mayoría, rápidos (60-70%)
            /     │   Tests     │     \
           ───────┴─────────────┴───────
```

### 4.1 Pruebas Unitarias (60-70% del total)

* **Herramientas:** JUnit 5, Mockito, AssertJ.
* **Enfoque:** Servicios, casos de uso, validadores y mapeadores.
* **Prácticas Clave:**
  * Usar patrón **Given-When-Then (AAA)** para estructurar los tests.
  * **Evitar Over-Mocking:** No simular más de 3 o 4 dependencias. Si una clase requiere demasiados mocks, indica que tiene demasiadas responsabilidades (violación de SRP).
  * **Test Doubles Claros:** Usar `Mocks` para verificar interacciones, `Stubs` para respuestas predefinidas, y `Fakes` para bases de datos en memoria simplificadas.
  * **Mutation Testing (PITest):** Evaluamos ocasionalmente la calidad de las aserciones introduciendo mutantes automáticos en el código de producción con `./gradlew pitest`.

### 4.2 Pruebas de Integración (20-30% del total)

* **Herramientas:** Spring Boot Test, Testcontainers, WireMock.
* **Enfoque:** Capa de persistencia, controladores REST, fragmentos Thymeleaf y APIs externas.
* **Prácticas Clave:**
  * **Slice Testing:** Usar anotaciones enfocadas como `@WebMvcTest` (para web layer) o `@DataJpaTest` (para repositorios) para evitar cargar el contexto completo de Spring de forma innecesaria.
  * **Testcontainers:** Usar una base de datos MySQL real en un contenedor Docker para pruebas de repositorio, garantizando que el dialecto SQL y los índices funcionen igual que en producción.
  * **WireMock:** Simular APIs externas de terceros (como Mercado Pago o Bunny.net) controlando timeouts, latencias y códigos de estado HTTP anómalos.

### 4.3 Pruebas de Contrato (5-10% del total)

* **Herramientas:** Spring Cloud Contract.
* **Enfoque:** Comunicación entre servicios (ej. BFF con API de Autenticación o API de Pagos).
* **Prácticas Clave:**
  * Establecer **Consumer-Driven Contracts (CDC)** para asegurar que los cambios en un proveedor no rompan a los servicios consumidores sin previo aviso.

### 4.4 Pruebas End-to-End (E2E) (5-10% del total)

* **Herramientas:** Playwright.
* **Enfoque:** Flujos de negocio críticos de principio a fin desde la perspectiva del navegador web.
* **Flujos E2E Obligatorios (P0):**
    1. Registro y activación de cuenta (Auth API + simulación de correo).
    2. Login y navegación al dashboard del alumno.
    3. Compra de una suscripción premium mediante redirección y webhook (Mercado Pago).
    4. Acceso y reproducción de videos en cursos adquiridos.
* **Prácticas Clave:**
  * **Page Object Model (POM):** Encapsular la lógica de interacción de UI en clases dedicadas para facilitar el mantenimiento.
  * **Visual Regression Testing:** Capturar screenshots de vistas clave y compararlas contra líneas base para evitar roturas visuales.

### 4.5 Pruebas de Carga y Rendimiento (< 5% del total)

* **Herramientas:** Gatling.
* **Enfoque:** Validar la degradación de tiempos de respuesta bajo concurrencia.
* **Objetivos de Nivel de Servicio (SLOs):**
  * Response time p50 < 100ms.
  * Response time p95 < 500ms.
  * Response time p99 < 1000ms.
  * Tasa de error < 0.1% bajo carga normal.

### 4.6 Pruebas de Seguridad (SAST/DAST)

* **SAST:** Análisis de código estático y secretos mediante SonarCloud.
* **Vulnerabilidades:** Escaneo automático de dependencias en CI usando Trivy (`trivy fs --severity HIGH,CRITICAL .`).
* **DAST:** Escaneo dinámico periódico de endpoints con OWASP ZAP.

---

## 5. Métricas Mínimas Accionables

Evitamos las métricas de vanidad y nos centramos exclusivamente en indicadores cuantitativos que exigen tomar acciones correctivas inmediatas ante desvíos.

### 5.1 Tasa de Éxito de Tests (Test Success Rate)

Representa el porcentaje de pruebas que pasan exitosamente sobre el total ejecutado en el pipeline diario.

$$\text{Test Success Rate} = \left( \frac{\text{Tests Pasados}}{\text{Total de Tests Ejecutados}} \right) \times 100$$

| Tasa de Éxito | Estado | Acción Requerida |
| :--- | :--- | :--- |
| **95% a 100%** | **Saludable** | Operación normal. Desarrollo continuo de nuevas funcionalidades. |
| **90% a 94.9%** | **Alerta** | **Detener Features:** Se pausa la creación de nuevas características. El equipo se enfoca en resolver los *flaky tests* o fallos existentes. |
| **< 90%** | **Crítico** | **Congelamiento de Código (Code Freeze):** Bloqueo total de despliegues. Todo el equipo se dedica exclusivamente a estabilizar la suite de pruebas. |

### 5.2 Tasa de Error en Producción (Production Error Rate)

Porcentaje de peticiones de usuario que resultan en fallos (errores 5xx o excepciones no controladas) en producción.

$$\text{Tasa de Error} = \left( \frac{\text{Peticiones Fallidas}}{\text{Total de Peticiones}} \right) \times 100$$

* **Tasa de Error > 1% (Investigación):** Un desarrollador senior debe revisar los logs de error de inmediato para corregir el problema de raíz en menos de 24 horas.
* **Tasa de Error > 2% (Emergencia):** Se activa un protocolo de emergencia y se evalúa el rollback del último despliegue.
* **Tasa de Error > 5% (Desastre):** Movilización total de recursos técnicos. Se suspenden las operaciones no críticas y se establece una sala de guerra (*war room*).

---

## 6. Antipatrones a Evitar (Prácticas Peligrosas)

* **Inflación de Cobertura (Coverage Inflation):** Escribir pruebas redundantes o sin aserciones reales (`assert` o `assertThat`) con el único fin de subir artificialmente la métrica de cobertura.
* **Abuso de Mocks (Mocking Everything):** Simular todas las dependencias en las pruebas de integración en vez de utilizar entornos reales con Testcontainers. Esto oculta fallos graves de comunicación con la base de datos o el sistema externo.
* **Cobertura Ciega (Blind Coverage):** Perseguir un porcentaje de cobertura general alto de forma homogénea, descuidando la capa crítica de pagos por gastar recursos en probar DTOs y clases autogeneradas.

---

## 7. Gestión de Datos de Prueba (Test Data)

* **Aislamiento (Isolation):** Cada test debe ser dueño de sus datos de entrada. Está prohibido asumir que existen registros previos creados por otras pruebas en la base de datos.
* **Test Data Factories / Builders:** Usar patrones de diseño Builder para inicializar objetos de prueba con valores por defecto consistentes, permitiendo sobrescribir únicamente los campos relevantes del test.
* **Limpieza de Datos:** Preferir el uso de `@Transactional` en las clases de prueba de integración para realizar un rollback automático de la transacción al finalizar cada método de test.

---

## 8. Manejo de Flaky Tests

Un test intermitente (*flaky test*) debilita la confianza en el pipeline de CI/CD.

### 8.1 Proceso de Cuarentena

```
┌────────────────────────────┐
│ Test falla 3+ veces en CI  │
└─────────────┬──────────────┘
              │
              ▼
┌────────────────────────────┐
│ Marcar con @Tag("flaky")   │
└─────────────┬──────────────┘
              │
              ▼
┌────────────────────────────┐
│ Excluir de pipeline base   │
└─────────────┬──────────────┘
              │
              ▼
┌────────────────────────────┐
│ Resolver en el mismo Sprint│
└────────────────────────────┘
```

1. Si una prueba falla intermitentemente por problemas de sincronización, orden o red, se marca con la anotación `@Tag("flaky")`.
2. El pipeline de CI principal excluye la etiqueta `flaky` para evitar bloqueos falsos en los despliegues.
3. Se abre un ticket en el backlog del equipo de desarrollo para corregir y reintegrar la prueba en el sprint actual.

---

## 9. Ejecución y Reportes

### 9.1 Comandos de Terminal

```bash
# Ejecutar todas las pruebas del proyecto
./gradlew test

# Ejecutar únicamente pruebas unitarias
./gradlew test --tests "*Test"

# Ejecutar únicamente pruebas de integración
./gradlew test --tests "*IntegrationTest"

# Verificar arquitectura técnica (ArchUnit)
./gradlew test --tests "com.menta.dance.architecture.*"

# Validar estilo de código
./gradlew checkstyleMain

# Ejecutar pruebas de carga
./gradlew gatlingRun
```

### 9.2 Reportes de Calidad Localizados

* **Resultados de Tests:** `build/reports/tests/test/index.html`
* **Cobertura de Código (JaCoCo):** `build/reports/jacoco/test/html/index.html`
* **Reporte de Checkstyle:** `build/reports/checkstyle/main.html`
* **Reporte de Carga (Gatling):** `build/reports/gatling/*/index.html`
