# USCO LabControl

Sistema web para la **gestión y trazabilidad de préstamos de equipos** en los laboratorios de la Facultad de Ingeniería
de la Universidad Surcolombiana (Electrónica, Petróleos, Civil y Agrícola).
Proyecto Integrador II · Daniel Felipe Gualtero · Carlos David Neira Vega · Cesar Sneider Pimiento Torres.

| Capa | Tecnología |
|---|---|
| Frontend | React 18 · Vite · Tailwind CSS (paleta oficial USCO) · Recharts · React Router |
| Backend | Java 21 · Spring Boot 3.3 · Spring Security (JWT + roles) · Spring Data JPA |
| Datos | **MySQL 8** (única base de datos; no existe modo en memoria ni datos de respaldo) |

La aplicación depende al 100 % del backend y de MySQL. Si la API no responde, el frontend muestra
**«Error de conexión con el servidor USCO LabControl - Verifique que la API Spring Boot esté activa»** (con botón *Reintentar*) y nunca datos inventados.

## Credenciales de acceso (cuentas creadas en MySQL, contraseñas cifradas con BCrypt)

El **rol se identifica automáticamente** a partir del correo: el login solo pide correo y contraseña.
Cada persona puede cambiar su contraseña en **Mi cuenta**, y el Coordinador puede crear usuarios y restablecer contraseñas en **Usuarios**.

| Persona | Rol (panel al que entra) | Correo | Contraseña |
|---|---|---|---|
| Administrador LabControl | COORDINADOR (general) | `admin@usco.edu.co` | `Admin#USCO2026` |
| Daniel Felipe Gualtero | COORDINADOR · Dashboard, historial, inventario y usuarios | `daniel.gualtero@usco.edu.co` | `Daniel#USCO2026` |
| Carlos David Neira Vega | AUXILIAR · Ventanilla, inventario y cierre de turno | `carlos.neira@usco.edu.co` | `Carlos#USCO2026` |
| Cesar Sneider Pimiento Torres | ESTUDIANTE · Catálogo, mis reservas, mi historial | `cesar.pimiento@usco.edu.co` | `Cesar#USCO2026` |
| Sandra Milena Vargas | COORDINADOR | `coordinador@usco.edu.co` | `Coord#USCO2026` |
| Juan Pablo Trujillo | AUXILIAR | `auxiliar@usco.edu.co` | `Aux#USCO2026` |
| Marcela Ortiz | AUXILIAR | `auxiliar2@usco.edu.co` | `Aux2#USCO2026` |
| Mauricio Perdomo | DOCENTE | `docente@usco.edu.co` | `Doc#USCO2026` |
| Laura Gómez (cód. 20211100456 · doc. 1075123456) | ESTUDIANTE | `estudiante@usco.edu.co` | `Est#USCO2026` |
| Andrés Ramírez (cód. 20201100011) | ESTUDIANTE | `andres.ramirez@usco.edu.co` | `Andres#USCO2026` |
| Valentina Cuéllar (cód. 20211100012) | ESTUDIANTE | `valentina.cuellar@usco.edu.co` | `Valentina#USCO2026` |
| Camila Rojas (cód. 20221100013) | ESTUDIANTE | `camila.rojas@usco.edu.co` | `Camila#USCO2026` |
| Sebastián Medina (cód. 20211100014) | ESTUDIANTE | `sebastian.medina@usco.edu.co` | `Sebastian#USCO2026` |

> Estas son las **contraseñas iniciales** de `backend/db/03_datos_iniciales.sql` (guardadas con BCrypt). **Cámbialas antes de publicar el sistema fuera de la red de pruebas.**

## Cómo ejecutarlo

Requisitos: Java 21 + Maven, Node 20+ y MySQL 8.

**Desde IntelliJ (lo más simple):** abre `backend` y pulsa ▶ en **LabControlApplication**; abre `frontend` y pulsa ▶ en **Frontend dev**.
No hay que hacer nada más: si MySQL no está encendido, la API lo inicia sola (`MysqlLocalAutostart` ejecuta `iniciar-mysql.ps1`;
MySQL queda corriendo aunque detengas la API). Se desactiva con `labcontrol.mysql.autostart=false`, y solo actúa en Windows con una base local.

