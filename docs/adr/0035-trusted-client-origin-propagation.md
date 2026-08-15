# ADR-0035: Propagación Confiable del Origen del Cliente entre Nginx, BFF y API

**Estado:** Propuesto
**Fecha:** 2026-08-15
**Decisores:** Arquitectura y equipo de Auth

## Contexto y Problema

El login web atraviesa dos saltos internos antes de llegar al módulo Auth:

```text
Usuario -> Nginx -> BFF (:8080) -> API (:8081)
```

El rate limiting de login necesita distinguir dos patrones semánticos:

* muchos fallos contra un mismo email, que pueden indicar ataque a una cuenta;
* muchos fallos desde un mismo origen contra emails diferentes, que pueden
  indicar *password spraying*.

El lado API puede derivar un fingerprint del cliente desde el request HTTP y
funciona correctamente para clientes directos, incluido Android. Sin embargo,
el adaptador HTTP del BFF que llama a Auth envía email y password, pero no
propaga la dirección observada por Nginx. Como consecuencia, la API usa
`remoteAddr`, que en el flujo web identifica al BFF. Todos los usuarios web
quedan colapsados en un único fingerprint de cliente.

Además, el contador de cliente actual se consume antes de validar las
credenciales y no se reinicia después de un login exitoso. Por lo tanto,
acumula requests exitosos y fallidos. Con un máximo de 30 intentos, el request
total número 31 dentro de la ventana puede bloquear a toda la web, no sólo al
origen responsable. No se debe reiniciar ese contador compartido al autenticar:
un atacante con una cuenta propia podría limpiarlo a voluntad. El defecto es
haber usado un mismo contador para protección volumétrica y detección de
fallos.

Nginx ya establece `X-Real-IP $remote_addr`, sobrescribiendo cualquier valor
entrante, y también construye `X-Forwarded-For` con
`$proxy_add_x_forwarded_for`. En la topología actual, `X-Real-IP` es el valor
canónico más simple para el salto Nginx -> BFF. Aun así, ese header sólo es
confiable cuando el peer inmediato pertenece a una red de proxies autorizada.

Esta ADR registra una propuesta. La propagación BFF -> API, la separación de
presupuestos y el límite específico del login web **todavía no están
implementados**.

## Factores Clave (Decision Drivers)

* Preservar la defensa contra *password spraying* sin agrupar a todos los
  usuarios web en el mismo presupuesto.
* Evitar que un cliente falsifique o rote su identidad mediante headers HTTP.
* Proteger el costo de recibir requests y ejecutar bcrypt lo antes y más barato
  posible.
* Mantener en Auth sólo los límites que necesitan conocer el resultado de la
  autenticación.
* Mantener la fuente del fingerprint detrás de un puerto para poder endurecer
  la identidad interna en el futuro sin cambiar el caso de uso.
* Conservar comportamiento seguro para clientes directos como Android.

## Opciones Consideradas

### Opción 1: Origen canónico saneado por Nginx y propagado por el BFF

* **Descripción:** el BFF acepta `X-Real-IP` sólo cuando su peer inmediato es
  un proxy confiable; en otro caso usa `remoteAddr`. Luego transporta un único
  origen canónico hasta el adaptador de Auth, que lo envía a la API como un
  `X-Forwarded-For` de un solo valor. No reenvía la cadena recibida.
* **Pros:** reutiliza el saneamiento que Nginx ya realiza; evita parsear cadenas
  de proxies en el BFF; conserva límites independientes por cliente web; no
  agrega secretos ni infraestructura.
* **Contras:** convierte los CIDR confiables y el aislamiento del BFF/API en
  controles de seguridad activos; requiere cambios coordinados en Nginx, BFF y
  API.

### Opción 2: Header interno firmado o mTLS

* **Descripción:** el BFF envía un fingerprint interno acompañado por HMAC, o
  autentica el salto BFF -> API mediante mTLS.
* **Pros:** reduce la dependencia exclusiva de la topología de red y permite
  autenticar explícitamente al emisor.
* **Contras:** exige gestión y rotación de secretos o certificados, protección
  contra replay y un contrato interno propietario.
