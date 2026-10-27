# ADR-0046: Android exige API 26 (Android 8.0) como `minSdk`

**Estado:** Aceptado
**Fecha:** 2026-10-27
**Implementado:** 2026-10-27 (#375)
**Decisores:** Equipo Android / Arquitectura

## Contexto y Problema

El módulo `android/` declara `minSdk = 24` (Android 7.0) desde el scaffold inicial, sin que
ninguna decisión documentada lo respalde. Hasta #374 el código no necesitaba nada que
dependiera de esa cifra.

#375 (Mis clases y QR de acceso) es la primera funcionalidad que parsea fechas de la API
(`expiresAt` del QR y `scheduledAt` de las sesiones) y calcula cuándo renovar la credencial.
Eso pide `java.time` (`Instant`, `Duration`, `Clock`), que **llegó a Android con la API 26**.
Con `minSdk = 24`, `Instant.parse(...)` lanza `NoClassDefFoundError` en Android 7.0 y 7.1.
Los tests unitarios JVM no lo detectan, porque corren con el JDK de la máquina de
desarrollo, que sí tiene `java.time`: el fallo sería silencioso hasta llegar a un
dispositivo real.

Hay que decidir ahora el piso de plataforma, antes de escribir código que dependa de él.

## Factores Clave (Decision Drivers)

* Usar `java.time` en `domain` sin parches de compatibilidad ni configuración extra.
* No sumar dependencias ni opciones de build que no aporten funcionalidad al alumno.
* Mantener un alcance de dispositivos amplio: la app es V0 y todavía no está en producción,
  pero la academia no controla los teléfonos de sus alumnos.
* Que el piso se justifique con lo que el código usa hoy, no con lo que podría usar.

## Opciones Consideradas

### Opción 1: Mantener `minSdk = 24` y habilitar *core library desugaring*

* **Descripción:** activar `isCoreLibraryDesugaringEnabled` y agregar la dependencia
  `desugar_jdk_libs`. El compilador (D8/R8) reescribe las llamadas a `java.time.*` y empaqueta
  una copia de esas clases en el APK.
* **Pros:**
  * Conserva Android 7.0 y 7.1.
* **Contras:**
  * Suma una dependencia y una opción de build que mantener.
  * El APK crece, aunque R8 recorta a lo usado.
  * Es una capa más que puede romper junto con las migraciones de AGP ya previstas
    (ADR-0036).

### Opción 2: `minSdk = 26` (elegida)

* **Descripción:** subir el piso a Android 8.0. `java.time` es nativo del sistema.
* **Pros:**
  * Resuelve el problema real sin dependencias ni configuración de desugaring.
  * El cambio es de una línea en `android/build.gradle.kts`.
  * Es un piso habitual: según la estimación consultada por el equipo, cubre cerca del
    96 % de los dispositivos activos.
* **Contras:**
  * Deja sin app a quienes usen Android 7.0 o 7.1. Esa cifra de cobertura proviene de una
    fuente externa y no se verificó al tomar la decisión.

### Opción 3: `minSdk = 28` (Android 9.0)

* **Descripción:** subir el piso un escalón más.
* **Pros:**
  * Habilita `StrongBox` y `BiometricPrompt` sin comprobaciones de versión.
* **Contras:**
  * Hoy no se usa nada de la API 28. `EncryptedRefreshTokenStore` ya usa AES/GCM con el
    Android Keystore directamente, disponible desde la API 23, sin `StrongBox`.
  * El tráfico sin cifrar de debug (`http://10.0.2.2`) se resuelve con un
    `network_security_config` en `src/debug` y depende de `targetSdk = 36`, no de `minSdk`.
  * Saca otros ~3 puntos de dispositivos (cifra externa, no verificada) sin desbloquear
    funcionalidad alguna de #374 ni #375.

## Decisión

Elegimos la **Opción 2: `minSdk = 26`** porque resuelve el problema concreto (`java.time` en
`domain`) sin dependencias adicionales y sin renunciar a más dispositivos de los necesarios.
Subir a 28 no se justifica mientras el código no use nada de esa versión.

El cambio efectivo es `minSdk = 26` en `android/build.gradle.kts`. No hay ramas por versión de
SDK (`SDK_INT`) en el código actual, así que no hay nada más que adaptar.

### `minSdk` no es `targetSdk`

Son dos cifras independientes. `minSdk` es el piso: la versión más vieja de Android que puede
instalar la app. `targetSdk` es la versión con la que se prueba y se declara compatible el
comportamiento de la app, y es la que exige Google Play.

Desde el 31 de agosto de 2026, Google Play exige que las apps nuevas y sus actualizaciones
apunten a Android 16 (API 36) o superior
([requisitos de API objetivo](https://developer.android.com/google/play/requirements/target-sdk?hl=es-419)).
El módulo ya declara `targetSdk = 36`, así que esta decisión no cambia nada para la
publicación: subir `minSdk` a 26 no afecta el cumplimiento de esa política.

## Consecuencias

### Positivas

* `java.time` se usa directamente, incluso en `domain` (`DomainPurityTest` prohíbe sólo
  imports `android.*` y `androidx.*`, no `java.time`).
* Sin dependencia ni configuración de desugaring que mantener.
* Lint deja de advertir sobre APIs de las versiones 24 y 25.

### Negativas / Deuda Técnica

* Los dispositivos con Android 7.0 y 7.1 no podrán instalar la app.
* La estimación de cobertura de dispositivos no está verificada. **Revisar con datos reales de
  la academia antes del lanzamiento público.**
* Subir a la API 28 queda abierto para cuando exista una necesidad concreta (por ejemplo,
  `StrongBox` para el refresh token); es un cambio de una línea y debe registrarse en un ADR
  nuevo.

## Referencias

* #375: primera funcionalidad que usa `java.time`.
* ADR-0025: custodia del refresh token con Keystore.
* ADR-0036: migración del tooling Android a AGP 9.x.
