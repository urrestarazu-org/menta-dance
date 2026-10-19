# Guía de contribución con SDD

Esta guía describe el recorrido esperado desde una issue refinada hasta un Pull
Request verificable. Su objetivo es que una contribución conserve los contratos
funcionales, arquitectónicos y operativos del proyecto, no solamente que el
código compile.

SDD (*Specification-Driven Development*) es la ruta recomendada cuando una
tarea contiene decisiones o ambigüedad relevantes. No sustituye las historias
de usuario, los ADRs ni los contratos existentes: los conecta con una
implementación y con evidencia de que el resultado cumple lo acordado.

El ciclo completo es:

```text
Explorar → Proponer → Diseñar → Especificar → Planificar → Aplicar → Verificar → Archivar
```

## 1. Elegir la ruta de implementación

No toda modificación necesita crear artefactos SDD.

| Situación | Ruta |
|---|---|
| Decisión o verificación acotada a uno o tres archivos | Directa |
| Cambio mecánico en un único archivo, sin investigación ni decisión pendiente | Directa |
| Investigación que requiere recorrer cuatro o más archivos | Directa delegada |
| Escritura en dos o más archivos no triviales | Directa delegada |
| Cambio con ambigüedad sustancial, nuevos contratos, seguridad, pagos o impacto arquitectónico | Proponer SDD |
| El responsable de la tarea solicita SDD explícitamente | SDD |

La cantidad de líneas modificadas no decide por sí sola la ruta. SDD aporta
valor cuando permite acordar el resultado y sus límites antes de implementar.
No debe utilizarse para revestir de documentación una solución que ya está
completamente entendida.

## 2. Confirmar que la issue está preparada

Antes de comenzar, la issue debe estar en `Ready` y contener:

- un resultado observable, no una instrucción genérica como "hacer código";
- criterios de aceptación verificables;
- módulos y capas afectados;
- límites arquitectónicos que no deben romperse;
- comandos o recorridos con los que se validará el resultado;
- ADR vinculado cuando introduce una decisión de arquitectura o seguridad.

La definición completa del tablero y sus estados está en
[`project-board.md`](project-board.md). La historia de usuario correspondiente,
los contratos HTTP y los ADRs aceptados siguen siendo fuentes de verdad.

Si la issue no permite saber qué significa terminar, debe refinarse antes de
crear código o documentación SDD.

## 3. Preparar Git y el entorno

El desarrollo normal parte de `develop` y se realiza en una rama
`feature/<issue>-descripcion`. Un hotfix parte de `main` y utiliza
`hotfix/<issue>-descripcion`.

```bash
git fetch origin
git switch develop
git pull --ff-only origin develop
git switch -c feature/<issue>-descripcion
```

No se deben transportar cambios sin commit desde otra tarea. Si hay trabajo en
curso en el checkout actual, se debe crear un worktree independiente. Todo
worktree que vaya a consultar CodeGraph debe tener su propio índice `.codegraph/`;
el índice de otro checkout no se copia ni se enlaza.

Antes de implementar:

1. leer [`02-ARCHITECTURE.md`](02-ARCHITECTURE.md),
   [`27-CLEAN-ARCHITECTURE-GUIDE.md`](27-CLEAN-ARCHITECTURE-GUIDE.md) y los ADRs
   relevantes;
2. revisar la historia de usuario y los contratos del módulo;
3. comprobar que el entorno satisface
   [`24-LOCAL-DEV-SETUP-REQUIREMENTS.md`](24-LOCAL-DEV-SETUP-REQUIREMENTS.md);
4. usar CodeGraph antes de búsquedas amplias para comprender flujos,
   dependencias e impacto.

## 4. Construir los artefactos SDD

Cada cambio vive en `openspec/changes/<change-id>/`. El identificador debe ser
estable, descriptivo y usar *kebab-case*.

### 4.1 Exploración, cuando existe incertidumbre

`exploration.md` registra hechos encontrados, alternativas, restricciones y
preguntas abiertas. Es útil cuando todavía no se puede formular una propuesta
responsable. No es obligatorio para cambios cuyo contexto ya está demostrado.

La exploración no elige una solución por intuición: debe citar código,
contratos o decisiones existentes que sostengan sus conclusiones.

### 4.2 Propuesta

`proposal.md` define el resultado que se pretende entregar:

- intención y problema que resuelve;
- alcance y exclusiones explícitas;
- criterios de aceptación;
- riesgos y estrategia de rollback, especialmente para Auth, Billing y datos;
- issue, release o historia de usuario relacionada.

La propuesta explica **por qué** se justifica el cambio. No debe anticipar una
implementación detallada que todavía no fue diseñada.

### 4.3 Diseño

`design.md` explica **cómo** se respetarán los contratos:

- módulo propietario y capas afectadas;
- puertos, adaptadores y contratos entre módulos;
- decisiones consideradas y sus tradeoffs;
- secuencias para flujos complejos como autenticación, pagos y webhooks;
- seguridad, persistencia, idempotencia y observabilidad;
- estrategia de pruebas.