```powershell
.\iniciar.ps1        # alternativa por consola: MySQL portátil -> API (8080) -> frontend (5173) y abre el navegador
```

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
| `LABCONTROL_SEED` | `true` | Carga inicial de usuarios e inventario si la base está vacía |
| `LABCONTROL_SEED_HISTORIAL` | `true` | Incluye préstamos, reservas e historial de ejemplo; en `false` arranca con inventario y usuarios solamente |

### Base de datos: definida 100 % en SQL

La estructura completa vive en `backend/db/` y el backend **no crea ni modifica tablas** (`spring.jpa.hibernate.ddl-auto: none`). Las clases Java solo conservan las anotaciones mínimas para leer y escribir (`@Entity`, `@Table`, `@Id`, `@ManyToOne`, `@Enumerated`…). Longitudes, obligatoriedad, únicos, llaves foráneas, índices, valores permitidos (`CHECK`) y triggers están en MySQL.

| Script | Qué hace |
|---|---|
| `00_usuario_mysql.sql` | Base `labcontrol` y usuario de la aplicación, con permisos solo de datos (SELECT, INSERT, UPDATE, DELETE) |
| `01_esquema.sql` | 9 tablas con sus llaves, índices y restricciones (**borra y recrea** las tablas) |
| `02_inmutabilidad.sql` | Triggers que impiden UPDATE y DELETE en `historial_eventos` |
| `03_datos_iniciales.sql` | Sede Neiva, 13 cuentas (contraseñas BCrypt) y 19 equipos |
| `04_claves_en_texto_normal.sql` | Solo para bases creadas antes: permite escribir contraseñas normales en los INSERT |

**Crear usuarios con un INSERT:** escribe la contraseña normal en `password_hash` (por ejemplo `'Usco#2026'`). El backend la cifra con BCrypt a los pocos segundos (`CifradoContrasenas`), o en el primer inicio de sesión si la persona entra antes. Mientras tanto, la contraseña se ve en texto normal en la base, así que usa una contraseña inicial y pide que la cambien en **Mi cuenta**.

Para ejecutarlos: `.\crear-base-datos.ps1` (MySQL portátil), o abre cada archivo en IntelliJ (**Database**) y ejecútalo en orden con root. En Docker se ejecutan solos la primera vez.

## Funcionalidades por rol (todas contra MySQL)

- **Estudiante / Docente:** catálogo con fotografías y filtros instantáneos, **ficha técnica completa** (foto, marca, modelo, serie, ubicación exacta, estado físico, accesorios, disponibilidad), solicitud de préstamo (fecha, franja, asignatura, docente), **Mis reservas** (cancelar pendientes, ver rechazos), **Mi historial**, alertas de devolución vencida y **Mi cuenta** (cambio de contraseña).
- **Auxiliar:** ventanilla con búsqueda por código estudiantil o documento, **aprobar y registrar salida** (azul) o **rechazar** con motivo, **registrar entrada** (verde) con checklist de estado físico y advertencia disciplinaria, alertas de retraso, **inventario** (enviar a mantenimiento / reactivar) y **cierre de turno** con transferencia de custodia.
- **Coordinador general:** sedes, presupuesto por sede, dashboard con KPIs y gráficos, **historial inmutable** con filtros y exportación CSV sanitizada, **CRUD de equipos** (crear, editar, mantenimiento, baja lógica) y **gestión de usuarios** (crear con rol, activar/desactivar, restablecer contraseña).
- **Seguridad:** login único por correo `@usco.edu.co` y contraseña, JWT, BCrypt, autorización por rol en el servidor (`SecurityConfig`) y en el cliente (`RequiereRol`), bloqueo temporal tras 5 intentos fallidos, mensajes de error genéricos, el rol y el estado de la cuenta se verifican en la base de datos en **cada** petición, cierre de sesión por inactividad (sin «Recordar mi sesión»).
- **Concurrencia:** bloqueo pesimista sobre el equipo (`SELECT … FOR UPDATE`): cero préstamos duplicados.

## Registro con código, avisos con IA y despliegue (v1.3)

