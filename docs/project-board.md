# Tablero del proyecto

Tablero: [Menta Dance — Tablero](https://github.com/orgs/urrestarazu-org/projects/1)

## Regla central

**Una issue = un resultado verificable, no "hacer código".**
Si no se puede escribir una forma de validación objetiva (comando, test, pasos
reproducibles), la issue está mal planteada: hay que partirla o reformularla.

Toda decisión relevante (arquitectura, seguridad, cambios de contrato) debe
quedar registrada como issue o como [ADR](adr/README.md) vinculado desde la
issue. Nada de decisiones "invisibles" resueltas solo en un PR o en un chat.

## Estados

`Backlog → Ready → In progress → In review → Done`

| Estado | Significa | Quién mueve / cuándo |
|---|---|---|
| Backlog | Sin refinar | Automático al crear la issue |
| Ready | Objetivo, criterios de aceptación, límites arquitectónicos y forma de validación completos | Manual, tras refinamiento |
| In progress | Alguien está trabajando activamente | Manual, al tomar la issue |
| In review | **Solo si hay PR abierto** vinculado a la issue | Automático (`project-sync.yml`, polling cada 10 min), o manual |
| Done | **Solo con PR mergeado y CI verde** (branch protection lo exige) | Automático (workflow nativo del Project) al mergear el PR o cerrar la issue |

No mover una issue a `In review` sin PR abierto, ni a `Done` sin merge. Son
reglas de proceso: la automatización cubre el camino feliz, pero el equipo es
responsable de no forzar estados manualmente fuera de esas condiciones.

## Campos

| Campo | Valores |
|---|---|
| Módulo | auth, billing, virtual, physical, shared, app, bff, android, infra, docs |
| Tipo | feature, bug, architecture, security, tech-debt, docs, ci |
| Prioridad | Crítica, Alta, Media, Baja |
| Tamaño | XS, S, M, L, XL |
| Release | texto libre (ej. `v1.4.0`), vacío si no está planificada |
| Bloqueado por | issues que deben cerrarse antes (ej. `#12, #34`) |

Cada issue nueva se crea con el template
[`task.yml`](../.github/ISSUE_TEMPLATE/task.yml), que exige objetivo,
criterios de aceptación, límites arquitectónicos y forma de validación.
Aplicar además los labels `module:<módulo>` y `type:<tipo>` correspondientes
(no se autoaplican desde el formulario).

## Checklist técnico por issue

Para tareas que tocan código:

- Tests unitarios/integración agregados o actualizados
- ArchUnit verifica las capas tocadas (`domain ← application ← infrastructure`)
- Contrato OpenAPI/Bruno sincronizado, si expone o cambia un endpoint HTTP
  (ver regla en [CLAUDE.md](../CLAUDE.md))
- Observabilidad cubierta (logs/métricas/trazas), si aplica

Marcar como "N/A — `<motivo>`" lo que no aplique, nunca dejarlo en blanco.

## Vínculo branch / PR / ADR

Cada issue debe tener, antes de pasar a `In progress`:

- **Branch**: `feature/<issue>-descripcion` o `hotfix/<issue>-descripcion`
- **PR**: se completa al abrir el PR (usar `Closes #<issue>` en el body para
  que GitHub y `project-sync.yml` vinculen automáticamente)
- **ADR**: obligatorio para issues de tipo `architecture` o `security` con
  impacto real en el diseño

## Vistas

- **Kanban operativo**: board agrupado por Estado, todas las issues activas.
- **Backlog priorizado**: table filtrada por `Estado = Backlog`, ordenada por
  Prioridad.
- **Release roadmap**: board/table agrupado por Release.
- **Bugs**: table filtrada por `Tipo = bug`.

## Automatización

Nativa del Project (workflows internos, se activan una sola vez desde la UI):

- Item agregado → `Backlog`
- Item cerrado → `Done`
- Pull request mergeado → `Done`
- Auto-archivado de items en `Done` tras ~90 días de inactividad

Custom (`.github/workflows/project-sync.yml`):

- Cada 10 minutos, o al ejecutarlo manualmente, relee los vínculos reales de
  los PRs sin usar eventos de PR ni ejecutar código de ellos.
- Una issue ya presente en el Project con un PR abierto no-draft que la cierra
  pasa a `In review`.
- Una issue que sigue en `In review` sin PR abierto que la cierre vuelve a
  `Ready`, salvo que exista un PR mergeado vinculado: esos casos quedan a cargo
  de la automatización nativa del Project que los mueve a `Done`.

El workflow no agrega items al Project. Usa un `PROJECT_TOKEN` fine-grained
limitado a la escritura de este Project; no debe habilitar acceso de escritura
al repositorio, paquetes ni despliegues.

### Configuración reproducible

Para que otro maintainer pueda reconstruir esto si el Project se recrea:

1. **Auto-add de issues al tablero**: activar el workflow nativo del Project
   *"Auto-add to project"* con filtro `is:issue repo:urrestarazu-org/menta-dance`
   (UI del Project → `⋯` → Workflows). Sin esto, las issues nuevas no entran
   solas al tablero.

2. **`PROJECT_TOKEN`** (repo → Settings → Secrets and variables → Actions):
   fine-grained personal access token, scope de organización
   `urrestarazu-org`, con permisos:
   - Projects: **Read and write**
   - Issues: **Read-only** (para resolver `content.number`)
   - Pull requests: **Read-only** (para `closingIssuesReferences`)

   No requiere `contents` ni `packages`. Cargarlo como secret `PROJECT_TOKEN`.

3. **IDs hardcodeados en `project-sync.yml`**: el script referencia el Project,
   el campo `Status` y sus opciones por ID (no por nombre, porque el nombre
   puede cambiar). Si el Project se recrea, regenerarlos con:
   ```
   gh project view <número> --owner urrestarazu-org --format json -q '.id'
   gh project field-list <número> --owner urrestarazu-org --format json \
     -q '.fields[] | select(.name=="Status")'
   ```
   y actualizar `projectId`, `statusFieldId` y los `optionId` de
   `readyOptionId` / `inReviewOptionId` / `doneOptionId` en el workflow.

### Branch protection

`main` y `develop` exigen *required status checks* (`strict: true`, sin
aprobación obligatoria, según ADR-0018):

- `develop`: `quick-build-and-test`, `nginx-validation`,
  `docker-compose-validation`, `shellcheck`, `hadolint`
  (jobs de `pr-develop.yml`)
- `main`: `full-build-and-test`, `nginx-validation`,
  `docker-compose-validation`, `shellcheck`, `hadolint`, `nginx-integration`
  (jobs de `pr-main.yml`)

Si se agrega o renombra un job en esos workflows, hay que actualizar la lista
de checks obligatorios en la protección de la rama correspondiente
(`gh api repos/urrestarazu-org/menta-dance/branches/<rama>/protection`),
si no el job nuevo no bloquea el merge.