El diseño debe mantener `domain -> application -> infrastructure`: `domain` y
`application` no dependen de Spring, JPA ni de infraestructura. La colaboración
entre módulos se realiza mediante interfaces Java mínimas, normalmente en
`api:shared`; no mediante repositorios, entidades JPA, SQL, HTTP o mensajería
entre módulos.

### 4.4 Especificación

`specs/<capability>/spec.md` convierte la propuesta en comportamiento
verificable. Los requisitos usan `MUST`, `SHALL`, `SHOULD` o `MAY`, y los
escenarios se expresan con Given/When/Then.

Una especificación describe efectos observables, errores e invariantes. No
debe limitarse a enumerar clases que se crearán.

### 4.5 Plan de tareas

`tasks.md` divide el diseño en unidades que puedan terminarse y validarse en
una sesión. Cada tarea debe indicar:

- objetivo y evidencia esperada;
- archivos o área que posee;
- test que debe fallar o comportamiento que falta antes de implementar;
- reglas arquitectónicas y contratos afectados;
- commit esperado cuando resulte útil para mantener unidades revisables.

Las tareas utilizan numeración jerárquica (`1.1`, `1.2`, etc.) y se marcan como
completas únicamente cuando la implementación y su evidencia existen. Un
checkbox no reemplaza un test, un diff o un commit verificable.

## 5. Implementar una unidad de trabajo

La implementación avanza una tarea acotada por vez:

1. comprobar el estado del cambio y seleccionar la siguiente tarea;
2. definir la evidencia que demostrará el resultado;
3. escribir primero el test que caracteriza el comportamiento, cuando aplica;
4. implementar en el orden `domain`, `application`, `infrastructure`;
5. ejecutar la validación focalizada;
6. revisar el diff y actualizar `tasks.md`;
7. cerrar la unidad registrando la evidencia obtenida.

El runtime `gentle-ai sdd-attempt` limita cada intento mediante una unidad de
trabajo, un objetivo de evidencia y una revisión esperada. Sus operaciones
principales son `status`, `begin` y `finish`; los parámetros exactos deben
consultarse con `gentle-ai sdd-attempt <operación> --help`, porque incluyen
revisiones e identificadores que dependen del estado activo.

Receipt-driven development es independiente de SDD y está desactivado por
defecto. Solo se ejecutan revisiones con recibos cuando el usuario habilita
explícitamente `gentle-ai review mode enable`; no debe activarse como parte
implícita de una contribución.

## 6. Mantener contratos relacionados

Una tarea no está completa si el código y sus contratos cuentan historias
distintas.

### Cambios HTTP

Al agregar, modificar o eliminar un endpoint se debe evaluar y, cuando
corresponda, actualizar en el mismo cambio:

- contrato OpenAPI/Swagger;
- request, variables y aserciones de Bruno;
- seguridad, códigos de respuesta, errores y ejemplos;
- rate limits y observabilidad.

Si un contrato no se actualiza porque todavía no existe o no aplica, el motivo
debe quedar explícito en la entrega.

### Persistencia

- Flyway en `api:app` es el único componente que modifica el schema.
- Hibernate permanece en `ddl-auto:validate`.
- Las tablas usan el prefijo del módulo propietario.
- No existen foreign keys ni consultas entre prefijos de módulos.
- Una migración ya aplicada se corrige con una nueva migración, no editando el
  historial ni ejecutando rollback destructivo.

### Observabilidad

Los nuevos flujos deben preservar `correlationId`, logs estructurados y trazas.
Un error operativo importante debe ser diagnosticable sin registrar secretos,
tokens ni datos sensibles.

## 7. Probar por niveles

La validación comienza por la prueba más pequeña que pueda refutar el cambio y
se amplía hasta el gate final.

| Alcance | Evidencia mínima |
|---|---|
| Regla de dominio o caso de uso | Test unitario focalizado del módulo |
| Adaptador, persistencia o configuración Spring | Test de integración o slice correspondiente |
| Dependencias entre capas o módulos | ArchUnit |
| Endpoint HTTP | Tests del controller/integración, OpenAPI y Bruno |
| Journey entre componentes | Script E2E aislado y repetible |
| Cambio de infraestructura | Validación específica de Compose, Nginx, Dockerfile o script |
| Entrega completa | `./gradlew check` y checks de CI aplicables |

Comandos base:

```bash
# Test focalizado: preferirlo durante el ciclo de implementación
./gradlew :api:<modulo>:test --tests "<ClaseDeTest>"

# Tests del módulo
./gradlew :api:<modulo>:test

# Reglas arquitectónicas
./gradlew test --tests "*ArchitectureTest"

# Cobertura y quality gate
./gradlew jacocoTestReport
./gradlew check
```

