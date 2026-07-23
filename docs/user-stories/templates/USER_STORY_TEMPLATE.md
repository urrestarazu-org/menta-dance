# Plantilla de Historia de Usuario

**ID:** [Ej. US-AUTH-001]  
**Título:** [Un título corto y descriptivo, ej. Registro de nuevo alumno]  
**Módulo / API:** [Auth API | Virtual API | Physical API | Billing API | BFF]  
**Prioridad (MoSCoW):** [Must have | Should have | Could have | Won't have]  
**Estado:** [Draft | Ready for Dev | In Progress | In Testing | Done]

---

## 1. Historia de Usuario

> **Como** [rol del usuario, ej. usuario anónimo / administrador / alumno]
> **Quiero** [acción o funcionalidad que se desea realizar]
> **Para** [beneficio o valor de negocio que se obtiene]

---

## 2. Criterios de Aceptación (BDD)

Describe los escenarios y flujos (tanto exitosos como de error) utilizando el formato Gherkin (*Given-When-Then*). Esto elimina la ambigüedad y sirve como base directa para escribir las **Pruebas de Integración**.

**Escenario 1: [Ej. Flujo exitoso principal]**

* **Dado que (Given):** [El contexto inicial, ej. un usuario anónimo está en el endpoint `/api/v1/auth/register`]
* **Cuando (When):** [La acción, ej. envía un payload con email válido y contraseña segura]
* **Entonces (Then):** [El resultado, ej. el sistema guarda el usuario, devuelve un HTTP 201 Created y emite el evento para el envío del correo de bienvenida]

**Escenario 2: [Ej. Flujo de error - Email duplicado]**

* **Dado que (Given):** [ej. un usuario anónimo]
* **Cuando (When):** [ej. intenta registrar un email que ya existe en la tabla `auth_users`]
* **Entonces (Then):** [ej. el sistema rechaza la petición devolviendo un HTTP 409 Conflict con el mensaje "Email en uso"]

*(Agrega tantos escenarios como sean necesarios para cubrir validaciones, casos límite y errores).*

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:** [ej. Endpoint público, pero la contraseña debe guardarse cifrada con BCrypt].
* **Rendimiento / Rate Limiting:** [ej. Máximo 3 peticiones por hora por IP para evitar spam].
* **Datos y Validaciones:** [ej. La contraseña debe tener al menos 8 caracteres, 1 mayúscula y 1 número].

---

## 4. Notas Técnicas (Arquitectura)

* **Endpoints Involucrados:** [ej. `POST /api/v1/auth/register`]
* **Tablas de BD (Schemas):** [ej. `auth_users`, `auth_roles`]
* **Integraciones:** [ej. Si requiere llamar de forma síncrona a otra API, o si usa un servicio externo como Mercado Pago].

---

## 5. Definition of Done (Criterios de Finalización)

Para considerar esta Historia de Usuario como terminada (lista para despliegue), debe cumplir de forma estricta:

* [ ] La lógica implementa todos los Criterios de Aceptación.
* [ ] Se han escrito **Pruebas Unitarias** para la lógica de negocio (Mockeando BD).
* [ ] Se han escrito **Pruebas de Integración** probando la ruta HTTP real.
* [ ] El endpoint cumple con la guía de estilo REST y está en el contrato **OpenAPI/Swagger**.
* [ ] El endpoint se probó manualmente con **Bruno** y el request queda en `bruno/`.
* [ ] El código pasa la validación de Checkstyle y ArchUnit (Reglas de arquitectura).
* [ ] No introduce nuevos "Issues" severos en SonarCloud.
