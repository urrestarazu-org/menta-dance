# Reglas de Arquitectura Ejecutables

## Límites de módulos

- `api:app` compone `auth`, `billing`, `virtual` y `physical`; cada módulo sigue
  `domain <- application <- infrastructure`.
- Un módulo sólo accede a su propia infraestructura/persistencia.
- Está prohibido usar FK, JOIN, repositorio, entidad JPA, HTTP, RabbitMQ, service
  discovery, M2M o circuit breaker para atravesar módulos.
- Los cruces se expresan como puertos Java o eventos internos declarados. `shared`
  sólo contiene contratos mínimos e inmutables, nunca lógica de dominio.

## Reglas ArchUnit

CI debe fallar cuando detecte:

1. dependencias desde `domain`/`application` a Spring, JPA o infraestructura;
2. dependencia de infraestructura de otro módulo;
3. repositorios, entidades o consultas SQL de otro módulo;
4. clientes HTTP/mensajería destinados a módulos internos;
5. controllers que acceden directamente a repositorios;
6. referencias a paquetes no públicos de otro módulo.

Las restricciones de esquema también se revisan sobre migraciones Flyway: no
puede existir una FK ni una consulta cross-module. Las excepciones requieren ADR
y una regla explícita, no un comentario.

## Verificación

La Fase 0 debe conectar estas reglas a `./gradlew check` y al workflow de CI.