La estrategia y los umbrales vigentes están en
[`14-TEST-STRATEGY.md`](14-TEST-STRATEGY.md). Los recorridos Bruno y E2E deben
ejecutarse según [`26-LOCAL-DEV-SETUP-HOWTO.md`](26-LOCAL-DEV-SETUP-HOWTO.md),
sin copiar secretos a archivos versionados ni destruir stacks ajenos.

Si un gate falla por infraestructura externa, se debe registrar el comando, la
salida relevante y el alcance que quedó sin comprobar. Un bloqueo ambiental no
autoriza a marcar el gate como aprobado ni a debilitar una aserción.

## 8. Verificar y archivar el cambio SDD

La verificación contrasta la implementación completa con la propuesta, la
especificación y los escenarios, no solamente con el último test ejecutado. El
reporte debe distinguir claramente:

- requisitos y escenarios demostrados;
- comandos ejecutados y sus resultados;
- hallazgos críticos o bloqueantes;
- warnings y riesgos residuales;
- evidencia que no pudo obtenerse por limitaciones ambientales.

Un resultado con warnings no equivale a un fallo, pero los warnings no se
ocultan: su aceptación debe quedar explícita. Un blocker o una discrepancia con
la especificación impide archivar el cambio como completado.

Cuando la verificación es aceptada, se archiva el directorio bajo
`openspec/changes/archive/<fecha>-<change-id>/`, se conserva el reporte de
verificación y la especificación vigente se sincroniza en `openspec/specs/`.
Archivar no significa borrar el diseño: preserva la trazabilidad entre la
decisión, la implementación y la evidencia.

## 9. Revisar antes del commit

Antes de crear cada commit:

```bash
git status --short
git diff --check
git diff
```

Además, se debe comprobar:

- que el diff corresponde únicamente a la tarea activa;
- que no se versionan secretos, archivos locales ni artefactos generados;
- que las interfaces públicas de `domain` y los puertos de `application`
  contienen Javadoc sobre responsabilidad, invariantes y límites relevantes;
- que los tests demuestran los criterios de aceptación y no detalles frágiles
  de implementación;
- que `tasks.md` representa el estado real.

Los commits siguen Conventional Commits y nunca incluyen atribución de IA:

```text
feat(billing): verify provider payment before fulfillment
test(e2e): cover duplicate payment webhook
docs(contributing): document SDD workflow
```

## 10. Abrir y completar el Pull Request

Antes de crear o actualizar el PR:

```bash
git fetch origin
git status --short
git branch --show-current
```

La rama debe estar actualizada respecto de su base remota. Las features apuntan
a `develop`; releases y hotfixes siguen el flujo definido en
[`adr/0018-cicd-github-actions-gitflow.md`](adr/0018-cicd-github-actions-gitflow.md).

El PR debe:

- explicar el resultado y las decisiones, no narrar archivo por archivo;
- incluir `Closes #<issue>` cuando corresponda;
- enumerar comandos ejecutados y resultados;
- declarar pruebas no ejecutadas y su motivo;
- señalar cambios en OpenAPI, Bruno, migraciones u observabilidad;
- esperar CI verde antes del merge por squash.

Nunca debe crearse un PR duplicado para la misma rama. Primero se verifica si
ya existe y, si apunta a una base incorrecta, se corrige el existente.

## 11. Definición de terminado

Una contribución está terminada cuando:

- [ ] los criterios de aceptación están demostrados;
- [ ] las tareas SDD representan el estado real;
- [ ] la verificación SDD no tiene blockers y sus warnings están registrados;
- [ ] el cambio SDD fue archivado cuando corresponde;
- [ ] se respetan los límites de capas y módulos;
- [ ] los tests focalizados están en verde;
- [ ] ArchUnit, cobertura y gates aplicables fueron ejecutados;
- [ ] OpenAPI y Bruno están sincronizados, o existe una justificación de N/A;
- [ ] observabilidad y migraciones fueron consideradas;
- [ ] el diff no contiene cambios ajenos ni secretos;
- [ ] la rama está actualizada respecto de su base;
- [ ] el PR está vinculado con la issue y CI está verde.

## Fuentes canónicas

- [`openspec/config.yaml`](../openspec/config.yaml): convenciones y comandos SDD.
- [`project-board.md`](project-board.md): estados, issue, branch y PR.
- [`02-ARCHITECTURE.md`](02-ARCHITECTURE.md): topología y ownership.
- [`27-CLEAN-ARCHITECTURE-GUIDE.md`](27-CLEAN-ARCHITECTURE-GUIDE.md): reglas por capa.
- [`14-TEST-STRATEGY.md`](14-TEST-STRATEGY.md): estrategia y cobertura.
- [`26-LOCAL-DEV-SETUP-HOWTO.md`](26-LOCAL-DEV-SETUP-HOWTO.md): ejecución local, Bruno y E2E.
- [`adr/0018-cicd-github-actions-gitflow.md`](adr/0018-cicd-github-actions-gitflow.md): Git Flow y CI/CD.
