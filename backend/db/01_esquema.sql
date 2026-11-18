-- =====================================================================================================
-- USCO LabControl · 01 · Esquema completo de la base de datos (MySQL 8.0.16 o superior) · v2.0
--
-- Este script es la ÚNICA fuente de la estructura: tablas, tipos, longitudes, obligatoriedad, valores
-- permitidos (CHECK), llaves primarias, únicas y foráneas, e índices. El backend NO crea ni modifica tablas
-- (spring.jpa.hibernate.ddl-auto = none); solo lee y escribe datos.
--
-- Roles: ADMINISTRADOR (general), COORDINADOR_LAB (docente responsable de uno o varios laboratorios) y
--        MONITOR (apoyo operativo en uno o varios laboratorios).
--
-- Orden de ejecución:  00_usuario_mysql.sql (una vez, como root) → 01_esquema.sql → 02_inmutabilidad.sql
--                      → 03_datos_iniciales.sql
-- ⚠ Borra y vuelve a crear todas las tablas: ejecútalo solo para crear la base desde cero.
--   Para actualizar una base que ya existe usa 06_roles_y_modulos.sql.
-- =====================================================================================================
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS labcontrol CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE labcontrol;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS avance_archivos, avances, compromisos, solicitud_eventos, solicitud_archivos, solicitudes,
                     mantenimientos, novedad_archivos, seguimiento_novedad, reportes_novedad, laboratorio_documentos,
                     usuario_laboratorios, permisos_rol, catalogos, parametros, plantillas, archivos,
                     solicitudes_insumo, registros_pendientes, prestamos, laboratorios, imagenes,
                     presupuesto_movimientos, historial_eventos, equipos, usuarios, sedes;
SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================================================
-- 1. Sedes, usuarios y laboratorios
-- =====================================================================================================

