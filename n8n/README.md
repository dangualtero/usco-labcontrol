# Agente de IA para los avisos de novedades (n8n)

Cuando un auxiliar reporta que un equipo se **agotó, se dañó, se perdió o falla**, ocurre esto:

```
Auxiliar → «Reportar novedad» → API LabControl (guarda el reporte)
              │ en segundo plano
              ▼
        Webhook de n8n ──► Agente de IA (Claude) ──► Interpretar respuesta ──► Enviar correo ──► Responder a LabControl
                             prioridad ALTA/MEDIA/BAJA,  (valida el JSON;        (SMTP al            (prioridad, asunto,
                             asunto, resumen y            si la IA falla usa      correo              resumen, acción)
                             acción sugerida              reglas)                 institucional)
```

La respuesta queda guardada en el reporte, y el coordinador la ve en **Inventario → Novedades reportadas**.

Si n8n está apagado o no responde, el backend **igual envía el aviso** con una plantilla y una prioridad calculada por reglas. Así ninguna novedad se pierde.

## 1. Ejecutar n8n

**En tu computador (Windows), con Node.js 20 o superior:**

```powershell
npx n8n
```

**Con Docker:**

```bash
docker run -it --rm -p 5678:5678 -v n8n_data:/home/node/.n8n n8nio/n8n:2.46.6
```

**En el servidor:** ya viene incluido en `docker-compose.prod.yml` (ver `DESPLIEGUE.md`).

Abre `http://localhost:5678` y crea tu cuenta de administrador de n8n.

## 2. Importar el flujo

1. En n8n ve a **Workflows → Create workflow**.
2. En el menú **⋯** (arriba a la derecha) elige **Import from File**.
3. Selecciona `n8n/labcontrol-novedades.json`.

El flujo se probó contra las definiciones de nodos de **n8n 2.46**. Si usas otra versión y algún nodo aparece con advertencia, ábrelo y vuelve a elegir sus opciones.

## 3. Credenciales (una por nodo)

| Nodo | Credencial | Qué poner |
|---|---|---|
| **Webhook LabControl** | *Header Auth* | Name: `X-LabControl-Token` · Value: el mismo valor de `N8N_WEBHOOK_TOKEN` del backend |
| **Modelo Claude (Anthropic)** | *Anthropic API* | Tu API key de console.anthropic.com. El modelo viene en `claude-haiku-5-5` (rápido y económico); puedes cambiarlo en el nodo. El uso de la API tiene costo. |
| **Enviar correo institucional** | *SMTP* | Gmail: host `smtp.gmail.com`, puerto `465` con SSL, usuario = tu correo y la contraseña de aplicación. Cambia también el campo **From Email**. |

El destinatario **no** se configura en n8n: lo envía el backend (`LABCONTROL_ALERTAS_DESTINO`, por defecto `u20232215593@usco.edu.co`). Para cambiarlo, edita esa variable.

## 4. Activar y conectar con el backend

1. Guarda el flujo y **actívalo** (interruptor o botón de publicar, arriba a la derecha).
2. En el nodo **Webhook LabControl**, copia la **Production URL**. Debe terminar en `/webhook/labcontrol-novedades`.
3. En el backend define estas variables (en IntelliJ: *Run → Edit Configurations → Environment variables*):
   ```
   N8N_WEBHOOK_URL=http://localhost:5678/webhook/labcontrol-novedades
   N8N_WEBHOOK_TOKEN=el-mismo-token-de-la-credencial
   ```
4. Reinicia el backend y reporta una novedad como auxiliar. En n8n, en **Executions**, verás cada ejecución paso a paso.

## Qué decide la IA

El mensaje de sistema del nodo **Agente de IA** define los criterios, y puedes ajustarlos ahí mismo:

- **ALTA:** equipo perdido; daño que impide usarlo o que representa un riesgo; equipo único o costoso; afecta clases inmediatas.
- **MEDIA:** falla parcial o equipo agotado que requiere reposición.
- **BAJA:** detalle estético o reporte informativo.

La descripción del auxiliar se trata como dato, no como instrucciones, y el correo se arma escapando todo el texto para evitar inyección de HTML.
