# ADR-0042: Baseline de tooling de seguridad de código

**Fecha:** 2026-09-08
**Estado:** Aceptado

## Contexto

El pipeline de CI del proyecto ejecuta hoy una cantidad considerable de
análisis estático. Ninguno de esos análisis busca vulnerabilidades.

Estado verificado en fuente al momento de esta decisión:

| Herramienta | Dónde corre | Qué valida realmente |
|---|---|---|
| Checkstyle (`config/checkstyle/google_checks.xml`) | 8 módulos JVM, ambos workflows | Estilo de código. Cero reglas de seguridad. |
| ArchUnit | `auth`, `billing`, `virtual`, `physical` | Regla de dependencia entre capas |
| JaCoCo | `pr-main.yml` | Cobertura por capa |
| ShellCheck | `pr-develop.yml`, `pr-main.yml` | Scripts de `infra/docker/` |
| Hadolint | ambos workflows | Buenas prácticas de escritura del Dockerfile (reglas `DL*`) |
| Redocly | `pr-develop.yml` | Estructura de los contratos OpenAPI |

El riesgo de esta configuración no es la ausencia de herramientas: es que
la abundancia de análisis estático **aparenta** una cobertura de seguridad
que no existe. Checkstyle corre sobre los ocho módulos JVM en los dos
workflows y falla el build; es razonable asumir que algo tan visible cubre
algo más que indentación. No lo hace. Hadolint aprueba un `FROM` impecable
hacia una imagen base con CVEs críticos publicados: la sintaxis es correcta
y el contenido es vulnerable.

Gaps concretos, todos verificados por ausencia en el árbol de fuentes:

1. **SCA (composición de dependencias)**: no existe `.github/dependabot.yml`,
   ni OWASP Dependency-Check, ni Renovate. Los plugins declarados en el
   `build.gradle.kts` raíz son `java`, `jacoco` y `checkstyle`. El stack
   combina Spring Boot 3.5.14 —que arrastra cientos de transitivas vía BOM—,
   Android/AGP y Node para el pipeline de Tailwind del BFF. Es la superficie
   de ataque más grande del proyecto y la única que nadie observa.
2. **Detección de secretos**: no hay gitleaks, trufflehog ni equivalente.
   `.pre-commit-config.yaml` sólo declara hooks genéricos de
   `pre-commit-hooks` v5.0.0. `.gitignore:28-30` excluye `.env` y `.env.*`
   salvo `.env.example`, higiene correcta pero parcial: protege contra
   agregar un archivo de configuración entero, no contra una credencial
   pegada dentro de un archivo que sí debe versionarse.
3. **SAST**: no hay SpotBugs, Find Security Bugs, Semgrep, PMD ni Error
   Prone. Nada inspecciona el código Java en busca de inyección,
   deserialización insegura, criptografía débil o path traversal.
4. **Escaneo de imagen de contenedor**: nadie mira la imagen construida ni
   su base. El job `nginx-integration` de `pr-main.yml` ya construye
   `menta-api:ci` y `menta-bff:ci`, de modo que el artefacto a escanear
   existe en el pipeline sin trabajo adicional.

Dos condiciones de contorno delimitan la decisión:

- **El repositorio `urrestarazu-org/menta-dance` es público.** Dependabot
  alerts y GitHub Secret Scanning con push protection no tienen costo. En
  contrapartida, un secreto commiteado queda expuesto de inmediato y debe
  considerarse comprometido aunque se borre después: el objeto permanece en
  el historial, en los forks y en los mirrors que ya lo copiaron.
- **`ADR-0024` ya declara Trivy y SonarCloud como parte del stack aceptado.**
  Nunca se implementaron. `pr-main.yml:17-20` documenta explícitamente esa
  deuda: los gates de release están "pendientes de habilitación segura",
  Trivy "no tiene una configuración o acción pineada", y se activarán en
  `pr-main.yml` y nunca en `pr-develop.yml`. Este ADR corrige esa deriva
  entre lo declarado y lo implementado.

## Decisión

Se adopta un baseline de cuatro herramientas, una por cada gap, ubicadas en
el pipeline según su costo de ejecución:

| Capa | Herramienta | Ubicación | Issue |
|---|---|---|---|
| SCA | Dependabot (`gradle`, `github-actions`, `npm`) | Fuera del pipeline de PR | #189 |
| Secretos | Gitleaks | `.pre-commit-config.yaml` + `pr-develop.yml` + `pr-main.yml` | #190 |
| SAST | SpotBugs + Find Security Bugs | `pr-main.yml` | #191 |
| Imagen | Trivy | `pr-main.yml` | #192 |

Se preserva el contrato de CI vigente: `pr-develop.yml` es feedback rápido
(<5 min) y `pr-main.yml` es el pipeline completo. Sólo Gitleaks califica para
`develop`, por correr en segundos. SpotBugs y Trivy son gates de release.

Se preserva también el pinning por SHA completo de las GitHub Actions que
exige ADR-0024. Dependabot debe actualizar el SHA y su comentario de versión,
nunca reemplazarlo por un tag mutable.

Toda supresión —`.gitleaks.toml`, `config/spotbugs/exclude.xml`,
`.trivyignore`— exige justificación escrita por entrada. No se acepta una
supresión global para poner el build en verde.

**Corrección a ADR-0024**: Trivy pasa de declarado a implementado con esta
decisión. SonarCloud se difiere; ver alternativas rechazadas.

## Alternativas rechazadas

