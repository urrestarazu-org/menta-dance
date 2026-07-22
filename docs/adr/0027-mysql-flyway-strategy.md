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
