-- Tablas que la aplicación usa con JdbcTemplate (no son entidades JPA). En las pruebas H2 las crea este script,
-- después de que Hibernate genera las demás (spring.jpa.defer-datasource-initialization).
CREATE TABLE IF NOT EXISTS permisos_rol (
  rol         VARCHAR(20)  NOT NULL,
  permiso     VARCHAR(40)  NOT NULL,
  habilitado  BOOLEAN      NOT NULL,
  PRIMARY KEY (rol, permiso)
);
CREATE TABLE IF NOT EXISTS parametros (
  clave        VARCHAR(60)   NOT NULL PRIMARY KEY,
  valor        VARCHAR(200)  NOT NULL,
  descripcion  VARCHAR(300)  NULL
);
CREATE TABLE IF NOT EXISTS plantillas (
  codigo  VARCHAR(40)    NOT NULL PRIMARY KEY,
  nombre  VARCHAR(120)   NOT NULL,
  asunto  VARCHAR(200)   NOT NULL,
  cuerpo  VARCHAR(4000)  NOT NULL,
  activa  BOOLEAN        NOT NULL DEFAULT TRUE
);
