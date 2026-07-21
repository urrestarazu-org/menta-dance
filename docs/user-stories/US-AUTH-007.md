# US-AUTH-007: Logout y revocación de tokens

**ID:** US-AUTH-007
**Título:** Cierre de sesión y revocación de tokens
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Should Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario autenticado
> **Quiero** poder cerrar mi sesión de forma segura
> **Para** proteger mi cuenta cuando uso dispositivos compartidos o cuando termino de usar la aplicación.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Logout exitoso (sesión actual)**

* **Dado que (Given):** Un usuario está autenticado con un access token y refresh token válidos.
* **Cuando (When):** Envía `POST /api/v1/auth/logout` con `Authorization: Bearer <access-token>` y el refresh token de la sesión en cookie (Web) o header `X-Refresh-Token` (Android).
* **Entonces (Then):** El sistema debe agregar inmediatamente el `jti` del access token a la blacklist Redis, con TTL igual a su tiempo restante de expiración.
* **Y (And):** Debe revocar el refresh token de la sesión cuando esté presente y sea válido.
* **Y (And):** Debe devolver `204 No Content` y limpiar la cookie de refresh en Web.

**Escenario 2: Logout inmediato del access token actual**

* **Dado que (Given):** Un usuario cerró sesión con un access token aún no expirado.
* **Cuando (When):** Reutiliza ese access token en una ruta protegida.
* **Entonces (Then):** El filtro de autenticación debe detectar su `jti` en Redis y devolver `401 Unauthorized`.

**Escenario 3: Logout con refresh token inválido**

* **Dado que (Given):** Un usuario autenticado intenta cerrar sesión.
* **Cuando (When):** El refresh token de la sesión no existe o ya fue revocado.
* **Entonces (Then):** El sistema debe invalidar igualmente el `jti` del access token y devolver `204 No Content`.
* **Y (And):** No debe revelar si el refresh token era válido o no.

**Escenario 4: Logout sin autenticación**

* **Dado que (Given):** Una petición llega al endpoint de logout.
* **Cuando (When):** No se proporciona un access token válido en `Authorization`.
* **Entonces (Then):** El sistema debe devolver `401 Unauthorized`.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * `POST /logout`: Requiere access token válido en `Authorization`; el refresh token se recibe por cookie (Web) o `X-Refresh-Token` (Android), no en el body.
  * Implementar invalidación inmediata del `jti` y revocación del refresh token de la sesión cuando esté presente.
* **Rendimiento / Rate Limiting:**
  * Sin rate limiting específico para logout (operación legítima).
* **Consideraciones de Seguridad:**
  * El access token (JWT) se invalida mediante blacklist en Redis usando su `jti`.
  * Ver [ADR-0025](../adr/0025-auth-token-strategy.md) para detalles de blacklist.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/auth/logout` - Cerrar sesión actual
* **Request Headers (logout):**

  ```
  Authorization: Bearer <access-token>
  X-Refresh-Token: <refresh-token>  # Solo Android; Web usa cookie HttpOnly
  ```

* **Response:**

  ```
  204 No Content
  Clear-Cookie: refreshToken  # Web
  ```

* **Tablas de BD:**
  * `auth_refresh_tokens` - Actualizar `revoked_at = NOW()`
* **Estrategia de Invalidación:**
  * Access token: Agregar primero su `jti` a blacklist Redis con TTL = tiempo restante de expiración.
  * Refresh token: Marcar como revocado en BD (`revoked_at`) cuando se recibió para la sesión.
  * Ver [ADR-0025](../adr/0025-auth-token-strategy.md) y [03-AUTH-API.md](../03-AUTH-API.md).

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (4 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para la lógica de revocación.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Se verifica que los refresh tokens revocados no pueden usarse para refresh.
