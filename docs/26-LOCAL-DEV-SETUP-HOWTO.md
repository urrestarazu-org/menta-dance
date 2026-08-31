# Guía de Instalación Local

Esta guía te ayudará a levantar el proyecto **Menta Dance** en tu máquina local para desarrollo.

---

## Inicio Rápido

Si ya tenés todo instalado:

```bash
# 1. Configurar variables de entorno
cp .env.example .env
# Editar .env y reemplazar los placeholders

# 2. Levantar TODO (infraestructura + API + BFF) en un solo comando
./scripts/dev.sh start

# 3. Verificar que todo esté corriendo
./scripts/dev.sh status

# 4. (Opcional) Registrar usuario de prueba para Bruno
curl -i -X POST "http://localhost:8081/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"password123","role":"STUDENT"}'

# 5. (Opcional) Ejecutar tests de Bruno CLI
cd bruno/BFF-Session-Custody && npx @usebruno/cli run --env Local .
```

**URLs:**
- API: http://localhost:8081
- BFF: http://localhost:8080
- Grafana (logs): http://localhost:3000
- Mailpit (correo local): http://localhost:8025

**Comandos útiles del script dev.sh:**
```bash
./scripts/dev.sh start     # Levantar todo
./scripts/dev.sh stop      # Detener todo
./scripts/dev.sh restart   # Reiniciar todo
./scripts/dev.sh status    # Ver estado
./scripts/dev.sh logs api  # Ver logs de API
./scripts/dev.sh logs bff  # Ver logs de BFF
```

### Recorrido E2E aislado de catálogo y contenido

Para verificar el recorrido reproducible de catálogo/contenido (registro,
activación real por Mailpit, administración y lectura pública), ejecutá desde
la raíz:

```bash
scripts/e2e/catalog-content.sh
```

El script requiere Docker Compose v2, JDK 21, Node **20.11.1**, `curl`, `jq`,
el wrapper de Gradle y un `.env` local. No utiliza el Compose raíz/deprecado:
crea exclusivamente el proyecto `menta-e2e-catalog-content`, con sus propios
puertos y volúmenes. Por eso puede ejecutarse aunque tu stack habitual esté
levantado.

El runner genera el correo del estudiante, obtiene el token de activación de
Mailpit en memoria y lo pasa a Bruno sólo como variable de ejecución. No copies
ni guardes tokens de activación, access tokens o refresh tokens en `.bru`,
`.env`, logs o artefactos versionados.

Para eliminar **solamente** ese estado aislado cuando terminaste de diagnosticar:

```bash
scripts/e2e/catalog-content.sh --clean
```

`--clean` no toca contenedores, volúmenes ni puertos del entorno de desarrollo
ordinario. Si el recorrido falla, conservá el stack aislado y revisá
`.dev-logs/e2e-catalog-content-api.log`; no ejecutes `docker compose down -v`
como sustituto.

### Simulador local de Mercado Pago (issue #128)

El mismo comando (`scripts/e2e/catalog-content.sh`) ejecuta a continuación el
recorrido `E2E/mercadopago`, contra la misma instancia de `:api:app` (perfil
adicional `e2e-mercadopago` activado junto a `e2e-catalog-content`). Prueba
checkout, webhook firmado, worker de fulfillment, idempotencia de webhooks
duplicados y reconciliación por inconsistencia — todo a través de endpoints
locales guardados por perfil, sin llamar nunca a `api.mercadopago.com`.

**Qué demuestra:**
- El checkout real de Billing (`POST /api/v1/billing/subscriptions`) persiste
  sus registros normales usando el adaptador local de preferencias.
- Un webhook con HMAC firmado y vigente llega al controller público real
  (`PaymentWebhookController`), pasa por el verificador
  `HmacSha256WebhookSignatureVerifier`, el inbox durable y el worker
  asíncrono existentes — nada de eso se reemplaza ni se bypasea.
- Un resultado aprobado activa la suscripción; un webhook duplicado es
  idempotente (no se aplica el fulfillment dos veces); un resultado
  inconsistente (mismatch de `merchantAccountId`) sigue el camino existente
  de reconciliación y la suscripción **nunca** llega a `ACTIVE`.

