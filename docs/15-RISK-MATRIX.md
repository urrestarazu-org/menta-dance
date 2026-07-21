# Matriz de Riesgos y Plan de Mitigación

[← Volver al índice](./README.md) | [← Estrategia de Pruebas](./14-TEST-STRATEGY.md) | [Pipeline CI/CD →](./16-CICD-PIPELINE.md)

---

## 1. Metodología

### 1.1 Escala de Probabilidad

| Nivel | Valor | Descripción |
|-------|-------|-------------|
| **Muy Baja** | 1 | < 10% de ocurrencia |
| **Baja** | 2 | 10-30% de ocurrencia |
| **Media** | 3 | 30-50% de ocurrencia |
| **Alta** | 4 | 50-70% de ocurrencia |
| **Muy Alta** | 5 | > 70% de ocurrencia |

### 1.2 Escala de Impacto

| Nivel | Valor | Descripción |
|-------|-------|-------------|
| **Insignificante** | 1 | Retraso < 1 día, sin impacto en funcionalidad |
| **Menor** | 2 | Retraso 1-3 días, funcionalidad degradada |
| **Moderado** | 3 | Retraso 1-2 semanas, feature incompleto |
| **Mayor** | 4 | Retraso 2-4 semanas, fase comprometida |
| **Catastrófico** | 5 | Retraso > 1 mes, proyecto en riesgo |

### 1.3 Matriz de Severidad

| Prob \ Impacto | 1 | 2 | 3 | 4 | 5 |
|----------------|---|---|---|---|---|
| **5** | 5 | 10 | 15 | 🔴 20 | 🔴 25 |
| **4** | 4 | 8 | 12 | 🟠 16 | 🔴 20 |
| **3** | 3 | 6 | 🟡 9 | 🟠 12 | 🟠 15 |
| **2** | 2 | 4 | 6 | 8 | 10 |
| **1** | 1 | 2 | 3 | 4 | 5 |

- 🔴 **Crítico (16-25):** Acción inmediata requerida
- 🟠 **Alto (12-15):** Plan de mitigación prioritario
- 🟡 **Medio (6-11):** Monitorear y preparar contingencia
- ⬜ **Bajo (1-5):** Aceptar o monitorear

---

## 2. Riesgos Identificados

### 2.1 Riesgos Técnicos

| ID | Riesgo | Prob | Imp | Sev | Fase |
|----|--------|------|-----|-----|------|
| RT-001 | Fallo en integración entre servicios (HTTP sync) | 3 | 4 | 🟠 12 | 3-5 |
| RT-002 | Problemas de rendimiento en BFF (agregación lenta) | 3 | 3 | 🟡 9 | 4 |
| RT-003 | Incompatibilidad JWT entre servicios | 2 | 4 | 8 | 1-3 |
| RT-004 | Pérdida de datos en migración Big Bang | 2 | 5 | 10 | 7 |
| RT-005 | Fallos de Mercado Pago webhooks en producción | 3 | 4 | 🟠 12 | 2 |
| RT-006 | Timeout en comunicación inter-servicios | 4 | 3 | 🟠 12 | 3-5 |
| RT-007 | Inconsistencia de cache entre servicios | 3 | 3 | 🟡 9 | 4-5 |
| RT-008 | Vulnerabilidades de seguridad en dependencias | 3 | 4 | 🟠 12 | Todas |

### 2.2 Riesgos de Infraestructura

| ID | Riesgo | Prob | Imp | Sev | Fase |
|----|--------|------|-----|-----|------|
| RI-001 | VPS insuficiente para carga real (8GB RAM) | 2 | 4 | 8 | 7 |
| RI-002 | Downtime de Donweb durante go-live | 2 | 5 | 10 | 7 |
| RI-003 | Certificado SSL no renovado automáticamente | 2 | 3 | 6 | 7 |
| RI-004 | Falta de espacio en disco (logs, BD) | 3 | 3 | 🟡 9 | 7+ |
| RI-005 | MySQL single point of failure | 3 | 5 | 🟠 15 | Todas |

### 2.3 Riesgos de Proyecto

| ID | Riesgo | Prob | Imp | Sev | Fase |
|----|--------|------|-----|-----|------|
| RP-001 | Estimaciones optimistas (scope creep) | 4 | 3 | 🟠 12 | Todas |
| RP-002 | Dependencia de un solo desarrollador | 4 | 4 | 🔴 16 | Todas |
| RP-003 | Cambios de requisitos a mitad del desarrollo | 3 | 4 | 🟠 12 | 1-5 |
| RP-004 | Falta de testing adecuado pre-release | 3 | 5 | 🟠 15 | 7 |
| RP-005 | Documentación desactualizada | 4 | 2 | 8 | Todas |

### 2.4 Riesgos de Negocio

| ID | Riesgo | Prob | Imp | Sev | Fase |
|----|--------|------|-----|-----|------|
| RN-001 | Usuarios no pueden pagar (Mercado Pago falla) | 2 | 5 | 10 | 7+ |
| RN-002 | Pérdida de suscripciones activas en migración | 1 | 5 | 5 | 7 |
| RN-003 | UX degradada respecto al monolito | 3 | 3 | 🟡 9 | 4 |
| RN-004 | Tiempo de respuesta inaceptable para usuarios | 2 | 4 | 8 | 7+ |

---

## 3. Planes de Mitigación

### 3.1 Riesgos Críticos (🔴)

#### RP-002: Dependencia de un solo desarrollador

**Severidad:** 16 (Crítico)

**Mitigación:**

1. Documentar exhaustivamente decisiones (ADRs ✓)
2. Code reviews obligatorios (aunque sea self-review documentado)
3. Pair programming virtual con grabaciones
4. Mantener `CLAUDE.md` actualizado como knowledge base
5. Considerar onboarding de segundo dev en Fase 3+

