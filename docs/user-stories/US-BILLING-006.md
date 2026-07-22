# US-BILLING-006: Cotización de curso presencial

**ID:** US-BILLING-006  
**Título:** Cotización mensual o individual de clases presenciales  
**Módulo / API:** Billing  
**Prioridad (MoSCoW):** Must Have  
**Estado:** Draft  
**Épica:** EP-03 Suscripciones y pagos

## 1. Historia de Usuario

> **Como** alumno  
> **Quiero** obtener una cotización persistible para una modalidad concreta  
> **Para** conocer exactamente qué sesiones, precio y vigencia voy a pagar.

## 2. Criterios de Aceptación (BDD)

**Escenario 1: cotización mensual**

- **Dado** un curso con precio, recargo positivo y sesiones disponibles.
- **Cuando** envío `POST /api/v1/billing/physical/quotes` con
  `purchaseType: MONTHLY`.
- **Entonces** Billing persiste un quote con el importe mensual y parámetros de
  precio snapshot, sin fijar cobertura ni `sessionIds`; éstos se calculan al
  confirmarse el pago. El quote no tiene `selectedSessionId` e incluye una
  proyección informativa de disponibilidad, sin reservar cupos.

**Escenario 2: cotización individual**

- **Dado** una sesión concreta programada.
- **Cuando** solicito `purchaseType: INDIVIDUAL` con `selectedSessionId`.
- **Entonces** se emite un quote independiente cuyo total aplica el recargo del
  curso; contiene un solo `sessionId`, pero `scheduledSessionCount` conserva la
  cantidad del período usada como divisor. No se devuelven alternativas ambiguas
  en el mismo quote.

**Escenario 3: período sin sesiones**

- **Dado** un período mensual sin sesiones `SCHEDULED`.
- **Cuando** solicito una cotización para el curso.
- **Entonces** responde `422 NO_SCHEDULED_SESSIONS` y no persiste un quote pagable.

**Escenario 4: cupo no disponible en la proyección**

- **Dado** sesiones programadas sin disponibilidad proyectada.
- **Cuando** solicito una cotización.
- **Entonces** el quote puede informar `availability: UNAVAILABLE`, sin reservar
  capacidad ni prometer que su estado se mantendrá hasta el checkout.

## 3. Requisitos No Funcionales y Restricciones

- Billing fija la fecha autoritativa; el cliente no puede enviarla.
- División y recargo con alta precisión en memoria; cada resultado monetario se
  redondea `HALF_UP` a dos decimales. `individualPrice` debe ser al menos
  `effectiveRounded + currencyMinorUnit` después del redondeo.
- El snapshot persiste los operandos redondeados y configurados necesarios para
  auditar/recomputar; no persiste cocientes periódicos como si fueran exactos.
- El quote es informativo, inmutable y válido por una hora; no reserva cupo. Un
  pago no iniciado antes de expirar requiere otro quote. Sólo el importe y sus
  parámetros de precio quedan fijados; cobertura y sesiones elegibles se
  calculan/revalidan desde el timestamp de confirmación.
- La proyección de disponibilidad sólo orienta la UI. El checkout crea un hold
  atómico o rechaza con `409 CAPACITY_UNAVAILABLE` antes de crear un Payment.

## 4. Notas Técnicas

```json
{
  "courseId": "b6bb98d6-179e-49d0-9dda-6c03a16998f0",
  "purchaseType": "INDIVIDUAL",
  "selectedSessionId": 101
}
```

Billing obtiene sesiones y capacidad mediante contratos neutrales de `shared` y
orquestación de `api:app`; ningún core importa al otro.

## 5. Definition of Done

- [ ] Casos normativos de septiembre y octubre de 2026.
- [ ] Constraints de tipo/sesión, recargo y sesiones mensuales.
- [ ] Contrato OpenAPI y pruebas de expiración.