**Modelo de seguridad (guardado por perfil):**
- Los adaptadores locales (`LocalMercadoPagoPaymentPreferenceAdapter`,
  `LocalMercadoPagoPaymentProviderAdapter`, `LocalWebhookPreparationService`,
  `LocalMercadoPagoScenarioController`) sólo se registran bajo
  `@Profile("e2e-mercadopago")`. Los adaptadores reales de Mercado Pago usan
  `@Profile("!e2e-mercadopago")` — son mutuamente excluyentes, nunca coexisten.
- `BillingConfiguration` falla el arranque (`@PostConstruct`) si el perfil
  `prod`/`production`/`staging` está activo y el secreto HMAC sigue siendo el
  valor de desarrollo por defecto — el simulador local no puede activarse
  accidentalmente en un ambiente productivo.
- El simulador nunca expone el secreto HMAC real en logs ni en las respuestas
  de sus endpoints; sólo devuelve la firma ya calculada para que Bruno la
  reenvíe al endpoint público real.
- El endpoint de preparación (`/api/v1/e2e/mercadopago/*-webhook`) sólo
  calcula y entrega una firma válida; no marca pagos, no activa suscripciones
  ni escribe filas del inbox directamente — esos efectos siguen siendo
  responsabilidad exclusiva del checkout y del worker reales.

**Modelo de resultados (outcome model):**
- Implementados: `approved` (fulfillment exitoso) e `inconsistent`
  (mismatch determinístico de `merchantAccountId` que dispara reconciliación).
- **Brecha conocida, fuera de alcance de este cambio**: `design.md` también
  contempla `pending` y `rejected` como resultados del proveedor. No están
  implementados — sólo se prepararon los dos escenarios requeridos por la
  especificación (`approved` e `inconsistent`). Quedan como trabajo futuro
  para quien amplíe el simulador.

**Gotchas documentados para quien mantenga este simulador:**
1. **Defecto de valor por defecto de configuración (ya corregido en el
   script)**: `BillingConfiguration` usa `""` como default de
   `billing.mercadopago.merchant-account-id`, mientras que
   `LocalWebhookPreparationService` usa `"local-merchant"` — es la misma
   propiedad Spring, con dos defaults distintos. Si quedara sin fijar, TODO
   webhook del camino aprobado fallaría `Payment.matchesExpected` en
   `merchantAccountId` y terminaría en reconciliación en vez de activar la
   suscripción. Además, `billing.webhook.mercadopago.hmac-secret` no tiene
   default en `LocalWebhookPreparationService` (a diferencia de
   `BillingConfiguration`), por lo que el bean falla al construirse si no se
   fija. El script fija ambas variables explícitamente
   (`BILLING_MERCADOPAGO_MERCHANT_ACCOUNT_ID` y
   `BILLING_WEBHOOK_MERCADOPAGO_HMAC_SECRET`) al levantar la API.
2. **Comportamiento de reconciliación no es cancelación**: un resultado
   inconsistente lleva el pago a `PaymentStatus.ReconciliationRequired`, que
   `Payment.isTerminal()` trata como **no terminal** — la suscripción queda
   `PENDING` (nunca se cancela ni libera el cupo de una suscripción por
   usuario) hasta que un operador reconcilie manualmente. Por eso el
   recorrido usa dos estudiantes independientes: uno para el camino
   aprobado/duplicado y otro exclusivo para el camino de mismatch, en vez de
   reutilizar la misma cuenta.

**Cómo ejecutarlo:** no requiere un comando separado — corre automáticamente
al final de `scripts/e2e/catalog-content.sh` (ver arriba). Usa Bruno CLI sobre
`bruno/E2E/mercadopago/01-registration` y `02-journey`, con el mismo
environment `e2e-catalog-content`.

### Adaptador local de Bunny.net (issue #129)

Para verificar localmente el streaming de video sin credenciales reales de
Bunny.net y sin salir nunca a la CDN real, ejecutá desde la raíz:

```bash
scripts/e2e/bunny-net.sh
```

El script requiere los mismos prerrequisitos que `catalog-content.sh`
(Docker Compose v2, JDK 21, Node **20.11.1**, `curl`, `jq`, el wrapper de
Gradle y un `.env` local). No comparte proyecto de Compose, puertos ni
volúmenes con `catalog-content.sh`: crea exclusivamente el proyecto
`menta-e2e-bunny-net`, por lo que ambos recorridos pueden ejecutarse en
paralelo sin colisionar.

Este único comando reproduce, en un solo recorrido de Bruno, los tres
escenarios de aceptación del issue #129:

