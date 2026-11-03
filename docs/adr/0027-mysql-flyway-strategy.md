# ADR-0027: MySQL Único y Flyway Forward-Only

**Estado:** Aceptado

## Decisión

El MVP usa un datasource y schema MySQL `menta`; las tablas llevan prefijo por
módulo. Flyway reside en `:api:app`, es la única autoridad de DDL y Hibernate
usa `ddl-auto:validate`.

No se permiten FKs, JOINs, entidades, repositorios ni consultas entre módulos.
Las referencias externas se conservan como IDs y se validan a través de puertos
o eventos internos. ArchUnit y revisión de migraciones lo hacen cumplir.

Las migraciones son inmutables y sólo hacia adelante. Ante un fallo de Flyway se
detiene la release e interviene una persona; no hay rollback automático de BD.
Cada release etiqueta sus migraciones como `backward-compatible` o
`manual-recovery-only`. El rollback automático de aplicación se permite sólo en
el primer caso cuando además existe evidencia de que la aplicación anterior opera
contra el schema migrado y los datos escritos por la release nueva.
Las eliminaciones de tablas/columnas/datos usan expand/contract y se difieren a
una release posterior a dejar de usarlos.

## Actualización (#449): MySQL 8.4 LTS en tests y entornos locales

**Decisión.** Producción ya corría `mysql:8.4.11` (#589), mientras que los tests de
Testcontainers y los `docker-compose*.yml` locales seguían en `mysql:8.0.46`: las
migraciones se probaban en un motor distinto del que se despliega, y la línea 8.0
llegó a fin de vida el 2026-04-30. Todos los entornos pasan a **`mysql:8.4.11`**, el
mismo tag exacto de producción (sin `latest` ni líneas flotantes).

- La imagen de los tests está en **un solo lugar**: `MySqlTestImage.NAME`
  (`config/test-support/java/com/menta/testsupport/`). El `build.gradle.kts` raíz agrega esa
  carpeta como fuente de test de cada módulo `:api:*`; no hay dependencias ni artefactos nuevos.
- `scripts/verify-local-infrastructure-contract.sh` (parte de `./gradlew check`) falla si
  algún compose, incluido el de producción, difiere de esa constante o vuelve a `8.0`.
- Alternativas descartadas: una propiedad de sistema inyectada por Gradle (obliga a cada
  test a leer una clave de texto y a decidir un valor por defecto, que sería un segundo literal);
  `java-test-fixtures` en `:api:shared` (agrega un plugin, una configuración nueva de
  dependencias y bloqueos de dependencias por cada módulo); y una clase en el `main` de
  `:api:shared` (pondría código de test en el artefacto de producción).

**Qué cambia de 8.0 a 8.4 para este proyecto** (manual oficial de MySQL 8.4,
["What Is New in MySQL 8.4 since MySQL 8.0"](https://dev.mysql.com/doc/refman/8.4/en/mysql-nutshell.html)
y ["Changes in MySQL 8.4"](https://dev.mysql.com/doc/refman/8.4/en/upgrading-from-previous-series.html)):

- **`mysql_native_password` ya no está habilitado por defecto**: «Beginning with MySQL 8.4.0, the
  deprecated `mysql_native_password` authentication plugin is no longer enabled by default.»
  El repositorio no lo usa: ningún compose, URL JDBC ni script lo menciona, y los usuarios
  (`MYSQL_USER`/`MYSQL_PASSWORD` de la imagen) se crean con el plugin por defecto,
  `caching_sha2_password`, que ya era el de 8.0. También se eliminó `default_authentication_plugin`
  (se reemplaza por `authentication_policy`); ningún compose lo fija.
- **`caching_sha2_password` y `allowPublicKeyRetrieval`**: sobre una conexión sin TLS, la
  primera conexión de una cuenta nueva exige intercambio con clave pública RSA y «the server
  does not send the RSA public key to clients by default»
  ([doc](https://dev.mysql.com/doc/refman/8.4/en/caching-sha2-pluggable-authentication.html)).
  Con Connector/J eso se pide con `allowPublicKeyRetrieval=true`, como ya hacen producción, el
  compose de nginx y `MySqlSchemas` en los tests. No es un cambio de 8.0 a 8.4 (8.0 ya usaba
  este plugin), pero explica por qué esa opción es necesaria.
- **Valores por defecto de InnoDB**: cambian, entre otros, `innodb_adaptive_hash_index`
  (`ON` a `OFF`), `innodb_change_buffering` (`all` a `none`), `innodb_io_capacity` (200 a
  10000), `innodb_log_buffer_size` (16 MiB a 64 MiB), `innodb_flush_method` (`O_DIRECT` si
  está disponible) y `innodb_doublewrite_*`. Afectan rendimiento, no el esquema ni la
  semántica SQL; los composes no fijan ninguno, así que ahora local, tests y producción
  comparten los mismos valores por defecto.
- **Claves foráneas no estándar**: `restrict_fk_on_non_standard_key` es `ON` por defecto
  y rechaza FKs que no apuntan a una clave estándar. Las migraciones aplicadas no se tocan; la
  las 20 pruebas de migración de `:api:app` (`*MigrationIntegrationTest`) y el resto de las suites pasaron sin cambios sobre 8.4.11.
- **Modo SQL por defecto**: `ONLY_FULL_GROUP_BY`, `STRICT_TRANS_TABLES`, `NO_ZERO_IN_DATE`,
  `NO_ZERO_DATE`, `ERROR_FOR_DIVISION_BY_ZERO`, `NO_ENGINE_SUBSTITUTION`
  ([doc](https://dev.mysql.com/doc/refman/8.4/en/sql-mode.html)): igual que en 8.0.
- **Otros cambios sin efecto aquí**: eliminación de la sintaxis `MASTER`/`SLAVE`, de
  `mysql_upgrade`, `mysqlpump`, `expire_logs_days` y del soporte de identificadores sin
  comillas con `$` inicial y más de un `$`; `AUTO_INCREMENT` en columnas de punto flotante
  ahora es error; nuevas palabras reservadas (`QUALIFY`, `PARALLEL`, `TABLESAMPLE`,
  `MANUAL`). El proyecto no usa replicación y no las requiere.
- **Compatibilidad de herramientas**: Hibernate ORM 6.6 (`MySQLDialect`, base mínima 8.0;
  [doc](https://docs.hibernate.org/orm/6.6/dialect/dialect.html)) y Connector/J 9.x (el
  manual actual del conector declara soporte de MySQL 8.4 o superior). Flyway 11.7.2 no
  lista 8.4 en su
  [matriz oficial](https://documentation.red-gate.com/fd/supported-databases-and-versions-143754067.html)
  (llega a 8.3 y 9.4), de modo que la evidencia es empírica: producción ya aplica las
  migraciones con 8.4.11 y la suite de migraciones de `:api:app` corre ahora contra 8.4.11.

**Volúmenes locales existentes.** Verificado en esta migración: `mysql:8.4.11` **sí abre**
un directorio de datos inicializado por `mysql:8.0.46` y lo actualiza en el lugar (la ruta
8.0.x a 8.4.x LTS está soportada,
[doc](https://dev.mysql.com/doc/refman/8.4/en/upgrade-paths.html)). Lo que **no** funciona es
volver atrás: una vez actualizado el volumen, `mysql:8.0.46` falla con `Invalid MySQL server
downgrade: Cannot downgrade from 80411 to 80046` y no arranca. Por eso, al cambiar de rama
conviene recrear el volumen en lugar de compartirlo entre ramas con y sin este cambio.

Para recrear la base local (**ADVERTENCIA: borra todos los datos locales de MySQL**; el
esquema vuelve a crearse con Flyway al arrancar la API):

```bash
# Base de desarrollo (infra/docker/database)
docker compose -f infra/docker/database/docker-compose.yml down -v
docker compose -f infra/docker/database/docker-compose.yml up -d

# Compose de la raíz
docker compose down -v && docker compose up -d
```

`down -v` elimina también los volúmenes de Redis, Grafana y Loki de ese compose; si solo
se quiere reiniciar MySQL: `docker compose stop mysql && docker compose rm -f mysql &&
docker volume rm <proyecto>_mysql_data` (el nombre exacto se ve con `docker volume ls`).
