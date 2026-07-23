# US-AUTH-005: Solicitud de recuperación de contraseña

**ID:** US-AUTH-005
**Título:** Solicitud de recuperación de contraseña (Forgot Password)
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario registrado que olvidó su contraseña
> **Quiero** solicitar un enlace de recuperación enviado a mi email
> **Para** poder restablecer mi contraseña y recuperar el acceso a mi cuenta.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Solicitud exitosa (email existe)**

* **Dado que (Given):** Un usuario registrado olvidó su contraseña.
* **Cuando (When):** Envía su email a `POST /api/v1/auth/forgot-password`.
* **Entonces (Then):** El sistema debe generar un `resetToken` único (válido por 1 hora).
* **Y (And):** Debe almacenar únicamente el hash del token en
  `auth_password_reset_tokens`.
* **Y (And):** Debe enviar un email con el enlace de recuperación.
* **Y (And):** Debe devolver un código HTTP `200 OK` con mensaje genérico.

**Escenario 2: Solicitud con email inexistente**

* **Dado que (Given):** Alguien intenta recuperar contraseña.
* **Cuando (When):** Envía un email que no está registrado en el sistema.
* **Entonces (Then):** El sistema NO debe revelar que el email no existe.
* **Y (And):** Debe devolver el mismo código HTTP `200 OK` con el mismo mensaje genérico.
* **Y (And):** No debe enviar ningún email.

**Escenario 3: Múltiples solicitudes (invalidar tokens anteriores)**

* **Dado que (Given):** Un usuario ya solicitó recuperación de contraseña previamente.
* **Cuando (When):** Solicita una nueva recuperación antes de usar el token anterior.
* **Entonces (Then):** El sistema debe invalidar todos los tokens de reset anteriores.
* **Y (And):** Debe generar un nuevo token.
* **Y (And):** Debe enviar un nuevo email con el enlace actualizado.

**Escenario 4: Rate limiting excedido**

* **Dado que (Given):** Un usuario o atacante hace múltiples solicitudes.
* **Cuando (When):** Se excede el límite de 3 solicitudes por hora para el mismo email.
* **Entonces (Then):** El sistema debe devolver un código HTTP `429 Too Many Requests`.
* **Y (And):** El mensaje debe indicar "Demasiados intentos. Intente nuevamente en X minutos".

**Escenario 5: Cuenta bloqueada o inactiva**

* **Dado que (Given):** Un usuario con estado `BLOQUEADO` o `INACTIVO` solicita recuperación.
* **Cuando (When):** Envía su email a `POST /api/v1/auth/forgot-password`.
* **Entonces (Then):** El sistema debe devolver el mismo `200 OK` genérico (no revelar estado).
* **Y (And):** No debe enviar email ni generar token.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (no requiere token JWT).
  * NUNCA revelar si un email existe o no en el sistema (prevención de enumeración).
  * El token de reset debe ser aleatorio criptográficamente; sólo su hash se
    persiste y el valor en claro no se registra.
  * El token debe ser de un solo uso.
* **Rendimiento / Rate Limiting:**
  * Máximo 3 solicitudes por hora por email.
  * Máximo 10 solicitudes por hora por IP.
* **Expiración:**
  * El token expira a la 1 hora de su generación.

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/auth/forgot-password`
* **Request Body:**

  ```json
  {
    "email": "usuario@example.com"
  }
  ```

* **Response Body (siempre el mismo):**

  ```json
  {
    "message": "Si el email está registrado, recibirá un enlace de recuperación."
  }
  ```

* **Tablas de BD (Schemas):**
  * `auth_password_reset_tokens`
    * Campos: `id`, `user_id`, `token_hash`, `expires_at`, `used_at`, `created_at`
* **Integraciones:**
  * Servicio de email (MailService) para envío del enlace.
* **Email Template:**
  * Asunto: "Recuperación de contraseña - Menta Academy"
  * Enlace: `https://mentavirtual.com/reset-password?token={token}`

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (5 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para generación de tokens y rate limiting.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] El template de email de recuperación está creado y funcional.
* [ ] Se verifica que NO se revela información sobre existencia de emails.
