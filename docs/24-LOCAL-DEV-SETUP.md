# Entorno de Desarrollo Local

## Requisitos

JDK 21, Docker Compose, Node 20.11.1, Bruno y el wrapper Gradle que se crea en Fase 0.
No se requiere Gradle global.

### Bruno (cliente API)

Bruno es el cliente HTTP oficial para pruebas manuales de endpoints, complementario
a Swagger UI. La colección queda versionada en el repositorio bajo `bruno/`.

Instalación: `brew install bruno` (macOS) o descarga desde [usebruno.com](https://www.usebruno.com/)

## Servicios locales

`docker compose up -d` debe iniciar como mínimo:

- MySQL 8.0 con database `menta`, usuario de aplicación no-root y volumen local;
- Redis con `maxmemory-policy noeviction`;
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
