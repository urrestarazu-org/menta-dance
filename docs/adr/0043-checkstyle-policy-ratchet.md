# ADR-0043: Política de Checkstyle y trinquete

**Fecha:** 2026-10-03
**Estado:** Aceptado
**Decisores:** Alejandro Urrestarazu

## Contexto y Problema

Checkstyle corre en los 7 módulos JVM (`api:shared`, `api:auth`,
`api:billing`, `api:virtual`, `api:physical`, `api:app` y `bff`), con dos
tareas por módulo (`checkstyleMain` y `checkstyleTest`): 14 tareas. Hasta
#298 el gate era sólo de apariencia. `config/checkstyle/google_checks.xml`
fija la severidad por defecto en `warning` (`org.checkstyle.google.severity`)
y ninguna tarea declara `maxWarnings`, de modo que nada falla nunca. En
`develop` @ `56ed6e4` el estado medido era de **6768 advertencias** (main 2793
/ test 3975) y crecía sin control.

Además, ADR-0042 afirma que Checkstyle "falla el build". Esa afirmación es
falsa: con severidad `warning` y sin umbral, ninguna cantidad de hallazgos
rompe el build. La documentación aparentaba un control que no existía.

La política de estilo vigente también tenía fricciones propias que inflaban
el número sin aportar calidad:

- `LineLength` en 100 sobre un código que ya se escribe a 120.
- Los métodos de test con `snake_case` (unos 2703 métodos en 432 archivos)
  son una convención deliberada pero nunca documentada; sólo una supresión
  parcial los cubría.
- Javadoc exigido en constructores, métodos con anotaciones de framework
  (`@Bean`, `@GetMapping`, ...) y tipos anidados.
- `config/checkstyle/suppressions.xml` no se cargaba nunca: la propiedad
  `org.checkstyle.google.suppressionfilter.config` no se define en ningún
  lado, y el valor por defecto (`checkstyle-suppressions.xml`) es un archivo
  que no existe (el filtro es `optional`). El archivo era código muerto.

## Factores Clave (Decision Drivers)

* Que ningún PR pueda agregar deuda de estilo sin que el build lo note, desde
  la primera fusión del cambio.
* No exigir un PR único gigante: 6768 advertencias no caben en una revisión.
* Que el trinquete sea nativo de Gradle y barato de mantener (sin tarea
  personalizada, sin parseo de reportes, sin dependencias nuevas).
* Que el mecanismo sea temporal: el estado final es "cualquier hallazgo falla
  el build" sin mapa de techos.
* Que las reglas que quedan reflejen lo que el equipo realmente quiere
  exigir, no el ruido heredado del ruleset de Google.

## Opciones Consideradas

### Opción 1: Status quo (warning-only)

* **Descripción:** dejar Checkstyle como está.
* **Pros:**
  * Cero trabajo.
* **Contras:**
  * La deuda sigue creciendo y la documentación sigue mintiendo.

### Opción 2: Big-bang (corregir todo en un PR y pasar a `error`)

* **Descripción:** arreglar las 6768 advertencias en un solo cambio.
* **Pros:**
  * Estado final inmediato.
* **Contras:**
  * Imposible de revisar; choca con todo PR abierto; riesgo alto de mezclar
    cambios de comportamiento con cambios de formato.

### Opción 3: Techos por tarea (trinquete nativo de Gradle) [elegida]

* **Descripción:** un mapa `checkstyleWarningCeiling` en el
  `build.gradle.kts` raíz, con una clave por tarea (`:api:auth:checkstyleTest`
  y las otras 13) y `maxWarnings` configurado desde él. Una tarea sin clave
  falla al configurarse. Un módulo que llega a 0 pasa a `checkstyleStrictModules`
  y corre con severidad `error`. Una regla que llega a 0 en las 14 tareas se
  bloquea con `severity=error` en `google_checks.xml`.
* **Pros:**
  * Nativo: `maxWarnings` es un input de la tarea (cambiarlo invalida la
    caché) y Gradle ya reporta `[warning:N]`.
  * Rompe CI desde el primer día ante cualquier advertencia nueva por encima
    del techo.
  * Se borra por completo al final, editando un solo archivo.
* **Contras:**
  * Admite holgura: una tarea con 90 advertencias y techo 95 pasa.
  * No detecta el intercambio de reglas (ver Justificación).

### Opción 4: Supresiones de baseline

* **Descripción:** suprimir cada hallazgo existente (archivo de baseline o
  comentarios) y dejar `error` para lo nuevo.
* **Pros:**
  * Cualquier hallazgo nuevo falla de inmediato.
