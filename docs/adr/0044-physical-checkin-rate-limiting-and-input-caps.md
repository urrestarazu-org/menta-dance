# ADR-0044: Rate Limiting y Topes de Entrada en el Check-in QR de Physical

**Estado:** Aceptado
**Fecha:** 2026-10-05
**Implementado:** 2026-10-05 (#309)
**Decisores:** Arquitectura y equipo de Physical

## Contexto y Problema

Desde #266 el check-in QR autentica cada lector contra el registro de
dispositivos. El endpoint `POST /api/v1/physical/sessions/{sessionId}/check-ins`
es `permitAll`, de modo que una request sin autenticar con un `deviceId` UUID
bien formado provoca una lectura por clave primaria en `physical_devices` y el
cálculo del SHA-256 del `deviceToken`.

Hoy no existe ningún freno:

* `CheckInRequest` no tiene `@Size` en `deviceId`, `deviceToken` ni
  `qrCredentials`, así que el SHA-256 se calcula sobre entradas de largo
  arbitrario.
* El módulo `physical` no tiene rate limiting. Nginx tampoco declara una zona
  `limit_req` para esta ruta, y un lector puede llamar desde la red local sin
  pasar por Nginx.

El único rastro actual es el log WARN y el contador
`physical.checkin.device.rejected` (#266). La decisión se difirió
explícitamente de #266 (D9).

## Factores Clave (Decision Drivers)

* El límite debe ejecutarse **antes** de `PhysicalDeviceAuthenticator`, que es
  quien calcula el hash, y sólo para el check-in QR. El check-in MANUAL está
  autenticado y no debe verse afectado.
* Ninguna clave de Redis puede construirse con una entrada cruda sin acotar.
* Respetar Clean Architecture (ADR-0021): `application` no depende de
  `HttpServletRequest`, de Redis ni de Micrometer.
* Mantener los módulos desacoplados (ADR-0020): hoy cada módulo tiene su propia
  copia del limitador y del fingerprint, y `api/shared` no contiene ninguno.
* Un control de seguridad no debe fallar en silencio ni abierto.
* La app está en V0 y no hay tráfico real para calibrar los valores.

## Opciones Consideradas

### Opción 1: Reutilizar el fingerprint y la propiedad de proxies de `auth`

* **Descripción:** importar `ClientFingerprint` y leer
  `auth.trusted-proxy-cidrs`.
* **Pros:**
  * Una única configuración de proxies de confianza.
* **Contras:**
  * `ClientFingerprint` y `Sha256Hex` son internos de `auth` (package-private);
    importarlos viola el límite entre módulos.
  * Acopla el despliegue de `physical` a una propiedad de otro módulo.

### Opción 2: Componentes y propiedad propios de `physical`, con fallback a `auth`

* **Descripción:** `physical` define su puerto, su adaptador Redis y su
  fingerprint. La lista de proxies de confianza se lee de
  `physical.checkin.trusted-proxy-cidrs`, con fallback a
  `auth.trusted-proxy-cidrs` cuando no se define.
* **Pros:**
  * Módulos independientes, coherente con billing y auth.
  * El fallback evita configurar el mismo valor dos veces en el caso común.
* **Contras:**
  * Duplica código (Lua, `RateLimitDecision`, fingerprint) por tercera vez.
  * Un valor más que revisar por entorno.

### Opción 3: Sólo `remoteAddr`, como billing (#195)

* **Descripción:** usar `request.getRemoteAddr()` sin considerar proxies.
* **Pros:**
  * La más simple; no hay lista de proxies que configurar.
* **Contras:**
  * Detrás de Nginx todo el tráfico colapsa en una única clave, de modo que un
    solo lector mal configurado agota el presupuesto de todos.

## Decisión

Elegimos la **Opción 2**: `physical` tiene sus propios puerto, adaptador y
fingerprint, y su propia propiedad de proxies de confianza con fallback a la de
`auth`.

El diseño completo es:

1. **Topes de longitud** en `CheckInRequest` mediante `@Size`; una violación
   produce `400 INVALID_REQUEST` por el mapeo existente.

   | Campo | Tope | Razón |
   |---|---|---|
   | `deviceId` | 36 | Largo de un UUID canónico |
   | `deviceToken` | 43 | 32 bytes en Base64 URL sin padding (`SecureRandomDeviceSecretGenerator`) |
   | `qrCredentials` | 512 | Holgado a propósito: el formato actual es un placeholder y será reemplazado por HMAC real |

2. **Dos presupuestos independientes**, uno por IP del cliente y otro por
   `deviceId`, cada uno con su propia clave y su propia llamada atómica. Se
   consume primero el de IP; si ya está agotado, el de `deviceId` no se toca, de
   modo que un origen limitado no puede gastar el presupuesto de un dispositivo
   ajeno falsificando su id. No se reutiliza el script de `auth` porque
   incrementa ambos contadores aunque uno ya haya superado su límite.
3. **Puerto propio** `CheckInRateLimitPort` en `application/port/out`. Recibe
   fingerprints opacos (`String`); derivarlos del request HTTP es tarea de
   `infrastructure/web`.
4. **Claves acotadas:** el `deviceId` se hashea (SHA-256) antes de usarse en la
   clave de Redis. Nunca se usa el valor crudo.
5. **Orden:** primero el `@Size` del bean validation, luego el rate limit y
   después `PhysicalDeviceAuthenticator`. Sólo para el check-in QR.
6. **IP del cliente:** se usa `X-Forwarded-For` únicamente si el peer inmediato
   está en `physical.checkin.trusted-proxy-cidrs`; en tal caso se toma la
   **última** entrada (la agregada por Nginx). En otro caso, `remoteAddr`. Es el
   mismo criterio de ADR-0035.
7. **Falla cerrada:** si Redis falla, el check-in responde `503
   CHECK_IN_DEGRADED` (misma excepción que `RedisCheckInLockPort`).
8. **Respuesta:** `429` con `Retry-After` y `application/problem+json` con
   código `CHECK_IN_RATE_LIMITED`, mismo formato que billing y auth.
9. **Observabilidad:** un contador y un log WARN en `infrastructure` cuando se
   rechaza por rate limit, sin registrar el `deviceId` crudo. Va en
   `infrastructure` porque ArchUnit prohíbe Micrometer en `application`.
10. **Valores configurables** en `application.yml` bajo `physical.checkin`. Los
    defaults son provisorios (ver Riesgos).

## Justificación (Rationale)

La Opción 1 se descarta porque rompe el aislamiento entre módulos: no hay nada
importable de `auth` ni de `shared`, y es el mismo motivo por el que billing
tiene su propia copia. La Opción 3 se descarta porque colapsaría a todos los
lectores detrás de Nginx en una sola clave, el mismo defecto que ADR-0035
documentó para el login web.

El tope de `deviceToken` en 43 queda acoplado al generador. Se acepta el
acoplamiento y se mitiga con un test que verifica que el secreto generado cabe
en el tope, para que un cambio de `SECRET_BYTES` rompa el build y no a los
lectores en producción.

Los dos presupuestos son independientes porque miden cosas distintas: el de IP
frena a un origen que prueba muchos dispositivos, y el de `deviceId` frena a un
dispositivo (o a alguien que lo suplanta) sin depender de la IP.

## Consecuencias

### Positivas

* Se acota el costo por request: el hash se calcula sobre como máximo 43
  caracteres y ninguna clave de Redis crece con la entrada.
* Un lector mal configurado o un cliente hostil recibe `429` antes de tocar la
  base de datos.
* El fallo de Redis no abre el control.

### Negativas / Deuda Técnica

* Tercera copia del limitador (auth, billing, physical). Extraer un módulo
  común queda como decisión futura y no se aborda aquí.
* Una violación de `@Size` devuelve `400` sin log ni métrica, porque nunca
  llega al autenticador. El rastro de ese caso es el propio `400`.
* Los tests de integración que mockean `RedisTemplate` deben mockear también el
  puerto nuevo; de lo contrario `execute` devuelve `null` y se interpreta como
  fallo (503).

### Implicaciones de Costos

* Sin costo de infraestructura: usa el Redis existente. Sin esquema nuevo.

### Riesgos y Reversibilidad

* **Riesgo Principal:** límites mal calibrados. Un presupuesto por IP demasiado
  bajo bloquearía a varios lectores legítimos detrás de un mismo NAT.
* **Plan de Mitigación:** los valores son configurables y los defaults son
  provisorios, porque el issue pide medir tráfico real y hoy no existe. Se
  deben revisar con el contador del punto 9 una vez que haya tráfico.
* **Riesgo aceptado en la primera versión:** el `deviceId` es un UUID **no
  secreto**. Quien lo conozca puede consumir su presupuesto (60 requests por
  minuto por defecto) y hacer que un lector legítimo reciba `429`. Es inherente a
  limitar por `deviceId` sin autenticar. La alternativa, contar sólo los fallos
  de autenticación, obligaría a calcular el hash antes de limitar, que es
  justamente el costo que este ADR busca acotar. Se acepta y se revisará si
  aparece abuso real; el contador `physical.checkin.rate.limited` con la
  dimensión `device` permite detectarlo.
* **Riesgo secundario:** `trusted-proxy-cidrs` demasiado amplio permite
  falsificar la IP. El default de desarrollo (`172.16.0.0/12`) confía en toda la
  red Docker y debe acotarse por entorno.
* **Reversibilidad:** alta. Es un puerto con una implementación; quitar el
  límite es retirar la llamada y el bean, sin migraciones.

## Referencias y Decisiones Relacionadas

* Issue #309; follow-up de #266 (decisión D9).
* Complementa a: ADR-0033 (rate limiting atómico con Redis Lua y fallo
  cerrado), ADR-0035 (propagación confiable del origen del cliente).
* Relacionado con: ADR-0020 (monolito modular), ADR-0021 (Clean Architecture).
* `docs/05-PHYSICAL-API.md` documenta el endpoint afectado.
