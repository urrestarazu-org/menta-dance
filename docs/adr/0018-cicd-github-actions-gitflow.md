# ADR-0018: CI/CD con GitHub Actions y Git Flow

**Estado:** Aceptado

## Decisión

GitHub Actions ejecuta CI/CD. Se aplica Git Flow: `feature/*` desde `develop`,
`release/*` de `develop` a `master` y vuelta a `develop`, `hotfix/*` desde
`master`. Los merges son squash y los commits siguen Conventional Commits 1.0.0.

CI verde es obligatorio para `develop` y `master`; los PR son registro de trabajo
individual y no requieren aprobación. El merge `release/* -> master` crea el tag
SemVer, publica JAR en GitHub Packages e imagen en GHCR y despliega el digest
inmutable. Un merge `hotfix/* -> master` ejecuta el mismo flujo como release
PATCH y luego vuelve a `develop`. Si falla el health check, se restaura el digest
anterior sólo con evidencia de que la aplicación anterior funciona contra el
schema migrado y los datos escritos por la release nueva; de lo contrario se
requiere recuperación manual.

Los workflows de PR son read-only. Sólo release tiene `packages:write` y secretos
de despliegue. Actions, imágenes y dependencias se fijan a SHA/versiones exactas.
