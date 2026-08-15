# Menta Dance Bruno Collection

Colección unificada para testing de API y BFF. Abrí `bruno/` como la colección raíz y seleccioná un environment antes de ejecutar requests.

## Estructura

```
bruno/
├── environments/           # Configuración compartida
│   └── local.bru          # Desarrollo local
│
├── API - Direct/          # Tests directos contra la API
│   ├── auth/              # Autenticación
│   ├── health.bru         # Health checks
│   ├── physical/          # Clases presenciales (placeholders)
│   └── virtual/           # Cursos virtuales (placeholders)
│
└── BFF - Session/         # Tests de integración BFF
    ├── 1. Login.bru
    ├── 2. Dashboard (Authenticated).bru
    ├── 3. Logout.bru
    ├── 4. Dashboard (After Logout).bru
    ├── README.md          # Guía específica de BFF Session
    ├── SETUP.md           # Setup local
    └── TESTING-CURL.md    # Testing con curl
```

## Environments

### local
- **API**: `http://localhost:8081`
- **BFF**: `http://localhost:8080`
- **Credenciales de prueba**:
  ```
  email: student@example.com
  password: password123
  ```

> **Nota**: Las variables `authToken` y `refreshToken` son runtime variables. NUNCA las guardes en archivos versionados.

## Cómo usar

### Opción 1: Bruno Desktop (GUI)

1. Abrir Bruno
2. **Open Collection** → seleccionar carpeta `bruno/`
3. Seleccionar environment **local** en el dropdown superior
4. Navegar a un folder:
   - **API - Direct** → Para testear la API directamente
   - **BFF - Session** → Para testear el flujo de sesiones del BFF

### Opción 2: Bruno CLI

**Ejecutar toda la colección:**
```bash
cd bruno && npx @usebruno/cli run --env local .
```

**Ejecutar solo BFF Session:**
```bash
cd bruno && npx @usebruno/cli run --env local --folder "BFF - Session" .
```

**Ejecutar solo API Direct:**
```bash
cd bruno && npx @usebruno/cli run --env local --folder "API - Direct" .
```

## Folder: API - Direct

Tests directos contra la API (`http://localhost:8081`).

### Authentication flow

Ejecutar en este orden:

1. **Register User** - Durante el desarrollo de `auth-account-activation` el endpoint está deshabilitado y debe retornar `503`. No crea un usuario fixture.
2. **Auth Login** - Requiere una cuenta local ya existente y activa; retorna `access_token` en JSON y `refresh_token` en header `X-Refresh-Token`. El script post-response guarda ambos como runtime variables.
3. **Auth Refresh** - Envía `X-Refresh-Token`, verifica rotación, actualiza runtime variables.
4. **Auth Logout** - Envía bearer token + `X-Refresh-Token`, retorna `204 No Content`, limpia runtime variables.

> **Importante**: El refresh token NUNCA aparece en el body JSON de request o response, solo en el header `X-Refresh-Token`.

### Health check

`health.bru` verifica `GET /actuator/health` retorna `200` y `status: UP`.

### Physical y Virtual (placeholders)

Los módulos physical classes y virtual courses aún no tienen endpoints implementados. Sus `.bru` son placeholders de documentación:
- No tienen assertions de success
- El collection runner los skipea
- Las variables `physicalClassesUrl` y `virtualCoursesUrl` están intencionalmente sin definir

Definí esas URLs solo cuando los controllers estén implementados.

## Folder: BFF - Session

Tests de integración del flujo completo de autenticación con sesiones (`http://localhost:8080`).

Ver documentación detallada en `BFF - Session/README.md`.

### Quick start

1. Configurar `email` y `password` del environment `local` con una cuenta local
   ya existente y activa. El registro público está temporalmente deshabilitado
   mientras se completa `auth-account-activation`.

2. Ejecutar requests EN ORDEN:
   - **1. Login** → 302, cookie SESSION
   - **2. Dashboard (Authenticated)** → 200 OK
   - **3. Logout** → 302, SESSION expirada
   - **4. Dashboard (After Logout)** → 302 a /login

3. O ejecutar con CLI:
   ```bash
   cd bruno && npx @usebruno/cli run --env local --folder "BFF - Session" .
   ```

**Resultado esperado**: las 11 aserciones de BFF Session pasan cuando el
environment usa una cuenta activa válida.

## Troubleshooting

### Variables no se cargan en Bruno GUI

1. Verificá que seleccionaste el environment **local** en el dropdown superior
2. Si sigue sin funcionar, cerrá y volvé a abrir la colección
3. Alternativa: Usá Bruno CLI que siempre funciona

### "Connection refused"

Verificá que los servicios estén corriendo:
```bash
./scripts/dev.sh status
```

Si no están corriendo:
```bash
./scripts/dev.sh start
```

### Testing con curl

Si preferís curl, seguí la guía en `BFF - Session/TESTING-CURL.md`.

## Referencias

- [Guía de Setup Local](../docs/26-LOCAL-DEV-SETUP-HOWTO.md)
- [Documentación de la API Auth](../docs/03-AUTH-API.md)