1. **Streaming de preview sin suscripción** — un alumno anónimo obtiene una
   URL firmada local para una lección de un módulo `isPreview`, sin tocar
   Bunny.net.
2. **Denegación D7** — la misma lección protegida de un curso que no
   pertenece a ningún plan de facturación devuelve `403` sin exponer
   `videoId` ni URL firmada (ADR-0041: un curso sin plan ya no es público
   por defecto).
3. **Acceso premium tras checkout real** — el mismo alumno se suscribe con
   el simulador local de Mercado Pago (checkout, webhook firmado, worker de
   fulfillment) y sólo entonces la lección protegida del curso *planned*
   devuelve `200` con una URL firmada determinista.

**Cómo funciona el adaptador local (ADR-0040):**
- `VirtualConfiguration` registra `LocalBunnyNetSignatureService` únicamente
  bajo `@Profile("e2e-bunny-net")`; el adaptador real
  (`StringFormatBunnyNetSignatureService`) usa `@Profile("!e2e-bunny-net")`
  — son mutuamente excluyentes, nunca coexisten. El propio factory `@Bean`
  falla el arranque si `e2e-bunny-net` se combina con `prod`/`production`/
  `staging`, antes de que el servidor acepte ningún request.
- La firma (`sig`) es SHA-256 de un salt público + `videoLibraryId` +
  `videoId` + `exp` — determinística, sin credencial real y sin uso posible
  contra la CDN de producción.
- El runner fija `APP_CDN_BUNNYNET_PULLZONEHOSTNAME=https://local-bunny-net.invalid`
  (dominio reservado por RFC 2606, nunca resuelve) y
  `APP_CDN_BUNNYNET_VIDEOLIBRARYID=e2e-library`.

**Fixtures usadas (perfil `e2e-bunny-net`):**
- `E2eBunnyNetVirtualFixture` (Virtual) siembra dos cursos con id fijo: uno
  sin plan asociado (`UNPLANNED_COURSE_ID`, con un módulo preview y uno
  protegido) y otro con un módulo protegido vinculado a un plan
  (`PLANNED_COURSE_ID`).
- `E2eBunnyNetBillingFixture` (Billing, perfil compuesto
  `e2e-bunny-net & e2e-mercadopago`) vincula `PLANNED_COURSE_ID` al mismo
  plan fixture (`E2eMercadoPagoBillingFixture.PLAN_ID`) que usa el recorrido
  de Mercado Pago — el checkout de este recorrido es el mismo checkout real,
  no un simulacro adicional.

Para eliminar **solamente** ese estado aislado cuando terminaste de
diagnosticar:

```bash
scripts/e2e/bunny-net.sh --clean
```

`--clean` no toca contenedores, volúmenes ni puertos de `catalog-content.sh`
ni del entorno de desarrollo ordinario. Si el recorrido falla, conservá el
stack aislado y revisá `.dev-logs/e2e-bunny-net-api.log`.

---

## Requisitos Previos

Asegurate de tener instalado:

- **JDK 21** (recomendado: Eclipse Temurin o Amazon Corretto)
  ```bash
  java -version  # Debe mostrar versión 21.x
  ```
- **Docker** y **Docker Compose**
  ```bash
  docker --version
  docker-compose --version
  ```
- **Node 20.11.1** para los assets y los recorridos E2E. Si usás nvm, el
  repositorio incluye `.nvmrc`; ejecutá desde la raíz antes de trabajar:
  ```bash
  nvm install
  nvm use
  node --version  # v20.11.1
  ```
- **Git**
- **Android Studio Quail 3 (2026.1.3) o más nuevo** (solo si vas a trabajar en la app
  móvil) — versiones anteriores a AGP 9.3 (julio 2026) fallan el sync sin causa obvia. Ver
  [ADR-0036](adr/0036-android-agp9-tooling-migration.md) y sección 6 más abajo.

---

## 1. Clonar el Repositorio

```bash
git clone https://github.com/urrestarazu-org/menta-dance.git
cd menta-dance
```

---

## 2. Configurar Variables de Entorno

**⚠️ IMPORTANTE**: Configurá este archivo **ANTES** de levantar la infraestructura por primera vez.

Crear el archivo `.env` en la **raíz del proyecto** (al lado de `docker-compose.yml`):

```bash
cp .env.example .env
```

