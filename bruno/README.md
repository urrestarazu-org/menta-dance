# Bruno API Collection - Menta Dance

Colección de endpoints para pruebas locales y de desarrollo de la API de Menta Dance.

## Instalación

1. Instalar Bruno: https://www.usebruno.com/downloads
2. Abrir Bruno y seleccionar "Open Collection"
3. Navegar a `menta-dance/bruno/`

## Estructura

```
bruno/
├── api/
│   ├── health.bru           # Health check
│   ├── auth/
│   │   └── register.bru     # Registro de usuarios
│   ├── virtual/
│   │   └── courses.bru      # Cursos online (ejemplo)
│   └── physical/
│       └── classes.bru      # Clases presenciales (ejemplo)
└── environments/
    ├── local.bru            # localhost:8081
    └── dev.bru              # Ambiente de desarrollo
```

## Uso

### 1. Seleccionar Environment

En Bruno, seleccionar el environment:
- **local**: Para desarrollo local (`localhost:8081`)
- **dev**: Para ambiente de desarrollo remoto

### 2. Ejecutar Requests

Los endpoints están organizados por dominio:
- `api/health.bru`: Verificar estado de la aplicación
- `api/auth/register.bru`: Registrar nuevo usuario

### 3. Autenticación

Para endpoints que requieren autenticación:

1. Ejecutar login (cuando esté implementado)
2. Copiar el token de la respuesta
3. Agregar variable `authToken` en el environment
4. Los requests con `auth: bearer` usarán ese token automáticamente

### Variables de Environment

**local.bru:**
```
baseUrl: http://localhost:8081
apiVersion: v1
authToken: <tu-token-aqui>  # Agregar después del login
```

## Tests

Cada request incluye tests básicos que se ejecutan automáticamente:
- Validación de status code
- Validación de estructura de respuesta

## Notas

- Los endpoints de `virtual/` y `physical/` son **plantillas** para cuando se implementen
- El endpoint `auth/register.bru` ya está funcional y se puede usar
- Usar `health.bru` para verificar que la API y sus dependencias están levantadas