- **Status quo**: rechazado. La combinación de repositorio público,
  credenciales de Mercado Pago, Bunny.net, Google Calendar y MySQL, y cero
  observación del árbol de dependencias, no es un riesgo teórico. Los
  incidentes de mayor impacto en el ecosistema Java —Log4Shell,
  Spring4Shell— llegaron por transitivas, no por código propio.

- **SonarCloud como SAST, en lugar de SpotBugs + Find Security Bugs**:
  diferido, no descartado. Cubre más terreno y ADR-0024 ya lo declara, pero
  exige organización, proyecto y `SONAR_TOKEN`: fricción de infraestructura
  y una dependencia de servicio externo en el camino crítico del build.
  SpotBugs con el plugin Find Security Bugs corre offline, sin token, sin
  cuenta y sin cuota, y aporta reglas OWASP específicas para el stack real
  (Spring MVC, JPA/Hibernate, Thymeleaf, criptografía JCA) — exactamente las
  superficies que el BFF y la API exponen. Se prefiere pagar el costo de
  adopción bajo primero y reevaluar SonarCloud cuando el triage inicial esté
  cerrado.

- **Semgrep**: rechazado por ahora. Su valor diferencial está en escribir
  reglas propias sobre patrones de dominio, y el proyecto todavía no tiene
  patrones consolidados que justifiquen ese esfuerzo. Con el ruleset
  genérico se solapa con Find Security Bugs sin agregar cobertura
  proporcional al costo.

- **OWASP Dependency-Check en lugar de Dependabot**: rechazado como punto de
  partida. Es la opción canónica para Java y ADR-0024 no la contradice, pero
  hoy requiere API key de NVD, es lento y sumaría minutos al pipeline.
  Dependabot cubre el mismo gap sin costo de CI, sin token, y además
  mantiene los SHA pinneados de las Actions. Puede incorporarse más adelante
  como gate de release si se necesita SBOM o evidencia de cumplimiento.

- **Concentrar las cuatro herramientas en un solo cambio**: rechazado. Cada
  escáner nuevo produce una tanda inicial de hallazgos. Cuatro tandas
  simultáneas hacen imposible distinguir lo que merece atención del ruido, y
  empujan hacia la supresión masiva —que es exactamente el fracaso que este
  ADR busca evitar. Una herramienta por PR, con su baseline y sus
  supresiones justificadas.

## Consecuencias

### Positivas

- Cierra los cuatro gaps con una herramienta por capa, sin solapamiento:
  Dependabot mira el manifiesto declarado, Trivy mira el binario resultante,
  Find Security Bugs mira el bytecode propio y Gitleaks mira el contenido
  versionado.
- Resuelve la deriva de ADR-0024 en lugar de acumularla: Trivy queda
  implementado y el diferimiento de SonarCloud queda escrito con su motivo.
- La documentación del pipeline pasa a distinguir explícitamente estilo
  (Checkstyle) de seguridad (SpotBugs), y lint de Dockerfile (Hadolint) de
  escaneo de imagen (Trivy). Es el malentendido que originó este ADR.
- Ninguna herramienta introduce dependencia de un servicio externo de pago
  ni comportamiento en runtime. Todas son build time o CI.

### Negativas / Deuda técnica

- `pr-main.yml` se alarga con dos gates nuevos. El impacto sobre el camino
  crítico se mide y se acuerda en cada PR.
- Cada herramienta suma un archivo de supresiones que hay que mantener
  honesto. Una supresión sin revisar envejece hasta volverse un agujero.
- SonarCloud queda declarado en ADR-0024 y todavía sin implementar. La deuda
  se reduce pero no se elimina.
- El triage inicial de SpotBugs puede exigir correcciones de código en
  módulos de negocio. Esas correcciones se separan en issues propias; la
  issue #191 entrega el gate funcionando y el triage documentado, no
  necesariamente cero hallazgos.

### Riesgos y Reversibilidad

- **Riesgo principal**: que el escaneo inicial de Gitleaks encuentre un
  secreto ya presente en el historial. En un repositorio público eso no es
  un hallazgo de CI, es un incidente.
- **Plan de mitigación**: el orden de respuesta es rotar primero la
  credencial expuesta y limpiar el historial después. Al revés no sirve de
  nada: el objeto ya fue copiado. La issue #190 lo fija como criterio de
  aceptación explícito.
- **Riesgo secundario**: que la presión por poner el build en verde derive
  en supresiones sin justificar, dejando las herramientas instaladas y
  ciegas. Mitigación: cada archivo de supresiones exige comentario por
  entrada, y `.trivyignore` además fecha de revisión.
- **Reversibilidad**: alta. Cada herramienta entra en su propio PR y se
  revierte de forma independiente sin migración de datos ni estado
  persistido. Ninguna toca `domain`, `application` ni `infrastructure`.

## Referencias y Decisiones Relacionadas

- Issues #189 (Dependabot), #190 (Gitleaks), #191 (SpotBugs + Find Security
  Bugs), #192 (Trivy)
- `docs/adr/0024-technology-baseline.md` — declara Trivy y SonarCloud en el
  stack, y exige pinning por SHA y versionado exacto. Este ADR lo corrige
  parcialmente.
- `docs/16-CICD-PIPELINE.md` — estado vigente del pipeline; se actualiza al
  cerrar cada issue.
- `docs/15-RISK-MATRIX.md` — los cambios de estrategia requieren ADR.
- `.github/workflows/pr-main.yml:17-20` — comentario que declaraba los gates
  pendientes.
