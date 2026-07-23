# ADR-XXXX: [Título descriptivo, ej: Adoptar Patrón BFF]

**Estado:** [Propuesto | Aceptado | Rechazado | Deprecado | Reemplazado]
**Fecha:** [YYYY-MM-DD]
**Decisores:** [Lista de los involucrados en la decisión, ej: Arquitectos, Tech Leads]

## Contexto y Problema

[Describe el contexto técnico o de negocio y el problema específico que hace necesaria esta decisión. ¿Por qué necesitamos decidir esto ahora?]

## Factores Clave (Decision Drivers)

* [Restricción o requisito 1, ej: "Debe soportar la misma base de usuarios para dos sistemas distintos"]
* [Restricción o requisito 2, ej: "El costo de infraestructura extra no debe superar $X/mes para el MVP"]
* [Restricción o requisito 3, ej: "Capacidad actual del equipo centrada en Spring Boot"]

## Opciones Consideradas

### Opción 1: [Nombre de la alternativa 1 - Ej: Monolito Modular]

* **Descripción:** [Breve descripción de la implementación técnica]
* **Pros:**
  * [Ventaja 1]
* **Contras:**
  * [Desventaja 1]

### Opción 2: [Nombre de la alternativa 2 - Ej: Microservicios con BFF]

* **Descripción:** [Breve descripción]
* **Pros:**
  * [Ventaja 1]
* **Contras:**
  * [Desventaja 1]

*(Se deben listar todas las opciones realistas que se discutieron. Incluir siempre el "status quo" (dejar todo como está) si es una alternativa válida).*

## Decisión

Elegimos **[Opción Seleccionada]** porque **[justificación principal resumida]**.

## Justificación (Rationale)

[Explica en detalle por qué la Opción Seleccionada es superior a las demás en este contexto específico. Relaciona la justificación con los "Factores Clave" definidos arriba. Sé honesto sobre por qué se aceptaron las desventajas de esta opción].

## Consecuencias

### Positivas

* [Beneficio directo 1]
* [Beneficio directo 2]

### Negativas / Deuda Técnica

* [Complejidad operativa añadida]
* [Mantenimiento adicional, curva de aprendizaje]

### Implicaciones de Costos

* [Detalla el impacto económico: infraestructura adicional en la nube (ej: nuevos clústeres, gateways), horas de desarrollo adicionales, licencias necesarias, etc.]

### Riesgos y Reversibilidad

* **Riesgo Principal:** [Ej: Inconsistencia de datos por comunicación asíncrona]
* **Plan de Mitigación:** [Ej: Implementación del patrón Outbox o conciliación nocturna]
* **Reversibilidad:** [¿Qué tan difícil es deshacer esta decisión si resulta ser incorrecta en 6 meses? Describe el plan de rollback si es posible].

## Referencias y Decisiones Relacionadas

* [Enlace a tickets de Jira, pruebas de concepto, diagramas, etc.]
* [Complementa a: ADR-YYYY]
* [Reemplaza a: ADR-ZZZZ (si aplica)]
