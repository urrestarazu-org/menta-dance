# US-BILLING-012: Suscripción de prueba asignada por un administrador

**ID:** US-BILLING-012
**Título:** Trial de acceso temporal sin pago
**Módulos:** Billing
**Prioridad (MoSCoW):** Should Have
**Estado:** Draft
**Épica:** EP-03 Suscripciones

---

## 1. Historia de Usuario

> **Como** administrador
> **Quiero** asignarle a un alumno una suscripción de prueba por una cantidad de días
> **Para** que evalúe el contenido completo sin pagar, en casos comerciales puntuales.

No hay alta self-service de trials: **siempre la origina un administrador**. Un
alumno nunca puede darse un trial a sí mismo.

---

## 2. Criterios de Aceptación (BDD)

**Escenario 1: asignar un trial**

- **Dado** un administrador, un alumno y un plan `ACTIVE`.
- **Cuando** asigna un trial indicando el plan y la cantidad de días.
- **Entonces** se crea una `Subscription` en `ACTIVE` de tipo `TRIAL`, con
  `startDate` en el momento de la asignación y `endDate` a los días indicados.
- **Y** persiste el **mismo snapshot de cursos habilitados** que una suscripción
  paga (ver [US-BILLING-010](US-BILLING-010.md)).
- **Y** no se crea ningún `Payment`: un trial no tiene contraparte financiera.
- **Y** queda registrado qué administrador lo asignó y con qué motivo.

**Escenario 2: el trial da el mismo acceso que un pago**

- **Dado** un alumno con un trial `ACTIVE`.
- **Cuando** accede a un curso incluido en el snapshot de su suscripción.
- **Entonces** el acceso se concede exactamente igual que con una suscripción
  paga: la cascada de [US-VIRTUAL-007](US-VIRTUAL-007.md) **no distingue el
  tipo de suscripción**, sólo su estado y su snapshot.

**Escenario 3: vencimiento automático**

- **Dado** un trial `ACTIVE` cuyo `endDate` ya pasó.
- **Cuando** corre el proceso de vencimiento de suscripciones.
- **Entonces** la suscripción pasa a `EXPIRED` y el acceso cesa.
- **Y** no requiere ninguna acción manual de un administrador.

> **Divergencia deliberada.** Un sistema con este mismo modelo resuelve el
> vencimiento con un botón de admin ("expirar trials") y manda los trials
> vencidos a `CANCELLED`. Acá se hace distinto por dos razones: el vencimiento
> por paso del tiempo es `EXPIRED`, no `CANCELLED` — que está reservado para una
> decisión explícita (ver [US-BILLING-011](US-BILLING-011.md)) —; y el proyecto
> ya tiene planificación de tareas en producción, con lo cual depender de que
> alguien apriete un botón sería una fragilidad operativa evitable.

**Escenario 4: el alumno ya tiene una suscripción vigente**

- **Dado** un alumno con una `Subscription` en `ACTIVE`, sea `TRIAL` o `PAID`.
- **Cuando** un administrador intenta asignarle un trial.
- **Entonces** responde `409 SUBSCRIPTION_ALREADY_ACTIVE` y no crea nada.
- **Y** la respuesta informa la fecha de vencimiento de la vigente.

> Misma regla que US-BILLING-010: una sola suscripción vigente por alumno.

**Escenario 5: comprar después de un trial**

- **Dado** un alumno cuyo trial venció o fue cancelado.
- **Cuando** inicia una suscripción paga
  ([US-BILLING-010](US-BILLING-010.md)).
- **Entonces** el sistema lo permite y crea una fila nueva.
- **Y** el historial ([US-BILLING-004](US-BILLING-004.md)) muestra ambas,
  distinguibles por su tipo.

**Escenario 6: plan inexistente o inactivo**

- **Dado** un `planId` que no existe o cuyo plan está `INACTIVE`.
- **Cuando** el administrador intenta asignar el trial.
- **Entonces** responde `422 PLAN_NOT_AVAILABLE` sin crear la suscripción.

---

## 3. Requisitos No Funcionales y Restricciones

* **Seguridad / Autorización:**
  * Sólo `ADMIN` puede asignar trials. No existe endpoint público.
  * Toda asignación queda auditada: administrador, alumno, plan, días y motivo.
* **Reglas de negocio:**
  * **No hay límite de trials por alumno.** Un administrador puede asignar otro
    trial a quien ya tuvo uno; la restricción es sólo no tener una suscripción
    vigente (Escenario 4). Es una decisión consciente: el control es
    administrativo, no automático.
  * La cantidad de días la fija el administrador en cada asignación; no se
    deriva de `Plan.durationDays`, que aplica a las suscripciones pagas.

---

## 4. Notas Técnicas (Arquitectura)

* **Modelo:** requiere distinguir el tipo de suscripción (`TRIAL` / `PAID`) en
  `Subscription`. Hoy ese concepto no existe en el modelo.

* **Lo que NO cambia:** la cascada de acceso de US-VIRTUAL-007 permanece igual.
  El tipo de suscripción es dato de negocio y auditoría — **nunca** entra en la
  decisión de autorización, que sigue dependiendo sólo del estado y del
  snapshot. Cualquier regla futura del estilo "los trials sólo acceden a cursos
  destacados" sería un cambio de esa cascada y necesita su propia historia.

* **Vencimiento:** comparte mecanismo con las suscripciones pagas. No se
  construye un proceso aparte sólo para trials.

* **Invalidación de caché:** el vencimiento emite `SubscriptionStatusChanged`
  para el usuario afectado, igual que cualquier cambio de estado.

---

## 5. Definition of Done (Criterios de Finalización)

- [ ] La lógica implementa todos los Criterios de Aceptación (6 escenarios).
- [ ] Prueba de que un alumno no puede asignarse un trial a sí mismo.
- [ ] Prueba de que el trial concede el mismo acceso que una suscripción paga.
- [ ] Prueba de vencimiento automático a `EXPIRED`, sin intervención manual.
- [ ] Prueba de rechazo con suscripción vigente, sea `TRIAL` o `PAID`.
- [ ] Auditoría completa de la asignación.
- [ ] El endpoint está documentado en el contrato **OpenAPI/Swagger**.
- [ ] El código pasa la validación de Checkstyle y ArchUnit.