* **Resultado:** diferida. Puede retomarse si la red interna deja de ser una
  frontera de confianza suficiente.

### Opción 3: Aplicar todos los límites en Nginx o BFF

* **Descripción:** mover tanto el techo volumétrico como los presupuestos por
  email y por origen al borde.
* **Pros:** el borde conoce la conexión del cliente y puede rechazar tráfico
  antes de llegar a Java o Redis.
* **Contras:** Nginx no conoce el resultado semántico del login; duplicaría o
  fragmentaría la política para Android y otros clientes directos; complica la
  auditoría de fallos de autenticación.
* **Resultado:** rechazada como solución completa. Nginx sólo debe asumir el
  techo volumétrico.

### Opción 4: Aumentar el límite compartido

* **Descripción:** elevar `client-max-attempts` hasta absorber el tráfico web
  agregado.
* **Pros:** cambio operativo inmediato y sin código.
* **Contras:** no distingue tráfico legítimo agregado de *password spraying*;
  un límite inalcanzable deja de proteger.
* **Resultado:** rechazada.

## Decisión Propuesta

Se propone elegir la **Opción 1**, combinada con una separación explícita de
responsabilidades:

1. **Nginx:** imponer un techo de requests por origen para el `POST /login` del
   BFF. Este límite volumétrico protege el borde y el costo de bcrypt; no intenta
   interpretar si las credenciales fueron válidas.
2. **BFF:** obtener una dirección canónica desde `X-Real-IP` sólo si la conexión
   inmediata proviene de un proxy confiable; de lo contrario usar
   `remoteAddr`. Transportar ese valor por el flujo de login hasta
   `AuthApiAdapter`.
3. **BFF -> API:** enviar el origen canónico como un `X-Forwarded-For` de un
   solo valor; nunca copiar la cadena recibida del cliente.
4. **API/Auth:** mantener dos presupuestos semánticos en Redis: fallos por email
   y fallos por origen. Verificarlos antes de bcrypt y registrar el consumo sólo
   después de un fallo contabilizable. Un éxito puede limpiar el presupuesto
   del email, pero no el del origen.
5. **Abstracción:** conservar la obtención del fingerprint detrás de un puerto,
   de modo que una futura identidad firmada o mTLS no obligue a modificar el
   caso de uso.

La decisión permanece **Propuesta** hasta que los cambios se implementen y se
validen extremo a extremo.

## Frontera de Confianza

La cadena prevista es:

```text
Internet
  -> Nginx sanea X-Real-IP con $remote_addr
  -> BFF confía en X-Real-IP sólo si remoteAddr pertenece a un CIDR confiable
  -> BFF propaga un X-Forwarded-For de un solo valor
  -> API acepta ese origen sólo desde BFF/Nginx confiables
```

Las siguientes condiciones son obligatorias:

* BFF y API no deben quedar expuestos directamente a Internet en producción.
* Los CIDR configurados deben ser los mínimos de los proxies reales; un rango
  Docker amplio no es un valor seguro por defecto para producción.
* Si el peer no es confiable o falta el header, cada aplicación debe ignorar el
  header y usar `remoteAddr`.
* El valor recibido no debe persistirse ni usarse como key en claro: la API lo
  transforma inmediatamente en un fingerprint criptográfico.
* Si se incorpora un CDN o balanceador delante de Nginx, `$remote_addr` puede
  pasar a identificar ese proxy. Antes del cambio se debe configurar y probar
  el módulo `real_ip` con una lista explícita de emisores confiables.

## Consecuencias

### Positivas

* Los usuarios web dejan de compartir el fingerprint del BFF.
* El límite de origen vuelve a detectar *password spraying* sin castigar el
  tráfico web agregado.
* Los logins exitosos ya no consumen un presupuesto definido como fallos.
* Nginx absorbe tráfico volumétrico antes de usar recursos de aplicación o
  Redis.
* La semántica de autenticación y su auditoría permanecen en Auth.

### Negativas / Deuda Técnica

* La configuración de red y proxies confiables pasa a formar parte del modelo
  de seguridad y requiere validación por entorno.