Editar `.env` y reemplazar los placeholders con valores locales:

```env
MYSQL_ROOT_PASSWORD=root123
MYSQL_APP_USER=menta_app
MYSQL_APP_PASSWORD=app123
```

> **Nota**:
> - Estos passwords son solo para desarrollo local. Podés usar cualquier valor.
> - MySQL solo aplica estas variables cuando **crea el volumen por primera vez**.
> - Si cambiás el `.env` después de haber levantado MySQL, tenés que eliminar el volumen: `docker-compose down -v`

---

## 3. Levantar la Infraestructura

Ejecutar el script de inicio:

```bash
./scripts/start-infra.sh
```

Esto levantará:
- **MySQL 8.0** en puerto `3306`
- **Redis 7.4** en puerto `6379`
- **OpenTelemetry Collector** en puertos `4317` (gRPC) y `4318` (HTTP)
- **Loki** en puerto `3100` (logs)
- **Grafana** en puerto `3000` (visualización)

Deberías ver:

```
✅ All infrastructure is ready!

🔗 Services:
  MySQL:   localhost:3306 (application user: menta_app)
  Redis:   localhost:6379
  Loki:    localhost:3100
  Grafana: http://localhost:3000
```

### Verificar que los servicios estén corriendo

```bash
docker-compose ps
```

Todos los contenedores deben estar en estado `Up` (healthy).

---

## 4. Ejecutar la API y BFF (Backend + Frontend)

### Opción A: Script consolidado (Recomendado)

El proyecto incluye un script que levanta **infraestructura + API + BFF** en un solo comando:

```bash
./scripts/dev.sh start
```

Esto ejecutará:
1. Levanta infraestructura (MySQL, Redis, OpenTelemetry, Loki, Grafana)
2. Espera a que MySQL y Redis estén listos
3. Levanta la API en puerto 8081
4. Levanta el BFF en puerto 8080
5. Verifica health checks de API y BFF

El output es visible en la terminal y también se guarda en `.dev-logs/api.log` y `.dev-logs/bff.log`.

**Verificar estado:**
```bash
./scripts/dev.sh status
```

**Ver logs en tiempo real:**
```bash
./scripts/dev.sh logs api   # Logs de API
./scripts/dev.sh logs bff   # Logs de BFF
```

**Detener todo:**
```bash
./scripts/dev.sh stop
```

**Reiniciar (útil después de cambios de código):**
```bash
./scripts/dev.sh restart
```

### Opción B: Ejecutar manualmente (para debugging)

Si preferís ejecutar cada servicio en terminales separadas:

**Terminal 1 - Infraestructura:**
```bash
./scripts/start-infra.sh
```

**Terminal 2 - API:**
```bash
./gradlew :api:app:bootRun
```

**Terminal 3 - BFF:**
```bash
./gradlew :bff:bootRun
```

> **Nota**: Gradle lee automáticamente el archivo `.env` de la raíz y pasa las variables a Spring Boot.

### Verificar que los servicios estén funcionando

```bash
# Health check de API
curl http://localhost:8081/actuator/health

# Health check de BFF
curl http://localhost:8080/actuator/health
```

Ambos deberían retornar:
```json
{"status":"UP"}
```

---

## 5. Testing con Bruno

El proyecto incluye colecciones de **Bruno** para testing manual y automatizado de la API y el BFF.

### Requisitos

- **Bruno CLI** instalado globalmente:
  ```bash
  npm install -g @usebruno/cli
  ```

### Preparar usuario de prueba

Antes de ejecutar los tests, registrá un usuario en la API:

```bash
curl -i -X POST "http://localhost:8081/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"student@example.com","password":"password123","role":"STUDENT"}'
```

La respuesta es `202 Accepted` sin cuerpo. Abrí Mailpit en
`http://localhost:8025`, copiá el token del enlace sólo a una variable runtime
de Bruno y ejecutá `Activate Account`; no lo guardes en `.bru` ni `.env`.

### Colección BFF Session Custody

Esta colección prueba el flujo completo de autenticación con sesiones:

```bash
cd bruno/BFF-Session-Custody
npx @usebruno/cli run --env Local .
```

**Tests incluidos:**
1. **Login** - POST /login con credenciales
   - Verifica 302 redirect a /dashboard
   - Verifica cookie SESSION con HttpOnly, Secure, SameSite=Lax
