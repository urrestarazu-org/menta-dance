# ADR-0041: Un curso sin plan deniega, no otorga, acceso a lecciones protegidas

**Fecha:** 2026-08-30
**Estado:** Aceptado

## Contexto

`LessonAccessPolicy.decide` evaluaba una lección protegida en tres pasos: lección
gratuita pública, módulo preview público y, si el curso no pertenecía a ningún
plan de Billing (`!access.courseInAnyPlan()`), otorgaba acceso público
(`PUBLIC_UNPLANNED_COURSE`) **antes** de mirar el entitlement del caller.

Esa tercera regla tenía dos problemas de producción:

1. Cualquier curso publicado sin fila en `billing_plan_courses` quedaba
   permanentemente abierto para cualquier visitante anónimo, incluidas sus
   lecciones no gratuitas y no preview — un curso "sin plan" nunca fue pensado
   como una ruta pública explícita, sino como un estado de configuración
   comercial incompleto.
2. La rama disparaba **antes** de consultar `currentEntitlement`, así que un
   administrador que desvinculara un curso de su plan revocaba de forma
   inmediata el acceso de un suscriptor pago vigente cuyo entitlement estaba
   congelado (`CourseAccessSnapshot(false, true)`, hoy producido por
   `VirtualCourseEntitlementService:42`) — una regresión contra clientes que sí
   pagaron, y contradice la premisa de que el snapshot de Billing es la fuente
   de verdad congelada del entitlement.

## Decisión

Se elimina la rama `!access.courseInAnyPlan()` de `LessonAccessPolicy.decide`.
La cascada de acceso público pasa a ser exactamente: lección gratuita →
módulo preview → entitlement de Billing. Un curso ausente de todo plan ya no
es una regla de acceso público; cae en la misma verificación de entitlement
que un curso planeado y deniega por defecto para cualquier caller sin un
entitlement vigente — anónimo o autenticado.

`LessonAccessDecision.PUBLIC_UNPLANNED_COURSE` se elimina del enum: su único
productor era esta rama (`LessonAccessPolicy:52`) y su única aserción vivía en
`LessonAccessPolicyTest`; no había ningún `switch` sobre el valor en el resto
del código.

## Alternativas rechazadas

- **A6 alternativa — veto literal**: `if (!access.courseInAnyPlan()) return
  SUBSCRIPTION_REQUIRED;` evaluado *antes* de consultar el entitlement.
  Deniega correctamente al caller sin pagar, pero también revoca el
  entitlement congelado de un suscriptor pago apenas se desvincula el plan del
  curso — un curso puede desvincularse de un plan por razones puramente
  comerciales (reorganización de oferta) sin que el negocio pretenda cortar el
  acceso ya pagado. Se descarta porque introduce una regresión nueva contra
  clientes pagos que la eliminación de la rama no introduce.
- **Mantener la rama pública documentando el riesgo operativo**: rechazada de
  plano — es la causa raíz del leak que este ADR corrige, y "documentar el
  riesgo" no cierra el acceso público real y ya vigente en producción.

## Deuda técnica registrada (A7)

`CourseAccessSnapshot.courseInAnyPlan` se mantiene en el puerto compartido
(`api/shared`) aunque, tras esta decisión, ya no tiene ningún lector en
`virtual`. Eliminar el campo ampliaría el diff hacia `api/shared` y
`api/billing` (y sus tests), fuera del alcance de este cambio. Queda anotado
aquí como deuda de limpieza a resolver en un cambio posterior e independiente.

## Consecuencias

### Positivas

- Cierra el leak de acceso público no intencional en cursos sin plan
  comercial asociado.
- El entitlement congelado de un suscriptor pago sobrevive a un
  desvinculamiento de plan hecho por un administrador — comportamiento pinneado
  por el test `unplanned_course_still_honours_a_frozen_paid_entitlement`.
- La respuesta 403 es indistinguible entre "curso sin plan" y "curso planeado
  sin entitlement": un caller no puede usar la diferencia para enumerar el
  catálogo comercial.

### Negativas / Deuda técnica

- `CourseAccessSnapshot.courseInAnyPlan` queda sin lector en `virtual` (ver
  A7 arriba).
- Es un cambio de comportamiento de autorización en producción, no solo de
  tests: cualquier curso publicado hoy sin plan asociado deja de ser
  públicamente accesible en sus lecciones no gratuitas/no preview.

### Riesgos y Reversibilidad

- **Riesgo principal**: datos de producción reales no fueron auditados como
  parte de este cambio SDD — solo se verificó local/E2E. Es posible que
  existan cursos publicados hoy sin fila en `billing_plan_courses` cuyas
  lecciones protegidas dependan, sin que nadie lo haya notado, del
  comportamiento público anterior.
- **Release gate obligatorio antes de promover a producción**: ejecutar una
  consulta que liste los cursos publicados ausentes de
  `billing_plan_courses` y, para cada uno, o bien asociarlo a un plan o bien
  marcar sus lecciones afectadas como gratuitas/preview, antes de este
  cambio quedar activo en producción.
- **Reversibilidad**: alta. Revertir el commit de este slice restaura la
  rama y la constante del enum sin migración de datos ni estado persistido
  dependiente.

## Referencias y Decisiones Relacionadas

- Issue #129 (D7)
- `docs/adr/0040-local-bunny-net-signature-adapter.md` (adapter de firma
  local; comparte el mismo cambio SDD pero es una decisión independiente)
- `openspec/changes/local-bunny-net-adapter/design.md` (A6, A7, A8)
