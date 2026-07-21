# ADR-0016: Validación de JWT Simétrica con Clave Compartida (HS256)

**Estado:** Reemplazado por ADR-0020  
**Fecha:** 2026-05-21  
**Decisores:** Tech Lead, Arquitectos de Seguridad  

## Vigencia actual

**Esta decisión fue reemplazada por
[ADR-0020](./0020-modular-monolith.md).** Ya no existen resource servers por
dominio ni un secreto JWT distribuido entre microservicios. El texto restante
documenta el contexto histórico y no debe utilizarse como guía de
implementación. El mecanismo vigente de firma, validación y rotación debe
definirse en un ADR de seguridad específico; este documento no lo presupone.

## Contexto y Problema

Durante el proceso de separación del monolito en microservicios, el sistema de autenticación centralizado (`auth-api`) se encargó de generar y firmar los tokens de acceso JSON Web Tokens (JWT). Por otro lado, las APIs de recursos (`virtual-api`, `physical-api` y `billing-api`) actúan como Resource Servers de Spring Security, encargados de recibir y validar dichos tokens para autorizar los accesos.

Inicialmente, existía una inconsistencia en la que `auth-api` firmaba de manera simétrica usando el algoritmo **HMAC-SHA256 (HS256)** con una clave compartida, mientras que las APIs de recursos estaban preconfiguradas para realizar validaciones asimétricas descargando las claves públicas desde un endpoint de **JWK Set (JSON Web Key Set)** provisto por el servidor de autorización (por ejemplo, mediante la propiedad `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`).

Al usar firmas simétricas, `auth-api` no publica (ni puede publicar de forma segura) claves en un JWK Set, lo que provocaba que los Resource Servers fallaran al intentar arrancar o validaran incorrectamente las firmas, devolviendo errores `401 Unauthorized` de conexión fallida al intentar resolver las claves de firma en el puerto de `auth-api`.

Se necesita definir una decisión arquitectónica para unificar y validar los JWT de manera consistente en todo el ecosistema de servicios locales e integrados.

---

## Factores Clave (Decision Drivers)

* **Facilidad de setup e integración local:** El entorno de desarrollo local orquesta 7 contenedores (MySQL, 4 microservicios, BFF y Nginx). Agregar complejidad de certificados/Keystores locales ralentiza el desarrollo.
* **Aislamiento de red:** Garantizar que los microservicios puedan validar los tokens de manera autónoma (stateless) sin sobrecargar de peticiones síncronas a `auth-api`.
* **Seguridad perimetral y de secretos:** Limitar la superficie de exposición del secreto de firma de los JWT.
* **Tiempo de salida al mercado (Time to Market):** Es crítico probar la funcionalidad local del BFF y las APIs separadas lo antes posible.

---

## Opciones Consideradas

### Opción 1: Firma Simétrica Compartida (HS256) en todo el ecosistema (Clave Compartida)

Consiste en configurar tanto a `auth-api` como a los Resource Servers (`virtual-api`, `physical-api`, `billing-api`) para usar el mismo algoritmo simétrico `HS256` cargando una clave compartida común mediante la variable de entorno `JWT_SECRET`.

* **Pros:**
  * **Simplicidad extrema:** No requiere generación de pares de claves, certificados ni almacenamiento en almacenes seguros (Keystore).
  * **Rendimiento:** El algoritmo de firma simétrica HMAC es computacionalmente más ligero y rápido que el procesamiento asimétrico.
  * **Facilidad local:** Mismo setup que se traía simplificado desde el monolito.
* **Contras (Seguridad):**
  * **Mayor superficie de ataque:** El secreto que permite *firmar* y *generar* tokens válidos debe ser distribuido e inyectado en todos los microservicios. Si un atacante compromete a un microservicio de recursos secundario (ej. `physical-api`), puede obtener el secreto de firma y falsificar tokens legítimos de administración para acceder a cualquier servicio.
  * **Rotación compleja:** Para cambiar la clave, es obligatorio actualizar la variable de entorno en todos los contenedores al mismo tiempo y reiniciarlos en conjunto. Los tokens activos se invalidan de inmediato.

### Opción 2: Firma Asimétrica (RS256) con Publicación de JWK Set

Consiste en generar un par de claves RSA en `auth-api`. `auth-api` firma con su clave privada (algoritmo `RS256`) y expone su clave pública en un endpoint centralizado de JWK Set (ej: `/api/v1/auth/.well-known/jwks.json`). Los Resource Servers descargan y cachean periódicamente esta clave pública para validar firmas.

* **Pros:**
  * **Aislamiento de Seguridad Total (Principio de Mínimo Privilegio):** Solo el servidor de autorización conoce la clave privada que genera identidades. Las APIs de recursos solo conocen la clave pública y no pueden falsificar identidades si una de ellas es comprometida.
  * **Rotación Dinámica de Claves:** Permite que `auth-api` rote la clave de firma sin downtime, ya que los microservicios descargarán automáticamente la clave pública nueva al recibir un token firmado con un ID de clave (`kid`) desconocido.