2. **Dashboard (Authenticated)** - GET /dashboard con sesión
   - Verifica 200 OK y HTML response
3. **Logout** - POST /logout con sesión
   - Verifica 302 redirect a /login?logout
   - Verifica que la cookie SESSION se expire (Max-Age=0)
4. **Dashboard (After Logout)** - GET /dashboard sin sesión
   - Verifica 302 redirect a /login

**Resultado esperado:**
```
📊 Execution Summary
┌───────────────┬──────────────┐
│ Metric        │    Result    │
├───────────────┼──────────────┤
│ Status        │    ✓ PASS    │
├───────────────┼──────────────┤
│ Requests      │ 4 (4 Passed) │
├───────────────┼──────────────┤
│ Tests         │    11/11     │
└───────────────┴──────────────┘
```

### Variables de entorno de Bruno

Las credenciales de prueba están configuradas en `bruno/BFF-Session-Custody/environments/Local.bru`:

```
vars {
  bff_url: http://localhost:8080
  auth_url: http://localhost:8081
  email: student@example.com
  password: password123
}
```

Si querés usar credenciales diferentes, editá este archivo antes de ejecutar los tests.

### Testing manual con Bruno GUI

1. Abrir Bruno desktop app
2. Seleccionar **Open Collection**
3. Navegar a `bruno/BFF-Session-Custody`
4. Seleccionar entorno **Local** en el dropdown superior
5. Ejecutar requests en orden (1 → 2 → 3 → 4)

### Testing con curl (alternativa)

Si preferís curl, seguí los pasos en `bruno/BFF-Session-Custody/TESTING-CURL.md`:

```bash
# 1. Login
curl -i --max-redirs 0 -X POST "http://localhost:8080/login" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "username=student@example.com" \
  --data-urlencode "password=password123" \
  -c /tmp/menta-bff-cookies.txt

# 2. Extraer SESSION ID (workaround para HTTP local)
SESSION_ID=$(awk '$6 == "SESSION" { print $7 }' /tmp/menta-bff-cookies.txt)

# 3. Dashboard autenticado
curl -i "http://localhost:8080/dashboard" \
  -H "Cookie: SESSION=${SESSION_ID}"

# 4. Logout
curl -i --max-redirs 0 -X POST "http://localhost:8080/logout" \
  -H "Cookie: SESSION=${SESSION_ID}"
```

### Notas importantes

- **CSRF está deshabilitado** para testing local (configurado en `BffSecurityConfig.java`)
- El **refresh_token** se retorna en el header `X-Refresh-Token` (no en el body JSON)
- La cookie SESSION usa el flag `Secure`, por eso el testing local con HTTP requiere enviarla manualmente
- En producción (HTTPS), las cookies Secure se envían automáticamente

---

## 6. Ejecutar la App Android (Opcional)

Contexto de las decisiones de tooling (versión de AGP, flags de compatibilidad, pin del
JDK del daemon) en [ADR-0036](adr/0036-android-agp9-tooling-migration.md).

### 6.1 Requisitos específicos de Android

- **Android Studio Quail 3** (2026.1.3) **o más nuevo**. Se confirmó que Quail 1 (build de
  mayo 2026, previo al release de AGP 9.3) falla el sync sin causa obvia; Quail 3 (agosto
  2026) funciona. `Android Studio → About` para verificar tu versión.
- **MySQL corriendo.** La app le pega directo al API (`/api/v1/auth/*` en el puerto
  `8081`), no al BFF. Si `OutboxBlacklistReconciler` u otro scheduled task tira
  `Communications link failure` / `Connection refused` al puerto `3306`, es que la
  infraestructura local no está levantada — correr `./scripts/dev.sh start` (o
  `infra/docker/manage.sh start --no-nginx`) primero.

### 6.2 Abrir el proyecto (IMPORTANTE)

**Abrir el monorepo completo (`menta-dance/`), nunca la carpeta `android/` directamente.**
Abrir `android/` como raíz genera un wrapper de Gradle duplicado ahí adentro
(`android/gradlew`, `android/gradle/`, `android/settings.gradle.kts`), que compite con el
build real del monorepo. Si ves esos archivos aparecer sin que los hayas creado vos, son
ese síntoma — borralos y volvé a abrir desde la raíz. Están en `.gitignore` para que no se
commiteen por accidente.

