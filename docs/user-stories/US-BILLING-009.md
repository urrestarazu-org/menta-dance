# US-BILLING-009: Administración y auditoría de pricing presencial

**ID:** US-BILLING-009  
**Título:** Configuración versionada de precio mensual y recargo individual  
**Módulo / API:** Billing  
**Prioridad (MoSCoW):** Must Have  
**Estado:** Draft  
**Épica:** EP-05 Administración

## 1. Historia de Usuario

> **Como** profesor responsable de un curso  
> **Quiero** definir su mensualidad y recargo individual  
> **Para** publicar precios trazables sin alterar cotizaciones existentes.

## 2. Criterios de Aceptación (BDD)

**Escenario 1: actualización válida**

- **Dado** un profesor responsable del curso con una configuración vigente.
- **Cuando** envío `PUT /api/v1/billing/physical-course-pricing/{courseId}` con
  `monthlyPrice`, `individualSurchargePercent` y motivo.
- **Entonces** Billing valida ownership del profesor mediante `api:app`, incrementa
  versión y agrega una revisión append-only con valores anteriores y nuevos.

**Escenario 2: recargo inválido**

- **Dado** un recargo menor o igual a cero.
- **Cuando** intento actualizar el pricing.
- **Entonces** responde `422 INVALID_INDIVIDUAL_SURCHARGE` y no crea una revisión.

**Escenario 3: cotización existente**

- **Dado** un quote persistido con una versión anterior del pricing.
- **Cuando** se publica una versión nueva.
- **Entonces** el snapshot y las Purchases existentes permanecen inmutables.

## 3. Requisitos No Funcionales y Restricciones

- Historial inmutable con profesor, motivo, timestamp y versión.
- Solo el profesor responsable o un administrador autorizado puede modificar.
- Lecturas públicas pueden cachearse; escrituras y auditoría no.

## 4. Notas Técnicas

La tabla `physical_course_pricing_revisions` es append-only. No se permiten
`UPDATE` o `DELETE` sobre revisiones desde la aplicación.

## 5. Definition of Done

- [ ] Pruebas de autorización, versión optimista y auditoría append-only.
- [ ] Pruebas de inmutabilidad de quotes existentes.
- [ ] Contrato OpenAPI actualizado.
