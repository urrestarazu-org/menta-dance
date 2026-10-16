# ADR-0018: CI/CD con GitHub Actions y Git Flow

**Estado:** Aceptado
**Actualizado:** 2026-10-16 (#586): flujo de release simplificado y rama de producción `main`

## Decisión

GitHub Actions ejecuta CI/CD. Se aplica Git Flow: `feature/*` desde `develop`,
`release/*` de `develop` a `main` y vuelta a `develop`, `hotfix/*` desde
`main`. Los merges son squash y los commits siguen Conventional Commits 1.0.0.

CI verde es obligatorio para `develop` y `main`; los PR son registro de trabajo
individual y no requieren aprobación. Tras el merge `release/* -> main` se crea el
tag SemVer `vX.Y.Z` sobre `main`, y ese tag dispara el workflow de release: compila
y testea con `./gradlew build`, publica las imágenes en GHCR, adjunta los JAR a la
GitHub Release y despliega los digests inmutables en el servidor (ADR-0045). Un
merge `hotfix/* -> main` sigue el mismo flujo como release PATCH y luego vuelve a
`develop`.

El deploy hace un backup de MySQL, descarga las imágenes por digest, levanta el
stack y comprueba la salud. Si falla el health check, el script termina con error
y el rollback es manual: se vuelve a ejecutar con los digests de la release
anterior. Flyway no tiene rollback de esquema, así que si la release trajo una
migración incompatible se restaura el backup previo. El rollback automático
condicionado a evidencia de compatibilidad queda como mejora futura.

Los workflows de PR son read-only. Sólo release tiene `packages:write` y secretos
de despliegue, que viven en el Environment `production` de GitHub. Actions,
imágenes y dependencias se fijan a SHA/versiones exactas.
