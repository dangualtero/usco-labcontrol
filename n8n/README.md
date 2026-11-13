# Agente de IA para los avisos de novedades (n8n)

Cuando un auxiliar reporta que un equipo se **agotó, se dañó, se perdió o falla**, ocurre esto:

```
Auxiliar → «Reportar novedad» → API LabControl (guarda el reporte)
              │ en segundo plano
              ▼
        Webhook ──► Agente de IA ──► Interpretar ──► ¿Prioridad ALTA? ──sí──► Enviar correo ──► Responder a LabControl
                    (prioriza y        respuesta              │
                     redacta)                                 └──no───► Responder sin correo (va al resumen diario)
```

**Política de correo:** solo los reportes de prioridad **ALTA** se envían al instante. Los de prioridad **MEDIA** y **BAJA**
no se envían en ese momento: el backend los guarda como «Irá en el resumen diario» y los manda todos juntos en el
**resumen diario** (7:00 a. m.), junto con los insumos agotados o con 25 % o menos.

La respuesta queda guardada en el reporte y el coordinador la ve en **Reportes** (en tiempo real y con seguimiento).

Si n8n está apagado o no responde, el backend calcula la prioridad por reglas (perdido o dañado = ALTA): si es ALTA **igual envía el aviso** con una plantilla, y si no, lo deja para el resumen diario. Así ninguna novedad se pierde.

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

El flujo usa versiones de nodos disponibles desde **n8n 2.22** (también funciona en versiones más nuevas). Si usas otra versión y algún nodo aparece con advertencia, ábrelo y vuelve a elegir sus opciones.

### ¿Ya tenías el flujo importado (por ejemplo con Gemini)? Agrega el nodo «¿Prioridad ALTA?» a mano

No hace falta volver a importar (perderías tu modelo Gemini). En tu flujo actual:

1. Borra la flecha que va de **Interpretar respuesta de la IA** a **Enviar correo institucional**.
2. Agrega un nodo **If** y llámalo `¿Prioridad ALTA?`. Conecta **Interpretar respuesta de la IA → ¿Prioridad ALTA?**.
3. En la condición elige **Boolean → is true** y en el valor pega esta expresión:
   ```
   {{ $json.prioridad === 'ALTA' || $('Webhook LabControl').first().json.body.forzarCorreo === true }}
   ```
4. Conecta la salida **true** a **Enviar correo institucional** (que sigue conectado a **Responder a LabControl**).
5. Duplica el nodo **Responder a LabControl**, llámalo `Responder sin correo`, conéctalo a la salida **false** y en su
   *Response Body* cambia `correoEnviado: !$json.error` por `correoEnviado: false`.
6. Guarda y publica el flujo.

`forzarCorreo` llega en `true` cuando el coordinador pulsa **Enviar correo ahora** en un reporte que no es ALTA.

Si no haces este cambio, el sistema sigue funcionando: n8n enviaría todos los correos al instante como antes y el
backend lo registra así.

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

El mensaje de sistema del nodo **Agente de IA** define los criterios, y puedes ajustarlos ahí mismo (recuerda que solo ALTA se envía al instante):

- **ALTA:** equipo perdido; daño que impide usarlo o que representa un riesgo; equipo único o costoso; afecta clases inmediatas.
- **MEDIA:** falla parcial o equipo agotado que requiere reposición.
- **BAJA:** detalle estético o reporte informativo.

La descripción del auxiliar se trata como dato, no como instrucciones, y el correo se arma escapando todo el texto para evitar inyección de HTML.