* La solución cruza tres capas y necesita pruebas integradas, no sólo unitarias.
* Usuarios distintos detrás de una NAT legítima aún comparten presupuesto por
  origen; los umbrales y ventanas deben contemplarlo.
* La autenticación fuerte del salto interno queda diferida hasta que HMAC o
  mTLS estén justificados operacionalmente.

### Riesgos y Reversibilidad

* **BFF expuesto directamente:** un atacante podría enviar headers falsos.
  Mitigación: aislamiento de red, fallback a `remoteAddr` y validación del peer.
* **CIDR demasiado amplio:** un emisor no autorizado podría rotar el origen.
  Mitigación: CIDR mínimos por ambiente y pruebas negativas de spoofing.
* **CDN futuro:** Nginx podría identificar al CDN como cliente. Mitigación:
  configurar `real_ip` antes de introducir ese salto.
* **NAT compartida:** usuarios legítimos pueden compartir el presupuesto.
  Mitigación: ajustar el límite semántico con métricas, sin convertirlo en un
  límite agregado global.
* **Reversibilidad:** media. El techo de Nginx y los límites Redis son
  configurables; cambiar la fuente del fingerprint queda aislado por el puerto,
  pero retirar la propagación requiere coordinar BFF y API.

## Plan de Implementación

La propuesta se divide para mantener revisiones cohesivas:

1. **PR1 — eliminar la duplicación del login BFF:** hacer que
   `BffAuthenticationProvider` delegue en `LoginUseCase`, sin cambiar contratos
   HTTP ni rate limiting.
2. **PR2 — propagar el origen confiable:** capturar los detalles del request con
   `AuthenticationDetailsSource`, transportar la dirección canónica por
   `LoginCommand`/`LoginUseCase`/`AuthApiClient` y enviarla desde
   `AuthApiAdapter`; generalizar la configuración de proxies confiables.
3. **PR3 — techo volumétrico en Nginx:** limitar específicamente `POST /login`
   del BFF por origen, sin consumir presupuesto al servir el formulario u otros
   métodos.
4. **PR4 — presupuestos semánticos en API:** separar verificación y registro de
   fallos; conservar fallos por email y por origen; impedir que éxitos consuman
   o limpien el presupuesto del origen.

El orden evita introducir presupuestos semánticos por origen mientras todo el
tráfico web todavía comparte identidad.

## Plan de Pruebas

* Probar que dos IP distintas a través de Nginx y BFF producen fingerprints
  distintos en Auth.
* Probar que múltiples usuarios detrás de una misma IP comparten sólo el
  presupuesto de origen, no el presupuesto por email.
* Probar que fallos contra emails distintos desde un origen alcanzan el límite
  de *password spraying*.
* Probar que logins exitosos no consumen ni reinician el presupuesto de fallos
  por origen.
* Probar que un header `X-Real-IP` enviado directamente al BFF se ignora cuando
  el peer no es confiable.
* Probar que la API ignora el origen propagado si el peer inmediato no es un
  BFF/Nginx confiable.
* Probar que una IP limitada no bloquea a otra IP.
* Probar el límite Nginx para `POST /login` sin afectar `GET /login` ni rutas no
  relacionadas.
* Ejecutar una prueba integrada Nginx -> BFF -> API que cubra propagación,
  spoofing, `429` y `Retry-After`.

## Referencias y Decisiones Relacionadas

* Complementa a [ADR-0033](0033-activation-rate-limiting-strategy.md), que
  conserva su decisión para activación y el salto directo Nginx -> API.
* Relacionada con [ADR-0031](0031-bff-session-strategy.md), por el flujo de login
  y la custodia de tokens en el BFF.
* [`docs/29-NGINX-REVERSE-PROXY.md`](../29-NGINX-REVERSE-PROXY.md)
* [`docs/03-AUTH-API.md`](../03-AUTH-API.md)
* `infra/docker/nginx/conf/conf.d/proxy_params.conf`
* `bff/src/main/java/com/menta/bff/infrastructure/adapter/AuthApiAdapter.java`
* `bff/src/main/java/com/menta/bff/infrastructure/security/BffAuthenticationProvider.java`
* `api/auth/src/main/java/com/menta/auth/infrastructure/web/controller/ClientFingerprint.java`
