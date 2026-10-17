# Runbook del VPS de DonWeb

Bitácora operativa del cloud server de DonWeb donde se despliega Menta Dance (#380). Cada sección registra lo que se ejecutó en el servidor, con fecha y resultado, para que se pueda repetir o auditar.

## Metadata

- **Servidor**: `Menta-server-cloud`, Ubuntu 24.04, 2 vCPU, 4 GB de RAM, ~35 GB de SSD, Docker preinstalado
- **IP y puerto SSH**: se consultan en el panel de DonWeb (Cloud Servers → Software y Accesos); no se guardan en el repositorio
- **Dominios**: `mentadance.com` (BFF, con `www` como CNAME) y `api.mentadance.com` (API); los tres nombres apuntan al VPS
- **Vencimiento del plan**: 2027-10-22
- **Estado**: proyecto anterior retirado (#595); VPS aprovisionado (#590), certificado emitido (vence 2027-01-14); renovación por `webroot` decidida (se aplica en #589); `/opt/menta/.env` creado vacío; firewall del panel revisado; endurecimiento de SSH descartado por ahora. El compose de producción (#589) y `deploy.sh` (#593) están en el repositorio y falta instalarlos y probarlos en el servidor (secciones 2.9 y 2.10)

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

## 2. Aprovisionamiento (#590)

Pasos ejecutados el 2026-10-16 en el VPS. `<PUERTO-SSH>` es el puerto SSH no estándar que figura en el panel de DonWeb.

### 2.1 Swap de 2 GB

Con 4 GB de RAM y límites de memoria por contenedor, el swap evita que el kernel mate procesos ante un pico.

```bash
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
free -h
```

### 2.2 Actualizaciones de seguridad automáticas

Ya venían activas; se verificó en lugar de reinstalar: `apt-daily-upgrade.timer` habilitado y `/etc/apt/apt.conf.d/20auto-upgrades` con las dos opciones en `"1"`.

```bash
systemctl is-enabled apt-daily-upgrade.timer
cat /etc/apt/apt.conf.d/20auto-upgrades
```

> `systemctl status unattended-upgrades` puede mostrar el servicio inactivo y aun así funcionar: lo dispara el timer.

### 2.3 Rotación de logs de Docker

Sin límite, el driver `json-file` crece sin tope y puede llenar el disco.

`/etc/docker/daemon.json`:

```json
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
```

```bash
python3 -m json.tool /etc/docker/daemon.json && sudo systemctl restart docker
docker info --format '{{.LoggingDriver}}'
```

Solo aplica a contenedores creados después del reinicio.

### 2.4 fail2ban sobre el puerto SSH real

El jail por defecto vigila el puerto 22, que ya no se usa; hay que fijar el puerto real. `/etc/fail2ban/jail.local`:

```ini
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 3
banaction = nftables

[sshd]
enabled = true
port = <PUERTO-SSH>
```

```bash
sudo systemctl restart fail2ban
sudo fail2ban-client status sshd
```

La tabla `inet f2b-table` de nftables se crea recién con el primer bloqueo, así que `nft list ruleset` aparece vacío antes. Prueba de que funciona, con una IP de documentación (RFC 5737):

```bash
sudo fail2ban-client set sshd banip 192.0.2.1
sudo nft list ruleset | grep 192.0.2.1
sudo fail2ban-client set sshd unbanip 192.0.2.1
```

### 2.5 Usuarios y accesos

| Usuario | Para qué | Acceso |
|---|---|---|
| `mentaadmin` | Administración | `sudo`, clave SSH del operador |
| `deploy` | Despliegue y operación de Compose | Clave SSH dedicada, contraseña bloqueada (`passwd -l`), grupo `docker`, dueño de `/opt/menta` |

La clave de despliegue se generó en la máquina del operador, fuera del servidor, y solo su parte pública está en `/home/deploy/.ssh/authorized_keys`. El grupo `docker` equivale a `root`: cualquiera que entre como `deploy` controla el servidor.

Una vez instalado `deploy.sh` (#593, sección 2.10), la entrada de esa clave se restringe a un comando forzado:

```
command="/opt/menta/deploy.sh",no-pty,no-port-forwarding ssh-ed25519 AAAA...
```

Directorios de trabajo:

```bash
install -d -o deploy -g deploy /opt/menta /opt/menta/backups
```

Archivo de secretos de la aplicación (ADR-0045). Se crea vacío, con permisos restringidos; su contenido lo define #589 con el `.env.production.example`:

```bash
sudo install -m 600 -o deploy -g deploy /dev/null /opt/menta/.env
ls -l /opt/menta/.env
# -rw------- 1 deploy deploy 0 ... /opt/menta/.env
```

Se eliminaron de `deploy` los restos de un uso anterior (`repos`, `.gradle`, `.cache`, `.docker` y una clave privada vieja). Conviene quitar también la deploy key vieja del repositorio `menta-virtual-academy` en GitHub.

### 2.6 DNS

En la zona `mentadance.com` del panel de DonWeb: registros `A` de `mentadance.com` y `api.mentadance.com` al VPS, y `www` como CNAME a `mentadance.com`. Verificación desde la máquina del operador:

```bash
dig +short mentadance.com A
dig +short www.mentadance.com
dig +short api.mentadance.com A
```

### 2.7 Lectura de imágenes desde GHCR

Token de GitHub **classic** con scope `read:packages` únicamente y vencimiento de 1 año, creado en *Settings → Developer settings → Personal access tokens → Tokens (classic)* con el nombre `menta-vps-ghcr-pull`. Como `deploy`:

```bash
read -rs -p "Token: " T; echo
echo "$T" | docker login ghcr.io -u <USUARIO-GITHUB> --password-stdin
unset T
chmod 600 ~/.docker/config.json
```

`read -rs` evita que el token quede en pantalla o en el historial. Docker avisa que guarda la credencial sin cifrar en `config.json`; es esperado y queda protegido por `chmod 600`. Resultado: `Login Succeeded`. El token vence a los 12 meses: renovarlo antes y repetir el login.

### 2.8 Certbot (Let's Encrypt)

Con el puerto 80 libre se usa el modo `standalone`. Instalación y prueba en seco, que valida el desafío HTTP sin emitir nada:

```bash
sudo apt install -y certbot
sudo certbot certonly --standalone --dry-run \
  -d mentadance.com -d www.mentadance.com -d api.mentadance.com \
  --register-unsafely-without-email --agree-tos
```

Resultado: `The dry run was successful`. El paquete instala `certbot.timer`, que renueva solo.

Emisión real (2026-10-16), con el mismo comando sin `--dry-run` y con el correo del operador para los avisos de vencimiento:

```bash
sudo certbot certonly --standalone \
  -d mentadance.com -d www.mentadance.com -d api.mentadance.com
```

Resultado: un único certificado para los tres nombres, en `/etc/letsencrypt/live/mentadance.com/` (`fullchain.pem` y `privkey.pem`), que vence el 2027-01-14. Let's Encrypt emite certificados de 90 días; el temporizador intenta renovar cuando faltan 30.

Verificación de la renovación automática (2026-10-16, con el puerto 80 todavía libre):

```bash
sudo certbot renew --dry-run
systemctl list-timers certbot.timer
```

Resultado: `all simulated renewals succeeded` y `certbot.timer` programado.

#### Renovación: pasar de `standalone` a `webroot` (decidido, se aplica en #589)

`--standalone` levanta su propio servidor en el puerto 80, que a partir del primer despliegue ocupa Nginx, así que la renovación fallaría en silencio hacia el 2026-12-15 (30 días antes del vencimiento). Se eligió `webroot` porque no corta el servicio; la alternativa, `standalone` con hooks que detienen y levantan Nginx, cuesta unos segundos de caída por renovación.

Dos cambios sobre lo que hoy trae el repositorio:

- El Nginx de `infra/docker/nginx/` espera los certificados como archivos en `./certs/`. En producción se monta `/etc/letsencrypt` en solo lectura y `ssl_certificate` apunta a `/etc/letsencrypt/live/mentadance.com/`, así Nginx siempre ve el certificado vigente sin copiarlo.
- El Nginx de producción tiene que servir el desafío ACME.

Pasos de #589. Los dos primeros ya están en el repositorio (`infra/docker/prod/`); los otros tres se ejecutan en el VPS y quedan **pendientes de ejecutar**:

1. `infra/docker/prod/nginx/nginx.conf` tiene la `location /.well-known/acme-challenge/` en el puerto 80, con `root /var/www/certbot`.
2. `infra/docker/prod/docker-compose.prod.yml` monta `/var/www/certbot` y `/etc/letsencrypt` en solo lectura en el contenedor de Nginx.
3. En el VPS, **antes del primer despliegue con Nginx**:

   ```bash
   sudo mkdir -p /var/www/certbot
   sudo certbot reconfigure --cert-name mentadance.com \
     --authenticator webroot --webroot-path /var/www/certbot
   ```

4. Hook de renovación que recarga Nginx, en `/etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh` (ejecutable). Si Nginx no está corriendo, el hook no debe hacer fallar la renovación:

   ```bash
   #!/bin/sh
   docker exec menta-nginx nginx -s reload || true
   ```

5. Con Nginx corriendo, `sudo certbot renew --dry-run` tiene que pasar.

Si el paso 3 se omite, la renovación falla sin avisar hasta que el certificado vence. Lo único que se entera es el correo de avisos de Let's Encrypt.

### 2.9 Compose de producción (#589)

Los archivos están en `infra/docker/prod/`:

| Archivo | Para qué |
|---|---|
| `docker-compose.prod.yml` | API y BFF por digest desde GHCR, MySQL 8.4.11, Redis y Nginx, con `mem_limit` en cada uno |
| `nginx/nginx.conf` | HTTPS con el certificado de Let's Encrypt, desafío ACME, host de la API y servidores por defecto |
| `production.env.example` | Nombres de las variables que van en `/opt/menta/.env` (sin valores) |

Puntos a tener en cuenta:

- `mem_limit`: API 1 GB, BFF 768 MB, MySQL 1 GB, Redis 128 MB, Nginx 128 MB. Suman unos 3 GB sobre los 4 GB del servidor, más el swap de 2.1.
- Redis tiene `--maxmemory 96mb` y `noeviction`: si se llena, responde error y la API falla cerrada, en lugar de que el kernel mate el contenedor.
- Subred de Compose fija (`172.29.250.0/24`): es la única que API y BFF aceptan como proxy de confianza para `X-Forwarded-For`. Si choca con otra red del servidor, se cambia con `MENTA_NETWORK_SUBNET`.
- El compose monta `../nginx/conf/conf.d`, así que `prod/` y `nginx/conf/conf.d/` tienen que estar juntas en el servidor. Con el despliegue automático, `deploy.sh` las recibe en cada release como un `tar.gz` y las deja en `/opt/menta/stack/` (sección 2.10); no hace falta clonar el repositorio.
- El healthcheck del contenedor de Nginx consulta `http://127.0.0.1/health`, que responde en el servidor por defecto del puerto 80. Antes consultaba `localhost`: resolvía a IPv6, donde Nginx no escucha, y por IPv4 el servidor por defecto cortaba la conexión (`return 444`), así que el contenedor habría quedado siempre `unhealthy` (corregido en #593; verificado con un Nginx real).
- Arranque a mano, solo para la primera prueba: `cd /opt/menta/stack/prod && docker compose --env-file /opt/menta/.env -f docker-compose.prod.yml up -d`. Después del primer despliegue, usar `deploy.sh`.

### 2.10 Despliegue con deploy.sh (#593)

`infra/docker/prod/deploy.sh` es lo único que ejecuta la clave SSH de despliegue. Lo llama el job `deploy` de `release.yml` con los digests de las imágenes y la configuración del stack (contrato y pasos en `docs/16-CICD-PIPELINE.md`, "Contrato con `deploy.sh`"). Se probó con un Docker real: el backup y su restauración (33 tablas del esquema), la lectura del health de API, BFF y Nginx, y la poda de imágenes. Las pruebas de lógica están en `infra/docker/prod/deploy.test.sh` y corren en CI.

**Archivos en el servidor:**

| Ruta | Contenido |
|---|---|
| `/opt/menta/deploy.sh` | El script (lo instala el operador; no se actualiza solo) |
| `/opt/menta/.env` | Secretos y `API_DIGEST` / `BFF_DIGEST` (los reescribe el deploy) |
| `/opt/menta/stack/` | Configuración del release: `prod/` y `nginx/conf/conf.d/` |
| `/opt/menta/previous-digests` | Digests de la versión anterior, en una línea |
| `/opt/menta/backups/` | `menta-<fecha>.sql.gz` previos a cada deploy (los últimos 14) |

**Instalación (pendiente de ejecutar en el VPS).** Como `mentaadmin`, con el `deploy.sh` del commit que se va a desplegar copiado al servidor:

```bash
sudo install -m 750 -o deploy -g deploy deploy.sh /opt/menta/deploy.sh
```

Pasos para el primer despliegue, en este orden:

1. Completar `/opt/menta/.env` (sección 2.9) y hacer el login a GHCR de `deploy` (sección 2.7).
2. Publicar un tag con sufijo (`v0.1.0-rc.1`): crea las imágenes y la pre-release sin desplegar y deja los digests en las notas.
3. Desplegar a mano esa primera vez, desde la máquina del operador y con la clave de despliegue todavía sin comando forzado:

   ```bash
   tar -czf - -C infra/docker prod nginx/conf/conf.d \
     | ssh -p <PUERTO> deploy@<HOST> /opt/menta/deploy.sh <digest-api> <digest-bff>
   ```

4. Comprobar los servicios y completar la renovación de certbot por `webroot` (sección 2.8).
5. Recién entonces restringir la clave en `/home/deploy/.ssh/authorized_keys` al comando forzado (sección 2.5) y cargar los secretos del Environment `production` (ver `docs/16-CICD-PIPELINE.md`, "Configuración en GitHub").

Con el comando forzado, la clave de despliegue ya no abre una shell: la administración se hace como `mentaadmin` y `sudo -iu deploy`.

**Rollback manual.** Si el deploy termina con "no respondió UP", el script imprime el comando para volver atrás. Como `deploy`:

```bash
cat /opt/menta/previous-digests
/opt/menta/deploy.sh $(cat /opt/menta/previous-digests)
```

Las imágenes anteriores siguen en el servidor, así que no descarga nada. Esto **no revierte la base de datos**: si la release trajo una migración de Flyway incompatible con la versión anterior, hay que restaurar antes el backup que `deploy.sh` hizo justo antes de ese deploy.

**Restaurar un backup.** El volcado se probó restaurado sobre una base con datos modificados y sobre una base borrada y recreada (que además elimina las tablas que el backup no tiene). Para dejar el esquema exactamente como estaba (incluida la historia de Flyway), se borra y se recrea la base y se restaura, con la API y el BFF detenidos:

```bash
cd /opt/menta/stack/prod
docker compose --env-file /opt/menta/.env -f docker-compose.prod.yml stop api bff
docker exec menta-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root -e "DROP DATABASE menta; CREATE DATABASE menta"'
gzip -dc /opt/menta/backups/menta-<fecha>.sql.gz \
  | docker exec -i menta-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --user=root menta'
/opt/menta/deploy.sh $(cat /opt/menta/previous-digests)
```

El usuario de la aplicación conserva sus permisos porque son sobre el nombre de la base. Los comprobantes de transferencia (volumen `payment_proofs_data`) no entran en este backup: su copia diaria fuera del servidor es parte de #594.

**Errores comunes:**

- `No se pudieron descargar las imágenes`: venció o falta el login a GHCR del usuario `deploy` (sección 2.7; el token dura 12 meses). El estado del servidor no cambia.
- `Hay otro deploy en curso`: otro `deploy.sh` tiene el lock (`/opt/menta/deploy.lock`); el lock se libera solo cuando ese proceso termina.
- `AVISO: ... difiere del deploy.sh de este release`: hay una versión nueva del script; reinstalarlo con el `install` de arriba. El deploy continúa con la versión instalada.
- `mysqldump falló` o `backup ... incompleto`: no se despliega nada. Ver el espacio en disco (`df -h /opt/menta`) y que `menta-mysql` esté `healthy`.

### Decisiones diferidas

- **SSH con `root` y contraseña** (descartado por ahora, decisión del operador del 2026-10-16): sigue permitido. Endurecerlo (solo clave, sin `root`, puerto 22 cerrado en `sshd`) se descarta porque un error de configuración puede dejar al operador sin acceso al servidor, y el beneficio para esta demo no lo justifica. La defensa es fail2ban y el firewall del panel, que bloquea el 22. Si se retoma: en `/etc/ssh/sshd_config.d/` hay `custom.conf` (`PermitRootLogin yes`) y `80-step.conf` (origen desconocido, parece Smallstep); SSH arranca por socket de systemd (`ssh.socket`), así que cerrar el 22 se hace ahí y no en `sshd_config`. Antes de cambiar nada, confirmar el acceso con clave de `mentaadmin` desde otra terminal y mantener abierta la sesión actual.
- **Postfix** escucha en el puerto 25 sin necesidad conocida; el firewall del panel lo bloquea desde afuera. Revisar el MX del dominio antes de quitarlo.
- **Puerto 22**: sigue escuchando en el servidor, pero el firewall del panel lo bloquea.

### Firewall del panel de DonWeb (revisado 2026-10-16)

El firewall virtual del panel bloquea por defecto todo el tráfico entrante. Reglas vigentes, todas para `0.0.0.0/0` y `::/0`:

| Puerto | Protocolo | Observación |
|---|---|---|
| 80, 443 | TCP | Web, HTTPS y desafío ACME de Let's Encrypt. Necesarios |
| puerto SSH no estándar | TCP | SSH. Abierto a todos (ver abajo) |

Se cerraron el 2026-10-16 dos reglas heredadas del proyecto anterior:

| Puerto | Protocolo | Motivo |
|---|---|---|
| 587 | TCP | Nada escuchaba en ese puerto. El correo saliente de la app no necesita una regla de entrada |
| 443 | UDP | Solo sirve para HTTP/3 y la configuración de Nginx no lo usa |

El puerto 22 y el 3306 no tienen regla, es decir, están bloqueados desde afuera (se verificó el 3306 con `nc`).

**SSH no se limita a la IP del operador.** El despliegue (#593) entra por SSH desde GitHub Actions y los runners hospedados de GitHub usan IPs que cambian y no se pueden fijar. El puerto se protege con clave SSH, el comando forzado del usuario `deploy` y fail2ban. Limitarlo exigiría un runner propio o abrir y cerrar la regla en cada despliegue.

## 3. Pendiente de documentar

Se completa a medida que avancen los sub-issues de #380:

- Compose de producción (#589): ejecutar en el VPS los pasos 3 a 5 de la renovación por `webroot`, completar `/opt/menta/.env` (el archivo existe, vacío; los nombres están en `production.env.example`) y documentar el resultado del primer arranque. El endurecimiento de SSH quedó descartado (ver "Decisiones diferidas").
- Despliegue y rollback (#593, sección 2.10): instalar `deploy.sh`, el primer despliegue a mano con un tag `-rc`, restringir la clave al comando forzado, y cargar en el Environment `production` de GitHub los secretos que usa `release.yml` (#592; ver `docs/16-CICD-PIPELINE.md`, "Configuración en GitHub"). Anotar acá el resultado de cada paso.
- Backups y monitoreo (#594).