1. `File → Open` y elegir la carpeta raíz `menta-dance/`, **no** `menta-dance/android/`.
2. Android Studio debería detectar y sincronizar automáticamente el proyecto Gradle
   multi-módulo completo (API, BFF y Android).
3. Configurar un emulador (`Device Manager`) o conectar un dispositivo físico.
4. Seleccionar el módulo `android` en el dropdown de configuración de Run, junto al
   dispositivo elegido.
5. Botón **Run** (▶ verde).

### 6.3 Troubleshooting de sync

Si el sync falla, en este orden:

1. **`File → Sync Project with Gradle Files`** (ícono del elefante). Correr una tarea
   puntual de Gradle desde el panel lateral *no* es lo mismo que un sync completo — el
   panel puede mostrar `Task list not built...` aunque una tarea individual haya
   compilado bien.
2. **`File → Invalidate Caches / Restart`**, marcando también *"Clear VCS Log caches and
   indexes"* y *"Clear downloaded shared indexes"* — no vienen tildadas por default y la
   primera pasada puede no limpiar todo.
3. Si el error es `removeContentEntry: ... still exists after removing` (o similar,
   apuntando a una carpeta de `build/generated/` que no existe en disco): es una condición
   de carrera conocida del VFS de IntelliJ Platform entre sincronizaciones consecutivas.
   Reintentar el sync una vez más suele resolverlo solo.
4. Si compila por CLI (`./gradlew :android:assembleDebug`) pero el sync del IDE sigue
   fallando con un error distinto, el problema es del IDE, no del build — no sigas
   ajustando `build.gradle.kts` a ciegas.

### 6.4 Compilar/testear por línea de comandos

```bash
# apuntar Gradle al SDK (una sola vez, local.properties está gitignored)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

./gradlew :android:assembleDebug
./gradlew :android:testDebugUnitTest       # unit tests
```

Los tests instrumentados (`connectedDebugAndroidTest`) necesitan un emulador corriendo:

```bash
emulator -avd <nombre-avd> -no-window -no-audio -no-snapshot-load -gpu swiftshader_indirect &
# esperar a que termine de bootear
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done

./gradlew :android:connectedDebugAndroidTest

adb emu kill   # apagar el emulador al terminar
```

**Ninguno de estos tests corre en CI hoy** (ver Deuda Técnica en ADR-0036) — correrlos
localmente antes de un PR que toque `android/` es la única red de seguridad que existe
por ahora.

---

## 7. Visualizar Logs en Grafana

La aplicación está configurada para enviar logs automáticamente a **Grafana** mediante **OpenTelemetry** y **Loki**.

### Acceder a Grafana

1. Abrí tu navegador en **http://localhost:3000**
2. Grafana está configurado sin autenticación para desarrollo local (acceso automático)

### Explorar Logs

1. En el menú lateral izquierdo, hacé click en el ícono de **brújula** 🧭 (**Explore**)
2. En el dropdown superior, seleccioná **Loki** como datasource
3. Ejecutá una query para ver los logs:

   **Opción A - Query Builder (más fácil):**
   - Click en **Label filters**
   - Seleccioná `service_name` = `menta-dance-api`
   - Click en **Run query** (botón azul arriba a la derecha)

   **Opción B - Code (más flexible):**
   - Click en **Code** (arriba a la derecha del query builder)
   - Escribí:
     ```
     {service_name="menta-dance-api"}
     ```
   - Presioná **Shift + Enter** o click en **Run query**

### Queries Útiles

```logql
# Todos los logs de la API
{service_name="menta-dance-api"}

# Solo logs con errores
{service_name="menta-dance-api"} |= "error"

# Health checks
{service_name="menta-dance-api"} |= "actuator/health"

# Excluir logs de test
{service_name="menta-dance-api"} != "test"

# Logs con nivel DEBUG de com.menta
{service_name="menta-dance-api"} |= "com.menta"

# Parsear logs JSON (si aplica)
{service_name="menta-dance-api"} | json
```

### Ajustar Rango de Tiempo

- Arriba a la derecha hay un selector de tiempo
- Podés elegir: **Last 5 minutes**, **Last 15 minutes**, **Last 1 hour**, etc.
- O definir un rango custom

### Nota Técnica

Los logs se envían automáticamente mediante **OpenTelemetry Java Agent** configurado en la API. No necesitás configurar nada adicional.

