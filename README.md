# USCO LabControl

Sistema web para la **gestión de los laboratorios** de la Facultad de Ingeniería de la Universidad Surcolombiana:
caracterización, inventarios y mantenimientos, solicitudes y requerimientos, presupuesto y contratación, reportes y
seguimiento. Cada sede tiene sus propios laboratorios. **3 roles:** Administrador general, Coordinador de Laboratorio y
Monitor de Laboratorio (detalle en [docs/ROLES_Y_PERMISOS.md](docs/ROLES_Y_PERMISOS.md)).
Proyecto Integrador II · Daniel Felipe Gualtero · Carlos David Neira Vega · Cesar Sneider Pimiento Torres.

| Capa | Tecnología |
|---|---|
| Frontend | React 18 · Vite · Tailwind CSS (paleta oficial USCO) · Recharts · React Router |
| Backend | Java 21 · Spring Boot 3.3 · Spring Security (JWT + roles) · Spring Data JPA |
| Datos | **MySQL 8** (única base de datos; no existe modo en memoria ni datos de respaldo) |

La aplicación depende al 100 % del backend y de MySQL. Si la API no responde, el frontend muestra
**«Error de conexión con el servidor USCO LabControl - Verifique que la API Spring Boot esté activa»** (con botón *Reintentar*) y nunca datos inventados.

## Primer acceso (no hay usuarios quemados en el código)

La base de datos arranca **sin cuentas**. Para el primer ingreso, define estas variables antes de iniciar el backend
(en IntelliJ: *Run → Edit Configurations → Environment variables*; en el servidor: el archivo `.env`):

```
LABCONTROL_ADMIN_CORREO=tu.correo@usco.edu.co
LABCONTROL_ADMIN_CLAVE=una-contraseña-segura
```

Al arrancar, si no existe ningún Administrador general activo, el backend lo crea (contraseña cifrada con BCrypt).
Después de entrar puedes quitar `LABCONTROL_ADMIN_CLAVE`: las demás cuentas (coordinadores y monitores, con sus
laboratorios) se crean en **Administración → Usuarios y roles**, y cada persona cambia su contraseña en **Mi cuenta**.
El rol se identifica a partir del correo: el login solo pide correo y contraseña.

## Cómo ejecutarlo

Requisitos: Java 21 + Maven, Node 20+ y MySQL 8.

**Desde IntelliJ (lo más simple):** abre `backend` y pulsa ▶ en **LabControlApplication**; abre `frontend` y pulsa ▶ en **Frontend dev**.
No hay que hacer nada más: si MySQL no está encendido, la API lo inicia sola (`MysqlLocalAutostart` ejecuta `iniciar-mysql.ps1`;
MySQL queda corriendo aunque detengas la API). Se desactiva con `labcontrol.mysql.autostart=false`, y solo actúa en Windows con una base local.


Paso a paso:

```bash
# 1) MySQL 8 con la base «labcontrol» (elige UNA opción)
.\iniciar-mysql.ps1                       # instancia portátil en el puerto 3307 (la primera vez también crea la base)
.\crear-base-datos.ps1                    # crea o vuelve a crear la base desde backend/db/*.sql (BORRA los datos)
docker compose up -d                      # o MySQL en Docker (cp .env.example .env; usa MYSQL_URL con el puerto 3306)

# 2) API Spring Boot (puerto 8080) — usa las tablas creadas por los scripts SQL (no las modifica)
cd backend && mvn spring-boot:run         # o ejecutar LabControlApplication desde IntelliJ

# 3) Frontend (puerto 5173; /api se reenvía a 8080)
cd frontend && npm install && npm run dev
```

Variables de entorno de la API (los valores por defecto sirven para el desarrollo local; en producción **defínelas siempre**):

| Variable | Por defecto | Uso |
|---|---|---|
| `MYSQL_URL` | `jdbc:mysql://127.0.0.1:3307/labcontrol?...` | Cadena de conexión |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `labcontrol` / `LabControl#2026` (solo desarrollo) | Credenciales de la base |
| `LABCONTROL_JWT_SECRET` | secreto de desarrollo | Firma de tokens (mínimo 32 caracteres) |
| `LABCONTROL_JWT_MINUTOS` / `LABCONTROL_JWT_DIAS_RECORDAR` | `60` / `7` | Vigencia del token (normal / «Recordar mi sesión») |
| `LABCONTROL_ADMIN_CORREO` / `LABCONTROL_ADMIN_CLAVE` | vacío | Crea el primer Administrador general si la base no tiene ninguno (opcional: `LABCONTROL_ADMIN_NOMBRE`, `LABCONTROL_ADMIN_DOCUMENTO`) |

