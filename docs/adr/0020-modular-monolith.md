# ADR-0020: Monolito Modular

**Fecha:** 2026-07-19
**Estado:** Aceptado

## Contexto

Necesitamos definir la arquitectura del backend. El proyecto tiene cuatro dominios de negocio:
- Auth (autenticación, usuarios)
- Virtual (cursos online)
- Physical (clases presenciales)
- Billing (pagos, suscripciones)

Escala objetivo MVP: 100 RPM, 200 usuarios.

## Decisión

Adoptamos **monolito modular** con **Gradle multi-módulo**.

```
api/
├── shared/        # :api:shared — código común
├── auth/          # :api:auth
├── virtual/       # :api:virtual
├── physical/      # :api:physical
├── billing/       # :api:billing
└── app/           # :api:app — ensambla todo en 1 JAR
```

El BFF se construye y despliega como otro JAR. Tanto el portal de alumnos como
el panel de administración son páginas web del BFF. BFF y Android consumen la
API por HTTP; dentro del JAR API, los módulos se comunican exclusivamente por
interfaces Java y puertos.

## Alternativas Consideradas

### Microservicios desde el día 1 (Rechazado)
- Cada dominio como servicio separado
- **Contra:** Overhead operacional alto para el MVP
- **Contra:** Necesita API Gateway, Service Discovery, etc.
- **Contra:** Equipo pequeño (1-2 devs)

### Monolito sin módulos (Rechazado)
- Todo en un solo módulo Gradle
- **Contra:** Dependencias no explícitas
- **Contra:** Difícil extraer servicios después
- **Contra:** Compilación lenta al crecer

### Monolito modular (Elegido)
- Un JAR, módulos Gradle separados
- **Pro:** Dependencias explícitas entre módulos
- **Pro:** El compilador enforce separación
- **Pro:** Preparado para extraer microservicios
- **Pro:** Simple de deployar y operar

## Consecuencias

### Positivas
- Un solo JAR para deployar
- Comunicación entre módulos via interfaces Java (rápido)
- Refactoring seguro con el compilador
- Evolución gradual a microservicios si escala

### Negativas
- Los módulos escalan juntos (no independiente)
- Un fallo puede afectar todo

### Mitigación
- Clean Architecture en cada módulo
- Tests de arquitectura con ArchUnit
- Interfaces entre módulos (no clases concretas)
- ArchUnit prohíbe acceso a infraestructura ajena y transporte de red interno

## Referencias

- [02-ARCHITECTURE.md](../02-ARCHITECTURE.md)
- [ADR-0019: Monorepo](./0019-monorepo-structure.md)