-- Sedes de la Universidad. El slug es el primer segmento de la URL del frontend (/neiva, /pitalito…).
CREATE TABLE sedes (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  slug       VARCHAR(40)  NOT NULL,
  nombre     VARCHAR(80)  NOT NULL,
  ciudad     VARCHAR(60)  NOT NULL,
  direccion  VARCHAR(160) NULL,
  activa     BOOLEAN      NOT NULL DEFAULT TRUE,
  creada_en  DATETIME(6)  NOT NULL,
  CONSTRAINT pk_sedes         PRIMARY KEY (id),
  CONSTRAINT uk_sedes_slug    UNIQUE (slug),
  CONSTRAINT uk_sedes_nombre  UNIQUE (nombre)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Usuarios. El rol lo asigna el Administrador. Contraseñas con BCrypt (en un INSERT manual se puede escribir la
-- contraseña normal en password_hash: el backend la cifra a los pocos segundos).
-- El Administrador es general (sede_id NULL). Coordinadores y monitores pertenecen a la sede de sus laboratorios.
CREATE TABLE usuarios (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  nombre         VARCHAR(120) NOT NULL,
  correo         VARCHAR(120) NOT NULL,
  password_hash  VARCHAR(255) NOT NULL,
  rol            VARCHAR(20)  NOT NULL,
  documento      VARCHAR(20)  NOT NULL,
  programa       VARCHAR(80)  NULL COMMENT 'Cargo, programa o dependencia',
  telefono       VARCHAR(30)  NULL,
  activo         BOOLEAN      NOT NULL DEFAULT TRUE,
  sede_id        BIGINT       NULL,
  CONSTRAINT pk_usuarios            PRIMARY KEY (id),
  CONSTRAINT uk_usuarios_correo     UNIQUE (correo),
  CONSTRAINT uk_usuarios_documento  UNIQUE (documento),
  CONSTRAINT fk_usuarios_sede       FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_usuarios_rol        CHECK (rol IN ('ADMINISTRADOR', 'COORDINADOR_LAB', 'MONITOR')),
  CONSTRAINT ck_usuarios_correo     CHECK (correo LIKE '%@usco.edu.co')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Laboratorios de cada sede, con su caracterización (la actualiza el Coordinador de Laboratorio).
-- Los equipos guardan el NOMBRE del laboratorio (equipos.laboratorio) para que el historial no cambie.
CREATE TABLE laboratorios (
  id                BIGINT         NOT NULL AUTO_INCREMENT,
  sede_id           BIGINT         NOT NULL,
  nombre            VARCHAR(40)    NOT NULL,
  ubicacion         VARCHAR(160)   NULL,
  descripcion       VARCHAR(300)   NULL,
  activo            BOOLEAN        NOT NULL DEFAULT TRUE,
  creado_en         DATETIME(6)    NOT NULL,
  codigo            VARCHAR(20)    NULL COMMENT 'Código institucional del laboratorio',
  area_m2           DECIMAL(8,2)   NULL,
  capacidad         INT            NULL COMMENT 'Personas al mismo tiempo',
  horario           VARCHAR(200)   NULL,
  telefono          VARCHAR(30)    NULL,
  correo            VARCHAR(120)   NULL,
  programas         VARCHAR(500)   NULL COMMENT 'Programas académicos que atiende',
  servicios         VARCHAR(1500)  NULL COMMENT 'Servicios, prácticas y líneas de trabajo',
  normas_seguridad  VARCHAR(2000)  NULL,
  actualizado_en    DATETIME(6)    NULL,
  actualizado_por   VARCHAR(120)   NULL,
  CONSTRAINT pk_laboratorios        PRIMARY KEY (id),
  CONSTRAINT uk_laboratorios_nombre UNIQUE (sede_id, nombre),
  CONSTRAINT fk_laboratorios_sede   FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_laboratorios_datos  CHECK ((area_m2 IS NULL OR area_m2 > 0) AND (capacidad IS NULL OR capacidad > 0))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Laboratorios asignados a cada Coordinador de Laboratorio o Monitor (uno o varios).
CREATE TABLE usuario_laboratorios (
  usuario_id      BIGINT NOT NULL,
  laboratorio_id  BIGINT NOT NULL,
  CONSTRAINT pk_usuario_laboratorios PRIMARY KEY (usuario_id, laboratorio_id),
  CONSTRAINT fk_ul_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuarios (id) ON DELETE CASCADE,
  CONSTRAINT fk_ul_laboratorio FOREIGN KEY (laboratorio_id) REFERENCES laboratorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================
-- 2. Parametrización (Tablero de control): permisos por rol, catálogos, parámetros de alertas y plantillas
-- =====================================================================================================

-- Permisos de cada rol. El Administrador siempre tiene todos; aquí se guardan los de COORDINADOR_LAB y MONITOR.
-- Si falta una fila, se usa el valor por defecto del sistema (el de la hoja de roles).
CREATE TABLE permisos_rol (
  rol         VARCHAR(20)  NOT NULL,
  permiso     VARCHAR(40)  NOT NULL,
  habilitado  BOOLEAN      NOT NULL,
  CONSTRAINT pk_permisos_rol PRIMARY KEY (rol, permiso),
  CONSTRAINT ck_permisos_rol CHECK (rol IN ('COORDINADOR_LAB', 'MONITOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Listas desplegables configurables: categorías de equipos, estados físicos, tipos de solicitud, tipos de documento,
-- fuentes de financiación y modalidades de contratación.
CREATE TABLE catalogos (
  id      BIGINT       NOT NULL AUTO_INCREMENT,
  tipo    VARCHAR(30)  NOT NULL,
  valor   VARCHAR(80)  NOT NULL,
  orden   INT          NOT NULL DEFAULT 0,
  activo  BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_catalogos       PRIMARY KEY (id),
  CONSTRAINT uk_catalogos_valor UNIQUE (tipo, valor),
  CONSTRAINT ck_catalogos_tipo  CHECK (tipo IN ('CATEGORIA_EQUIPO', 'ESTADO_FISICO', 'TIPO_SOLICITUD', 'TIPO_DOCUMENTO',
                                                'FUENTE_FINANCIACION', 'MODALIDAD_CONTRATACION'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Parámetros del sistema (umbrales de alertas, plazos…). Clave → valor.
CREATE TABLE parametros (
  clave        VARCHAR(60)   NOT NULL,
  valor        VARCHAR(200)  NOT NULL,
  descripcion  VARCHAR(300)  NULL,
  CONSTRAINT pk_parametros PRIMARY KEY (clave)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Plantillas de los correos automáticos. Admiten variables como {{codigo}}, {{titulo}}, {{laboratorio}}, {{enlace}}.
CREATE TABLE plantillas (
  codigo   VARCHAR(40)   NOT NULL,
  nombre   VARCHAR(120)  NOT NULL,
  asunto   VARCHAR(200)  NOT NULL,
  cuerpo   VARCHAR(4000) NOT NULL,
  activa   BOOLEAN       NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_plantillas PRIMARY KEY (codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================
-- 3. Archivos (documentos, cotizaciones, fotografías y evidencias)
-- =====================================================================================================

-- Fotografías de los equipos (públicas: se muestran en el inventario en /api/imagenes/{id}).
CREATE TABLE imagenes (
  id               VARCHAR(32)  NOT NULL COMMENT 'UUID sin guiones',
  tipo_contenido   VARCHAR(20)  NOT NULL,
  datos            LONGBLOB     NOT NULL,
  tamano           BIGINT       NOT NULL,
  nombre_original  VARCHAR(160) NULL,
  subida_por       VARCHAR(120) NOT NULL,
  subida_en        DATETIME(6)  NOT NULL,
  CONSTRAINT pk_imagenes        PRIMARY KEY (id),
  CONSTRAINT ck_imagenes_tipo   CHECK (tipo_contenido IN ('image/jpeg', 'image/png', 'image/webp')),
  CONSTRAINT ck_imagenes_tamano CHECK (tamano > 0 AND tamano <= 5242880)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Archivos privados (solo con sesión): PDF, imágenes, Word y Excel de hasta 10 MB.
CREATE TABLE archivos (
  id               VARCHAR(32)   NOT NULL COMMENT 'UUID sin guiones',
  nombre           VARCHAR(180)  NOT NULL,
  tipo_contenido   VARCHAR(100)  NOT NULL,
  tamano           BIGINT        NOT NULL,
  datos            LONGBLOB      NOT NULL,
  subido_por       VARCHAR(120)  NOT NULL,
  subido_en        DATETIME(6)   NOT NULL,
  CONSTRAINT pk_archivos        PRIMARY KEY (id),
  CONSTRAINT ck_archivos_tamano CHECK (tamano > 0 AND tamano <= 10485760),
  CONSTRAINT ck_archivos_tipo   CHECK (tipo_contenido IN ('application/pdf', 'image/jpeg', 'image/png', 'image/webp',
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Documentos del laboratorio (caracterización y documentación): manuales, protocolos, certificados, evidencias…
CREATE TABLE laboratorio_documentos (
  id              BIGINT        NOT NULL AUTO_INCREMENT,
  laboratorio_id  BIGINT        NOT NULL,
  tipo            VARCHAR(80)   NOT NULL,
  titulo          VARCHAR(160)  NOT NULL,
  descripcion     VARCHAR(500)  NULL,
  archivo_id      VARCHAR(32)   NOT NULL,
  subido_por      VARCHAR(120)  NOT NULL,
  subido_en       DATETIME(6)   NOT NULL,
  CONSTRAINT pk_laboratorio_documentos PRIMARY KEY (id),
  CONSTRAINT fk_labdoc_laboratorio     FOREIGN KEY (laboratorio_id) REFERENCES laboratorios (id),
  CONSTRAINT fk_labdoc_archivo         FOREIGN KEY (archivo_id)     REFERENCES archivos (id),
  INDEX idx_labdoc_laboratorio (laboratorio_id, subido_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================
-- 4. Inventario, novedades y mantenimientos
-- =====================================================================================================

-- Inventario: EQUIPOS (bienes con hoja de vida; placa USCO-EQ-000) e INSUMOS (se consumen; placa USCO-IN-000).
-- cantidad + unidad: UNIDAD (piezas), ML / L (líquidos), MG / G / KG (sólidos), CM / M (longitud).
-- cantidad_referencia = cantidad «llena» (100 %): se alerta según el umbral de Parametrización (25 % por defecto).
-- estado: DISPONIBLE (operativo), MANTENIMIENTO o FUERA_SERVICIO. Las bajas son lógicas (activo = FALSE).
CREATE TABLE equipos (
  id                   BIGINT         NOT NULL AUTO_INCREMENT,
  placa                VARCHAR(20)    NOT NULL,
  nombre               VARCHAR(120)   NOT NULL,
  marca                VARCHAR(60)    NULL,
  modelo               VARCHAR(60)    NULL,
  numero_serie         VARCHAR(60)    NULL,
  categoria            VARCHAR(80)    NOT NULL,
  laboratorio          VARCHAR(40)    NOT NULL,
  ubicacion            VARCHAR(160)   NULL,
  descripcion          VARCHAR(500)   NULL,
  specs                VARCHAR(600)   NULL COMMENT 'Especificaciones separadas por |',
  accesorios           VARCHAR(600)   NULL COMMENT 'Accesorios separados por |',
  estado_fisico        VARCHAR(80)    NULL DEFAULT 'Bueno',
  foto_url             VARCHAR(400)   NULL,
  estado               VARCHAR(20)    NOT NULL DEFAULT 'DISPONIBLE',
  activo               BOOLEAN        NOT NULL DEFAULT TRUE,
  baja_motivo          VARCHAR(300)   NULL,
  baja_fecha           DATETIME(6)    NULL,
  sede_id              BIGINT         NULL,
  tipo                 VARCHAR(10)    NOT NULL DEFAULT 'EQUIPO',
  cantidad             DECIMAL(14,3)  NOT NULL DEFAULT 1,
  unidad               VARCHAR(10)    NOT NULL DEFAULT 'UNIDAD',
  cantidad_referencia  DECIMAL(14,3)  NULL,
  observaciones        VARCHAR(1000)  NULL COMMENT 'Información operativa (la actualizan coordinador y monitor)',
  CONSTRAINT pk_equipos           PRIMARY KEY (id),
  CONSTRAINT uk_equipos_placa     UNIQUE (placa),
  CONSTRAINT fk_equipos_sede      FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_equipos_placa     CHECK ((tipo = 'EQUIPO' AND placa LIKE 'USCO-EQ-___') OR (tipo = 'INSUMO' AND placa LIKE 'USCO-IN-___')),
  CONSTRAINT ck_equipos_tipo      CHECK (tipo IN ('EQUIPO', 'INSUMO')),
  CONSTRAINT ck_equipos_unidad    CHECK (unidad IN ('UNIDAD', 'ML', 'L', 'MG', 'G', 'KG', 'CM', 'M') AND (tipo = 'INSUMO' OR unidad = 'UNIDAD')),
  CONSTRAINT ck_equipos_cantidad  CHECK (cantidad >= 0 AND (cantidad_referencia IS NULL OR cantidad_referencia > 0)),
  CONSTRAINT ck_equipos_estado    CHECK (estado IN ('DISPONIBLE', 'MANTENIMIENTO', 'FUERA_SERVICIO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Novedades (registro preliminar): fallas, daños, pérdidas, conteos de existencias y consumos de insumos.
-- Las que reporta un Monitor quedan PENDIENTE de validación del Coordinador de su laboratorio; al validarlas se
-- aplican (cambio de estado, ajuste de existencias) y se avisa por correo (agente de IA en n8n o plantilla).
CREATE TABLE reportes_novedad (
  id                     BIGINT         NOT NULL AUTO_INCREMENT,
  sede_id                BIGINT         NOT NULL,
  equipo_id              BIGINT         NOT NULL,
  tipo                   VARCHAR(20)    NOT NULL,
  descripcion            VARCHAR(1000)  NOT NULL,
  reportado_por          VARCHAR(120)   NOT NULL,
  reportado_por_correo   VARCHAR(120)   NULL,
  creado_en              DATETIME(6)    NOT NULL,
  en_mantenimiento       BOOLEAN        NOT NULL DEFAULT FALSE COMMENT 'Pide enviar el equipo a mantenimiento al validar',
  cantidad               DECIMAL(14,3)  NULL COMMENT 'Conteo (EXISTENCIAS) o cantidad usada (CONSUMO)',
  unidad                 VARCHAR(10)    NULL,
  validacion             VARCHAR(12)    NOT NULL DEFAULT 'PENDIENTE',
  validado_por           VARCHAR(120)   NULL,
  validado_en            DATETIME(6)    NULL,
  observacion_validacion VARCHAR(600)   NULL,
  prioridad              VARCHAR(10)    NULL,
  asunto_correo          VARCHAR(200)   NULL,
  resumen_ia             VARCHAR(1000)  NULL,
  accion_sugerida        VARCHAR(600)   NULL,
  notificacion           VARCHAR(20)    NOT NULL DEFAULT 'EN_ESPERA',
  notificacion_detalle   VARCHAR(300)   NULL,
  destinatario           VARCHAR(120)   NULL,
  notificado_en          DATETIME(6)    NULL,
  intentos_notificacion  INT            NOT NULL DEFAULT 0,
  estado                 VARCHAR(12)    NOT NULL DEFAULT 'ABIERTO' COMMENT 'Seguimiento: ABIERTO → EN_PROCESO → RESUELTO',
  actualizado_en         DATETIME(6)    NULL,
  CONSTRAINT pk_reportes_novedad     PRIMARY KEY (id),
  CONSTRAINT fk_novedad_sede         FOREIGN KEY (sede_id)   REFERENCES sedes (id),
  CONSTRAINT fk_novedad_equipo       FOREIGN KEY (equipo_id) REFERENCES equipos (id),
  CONSTRAINT ck_novedad_tipo         CHECK (tipo IN ('AGOTADO', 'DANADO', 'PERDIDO', 'FALLA', 'OTRO', 'EXISTENCIAS', 'CONSUMO')),
  CONSTRAINT ck_novedad_cantidad     CHECK (cantidad IS NULL OR cantidad >= 0),
  CONSTRAINT ck_novedad_validacion   CHECK (validacion IN ('PENDIENTE', 'VALIDADA', 'RECHAZADA')),
  CONSTRAINT ck_novedad_prioridad    CHECK (prioridad IS NULL OR prioridad IN ('ALTA', 'MEDIA', 'BAJA')),
  CONSTRAINT ck_novedad_notificacion CHECK (notificacion IN ('EN_ESPERA', 'PENDIENTE', 'ENVIADA_IA', 'ENVIADA_PLANTILLA', 'SIN_CONFIGURAR',
                                                            'FALLIDA', 'PENDIENTE_RESUMEN', 'ENVIADA_RESUMEN', 'NO_APLICA')),
  CONSTRAINT ck_novedad_estado       CHECK (estado IN ('ABIERTO', 'EN_PROCESO', 'RESUELTO')),
  INDEX idx_novedad_sede_fecha (sede_id, creado_en),
  INDEX idx_novedad_validacion (sede_id, validacion)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Fotografías y soportes adjuntos a una novedad.
CREATE TABLE novedad_archivos (
  novedad_id  BIGINT       NOT NULL,
  archivo_id  VARCHAR(32)  NOT NULL,
  CONSTRAINT pk_novedad_archivos PRIMARY KEY (novedad_id, archivo_id),
  CONSTRAINT fk_novarch_novedad  FOREIGN KEY (novedad_id) REFERENCES reportes_novedad (id),
  CONSTRAINT fk_novarch_archivo  FOREIGN KEY (archivo_id) REFERENCES archivos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Seguimiento de cada novedad: cambios de estado y comentarios (solo se agregan, no se editan).
CREATE TABLE seguimiento_novedad (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  reporte_id       BIGINT       NOT NULL,
  fecha            DATETIME(6)  NOT NULL,
  autor            VARCHAR(120) NOT NULL,
  estado_anterior  VARCHAR(12)  NULL,
  estado_nuevo     VARCHAR(12)  NOT NULL,
  comentario       VARCHAR(600) NOT NULL,
  CONSTRAINT pk_seguimiento_novedad PRIMARY KEY (id),
  CONSTRAINT fk_seguimiento_reporte FOREIGN KEY (reporte_id) REFERENCES reportes_novedad (id),
  CONSTRAINT ck_seguimiento_estado  CHECK (estado_nuevo IN ('ABIERTO', 'EN_PROCESO', 'RESUELTO')),
  INDEX idx_seguimiento_reporte (reporte_id, fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Mantenimientos: requerimientos (los registran coordinador o monitor), programación, ejecución y resultado.
CREATE TABLE mantenimientos (
  id                BIGINT         NOT NULL AUTO_INCREMENT,
  codigo            VARCHAR(20)    NOT NULL,
  sede_id           BIGINT         NOT NULL,
  equipo_id         BIGINT         NOT NULL,
  tipo              VARCHAR(15)    NOT NULL,
  estado            VARCHAR(15)    NOT NULL DEFAULT 'REQUERIDO',
  descripcion       VARCHAR(1000)  NOT NULL,
  fecha_programada  DATE           NULL,
  fecha_realizada   DATE           NULL,
  responsable       VARCHAR(160)   NULL COMMENT 'Técnico o proveedor',
  costo             BIGINT         NULL COMMENT 'Pesos colombianos',
  resultado         VARCHAR(1000)  NULL,
  novedad_id        BIGINT         NULL,
  registrado_por    VARCHAR(120)   NOT NULL,
  creado_en         DATETIME(6)    NOT NULL,
  actualizado_en    DATETIME(6)    NULL,
  CONSTRAINT pk_mantenimientos      PRIMARY KEY (id),
  CONSTRAINT uk_mantenimientos_cod  UNIQUE (codigo),
  CONSTRAINT fk_mant_sede           FOREIGN KEY (sede_id)    REFERENCES sedes (id),
  CONSTRAINT fk_mant_equipo         FOREIGN KEY (equipo_id)  REFERENCES equipos (id),
  CONSTRAINT fk_mant_novedad        FOREIGN KEY (novedad_id) REFERENCES reportes_novedad (id),
  CONSTRAINT ck_mant_tipo           CHECK (tipo IN ('PREVENTIVO', 'CORRECTIVO', 'CALIBRACION', 'VERIFICACION')),
  CONSTRAINT ck_mant_estado         CHECK (estado IN ('REQUERIDO', 'PROGRAMADO', 'EN_EJECUCION', 'REALIZADO', 'CANCELADO')),
  CONSTRAINT ck_mant_costo          CHECK (costo IS NULL OR costo >= 0),
  INDEX idx_mant_sede_estado (sede_id, estado, fecha_programada),
  INDEX idx_mant_equipo (equipo_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================
-- 5. Solicitudes y requerimientos, presupuesto y contratación
-- =====================================================================================================

-- Solicitudes de los laboratorios (compras, insumos, mantenimiento, servicios…).
-- Flujo: BORRADOR → EN_REVISION (enviada por un monitor, la valida el coordinador) → ENVIADA (al Administrador)
--        → DEVUELTA (para ajuste) → … → APROBADA o RECHAZADA → CERRADA. CANCELADA solo desde borrador o devuelta.
CREATE TABLE solicitudes (
  id                      BIGINT          NOT NULL AUTO_INCREMENT,
  codigo                  VARCHAR(20)     NOT NULL,
  sede_id                 BIGINT          NOT NULL,
  laboratorio_id          BIGINT          NOT NULL,
  tipo                    VARCHAR(80)     NOT NULL,
  titulo                  VARCHAR(160)    NOT NULL,
  justificacion           VARCHAR(3000)   NOT NULL,
  especificaciones        VARCHAR(3000)   NULL,
  cantidad                DECIMAL(14,3)   NULL,
  unidad_medida           VARCHAR(30)     NULL,
  prioridad_propuesta     VARCHAR(10)     NOT NULL,
  valor_estimado          BIGINT          NULL COMMENT 'Pesos colombianos',
  estado                  VARCHAR(15)     NOT NULL DEFAULT 'BORRADOR',
  creado_por_id           BIGINT          NOT NULL,
  creado_en               DATETIME(6)     NOT NULL,
  actualizado_en          DATETIME(6)     NULL,
  enviado_en              DATETIME(6)     NULL,
  observacion_devolucion  VARCHAR(1000)   NULL,
  prioridad_asignada      VARCHAR(10)     NULL,
  orden_prioridad         INT             NULL,
  decision_motivo         VARCHAR(1000)   NULL,
  decidido_por            VARCHAR(120)    NULL,
  decidido_en             DATETIME(6)     NULL,
  cierre_nota             VARCHAR(1000)   NULL,
  cerrado_en              DATETIME(6)     NULL,
  -- Presupuesto
  rubro                   VARCHAR(120)    NULL,
  fuente_financiacion     VARCHAR(80)     NULL,
  cdp_numero              VARCHAR(40)     NULL COMMENT 'Certificado de disponibilidad presupuestal',
  cdp_fecha               DATE            NULL,
  valor_aprobado          BIGINT          NULL,
  -- Contratación
  modalidad_contratacion  VARCHAR(80)     NULL,
  contrato_numero         VARCHAR(40)     NULL,
  contratista             VARCHAR(160)    NULL,
  valor_contratado        BIGINT          NULL,
  fecha_contrato          DATE            NULL,
  estado_contractual      VARCHAR(15)     NOT NULL DEFAULT 'SIN_INICIAR',
  observacion_contractual VARCHAR(1000)   NULL,
  CONSTRAINT pk_solicitudes            PRIMARY KEY (id),
  CONSTRAINT uk_solicitudes_codigo     UNIQUE (codigo),
  CONSTRAINT fk_solicitudes_sede       FOREIGN KEY (sede_id)        REFERENCES sedes (id),
  CONSTRAINT fk_solicitudes_lab        FOREIGN KEY (laboratorio_id) REFERENCES laboratorios (id),
  CONSTRAINT fk_solicitudes_creador    FOREIGN KEY (creado_por_id)  REFERENCES usuarios (id),
  CONSTRAINT ck_solicitudes_estado     CHECK (estado IN ('BORRADOR', 'EN_REVISION', 'ENVIADA', 'DEVUELTA', 'APROBADA', 'RECHAZADA', 'CERRADA', 'CANCELADA')),
  CONSTRAINT ck_solicitudes_prioridad  CHECK (prioridad_propuesta IN ('ALTA', 'MEDIA', 'BAJA')
                                              AND (prioridad_asignada IS NULL OR prioridad_asignada IN ('ALTA', 'MEDIA', 'BAJA'))),
  CONSTRAINT ck_solicitudes_valores    CHECK ((valor_estimado IS NULL OR valor_estimado >= 0) AND (valor_aprobado IS NULL OR valor_aprobado >= 0)
                                              AND (valor_contratado IS NULL OR valor_contratado >= 0) AND (cantidad IS NULL OR cantidad > 0)),
  CONSTRAINT ck_solicitudes_contrato   CHECK (estado_contractual IN ('SIN_INICIAR', 'EN_PROCESO', 'CONTRATADO', 'EN_EJECUCION', 'LIQUIDADO')),
  INDEX idx_solicitudes_sede_estado (sede_id, estado, creado_en),
  INDEX idx_solicitudes_lab (laboratorio_id, estado)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Cotizaciones, fotografías y soportes de cada solicitud.
CREATE TABLE solicitud_archivos (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  solicitud_id  BIGINT        NOT NULL,
  archivo_id    VARCHAR(32)   NOT NULL,
  tipo          VARCHAR(15)   NOT NULL,
  agregado_por  VARCHAR(120)  NOT NULL,
  agregado_en   DATETIME(6)   NOT NULL,
  CONSTRAINT pk_solicitud_archivos  PRIMARY KEY (id),
  CONSTRAINT fk_solarch_solicitud   FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id),
  CONSTRAINT fk_solarch_archivo     FOREIGN KEY (archivo_id)   REFERENCES archivos (id),
  CONSTRAINT ck_solarch_tipo        CHECK (tipo IN ('COTIZACION', 'FOTOGRAFIA', 'SOPORTE', 'CONTRATO'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Trazabilidad de cada solicitud: cada cambio de estado, priorización, decisión, dato presupuestal o comentario.
CREATE TABLE solicitud_eventos (
  id               BIGINT         NOT NULL AUTO_INCREMENT,
  solicitud_id     BIGINT         NOT NULL,
  fecha            DATETIME(6)    NOT NULL,
  autor            VARCHAR(120)   NOT NULL,
  accion           VARCHAR(30)    NOT NULL,
  estado_anterior  VARCHAR(15)    NULL,
  estado_nuevo     VARCHAR(15)    NULL,
  comentario       VARCHAR(1000)  NULL,
  CONSTRAINT pk_solicitud_eventos PRIMARY KEY (id),
  CONSTRAINT fk_solev_solicitud   FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id),
  INDEX idx_solev_solicitud (solicitud_id, fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Presupuesto por sede: INGRESO suma, GASTO resta. Los movimientos no se borran: se anulan.
CREATE TABLE presupuesto_movimientos (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  sede_id           BIGINT       NOT NULL,
  tipo              VARCHAR(10)  NOT NULL,
  concepto          VARCHAR(160) NOT NULL,
  categoria         VARCHAR(40)  NOT NULL,
  monto             BIGINT       NOT NULL COMMENT 'Pesos colombianos, sin decimales',
  fecha             DATE         NOT NULL,
  placa             VARCHAR(20)  NULL,
  solicitud_id      BIGINT       NULL,
  registrado_por    VARCHAR(120) NOT NULL,
  registrado_en     DATETIME(6)  NOT NULL,
  anulado           BOOLEAN      NOT NULL DEFAULT FALSE,
  anulacion_motivo  VARCHAR(300) NULL,
  anulado_por       VARCHAR(120) NULL,
  anulado_en        DATETIME(6)  NULL,
  CONSTRAINT pk_presupuesto_movimientos PRIMARY KEY (id),
  CONSTRAINT fk_presupuesto_sede        FOREIGN KEY (sede_id)      REFERENCES sedes (id),
  CONSTRAINT fk_presupuesto_solicitud   FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id),
  CONSTRAINT ck_presupuesto_tipo        CHECK (tipo IN ('INGRESO', 'GASTO')),
  CONSTRAINT ck_presupuesto_monto       CHECK (monto > 0 AND monto <= 100000000000),
  CONSTRAINT ck_presupuesto_categoria   CHECK (categoria IN ('Asignación', 'Compra de equipos', 'Mantenimiento', 'Calibración', 'Insumos y repuestos', 'Otro')),
  INDEX idx_presupuesto_sede_fecha (sede_id, fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================
-- 6. Reportes y seguimiento: compromisos, avances y evidencias
-- =====================================================================================================

-- Compromisos de cada laboratorio (o de toda la sede si laboratorio_id es NULL), con responsable y fecha límite.
CREATE TABLE compromisos (
  id              BIGINT         NOT NULL AUTO_INCREMENT,
  sede_id         BIGINT         NOT NULL,
  laboratorio_id  BIGINT         NULL,
  titulo          VARCHAR(160)   NOT NULL,
  descripcion     VARCHAR(1000)  NULL,
  responsable_id  BIGINT         NULL,
  fecha_limite    DATE           NOT NULL,
  estado          VARCHAR(12)    NOT NULL DEFAULT 'PENDIENTE',
  creado_por      VARCHAR(120)   NOT NULL,
  creado_en       DATETIME(6)    NOT NULL,
  actualizado_en  DATETIME(6)    NULL,
  CONSTRAINT pk_compromisos           PRIMARY KEY (id),
  CONSTRAINT fk_compromisos_sede      FOREIGN KEY (sede_id)        REFERENCES sedes (id),
  CONSTRAINT fk_compromisos_lab       FOREIGN KEY (laboratorio_id) REFERENCES laboratorios (id),
  CONSTRAINT fk_compromisos_resp      FOREIGN KEY (responsable_id) REFERENCES usuarios (id),
  CONSTRAINT ck_compromisos_estado    CHECK (estado IN ('PENDIENTE', 'EN_CURSO', 'CUMPLIDO', 'CANCELADO')),
  INDEX idx_compromisos_sede (sede_id, estado, fecha_limite)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Avances registrados por coordinadores y monitores (de un compromiso, de una solicitud o generales del laboratorio).
-- El Administrador los verifica.
CREATE TABLE avances (
  id                        BIGINT         NOT NULL AUTO_INCREMENT,
  sede_id                   BIGINT         NOT NULL,
  laboratorio_id            BIGINT         NOT NULL,
  compromiso_id             BIGINT         NULL,
  solicitud_id              BIGINT         NULL,
  descripcion               VARCHAR(2000)  NOT NULL,
  porcentaje                INT            NULL,
  autor                     VARCHAR(120)   NOT NULL,
  creado_en                 DATETIME(6)    NOT NULL,
  verificacion              VARCHAR(12)    NOT NULL DEFAULT 'PENDIENTE',
  verificado_por            VARCHAR(120)   NULL,
  verificado_en             DATETIME(6)    NULL,
  observacion_verificacion  VARCHAR(600)   NULL,
  CONSTRAINT pk_avances              PRIMARY KEY (id),
  CONSTRAINT fk_avances_sede         FOREIGN KEY (sede_id)        REFERENCES sedes (id),
  CONSTRAINT fk_avances_lab          FOREIGN KEY (laboratorio_id) REFERENCES laboratorios (id),
  CONSTRAINT fk_avances_compromiso   FOREIGN KEY (compromiso_id)  REFERENCES compromisos (id),
  CONSTRAINT fk_avances_solicitud    FOREIGN KEY (solicitud_id)   REFERENCES solicitudes (id),
  CONSTRAINT ck_avances_porcentaje   CHECK (porcentaje IS NULL OR porcentaje BETWEEN 0 AND 100),
  CONSTRAINT ck_avances_verificacion CHECK (verificacion IN ('PENDIENTE', 'VERIFICADO', 'OBSERVADO')),
  INDEX idx_avances_lab (laboratorio_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Evidencias (archivos) de cada avance.
CREATE TABLE avance_archivos (
  avance_id   BIGINT       NOT NULL,
  archivo_id  VARCHAR(32)  NOT NULL,
  CONSTRAINT pk_avance_archivos PRIMARY KEY (avance_id, archivo_id),
  CONSTRAINT fk_avarch_avance   FOREIGN KEY (avance_id)  REFERENCES avances (id),
  CONSTRAINT fk_avarch_archivo  FOREIGN KEY (archivo_id) REFERENCES archivos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =====================================================================================================
-- 7. Historial inmutable: solo se inserta (ver 02_inmutabilidad.sql). Los datos se copian (sin llaves foráneas)
--    para que el registro no cambie aunque después se edite el equipo o el usuario.
--    referencia = código de la solicitud, mantenimiento o novedad relacionada.
-- =====================================================================================================
CREATE TABLE historial_eventos (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  fecha            DATETIME(6)  NOT NULL,
  tipo             VARCHAR(20)  NOT NULL COMMENT 'ALTA, ESTADO, BAJA, NOVEDAD, VALIDACION, CONSUMO, EXISTENCIAS, MANTENIMIENTO, SEGUIMIENTO, OPERATIVO…',
  placa            VARCHAR(20)  NULL,
  equipo_nombre    VARCHAR(120) NULL,
  laboratorio      VARCHAR(40)  NULL,
  prestamo_codigo  VARCHAR(20)  NULL COMMENT 'Referencia (solicitud, mantenimiento, novedad…)',
  usuario_nombre   VARCHAR(120) NULL COMMENT 'Persona involucrada',
  auxiliar_nombre  VARCHAR(120) NULL COMMENT 'Quién registró el evento',
  detalle          VARCHAR(600) NULL,
  sede             VARCHAR(40)  NULL COMMENT 'Slug de la sede del equipo; NULL = sede principal',
  CONSTRAINT pk_historial_eventos PRIMARY KEY (id),
  INDEX idx_hist_fecha (fecha),
  INDEX idx_hist_placa (placa, fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
