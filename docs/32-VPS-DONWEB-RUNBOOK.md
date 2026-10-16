# Runbook del VPS de DonWeb

Bitácora operativa del cloud server de DonWeb donde se despliega Menta Dance (#380). Cada sección registra lo que se ejecutó en el servidor, con fecha y resultado, para que se pueda repetir o auditar.

## Metadata

- **Servidor**: `Menta-server-cloud`, Ubuntu 24.04, 2 vCPU, 4 GB de RAM, ~35 GB de SSD, Docker preinstalado
- **IP y puerto SSH**: se consultan en el panel de DonWeb (Cloud Servers → Software y Accesos); no se guardan en el repositorio
- **Dominios**: `mentadance.com` (BFF) y `api.mentadance.com` (API, pendiente de crear en el DNS); ambos apuntan al VPS
- **Vencimiento del plan**: 2027-10-22
- **Estado**: proyecto anterior retirado (#595); aprovisionamiento pendiente (#590)

> No se registran contraseñas, claves ni tokens en este documento.

---

## 1. Retiro del proyecto anterior (#595)

El VPS corría el proyecto anterior de `mentavirtual.com`. Ocupaba los puertos 80/443, la RAM y publicaba MySQL en `0.0.0.0:3306`. Decisión del dueño (2026-10-16): no tenía usuarios ni datos reales, se descarta **sin backup**.

### Inventario previo (2026-10-16, solo lectura)

| Recurso | Estado |
|---|---|
| Contenedores | `menta-app`, `menta-nginx` (80/443), `menta-db-container` (MySQL 8.0, `0.0.0.0:3306`), `menta-db-check` (detenido) |
| Imágenes | `menta-app:latest`, `mysql:8.0`, `nginx:alpine`, `nginx:latest` |
| Volúmenes | 48 (`database_menta-db-data`, `nginx_menta-app-logs`, `nginx_menta-nginx-logs` y 45 anónimos) |
| Docker | 3,9 GB en imágenes, 2,4 GB de build cache |
| Repo viejo | `/root/repos/menta-virtual-academy` |
| Tareas programadas | ninguna (`crontab` vacío) |
| Certificado | Sectigo solo para `mentavirtual.com` y `www`, vence 2026-10-23 |

### Pasos

Los comandos se corren en el VPS, en este orden.

**1. Cerrar MySQL a internet** (hecho 2026-10-16)

```bash
docker stop menta-db-container
ss -tlnp | grep ':3306' || echo "3306 cerrado"
```

> `ufw` no protege los puertos que Docker publica: Docker escribe sus propias reglas de `iptables`. Por eso se detiene el contenedor en lugar de confiar en el firewall del host.

**2. Eliminar los contenedores** (hecho 2026-10-16)

```bash
docker stop menta-nginx menta-app
docker rm menta-nginx menta-app menta-db-container menta-db-check
docker ps -a
```

Resultado: `docker ps -a` sin contenedores. `mentavirtual.com` deja de responder hasta resolver #598.

**3. Eliminar volúmenes, imágenes y build cache** (hecho 2026-10-16)

Antes de borrar se listó el contenido y se verificó que todo pertenecía al proyecto anterior:

```bash
docker volume ls
docker images
```

```bash
docker volume prune -a -f
docker image prune -a -f
docker builder prune -a -f
docker system df
```

Resultado: `docker system df` en cero (0 imágenes, 0 contenedores, 0 volúmenes, 0 B de build cache).

> Irreversible: borra también `database_menta-db-data`. Es correcto porque no hay datos que conservar.

**4. Eliminar el repo viejo** (hecho 2026-10-16)

```bash
rm -rf /root/repos/menta-virtual-academy
```

**5. Verificar** (hecho 2026-10-16)

```bash
sudo ss -tlnp | grep -E ':(80|443|3306)\b' || echo "80/443/3306 libres"
docker ps -a; docker volume ls
df -h /; free -h
```

Desde otra máquina, `nc -zv -w 5 <IP-DEL-VPS> 3306` debe fallar o dar timeout.

Resultado: en el servidor, 80/443/3306 libres; desde afuera, `nc` al 3306 dio `Operation timed out`.

### Errores comunes

- **`rg: command not found`** en el servidor: el VPS no tiene ripgrep. Usar `grep`. Ojo: con `cmd1 | rg ... || echo "ok"` el `echo` se ejecuta aunque el puerto siga abierto, porque falla `rg`.

---

## 2. Hallazgos de seguridad pendientes (#590)

Detectados en el inventario, se corrigen al aprovisionar el servidor:

- SSH escucha en el puerto 22 y en uno no estándar; dejar solo el no estándar.
- Acceso como `root` con contraseña; pasar a un usuario `deploy` con clave SSH.
- Verificar en el panel de DonWeb que el firewall bloquee 3306 desde afuera.
- Postfix escucha en el puerto 25 sin necesidad conocida.

### Firewall del panel de DonWeb (revisado 2026-10-16)

El firewall virtual del panel bloquea por defecto todo el tráfico entrante. Reglas existentes, todas para `0.0.0.0/0` y `::/0`:

| Puerto | Protocolo | Observación |
|---|---|---|
| 80, 443 | TCP | Web y HTTPS. Necesarios |
| puerto SSH no estándar | TCP | SSH. Se podría limitar a la IP del operador |
| 587 | TCP | Sin servicio conocido escuchando; revisar si se puede cerrar |
| 443 | UDP | Solo útil con HTTP/3 en Nginx; revisar si se puede cerrar |

El puerto 22 y el 3306 no tienen regla, es decir, están bloqueados desde afuera (se verificó el 3306 con `nc`). Las reglas tienen un año de antigüedad y se revisan en #590.

## 3. Pendiente de documentar

Se completa a medida que avancen los sub-issues de #380:

- Aprovisionamiento: usuario `deploy`, firewall, Docker y TLS (#590).
- Despliegue y rollback (#593).
- Backups y monitoreo (#594).
