-- =====================================================================================================
-- USCO LabControl · 06 · Roles nuevos y módulos de gestión de laboratorios (v2.0)
--
-- Para una base de datos que YA EXISTE (v1.4, con 05_insumos_laboratorios_reportes.sql aplicado).
-- Se puede ejecutar varias veces: cada cambio revisa primero si ya está hecho.
-- Ejecútalo como root:   mysql -uroot -p labcontrol   y luego   source ruta/06_roles_y_modulos.sql
--
-- Qué hace con los usuarios que ya existen:
--   COORDINADOR → ADMINISTRADOR (general, sin sede)
--   DOCENTE     → COORDINADOR_LAB (queda asignado a todos los laboratorios activos de su sede)
--   AUXILIAR    → MONITOR         (queda asignado a todos los laboratorios activos de su sede)
--   ESTUDIANTE  → MONITOR DESACTIVADO (el Administrador decide a quién activar y qué laboratorios asignarle)
-- Las tablas de préstamos (prestamos, solicitudes_insumo, registros_pendientes) ya no se usan, pero NO se borran:
-- al final hay una sección comentada para borrarlas cuando quieras.
-- =====================================================================================================
SET NAMES utf8mb4;
USE labcontrol;

-- =========================== 1. Usuarios: roles nuevos ===========================
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND constraint_name = 'ck_usuarios_rol');
SET @sql = IF(@existe, 'ALTER TABLE usuarios DROP CHECK ck_usuarios_rol', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND constraint_name = 'ck_usuarios_bcrypt');
SET @sql = IF(@existe, 'ALTER TABLE usuarios DROP CHECK ck_usuarios_bcrypt', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
UPDATE usuarios SET rol = 'ADMINISTRADOR', sede_id = NULL WHERE rol = 'COORDINADOR';
UPDATE usuarios SET rol = 'COORDINADOR_LAB' WHERE rol = 'DOCENTE';
UPDATE usuarios SET rol = 'MONITOR' WHERE rol = 'AUXILIAR';
UPDATE usuarios SET rol = 'MONITOR', activo = FALSE WHERE rol = 'ESTUDIANTE';
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND constraint_name = 'ck_usuarios_rol');
SET @sql = IF(@existe, 'ALTER TABLE usuarios DROP CHECK ck_usuarios_rol', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_rol CHECK (rol IN ('ADMINISTRADOR', 'COORDINADOR_LAB', 'MONITOR'));
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND column_name = 'telefono');
SET @sql = IF(@falta, 'ALTER TABLE usuarios ADD COLUMN telefono VARCHAR(30) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
-- El código estudiantil ya no se usa
SET @existe = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND index_name = 'uk_usuarios_codigo');
SET @sql = IF(@existe, 'ALTER TABLE usuarios DROP INDEX uk_usuarios_codigo', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @existe = (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND column_name = 'codigo_estudiantil');
SET @sql = IF(@existe, 'ALTER TABLE usuarios DROP COLUMN codigo_estudiantil', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- =========================== 2. Laboratorios: caracterización y asignaciones ===========================
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'codigo');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN codigo VARCHAR(20) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'area_m2');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN area_m2 DECIMAL(8,2) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'capacidad');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN capacidad INT NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'horario');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN horario VARCHAR(200) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'telefono');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN telefono VARCHAR(30) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'correo');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN correo VARCHAR(120) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'programas');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN programas VARCHAR(500) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'servicios');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN servicios VARCHAR(1500) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'normas_seguridad');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN normas_seguridad VARCHAR(2000) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'actualizado_en');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN actualizado_en DATETIME(6) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'laboratorios' AND column_name = 'actualizado_por');
SET @sql = IF(@falta, 'ALTER TABLE laboratorios ADD COLUMN actualizado_por VARCHAR(120) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
CREATE TABLE IF NOT EXISTS usuario_laboratorios (
  usuario_id      BIGINT NOT NULL,
  laboratorio_id  BIGINT NOT NULL,
  CONSTRAINT pk_usuario_laboratorios PRIMARY KEY (usuario_id, laboratorio_id),
  CONSTRAINT fk_ul_usuario     FOREIGN KEY (usuario_id)     REFERENCES usuarios (id) ON DELETE CASCADE,
  CONSTRAINT fk_ul_laboratorio FOREIGN KEY (laboratorio_id) REFERENCES laboratorios (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- Docentes (ahora coordinadores) y auxiliares (ahora monitores) quedan asignados a los laboratorios de su sede
INSERT IGNORE INTO usuario_laboratorios (usuario_id, laboratorio_id)
SELECT u.id, l.id FROM usuarios u JOIN laboratorios l ON l.sede_id = u.sede_id AND l.activo = TRUE
WHERE u.rol IN ('COORDINADOR_LAB', 'MONITOR') AND u.activo = TRUE;

-- =========================== 3. Parametrización y archivos ===========================
CREATE TABLE IF NOT EXISTS permisos_rol (
  rol         VARCHAR(20)  NOT NULL,
  permiso     VARCHAR(40)  NOT NULL,
  habilitado  BOOLEAN      NOT NULL,
  CONSTRAINT pk_permisos_rol PRIMARY KEY (rol, permiso),
  CONSTRAINT ck_permisos_rol CHECK (rol IN ('COORDINADOR_LAB', 'MONITOR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS catalogos (
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

CREATE TABLE IF NOT EXISTS parametros (
  clave        VARCHAR(60)   NOT NULL,
  valor        VARCHAR(200)  NOT NULL,
  descripcion  VARCHAR(300)  NULL,
  CONSTRAINT pk_parametros PRIMARY KEY (clave)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS plantillas (
  codigo   VARCHAR(40)   NOT NULL,
  nombre   VARCHAR(120)  NOT NULL,
  asunto   VARCHAR(200)  NOT NULL,
  cuerpo   VARCHAR(4000) NOT NULL,
  activa   BOOLEAN       NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_plantillas PRIMARY KEY (codigo)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS archivos (
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

CREATE TABLE IF NOT EXISTS laboratorio_documentos (
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

-- =========================== 4. Inventario ===========================
ALTER TABLE equipos MODIFY categoria VARCHAR(80) NOT NULL, MODIFY estado_fisico VARCHAR(80) NULL DEFAULT 'Bueno';
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'equipos' AND constraint_name = 'ck_equipos_fisico');
SET @sql = IF(@existe, 'ALTER TABLE equipos DROP CHECK ck_equipos_fisico', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'equipos' AND column_name = 'observaciones');
SET @sql = IF(@falta, 'ALTER TABLE equipos ADD COLUMN observaciones VARCHAR(1000) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'equipos' AND constraint_name = 'ck_equipos_estado');
SET @sql = IF(@existe, 'ALTER TABLE equipos DROP CHECK ck_equipos_estado', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
UPDATE equipos SET estado = 'DISPONIBLE' WHERE estado = 'PRESTADO';
ALTER TABLE equipos ADD CONSTRAINT ck_equipos_estado CHECK (estado IN ('DISPONIBLE', 'MANTENIMIENTO', 'FUERA_SERVICIO'));

-- =========================== 5. Novedades: registro preliminar y validación ===========================
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'cantidad');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN cantidad DECIMAL(14,3) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'unidad');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN unidad VARCHAR(10) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'validacion');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN validacion VARCHAR(12) NOT NULL DEFAULT ''VALIDADA''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'validado_por');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN validado_por VARCHAR(120) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'validado_en');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN validado_en DATETIME(6) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'observacion_validacion');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN observacion_validacion VARCHAR(600) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
-- Las novedades anteriores quedan VALIDADAS; las nuevas arrancan PENDIENTE
ALTER TABLE reportes_novedad ALTER COLUMN validacion SET DEFAULT 'PENDIENTE';
ALTER TABLE reportes_novedad ALTER COLUMN notificacion SET DEFAULT 'EN_ESPERA';
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND constraint_name = 'ck_novedad_tipo');
SET @sql = IF(@existe, 'ALTER TABLE reportes_novedad DROP CHECK ck_novedad_tipo', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE reportes_novedad ADD CONSTRAINT ck_novedad_tipo CHECK (tipo IN ('AGOTADO', 'DANADO', 'PERDIDO', 'FALLA', 'OTRO', 'EXISTENCIAS', 'CONSUMO'));
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND constraint_name = 'ck_novedad_notificacion');
SET @sql = IF(@existe, 'ALTER TABLE reportes_novedad DROP CHECK ck_novedad_notificacion', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE reportes_novedad ADD CONSTRAINT ck_novedad_notificacion CHECK (notificacion IN ('EN_ESPERA', 'PENDIENTE', 'ENVIADA_IA', 'ENVIADA_PLANTILLA', 'SIN_CONFIGURAR', 'FALLIDA', 'PENDIENTE_RESUMEN', 'ENVIADA_RESUMEN', 'NO_APLICA'));
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND constraint_name = 'ck_novedad_validacion');
SET @sql = IF(@existe, 'ALTER TABLE reportes_novedad DROP CHECK ck_novedad_validacion', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE reportes_novedad ADD CONSTRAINT ck_novedad_validacion CHECK (validacion IN ('PENDIENTE', 'VALIDADA', 'RECHAZADA'));
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND constraint_name = 'ck_novedad_cantidad');
SET @sql = IF(@existe, 'ALTER TABLE reportes_novedad DROP CHECK ck_novedad_cantidad', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE reportes_novedad ADD CONSTRAINT ck_novedad_cantidad CHECK (cantidad IS NULL OR cantidad >= 0);
SET @existe = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND index_name = 'idx_novedad_validacion');
SET @sql = IF(@existe, 'DO 0', 'ALTER TABLE reportes_novedad ADD INDEX idx_novedad_validacion (sede_id, validacion)');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

CREATE TABLE IF NOT EXISTS novedad_archivos (
  novedad_id  BIGINT       NOT NULL,
  archivo_id  VARCHAR(32)  NOT NULL,
  CONSTRAINT pk_novedad_archivos PRIMARY KEY (novedad_id, archivo_id),
  CONSTRAINT fk_novarch_novedad  FOREIGN KEY (novedad_id) REFERENCES reportes_novedad (id),
  CONSTRAINT fk_novarch_archivo  FOREIGN KEY (archivo_id) REFERENCES archivos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mantenimientos (
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

-- =========================== 6. Solicitudes, presupuesto y contratación ===========================
CREATE TABLE IF NOT EXISTS solicitudes (
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

CREATE TABLE IF NOT EXISTS solicitud_archivos (
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

CREATE TABLE IF NOT EXISTS solicitud_eventos (
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

SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'presupuesto_movimientos' AND column_name = 'solicitud_id');
SET @sql = IF(@falta, 'ALTER TABLE presupuesto_movimientos ADD COLUMN solicitud_id BIGINT NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'presupuesto_movimientos' AND constraint_name = 'fk_presupuesto_solicitud');
SET @sql = IF(@existe, 'DO 0', 'ALTER TABLE presupuesto_movimientos ADD CONSTRAINT fk_presupuesto_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitudes (id)');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- =========================== 7. Seguimiento: compromisos, avances y evidencias ===========================
CREATE TABLE IF NOT EXISTS compromisos (
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

CREATE TABLE IF NOT EXISTS avances (
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

CREATE TABLE IF NOT EXISTS avance_archivos (
  avance_id   BIGINT       NOT NULL,
  archivo_id  VARCHAR(32)  NOT NULL,
  CONSTRAINT pk_avance_archivos PRIMARY KEY (avance_id, archivo_id),
  CONSTRAINT fk_avarch_avance   FOREIGN KEY (avance_id)  REFERENCES avances (id),
  CONSTRAINT fk_avarch_archivo  FOREIGN KEY (archivo_id) REFERENCES archivos (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

SET @existe = (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'historial_eventos' AND index_name = 'idx_hist_placa');
SET @sql = IF(@existe, 'DO 0', 'ALTER TABLE historial_eventos ADD INDEX idx_hist_placa (placa, fecha)');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- =========================== 8. (Opcional) Borrar las tablas de préstamos que ya no se usan ===========================
-- Quita los dos guiones de cada línea si ya no necesitas esa información (no se puede deshacer):
-- DROP TABLE IF EXISTS solicitudes_insumo;
-- DROP TABLE IF EXISTS registros_pendientes;
-- DROP TABLE IF EXISTS prestamos;

SELECT 'Migración 06 aplicada: roles ADMINISTRADOR, COORDINADOR_LAB y MONITOR, y módulos de gestión' AS resultado;
