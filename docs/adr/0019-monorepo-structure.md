# ADR-0019: Estructura Monorepo

**Fecha:** 2026-07-19
**Estado:** Aceptado

## Contexto

Necesitamos definir cómo organizar el código del proyecto Menta Dance, que incluye:
- API REST (Spring Boot)
- BFF Web (Spring Boot + Thymeleaf)
- Panel de administración como páginas web del BFF
- App Android (Kotlin)

## Decisión

Adoptamos **monorepo**: todos los componentes viven en un único repositorio Git.

```
menta-dance/
├── api/          # Backend API REST
├── bff/          # Frontend Web
└── android/      # App móvil
```

## Alternativas Consideradas

### Multi-repo (Rechazado)
- Cada componente en repositorio separado
- **Contra:** Mayor complejidad para coordinar cambios
- **Contra:** Versionado de dependencias compartidas
- **Contra:** CI/CD más complejo

### Monorepo (Elegido)
- Todo en un repositorio
- **Pro:** Cambios atómicos entre API y clientes
- **Pro:** Refactoring más fácil
- **Pro:** Un solo CI/CD pipeline
- **Pro:** Visibilidad total del proyecto

## Consecuencias

### Positivas
- Cambios que afectan API + BFF + Android se hacen en un solo PR
- Más fácil mantener consistencia entre componentes
- Nuevos desarrolladores ven todo el proyecto

### Negativas
- Repositorio más grande
- Necesita configurar Gradle multi-proyecto

## Referencias

- [02-ARCHITECTURE.md](../02-ARCHITECTURE.md)
