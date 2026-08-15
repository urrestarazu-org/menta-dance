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
- **Git**
- **Android Studio** (solo si vas a trabajar en la app móvil)

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

1. Abrir **Android Studio**
2. Seleccionar **Open** y elegir la carpeta `android/` del proyecto
3. Esperar a que Gradle sincronice las dependencias
4. Configurar un emulador o conectar un dispositivo físico
5. Hacer clic en el botón **Run** (triángulo verde)

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
