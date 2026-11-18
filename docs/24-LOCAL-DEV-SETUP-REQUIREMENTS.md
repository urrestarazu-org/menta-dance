# Entorno de Desarrollo Local

## Requisitos

JDK 21, Docker Compose, Node 20.11.1, Bruno y el wrapper Gradle que se crea en Fase 0.
No se requiere Gradle global.

### Bruno (cliente API)

Bruno es el cliente HTTP oficial para pruebas manuales de endpoints. La colección
queda versionada en el repositorio bajo `bruno/`.

Instalación: `brew install bruno` (macOS) o descarga desde [usebruno.com](https://www.usebruno.com/)

### Swagger UI local (contratos OpenAPI)

La API **no** sirve Swagger UI ni `/v3/api-docs`: no usa `springdoc` y sus contratos
están escritos a mano en [`api/openapi/`](../api/openapi/README.md). Para leerlos en
el navegador, el repositorio trae un Swagger UI local y de solo lectura:

```bash
./scripts/api-docs.sh start     # sirve los contratos en http://localhost:8090
./scripts/api-docs.sh status
./scripts/api-docs.sh stop
```

- **Requisito:** Docker en ejecución (imagen oficial `swaggerapi/swagger-ui`, con
  versión fija). La primera vez descarga la imagen.
- **URL y puerto:** `http://localhost:8090`, publicado sólo en `127.0.0.1`. Se cambia
  con `API_DOCS_PORT=<puerto> ./scripts/api-docs.sh start`; el script rechaza los
  puertos que ya usa el proyecto (API 8081, BFF 8080, MySQL 3306, etc.).
- **Qué muestra:** un selector con cada `api/openapi/*-v1.yaml` (`auth`, `billing`,
  `catalog`, `physical`, `virtual`). Monta el directorio en solo lectura: un contrato
  nuevo aparece sin tocar el script.
- **Qué no hace:** no levanta ni detiene la API, el BFF ni la infraestructura, y
  "Try it out" está deshabilitado a propósito. Para llamar a la API real usá Bruno.
- Es idempotente: `start` con la UI ya levantada no hace nada y `stop` sin contenedor
  tampoco.

## Servicios locales

`docker compose up -d` debe iniciar como mínimo:

- MySQL 8.4 con database `menta`, usuario de aplicación no-root y volumen local;
- Redis con `maxmemory-policy noeviction`;
- Mailpit SMTP en `localhost:1025` y UI en `http://localhost:8025` para revisar
  enlaces de activación locales; los tokens se copian sólo a variables runtime
  de Bruno y nunca a archivos versionados;
- los auxiliares de observabilidad definidos para el entorno (OTel/Loki).

Las contraseñas se entregan por `.env` no versionado. El usuario root de MySQL
sólo se usa para inicialización del contenedor, nunca como datasource de la API.

## Configuración de API

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
    locations: classpath:db/migration
```

Flyway aplica las migraciones; Hibernate sólo valida. Una falla de migración se
investiga y corrige con una nueva migración, nunca con rollback automático.

## Comandos previstos del scaffold

```bash
docker compose up -d
./gradlew check
./gradlew :api:app:bootRun
./gradlew :bff:bootRun
```

Antes de que exista el scaffold, estos comandos son el contrato de Fase 0 y no
se consideran ejecutables todavía.

## Ver también

- [26-LOCAL-DEV-SETUP-HOWTO.md](26-LOCAL-DEV-SETUP-HOWTO.md) — guía práctica con scripts, troubleshooting y operación local paso a paso.