El flujo es:
```
Spring Boot → OpenTelemetry Agent → OTEL Collector → Loki → Grafana
```

---

## Resumen de Puertos

| Servicio | Puerto | URL |
|----------|--------|-----|
| API (Backend) | 8081 | http://localhost:8081 |
| BFF (Frontend Web) | 8080 | http://localhost:8080 |
| MySQL | 3306 | localhost:3306 |
| Redis | 6379 | localhost:6379 |
| OTEL Collector (gRPC) | 4317 | localhost:4317 |
| OTEL Collector (HTTP) | 4318 | localhost:4318 |
| Loki | 3100 | localhost:3100 |
| Grafana | 3000 | http://localhost:3000 |

---

## Scripts Disponibles

El proyecto incluye scripts para facilitar el manejo de servicios:

### Script dev.sh (Recomendado)

Script consolidado que maneja **infraestructura + API + BFF**:

```bash
./scripts/dev.sh start      # Levantar infraestructura + API + BFF
./scripts/dev.sh stop       # Detener API, BFF e infraestructura
./scripts/dev.sh restart    # Reiniciar todo (útil después de cambios de código)
./scripts/dev.sh status     # Ver estado de procesos y health checks
./scripts/dev.sh logs api   # Ver logs de API en tiempo real
./scripts/dev.sh logs bff   # Ver logs de BFF en tiempo real
./scripts/dev.sh help       # Mostrar ayuda
```

**Archivos de logs:**
- API: `.dev-logs/api.log`
- BFF: `.dev-logs/bff.log`

### Scripts individuales (para control granular)

**Infraestructura (Docker):**
```bash
./scripts/start-infra.sh    # Levantar solo infraestructura
docker compose down         # Detener infraestructura
docker compose logs -f      # Ver logs de Docker
```

**API (Backend):**
```bash
./gradlew :api:app:bootRun      # Levantar API manualmente
pkill -f 'api:app:bootRun'       # Detener API
```

**BFF (Frontend Web):**
```bash
./gradlew :bff:bootRun           # Levantar BFF manualmente
pkill -f 'bff:bootRun'            # Detener BFF
```

**Ejemplo de flujo de trabajo con dev.sh:**

```bash
# Levantar todo
./scripts/dev.sh start

# Ver estado
./scripts/dev.sh status

# Hacer cambios en el código...

# Reiniciar para aplicar cambios
./scripts/dev.sh restart

# Ver logs en tiempo real
./scripts/dev.sh logs api

# Detener todo
./scripts/dev.sh stop
```

---

## Detener los Servicios

### Usando dev.sh (Recomendado)

```bash
./scripts/dev.sh stop    # Detener API, BFF e infraestructura
```

Este comando:
1. Detiene API (SIGTERM, luego SIGKILL si es necesario)
2. Detiene BFF (SIGTERM, luego SIGKILL si es necesario)
3. Detiene infraestructura (docker compose down)

### Detener solo infraestructura

```bash
docker compose down              # Detener contenedores
docker compose down -v           # Detener Y eliminar volúmenes (base de datos)
```

### Forma manual (si no usaste dev.sh)

**API y BFF**: Presionar `Ctrl+C` en las terminales donde están corriendo.

**Infraestructura**:
```bash
docker compose down
```

---

## Troubleshooting

### Error: "Access denied for user 'menta_app'@'...' (using password: YES)"

Este es el error más común. Spring Boot no puede conectarse a MySQL.

**Causa**: MySQL ya fue inicializado con credenciales diferentes a las del `.env` actual.

**Solución**:

```bash
# 1. Detener y eliminar volúmenes (esto borra los datos locales)
docker-compose down -v

# 2. Verificar que el .env tiene las credenciales correctas
cat .env

# 3. Levantar de nuevo
./scripts/start-infra.sh

# 4. Ejecutar la API
./gradlew :api:app:bootRun
```

> **Importante**: MySQL solo aplica las variables `MYSQL_*` cuando inicializa un volumen vacío. Cambiar el `.env` después no actualiza las contraseñas en la base de datos existente.

---

### Error: "unknown shorthand flag: 'd' in -d"

Tu sistema usa `docker-compose` (con guion) pero el script usa `docker compose` (sin guion).

**Solución**: El script `start-infra.sh` ya está configurado para usar `docker-compose`.

---

### Error: "MYSQL_ROOT_PASSWORD is not set"

No configuraste el archivo `.env` correctamente.