* **Contras:**
  * Miles de supresiones frágiles a cualquier edición de línea; la deuda
    queda escondida en lugar de medida; limpiar exige tocar la supresión y
    el código.

### Opción 5: Herramienta de diff (sólo líneas cambiadas)

* **Descripción:** ejecutar Checkstyle sólo sobre las líneas tocadas por el PR.
* **Pros:**
  * No hay baseline que mantener.
* **Contras:**
  * Dependencia externa y paso extra de CI; no mide ni reduce el total;
    depende del diff base y es difícil de reproducir en local.

## Decisión

Elegimos **techos por tarea con trinquete nativo de Gradle** (Opción 3)
porque da un gate que rompe CI desde el primer PR, mide la deuda en lugar de
esconderla, y se retira editando un solo archivo cuando el conteo llega a 0.

La política de estilo que acompaña al trinquete queda así:

1. **`LineLength` en 120**, manteniendo el `ignorePattern` existente
   (`package`, `import`, `href`, URLs).
2. **Nombres de test:** `snake_case` en métodos `@Test` se **tolera, no se
   endosa**. La supresión de `MethodName` acepta nombres que cumplen
   `[A-Za-z][A-Za-z0-9]*(?:_[A-Za-z0-9]+)*` y `AbbreviationAsWordInName` se
   suprime en métodos `@Test`. La migración a `camelCase` queda **diferida**
   (unas 5400 líneas de cambio; no está autorizada ni planificada).
3. **Alcance de Javadoc:** `MissingJavadocMethod` no aplica a constructores
   ni a constructores compactos, ni a métodos anotados con `Bean`,
   `GetMapping`, `PostMapping`, `PutMapping`, `PatchMapping`, `DeleteMapping`,
   `RequestMapping`, `ExceptionHandler` o `Scheduled`. `MissingJavadocType` no
   aplica a tipos anidados. Los tipos de nivel superior y los métodos públicos
   o protegidos comunes siguen exigiendo Javadoc.
4. **Una sola superficie de supresión:** las supresiones viven únicamente
   dentro de `config/checkstyle/google_checks.xml`. `suppressions.xml` se
   elimina. Los filtros opcionales del ruleset upstream (`SuppressionFilter`,
   `SuppressionXpathFilter`) se dejan intactos para minimizar la divergencia.

Con estos cambios de política (sin tocar código fuente) las 6768
advertencias bajan a **1866** (main 1206 / test 660), medido con
`--rerun-tasks --no-build-cache` sobre la configuración final. Esos 14 conteos
son los techos iniciales.

## Justificación (Rationale)

El trinquete se apoya en `maxWarnings` de la tarea `Checkstyle`: Gradle falla
cuando el total de advertencias supera el techo y reporta
`[warning:N]` en el mensaje. La clave del mapa es el *path* de la tarea
(`:api:auth:checkstyleTest`), de modo que coincide con la lista de tareas de
CI y con lo que Gradle imprime, sin parsear *source sets*. Una tarea sin
clave falla al configurarse con un mensaje que nombra la tarea y este ADR:
una tarea nueva (por ejemplo, un *source set* nuevo) nunca corre sin límite.

