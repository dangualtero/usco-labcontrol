# Roles, permisos y módulos de USCO LabControl (v2.0)

LabControl tiene **3 roles** y **8 módulos**, tal como la hoja de requisitos. Cada acción la valida el servidor según la
**matriz de permisos**; la interfaz solo muestra lo que la cuenta puede hacer.

| Rol | Alcance |
|---|---|
| **Administrador general** | Control total de los 8 módulos, en **todos los laboratorios y todas las sedes** (cambia de sede con el selector del encabezado). Siempre tiene todos los permisos. |
| **Coordinador de Laboratorio** | Docente responsable de **uno o varios laboratorios asignados**. Solo ve y modifica la información de esos laboratorios. |
| **Monitor de Laboratorio** | Apoyo operativo **bajo supervisión del coordinador**, en **uno o varios laboratorios asignados**. |

La asignación se hace en **Administración → Usuarios y roles**: rol + laboratorios (con casillas, se pueden marcar varios).
Un coordinador o monitor sin laboratorios no puede ingresar.

## Módulos y lo que puede hacer cada rol

| Módulo | Administrador general | Coordinador de Laboratorio | Monitor de Laboratorio |
|---|---|---|---|
| **Tablero de control y parametrización** | Exclusivo: crea laboratorios y usuarios, asigna roles y permisos, configura catálogos, umbrales de alertas y plantillas de correo | Tablero con indicadores de sus laboratorios | Tablero con indicadores de sus laboratorios |
| **Laboratorios (caracterización y documentación)** | Consulta y administra todos | Consulta y actualiza los datos de su laboratorio; sube documentos y evidencias | Consulta |
| **Inventarios y mantenimientos** | Consulta y administra todo | Registra y edita bienes, actualiza la información operativa y la hoja de vida, **valida las novedades del monitor**, registra y programa mantenimientos | Consulta inventario, hojas de vida y mantenimientos programados; actualiza la información operativa; **registro preliminar** de fallas, novedades, existencias y consumos con fotos y soportes; registra requerimientos de mantenimiento |
| **Solicitudes y requerimientos** | Consolida necesidades, revisa, **devuelve para ajuste**, prioriza, aprueba o rechaza y cierra | Crea, edita y **envía** con justificación, especificaciones, prioridad propuesta y valor estimado; adjunta cotizaciones, fotos y soportes; **valida** lo que preparó el monitor; atiende devoluciones | Prepara borradores y reúne evidencias; al «enviar», la solicitud pasa **al coordinador** (no al Administrador) |
| **Presupuestos y contratación** | Registra y actualiza la información presupuestal (rubro, fuente, CDP, valor aprobado) y contractual (modalidad, contrato, contratista, valor, estado) de las solicitudes; presupuesto de cada sede | Consulta el estado presupuestal y contractual de sus solicitudes | — |
| **Reportes y seguimiento** | Genera y exporta reportes institucionales; verifica avances, indicadores y compromisos de todos los laboratorios | Consulta y genera reportes de su laboratorio; registra compromisos, avances y evidencias | Consulta y genera reportes; registra avances y evidencias |

## Flujos principales

### Novedades (registro preliminar → validación)
```
Monitor registra la novedad (fotos, observaciones, soportes) ──► POR VALIDAR ──► correo al coordinador
Coordinador valida ──► se aplica al inventario + aviso por correo (n8n)       Coordinador rechaza ──► sin efectos
```
Efectos al validar: *requiere mantenimiento* → el equipo pasa a «En mantenimiento» y se crea el mantenimiento
**correctivo requerido**; *perdido* → «Fuera de servicio»; *agotado* → existencias en 0; *conteo de existencias* → se
ajusta la cantidad; *consumo* → se descuenta. Si quien registra ya puede validar (coordinador), la novedad queda validada
de una vez.

### Solicitudes
```
BORRADOR ──(monitor «envía»)──► EN REVISIÓN DEL COORDINADOR ──(coordinador valida y envía)──► ENVIADA AL ADMINISTRADOR
   │                                    │                                                       │
   └──(coordinador «envía»)─────────────┴───────────────────────────────────────────────────────┤
                                                                                                ▼
                         DEVUELTA PARA AJUSTE ◄──(Administrador o coordinador)        APROBADA / RECHAZADA ──► CERRADA
```
Para enviarla al Administrador la solicitud debe tener justificación (mín. 20 caracteres), especificaciones técnicas,
prioridad propuesta y valor estimado. Cada paso queda en la **trazabilidad** de la solicitud y se avisa por correo.

### Mantenimientos
`REQUERIDO` (sin fecha) → `PROGRAMADO` (con fecha, responsable y costo) → `EN EJECUCIÓN` (el equipo pasa a mantenimiento)
→ `REALIZADO` (vuelve a «Operativo», o a «Fuera de servicio» si no quedó funcionando) · `CANCELADO`.

## Matriz de permisos (valores por defecto)

El Administrador puede cambiar los permisos de coordinador y monitor en **Parametrización → Permisos por rol**
(«Restablecer hoja de roles» vuelve a estos valores). Los marcados *Solo Administrador* no se pueden delegar.

