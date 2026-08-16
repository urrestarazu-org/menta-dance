# ADR-0036: Migración de Tooling Android a AGP 9.x

**Estado:** Aceptado
**Fecha:** 2026-08-16
**Implementado:** 2026-08-16 (PR #85)
**Decisores:** Equipo Android / Arquitectura

## Contexto y Problema

El módulo `android/` tenía sus plugins comentados en el `build.gradle.kts` raíz desde el
scaffold inicial, con la nota *"require Google Maven repository in buildscript"*. Ese
motivo ya no aplicaba — `settings.gradle.kts` ya declara `google()` en
`pluginManagement.repositories` — pero nadie había vuelto a activarlos.

Al reactivarlos y subir AGP de 9.2.1 a 9.3.1 (versión mínima de Gradle que exige: 9.1.0),
aparecieron en cadena varios problemas independientes, cada uno con causa raíz distinta:

1. **AGP 9.0 integró Kotlin de forma nativa** y cambió de DSL. El proyecto sigue usando el
   plugin `org.jetbrains.kotlin.android` explícito, que no es compatible con el DSL nuevo:
   aplicar ambos a la vez falla con `Cannot add extension with name 'kotlin'...`.
2. **Android Studio usa su propio runtime (JBR)** como JDK del daemon de Gradle — JDK 25 en
   Quail 3. Kotlin no soporta ese target y cae a bytecode 24, mientras Java sigue
   compilando a 21 vía `compileOptions`. El Kotlin Gradle Plugin bloquea la build por esa
   inconsistencia con un error duro.
3. **El sync de Android Studio es un camino distinto de la compilación por CLI.** Un build
   exitoso por línea de comandos no garantiza que el sync del IDE funcione: se
   diagnosticaron fallas de sync (`prepareKotlinBuildScriptModel not found`,
   `removeContentEntry ... still exists after removing`) que resultaron ser problemas de
   caché/estado interno del IDE, no del build en sí.
4. **Un wrapper de Gradle duplicado** apareció dentro de `android/gradle/` y
   `android/gradlew`, generado al abrir esa carpeta como raíz de proyecto en Android
   Studio en vez de abrir el monorepo completo.

## Decision Drivers

* No reescribir la configuración de Kotlin del módulo `android/` en el mismo cambio que
  sólo busca "que vuelva a compilar" — migrar a Kotlin integrado de AGP 9.x es un cambio
  aparte, deliberado.
* La compilación no debe depender de qué JDK lance el daemon de Gradle (CLI vs. Android
  Studio pueden diferir).
* Minimizar la superficie de cambio en archivos que afectan a *todo* el monorepo
  (`settings.gradle.kts`, `gradle/`), no sólo a Android.

## Opciones Consideradas

### Opción 1: Flags de compatibilidad de AGP 9.x (elegida)

Usar los flags oficiales de migración (`android.newDsl=false`,
`android.builtInKotlin=false`) para mantener el plugin `kotlin-android` funcionando sin
reescribir su configuración, y fijar el target de compilación con
`kotlin { jvmToolchain(21) }` en vez de depender del JDK del daemon.

* **Pros:** cambio mínimo, reversible, no toca código de la app; deja documentado que es
  temporal.
* **Contras:** estos flags se eliminan en AGP 10.0 (mediados de 2026); hay una fecha límite
  real para migrar.

### Opción 2: Migrar a Kotlin integrado de AGP 9.x ahora

Sacar el plugin `kotlin-android`, adoptar `builtInKotlin` de AGP y migrar toda API de
variant legacy (`applicationVariants`, `testVariants`, `unitTestVariants`) a
`AndroidComponentsExtension`.

* **Pros:** sin deuda de flags deprecados; alineado con AGP 10.0 desde ya.
* **Contras:** cambio de mayor alcance, sin relación directa con el objetivo inmediato de
  "que compile y sincronice"; conviene hacerlo como cambio propio, no mezclado con la
  migración de versión.
* **Resultado:** diferida — ver Deuda Técnica.

## Decisión

Se elige la **Opción 1**. El detalle de cada pieza:

1. `gradle.properties`: `android.newDsl=false` y `android.builtInKotlin=false`. **Deben ir
   juntos** — el plugin `kotlin-android` no es compatible con `newDsl=true`, y el propio
   AGP lo advierte si no coinciden.
2. `android/build.gradle.kts`: `kotlin { jvmToolchain(21) }`, alineado con
   `compileOptions.sourceCompatibility/targetCompatibility = VERSION_21`. Fija el target
   de compilación con independencia del JDK que lance el daemon.
3. `settings.gradle.kts` (raíz): plugin `org.gradle.toolchains.foojay-resolver-convention`,
   para que Gradle pueda **autoprovisionar** el JDK 21 que pide el toolchain si no está
   instalado localmente.
4. `gradle/gradle-daemon-jvm.properties` (nuevo, generado por `updateDaemonJvm`): fija el
   **daemon** de Gradle en JDK 25/JetBrains, igual al runtime embebido de Android Studio.
   Es ortogonal al punto 2: esto controla qué proceso *ejecuta* Gradle, el toolchain
   controla a qué bytecode *se compila*.
5. `gradlew` (raíz): `unset ANDROID_PREFS_ROOT` antes de invocar Gradle — workaround
   documentado para un conflicto real entre esa variable y `ANDROID_USER_HOME` en AGP.

Se descartó `android.enableLegacyVariantApi=true`: figuraba en guías para versiones
anteriores de AGP, pero **fue eliminado en AGP 9.0** (no deprecado — eliminado) y produce
error duro al aplicar el plugin, no sólo warning.

## Consecuencias

### Positivas

* El módulo `android/` vuelve a compilar y sincronizar, tanto por CLI (JDK 21) como desde
  Android Studio (JBR 25) — verificado explícitamente en ambos escenarios, no sólo en uno.
* El pin del daemon JVM hace reproducible entre desarrolladores y CI qué proceso ejecuta
  Gradle, en vez de depender de qué JDK tenga cada máquina como default.
* El resolver de Foojay evita que cada desarrollador tenga que instalar manualmente un
  JDK 21 aparte.

### Negativas / Deuda Técnica

* **Los flags `newDsl=false` y `builtInKotlin=false` tienen fecha de vencimiento.** AGP
  10.0 (mediados de 2026 según el cronograma público de Google) elimina por completo la
  posibilidad de optar por ellos. Antes de esa versión hay que completar la Opción 2:
  sacar `kotlin-android`, adoptar Kotlin integrado, y migrar
  `applicationVariants`/`testVariants`/`unitTestVariants` a `AndroidComponentsExtension`.
  Cada build hoy emite warnings de deprecación de estas tres APIs — son la lista concreta
  de lo que falta migrar.
* **`gradle-daemon-jvm.properties` fija el daemon en JDK 25 vendor `JETBRAINS` para *todo*
  el monorepo**, no sólo para `android/`. Es una decisión con alcance de equipo/CI que se
  tomó para resolver el problema inmediato (igualar el runtime de Android Studio), pero no
  se discutió si un OpenJDK genérico sería una elección más portable a largo plazo — por
  ejemplo, para desarrolladores o entornos de CI sin Android Studio instalado, donde
  provisionar un JDK con vendor específico de JetBrains es una dependencia extra que un
  OpenJDK estándar no impondría. **Revisar antes de que el equipo crezca.**
* **Ningún test de Android corre en CI.** `quick-build-and-test` excluye explícitamente
  `:android:testDebugUnitTest` por lentitud, y no existe ningún job con emulador para
  tests instrumentados. Esto ya causó al menos un incidente real: la dependencia
  `androidx.test:runner` faltaba pese a estar declarado el
  `testInstrumentationRunner`, y nadie lo notó porque los tests instrumentados
  jamás se habían ejecutado. Al menos los unit tests (`testDebugUnitTest`) deberían
  habilitarse en CI; los instrumentados requieren decidir estrategia de emulador
  (GitHub Actions con `reactivecircus/android-emulator-runner` u otra).
* **`docs/24-LOCAL-DEV-SETUP-REQUIREMENTS.md` no fija una versión mínima de Android
  Studio.** Se confirmó empíricamente que Android Studio Quail 1 (build de mayo 2026, previo
  al release de AGP 9.3 en julio 2026) falla el sync sin razón obvia; Quail 3 (agosto 2026)
  funciona. Falta documentar ese piso como requisito explícito.

### Riesgos y Reversibilidad

* **Reversibilidad:** alta para los flags de compatibilidad (opt-out documentado por
  Google, con ruta de migración clara). Media para el pin del daemon JVM: revertirlo es
  trivial (borrar el archivo), pero cambiar el *vendor* elegido afecta a todo el equipo y
  requiere coordinación, no es un cambio unilateral de un desarrollador.
* **Riesgo de recurrencia:** si alguien abre `android/` directamente como raíz de proyecto
  en vez del monorepo completo, Android Studio vuelve a generar un wrapper duplicado
  (`android/gradlew`, `android/gradle/`, `android/settings.gradle.kts`). Mitigado con
  reglas en `.gitignore` para que no se commitee, pero el síntoma puede reaparecer y vale
  la pena reconocerlo si sucede: la señal es una carpeta `android/gradle/` con contenido
  propio.

## Referencias y Decisiones Relacionadas

* [Android Gradle plugin 9.3.0 (July 2026)](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
* [Migrate to built-in Kotlin](https://developer.android.com/build/migrate-to-built-in-kotlin)
* [Android Gradle Plugin DSL/API migration timeline](https://developer.android.com/build/releases/gradle-plugin-roadmap)
* [26-LOCAL-DEV-SETUP-HOWTO.md](../26-LOCAL-DEV-SETUP-HOWTO.md) — pasos prácticos y troubleshooting
* `gradle/gradle-daemon-jvm.properties`, `settings.gradle.kts`, `android/build.gradle.kts`
