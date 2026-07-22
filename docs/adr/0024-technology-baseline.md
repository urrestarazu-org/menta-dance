# ADR-0024: Baseline Tecnológico

**Estado:** Aceptado

## Decisión

El scaffold fija versiones exactas en el catálogo/lockfiles. Versiones recibidas
del baseline inicial:

| Tecnología | Versión |
|---|---:|
| Java | 21 |
| Spring Boot | 3.5.14 |
| MySQL | 8.0 |
| Lombok | 1.18.38 |
| MapStruct | 1.5.5 |
| Mercado Pago SDK | 2.5.0 |
| Bunny SDK | 0.0.14 |
| Google Calendar API | 2.5.0 |
| Hashids | 1.0.3 |
| OpenHTMLtoPDF | 1.0.10 |
| libphonenumber | 8.13.47 |
| Tailwind CSS | 3.4.1 |
| Node.js | 20.11.1 |
| PostCSS / Autoprefixer | 8.4.35 / 10.4.17 |
| Checkstyle | 10.23.0 |

Spring-managed dependencies se resuelven mediante el BOM exacto de Spring Boot
3.5.14 y se congelan en lockfiles al crear el scaffold. La salida de logging es
**Logback JSON**, no Log4j. El stack incluye Gradle, Flyway, Caffeine, Redis,
JUnit 5, ArchUnit, JaCoCo, SonarCloud, Playwright, Gatling, Trivy, Docker Compose
y GitHub Actions.

Actions de GitHub se fijan por SHA completo y nunca por tags mutables. Cualquier
actualización de versión exige PR, CI verde y decisión documentada.