* **Contras:**
  * **Mayor complejidad:** Requiere generar y almacenar de forma segura las llaves criptográficas (Keystores, etc.) y programar el endpoint JWKS.
  * **Dependencia de red:** Los Resource Servers requieren acceso directo en el arranque al endpoint de JWKS de `auth-api`.

### Opción 3: Validación Centralizada en API Gateway (Token Relay)

El API Gateway (Nginx o un componente intermedio) valida los JWT externos de forma simétrica o asimétrica en la frontera de la red interna, y propaga cabeceras HTTP limpias (ej: `X-User-Id`, `X-User-Roles`) hacia los microservicios internos.

* **Pros:**
  * **Descarga criptográfica:** Los microservicios de negocio no requieren dependencias criptográficas de JWT ni configuración de seguridad OAuth2.
  * **Eficiencia:** Evita validar la firma del mismo token múltiples veces en llamadas encadenadas de servicios internos.
* **Contras:**
  * **Dependencia de red interna estricta:** Se requiere aislamiento total (mTLS o firewalls de red) en los contenedores de recursos. Si un atacante burla la frontera, puede enviar HTTP plano a las APIs con cabeceras simuladas sin pasar por validación.

---

## Decisión

Elegimos la **Opción 1: Firma Simétrica Compartida (HS256)** para el entorno de Desarrollo Local e Integración Inicial (MVP), acotando la inyección de secretos estrictamente a través de variables de entorno administradas por Docker Compose.

Sin embargo, **se define una ruta obligatoria para entornos productivos reales de migrar a la Opción 2 (RS256 / Asimétrica)** para mitigar el riesgo de fuga y asegurar el cumplimiento de estándares mínimos de seguridad en la nube.

---

## Justificación (Rationale)

La opción simétrica (HS256) fue elegida para desbloquear inmediatamente la fase final del desarrollo de integración local (Fase 7). Dado que estamos corriendo múltiples servicios de Spring Boot localmente y bajo contenedores, reducir la complejidad operativa en local nos permite garantizar que el BFF, Nginx y los microservicios se comuniquen de manera correcta a nivel funcional y de red rápidamente.

El riesgo de seguridad se mitiga temporalmente en local mediante la inyección del secreto de forma externa (nunca hardcoded) a través de `.env` y el archivo `docker-compose.yml`. Como la arquitectura de Spring Boot de los Resource Servers se desacopló de forma limpia, la reversibilidad de esta decisión a favor de la Opción 2 (Firma Asimétrica) es sumamente sencilla y no impacta en la lógica del negocio.

---

## Consecuencias

### Positivas

* **Desbloqueo Inmediato:** Las pruebas de Bruno e integración a través de Nginx Gateway funcionan al 100% de manera fluida en local.
* **Configuración Unificada:** Se inyecta la clave criptográfica de manera unificada mediante la variable `JWT_SECRET` en el archivo central `.env`.
* **Desempeño local óptimo:** Validación de firma instantánea en cada API.

### Negativas / Deuda Técnica

* **Deuda de Seguridad en Producción:** Compartir el secreto de firma con todas las APIs de recursos (`billing-api`, `virtual-api`, `physical-api`) es una práctica insegura que no debe desplegarse en entornos productivos reales.
* **Dependencia de variables de entorno:** Es crítico asegurar que ningún desarrollador hardcodee el secreto en los repositorios locales.

### Implicaciones de Costos

* **Nulo a nivel operativo:** No requiere almacenamiento extra ni llamadas de red adicionales en local.
* **Costo futuro:** Estimado en **1.5 sprints de desarrollo** en fases futuras para implementar la firma con clave asimétrica (RS256), configurar keystores dinámicos en producción y exponer el endpoint de JWKS en `auth-api`.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Fuga de la variable `JWT_SECRET` en entornos compartidos de staging/producción, permitiendo la generación no autorizada de accesos de administrador.
* **Plan de Mitigación:** Utilizar un secreto largo y de alta entropía (mínimo 256 bits) en desarrollo local y asegurar que en la nube se utilicen Secrets Managers independientes con posterior migración a RS256.
* **Reversibilidad:** Alta. Para pasar a criptografía asimétrica (RS256), solo se requiere modificar el bean `JwtDecoder` en `JwtConfig` de los Resource Servers por `NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build()` y reconfigurar el bean de generación en `auth-api`. La lógica interna de los controladores, servicios e interceptores de seguridad permanecerá idéntica.

---

## Referencias y Decisiones Relacionadas

* **Documentación de Spring Security Resource Server:** [OAuth2 Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
* **Referencia histórica:** `virtual-api/.../JwtConfig.java` del repositorio anterior.
* **Referencia histórica:** `physical-api/.../JwtConfig.java` del repositorio anterior.
* **Referencia histórica:** `infra/docker/microservices/docker-compose.yml` del repositorio anterior.
