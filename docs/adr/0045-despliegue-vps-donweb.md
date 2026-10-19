# ADR-0045: Despliegue en un VPS de DonWeb con Docker Compose

**Estado:** Aceptado
**Fecha:** 2026-10-16
**Decisores:** Alejandro Urrestarazu

## Contexto y Problema

El TFM recomienda un despliegue funcionando con su URL en la documentación, y
`docs/12-COST-ANALYSIS.md` dice que el proveedor y el presupuesto se deciden antes
de la primera release. #380 pide dejar esa decisión escrita: proveedor, costo
mensual, dominio y gestión de secretos.

El servidor ya está contratado y los scripts de `infra/docker/` y la configuración
de Nginx (`docs/29-NGINX-REVERSE-PROXY.md`) están pensados para un VPS con Docker
Compose. El flujo de entrega (GHCR, GitHub Actions) lo fija ADR-0018.

## Factores Clave (Decision Drivers)

* Presupuesto bajo y predecible: es una demo para el TFM y un primer despliegue
  real, sin ingresos que lo respalden todavía.
* Debe correr API, BFF, MySQL, Redis y Nginx en un solo servidor, detrás de HTTPS
  válido.
* Los scripts y la configuración de Nginx existentes asumen Docker Compose en un
  VPS.
* Un solo operador: la solución tiene que poder explicarse y repetirse con un
  runbook corto.

## Opciones Consideradas

### Opción 1: Seguir solo en local (status quo)

* **Descripción:** la demo se muestra desde la máquina del autor.
* **Pros:**
  * Sin costo.
* **Contras:**
  * No hay URL pública ni app Android apuntando a un servidor real.
  * No cumple la recomendación del TFM ni valida el despliegue.

### Opción 2: VPS de DonWeb (Cloud Server) con Docker Compose

* **Descripción:** un servidor virtual con Ubuntu 24.04 y Docker, ya contratado.
* **Pros:**
  * Ya pagado por 12 meses; costo fijo en pesos argentinos.
  * Encaja con los scripts y la configuración de Nginx existentes.
  * Proveedor local, con soporte en español y dominios gestionados en el mismo
    panel.
* **Contras:**
  * Un solo servidor: sin alta disponibilidad.
  * 4 GB de RAM obligan a limitar la memoria de cada servicio.

### Opción 3: Plataforma gestionada (PaaS) o varios servicios cloud

* **Descripción:** desplegar API, BFF, base de datos y Redis como servicios
  separados de un proveedor cloud.
* **Pros:**
  * Menos operación del sistema operativo y escalado más sencillo.
* **Contras:**
  * Costo variable y mayor que un VPS fijo; no se evaluó con precios.
  * Obliga a reescribir los scripts y la configuración de Nginx.

## Decisión

Elegimos **un Cloud Server de DonWeb con Docker Compose** (Opción 2), porque ya
está contratado, cubre el stack completo con memoria acotada y reutiliza lo que el
repositorio ya tiene.

### Servidor

| Dato | Valor |
|---|---|
| Plan | Cloud Server de DonWeb |
| Recursos | 2 vCPU, 4 GB de RAM, ~35 GB de SSD, 1 TB/mes de transferencia |
| Sistema | Ubuntu 24.04 LTS con Docker y Compose plugin |
| Contratación | 12 meses, del 2026-10-22 al 2027-10-22 |
| Costo pagado | $290.068,80 ARS por 12 meses (≈ $24.172,40 ARS por mes) |
| Precio de lista | $27.767,20 ARS por mes; $333.206,40 ARS por año |

La renovación de 2027 se calcula al precio de lista vigente, que puede diferir de
lo pagado en 2026.

### Dominios

| Dominio | Destino |
|---|---|
| `mentadance.com` | BFF (la web de todos los usuarios) |
| `api.mentadance.com` | API (la usa la app Android) |
| `mentavirtual.com` | Redirige a la web nueva; se resuelve en #598 |

Ambos subdominios apuntan con un registro `A` al VPS desde el DNS de DonWeb. El
resto de los dominios comprados no entra en este despliegue.