- **Registro de estudiantes** (`/neiva/registro`, enlace en el ingreso): el estudiante llena sus datos y recibe un **código de 6 dígitos** en su correo @usco.edu.co. Solo al ingresar el código se crea su cuenta en MySQL y se abre la sesión. El código vence en 15 minutos, admite 5 intentos y se puede reenviar cada 60 segundos. Sin correo configurado, el código aparece en la consola del backend (`CORREO NO ENVIADO`).
- **Novedades de equipos:** el auxiliar usa **Inventario → Reportar novedad** (agotado, dañado, perdido, falla…) y puede enviar el equipo a mantenimiento en el mismo paso. Un **agente de IA en n8n** prioriza el reporte (ALTA/MEDIA/BAJA), redacta el correo y lo envía a `LABCONTROL_ALERTAS_DESTINO` (por defecto `u20232215593@usco.edu.co`). Si n8n no responde, el backend envía el aviso con una plantilla. El coordinador ve las novedades, la prioridad y la acción sugerida en **Inventario**. Configuración: [n8n/README.md](n8n/README.md).
- **Publicar en la web:** `docker-compose.prod.yml` levanta MySQL, la API, el frontend (nginx) y n8n. Paso a paso: [DESPLIEGUE.md](DESPLIEGUE.md).

| Variable nueva | Para qué |
|---|---|
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Envío de correos (Gmail: `smtp.gmail.com`, `587`, contraseña de aplicación) |
| `LABCONTROL_ALERTAS_DESTINO` | Correo que recibe los avisos de novedades |
| `N8N_WEBHOOK_URL`, `N8N_WEBHOOK_TOKEN` | Flujo de n8n y token compartido (credencial Header Auth `X-LabControl-Token`) |
| `LABCONTROL_APP_URL` | Dirección pública del frontend (para los enlaces de los correos) |

## Sedes, presupuesto y fotografías (v1.2)

### Una dirección por sede
| Dirección | Qué muestra |
|---|---|
| `http://localhost:5173/` | «Elige tu sede» (solo sedes activas) |
| `http://localhost:5173/neiva/login` | Ingreso a la Sede Neiva |
| `http://localhost:5173/pitalito/login` | Ingreso a la Sede Pitalito (cuando el coordinador la cree) |
| `http://localhost:5173/garzon/coordinador/presupuesto` | Presupuesto de la Sede Garzón |

- Cada estudiante, docente y auxiliar **pertenece a una sede**: solo ve el inventario, los préstamos y el historial de esa sede. Si intenta ingresar por la dirección de otra, el sistema le dice a qué sede pertenece.
- El **Coordinador general** (rol COORDINADOR) administra todas las sedes y cambia entre ellas con el selector del encabezado; las pantallas conservan la sección (Inventario, Usuarios, Presupuesto…).
- El frontend envía la sede en la cabecera `X-Sede`; el servidor solo la respeta para el coordinador. A los demás siempre les aplica su propia sede.
- **Sede principal:** `03_datos_iniciales.sql` crea la *Sede Neiva* y le asigna los equipos y usuarios de ejemplo.

### Coordinador → Sedes
Crear sede (ciudad → dirección web, p. ej. «La Plata» → `/la-plata`), editar nombre y dirección física, activar/desactivar (desactivar no borra datos; impide el ingreso de sus usuarios). Los usuarios nuevos se crean en la sede que está abierta.

### Coordinador → Presupuesto
Un presupuesto por sede. **Ingreso** suma y **Gasto** resta; no se permite un gasto mayor al saldo. Cada movimiento lleva concepto, categoría, valor en COP, fecha (no futura) y placa opcional del equipo. Los movimientos **no se borran**: se anulan con motivo y quedan visibles tachados. Incluye gastos por categoría y el saldo de todas las sedes.

### Fotografía desde el computador o el celular
En **Inventario → Nuevo / Editar equipo**, «Subir fotografía» abre el explorador de archivos (en el celular: galería o cámara). También se puede arrastrar la imagen. Antes de subirla el navegador la reduce a 1600 px en JPEG, así una foto de celular pesa unos cientos de KB.
El servidor acepta JPG, PNG o WEBP de hasta 5 MB, verifica el tipo real del archivo y la guarda en MySQL (tabla `imagenes`); se sirve en `/api/imagenes/{id}`. La opción «O usar una URL» sigue disponible.

## Fotografías de los equipos

