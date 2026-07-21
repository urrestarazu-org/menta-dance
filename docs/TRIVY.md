# Trivy - Guía de Escaneo de Vulnerabilidades

Trivy es un escáner de seguridad rápido, simple y completo para contenedores, sistemas de archivos, repositorios Git y más.

## 🚀 Inicio Rápido

### Escaneo Básico

```bash
# Escanear todo el proyecto (dependencias Java)
trivy fs .

# Solo vulnerabilidades HIGH y CRITICAL
trivy fs --severity HIGH,CRITICAL .

# Usar configuración del archivo trivy.yaml
trivy fs --config trivy.yaml .
```

## 📊 Comandos Principales

### 1. Escaneo de Dependencias Java

```bash
# Escaneo completo
trivy fs .

# Solo dependencias Java (ignorar otros archivos)
trivy fs --scanners vuln .

# Mostrar solo paquetes vulnerables
trivy fs --severity HIGH,CRITICAL --scanners vuln .

# Con detalles completos
trivy fs --severity HIGH,CRITICAL --scanners vuln --format json .
```

### 2. Diferentes Formatos de Reporte

```bash
# Tabla en terminal (por defecto)
trivy fs .

# JSON para procesamiento automático
trivy fs --format json --output trivy-report.json .

# HTML para visualización web
trivy fs --format template --template "@contrib/html.tpl" --output trivy-report.html .

# SARIF para GitHub Code Scanning
trivy fs --format sarif --output trivy-report.sarif .

# CycloneDX SBOM (Software Bill of Materials)
trivy fs --format cyclonedx --output trivy-sbom.json .
```

### 3. Filtrar por Severidad

```bash
# Solo CRITICAL
trivy fs --severity CRITICAL .

# HIGH y CRITICAL
trivy fs --severity HIGH,CRITICAL .

# Todas las severidades
trivy fs --severity UNKNOWN,LOW,MEDIUM,HIGH,CRITICAL .
```

### 4. Escaneo de Secrets (Credenciales en Código)

```bash
# Buscar secrets en el código
trivy fs --scanners secret .

# Buscar secrets y vulnerabilidades
trivy fs --scanners vuln,secret .

# Solo secrets, ignorar test/
trivy fs --scanners secret --skip-dirs test .
```

### 5. Ignorar Vulnerabilidades Específicas

```bash
# Usar archivo .trivyignore
trivy fs --ignorefile .trivyignore .

# O especificar CVEs en línea de comandos
trivy fs --skip-policy CVE-2021-12345,CVE-2021-67890 .
```

## 📋 Reportes y Salida

### Guardar Reporte HTML

```bash
# Generar reporte HTML completo
trivy fs --format template \
  --template "@contrib/html.tpl" \
  --output build/reports/trivy/trivy-report.html \
  --severity HIGH,CRITICAL .

# Abrir automáticamente en el navegador (macOS)
trivy fs --format template \
  --template "@contrib/html.tpl" \
  --output build/reports/trivy/trivy-report.html \
  --severity HIGH,CRITICAL . && \
  open build/reports/trivy/trivy-report.html
```

### Guardar Reporte JSON

```bash
# JSON con toda la información
trivy fs --format json \
  --output build/reports/trivy/trivy-report.json \
  --severity HIGH,CRITICAL .

# JSON más legible (pretty print)
trivy fs --format json \
  --severity HIGH,CRITICAL . | jq '.' > build/reports/trivy/trivy-report.json
```

## 🔧 Configuración

### Archivo trivy.yaml

El proyecto incluye `trivy.yaml` con configuración predeterminada:

```yaml
scan:
  severity:
    - HIGH
    - CRITICAL
  security-checks:
    - vuln
    - config
    - secret

report:
  format: table
  ignore-unfixed: false
```

**Uso:**

```bash
trivy fs --config trivy.yaml .
```

### Archivo .trivyignore

Para ignorar vulnerabilidades conocidas o falsos positivos:

```bash
# Editar .trivyignore
echo "CVE-2021-12345  # Razón: No afecta nuestra configuración" >> .trivyignore

# Escanear usando el ignore file
trivy fs --ignorefile .trivyignore .
```

## 🎯 Casos de Uso Comunes

### 1. Verificación Rápida Local

```bash
# Antes de commit
trivy fs --severity CRITICAL --quiet .
```

### 2. Reporte Completo para Auditoría

```bash
# Generar reporte HTML completo
trivy fs --severity LOW,MEDIUM,HIGH,CRITICAL \
  --format template \
  --template "@contrib/html.tpl" \
  --output audit-$(date +%Y-%m-%d).html .
```

### 3. CI/CD - Fallar si hay Vulnerabilidades

```bash
# Fallar si encuentra vulnerabilidades HIGH o CRITICAL
trivy fs --severity HIGH,CRITICAL --exit-code 1 .

# Exit codes:
# 0: Sin vulnerabilidades
# 1: Vulnerabilidades encontradas
```

### 4. Escaneo Solo de build.gradle

```bash
# Escanear solo archivo de dependencias
trivy fs --file-patterns gradle:build.gradle .
```

### 5. Comparar con Scan Anterior

```bash
# Primer scan
trivy fs --format json --output baseline.json .

# Segundo scan después de updates
trivy fs --format json --output current.json .

# Comparar (requiere herramienta externa como jq o diff)
diff <(jq -S . baseline.json) <(jq -S . current.json)
```

## 📊 Interpretar Resultados

### Formato de Tabla