### Base de datos: definida 100 % en SQL

La estructura completa vive en `backend/db/` y el backend **no crea ni modifica tablas** (`spring.jpa.hibernate.ddl-auto: none`). Las clases Java solo conservan las anotaciones mínimas para leer y escribir (`@Entity`, `@Table`, `@Id`, `@ManyToOne`, `@Enumerated`…). Longitudes, obligatoriedad, únicos, llaves foráneas, índices, valores permitidos (`CHECK`) y triggers están en MySQL.

| Script | Qué hace |
|---|---|
| `00_usuario_mysql.sql` | Base `labcontrol` y usuario de la aplicación, con permisos solo de datos (SELECT, INSERT, UPDATE, DELETE) |
| `01_esquema.sql` | 25 tablas con sus llaves, índices y restricciones (**borra y recrea** las tablas) |
| `02_inmutabilidad.sql` | Triggers que impiden UPDATE y DELETE en `historial_eventos` |
| `03_datos_iniciales.sql` | Sede Neiva, sus 4 laboratorios, 19 equipos y 5 insumos de ejemplo. **No crea usuarios** (ver «Primer acceso») |
| `05_insumos_laboratorios_reportes.sql` | Solo para bases creadas antes de la v1.4. No borra nada y se puede ejecutar varias veces |
| `06_roles_y_modulos.sql` | Solo para bases creadas antes de la v2.0: convierte los roles (Coordinador → Administrador, Docente → Coordinador de Laboratorio, Auxiliar → Monitor) y crea las tablas de los módulos nuevos. No borra datos y se puede ejecutar varias veces (`.\actualizar-base-datos.ps1`) |

**Crear usuarios con un INSERT:** escribe la contraseña normal en `password_hash` (por ejemplo `'Usco#2026'`). El backend la cifra con BCrypt a los pocos segundos (`CifradoContrasenas`), o en el primer inicio de sesión si la persona entra antes. Mientras tanto, la contraseña se ve en texto normal en la base, así que usa una contraseña inicial y pide que la cambien en **Mi cuenta**.

Para ejecutarlos: `.\crear-base-datos.ps1` (MySQL portátil), o abre cada archivo en IntelliJ (**Database**) y ejecútalo en orden con root. En Docker se ejecutan solos la primera vez.

## Roles y módulos (v2.0)

| Módulo | Administrador general | Coordinador de Laboratorio | Monitor de Laboratorio |
|---|---|---|---|
| Tablero de control y parametrización | Exclusivo: laboratorios, usuarios, roles y permisos, catálogos, alertas, plantillas | Indicadores de sus laboratorios | Indicadores de sus laboratorios |
| Laboratorios: caracterización y documentación | Todos | Actualiza datos, documentos y evidencias | Consulta |
| Inventarios y mantenimientos | Todo | Bienes, hoja de vida, valida novedades, mantenimientos | Consulta, información operativa, **registro preliminar** de novedades con fotos |
| Solicitudes y requerimientos | Revisa, devuelve, prioriza, decide y cierra | Crea, adjunta, **valida** lo del monitor y envía | Prepara borradores (su «envío» va al coordinador) |
| Presupuestos y contratación | Registra presupuesto y contratación | Consulta el estado de sus solicitudes | — |
| Reportes y seguimiento | Reportes institucionales, verifica avances y compromisos | Reportes, compromisos, avances y evidencias | Reportes, avances y evidencias |

