# Definición de SLA (Service Level Agreement)

[← Volver al índice](./README.md)

---

## 1. Alcance

Este documento define los niveles de servicio objetivo para Menta Virtual Academy
en su arquitectura de **monolito modular** ([ADR-0020](./adr/0020-modular-monolith.md)).

### 1.1 Componentes Cubiertos

| Componente | Descripción | Criticidad |
|------------|-------------|------------|
| **API** (`:api:app`) | Monolito con módulos Auth, Billing, Virtual, Physical | Alta |
| **BFF** (`:bff`) | Interfaz web (Thymeleaf) | Alta |
| **MySQL** | Base de datos única compartida | Alta |
| **Redis** | Blacklist JWT, rate limiting, locks | Alta |

> Los módulos Auth, Billing, Virtual y Physical residen en el mismo proceso
> Java. El SLA aplica al JAR unificado, no a servicios separados.

---

## 2. Métricas de Disponibilidad

### 2.1 Objetivos de Uptime

| Nivel | Uptime | Downtime Permitido/Mes | Aplicación |
|-------|--------|------------------------|------------|
| **MVP** | 99.0% | ~7.3 horas | Fase inicial |
| **Producción** | 99.5% | ~3.6 horas | Post-estabilización |
| **Futuro** | 99.9% | ~43 minutos | Con HA |

### 2.2 Ventanas de Mantenimiento

| Tipo | Frecuencia | Duración Máx | Notificación |
|------|------------|--------------|--------------|
| Planificado | Semanal (domingo 3-5 AM) | 2 horas | 48h previas |
| Emergencia | Según necesidad | 1 hora | Inmediata |
| Actualización mayor | Mensual | 4 horas | 1 semana previa |

**Nota:** El mantenimiento planificado NO cuenta contra el uptime.

---

## 3. Métricas de Rendimiento

### 3.1 Tiempos de Respuesta

| Endpoint/Operación | p50 | p95 | p99 | Máximo |
|--------------------|-----|-----|-----|--------|
| **Login** | 200ms | 500ms | 1s | 2s |
| **Listado de cursos** | 300ms | 800ms | 1.5s | 3s |
| **Reproducción de video** | 500ms | 1s | 2s | 5s |
| **Checkout/Pago** | 1s | 2s | 3s | 5s |
| **Check-in QR** | 200ms | 500ms | 1s | 2s |
| **Health check** | 50ms | 100ms | 200ms | 500ms |

### 3.2 Throughput

| Métrica | Objetivo MVP | Objetivo Producción |
|---------|--------------|---------------------|
| Requests/segundo | 2 RPS | 10 RPS |
| Usuarios concurrentes | 50 | 200 |
| Transacciones/día | 100 | 500 |

---

## 4. Objetivos de Recuperación

### 4.1 RTO (Recovery Time Objective)

Tiempo máximo para restaurar el servicio después de una interrupción.

| Escenario | RTO |
|-----------|-----|
| Fallo de aplicación (código) | 15 minutos |
| Fallo de base de datos | 30 minutos |
| Fallo de servidor completo | 2 horas |
| Fallo de proveedor (Donweb) | 4 horas* |

*Dependiente del proveedor, fuera de control directo.

### 4.2 RPO (Recovery Point Objective)

Pérdida de datos máxima aceptable.

| Tipo de Dato | RPO | Estrategia |
|--------------|-----|------------|
| Transacciones de pago | 0 | Logs + webhook idempotente |
| Progreso de usuario | 1 hora | Backup horario |
| Datos de sesión | Sesión actual | Aceptable perder |
| Configuración | 24 horas | Backup diario |

---

## 5. Indicadores de Servicio (SLIs)

### 5.1 Métricas Monitoreadas

| SLI | Cómo se mide | Herramienta |
|-----|--------------|-------------|
| Disponibilidad | % requests exitosos (2xx, 3xx) | UptimeRobot + Actuator |
| Latencia | Tiempo de respuesta por endpoint | Actuator /metrics |
| Error rate | % de respuestas 5xx | Logs + métricas |
| Saturación | Uso de CPU, memoria, conexiones | Actuator /metrics |

### 5.2 Cálculo de Disponibilidad

```
Disponibilidad = (Tiempo Total - Tiempo de Inactividad No Planificado) / Tiempo Total × 100

Ejemplo (1 mes = 720 horas):
- Downtime no planificado: 3 horas
- Disponibilidad = (720 - 3) / 720 × 100 = 99.58%
```

