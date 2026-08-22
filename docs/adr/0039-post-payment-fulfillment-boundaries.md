# ADR-0039: Límites del fulfillment post-pago

**Fecha:** 2026-08-22
**Estado:** Aceptado

## Contexto

Billing verificaba el pago y luego intentaba empujar dos efectos hacia otros
módulos: otorgar acceso en Virtual y asignar cupo en Physical. Ambos adapters
eran placeholders que lanzaban excepciones, por lo que un pago acreditado podía
quedar sin entrega. Además, ADR-0028 ya asigna la orquestación física a
`api:app` y US-VIRTUAL-007 define a Billing como la fuente de verdad del
derecho virtual.

## Decisión

- Virtual consulta un contrato Java compartido de entitlement implementado por
  Billing. Billing responde desde la suscripción `ACTIVE`, vigente y su
  snapshot de cursos; no escribe estado dentro de Virtual.
- La verificación del pago activa y congela el snapshot de la suscripción en
  Billing. No depende de un adapter de grant externo para el camino feliz.
- La orquestación física (hold, conversión y asignación) queda fuera del core
  de Billing y vive en `api:app`, según ADR-0028. Su implementación concreta
  corresponde a #41.
- Una suscripción histórica en `ACTIVE/EXCEPTION` se recupera de forma
  idempotente al reprocesar un pago confirmado, sin volver a contactar Virtual.

## Consecuencias

- Se retiran los puertos push y sus placeholders de Billing.
- `Payment` permanece `COMPLETED` aunque el flujo físico posterior requiera
  tratamiento administrativo; liquidación y entrega son conceptos distintos.
- Virtual podrá invalidar su caché por usuario, pero la consulta a Billing es
  siempre la fuente de verdad.

## Referencias

- #106
- ADR-0028
- ADR-0038
- US-VIRTUAL-007
