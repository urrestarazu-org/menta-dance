# ADR-0028: Precheck de cupo y hold en checkout presencial

**Fecha:** 2026-07-22  
**Estado:** Aceptado

## Contexto

Las cotizaciones presenciales fijan precio, pero no reservan capacidad. El diseño
anterior podía permitir un pago confirmado y luego dejar la Purchase en
`EXCEPTION` cuando ya no había cupo. Necesitamos reducir ese caso sin convertir
una cotización en una reserva ni añadir una lista de espera.

Las mensualidades calculan cobertura y sesiones elegibles con el timestamp de
confirmación del proveedor. Por ello no es posible garantizar desde la cotización
que el conjunto final de sesiones será idéntico.

## Alternativas consideradas

| Alternativa | Ventajas | Desventajas |
|---|---|---|
| Mantener validación sólo al confirmar | Menor complejidad | Puede cobrar sin cupo y requiere gestión administrativa |
| Precheck sin reserva | Mejora la UX | Tiene carrera entre quote y checkout |
| Hold obligatorio y vinculante desde el quote | Garantía máxima | Convierte el quote en reserva, bloquea cupos y complica expiración |
| Precheck soft + hold atómico en checkout | Reduce pagos sin cupo con complejidad acotada | No elimina excepciones tras cambios de cobertura o expiración |

## Decisión

Adoptamos **precheck soft de disponibilidad al cotizar** y un **CapacityHold
atómico al iniciar checkout**.

- El quote informa disponibilidad proyectada, pero no reserva ni garantiza cupo.
- Antes de crear `Payment` o iniciar el proveedor, `api:app` solicita a Physical
  un hold técnico para la capacidad proyectada.
- Si el hold no puede crearse, se devuelve `409 CAPACITY_UNAVAILABLE` y no hay
  Payment ni movimiento externo.
- El hold cuenta contra disponibilidad, es invisible para el alumno y su TTL no
  supera el deadline del proveedor.
- Tras confirmar el proveedor, se revalida y convierte o ajusta el hold frente a
  las sesiones reales. Puede quedar `Purchase.EXCEPTION` sólo en el caso residual
  de expiración, concurrencia o cambio de cobertura mensual.

## Consecuencias

### Positivas

- Evita iniciar pagos cuando no hay capacidad al checkout.
- Mantiene las cotizaciones simples e informativas.
- Preserva la protección contra carrera mediante una reserva atómica y temporal.

### Negativas

- La disponibilidad del quote no es una promesa.
- Un hold expirado o una cobertura mensual distinta aún puede causar excepción.
- Requiere pruebas de TTL, último cupo, liberación y conversión idempotente.

## Referencias

- [Pagos de Clases Presenciales](../28-PHYSICAL-CLASS-PAYMENTS.md)
- [API de Facturación y Pagos](../06-BILLING-API.md)
- [US-PHYSICAL-004](../user-stories/US-PHYSICAL-004.md)
- [US-BILLING-006](../user-stories/US-BILLING-006.md)
