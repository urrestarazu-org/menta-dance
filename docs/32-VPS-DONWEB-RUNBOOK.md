# Runbook del VPS de DonWeb

Bitácora operativa del cloud server de DonWeb donde se despliega Menta Dance (#380). Cada sección registra lo que se ejecutó en el servidor, con fecha y resultado, para que se pueda repetir o auditar.

## Metadata

- **Servidor**: `Menta-server-cloud`, Ubuntu 24.04, 2 vCPU, 4 GB de RAM, ~35 GB de SSD, Docker preinstalado
- **IP y puerto SSH**: se consultan en el panel de DonWeb (Cloud Servers → Software y Accesos); no se guardan en el repositorio
- **Dominios**: `mentadance.com` (BFF, con `www` como CNAME) y `api.mentadance.com` (API); los tres nombres apuntan al VPS
- **Vencimiento del plan**: 2027-10-22
- **Estado**: proyecto anterior retirado (#595); VPS aprovisionado (#590), certificado emitido (vence 2027-01-14); renovación por `webroot` decidida (se aplica en #589); `/opt/menta/.env` creado vacío; firewall del panel revisado; endurecimiento de SSH descartado por ahora. El compose de producción (#589), `deploy.sh` (#593) y `backup.sh` (#594) están en el repositorio y falta instalarlos y probarlos en el servidor (secciones 2.9, 2.10 y 2.11). Hecho en el servidor el 2026-10-17: login a Docker Hub y bucket, tokens y credenciales de R2 probados con cifrado (#610 hace el seguimiento de lo que falta)

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

**Baneo del operador (2026-10-18).** Al hacer el primer despliegue a mano, `ssh` probó claves de más y cayó a pedir contraseña (el usuario `deploy` no tiene, ver 2.5): tres fallos en 10 minutos bastan para que fail2ban banee la IP del operador una hora. Con `banaction = nftables` el bloqueo se ve como `Connection refused` (no como un tiempo de espera agotado), y desde SSH no se puede entrar ni como `mentaadmin`. Se resolvió desde la consola web del panel de DonWeb, como `root`:

```bash
fail2ban-client status sshd        # "Banned IP list" confirma la causa (no pegar el resultado en el repositorio)
fail2ban-client set sshd unbanip <IP-DEL-OPERADOR>
```

`fail2ban-client unban --all` **no existe** en la versión instalada (`Invalid command`); se usa `set sshd unbanip`. Mientras se opera por SSH conviene acortar el baneo sin tocar `jail.local`: `fail2ban-client set sshd bantime 900` rige solo hasta que se reinicie fail2ban, y para volver a una hora sin reiniciar es `fail2ban-client set sshd bantime 3600`. Para no caer en el baneo, siempre `-i <clave> -o IdentitiesOnly=yes` (ver 2.10) y cortar con Ctrl+C ante el primer pedido de contraseña.

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

**Aplicado el 2026-10-18**, después del primer despliegue, como `root` en el VPS: se comprobó que `authorized_keys` tuviera una sola clave, se guardó una copia de la parte pública en `/root/authorized_keys.deploy.bak` y se antepuso `command="/opt/menta/deploy.sh",no-pty,no-port-forwarding ` a esa línea. Verificación desde la máquina del operador:

```bash
ssh -i ~/.ssh/<clave-de-deploy> -o IdentitiesOnly=yes -p <PUERTO> deploy@<HOST> "ls /"
```

No lista nada: responde `[ERROR] Digest de la API inválido…`, porque el comando pedido llega a `deploy.sh` como argumento en vez de ejecutarse. Para deshacerlo, restaurar la copia del respaldo en `authorized_keys`.

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

Verificado el 2026-10-18, antes del primer despliegue: como `deploy`, `grep -c 'ghcr.io' ~/.docker/config.json` dio `1`. Al desplegar, `docker compose pull` bajó las dos imágenes de GHCR sin errores de autenticación.

#### Docker Hub (límite de descargas anónimas)

**Qué pasó (2026-10-17).** La primera descarga de `rclone/rclone` en el VPS falló con `toomanyrequests: You have reached your unauthenticated pull rate limit`. Según la documentación de Docker Hub, sin iniciar sesión el límite es de **100 descargas cada 6 horas por dirección IPv4 (o subred IPv6 /64)**, y de **200 por cuenta** gratuita autenticada. En un VPS la IP puede estar compartida o ya consumida, así que el límite se agota sin haber descargado nada. Importa más allá de `rclone`: el compose de producción baja `mysql`, `redis` y `nginx` de Docker Hub en el primer despliegue, y podría fallar igual.

**Solución.** Iniciar sesión en Docker Hub desde `deploy` con una cuenta gratuita y un **token de acceso personal de solo lectura** (*Account settings → Personal access tokens*; los nombres de los menús pueden variar). Con una cuenta creada con Google no hay contraseña para la terminal: se usa el nombre de usuario de Docker (Docker ID) y el token como contraseña.

```bash
read -rs -p "Token de Docker Hub: " T; echo
echo "$T" | docker login -u <DOCKER-ID> --password-stdin
unset T
chmod 600 ~/.docker/config.json
```

Resultado: `Login Succeeded` (con el mismo aviso de credenciales sin cifrar que GHCR). Las credenciales de Docker Hub y de GHCR conviven en el mismo `~/.docker/config.json` de `deploy`: para revocar una hay que borrar su entrada o hacer `docker logout` del registro. Se hizo el 2026-10-17; el nombre de usuario no se registra en este documento.

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

#### Renovación: pasar de `standalone` a `webroot` (aplicado el 2026-10-18)

`--standalone` levanta su propio servidor en el puerto 80, que a partir del primer despliegue ocupa Nginx, así que la renovación fallaría en silencio hacia el 2026-12-15 (30 días antes del vencimiento). Se eligió `webroot` porque no corta el servicio; la alternativa, `standalone` con hooks que detienen y levantan Nginx, cuesta unos segundos de caída por renovación.

Dos cambios sobre lo que hoy trae el repositorio:

- El Nginx de `infra/docker/nginx/` espera los certificados como archivos en `./certs/`. En producción se monta `/etc/letsencrypt` en solo lectura y `ssl_certificate` apunta a `/etc/letsencrypt/live/mentadance.com/`, así Nginx siempre ve el certificado vigente sin copiarlo.
- El Nginx de producción tiene que servir el desafío ACME.

Pasos de #589. Los dos primeros ya están en el repositorio (`infra/docker/prod/`); los otros se ejecutan en el VPS:

1. `infra/docker/prod/nginx/nginx.conf` tiene la `location /.well-known/acme-challenge/` en el puerto 80, con `root /var/www/certbot`.
2. `infra/docker/prod/docker-compose.prod.yml` monta `/var/www/certbot` y `/etc/letsencrypt` en solo lectura en el contenedor de Nginx.
3. El directorio del desafío y el hook de renovación, **antes del primer despliegue** (hecho el 2026-10-18). El hook recarga Nginx; si Nginx no está corriendo, no debe hacer fallar la renovación. Queda con dueño `root` y permisos `755`:

   ```bash
   sudo mkdir -p /var/www/certbot
   sudo tee /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh >/dev/null <<'EOF'
   #!/bin/sh
   docker exec menta-nginx nginx -s reload || true
   EOF
   sudo chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
   ```

4. El cambio de `standalone` a `webroot`, **después del primer despliegue, con Nginx corriendo** (hecho el 2026-10-18). `certbot reconfigure` hace una renovación de prueba contra el entorno de staging, y esa prueba necesita que Nginx ya sirva `/var/www/certbot` en el puerto 80; antes del despliegue el puerto está libre y el desafío no tiene quién lo responda:

   ```bash
   sudo certbot reconfigure --cert-name mentadance.com \
     --authenticator webroot --webroot-path /var/www/certbot
   sudo certbot renew --dry-run
   ```

   Resultado: `Successfully updated configuration` y `all simulated renewals succeeded`.

5. El hook se prueba a mano, porque `certbot renew --dry-run` **no ejecuta los deploy hooks** (según la documentación de certbot). Resultado el 2026-10-18: `hook ok` y `menta-nginx` sigue `Up (healthy)` sin reiniciarse (solo recargó la configuración):

   ```bash
   sudo /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh && echo "hook ok"
   docker ps --filter name=menta-nginx --format '{{.Names}} {{.Status}}'
   ```

Si el paso 4 se omite, la renovación falla sin avisar hasta que el certificado vence. Lo único que se entera es el correo de avisos de Let's Encrypt.

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

**Ejecutado el 2026-10-17 (VPS): `/opt/menta/.env`** (seguimiento en #610).

- `/opt/menta` es de `deploy` y `mentaadmin` no puede entrar: todo lo que esté adentro se hace con `sudo -iu deploy` (o con `sudo` puntual).
- El archivo se creó como `deploy` con `set -C` (no pisa un archivo existente) y `umask 077`, y quedó en modo `600`. Sin comillas en los valores. Los secretos se generan en el propio servidor con `openssl` y no se pegan en ningún otro lado. Formato que exige la aplicación y comando con el que se generó cada una:

  | Variable | Formato | Generación |
  |---|---|---|
  | `MYSQL_ROOT_PASSWORD`, `MYSQL_APP_PASSWORD` | arbitrario, sin comillas ni espacios | `openssl rand -hex 24` |
  | `AUTH_JWT_BASE64_SECRET` | base64, al menos 32 bytes | `openssl rand -base64 48` |
  | `AUTH_ACTIVATION_DELIVERY_KEY`, `AUTH_PASSWORD_RESET_DELIVERY_KEY` | base64 de exactamente 32 bytes (AES-256) | `openssl rand -base64 32` |
  | `BILLING_WEBHOOK_MERCADOPAGO_HMAC_SECRET`, `BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET` | arbitrario | `openssl rand -hex 32` |

- Correo: **EnvialoSimple** por SMTP, puerto **587** con `SMTP_AUTH=true` y `SMTP_STARTTLS=true`. El compose solo mapea STARTTLS (no hay variable para TLS implícito, puerto 465), así que el puerto 587 es el único que sirve.
- Quedan vacías a propósito cinco variables: `BILLING_MERCADOPAGO_ACCESS_TOKEN` (sin ese token no hay cobros) y los cuatro datos de transferencia bancaria (CBU, alias, titular, CUIT). Se cargan antes de habilitar pagos reales.
- `API_DIGEST` y `BFF_DIGEST` no están: las escribe `deploy.sh` en cada despliegue.
- **Cómo revisar el archivo sin exponer valores:** `sed 's/=.*/=…/' /opt/menta/.env` lista los nombres, y `grep -cE '^[A-Z_]+=$' /opt/menta/.env` cuenta las vacías (deben ser 5). No usar `grep '=$'` a secas: las claves en base64 con relleno terminan en `=` y se imprimirían enteras. Pasó ese día con las dos claves de entrega; como la API todavía no había arrancado, se regeneraron en el momento y no hubo exposición real.

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

**Instalación (hecha el 2026-10-17).** Como `mentaadmin`, con el `deploy.sh` del commit que se va a desplegar copiado al servidor:

```bash
sudo install -m 750 -o deploy -g deploy deploy.sh /opt/menta/deploy.sh
```

Se verificó que el instalado es idéntico al del repositorio: `sha256sum deploy.sh` en la máquina del operador y `sudo sha256sum /opt/menta/deploy.sh` en el servidor dan el mismo hash, y `sudo stat -c '%a %U:%G' /opt/menta/deploy.sh` da `750 deploy:deploy`. Con `sudo` porque `mentaadmin` no puede entrar a `/opt/menta`.

Pasos para el primer despliegue, en este orden:

1. Completar `/opt/menta/.env` (sección 2.9) y hacer el login a GHCR de `deploy` (sección 2.7).
2. Publicar un tag con sufijo (`v0.2.0-rc.N`): crea las imágenes y la pre-release sin desplegar y deja los digests en las notas.
3. Desplegar a mano esa primera vez, desde la máquina del operador y con la clave de despliegue todavía sin comando forzado. La clave se pasa explícitamente y con `IdentitiesOnly`, para que `ssh` no pruebe otras y fail2ban no banee al operador (sección 2.4). Desde macOS, `COPYFILE_DISABLE=1` y `--no-xattrs` evitan que `tar` agregue entradas `._*` (AppleDouble) que el validador del script rechaza:

   ```bash
   COPYFILE_DISABLE=1 tar --no-xattrs -czf - -C infra/docker prod nginx/conf/conf.d \
     | ssh -i ~/.ssh/<clave-de-deploy> -o IdentitiesOnly=yes -p <PUERTO> deploy@<HOST> \
       /opt/menta/deploy.sh <digest-api> <digest-bff>
   ```

4. Comprobar los servicios y completar la renovación de certbot por `webroot` (sección 2.8).
5. Recién entonces restringir la clave en `/home/deploy/.ssh/authorized_keys` al comando forzado (sección 2.5) y cargar los secretos del Environment `production` (ver `docs/16-CICD-PIPELINE.md`, "Configuración en GitHub").

Con el comando forzado, la clave de despliegue ya no abre una shell: la administración se hace como `mentaadmin` y `sudo -iu deploy`.

#### Primer despliegue (ejecutado el 2026-10-18)

Se desplegó `v0.2.0-rc.2` a mano, con los pasos 1 a 4 de arriba. El camino hasta ahí, con lo que falló:

1. **`v0.2.0-rc.1`: Trivy rechazó las imágenes.** El job `publish` de `release.yml` cortó antes de publicar nada en GHCR por CVE-2026-77214 (HIGH) en `libexpat` 2.8.5-r0, que viene en el Alpine 3.24 de la imagen base y entra por `fontconfig`. Todavía no hay una imagen de Temurin sobre Alpine 3.24 con `libexpat` 2.9.0. Se pasaron los Dockerfiles a `eclipse-temurin:21.0.12.1_1-jre-alpine-3.23` (#612, PR #613) y se volverá a 3.24 cuando Temurin publique con `libexpat` >= 2.9.0. El tag `v0.2.0-rc.1` quedó sin imágenes ni release.
2. **`v0.2.0-rc.2`: un test intermitente.** El primer intento falló en `:api:app:test` (`PaymentExpirySweepIntegrationTest`, `a_stale_unproven_payment_expires_and_cancels_the_subscription`); al reintentar solo el job fallido (`gh run rerun --failed`) pasó con el mismo código. La causa no se investigó.
3. **El release publicó** las dos imágenes por digest y la pre-release; el job `deploy` quedó omitido porque el tag tiene sufijo.
4. **Autenticación SSH.** El primer intento no usó la clave de despliegue, `ssh` cayó a pedir contraseña (el usuario `deploy` no tiene) y fail2ban baneó la IP del operador (sección 2.4).
5. **Paquete rechazado.** `deploy.sh` respondió `Ruta inesperada en la configuración del stack: ._prod` y terminó sin tocar nada: el `tar` de macOS había agregado una entrada AppleDouble. Se resolvió con `COPYFILE_DISABLE=1` y `--no-xattrs`. Los avisos `Ignoring unknown extended header keyword 'LIBARCHIVE.xattr.com.apple.provenance'` que imprime el `tar` GNU del servidor son inofensivos.
6. **MySQL no llegó a `healthy` en la primera inicialización.** `docker compose up` terminó con `dependency failed to start: container menta-mysql is unhealthy` y `deploy.sh` con `[ERROR] docker compose up falló`. MySQL estaba bien (`oom=false`, sin errores en los logs): inicializar el directorio de datos, crear la base y el usuario y reiniciar tardó unos 70 segundos, y el healthcheck del compose (`start_period: 30s` más 5 reintentos de 10 s) se agota antes. Como el volumen quedó inicializado, el segundo arranque no repite esa inicialización.
7. **Reintento: éxito.** Mismo comando. Esta vez hizo el `mysqldump` previo (`/opt/menta/backups/menta-<fecha>.sql.gz`), bajó las imágenes, levantó el stack y terminó con `Stack saludable tras 0s` y `Deploy completado`.

Verificación desde fuera, el mismo día: `https://mentadance.com/` responde 200, `https://www.mentadance.com/` y `http://mentadance.com/` redirigen con 301 a `https://mentadance.com/`, `https://api.mentadance.com/actuator/health` responde 200 con `status: UP`, y el certificado es de Let's Encrypt y vence el 2027-01-14.

#### Comando forzado y Environment `production` (ejecutado el 2026-10-18)

1. **Comando forzado** en la clave de despliegue, verificado (sección 2.5).
2. **Huella del servidor.** Antes de guardar `DEPLOY_KNOWN_HOSTS` se comparó la huella de la clave de host del servidor (`ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`, en el VPS) con la que presenta el puerto real (`ssh-keyscan -p <PUERTO> -t ed25519 <HOST> | ssh-keygen -lf -`, en la máquina del operador). Coincidían. Esa línea es lo único que impide aceptar un servidor falso, porque el workflow usa `StrictHostKeyChecking=yes`.
3. **Environment `production` creado, sin reglas de protección.** Agregar al operador como *required reviewer* falló con `HTTP 422` (`Failed to create the environment protection rule. Please ensure the billing plan supports the required reviewers protection rule`): el repositorio es privado y el plan no lo permite, aunque la API reporte el plan de la organización como `team`. **Pendiente: agregar el *required reviewer* cuando el repositorio sea público**, para que el deploy de un tag estable espere una aprobación manual. Hasta entonces, un tag estable desde `main` se despliega sin aprobación.
4. **Cinco secretos cargados** con `gh secret set <NOMBRE> --env production` (el valor se pega en el prompt, nunca como argumento): `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` y `DEPLOY_KNOWN_HOSTS`. `gh secret list --env production` los muestra con su fecha de actualización y sin los valores.

Todavía pendiente: desplegar un tag estable desde `main` por `release.yml` y probar el rollback.

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

El usuario de la aplicación conserva sus permisos porque son sobre el nombre de la base. Este backup (el previo a cada deploy) es solo de la base y solo local; el diario con copia fuera del servidor, que incluye los comprobantes de transferencia, es el de la sección 2.11.

**Errores comunes:**

- `No se pudieron descargar las imágenes`: venció o falta el login a GHCR del usuario `deploy` (sección 2.7; el token dura 12 meses). El estado del servidor no cambia.
- `Hay otro deploy en curso`: otro `deploy.sh` tiene el lock (`/opt/menta/deploy.lock`); el lock se libera solo cuando ese proceso termina.
- `AVISO: ... difiere del deploy.sh de este release`: hay una versión nueva del script; reinstalarlo con el `install` de arriba. El deploy continúa con la versión instalada.
- `mysqldump falló` o `backup ... incompleto`: no se despliega nada. Ver el espacio en disco (`df -h /opt/menta`) y que `menta-mysql` esté `healthy`.
- `Ruta inesperada en la configuración del stack: ._...`: el `tar` es de macOS y agregó entradas AppleDouble; usar `COPYFILE_DISABLE=1 tar --no-xattrs` (paso 3 de arriba). No se tocó nada en el servidor.
- `container menta-mysql is unhealthy` en el primer `compose up`: la inicialización del volumen nuevo tarda más que la ventana del healthcheck. Con `docker inspect menta-mysql` en `healthy` y sin errores en `docker logs menta-mysql`, alcanza con repetir el mismo comando de despliegue.
- `Permission denied` pidiendo contraseña y luego `Connection refused`: `ssh` no usó la clave de despliegue y fail2ban baneó la IP (sección 2.4). Pasar `-i <clave> -o IdentitiesOnly=yes`.

### 2.11 Backups diarios y monitoreo (#594)

Con un único VPS, un backup que vive en el mismo servidor no protege de perderlo. `infra/docker/prod/backup.sh` hace cada día un `mysqldump` y un `tar` de los comprobantes de transferencia (`payment_proofs_data`, que no se pueden regenerar), los copia **cifrados** a un almacenamiento S3-compatible fuera del VPS y avisa a un monitor externo. Las pruebas de lógica están en `infra/docker/prod/backup.test.sh` y corren en CI.

**Archivos en el servidor:**

| Ruta | Contenido |
|---|---|
| `/opt/menta/backup.sh` | El script (se instala desde `/opt/menta/stack/prod/`, que `deploy.sh` actualiza en cada release) |
| `/opt/menta/backup.env` | Credenciales del almacenamiento, claves de cifrado y retención (`chmod 600`, dueño `deploy`; los nombres están en `infra/docker/prod/backup.env.example`) |
| `/opt/menta/backups/daily/` | `menta-<fecha>.sql.gz` y `proofs-<fecha>.tar.gz`; se conservan los últimos 7 de cada uno |
| `/etc/systemd/system/menta-backup.{service,timer}` | Corre `backup.sh` todos los días a las 06:30 UTC (03:30 en Argentina), con `Persistent=true` por si el servidor estaba apagado |

**Cómo funciona.** Verifica el volcado (`gzip -t` y la línea `Dump completed`), archiva los comprobantes desde el contenedor de la API, copia ambos con `rclone` y comprueba que el almacenamiento externo tenga los dos archivos con el mismo tamaño. Recién entonces borra lo que supere los 30 días fuera del servidor, sin tocar nunca el backup que acaba de subir. `rclone` corre como la imagen oficial con versión y digest fijos (igual que Trivy), así que no hay nada que instalar. El cifrado es del lado del cliente (`rclone crypt`, incluye los nombres de archivo): el proveedor guarda solo datos ilegibles. Si algo falla, el script sale con error y llama a `BACKUP_PING_URL/fail`; si deja de correr, el monitor avisa porque no recibe el ping diario.

**Relación con la copia de seguridad del panel de DonWeb.** El plan incluye una copia semanal del servidor entero (el panel muestra las últimas cuatro, del 2026-09-24 al 2026-10-15). No reemplaza a este backup, porque cubre otra cosa:

| | Copia del panel de DonWeb | `backup.sh` |
|---|---|---|
| Frecuencia | Semanal | Diaria |
| Qué guarda | El servidor entero (sistema, Docker, configuración y datos) | Solo los datos: la base y los comprobantes |
| Dónde vive | En DonWeb, en la misma cuenta que el servidor | En otro proveedor, cifrada |
| Pérdida máxima de datos | Hasta 7 días | Hasta 24 horas |
| Sirve para | Recuperar el servidor si se rompe el sistema | Recuperar los datos aunque se pierda la cuenta o el proveedor |

Con una semana de ventana se perderían pagos y comprobantes que no se pueden regenerar, y la copia del panel comparte cuenta y proveedor con el servidor. No se sabe cómo toma DonWeb la copia, así que no se da por hecho que la base quede consistente como con un `mysqldump`. Se mantiene el backup diario: cuesta centavos y acorta la pérdida máxima de 7 días a 24 horas.

**Qué se probó (en local, sin tocar el VPS).** Con MySQL 8.4.11, la API y un servidor S3 local (el propio `rclone serve s3`) reales, `backup.sh` generó el volcado (las 33 tablas del esquema más una tabla de control creada para la prueba) y el archivo de comprobantes, ambos con modo 600, y los subió. En el almacenamiento los nombres se ven cifrados y el objeto crudo no contiene texto legible (0 apariciones de `CREATE TABLE`). Después, en una base MySQL vacía: se descargó y descifró el backup, se restauró (34 tablas, la fila de control incluida), se recuperó el comprobante y **la API arrancó contra la base restaurada con `status: UP`** y las 27 migraciones de Flyway en su historia. Falta repetir el simulacro con el almacenamiento real y el VPS (ver "Pendiente de ejecutar").

**Hallazgo corregido en este issue.** El directorio de comprobantes no existía en la imagen de la API: Docker crea el punto de montaje del volumen con dueño `root` y la API corre como `menta` (uid 1000), así que **no podía escribir ningún comprobante** (se reprodujo: `touch: Permission denied`). `Dockerfile.api` ahora crea `/var/menta-dance/payment-proofs` con dueño `menta`: un volumen nuevo hereda dueño y permisos de la ruta de la imagen. Vale para volúmenes nuevos; si ya existiera un `payment_proofs_data` creado con la imagen anterior, hay que corregir el dueño a mano (`docker exec -u root menta-api chown menta:menta /var/menta-dance/payment-proofs`).

**Puesta en marcha en el VPS** (seguimiento en #610). Los pasos 1 y 2 están hechos y probados desde el VPS (2026-10-17); del 3 al 6 falta ejecutar. Lo ejecutado y sus resultados están en "Ejecutado el 2026-10-17", más abajo.

1. **Almacenamiento externo** (hecho el 2026-10-17: Cloudflare R2). Crear un bucket en un proveedor S3-compatible y una clave de acceso limitada a ese bucket. Opción recomendada: **Cloudflare R2**; alternativa: Bunny Storage (más abajo). Cualquier otro proveedor S3-compatible también sirve, porque solo cambian las variables `RCLONE_CONFIG_OFFSITE_*`.

   **Cloudflare R2** (recomendado). Los pasos y las cifras salen de la documentación oficial de Cloudflare (consultada el 2026-10-17); los nombres de los botones del panel pueden variar.

   - **Costo.** Almacenamiento *Standard*: 10 GB-mes gratis y después $0,015/GB-mes; 1 millón de operaciones de clase A (escrituras y listados) y 10 millones de clase B (lecturas y `HEAD`) gratis al mes; salida de datos gratis; `DeleteObject` no se cobra. Una corrida del backup hace unas 20 operaciones, así que son unas 600 al mes. El almacenamiento es aproximadamente (tamaño de los comprobantes) × 30 copias: se pasa de los 10 GB gratis con unos 330 MB de comprobantes, y cada GB de más cuesta $0,015 al mes. En el panel, la suscripción a R2 se agrega a la cuenta con un método de pago y solo se cobra si se superan esos límites.
   - **Bucket.** Crear un bucket (el del proyecto se llama `menta-dance-backups`; creado el 2026-10-17 con clase *Estándar* y acceso público *Deshabilitado*) con ubicación automática y **sin acceso público** (no habilitar el dominio `r2.dev` ni un dominio personalizado). No elegir una jurisdicción (UE, EE. UU., FedRAMP) salvo que se necesite: obliga a usar otro endpoint.
   - **Token.** En *R2 object storage → Overview → API Tokens → Manage*, crear un **Account API token** (los de usuario dejan de funcionar si se quita al usuario de la cuenta) con permiso **Object Read & Write**, limitado **solo a ese bucket**. El panel muestra el *Access Key ID* y el *Secret Access Key* **una sola vez**: copiarlos directo a `/opt/menta/backup.env`, nunca al repositorio ni a un chat. Crear también un segundo token **Object Read only** para el simulacro y para restaurar desde otra máquina: no puede borrar ni pisar backups. Ver abajo "Cómo obtener el ID y el secreto" si el token se creó desde la pantalla genérica de *Tokens de API de cuenta*.
   - **Endpoint.** `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`; el ID de la cuenta está en el panel de Cloudflare.
   - **Variables** de `/opt/menta/backup.env`:

     ```
     RCLONE_CONFIG_OFFSITE_PROVIDER=Cloudflare
     RCLONE_CONFIG_OFFSITE_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
     RCLONE_CONFIG_OFFSITE_ACCESS_KEY_ID=<Access Key ID>
     RCLONE_CONFIG_OFFSITE_SECRET_ACCESS_KEY=<Secret Access Key>
     RCLONE_CONFIG_OFFSITE_ACL=private
     RCLONE_CONFIG_OFFSITE_NO_CHECK_BUCKET=true
     RCLONE_CONFIG_OFFSITECRYPT_REMOTE=offsite:menta-dance-backups/menta
     ```

     `NO_CHECK_BUCKET=true` es obligatorio con un token de permisos de objeto: la guía de `rclone` de Cloudflare advierte que sin él se obtienen errores. La región se deja vacía (R2 usa `auto`).
   - **Compatibilidad con `backup.sh`.** R2 implementa todas las operaciones que usa `rclone` en el script (`PutObject`, `HeadObject`, `ListObjectsV2`, `DeleteObject`, y también el borrado por lotes y el multipart). Cloudflare indica que la implementación de la API S3 sigue en progreso, así que el primer backup y el simulacro de restauración (pasos 4 y 6) siguen siendo la prueba real.

   **Alternativa: Bunny Storage**, que ya se usa para los videos. Más cara en la práctica ($0,02/GB por las dos regiones obligatorias) y con la compatibilidad S3 en *Public preview*:

   - Crear una **zona de almacenamiento nueva y propia** (por ejemplo `menta-backups`; el nombre admite letras, números y guiones, mínimo 4 caracteres), separada de la de los videos, para que la clave del backup no pueda tocar los videos ni al revés. Activar *S3 Compatibility* **al crearla**: según la documentación de Bunny no se puede activar después en una zona existente (por eso la zona de los videos no sirve).
   - **No conectarle ninguna Pull Zone (CDN)**: así los backups nunca quedan servidos por una URL pública.
   - Tier *Standard*. Bunny exige una **región principal y al menos una de réplica** (por ejemplo Los Angeles y Frankfurt); las réplicas no se pueden quitar después de crear la zona. Al crearla (2026-10-17) el panel mostraba $0,02/GB al mes con dos regiones y $0,01/GB con una; comprobar el precio vigente. Para unos pocos MB son centavos.
   - **Datos de acceso** (pestaña *Access* de la zona, según la documentación de Bunny): *Access Key ID* = nombre de la zona, *Secret Access Key* = contraseña de la zona, y endpoint `https://<región>-s3.storage.bunnycdn.com`, donde `<región>` es el código de la región **principal** (`la` para Los Angeles, `de` Frankfurt, `ny` New York, etc.). Van en `/opt/menta/backup.env`, nunca en el repositorio ni en un chat:

     ```
     RCLONE_CONFIG_OFFSITE_PROVIDER=Other
     RCLONE_CONFIG_OFFSITE_ENDPOINT=https://la-s3.storage.bunnycdn.com
     RCLONE_CONFIG_OFFSITE_REGION=la
     RCLONE_CONFIG_OFFSITE_ACCESS_KEY_ID=<nombre de la zona>
     RCLONE_CONFIG_OFFSITE_SECRET_ACCESS_KEY=<contraseña de la zona>
     RCLONE_CONFIG_OFFSITECRYPT_REMOTE=offsite:<nombre de la zona>/menta
     ```

     El nombre de la zona hace de bucket. La pestaña *Access* también ofrece una **contraseña de solo lectura**: usarla para restaurar desde otra máquina o para el simulacro, así esa máquina no puede borrar ni pisar backups.
   - **Compatibilidad con `backup.sh`.** La documentación de Bunny tiene un ejemplo con `rclone` y `provider=Other`. Se comprobó en local qué pedidos S3 hace `rclone` en las tres operaciones del script (copiar, verificar por tamaño y borrar por antigüedad): `HEAD` de objeto, `PUT` de objeto, listado y `DELETE` de un objeto. Las cuatro están en la lista de operaciones que soporta Bunny. No usa el borrado por lotes (`DeleteObjects`, que Bunny no soporta), no crea buckets ni usa operaciones de multipart para archivos chicos. Bunny no ofrece políticas de ciclo de vida ni cifrado del lado del servidor: la retención la aplica el propio script y el cifrado es el de `rclone crypt`. Tampoco guarda metadatos propios (`x-amz-meta-*`), así que la antigüedad de cada archivo sale de su fecha de subida.
   - Por ser *Public preview*, el primer backup y el simulacro de restauración con la zona real (pasos 4 y 6) son obligatorios antes de confiar en ella: lo único que no se pudo probar es la conexión con Bunny en sí (firma de las peticiones, región y comportamiento real del servicio). Si diera problemas, cambiar a otro proveedor son solo las variables `RCLONE_CONFIG_OFFSITE_*` de `backup.env`.
2. **Claves de cifrado** (hecho el 2026-10-17). Generar dos claves largas, guardarlas **en texto plano en un gestor de contraseñas fuera del servidor** (sin ellas los backups no se pueden descifrar) y ofuscarlas para `rclone`:

   ```bash
   read -rs -p "Clave: " K; echo
   docker run --rm rclone/rclone:1.75.2 obscure "$K"
   unset K
   ```

   `read -rs` evita que la clave quede en pantalla o en el historial del shell. Se repite para la segunda clave.

3. **Instalar los archivos**, como `mentaadmin` (después de un deploy que haya dejado `/opt/menta/stack/prod/` actualizado). `backup.env` ya se creó a mano el 2026-10-17, porque ese deploy todavía no se hizo; faltan `backup.sh` y las unidades de systemd:

   ```bash
   sudo install -m 750 -o deploy -g deploy /opt/menta/stack/prod/backup.sh /opt/menta/backup.sh
   sudo cp /opt/menta/stack/prod/systemd/menta-backup.service /opt/menta/stack/prod/systemd/menta-backup.timer /etc/systemd/system/
   ```

   **No volver a instalar `backup.env.example` sobre `/opt/menta/backup.env`**: `install` lo pisaría y se perderían las claves de cifrado ofuscadas. El `backup.env` se crea una sola vez (ver la tabla de abajo, 2026-10-17); `BACKUP_PING_URL` se agrega después, cuando exista el monitor *heartbeat*. Si está vacío, `backup.sh` no avisa y el backup funciona igual. El formato del archivo es el de `docker --env-file`: una variable por línea, sin comillas.

4. **Primer backup a mano**, antes de activar el timer:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl start menta-backup.service
   journalctl -u menta-backup.service -n 30 --no-pager
   ```

   Debe terminar con `Backup completado`. Los avisos `Config file ... not found` y `No common hash found` de `rclone` son normales: la configuración viene de variables de entorno y un remoto cifrado no tiene hashes comparables, por eso se compara el tamaño.
5. **Activar el timer** y comprobar la próxima ejecución:

   ```bash
   sudo systemctl enable --now menta-backup.timer
   systemctl list-timers menta-backup.timer
   ```

6. **Simulacro de restauración en el VPS** (el criterio de aceptación pide hacerlo al menos una vez): bajar el último backup del almacenamiento externo, restaurarlo en una base vacía y levantar la API contra ella, como se hizo en local. Anotar acá la fecha y el resultado.

**Ejecutado el 2026-10-17 (VPS, usuario `deploy`):**

| Paso | Resultado |
|---|---|
| Bucket `menta-dance-backups` en R2 (clase *Estándar*, acceso público *Deshabilitado*) y dos tokens de cuenta: escritura (*Workers R2 Storage Bucket Item* con Read y Edit, vence al año) y solo lectura | Creados. Los valores, en el gestor de contraseñas; el de solo lectura no va en el servidor |
| Dos claves de cifrado (`openssl rand -base64 33`) y su versión ofuscada con `rclone obscure` | Claves en el gestor de contraseñas; las ofuscadas, en `backup.env` |
| `/opt/menta/backup.env` creado a mano (`install -m 600 -o deploy -g deploy`), con las variables de R2 y del remoto cifrado | Creado |
| `rclone lsf offsite:menta-dance-backups` | Bucket vacío, sin errores: las credenciales son correctas |
| `rcat`, `lsf` y `deletefile` de un archivo de prueba en `offsite:` | Subió, listó y borró sin errores |
| Mismo ciclo por `offsitecrypt:` (más `cat` y un `lsf -R` sobre el remoto sin cifrar) | `cat` devolvió el texto original; R2 solo muestra nombres ilegibles; el aviso `--checksum ... no hashes in common` es normal en un remoto cifrado |

**Ejecutado el 2026-10-19 (VPS, como `root`):**

| Paso | Resultado |
|---|---|
| Instalar `backup.sh` y las unidades de systemd, `daemon-reload` (sin tocar `backup.env`) | Hecho |
| `systemctl start menta-backup.service` (primer backup real) | `Backup completado (20261019T133346Z)` en unos 30 s. `rclone check` posterior: `0 differences found`, `2 matching files` |
| `systemctl enable --now menta-backup.timer` | Activo. Próxima ejecución 06:30 UTC más hasta 10 min de `RandomizedDelaySec` |
| Archivos locales | Dump de 5,5 KB y tar de comprobantes de 91 bytes (vacío: aún no hay comprobantes), ambos con modo 600 y `gzip -t` OK |
| **Simulacro de restauración.** Con `lsf` y `copy` de `offsitecrypt:daily` se bajó y descifró el backup a `/root/restore` | Los dos archivos descargados son idénticos byte a byte (`cmp`) a los locales |
| Restauración del dump en una base aparte `menta_drill` dentro del MySQL de producción (el dump no trae `USE` ni `CREATE DATABASE`, así que no toca `menta`) | `menta` y `menta_drill`: 33 tablas cada una y 27 migraciones de Flyway (`installed_rank` máximo 27). El tar de comprobantes abre bien |
| Limpieza: `DROP DATABASE menta_drill` y borrado de `/root/restore` | `SHOW DATABASES` ya no lista `menta_drill` |

Alcance del simulacro: se usó el `backup.env` del servidor (token de escritura). No se probó el token de solo lectura ni la restauración desde otra máquina con las claves del gestor de contraseñas, ni se levantó la API contra la base restaurada (eso sí se probó en local, ver arriba).

**Cómo obtener el ID y el secreto.** Si el token se creó desde la pantalla genérica de *Tokens de API de cuenta* (el valor empieza con `cfat_`), `rclone` necesita dos datos que se derivan del valor: el **Access Key ID** es el `id` del token (32 caracteres, lo devuelve `tokens/verify`) y el **Secret Access Key** es el **SHA-256 del valor del token** (64 caracteres), según la documentación de Cloudflare. El valor del token no va en ninguna variable de `rclone`.

```bash
read -rs -p "Valor del token: " T; echo
curl -s "https://api.cloudflare.com/client/v4/accounts/<ACCOUNT_ID>/tokens/verify" \
  -H "Authorization: Bearer $T" | jq -r .result.id      # Access Key ID
printf '%s' "$T" | sha256sum | cut -d' ' -f1             # Secret Access Key (en macOS: shasum -a 256)
unset T
```

Para comprobar el archivo sin mostrar secretos:

```bash
sudo -u deploy awk -F= '/^RCLONE_CONFIG_OFFSITE_(ACCESS_KEY_ID|SECRET_ACCESS_KEY)=/{print $1, length($2)}' /opt/menta/backup.env
```

Deben salir 32 y 64.

**Errores encontrados al probar R2 (y su causa):**

- `toomanyrequests: You have reached your unauthenticated pull rate limit`: límite de descargas anónimas de Docker Hub; ver la sección 2.7, "Docker Hub".
- `InvalidArgument: Credential access key has length 53, should be 32`: en `ACCESS_KEY_ID` se había puesto el **valor** del token (53 caracteres) en lugar de su **ID** (32).
- `AccessDenied` al subir un archivo, con el listado funcionando: estaban cargados el ID y el hash del token de **solo lectura**. Lectura sí, escritura no. Se corrigió cargando el ID y el SHA-256 del token de escritura. Un `403` en una operación y no en otra apunta a permisos del token; si fallara todo, sería el filtro de IP o el alcance.
- Filtro de IP del token de escritura: el token de escritura tiene un filtro que solo admite la IP del VPS (confirmado el 2026-10-17), así que robar el token sin salir desde el servidor no sirve. Si el VPS cambiara de IP, hay que actualizar el filtro en Cloudflare o los backups empezarán a fallar con `403`. La IP no se registra en el repositorio.
- Cuidado con pegar tokens en chats, tickets o capturas: el valor `cfat_…` de un token se pegó por error en una conversación y hubo que rotarlo. Si un valor se expone, se invalida en el panel de Cloudflare y se vuelve a calcular el secreto.

**Restaurar desde el almacenamiento externo.** Hace falta Docker, el `backup.env` (o sus variables `RCLONE_CONFIG_*`) y las claves de cifrado en texto plano. En una máquina nueva se puede recrear el `backup.env` con las claves del gestor de contraseñas.

```bash
RCLONE=rclone/rclone:1.75.2@sha256:2687085f718d3c628f7fdfb77c52a1d344332aed543110bb88088f2c70d43eb5
# 1. Ver qué hay (los nombres salen descifrados)
docker run --rm --env-file /opt/menta/backup.env "$RCLONE" lsf offsitecrypt:daily
# 2. Bajar y descifrar un día concreto
mkdir -p ~/restore
docker run --rm --env-file /opt/menta/backup.env -v ~/restore:/restore "$RCLONE" copy offsitecrypt:daily /restore \
  --include "menta-<fecha>.sql.gz" --include "proofs-<fecha>.tar.gz"
# 3. Base de datos: igual que en la sección 2.10 (detener API y BFF, DROP/CREATE DATABASE y restaurar el .sql.gz)
# 4. Comprobantes, con la API en marcha (los archivos quedan con dueño menta)
docker exec -i menta-api tar -xzf - -C /var/menta-dance/payment-proofs < ~/restore/proofs-<fecha>.tar.gz
```

**Monitoreo externo (uptime configurado el 2026-10-19; heartbeat del backup pendiente).** Un chequeo desde afuera detecta lo que ningún script del servidor puede: que el servidor entero no responda. Se usa UptimeRobot (plan gratuito, chequeos cada 5 minutos, alerta por mail; comprobar las condiciones vigentes del plan) con estos monitores, de tipo *keyword*:

| URL | Palabra clave | Qué cubre |
|---|---|---|
| `https://mentadance.com/health` | `ok` | Nginx y el certificado de `mentadance.com` |
| `https://mentadance.com/actuator/health` | `"status":"UP"` | BFF |
| `https://mentadance.com/api/actuator/health` | `"status":"UP"` | API, por el camino que usa la web |
| `https://api.mentadance.com/actuator/health` | `"status":"UP"` | API, por el host que usa la app Android, y su certificado |

**Condición de alerta.** En cada monitor de keyword hay que elegir alertar cuando la palabra clave **no existe**. Con la opción contraria (alertar cuando existe) el monitor marca caída justo cuando el servicio está sano: pasó con el monitor de `api.mentadance.com` y se corrigió en el panel (*Edit*). Además de los cuatro de keyword hay dos monitores HTTP (`https://mentadance.com` y `https://mentadance.com/login`) que detectan caídas de la web por código de respuesta; en total son 6 de los 50 monitores del plan gratuito.

**Validación (2026-10-19).** Se apagó la API en el VPS con `docker stop menta-api`: llegaron los mails de caída y, al ejecutar `docker start menta-api`, los monitores volvieron a `Up` y llegó el aviso de recuperación. `mentadance.com/api/actuator/health` quedó mostrando `502` unos minutos después de la recuperación; un `curl` ya devolvía `200`, porque cada monitor repite su chequeo en su propio momento dentro del intervalo de 5 minutos.

Para el backup, un monitor tipo *heartbeat* (por ejemplo healthchecks.io, gratuito) con período de 1 día y unas 2 horas de gracia: su URL de ping va en `BACKUP_PING_URL`.

**Heartbeat configurado el 2026-10-19.** Check `menta-backup` en healthchecks.io (*Period* 1 día, *Grace Time* 2 horas, alerta por mail), con una descripción que dice dónde mirar cuando avise (`journalctl -u menta-backup.service`). La URL de ping es un secreto (quien la tenga puede mandar avisos falsos): se cargó en el VPS sin mostrarla y sin pisar el archivo.

```bash
read -rs -p "Ping URL: " U; echo
printf 'BACKUP_PING_URL=%s\n' "$U" >> /opt/menta/backup.env; unset U
awk -F= '/^BACKUP_PING_URL=/{print $1, length($2)}' /opt/menta/backup.env   # largo 56, sin mostrar el valor
stat -c '%a %U:%G' /opt/menta/backup.env                                     # sigue en 600 deploy:deploy
```

Un backup a mano (`systemctl start menta-backup.service`) terminó con `Backup completado` y el check pasó a verde con el último aviso de hace un minuto. **No se probó** el camino de fallo (`BACKUP_PING_URL/fail`) ni la alerta por falta de aviso a tiempo. Para probar la alerta de fallo se puede ejecutar `curl -fsS "$URL/fail"` desde una terminal que ya tenga la URL en una variable (sin escribirla en el historial).

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

- Compose de producción (#589): `/opt/menta/.env` ya está completo (2026-10-17, sección 2.9), salvo el token de producción de Mercado Pago y los datos de transferencia, que se cargan antes de habilitar pagos. El primer arranque y la renovación por `webroot` están hechos y documentados (2026-10-18, secciones 2.8 y 2.10). El endurecimiento de SSH quedó descartado (ver "Decisiones diferidas").
- Mejoras al repositorio que dejó el primer despliegue, sin issue todavía: subir el `start_period` del healthcheck de MySQL en `docker-compose.prod.yml` (por ejemplo a 120 s) para que el primer despliegue en un servidor nuevo no falle, y revisar el test intermitente `PaymentExpirySweepIntegrationTest`.
- Baneo de fail2ban: el `bantime` volvió a 3600 el 2026-10-19 con `fail2ban-client set sshd bantime 3600` (sección 2.4).
- Correo (EnvialoSimple): agregar en el DNS de `mentadance.com` los registros SPF y DKIM que indica la pestaña "Registro DNS" de la cuenta, y probar un envío real (activación de cuenta). Sin esos registros los mails pueden caer en spam.
- Despliegue y rollback (#593, sección 2.10): `deploy.sh` está instalado y el primer despliegue a mano con `v0.2.0-rc.2` ya se hizo (2026-10-18). La clave de despliegue ya tiene el comando forzado y el Environment `production` de GitHub tiene los cinco secretos que usa `release.yml` (#592; 2026-10-18). Falta el *required reviewer* del Environment (cuando el repositorio sea público), desplegar un tag estable desde `main` y probar el rollback. Anotar acá el resultado de cada paso.
- Backups y monitoreo (#594, sección 2.11): el bucket, los tokens, las claves de cifrado y `backup.env` ya están hechos y probados (2026-10-17). `backup.sh`, el timer, el primer backup real y el simulacro de restauración en el VPS se hicieron el 2026-10-19 (ver la tabla de la sección 2.11). El uptime externo (UptimeRobot) se configuró y se validó con la caída de la API el mismo día. El heartbeat del backup (`BACKUP_PING_URL`, healthchecks.io) también se configuró ese día; sin probar aún su camino de fallo. Anotar acá el resultado de cada paso.
- Seguimiento de todos los pasos pendientes del servidor: #610.
