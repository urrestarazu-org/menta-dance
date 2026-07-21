# ADR-0002: Implementación de Patrón BFF con Thymeleaf

**Estado:** Aceptado
**Fecha:** 2026-05-17
**Decisores:** Equipo de Arquitectura

## Vigencia actual

El patrón BFF con Thymeleaf continúa aceptado. El BFF se despliega como un JAR separado y consume por HTTP al único JAR API. Las referencias históricas a cuatro APIs independientes, RabbitMQ, circuit breakers o credenciales M2M no son instrucciones vigentes; fueron reemplazadas por [ADR-0020](./0020-modular-monolith.md). El panel de administración es una página web servida por este BFF.


## Contexto y Problema

Menta Virtual Academy actualmente renderiza su interfaz usando Thymeleaf acoplado a la lógica de negocio en un monolito. Al separar la lógica en múltiples APIs, el frontend necesita consumir estos nuevos endpoints. Reescribir todo el frontend en un framework SPA (React/Vue/Next.js) tomaría demasiado tiempo, excediendo la estimación del MVP (12-18 meses).

## Factores Clave (Decision Drivers)

* Minimizar el tiempo de migración y reescritura de código (Time-to-market del MVP).
* SEO: Mantener las URLs y la indexabilidad actual intacta.
* Rendimiento: Evitar que el cliente web (navegador) tenga que hacer múltiples peticiones a distintas APIs para armar una página.
* **Límite de Hardware:** Todos los servicios (APIs, BFF, BD) operarán consolidados en un servidor único de 8GB RAM y 30GB SSD.

## Opciones Consideradas

### Opción 1: SPA (React/Vue/Angular) + API Gateway

* **Descripción:** Reescribir el frontend completamente y consumirlo a través de un API Gateway directo.
* **Pros:**
  * Separación total de frontend y backend.
  * Experiencia de usuario (UX) moderna y fluida.
* **Contras:**
  * Costo y tiempo de desarrollo inaceptable para el MVP.
  * Riesgos de SEO si no se implementa Server-Side Rendering (SSR) correctamente.

### Opción 2: BFF (Backend for Frontend) manteniendo Thymeleaf

* **Descripción:** Crear una aplicación Spring Boot que sirva exclusivamente para renderizar los templates actuales de Thymeleaf, actuando como cliente (agregador) de las nuevas APIs.
* **Pros:**
  * Reutilización de casi el 100% de las vistas actuales (HTML/Thymeleaf).
  * Reducción de latencia: el BFF hace las llamadas a las APIs internamente en la red del servidor.
* **Contras:**
  * Se mantiene una tecnología de renderizado antigua.
  * Riesgo de que el BFF acumule lógica de negocio si no se controla.

## Decisión

Elegimos **Opción 2: BFF con Thymeleaf** porque **permite reutilizar la interfaz web existente, mitigando enormemente los tiempos de desarrollo y protegiendo el SEO, cumpliendo así con los tiempos del MVP**.

## Justificación (Rationale)

Para un MVP con un equipo de 2-3 personas, reescribir la UI es inviable. El BFF actúa como un puente perfecto: orquesta y agrega datos de la Auth API, Virtual API, Physical API y Billing API, pasándolos a las vistas Thymeleaf. Esto garantiza que las APIs subyacentes se diseñen limpias (REST) sin forzar respuestas específicas para la UI, ya que el BFF se encarga de esa transformación.

## Responsabilidades del BFF

### Permitido (Lógica de Presentación)

* Agregación de datos de múltiples APIs para una sola vista.
* Transformación de DTOs de API a ViewModels para Thymeleaf.
* Manejo de sesión web y cookies del usuario.
* Validaciones de UI (campos requeridos, formatos).
* Formateo de fechas, monedas y textos para la UI.
* Manejo de errores y mensajes amigables al usuario.

### Prohibido (Lógica de Negocio)

* Cálculos de precios, descuentos o impuestos (responsabilidad de Billing API).
* Validaciones de reglas de negocio (permisos, límites de suscripción).
* Acceso directo a base de datos.
* Envío de notificaciones o emails.
* Procesamiento de pagos.

## Consecuencias

### Positivas

* MVP alcanzable en 12-18 meses.
* Migración transparente para el usuario final (SEO y URLs intactas).

### Negativas / Deuda Técnica

* Se aplaza la modernización de la UI a una fase futura.
* El BFF podría convertirse en un cuello de botella si no se controlan sus responsabilidades.

### Implicaciones de Costos e Infraestructura

* Añade un servicio más (Spring Boot) a desplegar vía Docker.
* **Gestión de memoria:** Al compartir un host de 8GB RAM, los 5 servicios Spring Boot (Auth, Virtual, Physical, Billing, BFF) deberán configurarse con límites de heap moderados (`-Xmx512m`) para convivir adecuadamente con MySQL, Redis y RabbitMQ sin riesgo de Out Of Memory (OOM).

### Riesgos y Reversibilidad

* **Riesgo Principal:** Que el BFF se convierta en un "mini-monolito" donde los desarrolladores filtren lógica de negocio por conveniencia.
* **Plan de Mitigación:** Validaciones estrictas en Code Review (Checkstyle/ArchUnit) para asegurar que el BFF solo hace delegación y agregación. Se definirá una regla ArchUnit que prohíba acceso a repositorios desde el BFF.
* **Reversibilidad:** Alta. Las APIs REST subyacentes estarán listas; en el futuro, se puede reemplazar el BFF por una SPA en Next.js sin tocar las APIs.

## Referencias y Decisiones Relacionadas

* **Alcance reemplazado por:** [ADR-0020](./0020-modular-monolith.md) - El BFF llama a un único API
* **Aclaración vigente:** BFF → API usa el mecanismo de autenticación del contrato HTTP externo, no credenciales entre módulos
* **Patrón BFF:** <https://samnewman.io/patterns/architectural/bff/>
