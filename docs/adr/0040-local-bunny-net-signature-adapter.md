# ADR-0040: Adaptador local determinista para `BunnyNetSignatureService`

**Fecha:** 2026-08-30
**Estado:** Aceptado (enmendado el 2026-11-14, ver «Enmienda 2026-11-14 (#751)»)

## Contexto

`api:virtual` valida el acceso a lecciones preview y premium (US-VIRTUAL-004)
firmando una URL de streaming a través del puerto `BunnyNetSignatureService`.
La única implementación existente, `StringFormatBunnyNetSignatureService`, es
un placeholder de MVP (sin HMAC real) y de todos modos nunca debe usarse en
una prueba de aceptación E2E local: no hay credencial de Bunny.net
disponible en ese entorno, y la aserción de determinismo entre dos llamadas
en vivo es frágil porque el TTL depende de la hora real.

Se necesita un tercer adaptador — activo solo bajo un perfil E2E — que
produzca una URL con la misma forma que la de producción, pero sin depender
de ninguna credencial real, y que dos invocaciones con el mismo `videoId` y
el mismo TTL produzcan exactamente el mismo resultado (para que un journey
de Bruno pueda afirmarlo sin flakiness de reloj).

## Decisión

Se agrega `LocalBunnyNetSignatureService` (`infrastructure/cdn/local/`),
implementación pura sin estado del puerto `BunnyNetSignatureService`. Produce:

```
<pullZoneHostname>/<videoLibraryId>/<videoId>?exp=<epochSeconds>&sig=<64 hex minúscula>
```

donde `sig = SHA-256("menta-local-e2e|" + videoLibraryId + "|" + videoId +
"|" + exp)`. `exp` es el TTL del caller tal cual (verbatim, no
recalculado). El salt `"menta-local-e2e"` es una constante **pública**, no
un secreto: el digest no lleva ninguna credencial y es inútil contra el CDN
real — la determinación entre dos llamadas con el mismo `videoId`+TTL surge
de que la función es pura, no de que el salt esté oculto.

`VirtualConfiguration` expone ahora **dos** métodos `@Bean` complementarios
para `BunnyNetSignatureService`, en vez de uno:

- `localBunnyNetSignatureService(...)` — `@Profile("e2e-bunny-net")`.
- `defaultBunnyNetSignatureService(...)` — `@Profile("!e2e-bunny-net")`.

Al ser perfiles complementarios (uno es la negación exacta del otro), Spring
nunca ve dos candidatos simultáneos: exactamente un bean de
`BunnyNetSignatureService` existe en cualquier arranque.

El guard fail-closed vive **dentro del método `@Bean` local**, que recibe
`Environment` como parámetro:

```java
@Bean
@Profile("e2e-bunny-net")
public BunnyNetSignatureService localBunnyNetSignatureService(
    BunnyNetProperties properties, Environment environment
) {
    failClosedIfProductionLike(environment);
    return new LocalBunnyNetSignatureService(properties);
}
```

Si `e2e-bunny-net` coincide con `prod`, `production` o `staging`, el método
lanza `IllegalStateException` — Spring lo envuelve en `BeanCreationException`
y aborta el refresh del contexto **antes** de que el servidor web acepte
ningún request.

## Alternativas rechazadas

- **`ApplicationRunner` para el guard**: los `ApplicationRunner` se ejecutan
  **después** de que el servidor web ya está arriba — una falla ahí es
  demasiado tarde; el proceso pudo haber aceptado tráfico brevemente antes
  de morir. Un throw dentro del método `@Bean` aborta el `refresh()` del
  contexto de Spring, que ocurre antes de que `MentaDanceApplication`
  levante el listener HTTP.
- **`@Component @Profile` (patrón de `BillingConfiguration`/Mercado Pago
  local)**: Billing usa `@Component` + `@Profile` con component-scan para
  su simulador local de Mercado Pago. Se descarta aquí porque
  `VirtualConfiguration` ya es la raíz de composición explícita de todo
  `virtual` (ver su propio Javadoc de clase) — mezclar component-scan solo
  para este bean rompería esa convención sin necesidad; los dos métodos
  `@Bean` con perfiles complementarios logran la misma exclusión mutua sin
  abandonar la composición explícita.
- **Comparar `sig` entre dos llamadas HTTP reales en vez de determinismo
  puro**: el TTL se calcula como `now + 4h` en
  `GetPublicLessonStreamUseCaseImpl`; dos llamadas reales separadas por
  segundos producirían `exp` distintos y por lo tanto `sig` distintos —
  no es una aserción estable. El test unitario de
  `LocalBunnyNetSignatureService` fija el `exp` explícitamente en vez de
  depender del reloj.

## Deuda técnica registrada (A7, compartida con ADR-0041)

Este cambio SDD también corrige (ADR-0041) la regla de acceso público de un
curso sin plan comercial asociado. Esa corrección deja
`CourseAccessSnapshot.courseInAnyPlan` (puerto compartido, `api/shared`) sin
ningún lector en `virtual`. Eliminar el campo ampliaría el diff hacia
`api/shared` y `api/billing`, fuera del alcance de este PR — queda anotado
como deuda de limpieza a resolver en un cambio posterior e independiente.

## Consecuencias

### Positivas

- El journey de Bruno E2E (`e2e-bunny-net`) prueba preview público,
  denegación sin entitlement y otorgamiento tras checkout real, sin
  ninguna credencial de Bunny.net ni llamada de red al CDN.
- La forma de la URL firmada es idéntica a la de producción
  (`<pullZone>/<library>/<videoId>?...`), así que el contrato que consume
  el cliente no diverge entre entornos.
- El guard fail-closed hace que un despliegue accidental con
  `e2e-bunny-net` activo en `prod`/`production`/`staging` no llegue a
  aceptar tráfico.

### Negativas / Deuda técnica

- Dos métodos `@Bean` en vez de uno añaden una pequeña superficie de
  mantenimiento a `VirtualConfiguration` (mitigado: los perfiles son
  complementarios y el compilador/Spring detectan un perfil mal escrito
  como ausencia total del bean, un fallo ruidoso en cualquier entorno).
- Deuda A7 heredada de ADR-0041 (ver arriba).

### Riesgos y Reversibilidad

- **Riesgo principal**: un typo en la cadena literal `"e2e-bunny-net"` en
  cualquiera de los dos `@Profile` rompería la exclusión mutua (dos
  candidatos, o ninguno). Mitigado por
  `VirtualConfigurationTest.BunnyNetAdapterProfileSelection`, que arranca
  un contexto Spring real bajo cada combinación de perfiles y afirma
  `getBeanNamesForType(BunnyNetSignatureService.class)).hasSize(1)`.
- **Reversibilidad**: alta. El perfil `e2e-bunny-net` está inactivo por
  defecto; revertir este commit elimina el paquete
  `infrastructure/cdn/local/` y restaura el único método `@Bean` original
  sin ninguna migración de datos.

## Enmienda 2026-11-14 (#751): firma real y selección por perfil y clave

Esta enmienda no reescribe la decisión original: la complementa. Lo que sigue
queda **vigente**; los apartados anteriores que lo contradigan (la
exclusión mutua por `@Profile`, el adaptador "no local" como placeholder y el
perfil `demo` atado siempre al adaptador local) quedan reemplazados por lo
siguiente.

**Contexto.** El adaptador no local (`StringFormatBunnyNetSignatureService`)
devolvía `<host>/<library>/<videoId>` sin firma ni vencimiento, y ese formato
no es el de Bunny Stream. Con *CDN token authentication* activada en la
biblioteca Stream, ninguna URL sin firmar reproduce (`403`). Además `demo` usaba siempre el
adaptador local, así que ni un desarrollador con un video real podía verlo.

**Decisión.**

1. **Firma real.** `StringFormatBunnyNetSignatureService` se reemplaza por
   `TokenAuthBunnyNetSignatureService`, que firma con el esquema *advanced* de
   Token Authentication de Bunny.net (HMAC-SHA256):
   `token = "HS256-" + base64url_sin_padding(HMAC-SHA256(securityKey, path + expires))`,
   donde `path` es el path **decodificado** (`/<videoId>/<fileName>`) y `expires`
   los segundos epoch tal cual los entrega el caller. La URL es
   `https://<pullZoneHostname>/<videoId>/<fileName>?token=<token>&expires=<epoch>`;
   `fileName` es configurable (`app.cdn.bunny-net.file-name`, por defecto
   `play_720p.mp4`, requiere *MP4 Fallback*). `videoLibraryId` no forma parte de
   la URL de Stream. El host se acepta con o sin `https://` y sin barra final.
   Referencia: documentación oficial de Bunny.net
   (`bunny.net/docs/cdn/security/token-authentication/advanced`) y su repositorio
   `BunnyWay/BunnyCDN.TokenAuthentication`, cuyo vector publicado
   (`e2e/vectors.json`, caso `plain`) fija el test. El esquema *basic* (MD5) está
   marcado como deprecado por Bunny.net y no se usa.
2. **Clave como secreto.** `app.cdn.bunny-net.security-key`
   (`APP_CDN_BUNNYNET_SECURITYKEY`) es la clave de la tarjeta *Token
   authentication key* de **Security > General** de la biblioteca Stream
   (rotulada *API key* ahí, pero distinta de la API key de la página *API* y de
   la *Read-only API key*). Según la documentación de Bunny.net esa misma clave
   firma los embeds y las URLs de CDN token authentication de la biblioteca:
   no se resetea (rompería ambos) y la biblioteca necesita *CDN token
   authentication* activada en esa página. (Corrección del 2026-11-14: una
   redacción anterior de esta enmienda decía que la clave correcta era la
   *URL Token Authentication Key* del pull zone y que la de la biblioteca solo
   firmaba embeds; para una biblioteca Stream eso es incorrecto. La clave propia
   de un pull zone solo aplica a un pull zone independiente.) Está excluida de `toString` (`@ToString.Exclude`), nunca se
   registra ni aparece en un mensaje de error, y no pasa por el BFF (este solo
   recibe la URL ya firmada).
3. **Un único método `@Bean`.** `VirtualConfiguration` deja de tener dos métodos
   con `@Profile` complementarios, porque una expresión de perfil no puede
   consultar si una propiedad está en blanco. Un solo método decide, en este
   orden:

   | Perfiles activos | `security-key` | Adaptador |
   |---|---|---|
   | `e2e-bunny-net` (con o sin `demo`) | cualquiera | local, siempre |
   | `demo` | en blanco o ausente | local |
   | `demo` | con valor | real (valida configuración) |
   | cualquier otro | cualquiera | real (valida configuración) |

   Sigue existiendo exactamente un bean. El guard contra `prod`/`production`/
   `staging` sigue en el factory y se aplica siempre que se elige el adaptador
   local, o `demo` con clave.
4. **Fail-closed.** Cuando se elige el adaptador real, el arranque falla si falta
   la clave, si el host o la biblioteca siguen en el placeholder
   (`invalid-missing-config` / `missing-config`) o están mal formados, o si
   `file-name` no es un único segmento. El mensaje nombra las propiedades,
   nunca sus valores. Esto salda la verificación diferida que antes figuraba en
   `BunnyNetProperties`. Consecuencia operativa: cualquier despliegue sin perfil
   `demo`/`e2e-bunny-net` necesita `APP_CDN_BUNNYNET_SECURITYKEY`.
5. **El e2e no cambia.** `e2e-bunny-net` conserva el adaptador local y su
   salida determinista (`exp`/`sig`), sin salir a la CDN real. Los contextos de
   test (`test`, `integration-test`) y `scripts/e2e/catalog-content.sh` definen
   valores falsos propios porque ahora usan el adaptador real.
6. **Fixture de la demo.** `demo.virtual.free-lesson-video-id`
   (`DEMO_VIRTUAL_FREE_LESSON_VIDEO_ID`), si no está en blanco, reemplaza la
   etiqueta como `videoId` de las lecciones gratuitas de la demo, y también se
   aplica a las ya sembradas (únicamente a los ids fijos de la demo).

**Consecuencias.** Positivas: una lección puede reproducirse de verdad contra
una biblioteca Stream con CDN token authentication; el adaptador real tiene vector oficial
y prueba de selección por perfil y clave. Negativas / riesgo: la selección ya
no la garantizan dos `@Profile` complementarios sino lógica en un método
(mitigado por `VirtualConfigurationTest.BunnyNetAdapterProfileSelection`, que
arranca un contexto real por cada combinación); la firma no se pudo contrastar
contra una biblioteca real en esta enmienda (la verificación manual queda a
cargo de quien tenga la clave). Fuera de alcance: HLS, reproductor embebido,
subida de videos y firma por IP.

**Diagnóstico de `403` (corrección 2026-11-14).** Un `403` no implica solo token
o clave: con *Block Direct URL File Access* un pedido **sin** `Referer` se
rechaza aunque el token sea perfecto, y los dominios permitidos o bloqueados se
comparan con el *hostname* del `Referer`. El `<video>` del navegador manda
`Referer`; la sonda de diagnóstico y los pedidos de Bruno ahora pueden mandarlo
(`app.cdn.bunny-net.diagnostic-probe-referer`, opcional, en blanco por defecto).
Son ajustes de la biblioteca; cambiarlos es decisión de quien la administra.

## Referencias y Decisiones Relacionadas

- Issue #129
- Issue #751 (enmienda 2026-11-14)
- `docs/adr/0041-lesson-access-unplanned-course-denial.md` (D7, mismo
  cambio SDD, decisión independiente)
- `openspec/changes/local-bunny-net-adapter/design.md` (A1, A2, A5, A7)
