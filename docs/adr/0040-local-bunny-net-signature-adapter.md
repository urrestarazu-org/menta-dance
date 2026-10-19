# ADR-0040: Adaptador local determinista para `BunnyNetSignatureService`

**Fecha:** 2026-08-30
**Estado:** Aceptado

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

## Referencias y Decisiones Relacionadas

- Issue #129
- `docs/adr/0041-lesson-access-unplanned-course-denial.md` (D7, mismo
  cambio SDD, decisión independiente)
- `openspec/changes/local-bunny-net-adapter/design.md` (A1, A2, A5, A7)
