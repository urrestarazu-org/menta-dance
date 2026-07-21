# Lógica de Precios — Clases Presenciales

[← Volver al índice](./README.md) | [← Physical API](./05-PHYSICAL-API.md)

> [!CAUTION]
> **HISTÓRICO — REEMPLAZADO — NO IMPLEMENTAR.** Este documento conserva la
> formulación inicial para trazabilidad. La única autoridad funcional vigente es
> [Pagos de Clases Presenciales](./28-PHYSICAL-CLASS-PAYMENTS.md).

---

## Resumen

Este documento describía las reglas iniciales de cálculo de precios presenciales.

**Regla fundamental:**
> El pago mensual **siempre** debe resultar más económico que pagar cada clase de forma individual.

---

## Modalidades de Pago

| Modalidad | Descripción | Cuándo aplica |
|-----------|-------------|---------------|
| **Plan Mensual** | Precio fijo por mes, independiente de la cantidad de clases | Alumnos regulares que asisten todas las semanas |
| **Clase Individual** | Pago por clase asistida | Alumnos ocasionales o que prueban una clase |

---

## Cálculo de Precios

### Variables

| Variable | Descripción | Ejemplo |
|----------|-------------|---------|
| `P_mensual` | Precio del plan mensual | $5.000 |
| `N_clases` | Cantidad de clases en el mes | 4 o 5 (según calendario) |
| `P_clase_mensual` | Costo por clase pagando mensual | `P_mensual / N_clases` |
| `P_clase_individual` | Precio por clase individual | Siempre > `P_clase_mensual` |
| `Recargo` | Porcentaje de recargo clase individual | 10% mínimo recomendado |

### Fórmula

```
P_clase_individual = P_clase_mensual × (1 + Recargo)

Donde:
  P_clase_mensual = P_mensual / N_clases
  Recargo ≥ 0.10 (10%)
```

---

## Ejemplos Prácticos

### Ejemplo 1: Septiembre 2026 (5 clases)

**Contexto:** Clases los días martes, comenzando el 01/09/2026.

| Martes del mes |
|----------------|
| 01/09/2026 |
| 08/09/2026 |
| 15/09/2026 |
| 22/09/2026 |
| 29/09/2026 |
| **Total: 5 clases** |

**Cálculo:**

| Concepto | Cálculo | Resultado |
|----------|---------|-----------|
| Plan mensual | — | $5.000 |
| Clases en el mes | — | 5 |
| Costo por clase (mensual) | $5.000 ÷ 5 | **$1.000** |
| Clase individual (con 10% recargo) | $1.000 × 1.10 | **$1.100** |

**Verificación de la regla:**
- ✅ Pagar mensual: $5.000 por 5 clases = $1.000/clase
- ✅ Pagar individual: $1.100 × 5 = $5.500 por 5 clases
- ✅ **Mensual es más barato** ($500 de ahorro)

---

### Ejemplo 2: Octubre 2026 (4 clases)

**Contexto:** Clases los días martes, comenzando el 06/10/2026.

| Martes del mes |
|----------------|
| 06/10/2026 |
| 13/10/2026 |
| 20/10/2026 |
| 27/10/2026 |
| **Total: 4 clases** |

**Cálculo:**

| Concepto | Cálculo | Resultado |
|----------|---------|-----------|
| Plan mensual | — | $5.000 |
| Clases en el mes | — | 4 |
| Costo por clase (mensual) | $5.000 ÷ 4 | **$1.250** |
| Clase individual (con 10% recargo) | $1.250 × 1.10 | **$1.375** |

**Verificación de la regla:**
- ✅ Pagar mensual: $5.000 por 4 clases = $1.250/clase
- ✅ Pagar individual: $1.375 × 4 = $5.500 por 4 clases
- ✅ **Mensual es más barato** ($500 de ahorro)

---

## Tabla de Referencia Rápida

Para un plan mensual de **$5.000** con recargo del 10%:

