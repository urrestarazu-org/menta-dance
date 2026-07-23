# Pipeline CI/CD

## GitHub Actions y permisos

GitHub Actions es la única plataforma de CI/CD. El workflow de Pull Request usa
`contents: read` y no recibe secretos de despliegue ni `packages:write`. El de
release recibe permisos mínimos, incluido `packages:write`, y secretos sólo
mediante GitHub Secrets.

Todas las actions se fijan a un **SHA de commit completo**; dependencias,
plugins e imágenes se fijan a versiones exactas. No se permiten `latest`,
`master` ni rangos de versión.

## CI de Pull Request

En cada PR contra `develop` o `master`:

1. compilar backend, BFF y Android;
2. ejecutar Checkstyle, tests, JaCoCo, SonarCloud y ArchUnit;
3. construir assets Node/Tailwind con lockfile;
4. ejecutar Playwright, Gatling acordado y Trivy;
5. publicar resultados sin publicar artefactos ni desplegar.

CI verde es obligatorio para mergear. Los PR son registro de trabajo individual:
no requieren aprobación humana y se integran con **squash merge**.

## Git Flow y releases

- `feature/*` nace de `develop`.
- `release/*` nace de `develop` y se mergea a `master`.
- Después, `master` se mergea de regreso a `develop`.
- `hotfix/*` nace de `master`; su merge vuelve también a `develop`.

Los commits y títulos de PR siguen Conventional Commits 1.0.0. La versión de
release es SemVer `vMAJOR.MINOR.PATCH` y etiqueta el mismo commit, JAR, imagen y
GitHub Release.

## Release a producción

El merge `release/* -> master` o `hotfix/* -> master` dispara automáticamente:

1. crear tag SemVer;
2. publicar el JAR versionado en GitHub Packages y la imagen en GHCR;
3. registrar y desplegar el **digest inmutable** de la imagen, nunca una tag;
4. ejecutar migraciones Flyway hacia adelante, etiquetadas como
   `backward-compatible` o `manual-recovery-only`, y health checks;
5. si falla el health check, volver automáticamente al digest de la release
   anterior.

No existe rollback automático de base de datos. Si Flyway falla, se detiene la
release y se interviene manualmente. El rollback automático de imagen sólo se
permite con evidencia de compatibilidad: la aplicación anterior debe funcionar
contra el schema migrado y los datos escritos por la release nueva. La etiqueta
`backward-compatible` no sustituye esa prueba; sin evidencia se detiene para
recuperación manual. Las migraciones destructivas se ejecutan sólo en una release
posterior a dejar de usar los datos/columnas.