---

## 6. Escalamiento y Soporte

### 6.1 Niveles de Severidad

| Severidad | Descripción | Ejemplo |
|-----------|-------------|---------|
| **P1 - Crítico** | Sistema completamente caído | Login no funciona, pagos fallan |
| **P2 - Alto** | Funcionalidad mayor afectada | Videos no cargan, checkout lento |
| **P3 - Medio** | Funcionalidad menor afectada | Página de perfil con errores |
| **P4 - Bajo** | Cosmético o mejora | Typo en interfaz, lentitud menor |

### 6.2 Tiempos de Respuesta

| Severidad | Tiempo de Reconocimiento | Tiempo de Resolución |
|-----------|--------------------------|----------------------|
| P1 | 15 minutos | 2 horas |
| P2 | 1 hora | 8 horas |
| P3 | 4 horas | 48 horas |
| P4 | 24 horas | Próximo release |

### 6.3 Horario de Soporte

| Nivel | Cobertura |
|-------|-----------|
| **MVP** | Horario laboral (Lun-Vie 9-18) |
| **Producción** | Horario extendido (Lun-Sáb 8-22) |
| **Crítico (P1)** | Respuesta 24/7 vía alertas |

---

## 7. Exclusiones

Los siguientes eventos NO cuentan contra el SLA:

- Mantenimiento planificado (notificado con anticipación)
- Fallas del proveedor de hosting (Donweb)
- Fallas de servicios externos (Mercado Pago, Bunny.net)
- Ataques DDoS
- Fuerza mayor
- Acciones del cliente que causen indisponibilidad

---

## 8. Penalidades y Créditos

### 8.1 Política MVP

Durante la fase MVP (primeros 6 meses post-lanzamiento):

- Sin penalidades formales
- Compromiso de transparencia en incidentes
- Post-mortem compartido para incidentes P1/P2

### 8.2 Política Producción (Futura)

| Uptime Mensual | Crédito |
|----------------|---------|
| < 99.5% | - |
| < 99.0% | 10% próximo mes |
| < 98.0% | 25% próximo mes |
| < 95.0% | 50% próximo mes |

---

## 9. Monitoreo y Reportes

### 9.1 Herramientas de Monitoreo

| Métrica | Herramienta | Frecuencia |
|---------|-------------|------------|
| Uptime | UptimeRobot | Cada 5 min |
| Health | Actuator /health | Cada 1 min |
| Métricas | Actuator /metrics | Cada 1 min |
| Logs | Docker logs | Tiempo real |

### 9.2 Reportes

| Reporte | Frecuencia | Contenido |
|---------|------------|-----------|
| Uptime semanal | Lunes | % disponibilidad, incidentes |
| Rendimiento mensual | 1ro del mes | Latencias, errores, tendencias |
| Post-mortem | Por incidente P1/P2 | Causa raíz, acciones, lecciones |

---

## 10. Proceso de Incidentes

### 10.1 Flujo

```
Detección → Clasificación → Comunicación → Resolución → Post-mortem
     │            │              │              │             │
  Alerta      Asignar P1-4    Notificar     Fix/Rollback  Documentar
  automática                  stakeholders
```

### 10.2 Plantilla de Incidente

```markdown
# Incidente: [Título breve]

**Severidad:** P1/P2/P3/P4
**Inicio:** YYYY-MM-DD HH:MM
**Fin:** YYYY-MM-DD HH:MM
**Duración:** X horas/minutos

## Impacto
- Usuarios afectados: X
- Funcionalidad afectada: [Descripción]

## Timeline
- HH:MM - Detección
- HH:MM - Acción tomada
- HH:MM - Resolución

## Causa Raíz
[Descripción técnica]

## Acciones Correctivas
- [ ] Acción 1
- [ ] Acción 2

## Lecciones Aprendidas
- [Lección 1]
```

---

## 11. Revisión del SLA

| Aspecto | Frecuencia |
|---------|------------|
| Métricas actuales vs objetivos | Mensual |
| Ajuste de objetivos | Trimestral |
| Revisión completa del SLA | Anual |

---

## 12. Aceptación

Este SLA entra en vigencia con el lanzamiento a producción.

| Rol | Responsabilidad |
|-----|-----------------|
| Tech Lead | Cumplimiento técnico |
| Product Owner | Comunicación con usuarios |
| DevOps | Monitoreo y alertas |

---

*Documento vivo. Ajustar según evolucionen las capacidades y requisitos del negocio.*
