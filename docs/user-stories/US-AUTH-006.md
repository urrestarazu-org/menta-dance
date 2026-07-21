# US-AUTH-006: Reset de contraseña

**ID:** US-AUTH-006
**Título:** Restablecimiento de contraseña con token
**Módulo / API:** Auth API
**Prioridad (MoSCoW):** Must Have
**Estado:** Draft
**Épica:** EP-01 Autenticación

---

## 1. Historia de Usuario

> **Como** usuario que recibió un email de recuperación de contraseña
> **Quiero** establecer una nueva contraseña usando el enlace recibido
> **Para** recuperar el acceso a mi cuenta de forma segura.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: Reset exitoso**

* **Dado que (Given):** Un usuario recibió un email con el link de reset de contraseña.
* **Cuando (When):** Accede al enlace y envía `POST /api/v1/auth/reset-password` con el token y la nueva contraseña.
* **Entonces (Then):** El sistema debe validar que el token existe y no está expirado.
* **Y (And):** Debe actualizar la contraseña del usuario (cifrada con BCrypt).
* **Y (And):** Debe marcar el token como usado (`used_at = NOW()`).
* **Y (And):** Debe invalidar TODOS los refresh tokens del usuario (cerrar otras sesiones).
* **Y (And):** Debe reiniciar `failedLoginAttempts` a 0 y desbloquear la cuenta si estaba bloqueada por intentos.
* **Y (And):** Debe devolver un código HTTP `200 OK` con mensaje de éxito.

**Escenario 2: Token expirado**

* **Dado que (Given):** Un usuario intenta usar el link de reset.
* **Cuando (When):** El token tiene más de 1 hora desde su generación.
* **Entonces (Then):** El sistema debe devolver un código HTTP `410 Gone`.
* **Y (And):** El mensaje debe indicar "El enlace ha expirado. Solicite uno nuevo".

**Escenario 3: Token inválido o inexistente**

* **Dado que (Given):** Alguien intenta resetear una contraseña.
* **Cuando (When):** El token no existe en la base de datos o tiene formato inválido.
* **Entonces (Then):** El sistema debe devolver un código HTTP `404 Not Found`.
* **Y (And):** El mensaje debe indicar "Enlace de recuperación inválido".

**Escenario 4: Token ya utilizado**

* **Dado que (Given):** Un usuario ya usó el link de reset previamente.
* **Cuando (When):** Intenta usar el mismo link de nuevo.
* **Entonces (Then):** El sistema debe devolver un código HTTP `410 Gone`.
* **Y (And):** El mensaje debe indicar "Este enlace ya fue utilizado".

**Escenario 5: Contraseña no cumple requisitos**

* **Dado que (Given):** Un usuario intenta establecer una nueva contraseña.
* **Cuando (When):** La contraseña no cumple los requisitos mínimos (< 8 caracteres, sin mayúscula, sin número).
* **Entonces (Then):** El sistema debe devolver un código HTTP `400 Bad Request`.
* **Y (And):** El mensaje debe detallar qué requisitos no se cumplen.
* **Y (And):** El token NO debe marcarse como usado (permitir reintentar).

**Escenario 6: Contraseña igual a la anterior**

* **Dado que (Given):** Un usuario intenta establecer una nueva contraseña.
* **Cuando (When):** La nueva contraseña es idéntica a la contraseña actual.
* **Entonces (Then):** El sistema debe devolver un código HTTP `400 Bad Request`.
* **Y (And):** El mensaje debe indicar "La nueva contraseña debe ser diferente a la actual".

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Endpoint público (el token actúa como autorización temporal).
  * La nueva contraseña debe cumplir requisitos: mínimo 8 caracteres, 1 mayúscula, 1 número.
  * Al cambiar contraseña, invalidar todas las sesiones activas (refresh tokens).
  * El token es de un solo uso.
* **Rendimiento / Rate Limiting:**
  * Máximo 5 intentos de reset por token (para permitir errores de validación).
  * Máximo 10 intentos por IP por hora.
* **Auditoría:**
  * Registrar en logs el evento de cambio de contraseña (sin incluir la contraseña).

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:**
  * `POST /api/v1/auth/reset-password`
* **Request Body:**

  ```json
  {
    "token": "uuid-reset-token",
    "newPassword": "NuevaContraseña123"
  }
  ```

* **Response Body (éxito):**

  ```json
  {
    "message": "Contraseña actualizada exitosamente. Ya puede iniciar sesión."
  }
  ```

* **Tablas de BD (Schemas):**
  * `menta_auth.password_reset_tokens` - Validar y marcar token como usado
  * `menta_auth.users` - Actualizar `password_hash`
  * `menta_auth.refresh_tokens` - Eliminar todos los tokens del usuario
* **Flujo de Seguridad:**
  1. Validar token existe y no expirado
  2. Validar token no usado
  3. Validar nueva contraseña cumple requisitos
  4. Validar nueva contraseña != contraseña actual
  5. Actualizar contraseña
  6. Marcar token como usado
  7. Invalidar refresh tokens
  8. Log del evento

---

## 5. Definition of Done (Criterios de Finalización)

* [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
* [ ] Se han escrito **Pruebas Unitarias** para validación de tokens y contraseñas.
* [ ] Se han escrito **Pruebas de Integración** para el endpoint REST.
* [ ] Se verifica que las sesiones existentes se invalidan correctamente.
* [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
* [ ] El código pasa la validación de Checkstyle y ArchUnit.
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
* [ ] Se registra el evento de cambio de contraseña en logs de auditoría.