**Solución**:
1. Verificar que `.env` existe en la raíz del proyecto
2. Verificar que contiene las variables requeridas:
   ```env
   MYSQL_ROOT_PASSWORD=tu_password
   MYSQL_APP_USER=menta_app
   MYSQL_APP_PASSWORD=tu_password
   ```
3. Reiniciar la infraestructura: `docker-compose down -v && ./scripts/start-infra.sh`

---

### Error: "Port 3306 already in use"

Ya tenés MySQL corriendo en tu máquina.

**Solución**:
1. Detener tu MySQL local: `brew services stop mysql` (macOS) o equivalente
2. O cambiar el puerto en `docker-compose.yml`:
   ```yaml
   ports:
     - "3307:3306"  # Cambiar a 3307
   ```
3. Actualizar la configuración de Spring Boot en `api/app/src/main/resources/application.yml` para usar el nuevo puerto.

---

### Error: "Port 8080 already in use"

Otro servicio está usando el puerto 8080.

**Solución**: Cambiar el puerto del BFF editando `bff/src/main/resources/application.yml`:
```yaml
server:
  port: 8082  # Cambiar a 8082 o cualquier puerto libre
```

---

### MySQL tarda mucho en estar listo

Es normal en el primer arranque (creación de volúmenes). Esperá hasta ver:

```
✅ MySQL is ready!
```

---

### Warning: "Using a password on the command line interface can be insecure"

Este warning de MySQL es normal y no afecta el funcionamiento. Aparece cuando el script verifica que MySQL esté listo.

**Solución**: Ignorar, es solo un warning informativo.

---

### Ver logs de la infraestructura

**Logs de Docker (infraestructura):**
```bash
docker-compose logs -f           # Todos los logs
docker-compose logs -f mysql     # Solo MySQL
docker-compose logs -f redis     # Solo Redis
docker-compose logs -f loki      # Solo Loki
docker-compose logs -f grafana   # Solo Grafana
```

**Logs de la Aplicación (API):**

La mejor forma de ver los logs de la API es mediante **Grafana**:

1. Abrí http://localhost:3000
2. Andá a **Explore** → **Loki**
3. Query: `{service_name="menta-dance-api"}`

Ver sección [Visualizar Logs en Grafana](#7-visualizar-logs-en-grafana) para más detalles.

---

### Reiniciar todo desde cero

```bash
# Detener y eliminar contenedores + volúmenes
docker-compose down -v

# Levantar de nuevo
./scripts/start-infra.sh
```

---

## Próximos Pasos

- **Ejecutar tests automatizados con Bruno CLI**:
  ```bash
  cd bruno/BFF-Session-Custody && npx @usebruno/cli run --env Local .
  ```
- **Visualizar logs en Grafana**: http://localhost:3000 (ver sección "Visualizar Logs en Grafana")
- **Probar API con Bruno GUI**: Abrí la colección en `bruno/BFF-Session-Custody`
- **Ejecutar tests unitarios**: `./gradlew test`
- **Ver cobertura**: `./gradlew jacocoTestReport`
- Revisar la [Documentación de la API](03-AUTH-API.md)
- Ver las [Historias de Usuario](user-stories/)
- Explorar la [Arquitectura del Proyecto](02-ARCHITECTURE.md)

---

## Credenciales de Prueba

> **Nota**: Estas credenciales estarán disponibles una vez que se carguen los datos de seed.

| Rol | Email | Contraseña |
|-----|-------|------------|
| Admin | `admin@menta.dance` | `Admin123!` |
| Profesor | `profesor@menta.dance` | `Prof123!` |
| Alumno | `alumno@menta.dance` | `Alumno123!` |
| Recepcionista | `recepcion@menta.dance` | `Recep123!` |

---

## Recursos Útiles

- [README principal](../README.md)
- [Plan de Desarrollo](13-DEVELOPMENT-PLAN.md)
- [ADRs (Decisiones de Arquitectura)](adr/README.md)
- [Colección Bruno (API requests)](../bruno/)

---

¿Necesitás ayuda? Abrí un issue en el [repositorio](https://github.com/urrestarazu-org/menta-dance/issues).

## Ver también

- [24-LOCAL-DEV-SETUP-REQUIREMENTS.md](24-LOCAL-DEV-SETUP-REQUIREMENTS.md) — contrato arquitectónico del setup local.
