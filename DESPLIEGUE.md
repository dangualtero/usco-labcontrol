# Publicar USCO LabControl en la web

Esta guía lleva el proyecto de tu computador a un servidor accesible por internet, con:

- **MySQL** (base de datos)
- **API Spring Boot** (backend)
- **Frontend React** servido por **nginx**, que además reenvía `/api` al backend (mismo dominio, sin CORS)
- **n8n** con el agente de IA que prioriza y redacta los avisos de novedades

Todo se levanta con un solo archivo: `docker-compose.prod.yml`.

---

## 0. Antes de publicar: pruébalo completo en tu computador

| Qué | Cómo probarlo en local |
|---|---|
| Registro con código | Sin configurar correo, el código aparece en la consola de IntelliJ (busca `CORREO NO ENVIADO`). |
| Correo real | En IntelliJ → *Run → Edit Configurations → Environment variables* agrega `MAIL_HOST=smtp.gmail.com;MAIL_PORT=587;MAIL_USERNAME=tu@gmail.com;MAIL_PASSWORD=contraseña-de-aplicación` |
| n8n + IA | Ver [n8n/README.md](n8n/README.md). En local: `N8N_WEBHOOK_URL=http://localhost:5678/webhook/labcontrol-novedades` y `N8N_WEBHOOK_TOKEN=…` |
| Novedad | Entra como auxiliar → **Inventario → Reportar novedad**. En **Coordinador → Inventario** verás la prioridad que puso la IA. |

**Gmail como remitente:** activa la verificación en dos pasos en la cuenta y crea una *contraseña de aplicación* (Cuenta de Google → Seguridad → Contraseñas de aplicaciones). Si tu cuenta @usco.edu.co no permite contraseñas de aplicación, usa una cuenta Gmail personal solo para enviar; los destinatarios siguen siendo los correos @usco.edu.co.

---

## 1. Consigue un servidor

Cualquier servidor Linux con **Docker** y **2 GB de RAM** (4 GB recomendado, por n8n) sirve:

- **Un VPS** (DigitalOcean, Hetzner, AWS Lightsail, Oracle Cloud, Azure for Students…). Revisa los planes vigentes y los créditos para estudiantes, porque cambian con frecuencia.
- **El servidor Ubuntu que ya usas** (por ejemplo el de HelpDesk_USCO), si es accesible desde internet o si lo publicas con un túnel (paso 5).

Instala Docker en Ubuntu:

```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER   # cierra sesión y vuelve a entrar
docker compose version
```

## 2. Sube el código al servidor

```bash
git clone https://github.com/TU_USUARIO/usco-labcontrol.git
cd usco-labcontrol
```

Cada vez que hagas `git push` desde tu computador, en el servidor basta con `git pull` y repetir el paso 4.

## 3. Crea el archivo `.env` (secretos)

```bash
cp .env.example .env
nano .env
```

Genera valores aleatorios para los secretos:

```bash
openssl rand -hex 32   # úsalo para LABCONTROL_JWT_SECRET
openssl rand -hex 24   # N8N_WEBHOOK_TOKEN
openssl rand -hex 24   # N8N_ENCRYPTION_KEY
```

Completa como mínimo `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `LABCONTROL_JWT_SECRET`, `LABCONTROL_APP_URL` (por ejemplo `https://labcontrol.tudominio.com` o `http://IP-DEL-SERVIDOR`), los datos de `MAIL_*`, `N8N_WEBHOOK_TOKEN` y `N8N_ENCRYPTION_KEY`.

> `.env` está en `.gitignore`: **nunca** lo subas a GitHub.

## 4. Levanta todo

```bash
docker compose -f docker-compose.prod.yml up -d --build
docker compose -f docker-compose.prod.yml logs -f backend     # espera «Started LabControlApplication»
```

Abre `http://IP-DEL-SERVIDOR/` → «Elige tu sede».

### ⚠️ Cuentas de ejemplo

Con `LABCONTROL_SEED=true` se crean las cuentas de prueba del README, y sus contraseñas están publicadas en GitHub. **Apenas arranque por primera vez**:

1. Entra como coordinador y, en **Usuarios**, crea tu propia cuenta de coordinador con una contraseña nueva.
2. Desactiva las cuentas de ejemplo o cámbiales la contraseña.
3. Cambia `LABCONTROL_SEED=false` en `.env`.

## 5. HTTPS y dominio

Para que funcione desde celulares y se vea profesional, publícalo con HTTPS. Elige una opción:

**A. Prueba rápida sin dominio (Cloudflare Quick Tunnel)**

```bash
docker run --rm --network host cloudflare/cloudflared:latest tunnel --url http://localhost:80
```

Muestra una dirección `https://algo-aleatorio.trycloudflare.com` que puedes compartir. La dirección cambia cada vez que lo reinicias: sirve para demos y pruebas, no para uso permanente. Pon esa dirección en `LABCONTROL_APP_URL` y reinicia el backend.

**B. Con dominio propio**

Apunta el dominio (registro DNS tipo A) a la IP del servidor y pon delante un proxy con certificado automático, como Caddy o nginx con Let's Encrypt (certbot). Luego actualiza `LABCONTROL_APP_URL` con `https://…`.

## 6. Configura n8n en el servidor

El editor de n8n solo escucha en el propio servidor (`127.0.0.1:5678`), para que nadie más pueda abrirlo. Ábrelo desde tu computador con un túnel SSH:

```bash
ssh -L 5678:localhost:5678 usuario@IP-DEL-SERVIDOR
```

Luego entra a `http://localhost:5678` en tu navegador, crea la cuenta de administrador de n8n e importa el flujo siguiendo [n8n/README.md](n8n/README.md).

Dentro de Docker, el backend ya está configurado para llamar a `http://n8n:5678/webhook/labcontrol-novedades`.

## 7. Mantenimiento

```bash
git pull && docker compose -f docker-compose.prod.yml up -d --build                  # actualizar
docker compose -f docker-compose.prod.yml logs -f backend                            # ver errores
docker compose -f docker-compose.prod.yml exec mysql \
  mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" labcontrol > respaldo-$(date +%F).sql      # respaldo
```

## Alternativa: plataformas que despliegan desde GitHub

Railway, Render y otras similares pueden construir directamente los `Dockerfile` de `backend/` y `frontend/`. Allí tendrías que crear la base MySQL y n8n como servicios aparte y copiar las mismas variables de `.env` en su panel.

En ese caso, el `nginx.conf` del frontend debe apuntar a la dirección interna del backend que te asigne la plataforma, en lugar de `http://backend:8080`. Revisa los planes y límites vigentes de cada plataforma antes de elegir.