```
build.gradle (gradle)
=====================
Total: 5 (HIGH: 3, CRITICAL: 2)

┌──────────────────────┬────────────────┬──────────┬───────────────────┬─────────────────┬────────────────────┐
│       Library        │ Vulnerability  │ Severity │ Installed Version │  Fixed Version  │       Title        │
├──────────────────────┼────────────────┼──────────┼───────────────────┼─────────────────┼────────────────────┤
│ org.example:library  │ CVE-2021-12345 │ CRITICAL │ 1.0.0             │ 1.0.1           │ SQL Injection      │
└──────────────────────┴────────────────┴──────────┴───────────────────┴─────────────────┴────────────────────┘
```

**Columnas:**

- **Library**: Dependencia afectada (group:artifact)
- **Vulnerability**: CVE identificador
- **Severity**: CRITICAL, HIGH, MEDIUM, LOW, UNKNOWN
- **Installed Version**: Versión actual vulnerable
- **Fixed Version**: Versión que corrige la vulnerabilidad
- **Title**: Descripción breve

### Niveles de Severidad

- **CRITICAL** (9.0-10.0): Acción inmediata requerida
- **HIGH** (7.0-8.9): Actualizar pronto
- **MEDIUM** (4.0-6.9): Planificar actualización
- **LOW** (0.1-3.9): Monitorear
- **UNKNOWN**: Sin calificación CVSS

## 🛠️ Troubleshooting

### Error: Database not found

```bash
# Actualizar base de datos de vulnerabilidades
trivy image --download-db-only

# O limpiar caché y re-descargar
trivy clean --all
trivy image --download-db-only
```

### Escaneo muy lento

```bash
# Reducir scope del escaneo
trivy fs --scanners vuln --skip-dirs node_modules,test .

# Usar caché
trivy fs --cache-dir ~/.cache/trivy .
```

### Falsos Positivos

```bash
# Agregar a .trivyignore con razón documentada
echo "CVE-2023-12345  # Falso positivo: solo afecta Windows, usamos Linux" >> .trivyignore
```

### No encuentra dependencias

```bash
# Asegurarse de que build.gradle existe
ls -la build.gradle

# Ejecutar desde la raíz del proyecto
cd /ruta/al/proyecto
trivy fs .
```

## 🔄 Actualización de Trivy

```bash
# Actualizar Trivy
brew upgrade trivy

# Verificar versión
trivy --version

# Actualizar base de datos de vulnerabilidades
trivy image --download-db-only
```

## 📚 Scripts Útiles

### Script para Reportes Automáticos

Crear `scripts/security-scan.sh`:

```bash
#!/bin/bash
# Script de escaneo de seguridad con Trivy

REPORT_DIR="build/reports/trivy"
DATE=$(date +%Y-%m-%d)

# Crear directorio de reportes
mkdir -p "$REPORT_DIR"

echo "🔍 Escaneando vulnerabilidades..."

# Escaneo básico en terminal
trivy fs --severity HIGH,CRITICAL .

# Generar reporte HTML
echo "📊 Generando reporte HTML..."
trivy fs --format template \
  --template "@contrib/html.tpl" \
  --output "$REPORT_DIR/trivy-report-$DATE.html" \
  --severity HIGH,CRITICAL .

# Generar reporte JSON
echo "💾 Generando reporte JSON..."
trivy fs --format json \
  --output "$REPORT_DIR/trivy-report-$DATE.json" \
  --severity HIGH,CRITICAL .

echo "✅ Reportes generados en: $REPORT_DIR"
echo "   - trivy-report-$DATE.html"
echo "   - trivy-report-$DATE.json"

# Abrir HTML en navegador (macOS)
if [[ "$OSTYPE" == "darwin"* ]]; then
  open "$REPORT_DIR/trivy-report-$DATE.html"
fi
```

Hacer ejecutable:

```bash
chmod +x scripts/security-scan.sh
./scripts/security-scan.sh
```

## 🎓 Mejores Prácticas

1. **Escanear regularmente**: Semanalmente o antes de cada release
2. **Priorizar CRITICAL y HIGH**: Enfocarse en vulnerabilidades de alto impacto
3. **Documentar exclusiones**: Siempre documentar por qué se ignora un CVE
4. **Actualizar dependencias**: Mantener dependencias actualizadas
5. **Integrar en CI/CD**: Automatizar escaneos en pipeline
6. **Revisar reportes**: No solo ejecutar, revisar y actuar
7. **Mantener Trivy actualizado**: `brew upgrade trivy` regularmente

## 📖 Recursos

- [Documentación Oficial](https://aquasecurity.github.io/trivy/)
- [GitHub Repository](https://github.com/aquasecurity/trivy)
- [Templates de Reportes](https://github.com/aquasecurity/trivy/tree/main/contrib)
- [Base de Datos de Vulnerabilidades](https://github.com/aquasecurity/trivy-db)

## 🆚 Trivy vs OWASP Dependency Check

| Característica | Trivy | OWASP DC |
|----------------|-------|----------|
| Velocidad | ⚡⚡⚡ 1-2 min | 🐌 5-60+ min |
| Configuración | ✅ Ninguna | ⚠️ API Key recomendado |
| Formatos | Múltiples | HTML, JSON |
| Mantenimiento | ✅ Activo | ⚠️ Problemas frecuentes |
| Costo | Gratis | Gratis |

**Recomendación**: Trivy para escaneos locales rápidos, OWASP DC solo si es requerido por política corporativa.

---

**¿Listo para empezar?** Ejecuta:

```bash
trivy fs --severity HIGH,CRITICAL .
```