**Contingencia:**

- Si el dev principal no está disponible > 2 semanas: pausar desarrollo
- Priorizar documentación sobre velocidad

**Responsable:** Tech Lead / Owner

---

### 3.2 Riesgos Altos (🟠)

#### RT-001: Fallo en integración entre servicios

**Severidad:** 12

**Mitigación:**

1. Tests de contrato entre servicios (Spring Cloud Contract)
2. Timeouts configurados con valores sensatos (5s default)
3. Retry con backoff exponencial (Resilience4j)
4. Logging correlacionado (X-Request-ID)
5. Health checks entre servicios

**Contingencia:**

- Circuit breaker para degradar gracefully
- Cache de respuestas para operaciones de lectura

---

#### RT-005: Fallos de Mercado Pago webhooks

**Severidad:** 12

**Mitigación:**

1. Idempotencia en procesamiento de webhooks
2. Cola de reintentos para webhooks fallidos
3. Endpoint de verificación manual para admin
4. Logs detallados de cada webhook recibido
5. Alertas cuando webhook falla 3+ veces

**Contingencia:**

- Verificación manual de pagos por admin
- Script de reconciliación con API de MP

---

#### RT-006: Timeout en comunicación inter-servicios

**Severidad:** 12

**Mitigación:**

1. Virtual Threads (Java 21) para I/O no bloqueante
2. Timeouts escalonados: Auth(2s) < Billing(3s) < Virtual(5s)
3. Cache en BFF para datos no críticos
4. Llamadas paralelas donde sea posible

**Contingencia:**

- Respuestas parciales (mostrar lo que se pudo obtener)
- Mensaje de "intente nuevamente" user-friendly

---

#### RT-008: Vulnerabilidades en dependencias

**Severidad:** 12

**Mitigación:**

1. Escaneo Trivy en cada build
2. Dependabot activado en GitHub
3. Actualización mensual de dependencias
4. No deployar con vulnerabilidades HIGH/CRITICAL

**Contingencia:**

- Parche de emergencia si se descubre en producción
- Rollback si el parche no es viable

---

#### RI-005: MySQL single point of failure

**Severidad:** 15

**Mitigación:**

1. Backups automáticos diarios (cron + mysqldump)
2. Backup pre-deploy obligatorio
3. Volumen persistente Docker separado
4. Monitoreo de espacio y conexiones

**Contingencia:**

- Restaurar último backup (RTO < 1 hora)
- Scripts de restore documentados y probados

---

#### RP-001: Estimaciones optimistas

**Severidad:** 12

**Mitigación:**

1. Buffer de 30% en cada fase
2. Revisión de scope al inicio de cada fase
3. MVP estricto: solo lo necesario
4. Cortar features antes que comprometer calidad

**Contingencia:**

- Mover features a post-MVP
- Comunicar retrasos temprano

---

#### RP-004: Falta de testing pre-release

**Severidad:** 15

**Mitigación:**

1. Cobertura mínima 80% (enforced por CI)
2. Checklist de testing pre-release
3. Staging environment obligatorio
4. Smoke tests automatizados

**Contingencia:**

- No hacer release sin todos los checks
- Extender fase de QA si es necesario

---

### 3.3 Riesgos Medios (🟡)

| ID | Mitigación Resumida |
|----|---------------------|
| RT-002 | Profiling, cache, lazy loading, paginación |
| RT-007 | TTL cortos, invalidación explícita, cache-aside pattern |
| RI-004 | Rotación de logs, alertas de disco > 80% |
| RN-003 | User testing en staging, feedback temprano |

---

## 4. Matriz de Riesgos Visual

```
IMPACTO
    5 │                        RI-005  RT-004
      │              RT-001   RP-004  RI-002  RN-001
    4 │     RP-005   RT-005   RP-002
      │              RT-006   RP-003
    3 │              RT-002   RP-001
      │     RI-003   RT-007   RN-003
    2 │              RI-004
      │
    1 │
      └──────────────────────────────────────────
           1         2         3         4         5
                         PROBABILIDAD
```

---

## 5. Monitoreo de Riesgos

### 5.1 Indicadores de Alerta Temprana

| Riesgo | Indicador | Umbral de Alerta |
|--------|-----------|------------------|
| RT-006 | Latencia p95 inter-servicios | > 2 segundos |
| RI-004 | Uso de disco | > 80% |
| RI-005 | Backup exitoso | Falla 2 días seguidos |
| RP-001 | Tareas completadas vs planificadas | < 70% por sprint |
| RT-008 | Vulnerabilidades detectadas | Cualquier HIGH/CRITICAL |

### 5.2 Frecuencia de Revisión

| Severidad | Frecuencia de Revisión |
|-----------|------------------------|
| Crítico (🔴) | Semanal |
| Alto (🟠) | Quincenal |
| Medio (🟡) | Mensual |
| Bajo | Por fase |

---

## 6. Registro de Riesgos Materializados

| Fecha | ID | Descripción | Acción Tomada | Resultado |
|-------|-----|-------------|---------------|-----------|
| - | - | *Sin eventos registrados* | - | - |

---

## 7. Aceptación de Riesgos

Los siguientes riesgos se aceptan conscientemente para el MVP:

| ID | Riesgo | Justificación |
|----|--------|---------------|
| RI-005 | MySQL SPOF | Costo de HA no justificado para 200 usuarios. Backups mitigan. |
| RT-004 | Pérdida de datos | No hay datos de producción actuales. Tabula rasa. |
| RP-002 | Single dev | Realidad del proyecto. Documentación compensa. |

---

*Actualizar este documento cuando se identifiquen nuevos riesgos o cambien las probabilidades/impactos*
