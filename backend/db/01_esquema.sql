-- =====================================================================================================
-- USCO LabControl · 01 · Esquema completo de la base de datos (MySQL 8.0.16 o superior)
--
-- Este script es la ÚNICA fuente de la estructura: tablas, tipos, longitudes, obligatoriedad, valores
-- permitidos (CHECK), llaves primarias, únicas y foráneas, e índices. El backend NO crea ni modifica tablas
-- (spring.jpa.hibernate.ddl-auto = none); solo lee y escribe datos.
--
-- Orden de ejecución:  00_usuario_mysql.sql (una vez, como root) → 01_esquema.sql → 02_inmutabilidad.sql
--                      → 03_datos_iniciales.sql
-- ⚠ Borra y vuelve a crear todas las tablas: ejecútalo solo para crear la base desde cero.
-- =====================================================================================================
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS labcontrol CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE labcontrol;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS reportes_novedad, registros_pendientes, imagenes, presupuesto_movimientos,
                     historial_eventos, prestamos, equipos, usuarios, sedes;
SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------------------------------
-- Sedes de la Universidad. El slug es el primer segmento de la URL del frontend (/neiva, /pitalito…).
-- -----------------------------------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------------------------------
-- Usuarios. El rol lo asigna la base de datos (no se elige al iniciar sesión). Contraseñas con BCrypt.
-- En un INSERT manual puedes escribir la contraseña NORMAL en password_hash: el backend la cifra a los pocos segundos.
-- El Coordinador es general: no tiene sede (sede_id NULL) y administra todas.
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE usuarios (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  nombre              VARCHAR(120) NOT NULL,
  correo              VARCHAR(120) NOT NULL,
  password_hash       VARCHAR(255) NOT NULL,
  rol                 VARCHAR(20)  NOT NULL,
  documento           VARCHAR(20)  NOT NULL,
  codigo_estudiantil  VARCHAR(20)  NULL,
  programa            VARCHAR(80)  NULL,
  activo              BOOLEAN      NOT NULL DEFAULT TRUE,
  sede_id             BIGINT       NULL,
  CONSTRAINT pk_usuarios                 PRIMARY KEY (id),
  CONSTRAINT uk_usuarios_correo          UNIQUE (correo),
  CONSTRAINT uk_usuarios_documento       UNIQUE (documento),
  CONSTRAINT uk_usuarios_codigo          UNIQUE (codigo_estudiantil),
  CONSTRAINT fk_usuarios_sede            FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_usuarios_rol             CHECK (rol IN ('ESTUDIANTE', 'DOCENTE', 'AUXILIAR', 'COORDINADOR')),
  CONSTRAINT ck_usuarios_correo          CHECK (correo LIKE '%@usco.edu.co')
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------------------------------
-- Equipos de laboratorio. Las bajas son lógicas (activo = FALSE): el registro se conserva para auditoría.
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE equipos (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  placa          VARCHAR(20)  NOT NULL,
  nombre         VARCHAR(120) NOT NULL,
  marca          VARCHAR(60)  NULL,
  modelo         VARCHAR(60)  NULL,
  numero_serie   VARCHAR(60)  NULL,
  categoria      VARCHAR(60)  NOT NULL,
  laboratorio    VARCHAR(40)  NOT NULL,
  ubicacion      VARCHAR(160) NULL,
  descripcion    VARCHAR(500) NULL,
  specs          VARCHAR(600) NULL COMMENT 'Especificaciones separadas por |',
  accesorios     VARCHAR(600) NULL COMMENT 'Accesorios separados por |',
  estado_fisico  VARCHAR(30)  NULL DEFAULT 'Bueno',
  foto_url       VARCHAR(400) NULL,
  estado         VARCHAR(20)  NOT NULL DEFAULT 'DISPONIBLE',
  activo         BOOLEAN      NOT NULL DEFAULT TRUE,
  baja_motivo    VARCHAR(300) NULL,
  baja_fecha     DATETIME(6)  NULL,
  sede_id        BIGINT       NULL,
  CONSTRAINT pk_equipos         PRIMARY KEY (id),
  CONSTRAINT uk_equipos_placa   UNIQUE (placa),
  CONSTRAINT fk_equipos_sede    FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_equipos_placa   CHECK (placa LIKE 'USCO-EQ-___'),
  CONSTRAINT ck_equipos_estado  CHECK (estado IN ('DISPONIBLE', 'PRESTADO', 'MANTENIMIENTO')),
  CONSTRAINT ck_equipos_fisico  CHECK (estado_fisico IS NULL OR estado_fisico IN ('Excelente', 'Bueno', 'Regular', 'Deteriorado'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------------------------------
-- Préstamos y reservas (RESERVADO → ACTIVO → DEVUELTO, o CANCELADO / RECHAZADO).
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE prestamos (
  id                   BIGINT       NOT NULL AUTO_INCREMENT,
  codigo               VARCHAR(20)  NOT NULL,
  equipo_id            BIGINT       NOT NULL,
  solicitante_id       BIGINT       NOT NULL,
  auxiliar_salida_id   BIGINT       NULL,
  auxiliar_entrada_id  BIGINT       NULL,
  custodio_id          BIGINT       NULL COMMENT 'Auxiliar responsable del equipo (cambia en el cierre de turno)',
  estado               VARCHAR(20)  NOT NULL,
  asignatura           VARCHAR(120) NULL,
  docente              VARCHAR(120) NULL,
  franja_inicio        DATETIME(6)  NULL,
  franja_fin           DATETIME(6)  NULL,
  fecha_solicitud      DATETIME(6)  NOT NULL,
  fecha_salida         DATETIME(6)  NULL,
  fecha_limite         DATETIME(6)  NULL,
  fecha_entrada        DATETIME(6)  NULL,
  estado_entrada       VARCHAR(20)  NULL,
  observaciones        VARCHAR(600) NULL,
  incidencia           VARCHAR(20)  NULL,
  CONSTRAINT pk_prestamos                PRIMARY KEY (id),
  CONSTRAINT uk_prestamos_codigo         UNIQUE (codigo),
  CONSTRAINT fk_prestamos_equipo         FOREIGN KEY (equipo_id)           REFERENCES equipos (id),
  CONSTRAINT fk_prestamos_solicitante    FOREIGN KEY (solicitante_id)      REFERENCES usuarios (id),
  CONSTRAINT fk_prestamos_aux_salida     FOREIGN KEY (auxiliar_salida_id)  REFERENCES usuarios (id),
  CONSTRAINT fk_prestamos_aux_entrada    FOREIGN KEY (auxiliar_entrada_id) REFERENCES usuarios (id),
  CONSTRAINT fk_prestamos_custodio       FOREIGN KEY (custodio_id)         REFERENCES usuarios (id),
  CONSTRAINT ck_prestamos_estado         CHECK (estado IN ('RESERVADO', 'ACTIVO', 'DEVUELTO', 'CANCELADO', 'RECHAZADO')),
  CONSTRAINT ck_prestamos_estado_entrada CHECK (estado_entrada IS NULL OR estado_entrada IN ('BUEN_ESTADO', 'CON_NOVEDAD')),
  INDEX idx_prestamo_equipo_estado (equipo_id, estado),
  INDEX idx_prestamo_solicitante   (solicitante_id, estado)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------------------------------
-- Historial inmutable (RF-03): solo se inserta. Los datos se copian (no llaves foráneas) para que el
-- registro histórico no cambie aunque después se edite el equipo o el usuario. Ver 02_inmutabilidad.sql.
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE historial_eventos (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  fecha            DATETIME(6)  NOT NULL,
  tipo             VARCHAR(20)  NOT NULL COMMENT 'RESERVA, CANCELACION, RECHAZO, SALIDA, ENTRADA, INCIDENCIA, CUSTODIA, ALTA, ESTADO, BAJA, NOVEDAD',
  placa            VARCHAR(20)  NULL,
  equipo_nombre    VARCHAR(120) NULL,
  laboratorio      VARCHAR(40)  NULL,
  prestamo_codigo  VARCHAR(20)  NULL,
  usuario_nombre   VARCHAR(120) NULL,
  auxiliar_nombre  VARCHAR(120) NULL,
  detalle          VARCHAR(600) NULL,
  sede             VARCHAR(40)  NULL COMMENT 'Slug de la sede del equipo; NULL = sede principal',
  CONSTRAINT pk_historial_eventos PRIMARY KEY (id),
  INDEX idx_hist_fecha (fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------------------------------
-- Presupuesto por sede: INGRESO suma, GASTO resta. Los movimientos no se borran: se anulan.
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE presupuesto_movimientos (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  sede_id           BIGINT       NOT NULL,
  tipo              VARCHAR(10)  NOT NULL,
  concepto          VARCHAR(160) NOT NULL,
  categoria         VARCHAR(40)  NOT NULL,
  monto             BIGINT       NOT NULL COMMENT 'Pesos colombianos, sin decimales',
  fecha             DATE         NOT NULL,
  placa             VARCHAR(20)  NULL,
  registrado_por    VARCHAR(120) NOT NULL,
  registrado_en     DATETIME(6)  NOT NULL,
  anulado           BOOLEAN      NOT NULL DEFAULT FALSE,
  anulacion_motivo  VARCHAR(300) NULL,
  anulado_por       VARCHAR(120) NULL,
  anulado_en        DATETIME(6)  NULL,
  CONSTRAINT pk_presupuesto_movimientos PRIMARY KEY (id),
  CONSTRAINT fk_presupuesto_sede        FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_presupuesto_tipo        CHECK (tipo IN ('INGRESO', 'GASTO')),
  CONSTRAINT ck_presupuesto_monto       CHECK (monto > 0 AND monto <= 100000000000),
  CONSTRAINT ck_presupuesto_categoria   CHECK (categoria IN ('Asignación', 'Compra de equipos', 'Mantenimiento', 'Calibración', 'Insumos y repuestos', 'Otro')),
  INDEX idx_presupuesto_sede_fecha (sede_id, fecha)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------------------------------
-- Fotografías subidas desde el computador o el celular (se sirven en /api/imagenes/{id}).
-- -----------------------------------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------------------------------
-- Registros de estudiantes que aún no confirman el código enviado a su correo (no son usuarios todavía).
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE registros_pendientes (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  correo              VARCHAR(120) NOT NULL,
  nombre              VARCHAR(120) NOT NULL,
  documento           VARCHAR(20)  NOT NULL,
  codigo_estudiantil  VARCHAR(20)  NULL,
  programa            VARCHAR(80)  NULL,
  password_hash       VARCHAR(255) NOT NULL,
  sede_id             BIGINT       NOT NULL,
  codigo_hash         VARCHAR(255) NOT NULL COMMENT 'Código de 6 dígitos cifrado con BCrypt',
  expira_en           DATETIME(6)  NOT NULL,
  intentos            INT          NOT NULL DEFAULT 0,
  ultimo_envio        DATETIME(6)  NOT NULL,
  envios_ventana      INT          NOT NULL DEFAULT 0,
  ventana_desde       DATETIME(6)  NOT NULL,
  creado_en           DATETIME(6)  NOT NULL,
  CONSTRAINT pk_registros_pendientes  PRIMARY KEY (id),
  CONSTRAINT uk_registros_correo      UNIQUE (correo),
  CONSTRAINT fk_registros_sede        FOREIGN KEY (sede_id) REFERENCES sedes (id),
  CONSTRAINT ck_registros_correo      CHECK (correo LIKE '%@usco.edu.co'),
  CONSTRAINT ck_registros_intentos    CHECK (intentos >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------------------------------
-- Novedades de equipos reportadas por los auxiliares y resultado del aviso (agente de IA en n8n / correo).
-- -----------------------------------------------------------------------------------------------------
CREATE TABLE reportes_novedad (
  id                     BIGINT        NOT NULL AUTO_INCREMENT,
  sede_id                BIGINT        NOT NULL,
  equipo_id              BIGINT        NOT NULL,
  tipo                   VARCHAR(20)   NOT NULL,
  descripcion            VARCHAR(1000) NOT NULL,
  reportado_por          VARCHAR(120)  NOT NULL,
  reportado_por_correo   VARCHAR(120)  NULL,
  creado_en              DATETIME(6)   NOT NULL,
  en_mantenimiento       BOOLEAN       NOT NULL DEFAULT FALSE,
  prioridad              VARCHAR(10)   NULL,
  asunto_correo          VARCHAR(200)  NULL,
  resumen_ia             VARCHAR(1000) NULL,
  accion_sugerida        VARCHAR(600)  NULL,
  notificacion           VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
  notificacion_detalle   VARCHAR(300)  NULL,
  destinatario           VARCHAR(120)  NULL,
  notificado_en          DATETIME(6)   NULL,
  intentos_notificacion  INT           NOT NULL DEFAULT 0,
  CONSTRAINT pk_reportes_novedad     PRIMARY KEY (id),
  CONSTRAINT fk_novedad_sede         FOREIGN KEY (sede_id)   REFERENCES sedes (id),
  CONSTRAINT fk_novedad_equipo       FOREIGN KEY (equipo_id) REFERENCES equipos (id),
  CONSTRAINT ck_novedad_tipo         CHECK (tipo IN ('AGOTADO', 'DANADO', 'PERDIDO', 'FALLA', 'OTRO')),
  CONSTRAINT ck_novedad_prioridad    CHECK (prioridad IS NULL OR prioridad IN ('ALTA', 'MEDIA', 'BAJA')),
  CONSTRAINT ck_novedad_notificacion CHECK (notificacion IN ('PENDIENTE', 'ENVIADA_IA', 'ENVIADA_PLANTILLA', 'SIN_CONFIGURAR', 'FALLIDA')),
  INDEX idx_novedad_sede_fecha (sede_id, creado_en)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
