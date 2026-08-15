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
| In review | **Solo si hay PR abierto** vinculado a la issue | Automático (`project-sync.yml`) al abrir el PR, o manual |
| Done | **Solo con PR mergeado y CI verde** | Automático (workflow nativo del Project) al mergear el PR o cerrar la issue |

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

- PR abierto con `Closes #<issue>` en el body → issue vinculada pasa a
  `In review`

### ⚠️ Gap detectado: CI verde no está garantizado en el merge

Ni `main` ni `develop` tienen branch protection con *required status checks*
configurado hoy. Eso significa que un merge puede ocurrir sin que
`PR to Main (Full Pipeline)` o `PR to Develop (Fast Feedback)` hayan pasado, y
la automatización `Pull request mergeado → Done` no lo va a bloquear. Para que
"Done solo con CI verde" sea real (no solo una convención), hay que activar
branch protection en ambas ramas exigiendo esos checks.