### TLS

Let's Encrypt con certbot y renovación automática. El certificado Sectigo que
tenía el proyecto anterior cubre solo `mentavirtual.com` y `www`, vence el
2026-10-23 y se renueva a mano, por lo que no se reutiliza.

### Secretos

* **Secretos de la aplicación** (JWT, cifrado de activación, base de datos,
  credenciales de proveedores): en `/opt/menta/.env` del servidor, con `chmod 600`
  y dueño `deploy`. Nunca pasan por GitHub ni por el repositorio; el repositorio
  solo tiene un `.env.production.example` con los nombres.
* **Secretos de despliegue** (`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`,
  `DEPLOY_KNOWN_HOSTS`): en el Environment `production` de GitHub, visible solo
  para el job de deploy.
* **Lectura de imágenes:** el servidor se autentica en GHCR con un PAT con scope
  `read:packages` únicamente.

### Red y acceso

* El firewall se gestiona en el panel de DonWeb. Quedan abiertos HTTP (80),
  HTTPS (443) y SSH en el puerto no estándar que DonWeb asigna al servidor
  (se consulta en el panel y no se documenta en el repositorio); el puerto 22 se
  cierra.
* SSH solo con clave y sin `root`, mediante un usuario `deploy` dedicado.
* MySQL y Redis no publican puertos: solo son accesibles desde la red interna de
  Compose.

### Memoria

Referencia para 4 GB, con `mem_limit` en Compose: API 1 GB, BFF 768 MB, MySQL 1 GB,
Redis 128 MB, más 1–2 GB de swap. Sin estos límites, el heap de cada JVM se calcula
sobre la RAM del servidor entero.

## Justificación (Rationale)

Un VPS fijo cumple el presupuesto y es lo único que los scripts actuales saben
desplegar. Un solo servidor es un riesgo aceptado: la demo no tiene un SLA
comprometido, y un fallo se resuelve restaurando un backup en otro VPS. Let's
Encrypt evita una renovación anual manual y cubre ambos subdominios sin costo.

## Consecuencias

### Positivas

* URL pública con HTTPS para el TFM y para la app Android de demo.
* Costo fijo y conocido durante 12 meses.
* Flujo de despliegue repetible: ADR-0018 y el runbook
  [`docs/32-VPS-DONWEB-RUNBOOK.md`](../32-VPS-DONWEB-RUNBOOK.md).

### Negativas / Deuda Técnica

* Sin alta disponibilidad: el despliegue implica un corte de unos segundos al
  arrancar las JVM.
* La operación del sistema operativo (parches, firewall, backups) es del autor.
* Los backups del panel de DonWeb son semanales; los diarios se hacen fuera del
  servidor (#594).

### Implicaciones de Costos

* Servidor: ≈ $24.172,40 ARS por mes (pagado en 2026-10-16, $290.068,80 ARS).
* Dominios: ya comprados; su renovación es un costo aparte.
* GHCR, GitHub Actions y Let's Encrypt: sin costo adicional dentro de los límites
  del plan gratuito.

### Riesgos y Reversibilidad

* **Riesgo principal:** pérdida de datos o corte prolongado por tener un único
  servidor.
* **Mitigación:** backup diario de MySQL y de los comprobantes de pago fuera del
  servidor, restauración probada una vez, y uptime check externo (#594).
* **Reversibilidad:** alta. Las imágenes viven en GHCR y el estado en MySQL y un
  volumen; migrar a otro proveedor es crear un servidor, restaurar el backup y
  cambiar el DNS.

## Referencias y Decisiones Relacionadas

* #380 (despliegue público de la demo) y #586 (este ADR).
* Complementa a: ADR-0018 (CI/CD, GHCR y despliegue por digest), ADR-0027 (Flyway
  como única vía de esquema).
* [`docs/32-VPS-DONWEB-RUNBOOK.md`](../32-VPS-DONWEB-RUNBOOK.md)
* [`docs/16-CICD-PIPELINE.md`](../16-CICD-PIPELINE.md)