La severidad por módulo se cambia con `configProperties` de la extensión
`checkstyle` (`org.checkstyle.google.severity` = `error`) y **no** con una
propiedad de sistema `-D`: pasar `-Dorg.checkstyle.google.severity=error` no
cambia el resultado ni el conteo (verificado empíricamente en #298).
Si un módulo estricto conserva su clave de techo, gana el modo estricto: la
clave se ignora y se emite una advertencia de que está obsoleta.

**Límites conocidos, aceptados:**

* **Holgura:** el techo sólo falla cuando el conteo lo *supera*. Un PR que
  baja un hallazgo sin actualizar el mapa deja holgura que otro PR podría
  consumir. No se implementa "fallar por holgura" (haría falta parsear
  reportes, se saltaría con `FROM-CACHE`, y obligaría a cada PR de feature
  que elimine una advertencia a editar el mapa, multiplicando los conflictos
  entre PRs apilados). Se compensa por proceso: cada PR de limpieza
  re-mide y confirma el conteo exacto de las tareas que toca, con la tabla de
  medición en el cuerpo del PR.
* **Intercambio de reglas:** el techo es por tarea, no por regla. Corregir un
  hallazgo de una regla y agregar uno de otra dentro de la misma tarea deja
  el total igual. Lo mitigan los bloqueos por regla (una regla en 0 que pasa
  a `error` no consume techo) y la activación del modo estricto por módulo.

**Dependencia del mensaje en inglés.** La supresión de `MethodName` para
`@Test` filtra por `message` con una expresión regular sobre el texto
`Method name 'x' must match pattern 'y'.`, que está *fijado* en
`google_checks.xml` mediante la clave `name.invalidPattern`. Si ese texto
cambia (al editar la configuración o al actualizar Checkstyle), la supresión
deja de coincidir sin aviso y los hallazgos reaparecen. No es silencioso: los
hallazgos nuevos superan el techo y el build falla, pero el origen no es
evidente a primera vista. Por eso el comentario junto a la supresión
nombra esta fragilidad, y toda actualización de versión de Checkstyle debe
revisarla.

## Consecuencias

### Positivas

* Desde la fusión de este cambio, ningún PR agrega advertencias de estilo
  por encima del techo de su tarea sin que CI falle.
* El 72% del conteo inicial desaparece sólo por política (6768 → 1866),
  separando ruido heredado de deuda real.
* El archivo `suppressions.xml` muerto desaparece y queda una única
  superficie de supresión.
* El plan final (severidad `error` por defecto, mapa eliminado, bloqueos
  por regla redundantes retirados) es una edición acotada.

### Negativas / Deuda Técnica

* **Los PRs de feature abiertos se ponen en rojo por diseño** si agregan
  advertencias por encima del techo de su tarea. La regla es **"corregir o
  bajar, nunca subir"**: nunca se sube un techo para poner un build en verde.
  Dos PRs que tocan la misma clave resuelven rebaseando y re-midiendo, nunca
  eligiendo un número en el conflicto.
* La **única excepción** a "nunca subir" es un PR de actualización de versión
  de Checkstyle, que puede introducir hallazgos nuevos por cambios de reglas;
  ese PR recalibra los techos y lo declara explícitamente en su descripción.
* El `snake_case` en tests queda tolerado pero no endosado; la migración a
  `camelCase` es deuda diferida y no figura en `CLAUDE.md` como convención
  oficial.
* El mecanismo depende del texto del mensaje de `MethodName` (ver arriba).
* Mantener el mapa durante las fases de limpieza exige disciplina de proceso
  (techos exactos en cada PR de limpieza).

### Implicaciones de Costos

* Sin costo de infraestructura ni licencias. El costo es de tiempo de
  desarrollo: los PRs de limpieza por fases (importaciones, Javadoc,
  formato, módulo por módulo) hasta llegar a 0.

### Riesgos y Reversibilidad

* **Riesgo Principal:** que la presión por poner un build en verde derive en
  subir techos o en agregar supresiones, vaciando el trinquete.
* **Plan de Mitigación:** el diff del mapa es visible en cada revisión; las
  supresiones sólo viven en `google_checks.xml` y, por defecto, no se usan
  comentarios `CHECKSTYLE.SUPPRESS` para esquivar límites de línea; cada PR de limpieza
  publica la tabla de 14 conteos antes y después y los totales de JUnit por
  módulo (que deben mantenerse iguales: la limpieza no cambia comportamiento).
* **Reversibilidad:** alta. Revertir el PR de este ADR restituye el
  comportamiento warning-only sin cambio de esquema ni de runtime. Cada PR
  posterior lleva sus propios techos y se revierte en orden inverso.

### Bloqueos de rigor (estricto) y estado final

* **Bloqueo por regla:** una regla con 0 hallazgos en las 14 tareas puede
  pasar a `severity=error` en `google_checks.xml`; sus hallazgos nunca consumen
  techo. No se puede fijar un bloqueo mientras alguna tarea reporte la regla
  (el build falla).
* **Módulo estricto:** al llevar ambas tareas de un módulo a 0, el mismo PR
  borra sus dos claves y lo agrega a `checkstyleStrictModules`.
* **Estado final:** severidad por defecto `error`, `maxWarnings = 0`, y se
  elimina el mapa, el conjunto estricto y los bloqueos por regla redundantes.
  Cualquier hallazgo falla el build.

## Referencias y Decisiones Relacionadas

* Issue #298 (limpieza y trinquete de Checkstyle) y sus sub-issues.
* Corrige a: ADR-0042 (la afirmación de que Checkstyle "falla el build").
* Complementa a: ADR-0024 (versión de Checkstyle declarada en el baseline).
* `docs/14-TEST-STRATEGY.md` — sección "Estilo: trinquete de Checkstyle" con
  los comandos de medición.
* `build.gradle.kts` (mapa `checkstyleWarningCeiling`) y
  `config/checkstyle/google_checks.xml` (política y supresiones).