- **Asignación:** cada coordinador o monitor tiene uno o varios laboratorios y solo ve esos. El Administrador ve todo y cambia de sede con el selector.
- **Permisos configurables:** el Administrador ajusta la matriz en **Parametrización → Permisos por rol** (lo exclusivo del Administrador no se delega).
- **Novedades:** el monitor reporta (con fotografías y soportes) → el coordinador valida → se aplica al inventario y se avisa por correo con el agente de IA de n8n. Incluye conteos de existencias y consumos de insumos con conversión de unidades.
- **Solicitudes:** borrador → en revisión del coordinador → enviada → aprobada o rechazada → cerrada (con devolución para ajuste), cotizaciones adjuntas, prioridad propuesta y asignada, presupuesto (rubro, fuente, CDP) y contratación, que puede registrar el gasto en el presupuesto de la sede.
- **Alertas:** existencias bajas, equipos fuera de servicio, novedades por validar, mantenimientos vencidos o próximos, solicitudes por validar, devueltas o sin decisión y compromisos vencidos (umbrales configurables).
- **Reportes:** indicadores por laboratorio y exportación CSV de inventario, mantenimientos, novedades, solicitudes, compromisos, avances, indicadores e historial.
- **Seguridad:** login por correo `@usco.edu.co` y contraseña, JWT, BCrypt, permisos verificados en el servidor en **cada** acción, archivos privados que solo descarga quien ve el laboratorio, bloqueo temporal tras 5 intentos fallidos y cierre de sesión por inactividad.
- **Correo:** las novedades de prioridad ALTA se envían al instante; MEDIA y BAJA van en un **resumen diario** (7:00 a. m., `LABCONTROL_RESUMEN_CRON`). Las notificaciones del flujo (novedad por validar, solicitud enviada, devuelta, decidida, cerrada, mantenimiento programado, compromiso asignado) usan plantillas editables.

Todo el detalle, la matriz de permisos y las **mejoras hechas a los requisitos de la hoja** están en [docs/ROLES_Y_PERMISOS.md](docs/ROLES_Y_PERMISOS.md).

| Variable | Para qué |
|---|---|
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Envío de correos (Gmail: `smtp.gmail.com`, `587`, contraseña de aplicación) |
| `LABCONTROL_ALERTAS_DESTINO` | Correo que recibe los avisos de novedades validadas |
| `N8N_WEBHOOK_URL`, `N8N_WEBHOOK_TOKEN` | Flujo de n8n y token compartido (credencial Header Auth `X-LabControl-Token`). Configuración: [n8n/README.md](n8n/README.md) |
| `LABCONTROL_APP_URL` | Dirección pública del frontend (para los enlaces de los correos) |
| `LABCONTROL_RESUMEN_CRON`, `LABCONTROL_RESUMEN_ZONA` | Hora del resumen diario (por defecto `0 0 7 * * *`, `America/Bogota`) |

Publicar en la web: `docker-compose.prod.yml` levanta MySQL, la API, el frontend (nginx) y n8n. Paso a paso: [DESPLIEGUE.md](DESPLIEGUE.md).

## Sedes, presupuesto y fotografías

### Una dirección por sede
| Dirección | Qué muestra |
|---|---|
| `http://localhost:5173/` | «Elige tu sede» (solo sedes activas) |
| `http://localhost:5173/neiva/login` | Ingreso a la Sede Neiva |
| `http://localhost:5173/pitalito/login` | Ingreso a la Sede Pitalito (cuando el Administrador la cree) |
| `http://localhost:5173/garzon/presupuesto` | Presupuesto de la Sede Garzón |

- Cada coordinador y monitor **pertenece a una sede** y a sus laboratorios asignados. Si intenta ingresar por la dirección de otra sede, el sistema le dice a cuál pertenece.
- El **Administrador general** administra todas las sedes y cambia entre ellas con el selector del encabezado; las pantallas conservan la sección (Inventario, Usuarios, Presupuesto…).
- El frontend envía la sede en la cabecera `X-Sede`; el servidor solo la respeta para el Administrador. A los demás siempre les aplica su propia sede.
- **Sede principal:** `03_datos_iniciales.sql` crea la *Sede Neiva* con sus laboratorios y el inventario de ejemplo.

### Administración → Sedes
Crear sede (ciudad → dirección web, p. ej. «La Plata» → `/la-plata`), editar nombre y dirección física, activar/desactivar (desactivar no borra datos; impide el ingreso de sus usuarios). Los coordinadores y monitores nuevos se crean en la sede que está abierta.

### Presupuestos y contratación → Presupuesto
Un presupuesto por sede. **Ingreso** suma y **Gasto** resta; no se permite un gasto mayor al saldo. Cada movimiento lleva concepto, categoría, valor en COP, fecha (no futura) y placa opcional del equipo. Los movimientos **no se borran**: se anulan con motivo y quedan visibles tachados. Incluye gastos por categoría y el saldo de todas las sedes. Los gastos de una solicitud contratada quedan enlazados a ella.

