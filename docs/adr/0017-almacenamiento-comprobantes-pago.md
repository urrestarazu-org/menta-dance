# ADR-0017: Estrategia de Almacenamiento de Comprobantes de Pago para Transferencias Bancarias

**Estado:** Aceptado
**Fecha:** 2026-05-23
**Decisores:** Arquitecto de Software, Tech Lead

## Contexto y Problema

Con la introducción de pagos por transferencia bancaria (Should Have del MVP) descritos en la historia de usuario `US-BILLING-003`, los alumnos necesitan subir un comprobante físico (PDF o imagen de hasta 5MB) de la transferencia. El sistema debe almacenar estos archivos de forma privada y segura para que los administradores los auditen y aprueben los accesos.

Necesitamos definir una estrategia de almacenamiento de archivos para el MVP que sea rentable, fácil de implementar y segura, considerando que las primeras etapas del proyecto contarán con un volumen moderado de transacciones (~200 activas).

## Factores Clave (Decision Drivers)

* **Costo mensual extra:** Debe ser nulo o mínimo para el MVP.
* **Seguridad lógica:** Los comprobantes no deben estar en directorios accesibles de forma pública para proteger la privacidad financiera.
* **Simplicidad de desarrollo:** Minimizar el esfuerzo inicial de desarrollo en Spring Boot.
* **Escalabilidad y portabilidad:** Debe ser fácil migrar a almacenamiento en la nube (ej. S3/Cloudflare R2) a medida que el volumen escale.

## Opciones Consideradas

### Opción 1: Almacenamiento en File System Local Seguro (VPS)

* **Descripción:** Almacenar los archivos en un directorio privado local del servidor VPS (fuera de la raíz pública del servidor web). En la base de datos se guarda un ID único o ruta relativa. El acceso al archivo se realiza únicamente a través de un endpoint protegido de la `Billing API` (ej. `GET /api/v1/billing/admin/payments/{id}/proof/download`), que lee y transmite el archivo tras validar permisos de administrador.
* **Pros:**
  * **Costo Nulo:** Utiliza el almacenamiento de disco local de 100GB SSD ya contratado en el VPS.
  * **Simplicidad de implementación:** Requiere solo clases estándar de Java (NIO Files) sin SDKs externas.
  * **Seguro:** Los archivos no están expuestos al exterior directamente.
* **Contras:**
  * En un entorno Docker, requiere configurar volúmenes persistentes en el host para evitar pérdida de archivos al recrear contenedores.
  * Dificulta el escalamiento horizontal sin usar almacenamiento de archivos compartido.

### Opción 2: Google Drive API (Google Workspace)

* **Descripción:** Integrar la `Billing API` con Google Drive mediante una Cuenta de Servicio. Al recibir un archivo, el backend lo sube a una carpeta privada y guarda en MySQL el `fileId` de Google Drive.
* **Pros:**
  * **Gratuito/Económico:** Hasta 15 GB con cuenta personal o integrado en la cuenta existente de Google Workspace de la academia.
  * Desliga el almacenamiento local.
* **Contras:**
  * **Complejidad:** Alta complejidad para configurar credenciales, OAuth2 y mantener la SDK cliente de Google Drive en Spring Boot.
  * **Latencia y dependencia:** Añade llamadas HTTP de red bloqueantes del VPS a Google en cada subida/inspección de comprobante.

### Opción 3: Amazon S3 o Cloudflare R2

* **Descripción:** Almacenar los archivos en la nube utilizando protocolos compatibles con S3.
* **Pros:**
  * Estándar de la industria, desacoplado y altamente escalable.
  * Cloudflare R2 ofrece 10 GB gratuitos sin cargos de descarga (egress).
* **Contras:**
  * Añade complejidad de setup inicial (creación de buckets, políticas de acceso IAM).
  * AWS S3 puede introducir costos variables a futuro.

## Decisión

Elegimos la **Opción 1: Almacenamiento en File System Local Seguro (VPS)** para el MVP, por su **costo nulo** y **simplicidad extrema de desarrollo**.

Para asegurar la extensibilidad hacia la **Opción 3 (Cloud Storage / S3)** en fases posteriores de crecimiento, definiremos una interfaz de almacenamiento en código (ej. `FileStorageService`) con implementaciones intercambiables (`LocalFileStorageServiceImpl` y `S3FileStorageServiceImpl`).

## Justificación (Rationale)

Para el MVP (con 200 usuarios y ~200 transacciones mensuales estimadas), contratar o integrar almacenamiento en la nube complejo introduce sobreingeniería. El uso de almacenamiento en disco local del VPS es inmediato. La seguridad se garantiza al guardar los archivos fuera del directorio accesible por NGINX, obligando a que cualquier visualización pase por la lógica de control de accesos de la `Billing API`.

La persistencia del almacenamiento local en Docker se resuelve de manera limpia mediante mounts de volúmenes en el archivo `docker-compose.yml`.

## Consecuencias

### Positivas

* Implementación del MVP rápida (reducción de esfuerzo a 1-2 días de desarrollo).
* Cero costos mensuales adicionales en almacenamiento de infraestructura.
* Privacidad de datos financieros de alumnos garantizada por diseño (acceso mediado por API).

### Negativas / Deuda Técnica

* Dependencia del disco local del VPS para backups (los comprobantes deben ser respaldados junto con el disco del servidor).
* En el momento que se necesite escalar horizontalmente a múltiples servidores de aplicación, será obligatorio migrar al almacenamiento S3/R2 compatible.

### Implicaciones de Costos

* Costo de infraestructura adicional: **$0 USD/mes** para el MVP.

### Riesgos y Reversibilidad

* **Riesgo Principal:** Pérdida de comprobantes en recreación de contenedores Docker.
* **Plan de Mitigación:** Configurar obligatoriamente un volumen Docker montado en el host en `/var/data/menta/proofs` para asegurar la persistencia física independiente del ciclo de vida del contenedor.
* **Reversibilidad:** Muy alta. Al programar la lógica detrás de una interfaz `FileStorageService`, la migración a Cloudflare R2, AWS S3 o Google Drive en el futuro solo requerirá escribir una nueva clase que implemente la interfaz y cambiar una propiedad de configuración, sin alterar los controladores ni la lógica de negocio de pagos.

## Referencias y Decisiones Relacionadas

* Historia de Usuario: [US-BILLING-003: Suscripción por Transferencia Bancaria](./../user-stories/US-BILLING-003.md)
* Restricciones de Infraestructura: [ADR-0009: Restricciones de Infraestructura MVP](./0009-restricciones-infraestructura-mvp.md)
