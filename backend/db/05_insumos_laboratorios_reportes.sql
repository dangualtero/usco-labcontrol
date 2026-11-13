-- =====================================================================================================
-- USCO LabControl · 05 · Insumos con cantidades, laboratorios por sede, seguimiento de reportes y
--                         solicitudes de insumos.
--
-- Para una base de datos que YA EXISTE (creada antes de esta versión). No borra nada.
-- Se puede ejecutar varias veces: cada cambio revisa primero si ya está hecho.
-- Ejecútalo como root:   mysql -uroot -p labcontrol   y luego   source ruta/05_insumos_laboratorios_reportes.sql
-- (Las bases nuevas creadas con 01_esquema.sql ya traen todo esto.)
-- =====================================================================================================
SET NAMES utf8mb4;
USE labcontrol;

-- ---------- Ayuda: ejecuta @sql solo si @falta = 1 (sin procedimientos ni DELIMITER)
-- Patrón:  SET @falta = (condición);  SET @sql = '...';  luego las 3 líneas PREPARE / EXECUTE / DEALLOCATE.

-- =========================== 1. Equipos: tipo, cantidad, unidad y referencia ===========================
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'equipos' AND column_name = 'tipo');
SET @sql = IF(@falta, 'ALTER TABLE equipos ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT ''EQUIPO''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'equipos' AND column_name = 'cantidad');
SET @sql = IF(@falta, 'ALTER TABLE equipos ADD COLUMN cantidad DECIMAL(14,3) NOT NULL DEFAULT 1', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'equipos' AND column_name = 'unidad');
SET @sql = IF(@falta, 'ALTER TABLE equipos ADD COLUMN unidad VARCHAR(10) NOT NULL DEFAULT ''UNIDAD''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'equipos' AND column_name = 'cantidad_referencia');
SET @sql = IF(@falta, 'ALTER TABLE equipos ADD COLUMN cantidad_referencia DECIMAL(14,3) NULL COMMENT ''100 % del insumo; alerta con 25 % o menos''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- Reglas (CHECK): se quitan si existen y se vuelven a crear con la versión nueva
SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'equipos' AND constraint_name = 'ck_equipos_placa');
SET @sql = IF(@existe, 'ALTER TABLE equipos DROP CHECK ck_equipos_placa', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE equipos ADD CONSTRAINT ck_equipos_placa
  CHECK ((tipo = 'EQUIPO' AND placa LIKE 'USCO-EQ-___') OR (tipo = 'INSUMO' AND placa LIKE 'USCO-IN-___'));

SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'equipos' AND constraint_name = 'ck_equipos_tipo');
SET @sql = IF(@existe, 'ALTER TABLE equipos DROP CHECK ck_equipos_tipo', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE equipos ADD CONSTRAINT ck_equipos_tipo CHECK (tipo IN ('EQUIPO', 'INSUMO'));

SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'equipos' AND constraint_name = 'ck_equipos_unidad');
SET @sql = IF(@existe, 'ALTER TABLE equipos DROP CHECK ck_equipos_unidad', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE equipos ADD CONSTRAINT ck_equipos_unidad
  CHECK (unidad IN ('UNIDAD', 'ML', 'L', 'MG', 'G', 'KG', 'CM', 'M') AND (tipo = 'INSUMO' OR unidad = 'UNIDAD'));

SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'equipos' AND constraint_name = 'ck_equipos_cantidad');
SET @sql = IF(@existe, 'ALTER TABLE equipos DROP CHECK ck_equipos_cantidad', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE equipos ADD CONSTRAINT ck_equipos_cantidad
  CHECK (cantidad >= 0 AND (cantidad_referencia IS NULL OR cantidad_referencia > 0));

-- =========================== 2. Laboratorios por sede ===========================
CREATE TABLE IF NOT EXISTS laboratorios (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  sede_id      BIGINT       NOT NULL,
  nombre       VARCHAR(40)  NOT NULL,
  ubicacion    VARCHAR(160) NULL,
  descripcion  VARCHAR(300) NULL,
  activo       BOOLEAN      NOT NULL DEFAULT TRUE,
  creado_en    DATETIME(6)  NOT NULL,
  CONSTRAINT pk_laboratorios        PRIMARY KEY (id),
  CONSTRAINT uk_laboratorios_nombre UNIQUE (sede_id, nombre),
  CONSTRAINT fk_laboratorios_sede   FOREIGN KEY (sede_id) REFERENCES sedes (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Los laboratorios que ya usan los equipos de cada sede (los equipos sin sede son de la sede principal)
INSERT IGNORE INTO laboratorios (sede_id, nombre, activo, creado_en)
SELECT DISTINCT COALESCE(e.sede_id, (SELECT MIN(id) FROM sedes)), e.laboratorio, TRUE, NOW(6)
FROM equipos e WHERE e.laboratorio IS NOT NULL AND e.laboratorio <> '';

-- Los 4 laboratorios de siempre en la sede principal
INSERT IGNORE INTO laboratorios (sede_id, nombre, activo, creado_en)
SELECT s.id, l.nombre, TRUE, NOW(6)
FROM (SELECT MIN(id) AS id FROM sedes) s
CROSS JOIN (SELECT 'Electrónica' AS nombre UNION ALL SELECT 'Petróleos' UNION ALL SELECT 'Civil' UNION ALL SELECT 'Agrícola') l
WHERE s.id IS NOT NULL;

-- =========================== 3. Reportes: estado de seguimiento y resumen diario ===========================
SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'estado');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN estado VARCHAR(12) NOT NULL DEFAULT ''ABIERTO'' COMMENT ''Seguimiento: ABIERTO → EN_PROCESO → RESUELTO''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @falta = (SELECT COUNT(*) = 0 FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND column_name = 'actualizado_en');
SET @sql = IF(@falta, 'ALTER TABLE reportes_novedad ADD COLUMN actualizado_en DATETIME(6) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND constraint_name = 'ck_novedad_notificacion');
SET @sql = IF(@existe, 'ALTER TABLE reportes_novedad DROP CHECK ck_novedad_notificacion', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE reportes_novedad ADD CONSTRAINT ck_novedad_notificacion
  CHECK (notificacion IN ('PENDIENTE', 'ENVIADA_IA', 'ENVIADA_PLANTILLA', 'SIN_CONFIGURAR', 'FALLIDA', 'PENDIENTE_RESUMEN', 'ENVIADA_RESUMEN'));

SET @existe = (SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema = DATABASE() AND table_name = 'reportes_novedad' AND constraint_name = 'ck_novedad_estado');
SET @sql = IF(@existe, 'ALTER TABLE reportes_novedad DROP CHECK ck_novedad_estado', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;
ALTER TABLE reportes_novedad ADD CONSTRAINT ck_novedad_estado CHECK (estado IN ('ABIERTO', 'EN_PROCESO', 'RESUELTO'));

CREATE TABLE IF NOT EXISTS seguimiento_novedad (
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

-- =========================== 4. Solicitudes de insumos ===========================
CREATE TABLE IF NOT EXISTS solicitudes_insumo (
  id                    BIGINT         NOT NULL AUTO_INCREMENT,
  codigo                VARCHAR(20)    NOT NULL,
  sede_id               BIGINT         NOT NULL,
  equipo_id             BIGINT         NOT NULL COMMENT 'El insumo (equipos.tipo = INSUMO)',
  solicitante_id        BIGINT         NOT NULL,
  cantidad              DECIMAL(14,3)  NOT NULL,
  unidad                VARCHAR(10)    NOT NULL,
  cantidad_equivalente  DECIMAL(14,3)  NOT NULL,
  motivo                VARCHAR(300)   NOT NULL,
  asignatura            VARCHAR(120)   NULL,
  estado                VARCHAR(12)    NOT NULL DEFAULT 'PENDIENTE',
  creado_en             DATETIME(6)    NOT NULL,
  resuelto_en           DATETIME(6)    NULL,
  resuelto_por          VARCHAR(120)   NULL,
  respuesta             VARCHAR(300)   NULL,
  CONSTRAINT pk_solicitudes_insumo     PRIMARY KEY (id),
  CONSTRAINT uk_solicitudes_codigo     UNIQUE (codigo),
  CONSTRAINT fk_solicitud_sede         FOREIGN KEY (sede_id)        REFERENCES sedes (id),
  CONSTRAINT fk_solicitud_insumo       FOREIGN KEY (equipo_id)      REFERENCES equipos (id),
  CONSTRAINT fk_solicitud_solicitante  FOREIGN KEY (solicitante_id) REFERENCES usuarios (id),
  CONSTRAINT ck_solicitud_cantidad     CHECK (cantidad > 0 AND cantidad_equivalente > 0),
  CONSTRAINT ck_solicitud_unidad       CHECK (unidad IN ('UNIDAD', 'ML', 'L', 'MG', 'G', 'KG', 'CM', 'M')),
  CONSTRAINT ck_solicitud_estado       CHECK (estado IN ('PENDIENTE', 'APROBADA', 'RECHAZADA', 'CANCELADA')),
  INDEX idx_solicitud_sede_estado (sede_id, estado, creado_en),
  INDEX idx_solicitud_solicitante (solicitante_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- =========================== 5. Permisos del usuario de la aplicación ===========================
-- El usuario 'labcontrol' (00_usuario_mysql.sql) ya tiene SELECT/INSERT/UPDATE/DELETE sobre toda la base,
-- así que las tablas nuevas quedan cubiertas sin hacer nada más.

SELECT 'Migración 05 aplicada: insumos, laboratorios, seguimiento de reportes y solicitudes de insumos' AS resultado;
