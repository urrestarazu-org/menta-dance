# Contratos OpenAPI

Contratos HTTP versionados de la API, escritos a mano.

| Archivo | Alcance |
|---------|---------|
| `auth-v1.yaml` | Auth v1: sesión (login, refresh, logout) y activación de cuentas |
| `billing-v1.yaml` | Billing v1: catálogo público de planes de suscripción (US-BILLING-001) |

## Por qué a mano y no generado

Se evaluó `springdoc-openapi` y se descartó por dos motivos concretos:

1. **El diff es el artefacto de revisión.** Un contrato escrito se lee en el PR igual
   que la colección Bruno: se ve exactamente qué cambió en una respuesta o en un
   código de error. Un spec generado sólo se puede comparar después de generarlo.
2. **No agrega superficie expuesta.** `springdoc` publica `/v3/api-docs` y
   `/swagger-ui`, y la configuración de seguridad de este API es fail-closed por
   diseño. Sumar endpoints públicos de documentación es una decisión de seguridad
   propia, no un efecto colateral de querer documentar.

El costo aceptado es el mantenimiento manual. La mitigación es doble:

- `AGENTS.md` exige actualizar este contrato en el **mismo cambio** que modifique una
  ruta, método, parámetro, autenticación, respuesta, error o rate limit.
- El job `openapi-validation` del workflow de PR corre `redocly lint` sobre los 5
  contratos (`auth`, `billing`, `catalog`, `physical`, `virtual`) y falla ante
  errores estructurales. La lista es explícita: un contrato nuevo se agrega en
  `.github/workflows/pr-develop.yml`, o queda sin validar.

## Validación local

```bash
docker run --rm -v "$(pwd)/api/openapi:/spec:ro" \
  redocly/cli:2.59.0 lint /spec/auth-v1.yaml /spec/billing-v1.yaml \
  /spec/catalog-v1.yaml /spec/physical-v1.yaml /spec/virtual-v1.yaml
```

Dos warnings son esperados y aceptados:

- `info-license` — el repositorio no declara licencia.
- `no-server-example.com` — el servidor `localhost:8081` es el entorno de desarrollo
  real del proyecto y se documenta a propósito.

## Alcance

Estos archivos describen únicamente endpoints **implementados**. Los endpoints
planificados que figuran en [`docs/03-AUTH-API.md`](../../docs/03-AUTH-API.md)
(`logout-all`, `forgot-password`, `reset-password`, `change-password`, `me`,
`me/roles`) no aparecen acá hasta que existan: un contrato que promete rutas
inexistentes es peor que no tener contrato.

## Relación con Bruno

[`bruno/`](../../bruno) contiene requests ejecutables contra el API real; este
directorio contiene el contrato declarativo. Ambos deben actualizarse juntos ante
un cambio de endpoint.
