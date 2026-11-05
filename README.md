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

> Estas son las **contraseñas iniciales** de la carga de datos (`DataInitializer.java`). **Cámbialas antes de publicar el sistema fuera de la red de pruebas.**
> Las cuentas solo se crean si la tabla `usuarios` está vacía.

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
.\iniciar-mysql.ps1                       # instancia portátil en el puerto 3307 (no toca otro MySQL del equipo)
docker compose up -d                      # o MySQL en Docker (cp .env.example .env; usa MYSQL_URL con el puerto 3306)

# 2) API Spring Boot (puerto 8080) — crea las tablas y carga usuarios/equipos la primera vez
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

Para empezar de cero: `DROP DATABASE labcontrol; CREATE DATABASE labcontrol CHARACTER SET utf8mb4;` y reiniciar la API.
Opcional: `mysql -u root -p labcontrol < backend/db/inmutabilidad.sql` agrega triggers que impiden UPDATE/DELETE en el historial.

## Funcionalidades por rol (todas contra MySQL)

- **Estudiante / Docente:** catálogo con fotografías y filtros instantáneos, **ficha técnica completa** (foto, marca, modelo, serie, ubicación exacta, estado físico, accesorios, disponibilidad), solicitud de préstamo (fecha, franja, asignatura, docente), **Mis reservas** (cancelar pendientes, ver rechazos), **Mi historial**, alertas de devolución vencida y **Mi cuenta** (cambio de contraseña).
- **Auxiliar:** ventanilla con búsqueda por código estudiantil o documento, **aprobar y registrar salida** (azul) o **rechazar** con motivo, **registrar entrada** (verde) con checklist de estado físico y advertencia disciplinaria, alertas de retraso, **inventario** (enviar a mantenimiento / reactivar) y **cierre de turno** con transferencia de custodia.
- **Coordinador:** dashboard con KPIs y gráficos, **historial inmutable** con filtros y exportación CSV sanitizada, **CRUD de equipos** (crear, editar, mantenimiento, baja lógica) y **gestión de usuarios** (crear con rol, activar/desactivar, restablecer contraseña).
- **Seguridad:** login único por correo `@usco.edu.co` y contraseña, JWT, BCrypt, autorización por rol en el servidor (`SecurityConfig`) y en el cliente (`RequiereRol`), bloqueo temporal tras 5 intentos fallidos, mensajes de error genéricos, el rol y el estado de la cuenta se verifican en la base de datos en **cada** petición, cierre de sesión por inactividad (sin «Recordar mi sesión»).
- **Concurrencia:** bloqueo pesimista sobre el equipo (`SELECT … FOR UPDATE`): cero préstamos duplicados.

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