Están en `frontend/public/images/equipos/usco-eq-XXX.jpg` y provienen de Wikimedia Commons (licencias libres, ver `CREDITOS.md` en esa carpeta).
Son fotografías de equipos **de la misma marca o tipo**, no necesariamente de la unidad física del inventario USCO. `USCO-EQ-402` (Tensiómetro de humedad de suelo) usa una fotografía de un equipo Irrometer; `USCO-EQ-201` (Balanza de lodos presurizada) usa una
**ilustración referencial** propia (`ilustraciones/usco-eq-201.svg`), porque no existe una fotografía libre del equipo. Para usar la foto real de cualquier unidad basta
copiarla como `usco-eq-XXX.jpg` en esa carpeta, o cambiar la URL desde **Coordinador → Inventario → Editar**. Si un equipo no tiene imagen, la interfaz muestra «Fotografía no disponible».
Los números de serie y ubicaciones de la carga inicial son datos de referencia: reemplázalos por los del inventario físico desde la misma pantalla.

## Trazabilidad con los documentos del proyecto

| Requisito (QA) | Dónde está |
|---|---|
| RF-01 Autenticación y roles · PF-01..03 · PV-01/02 · PS-04/06/08/09 | `pages/Login.jsx`, `context/AuthContext.jsx`, `security/*`, `AuthService` |
| RF-02 Catálogo y filtros · PF-04/05 · PV-07 · PU-01/02/15 | `pages/usuario/Catalogo.jsx`, `FichaEquipo.jsx` |
| RF-03 Solicitud de préstamo · PF-06 · PV-03/05 · PU-04 | `SolicitudModal.jsx`, `PrestamoService.reservar` |
| RF-04 Ventanilla / salida · PF-07/08 · PV-04 · PU-06/07 | `pages/auxiliar/Ventanilla.jsx`, `SalidaModal.jsx`, `PrestamoService.salida` |
| RF-05 Devolución / novedad · PF-09/10 · PV-06/10 · PS-07 | `EntradaModal.jsx`, `PrestamoService.entrada` |
| RF-06 Historial de usuario · PF-11 | `pages/usuario/MiHistorial.jsx` |
| RF-07 Cierre de turno y baja · PF-12 · PV-08/09 · PS-08 | `CierreTurno.jsx`, `Inventario.jsx`, `CoordinadorService.baja` |
| RF-08 Reportes · PF-14 · PS-10 · PA-10 | `pages/coordinador/Dashboard.jsx`, `HistorialInmutable.jsx`, `reporteCsv` |
| Navegación global PF-13 (logo → panel del rol) | `components/Shell.jsx` (`LogoUsco`) |
| PA-07 color funcional (azul = salida, verde = entrada) | `tailwind.config.js`, `components/ui.jsx` |
| PS-02/PS-03 acceso restringido por rol | `RequiereRol` en `App.jsx` + `SecurityConfig` |
| RNF-02 Confiabilidad (0 préstamos duplicados) | `EquipoRepository.findActivoForUpdate` + prueba de concurrencia |

Notas de alineación con el QA: `USCO-EQ-102` es el Osciloscopio Digital; el Multímetro Fluke 179 es `USCO-EQ-103` (el informe QA usa `-102` para ambos).

## Accesibilidad y modo oscuro

El botón «Registrar Entrada» usa el verde oscuro `#047857` (5,5:1 con texto blanco, WCAG AA); el verde `#059669` original daba 3,77:1. El resto de la paleta cumple
(texto/blanco 10,3:1 · blanco/vino 9,2:1 · blanco/azul salida 10,4:1). La interfaz ofrece **modo claro y oscuro** (botón sol/luna en el encabezado y en el login);
la primera vez sigue la preferencia del sistema y luego recuerda la elección del usuario.

## Pruebas

```bash
cd backend  && mvn test     # 13 pruebas de integración (H2 en modo MySQL; incluye concurrencia con 6 hilos)
cd frontend && npm test     # 7 pruebas de utilidades y validaciones
```

La aplicación en ejecución se verificó además con un recorrido de extremo a extremo contra MySQL real (login por rol, RBAC por HTTP, solicitud → rechazo/entrega → devolución con daños, CRUD de equipos y usuarios).

## Estructura

```
backend/   API REST (Spring Boot) · domain, repo, service, security, web
frontend/  SPA React · api, pages por rol, components, context · public/images/equipos
docker-compose.yml  MySQL 8 · iniciar.ps1  arranque completo · iniciar-mysql.ps1  MySQL portátil
```