| Clases en el mes | Costo/clase (mensual) | Clase individual |
|------------------|----------------------|------------------|
| 3 | $1.667 | $1.834 |
| 4 | $1.250 | $1.375 |
| 5 | $1.000 | $1.100 |

---

## Implementación en el Sistema

### Modelo de Datos

El precio de la clase individual se calcula dinámicamente según el mes, no es un valor fijo en la base de datos.

```java
public class PhysicalClassPricing {

    private static final BigDecimal INDIVIDUAL_CLASS_SURCHARGE = new BigDecimal("0.10"); // 10%

    public BigDecimal calculateIndividualClassPrice(
            BigDecimal monthlyPlanPrice,
            int classesInMonth
    ) {
        BigDecimal costPerClassMonthly = monthlyPlanPrice
                .divide(BigDecimal.valueOf(classesInMonth), 2, RoundingMode.HALF_UP);

        return costPerClassMonthly
                .multiply(BigDecimal.ONE.add(INDIVIDUAL_CLASS_SURCHARGE))
                .setScale(0, RoundingMode.CEILING); // Redondear hacia arriba
    }
}
```

### Endpoint para Consultar Precios

```
GET /api/v1/physical/pricing?courseId={id}&month=2026-09
```

**Response:**

```json
{
  "courseId": 1,
  "courseName": "Salsa Intermedio",
  "month": "2026-09",
  "classesInMonth": 5,
  "classDates": ["2026-09-01", "2026-09-08", "2026-09-15", "2026-09-22", "2026-09-29"],
  "pricing": {
    "monthlyPlan": {
      "price": 5000.00,
      "pricePerClass": 1000.00
    },
    "individualClass": {
      "price": 1100.00,
      "surchargePercent": 10
    }
  },
  "savings": {
    "amount": 500.00,
    "percent": 9.09,
    "message": "Ahorrás $500 pagando el plan mensual"
  }
}
```

---

## Reglas de Negocio

### RN-PHYS-PRICE-001: Precio individual siempre mayor

> El precio de una clase individual DEBE ser siempre mayor al costo por clase del plan mensual.

**Validación:** Al configurar un plan, el sistema debe verificar que el recargo sea ≥ 10%.

### RN-PHYS-PRICE-002: Cálculo dinámico por mes

> El precio de la clase individual se calcula según la cantidad de clases del mes consultado.

**Implicación:** Un mes con 4 martes tiene clase individual más cara que un mes con 5 martes.

### RN-PHYS-PRICE-003: Transparencia de precios

> El sistema debe mostrar claramente el ahorro al elegir el plan mensual.

**UX:** Mostrar comparativa de precios y ahorro en la pantalla de inscripción.

---

## Casos Especiales

### Feriados

Si una clase cae en feriado y no se dicta:

- **Plan mensual:** No se descuenta (el alumno paga el precio fijo).
- **Clase individual:** No aplica (no hay clase que pagar).

### Clases de recuperación

Si se ofrece una clase de recuperación:

- **Plan mensual:** Incluida sin costo adicional.
- **Clase individual:** Debe pagarse como cualquier otra clase.

### Cambio de precio mid-month

Si el precio del plan cambia durante el mes:

- Los alumnos con plan activo mantienen el precio anterior hasta fin de mes.
- Las clases individuales usan el precio nuevo inmediatamente.

---

## Auditoría de Precios

Todos los cambios de precios se registran para auditoría:

```sql
CREATE TABLE physical_plan_price_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    plan_id BIGINT NOT NULL,
    old_price DECIMAL(10,2),
    new_price DECIMAL(10,2),
    changed_by BIGINT NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason TEXT,

    FOREIGN KEY (plan_id) REFERENCES physical_plans(id),
    INDEX idx_audit_plan (plan_id),
    INDEX idx_audit_date (changed_at)
);
```

---

[Siguiente: ADRs →](./10-ADRS.md)