### Fotografía desde el computador o el celular
En **Inventario → Nuevo / Editar equipo**, «Subir fotografía» abre el explorador de archivos (en el celular: galería o cámara). También se puede arrastrar la imagen. Antes de subirla el navegador la reduce a 1600 px en JPEG, así una foto de celular pesa unos cientos de KB.
El servidor acepta JPG, PNG o WEBP de hasta 5 MB, verifica el tipo real del archivo y la guarda en MySQL (tabla `imagenes`); se sirve en `/api/imagenes/{id}`. La opción «O usar una URL» sigue disponible.

## Fotografías de los equipos

Están en `frontend/public/images/equipos/usco-eq-XXX.jpg` y provienen de Wikimedia Commons (licencias libres, ver `CREDITOS.md` en esa carpeta).
Son fotografías de equipos **de la misma marca o tipo**, no necesariamente de la unidad física del inventario USCO. `USCO-EQ-402` (Tensiómetro de humedad de suelo) usa una fotografía de un equipo Irrometer; `USCO-EQ-201` (Balanza de lodos presurizada) usa una
**ilustración referencial** propia (`ilustraciones/usco-eq-201.svg`), porque no existe una fotografía libre del equipo. Para usar la foto real de cualquier unidad basta
copiarla como `usco-eq-XXX.jpg` en esa carpeta, o cambiar la URL desde **Inventario → Editar**. Si un equipo no tiene imagen, la interfaz muestra «Fotografía no disponible».
Los números de serie y ubicaciones de la carga inicial son datos de referencia: reemplázalos por los del inventario físico desde la misma pantalla.

## Dónde está cada cosa

| Requisito | Dónde está |
|---|---|
| Roles y permisos (3 roles, matriz configurable) | `domain/Rol.java`, `domain/Permiso.java`, `service/PermisoService.java`, `service/Acceso.java`, `lib/modulos.js`, `App.jsx` (`RequierePermiso`) |
| Tablero y parametrización | `ReporteService.tablero`, `ConfiguracionService`, `pages/tablero/*`, `pages/admin/*` |
| Laboratorios: caracterización y documentación | `LaboratorioService`, `pages/laboratorios/*` |
| Inventarios, hojas de vida y mantenimientos | `InventarioService`, `MantenimientoService`, `pages/inventario/*` |
| Novedades (registro preliminar y validación) | `NovedadService`, `NotificadorNovedades`, `pages/inventario/Novedades.jsx`, `components/Novedades.jsx` |
| Solicitudes y requerimientos | `SolicitudService`, `pages/solicitudes/*` |
| Presupuestos y contratación | `PresupuestoService`, `SolicitudService.presupuesto/contratacion`, `pages/presupuesto/*` |
| Reportes y seguimiento | `ReporteService`, `SeguimientoService`, `pages/seguimiento/*` |
| Historial inmutable (RF-03) | `HistorialRepository` (solo inserción) + triggers de `02_inmutabilidad.sql` |
| Navegación PF-13 (logo → inicio) | `components/Shell.jsx` (`LogoUsco`) |

## Accesibilidad y modo oscuro

El botón «Registrar Entrada» usa el verde oscuro `#047857` (5,5:1 con texto blanco, WCAG AA); el verde `#059669` original daba 3,77:1. El resto de la paleta cumple
(texto/blanco 10,3:1 · blanco/vino 9,2:1 · blanco/azul salida 10,4:1). La interfaz ofrece **modo claro y oscuro** (botón sol/luna en el encabezado y en el login);
la primera vez sigue la preferencia del sistema y luego recuerda la elección del usuario.

## Pruebas

```bash
cd backend  && mvn test     # pruebas de integración (H2 en memoria): roles y permisos, novedades, mantenimientos, solicitudes, seguimiento
cd frontend && npm test     # pruebas de utilidades, validaciones, unidades de medida y permisos del menú
```

Las cuentas e inventario que usan las pruebas se crean solo en la base de prueba en memoria
(`backend/src/test/.../DatosDePrueba.java`); no forman parte de la aplicación.

## Estructura

```
backend/   API REST (Spring Boot) · domain, repo, service, security, web
frontend/  SPA React · api, pages por módulo, components, context · public/images/equipos
docs/      ROLES_Y_PERMISOS.md (roles, permisos, flujos y mejoras a los requisitos)
docker-compose.yml  MySQL 8 · iniciar-mysql.ps1  MySQL portátil · crear-base-datos.ps1  crea la base desde backend/db