| Permiso | Coordinador | Monitor |
|---|:-:|:-:|
| Ver el tablero | ✔ | ✔ |
| Parametrizar · Gestionar usuarios · Gestionar laboratorios · Gestionar sedes | Solo Administrador | Solo Administrador |
| Consultar la caracterización del laboratorio | ✔ | ✔ |
| Editar la caracterización y subir documentos | ✔ | — |
| Consultar el inventario y las hojas de vida | ✔ | ✔ |
| Registrar y editar bienes, darlos de baja | ✔ | — |
| Actualizar la información operativa | ✔ | ✔ |
| Registrar novedades (registro preliminar) | ✔ | ✔ |
| Validar novedades | ✔ | — |
| Registrar requerimientos de mantenimiento | ✔ | ✔ |
| Programar y cerrar mantenimientos | ✔ | — |
| Ver el historial (trazabilidad) | ✔ | — |
| Crear y editar solicitudes | ✔ | ✔ |
| Enviar solicitudes al Administrador | ✔ | — *(configurable)* |
| Validar solicitudes preparadas por el monitor | ✔ | — |
| Revisar, priorizar, decidir y cerrar solicitudes | Solo Administrador | Solo Administrador |
| Consultar presupuesto y contratación de sus solicitudes | ✔ | — |
| Registrar presupuesto y contratación | Solo Administrador | Solo Administrador |
| Generar y exportar reportes | ✔ | ✔ |
| Registrar avances y evidencias | ✔ | ✔ |
| Gestionar compromisos | ✔ | — |
| Verificar avances | Solo Administrador | Solo Administrador |
| Ver alertas | ✔ | ✔ |

## Ajustes y mejoras a los requisitos de la hoja

Estos puntos completan o aclaran la hoja original:

1. **Contradicción del monitor en «Solicitudes».** La hoja dice que el monitor «prepara borradores; el coordinador valida y
   envía», pero en otra fila le asigna «crea, edita y envía». Se resolvió así: el monitor sí puede crear, editar y pulsar
   «Enviar», pero su envío llega **al coordinador** (estado *En revisión del coordinador*), que la valida y la envía al
   Administrador. Si la institución decide que el monitor envíe directo, basta activar «Enviar solicitudes al
   Administrador» para el monitor en Parametrización.
2. **Validación de novedades.** La hoja copia para el monitor la fila del coordinador «valida novedades reportadas por el
   monitor». No tiene sentido que el monitor valide sus propios reportes, así que ese permiso queda solo en el coordinador.
   El monitor conserva la actualización de la información operativa.
3. **Novedades de existencias y consumos.** El «registro preliminar … stock de insumos» se implementó con dos tipos de
   novedad nuevos: *Conteo de existencias* (ajusta la cantidad al validarse) y *Consumo de insumo* (la descuenta), con
   conversión de unidades (p. ej. 500 mL de un insumo que se guarda en litros).
4. **Mantenimientos con ciclo de vida.** «Registra requerimientos de mantenimiento» se completó con el ciclo
   requerido → programado → en ejecución → realizado/cancelado, con responsable, costo, resultado y efecto sobre el estado
   del equipo; una novedad validada que pide mantenimiento crea el correctivo automáticamente.
5. **Nuevo estado «Fuera de servicio».** Además de «Operativo» y «En mantenimiento», para equipos perdidos o que no
   quedaron funcionando.
6. **Compromisos.** La hoja menciona «verifica compromisos» sin definirlos: un compromiso tiene laboratorio (o toda la
   sede, solo el Administrador), responsable, fecha límite, estado y avances con evidencias; vence automáticamente.
7. **Verificación de avances.** Cada avance queda *por verificar* hasta que el Administrador lo marca *verificado* o *con
   observaciones* (obligatoria la observación).
8. **Priorización explícita.** El Administrador asigna prioridad (puede diferir de la propuesta) y un **orden** que define
   la lista de priorización de las solicitudes enviadas.
9. **Presupuesto conectado con contratación.** Al registrar la contratación se puede crear, una sola vez, el gasto en el
   presupuesto de la sede, enlazado a la solicitud (con control de saldo).
10. **Privacidad del presupuesto.** Quien no tiene permiso de presupuesto (monitor) no ve valores aprobados, datos
    contractuales ni documentos de tipo *Contrato*.
11. **Archivos privados.** Documentos, cotizaciones, fotos de novedades y evidencias (PDF, JPG, PNG, WEBP, Word, Excel,
    máx. 10 MB) se verifican por su contenido real y solo se descargan con sesión y si el laboratorio es visible para la
    cuenta.
12. **Alertas y avisos configurables.** Umbral de existencias, días de anticipación de mantenimientos, días sin validar
    una novedad y días sin decisión de una solicitud se configuran en Parametrización; los correos usan plantillas
    editables con variables (`{{codigo}}`, `{{laboratorio}}`, …) y se pueden desactivar.
13. **Catálogos configurables.** Categorías de equipos, estados físicos, tipos de solicitud, tipos de documento, fuentes
    de financiación y modalidades de contratación ya no están fijos en el código.
14. **Trazabilidad completa.** Toda acción sobre el inventario queda en el historial inmutable; las solicitudes tienen su
    propia línea de tiempo (quién, qué, cuándo, estado anterior y nuevo, comentario).
15. **Asignación múltiple.** Un coordinador o monitor puede tener varios laboratorios; su vista combina todos.

## Migración desde la versión anterior

`backend/db/06_roles_y_modulos.sql` convierte una base existente sin perder datos:

| Rol anterior | Rol nuevo |
|---|---|
| Coordinador (general) | Administrador general |
| Docente | Coordinador de Laboratorio |
| Auxiliar | Monitor de Laboratorio |
| Estudiante | Monitor de Laboratorio **desactivado** (el Administrador decide a quién activar) |

A los coordinadores y monitores se les asignan **todos los laboratorios activos de su sede**; ajústalos después en
Usuarios. Los préstamos ya no existen como módulo: sus tablas se conservan (hay un `DROP` opcional comentado al final del
script) y los eventos antiguos siguen en el historial.
