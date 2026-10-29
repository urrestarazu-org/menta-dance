# ADR-0047: Android exige API 29 (Android 10) como `minSdk`

**Estado:** Aceptado
**Fecha:** 2026-10-29
**Decisores:** Equipo Android / Arquitectura
**Reemplaza a:** [ADR-0046](0046-android-min-sdk-26.md)

## Contexto y Problema

El ADR-0046 fijó `minSdk = 26` (Android 8.0) porque #375 usa `java.time`, que es nativo del
sistema desde esa versión. Al cerrar #375 quedó sin ejecutar la prueba manual del piso (tarea
0.9: instalar el build en un emulador API 26), y al intentar hacerla apareció un problema
práctico: **ese piso no se puede ejecutar en un emulador local en Apple Silicon**.

El emulador de Android corre solo imágenes `arm64-v8a` en un procesador ARM. En el
administrador de dispositivos de Android Studio (2026-10-29) las imágenes ARM con Google Play
empiezan en la **API 29**; por debajo de esa versión solo hay imágenes Intel (`x86` y
`x86_64`), que no arrancan en un Mac con Apple Silicon. Un piso que no se puede probar en la
máquina de desarrollo no ofrece ninguna garantía: es una promesa sin verificar.

El ADR-0046 ya preveía este caso: dejaba abierto subir el piso con un ADR nuevo.

## Factores Clave (Decision Drivers)

* Poder ejecutar el piso de plataforma en un emulador local, sin depender de CI ni de
  hardware extra.
* Conservar `java.time` nativo en `domain`, sin *core library desugaring* (razón del
  ADR-0046).
* Perder la menor cantidad de dispositivos posible: la app es V0 y no está en producción,
  pero la academia no controla los teléfonos de sus alumnos.
* Que el piso se justifique con lo que se puede verificar hoy.

## Opciones Consideradas

### Opción 1: Mantener `minSdk = 26` y probar con CI o un dispositivo físico

* **Descripción:** dejar el piso como está y verificarlo con un emulador x86 en un runner
  Linux de CI (que sí puede usar KVM) o con un teléfono físico con Android 8 a 10.
* **Pros:**
  * Conserva Android 8.0 y 9.
  * No revierte una decisión ya documentada.
* **Contras:**
  * La prueba deja de ser local: depende de infraestructura de CI nueva o de tener el
    dispositivo.
  * Hoy no existe ninguna de las dos cosas, así que el piso seguiría sin verificarse.

### Opción 2: `minSdk = 29` (elegida)

* **Descripción:** subir el piso a Android 10. Es el nivel más bajo con imagen ARM de
  emulador disponible para Apple Silicon.
* **Pros:**
  * El piso exacto se ejecuta en un emulador local del Mac.
  * `java.time` sigue siendo nativo; no hay desugaring ni ramas por versión.
  * El cambio es de una línea en `android/build.gradle.kts`.
* **Contras:**
  * Deja sin app a quienes usen Android 8.0 y 9 (API 26 a 28).

### Opción 3: `minSdk` mayor que 29 (por ejemplo, 31)

* **Descripción:** subir el piso aún más.
* **Pros:**
  * Ninguna que no tenga ya la opción 2.
* **Contras:**
  * Saca más dispositivos sin habilitar nada: el piso 29 ya es ejecutable localmente y el
    código no usa nada de las versiones 30 o 31.

## Decisión

Elegimos la **Opción 2: `minSdk = 29`** porque es el piso más bajo que se puede ejecutar y
verificar en un emulador local, sin renunciar a `java.time` nativo.

El cambio efectivo es `minSdk = 29` en `android/build.gradle.kts`. `targetSdk` no cambia
(36). No hay ramas por versión de SDK (`SDK_INT`) en el código y no debe agregarse ninguna.

## Justificación (Rationale)

El criterio que decide es la verificabilidad: preferimos un piso un poco más alto que
podemos probar en la máquina de desarrollo antes que uno más bajo que solo existe en un
documento.

**Alcance estimado.** Según StatCounter para Argentina (móvil y tablet, junio 2026), Android 11
o superior suma cerca de 87,5 % (Android 14: 21,3 %; 16: 19 %; 13: 16,6 %; 15: 14,4 %;
12: 9 %; 11: 7,2 %). Por lo tanto, quien usa Android 10 o anterior es como máximo ~12,5 %, y
quien queda realmente afuera (API 26 a 28) es una fracción menor de eso. Es una suma propia
sobre datos parciales; no se obtuvo la cifra exacta de Android 9 o anterior en móvil.

## Consecuencias

### Positivas

* El piso de plataforma se prueba en un AVD API 29 (`arm64-v8a`) en el Mac.
* Sin dependencias ni configuración de desugaring que mantener.
* Lint deja de advertir sobre APIs de las versiones 26 a 28.

### Negativas / Deuda Técnica

* Los dispositivos con Android 8.0 y 9 no podrán instalar la app.
* La estimación de alcance no está verificada con datos de la academia. **Revisar con datos
  reales de los alumnos antes del lanzamiento público.** Si hace falta bajar el piso, es un
  cambio de una línea que debe registrarse en un ADR nuevo, y la verificación pasaría a
  depender de CI con emulador x86 o de un dispositivo físico.

## Referencias

* ADR-0046: piso `minSdk = 26`, que este ADR reemplaza.
* #375: primera funcionalidad que usa `java.time` y origen de la tarea manual 0.9.
* #668: cambio que implementa este ADR.
* [StatCounter: versiones de Android en Argentina](https://gs.statcounter.com/android-version-market-share/mobile-tablet/argentina).
