# ADR-0021: Clean Architecture Obligatoria

**Fecha:** 2026-07-19
**Estado:** Aceptado

## Contexto

Necesitamos definir cómo estructurar el código dentro de cada módulo del proyecto. El objetivo es:
- Código testeable
- Independencia de frameworks
- Preparado para cambios de infraestructura

## Decisión

Adoptamos **Clean Architecture** como estándar **obligatorio** en todos los módulos (API, BFF, Android).

### Estructura de cada módulo

```
module/
└── src/main/java/com/menta/{module}/
    ├── domain/           # Entidades, Value Objects, Domain Services
    ├── application/      # Use Cases, Ports (interfaces), DTOs
    └── infrastructure/   # Controllers, JPA, External services
```

### Regla de dependencias

```
domain ← application ← infrastructure
```

- `domain` NO importa nada de `application` ni `infrastructure`
- `application` NO importa nada de `infrastructure`
- Solo `infrastructure` conoce frameworks (Spring, JPA, Retrofit)

### Enforcement

Usamos **ArchUnit** para validar las reglas en CI:

```java
@ArchTest
static final ArchRule domain_should_not_depend_on_infrastructure =
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAPackage("..infrastructure..");
```

Además de las capas, ArchUnit valida las fronteras del monolito modular: un
módulo no puede importar infraestructura ni implementaciones de otro, y los
controllers no acceden directamente a repositorios. La colaboración interna se
realiza mediante puertos Java públicos.

## Alternativas Consideradas

### Capas tradicionales (Controller/Service/Repository) — Rechazado
- Más simple de entender
- **Contra:** Servicios acoplados a DTOs de controller
- **Contra:** Difícil testear sin Spring
- **Contra:** Dominio contaminado con JPA

### Hexagonal Architecture — Considerado
- Muy similar a Clean Architecture
- **Neutral:** Terminología diferente (ports/adapters)
- Elegimos Clean por convención del equipo

### Clean Architecture — Elegido
- **Pro:** Dominio puro, sin frameworks
- **Pro:** Tests unitarios sin contexto Spring
- **Pro:** Fácil cambiar infraestructura
- **Pro:** ArchUnit valida automáticamente

## Consecuencias

### Positivas
- Código de dominio 100% testeable sin mocks de Spring
- Cambiar de JPA a otro ORM no afecta dominio
- Casos de uso explícitos (no servicios genéricos)
- El compilador + ArchUnit previenen violaciones

### Negativas
- Más archivos y carpetas
- Mapeo entre capas (Entity ↔ Domain ↔ DTO)
- Curva de aprendizaje inicial

### Mitigación
- Usar MapStruct para mapeos
- Plantillas de código para nuevas features
- Documentación clara de convenciones

## Aplicación por Componente

| Componente | Estructura |
|------------|------------|
| API (módulos) | domain / application / infrastructure |
| BFF | domain / application / infrastructure |
| Android | domain / data / presentation (MVVM) |

## Referencias

- [02-ARCHITECTURE.md](../02-ARCHITECTURE.md)
- [27-CLEAN-ARCHITECTURE-GUIDE.md](../27-CLEAN-ARCHITECTURE-GUIDE.md)
- [Clean Architecture - Robert C. Martin](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
